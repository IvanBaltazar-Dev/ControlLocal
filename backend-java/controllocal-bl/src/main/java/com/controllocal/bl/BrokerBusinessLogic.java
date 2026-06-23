package com.controllocal.bl;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.usuario.BrokerAgente;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.ReasignacionAgenteBroker;

public interface BrokerBusinessLogic {

    public Long registrarBroker(Long idBrokerAdministrador, Broker broker);
    public Long registrarPrimerBrokerAdministrador(Broker broker);
    public Optional<Broker> buscarPorId(Long idBroker);
    public Optional<Broker> buscarPorUsuario(Long idUsuario);
    public List<Broker> listarTodos();
    public boolean actualizarBroker(Long idBrokerAdministrador, Broker broker);
    // Alta/edicion atomica del broker con su persona y usuario interno (1 transaccion).
    public Long registrarBrokerCompleto(Long idBrokerAdministrador, Broker broker);
    public boolean actualizarBrokerCompleto(Long idBrokerAdministrador, Broker broker);
    // Supervision activa de un agente (el broker que lo tiene asignado ahora).
    public Optional<BrokerAgente> buscarBrokerActivoDeAgente(Long idAgente);
    public boolean desactivarBroker(Long idBrokerAdministrador, Long idBroker);
    public Long asignarAgente(Long idBrokerAdministrador, Long idBrokerSupervisor, Long idAgente, String motivo);
    public boolean desactivarAsignacionAgente(Long idBrokerAdministrador, Long idBrokerAgente);
    public List<BrokerAgente> listarAgentesSupervisados(Long idBroker);
    public List<ReasignacionAgenteBroker> listarReasignacionesAgenteBroker();
    public boolean puedeSupervisarAgente(Long idBroker, Long idAgente);
    public Broker validarBroker(Long idBroker);
    public Broker validarBrokerAdministrador(Long idBrokerAdministrador);
}

