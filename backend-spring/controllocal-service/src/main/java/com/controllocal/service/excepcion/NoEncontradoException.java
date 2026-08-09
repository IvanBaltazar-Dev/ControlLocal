package com.controllocal.service.excepcion;

/**
 * Recurso inexistente: se traduce a 404 con el formato congelado del
 * backend Jakarta (ApiException.noEncontrado(recurso) = "Recurso no encontrado.").
 */
public class NoEncontradoException extends RuntimeException {

    public NoEncontradoException(String recurso) {
        super(recurso + " no encontrado.");
    }
}
