package com.controllocal.persistence.repositorio;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Fuerza el PLAN PERSONALIZADO de PostgreSQL para la transaccion en curso.
 *
 * <p><b>Por que existe.</b> Las busquedas por conjunto de candidatos llevan el
 * texto como parametro dentro de un {@code LIKE}. El driver JDBC convierte la
 * sentencia en un <i>prepared statement</i> del servidor tras cinco
 * ejecuciones, y PostgreSQL empieza entonces a reutilizar un <b>plan
 * generico</b>: uno construido sin mirar los valores, que para un {@code LIKE}
 * parametrizado asume una selectividad por defecto de "casi nada".
 *
 * <p>Medido sobre 100.000 visitas, misma consulta y mismos parametros:
 * <ul>
 *   <li>plan personalizado: {@code HashAggregate} sobre recorridos completos,
 *       <b>245–400 ms</b> el conteo y <b>340–430 ms</b> la pagina;</li>
 *   <li>plan generico: {@code Nested Loop} con <b>100.000 iteraciones</b>,
 *       <b>770–845 ms</b> y <b>790–960 ms</b>.</li>
 * </ul>
 *
 * <p>El sintoma en el gate era caracteristico y facil de leer al reves: la
 * llamada <b>en frio era mas rapida que el regimen</b> (986 ms contra 3.253 de
 * p50), porque las primeras ejecuciones todavia planificaban con los valores
 * delante.
 *
 * <p><b>Por que asi y no de otra forma.</b> {@code SET LOCAL} vive dentro de la
 * transaccion y revierte sola al terminar, de modo que solo afecta a la
 * consulta de busqueda. Ponerlo en la conexion
 * ({@code connection-init-sql}) o desactivar los <i>prepared statements</i>
 * ({@code prepareThreshold=0}) resolveria lo mismo, pero cobrandole la
 * replanificacion a TODAS las consultas del sistema, incluidas las que se
 * benefician del plan cacheado.
 *
 * <p>El coste de replanificar es de unos <b>7 ms</b>, medido: irrelevante
 * frente a los cientos que ahorra aqui, y por eso no se aplica en los listados
 * sin texto, donde no hay nada que ganar.
 */
@Repository
public class PlanDeConsulta {

    @PersistenceContext
    private EntityManager em;

    /**
     * Debe llamarse DENTRO de la transaccion de lectura, antes de la consulta
     * de candidatos. Fuera de una transaccion no hace nada util: {@code SET
     * LOCAL} sin transaccion es una operacion vacia.
     */
    public void forzarPersonalizado() {
        // Sin contexto de persistencia no hay transaccion que acotar, y
        // `SET LOCAL` fuera de una transaccion no hace nada. Los tests unitarios
        // de service construyen esta clase a mano para no arrastrar un
        // EntityManager que no usan: ahi esto es un no-op deliberado, no un
        // fallo silencioso.
        if (em == null) {
            return;
        }
        em.createNativeQuery("set local plan_cache_mode = 'force_custom_plan'").executeUpdate();
    }
}
