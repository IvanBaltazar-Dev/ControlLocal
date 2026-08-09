package com.controllocal.persistence.query;

import java.time.OffsetDateTime;

/**
 * Read-DTO de una interaccion para E4. Al ser polimorfica, una interaccion
 * llega a la captacion por dos caminos —directo o via su oportunidad— y el
 * avance cuenta las dos.
 */
public interface IndicadorInteraccion {

    Long getId();

    Long getIdAgente();

    OffsetDateTime getFechaHora();

    /** Captacion directa (contexto CAPTACION); null en los demas contextos. */
    Long getIdCaptacion();

    /**
     * Oportunidad de la que cuelga, si cuelga de una. El avance compara contra
     * las oportunidades EN ALCANCE de la captacion, no contra la captacion de
     * la oportunidad: una oportunidad fuera de alcance no aporta su interaccion.
     */
    Long getIdOportunidad();
}
