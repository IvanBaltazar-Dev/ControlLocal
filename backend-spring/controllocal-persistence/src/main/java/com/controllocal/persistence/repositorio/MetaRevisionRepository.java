package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.MetaRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * La serie de revisiones de la meta.
 *
 * <p>No hay ningún {@code delete} ni ningún {@code update} masivo, y no es un
 * olvido: la tabla es append-only. Lo único que se modifica es el bloque de
 * decisión de una propuesta en espera, y eso lo hace la propia entidad.
 */
public interface MetaRevisionRepository extends JpaRepository<MetaRevision, Long> {

    /**
     * El historial de una meta concreta, en el orden en que ocurrió.
     *
     * <p>Ordena por id y no por fecha: dos revisiones del mismo día son normales
     * —el broker fija y corrige en la misma sesión— y solo el id las desempata
     * en el orden real de escritura. Es el mismo criterio que el histórico de
     * precios de E0.
     */
    List<MetaRevision> findByOrganizacionIdAndIdRolAgenteAndKpiAndAnioAndMesOrderByIdAsc(
            Long organizacionId, Long idRolAgente, String kpi, int anio, int mes);

    /** Todo el historial del mes para un conjunto de agentes, en una consulta. */
    List<MetaRevision> findByOrganizacionIdAndAnioAndMesAndIdRolAgenteInOrderByIdAsc(
            Long organizacionId, int anio, int mes, List<Long> agentes);

    /** Lo que el broker tiene pendiente de decidir. */
    List<MetaRevision> findByOrganizacionIdAndEstadoAndIdRolAgenteInOrderByIdAsc(
            Long organizacionId, String estado, List<Long> agentes);

    /**
     * La propuesta viva de un agente para un KPI y mes, si la hay.
     *
     * <p>Es como mucho una: un índice único parcial lo garantiza, para que
     * insistir diez veces no le llegue al broker como diez avisos de lo mismo.
     */
    Optional<MetaRevision> findByOrganizacionIdAndIdRolAgenteAndKpiAndAnioAndMesAndEstado(
            Long organizacionId, Long idRolAgente, String kpi, int anio, int mes, String estado);
}
