package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ExpedienteOperacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ExpedienteOperacionRepository extends JpaRepository<ExpedienteOperacion,Long> {
    @Query("select e from ExpedienteOperacion e where e.organizacionId=:org and e.id=:id")
    Optional<ExpedienteOperacion> buscar(@Param("org") long org,@Param("id") long id);
    @Query("select e from ExpedienteOperacion e where e.organizacionId=:org and e.oportunidad=:oportunidad and e.contratoOrigen is null")
    Optional<ExpedienteOperacion> originalPorOportunidad(@Param("org") long org,@Param("oportunidad") long oportunidad);
    @Query("select e from ExpedienteOperacion e where e.organizacionId=:org and e.contratoOrigen=:contrato")
    Optional<ExpedienteOperacion> porContratoOrigen(@Param("org") long org,@Param("contrato") long contrato);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ExpedienteOperacion e where e.organizacionId=:org and e.id=:id")
    Optional<ExpedienteOperacion> bloquear(@Param("org") long org,@Param("id") long id);
}
