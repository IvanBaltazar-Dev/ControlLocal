package com.controllocal.persistence.repositorio;

import com.controllocal.domain.matching.ReferenciaRecorridoMatching;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface ReferenciaRecorridoMatchingRepository extends JpaRepository<ReferenciaRecorridoMatching,Long> {
    @Query("select r from ReferenciaRecorridoMatching r where r.organizacionId=:org and r.idRecorrido=:recorrido order by r.ordinal")
    List<ReferenciaRecorridoMatching> delRecorrido(@Param("org") long org,@Param("recorrido") UUID recorrido);

    @Query(value="select ordinal from matching_referencia_retirada where organizacion_id=:org and id_recorrido=:recorrido order by ordinal",nativeQuery=true)
    List<Integer> retiradas(@Param("org") long org,@Param("recorrido") UUID recorrido);

    @Modifying
    @Query(value="insert into matching_referencia_retirada(organizacion_id,id_recorrido,ordinal,retirada_en) values(:org,:recorrido,:ordinal,:instante) on conflict(organizacion_id,id_recorrido,ordinal) do nothing",nativeQuery=true)
    int retirar(@Param("org") long org,@Param("recorrido") UUID recorrido,@Param("ordinal") int ordinal,@Param("instante") Instant instante);
}

