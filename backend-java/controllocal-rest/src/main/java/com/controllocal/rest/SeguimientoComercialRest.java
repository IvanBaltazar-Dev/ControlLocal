package com.controllocal.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.ContratoAlquilerBusinessLogic;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.ProspeccionBusinessLogic;
import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.ContratoAlquilerBusinessLogicImpl;
import com.controllocal.bl.impl.OportunidadComercialBusinessLogicImpl;
import com.controllocal.bl.impl.ProspeccionBusinessLogicImpl;
import com.controllocal.bl.impl.SolicitudAlquilerBusinessLogicImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.ContratoAlquiler;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.Prospeccion;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.enums.EstadoCaptacion;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.usuario.BrokerAgente;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("seguimiento-comercial")
@Produces(MediaType.APPLICATION_JSON)
public class SeguimientoComercialRest {

    private static final String TODOS = "Todos";
    private static final String PROSPECCION = "Prospeccion";
    private static final String CAPTACION = "Captacion";
    private static final String OPORTUNIDAD = "Oportunidad";
    private static final String SOLICITUD = "Solicitud";
    private static final String CIERRE = "Cierre";
    private static final DateTimeFormatter FECHA_LEGIBLE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final ProspeccionBusinessLogic prospecciones = new ProspeccionBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final OportunidadComercialBusinessLogic oportunidades =
            new OportunidadComercialBusinessLogicImpl();
    private final SolicitudAlquilerBusinessLogic solicitudes = new SolicitudAlquilerBusinessLogicImpl();
    private final ContratoAlquilerBusinessLogic contratos = new ContratoAlquilerBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public SeguimientoComercialPageResponse listar(
            @QueryParam("tipo") @DefaultValue(TODOS) String tipo,
            @QueryParam("q") @DefaultValue("") String query,
            @QueryParam("agente") @DefaultValue("") String agente,
            @QueryParam("propietario") @DefaultValue("") String propietario,
            @QueryParam("estado") @DefaultValue("") String estado,
            @QueryParam("distrito") @DefaultValue("") String distrito,
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("8") int tamano,
            @QueryParam("process__eq") String processEq,
            @QueryParam("proceso__eq") String procesoEq,
            @QueryParam("query__contains") String queryContains,
            @QueryParam("q__contains") String qContains,
            @QueryParam("busqueda__contains") String busquedaContains,
            @QueryParam("agent__eq") String agentEq,
            @QueryParam("agente__eq") String agenteEq,
            @QueryParam("owner__eq") String ownerEq,
            @QueryParam("propietario__eq") String propietarioEq,
            @QueryParam("state__eq") String stateEq,
            @QueryParam("estado__eq") String estadoEq,
            @QueryParam("district__eq") String districtEq,
            @QueryParam("distrito__eq") String distritoEq,
            @QueryParam("page") Integer page,
            @QueryParam("page_size") Integer pageSize) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        String filtroProceso = primero(processEq, procesoEq, tipo);
        String filtroBusqueda = primero(queryContains, qContains, busquedaContains, query);
        String filtroAgente = primero(agentEq, agenteEq, agente);
        String filtroPropietario = primero(ownerEq, propietarioEq, propietario);
        String filtroEstado = primero(stateEq, estadoEq, estado);
        String filtroDistrito = primero(districtEq, distritoEq, distrito);

        List<SeguimientoComercialItemResponse> todas = filasPermitidas(usuario);
        List<SeguimientoComercialItemResponse> base = filtrar(
                todas, TODOS, filtroBusqueda, filtroAgente, filtroPropietario, filtroEstado, filtroDistrito);
        List<SeguimientoComercialItemResponse> filtradas = filtrar(
                todas, filtroProceso, filtroBusqueda, filtroAgente, filtroPropietario, filtroEstado, filtroDistrito);

