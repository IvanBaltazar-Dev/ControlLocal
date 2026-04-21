package com.controllocal.model.comercial;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;

public class Captacion {

    private long idCaptacion;
    private LocalDateTime fechaCaptacion;
    private LocalDateTime fechaInicioVigencia;
    private LocalDateTime fechaFinVigencia;
    private double comisionPactada;
    private String observaciones;
    private String estado;
    private LocalComercial localComercial;
    private AgenteInmobiliario agenteResponsable;
    private Broker brokerSupervisor;
    private List<InteraccionComercial> interaccionesComerciales = new ArrayList<>();
    private List<Visita> visitas = new ArrayList<>();
    private List<SolicitudAlquiler> solicitudesAlquiler = new ArrayList<>();
    private List<ReasignacionCaptacion> reasignaciones = new ArrayList<>();

    public long getIdCaptacion() {
        return idCaptacion;
    }

    public void setIdCaptacion(long idCaptacion) {
        this.idCaptacion = idCaptacion;
    }

    public LocalDateTime getFechaCaptacion() {
        return fechaCaptacion;
    }

    public void setFechaCaptacion(LocalDateTime fechaCaptacion) {
        this.fechaCaptacion = fechaCaptacion;
    }

    public LocalDateTime getFechaInicioVigencia() {
        return fechaInicioVigencia;
    }

    public void setFechaInicioVigencia(LocalDateTime fechaInicioVigencia) {
        this.fechaInicioVigencia = fechaInicioVigencia;
    }

    public LocalDateTime getFechaFinVigencia() {
        return fechaFinVigencia;
    }

    public void setFechaFinVigencia(LocalDateTime fechaFinVigencia) {
        this.fechaFinVigencia = fechaFinVigencia;
    }

    public double getComisionPactada() {
        return comisionPactada;
    }

    public void setComisionPactada(double comisionPactada) {
        this.comisionPactada = comisionPactada;
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

    public LocalComercial getLocalComercial() {
        return localComercial;
    }

    public void setLocalComercial(LocalComercial localComercial) {
        this.localComercial = localComercial;
    }

    public AgenteInmobiliario getAgenteResponsable() {
        return agenteResponsable;
    }

    public void setAgenteResponsable(AgenteInmobiliario agenteResponsable) {
        this.agenteResponsable = agenteResponsable;
    }

    public Broker getBrokerSupervisor() {
        return brokerSupervisor;
    }

    public void setBrokerSupervisor(Broker brokerSupervisor) {
        this.brokerSupervisor = brokerSupervisor;
    }

    public List<InteraccionComercial> getInteraccionesComerciales() {
        return interaccionesComerciales;
    }

    public void setInteraccionesComerciales(List<InteraccionComercial> interaccionesComerciales) {
        this.interaccionesComerciales = interaccionesComerciales;
    }

    public List<Visita> getVisitas() {
        return visitas;
    }

    public void setVisitas(List<Visita> visitas) {
        this.visitas = visitas;
    }

    public List<SolicitudAlquiler> getSolicitudesAlquiler() {
        return solicitudesAlquiler;
    }

    public void setSolicitudesAlquiler(List<SolicitudAlquiler> solicitudesAlquiler) {
        this.solicitudesAlquiler = solicitudesAlquiler;
    }

    public List<ReasignacionCaptacion> getReasignaciones() {
        return reasignaciones;
    }

    public void setReasignaciones(List<ReasignacionCaptacion> reasignaciones) {
        this.reasignaciones = reasignaciones;
    }

    public void activar() {
    }

    public void cerrar() {
    }

    public void reasignarAgente(AgenteInmobiliario agenteInmobiliario) {
    }

    public void actualizarEstado(String estado) {
    }
}
