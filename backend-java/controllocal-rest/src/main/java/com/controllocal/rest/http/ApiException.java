package com.controllocal.rest.http;


public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static ApiException badRequest(String mensaje) {
        return new ApiException(400, mensaje);
    }

    public static ApiException noAutorizado() {
        return new ApiException(401, "Token invalido o expirado.");
    }

    public static ApiException prohibido() {
        return new ApiException(403, "No tienes permisos para esta operacion.");
    }

    public static ApiException noEncontrado(String recurso) {
        return new ApiException(404, recurso + " no encontrado.");
    }

    public static ApiException demasiadasSolicitudes() {
        return new ApiException(429, "Demasiadas solicitudes. Intenta nuevamente en un minuto.");
    }
}
