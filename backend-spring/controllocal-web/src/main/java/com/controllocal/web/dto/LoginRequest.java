package com.controllocal.web.dto;

/** Acceso por correo propio verificado; no acepta tenant, rol ni alias alternativo. */
public record LoginRequest(String correo, String contrasena) {
}
