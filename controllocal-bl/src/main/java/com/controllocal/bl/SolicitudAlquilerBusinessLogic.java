package com.controllocal.bl;

import com.controllocal.model.comercial.SolicitudAlquiler;

import java.util.List;

public interface SolicitudAlquilerBusinessLogic {

    Long registrarSolicitud(SolicitudAlquiler solicitud);

    void aprobarSolicitud(Long solicitudId, Long brokerId);

    void rechazarSolicitud(Long solicitudId, Long brokerId, String motivo);

    void adjuntarDocumento(Long solicitudId, Long documentoId);

    List<SolicitudAlquiler> listarSolicitudesPendientes();
}
