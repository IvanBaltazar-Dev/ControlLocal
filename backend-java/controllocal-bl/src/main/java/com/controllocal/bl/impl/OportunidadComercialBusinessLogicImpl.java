package com.controllocal.bl.impl;

import java.util.List;
import java.util.Optional;

import com.controllocal.bl.BusinessException;
import com.controllocal.bl.OportunidadComercialBusinessLogic;
import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.EvaluacionSolicitudDAO;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.EvaluacionSolicitudDAOImpl;
import com.controllocal.dao.impl.OportunidadComercialDAOImpl;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.dao.OportunidadComercialDAO;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.enums.EstadoOportunidadComercial;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.enums.ResultadoEvaluacionSolicitud;
import com.controllocal.model.comercial.enums.TipoEvaluacionSolicitud;
import com.controllocal.model.comercial.OportunidadComercial;

public class OportunidadComercialBusinessLogicImpl implements OportunidadComercialBusinessLogic {

    private final OportunidadComercialDAO oportunidadDAO;
    private final CaptacionDAO captacionDAO;
    private final SolicitudAlquilerDAO solicitudDAO;
    private final EvaluacionSolicitudDAO evaluacionDAO;

    public OportunidadComercialBusinessLogicImpl() {
        this(new OportunidadComercialDAOImpl(), new CaptacionDAOImpl(), new SolicitudAlquilerDAOImpl(), new EvaluacionSolicitudDAOImpl());
    }

    public OportunidadComercialBusinessLogicImpl(
            OportunidadComercialDAO oportunidadDAO,
            CaptacionDAO captacionDAO,
            SolicitudAlquilerDAO solicitudDAO,
            EvaluacionSolicitudDAO evaluacionDAO
    ) {
        this.oportunidadDAO = oportunidadDAO;
        this.captacionDAO = captacionDAO;
        this.solicitudDAO = solicitudDAO;
        this.evaluacionDAO = evaluacionDAO;
    }

    public Long registrar(OportunidadComercial oportunidad) {
        return TransactionRunner.write(conn -> {
            oportunidad.abrir();
            BusinessValidations.oportunidad(oportunidad);
            Captacion captacion = captacionDAO.buscarPorId(BusinessValidations.idCaptacion(oportunidad.getCaptacion()))
                    .orElseThrow(() -> new BusinessException("Captacion no encontrada para oportunidad."));
            BusinessValidations.captacionActiva(captacion);
            validarUnicaAbierta(oportunidad);
            return oportunidadDAO.crear(oportunidad);
        });
    }

    public Optional<OportunidadComercial> buscarPorId(Long idOportunidad) {
        BusinessValidations.id(idOportunidad, "El id de oportunidad");
        return oportunidadDAO.buscarPorId(idOportunidad);
    }

    public List<OportunidadComercial> listarTodos() {
        return oportunidadDAO.listarTodos();
    }

    @Override
    public List<OportunidadComercial> listarPorAgentes(java.util.Collection<Long> idsAgente) {
        return oportunidadDAO.listarPorAgentes(idsAgente);
    }

    @Override
    public List<OportunidadComercial> listarPorCaptaciones(java.util.Collection<Long> idsCaptacion) {
        return oportunidadDAO.listarPorCaptaciones(idsCaptacion);
    }

    @Override
    public List<OportunidadComercial> listarPorCliente(Long idCliente) {
        return oportunidadDAO.listarPorCliente(idCliente);
    }

    @Override
    public List<OportunidadComercial> listarPorPropietario(Long idPropietario) {
        return oportunidadDAO.listarPorPropietario(idPropietario);
    }

    @Override
    public List<OportunidadComercial> listarPorIds(java.util.Collection<Long> ids) {
        return oportunidadDAO.listarPorIds(ids);
    }

