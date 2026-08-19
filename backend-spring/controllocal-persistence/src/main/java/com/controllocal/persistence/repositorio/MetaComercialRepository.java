package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.MetaComercial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Las metas del mes. Se leen SIEMPRE por organizacion, mes y conjunto de
 * agentes: es la unica pregunta que el tablero hace.
 *
 * <p>No hay un {@code findByEquipo}: la meta del equipo es la suma de las de sus
 * agentes y se suma en el servicio, que es quien sabe si el alcance esta
 * completo. Un metodo que devolviera «la meta del equipo» invitaria a usarlo sin
 * comprobar la cobertura, que es justo lo que D-E2-2 §5 no permite.
 */
public interface MetaComercialRepository extends JpaRepository<MetaComercial, Long> {

    /** Las metas de un conjunto de agentes en un mes. La lectura del tablero. */
    List<MetaComercial> findByOrganizacionIdAndAnioAndMesAndIdRolAgenteIn(
            Long organizacionId, int anio, int mes, List<Long> agentes);

    /** Todas las del mes en la organizacion, para la vista de equipo completa. */
    List<MetaComercial> findByOrganizacionIdAndAnioAndMes(Long organizacionId, int anio, int mes);

    /** La de un agente y KPI concretos, para fijarla o corregirla. */
    Optional<MetaComercial> findByOrganizacionIdAndIdRolAgenteAndKpiAndAnioAndMes(
            Long organizacionId, Long idRolAgente, String kpi, int anio, int mes);
}
