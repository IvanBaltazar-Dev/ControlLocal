package com.controllocal.bl.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.OportunidadComercialDAOImpl;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.dao.OportunidadComercialDAO;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class SolicitudAlquilerBusinessLogicImpl implements SolicitudAlquilerBusinessLogic {

    private final SolicitudAlquilerDAO solicitudDAO;
    private final CaptacionDAO captacionDAO;
    private final OportunidadComercialDAO oportunidadDAO;
    private final AgenteInmobiliarioDAO agenteDAO;

    public SolicitudAlquilerBusinessLogicImpl() {
        this(new SolicitudAlquilerDAOImpl(), new CaptacionDAOImpl(), new OportunidadComercialDAOImpl(), new AgenteInmobiliarioDAOImpl());
    }

    public SolicitudAlquilerBusinessLogicImpl(
            SolicitudAlquilerDAO solicitudDAO,
            CaptacionDAO captacionDAO,
            OportunidadComercialDAO oportunidadDAO,
            AgenteInmobiliarioDAO agenteDAO
    ) {
        this.solicitudDAO = solicitudDAO;
        this.captacionDAO = captacionDAO;
        this.oportunidadDAO = oportunidadDAO;
        this.agenteDAO = agenteDAO;
    }

    public Long registrar(SolicitudAlquiler solicitud) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.solicitud(solicitud);
            Captacion captacion = captacionDAO.buscarPorId(BusinessValidations.idCaptacion(solicitud.getCaptacion()))
                    .orElseThrow(() -> new BusinessException("Captacion no encontrada para solicitud."));
            BusinessValidations.captacionActiva(captacion);
            OportunidadComercial oportunidad = oportunidadDAO
                    .buscarPorId(BusinessValidations.idOportunidad(solicitud.getOportunidadComercial()))
                    .orElseThrow(() -> new BusinessException("Oportunidad comercial no encontrada para solicitud."));
            BusinessValidations.oportunidadAbierta(oportunidad);
            validarAgenteDisponible(BusinessValidations.idAgente(solicitud.getAgenteResponsable()));
            solicitud.setClienteInteresado(oportunidad.getClienteInteresado());
            solicitud.setCaptacion(oportunidad.getCaptacion());
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

    private void validarAgenteDisponible(Long idAgente) {
        AgenteInmobiliario agente = agenteDAO.buscarPorId(idAgente)
                .orElseThrow(() -> new BusinessException("Agente no encontrado para solicitud."));
        BusinessValidations.agenteDisponible(agente);
    }
}
