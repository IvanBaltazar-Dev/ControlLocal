package com.controllocal.service.impl;

import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.repositorio.DetalleClienteRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.PersonaRolRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ClienteService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Autorizaciones;
import com.controllocal.service.soporte.Personas;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reglas y mensajes calcados de ClientesRest + ClienteInteresadoBusinessLogicImpl
 * de la v1. Dos cosas que NO hay que "arreglar" durante la convivencia:
 * <ul>
 *   <li>el cliente es catalogo compartido (cualquier agente edita cualquier
 *       cliente): el unico rol acotado es el BROKER;</li>
 *   <li>el alta crea la PERSONA y su rol CLIENTE — en la v2 no hay tabla de
 *       cliente con nombre y documento propios (Party-Role).</li>
 * </ul>
 */
@Service
public class ClienteServiceImpl implements ClienteService {

    private final DetalleClienteRepository clientes;
    private final PersonaRepository personas;
    private final PersonaRolRepository roles;
    private final OportunidadComercialRepository oportunidades;
    private final Alcances alcances;
    private final Autorizaciones autorizaciones;

    public ClienteServiceImpl(DetalleClienteRepository clientes, PersonaRepository personas,
                             PersonaRolRepository roles, OportunidadComercialRepository oportunidades,
                             Alcances alcances, Autorizaciones autorizaciones) {
        this.clientes = clientes;
        this.personas = personas;
        this.roles = roles;
        this.oportunidades = oportunidades;
        this.alcances = alcances;
        this.autorizaciones = autorizaciones;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaCliente> listar(int pagina, int tamano, Actor actor) {
        int tamanoValido = tamano(tamano);
        PageRequest pageable = PageRequest.of(Math.max(0, pagina - 1), tamanoValido);
        if (!esBroker(actor)) {
            // Catalogo compartido: admin y agente ven TODOS los del tenant.
            return paginaDe(clientes.pagina(actor.idOrganizacion(), pageable));
        }
        List<Long> ids = idsDelEquipo(actor);
        if (ids.isEmpty()) {
            return Pagina.vacia();
        }
        return paginaDe(clientes.paginaPorIds(actor.idOrganizacion(), ids, pageable));
    }

    /**
     * Bandeja con filtros. El texto se resuelve por CONJUNTO DE CANDIDATOS
     * —nombre y documento viven en {@code persona}, el rubro en
     * {@code detalle_cliente}— para no escribir un OR que cruce tablas
     * (contrato-listados-paginados.md §5). Con los cuatro filtros nulos el
     * resultado es el mismo que {@link #listar(int, int, Actor)}.
     */
    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaCliente> listar(FiltrosCliente filtros, Actor actor) {
        int pagina = Math.max(1, filtros.pagina());
        int tamano = tamano(filtros.tamano());
        AlcanceBandeja alcance = alcanceDeBandeja(actor);
        if (alcance == null) {
            return Pagina.vacia();
        }

        List<Long> ids = clientes.idsBandeja(actor.idOrganizacion(), texto(filtros.texto()),
                codigo(filtros.tipoPersona()), codigo(filtros.estado()), texto(filtros.rubro()),
                alcance.sinScope(), alcance.ids(), tamano, (pagina - 1) * tamano);
        long total = clientes.contarBandeja(actor.idOrganizacion(), texto(filtros.texto()),
                codigo(filtros.tipoPersona()), codigo(filtros.estado()), texto(filtros.rubro()),
                alcance.sinScope(), alcance.ids());

        // La ficha completa se carga SOLO para los ids de la pagina, y se
        // devuelve en el orden que fijo la base (id desc), no en el del `in`.
        List<FichaCliente> filas = ids.isEmpty() ? List.of()
                : clientes.fichasPorIds(actor.idOrganizacion(), ids).stream()
                        .map(ClienteServiceImpl::ficha).toList();
        return new Pagina<>(filas, total);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenClientes resumen(FiltrosCliente filtros, Actor actor) {
        AlcanceBandeja alcance = alcanceDeBandeja(actor);
        if (alcance == null) {
            return new ResumenClientes(0, 0, 0, 0, 0, List.of());
        }
        // El estado viaja nulo: el resumen cuenta activos e inactivos, no filtra.
        var kpi = clientes.resumenBandeja(actor.idOrganizacion(), texto(filtros.texto()),
                codigo(filtros.tipoPersona()), null, texto(filtros.rubro()),
                alcance.sinScope(), alcance.ids());
        long total = kpi == null ? 0 : kpi.getTotal();
        long activos = kpi == null ? 0 : kpi.getActivos();
        return new ResumenClientes(total, activos, total - activos,
                kpi == null ? 0 : kpi.getContactoAutorizado(),
                kpi == null ? 0 : kpi.getUsoDatoAutorizado(),
                clientes.rubrosDisponibles(actor.idOrganizacion(), alcance.sinScope(), alcance.ids()));
    }

    /**
     * Alcance de la bandeja resuelto una sola vez. {@code null} significa "el
     * broker no supervisa a nadie con clientes": conjunto vacio, y ademas evita
     * un {@code in ()} sin elementos, que Postgres no acepta.
     */
    private AlcanceBandeja alcanceDeBandeja(Actor actor) {
        if (!esBroker(actor)) {
            // Centinela -1: la consulta lleva `in (:ids)` aunque no se use.
            return new AlcanceBandeja(true, List.of(-1L));
        }
        List<Long> ids = idsDelEquipo(actor);
        return ids.isEmpty() ? null : new AlcanceBandeja(false, ids);
    }

    private record AlcanceBandeja(boolean sinScope, List<Long> ids) {
    }

    /** Un filtro en blanco es "sin filtro", no una busqueda de cadena vacia. */
    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String codigo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    @Transactional(readOnly = true)
    public FichaCliente obtener(long id, Actor actor) {
        return ficha(cargarConAcceso(id, actor));
    }

    @Override
    @Transactional
    public FichaCliente registrar(DatosCliente datos, Actor actor) {
        if (datos == null) {
            throw new ReglaNegocioException("Los datos del cliente son obligatorios.");
        }
        String tipoPersona = Personas.exigirCodigo(datos.tipoPersona(), Personas.TIPOS_PERSONA,
                "tipo de persona");
        String tipoDocumento = Personas.exigirCodigo(datos.tipoDocumento(), Personas.TIPOS_DOCUMENTO,
                "tipo de documento");
        String estado = Personas.estadoOActivo(datos.estado());
        Personas.validar(tipoDocumento, datos.numeroDocumento(), datos.nombre());

        Persona persona = Personas.nueva(actor.idOrganizacion(), tipoPersona, tipoDocumento,
                datos.numeroDocumento(), datos.nombre(), datos.telefono(), datos.correo(),
                estado, datos.consentimientoUsoDato());
        personas.save(persona);

        // D-27: el alta es TRANSACCIONAL —persona + rol + contacto +
        // autorizacion—. Si la autorizacion falta, esto lanza y NADA se
        // persiste: no queda una persona marcada como que no autorizo, porque
        // esa fila seria justo el dato que no se puede guardar.
        autorizaciones.registrarEnAlta(persona.getId(), datos.consentimientoUsoDato(), actor);

        PersonaRol rol = Personas.nuevoRol(actor.idOrganizacion(), persona, TipoRol.CLIENTE);
        roles.save(rol);

        DetalleCliente cliente = new DetalleCliente();
        cliente.setOrganizacionId(actor.idOrganizacion());
        cliente.setRol(rol);
        cliente.setRubroComercial(datos.rubroComercial());
        cliente.setConsentimientoContacto(datos.consentimientoContacto());
        // @MapsId: el id del detalle es el del rol, asi que se lee del guardado.
        return ficha(clientes.save(cliente));
    }

    @Override
    @Transactional
    public FichaCliente actualizar(long id, DatosCliente datos, Actor actor) {
        if (datos == null) {
            throw new ReglaNegocioException("Los datos del cliente son obligatorios.");
        }
        DetalleCliente cliente = cargarConAcceso(id, actor);
        Persona persona = cliente.getRol().getPersona();

        // actualizarDatos de la v1: reemplaza los tres campos tal cual llegan
        // (incluso a null), y NO toca documento ni tipo de persona.
        persona.setTelefono(datos.telefono());
        persona.setCorreo(datos.correo());
        persona.setNombresORazonSocial(datos.nombre());
        cliente.setRubroComercial(datos.rubroComercial());
        cliente.setConsentimientoContacto(datos.consentimientoContacto());
        if (datos.consentimientoUsoDato() != null) {
            persona.setConsentimientoUsoDato(datos.consentimientoUsoDato());
        }
        if (datos.estado() != null && !datos.estado().isBlank()) {
            persona.setEstado(Personas.exigirCodigo(datos.estado(), Personas.ESTADOS,
                    "estado de la persona"));
        }
        // La v1 revalida la persona completa al guardar: el nombre sigue siendo
        // obligatorio aunque el PUT no toque el documento.
        Personas.validar(persona.getTipoDocumento(), persona.getNumeroDocumento(),
                persona.getNombresORazonSocial());
        personas.save(persona);
        clientes.save(cliente);
        return ficha(cliente);
    }

    @Override
    @Transactional
    public boolean desactivar(long id, Actor actor) {
        DetalleCliente cliente = clientes.buscarFicha(actor.idOrganizacion(), id).orElse(null);
        if (cliente == null) {
            return false;
        }
        if (!alcanza(cliente, actor)) {
            throw new AccesoNoAutorizadoException();
        }
        Persona persona = cliente.getRol().getPersona();
        persona.setEstado(Personas.INACTIVO);
        personas.save(persona);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Autorizaciones.Constancia autorizacion(long id, Actor actor) {
        // La autorizacion es de la PERSONA, no del rol: quien la otorgo lo hizo
        // una vez y vale para todos sus roles. Por eso se consulta por
        // persona.id y no por el id del rol CLIENTE que llega en la ruta.
        DetalleCliente cliente = cargarConAcceso(id, actor);
        return autorizaciones.constancia(cliente.getRol().getPersona().getId(), actor.idOrganizacion());
    }

    // ------------------------------------------------------------------
    // Alcance (§2: solo el BROKER queda acotado) y soporte.
    // ------------------------------------------------------------------

    private DetalleCliente cargarConAcceso(long id, Actor actor) {
        DetalleCliente cliente = clientes.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Cliente"));
        if (!alcanza(cliente, actor)) {
            throw new AccesoNoAutorizadoException();
        }
        return cliente;
    }

    private boolean alcanza(DetalleCliente cliente, Actor actor) {
        if (!esBroker(actor)) {
            return true;
        }
        return idsDelEquipo(actor).contains(cliente.getId());
    }

    /** Clientes que el equipo del broker trabaja (via oportunidades), ids desc. */
    private List<Long> idsDelEquipo(Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return List.of();
        }
        return oportunidades.idsClienteDelEquipo(alcance.idOrganizacion(), alcance.paramRoles());
    }

    private static boolean esBroker(Actor actor) {
        return !actor.esTenantAdmin() && !actor.esAgente();
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private Pagina<FichaCliente> paginaDe(Page<DetalleCliente> page) {
        return new Pagina<>(page.getContent().stream().map(ClienteServiceImpl::ficha).toList(),
                page.getTotalElements());
    }

    private static FichaCliente ficha(DetalleCliente cliente) {
        Persona persona = cliente.getRol() != null ? cliente.getRol().getPersona() : null;
        return new FichaCliente(
                cliente.getId(),
                persona != null ? persona.getTipoPersona() : null,
                persona != null ? persona.getTipoDocumento() : null,
                persona != null ? persona.getNumeroDocumento() : null,
                persona != null ? persona.getNombresORazonSocial() : null,
                persona != null ? persona.getTelefono() : null,
                persona != null ? persona.getCorreo() : null,
                cliente.getRubroComercial(),
                persona != null ? persona.getEstado() : null,
                cliente.getConsentimientoContacto(),
                // En la v2 el consentimiento de USO DEL DATO vive en la persona
                // (la v1 lo duplicaba en cliente_interesado); el cable no cambia.
                persona != null ? persona.getConsentimientoUsoDato() : null,
                persona != null ? Fechas.local(persona.getFechaCreacion()) : null);
    }
}
