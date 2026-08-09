package com.controllocal.web.dto;

import com.controllocal.service.AsignacionService;

public record AsignacionAgenteResponse(Long idAgente, String nombre,
                                       String numeroDocumento,
                                       String estadoAdministrativo,
                                       String estadoOperativo,
                                       String brokerActual) {

    public static AsignacionAgenteResponse desde(
            AsignacionService.AsignacionAgente ficha) {
        return new AsignacionAgenteResponse(ficha.idAgente(), ficha.nombre(),
                ficha.numeroDocumento(), ficha.estadoAdministrativo(),
                ficha.estadoOperativo(), ficha.brokerActual());
    }
}
