package com.controllocal.service;

import java.util.List;

/**
 * Fijar y consultar las metas mensuales del equipo.
 *
 * <h2>Quien las fija</h2>
 *
 * <p>El broker sobre sus agentes; el administrador sobre los de su organizacion.
 * <b>Un agente no fija la suya</b>: una meta que se pone uno mismo no es una
 * meta, y el semaforo dejaria de significar nada el primer mes que alguien vaya
 * mal.
 *
 * <h2>Lo que NO existe aqui, a proposito</h2>
 *
 * <p>No hay «fijar la meta del equipo». La del equipo es la suma de las de sus
 * agentes (D-E2-2 §5), y si tambien se pudiera escribir a mano habria dos
 * verdades: el broker abriria «Equipo 56» y encontraria agentes que suman 48. Se
 * reparte por agente o no se fija.
 */
public interface MetaComercialService {

    /** Las metas del mes para los agentes que el actor alcanza. */
    List<MetaDeAgente> del(String mes, Actor actor);

    /**
     * Fija —o corrige— las metas de un mes. Es idempotente por
     * (agente, KPI, mes): repetir la misma llamada deja el mismo estado.
     *
     * <p>Una meta ausente en la peticion <b>no se borra</b>: se dejan las que ya
     * estaban. Borrar por omision convertiria un formulario a medio enviar en
     * una perdida silenciosa de objetivos.
     */
    List<MetaDeAgente> fijar(String mes, List<Asignacion> asignaciones, Actor actor);

    /**
     * Una meta con quien la tiene.
     *
     * @param valor {@code null} si ese agente no tiene meta de ese KPI. No es
     *              cero: cero significaria que este mes no se le pide ese
     *              resultado, que es una decision distinta de no haber decidido
     */
    record MetaDeAgente(long idRolAgente, String nombreAgente, String kpi, String rotuloKpi,
                        Integer valor) {
    }

    /** Lo que se pide fijar: a quien, que KPI y cuanto. */
    record Asignacion(long idRolAgente, String kpi, int valor) {
    }
}
