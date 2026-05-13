package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.usuario.AgenteInmobiliario;

public class MotivoNoContinuidad {

    private Long idMotivoNoContinuidad;
    private LocalDateTime fechaHora;
    private String razonPrincipal;
    private String observaciones;
    private AgenteInmobiliario agenteResponsable;
    private InteraccionComercial interaccionComercial;
    private Visita visita;
    private SolicitudAlquiler solicitudAlquiler;

    public Long getIdMotivoNoContinuidad() { return idMotivoNoContinuidad; }
    public void setIdMotivoNoContinuidad(Long idMotivoNoContinuidad) { this.idMotivoNoContinuidad = idMotivoNoContinuidad; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
    public String getRazonPrincipal() { return razonPrincipal; }
    public void setRazonPrincipal(String razonPrincipal) { this.razonPrincipal = razonPrincipal; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public AgenteInmobiliario getAgenteResponsable() { return agenteResponsable; }
    public void setAgenteResponsable(AgenteInmobiliario agenteResponsable) { this.agenteResponsable = agenteResponsable; }
    public InteraccionComercial getInteraccionComercial() { return interaccionComercial; }
    public void setInteraccionComercial(InteraccionComercial interaccionComercial) { this.interaccionComercial = interaccionComercial; validarReferenciaUnica(); }
    public Visita getVisita() { return visita; }
    public void setVisita(Visita visita) { this.visita = visita; validarReferenciaUnica(); }
    public SolicitudAlquiler getSolicitudAlquiler() { return solicitudAlquiler; }
    public void setSolicitudAlquiler(SolicitudAlquiler solicitudAlquiler) { this.solicitudAlquiler = solicitudAlquiler; validarReferenciaUnica(); }

    public void registrarMotivo() {
        validarReferenciaUnica();
        if (fechaHora == null) {
            fechaHora = LocalDateTime.now();
        }
    }

    public void actualizarObservacion(String observacion) {
        this.observaciones = observacion;
    }

    public void validarReferenciaUnica() {
        int referencias = 0;
        referencias += interaccionComercial != null ? 1 : 0;
        referencias += visita != null ? 1 : 0;
        referencias += solicitudAlquiler != null ? 1 : 0;
        if (referencias > 1) {
            throw new IllegalStateException("Solo una referencia principal puede estar presente.");
        }
    }
}
