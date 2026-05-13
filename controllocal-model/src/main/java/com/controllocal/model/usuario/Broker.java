package com.controllocal.model.usuario;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.comercial.EvaluacionSolicitud;
import com.controllocal.model.comercial.ReasignacionCaptacion;

public class Broker extends UsuarioInterno {

    private Long idBroker;
    private String codigoBroker;
    private LocalDate fechaDesignacion;
    private boolean esAdministrador;
    private List<Captacion> captacionesSupervisadas = new ArrayList<>();

    public Long getIdBroker() {
        return idBroker;
    }

    public void setIdBroker(Long idBroker) {
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

    public boolean isEsAdministrador() {
        return esAdministrador;
    }

    public boolean getEsAdministrador() {
        return esAdministrador;
    }

    public void setEsAdministrador(boolean esAdministrador) {
        this.esAdministrador = esAdministrador;
    }

    public List<Captacion> getCaptacionesSupervisadas() {
        return captacionesSupervisadas;
    }

    public void setCaptacionesSupervisadas(List<Captacion> captacionesSupervisadas) {
        this.captacionesSupervisadas = captacionesSupervisadas;
    }

    public void validarCaptacion(Captacion captacion) {
        if (captacion == null) {
            return;
        }
        captacion.setBrokerRevisor(this);
        captacion.setFechaRevision(java.time.LocalDateTime.now());
        captacion.actualizarEstado(com.controllocal.model.comercial.EstadoCaptacion.ACTIVA);
        if (!captacionesSupervisadas.contains(captacion)) {
            captacionesSupervisadas.add(captacion);
        }
    }

    public void aprobarCambioSensible(Captacion captacion) {
        validarCaptacion(captacion);
    }

    public ReasignacionCaptacion reasignarCaptacion(
            Captacion captacion,
            AgenteInmobiliario agenteNuevo,
            String motivo
    ) {
        AgenteInmobiliario anterior = captacion != null ? captacion.getAgenteResponsable() : null;
        if (captacion != null) {
            captacion.setAgenteResponsable(agenteNuevo);
        }
        ReasignacionCaptacion reasignacion = new ReasignacionCaptacion();
        reasignacion.setCaptacion(captacion);
        reasignacion.setAgenteAnterior(anterior);
        reasignacion.setAgenteNuevo(agenteNuevo);
        reasignacion.setBrokerResponsable(this);
        reasignacion.setMotivo(motivo);
        reasignacion.registrarCambio();
        return reasignacion;
    }

    public void registrarEvaluacion(EvaluacionSolicitud evaluacion) {
        if (evaluacion != null) {
            evaluacion.setResponsableEvaluacion(this);
            evaluacion.emitirResultado(evaluacion.getResultado(), evaluacion.getObservaciones());
        }
    }
    public Broker() {
    }

    public Broker(Long idBroker) {
        this.idBroker = idBroker;
    }
}
