package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.PrecioPropiedad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrecioPropiedadRepository extends JpaRepository<PrecioPropiedad, Long> {

    /** Historico cronologico del local (mismo orden que la v1: fecha, id). */
    List<PrecioPropiedad> findByIdPropiedadOrderByFechaAscIdAsc(long idPropiedad);

    /**
     * Ultimo hito de un tipo. Lo usa E0.2 para no duplicar la renta publicada:
     * ordena por id ademas de por fecha porque varios hitos del mismo dia son
     * normales —una edicion de precio y su propagacion a la publicacion caen
     * ambas hoy— y solo el id los desempata en el orden real de escritura.
     */
    Optional<PrecioPropiedad> findFirstByIdPropiedadAndHitoOrderByFechaDescIdDesc(
            long idPropiedad, String hito);
}
