package com.controllocal.bl;

import com.controllocal.model.comercial.SolicitudAlquiler;
import java.util.List;

public interface SolicitudAlquilerBusinessLogic {

    Long registerRequest(SolicitudAlquiler request);

    void approveRequest(Long requestId, Long brokerId);

    void rejectRequest(Long requestId, Long brokerId, String reason);

    void attachDocument(Long requestId, Long documentId);

    List<SolicitudAlquiler> listPendingRequests();
}
