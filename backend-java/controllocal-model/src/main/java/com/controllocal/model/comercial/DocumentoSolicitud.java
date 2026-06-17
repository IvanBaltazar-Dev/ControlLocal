package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.comercial.enums.EstadoDocumentoSolicitud;
import com.controllocal.model.comercial.enums.OperacionRequerimiento;
import com.controllocal.model.comercial.enums.ResultadoRevisionDocumento;
import com.controllocal.model.comercial.enums.TipoDocumentoSolicitud;

public class DocumentoSolicitud {

    private Long idDocumento;
    private TipoDocumentoRequerido tipoDocumentoRequerido;
    private String nombreArchivo;
    private String rutaArchivo;
    private LocalDateTime fechaEntrega;
    private ResultadoRevisionDocumento resultadoRevision;
    private String observaciones;
    private EstadoDocumentoSolicitud estado;
    private SolicitudAlquiler solicitudAlquiler;

    public Long getIdDocumento() { return idDocumento; }
    public void setIdDocumento(Long idDocumento) { this.idDocumento = idDocumento; }
    public TipoDocumentoRequerido getTipoDocumentoRequerido() { return tipoDocumentoRequerido; }
    public void setTipoDocumentoRequerido(TipoDocumentoRequerido tipoDocumentoRequerido) {
        this.tipoDocumentoRequerido = tipoDocumentoRequerido;
    }

    /**
     * Adaptador temporal para consumidores anteriores al catalogo.
     */
    @Deprecated
    public TipoDocumentoSolicitud getTipoDocumento() {
        return tipoDocumentoRequerido != null
                ? TipoDocumentoSolicitud.porIdCatalogo(tipoDocumentoRequerido.getIdTipoDocumentoRequerido())
                : null;
    }

    @Deprecated
    public void setTipoDocumento(TipoDocumentoSolicitud tipoDocumento) {
        if (tipoDocumento == null) {
            tipoDocumentoRequerido = null;
            return;
        }
        TipoDocumentoRequerido catalogo = new TipoDocumentoRequerido();
        catalogo.setIdTipoDocumentoRequerido(tipoDocumento.getIdCatalogo());
        catalogo.setTipoOperacion(OperacionRequerimiento.ALQUILER);
        catalogo.setTipoDocumento(tipoDocumento.getDescripcion());
        catalogo.setObligatorio(true);
        catalogo.setActivo(true);
        tipoDocumentoRequerido = catalogo;
    }
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
