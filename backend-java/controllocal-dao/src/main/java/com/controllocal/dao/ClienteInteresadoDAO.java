package com.controllocal.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.persona.ClienteInteresado;

public interface ClienteInteresadoDAO extends CrudDAO<ClienteInteresado> {
    Long crear(ClienteInteresado cliente);
    Optional<ClienteInteresado> buscarPorId(Long id);
    List<ClienteInteresado> listarTodos();
    // Carga en bloque solo los clientes pedidos (paginar el alcance del broker sin traer todo).
    List<ClienteInteresado> listarPorIds(Collection<Long> ids);
    boolean actualizar(ClienteInteresado cliente);
    boolean eliminar(Long id);
}
