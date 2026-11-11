package com.controllocal.web.http;

/**
 * Error estructurado C0/K0. El mensaje conserva su concepto; el código permite
 * distinguir permiso, deshabilitación y fallo técnico sin comparar frases.
 * Los códigos específicos de sesión capada y MFA se conservan. Un error no
 * representa una colección vacía y la causa técnica privada no sale al cliente.
 */
public record ErrorResponse(String error, String codigo) {

    public static final String PERMISO_INSUFICIENTE = "PERMISO_INSUFICIENTE";
    public static final String CAPACIDAD_DESHABILITADA = "CAPACIDAD_DESHABILITADA";
    public static final String FALLO_TECNICO = "FALLO_TECNICO";

    /** C0/K0: código estable, sin comparar texto y sin convertir errores en un conjunto vacío. */
    public static ErrorResponse deEstado(int estado, String mensaje) {
        String codigo = switch (estado) {
            case 401 -> "SESION_REQUERIDA";
            case 403 -> PERMISO_INSUFICIENTE;
            case 404 -> "RECURSO_NO_ENCONTRADO";
            case 409 -> "CONFLICTO";
            case 429 -> "LIMITE_SOLICITUDES";
            default -> estado >= 500 ? FALLO_TECNICO : "PETICION_INVALIDA";
        };
        return new ErrorResponse(mensaje, codigo);
    }

    public ErrorResponse(String error) {
        this(error, null);
    }
}
