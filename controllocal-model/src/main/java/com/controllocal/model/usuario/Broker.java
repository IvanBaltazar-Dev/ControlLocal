package com.controllocal.model.usuario;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.comercial.Captacion;

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

    public Broker() {
    }

    public Broker(Long idBroker) {
        this.idBroker = idBroker;
    }
}
