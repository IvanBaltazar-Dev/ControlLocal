package com.controllocal.service.impl;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.comercial.ReportePropietario;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.InteraccionComercialRepository;
import com.controllocal.persistence.repositorio.MotivoNoContinuidadRepository;
import com.controllocal.persistence.repositorio.ReportePropietarioRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ReportePropietarioService;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.soporte.Fechas;
import com.controllocal.service.soporte.Vocabulario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Implementacion E2. A diferencia del legado, los tres agregados del preview
 * bajan a SQL: misma respuesta sin listar oportunidades/interacciones/motivos
 * completos ni producir N+1.
 */
@Service
public class ReportePropietarioServiceImpl implements ReportePropietarioService {

    private static final LocalDate FECHA_MINIMA = LocalDate.of(1, 1, 1);
    private static final LocalDate FECHA_MAXIMA = LocalDate.of(9999, 12, 30);

    private static final Map<String, String> DESCRIPCIONES_MOTIVO = Map.of(
            "P", "Precio",
            "U", "Ubicacion",
            "C", "Condiciones del contrato",
            "L", "Local no adecuado",
            "N", "Cliente no responde",
            "E", "Encontro otra opcion",
            "O", "Otro");

    private final ReportePropietarioRepository reportes;
    private final CaptacionRepository captaciones;
    private final InteraccionComercialRepository interacciones;
    private final VisitaRepository visitas;
    private final MotivoNoContinuidadRepository motivos;
    private final Alcances alcances;

    public ReportePropietarioServiceImpl(ReportePropietarioRepository reportes,
                                         CaptacionRepository captaciones,
                                         InteraccionComercialRepository interacciones,
                                         VisitaRepository visitas,
                                         MotivoNoContinuidadRepository motivos,
                                         Alcances alcances) {
        this.reportes = reportes;
        this.captaciones = captaciones;
        this.interacciones = interacciones;
        this.visitas = visitas;
        this.motivos = motivos;
        this.alcances = alcances;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichaReporte> listar(long idCaptacion, Actor actor) {
        Captacion captacion = cargarVisible(idCaptacion, actor);
        return reportes.listarPorCaptacion(actor.idOrganizacion(), captacion.getId()).stream()
                .map(ReportePropietarioServiceImpl::ficha)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenAvance preview(long idCaptacion, LocalDate desde, LocalDate hasta, Actor actor) {
        Captacion captacion = cargarVisible(idCaptacion, actor);
        return resumir(actor.idOrganizacion(), captacion.getId(), desde, hasta);
    }

    @Override
    @Transactional
    public FichaReporte registrar(long idCaptacion, DatosReporte datos, Actor actor) {
        Captacion captacion = cargarPropia(idCaptacion, actor);
        if (datos == null) {
            throw new ReglaNegocioException("Los datos del reporte son obligatorios.");
        }
        validarPeriodo(datos.periodoInicio(), datos.periodoFin());
        String canal = canal(datos.canalEnvio());
        ResumenAvance resumen = resumir(actor.idOrganizacion(), captacion.getId(),
                datos.periodoInicio(), datos.periodoFin());

        ReportePropietario reporte = new ReportePropietario();
        reporte.setOrganizacionId(actor.idOrganizacion());
        reporte.setCaptacion(captacion);
        reporte.setAgente(captacion.getAgente());
        reporte.setFechaReporte(LocalDate.now());
        reporte.setPeriodoInicio(datos.periodoInicio());
        reporte.setPeriodoFin(datos.periodoFin());
        reporte.setConsultasReportadas(resumen.consultas());
        reporte.setVisitasReportadas(resumen.visitas());
        reporte.setObjecionesFrecuentes(resumen.objeciones());
        reporte.setAjustesRecomendados(datos.ajustesRecomendados());
        reporte.setCanalEnvio(canal);
        // La columna conserva DEFAULT now(). Se fija tambien en la entidad
        // administrada para que el 201 tenga el campo sin depender de refresh.
        reporte.setFechaCreacion(OffsetDateTime.now());
        reportes.saveAndFlush(reporte);
        return ficha(reporte);
    }

    private Captacion cargarVisible(long idCaptacion, Actor actor) {
        Captacion captacion = cargarDelTenant(idCaptacion, actor);
        if (!alcances.alcanza(actor, captacion.getAgente().getId())) {
            throw new AccesoNoAutorizadoException();
        }
        return captacion;
    }

    private Captacion cargarPropia(long idCaptacion, Actor actor) {
        Captacion captacion = cargarDelTenant(idCaptacion, actor);
        if (!actor.esAgente() || captacion.getAgente() == null
                || captacion.getAgente().getId() != actor.idRolOperativo()) {
            throw new AccesoNoAutorizadoException();
        }
        return captacion;
    }

    private Captacion cargarDelTenant(long idCaptacion, Actor actor) {
        return captaciones.buscarFicha(actor.idOrganizacion(), idCaptacion)
                .orElseThrow(() -> new NoEncontradoException("Captacion"));
    }

    private ResumenAvance resumir(long idOrganizacion, long idCaptacion,
                                  LocalDate desde, LocalDate hasta) {
        LocalDate desdeEfectivo = desde != null ? desde : FECHA_MINIMA;
        LocalDate hastaEfectivo = hasta != null ? hasta : FECHA_MAXIMA;
        OffsetDateTime desdeInstante = inicio(desdeEfectivo);
        OffsetDateTime hastaExclusiva = inicio(hastaEfectivo.plusDays(1));
        int consultas = Math.toIntExact(interacciones.contarParaReporte(
                idOrganizacion, idCaptacion, desdeInstante, hastaExclusiva));
        int visitasRealizadas = Math.toIntExact(visitas.contarRealizadasParaReporte(
                idOrganizacion, idCaptacion, desdeEfectivo, hastaEfectivo));
        String objeciones = motivos.contarParaReporte(
                        idOrganizacion, idCaptacion, desdeInstante, hastaExclusiva).stream()
                .map(ReportePropietarioServiceImpl::objecion)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return new ResumenAvance(consultas, visitasRealizadas, objeciones);
    }

    private static String objecion(Object[] fila) {
        String codigo = (String) fila[0];
        Number cantidad = (Number) fila[1];
        return DESCRIPCIONES_MOTIVO.getOrDefault(codigo, codigo)
                + " (" + cantidad.longValue() + ")";
    }

    private static OffsetDateTime inicio(LocalDate fecha) {
        return fecha.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static void validarPeriodo(LocalDate inicio, LocalDate fin) {
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            throw new ReglaNegocioException(
                    "El fin del periodo no puede ser anterior al inicio.");
        }
    }

    private static String canal(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return ReportePropietario.CANAL_EMAIL;
        }
        String valor = codigo.trim();
        if (!Vocabulario.CANALES.contains(valor)) {
            throw new ReglaNegocioException("Canal de envío no válido: " + codigo);
        }
        return valor;
    }

    private static FichaReporte ficha(ReportePropietario reporte) {
        return new FichaReporte(
                reporte.getId(),
                reporte.getCaptacion() != null ? reporte.getCaptacion().getId() : null,
                reporte.getAgente() != null ? reporte.getAgente().getId() : null,
                reporte.getFechaReporte(),
                reporte.getPeriodoInicio(),
                reporte.getPeriodoFin(),
                reporte.getConsultasReportadas(),
                reporte.getVisitasReportadas(),
                reporte.getObjecionesFrecuentes(),
                reporte.getAjustesRecomendados(),
                reporte.getCanalEnvio(),
                Fechas.local(reporte.getFechaCreacion()));
    }
}
