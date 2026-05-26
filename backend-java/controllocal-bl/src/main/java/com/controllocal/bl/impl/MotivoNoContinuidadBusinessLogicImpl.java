package com.controllocal.bl.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.MotivoNoContinuidadBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.AgenteInmobiliarioDAO;
import com.controllocal.dao.impl.AgenteInmobiliarioDAOImpl;
import com.controllocal.dao.impl.MotivoNoContinuidadDAOImpl;
import com.controllocal.dao.impl.OportunidadComercialDAOImpl;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.dao.MotivoNoContinuidadDAO;
import com.controllocal.dao.OportunidadComercialDAO;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.MotivoNoContinuidad;
import com.controllocal.model.comercial.OportunidadComercial;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class MotivoNoContinuidadBusinessLogicImpl implements MotivoNoContinuidadBusinessLogic {

    private final MotivoNoContinuidadDAO motivoDAO;
    private final OportunidadComercialDAO oportunidadDAO;
    private final SolicitudAlquilerDAO solicitudDAO;
    private final AgenteInmobiliarioDAO agenteDAO;

    public MotivoNoContinuidadBusinessLogicImpl() {
        this(new MotivoNoContinuidadDAOImpl(), new OportunidadComercialDAOImpl(), new SolicitudAlquilerDAOImpl(), new AgenteInmobiliarioDAOImpl());
    }

    public MotivoNoContinuidadBusinessLogicImpl(MotivoNoContinuidadDAO motivoDAO) {
        this(motivoDAO, new OportunidadComercialDAOImpl(), new SolicitudAlquilerDAOImpl(), new AgenteInmobiliarioDAOImpl());
    }

    public MotivoNoContinuidadBusinessLogicImpl(
            MotivoNoContinuidadDAO motivoDAO,
            OportunidadComercialDAO oportunidadDAO,
            SolicitudAlquilerDAO solicitudDAO,
            AgenteInmobiliarioDAO agenteDAO
    ) {
        this.motivoDAO = motivoDAO;
        this.oportunidadDAO = oportunidadDAO;
        this.solicitudDAO = solicitudDAO;
        this.agenteDAO = agenteDAO;
    }

    public Long registrar(MotivoNoContinuidad motivo) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.motivo(motivo);
            OportunidadComercial oportunidad = oportunidadDAO
                    .buscarPorId(BusinessValidations.idOportunidad(motivo.getOportunidadComercial()))
                    .orElseThrow(() -> new BusinessException("Oportunidad comercial no encontrada para no continuidad."));
            BusinessValidations.oportunidadAbierta(oportunidad);
            validarAgenteDisponible(BusinessValidations.idAgente(motivo.getAgenteResponsable()));
            validarSinSolicitudAprobada(oportunidad);
            if (motivo.getFechaHora() == null) {
                motivo.setFechaHora(LocalDateTime.now());
            }
            Long idMotivo = motivoDAO.crear(motivo);
            oportunidad.cerrarNoContinua(motivo.getRazonPrincipal().getDescripcion());
            oportunidadDAO.actualizar(oportunidad);
            return idMotivo;
        });
    }

    public Optional<MotivoNoContinuidad> buscarPorId(Long idMotivo) {
        BusinessValidations.id(idMotivo, "El id de motivo");
        return motivoDAO.buscarPorId(idMotivo);
    }

    public List<MotivoNoContinuidad> listarTodos() {
        return motivoDAO.listarTodos();
    }

    public boolean actualizar(MotivoNoContinuidad motivo) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(motivo != null ? motivo.getIdMotivoNoContinuidad() : null, "El id de motivo");
            BusinessValidations.motivo(motivo);
            return motivoDAO.actualizar(motivo);
        });
    }

    public boolean eliminar(Long idMotivo) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idMotivo, "El id de motivo");
            return motivoDAO.eliminar(idMotivo);
        });
    }

    private void validarAgenteDisponible(Long idAgente) {
        AgenteInmobiliario agente = agenteDAO.buscarPorId(idAgente)
                .orElseThrow(() -> new BusinessException("Agente no encontrado para no continuidad."));
        BusinessValidations.agenteDisponible(agente);
    }

    private void validarSinSolicitudAprobada(OportunidadComercial oportunidad) {
        boolean existeAprobada = solicitudDAO.listarTodos().stream()
                .anyMatch(solicitud -> solicitud.getEstado() == EstadoSolicitudAlquiler.APROBADA
                        && solicitud.getOportunidadComercial() != null
                        && oportunidad.getIdOportunidad().equals(solicitud.getOportunidadComercial().getIdOportunidad()));
        if (existeAprobada) {
            throw new BusinessException("No se puede registrar no continuidad si ya existe una solicitud aprobada.");
        }
    }
}
