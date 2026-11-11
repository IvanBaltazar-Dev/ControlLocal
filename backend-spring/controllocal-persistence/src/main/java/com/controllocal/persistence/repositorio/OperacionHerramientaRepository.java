package com.controllocal.persistence.repositorio;

import com.controllocal.domain.captura.OperacionHerramienta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface OperacionHerramientaRepository extends JpaRepository<OperacionHerramienta, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OperacionHerramienta o where o.organizacionId = :tenant and o.referencia = :referencia")
    Optional<OperacionHerramienta> bloquear(@Param("tenant") long tenant, @Param("referencia") String referencia);
    Optional<OperacionHerramienta> findByOrganizacionIdAndReferencia(long tenant, String referencia);
    interface SelectorDeCapacidad {
        String getCapacidad();long getIdPersona();long getIdPersonaRol();String getRolEfectivo();
    }
    /** Selector del tenant; el servicio conserva el rechazo 403 del actor ajeno sin hidratar entidades. */
    @Query("""
            select o.capacidad as capacidad,o.idPersona as idPersona,o.idPersonaRol as idPersonaRol,
                   o.rolEfectivo as rolEfectivo from OperacionHerramienta o
            where o.organizacionId=:tenant and o.referencia=:referencia
            """)
    Optional<SelectorDeCapacidad> selectorDeLaActuacion(@Param("tenant") long tenant,@Param("referencia") String referencia);

    interface ContextoDeCapacidad {
        String getCapacidad();String getEstado();Long getIdBorrador();String getHuella();String getContenido();
        long getIdPersona();long getIdPersonaRol();String getRolEfectivo();
    }
    /** La descripción conserva integridad y versiones de origen sin hidratar la operación mutable. */
    @Query("""
            select o.capacidad as capacidad,o.estado as estado,o.idBorrador as idBorrador,
                   o.huella as huella,o.contenido as contenido,o.idPersona as idPersona,
                   o.idPersonaRol as idPersonaRol,o.rolEfectivo as rolEfectivo from OperacionHerramienta o
            where o.organizacionId=:tenant and o.referencia=:referencia
            """)
    Optional<ContextoDeCapacidad> contextoDeLaActuacion(@Param("tenant") long tenant,@Param("referencia") String referencia);
}
