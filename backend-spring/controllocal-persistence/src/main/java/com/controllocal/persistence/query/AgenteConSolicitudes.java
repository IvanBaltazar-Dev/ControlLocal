package com.controllocal.persistence.query;

/**
 * Un agente con al menos una solicitud en el alcance del actor, para que el
 * filtro por agente de la bandeja sea data-driven: se ofrece solo a quien tiene
 * algo que mostrar.
 *
 * <p>Viaja con el id ademas del nombre porque el filtro se manda por id: dos
 * agentes pueden llamarse igual, y filtrar por nombre los mezclaria. Es la
 * misma forma que {@link AgenteConCierres}, pero sobre otro conjunto: no se
 * comparten porque lo que se comparte entre modulos es la FORMA de la consulta,
 * no la consulta.
 */
public interface AgenteConSolicitudes {

    Long getId();

    String getNombre();
}
