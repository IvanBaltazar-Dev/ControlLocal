package com.controllocal.persistence.repositorio;

import com.controllocal.domain.captura.BorradorCaptura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Los borradores de captura (D-E4-2, V56).
 *
 * <p>Las dos consultas que importan son de signo opuesto y por eso tienen
 * indices distintos en V56: {@link #enCursoDe} es "que dejaste a medias" y va
 * contra un indice PARCIAL —solo los vivos, asi que no crece con los ya
 * ejecutados—; {@link #porObjetivo} es "de donde salio esta propiedad" y va
 * contra el indice de la entidad producida.
 */
public interface BorradorCapturaRepository extends JpaRepository<BorradorCaptura, Long> {

    /** Lo que esta persona dejo empezado, lo mas reciente primero. */
    @Query("""
            select b from BorradorCaptura b
            where b.organizacionId = :idOrganizacion
              and b.idPersonaRol = :idPersonaRol
              and b.estado = 'E'
            order by b.actualizadoEn desc
            """)
    List<BorradorCaptura> enCursoDe(@Param("idOrganizacion") long idOrganizacion,
                                    @Param("idPersonaRol") long idPersonaRol);

    /**
     * Todo lo que el tenant tiene a medias, sin importar quien lo empezo. Es lo
     * que permite que una captura iniciada por KAIROS la termine otra persona:
     * el borrador es de la organizacion, no de quien tecleo primero.
     */
    @Query("""
            select b from BorradorCaptura b
            where b.organizacionId = :idOrganizacion and b.estado = 'E'
            order by b.actualizadoEn desc
            """)
    List<BorradorCaptura> enCurso(@Param("idOrganizacion") long idOrganizacion);

    Optional<BorradorCaptura> findByOrganizacionIdAndCodigo(long idOrganizacion, String codigo);

    Optional<BorradorCaptura> findByOrganizacionIdAndId(long idOrganizacion, long id);

    /** De donde salio una entidad: que captura, por que canal y de quien. */
    @Query("""
            select b from BorradorCaptura b
            where b.entidadObjetivoTipo = :entidadTipo and b.entidadObjetivoId = :entidadId
            order by b.actualizadoEn desc
            """)
    List<BorradorCaptura> porObjetivo(@Param("entidadTipo") String entidadTipo,
                                      @Param("entidadId") long entidadId);

    /** Correlativo CAP-#### por organizacion, igual que las captaciones (V6.3). */
    long countByOrganizacionId(long idOrganizacion);
}
