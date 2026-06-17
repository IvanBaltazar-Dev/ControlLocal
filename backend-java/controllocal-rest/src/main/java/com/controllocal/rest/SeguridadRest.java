package com.controllocal.rest;

import com.controllocal.rest.http.ApiException;
import com.controllocal.rest.seguridad.UsuarioAutenticado;

import jakarta.servlet.http.HttpServletRequest;

final class SeguridadRest {

    private SeguridadRest() {
    }

    static UsuarioAutenticado usuario(HttpServletRequest request) {
        Object valor = request.getAttribute("usuarioAutenticado");
        if (valor instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        throw ApiException.noAutorizado();
    }

    static UsuarioAutenticado exigirRol(HttpServletRequest request, String... roles) {
        UsuarioAutenticado usuario = usuario(request);
        if (!usuario.tieneRol(roles)) {
            throw ApiException.prohibido();
        }
        return usuario;
    }

    static int pagina(int pagina) {
        return Math.max(1, pagina);
    }

    static int tamano(int tamano) {
        return Math.max(1, Math.min(100, tamano));
    }
}
