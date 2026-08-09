package com.controllocal.domain.persona.enums;

/**
 * Roles acumulables de una persona (Party-Role). BROKER y AGENTE son roles
 * DE LA PERSONA; el acceso al sistema lo da el rol USUARIO_INTERNO
 * (credenciales), que Broker/Agente implican.
 *
 * <p>{@code ADMIN} (V32, D-S0-6) es el rol de <b>gobierno</b> del tenant y el
 * unico que no describe una actividad comercial: existe para que el
 * administrador tenga un {@code persona_rol} propio —el que viaja como
 * {@code idDominio} en el token (R1/R2)— <b>sin</b> tener que ser broker.
 * Antes de V32, administrar era un booleano del detalle de broker, y esa era
 * justo la herencia que confundia gobernar con operar (D-S0-7).
 */
public enum TipoRol {
    PROPIETARIO,
    CLIENTE,
    USUARIO_INTERNO,
    BROKER,
    AGENTE,
    ADMIN
}
