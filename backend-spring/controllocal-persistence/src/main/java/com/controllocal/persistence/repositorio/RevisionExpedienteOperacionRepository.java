package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.RevisionExpedienteOperacion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface RevisionExpedienteOperacionRepository extends JpaRepository<RevisionExpedienteOperacion,Long> {
    @Query("select r from RevisionExpedienteOperacion r where r.organizacionId=:org and r.expediente=:expediente and r.id=:id")
    Optional<RevisionExpedienteOperacion> buscar(@Param("org") long org,@Param("expediente") long expediente,@Param("id") long id);
    @Query("select r from RevisionExpedienteOperacion r where r.organizacionId=:org and r.expediente=:expediente order by r.id")
    List<RevisionExpedienteOperacion> historia(@Param("org") long org,@Param("expediente") long expediente);
}
