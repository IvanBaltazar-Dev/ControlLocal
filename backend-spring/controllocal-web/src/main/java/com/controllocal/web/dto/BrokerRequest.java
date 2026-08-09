package com.controllocal.web.dto;

import com.controllocal.service.BrokerService;

public record BrokerRequest(String nombre, String tipoPersona, String tipoDocumento,
                            String numeroDocumento, String telefono, String correo,
                            String usuario, String contrasena, String zona,
                            String codigoBroker, String estado,
                            Boolean esAdministrador) {

    public BrokerService.DatosBroker aDatos() {
        return new BrokerService.DatosBroker(nombre, tipoPersona, tipoDocumento,
                numeroDocumento, telefono, correo, usuario, contrasena, zona,
                codigoBroker, estado, esAdministrador);
    }
}
