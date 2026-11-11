package com.controllocal.service;

/** Casos internos de contraseña global. CuentaService deriva el titular de la sesión segura. */
public interface ContrasenaService {
    void exigirActual(long idCuenta, char[] actual);
    void cambiar(long idCuenta, char[] actual, char[] nueva);
    void solicitarRecuperacion(String correoPropio, String ip, String agenteUsuario);
    void canjear(String token, char[] nueva, String ip, String agenteUsuario);
}

