package com.controllocal.bl;

import java.util.List;

import com.controllocal.model.comercial.ReportePropietario;

/**
 * Reporte periódico al propietario (Etapa 8). Vive en el expediente de la captación activa y
 * registra periodo, consultas, visitas, objeciones y recomendaciones. Cada reporte registrado
 * reinicia el reloj de la tarea automática de reporte (ver TareaBusinessLogic, disparador #6).
 */
public interface ReportePropietarioBusinessLogic {

    List<ReportePropietario> listarPorCaptacion(Long idCaptacion);

    /** Registra el reporte (con defaults seguros) y devuelve su id. */
    Long registrar(ReportePropietario reporte);
}
