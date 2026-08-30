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

    /**
     * Con el motivo dicho. Lo usa la autoridad de la propiedad (P0): "no
     * puedes" a secas obliga al usuario a adivinar si le falta un permiso o le
     * falta un dato, y en este caso son cosas distintas — que la propiedad no
     * tenga responsable no es lo mismo que tenerlo y ser otro.
     *
     * <p>Sigue siendo 403: cambia el texto, no el codigo.
     */
    public AccesoNoAutorizadoException(String mensaje) {
        super(mensaje);
    }
}
