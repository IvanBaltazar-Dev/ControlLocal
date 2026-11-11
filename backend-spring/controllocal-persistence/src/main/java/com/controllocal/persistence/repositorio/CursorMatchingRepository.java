package com.controllocal.persistence.repositorio;

import com.controllocal.domain.matching.CursorMatching;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface CursorMatchingRepository extends JpaRepository<CursorMatching,String> {
    @Query("select c from CursorMatching c where c.organizacionId=:org and c.referencia=:referencia")
    Optional<CursorMatching> porReferencia(@Param("org") long org,@Param("referencia") String referencia);

    @Query("select c from CursorMatching c where c.organizacionId=:org and c.idRecorrido=:recorrido and c.posicion=:posicion")
    Optional<CursorMatching> porPosicion(@Param("org") long org,@Param("recorrido") UUID recorrido,@Param("posicion") int posicion);

    @Modifying
    @Query(value="insert into matching_cursor(referencia,organizacion_id,id_recorrido,posicion) values(:ref,:org,:recorrido,:posicion) on conflict(organizacion_id,id_recorrido,posicion) do nothing",nativeQuery=true)
    int insertar(@Param("ref") String referencia,@Param("org") long org,@Param("recorrido") UUID recorrido,@Param("posicion") int posicion);
}

