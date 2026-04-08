package com.commercialbrokerage.model;

import java.util.ArrayList;
import java.util.List;

public class LocalComercial {

    private long idLocal;
    private String codigoLocal;
    private String direccion;
    private String distrito;
    private double metraje;
    private double precioReferencial;
    private String rubroPermitido;
    private String descripcion;
    private String estado;
    private Propietario propietario;
    private List<Captacion> captaciones = new ArrayList<>();

    public long getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(long idLocal) {
        this.idLocal = idLocal;
    }

    public String getCodigoLocal() {
        return codigoLocal;
    }

    public void setCodigoLocal(String codigoLocal) {
        this.codigoLocal = codigoLocal;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getDistrito() {
        return distrito;
    }

    public void setDistrito(String distrito) {
        this.distrito = distrito;
    }

    public double getMetraje() {
        return metraje;
    }

    public void setMetraje(double metraje) {
        this.metraje = metraje;
    }

    public double getPrecioReferencial() {
        return precioReferencial;
    }

    public void setPrecioReferencial(double precioReferencial) {
        this.precioReferencial = precioReferencial;
    }

    public String getRubroPermitido() {
        return rubroPermitido;
    }

    public void setRubroPermitido(String rubroPermitido) {
        this.rubroPermitido = rubroPermitido;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Propietario getPropietario() {
        return propietario;
    }

    public void setPropietario(Propietario propietario) {
        this.propietario = propietario;
    }

    public List<Captacion> getCaptaciones() {
        return captaciones;
    }

    public void setCaptaciones(List<Captacion> captaciones) {
        this.captaciones = captaciones;
    }

    public void actualizarDatos() {
    }

    public void cambiarEstado(String estado) {
    }

    public String obtenerResumen() {
        return "";
    }
}
