package com.controllocal.persistence.query;

import java.time.LocalDate;

/**
 * Read-DTO de una solicitud para los agregados de E4.
 *
 * <p>{@code idCaptacion} sale de la oportunidad: ni la v1 ni la v2 guardan la
 * captacion en la solicitud (el modelo v1 la expone porque el DAO la resuelve
 * con el mismo JOIN).
 */
public interface IndicadorSolicitud {

    Long getId();

    Long getIdAgente();

    /** Codigo de 1 caracter: G, E, O, A, R, D, C. */
    String getEstado();

    LocalDate getFechaRegistro();

    Long getIdCaptacion();

    Long getIdOportunidad();

    Long getIdCliente();
}
