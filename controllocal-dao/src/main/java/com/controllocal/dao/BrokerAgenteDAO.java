package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.usuario.BrokerAgente;

public interface BrokerAgenteDAO extends CrudDAO<BrokerAgente> {

    Long crear(BrokerAgente brokerAgente);

    Optional<BrokerAgente> buscarPorId(Long id);

    List<BrokerAgente> listarTodos();

    List<BrokerAgente> listarActivosPorBroker(Long idBroker);

    Optional<BrokerAgente> buscarActivoPorAgente(Long idAgente);

    boolean existeAsignacionActiva(Long idBroker, Long idAgente);

    boolean actualizar(BrokerAgente brokerAgente);

    boolean eliminar(Long id);
}
