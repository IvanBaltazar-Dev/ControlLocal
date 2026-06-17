package com.controllocal.bl.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BrokerBusinessLogic;
import com.controllocal.bl.BusinessException;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.BrokerAgenteDAO;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.ReasignacionAgenteBrokerDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.BrokerAgenteDAOImpl;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.dao.impl.ReasignacionAgenteBrokerDAOImpl;
import com.controllocal.model.persona.enums.EstadoActivoInactivo;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.BrokerAgente;
import com.controllocal.model.usuario.ReasignacionAgenteBroker;

public class BrokerBusinessLogicImpl implements BrokerBusinessLogic {

    private final BrokerDAO brokerDAO;
    private final AgenteInmobiliarioDAO agenteDAO;
    private final BrokerAgenteDAO brokerAgenteDAO;
    private final ReasignacionAgenteBrokerDAO reasignacionDAO;

    public BrokerBusinessLogicImpl() {
        this(new BrokerDAOImpl(), new AgenteInmobiliarioDAOImpl(), new BrokerAgenteDAOImpl(), new ReasignacionAgenteBrokerDAOImpl());
    }

    public BrokerBusinessLogicImpl(BrokerDAO brokerDAO) {
        this(brokerDAO, new AgenteInmobiliarioDAOImpl(), new BrokerAgenteDAOImpl(), new ReasignacionAgenteBrokerDAOImpl());
    }

    public BrokerBusinessLogicImpl(BrokerDAO brokerDAO, AgenteInmobiliarioDAO agenteDAO, BrokerAgenteDAO brokerAgenteDAO) {
        this(brokerDAO, agenteDAO, brokerAgenteDAO, new ReasignacionAgenteBrokerDAOImpl());
    }

    public BrokerBusinessLogicImpl(BrokerDAO brokerDAO, AgenteInmobiliarioDAO agenteDAO, BrokerAgenteDAO brokerAgenteDAO,
                                   ReasignacionAgenteBrokerDAO reasignacionDAO) {
        this.brokerDAO = brokerDAO;
        this.agenteDAO = agenteDAO;
        this.brokerAgenteDAO = brokerAgenteDAO;
        this.reasignacionDAO = reasignacionDAO;
    }

    public Long registrarBroker(Long idBrokerAdministrador, Broker broker) {
        return TransactionRunner.write(conn -> {
            validarBrokerAdministrador(idBrokerAdministrador);
            validarUnicoBrokerAdministrador(broker, null);
            BusinessValidations.broker(broker);
            return brokerDAO.crear(broker);
        });
    }

    public Long registrarPrimerBrokerAdministrador(Broker broker) {
        return TransactionRunner.write(conn -> {
            if (existeBrokerAdministrador(null)) {
                throw new BusinessException("Ya existe un broker administrador.");
            }
            broker.setEsAdministrador(true);
            BusinessValidations.broker(broker);
            return brokerDAO.crear(broker);
        });
    }

    public Optional<Broker> buscarPorId(Long idBroker) {
        BusinessValidations.id(idBroker, "El id de broker");
        return brokerDAO.buscarPorId(idBroker);
    }

    public Optional<Broker> buscarPorUsuario(Long idUsuario) {
        BusinessValidations.id(idUsuario, "El id de usuario");
        return brokerDAO.buscarPorUsuario(idUsuario);
    }

    public List<Broker> listarTodos() {
        return brokerDAO.listarTodos();
    }

    public boolean actualizarBroker(Long idBrokerAdministrador, Broker broker) {
        return TransactionRunner.write(conn -> {
            validarBrokerAdministrador(idBrokerAdministrador);
            BusinessValidations.id(broker != null ? broker.getIdBroker() : null, "El id de broker");
            validarUnicoBrokerAdministrador(broker, broker.getIdBroker());
            BusinessValidations.broker(broker);
            return brokerDAO.actualizar(broker);
        });
    }

    public boolean desactivarBroker(Long idBrokerAdministrador, Long idBroker) {
        return TransactionRunner.write(conn -> {
            validarBrokerAdministrador(idBrokerAdministrador);
            BusinessValidations.id(idBroker, "El id de broker");
            return brokerDAO.eliminar(idBroker);
        });
    }

