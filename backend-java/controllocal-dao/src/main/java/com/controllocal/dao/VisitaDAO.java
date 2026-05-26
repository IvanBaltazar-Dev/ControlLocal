package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Visita;

public interface VisitaDAO extends CrudDAO<Visita> {
    Long crear(Visita visita);
    Optional<Visita> buscarPorId(Long id);
    List<Visita> listarTodos();
    boolean actualizar(Visita visita);
    boolean eliminar(Long id);
}
