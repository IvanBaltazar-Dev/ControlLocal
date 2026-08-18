package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.TitularidadPropiedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Titularidad de las propiedades (D-E4-1 M1).
 *
 * <p>Todas las consultas filtran por {@code vigenteHasta is null} salvo el
 * historico: una titularidad cerrada sigue en la tabla a proposito — es la
 * historia de la propiedad — y devolverla como si fuera actual seria un error
 * de alcance, no de rendimiento.
 */
public interface TitularidadPropiedadRepository extends JpaRepository<TitularidadPropiedad, Long> {

    /** Los titulares vigentes de una propiedad, el representante primero. */
    @Query("""
            select t from TitularidadPropiedad t
            where t.idPropiedad = :idPropiedad and t.vigenteHasta is null
            order by t.esRepresentante desc, t.cuota desc, t.id asc
            """)
    List<TitularidadPropiedad> vigentesDe(@Param("idPropiedad") long idPropiedad);

    /** Con quien se habla de esta propiedad. */
    @Query("""
            select t from TitularidadPropiedad t
            where t.idPropiedad = :idPropiedad and t.vigenteHasta is null and t.esRepresentante = true
            """)
    Optional<TitularidadPropiedad> representanteDe(@Param("idPropiedad") long idPropiedad);

    /**
     * Titulares vigentes de un lote de propiedades, en una sola consulta. Lo usa
     * el listado: sin esto, pintar 50 filas con su propietario son 50 consultas
     * — el N+1 que RC-003 vino a quitar.
     */
    @Query("""
            select t from TitularidadPropiedad t
            where t.idPropiedad in :ids and t.vigenteHasta is null
            order by t.idPropiedad asc, t.esRepresentante desc, t.id asc
            """)
    List<TitularidadPropiedad> vigentesDeVarias(@Param("ids") Collection<Long> ids);

    /** Las propiedades de las que esta persona es titular hoy. */
    @Query("""
            select t from TitularidadPropiedad t
            where t.organizacionId = :idOrganizacion
              and t.rolPropietario.id = :idRolPropietario
              and t.vigenteHasta is null
            order by t.idPropiedad asc
            """)
    List<TitularidadPropiedad> vigentesDeTitular(@Param("idOrganizacion") long idOrganizacion,
                                                 @Param("idRolPropietario") long idRolPropietario);

    /** Toda la historia de titularidad, incluida la cerrada, en orden. */
    @Query("""
            select t from TitularidadPropiedad t
            where t.idPropiedad = :idPropiedad
            order by t.vigenteDesde asc, t.id asc
            """)
    List<TitularidadPropiedad> historicoDe(@Param("idPropiedad") long idPropiedad);

    /**
     * Suma de las cuotas vigentes. La BD ya impide que no sea 100 —constraint
     * trigger diferido de V47—, pero el servicio la necesita ANTES de escribir
     * para poder decir "te faltan 30" en vez de dejar que estalle el COMMIT.
     */
    @Query("""
            select coalesce(sum(t.cuota), 0) from TitularidadPropiedad t
            where t.idPropiedad = :idPropiedad and t.vigenteHasta is null
            """)
    BigDecimal cuotaVigenteDe(@Param("idPropiedad") long idPropiedad);

    boolean existsByIdPropiedadAndVigenteHastaIsNull(long idPropiedad);
}
