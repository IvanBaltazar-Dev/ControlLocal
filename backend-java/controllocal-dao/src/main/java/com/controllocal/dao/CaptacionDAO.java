package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.Captacion;

public interface CaptacionDAO extends CrudDAO<Captacion> {
    Long crear(Captacion captacion);

    Optional<Captacion> buscarPorId(Long id);

    List<Captacion> listarTodos();

    boolean actualizar(Captacion captacion);

    boolean eliminar(Long id);

    public boolean perteneceAlAgente(Long idLocal, Long idAgente);
}
