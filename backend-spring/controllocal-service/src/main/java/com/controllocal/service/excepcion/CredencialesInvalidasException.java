package com.controllocal.service.excepcion;

/**
 * Credenciales rechazadas. El mensaje es parte del contrato congelado
 * (mismo texto que devolvia el backend Jakarta en el 401 de login).
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Credenciales invalidas.");
    }
}
