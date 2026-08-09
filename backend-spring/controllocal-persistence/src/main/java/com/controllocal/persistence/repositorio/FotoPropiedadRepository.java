package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.FotoPropiedad;
import com.controllocal.persistence.query.PortadaPropiedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FotoPropiedadRepository extends JpaRepository<FotoPropiedad, Long> {

    List<FotoPropiedad> findByIdPropiedadOrderByOrdenAscIdAsc(long idPropiedad);

    long countByIdPropiedad(long idPropiedad);

    /** Portada (primera foto por orden) de cada propiedad del lote, en una sola consulta. */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (id_propiedad) id_propiedad AS idPropiedad, clave
            FROM foto_propiedad
            WHERE id_propiedad IN (:ids)
            ORDER BY id_propiedad, orden, id_foto
            """)
    List<PortadaPropiedad> portadas(@Param("ids") Collection<Long> ids);
}
