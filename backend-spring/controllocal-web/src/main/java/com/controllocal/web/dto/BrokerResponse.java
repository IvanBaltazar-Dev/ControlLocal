package com.controllocal.web.dto;

import com.controllocal.service.BrokerService;

import java.time.LocalDate;

public record BrokerResponse(Long id, String codigoBroker, String nombre,
                             String tipoPersona, String tipoDocumento,
                             String numeroDocumento, String telefono, String correo,
                             String usuario, String zona, LocalDate fechaDesignacion,
                             String estadoAdministrativo, boolean esAdministrador,
                             int agentesACargo) {

    public static BrokerResponse desde(BrokerService.FichaBroker ficha) {
        return new BrokerResponse(ficha.id(), ficha.codigoBroker(), ficha.nombre(),
                ficha.tipoPersona(), ficha.tipoDocumento(), ficha.numeroDocumento(),
                ficha.telefono(), ficha.correo(), ficha.usuario(), ficha.zona(),
                ficha.fechaDesignacion(), ficha.estadoAdministrativo(),
                ficha.esAdministrador(), ficha.agentesACargo());
    }
}
