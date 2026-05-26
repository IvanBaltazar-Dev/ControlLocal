package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.persona.ClienteInteresado;

public interface ClienteInteresadoDAO extends CrudDAO<ClienteInteresado> {
    Long crear(ClienteInteresado cliente);
    Optional<ClienteInteresado> buscarPorId(Long id);
    List<ClienteInteresado> listarTodos();
    boolean actualizar(ClienteInteresado cliente);
    boolean eliminar(Long id);
}
