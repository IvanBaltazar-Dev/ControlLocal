package com.controllocal.web.seguridad;

/**
 * Principal publicado por {@link FiltroAutenticacionJwt}: los claims del token
 * (CONGELADOS, byte-compatibles con el backend Jakarta) mas las dos cosas que
 * el backend resuelve por su cuenta en cada peticion — la organizacion y la
 * <b>banda efectiva</b>.
 *
 * <p>Las dos van aparte del token por la misma razon (D-20, D-S0-8): el token
 * no puede cambiar mientras convive con GlassFish —y solo admite tres roles
 * (R1)—, y aunque pudiera, ni el tenant ni el gobierno deben viajar en algo
 * que controla el cliente. Este record es la costura: cuando el token se
 * descongele y lleve ambos, solo cambia de donde salen los campos, no quien
 * los consume.
 *
 * <p>{@code rolEfectivo} es {@code AGENTE | BROKER | TENANT_ADMIN}, nunca el
 * {@code ADMIN} del cable: esa banda heredada muere en esta frontera.
 */
public record SesionDeRequest(TokenService.Sesion token, long idOrganizacion, String rolEfectivo) {
}
