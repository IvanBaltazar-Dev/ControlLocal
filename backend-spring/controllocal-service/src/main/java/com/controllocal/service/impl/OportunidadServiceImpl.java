package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.MotivoNoContinuidad;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleClienteRepository;
import com.controllocal.persistence.repositorio.MotivoNoContinuidadRepository;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PlanDeConsulta;
import com.controllocal.service.Actor;
import com.controllocal.service.OfertaService;
import com.controllocal.service.OportunidadService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.AccesoOportunidad;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Alcances.Alcance;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Transiciones;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Reglas y mensajes calcados de OportunidadesRest +
 * OportunidadComercialBusinessLogicImpl + MotivoNoContinuidadBusinessLogicImpl.
 *
 * <p>Mejora del stack nuevo, sin tocar el cable: A->N se aplica por
 * {@link Transiciones}, asi que el cierre por no continuidad deja fila en
 * {@code historial_estado} con actor y motivo (MEJ-01). El {@code motivoCierre}
 * que viaja en la respuesta sigue siendo la DESCRIPCION de la razon, como en la v1.
 */
@Service
public class OportunidadServiceImpl implements OportunidadService {

    private static final DateTimeFormatter CODIGO = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private final OportunidadComercialRepository oportunidades;
    private final MotivoNoContinuidadRepository motivos;
    private final CaptacionRepository captaciones;
    private final DetalleClienteRepository clientes;
    private final DetalleAgenteRepository agentes;
    private final Alcances alcances;
    private final Transiciones transiciones;
    private final PlanDeConsulta plan;
    /** Quien alcanza esta oportunidad. Una sola definicion para los tres casos de uso. */
    private final AccesoOportunidad acceso;
    /**
     * La mesa de negociacion (WP-A). La ficha INDIVIDUAL publica sus ofertas y
     * las capacidades del actor sobre ella para que la pantalla no tenga que
     * pedirlas aparte ni decidir por rol.
     */
    private final OfertaService ofertas;

    public OportunidadServiceImpl(OportunidadComercialRepository oportunidades,
                                  MotivoNoContinuidadRepository motivos, CaptacionRepository captaciones,
                                  DetalleClienteRepository clientes, DetalleAgenteRepository agentes,
                                  Alcances alcances, Transiciones transiciones,
                                  PlanDeConsulta plan, AccesoOportunidad acceso,
                                  OfertaService ofertas) {
        this.oportunidades = oportunidades;
        this.motivos = motivos;
        this.captaciones = captaciones;
        this.clientes = clientes;
        this.agentes = agentes;
        this.alcances = alcances;
        this.transiciones = transiciones;
        this.plan = plan;
        this.acceso = acceso;
        this.ofertas = ofertas;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaOportunidad> listar(int pagina, int tamano, Long idCaptacion, Long idCliente,
                                           String estado, String query, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return Pagina.vacia();
        }
        int tamanoValido = tamano(tamano);
        String texto = vacioNull(query);
        if (texto != null) {
            return porTexto(alcance, actor, idCaptacion, idCliente, vacioNull(estado), texto,
                    Math.max(1, pagina), tamanoValido);
        }
        Page<OportunidadComercial> page = oportunidades.buscar(
                alcance.idOrganizacion(), alcance.global(), actor.esAgente(), alcance.paramRoles(),
                idCaptacion, idCliente, vacioNull(estado),
                PageRequest.of(Math.max(0, pagina - 1), tamanoValido));
        return new Pagina<>(page.getContent().stream().map(OportunidadServiceImpl::ficha).toList(),
                page.getTotalElements());
    }

