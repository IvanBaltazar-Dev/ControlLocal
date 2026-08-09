package com.controllocal.persistence.query;

import java.time.LocalDate;

/** Read-DTO de una visita para el embudo, el operativo y el avance (E4). */
public interface IndicadorVisita {

    Long getId();

    Long getIdAgente();

    /** Codigo de 1 caracter: P, G, C, N, R. */
    String getEstado();

    LocalDate getFechaVisita();

    Long getIdOportunidad();
}
