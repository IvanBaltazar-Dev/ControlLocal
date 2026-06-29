package com.controllocal.bl;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.comercial.SolicitudAlquiler;

public interface SolicitudAlquilerBusinessLogic {

    public Long registrar(SolicitudAlquiler solicitud);
    public Optional<SolicitudAlquiler> buscarPorId(Long idSolicitud);
    public List<SolicitudAlquiler> listarTodos();
    // Carga en bloque solo las solicitudes pedidas (enriquecer una pagina sin traer todo).
    public List<SolicitudAlquiler> listarPorIds(Collection<Long> ids);
    // Solicitudes acotadas a los agentes dados (alcance por rol del dashboard).
    public List<SolicitudAlquiler> listarPorAgentes(Collection<Long> idsAgente);
    public List<SolicitudAlquiler> listarPorCaptaciones(Collection<Long> idsCaptacion);
    public List<SolicitudAlquiler> listarPorCliente(Long idCliente);
    public List<SolicitudAlquiler> listarPorPropietario(Long idPropietario);
    public boolean actualizar(SolicitudAlquiler solicitud);
    public boolean eliminar(Long idSolicitud);

    // Mueve la solicitud (Registrada u Observada) a En revision y notifica al broker
    // supervisor del agente responsable. Devuelve true si se persistio el cambio.
    public boolean reenviarAEvaluacion(Long idSolicitud);
}

