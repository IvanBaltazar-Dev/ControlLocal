package com.controllocal.asistencia.brox;

/** Contexto resuelto por la sesión de servidor. No contiene credenciales ni concede permisos. */
public record SesionBrox(long idOrganizacion, long idPersona, long idRolOperativo,
                         String rolEfectivo, String identidadSesion, long expiraEnEpochSeconds) {
    public SesionBrox {
        if (idOrganizacion <= 0 || idPersona <= 0 || idRolOperativo <= 0
                || rolEfectivo == null || rolEfectivo.isBlank()
                || identidadSesion == null || identidadSesion.isBlank() || expiraEnEpochSeconds <= 0) {
            throw new IllegalArgumentException("Se requiere un contexto humano resuelto por Core.");
        }
    }
}
