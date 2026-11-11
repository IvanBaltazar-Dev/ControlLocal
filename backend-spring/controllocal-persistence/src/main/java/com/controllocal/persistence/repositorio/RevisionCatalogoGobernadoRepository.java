package com.controllocal.persistence.repositorio;

import com.controllocal.domain.inmueble.RevisionCatalogoGobernado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** Historial inmutable por recurso/version, siempre dentro de su organización. */
public interface RevisionCatalogoGobernadoRepository extends JpaRepository<RevisionCatalogoGobernado,Long> {
    Optional<RevisionCatalogoGobernado> findByOrganizacionIdAndClaseRecursoAndIdRecursoAndVersionDefinicion(
            Long organizacionId,String claseRecurso,Long idRecurso,long versionDefinicion);
}
