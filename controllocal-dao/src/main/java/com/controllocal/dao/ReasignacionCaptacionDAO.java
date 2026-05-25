package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.ReasignacionCaptacion;

public interface ReasignacionCaptacionDAO extends CrudDAO<ReasignacionCaptacion> {
    Long crear(ReasignacionCaptacion reasignacion);
    Optional<ReasignacionCaptacion> buscarPorId(Long id);
    List<ReasignacionCaptacion> listarTodos();
    boolean actualizar(ReasignacionCaptacion reasignacion);
    boolean eliminar(Long id);
}
