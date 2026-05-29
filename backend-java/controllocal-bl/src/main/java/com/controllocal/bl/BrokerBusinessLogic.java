package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.usuario.BrokerAgente;
import com.controllocal.model.usuario.Broker;

public interface BrokerBusinessLogic {

    public Long registrarBroker(Long idBrokerAdministrador, Broker broker);
    public Long registrarPrimerBrokerAdministrador(Broker broker);
    public Optional<Broker> buscarPorId(Long idBroker);
    public List<Broker> listarTodos();
    public boolean actualizarBroker(Long idBrokerAdministrador, Broker broker);
    public boolean desactivarBroker(Long idBrokerAdministrador, Long idBroker);
    public Long asignarAgente(Long idBrokerAdministrador, Long idBrokerSupervisor, Long idAgente, String motivo);
    public boolean desactivarAsignacionAgente(Long idBrokerAdministrador, Long idBrokerAgente);
    public List<BrokerAgente> listarAgentesSupervisados(Long idBroker);
    public boolean puedeSupervisarAgente(Long idBroker, Long idAgente);
    public Broker validarBroker(Long idBroker);
    public Broker validarBrokerAdministrador(Long idBrokerAdministrador);
}

