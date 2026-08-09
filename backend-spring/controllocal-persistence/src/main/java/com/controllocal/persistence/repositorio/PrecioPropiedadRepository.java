package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.PrecioPropiedad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrecioPropiedadRepository extends JpaRepository<PrecioPropiedad, Long> {

    /** Historico cronologico del local (mismo orden que la v1: fecha, id). */
    List<PrecioPropiedad> findByIdPropiedadOrderByFechaAscIdAsc(long idPropiedad);
}
