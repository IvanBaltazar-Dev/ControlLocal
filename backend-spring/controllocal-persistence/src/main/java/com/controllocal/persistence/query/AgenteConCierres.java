package com.controllocal.persistence.query;

/**
 * Un agente con al menos un cierre en el alcance del actor, para que el filtro
 * por agente sea data-driven: se ofrece solo a quien tiene algo que mostrar.
 *
 * <p>Viaja con el id ademas del nombre porque el filtro se manda por id: dos
 * agentes pueden llamarse igual, y filtrar por nombre los mezclaria.
 */
public interface AgenteConCierres {

    Long getId();

    String getNombre();
}
