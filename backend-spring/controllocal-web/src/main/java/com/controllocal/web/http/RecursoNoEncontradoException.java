package com.controllocal.web.http;

/**
 * 404 con el mensaje del contrato congelado: "&lt;Recurso&gt; no encontrado."
 * (sufijo invariable, igual que ApiException.noEncontrado de la v1).
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso) {
        super(recurso + " no encontrado.");
    }
}
