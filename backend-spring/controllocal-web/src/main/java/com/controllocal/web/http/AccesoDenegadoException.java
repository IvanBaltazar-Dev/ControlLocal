package com.controllocal.web.http;

/**
 * 403 con mensaje propio (el DELETE de locales de la v1 responde 403 con el
 * texto de la regla de negocio, no con el generico de permisos).
 */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
