package com.controllocal.model.usuario;

import java.time.LocalDateTime;

/**
 * Historial de una reasignación de un agente entre brokers supervisores.
 * Es el evento (anterior -> nuevo, autorizado por el broker administrador),
 * análogo a {@link com.controllocal.model.comercial.ReasignacionCaptacion}.
 * La supervisión vigente sigue viviendo en {@link BrokerAgente}; esta clase
 * solo conserva la traza histórica del cambio.
 */
public class ReasignacionAgenteBroker {

    private Long idReasignacion;
    private LocalDateTime fechaCambio;
    private String motivo;
    private AgenteInmobiliario agente;
    private Broker brokerAnterior;       // null en la primera asignación del agente
    private Broker brokerNuevo;
    private Broker brokerAdministrador;  // broker administrador que autoriza el cambio

    public Long getIdReasignacion() { return idReasignacion; }
    public void setIdReasignacion(Long idReasignacion) { this.idReasignacion = idReasignacion; }
    public LocalDateTime getFechaCambio() { return fechaCambio; }
    public void setFechaCambio(LocalDateTime fechaCambio) { this.fechaCambio = fechaCambio; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public AgenteInmobiliario getAgente() { return agente; }
    public void setAgente(AgenteInmobiliario agente) { this.agente = agente; }
    public Broker getBrokerAnterior() { return brokerAnterior; }
    public void setBrokerAnterior(Broker brokerAnterior) { this.brokerAnterior = brokerAnterior; }
    public Broker getBrokerNuevo() { return brokerNuevo; }
    public void setBrokerNuevo(Broker brokerNuevo) { this.brokerNuevo = brokerNuevo; }
    public Broker getBrokerAdministrador() { return brokerAdministrador; }
    public void setBrokerAdministrador(Broker brokerAdministrador) { this.brokerAdministrador = brokerAdministrador; }

    public void registrarCambio() {
        this.fechaCambio = LocalDateTime.now();
    }

    public String obtenerResumen() {
        return "Agente " + (agente != null ? agente.getIdAgente() : "—")
                + " reasignado de broker " + (brokerAnterior != null ? brokerAnterior.getIdBroker() : "—")
                + " a broker " + (brokerNuevo != null ? brokerNuevo.getIdBroker() : "—");
    }
}
