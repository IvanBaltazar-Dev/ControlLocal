package com.controllocal.web.dto;

import com.controllocal.service.PerfilService;

public record PerfilResponse(String nombre, String correo,
                             String telefono, String fotoClave) {

    public static PerfilResponse desde(PerfilService.FichaPerfil ficha) {
        return new PerfilResponse(ficha.nombre(), ficha.correo(),
                ficha.telefono(), ficha.fotoClave());
    }
}
