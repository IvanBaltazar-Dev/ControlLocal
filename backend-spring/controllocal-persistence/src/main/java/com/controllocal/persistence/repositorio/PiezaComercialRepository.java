package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.PiezaComercial;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface PiezaComercialRepository extends JpaRepository<PiezaComercial,Long> {
    Optional<PiezaComercial> findByOrganizacionIdAndReferencia(long org,UUID referencia);
    @Query("select p from PiezaComercial p where p.organizacionId=:org and (p.archivoPrivado=:archivo or p.archivoDifusion=:archivo)")
    Optional<PiezaComercial> deArchivo(@Param("org")long org,@Param("archivo")UUID archivo);
    Optional<PiezaComercial> findFirstByOrganizacionIdAndFamiliaOrderByVersionContenidoDesc(long org,UUID familia);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PiezaComercial p where p.organizacionId=:org and p.referencia=:referencia")
    Optional<PiezaComercial> bloquear(@Param("org")long org,@Param("referencia")UUID referencia);
}
