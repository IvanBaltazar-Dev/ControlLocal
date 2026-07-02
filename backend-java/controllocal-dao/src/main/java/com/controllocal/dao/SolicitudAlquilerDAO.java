package com.controllocal.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.SolicitudAlquiler;

public interface SolicitudAlquilerDAO extends CrudDAO<SolicitudAlquiler> {
    Long crear(SolicitudAlquiler solicitud);
    Optional<SolicitudAlquiler> buscarPorId(Long id);
    Optional<SolicitudAlquiler> buscarPorCodigo(String codigo);
    List<SolicitudAlquiler> listarTodos();
    boolean actualizar(SolicitudAlquiler solicitud);
    boolean eliminar(Long id);

    // Carga en bloque solo las solicitudes pedidas (para enriquecer una pagina sin traer
    // la tabla completa). Devuelve lista vacia si la coleccion viene vacia.
    List<SolicitudAlquiler> listarPorIds(Collection<Long> ids);

    // Solicitudes acotadas a los agentes dados (alcance por rol). Vacio si la coleccion viene vacia.
    List<SolicitudAlquiler> listarPorAgentes(Collection<Long> idsAgente);

    // Pagina de solicitudes con LIMIT/OFFSET en SQL. Alcance por rol: idsAgente == null => todas
    // (admin); vacia => sin resultados (broker sin equipo). Filtros opcionales por oportunidad y
    // captacion (null = sin filtro). Reemplaza el "traer todo y cortar en memoria".
    List<SolicitudAlquiler> listarPagina(
            Collection<Long> idsAgente, Long idOportunidad, Long idCaptacion, int offset, int limite);

    // Conteo total (para la paginacion) con el mismo alcance y filtros que listarPagina.
    long contar(Collection<Long> idsAgente, Long idOportunidad, Long idCaptacion);
    // Solicitudes acotadas a las captaciones dadas (alcance del broker). Vacio si la coleccion viene vacia.
    List<SolicitudAlquiler> listarPorCaptaciones(Collection<Long> idsCaptacion);
    // Solicitudes de un cliente (ficha comercial). Vacio si el id viene nulo.
    List<SolicitudAlquiler> listarPorCliente(Long idCliente);
    // Solicitudes cuyo local pertenece al propietario (ficha comercial). Vacio si el id viene nulo.
    List<SolicitudAlquiler> listarPorPropietario(Long idPropietario);
}
