package com.controllocal.web.dto;

import java.time.LocalDateTime;

/**
 * Contrato congelado: identico a Dtos.LoginResponse del backend Jakarta.
 * Semantica v2 de los ids: idUsuario = persona.id (actor unico);
 * idDominio = persona_rol.id del rol operativo (BROKER/AGENTE).
 */
public record LoginResponse(String token, long expiraEnSegundos, String rol, long idUsuario, long idDominio,
                            String nombre, String usuario, LocalDateTime expiraEn) {
}
