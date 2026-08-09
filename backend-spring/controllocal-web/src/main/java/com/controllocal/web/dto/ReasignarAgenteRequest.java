package com.controllocal.web.dto;

import com.controllocal.service.AsignacionService;

public record ReasignarAgenteRequest(Long idAgente, Long idBrokerDestino,
                                     String motivo) {

    public AsignacionService.DatosReasignacion aDatos() {
        return new AsignacionService.DatosReasignacion(
                idAgente, idBrokerDestino, motivo);
    }
}
