package com.controllocal.service.excepcion;

/**
 * Violacion de una regla de negocio: se traduce a 400 en la capa web
 * (equivalente a la BusinessException del backend Jakarta).
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
