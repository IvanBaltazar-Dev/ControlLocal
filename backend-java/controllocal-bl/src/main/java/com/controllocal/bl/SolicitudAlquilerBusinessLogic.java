package com.controllocal.bl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.SolicitudAlquiler;

public interface SolicitudAlquilerBusinessLogic {

    public Long registrar(SolicitudAlquiler solicitud);
    public Optional<SolicitudAlquiler> buscarPorId(Long idSolicitud);
    public List<SolicitudAlquiler> listarTodos();
    public boolean actualizar(SolicitudAlquiler solicitud);
    public boolean eliminar(Long idSolicitud);

    // Mueve la solicitud (Registrada u Observada) a En revision y notifica al broker
    // supervisor del agente responsable. Devuelve true si se persistio el cambio.
    public boolean reenviarAEvaluacion(Long idSolicitud);
}

