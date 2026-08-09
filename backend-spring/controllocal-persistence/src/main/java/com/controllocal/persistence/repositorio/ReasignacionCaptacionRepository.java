package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ReasignacionCaptacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReasignacionCaptacionRepository extends JpaRepository<ReasignacionCaptacion, Long> {

    /** Eventos de reasignacion de una captacion, el mas reciente primero (timeline). */
    List<ReasignacionCaptacion> findByCaptacionIdOrderByFechaCambioDesc(long idCaptacion);

    /** Bitacora de reasignaciones de la organizacion, la mas reciente primero. */
    List<ReasignacionCaptacion> findByOrganizacionIdOrderByIdDesc(long idOrganizacion);
}
