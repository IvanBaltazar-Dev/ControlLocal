package com.controllocal.bl;

import com.controllocal.model.comercial.Captacion;

import java.util.List;

public interface customerAcquisitionLifecycle {

    /**
     * Registra una nueva captacion vinculando un local y un agente.
     */
    Long registerAcquisition(Captacion acquisition);

    /**
     * Cambia el agente responsable de una captacion y registra auditoria.
     */
    void reassignAgent(Long acquisitionId, Long newAgentId, Long brokerId, String reason);

    /**
     * Proceso de revision por parte de un broker.
     */
    void reviewAcquisition(Long acquisitionId, Long brokerId, boolean approved, String remarks);

    /**
     * Metodo historico. Para cumplir trazabilidad se debe usar closeAcquisition(id, brokerId, reason).
     */
    void closeAcquisition(Long acquisitionId);

    /**
     * Cierra una captacion con broker responsable y motivo.
     */
    default void closeAcquisition(Long acquisitionId, Long brokerId, String reason) {
        throw new UnsupportedOperationException("Operacion no implementada.");
    }

    /**
     * Lista captaciones pendientes de revision.
     */
    List<Captacion> listPendingReviews();
}
