package com.controllocal.web.dto;

import java.time.OffsetDateTime;

/**
 * DTOs de contrasenas y recuperacion (Bloque 4). <b>Todos aditivos</b>: la v1
 * no tiene ninguna de estas operaciones, asi que no hay forma congelada que
 * respetar — pero si la regla de siempre, que Jackson omite los nulos.
 *
 * <p>Van juntos en un solo fichero por lo mismo que {@code Dtos} en la v1: son
 * cuatro formas triviales de un mismo flujo, y repartirlas en cuatro archivos
 * solo obliga a abrir cuatro archivos.
 */
public final class ContrasenaDtos {

    private ContrasenaDtos() {
    }

    /** {@code POST /perfil/contrasena}. */
    public record CambioContrasenaRequest(String contrasenaActual, String contrasenaNueva) {
    }

    /** {@code POST /auth/recuperacion} — solo el usuario; nunca se responde nada de el. */
    public record RecuperacionRequest(String usuario) {
    }

    /** {@code POST /auth/recuperacion/canje}. */
    public record CanjeRequest(String token, String contrasenaNueva) {
    }

    /**
     * Respuesta de una invitacion. {@code token} viaja <b>una sola vez</b>: no
     * hay ningun endpoint que lo vuelva a mostrar, porque en la base solo
     * queda su hash.
     *
     * @param entregadoAlTitular hoy siempre {@code false} — no hay transporte
     *                           configurado, asi que quien la emitio tiene que
     *                           entregar el enlace por su cuenta
     */
    public record InvitacionResponse(String token, OffsetDateTime expiraEn,
                                     boolean entregadoAlTitular) {
    }

    /**
     * Respuesta de una contrasena temporal. Tambien de una sola vez, y con el
     * aviso de que la cuenta queda obligada a cambiarla.
     */
    public record ContrasenaTemporalResponse(String usuario, String contrasenaTemporal,
                                             boolean debeCambiarla) {
    }
}
