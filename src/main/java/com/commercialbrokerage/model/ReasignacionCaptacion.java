package com.commercialbrokerage.model;

import java.time.LocalDateTime;

public class ReasignacionCaptacion {

    private long idReasignacion;
    private LocalDateTime fechaCambio;
    private String motivo;
    private Captacion captacion;
    private AgenteInmobiliario agenteAnterior;
    private AgenteInmobiliario agenteNuevo;
    private Broker brokerAutorizador;

    public long getIdReasignacion() {
        return idReasignacion;
    }

    public void setIdReasignacion(long idReasignacion) {
        this.idReasignacion = idReasignacion;
    }

    public LocalDateTime getFechaCambio() {
        return fechaCambio;
    }

    public void setFechaCambio(LocalDateTime fechaCambio) {
        this.fechaCambio = fechaCambio;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Captacion getCaptacion() {
        return captacion;
    }

    public void setCaptacion(Captacion captacion) {
        this.captacion = captacion;
    }

    public AgenteInmobiliario getAgenteAnterior() {
        return agenteAnterior;
    }

    public void setAgenteAnterior(AgenteInmobiliario agenteAnterior) {
        this.agenteAnterior = agenteAnterior;
    }

    public AgenteInmobiliario getAgenteNuevo() {
        return agenteNuevo;
    }

    public void setAgenteNuevo(AgenteInmobiliario agenteNuevo) {
        this.agenteNuevo = agenteNuevo;
    }

    public Broker getBrokerAutorizador() {
        return brokerAutorizador;
    }

    public void setBrokerAutorizador(Broker brokerAutorizador) {
        this.brokerAutorizador = brokerAutorizador;
    }

    public void registrarCambio() {
    }

    public String obtenerResumen() {
        return "";
    }
}
