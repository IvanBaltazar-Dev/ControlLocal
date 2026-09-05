package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Las ofertas de una mesa, con el tenant siempre por delante (V6).
 *
 * <p>Todas las consultas llevan {@code organizacionId} en el {@code where} y no
 * como comprobacion posterior: una oferta de otra corredora tiene que
 * comportarse como inexistente —404—, no como un 403 que confirma que existe.
 */
public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    /** La cadena completa de una mesa, en el orden en que ocurrio. */
    @Query("""
            select o from Oferta o
             where o.organizacionId = :idOrganizacion
               and o.idOportunidad = :idOportunidad
             order by o.ronda asc, o.id asc
            """)
    List<Oferta> deLaMesa(@Param("idOrganizacion") long idOrganizacion,
                          @Param("idOportunidad") long idOportunidad);

    /** Una oferta concreta dentro del tenant. */
    @Query("""
            select o from Oferta o
             where o.organizacionId = :idOrganizacion
               and o.id = :idOferta
            """)
    Optional<Oferta> buscarFicha(@Param("idOrganizacion") long idOrganizacion,
                                 @Param("idOferta") long idOferta);

    /**
     * Una oferta de la mesa en un estado dado. El codigo entra por parametro y
     * no como literal dentro del JPQL a proposito: los estados los declara
     * {@code Oferta} y una {@code 'P'} escrita aqui seria una segunda copia del
     * vocabulario, invisible para quien lo cambie.
     */
    @Query("""
            select o from Oferta o
             where o.organizacionId = :idOrganizacion
               and o.idOportunidad = :idOportunidad
               and o.estado = :estado
            """)
    Optional<Oferta> unaEnEstado(@Param("idOrganizacion") long idOrganizacion,
                                 @Param("idOportunidad") long idOportunidad,
                                 @Param("estado") String estado);

    /**
     * La que esta sobre la mesa ahora. Como mucho hay una: lo impone el indice
     * parcial {@code uq_oferta_viva_por_oportunidad}, no solo esta consulta.
     */
    default Optional<Oferta> vivaDe(long idOrganizacion, long idOportunidad) {
        return unaEnEstado(idOrganizacion, idOportunidad, Oferta.PRESENTADA);
    }

    /**
     * La aceptada de una mesa, si la hay. Como mucho una: lo impone el indice
     * parcial {@code uq_oferta_aceptada_por_oportunidad}.
     *
     * <p>La usa el cierre de venta para saber que se firma, y tambien el cierre
     * de ALQUILER para poder atar su hito {@code C} a la mesa que lo produjo —
     * sin eso, la serie de un encargo alquilado despues de negociar no se puede
     * leer por negociacion, y la de una venta si: dos verdades para el mismo
     * dato.
     */
    default Optional<Oferta> aceptadaDe(long idOrganizacion, long idOportunidad) {
        return unaEnEstado(idOrganizacion, idOportunidad, Oferta.ACEPTADA);
    }

    /** La ronda mas alta ya usada en esta mesa; {@code null} si no hay ninguna. */
    @Query("""
            select max(o.ronda) from Oferta o
             where o.organizacionId = :idOrganizacion
               and o.idOportunidad = :idOportunidad
            """)
    Integer ultimaRondaDe(@Param("idOrganizacion") long idOrganizacion,
                          @Param("idOportunidad") long idOportunidad);

    /**
     * Todas las ofertas de un ENCARGO, de la mas reciente a la mas antigua.
     * Es la lectura por encargo —no por mesa— que la ficha de la propiedad
     * necesita para contar la negociacion completa de un inmueble.
     */
    @Query("""
            select o from Oferta o
             where o.organizacionId = :idOrganizacion
               and o.idCaptacion = :idCaptacion
             order by o.id desc
            """)
    List<Oferta> delEncargo(@Param("idOrganizacion") long idOrganizacion,
                            @Param("idCaptacion") long idCaptacion);
}
