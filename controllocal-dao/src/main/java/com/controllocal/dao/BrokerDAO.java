package com.controllocal.dao;

import com.controllocal.model.usuario.Broker;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de persistencia para la entidad Broker.
 */
public interface BrokerDAO {

    Long crear(Broker broker);

    Optional<Broker> buscarPorId(Long id);

    List<Broker> listarTodos();

    boolean actualizar(Broker broker);

    boolean eliminar(Long id);
}
