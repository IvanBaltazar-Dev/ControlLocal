package com.controllocal.service.excepcion;

/**
 * El actor no tiene alcance sobre el recurso (agente ajeno, broker que no
 * supervisa al agente). Se traduce a 403 con el mensaje congelado del
 * backend Jakarta (ApiException.prohibido()).
 */
public class AccesoNoAutorizadoException extends RuntimeException {

    public AccesoNoAutorizadoException() {
        super("No tienes permisos para esta operacion.");
    }
}