    public boolean actualizar(OportunidadComercial oportunidad) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(oportunidad != null ? oportunidad.getIdOportunidad() : null, "El id de oportunidad");
            BusinessValidations.oportunidad(oportunidad);
            return oportunidadDAO.actualizar(oportunidad);
        });
    }

    public boolean cerrarNoContinua(Long idOportunidad, String motivo) {
        return cerrar(idOportunidad, oportunidad -> {
            validarSinSolicitudAprobada(oportunidad);
            oportunidad.cerrarNoContinua(motivo);
        });
    }

    public boolean cerrarExitosa(Long idOportunidad) {
        return cerrar(idOportunidad, oportunidad -> {
            validarSolicitudAprobada(oportunidad);
            oportunidad.cerrarExitosa();
        });
    }

    public boolean cerrarNoFavorable(Long idOportunidad, String motivo) {
        return cerrar(idOportunidad, oportunidad -> {
            validarEvaluacionFinalRechazada(oportunidad);
            oportunidad.cerrarNoFavorable(motivo);
        });
    }

    public boolean eliminar(Long idOportunidad) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idOportunidad, "El id de oportunidad");
            return oportunidadDAO.eliminar(idOportunidad);
        });
    }

    private boolean cerrar(Long idOportunidad, CierreOportunidad cierre) {
        return TransactionRunner.write(conn -> {
            BusinessValidations.id(idOportunidad, "El id de oportunidad");
            OportunidadComercial oportunidad = oportunidadDAO.buscarPorId(idOportunidad)
                    .orElseThrow(() -> new BusinessException("Oportunidad comercial no encontrada."));
            if (oportunidad.getEstado() != EstadoOportunidadComercial.ABIERTA
                    && oportunidad.getEstado() != EstadoOportunidadComercial.SOLICITUD_CREADA) {
                throw new BusinessException("La oportunidad comercial ya esta cerrada.");
            }
            cierre.aplicar(oportunidad);
            BusinessValidations.oportunidad(oportunidad);
            return oportunidadDAO.actualizar(oportunidad);
        });
    }

    private void validarUnicaAbierta(OportunidadComercial oportunidad) {
        Long idCliente = BusinessValidations.idCliente(oportunidad.getClienteInteresado());
        Long idCaptacion = BusinessValidations.idCaptacion(oportunidad.getCaptacion());
        boolean existeAbierta = oportunidadDAO.listarTodos().stream()
                .anyMatch(item -> item.getEstado() == EstadoOportunidadComercial.ABIERTA
                        && item.getClienteInteresado() != null
                        && item.getCaptacion() != null
                        && idCliente.equals(item.getClienteInteresado().getIdCliente())
                        && idCaptacion.equals(item.getCaptacion().getIdCaptacion()));
        if (existeAbierta) {
            throw new BusinessException("Ya existe una oportunidad abierta para el cliente y captacion.");
        }
    }

    private void validarSolicitudAprobada(OportunidadComercial oportunidad) {
        boolean existeAprobada = solicitudDAO.listarTodos().stream()
                .anyMatch(solicitud -> solicitud.getEstado() == EstadoSolicitudAlquiler.APROBADA
                        && mismaOportunidad(solicitud.getOportunidadComercial(), oportunidad));
        if (!existeAprobada) {
            throw new BusinessException("Solo se puede cerrar exitosamente una oportunidad con solicitud aprobada.");
        }
    }

    private void validarSinSolicitudAprobada(OportunidadComercial oportunidad) {
        boolean existeAprobada = solicitudDAO.listarTodos().stream()
                .anyMatch(solicitud -> solicitud.getEstado() == EstadoSolicitudAlquiler.APROBADA
                        && mismaOportunidad(solicitud.getOportunidadComercial(), oportunidad));
        if (existeAprobada) {
            throw new BusinessException("No se puede registrar no continuidad si ya existe una solicitud aprobada.");
        }
    }

    private void validarEvaluacionFinalRechazada(OportunidadComercial oportunidad) {
        boolean existeFinalRechazada = evaluacionDAO.listarTodos().stream()
                .anyMatch(evaluacion -> evaluacion.getResultado() == ResultadoEvaluacionSolicitud.RECHAZADA
                        && evaluacion.getTipoEvaluacion() == TipoEvaluacionSolicitud.FINAL
                        && evaluacion.getSolicitudAlquiler() != null
                        && solicitudDAO.buscarPorId(evaluacion.getSolicitudAlquiler().getIdSolicitud())
                        .map(solicitud -> mismaOportunidad(solicitud.getOportunidadComercial(), oportunidad))
                        .orElse(false));
        if (!existeFinalRechazada) {
            throw new BusinessException("Solo se puede cerrar no favorable con evaluacion final rechazada.");
        }
    }

    private boolean mismaOportunidad(OportunidadComercial izquierda, OportunidadComercial derecha) {
        return izquierda != null
                && derecha != null
                && izquierda.getIdOportunidad() != null
                && izquierda.getIdOportunidad().equals(derecha.getIdOportunidad());
    }

    private interface CierreOportunidad {
        void aplicar(OportunidadComercial oportunidad);
    }
}
