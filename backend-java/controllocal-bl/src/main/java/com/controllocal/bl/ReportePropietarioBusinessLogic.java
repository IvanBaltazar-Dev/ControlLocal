package com.controllocal.bl;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.controllocal.model.comercial.ReportePropietario;

/**
 * Reporte periódico al propietario (Etapa 8). Vive en el expediente de la captación activa y
 * registra periodo, consultas, visitas, objeciones y recomendaciones. Cada reporte registrado
 * reinicia el reloj de la tarea automática de reporte (ver TareaBusinessLogic, disparador #6).
 */
public interface ReportePropietarioBusinessLogic {

    List<ReportePropietario> listarPorCaptacion(Long idCaptacion);

    /**
     * Fecha del último reporte de cada captación pedida, en una sola consulta. La usa la
     * bandeja del agente para saber cuándo vence el siguiente reporte sin consultar captación
     * por captación. Las captaciones sin reportes no aparecen en el mapa.
     */
    Map<Long, LocalDate> ultimoReportePorCaptaciones(Collection<Long> idsCaptacion);

    /** Registra el reporte (con defaults seguros) y devuelve su id. */
    Long registrar(ReportePropietario reporte);
}
