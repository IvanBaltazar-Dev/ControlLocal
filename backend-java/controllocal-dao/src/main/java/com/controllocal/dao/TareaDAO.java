package com.controllocal.dao;

import java.util.List;

import com.controllocal.model.comercial.Tarea;
import com.controllocal.model.comercial.enums.EstadoTarea;
import com.controllocal.model.comercial.enums.TipoEntidad;

public interface TareaDAO extends CrudDAO<Tarea> {
    List<Tarea> listarPorAgente(Long idAgente, EstadoTarea estado);

    // Tareas asociadas a una entidad concreta (oportunidad, local, captacion, etc.),
    // para resolverlas en bloque cuando esa entidad se cierra. Usa idx_tarea_entidad.
    List<Tarea> listarPorEntidad(TipoEntidad entidadTipo, Long entidadId);
}
