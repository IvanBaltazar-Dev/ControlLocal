package com.controllocal.persistence.query;

import java.time.LocalDate;

/**
 * Read-DTO de los disparadores de la bandeja: lo minimo que hace falta para
 * construir una tarea derivada, sin traer el grafo de la entidad de origen.
 *
 * <p>La v1 cargaba en memoria TODAS las prospecciones, solicitudes,
 * oportunidades, captaciones, visitas y contratos del agente y filtraba en
 * Java. Aqui cada disparador pregunta exactamente por lo suyo y la condicion
 * baja al WHERE (MEJ-05 / RC-003). El conjunto de tareas resultante es el
 * mismo; lo que cambia es cuanto se lee para llegar a el.
 *
 * <p>Los indices los reutiliza de las consultas de listado de cada vertical.
 */
public interface CandidatoTarea {

    /** Id de la entidad de origen (la mitad de la clave de dedup). */
    Long getEntidadId();

    /** Codigo legible (CAP-0001, SOL-…); vacio o null cuando la entidad no tiene. */
    String getEntidadCodigo();

    /**
     * Fecha que impone el plazo de la entidad —recontacto, vigencia de la
     * oferta, fecha de visita, ultimo reporte—. Es la base de
     * {@code diasSinAccion} y, cuando aplica, el vencimiento. Null si la
     * entidad no impone plazo.
     */
    LocalDate getFechaPlazo();

    /**
     * Dato suelto que el disparador necesita para decidir prioridad o mensaje
     * (hoy solo el estado de la visita). Null cuando no aplica.
     */
    String getMarca();
}
