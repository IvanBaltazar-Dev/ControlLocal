package com.controllocal.persistence.repositorio;

import com.controllocal.domain.seguridad.VinculoInteraccionSegura;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface VinculoInteraccionSeguraRepository extends JpaRepository<VinculoInteraccionSegura, String> {
    /** Serializa completar/consumir: dos peticiones no aceptan el mismo retorno. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VinculoInteraccionSegura v where v.hashFlujo = :hash")
    Optional<VinculoInteraccionSegura> bloquearPorHuella(@Param("hash") String hash);
}
