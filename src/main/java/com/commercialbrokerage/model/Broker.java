package com.commercialbrokerage.model;

import java.util.ArrayList;
import java.util.List;

public class Broker extends UsuarioInterno {

    private long idBroker;
    private String codigoBroker;
    private List<Captacion> captacionesSupervisadas = new ArrayList<>();
    private List<ReasignacionCaptacion> reasignacionesAutorizadas = new ArrayList<>();

    public long getIdBroker() {
        return idBroker;
    }

    public void setIdBroker(long idBroker) {
        this.idBroker = idBroker;
    }

    public String getCodigoBroker() {
        return codigoBroker;
    }

    public void setCodigoBroker(String codigoBroker) {
        this.codigoBroker = codigoBroker;
    }

    public List<Captacion> getCaptacionesSupervisadas() {
        return captacionesSupervisadas;
    }

    public void setCaptacionesSupervisadas(List<Captacion> captacionesSupervisadas) {
        this.captacionesSupervisadas = captacionesSupervisadas;
    }

    public List<ReasignacionCaptacion> getReasignacionesAutorizadas() {
        return reasignacionesAutorizadas;
    }

    public void setReasignacionesAutorizadas(List<ReasignacionCaptacion> reasignacionesAutorizadas) {
        this.reasignacionesAutorizadas = reasignacionesAutorizadas;
    }

    public void asignarCaptacion(Captacion captacion, AgenteInmobiliario agenteInmobiliario) {
    }

    public void reasignarCaptacion(Captacion captacion, AgenteInmobiliario agenteAnterior,
                                   AgenteInmobiliario agenteNuevo) {
    }

    public void autorizarCambio(ReasignacionCaptacion reasignacionCaptacion) {
    }

    public String generarReporte() {
        return "";
    }
}
