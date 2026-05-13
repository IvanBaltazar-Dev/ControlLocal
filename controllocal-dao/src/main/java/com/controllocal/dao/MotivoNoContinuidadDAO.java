package com.controllocal.dao;

import com.controllocal.model.comercial.MotivoNoContinuidad;

import java.util.List;
import java.util.Optional;

public interface MotivoNoContinuidadDAO {
    Long crear(MotivoNoContinuidad motivo);
    Optional<MotivoNoContinuidad> buscarPorId(Long id);
    List<MotivoNoContinuidad> listarTodos();
    boolean actualizar(MotivoNoContinuidad motivo);
    boolean eliminar(Long id);
}
