package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ComisionLiquidacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Liquidacion de comision. Cuelga 1:1 del contrato, asi que el acceso se
 * decide sobre el CONTRATO (que ya viene con scope) y aqui basta el tenant.
 */
public interface ComisionLiquidacionRepository extends JpaRepository<ComisionLiquidacion, Long> {

    @Query("""
            select cm from ComisionLiquidacion cm
            where cm.organizacionId = :idOrganizacion and cm.contrato.id = :idContrato
            """)
    Optional<ComisionLiquidacion> porContrato(@Param("idOrganizacion") long idOrganizacion,
                                              @Param("idContrato") long idContrato);

    /** Una sola lectura para una pagina de contratos: evita el N+1 al listar. */
    @Query("""
            select cm from ComisionLiquidacion cm
            where cm.organizacionId = :idOrganizacion and cm.contrato.id in :idsContrato
            """)
    List<ComisionLiquidacion> porContratos(@Param("idOrganizacion") long idOrganizacion,
                                           @Param("idsContrato") Collection<Long> idsContrato);
}
