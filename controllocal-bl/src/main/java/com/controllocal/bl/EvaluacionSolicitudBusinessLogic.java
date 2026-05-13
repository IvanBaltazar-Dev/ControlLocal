package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.EvaluacionSolicitudDAO;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.dao.impl.EvaluacionSolicitudDAOImpl;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.model.comercial.EvaluacionSolicitud;
import com.controllocal.model.comercial.TipoEvaluacionSolicitud;
import com.controllocal.model.usuario.Broker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class EvaluacionSolicitudBusinessLogic {

    private final EvaluacionSolicitudDAO evaluacionDAO;
    private final SolicitudAlquilerDAO solicitudDAO;
    private final BrokerDAO brokerDAO;

    public EvaluacionSolicitudBusinessLogic() {
        this(new EvaluacionSolicitudDAOImpl(), new SolicitudAlquilerDAOImpl(), new BrokerDAOImpl());
    }

    public EvaluacionSolicitudBusinessLogic(
            EvaluacionSolicitudDAO evaluacionDAO,
            SolicitudAlquilerDAO solicitudDAO,
            BrokerDAO brokerDAO
    ) {
        this.evaluacionDAO = evaluacionDAO;
        this.solicitudDAO = solicitudDAO;
        this.brokerDAO = brokerDAO;
    }

    public Long registrar(EvaluacionSolicitud evaluacion) {
        return TransactionRunner.write(() -> {
            BusinessValidations.evaluacion(evaluacion);
            solicitudDAO.buscarPorId(BusinessValidations.idSolicitud(evaluacion.getSolicitudAlquiler()))
                    .orElseThrow(() -> new BusinessException("Solicitud no encontrada para evaluacion."));
            Broker broker = brokerDAO.buscarPorId(BusinessValidations.idBroker(evaluacion.getResponsableEvaluacion()))
                    .orElseThrow(() -> new BusinessException("Broker responsable no encontrado."));
            BusinessValidations.brokerValido(broker);
            validarUnicaEvaluacionFinal(evaluacion);
            if (evaluacion.getFechaEvaluacion() == null) {
                evaluacion.setFechaEvaluacion(LocalDateTime.now());
            }
            return evaluacionDAO.crear(evaluacion);
        });
    }

    public Optional<EvaluacionSolicitud> buscarPorId(Long idEvaluacion) {
        BusinessValidations.id(idEvaluacion, "El id de evaluacion");
        return evaluacionDAO.buscarPorId(idEvaluacion);
    }

    public List<EvaluacionSolicitud> listarTodos() {
        return evaluacionDAO.listarTodos();
    }

    public boolean actualizar(EvaluacionSolicitud evaluacion) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(evaluacion != null ? evaluacion.getIdEvaluacion() : null, "El id de evaluacion");
            BusinessValidations.evaluacion(evaluacion);
            validarUnicaEvaluacionFinal(evaluacion);
            return evaluacionDAO.actualizar(evaluacion);
        });
    }

    public boolean eliminar(Long idEvaluacion) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(idEvaluacion, "El id de evaluacion");
            return evaluacionDAO.eliminar(idEvaluacion);
        });
    }

    private void validarUnicaEvaluacionFinal(EvaluacionSolicitud evaluacion) {
        if (evaluacion.getTipoEvaluacion() != TipoEvaluacionSolicitud.FINAL) {
            return;
        }
        Long idSolicitud = BusinessValidations.idSolicitud(evaluacion.getSolicitudAlquiler());
        boolean existeFinal = evaluacionDAO.listarTodos().stream()
                .anyMatch(item -> item.getTipoEvaluacion() == TipoEvaluacionSolicitud.FINAL
                        && item.getSolicitudAlquiler() != null
                        && idSolicitud.equals(item.getSolicitudAlquiler().getIdSolicitud())
                        && (evaluacion.getIdEvaluacion() == null
                        || !evaluacion.getIdEvaluacion().equals(item.getIdEvaluacion())));
        if (existeFinal) {
            throw new BusinessException("Solo puede existir una evaluacion final por solicitud.");
        }
    }
}
