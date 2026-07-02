package com.controllocal.bl.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.AgenteBusinessLogic;
import com.controllocal.bl.BusinessException;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.BrokerAgenteDAO;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.PersonaDAO;
import com.controllocal.dao.UsuarioInternoDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.BrokerAgenteDAOImpl;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.dao.impl.PersonaDAOImpl;
import com.controllocal.dao.impl.UsuarioInternoDAOImpl;
import com.controllocal.model.persona.enums.EstadoActivoInactivo;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;
import com.controllocal.model.usuario.BrokerAgente;

public class AgenteBusinessLogicImpl implements AgenteBusinessLogic {

    private final AgenteInmobiliarioDAO agenteDAO;
    private final BrokerDAO brokerDAO;
    private final BrokerAgenteDAO brokerAgenteDAO;
    // Necesarios para el alta/edicion atomica del agente (persona + usuario interno).
    private final PersonaDAO personaDAO;
    private final UsuarioInternoDAO usuarioDAO;

    public AgenteBusinessLogicImpl() {
        this(new AgenteInmobiliarioDAOImpl(), new BrokerDAOImpl(), new BrokerAgenteDAOImpl());
    }

    public AgenteBusinessLogicImpl(AgenteInmobiliarioDAO agenteDAO) {
        this(agenteDAO, new BrokerDAOImpl(), new BrokerAgenteDAOImpl());
    }

    public AgenteBusinessLogicImpl(AgenteInmobiliarioDAO agenteDAO, BrokerDAO brokerDAO, BrokerAgenteDAO brokerAgenteDAO) {
        this(agenteDAO, brokerDAO, brokerAgenteDAO, new PersonaDAOImpl(), new UsuarioInternoDAOImpl());
    }

    public AgenteBusinessLogicImpl(AgenteInmobiliarioDAO agenteDAO, BrokerDAO brokerDAO, BrokerAgenteDAO brokerAgenteDAO,
                                   PersonaDAO personaDAO, UsuarioInternoDAO usuarioDAO) {
        this.agenteDAO = agenteDAO;
        this.brokerDAO = brokerDAO;
        this.brokerAgenteDAO = brokerAgenteDAO;
        this.personaDAO = personaDAO;
        this.usuarioDAO = usuarioDAO;
    }

    public Long registrar(AgenteInmobiliario agente) {
        throw new BusinessException("Debe indicar el broker supervisor para registrar un agente inmobiliario.");
    }

    public Long registrar(Long idBrokerSupervisor, AgenteInmobiliario agente) {
        return TransactionRunner.write(conn -> {
            Broker broker = validarBrokerSupervisorOperativo(idBrokerSupervisor);
            BusinessValidations.agente(agente);
            Long idAgente = agenteDAO.crear(agente);
            crearAsignacionSupervisor(broker, agente);
            return idAgente;
        });
    }

    @Override
    public Long registrarAgenteCompleto(Long idBrokerSupervisor, AgenteInmobiliario agente) {
        return TransactionRunner.write(conn -> {
            Broker broker = validarBrokerSupervisorOperativo(idBrokerSupervisor);
            if (agente == null || agente.getPersona() == null) {
                throw new BusinessException("El agente y su persona son obligatorios.");
            }
            // 1) persona  2) usuario_interno (el agente lo es)  3) agente  4) asignacion — atomico.
            personaDAO.crear(agente.getPersona());
            usuarioDAO.crear(agente);
            BusinessValidations.agente(agente);
            Long idAgente = agenteDAO.crear(agente);
            crearAsignacionSupervisor(broker, agente);
            return idAgente;
        });
    }

    @Override
    public boolean actualizarAgenteCompleto(Long idBrokerSupervisor, AgenteInmobiliario agente) {
        return TransactionRunner.write(conn -> {
            Broker broker = validarBroker(idBrokerSupervisor);
            BusinessValidations.id(agente != null ? agente.getIdAgente() : null, "El id de agente");
            validarAlcanceSobreAgente(broker, agente.getIdAgente());
            if (agente.getPersona() != null) {
                personaDAO.actualizar(agente.getPersona());
            }
            usuarioDAO.actualizar(agente);
            BusinessValidations.agente(agente);
            return agenteDAO.actualizar(agente);
        });
    }

