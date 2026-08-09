package com.controllocal.web.dto;

/**
 * Identidad efectiva de la sesion (aditivo, Bloque 5).
 *
 * @param rol       banda REAL en el tenant: {@code AGENTE | BROKER |
 *                  TENANT_ADMIN}. Nunca el {@code ADMIN} del token, que es la
 *                  banda heredada del cable congelado
 * @param usuario   nombre de usuario con el que entro
 * @param idPersona identidad unica del actor (Party-Role)
 * @param idDominio {@code persona_rol} con el que firma lo que haga; en un
 *                  TENANT_ADMIN es su rol de gobierno, no uno de broker
 */
public record SesionResponse(String rol, String usuario, long idPersona, long idDominio) {
}
