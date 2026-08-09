package com.controllocal.web.dto;

import com.controllocal.service.AsignacionService;

import java.time.LocalDateTime;

public record BrokerAgenteResponse(Long id, Long idAgente, String agenteNombre,
                                   Long idBrokerAnterior,
                                   String brokerAnteriorNombre,
                                   Long idBrokerNuevo, String brokerNuevoNombre,
                                   Long idBrokerAdministrador,
                                   String brokerAdministradorNombre,
                                   LocalDateTime fechaCambio, String motivo) {

    public static BrokerAgenteResponse desde(
            AsignacionService.Reasignacion ficha) {
        return new BrokerAgenteResponse(ficha.id(), ficha.idAgente(),
                ficha.agenteNombre(), ficha.idBrokerAnterior(),
                ficha.brokerAnteriorNombre(), ficha.idBrokerNuevo(),
                ficha.brokerNuevoNombre(), ficha.idBrokerAdministrador(),
                ficha.brokerAdministradorNombre(), ficha.fechaCambio(),
                ficha.motivo());
    }
}
