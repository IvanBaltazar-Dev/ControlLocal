package com.controllocal.persistence.query;

/**
 * Conteo de motivos de no continuidad agrupado por captacion: alimenta el
 * "motivo principal" de cada fila del avance comercial (RF-017).
 */
public interface MotivoPorCaptacion {

    Long getIdCaptacion();

    /** Codigo de 1 caracter: P, U, C, L, N, E, O. */
    String getRazon();

    long getTotal();
}