    public Optional<AgenteInmobiliario> buscarPorId(Long idAgente) {
        BusinessValidations.id(idAgente, "El id de agente");
        return agenteDAO.buscarPorId(idAgente);
    }

    public Optional<AgenteInmobiliario> buscarPorUsuario(Long idUsuario) {
        BusinessValidations.id(idUsuario, "El id de usuario");
        return agenteDAO.buscarPorUsuario(idUsuario);
    }

    public List<AgenteInmobiliario> listarTodos() {
        return agenteDAO.listarTodos();
    }

    @Override
    public long contar() {
        return agenteDAO.contar();
    }

    public List<AgenteInmobiliario> listarPorBroker(Long idBroker) {
        Broker broker = validarBroker(idBroker);
        if (broker.isEsAdministrador()) {
            return agenteDAO.listarTodos();
        }
        return brokerAgenteDAO.listarActivosPorBroker(idBroker).stream()
                .map(BrokerAgente::getIdAgente)
                .map(agenteDAO::buscarPorId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public boolean actualizar(AgenteInmobiliario agente) {
        throw new BusinessException("Debe indicar el broker supervisor para actualizar un agente inmobiliario.");
    }

    public boolean actualizar(Long idBrokerSupervisor, AgenteInmobiliario agente) {
        return TransactionRunner.write(conn -> {
            Broker broker = validarBroker(idBrokerSupervisor);
            BusinessValidations.id(agente != null ? agente.getIdAgente() : null, "El id de agente");
            validarAlcanceSobreAgente(broker, agente.getIdAgente());
            BusinessValidations.agente(agente);
            return agenteDAO.actualizar(agente);
        });
    }

    public boolean desactivar(Long idAgente) {
        throw new BusinessException("Debe indicar el broker supervisor para desactivar un agente inmobiliario.");
    }

    public boolean desactivar(Long idBrokerSupervisor, Long idAgente) {
        return TransactionRunner.write(conn -> {
            Broker broker = validarBroker(idBrokerSupervisor);
            BusinessValidations.id(idAgente, "El id de agente");
            validarAlcanceSobreAgente(broker, idAgente);
            brokerAgenteDAO.buscarActivoPorAgente(idAgente).ifPresent(asignacion ->
                    brokerAgenteDAO.eliminar(asignacion.getIdBrokerAgente()));
            return agenteDAO.eliminar(idAgente);
        });
    }

    private Broker validarBroker(Long idBroker) {
        BusinessValidations.id(idBroker, "El id de broker");
        Broker broker = brokerDAO.buscarPorId(idBroker)
                .orElseThrow(() -> new BusinessException("Broker no encontrado."));
        BusinessValidations.brokerValido(broker);
        return broker;
    }

    private Broker validarBrokerSupervisorOperativo(Long idBroker) {
        Broker broker = validarBroker(idBroker);
        if (broker.isEsAdministrador()) {
            throw new BusinessException("El broker administrador no registra agentes operativos; debe hacerlo el broker responsable del equipo.");
        }
        return broker;
    }

    private void validarAlcanceSobreAgente(Broker broker, Long idAgente) {
        BusinessValidations.id(idAgente, "El id de agente");
        if (broker.isEsAdministrador()) {
            return;
        }
        if (!brokerAgenteDAO.existeAsignacionActiva(broker.getIdBroker(), idAgente)) {
            throw new BusinessException("El broker no supervisa al agente indicado.");
        }
    }

    private void crearAsignacionSupervisor(Broker broker, AgenteInmobiliario agente) {
        brokerAgenteDAO.buscarActivoPorAgente(agente.getIdAgente()).ifPresent(asignacion -> {
            throw new BusinessException("El agente ya tiene un broker supervisor activo.");
        });
        BrokerAgente brokerAgente = new BrokerAgente();
        brokerAgente.setBroker(broker);
        brokerAgente.setAgente(agente);
        brokerAgente.setFechaAsignacion(LocalDate.now());
        brokerAgente.setMotivo("Asignacion inicial por registro de agente.");
        brokerAgente.setEstado(EstadoActivoInactivo.ACTIVO);
        BusinessValidations.brokerAgente(brokerAgente);
        brokerAgenteDAO.crear(brokerAgente);
    }
}
