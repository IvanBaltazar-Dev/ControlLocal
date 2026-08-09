package com.controllocal.persistence.repositorio;

import com.controllocal.domain.auditoria.HistorialEstado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialEstadoRepository extends JpaRepository<HistorialEstado, Long> {

    /** Timeline de una entidad (usa el indice ix_historial_entidad). */
    List<HistorialEstado> findByEntidadTipoAndIdEntidadOrderByFechaEventoDesc(String entidadTipo, Long idEntidad);
}
