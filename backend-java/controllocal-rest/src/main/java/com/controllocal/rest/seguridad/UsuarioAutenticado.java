package com.controllocal.rest.seguridad;

public record UsuarioAutenticado(
        long idUsuario,
        long idDominio,
        String usuario,
        String rol) {

    public boolean tieneRol(String... rolesPermitidos) {
        for (String permitido : rolesPermitidos) {
            if (rol.equals(permitido)) {
                return true;
            }
        }
        return false;
    }
}
