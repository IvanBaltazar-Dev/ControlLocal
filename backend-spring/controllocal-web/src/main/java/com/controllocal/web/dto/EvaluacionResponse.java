package com.controllocal.web.dto;

import com.controllocal.service.EvaluacionService;

import java.time.LocalDateTime;

/** Contrato CONGELADO: espejo de Dtos.EvaluacionResponse de la v1. */
public record EvaluacionResponse(Long id, LocalDateTime fechaEvaluacion, String resultado,
                                 String observaciones, Long idBroker, String brokerNombre,
                                 String tipoEvaluacion, Long idSolicitud) {

    public static EvaluacionResponse desde(EvaluacionService.FichaEvaluacion f) {
        return new EvaluacionResponse(f.id(), f.fechaEvaluacion(), f.resultado(), f.observaciones(),
                f.idBroker(), f.brokerNombre(), f.tipoEvaluacion(), f.idSolicitud());
    }
}
