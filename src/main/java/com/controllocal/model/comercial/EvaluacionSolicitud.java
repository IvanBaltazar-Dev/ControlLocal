package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.enums.ResultadoEvaluacionSolicitud;
import com.controllocal.model.enums.TipoEvaluacionSolicitud;
import com.controllocal.model.usuario.Broker;

public class EvaluacionSolicitud {

    private long idEvaluacion;
    private LocalDateTime fechaEvaluacion;
    private ResultadoEvaluacionSolicitud resultado;
    private String observaciones;
    private TipoEvaluacionSolicitud tipoEvaluacion;
    private SolicitudAlquiler solicitudAlquiler;
    private Broker responsableEvaluacion;

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

    public ResultadoEvaluacionSolicitud getResultado() {
        return resultado;
    }

    public void setResultado(ResultadoEvaluacionSolicitud resultado) {
        this.resultado = resultado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public TipoEvaluacionSolicitud getTipoEvaluacion() {
        return tipoEvaluacion;
    }

    public void setTipoEvaluacion(TipoEvaluacionSolicitud tipoEvaluacion) {
        this.tipoEvaluacion = tipoEvaluacion;
    }

    public SolicitudAlquiler getSolicitudAlquiler() {
        return solicitudAlquiler;
    }

    public void setSolicitudAlquiler(SolicitudAlquiler solicitudAlquiler) {
        this.solicitudAlquiler = solicitudAlquiler;
    }

    public Broker getResponsableEvaluacion() {
        return responsableEvaluacion;
    }

    public void setResponsableEvaluacion(Broker responsableEvaluacion) {
        this.responsableEvaluacion = responsableEvaluacion;
    }

    public void emitirResultado(ResultadoEvaluacionSolicitud resultado) {
    }

    public void actualizarResultado(ResultadoEvaluacionSolicitud resultado) {
    }

    public void registrarObservacion(String observacion) {
    }
}
