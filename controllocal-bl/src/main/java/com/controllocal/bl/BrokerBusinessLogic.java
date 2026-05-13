package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.model.usuario.Broker;

import java.util.List;
import java.util.Optional;

public class BrokerBusinessLogic {

    private final BrokerDAO brokerDAO;

    public BrokerBusinessLogic() {
        this(new BrokerDAOImpl());
    }

    public BrokerBusinessLogic(BrokerDAO brokerDAO) {
        this.brokerDAO = brokerDAO;
    }

    public Long registrarBroker(Long idBrokerAdministrador, Broker broker) {
        return TransactionRunner.write(() -> {
            validarBrokerAdministrador(idBrokerAdministrador);
            validarUnicoBrokerAdministrador(broker, null);
            BusinessValidations.broker(broker);
            return brokerDAO.crear(broker);
        });
    }

    public Long registrarPrimerBrokerAdministrador(Broker broker) {
        return TransactionRunner.write(() -> {
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

    public List<Broker> listarTodos() {
        return brokerDAO.listarTodos();
    }

    public boolean actualizarBroker(Long idBrokerAdministrador, Broker broker) {
        return TransactionRunner.write(() -> {
            validarBrokerAdministrador(idBrokerAdministrador);
            BusinessValidations.id(broker != null ? broker.getIdBroker() : null, "El id de broker");
            validarUnicoBrokerAdministrador(broker, broker.getIdBroker());
            BusinessValidations.broker(broker);
            return brokerDAO.actualizar(broker);
        });
    }

    public boolean desactivarBroker(Long idBrokerAdministrador, Long idBroker) {
        return TransactionRunner.write(() -> {
            validarBrokerAdministrador(idBrokerAdministrador);
            BusinessValidations.id(idBroker, "El id de broker");
            return brokerDAO.eliminar(idBroker);
        });
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
