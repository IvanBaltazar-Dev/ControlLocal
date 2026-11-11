package com.controllocal.persistence.repositorio;

import com.controllocal.domain.colaboracion.CanalColaboracion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** Acceso al canal de SU lado sólo después de la autoridad del caso; no directorio de contactos. */
public interface CanalColaboracionRepository extends JpaRepository<CanalColaboracion,Long> {
    Optional<CanalColaboracion> findByOrganizacionIdAndId(long organizacion,long id);
}
