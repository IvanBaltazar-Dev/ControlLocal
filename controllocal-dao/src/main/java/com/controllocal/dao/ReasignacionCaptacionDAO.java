package com.controllocal.dao;

import com.controllocal.model.comercial.ReasignacionCaptacion;

import java.util.List;
import java.util.Optional;

public interface ReasignacionCaptacionDAO {
    Long crear(ReasignacionCaptacion reasignacion);
    Optional<ReasignacionCaptacion> buscarPorId(Long id);
    List<ReasignacionCaptacion> listarTodos();
    boolean actualizar(ReasignacionCaptacion reasignacion);
    boolean eliminar(Long id);
}
