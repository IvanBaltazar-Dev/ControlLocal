package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.HitoContractual;
import com.controllocal.domain.comercial.TipoHitoContractual;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

/** El escritor bloquea primero expediente; no otra ruta de bloqueo hito/expediente inversa. */
public interface HitoContractualRepository extends JpaRepository<HitoContractual,Long> {
    @Query("select h from HitoContractual h where h.organizacionId=:org and h.id=:id")
    Optional<HitoContractual> buscar(@Param("org") long org,@Param("id") long id);
    @Query("select h from HitoContractual h where h.organizacionId=:org and h.expediente=:expediente and h.tipo=:tipo")
    Optional<HitoContractual> porTipo(@Param("org") long org,@Param("expediente") long expediente,@Param("tipo") TipoHitoContractual tipo);
    @Query("select h from HitoContractual h where h.organizacionId=:org and h.expediente=:expediente order by h.id")
    List<HitoContractual> delExpediente(@Param("org") long org,@Param("expediente") long expediente);
}
