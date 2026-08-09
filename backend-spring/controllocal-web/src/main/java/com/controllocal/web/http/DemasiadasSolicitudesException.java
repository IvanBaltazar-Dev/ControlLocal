package com.controllocal.web.http;

/**
 * Limite de intentos excedido: se traduce a 429 con el mensaje congelado
 * del backend Jakarta (ApiException.demasiadasSolicitudes()).
 */
public class DemasiadasSolicitudesException extends RuntimeException {

    /**
     * Espera real en segundos, que sale como cabecera {@code Retry-After}.
     * <p>
     * El CUERPO no se toca —dice "en un minuto" y es contrato congelado—, pero
     * con el bloqueo progresivo (D-S0-21) la espera puede ser de 15 minutos.
     * La cabecera es <b>aditiva</b>, no revela nada que el 429 no diga ya, y
     * evita que un cliente legitimo reintente en bucle contra una puerta que
     * sabe cuando se abre.
     */
    private final int reintentarEnSegundos;

    public DemasiadasSolicitudesException() {
        this(60);
    }

    public DemasiadasSolicitudesException(int reintentarEnSegundos) {
        super("Demasiadas solicitudes. Intenta nuevamente en un minuto.");
        this.reintentarEnSegundos = Math.max(1, reintentarEnSegundos);
    }

    public int reintentarEnSegundos() {
        return reintentarEnSegundos;
    }
}
