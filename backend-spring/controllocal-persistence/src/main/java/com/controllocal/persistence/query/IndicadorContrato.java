package com.controllocal.persistence.query;

import java.time.LocalDate;

/**
 * Read-DTO de un contrato para los cierres de E4.
 *
 * <p>El contrato no tiene agente ni captacion propios: los hereda de su
 * solicitud y, en su defecto, de su oportunidad. Por eso viajan las DOS ramas —
 * el alcance de la v1 se decide comparando ambas (§2 del contrato E4), y esta
 * consulta NO filtra por rol: el filtro se aplica arriba, con la misma regla
 * indirecta del cable.
 */
public interface IndicadorContrato {

    Long getId();

    LocalDate getFechaCierre();

    Long getIdAgenteSolicitud();

    Long getIdCaptacionSolicitud();

    Long getIdAgenteOportunidad();

    Long getIdCaptacionOportunidad();
}
