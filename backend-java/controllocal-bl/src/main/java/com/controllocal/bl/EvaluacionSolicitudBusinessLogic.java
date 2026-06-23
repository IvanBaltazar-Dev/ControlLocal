package com.controllocal.bl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.controllocal.model.comercial.enums.TipoEvaluacionSolicitud;
import com.controllocal.model.comercial.EvaluacionSolicitud;
import com.controllocal.model.usuario.Broker;

public interface EvaluacionSolicitudBusinessLogic {

    public Long registrar(EvaluacionSolicitud evaluacion);
    public Optional<EvaluacionSolicitud> buscarPorId(Long idEvaluacion);
    public List<EvaluacionSolicitud> listarTodos();
    public List<EvaluacionSolicitud> listarPorBroker(Long idBroker);
    public List<EvaluacionSolicitud> listarPorSolicitud(Long idSolicitud);
    public boolean actualizar(EvaluacionSolicitud evaluacion);
    public boolean eliminar(Long idEvaluacion);
}

