package com.controllocal.persistence.repositorio;

import com.controllocal.domain.colaboracion.SolicitudColaboracion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface SolicitudColaboracionRepository extends JpaRepository<SolicitudColaboracion,Long> {
    String VISIBLE="(s.organizacionEncargo=:org or (s.organizacionId=:org and (s.idDemanda is not null or exists(select d.id from DecisionColaboracion d where d.id=s.decisionEncargo and d.decision in ('ACEPTAR','LIMITAR')))))";
    String VISIBLE_SQL="(s.organizacion_encargo_id=:org or (s.organizacion_id=:org and (s.id_requerimiento is not null or exists(select 1 from decision_colaboracion d where d.id_decision_colaboracion=s.id_decision_encargo and d.decision in ('ACEPTAR','LIMITAR')))))";
    @Query("select s.referencia from SolicitudColaboracion s where s.idDemanda is null and s.organizacionId=:org and s.responsableInvitado in :roles and "+VISIBLE+" order by s.id desc")
    List<UUID> invitacionesRecibidas(@Param("org")long org,@Param("roles")Collection<Long> roles,org.springframework.data.domain.Pageable pagina);
    @Query("select s.referencia from SolicitudColaboracion s,Captacion c where s.idDemanda is null and s.organizacionEncargo=:org and c.organizacionId=:org and c.id=s.idEncargo and c.agente.id in :roles order by s.id desc")
    List<UUID> invitacionesOriginadas(@Param("org")long org,@Param("roles")Collection<Long> roles,org.springframework.data.domain.Pageable pagina);
    @Query("select s from SolicitudColaboracion s where s.referencia=:ref and "+VISIBLE)
    Optional<SolicitudColaboracion> leer(@Param("ref") UUID referencia,@Param("org") long organizacion);
    // El bloqueo SQL precede al refresh del consumidor: JPQL @Lock comprobaría antes una versión ya cacheada.
    @Query(value="select s.* from solicitud_colaboracion s where s.referencia=:ref and "+VISIBLE_SQL+" for update",nativeQuery=true)
    Optional<SolicitudColaboracion> bloquear(@Param("ref") UUID referencia,@Param("org") long organizacion);
    @Query("select s from SolicitudColaboracion s where s.id=:id and "+VISIBLE)
    Optional<SolicitudColaboracion> leerId(@Param("id") long id,@Param("org") long organizacion);
    @Query(value="select s.* from solicitud_colaboracion s where s.id_solicitud_colaboracion=:id and "+VISIBLE_SQL+" for update",nativeQuery=true)
    Optional<SolicitudColaboracion> bloquearId(@Param("id") long id,@Param("org") long organizacion);
}
