package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.AtributoPropiedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Los valores de las caracteristicas de cada propiedad (D-E4-1 M2).
 *
 * <p>La consulta que sostiene el matcher es {@link #idsQueCumplenNumero}: sin
 * ella, "propiedades con 3 dormitorios o mas" obligaria a traer todas las
 * propiedades y filtrar en memoria. Con el indice parcial de V48
 * ({@code organizacion_id, clave, valor_numero}) se resuelve en el indice.
 */
public interface AtributoPropiedadRepository extends JpaRepository<AtributoPropiedad, Long> {

    List<AtributoPropiedad> findByIdPropiedadOrderByClaveAsc(long idPropiedad);

    Optional<AtributoPropiedad> findByIdPropiedadAndClave(long idPropiedad, String clave);

    /** De un lote de propiedades, en una sola consulta: el listado los pinta juntos. */
    @Query("""
            select a from AtributoPropiedad a
            where a.idPropiedad in :ids
            order by a.idPropiedad asc, a.clave asc
            """)
    List<AtributoPropiedad> deVarias(@Param("ids") Collection<Long> ids);

    /**
     * Que propiedades cumplen un criterio numerico. El operador va fuera: la
     * comparacion la decide el criterio del requerimiento (INDISPENSABLE o
     * DESEABLE), no el repositorio.
     */
    @Query("""
            select a.idPropiedad from AtributoPropiedad a
            where a.organizacionId = :idOrganizacion
              and a.clave = :clave
              and a.valorNumero is not null
              and a.valorNumero >= :minimo
            """)
    List<Long> idsQueCumplenNumero(@Param("idOrganizacion") long idOrganizacion,
                                   @Param("clave") String clave,
                                   @Param("minimo") BigDecimal minimo);

    /** Lo mismo para un valor exacto de texto: rubro, zonificacion. */
    @Query("""
            select a.idPropiedad from AtributoPropiedad a
            where a.organizacionId = :idOrganizacion
              and a.clave = :clave
              and lower(a.valorTexto) = lower(:valor)
            """)
    List<Long> idsQueCumplenTexto(@Param("idOrganizacion") long idOrganizacion,
                                  @Param("clave") String clave,
                                  @Param("valor") String valor);

    /**
     * Que claves obligatorias le faltan a una propiedad. Es lo que permite al
     * motor de captura decir "me falta el metraje" en vez de fallar al guardar,
     * y a la ficha avisar de que no se puede publicar todavia.
     *
     * <p><b>Solo mira las de autoridad ATRIBUTO</b> (D-E4-3). Una clave
     * declarada ESTRUCTURAL no guarda fila aqui —su valor vive en el campo
     * canonico del agregado—, asi que buscarla en esta tabla la daria por
     * faltante siempre. Las estructurales las comprueba
     * {@code AtributosGobernados} contra su propio campo.
     */
    @Query("""
            select c.clave from CatalogoAtributo c join c.aplicaciones a
            where c.activo = true
              and a.requerido = true
              and a.tipoPropiedad = :tipoPropiedad
              and (c.organizacionId is null or c.organizacionId = :idOrganizacion)
              and c.destino = 'ATRIBUTO'
              and not exists (select 1 from AtributoPropiedad ap
                               where ap.idPropiedad = :idPropiedad and ap.clave = c.clave)
            order by c.orden asc
            """)
    List<String> clavesObligatoriasQueFaltan(@Param("idOrganizacion") long idOrganizacion,
                                             @Param("idPropiedad") long idPropiedad,
                                             @Param("tipoPropiedad") String tipoPropiedad);

    void deleteByIdPropiedadAndClave(long idPropiedad, String clave);
}
