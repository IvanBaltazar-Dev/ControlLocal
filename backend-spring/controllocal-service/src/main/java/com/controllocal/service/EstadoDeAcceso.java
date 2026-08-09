package com.controllocal.service;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Lo que la capa web necesita saber de una credencial <b>en cada peticion</b>
 * autenticada, en una sola lectura.
 *
 * <p>Son tres preguntas distintas que se hacen a la vez y en el mismo punto:
 * <ol>
 *   <li>¿sigue viva esta sesion? ({@code sesionesInvalidasDesde}, D-S0-12)</li>
 *   <li>¿esta capada por contrasena temporal? ({@code debeCambiarContrasena},
 *       §4.5)</li>
 *   <li>¿con que banda entra al tenant? ({@code rolEfectivo}, D-S0-8)</li>
 * </ol>
 *
 * <p>Van juntas <b>a proposito</b>: separarlas costaria varias consultas por
 * peticion. Se lee como proyeccion estrecha —no como entidad— porque traer
 * credencial + rol + persona para obtener un instante y un booleano seria
 * pagar tres tablas por dos datos.
 *
 * <p>Vive aqui, y no en dominio, porque lo consume el filtro de la capa web:
 * {@code ArquitecturaCapasTest} prohibe que la web dependa de dominio o de
 * persistencia, y con razon — es lo que evita que una entidad JPA acabe
 * serializandose en una respuesta.
 */
public record EstadoDeAcceso(OffsetDateTime sesionesInvalidasDesde,
                             boolean debeCambiarContrasena,
                             boolean debeEnrolarMfa,
                             String rolEfectivo) {

    /** Sin credencial no hay nada invalidado, nada que capar y ninguna banda. */
    public static final EstadoDeAcceso SIN_RESTRICCIONES =
            new EstadoDeAcceso(null, false, false, null);

    /** Banda heredada del cable congelado: un broker con un booleano. */
    private static final String ADMIN_DEL_TOKEN = "ADMIN";
    private static final String TENANT_ADMIN = "TENANT_ADMIN";
    private static final String BROKER = "BROKER";

    /**
     * Banda con la que se autoriza esta peticion.
     *
     * <p><b>Bajar es inmediato; subir exige volver a entrar.</b> La membresia
     * manda —degradar a alguien surte efecto en la siguiente peticion, sin
     * esperar a que caduque su token—, pero el gobierno solo se concede si el
     * token <b>tambien</b> se emitio como administrador.
     *
     * <p>No es desconfianza de la membresia: es que el {@code idDominio} del
     * token es el {@code persona_rol} con el que se firmara todo lo que haga.
     * En un administrador ese id es su rol de gobierno, y solo el login sabe
     * elegirlo. Un token de broker al que se le concediera {@code TENANT_ADMIN}
     * por su membresia gobernaria firmando con el rol de broker — justo la
     * confusion que el Bloque 5 desmonta. Exigir que ambos coincidan cierra el
     * hueco sin una sola consulta extra, y no cuesta esperas: cambiar una
     * membresia invalida las sesiones de esa cuenta, asi que la promocion
     * obliga a reautenticar de todas formas.
     *
     * <p>Sin membresia se cae al rol del token, y ese respaldo <b>nunca</b>
     * concede gobierno: {@code ADMIN} degrada a {@code BROKER}, porque es la
     * banda heredada que este bloque viene a retirar.
     */
    public String bandaEfectiva(String rolDelToken) {
        if (rolEfectivo == null) {
            return ADMIN_DEL_TOKEN.equals(rolDelToken) ? BROKER : rolDelToken;
        }
        if (TENANT_ADMIN.equals(rolEfectivo) && !ADMIN_DEL_TOKEN.equals(rolDelToken)) {
            return rolDelToken;
        }
        return rolEfectivo;
    }

    /**
     * ¿Este token, emitido en {@code emitidoEn}, sigue valiendo?
     *
     * <p>Borde conocido y aceptado: {@code iat} tiene precision de segundo, asi
     * que un login dentro del mismo segundo que un logout nace invalidado.
     * Falla del lado seguro.
     */
    public boolean sesionInvalidada(Instant emitidoEn) {
        return sesionesInvalidasDesde != null
                && emitidoEn.isBefore(sesionesInvalidasDesde.toInstant());
    }
}
