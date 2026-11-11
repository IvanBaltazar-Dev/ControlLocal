package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ComisionLiquidacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ComisionLiquidacionRepository extends JpaRepository<ComisionLiquidacion,Long> {
    interface VersionVisible {long getId();long getVersion();}
    @Query("select c.id as id,c.version as version from ComisionLiquidacion c join Captacion e on e.id=c.encargo and e.organizacionId=c.organizacionId where c.organizacionId=:org and e.agente.id in :roles order by c.id")
    List<VersionVisible> versionesEnAlcance(@Param("org")long org,@Param("roles")Collection<Long> roles);
    @Query("select c from ComisionLiquidacion c join Captacion e on e.id=c.encargo and e.organizacionId=c.organizacionId where c.organizacionId=:org and e.agente.id in :roles order by c.id")
    List<ComisionLiquidacion> enAlcance(@Param("org")long org,@Param("roles")Collection<Long> roles);
    Optional<ComisionLiquidacion> findByOrganizacionIdAndId(long org,long id);
    @Query("select c.version from ComisionLiquidacion c where c.organizacionId=:org and c.id=:id")
    Long versionActualParaRadar(@Param("org")long org,@Param("id")long id);
    Optional<ComisionLiquidacion> findByOrganizacionIdAndContextoEconomico(long org,long contexto);
    Optional<ComisionLiquidacion> findByOrganizacionIdAndContratoId(long org,long contrato);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ComisionLiquidacion c where c.organizacionId=:org and c.id=:id")
    Optional<ComisionLiquidacion> bloquear(@Param("org")long org,@Param("id")long id);
    @Query("""
        select c from ComisionLiquidacion c join Captacion e on e.id=c.encargo and e.organizacionId=c.organizacionId
        where c.organizacionId=:org and c.contrato.id in :ids and e.agente.id in :roles
        """)
    List<ComisionLiquidacion> porContratosEnAlcance(@Param("org")long org,@Param("ids")Collection<Long> ids,
                                                  @Param("roles")Collection<Long> roles);
}
