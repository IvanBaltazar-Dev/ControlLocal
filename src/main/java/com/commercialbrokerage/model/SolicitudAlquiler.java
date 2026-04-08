package com.commercialbrokerage.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SolicitudAlquiler {

    private long idSolicitud;
    private LocalDateTime fechaRegistro;
    private double montoPropuesto;
    private String plazoTentativo;
    private String observaciones;
    private String estado;
    private ClienteInteresado clienteInteresado;
    private Captacion captacion;
    private AgenteInmobiliario agenteInmobiliario;
    private List<DocumentoSolicitud> documentosSolicitud = new ArrayList<>();
    private List<EvaluacionSolicitud> evaluacionesSolicitud = new ArrayList<>();

    public long getIdSolicitud() {
        return idSolicitud;
    }

    public void setIdSolicitud(long idSolicitud) {
        this.idSolicitud = idSolicitud;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
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

    public AgenteInmobiliario getAgenteInmobiliario() {
        return agenteInmobiliario;
    }

    public void setAgenteInmobiliario(AgenteInmobiliario agenteInmobiliario) {
        this.agenteInmobiliario = agenteInmobiliario;
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

    public void aprobar() {
    }

    public void rechazar() {
    }

    public void observar(String observacion) {
    }

    public void desistir() {
    }
}
