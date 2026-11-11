package com.controllocal.persistence.repositorio;

import com.controllocal.domain.matching.RecorridoMatching;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface RecorridoMatchingRepository extends JpaRepository<RecorridoMatching, UUID> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select r from RecorridoMatching r where r.organizacionId=:org and r.id=:id and r.idPersona=:persona and r.idRol=:rol and r.rol=:banda and r.huellaContexto=:contexto")
    Optional<RecorridoMatching> delActor(@Param("org") long org,@Param("id") UUID id,
        @Param("persona") long persona,@Param("rol") long rol,@Param("banda") String banda,
        @Param("contexto") String contexto);
}

