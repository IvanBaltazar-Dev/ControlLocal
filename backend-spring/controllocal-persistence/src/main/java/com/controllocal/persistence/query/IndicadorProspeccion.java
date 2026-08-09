package com.controllocal.persistence.query;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Read-DTO de una prospeccion para el indicador operativo de E4. */
public interface IndicadorProspeccion {

    Long getId();

    Long getIdAgente();

    /** Codigo de 1 caracter: P, C, R, E, S, T, D. */
    String getEstado();

    OffsetDateTime getFechaRegistro();

    /** Fecha del proximo contacto: base del atraso de "Disciplina comercial". */
    LocalDate getFechaRecontacto();
}
