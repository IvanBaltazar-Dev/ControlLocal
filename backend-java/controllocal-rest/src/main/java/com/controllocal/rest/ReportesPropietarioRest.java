package com.controllocal.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.controllocal.bl.AvanceCaptacionBusinessLogic;
import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.ReportePropietarioBusinessLogic;
import com.controllocal.bl.impl.AvanceCaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.CaptacionBusinessLogicImpl;
import com.controllocal.bl.impl.ReportePropietarioBusinessLogicImpl;
import com.controllocal.model.CodigoEnum;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.ReportePropietario;
import com.controllocal.model.comercial.enums.CanalContacto;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Reporte periódico al propietario (Etapa 8), anclado al expediente de la captación. El AGENTE
 * dueño de la captación registra el reporte (periodo, consultas, visitas, objeciones,
 * recomendaciones); registrarlo reinicia la tarea automática de reporte. La visibilidad respeta
 * la "vista personal": AGENTE solo sus captaciones, BROKER las de sus agentes, ADMIN todas.
 */
@Path("captaciones/{idCaptacion}/reportes-propietario")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportesPropietarioRest {

    private final ReportePropietarioBusinessLogic reportes = new ReportePropietarioBusinessLogicImpl();
    private final CaptacionBusinessLogic captaciones = new CaptacionBusinessLogicImpl();
    private final AvanceCaptacionBusinessLogic avance = new AvanceCaptacionBusinessLogicImpl();

    @Context
    private HttpServletRequest request;

    @GET
    public List<ReporteResponse> listar(@PathParam("idCaptacion") long idCaptacion) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        Captacion captacion = captacionVisible(idCaptacion, usuario);
        return reportes.listarPorCaptacion(captacion.getIdCaptacion()).stream()
                .map(ReporteResponse::desde)
                .toList();
    }

    // Vista previa de los valores derivados (consultas/visitas/objeciones) para el periodo dado,
    // para que el formulario los muestre antes de registrar. No persiste nada.
    @GET
    @Path("preview")
    public PreviewResponse preview(@PathParam("idCaptacion") long idCaptacion,
            @QueryParam("desde") String desde, @QueryParam("hasta") String hasta) {
        UsuarioAutenticado usuario = SeguridadRest.usuario(request);
        Captacion captacion = captacionVisible(idCaptacion, usuario);
        AvanceCaptacionBusinessLogic.ResumenAvance resumen =
                avance.resumen(captacion.getIdCaptacion(), fecha(desde), fecha(hasta));
        return new PreviewResponse(resumen.consultas(), resumen.visitas(), resumen.objeciones());
    }

    @POST
    public Response crear(@PathParam("idCaptacion") long idCaptacion, ReporteRequest dto) {
        UsuarioAutenticado usuario = SeguridadRest.exigirRol(request, "AGENTE");
        Captacion captacion = captacionPropia(idCaptacion, usuario);
        ReportePropietario reporte = aEntidad(dto, captacion, usuario.idDominio());
        // Consultas, visitas y objeciones son derivadas (no manuales): el servidor las recomputa de
        // la actividad real del periodo e ignora lo que envie el cliente.
        AvanceCaptacionBusinessLogic.ResumenAvance resumen =
                avance.resumen(captacion.getIdCaptacion(), reporte.getPeriodoInicio(), reporte.getPeriodoFin());
        reporte.setConsultasReportadas(resumen.consultas());
        reporte.setVisitasReportadas(resumen.visitas());
        reporte.setObjecionesFrecuentes(resumen.objeciones());
        Long id = reportes.registrar(reporte);
        ReportePropietario creado = reportes.listarPorCaptacion(captacion.getIdCaptacion()).stream()
                .filter(r -> id != null && id.equals(r.getIdReportePropietario()))
                .findFirst()
                .orElse(reporte);
        return Response.status(Response.Status.CREATED).entity(ReporteResponse.desde(creado)).build();
    }

    // ---------- visibilidad por rol ----------

    private Captacion captacionVisible(long idCaptacion, UsuarioAutenticado usuario) {
        Captacion captacion = captaciones.buscarPorId(idCaptacion)
                .orElseThrow(() -> ApiException.noEncontrado("Captacion"));
        String rol = usuario.rol();
        if ("ADMIN".equals(rol)
                || ("AGENTE".equals(rol) && esDelAgente(captacion, usuario.idDominio()))
                || ("BROKER".equals(rol) && esDelBroker(idCaptacion, usuario.idDominio()))) {
            return captacion;
        }
        throw ApiException.prohibido();
    }

    private Captacion captacionPropia(long idCaptacion, UsuarioAutenticado usuario) {
        Captacion captacion = captaciones.buscarPorId(idCaptacion)
                .orElseThrow(() -> ApiException.noEncontrado("Captacion"));
        if (!esDelAgente(captacion, usuario.idDominio())) {
            throw ApiException.prohibido();
        }
        return captacion;
    }

    private boolean esDelBroker(long idCaptacion, long idBroker) {
        return captaciones.listarPorBroker(idBroker).stream()
                .anyMatch(c -> c.getIdCaptacion() != null && c.getIdCaptacion() == idCaptacion);
    }

    private static boolean esDelAgente(Captacion captacion, long idAgente) {
        AgenteInmobiliario agente = captacion.getAgenteResponsable();
        return agente != null && agente.getIdAgente() != null && agente.getIdAgente() == idAgente;
    }

    // ---------- mapeo ----------

    private static ReportePropietario aEntidad(ReporteRequest dto, Captacion captacion, long idAgente) {
        if (dto == null) {
            throw ApiException.badRequest("Los datos del reporte son obligatorios.");
        }
        ReportePropietario reporte = new ReportePropietario();
        reporte.setCaptacion(captacion);
        AgenteInmobiliario agente = new AgenteInmobiliario();
        agente.setIdAgente(idAgente);
        reporte.setAgente(agente);
        reporte.setFechaReporte(LocalDate.now());
        reporte.setPeriodoInicio(dto.periodoInicio());
        reporte.setPeriodoFin(dto.periodoFin());
        reporte.setConsultasReportadas(dto.consultasReportadas());
        reporte.setVisitasReportadas(dto.visitasReportadas());
        reporte.setObjecionesFrecuentes(dto.objecionesFrecuentes());
        reporte.setAjustesRecomendados(dto.ajustesRecomendados());
        reporte.setCanalEnvio(canal(dto.canalEnvio()));
        return reporte;
    }

    private static LocalDate fecha(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(iso.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Fecha no valida: " + iso);
        }
    }

    private static CanalContacto canal(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null; // la BL aplica un canal por defecto.
        }
        try {
            return CodigoEnum.fromCodigo(CanalContacto.class, codigo);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Canal de envío no válido: " + codigo);
        }
    }

    public record ReporteRequest(LocalDate periodoInicio, LocalDate periodoFin,
                                 Integer consultasReportadas, Integer visitasReportadas,
                                 String objecionesFrecuentes, String ajustesRecomendados,
                                 String canalEnvio) {
    }

    // Valores derivados del periodo que el formulario muestra en modo solo lectura.
    public record PreviewResponse(int consultas, int visitas, String objeciones) {
    }

    public record ReporteResponse(Long id, Long idCaptacion, Long idAgente, LocalDate fechaReporte,
                                  LocalDate periodoInicio, LocalDate periodoFin,
                                  Integer consultasReportadas, Integer visitasReportadas,
                                  String objecionesFrecuentes, String ajustesRecomendados,
                                  String canalEnvio, LocalDateTime fechaCreacion) {
        public static ReporteResponse desde(ReportePropietario r) {
            return new ReporteResponse(
                    r.getIdReportePropietario(),
                    r.getCaptacion() != null ? r.getCaptacion().getIdCaptacion() : null,
                    r.getAgente() != null ? r.getAgente().getIdAgente() : null,
                    r.getFechaReporte(),
                    r.getPeriodoInicio(),
                    r.getPeriodoFin(),
                    r.getConsultasReportadas(),
                    r.getVisitasReportadas(),
                    r.getObjecionesFrecuentes(),
                    r.getAjustesRecomendados(),
                    r.getCanalEnvio() != null ? r.getCanalEnvio().getCodigo() : null,
                    r.getFechaCreacion());
        }
    }
}
