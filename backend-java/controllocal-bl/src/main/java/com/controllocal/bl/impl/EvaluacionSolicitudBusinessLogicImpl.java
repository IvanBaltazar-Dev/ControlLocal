package com.controllocal.bl.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.EvaluacionSolicitudBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AlertaDAO;
import com.controllocal.dao.BrokerAgenteDAO;
import com.controllocal.dao.BrokerDAO;
import com.controllocal.dao.EvaluacionSolicitudDAO;
import com.controllocal.dao.impl.AlertaDAOImpl;
import com.controllocal.dao.impl.BrokerAgenteDAOImpl;
import com.controllocal.dao.impl.BrokerDAOImpl;
import com.controllocal.dao.impl.EvaluacionSolicitudDAOImpl;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.model.comercial.Alerta;
import com.controllocal.model.comercial.enums.ResultadoEvaluacionSolicitud;
import com.controllocal.model.comercial.enums.Severidad;
import com.controllocal.model.comercial.enums.TipoAlerta;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.comercial.enums.TipoEvaluacionSolicitud;
import com.controllocal.model.comercial.EvaluacionSolicitud;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.usuario.Broker;

public class EvaluacionSolicitudBusinessLogicImpl implements EvaluacionSolicitudBusinessLogic {

    private final EvaluacionSolicitudDAO evaluacionDAO;
    private final SolicitudAlquilerDAO solicitudDAO;
    private final BrokerDAO brokerDAO;
    private final BrokerAgenteDAO brokerAgenteDAO;
    private final AlertaDAO alertaDAO;

    public EvaluacionSolicitudBusinessLogicImpl() {
        this(new EvaluacionSolicitudDAOImpl(), new SolicitudAlquilerDAOImpl(), new BrokerDAOImpl(),
                new BrokerAgenteDAOImpl(), new AlertaDAOImpl());
    }

    public EvaluacionSolicitudBusinessLogicImpl(
            EvaluacionSolicitudDAO evaluacionDAO,
            SolicitudAlquilerDAO solicitudDAO,
            BrokerDAO brokerDAO
    ) {
        this(evaluacionDAO, solicitudDAO, brokerDAO, new BrokerAgenteDAOImpl(), new AlertaDAOImpl());
    }

    public EvaluacionSolicitudBusinessLogicImpl(
            EvaluacionSolicitudDAO evaluacionDAO,
            SolicitudAlquilerDAO solicitudDAO,
            BrokerDAO brokerDAO,
            BrokerAgenteDAO brokerAgenteDAO
    ) {
        this(evaluacionDAO, solicitudDAO, brokerDAO, brokerAgenteDAO, new AlertaDAOImpl());
    }

    public EvaluacionSolicitudBusinessLogicImpl(
            EvaluacionSolicitudDAO evaluacionDAO,
            SolicitudAlquilerDAO solicitudDAO,
            BrokerDAO brokerDAO,
            BrokerAgenteDAO brokerAgenteDAO,
            AlertaDAO alertaDAO
    ) {
        this.evaluacionDAO = evaluacionDAO;
        this.solicitudDAO = solicitudDAO;
        this.brokerDAO = brokerDAO;
        this.brokerAgenteDAO = brokerAgenteDAO;
        this.alertaDAO = alertaDAO;
    }

