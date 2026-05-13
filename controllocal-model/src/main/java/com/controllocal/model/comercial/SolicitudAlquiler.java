package com.controllocal.model.comercial;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class SolicitudAlquiler {

    private Long idSolicitud;
    private String codigoSolicitud;
    private LocalDate fechaRegistro;
    private BigDecimal montoPropuesto;
    private String plazoTentativo;
    private String observaciones;
    private EstadoSolicitudAlquiler estado;
    private LocalDateTime fechaActualizacionEstado;
    private ClienteInteresado clienteInteresado;
    private Captacion captacion;
    private AgenteInmobiliario agenteResponsable;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private List<DocumentoSolicitud> documentos = new ArrayList<>();
    private List<EvaluacionSolicitud> evaluaciones = new ArrayList<>();

    public Long getIdSolicitud() { return idSolicitud; }
    public void setIdSolicitud(Long idSolicitud) { this.idSolicitud = idSolicitud; }
    public String getCodigoSolicitud() { return codigoSolicitud; }
    public void setCodigoSolicitud(String codigoSolicitud) { this.codigoSolicitud = codigoSolicitud; }
    public LocalDate getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDate fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public BigDecimal getMontoPropuesto() { return montoPropuesto; }
    public void setMontoPropuesto(BigDecimal montoPropuesto) { this.montoPropuesto = montoPropuesto; }
    public String getPlazoTentativo() { return plazoTentativo; }
    public void setPlazoTentativo(String plazoTentativo) { this.plazoTentativo = plazoTentativo; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public EstadoSolicitudAlquiler getEstado() { return estado; }
    public void setEstado(EstadoSolicitudAlquiler estado) { this.estado = estado; }
    public LocalDateTime getFechaActualizacionEstado() { return fechaActualizacionEstado; }
    public void setFechaActualizacionEstado(LocalDateTime fechaActualizacionEstado) { this.fechaActualizacionEstado = fechaActualizacionEstado; }
    public ClienteInteresado getClienteInteresado() { return clienteInteresado; }
    public void setClienteInteresado(ClienteInteresado clienteInteresado) { this.clienteInteresado = clienteInteresado; }
    public Captacion getCaptacion() { return captacion; }
    public void setCaptacion(Captacion captacion) { this.captacion = captacion; }
    public AgenteInmobiliario getAgenteResponsable() { return agenteResponsable; }
    public void setAgenteResponsable(AgenteInmobiliario agenteResponsable) { this.agenteResponsable = agenteResponsable; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
    public List<DocumentoSolicitud> getDocumentos() { return documentos; }
    public void setDocumentos(List<DocumentoSolicitud> documentos) { this.documentos = documentos; }
    public List<EvaluacionSolicitud> getEvaluaciones() { return evaluaciones; }
    public void setEvaluaciones(List<EvaluacionSolicitud> evaluaciones) { this.evaluaciones = evaluaciones; }

    public void registrar() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDate.now();
        }
        actualizarEstado(EstadoSolicitudAlquiler.REGISTRADA);
    }

    public void aprobar() { actualizarEstado(EstadoSolicitudAlquiler.APROBADA); }
    public void rechazar() { actualizarEstado(EstadoSolicitudAlquiler.RECHAZADA); }
    public void solicitarAjustes() { actualizarEstado(EstadoSolicitudAlquiler.OBSERVADA); }
    public void desistir() { actualizarEstado(EstadoSolicitudAlquiler.DESISTIDA); }

    public void actualizarEstado(EstadoSolicitudAlquiler estado) {
        this.estado = estado;
        this.fechaActualizacionEstado = LocalDateTime.now();
        this.fechaActualizacion = this.fechaActualizacionEstado;
    }
}