    /**
     * Camino de BUSQUEDA POR CONJUNTO DE CANDIDATOS (§5 del contrato de
     * listados): una rama por tabla, {@code UNION} en la base, y el mismo
     * conjunto para el conteo y para la pagina. La proyeccion completa se carga
     * despues, solo para los ids ya resueltos.
     */
    private Pagina<FichaOportunidad> porTexto(Alcance alcance, Actor actor, Long idCaptacion,
                                              Long idCliente, String estado, String texto,
                                              int pagina, int tamano) {
        plan.forzarPersonalizado();
        String roles = alcance.paramRolesArray();
        long total = oportunidades.contarPorTexto(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), roles, idCaptacion, idCliente, estado, texto);
        if (total == 0) {
            return new Pagina<>(List.of(), 0);
        }
        List<Long> ids = oportunidades.idsPorTexto(alcance.idOrganizacion(), alcance.global(),
                actor.esAgente(), roles, idCaptacion, idCliente, estado, texto,
                tamano, (pagina - 1) * tamano);
        if (ids.isEmpty()) {
            return new Pagina<>(List.of(), total);
        }
        return new Pagina<>(
                oportunidades.buscarFichaPorIds(alcance.idOrganizacion(), ids).stream()
                        .map(OportunidadServiceImpl::ficha).toList(),
                total);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenOportunidades resumen(Long idCaptacion, Long idCliente, String query, Actor actor) {
        Alcance alcance = alcances.de(actor);
        if (alcance.vacio()) {
            return new ResumenOportunidades(0, 0, 0, 0, 0, 0);
        }
        // El estado viaja nulo: el resumen cuenta los cubos, no filtra por uno.
        // Con texto, cuenta sobre el MISMO conjunto de candidatos que pagina la
        // lista; sin texto, sobre el mismo WHERE. Nunca sobre dos criterios.
        String texto = vacioNull(query);
        if (texto != null) {
            plan.forzarPersonalizado();
        }
        List<ConteoPorEstado> conteos = texto != null
                ? oportunidades.contarPorEstadoConTexto(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRolesArray(), idCaptacion, idCliente, null, texto)
                : oportunidades.contarPorEstado(alcance.idOrganizacion(), alcance.global(),
                        actor.esAgente(), alcance.paramRoles(), idCaptacion, idCliente, null);
        Map<String, Long> porEstado = conteos.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ConteoPorEstado::getEstado, ConteoPorEstado::getTotal));
        long abiertas = porEstado.getOrDefault("A", 0L);
        long conSolicitud = porEstado.getOrDefault("S", 0L);
        long noContinuan = porEstado.getOrDefault("N", 0L);
        long exitosas = porEstado.getOrDefault("F", 0L);
        long noFavorables = porEstado.getOrDefault("X", 0L);
        // El total suma TODOS los cubos devueltos, no solo los cinco conocidos:
        // si algun dia apareciera un estado nuevo, el total seguiria cuadrando
        // con la lista en vez de quedarse corto en silencio.
        long total = porEstado.values().stream().mapToLong(Long::longValue).sum();
        return new ResumenOportunidades(total, abiertas, conSolicitud, noContinuan,
                exitosas, noFavorables);
    }

    @Override
    @Transactional(readOnly = true)
    public FichaOportunidad obtener(long id, Actor actor) {
        return fichaIndividual(cargarConAcceso(id, actor), actor);
    }

    @Override
    @Transactional
    public FichaOportunidad registrar(DatosOportunidad datos, Actor actor) {
        if (datos == null) {
            throw new ReglaNegocioException("Los datos de la oportunidad son obligatorios.");
        }
        if (datos.idCliente() == null || datos.idCliente() <= 0) {
            throw new ReglaNegocioException("Selecciona un cliente interesado.");
        }
        if (datos.idCaptacion() == null || datos.idCaptacion() <= 0) {
            throw new ReglaNegocioException("Selecciona una captacion activa.");
        }
        // Regla del cable: la captacion tiene que estar en la lista del AGENTE
        // que registra (sin alcance de broker). Una captacion inexistente o
        // ajena responde IGUAL —403—, porque la v1 solo consulta esa lista.
        Captacion captacion = captaciones.buscarFicha(actor.idOrganizacion(), datos.idCaptacion())
                .filter(cap -> cap.getAgente() != null && cap.getAgente().getId() == actor.idRolOperativo())
                .orElseThrow(AccesoNoAutorizadoException::new);
        if (!Captacion.ACTIVA.equals(captacion.estadoActual())) {
            throw new ReglaNegocioException("La captacion debe estar ACTIVA.");
        }
        DetalleCliente cliente = clientes.buscarFicha(actor.idOrganizacion(), datos.idCliente())
                .orElseThrow(() -> new NoEncontradoException("Cliente"));
        if (oportunidades.existeAbiertaDe(actor.idOrganizacion(), cliente.getId(), captacion.getId())) {
            throw new ReglaNegocioException("Ya existe una oportunidad abierta para el cliente y captacion.");
        }
        DetalleAgente agente = agentes.findById(actor.idRolOperativo())
                .orElseThrow(() -> new ReglaNegocioException("Agente no encontrado para oportunidad."));

        OportunidadComercial oportunidad = new OportunidadComercial();
        oportunidad.setOrganizacionId(actor.idOrganizacion());
        oportunidad.setCodigoOportunidad(codigo(datos.codigoOportunidad()));
        oportunidad.setCliente(cliente);
        oportunidad.setCaptacion(captacion);
        oportunidad.setAgente(agente);
        oportunidad.setObservaciones(datos.observaciones());
        if (datos.idPublicacionOrigen() != null && datos.idPublicacionOrigen() > 0) {
            oportunidad.setIdPublicacionOrigen(datos.idPublicacionOrigen());
        }
        transiciones.iniciar(oportunidad, OportunidadComercial.ABIERTA);
        return ficha(oportunidades.save(oportunidad));
    }

    @Override
    @Transactional
    public FichaOportunidad noContinuidad(long id, String razon, String observaciones, Actor actor) {
        OportunidadComercial oportunidad = cargarConAcceso(id, actor);
        if (razon == null || razon.isBlank()) {
            throw new ReglaNegocioException("El motivo de no continuidad es obligatorio.");
        }
        String codigoRazon = razon.trim();
        if (!MotivoNoContinuidad.RAZONES.contains(codigoRazon)) {
            throw new ReglaNegocioException("Codigo invalido para MotivoNoContinuidadTipo: " + razon);
        }
        if (!oportunidad.estaAbierta()) {
            throw new ReglaNegocioException("La oportunidad comercial debe estar ABIERTA.");
        }
        DetalleAgente agente = agentes.findById(actor.idRolOperativo())
                .orElseThrow(() -> new ReglaNegocioException("Agente no encontrado para no continuidad."));

        MotivoNoContinuidad motivo = new MotivoNoContinuidad();
        motivo.setOrganizacionId(oportunidad.getOrganizacionId());
        motivo.setOportunidad(oportunidad);
        motivo.setRazonPrincipal(codigoRazon);
        motivo.setObservaciones(observaciones);
        motivo.setAgente(agente);
        motivos.save(motivo);

        // El cable guarda la DESCRIPCION, no el codigo, y esa descripcion sale
        // de la tabla que la guarda (WP-A): aqui vivia una de las cinco copias
        // del mismo vocabulario, y una razon nueva salia como null en el cable.
        String descripcion = MotivoNoContinuidad.rotuloDe(codigoRazon);
        oportunidad.marcarCierre(descripcion);
        transiciones.aplicar(oportunidad, id, OportunidadComercial.NO_CONTINUA, actor, descripcion);
        return ficha(oportunidades.save(oportunidad));
    }

    @Override
    @Transactional(readOnly = true)
    public FichaOportunidad cierreExitoso(long id, Actor actor) {
        // Valida rol (en el controlador) y acceso, y despues SIEMPRE falla:
        // es cable real que el frontend consume.
        cargarConAcceso(id, actor);
        throw new ReglaNegocioException(
                "El cierre exitoso se registra desde la solicitud aprobada para crear el contrato de alquiler.");
    }

    // ------------------------------------------------------------------
    // Alcance por rol: BROKER por CAPTACION (§4), no por agente.
    // ------------------------------------------------------------------

    /**
     * <b>La definicion vive en {@link AccesoOportunidad}</b> desde WP-A, y este
     * metodo la usa. Se conserva el nombre porque es el que la matriz
     * operacion-rol declara como autoridad de estas filas; lo que cambio es que
     * la regla dejo de estar escrita aqui dentro, donde la mesa de negociacion
     * habria tenido que copiarla.
     */
    private OportunidadComercial cargarConAcceso(long id, Actor actor) {
        return acceso.conAcceso(id, actor);
    }

    private static String codigo(String codigoSolicitado) {
        return codigoSolicitado != null && !codigoSolicitado.isBlank()
                ? codigoSolicitado.trim()
                : "OP-" + LocalDateTime.now().format(CODIGO);
    }

    private static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }

    private static String vacioNull(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    /**
     * <b>La ficha INDIVIDUAL, con la mesa dentro</b> (WP-A).
     *
     * <p>Trae la cadena de ofertas y las capacidades del actor sobre ella. Los
     * listados no pasan por aqui, igual que en la ficha del encargo: su pregunta
     * es «que hay», no «que puedo hacer con este», y resolver la mesa por fila
     * serian dos consultas por elemento de la pagina. En el listado los dos
     * campos llegan nulos, y con {@code NON_NULL} no viajan: su ausencia
     * significa «no calculado aqui», no «no hay ofertas» ni «no puedes».
     */
    private FichaOportunidad fichaIndividual(OportunidadComercial o, Actor actor) {
        FichaOportunidad base = ficha(o);
        return new FichaOportunidad(base.id(), base.codigoOportunidad(), base.idCliente(),
                base.clienteNombre(), base.idCaptacion(), base.codigoCaptacion(),
                base.direccionLocal(), base.distritoLocal(), base.idAgente(), base.agenteNombre(),
                base.estado(), base.fechaRegistro(), base.motivoCierre(), base.observaciones(),
                base.fechaCierre(), base.fechaActualizacion(), base.idPublicacionOrigen(),
                ofertas.listar(o.getId(), actor),
                ofertas.capacidades(o.getId(), actor));
    }

    private static FichaOportunidad ficha(OportunidadComercial o) {
        DetalleCliente cliente = o.getCliente();
        Captacion captacion = o.getCaptacion();
        Propiedad propiedad = captacion != null ? captacion.getPropiedad() : null;
        DetalleAgente agente = o.getAgente();
        return new FichaOportunidad(
                o.getId(), o.getCodigoOportunidad(),
                cliente != null ? cliente.getId() : null,
                nombre(cliente != null ? cliente.getRol() : null),
                captacion != null ? captacion.getId() : null,
                captacion != null ? captacion.getCodigoCaptacion() : null,
                propiedad != null ? propiedad.getDireccion() : null,
                propiedad != null ? propiedad.getDistrito() : null,
                agente != null ? agente.getId() : null,
                nombre(agente != null ? agente.getRol() : null),
                o.estadoActual(),
                Fechas.local(o.getFechaRegistro()),
                o.getMotivoCierre(), o.getObservaciones(),
                Fechas.local(o.getFechaCierre()),
                Fechas.local(o.getFechaActualizacion()),
                o.getIdPublicacionOrigen(),
                // Sin mesa ni capacidades: los produce fichaIndividual.
                null, null);
    }

    private static String nombre(PersonaRol rol) {
        return rol == null || rol.getPersona() == null ? null : rol.getPersona().getNombresORazonSocial();
    }
}
