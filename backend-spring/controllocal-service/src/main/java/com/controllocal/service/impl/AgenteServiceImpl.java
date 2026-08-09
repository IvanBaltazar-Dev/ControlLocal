package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ContratoAlquiler;
import com.controllocal.domain.comercial.OportunidadComercial;
import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comun.EstadosDominio;
import com.controllocal.domain.comun.EstadosDominio.EstadoCaptacion;
import com.controllocal.domain.comun.EstadosDominio.EstadoOportunidad;
import com.controllocal.domain.comun.EstadosDominio.EstadoSolicitud;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.domain.persona.CredencialUsuario;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.DetalleBroker;
import com.controllocal.domain.persona.DetalleCliente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.SupervisionAgente;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.ComisionGeneradaPorMoneda;
import com.controllocal.persistence.query.ConteoPorAgente;
import com.controllocal.persistence.query.ConteoPorEstado;
import com.controllocal.persistence.query.MovimientoComisionPorMoneda;
import com.controllocal.persistence.query.RepartoComisionPorMoneda;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.CredencialUsuarioRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.DetalleBrokerRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.PersonaRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.SupervisionAgenteRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.AgenteService;
import com.controllocal.service.Pagina;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.UsuariosInternos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class AgenteServiceImpl implements AgenteService {

    private final DetalleAgenteRepository agentes;
    private final DetalleBrokerRepository brokers;
    private final SupervisionAgenteRepository supervisiones;
    private final CredencialUsuarioRepository credenciales;
    private final PersonaRepository personas;
    private final CaptacionRepository captaciones;
    private final OportunidadComercialRepository oportunidades;
    private final SolicitudAlquilerRepository solicitudes;
    private final ContratoAlquilerRepository contratos;
    private final UsuariosInternos usuarios;

    /** Tope de la lista corta de cierres en la ficha; el resto va en Cierres. */
    private static final int ULTIMOS_CIERRES = 8;

    public AgenteServiceImpl(DetalleAgenteRepository agentes,
                             DetalleBrokerRepository brokers,
                             SupervisionAgenteRepository supervisiones,
                             CredencialUsuarioRepository credenciales,
                             PersonaRepository personas,
                             CaptacionRepository captaciones,
                             OportunidadComercialRepository oportunidades,
                             SolicitudAlquilerRepository solicitudes,
                             ContratoAlquilerRepository contratos,
                             UsuariosInternos usuarios) {
        this.agentes = agentes;
        this.brokers = brokers;
        this.supervisiones = supervisiones;
        this.credenciales = credenciales;
        this.personas = personas;
        this.captaciones = captaciones;
        this.oportunidades = oportunidades;
        this.solicitudes = solicitudes;
        this.contratos = contratos;
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaAgente> listar(int pagina, int tamano, Actor actor) {
        return listar(new FiltrosAgente(null, null, null, null, pagina, tamano), actor);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<FichaAgente> listar(FiltrosAgente filtros, Actor actor) {
        exigirBrokerOAdmin(actor);
        validarActorOperativo(actor);
        int paginaValida = Math.max(1, filtros.pagina());
        int tamanoValido = tamano(filtros.tamano());
        Page<DetalleAgente> page = agentes.buscar(actor.idOrganizacion(), actor.esTenantAdmin(),
                actor.idRolOperativo(), limpiar(filtros.texto()), codigo(filtros.estado()),
                codigo(filtros.estadoOperativo()), limpiar(filtros.zona()),
                PageRequest.of(paginaValida - 1, tamanoValido));
        return new Pagina<>(fichas(page.getContent(), actor.idOrganizacion()),
                page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenAgentes resumen(FiltrosAgente filtros, Actor actor) {
        exigirBrokerOAdmin(actor);
        validarActorOperativo(actor);
        long org = actor.idOrganizacion();
        boolean sinScope = actor.esTenantAdmin();
        long broker = actor.idRolOperativo();
        String texto = limpiar(filtros.texto());
        String estado = codigo(filtros.estado());
        String operativo = codigo(filtros.estadoOperativo());
        String zona = limpiar(filtros.zona());

        long total = agentes.contarFiltrados(org, sinScope, broker, texto, estado, operativo, zona);
        Map<String, Long> administrativos = porEstado(agentes.contarPorEstadoAdministrativo(
                org, sinScope, broker, texto, estado, operativo, zona));
        Map<String, Long> operativos = porEstado(agentes.contarPorEstadoOperativo(
                org, sinScope, broker, texto, estado, operativo, zona));
        return new ResumenAgentes(total,
                administrativos.getOrDefault("A", 0L), administrativos.getOrDefault("I", 0L),
                operativos.getOrDefault("D", 0L), operativos.getOrDefault("O", 0L),
                operativos.getOrDefault("V", 0L), operativos.getOrDefault("S", 0L),
                // Las zonas recorren el ALCANCE completo, no el conjunto filtrado:
                // son las opciones que se ofrecen, igual que en el resto de bandejas.
                agentes.zonasDisponibles(org, sinScope, broker));
    }

    private static Map<String, Long> porEstado(List<ConteoPorEstado> filas) {
        Map<String, Long> resultado = new HashMap<>();
        for (ConteoPorEstado fila : filas) {
            if (fila.getEstado() != null) {
                resultado.merge(fila.getEstado(), fila.getTotal(), Long::sum);
            }
        }
        return resultado;
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Un código de una letra, en mayúscula. Vacío = sin filtro. */
    private static String codigo(String valor) {
        String limpio = limpiar(valor);
        return limpio == null ? null : limpio.toUpperCase(Locale.ROOT);
    }

    /**
     * Ficha individual del agente. Es una operación ADITIVA (la v1 no tenía
     * {@code GET /agentes/{id}}) y responde de una sola vez lo que antes habría
     * que reconstruir combinando páginas de cuatro bandejas distintas —lo que
     * además daría números falsos, porque cada listado pagina—.
     *
     * <p><b>Los cierres y el dinero salen de la atribución histórica de V27</b>,
     * no de la cadena solicitud→agente: un agente que cambió de equipo conserva
     * sus cierres, que es justo lo que una ficha de persona tiene que mostrar.
     *
     * <p>El alcance se comprueba <b>aquí y una sola vez</b>: el BROKER llega a
     * los agentes que supervisa hoy y el ADMIN a todos. Las consultas de abajo
     * no vuelven a filtrar por rol; si lo hicieran, la ficha mostraría números
     * distintos de los que el propio agente ve en sus bandejas.
     */
    @Override
    @Transactional(readOnly = true)
    public FichaCompletaAgente ficha(long id, Actor actor) {
        exigirBrokerOAdmin(actor);
        validarActorOperativo(actor);
        long org = actor.idOrganizacion();
        DetalleAgente agente = agentes.buscarFicha(org, id)
                .orElseThrow(() -> new NoEncontradoException("Agente"));

        Optional<SupervisionAgente> supervision = supervisiones.buscarActivaPorAgente(org, id);
        if (!actor.esTenantAdmin()
                && supervision.filter(s -> s.getIdRolBroker().equals(actor.idRolOperativo()))
                        .isEmpty()) {
            throw new AccesoNoAutorizadoException();
        }

        CredencialUsuario credencial = usuarios.credencial(
                org, agente.getRol().getPersona().getId());
        int caps = conteosCaptacion(List.of(id), org).getOrDefault(id, 0);
        int ops = conteosOportunidad(List.of(id), org).getOrDefault(id, 0);

        return new FichaCompletaAgente(
                ficha(agente, credencial, caps, ops),
                supervision.map(this::supervisionVigente).orElse(null),
                conteos(captaciones.contarPorEstadoDeAgente(org, id), EstadoCaptacion::desde),
                conteos(oportunidades.contarPorEstadoDeAgente(org, id), EstadoOportunidad::desde),
                conteos(solicitudes.contarPorEstadoDeAgente(org, id), EstadoSolicitud::desde),
                contratos.contarCierresDeAgente(org, id),
                comisionesDe(org, id),
                ultimosCierres(org, id));
    }

    private SupervisionVigente supervisionVigente(SupervisionAgente supervision) {
        DetalleBroker broker = brokers
                .buscarFicha(supervision.getOrganizacionId(), supervision.getIdRolBroker())
                .orElse(null);
        return new SupervisionVigente(supervision.getIdRolBroker(),
                broker != null ? broker.getRol().getPersona().getNombresORazonSocial() : null,
                broker != null ? broker.getCodigoBroker() : null,
                supervision.getFechaAsignacion(), supervision.getMotivo());
    }

    /**
     * Traduce los pares (código, total) del {@code group by} a códigos con su
     * descripción. Un código que el enum no reconozca se muestra tal cual en
     * vez de romper la ficha: es un dato, no una regla.
     */
    private static List<ConteoEstado> conteos(List<ConteoPorEstado> filas,
                                              java.util.function.Function<String, ?> traductor) {
        return filas.stream()
                .filter(f -> f.getEstado() != null)
                .map(f -> new ConteoEstado(f.getEstado(), descripcion(f.getEstado(), traductor),
                        f.getTotal()))
                .sorted(java.util.Comparator.comparing(ConteoEstado::estado))
                .toList();
    }

    private static String descripcion(String codigo,
                                      java.util.function.Function<String, ?> traductor) {
        try {
            Object estado = traductor.apply(codigo);
            return estado instanceof EstadosDominio.Codigo c ? c.descripcion() : codigo;
        } catch (RuntimeException noReconocido) {
            return codigo;
        }
    }

    /**
     * Las cuatro magnitudes del dinero, por moneda y sin mezclarlas. Los dos
     * saldos son diferencias derivadas y nunca negativas: un cobro de más no se
     * publica como "pendiente negativo".
     */
    private ComisionesAgente comisionesDe(long org, long idAgente) {
        Map<String, BigDecimal> generada = porMoneda(
                contratos.comisionesGeneradasDeAgente(org, idAgente),
                ComisionGeneradaPorMoneda::getMoneda, ComisionGeneradaPorMoneda::getMonto);
        Map<String, BigDecimal> asignada = porMoneda(
                contratos.repartosDeAgente(org, idAgente),
                RepartoComisionPorMoneda::getMoneda, RepartoComisionPorMoneda::getParteAgente);
        List<MovimientoComisionPorMoneda> evidencia = contratos.movimientosDeAgente(org, idAgente);
        Map<String, BigDecimal> cobrada = porMoneda(evidencia,
                MovimientoComisionPorMoneda::getMoneda,
                m -> maximoCero(m.getMontoCobrado()));
        Map<String, BigDecimal> pagada = porMoneda(evidencia,
                MovimientoComisionPorMoneda::getMoneda,
                m -> maximoCero(m.getMontoPagadoAgente()));
        return new ComisionesAgente(
                importes(generada), importes(cobrada), diferencia(generada, cobrada),
                importes(asignada), importes(pagada), diferencia(asignada, pagada));
    }

    private List<CierreDeAgente> ultimosCierres(long org, long idAgente) {
        return contratos.cierresDeAgente(org, idAgente,
                        PageRequest.of(0, ULTIMOS_CIERRES,
                                Sort.by(Sort.Direction.DESC, "fechaCierre", "id")))
                .getContent().stream()
                .map(AgenteServiceImpl::cierre)
                .toList();
    }

    private static CierreDeAgente cierre(ContratoAlquiler c) {
        SolicitudAlquiler solicitud = c.getSolicitud();
        OportunidadComercial oportunidad = c.getOportunidad();
        DetalleCliente cliente = oportunidad != null ? oportunidad.getCliente() : null;
        Captacion captacion = oportunidad != null ? oportunidad.getCaptacion() : null;
        Propiedad propiedad = captacion != null ? captacion.getPropiedad() : null;
        return new CierreDeAgente(c.getId(),
                solicitud != null ? solicitud.getCodigoSolicitud() : null,
                propiedad != null ? propiedad.getDireccion() : null,
                propiedad != null ? propiedad.getDistrito() : null,
                cliente != null && cliente.getRol() != null
                        && cliente.getRol().getPersona() != null
                        ? cliente.getRol().getPersona().getNombresORazonSocial() : null,
                c.getFechaCierre(), c.estadoActual(),
                c.getRentaContractual(), c.getMoneda());
    }

    private static <T> Map<String, BigDecimal> porMoneda(
            List<T> filas, java.util.function.Function<T, String> moneda,
            java.util.function.Function<T, BigDecimal> monto) {
        Map<String, BigDecimal> resultado = new HashMap<>();
        for (T fila : filas) {
            String clave = moneda.apply(fila);
            if (clave != null) {
                resultado.merge(clave, cero(monto.apply(fila)), BigDecimal::add);
            }
        }
        return resultado;
    }

    private static List<ImportePorMoneda> importes(Map<String, BigDecimal> valores) {
        return valores.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new ImportePorMoneda(e.getKey(), e.getValue()))
                .toList();
    }

    private static List<ImportePorMoneda> diferencia(Map<String, BigDecimal> total,
                                                      Map<String, BigDecimal> hecho) {
        return total.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new ImportePorMoneda(e.getKey(),
                        cero(e.getValue()).subtract(cero(hecho.get(e.getKey())))
                                .max(BigDecimal.ZERO)))
                .toList();
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static BigDecimal maximoCero(BigDecimal valor) {
        return cero(valor).max(BigDecimal.ZERO);
    }

    @Override
    @Transactional
    public FichaAgente registrar(DatosAgente datos, Actor actor) {
        exigirGobierno(actor);
        if (datos == null || UsuariosInternos.vacio(datos.nombre())
                || UsuariosInternos.vacio(datos.usuario())
                || UsuariosInternos.vacio(datos.contrasena())) {
            throw new ReglaNegocioException(
                    "Nombre, usuario y contrasena del agente son obligatorios.");
        }

        String tipoPersona = UsuariosInternos.tipoPersonaO(datos.tipoPersona(), "N");
        String tipoDocumento = UsuariosInternos.tipoDocumentoO(datos.tipoDocumento(), "D");
        String estado = UsuariosInternos.estadoAdministrativoO(datos.estado(), "A");
        String estadoOperativo =
                UsuariosInternos.estadoOperativoO(datos.estadoOperativo(), "D");

        // Fila 17 de D-S0-17, invertida respecto a como estaba. Antes el
        // supervisor era el broker de la sesion y el administrador quedaba
        // EXPRESAMENTE excluido con una excepcion; ahora el alta es gobierno
        // del tenant (D-S0-18: un broker no crea cuentas) y el equipo de
        // destino viaja en la peticion, porque quien gobierna no supervisa a
        // nadie de quien deducirlo.
        //
        // Se valida DESPUES de los codigos del cuerpo para no adelantarse a los
        // mensajes de validacion del cable v1: el campo es nuestro, y sus
        // errores no deberian tapar los suyos.
        if (datos.idBrokerSupervisor() == null) {
            throw new ReglaNegocioException(
                    "Debe indicar el broker que supervisara al agente.");
        }
        DetalleBroker supervisor = validarBrokerActivo(
                datos.idBrokerSupervisor(), actor.idOrganizacion());

        String codigo = UsuariosInternos.vacio(datos.codigoAgente())
                ? String.format("AGE-%03d",
                        agentes.countByOrganizacionId(actor.idOrganizacion()) + 1)
                : datos.codigoAgente().trim();

        UsuariosInternos.Alta alta = usuarios.registrar(actor.idOrganizacion(),
                TipoRol.AGENTE, tipoPersona, tipoDocumento, datos.numeroDocumento(),
                datos.nombre(), datos.telefono(), datos.correo(), datos.usuario(),
                datos.contrasena(), estado);

        DetalleAgente agente = new DetalleAgente();
        agente.setOrganizacionId(actor.idOrganizacion());
        agente.setRol(alta.rolOperativo());
        agente.setCodigoAgente(codigo);
        agente.setZonaAsignada(datos.zona());
        agente.setFechaIngreso(LocalDate.now());
        agente.setEstadoOperativo(estadoOperativo);
        agentes.save(agente);

        SupervisionAgente supervision = new SupervisionAgente();
        supervision.setOrganizacionId(actor.idOrganizacion());
        supervision.setIdRolBroker(supervisor.getId());
        supervision.setIdRolAgente(alta.rolOperativo().getId());
        supervision.setFechaAsignacion(LocalDate.now());
        supervision.setMotivo("Asignacion inicial por registro de agente.");
        supervisiones.save(supervision);
        return ficha(agente, alta.credencial(), 0, 0);
    }

    @Override
    @Transactional
    public FichaAgente actualizar(long id, DatosAgente datos, Actor actor) {
        // Fila 18: editar la ficha administrativa de un agente es gobierno. El
        // filtro de supervision que habia aqui protegia al BROKER de tocar
        // agentes ajenos y ya no aplica — el broker no llega a esta operacion,
        // y el TENANT_ADMIN alcanza a todo su tenant por definicion.
        exigirGobierno(actor);
        DetalleAgente agente = agentes.buscarFicha(actor.idOrganizacion(), id)
                .orElseThrow(() -> new NoEncontradoException("Agente"));

        CredencialUsuario credencial = usuarios.credencial(actor.idOrganizacion(),
                agente.getRol().getPersona().getId());
        if (datos != null) {
            Persona persona = agente.getRol().getPersona();
            if (!UsuariosInternos.vacio(datos.nombre())) {
                persona.setNombresORazonSocial(datos.nombre());
            }
            if (datos.telefono() != null) {
                persona.setTelefono(datos.telefono());
            }
            if (datos.correo() != null) {
                persona.setCorreo(datos.correo());
            }
            if (!UsuariosInternos.vacio(datos.estado())) {
                credencial.setEstadoAdministrativo(
                        UsuariosInternos.estadoAdministrativoO(datos.estado(), "A"));
                credenciales.save(credencial);
            }
            if (!UsuariosInternos.vacio(datos.estadoOperativo())) {
                agente.setEstadoOperativo(
                        UsuariosInternos.estadoOperativoO(datos.estadoOperativo(), "D"));
            }
            if (datos.zona() != null) {
                agente.setZonaAsignada(datos.zona());
            }
            personas.save(persona);
            agentes.save(agente);
        }
        return ficha(agente, credencial, 0, 0);
    }

    private List<FichaAgente> fichas(List<DetalleAgente> filas, long idOrganizacion) {
        List<Long> idsPersona = filas.stream()
                .map(a -> a.getRol().getPersona().getId())
                .filter(Objects::nonNull)
                .toList();
        Map<Long, CredencialUsuario> usuariosPorPersona =
                usuarios.credencialesPorPersona(idOrganizacion, idsPersona);
        List<Long> idsAgente = filas.stream().map(DetalleAgente::getId).toList();
        Map<Long, Integer> caps = conteosCaptacion(idsAgente, idOrganizacion);
        Map<Long, Integer> ops = conteosOportunidad(idsAgente, idOrganizacion);
        return filas.stream()
                .map(a -> ficha(a,
                        usuariosPorPersona.get(a.getRol().getPersona().getId()),
                        caps.getOrDefault(a.getId(), 0),
                        ops.getOrDefault(a.getId(), 0)))
                .toList();
    }

    private Map<Long, Integer> conteosCaptacion(
            Collection<Long> ids, long idOrganizacion) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapaConteo(
                captaciones.contarEnCarteraPorAgentes(idOrganizacion, ids));
    }

    private Map<Long, Integer> conteosOportunidad(
            Collection<Long> ids, long idOrganizacion) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapaConteo(
                oportunidades.contarActivasPorAgentes(idOrganizacion, ids));
    }

    private static Map<Long, Integer> mapaConteo(List<ConteoPorAgente> filas) {
        Map<Long, Integer> resultado = new HashMap<>();
        for (ConteoPorAgente fila : filas) {
            resultado.put(fila.getIdAgente(), Math.toIntExact(fila.getTotal()));
        }
        return resultado;
    }

    private DetalleBroker validarBrokerActivo(long id, long idOrganizacion) {
        DetalleBroker broker = brokers.buscarFicha(idOrganizacion, id)
                .orElseThrow(() -> new ReglaNegocioException("Broker no encontrado."));
        CredencialUsuario credencial = usuarios.credencial(
                idOrganizacion, broker.getRol().getPersona().getId());
        if (!"A".equals(credencial.getEstadoAdministrativo())) {
            throw new ReglaNegocioException("El broker no esta activo.");
        }
        return broker;
    }

    /** Lectura del catalogo (filas 14-16): supervision del broker o del admin. */
    private static void exigirBrokerOAdmin(Actor actor) {
        if (actor.esAgente()) {
            throw new AccesoNoAutorizadoException();
        }
    }

    /**
     * Alta y edicion de agentes (filas 17-18): <b>solo gobierno</b>. Duplica en
     * el service el gate de {@code @PreAuthorize} a proposito — es la regla de
     * negocio, no una comprobacion de transporte, y un caso de uso no deberia
     * depender de que nadie olvide la anotacion.
     */
    private static void exigirGobierno(Actor actor) {
        if (!actor.esTenantAdmin()) {
            throw new AccesoNoAutorizadoException();
        }
    }

    /**
     * El BROKER tiene que estar activo para operar sobre su equipo. El
     * TENANT_ADMIN no tiene equipo <b>ni {@code detalle_broker}</b>: su
     * {@code idRolOperativo} es el rol de gobierno, y buscarlo entre los
     * brokers fallaria con "Broker no encontrado".
     */
    private void validarActorOperativo(Actor actor) {
        if (actor.esBroker()) {
            validarBrokerActivo(actor.idRolOperativo(), actor.idOrganizacion());
        }
    }

    private static FichaAgente ficha(DetalleAgente agente,
                                     CredencialUsuario credencial,
                                     int captacionesActivas,
                                     int operacionesActivas) {
        Persona persona = agente.getRol().getPersona();
        return new FichaAgente(agente.getId(), agente.getCodigoAgente(),
                persona.getNombresORazonSocial(), persona.getTipoPersona(),
                persona.getTipoDocumento(), persona.getNumeroDocumento(),
                persona.getTelefono(), persona.getCorreo(),
                credencial != null ? credencial.getNombreUsuario() : null,
                agente.getZonaAsignada(), agente.getFechaIngreso(),
                credencial != null ? credencial.getEstadoAdministrativo() : null,
                agente.getEstadoOperativo(), captacionesActivas, operacionesActivas);
    }

    private static int tamano(int valor) {
        return Math.max(1, Math.min(100, valor));
    }
}
