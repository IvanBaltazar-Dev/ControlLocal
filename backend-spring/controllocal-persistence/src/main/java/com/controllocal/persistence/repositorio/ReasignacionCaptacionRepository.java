package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ReasignacionCaptacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReasignacionCaptacionRepository extends JpaRepository<ReasignacionCaptacion, Long> {

    /** Eventos de reasignacion de una captacion, el mas reciente primero (timeline). */
    List<ReasignacionCaptacion> findByCaptacionIdOrderByFechaCambioDesc(long idCaptacion);

    /**
     * <b>Bitacora de reasignaciones DENTRO DEL ALCANCE del actor</b>, la mas
     * reciente primero (F3-bis, interpretacion de D-P0-6).
     *
     * <p>Mismo par {@code (:sinScope, :rolesAgente)} que los listados de
     * {@code CaptacionRepository}, y sobre <b>la misma columna</b>
     * ({@code captacion.id_rol_agente}): quien ve el encargo ve su rastro. El
     * alcance es el del encargo <b>de hoy</b>, no el del agente saliente ni el
     * del broker que firmo la reasignacion.
     *
     * <p><b>Sustituye a {@code findByOrganizacionIdOrderByIdDesc}, que ya no
     * existe</b>: mientras quedara un derivado que devolviera el tenant entero,
     * el siguiente consumidor de esta tabla podia volver a tomarlo sin enterarse
     * de que se saltaba el alcance — que es exactamente como llego aqui el
     * defecto.
     */
    @Query("""
            select r from ReasignacionCaptacion r
              join r.captacion c
             where r.organizacionId = :idOrganizacion
               and (:sinScope = true or c.agente.id in :rolesAgente)
             order by r.id desc
            """)
    List<ReasignacionCaptacion> bitacora(@Param("idOrganizacion") long idOrganizacion,
                                         @Param("sinScope") boolean sinScope,
                                         @Param("rolesAgente") Collection<Long> rolesAgente);
}
