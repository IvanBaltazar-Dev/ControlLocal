package com.controllocal.web.dto;

/** Entradas exclusivas de la superficie segura; no son argumentos de herramientas del modelo. */
public final class CuentaDtos {
    private CuentaDtos() { }
    public record AltaRequest(String correo) { }
    public record ConfirmacionAltaRequest(String token,String nombreUsuario,String nombreProfesionalDeclarado,String contrasena) { }
    public record CambioCorreoRequest(String correoNuevo,String contrasenaActual,String codigoMfa) { }
    public record ConfirmacionCorreoRequest(String token) { }
}
