package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.persistence.query.EstadoPublicacionPropiedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PublicacionRepository extends JpaRepository<Publicacion, Long> {

    /** La primera de la lista es la publicacion principal (mas reciente), como en la v1. */
    List<Publicacion> findByIdPropiedadOrderByFechaPublicacionDesc(long idPropiedad);

    /**
     * <b>Los anuncios de UN encargo</b>, del mas reciente al mas antiguo (V70).
     *
     * <p>Es la lectura canonica desde que la publicacion pertenece al encargo:
     * una propiedad en venta y en alquiler tiene dos series de anuncios que no
     * se mezclan, y preguntar por {@code id_propiedad} las devolveria juntas sin
     * poder decir cual publica que.
     *
     * <p>Filtra por organizacion, a diferencia de la consulta por inmueble de
     * arriba: toda lectura nueva se acota al tenant.
     */
    @Query("""
            select p from Publicacion p
            where p.organizacionId = :idOrganizacion
              and p.idEncargo = :idEncargo
            order by p.fechaPublicacion desc, p.id desc
            """)
    List<Publicacion> deEncargo(@Param("idOrganizacion") long idOrganizacion,
                                @Param("idEncargo") long idEncargo);

    /**
     * Los anuncios de VARIOS encargos de una vez, para colgarlos de la ficha sin
     * una consulta por bloque. Mismo patron con el que la ficha cuelga los
     * encargos de una pagina de propiedades.
     */
    @Query("""
            select p from Publicacion p
            where p.organizacionId = :idOrganizacion
              and p.idEncargo in :idsEncargos
            order by p.idEncargo, p.fechaPublicacion desc, p.id desc
            """)
    List<Publicacion> deEncargos(@Param("idOrganizacion") long idOrganizacion,
                                 @Param("idsEncargos") Collection<Long> idsEncargos);

    /**
     * Una publicacion del tenant. Se busca <b>siempre</b> con la organizacion:
     * un {@code findById} desnudo deja editar el anuncio del vecino con solo
     * acertar el id, que es lo que hacian {@code actualizar} y
     * {@code cambiarEstado} hasta V70.
     */
    Optional<Publicacion> findByOrganizacionIdAndId(long organizacionId, Long id);

    /** Estado de la publicacion principal de cada propiedad del lote, en una sola consulta. */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (id_propiedad) id_propiedad AS idPropiedad, estado
            FROM publicacion
            WHERE id_propiedad IN (:ids)
            ORDER BY id_propiedad, fecha_publicacion DESC, id_publicacion DESC
            """)
    List<EstadoPublicacionPropiedad> estadosPublicacion(@Param("ids") Collection<Long> ids);
}
