package com.kairos.brox;

/**
 * <b>La sesion de la persona, no la de KAIROS.</b>
 *
 * <h2>KAIROS no tiene cuenta propia</h2>
 * No hay aqui un token de servicio, ni una cuenta de sistema, ni nada parecido
 * a {@code sudo}. Cada llamada a BROX viaja con el token de <b>la persona</b>
 * que esta conversando, y por eso los permisos y el alcance son exactamente los
 * suyos: si no ve una propiedad en la pantalla, KAIROS tampoco la ve.
 *
 * <p>La consecuencia util es que <b>no hay una segunda politica de permisos que
 * mantener</b>. La que hay es la de BROX, y KAIROS recibe el mismo 403 que
 * recibiria el navegador. Con sesion capada por contrasena temporal o MFA
 * pendiente, tampoco ejecuta nada — y lo dice, en vez de callarse.
 *
 * <h2>El tenant no lo decide el modelo</h2>
 * {@link #idOrganizacion} y la identidad salen del token, que a su vez sale de
 * la integracion del canal: quien escribe desde este numero de WhatsApp es esta
 * persona de esta organizacion, resuelto tecnicamente. Un modelo de lenguaje no
 * puede concluir "creo que este usuario es de Inmobiliaria X", y tampoco puede
 * cambiar de organizacion porque el mensaje lo pida.
 *
 * @param token          el JWT de la persona, tal cual lo emitio BROX
 * @param idOrganizacion solo para trazar y para no cruzar conversaciones; la
 *                       frontera real la aplica BROX en cada peticion
 */
public record SesionBrox(String token, Long idOrganizacion) {

    public SesionBrox {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Sin token no hay sesion: KAIROS actua siempre como una persona, nunca por su "
                            + "cuenta.");
        }
    }
}
