package com.controllocal.dao;

import com.controllocal.model.comercial.Captacion;

import java.util.List;
import java.util.Optional;

public interface CaptacionDAO {
    Long crear(Captacion captacion);

    Optional<Captacion> buscarPorId(Long id);

    List<Captacion> listarTodos();

    boolean actualizar(Captacion captacion);

    boolean eliminar(Long id);
}
