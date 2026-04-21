package com.controllocal.model.comercial;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.enums.EstadoSolicitudAlquiler;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class SolicitudAlquiler {

    private long idSolicitud;
    private String codigoSolicitud;
    private LocalDate fechaRegistro;
    private double montoPropuesto;
    private String plazoTentativo;
    private String observaciones;
    private EstadoSolicitudAlquiler estado;
    private LocalDateTime fechaActualizacionEstado;
    private ClienteInteresado clienteInteresado;
    private Captacion captacion;
    private AgenteInmobiliario agenteResponsable;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private List<DocumentoSolicitud> documentosSolicitud = new ArrayList<>();
    private List<EvaluacionSolicitud> evaluacionesSolicitud = new ArrayList<>();

    public long getIdSolicitud() {
        return idSolicitud;
    }

    public void setIdSolicitud(long idSolicitud) {
        this.idSolicitud = idSolicitud;
    }

    public String getCodigoSolicitud() {
        return codigoSolicitud;
    }

    public void setCodigoSolicitud(String codigoSolicitud) {
        this.codigoSolicitud = codigoSolicitud;
    }

    public LocalDate getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDate fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public double getMontoPropuesto() {
        return montoPropuesto;
    }

    public void setMontoPropuesto(double montoPropuesto) {
        this.montoPropuesto = montoPropuesto;
    }

    public String getPlazoTentativo() {
        return plazoTentativo;
    }

    public void setPlazoTentativo(String plazoTentativo) {
        this.plazoTentativo = plazoTentativo;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public EstadoSolicitudAlquiler getEstado() {
        return estado;
    }

    public void setEstado(EstadoSolicitudAlquiler estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaActualizacionEstado() {
        return fechaActualizacionEstado;
    }

    public void setFechaActualizacionEstado(LocalDateTime fechaActualizacionEstado) {
        this.fechaActualizacionEstado = fechaActualizacionEstado;
    }

    public ClienteInteresado getClienteInteresado() {
        return clienteInteresado;
    }

    public void setClienteInteresado(ClienteInteresado clienteInteresado) {
        this.clienteInteresado = clienteInteresado;
    }

    public Captacion getCaptacion() {
        return captacion;
    }

    public void setCaptacion(Captacion captacion) {
        this.captacion = captacion;
    }

    public AgenteInmobiliario getAgenteResponsable() {
        return agenteResponsable;
    }

    public void setAgenteResponsable(AgenteInmobiliario agenteResponsable) {
        this.agenteResponsable = agenteResponsable;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public List<DocumentoSolicitud> getDocumentosSolicitud() {
        return documentosSolicitud;
    }

    public void setDocumentosSolicitud(List<DocumentoSolicitud> documentosSolicitud) {
        this.documentosSolicitud = documentosSolicitud;
    }

    public List<EvaluacionSolicitud> getEvaluacionesSolicitud() {
        return evaluacionesSolicitud;
    }

    public void setEvaluacionesSolicitud(List<EvaluacionSolicitud> evaluacionesSolicitud) {
        this.evaluacionesSolicitud = evaluacionesSolicitud;
    }

    public void registrar() {
    }

    public void aprobar() {
    }

    public void rechazar() {
    }

    public void solicitarAjustes() {
    }

    public void desistir() {
    }
}
