package com.controllocal.persistence.repositorio;

import com.controllocal.domain.persona.SupervisionAgente;
import com.controllocal.persistence.query.ConteoPorBroker;
import com.controllocal.persistence.query.SupervisionVigente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupervisionAgenteRepository extends JpaRepository<SupervisionAgente, Long> {

    /**
     * Roles de agente que el broker supervisa HOY (vigencia abierta): base del
     * alcance por fila. El tenant va PRIMERO en el WHERE (V6): una supervision
     * de otra organizacion no puede ampliar el alcance de nadie.
     */
    @Query("""
            select s.idRolAgente from SupervisionAgente s
            where s.organizacionId = :idOrganizacion
              and s.idRolBroker = :idRolBroker
              and s.fechaFin is null
            """)
    List<Long> agentesSupervisados(@Param("idOrganizacion") long idOrganizacion,
                                   @Param("idRolBroker") long idRolBroker);

    /**
     * ¿El agente tiene HOY un broker que lo supervise? Es el gate del reenvio a
     * evaluacion (F4 §2): sin supervisor activo no habria quien evalue, y el
     * cable responde "El agente responsable no tiene broker supervisor activo.".
     */
    @Query("""
            select count(s) > 0 from SupervisionAgente s
            where s.organizacionId = :idOrganizacion
              and s.idRolAgente = :idRolAgente
              and s.fechaFin is null
            """)
    boolean tieneSupervisorActivo(@Param("idOrganizacion") long idOrganizacion,
                                  @Param("idRolAgente") long idRolAgente);

    @Query("""
            select s from SupervisionAgente s
            where s.organizacionId = :idOrganizacion
              and s.idRolAgente = :idRolAgente
              and s.fechaFin is null
            """)
    Optional<SupervisionAgente> buscarActivaPorAgente(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idRolAgente") long idRolAgente);

    @Query("""
            select s from SupervisionAgente s
            where s.organizacionId = :idOrganizacion
              and s.idRolAgente in :idsAgente
              and s.fechaFin is null
            """)
    List<SupervisionAgente> activasPorAgentes(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idsAgente") Collection<Long> idsAgente);

    @Query("""
            select s.idRolBroker as idBroker, count(s) as total
            from SupervisionAgente s
            where s.organizacionId = :idOrganizacion
              and s.idRolBroker in :idsBroker
              and s.fechaFin is null
            group by s.idRolBroker
            """)
    List<ConteoPorBroker> contarActivasPorBrokers(
            @Param("idOrganizacion") long idOrganizacion,
            @Param("idsBroker") Collection<Long> idsBroker);

    /**
     * Todos los equipos vigentes del tenant de una vez: el desempeno por broker
     * (E4) necesita el mapa completo broker→agentes, y pedirlo broker por
     * broker es el N+1 que la v1 pagaba en cada carga del dashboard.
     */
    @Query("""
            select s.idRolBroker as idBroker, s.idRolAgente as idAgente
            from SupervisionAgente s
            where s.organizacionId = :idOrganizacion
              and s.fechaFin is null
            """)
    List<SupervisionVigente> equiposVigentes(@Param("idOrganizacion") long idOrganizacion);
}
