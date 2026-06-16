package com.controllocal.bl.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.AlertaDAO;
import com.controllocal.dao.BrokerAgenteDAO;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.AlertaDAOImpl;
import com.controllocal.dao.impl.BrokerAgenteDAOImpl;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.OportunidadComercialDAOImpl;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.dao.OportunidadComercialDAO;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.model.comercial.Alerta;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.enums.EstadoAlerta;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.enums.Severidad;
import com.controllocal.model.comercial.enums.TipoAlerta;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class SolicitudAlquilerBusinessLogicImpl implements SolicitudAlquilerBusinessLogic {

    private final SolicitudAlquilerDAO solicitudDAO;
    private final CaptacionDAO captacionDAO;
    private final OportunidadComercialDAO oportunidadDAO;
    private final AgenteInmobiliarioDAO agenteDAO;
    private final AlertaDAO alertaDAO;
    private final BrokerAgenteDAO brokerAgenteDAO;

    public SolicitudAlquilerBusinessLogicImpl() {
        this(new SolicitudAlquilerDAOImpl(), new CaptacionDAOImpl(), new OportunidadComercialDAOImpl(),
                new AgenteInmobiliarioDAOImpl(), new AlertaDAOImpl(), new BrokerAgenteDAOImpl());
    }

    public SolicitudAlquilerBusinessLogicImpl(
            SolicitudAlquilerDAO solicitudDAO,
            CaptacionDAO captacionDAO,
            OportunidadComercialDAO oportunidadDAO,
            AgenteInmobiliarioDAO agenteDAO
    ) {
        this(solicitudDAO, captacionDAO, oportunidadDAO, agenteDAO, new AlertaDAOImpl(), new BrokerAgenteDAOImpl());
    }

    public SolicitudAlquilerBusinessLogicImpl(
            SolicitudAlquilerDAO solicitudDAO,
            CaptacionDAO captacionDAO,
            OportunidadComercialDAO oportunidadDAO,
            AgenteInmobiliarioDAO agenteDAO,
            AlertaDAO alertaDAO,
            BrokerAgenteDAO brokerAgenteDAO
    ) {
        this.solicitudDAO = solicitudDAO;
        this.captacionDAO = captacionDAO;
        this.oportunidadDAO = oportunidadDAO;
        this.agenteDAO = agenteDAO;
        this.alertaDAO = alertaDAO;
        this.brokerAgenteDAO = brokerAgenteDAO;
    }

    public Long registrar(SolicitudAlquiler solicitud) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.solicitud(solicitud);
            OportunidadComercial oportunidad = oportunidadDAO
                    .buscarPorId(BusinessValidations.idOportunidad(solicitud.getOportunidadComercial()))
                    .orElseThrow(() -> new BusinessException("Oportunidad comercial no encontrada para solicitud."));
            Captacion captacion = captacionDAO.buscarPorId(BusinessValidations.idCaptacion(oportunidad.getCaptacion()))
                    .orElseThrow(() -> new BusinessException("Captacion no encontrada para solicitud."));
            BusinessValidations.captacionActiva(captacion);
            BusinessValidations.oportunidadAbierta(oportunidad);
            validarAgenteDisponible(BusinessValidations.idAgente(solicitud.getAgenteResponsable()));
            solicitud.setClienteInteresado(oportunidad.getClienteInteresado());
            solicitud.setCaptacion(captacion);
            solicitud.setOportunidadComercial(oportunidad);
            solicitud.setEstado(EstadoSolicitudAlquiler.REGISTRADA);
            solicitud.setFechaActualizacionEstado(LocalDateTime.now());
            Long idSolicitud = solicitudDAO.crear(solicitud);
            oportunidad.marcarSolicitudCreada();
            oportunidadDAO.actualizar(oportunidad);
            return idSolicitud;
        });
    }

    public Optional<SolicitudAlquiler> buscarPorId(Long idSolicitud) {
        BusinessValidations.id(idSolicitud, "El id de solicitud");
        return solicitudDAO.buscarPorId(idSolicitud);
    }

    public List<SolicitudAlquiler> listarTodos() {
        return solicitudDAO.listarTodos();
    }

    public boolean actualizar(SolicitudAlquiler solicitud) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(solicitud != null ? solicitud.getIdSolicitud() : null, "El id de solicitud");
            BusinessValidations.solicitud(solicitud);
            return solicitudDAO.actualizar(solicitud);
        });
    }

    public boolean eliminar(Long idSolicitud) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idSolicitud, "El id de solicitud");
            return solicitudDAO.eliminar(idSolicitud);
        });
    }

    public boolean reenviarAEvaluacion(Long idSolicitud) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idSolicitud, "El id de solicitud");
            SolicitudAlquiler solicitud = solicitudDAO.buscarPorId(idSolicitud)
                    .orElseThrow(() -> new BusinessException("Solicitud no encontrada para reenviar a evaluacion."));
            if (solicitud.getEstado() != EstadoSolicitudAlquiler.REGISTRADA
                    && solicitud.getEstado() != EstadoSolicitudAlquiler.OBSERVADA) {
                throw new BusinessException("Solo una solicitud registrada u observada puede enviarse a evaluacion.");
            }

            Long idAgente = BusinessValidations.idAgente(solicitud.getAgenteResponsable());
            brokerAgenteDAO.buscarActivoPorAgente(idAgente)
                    .orElseThrow(() -> new BusinessException(
                            "El agente responsable no tiene broker supervisor activo."));

            solicitud.actualizarEstado(EstadoSolicitudAlquiler.EN_REVISION);
            boolean actualizada = solicitudDAO.actualizar(solicitud);
            alertaDAO.crear(alertaReenvio(solicitud));
            return actualizada;
        });
    }

    private void validarAgenteDisponible(Long idAgente) {
        AgenteInmobiliario agente = agenteDAO.buscarPorId(idAgente)
                .orElseThrow(() -> new BusinessException("Agente no encontrado para solicitud."));
        BusinessValidations.agenteDisponible(agente);
    }

    private Alerta alertaReenvio(SolicitudAlquiler solicitud) {
        Alerta alerta = new Alerta();
        alerta.setTipo(TipoAlerta.SOLICITUD_REENVIADA);
        alerta.setSeveridad(Severidad.MEDIA);
        alerta.setEntidadTipo(TipoEntidad.SOLICITUD_ALQUILER);
        alerta.setEntidadId(solicitud.getIdSolicitud());
        alerta.setAgente(solicitud.getAgenteResponsable());
        alerta.setMensaje("La solicitud " + solicitud.getCodigoSolicitud()
                + " fue enviada a evaluacion del broker supervisor.");
        AlertaBusinessLogicImpl.prepararNueva(alerta);
        return alerta;
    }
}
