package com.controllocal.bl;

import com.controllocal.bl.support.BusinessValidations;
import com.controllocal.bl.support.TransactionRunner;
import com.controllocal.dao.CaptacionDAO;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.dao.impl.CaptacionDAOImpl;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.SolicitudAlquiler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class SolicitudAlquilerBusinessLogic {

    private final SolicitudAlquilerDAO solicitudDAO;
    private final CaptacionDAO captacionDAO;

    public SolicitudAlquilerBusinessLogic() {
        this(new SolicitudAlquilerDAOImpl(), new CaptacionDAOImpl());
    }

    public SolicitudAlquilerBusinessLogic(SolicitudAlquilerDAO solicitudDAO, CaptacionDAO captacionDAO) {
        this.solicitudDAO = solicitudDAO;
        this.captacionDAO = captacionDAO;
    }

    public Long registrar(SolicitudAlquiler solicitud) {
        return TransactionRunner.write(() -> {
            BusinessValidations.solicitud(solicitud);
            Captacion captacion = captacionDAO.buscarPorId(BusinessValidations.idCaptacion(solicitud.getCaptacion()))
                    .orElseThrow(() -> new BusinessException("Captacion no encontrada para solicitud."));
            BusinessValidations.captacionActiva(captacion);
            solicitud.setEstado(EstadoSolicitudAlquiler.REGISTRADA);
            solicitud.setFechaActualizacionEstado(LocalDateTime.now());
            return solicitudDAO.crear(solicitud);
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
        return TransactionRunner.write(() -> {
            BusinessValidations.id(solicitud != null ? solicitud.getIdSolicitud() : null, "El id de solicitud");
            BusinessValidations.solicitud(solicitud);
            return solicitudDAO.actualizar(solicitud);
        });
    }

    public boolean eliminar(Long idSolicitud) {
        return TransactionRunner.write(() -> {
            BusinessValidations.id(idSolicitud, "El id de solicitud");
            return solicitudDAO.eliminar(idSolicitud);
        });
    }
}
