package com.controllocal.model.comercial;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    public Captacion() {
    }

    public Captacion(
            long idCaptacion,
            String codigoCaptacion,
            LocalDate fechaCaptacion,
            LocalDate fechaInicioVigencia,
            LocalDate fechaFinVigencia,
            double comisionPactada,
            String observaciones,
            EstadoCaptacion estado,
            LocalDateTime fechaRevision,
            String observacionRevision,
            LocalComercial localComercial,
            AgenteInmobiliario agenteResponsable,
            Broker brokerRevisor,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaActualizacion
    ) {
        this.idCaptacion = idCaptacion;
        this.codigoCaptacion = codigoCaptacion;
        this.fechaCaptacion = fechaCaptacion;
        this.fechaInicioVigencia = fechaInicioVigencia;
        this.fechaFinVigencia = fechaFinVigencia;
        this.comisionPactada = comisionPactada;
        this.observaciones = observaciones;
        this.estado = estado;
        this.fechaRevision = fechaRevision;
        this.observacionRevision = observacionRevision;
        this.localComercial = localComercial;
        this.agenteResponsable = agenteResponsable;
        this.brokerRevisor = brokerRevisor;
        this.fechaCreacion = fechaCreacion;
        this.fechaActualizacion = fechaActualizacion;
    }

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

    public void registrar() {
        if (estado == null) {
            this.estado = EstadoCaptacion.PENDIENTE_REVISION;
        }
    }

    public void aprobarIncorporacion() {
        this.estado = EstadoCaptacion.ACTIVA;
        this.fechaRevision = LocalDateTime.now();
    }

    public void solicitarAjustes() {
        this.estado = EstadoCaptacion.OBSERVADA;
        this.fechaRevision = LocalDateTime.now();
    }

    public void rechazarIncorporacion() {
        this.estado = EstadoCaptacion.RECHAZADA;
        this.fechaRevision = LocalDateTime.now();
    }

    public void activar() {
        this.estado = EstadoCaptacion.ACTIVA;
        if (this.fechaInicioVigencia == null) {
            this.fechaInicioVigencia = LocalDate.now();
        }
    }

    public void cerrar() {
        this.estado = EstadoCaptacion.CERRADA;
        if (this.fechaFinVigencia == null) {
            this.fechaFinVigencia = LocalDate.now();
        }
    }

    public void reasignarAgente(AgenteInmobiliario agenteInmobiliario) {
        this.agenteResponsable = agenteInmobiliario;
    }

    public void actualizarEstado(EstadoCaptacion estado) {
        this.estado = estado;
    }
}
