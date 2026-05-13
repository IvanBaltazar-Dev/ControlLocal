package com.controllocal.bl;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EstadoCaptacion;
import java.util.List;

public interface customerAcquisitionLifecycle {

    /**
     * Registra una nueva captación vinculando un local y un agente.
     * Debe validar que el agente esté disponible y el local no tenga captaciones activas.
     */
    Long registerAcquisition(Captacion acquisition);

    /**
     * Cambia el agente responsable de una captación.
     * Debe registrar el movimiento en la tabla de reasignaciones para auditoría.
     */
    void reassignAgent(Long acquisitionId, Long newAgentId, Long brokerId, String reason);

    /**
     * Proceso de revisión por parte de un Broker.
     * Actualiza el estado a ACTIVA o RECHAZADA y guarda las observaciones de revisión.
     */
    void reviewAcquisition(Long acquisitionId, Long brokerId, boolean approved, String remarks);

    /**
     * Cierra una captación (soft delete), cambiando su estado y registrando la fecha de fin.
     */
    void closeAcquisition(Long acquisitionId);

    /**
     * Lista las captaciones que requieren atención inmediata (ej. Pendientes de revisión).
     */
    List<Captacion> listPendingReviews();
}