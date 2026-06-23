package com.controllocal.dao;

import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.EvaluacionSolicitud;

public interface EvaluacionSolicitudDAO extends CrudDAO<EvaluacionSolicitud> {
    Long crear(EvaluacionSolicitud evaluacion);
    Optional<EvaluacionSolicitud> buscarPorId(Long id);
    List<EvaluacionSolicitud> listarTodos();
    List<EvaluacionSolicitud> listarPorSolicitud(Long idSolicitud);
    boolean actualizar(EvaluacionSolicitud evaluacion);
    boolean eliminar(Long id);
}
