package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.usuario.UsuarioInterno;

public class EvaluacionSolicitud {

    private long idEvaluacion;
    private LocalDateTime fechaEvaluacion;
    private String resultado;
    private String observaciones;
    private SolicitudAlquiler solicitudAlquiler;
    private UsuarioInterno responsableEvaluacion;

    public long getIdEvaluacion() {
        return idEvaluacion;
    }

    public void setIdEvaluacion(long idEvaluacion) {
        this.idEvaluacion = idEvaluacion;
    }

    public LocalDateTime getFechaEvaluacion() {
        return fechaEvaluacion;
    }

    public void setFechaEvaluacion(LocalDateTime fechaEvaluacion) {
        this.fechaEvaluacion = fechaEvaluacion;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public SolicitudAlquiler getSolicitudAlquiler() {
        return solicitudAlquiler;
    }

    public void setSolicitudAlquiler(SolicitudAlquiler solicitudAlquiler) {
        this.solicitudAlquiler = solicitudAlquiler;
    }

    public UsuarioInterno getResponsableEvaluacion() {
        return responsableEvaluacion;
    }

    public void setResponsableEvaluacion(UsuarioInterno responsableEvaluacion) {
        this.responsableEvaluacion = responsableEvaluacion;
    }

    public void emitirResultado(String resultado) {
    }

    public void actualizarResultado(String resultado) {
    }

    public void registrarObservacion(String observacion) {
    }
}
