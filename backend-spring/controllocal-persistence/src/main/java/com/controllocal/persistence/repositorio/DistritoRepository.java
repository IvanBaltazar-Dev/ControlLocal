package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.Distrito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DistritoRepository extends JpaRepository<Distrito, Long> {

    /** Catalogo vigente para resolver el nombre escrito (comparacion laxa en el service). */
    List<Distrito> findByActivoTrueOrderByNombre();
}
