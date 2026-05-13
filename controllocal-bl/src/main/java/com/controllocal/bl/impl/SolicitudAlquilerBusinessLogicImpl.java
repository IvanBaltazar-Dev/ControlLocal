package com.controllocal.bl.impl;

import com.controllocal.bl.SolicitudAlquilerBusinessLogic;
import com.controllocal.config.DatabaseConfig;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.model.comercial.SolicitudAlquiler;

import java.util.List;

public class SolicitudAlquilerBusinessLogicImpl implements SolicitudAlquilerBusinessLogic {

    private final SolicitudAlquilerDAO solicitudDAO = new SolicitudAlquilerDAOImpl();

    @Override
    public Long registrarSolicitud(SolicitudAlquiler solicitud) {
        try {
            if (solicitud == null) {
                throw new RuntimeException("Solicitud inválida.");
            }

            if (solicitud.getClienteInteresado() == null) {
                throw new RuntimeException("Debe existir cliente interesado.");
            }

            if (solicitud.getCaptacion() == null) {
                throw new RuntimeException("Debe existir captación asociada.");
            }

            Long id = solicitudDAO.crear(solicitud);

            DatabaseConfig.commit();

            return id;

        } catch (Exception ex) {
            DatabaseConfig.rollback();
            throw new RuntimeException("Error al registrar solicitud: " + ex.getMessage());
        } finally {
            DatabaseConfig.close();
        }
    }

    @Override
    public void aprobarSolicitud(Long solicitudId, Long brokerId) {
        System.out.println("Solicitud aprobada.");
    }

    @Override
    public void rechazarSolicitud(Long solicitudId, Long brokerId, String motivo) {
        System.out.println("Solicitud rechazada. Motivo: " + motivo);
    }

    @Override
    public void adjuntarDocumento(Long solicitudId, Long documentoId) {
        System.out.println("Documento adjuntado.");
    }

    @Override
    public List<SolicitudAlquiler> listarSolicitudesPendientes() {
        return solicitudDAO.listarTodos();
    }
}
