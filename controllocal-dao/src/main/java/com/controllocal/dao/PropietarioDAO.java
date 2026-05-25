package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.persona.Propietario;

public interface PropietarioDAO extends CrudDAO<Propietario> {
    Long crear(Propietario propietario);

    Optional<Propietario> buscarPorId(Long id);

    List<Propietario> listarTodos();

    boolean actualizar(Propietario propietario);

    boolean eliminar(Long id);
}
