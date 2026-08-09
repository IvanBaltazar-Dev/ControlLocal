package com.controllocal.persistence.repositorio;

import com.controllocal.domain.seguridad.AprobacionRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AprobacionRecuperacionRepository
        extends JpaRepository<AprobacionRecuperacion, Long> {

    /**
     * Las aprobaciones de una concesion, en orden. Dos de custodios distintos
     * son la condicion para pasar a VIGENTE; con una, la concesion sigue
     * PENDIENTE y <b>no autoriza nada</b>.
     */
    List<AprobacionRecuperacion> findByIdConcesionOrderByOrdenAsc(long idConcesion);

    boolean existsByIdConcesionAndIdentificadorCustodio(long idConcesion, String identificador);
}
