package com.controllocal.model.comercial;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;

public class Captacion {

    private long idCaptacion;
    private String codigoCaptacion;
    private LocalDate fechaCaptacion;
    private LocalDate fechaInicioVigencia;
    private LocalDate fechaFinVigencia;
    private double comisionPactada;
    private String observaciones;
    private EstadoCaptacion estado;
    private LocalDateTime fechaRevision;
    private String observacionRevision;
    private LocalComercial localComercial;
    private AgenteInmobiliario agenteResponsable;
    private Broker brokerRevisor;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
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

    public String getCodigoCaptacion() {
        return codigoCaptacion;
    }

    public void setCodigoCaptacion(String codigoCaptacion) {
        this.codigoCaptacion = codigoCaptacion;
    }

    public LocalDate getFechaCaptacion() {
        return fechaCaptacion;
    }

    public void setFechaCaptacion(LocalDate fechaCaptacion) {
        this.fechaCaptacion = fechaCaptacion;
    }

    public LocalDate getFechaInicioVigencia() {
        return fechaInicioVigencia;
    }

    public void setFechaInicioVigencia(LocalDate fechaInicioVigencia) {
        this.fechaInicioVigencia = fechaInicioVigencia;
    }

    public LocalDate getFechaFinVigencia() {
        return fechaFinVigencia;
    }

    public void setFechaFinVigencia(LocalDate fechaFinVigencia) {
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

    public EstadoCaptacion getEstado() {
        return estado;
    }

    public void setEstado(EstadoCaptacion estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaRevision() {
        return fechaRevision;
    }

    public void setFechaRevision(LocalDateTime fechaRevision) {
        this.fechaRevision = fechaRevision;
    }

    public String getObservacionRevision() {
        return observacionRevision;
    }

    public void setObservacionRevision(String observacionRevision) {
        this.observacionRevision = observacionRevision;
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

    public Broker getBrokerRevisor() {
        return brokerRevisor;
    }

    public void setBrokerRevisor(Broker brokerRevisor) {
        this.brokerRevisor = brokerRevisor;
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

    public void registrar() {
    }

    public void aprobarIncorporacion() {
    }

    public void solicitarAjustes() {
    }

    public void rechazarIncorporacion() {
    }

    public void activar() {
    }

    public void cerrar() {
    }

    public void reasignarAgente(AgenteInmobiliario agenteInmobiliario) {
    }

    public void actualizarEstado(EstadoCaptacion estado) {
    }
}
