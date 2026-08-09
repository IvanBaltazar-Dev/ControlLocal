package com.controllocal.web.http;

/**
 * Contrato congelado: mismo cuerpo de error {@code {"error": ...}} que el
 * backend Jakarta.
 *
 * <p>{@code codigo} es <b>aditivo y casi siempre nulo</b>, asi que Jackson
 * (configurado {@code non_null}) no lo emite y todos los errores del contrato
 * siguen viajando byte a byte como antes. Solo lo llevan situaciones que la v1
 * <b>no puede producir</b>:
 * <ul>
 *   <li>el 403 de una sesion capada, por contrasena temporal (§4.5) o por
 *       segundo factor pendiente (D-S0-25) — el SPA tiene que distinguirlos de
 *       un "no tienes permisos" para llevar al paso que falta en vez de a la
 *       pantalla de acceso denegado;</li>
 *   <li>los 400 del segundo factor (V37,
 *       {@code ErrorMfaException}) — "el codigo esta mal", "ese codigo ya se
 *       uso" y "el desafio caduco" piden reacciones distintas.</li>
 * </ul>
 *
 * <p>En los dos casos la alternativa era comparar el texto en español, que es
 * traducible y se retoca sin pensar que algo depende de el.
 */
public record ErrorResponse(String error, String codigo) {

    public ErrorResponse(String error) {
        this(error, null);
    }
}
