package com.controllocal.persistence.repositorio;
import com.controllocal.domain.comercial.PeticionRevisionEncargo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface PeticionRevisionEncargoRepository extends JpaRepository<PeticionRevisionEncargo,Long> {
    Optional<PeticionRevisionEncargo> findByOrganizacionIdAndId(long org,long id);
    @Query("select p.idEncargo from PeticionRevisionEncargo p where p.organizacionId=:org and p.id=:id")
    Optional<Long> encargoDe(@Param("org") long org,@Param("id") long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PeticionRevisionEncargo p where p.organizacionId=:org and p.id=:id")
    Optional<PeticionRevisionEncargo> bloquear(@Param("org") long org,@Param("id") long id);
}
