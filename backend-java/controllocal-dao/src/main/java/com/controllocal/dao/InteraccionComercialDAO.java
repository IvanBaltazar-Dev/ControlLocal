package com.controllocal.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.InteraccionComercial;

public interface InteraccionComercialDAO extends CrudDAO<InteraccionComercial> {
    Long crear(InteraccionComercial interaccion);
    Optional<InteraccionComercial> buscarPorId(Long id);
    List<InteraccionComercial> listarTodos();
    List<InteraccionComercial> listarPorOportunidad(Long idOportunidad);
    List<InteraccionComercial> listarPorProspeccion(Long idProspeccion);
    List<InteraccionComercial> listarPorCaptacion(Long idCaptacion);
    List<InteraccionComercial> listarPorCliente(Long idCliente);
    // Filas acotadas a los agentes dados (alcance por rol). Vacio si la coleccion viene vacia.
    List<InteraccionComercial> listarPorAgentes(java.util.Collection<Long> idsAgente);
    boolean actualizar(InteraccionComercial interaccion);
    boolean eliminar(Long id);
}
