package com.controllocal.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Captacion;

public interface CaptacionDAO extends CrudDAO<Captacion> {
    Long crear(Captacion captacion);

    Optional<Captacion> buscarPorId(Long id);

    List<Captacion> listarTodos();

    // Filas acotadas a los agentes dados (alcance por rol). Vacio si la coleccion viene vacia.
    List<Captacion> listarPorAgentes(java.util.Collection<Long> idsAgente);
    // Captaciones cuyo local pertenece al propietario (ficha comercial). Vacio si el id viene nulo.
    List<Captacion> listarPorPropietario(Long idPropietario);

    boolean actualizar(Captacion captacion);

    boolean eliminar(Long id);

    public boolean perteneceAlAgente(Long idLocal, Long idAgente);
}
