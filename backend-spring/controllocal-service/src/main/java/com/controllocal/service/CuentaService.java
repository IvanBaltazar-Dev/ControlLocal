package com.controllocal.service;

import java.time.Instant;
import java.util.List;

/** Cuenta personal global: el sujeto se obtiene de la sesión segura, sin tenant ni ID del cuerpo. */
public interface CuentaService {
    record FichaCuenta(long idCuenta, String nombreUsuario, String correoPropio, Instant correoConfirmadoEn,
                       String nombreProfesionalDeclarado, boolean debeCambiarContrasena, boolean debeEnrolarMfa) { }

    void solicitarAlta(String correo);
    void confirmarAlta(String token, String nombreUsuario, String nombreProfesionalDeclarado, char[] contrasena);
    FichaCuenta consultarPropia(String referenciaSesion, long revision);
    void cambiarContrasena(String referenciaSesion, long revision, char[] actual, char[] nueva);
    void solicitarCambioCorreo(String referenciaSesion, long revision, String correoNuevo,
                               char[] contrasenaActual, String codigoMfa);
    void confirmarCambioCorreo(String token);
    MfaService.EstadoFactor estadoMfa(String referenciaSesion, long revision);
    MfaService.Enrolamiento iniciarMfa(String referenciaSesion, long revision);
    List<String> confirmarMfa(String referenciaSesion, long revision, String codigo);
    List<String> regenerarCodigosMfa(String referenciaSesion, long revision, char[] contrasena, String codigo);
    void revocarMfaPropio(String referenciaSesion, long revision, char[] contrasena, String codigo,
                         String ip, String agenteUsuario);
}
