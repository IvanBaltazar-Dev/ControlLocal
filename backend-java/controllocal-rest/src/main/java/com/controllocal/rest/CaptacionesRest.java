package com.controllocal.rest;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.LocalComercialBusinessLogic;
import com.controllocal.bl.PrecioLocalBusinessLogic;
import com.controllocal.bl.PropietarioBusinessLogic;
import com.controllocal.bl.ReportePropietarioBusinessLogic;
import com.controllocal.bl.impl.BrokerBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.LocalComercialBusinessLogicImpl;
import com.controllocal.bl.impl.PrecioLocalBusinessLogicImpl;
import com.controllocal.bl.impl.PropietarioBusinessLogicImpl;
import com.controllocal.bl.impl.ReportePropietarioBusinessLogicImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.ReportePropietario;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.persona.Propietario;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.rest.dto.Dtos;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.http.PageResponse;
import com.controllocal.rest.reports.CaptacionJasperMapper;
import com.controllocal.rest.reports.JasperPdfService;
import com.controllocal.rest.seguridad.RateLimiter;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("captaciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CaptacionesRest {

    private static final RateLimiter LIMITADOR_SENSIBLE = new RateLimiter(30);

    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final BrokerBusinessLogic brokers = new BrokerBusinessLogicImpl();
    private final LocalComercialBusinessLogic locales = new LocalComercialBusinessLogicImpl();
    private final PrecioLocalBusinessLogic precios = new PrecioLocalBusinessLogicImpl();
    private final PropietarioBusinessLogic propietarios = new PropietarioBusinessLogicImpl();
    private final ReportePropietarioBusinessLogic reportes = new ReportePropietarioBusinessLogicImpl();
    private final CoincidenciaCarteraSupport coincidencias = new CoincidenciaCarteraSupport();
    private final JasperPdfService jasper = new JasperPdfService();

    @Context
    private HttpServletRequest request;

    @GET
    public PageResponse<Dtos.CaptacionResponse> listar(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        List<Captacion> fuente = captacionesDelUsuario(usuario);
        return pagina(fuente, pagina, tamano);
    }

    @GET
    @Path("pendientes")
    public PageResponse<Dtos.CaptacionResponse> pendientes(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        List<Captacion> fuente = "ADMIN".equals(usuario.rol())
                ? captaciones.listPendingReviews()
                : captaciones.listPendingReviews(usuario.idDominio());
        return pagina(fuente, pagina, tamano);
    }

    @GET
    @Path("reasignables")
    public PageResponse<Dtos.CaptacionResponse> reasignables(
            @QueryParam("pagina") @DefaultValue("1") int pagina,
            @QueryParam("tamano") @DefaultValue("10") int tamano,
            @QueryParam("q") String query) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Captacion> fuente = captacionesDelUsuario(usuario).stream()
                .filter(c -> c.getEstado() == com.controllocal.model.comercial.enums.EstadoCaptacion.ACTIVA)
                .filter(c -> q.isBlank() || coincideReasignable(c, q))
                .toList();
        return pagina(fuente, pagina, tamano);
    }

    @GET
    @Path("{id}")
    public Dtos.CaptacionResponse obtener(@PathParam("id") long id) {
        return respuesta(obtenerConAcceso(id, SeguridadRest.usuario(request)));
    }

    @GET
    @Path("codigo/{codigo}")
    public Dtos.CaptacionResponse obtenerPorCodigo(@PathParam("codigo") String codigo) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        Captacion captacion = captaciones.buscarPorCodigo(codigo)
                .orElseThrow(() -> ApiException.noEncontrado("Captacion"));
        if (!puedeVer(usuario, captacion)) {
            throw ApiException.prohibido();
        }
        return respuesta(captacion);
    }

    @GET
    @Path("{codigo}/contrato-exclusividad/pdf")
    @Produces("application/pdf")
    public Response contratoExclusividadPdf(@PathParam("codigo") String codigo) {
        Captacion captacion = obtenerPorCodigoConAcceso(codigo, SeguridadRest.usuario(request));
        byte[] pdf = jasper.generarPdf(
                "contrato_exclusividad.jasper",
                Map.of(),
                List.of(CaptacionJasperMapper.contrato(captacion)));
        return pdf("Contrato_exclusividad_" + captacion.getCodigoCaptacion() + ".pdf", pdf);
    }

    @GET
    @Path("{codigo}/ficha-captacion/pdf")
    @Produces("application/pdf")
    public Response fichaCaptacionPdf(@PathParam("codigo") String codigo) {
        Captacion captacion = obtenerPorCodigoConAcceso(codigo, SeguridadRest.usuario(request));
        byte[] pdf = jasper.generarPdf(
                "ficha_captacion.jasper",
                Map.of(),
                List.of(CaptacionJasperMapper.ficha(captacion)));
        return pdf("Ficha_captacion_" + captacion.getCodigoCaptacion() + ".pdf", pdf);
    }

    @GET
    @Path("{codigo}/ficha-propiedad/pdf")
    @Produces("application/pdf")
    public Response fichaPropiedadPdf(@PathParam("codigo") String codigo) {
        Captacion captacion = obtenerPorCodigoConAcceso(codigo, SeguridadRest.usuario(request));
        LocalComercial local = captacion.getLocalComercial();
        Propietario propietario = propietarioCompleto(local);
        long idLocal = local != null && local.getIdLocal() != null ? local.getIdLocal() : 0;
        int cantidadFotos = idLocal > 0 ? locales.listarFotos(idLocal).size() : 0;
        byte[] pdf = jasper.generarPdf(
                "ficha_propiedad.jasper",
                Map.of(),
                List.of(CaptacionJasperMapper.fichaPropiedad(
                        captacion,
                        propietario,
                        idLocal > 0 ? precios.listarPorLocal(idLocal) : List.of(),
                        cantidadFotos)));
        return pdf("Ficha_propiedad_" + captacion.getCodigoCaptacion() + ".pdf", pdf);
    }

    @GET
    @Path("{codigo}/reportes-propietario/{idReporte}/pdf")
    @Produces("application/pdf")
    public Response reportePropietarioPdf(
            @PathParam("codigo") String codigo,
            @PathParam("idReporte") long idReporte) {
        Captacion captacion = obtenerPorCodigoConAcceso(codigo, SeguridadRest.usuario(request));
        ReportePropietario reporte = reportes.listarPorCaptacion(captacion.getIdCaptacion()).stream()
                .filter(item -> item.getIdReportePropietario() != null
                        && item.getIdReportePropietario() == idReporte)
                .findFirst()
                .orElseThrow(() -> ApiException.noEncontrado("Reporte propietario"));
        byte[] pdf = jasper.generarPdf(
                "reporte_propietario.jasper",
                Map.of(),
                List.of(CaptacionJasperMapper.reporte(captacion, reporte)));
        String sufijo = reporte.getFechaReporte() != null
                ? DateTimeFormatter.BASIC_ISO_DATE.format(reporte.getFechaReporte())
                : String.valueOf(idReporte);
        return pdf("Reporte_propietario_" + captacion.getCodigoCaptacion() + "_" + sufijo + ".pdf", pdf);
    }

    @GET
    @Path("{idOrCodigo}/coincidencias")
    public CoincidenciaCarteraSupport.CoincidenciasResponse coincidencias(
            @PathParam("idOrCodigo") String idOrCodigo,
            @QueryParam("page") Integer page,
            @QueryParam("pagina") Integer pagina,
            @QueryParam("page_size") Integer pageSize,
            @QueryParam("tamano") Integer tamano) {
        return coincidencias.clientesParaCaptacion(
                idOrCodigo,
                SeguridadRest.usuario(request),
                page != null ? page : pagina != null ? pagina : 1,
                pageSize != null ? pageSize : tamano != null ? tamano : 6);
    }

    @POST
    public Response registrar(Dtos.CaptacionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null) {
            throw ApiException.badRequest("Los datos de la captacion son obligatorios.");
        }

        LocalComercial local = new LocalComercial();
        local.setIdLocal(dto.idLocal());
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(usuario.idDominio());

        Captacion captacion = new Captacion();
        captacion.setCodigoCaptacion(dto.codigoCaptacion());
        captacion.setFechaCaptacion(dto.fechaCaptacion());
        captacion.setFechaInicioVigencia(dto.fechaInicioVigencia());
        captacion.setFechaFinVigencia(dto.fechaFinVigencia());
        captacion.setComisionPactada(dto.comisionPactada());
        captacion.setObservaciones(dto.observaciones());
        captacion.setMotivoOperacion(operacionAlquiler(dto.motivoOperacion()));
        captacion.setUrgencia(dto.urgencia());
        captacion.setExclusividad(dto.exclusividad());
        captacion.setLocalComercial(local);
        captacion.setAgenteResponsable(agente);
        long id = captaciones.registrar(captacion);

        return Response.status(Response.Status.CREATED)
                .entity(respuesta(captaciones.buscarPorId(id).orElseThrow(() -> ApiException.noEncontrado("Captacion"))))
                .build();
    }

    @PUT
    @Path("{id}")
    public Dtos.CaptacionResponse actualizar(@PathParam("id") long id, Dtos.CaptacionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        if (dto == null) {
            throw ApiException.badRequest("Los datos de la captacion son obligatorios.");
        }

        Captacion captacion = obtenerConAcceso(id, usuario);
        if (captacion.getEstado() != com.controllocal.model.comercial.enums.EstadoCaptacion.PENDIENTE_REVISION
                && captacion.getEstado() != com.controllocal.model.comercial.enums.EstadoCaptacion.OBSERVADA) {
            throw ApiException.badRequest("Solo se puede editar una captacion pendiente u observada.");
        }

        captacion.setFechaCaptacion(dto.fechaCaptacion());
        captacion.setFechaInicioVigencia(dto.fechaInicioVigencia());
        captacion.setFechaFinVigencia(dto.fechaFinVigencia());
        captacion.setComisionPactada(dto.comisionPactada());
        captacion.setObservaciones(dto.observaciones());
        captacion.setMotivoOperacion(operacionAlquiler(dto.motivoOperacion()));
        captacion.setUrgencia(dto.urgencia());
        captacion.setExclusividad(dto.exclusividad());
        // Reenviar a revision: una captacion OBSERVADA que el agente edita y guarda vuelve
        // a la cola de revision del broker (PENDIENTE_REVISION). Sin esto se quedaba
        // OBSERVADA y el broker nunca la volvia a ver.
        if (captacion.getEstado() == com.controllocal.model.comercial.enums.EstadoCaptacion.OBSERVADA) {
            captacion.setEstado(com.controllocal.model.comercial.enums.EstadoCaptacion.PENDIENTE_REVISION);
        }
        captaciones.actualizar(captacion);

        return respuesta(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/decision")
    public Dtos.CaptacionResponse decidir(@PathParam("id") long id, Dtos.DecisionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        limitar();
        obtenerConAcceso(id, usuario);
        if (dto == null || dto.accion() == null) {
            throw ApiException.badRequest("La decision es obligatoria.");
        }

        String accion = dto.accion().trim().toUpperCase(Locale.ROOT);
        switch (accion) {
            case "APROBAR", "A" -> captaciones.aprobarCaptacion(id, usuario.idDominio(), dto.observacion());
            case "OBSERVAR", "O" -> captaciones.observarCaptacion(id, usuario.idDominio(), dto.observacion());
            case "RECHAZAR", "R" -> captaciones.rechazarCaptacion(id, usuario.idDominio(), dto.observacion());
            default -> throw ApiException.badRequest("Decision no valida.");
        }
        return respuesta(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/reasignar")
    public Dtos.CaptacionResponse reasignar(@PathParam("id") long id, Dtos.ReasignacionRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        limitar();
        obtenerConAcceso(id, usuario);
        if (dto == null || dto.idAgenteNuevo() == null) {
            throw ApiException.badRequest("El agente destino es obligatorio.");
        }
        captaciones.reasignarCaptacion(id, dto.idAgenteNuevo(), usuario.idDominio(), dto.motivo());
        return respuesta(obtenerConAcceso(id, usuario));
    }

    @POST
    @Path("{id}/cierre")
    public Dtos.CaptacionResponse cerrar(@PathParam("id") long id, Dtos.CierreRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "BROKER", "ADMIN");
        limitar();
        obtenerConAcceso(id, usuario);
        if (dto == null) {
            throw ApiException.badRequest("El motivo de cierre es obligatorio.");
        }
        captaciones.cerrarCaptacion(id, usuario.idDominio(), dto.motivo());
        return respuesta(obtenerConAcceso(id, usuario));
    }

    private Captacion obtenerConAcceso(long id, UsuarioAutenticado usuario) {
        Captacion captacion = captaciones.buscarPorId(id)
                .orElseThrow(() -> ApiException.noEncontrado("Captacion"));
        if (!puedeVer(usuario, captacion)) {
            throw ApiException.prohibido();
        }
        return captacion;
    }

    private Captacion obtenerPorCodigoConAcceso(String codigo, UsuarioAutenticado usuario) {
        Captacion captacion = captaciones.buscarPorCodigo(codigo)
                .orElseThrow(() -> ApiException.noEncontrado("Captacion"));
        if (!puedeVer(usuario, captacion)) {
            throw ApiException.prohibido();
        }
        return captacion;
    }

    private boolean puedeVer(UsuarioAutenticado usuario, Captacion captacion) {
        AgenteInmobiliario agente = captacion.getAgenteResponsable();
        Long idAgente = agente != null ? agente.getIdAgente() : null;
        if (idAgente == null) {
            return false;
        }
        if ("ADMIN".equals(usuario.rol())) {
            return true;
        }
        if ("AGENTE".equals(usuario.rol())) {
            return usuario.idDominio() == idAgente;
        }
        if ("BROKER".equals(usuario.rol())) {
            return brokers.puedeSupervisarAgente(usuario.idDominio(), idAgente);
        }
        return false;
    }

    private List<Captacion> captacionesDelUsuario(UsuarioAutenticado usuario) {
        if ("AGENTE".equals(usuario.rol())) {
            return captaciones.listarPorAgente(usuario.idDominio());
        }
        if ("ADMIN".equals(usuario.rol())) {
            return captaciones.listarTodos();
        }
        if ("BROKER".equals(usuario.rol())) {
            return captaciones.listarPorBroker(usuario.idDominio());
        }
        throw ApiException.prohibido();
    }

    private PageResponse<Dtos.CaptacionResponse> pagina(List<Captacion> fuente, int pagina, int tamano) {
        int paginaValida = SeguridadRest.pagina(pagina);
        int tamanoValido = SeguridadRest.tamano(tamano);
        int desde = Math.min((paginaValida - 1) * tamanoValido, fuente.size());
        int hasta = Math.min(desde + tamanoValido, fuente.size());
        List<Captacion> paginaItems = fuente.subList(desde, hasta);
        Map<Long, String> portadas = locales.listarPortadas(paginaItems.stream()
                .map(CaptacionesRest::idLocal)
                .filter(id -> id != null)
                .toList());
        List<Dtos.CaptacionResponse> items = paginaItems.stream()
                .map(captacion -> respuesta(captacion, portadas.get(idLocal(captacion))))
                .toList();
        return new PageResponse<>(items, fuente.size(), paginaValida, tamanoValido);
    }

    private Dtos.CaptacionResponse respuesta(Captacion captacion) {
        return Dtos.CaptacionResponse.desde(captacion, fotoPortadaClave(captacion));
    }

    private Dtos.CaptacionResponse respuesta(Captacion captacion, String fotoPortadaClave) {
        return Dtos.CaptacionResponse.desde(captacion, fotoPortadaClave);
    }

    private String fotoPortadaClave(Captacion captacion) {
        Long idLocal = idLocal(captacion);
        if (idLocal == null) {
            return null;
        }
        return locales.listarPortadas(List.of(idLocal)).get(idLocal);
    }

    private Propietario propietarioCompleto(LocalComercial local) {
        if (local == null || local.getIdPropietario() == null) {
            return local != null ? local.getPropietario() : null;
        }
        return propietarios.buscarPorId(local.getIdPropietario()).orElse(local.getPropietario());
    }

    private static Long idLocal(Captacion captacion) {
        LocalComercial local = captacion.getLocalComercial();
        return local != null ? local.getIdLocal() : null;
    }

    private static boolean coincideReasignable(Captacion c, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String codigo = texto(c.getCodigoCaptacion());
        LocalComercial local = c.getLocalComercial();
        String direccion = local != null ? texto(local.getDireccion()) : "";
        String distrito = local != null ? texto(local.getDistrito()) : "";
        AgenteInmobiliario agente = c.getAgenteResponsable();
        String agenteNombre = agente != null && agente.getPersona() != null
                ? texto(agente.getPersona().getNombresORazonSocial()) : "";
        return codigo.contains(q) || direccion.contains(q) || distrito.contains(q) || agenteNombre.contains(q);
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.toLowerCase(Locale.ROOT);
    }

    private void limitar() {
        String clave = request.getRemoteAddr() + ":" + request.getRequestURI();
        if (!LIMITADOR_SENSIBLE.permitir(clave)) {
            throw ApiException.demasiadasSolicitudes();
        }
    }

    private com.controllocal.model.comercial.enums.OperacionRequerimiento operacionAlquiler(String codigo) {
        if (codigo != null && !codigo.isBlank() && !"A".equals(codigo)) {
            throw ApiException.badRequest("ControlLocal solo admite operaciones de alquiler comercial.");
        }
        return com.controllocal.model.comercial.enums.OperacionRequerimiento.ALQUILER;
    }

    private static Response pdf(String nombreArchivo, byte[] contenido) {
        return Response.ok(contenido, "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + nombreSeguro(nombreArchivo) + "\"")
                .build();
    }

    private static String nombreSeguro(String nombreArchivo) {
        return nombreArchivo == null || nombreArchivo.isBlank()
                ? "reporte.pdf"
                : nombreArchivo.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
