package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.RevisionHitoContractual;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface RevisionHitoContractualRepository extends JpaRepository<RevisionHitoContractual,Long> {
    @Query("select r from RevisionHitoContractual r where r.organizacionId=:org and r.expediente=:expediente and r.hito=:hito and r.id=:id")
    Optional<RevisionHitoContractual> buscar(@Param("org") long org,@Param("expediente") long expediente,@Param("hito") long hito,@Param("id") long id);
    @Query("select r from RevisionHitoContractual r where r.organizacionId=:org and r.expediente=:expediente and r.hito=:hito order by r.id")
    List<RevisionHitoContractual> historia(@Param("org") long org,@Param("expediente") long expediente,@Param("hito") long hito);
}
