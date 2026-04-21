package com.controllocal.dao;

/**
 * Excepcion no verificada para errores de acceso a datos.
 */
public class DAOException extends RuntimeException {

    public DAOException(String message) {
        super(message);
    }

    public DAOException(String message, Throwable cause) {
        super(message, cause);
    }
}
