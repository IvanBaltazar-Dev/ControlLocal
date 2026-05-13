package com.controllocal.bl.impl;

import com.controllocal.bl.RentalRequestLifecycle;
import com.controllocal.config.DatabaseConfig;
import com.controllocal.dao.SolicitudAlquilerDAO;
import com.controllocal.dao.impl.SolicitudAlquilerDAOImpl;
import com.controllocal.model.comercial.SolicitudAlquiler;

import java.util.List;

public class RentalRequestLifecycleImpl implements RentalRequestLifecycle {

    private final SolicitudAlquilerDAO solicitudDAO =
            new SolicitudAlquilerDAOImpl();

    @Override
    public Long registerRequest(SolicitudAlquiler request) {

        try {

            if(request == null){
                throw new RuntimeException("Solicitud inválida.");
            }

            if(request.getClienteInteresado() == null){
                throw new RuntimeException("Debe existir cliente.");
            }

            if(request.getCaptacion() == null){
                throw new RuntimeException("Debe existir captación.");
            }

            Long id = solicitudDAO.crear(request);

            DatabaseConfig.commit();

            return id;

        } catch (Exception ex){

            DatabaseConfig.rollback();
            throw new RuntimeException(ex.getMessage());

        } finally {
            DatabaseConfig.close();
        }
    }

    @Override
    public void approveRequest(Long requestId, Long brokerId) {
        System.out.println("Solicitud aprobada.");
    }

    @Override
    public void rejectRequest(Long requestId, Long brokerId, String reason) {
        System.out.println("Solicitud rechazada.");
    }

    @Override
    public void attachDocument(Long requestId, Long documentId) {
        System.out.println("Documento adjuntado.");
    }

    @Override
    public List<SolicitudAlquiler> listPendingRequests() {
        return solicitudDAO.listarTodos();
    }
}
