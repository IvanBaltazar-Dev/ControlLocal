package com.controllocal.web.dto;

import com.controllocal.service.AsignacionService;

public record AsignacionBrokerResponse(Long idBroker, String nombre, String zona,
                                       String estadoAdministrativo,
                                       boolean esAdministrador,
                                       int agentesACargo) {

    public static AsignacionBrokerResponse desde(
            AsignacionService.AsignacionBroker ficha) {
        return new AsignacionBrokerResponse(ficha.idBroker(), ficha.nombre(),
                ficha.zona(), ficha.estadoAdministrativo(),
                ficha.esAdministrador(), ficha.agentesACargo());
    }
}
