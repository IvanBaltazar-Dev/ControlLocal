package com.controllocal.service;

public interface PerfilService {

    record FichaPerfil(String nombre, String correo,
                       String telefono, String fotoClave) {
    }

    FichaPerfil obtener(Actor actor);

    FichaPerfil actualizarTelefono(String telefono, Actor actor);

    String actualizarFoto(String clave, Actor actor);
}