    public Long registrar(EvaluacionSolicitud evaluacion) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.evaluacion(evaluacion);
            SolicitudAlquiler solicitud = solicitudDAO.buscarPorId(BusinessValidations.idSolicitud(evaluacion.getSolicitudAlquiler()))
                    .orElseThrow(() -> new BusinessException("Solicitud no encontrada para evaluacion."));
            Broker broker = brokerDAO.buscarPorId(BusinessValidations.idBroker(evaluacion.getResponsableEvaluacion()))
                    .orElseThrow(() -> new BusinessException("Broker responsable no encontrado."));
            BusinessValidations.brokerValido(broker);
            validarAlcanceBrokerSobreSolicitud(broker, solicitud);
            validarUnicaEvaluacionFinal(evaluacion);
            if (evaluacion.getFechaEvaluacion() == null) {
                evaluacion.setFechaEvaluacion(LocalDateTime.now());
            }
            Long idEvaluacion = evaluacionDAO.crear(evaluacion);
            aplicarResultado(solicitud, evaluacion);
            solicitudDAO.actualizar(solicitud);
            alertaDAO.crear(alertaEvaluacion(solicitud, evaluacion));
            return idEvaluacion;
        });
    }

    public Optional<EvaluacionSolicitud> buscarPorId(Long idEvaluacion) {
        BusinessValidations.id(idEvaluacion, "El id de evaluacion");
        return evaluacionDAO.buscarPorId(idEvaluacion);
    }

    public List<EvaluacionSolicitud> listarTodos() {
        return evaluacionDAO.listarTodos();
    }

    public List<EvaluacionSolicitud> listarPorBroker(Long idBroker) {
        Broker broker = brokerDAO.buscarPorId(idBroker)
                .orElseThrow(() -> new BusinessException("Broker no encontrado."));
        BusinessValidations.brokerValido(broker);
        if (broker.isEsAdministrador()) {
            return evaluacionDAO.listarTodos();
        }
        return evaluacionDAO.listarTodos().stream()
                .filter(evaluacion -> idBroker.equals(BusinessValidations.idBroker(evaluacion.getResponsableEvaluacion())))
                .toList();
    }

    public boolean actualizar(EvaluacionSolicitud evaluacion) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(evaluacion != null ? evaluacion.getIdEvaluacion() : null, "El id de evaluacion");
            BusinessValidations.evaluacion(evaluacion);
            SolicitudAlquiler solicitud = solicitudDAO.buscarPorId(BusinessValidations.idSolicitud(evaluacion.getSolicitudAlquiler()))
                    .orElseThrow(() -> new BusinessException("Solicitud no encontrada para evaluacion."));
            Broker broker = brokerDAO.buscarPorId(BusinessValidations.idBroker(evaluacion.getResponsableEvaluacion()))
                    .orElseThrow(() -> new BusinessException("Broker responsable no encontrado."));
            BusinessValidations.brokerValido(broker);
            validarAlcanceBrokerSobreSolicitud(broker, solicitud);
            validarUnicaEvaluacionFinal(evaluacion);
            boolean actualizada = evaluacionDAO.actualizar(evaluacion);
            if (actualizada) {
                aplicarResultado(solicitud, evaluacion);
                solicitudDAO.actualizar(solicitud);
            }
            return actualizada;
        });
    }

    public boolean eliminar(Long idEvaluacion) {
        return TransactionRunner.write(conn -> {
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

    private void validarAlcanceBrokerSobreSolicitud(Broker broker, SolicitudAlquiler solicitud) {
        Long idAgente = BusinessValidations.idAgente(solicitud.getAgenteResponsable());
        BusinessValidations.id(idAgente, "El agente responsable de la solicitud");
        if (broker.isEsAdministrador()) {
            return;
        }
        if (!brokerAgenteDAO.existeAsignacionActiva(broker.getIdBroker(), idAgente)) {
            throw new BusinessException("El broker no supervisa al agente responsable de esta solicitud.");
        }
    }

    private static void aplicarResultado(SolicitudAlquiler solicitud, EvaluacionSolicitud evaluacion) {
        if (evaluacion.getResultado() == ResultadoEvaluacionSolicitud.APROBADA) {
            solicitud.aprobar();
        } else if (evaluacion.getResultado() == ResultadoEvaluacionSolicitud.RECHAZADA) {
            solicitud.rechazar();
        } else if (evaluacion.getResultado() == ResultadoEvaluacionSolicitud.OBSERVADA) {
            solicitud.solicitarAjustes();
        }
    }

    private static Alerta alertaEvaluacion(SolicitudAlquiler solicitud, EvaluacionSolicitud evaluacion) {
        Alerta alerta = new Alerta();
        alerta.setTipo(TipoAlerta.SOLICITUD_EVALUADA);
        alerta.setSeveridad(severidad(evaluacion.getResultado()));
        alerta.setEntidadTipo(TipoEntidad.SOLICITUD_ALQUILER);
        alerta.setEntidadId(solicitud.getIdSolicitud());
        alerta.setAgente(solicitud.getAgenteResponsable());
        alerta.setMensaje("La solicitud " + solicitud.getCodigoSolicitud()
                + " fue evaluada con resultado " + evaluacion.getResultado().getDescripcion() + ".");
        AlertaBusinessLogicImpl.prepararNueva(alerta);
        return alerta;
    }

    private static Severidad severidad(ResultadoEvaluacionSolicitud resultado) {
        if (resultado == ResultadoEvaluacionSolicitud.RECHAZADA) {
            return Severidad.ALTA;
        }
        if (resultado == ResultadoEvaluacionSolicitud.OBSERVADA) {
            return Severidad.MEDIA;
        }
        return Severidad.INFO;
    }
}