        int paginaValida = SeguridadRest.pagina(page != null ? page : pagina);
        int tamanoValido = Math.min(8, SeguridadRest.tamano(pageSize != null ? pageSize : tamano));
        int desde = Math.min((paginaValida - 1) * tamanoValido, filtradas.size());
        int hasta = Math.min(desde + tamanoValido, filtradas.size());

        return new SeguimientoComercialPageResponse(
                filtradas.subList(desde, hasta),
                filtradas.size(),
                paginaValida,
                tamanoValido,
                conteos(base),
                opcionesFiltro(todas));
    }

    private List<SeguimientoComercialItemResponse> filasPermitidas(UsuarioAutenticado usuario) {
        Set<Long> agentesBroker = agentesSupervisados(usuario);
        Set<Long> captacionesBroker = captacionesPermitidasBroker(usuario).stream()
                .map(Captacion::getIdCaptacion)
                .collect(Collectors.toSet());

        // El propietario solo viene cargado en la captacion; oportunidad/solicitud/cierre cargan el
        // local sin propietario. Se arma un mapa id_local -> propietario desde las captaciones (que
        // si lo traen) para enriquecer esas filas sin consultas extra.
        List<Captacion> todasCaptaciones = captaciones.listarTodos();
        Map<Long, String> propietarioPorLocal = todasCaptaciones.stream()
                .filter(c -> c.getLocalComercial() != null
                        && c.getLocalComercial().getIdLocal() != null
                        && c.getLocalComercial().getIdLocal() > 0)
                .collect(Collectors.toMap(
                        c -> c.getLocalComercial().getIdLocal(),
                        c -> propietario(c.getLocalComercial()),
                        (a, b) -> a));
        Map<Long, Long> propietarioIdPorLocal = todasCaptaciones.stream()
                .filter(c -> c.getLocalComercial() != null
                        && c.getLocalComercial().getIdLocal() != null
                        && c.getLocalComercial().getIdLocal() > 0
                        && c.getLocalComercial().getIdPropietario() != null
                        && c.getLocalComercial().getIdPropietario() > 0)
                .collect(Collectors.toMap(
                        c -> c.getLocalComercial().getIdLocal(),
                        c -> c.getLocalComercial().getIdPropietario(),
                        (a, b) -> a));

        List<SeguimientoComercialItemResponse> filas = new ArrayList<>();
        prospeccionesEnAlcance(usuario, agentesBroker).stream()
                .filter(item -> permitidoPorAgente(item.getAgenteResponsable(), usuario, agentesBroker))
                .map(this::filaProspeccion)
                .forEach(filas::add);
        todasCaptaciones.stream()
                .filter(item -> permitidoPorAgente(item.getAgenteResponsable(), usuario, agentesBroker)
                        || captacionesBroker.contains(item.getIdCaptacion()))
                .map(this::filaCaptacion)
                .forEach(filas::add);
        oportunidadesEnAlcance(usuario, agentesBroker, captacionesBroker).stream()
                .filter(item -> permitidoPorAgente(item.getAgenteResponsable(), usuario, agentesBroker)
                        || captacionPermitida(item.getCaptacion(), captacionesBroker, usuario))
                .map(item -> filaOportunidad(item, propietarioPorLocal, propietarioIdPorLocal))
                .forEach(filas::add);
        solicitudesEnAlcance(usuario, agentesBroker, captacionesBroker).stream()
                .filter(item -> permitidoPorAgente(item.getAgenteResponsable(), usuario, agentesBroker)
                        || captacionPermitida(item.getCaptacion(), captacionesBroker, usuario))
                .map(item -> filaSolicitud(item, propietarioPorLocal, propietarioIdPorLocal))
                .forEach(filas::add);
        contratos.listarTodos().stream()
                .map(contrato -> filaContrato(contrato, usuario, agentesBroker, captacionesBroker, propietarioPorLocal, propietarioIdPorLocal))
                .filter(item -> item != null)
                .forEach(filas::add);

        return filas.stream()
                .sorted(Comparator
                        .comparing(SeguimientoComercialItemResponse::fechaOrden,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
                        .thenComparing(SeguimientoComercialItemResponse::proceso)
                        .thenComparing(SeguimientoComercialItemResponse::codigo))
                .toList();
    }

    private Set<Long> agentesSupervisados(UsuarioAutenticado usuario) {
        if (!"BROKER".equals(usuario.rol())) {
            return Set.of();
        }
        return brokers.listarAgentesSupervisados(usuario.idDominio()).stream()
                .map(BrokerAgente::getIdAgente)
                .collect(Collectors.toSet());
    }

    // Fuentes acotadas en SQL segun el rol (antes se escaneaban tablas completas). El filtro
    // de visibilidad autoritativo se mantiene aguas abajo sobre este superconjunto.
    private List<Prospeccion> prospeccionesEnAlcance(UsuarioAutenticado usuario, Set<Long> agentesBroker) {
        if ("ADMIN".equals(usuario.rol())) {
            return prospecciones.listarTodos();
        }
        if ("AGENTE".equals(usuario.rol())) {
            return prospecciones.listarPorAgentes(List.of(usuario.idDominio()));
        }
        return prospecciones.listarPorAgentes(agentesBroker);
    }

    private List<OportunidadComercial> oportunidadesEnAlcance(
            UsuarioAutenticado usuario, Set<Long> agentesBroker, Set<Long> captacionesBroker) {
        if ("ADMIN".equals(usuario.rol())) {
            return oportunidades.listarTodos();
        }
        if ("AGENTE".equals(usuario.rol())) {
            return oportunidades.listarPorAgentes(List.of(usuario.idDominio()));
        }
        // BROKER: por agente del equipo UNION por captacion supervisada (sin duplicar filas).
        Map<Long, OportunidadComercial> dedup = new LinkedHashMap<>();
        for (OportunidadComercial o : oportunidades.listarPorAgentes(agentesBroker)) {
            dedup.put(o.getIdOportunidad(), o);
        }
        for (OportunidadComercial o : oportunidades.listarPorCaptaciones(captacionesBroker)) {
            dedup.put(o.getIdOportunidad(), o);
        }
        return new ArrayList<>(dedup.values());
    }

    private List<SolicitudAlquiler> solicitudesEnAlcance(
            UsuarioAutenticado usuario, Set<Long> agentesBroker, Set<Long> captacionesBroker) {
        if ("ADMIN".equals(usuario.rol())) {
            return solicitudes.listarTodos();
        }
        if ("AGENTE".equals(usuario.rol())) {
            return solicitudes.listarPorAgentes(List.of(usuario.idDominio()));
        }
        // BROKER: por agente del equipo UNION por captacion supervisada (sin duplicar filas).
        Map<Long, SolicitudAlquiler> dedup = new LinkedHashMap<>();
        for (SolicitudAlquiler s : solicitudes.listarPorAgentes(agentesBroker)) {
            dedup.put(s.getIdSolicitud(), s);
        }
        for (SolicitudAlquiler s : solicitudes.listarPorCaptaciones(captacionesBroker)) {
            dedup.put(s.getIdSolicitud(), s);
        }
        return new ArrayList<>(dedup.values());
    }

    private List<Captacion> captacionesPermitidasBroker(UsuarioAutenticado usuario) {
        return "BROKER".equals(usuario.rol()) ? captaciones.listarPorBroker(usuario.idDominio()) : List.of();
    }

    private boolean permitidoPorAgente(
            com.controllocal.model.usuario.AgenteInmobiliario agente,
            UsuarioAutenticado usuario,
            Set<Long> agentesBroker) {
        if ("ADMIN".equals(usuario.rol())) {
            return true;
        }
        if (agente == null || agente.getIdAgente() == null) {
            return false;
        }
        if ("AGENTE".equals(usuario.rol())) {
            return usuario.idDominio() == agente.getIdAgente();
        }
        if ("BROKER".equals(usuario.rol())) {
            return agentesBroker.contains(agente.getIdAgente());
        }
        return false;
    }

    private boolean captacionPermitida(Captacion captacion, Set<Long> captacionesBroker, UsuarioAutenticado usuario) {
        if ("ADMIN".equals(usuario.rol())) {
            return true;
        }
        return captacion != null
                && captacion.getIdCaptacion() != null
                && captacionesBroker.contains(captacion.getIdCaptacion());
    }

    private SeguimientoComercialItemResponse filaProspeccion(Prospeccion p) {
        LocalComercial local = p.getLocalComercial();
        String captacionCodigo = p.getCaptacion() != null ? p.getCaptacion().getCodigoCaptacion() : null;
        return new SeguimientoComercialItemResponse(
                PROSPECCION,
                texto(p.getCodigoProspeccion(), local != null ? local.getCodigoLocal() : null),
                "-",
                null,
                direccion(local),
                distrito(local),
                agente(p.getAgenteResponsable()),
                propietario(local),
                propietarioId(local),
                estado(p.getEstado()),
                ultimoHitoProspeccion(p, captacionCodigo),
                p.getIdProspeccion() != null ? "prospeccion-detail/" + p.getIdProspeccion() : "",
                "",
                "store",
                "blue",
                fechaOrden(p.getFechaPropuesta(), p.getFechaReunion(), p.getFechaContacto()),
                "");
    }

    private SeguimientoComercialItemResponse filaCaptacion(Captacion c) {
        LocalComercial local = c.getLocalComercial();
        return new SeguimientoComercialItemResponse(
                CAPTACION,
                texto(c.getCodigoCaptacion()),
                "-",
                null,
                direccion(local),
                distrito(local),
                agente(c.getAgenteResponsable()),
                propietario(local),
                propietarioId(local),
                estado(c.getEstado()),
                vigenciaCaptacion(c),
                c.getCodigoCaptacion() != null ? "captacion-detail/" + c.getCodigoCaptacion() : "",
                rutaRevisionCaptacion(c),
                "pin",
                "blue",
                fechaOrden(c.getFechaCaptacion(), c.getFechaInicioVigencia()),
                "");
    }

    private SeguimientoComercialItemResponse filaOportunidad(OportunidadComercial o, Map<Long, String> propietarioPorLocal, Map<Long, Long> propietarioIdPorLocal) {
        Captacion captacion = o.getCaptacion();
        LocalComercial local = captacion != null ? captacion.getLocalComercial() : null;
        return new SeguimientoComercialItemResponse(
                OPORTUNIDAD,
                texto(o.getCodigoOportunidad()),
                cliente(o),
                clienteId(o),
                direccion(local),
                distrito(local),
                agente(o.getAgenteResponsable()),
                propietarioFila(local, propietarioPorLocal),
                propietarioIdFila(local, propietarioIdPorLocal),
                estado(o.getEstado()),
                fechaHora(o.getFechaActualizacion() != null ? o.getFechaActualizacion() : o.getFechaRegistro()),
                o.getIdOportunidad() != null ? "oportunidad-detail/" + o.getIdOportunidad() : "",
                "",
                "target",
                "info",
                o.getFechaActualizacion() != null ? o.getFechaActualizacion() : o.getFechaRegistro(),
                "");
    }

    private SeguimientoComercialItemResponse filaSolicitud(SolicitudAlquiler s, Map<Long, String> propietarioPorLocal, Map<Long, Long> propietarioIdPorLocal) {
        Captacion captacion = s.getCaptacion();
        LocalComercial local = captacion != null ? captacion.getLocalComercial() : null;
        return new SeguimientoComercialItemResponse(
                SOLICITUD,
                texto(s.getCodigoSolicitud()),
                cliente(s),
                clienteId(s),
                direccion(local),
                distrito(local),
                agente(s.getAgenteResponsable()),
                propietarioFila(local, propietarioPorLocal),
                propietarioIdFila(local, propietarioIdPorLocal),
                estado(s.getEstado()),
                fechaHora(s.getFechaActualizacionEstado()),
                s.getCodigoSolicitud() != null ? "solicitud-detail/" + s.getCodigoSolicitud() : "",
                rutaRevisionSolicitud(s),
                "fileText",
                "gray",
                s.getFechaActualizacionEstado() != null ? s.getFechaActualizacionEstado() : fechaOrden(s.getFechaRegistro()),
                s.getMontoPropuesto() != null ? s.getMontoPropuesto().toPlainString() : "");
    }

    private SeguimientoComercialItemResponse filaContrato(
            ContratoAlquiler contrato,
            UsuarioAutenticado usuario,
            Set<Long> agentesBroker,
            Set<Long> captacionesBroker,
            Map<Long, String> propietarioPorLocal,
            Map<Long, Long> propietarioIdPorLocal) {
        Long idSolicitud = contrato.getSolicitudAlquiler() != null
                ? contrato.getSolicitudAlquiler().getIdSolicitud()
                : null;
        SolicitudAlquiler solicitud = idSolicitud != null
                ? solicitudes.buscarPorId(idSolicitud).orElse(null)
                : null;
        if (solicitud == null
                || (!permitidoPorAgente(solicitud.getAgenteResponsable(), usuario, agentesBroker)
                        && !captacionPermitida(solicitud.getCaptacion(), captacionesBroker, usuario))) {
            return null;
        }
        Captacion captacion = solicitud.getCaptacion();
        LocalComercial local = captacion != null ? captacion.getLocalComercial() : null;
        return new SeguimientoComercialItemResponse(
                CIERRE,
                texto(
                        solicitud.getOportunidadComercial() != null
                                ? solicitud.getOportunidadComercial().getCodigoOportunidad()
                                : null,
                        solicitud.getCodigoSolicitud()),
                cliente(solicitud),
                clienteId(solicitud),
                direccion(local),
                distrito(local),
                agente(solicitud.getAgenteResponsable()),
                propietarioFila(local, propietarioPorLocal),
                propietarioIdFila(local, propietarioIdPorLocal),
                contrato.getEstadoContrato() != null ? contrato.getEstadoContrato().getDescripcion() : "Alquilado",
                fecha(contrato.getFechaCierre()),
                solicitud.getCodigoSolicitud() != null ? "solicitud-detail/" + solicitud.getCodigoSolicitud() : "propiedades-alquiladas",
                "",
                "checkCircle",
                "green",
                fechaOrden(contrato.getFechaCierre()),
                solicitud.getMontoPropuesto() != null ? solicitud.getMontoPropuesto().toPlainString() : "");
    }

    private List<SeguimientoComercialItemResponse> filtrar(
            List<SeguimientoComercialItemResponse> filas,
            String tipo,
            String query,
            String agente,
            String propietario,
            String estado,
            String distrito) {
        String tipoNormal = normal(tipo);
        return filas.stream()
                .filter(item -> tipoNormal.isBlank()
                        || normal(TODOS).equals(tipoNormal)
                        || normal(item.proceso()).equals(tipoNormal))
                .filter(item -> contiene(item.agente(), agente))
                .filter(item -> contiene(item.propietario(), propietario))
                .filter(item -> contiene(item.estado(), estado))
                .filter(item -> contiene(item.distrito(), distrito))
                .filter(item -> query == null || query.isBlank()
                        || contiene(item.proceso(), query)
                        || contiene(item.codigo(), query)
                        || contiene(item.cliente(), query)
                        || contiene(item.local(), query)
                        || contiene(item.distrito(), query)
                        || contiene(item.agente(), query)
                        || contiene(item.propietario(), query)
                        || contiene(item.estado(), query))
                .toList();
    }

    private SeguimientoComercialCounts conteos(List<SeguimientoComercialItemResponse> filas) {
        return new SeguimientoComercialCounts(
                filas.size(),
                contar(filas, PROSPECCION),
                contar(filas, CAPTACION),
                contar(filas, OPORTUNIDAD),
                contar(filas, SOLICITUD),
                contar(filas, CIERRE));
    }

    private SeguimientoComercialOptions opcionesFiltro(List<SeguimientoComercialItemResponse> filas) {
        return new SeguimientoComercialOptions(
                opciones(filas.stream().map(SeguimientoComercialItemResponse::agente).toList()),
                opciones(filas.stream().map(SeguimientoComercialItemResponse::propietario).toList()),
                opciones(filas.stream().map(SeguimientoComercialItemResponse::estado).toList()),
                opciones(filas.stream().map(SeguimientoComercialItemResponse::distrito).toList()));
    }

    private static int contar(List<SeguimientoComercialItemResponse> filas, String proceso) {
        return (int) filas.stream().filter(item -> proceso.equals(item.proceso())).count();
    }

    private static List<String> opciones(List<String> valores) {
        return valores.stream()
                .filter(valor -> valor != null && !valor.isBlank() && !"-".equals(valor))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static boolean contiene(String valor, String filtro) {
        return filtro == null || filtro.isBlank() || normal(valor).contains(normal(filtro));
    }

    private static String normal(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private static String primero(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return "";
    }

    private static String texto(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return "-";
    }

    private static String direccion(LocalComercial local) {
        return local != null ? texto(local.getDireccion()) : "-";
    }

    private static String distrito(LocalComercial local) {
        return local != null ? texto(local.getDistrito()) : "-";
    }

    private static String propietario(LocalComercial local) {
        return local != null && local.getPropietario() != null
                ? texto(local.getPropietario().getNombresORazonSocial())
                : "-";
    }

    // Propietario de la fila: usa el cargado en el local (caso captacion) o, si falta porque la
    // oportunidad/solicitud/cierre cargan el local sin propietario, lo resuelve por id_local.
    private static String propietarioFila(LocalComercial local, Map<Long, String> propietarioPorLocal) {
        String cargado = propietario(local);
        if (!"-".equals(cargado)) {
            return cargado;
        }
        if (local != null && local.getIdLocal() != null) {
            String nombre = propietarioPorLocal.get(local.getIdLocal());
            if (nombre != null && !nombre.isBlank() && !"-".equals(nombre)) {
                return nombre;
            }
        }
        return "-";
    }

    private static Long propietarioId(LocalComercial local) {
        return local != null ? local.getIdPropietario() : null;
    }

    // Id del propietario para la fila (habilita el enlace a su ficha): usa el cargado en el local
    // o, si falta (oportunidad/solicitud/cierre cargan el local sin propietario), lo resuelve por id_local.
    private static Long propietarioIdFila(LocalComercial local, Map<Long, Long> propietarioIdPorLocal) {
        Long cargado = propietarioId(local);
        if (cargado != null && cargado > 0) {
            return cargado;
        }
        return local != null && local.getIdLocal() != null
                ? propietarioIdPorLocal.get(local.getIdLocal())
                : null;
    }

    private static String agente(com.controllocal.model.usuario.AgenteInmobiliario agente) {
        return agente != null && agente.getPersona() != null
                ? texto(agente.getPersona().getNombresORazonSocial())
                : "-";
    }

    private static String cliente(OportunidadComercial oportunidad) {
        return oportunidad.getClienteInteresado() != null && oportunidad.getClienteInteresado().getPersona() != null
                ? texto(oportunidad.getClienteInteresado().getPersona().getNombresORazonSocial())
                : "-";
    }

    private static Long clienteId(OportunidadComercial oportunidad) {
        return oportunidad.getClienteInteresado() != null
                ? oportunidad.getClienteInteresado().getIdCliente()
                : null;
    }

    private static String cliente(SolicitudAlquiler solicitud) {
        return solicitud.getClienteInteresado() != null && solicitud.getClienteInteresado().getPersona() != null
                ? texto(solicitud.getClienteInteresado().getPersona().getNombresORazonSocial())
                : "-";
    }

    private static Long clienteId(SolicitudAlquiler solicitud) {
        return solicitud.getClienteInteresado() != null
                ? solicitud.getClienteInteresado().getIdCliente()
                : null;
    }

    private static String rutaRevisionCaptacion(Captacion captacion) {
        return captacion.getEstado() == EstadoCaptacion.PENDIENTE_REVISION
                && captacion.getCodigoCaptacion() != null
                ? "captacion-review/" + captacion.getCodigoCaptacion()
                : "";
    }

    private static String rutaRevisionSolicitud(SolicitudAlquiler solicitud) {
        return solicitud.getEstado() == EstadoSolicitudAlquiler.EN_REVISION
                && solicitud.getCodigoSolicitud() != null
                ? "evaluacion/" + solicitud.getCodigoSolicitud()
                : "";
    }

    private static String ultimoHitoProspeccion(Prospeccion p, String captacionCodigo) {
        if (captacionCodigo != null && !captacionCodigo.isBlank()) {
            return captacionCodigo;
        }
        if (p.getFechaPropuesta() != null) {
            return "Propuesta entregada " + fecha(p.getFechaPropuesta());
        }
        if (p.getFechaReunion() != null) {
            return "Reunion " + fecha(p.getFechaReunion());
        }
        if (p.getFechaContacto() != null) {
            return "Contacto " + fecha(p.getFechaContacto());
        }
        return "Prospecto";
    }

    private static String fechaLegible(LocalDate fecha) {
        return fecha != null ? fecha.format(FECHA_LEGIBLE) : "";
    }

    // Avance de una captacion: hasta cuando esta vigente; si no hay vigencia, cuando se capto.
    private static String vigenciaCaptacion(Captacion c) {
        if (c.getFechaFinVigencia() != null) {
            return "Vigente hasta " + fechaLegible(c.getFechaFinVigencia());
        }
        if (c.getFechaCaptacion() != null) {
            return "Captada el " + fechaLegible(c.getFechaCaptacion());
        }
        return "-";
    }

    private static String fecha(LocalDate fecha) {
        return fecha != null ? fecha.toString() : "";
    }

    private static String fechaHora(LocalDateTime fecha) {
        return fecha != null ? fecha.toString() : "";
    }

    private static LocalDateTime fechaOrden(LocalDate... fechas) {
        for (LocalDate fecha : fechas) {
            if (fecha != null) {
                return fecha.atStartOfDay();
            }
        }
        return null;
    }

    private static String estado(com.controllocal.model.CodigoEnum valor) {
        return valor != null ? valor.getDescripcion() : "-";
    }

    public record SeguimientoComercialPageResponse(
            List<SeguimientoComercialItemResponse> items,
            long totalRecords,
            int page,
            int pageSize,
            SeguimientoComercialCounts counts,
            SeguimientoComercialOptions options) {
    }

    public record SeguimientoComercialCounts(
            int todos,
            int prospeccion,
            int captacion,
            int oportunidad,
            int solicitud,
            int cierre) {
    }

    public record SeguimientoComercialOptions(
            List<String> agentes,
            List<String> propietarios,
            List<String> estados,
            List<String> distritos) {
    }

    public record SeguimientoComercialItemResponse(
            String proceso,
            String codigo,
            String cliente,
            Long clienteId,
            String local,
            String distrito,
            String agente,
            String propietario,
            Long propietarioId,
            String estado,
            String ultimoHito,
            String ruta,
            String rutaRevision,
            String icono,
            String tono,
            LocalDateTime fechaOrden,
            String monto) {
    }
}
