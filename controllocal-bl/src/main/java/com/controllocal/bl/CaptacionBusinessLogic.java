package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.ReasignacionCaptacionDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.ReasignacionCaptacionDAOImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EstadoCaptacion;
import com.controllocal.model.comercial.ReasignacionCaptacion;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class CaptacionBusinessLogic implements customerAcquisitionLifecycle {

    private final CaptacionDAO captacionDAO;
    private final AgenteInmobiliarioDAO agenteDAO;
    private final ReasignacionCaptacionDAO reasignacionDAO;
    private final BrokerDAO brokerDAO;

    public CaptacionBusinessLogic() {
        this(new CaptacionDAOImpl(), new AgenteInmobiliarioDAOImpl(), new ReasignacionCaptacionDAOImpl(), new BrokerDAOImpl());
    }

    public CaptacionBusinessLogic(
            CaptacionDAO captacionDAO,
            AgenteInmobiliarioDAO agenteDAO,
            ReasignacionCaptacionDAO reasignacionDAO,
            BrokerDAO brokerDAO
    ) {
        this.captacionDAO = captacionDAO;
        this.agenteDAO = agenteDAO;
        this.reasignacionDAO = reasignacionDAO;
        this.brokerDAO = brokerDAO;
    }

    public Long registrar(Captacion captacion) {
        return registerAcquisition(captacion);
    }

    @Override
    public Long registerAcquisition(Captacion acquisition) {
        return TransactionRunner.write(() -> {
            BusinessValidations.captacion(acquisition);
            validarAgenteDisponible(BusinessValidations.idAgente(acquisition.getAgenteResponsable()));
            validarUnicaCaptacionActivaPorLocal(BusinessValidations.idLocal(acquisition));
            acquisition.setEstado(EstadoCaptacion.PENDIENTE_REVISION);
            if (acquisition.getFechaInicioVigencia() == null) {
                acquisition.setFechaInicioVigencia(LocalDate.now());
            }
            return captacionDAO.crear(acquisition);
        });
    }

    public void aprobarCaptacion(Long idCaptacion, Long idBroker, String observacion) {
        revisarCaptacion(idCaptacion, idBroker, EstadoCaptacion.ACTIVA, observacion);
    }

    public void observarCaptacion(Long idCaptacion, Long idBroker, String observacion) {
        revisarCaptacion(idCaptacion, idBroker, EstadoCaptacion.OBSERVADA, observacion);
    }

    public void rechazarCaptacion(Long idCaptacion, Long idBroker, String observacion) {
        revisarCaptacion(idCaptacion, idBroker, EstadoCaptacion.RECHAZADA, observacion);
    }

    @Override
    public void reviewAcquisition(Long acquisitionId, Long brokerId, boolean approved, String remarks) {
        revisarCaptacion(acquisitionId, brokerId, approved ? EstadoCaptacion.ACTIVA : EstadoCaptacion.RECHAZADA, remarks);
    }

    public void revisarCaptacion(Long idCaptacion, Long idBroker, EstadoCaptacion estadoRevision, String observacion) {
        TransactionRunner.write(() -> {
            Broker broker = validarBroker(idBroker);
            Captacion captacion = buscarCaptacionObligatoria(idCaptacion);
            validarCaptacionPendienteRevision(captacion);
            if (estadoRevision == EstadoCaptacion.ACTIVA) {
                validarUnicaCaptacionActivaPorLocal(BusinessValidations.idLocal(captacion));
            }
            if (estadoRevision == EstadoCaptacion.RECHAZADA || estadoRevision == EstadoCaptacion.OBSERVADA) {
                BusinessValidations.texto(observacion, "La observacion de revision");
            }
            captacion.setEstado(estadoRevision);
            captacion.setBrokerRevisor(broker);
            captacion.setFechaRevision(LocalDateTime.now());
            captacion.setObservacionRevision(observacion);
            captacionDAO.actualizar(captacion);
        });
    }

    @Override
    public void reassignAgent(Long acquisitionId, Long newAgentId, Long brokerId, String reason) {
        reasignarCaptacion(acquisitionId, newAgentId, brokerId, reason);
    }

    public void reasignarCaptacion(Long idCaptacion, Long idAgenteNuevo, Long idBroker, String motivo) {
        TransactionRunner.write(() -> {
            Broker broker = validarBroker(idBroker);
            Captacion captacion = buscarCaptacionObligatoria(idCaptacion);
            BusinessValidations.texto(motivo, "El motivo de reasignacion");
            AgenteInmobiliario agenteNuevo = validarAgenteDisponible(idAgenteNuevo);
            AgenteInmobiliario agenteAnterior = captacion.getAgenteResponsable();
            Long idAgenteAnterior = BusinessValidations.idAgente(agenteAnterior);
            BusinessValidations.id(idAgenteAnterior, "El agente anterior");
            if (idAgenteAnterior.equals(idAgenteNuevo)) {
                throw new BusinessException("La captacion ya esta asignada a ese agente.");
            }

            captacion.reasignarAgente(agenteNuevo);
            captacionDAO.actualizar(captacion);

            ReasignacionCaptacion reasignacion = new ReasignacionCaptacion();
            reasignacion.setCaptacion(captacion);
            reasignacion.setAgenteAnterior(agenteAnterior);
            reasignacion.setAgenteNuevo(agenteNuevo);
            reasignacion.setBrokerResponsable(broker);
            reasignacion.setFechaCambio(LocalDateTime.now());
            reasignacion.setMotivo(motivo);
            reasignacionDAO.crear(reasignacion);
        });
    }

    public void cerrarCaptacion(Long idCaptacion, Long idBroker, String motivo) {
        TransactionRunner.write(() -> {
            validarBroker(idBroker);
            BusinessValidations.texto(motivo, "El motivo de cierre");
            Captacion captacion = buscarCaptacionObligatoria(idCaptacion);
            validarCaptacionActiva(captacion);
            captacion.setObservacionRevision(motivo);
            captacion.cerrar();
            captacionDAO.actualizar(captacion);
        });
    }

    @Override
    public void closeAcquisition(Long acquisitionId, Long brokerId, String reason) {
        cerrarCaptacion(acquisitionId, brokerId, reason);
    }

    @Override
    public void closeAcquisition(Long acquisitionId) {
        throw new BusinessException("Debe indicar un broker valido y motivo para cerrar una captacion.");
    }

    public Optional<Captacion> buscarPorId(Long idCaptacion) {
        BusinessValidations.id(idCaptacion, "El id de captacion");
        return captacionDAO.buscarPorId(idCaptacion);
    }

    public List<Captacion> listarTodos() {
        return captacionDAO.listarTodos();
    }

    @Override
    public List<Captacion> listPendingReviews() {
        return captacionDAO.listarTodos().stream()
                .filter(captacion -> captacion.getEstado() == EstadoCaptacion.PENDIENTE_REVISION
                        || captacion.getEstado() == EstadoCaptacion.OBSERVADA)
                .toList();
    }

    public boolean actualizar(Captacion captacion) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(captacion != null ? captacion.getIdCaptacion() : null, "El id de captacion");
            BusinessValidations.captacion(captacion);
            return captacionDAO.actualizar(captacion);
        });
    }

    public Broker validarBroker(Long brokerId) {
        BusinessValidations.id(brokerId, "El id de broker");
        Broker broker = brokerDAO.buscarPorId(brokerId)
                .orElseThrow(() -> new BusinessException("Broker no encontrado."));
        BusinessValidations.brokerValido(broker);
        return broker;
    }

    public Broker validarBrokerAdministrador(Long brokerId) {
        Broker broker = validarBroker(brokerId);
        BusinessValidations.brokerAdministrador(broker);
        return broker;
    }

    public AgenteInmobiliario validarAgenteDisponible(Long agenteId) {
        BusinessValidations.id(agenteId, "El id de agente");
        AgenteInmobiliario agente = agenteDAO.buscarPorId(agenteId)
                .orElseThrow(() -> new BusinessException("Agente no encontrado."));
        BusinessValidations.agenteDisponible(agente);
        return agente;
    }

    public void validarCaptacionPendienteRevision(Captacion captacion) {
        BusinessValidations.captacionPendienteRevision(captacion);
    }

    public void validarCaptacionActiva(Captacion captacion) {
        BusinessValidations.captacionActiva(captacion);
    }

    public void validarUnicaCaptacionActivaPorLocal(Long idLocal) {
        BusinessValidations.id(idLocal, "El id de local");
        boolean existeActiva = captacionDAO.listarTodos().stream()
                .anyMatch(captacion -> captacion.getEstado() == EstadoCaptacion.ACTIVA
                        && captacion.getLocalComercial() != null
                        && idLocal.equals(captacion.getLocalComercial().getIdLocal()));
        if (existeActiva) {
            throw new BusinessException("Solo una captacion del mismo local puede estar ACTIVA.");
        }
    }

    private Captacion buscarCaptacionObligatoria(Long idCaptacion) {
        BusinessValidations.id(idCaptacion, "El id de captacion");
        return captacionDAO.buscarPorId(idCaptacion)
                .orElseThrow(() -> new BusinessException("Captacion no encontrada."));
    }
}
