package com.controllocal.web.dto;

import com.controllocal.service.EvaluacionService;

/**
 * Contrato CONGELADO: espejo de Dtos.EvaluacionRequest de la v1.
 *
 * <p>{@code tipoEvaluacion} se ignora como VALOR —lo deriva el resultado:
 * OBSERVADA ⇒ OBSERVACION, APROBADA/RECHAZADA ⇒ FINAL— pero el cable lo exige
 * PRESENTE y valido, porque la v1 lo parseaba en {@code aEntidad} antes de que
 * la BL lo pisara. Ese parseo vive ahora en {@code EvaluacionServiceImpl}, con
 * el orden del cable (primero el tipo, despues el resultado).
 */
public record EvaluacionRequest(String tipoEvaluacion, String resultado, String observaciones,
                                Long idSolicitud) {

    public EvaluacionService.DatosEvaluacion aDatos() {
        return new EvaluacionService.DatosEvaluacion(tipoEvaluacion, resultado, observaciones, idSolicitud);
    }
}
