package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.usuario.AgenteInmobiliario;

public class MotivoNoContinuidad {

    private long idMotivoNoContinuidad;
    private LocalDateTime fechaHora;
    private String razonPrincipal;
    private String observaciones;
    private AgenteInmobiliario agenteResponsable;
    private InteraccionComercial interaccionComercial;
    private Visita visita;
    private SolicitudAlquiler solicitudAlquiler;

    public long getIdMotivoNoContinuidad() {
        return idMotivoNoContinuidad;
    }

    public void setIdMotivoNoContinuidad(long idMotivoNoContinuidad) {
        this.idMotivoNoContinuidad = idMotivoNoContinuidad;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getRazonPrincipal() {
        return razonPrincipal;
    }

    public void setRazonPrincipal(String razonPrincipal) {
        this.razonPrincipal = razonPrincipal;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public AgenteInmobiliario getAgenteResponsable() {
        return agenteResponsable;
    }

    public void setAgenteResponsable(AgenteInmobiliario agenteResponsable) {
        this.agenteResponsable = agenteResponsable;
    }

    public InteraccionComercial getInteraccionComercial() {
        return interaccionComercial;
    }

    public void setInteraccionComercial(InteraccionComercial interaccionComercial) {
        this.interaccionComercial = interaccionComercial;
    }

    public Visita getVisita() {
        return visita;
    }

    public void setVisita(Visita visita) {
        this.visita = visita;
    }

    public SolicitudAlquiler getSolicitudAlquiler() {
        return solicitudAlquiler;
    }

    public void setSolicitudAlquiler(SolicitudAlquiler solicitudAlquiler) {
        this.solicitudAlquiler = solicitudAlquiler;
    }

    public void registrarMotivo() {
    }

    public void actualizarObservacion() {
    }
}