    public Long asignarAgente(Long idBrokerAdministrador, Long idBrokerSupervisor, Long idAgente, String motivo) {
        return TransactionRunner.write(conn -> {
            Broker administrador = validarBrokerAdministrador(idBrokerAdministrador);
            BusinessValidations.texto(motivo, "El motivo de reasignacion de agente");
            Broker brokerSupervisor = validarBroker(idBrokerSupervisor);
            if (brokerSupervisor.isEsAdministrador()) {
                throw new BusinessException("El broker administrador no requiere asignacion de agentes para supervisar.");
            }
            AgenteInmobiliario agente = agenteDAO.buscarPorId(idAgente)
                    .orElseThrow(() -> new BusinessException("Agente no encontrado."));
            BusinessValidations.agenteDisponible(agente);
            Broker brokerAnterior = null;
            Optional<BrokerAgente> asignacionActiva = brokerAgenteDAO.buscarActivoPorAgente(idAgente);
            if (asignacionActiva.isPresent()) {
                BrokerAgente asignacion = asignacionActiva.get();
                if (idBrokerSupervisor.equals(asignacion.getIdBroker())) {
                    throw new BusinessException("El agente ya esta asignado a ese broker supervisor.");
                }
                brokerAnterior = asignacion.getBroker();
                brokerAgenteDAO.eliminar(asignacion.getIdBrokerAgente());
            }

            BrokerAgente brokerAgente = new BrokerAgente();
            brokerAgente.setBroker(brokerSupervisor);
            brokerAgente.setAgente(agente);
            brokerAgente.setFechaAsignacion(LocalDate.now());
            brokerAgente.setMotivo(motivo);
            brokerAgente.setEstado(EstadoActivoInactivo.ACTIVO);
            BusinessValidations.brokerAgente(brokerAgente);
            Long idAsignacion = brokerAgenteDAO.crear(brokerAgente);

            // Traza historica del cambio (broker anterior -> nuevo, autorizado por el admin).
            ReasignacionAgenteBroker reasignacion = new ReasignacionAgenteBroker();
            reasignacion.setAgente(agente);
            reasignacion.setBrokerAnterior(brokerAnterior);
            reasignacion.setBrokerNuevo(brokerSupervisor);
            reasignacion.setBrokerAdministrador(administrador);
            reasignacion.setMotivo(motivo);
            reasignacion.registrarCambio();
            reasignacionDAO.crear(reasignacion);

            return idAsignacion;
        });
    }

    public boolean desactivarAsignacionAgente(Long idBrokerAdministrador, Long idBrokerAgente) {
        return TransactionRunner.write(conn -> {
            validarBrokerAdministrador(idBrokerAdministrador);
            BusinessValidations.id(idBrokerAgente, "El id de asignacion broker-agente");
            return brokerAgenteDAO.eliminar(idBrokerAgente);
        });
    }

    public List<BrokerAgente> listarAgentesSupervisados(Long idBroker) {
        Broker broker = validarBroker(idBroker);
        if (broker.isEsAdministrador()) {
            return brokerAgenteDAO.listarTodos();
        }
        return brokerAgenteDAO.listarActivosPorBroker(idBroker);
    }

    public List<ReasignacionAgenteBroker> listarReasignacionesAgenteBroker() {
        return reasignacionDAO.listarTodos();
    }

    public boolean puedeSupervisarAgente(Long idBroker, Long idAgente) {
        Broker broker = validarBroker(idBroker);
        BusinessValidations.id(idAgente, "El id de agente");
        return broker.isEsAdministrador() || brokerAgenteDAO.existeAsignacionActiva(idBroker, idAgente);
    }

    public Broker validarBroker(Long idBroker) {
        BusinessValidations.id(idBroker, "El id de broker");
        Broker broker = brokerDAO.buscarPorId(idBroker)
                .orElseThrow(() -> new BusinessException("Broker no encontrado."));
        BusinessValidations.brokerValido(broker);
        return broker;
    }

    public Broker validarBrokerAdministrador(Long idBrokerAdministrador) {
        Broker broker = validarBroker(idBrokerAdministrador);
        BusinessValidations.brokerAdministrador(broker);
        return broker;
    }

    private void validarUnicoBrokerAdministrador(Broker broker, Long idBrokerActual) {
        if (broker != null && broker.isEsAdministrador() && existeBrokerAdministrador(idBrokerActual)) {
            throw new BusinessException("Solo debe existir un broker administrador.");
        }
    }

    private boolean existeBrokerAdministrador(Long idBrokerExcluido) {
        return brokerDAO.listarTodos().stream()
                .anyMatch(broker -> broker.isEsAdministrador()
                        && (idBrokerExcluido == null || !idBrokerExcluido.equals(broker.getIdBroker())));
    }
}
