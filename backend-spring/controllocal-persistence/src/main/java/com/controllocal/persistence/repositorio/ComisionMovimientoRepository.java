package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ComisionMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ComisionMovimientoRepository extends JpaRepository<ComisionMovimiento,Long> {
    Optional<ComisionMovimiento> findByOrganizacionIdAndId(long org,long id);
    List<ComisionMovimiento> findByOrganizacionIdAndLiquidacionIdOrderByFechaAscIdAsc(long org,long id);
    List<ComisionMovimiento> findByOrganizacionIdAndLiquidacionIdIn(long org,Collection<Long> ids);
}
