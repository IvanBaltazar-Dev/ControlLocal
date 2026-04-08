package com.commercialbrokerage.model;

import java.time.LocalDateTime;

public class DocumentoSolicitud {

    private long idDocumento;
    private String tipoDocumento;
    private String nombreArchivo;
    private LocalDateTime fechaEntrega;
    private String resultadoRevision;
    private String observaciones;
    private String estado;
    private SolicitudAlquiler solicitudAlquiler;

    public long getIdDocumento() {
        return idDocumento;
    }

    public void setIdDocumento(long idDocumento) {
        this.idDocumento = idDocumento;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public LocalDateTime getFechaEntrega() {
        return fechaEntrega;
    }

    public void setFechaEntrega(LocalDateTime fechaEntrega) {
        this.fechaEntrega = fechaEntrega;
    }

    public String getResultadoRevision() {
        return resultadoRevision;
    }

    public void setResultadoRevision(String resultadoRevision) {
        this.resultadoRevision = resultadoRevision;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public SolicitudAlquiler getSolicitudAlquiler() {
        return solicitudAlquiler;
    }

    public void setSolicitudAlquiler(SolicitudAlquiler solicitudAlquiler) {
        this.solicitudAlquiler = solicitudAlquiler;
    }

    public void registrarEntrega() {
    }

    public void actualizarRevision(String resultadoRevision) {
    }

    public void cambiarEstado(String estado) {
    }
}
