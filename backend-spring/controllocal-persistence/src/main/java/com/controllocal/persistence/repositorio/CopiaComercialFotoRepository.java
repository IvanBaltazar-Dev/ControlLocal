package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.CopiaComercialFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CopiaComercialFotoRepository extends JpaRepository<CopiaComercialFoto,Long> {
    /** Sólo decide el modo de fence; el original/SHA/proceso se revalidan antes de reutilizar. */
    @Query("select distinct c.original from CopiaComercialFoto c where c.organizacionId=:org "
            +"and c.original in :originales and c.proceso=:proceso")
    List<UUID> originalesConCopia(@Param("org") long org,@Param("originales") List<UUID> originales,
                                  @Param("proceso") String proceso);
    Optional<CopiaComercialFoto> findByOrganizacionIdAndOriginalAndHuellaOriginalAndProceso(
            long org,UUID original,String huella,String proceso);
    Optional<CopiaComercialFoto> findByOrganizacionIdAndReferencia(long org,UUID referencia);
}
