package com.controllocal.web.dto;

import com.controllocal.service.ClienteService;

import java.time.LocalDateTime;

/** Contrato CONGELADO: espejo de Dtos.ClienteResponse de la v1. */
public record ClienteResponse(Long id, String tipoPersona, String tipoDocumento, String numeroDocumento,
                              String nombre, String telefono, String correo, String rubroComercial,
                              String estado, Boolean consentimientoContacto, Boolean consentimientoUsoDato,
                              LocalDateTime fechaCreacion) {

    public static ClienteResponse desde(ClienteService.FichaCliente f) {
        return new ClienteResponse(f.id(), f.tipoPersona(), f.tipoDocumento(), f.numeroDocumento(),
                f.nombre(), f.telefono(), f.correo(), f.rubroComercial(), f.estado(),
                f.consentimientoContacto(), f.consentimientoUsoDato(), f.fechaCreacion());
    }
}
