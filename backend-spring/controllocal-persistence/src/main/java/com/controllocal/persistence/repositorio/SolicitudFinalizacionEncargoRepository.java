package com.controllocal.persistence.repositorio;
import com.controllocal.domain.comercial.SolicitudFinalizacionEncargo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface SolicitudFinalizacionEncargoRepository extends JpaRepository<SolicitudFinalizacionEncargo,Long> {
    Optional<SolicitudFinalizacionEncargo> findFirstByOrganizacionIdAndIdEncargoAndEstadoInOrderByIdAsc(
            long org,long encargo,java.util.Collection<String> estados);
    Optional<SolicitudFinalizacionEncargo> findByOrganizacionIdAndId(long org,long id);
    @Query("select s.idEncargo from SolicitudFinalizacionEncargo s where s.organizacionId=:org and s.id=:id")
    Optional<Long> encargoDe(@Param("org") long org,@Param("id") long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudFinalizacionEncargo s where s.organizacionId=:org and s.id=:id")
    Optional<SolicitudFinalizacionEncargo> bloquear(@Param("org") long org,@Param("id") long id);
}
