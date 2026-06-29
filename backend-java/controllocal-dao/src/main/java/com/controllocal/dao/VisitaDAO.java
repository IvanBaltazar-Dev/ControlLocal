package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Visita;

public interface VisitaDAO extends CrudDAO<Visita> {
    Long crear(Visita visita);
    Optional<Visita> buscarPorId(Long id);
    List<Visita> listarTodos();
    // Filas acotadas a los agentes dados (alcance por rol). Vacio si la coleccion viene vacia.
    List<Visita> listarPorAgentes(java.util.Collection<Long> idsAgente);
    // Filas acotadas a las captaciones dadas (alcance del broker). Vacio si la coleccion viene vacia.
    List<Visita> listarPorCaptaciones(java.util.Collection<Long> idsCaptacion);
    // Visitas de un cliente (ficha comercial). Vacio si el id viene nulo.
    List<Visita> listarPorCliente(Long idCliente);
    boolean actualizar(Visita visita);
    boolean eliminar(Long id);
}
