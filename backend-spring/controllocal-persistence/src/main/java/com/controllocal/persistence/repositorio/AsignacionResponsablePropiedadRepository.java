package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.AsignacionResponsablePropiedad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * El rastro de traspasos de responsable (V87).
 *
 * <p>Solo lectura y alta: no hay {@code delete} ni {@code update} declarados
 * porque la tabla es append-only por decision, no por casualidad.
 */
public interface AsignacionResponsablePropiedadRepository
        extends JpaRepository<AsignacionResponsablePropiedad, Long> {

    /** Traspasos de una propiedad, el mas reciente primero (timeline). */
    List<AsignacionResponsablePropiedad>
            findByOrganizacionIdAndIdPropiedadOrderByFechaAsignacionDescIdDesc(
                    long idOrganizacion, long idPropiedad);
}
