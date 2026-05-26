package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.usuario.Broker;

/**
 * Contrato de persistencia para la entidad Broker.
 */
public interface BrokerDAO extends CrudDAO<Broker> {

    Long crear(Broker broker);

    Optional<Broker> buscarPorId(Long id);

    List<Broker> listarTodos();

    boolean actualizar(Broker broker);

    boolean eliminar(Long id);
}
