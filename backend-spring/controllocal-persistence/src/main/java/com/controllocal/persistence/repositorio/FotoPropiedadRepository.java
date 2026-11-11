package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.FotoPropiedad;
import com.controllocal.persistence.query.PortadaPropiedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FotoPropiedadRepository extends JpaRepository<FotoPropiedad, Long> {

    @Query("select f.fechaRegistro from FotoPropiedad f where f.organizacionId=:org and f.archivoRef=:referencia")
    java.util.Optional<java.time.OffsetDateTime> fechaRegistroOriginal(@Param("org")long org,@Param("referencia")java.util.UUID referencia);

    /**
     * <b>La galeria de una propiedad DE ESTA organizacion</b>, en su orden.
     *
     * <p>Sustituye a {@code findByIdPropiedadOrderByOrdenAscIdAsc}, que
     * resolvia por {@code id_propiedad} a secas. La foto es un hecho privado del
     * tenant —y su {@code clave} es lo que hace falta para pedirle el binario al
     * almacen—, asi que una consulta sin discriminador convertia acertar un id
     * en leer la galeria de otra corredora.
     *
     * <p>El orden es {@code (orden, id_foto)} y no solo {@code orden}: el
     * desempate por id deja el resultado estable si dos filas compartieran
     * posicion — cosa que el invariante 1..N impide, pero que una lectura no
     * puede dar por supuesta.
     */
    @Query("select f from FotoPropiedad f where f.organizacionId=:organizacionId and f.idPropiedad=:idPropiedad "
            + "and f.estado='A' order by f.orden,f.id")
    List<FotoPropiedad> deLaPropiedad(@Param("organizacionId") long organizacionId,
                                     @Param("idPropiedad") long idPropiedad);

    /** Comprobación fresca bajo el candado de la propiedad; no confía en una foto gestionada antes. */
    @Query(nativeQuery = true, value = "select exists(select 1 from foto_propiedad "
            + "where organizacion_id=:organizacion and id_foto=:id and estado='A')")
    boolean estaActiva(@Param("organizacion") long organizacion, @Param("id") long id);

    /** Portada (primera foto por orden) de cada propiedad del lote, en una sola consulta. */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (id_propiedad) id_propiedad AS idPropiedad, CAST(archivo_ref AS text) AS clave
            FROM foto_propiedad
            WHERE id_propiedad IN (:ids) AND estado = 'A'
            ORDER BY id_propiedad, orden, id_foto
            """)
    List<PortadaPropiedad> portadas(@Param("ids") Collection<Long> ids);
}
