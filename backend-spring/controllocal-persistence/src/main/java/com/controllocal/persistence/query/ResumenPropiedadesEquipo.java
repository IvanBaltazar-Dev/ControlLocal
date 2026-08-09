package com.controllocal.persistence.query;

/**
 * KPI de {@code GET /captaciones/propiedades-equipo/resumen}, calculados en la
 * BASE sobre el mismo conjunto deduplicado que la lista.
 *
 * <p>Existe por la misma razon que {@code /locales/resumen}: contar en el
 * cliente solo ve la pagina descargada. Aqui ademas hay una razon extra —los
 * cuatro contadores son sobre INMUEBLES DISTINTOS, y eso no se puede deducir
 * de una pagina de captaciones.
 */
public interface ResumenPropiedadesEquipo {

    /** Inmuebles distintos captados por el equipo. */
    Long getPropiedades();

    /** De esos, cuantos tienen su captacion mas reciente en estado ACTIVA. */
    Long getConCaptacionActiva();

    /** Agentes distintos con al menos un inmueble en la cartera. */
    Long getAgentesConCartera();

    /** Distritos distintos representados. */
    Long getDistritos();
}
