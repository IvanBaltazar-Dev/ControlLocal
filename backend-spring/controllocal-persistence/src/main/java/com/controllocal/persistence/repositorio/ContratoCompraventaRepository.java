package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ContratoCompraventa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Los cierres de venta, con el tenant por delante (V6).
 *
 * <p>La unicidad real —un cierre por mesa y uno por oferta— la imponen
 * {@code uq_compraventa_oportunidad} y {@code uq_compraventa_oferta} en la base.
 * Estas consultas sirven para responder antes con un mensaje util; no son la
 * garantia.
 */
public interface ContratoCompraventaRepository extends JpaRepository<ContratoCompraventa, Long> {

    @Query("""
            select c from ContratoCompraventa c
             where c.organizacionId = :idOrganizacion
               and c.idOportunidad = :idOportunidad
            """)
    Optional<ContratoCompraventa> deLaMesa(@Param("idOrganizacion") long idOrganizacion,
                                           @Param("idOportunidad") long idOportunidad);

    @Query("""
            select c from ContratoCompraventa c
             where c.organizacionId = :idOrganizacion
               and c.idCaptacion = :idCaptacion
             order by c.id desc
            """)
    List<ContratoCompraventa> delEncargo(@Param("idOrganizacion") long idOrganizacion,
                                         @Param("idCaptacion") long idCaptacion);
}
