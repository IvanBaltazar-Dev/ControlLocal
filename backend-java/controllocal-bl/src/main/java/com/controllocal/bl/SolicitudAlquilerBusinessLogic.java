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
    public Optional<SolicitudAlquiler> buscarPorCodigo(String codigoSolicitud);
    public List<SolicitudAlquiler> listarTodos();
    // Carga en bloque solo las solicitudes pedidas (enriquecer una pagina sin traer todo).
    public List<SolicitudAlquiler> listarPorIds(Collection<Long> ids);
    // Solicitudes acotadas a los agentes dados (alcance por rol del dashboard).
    public List<SolicitudAlquiler> listarPorAgentes(Collection<Long> idsAgente);
    // Pagina de solicitudes (LIMIT/OFFSET en SQL) con alcance por rol y filtros opcionales:
    // idsAgente == null => todas (admin); vacia => sin resultados (broker sin equipo).
    public List<SolicitudAlquiler> listarPagina(
            Collection<Long> idsAgente, Long idOportunidad, Long idCaptacion, int offset, int limite);
    // Conteo total con el mismo alcance y filtros que listarPagina (para la paginacion).
    public long contar(Collection<Long> idsAgente, Long idOportunidad, Long idCaptacion);
    public List<SolicitudAlquiler> listarPorCaptaciones(Collection<Long> idsCaptacion);
    public List<SolicitudAlquiler> listarPorCliente(Long idCliente);
    public List<SolicitudAlquiler> listarPorPropietario(Long idPropietario);
    public boolean actualizar(SolicitudAlquiler solicitud);
    public boolean eliminar(Long idSolicitud);

    // Mueve la solicitud (Registrada u Observada) a En revision y notifica al broker
    // supervisor del agente responsable. Devuelve true si se persistio el cambio.
    public boolean reenviarAEvaluacion(Long idSolicitud);
}
