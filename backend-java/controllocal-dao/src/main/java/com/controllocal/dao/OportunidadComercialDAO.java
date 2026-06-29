package com.controllocal.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.OportunidadComercial;

public interface OportunidadComercialDAO extends CrudDAO<OportunidadComercial> {
    Long crear(OportunidadComercial oportunidad);
    Optional<OportunidadComercial> buscarPorId(Long id);
    List<OportunidadComercial> listarTodos();
    // Filas acotadas a los agentes dados (alcance por rol). Vacio si la coleccion viene vacia.
    List<OportunidadComercial> listarPorAgentes(java.util.Collection<Long> idsAgente);
    // Filas acotadas a las captaciones dadas (alcance del broker). Vacio si la coleccion viene vacia.
    List<OportunidadComercial> listarPorCaptaciones(java.util.Collection<Long> idsCaptacion);
    // Oportunidades de un cliente (ficha comercial). Vacio si el id viene nulo.
    List<OportunidadComercial> listarPorCliente(Long idCliente);
    // Oportunidades cuyo local pertenece al propietario (ficha comercial). Vacio si el id viene nulo.
    List<OportunidadComercial> listarPorPropietario(Long idPropietario);
    // Carga en bloque solo las oportunidades pedidas (enriquecer una pagina sin traer la tabla). Vacio si la coleccion viene vacia.
    List<OportunidadComercial> listarPorIds(java.util.Collection<Long> ids);
    boolean actualizar(OportunidadComercial oportunidad);
    boolean eliminar(Long id);
}
