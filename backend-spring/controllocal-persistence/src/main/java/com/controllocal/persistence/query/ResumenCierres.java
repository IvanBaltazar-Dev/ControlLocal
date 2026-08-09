package com.controllocal.persistence.query;

/**
 * KPI de la pantalla de cierres exitosos, calculados en la BASE sobre el
 * alcance del actor.
 *
 * <p>Existe por lo mismo que {@code /locales/resumen}: sumar la comision en el
 * cliente solo sumaria la pagina descargada. Con el tope de 100 filas por
 * pagina del recurso, un total calculado en el navegador seria simplemente
 * falso en cuanto la corredora pase de 100 cierres.
 */
public interface ResumenCierres {

    /** Contratos cerrados dentro del alcance. */
    Long getCierres();

    /** Liquidaciones en estado PENDIENTE (el nombre del enum, no 'P'). */
    Long getPorLiquidar();

    /** Contratos que no tienen la liquidacion que debio crear la cascada. */
    Long getSinLiquidacion();
}
