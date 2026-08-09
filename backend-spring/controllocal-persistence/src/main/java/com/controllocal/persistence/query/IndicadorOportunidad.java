package com.controllocal.persistence.query;

import java.time.OffsetDateTime;

/** Read-DTO de una oportunidad para el embudo, el donut y el avance (E4). */
public interface IndicadorOportunidad {

    Long getId();

    Long getIdAgente();

    /** Codigo de 1 caracter: A, S, N, F, X. */
    String getEstado();

    OffsetDateTime getFechaRegistro();

    Long getIdCaptacion();

    /** Base de {@code interesados}: clientes distintos por captacion. */
    Long getIdCliente();
}
