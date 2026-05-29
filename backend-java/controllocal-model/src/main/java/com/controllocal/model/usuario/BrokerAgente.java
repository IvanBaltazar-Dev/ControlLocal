package com.controllocal.model.usuario;

import java.time.LocalDate;

import com.controllocal.model.persona.enums.EstadoActivoInactivo;

public class BrokerAgente {

    private Long idBrokerAgente;
    private Broker broker;
    private AgenteInmobiliario agente;
    private LocalDate fechaAsignacion;
    private LocalDate fechaFin;
    private String motivo;
    private EstadoActivoInactivo estado;

    public Long getIdBrokerAgente() {
        return idBrokerAgente;
    }

    public void setIdBrokerAgente(Long idBrokerAgente) {
        this.idBrokerAgente = idBrokerAgente;
    }

    public Broker getBroker() {
        return broker;
    }

    public void setBroker(Broker broker) {
        this.broker = broker;
    }

    public AgenteInmobiliario getAgente() {
        return agente;
    }

    public void setAgente(AgenteInmobiliario agente) {
        this.agente = agente;
    }

    public LocalDate getFechaAsignacion() {
        return fechaAsignacion;
    }

    public void setFechaAsignacion(LocalDate fechaAsignacion) {
        this.fechaAsignacion = fechaAsignacion;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public EstadoActivoInactivo getEstado() {
        return estado;
    }

    public void setEstado(EstadoActivoInactivo estado) {
        this.estado = estado;
    }

    public Long getIdBroker() {
        return broker != null ? broker.getIdBroker() : null;
    }

    public Long getIdAgente() {
        return agente != null ? agente.getIdAgente() : null;
    }
}
