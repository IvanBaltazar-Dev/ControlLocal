package com.controllocal.web.dto;

import com.controllocal.service.PropietarioService;

import java.time.LocalDateTime;

/**
 * Contrato CONGELADO: espejo de Dtos.PropietarioResponse de la v1.
 *
 * <p>{@code id} es el {@code persona_rol.id} del rol PROPIETARIO — el mismo
 * valor que {@code idPropietario} en el cable de {@code /locales}.
 */
public record PropietarioResponse(Long id, String tipoPersona, String tipoDocumento,
                                  String numeroDocumento, String nombre, String telefono,
                                  String correo, String estado, Boolean consentimientoUsoDato,
                                  LocalDateTime fechaCreacion, int cantidadLocales) {

    public static PropietarioResponse desde(PropietarioService.FichaPropietario f) {
        return new PropietarioResponse(f.id(), f.tipoPersona(), f.tipoDocumento(), f.numeroDocumento(),
                f.nombre(), f.telefono(), f.correo(), f.estado(), f.consentimientoUsoDato(),
                f.fechaCreacion(), f.cantidadLocales());
    }
}
