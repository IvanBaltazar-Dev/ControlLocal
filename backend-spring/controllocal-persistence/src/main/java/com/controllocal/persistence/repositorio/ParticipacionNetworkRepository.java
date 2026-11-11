package com.controllocal.persistence.repositorio;

import com.controllocal.domain.network.ParticipacionNetwork;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ParticipacionNetworkRepository extends JpaRepository<ParticipacionNetwork,Long> {
    Optional<ParticipacionNetwork> findByOrganizacionIdAndEncargo(long organizacion,long encargo);
    Optional<ParticipacionNetwork> findByOrganizacionIdAndId(long organizacion,long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParticipacionNetwork p where p.organizacionId=:org and p.encargo=:encargo")
    Optional<ParticipacionNetwork> bloquearEncargo(@Param("org") long org,@Param("encargo") long encargo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParticipacionNetwork p where p.organizacionId=:org and p.id=:id")
    Optional<ParticipacionNetwork> bloquear(@Param("org") long org,@Param("id") long id);
}
