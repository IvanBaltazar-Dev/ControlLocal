package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.RevisionDisponibilidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RevisionDisponibilidadRepository extends JpaRepository<RevisionDisponibilidad, Long> {

    /**
     * La revision de un contrato, si ya se hizo. Solo puede haber una
     * ({@code uq_revision_contrato}); esta lectura da la respuesta idempotente
     * sin llegar a chocar contra el indice.
     */
    @Query("""
            select r from RevisionDisponibilidad r
            where r.organizacionId = :idOrganizacion
              and r.idContratoAlquiler = :idContrato
            """)
    Optional<RevisionDisponibilidad> porContrato(@Param("idOrganizacion") long idOrganizacion,
                                                 @Param("idContrato") long idContrato);
}
