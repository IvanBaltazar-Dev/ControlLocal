package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.MotivoNoContinuidad;

public interface MotivoNoContinuidadDAO extends CrudDAO<MotivoNoContinuidad> {
    Long crear(MotivoNoContinuidad motivo);
    Optional<MotivoNoContinuidad> buscarPorId(Long id);
    List<MotivoNoContinuidad> listarTodos();
    boolean actualizar(MotivoNoContinuidad motivo);
    boolean eliminar(Long id);
}
