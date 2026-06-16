package com.controllocal.dao;

import java.util.List;

import com.controllocal.model.comercial.Tarea;
import com.controllocal.model.comercial.enums.EstadoTarea;

public interface TareaDAO extends CrudDAO<Tarea> {
    List<Tarea> listarPorAgente(Long idAgente, EstadoTarea estado);
}
