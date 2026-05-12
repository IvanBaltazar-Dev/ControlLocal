package com.controllocal.dao;

import com.controllocal.model.persona.Propietario;

import java.util.List;
import java.util.Optional;

public interface PropietarioDAO {
    Long crear(Propietario propietario);

    Optional<Propietario> buscarPorId(Long id);

    List<Propietario> listarTodos();

    boolean actualizar(Propietario propietario);

    boolean eliminar(Long id);
}
