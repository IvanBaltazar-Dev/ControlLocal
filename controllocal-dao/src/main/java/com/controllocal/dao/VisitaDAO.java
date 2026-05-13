package com.controllocal.dao;

import com.controllocal.model.comercial.Visita;

import java.util.List;
import java.util.Optional;

public interface VisitaDAO {
    Long crear(Visita visita);
    Optional<Visita> buscarPorId(Long id);
    List<Visita> listarTodos();
    boolean actualizar(Visita visita);
    boolean eliminar(Long id);
}
