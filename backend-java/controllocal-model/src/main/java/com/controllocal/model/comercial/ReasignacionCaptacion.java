package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.usuario.AgenteInmobiliario;
import com.controllocal.model.usuario.Broker;

public class ReasignacionCaptacion {

    private Long idReasignacion;
    private LocalDateTime fechaCambio;
    private String motivo;
    private Captacion captacion;
    private AgenteInmobiliario agenteAnterior;
    private AgenteInmobiliario agenteNuevo;
    private Broker brokerResponsable;

    public Long getIdReasignacion() { return idReasignacion; }
    public void setIdReasignacion(Long idReasignacion) { this.idReasignacion = idReasignacion; }
    public LocalDateTime getFechaCambio() { return fechaCambio; }
    public void setFechaCambio(LocalDateTime fechaCambio) { this.fechaCambio = fechaCambio; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public Captacion getCaptacion() { return captacion; }
    public void setCaptacion(Captacion captacion) { this.captacion = captacion; }
    public AgenteInmobiliario getAgenteAnterior() { return agenteAnterior; }
    public void setAgenteAnterior(AgenteInmobiliario agenteAnterior) { this.agenteAnterior = agenteAnterior; }
    public AgenteInmobiliario getAgenteNuevo() { return agenteNuevo; }
    public void setAgenteNuevo(AgenteInmobiliario agenteNuevo) { this.agenteNuevo = agenteNuevo; }
    public Broker getBrokerResponsable() { return brokerResponsable; }
    public void setBrokerResponsable(Broker brokerResponsable) { this.brokerResponsable = brokerResponsable; }

    public void registrarCambio() {
        this.fechaCambio = LocalDateTime.now();
    }

    public String obtenerResumen() {
        return "Captacion " + id(captacion) + " reasignada de agente " + id(agenteAnterior)
                + " a agente " + id(agenteNuevo);
    }

    private Object id(Captacion captacion) {
        return captacion != null ? captacion.getIdCaptacion() : null;
    }

    private Object id(AgenteInmobiliario agente) {
        return agente != null ? agente.getIdAgente() : null;
    }
}
