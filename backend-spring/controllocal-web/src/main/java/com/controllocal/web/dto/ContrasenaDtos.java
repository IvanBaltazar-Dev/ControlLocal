package com.controllocal.web.dto;

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

    /** {@code POST /cuenta/contrasena}. */
    public record CambioContrasenaRequest(String contrasenaActual, String contrasenaNueva) {
    }

    /** {@code POST /auth/recuperacion}: correo propio, acuse uniforme. */
    public record RecuperacionRequest(String correo) {
    }

    /** {@code POST /auth/recuperacion/canje}. */
    public record CanjeRequest(String token, String contrasenaNueva) {
    }

}
