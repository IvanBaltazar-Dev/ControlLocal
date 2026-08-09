package com.controllocal.persistence.repositorio;

import com.controllocal.domain.comercial.ComisionMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ComisionMovimientoRepository extends JpaRepository<ComisionMovimiento, Long> {
    List<ComisionMovimiento> findByOrganizacionIdAndLiquidacionIdOrderByFechaAscIdAsc(
            long organizacionId, long idLiquidacion);

    List<ComisionMovimiento> findByOrganizacionIdAndLiquidacionIdIn(
            long organizacionId, Collection<Long> idsLiquidacion);

    /**
     * El movimiento que ya creo este comando, si existe. La unicidad la
     * garantiza {@code uq_movimiento_idempotencia}; esta lectura solo resuelve
     * el caso normal sin llegar a intentar el INSERT.
     */
    java.util.Optional<ComisionMovimiento> findByOrganizacionIdAndClaveIdempotencia(
            long organizacionId, String claveIdempotencia);
}
