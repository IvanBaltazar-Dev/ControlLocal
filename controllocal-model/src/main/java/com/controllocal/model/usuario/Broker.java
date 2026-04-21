package com.controllocal.model.usuario;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EvaluacionSolicitud;
import com.controllocal.model.comercial.ReasignacionCaptacion;

public class Broker extends UsuarioInterno {

    private long idBroker;
    private String codigoBroker;
    private LocalDate fechaDesignacion;
    private List<Captacion> captacionesSupervisadas = new ArrayList<>();
    private List<ReasignacionCaptacion> reasignacionesAutorizadas = new ArrayList<>();
    private List<EvaluacionSolicitud> evaluacionesRegistradas = new ArrayList<>();

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

    public LocalDate getFechaDesignacion() {
        return fechaDesignacion;
    }

    public void setFechaDesignacion(LocalDate fechaDesignacion) {
        this.fechaDesignacion = fechaDesignacion;
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

    public List<EvaluacionSolicitud> getEvaluacionesRegistradas() {
        return evaluacionesRegistradas;
    }

    public void setEvaluacionesRegistradas(List<EvaluacionSolicitud> evaluacionesRegistradas) {
        this.evaluacionesRegistradas = evaluacionesRegistradas;
    }

    public void validarCaptacion(Captacion captacion) {
    }

    public void aprobarCambioSensible(Captacion captacion) {
    }

    public void reasignarCaptacion(Captacion captacion, AgenteInmobiliario agenteAnterior,
                                   AgenteInmobiliario agenteNuevo) {
    }

    public void registrarEvaluacion(EvaluacionSolicitud evaluacionSolicitud) {
    }
}
