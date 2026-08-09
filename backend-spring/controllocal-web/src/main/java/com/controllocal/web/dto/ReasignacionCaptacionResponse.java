package com.controllocal.web.dto;

import com.controllocal.service.CaptacionService;

import java.time.LocalDateTime;

/** Contrato CONGELADO: espejo de Dtos.ReasignacionCaptacionResponse de la v1. */
public record ReasignacionCaptacionResponse(Long idReasignacion, Long idCaptacion, String codigoCaptacion,
                                            String direccionLocal, Long idAgenteAnterior, String agenteAnteriorNombre,
                                            Long idAgenteNuevo, String agenteNuevoNombre, Long idBroker,
                                            String brokerNombre, LocalDateTime fechaCambio, String motivo) {

    public static ReasignacionCaptacionResponse desde(CaptacionService.FichaReasignacion f) {
        return new ReasignacionCaptacionResponse(f.idReasignacion(), f.idCaptacion(), f.codigoCaptacion(),
                f.direccionLocal(), f.idAgenteAnterior(), f.agenteAnteriorNombre(), f.idAgenteNuevo(),
                f.agenteNuevoNombre(), f.idBroker(), f.brokerNombre(), f.fechaCambio(), f.motivo());
    }
}
