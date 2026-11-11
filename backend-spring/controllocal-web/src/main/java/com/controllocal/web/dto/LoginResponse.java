package com.controllocal.web.dto;

import java.time.Instant;

/** Sesión contextual; IDENTIDAD requiere selección segura antes de operar. */
public record LoginResponse(String token, long expiraEnSegundos, String rol, long idUsuario, long idDominio,
                            String nombre, String usuario, Instant expiraEn,
                            boolean contextoRequerido, java.util.List<com.controllocal.service.ContextosService.Contexto> contextos,
                            long idOrganizacion, long idCuenta, long revisionContexto, String modalidad) {
}
