package com.controllocal.persistence.repositorio;

import com.controllocal.domain.network.SolicitudParticipacionNetwork;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface SolicitudParticipacionNetworkRepository extends JpaRepository<SolicitudParticipacionNetwork,Long> {
    interface ContextoSolicitud {Long getId();Long getParticipacion();Long getEncargo();long getVersion();String getEstado();String getAccion();}
    @Query("select s.id as id,s.participacion as participacion,p.encargo as encargo,s.version as version,s.estado as estado,s.accion as accion from SolicitudParticipacionNetwork s join ParticipacionNetwork p on p.id=s.participacion and p.organizacionId=s.organizacionId where s.organizacionId=:org and s.referencia=:ref")
    Optional<ContextoSolicitud> contexto(@Param("org") long org,@Param("ref") UUID referencia);
    @Query("select s.id as id,s.participacion as participacion,p.encargo as encargo,s.version as version,s.estado as estado,s.accion as accion from SolicitudParticipacionNetwork s join ParticipacionNetwork p on p.id=s.participacion and p.organizacionId=s.organizacionId where s.organizacionId=:org and s.id=:id")
    Optional<ContextoSolicitud> contextoId(@Param("org") long org,@Param("id") long id);
    @Query("select s.referencia from SolicitudParticipacionNetwork s where s.organizacionId=:org and s.id=:id")
    Optional<UUID> referenciaDe(@Param("org") long org,@Param("id") long id);
    Optional<SolicitudParticipacionNetwork> findByOrganizacionIdAndReferencia(long organizacion,UUID referencia);
    Optional<SolicitudParticipacionNetwork> findByOrganizacionIdAndId(long organizacion,long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudParticipacionNetwork s where s.organizacionId=:org and s.referencia=:ref")
    Optional<SolicitudParticipacionNetwork> bloquear(@Param("org") long org,@Param("ref") UUID referencia);
}
