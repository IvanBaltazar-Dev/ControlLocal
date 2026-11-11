package com.controllocal.persistence.repositorio;

import com.controllocal.domain.colaboracion.ConcesionColaboracion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ConcesionColaboracionRepository extends JpaRepository<ConcesionColaboracion,Long> {
    @Query("select c.solicitud from ConcesionColaboracion c where c.referencia=:ref and (c.organizacionId=:org or c.organizacionEncargo=:org)")
    Optional<Long> solicitudDe(@Param("ref") UUID referencia,@Param("org") long organizacion);
    @Query("select c from ConcesionColaboracion c where c.solicitud=:sol and (c.organizacionId=:org or c.organizacionEncargo=:org)")
    Optional<ConcesionColaboracion> deSolicitud(@Param("sol") long solicitud,@Param("org") long organizacion);
    @Query("select c from ConcesionColaboracion c where c.referencia=:ref and (c.organizacionId=:org or c.organizacionEncargo=:org)")
    Optional<ConcesionColaboracion> leer(@Param("ref") UUID referencia,@Param("org") long organizacion);
    // Conserva FOR UPDATE sin exigir a Hibernate bloquear la instancia histórica de su caché primero.
    @Query(value="select c.* from concesion_colaboracion c where c.referencia=:ref and (c.organizacion_id=:org or c.organizacion_encargo_id=:org) for update",nativeQuery=true)
    Optional<ConcesionColaboracion> bloquear(@Param("ref") UUID referencia,@Param("org") long organizacion);
}
