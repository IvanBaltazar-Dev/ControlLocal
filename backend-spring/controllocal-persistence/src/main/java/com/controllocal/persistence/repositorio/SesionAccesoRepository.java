package com.controllocal.persistence.repositorio;

import com.controllocal.domain.seguridad.SesionAcceso;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SesionAccesoRepository extends JpaRepository<SesionAcceso,Long> {
    @Query("""
        select new com.controllocal.persistence.repositorio.SesionAccesoFila(s.id,s.idCuenta,s.revision,
        s.creadaEn,s.emitidaEn,s.venceEn,s.revocada,s.idMembresia,s.idRolOperativo,s.rolEfectivo,
        s.semillaContextos,s.contextoRefHash) from SesionAcceso s where s.referenciaHash=:huella
        """)
    Optional<SesionAccesoFila> vista(@Param("huella") String huella);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SesionAcceso s where s.referenciaHash=:huella")
    Optional<SesionAcceso> bloquear(@Param("huella") String huella);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from SesionAcceso s where s.referenciaHash=:huella")
    Optional<SesionAcceso> bloquearLecturaContexto(@Param("huella") String huella);
    @Modifying
    @Query("update SesionAcceso s set s.revocada=true,s.revision=s.revision+1 where s.idCuenta=:cuenta and s.revocada=false")
    int revocarDeCuenta(@Param("cuenta") long cuenta);

    @Modifying
    @Query("update SesionAcceso s set s.revocada=true,s.revision=s.revision+1 where s.idMembresia=:miembro and s.revocada=false")
    int revocarDeMembresia(@Param("miembro") long miembro);
}
