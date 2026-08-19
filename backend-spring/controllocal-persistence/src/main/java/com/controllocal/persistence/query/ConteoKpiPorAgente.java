package com.controllocal.persistence.query;

/**
 * Un KPI canonico de un agente concreto en el mes.
 *
 * <p>Lo consume el pulso del equipo, que responde a una pregunta distinta de la
 * del total: no «cuanto produjo el equipo» sino «como se reparte». Un total en
 * meta puede esconder a la mitad del equipo en cero.
 */
public interface ConteoKpiPorAgente {

    Long getIdAgente();

    String getKpi();

    int getCantidad();
}
