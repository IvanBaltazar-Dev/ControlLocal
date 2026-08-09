package com.controllocal.service;

import java.time.OffsetDateTime;

/**
 * Contrasenas y recuperacion de acceso (Plan S0 §4.2-§4.5).
 *
 * <p>Cierra los dos huecos mas basicos del diagnostico: <b>H-02</b> (no existe
 * ninguna forma de cambiar una contrasena — el {@code PUT} de brokers y agentes
 * <b>ignoraba</b> el campo) y <b>H-08</b> (no existe ninguna forma de recuperar
 * el acceso salvo entrar a la base a mano).
 *
 * <h2>La regla que atraviesa todas las operaciones</h2>
 * <b>Nadie fija la contrasena de otra persona.</b> Ni el administrador, ni el
 * broker, ni el sistema. Las unicas dos formas de que una credencial cambie de
 * clave son: su titular la cambia sabiendo la anterior, o su titular la define
 * al canjear un token de un solo uso. La contrasena temporal es la unica
 * excepcion, y por eso <b>la genera el sistema</b> (no la elige quien la pide)
 * y nace <b>capada</b>: sirve para entrar una vez y cambiarla.
 *
 * <h2>Y la que gobierna las respuestas</h2>
 * Ninguna operacion publica revela si una cuenta existe. Ni por el codigo, ni
 * por el cuerpo, ni por el tiempo de respuesta.
 */
public interface ContrasenaService {

    /**
     * Token entregado <b>una sola vez</b>. Es el unico instante en el que
     * existe en claro: en la base solo queda su SHA-256.
     *
     * @param entregadoAlTitular {@code false} mientras no haya transporte: quien
     *                           lo pidio tiene que entregarlo a mano
     */
    record TokenEntregado(String token, OffsetDateTime expiraEn, boolean entregadoAlTitular) {
    }

    /** Contrasena temporal entregada una sola vez, junto a su titular. */
    record TemporalEntregada(String nombreUsuario, String contrasenaTemporal) {
    }

    /**
     * Cambio autenticado (§4.2). Exige la contrasena <b>actual</b>: sin eso,
     * una sesion robada podria quedarse con la cuenta para siempre.
     *
     * <p>Efectos: guarda el hash nuevo, sella {@code password_actualizada_en},
     * apaga {@code debe_cambiar_contrasena}, guarda el hash anterior en el
     * historial e <b>invalida todas las demas sesiones</b> — incluida la que
     * hace la llamada, que por eso tiene que volver a entrar.
     */
    void cambiar(Actor actor, char[] actual, char[] nueva);

    /**
     * Recuperacion pedida por el titular (§4.3). <b>No devuelve nada y no falla
     * nunca</b>: exista o no la cuenta, el llamador recibe 202. Devolver algo
     * distinto convertiria el endpoint en un padron de usuarios.
     */
    void solicitarRecuperacion(long idOrganizacion, String nombreUsuario, String ip, String agenteUsuario);

    /**
     * Canje del token (§4.3). Un solo uso: {@code usado_en} se sella en la
     * MISMA transaccion que cambia la clave, asi que repetir la llamada con el
     * mismo token ya no hace nada.
     *
     * <p>Sirve para los dos tipos —recuperacion e invitacion— porque el efecto
     * es identico: el titular define su clave.
     */
    void canjear(String token, char[] nueva, String ip, String agenteUsuario);

    /**
     * Invitacion emitida por el gobierno del tenant (§4.4). Devuelve el token
     * una sola vez para que quien lo pidio lo entregue.
     *
     * <p>Emitir uno nuevo <b>invalida el anterior</b> de esa credencial.
     */
    TokenEntregado emitirInvitacion(Actor actor, long idPersonaObjetivo, String motivo);

    /**
     * Contrasena temporal (§4.4, alternativa para el arranque sin correo).
     * La genera el sistema, se muestra una sola vez y deja la cuenta con
     * {@code debe_cambiar_contrasena = TRUE}: la sesion existe pero solo puede
     * ver su perfil, cambiar la clave y salir.
     *
     * <p>Invalida las sesiones vivas de esa cuenta: si alguien estaba dentro
     * con la clave anterior, deja de estarlo.
     */
    TemporalEntregada emitirContrasenaTemporal(Actor actor, long idPersonaObjetivo, String motivo);
}
