package com.controllocal.web.dto;

import com.controllocal.service.PropietarioService;

/**
 * Contrato CONGELADO: espejo exacto de Dtos.PropietarioRequest de la v1. Mismo
 * criterio que {@link ClienteRequest}: de la autorizacion (D-27) no entra
 * ningun campo nuevo, porque el usuario solo aporta la casilla y esa ya viaja
 * como {@code consentimientoUsoDato}; canal, fecha, actor, tenant y version del
 * aviso los pone el backend.
 */
public record PropietarioRequest(String tipoPersona, String tipoDocumento, String numeroDocumento,
                                 String nombre, String telefono, String correo,
                                 Boolean consentimientoUsoDato, String estado) {

    public PropietarioService.DatosPropietario aDatos() {
        return new PropietarioService.DatosPropietario(tipoPersona, tipoDocumento, numeroDocumento,
                nombre, telefono, correo, consentimientoUsoDato, estado);
    }
}
