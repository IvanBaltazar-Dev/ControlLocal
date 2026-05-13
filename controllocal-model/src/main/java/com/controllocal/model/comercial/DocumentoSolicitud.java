package com.controllocal.model.comercial;

import java.time.LocalDateTime;

public class DocumentoSolicitud {

    private Long idDocumento;
    private String tipoDocumento;
    private String nombreArchivo;
    private String rutaArchivo;
    private LocalDateTime fechaEntrega;
    private ResultadoRevisionDocumento resultadoRevision;
    private String observaciones;
    private EstadoDocumentoSolicitud estado;
    private SolicitudAlquiler solicitudAlquiler;

    public Long getIdDocumento() { return idDocumento; }
    public void setIdDocumento(Long idDocumento) { this.idDocumento = idDocumento; }
    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }
    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDateTime fechaEntrega) { this.fechaEntrega = fechaEntrega; }
    public ResultadoRevisionDocumento getResultadoRevision() { return resultadoRevision; }
    public void setResultadoRevision(ResultadoRevisionDocumento resultadoRevision) { this.resultadoRevision = resultadoRevision; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public EstadoDocumentoSolicitud getEstado() { return estado; }
    public void setEstado(EstadoDocumentoSolicitud estado) { this.estado = estado; }
    public SolicitudAlquiler getSolicitudAlquiler() { return solicitudAlquiler; }
    public void setSolicitudAlquiler(SolicitudAlquiler solicitudAlquiler) { this.solicitudAlquiler = solicitudAlquiler; }

    public void registrarEntrega() {
        if (fechaEntrega == null) {
            fechaEntrega = LocalDateTime.now();
        }
        if (resultadoRevision == null) {
            resultadoRevision = ResultadoRevisionDocumento.PENDIENTE;
        }
        estado = EstadoDocumentoSolicitud.REGISTRADO;
    }

    public void actualizarRevision(ResultadoRevisionDocumento resultado, String observacion) {
        this.resultadoRevision = resultado;
        this.observaciones = observacion;
        this.estado = ResultadoRevisionDocumento.CONFORME == resultado
                ? EstadoDocumentoSolicitud.VALIDADO
                : EstadoDocumentoSolicitud.OBSERVADO;
    }

    public void cambiarEstado(EstadoDocumentoSolicitud estado) {
        this.estado = estado;
    }
}
