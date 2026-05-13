package com.controllocal.dao;

import com.controllocal.model.persona.ClienteInteresado;

import java.util.List;
import java.util.Optional;

public interface ClienteInteresadoDAO {
    Long crear(ClienteInteresado cliente);
    Optional<ClienteInteresado> buscarPorId(Long id);
    List<ClienteInteresado> listarTodos();
    boolean actualizar(ClienteInteresado cliente);
    boolean eliminar(Long id);
}
