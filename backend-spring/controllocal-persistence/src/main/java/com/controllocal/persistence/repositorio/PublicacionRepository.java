package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.query.EstadoPublicacionPropiedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {

    /** La primera de la lista es la publicacion principal (mas reciente), como en la v1. */
    List<Publicacion> findByIdPropiedadOrderByFechaPublicacionDesc(long idPropiedad);

    /** Estado de la publicacion principal de cada propiedad del lote, en una sola consulta. */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (id_propiedad) id_propiedad AS idPropiedad, estado
            FROM publicacion
            WHERE id_propiedad IN (:ids)
            ORDER BY id_propiedad, fecha_publicacion DESC, id_publicacion DESC
            """)
    List<EstadoPublicacionPropiedad> estadosPublicacion(@Param("ids") Collection<Long> ids);
}
