package com.controllocal.persistence.repositorio;

import com.controllocal.domain.seguridad.AccionRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccionRecuperacionRepository extends JpaRepository<AccionRecuperacion, Long> {

    List<AccionRecuperacion> findByIdConcesion(long idConcesion);

    /**
     * Una accion por tipo y por concesion. La comprobacion se hace aqui para
     * dar un mensaje, y el {@code UNIQUE} de la tabla la garantiza aunque dos
     * llamadas lleguen a la vez — mismo reparto que en el resto del proyecto:
     * la guarda explica, la restriccion asegura.
     */
    boolean existsByIdConcesionAndTipo(long idConcesion, String tipo);
}
