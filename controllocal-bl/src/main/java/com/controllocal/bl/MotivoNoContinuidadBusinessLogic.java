package com.controllocal.bl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.MotivoNoContinuidad;

public interface MotivoNoContinuidadBusinessLogic {

    public Long registrar(MotivoNoContinuidad motivo);
    public Optional<MotivoNoContinuidad> buscarPorId(Long idMotivo);
    public List<MotivoNoContinuidad> listarTodos();
    public boolean actualizar(MotivoNoContinuidad motivo);
    public boolean eliminar(Long idMotivo);
}

