package com.controllocal.persistence.repositorio;

import com.controllocal.domain.colaboracion.DecisionColaboracion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface DecisionColaboracionRepository extends JpaRepository<DecisionColaboracion,Long> {
    @Query("select d from DecisionColaboracion d where d.solicitud=:sol and (d.organizacionDemanda=:org or d.organizacionEncargo=:org) order by d.id")
    List<DecisionColaboracion> historial(@Param("sol") long solicitud,@Param("org") long organizacion);
    @Query("select d from DecisionColaboracion d where d.id=:id and d.solicitud=:sol and (d.organizacionDemanda=:org or d.organizacionEncargo=:org)")
    Optional<DecisionColaboracion> actual(@Param("id") long id,@Param("sol") long solicitud,@Param("org") long organizacion);
}
