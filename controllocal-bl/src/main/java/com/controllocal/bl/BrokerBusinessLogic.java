package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.usuario.Broker;

public interface BrokerBusinessLogic {

    public Long registrarBroker(Long idBrokerAdministrador, Broker broker);
    public Long registrarPrimerBrokerAdministrador(Broker broker);
    public Optional<Broker> buscarPorId(Long idBroker);
    public List<Broker> listarTodos();
    public boolean actualizarBroker(Long idBrokerAdministrador, Broker broker);
    public boolean desactivarBroker(Long idBrokerAdministrador, Long idBroker);
    public Broker validarBroker(Long idBroker);
    public Broker validarBrokerAdministrador(Long idBrokerAdministrador);
}

