package com.controllocal.dao;

import com.controllocal.model.comercial.EvaluacionSolicitud;

import java.util.List;
import java.util.Optional;

public interface EvaluacionSolicitudDAO {
    Long crear(EvaluacionSolicitud evaluacion);
    Optional<EvaluacionSolicitud> buscarPorId(Long id);
    List<EvaluacionSolicitud> listarTodos();
    boolean actualizar(EvaluacionSolicitud evaluacion);
    boolean eliminar(Long id);
}
