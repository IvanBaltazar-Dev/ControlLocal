package com.controllocal.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Prospeccion;

public interface ProspeccionDAO extends CrudDAO<Prospeccion> {
    Long crear(Prospeccion prospeccion);
    Optional<Prospeccion> buscarPorId(Long id);
    List<Prospeccion> listarTodos();
    boolean actualizar(Prospeccion prospeccion);
    boolean eliminar(Long id);

    /** Prospecciones EN_SEGUIMIENTO cuyo recontacto vence en/antes de {@code limite}. */
    List<Prospeccion> listarPorRecontactar(LocalDate limite);
}
