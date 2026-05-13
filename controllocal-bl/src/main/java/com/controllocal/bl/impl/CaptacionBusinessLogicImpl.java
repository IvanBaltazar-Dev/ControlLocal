package com.controllocal.bl.impl;

import com.controllocal.bl.CaptacionBusinessLogic;
import com.controllocal.bl.customerAcquisitionLifecycle;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.ReasignacionCaptacionDAO;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EstadoCaptacion;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;

import java.util.List;

public class customerAcquisitionLifecycleImpl implements customerAcquisitionLifecycle {

    private final CaptacionBusinessLogic captacionBusinessLogic;

    public customerAcquisitionLifecycleImpl() {
        this.captacionBusinessLogic = new CaptacionBusinessLogic();
    }

    public customerAcquisitionLifecycleImpl(
            CaptacionDAO captacionDAO,
            AgenteInmobiliarioDAO agenteDAO,
            ReasignacionCaptacionDAO reasignacionDAO,
            BrokerDAO brokerDAO
    ) {
        this.captacionBusinessLogic = new CaptacionBusinessLogic(captacionDAO, agenteDAO, reasignacionDAO, brokerDAO);
    }

    @Override
    public Long registerAcquisition(Captacion acquisition) {
        return captacionBusinessLogic.registerAcquisition(acquisition);
    }

    @Override
    public void reassignAgent(Long acquisitionId, Long newAgentId, Long brokerId, String reason) {
        captacionBusinessLogic.reassignAgent(acquisitionId, newAgentId, brokerId, reason);
    }

    @Override
    public void reviewAcquisition(Long acquisitionId, Long brokerId, boolean approved, String remarks) {
        captacionBusinessLogic.reviewAcquisition(acquisitionId, brokerId, approved, remarks);
    }

    @Override
    public void closeAcquisition(Long acquisitionId) {
        captacionBusinessLogic.closeAcquisition(acquisitionId);
    }

    @Override
    public void closeAcquisition(Long acquisitionId, Long brokerId, String reason) {
        captacionBusinessLogic.closeAcquisition(acquisitionId, brokerId, reason);
    }

    @Override
    public List<Captacion> listPendingReviews() {
        return captacionBusinessLogic.listPendingReviews();
    }

    public void aprobarCaptacion(Long idCaptacion, Long idBroker, String observacion) {
        captacionBusinessLogic.aprobarCaptacion(idCaptacion, idBroker, observacion);
    }

    public void observarCaptacion(Long idCaptacion, Long idBroker, String observacion) {
        captacionBusinessLogic.observarCaptacion(idCaptacion, idBroker, observacion);
    }

    public void rechazarCaptacion(Long idCaptacion, Long idBroker, String observacion) {
        captacionBusinessLogic.rechazarCaptacion(idCaptacion, idBroker, observacion);
    }

    public void revisarCaptacion(Long idCaptacion, Long idBroker, EstadoCaptacion estadoRevision, String observacion) {
        captacionBusinessLogic.revisarCaptacion(idCaptacion, idBroker, estadoRevision, observacion);
    }

    public void cerrarCaptacion(Long idCaptacion, Long idBroker, String motivo) {
        captacionBusinessLogic.cerrarCaptacion(idCaptacion, idBroker, motivo);
    }

    public Broker validarBroker(Long brokerId) {
        return captacionBusinessLogic.validarBroker(brokerId);
    }

    public Broker validarBrokerAdministrador(Long brokerId) {
        return captacionBusinessLogic.validarBrokerAdministrador(brokerId);
    }

    public AgenteInmobiliario validarAgenteDisponible(Long agenteId) {
        return captacionBusinessLogic.validarAgenteDisponible(agenteId);
    }
}
