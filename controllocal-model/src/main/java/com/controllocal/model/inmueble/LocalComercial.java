package com.controllocal.model.inmueble;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.persona.Propietario;

public class LocalComercial {

    private long idLocal;
    private String codigoLocal;
    private String direccion;
    private String distrito;
    private double metraje;
    private double precioReferencial;
    private String rubroPermitido;
    private String descripcion;
    private EstadoLocalComercial estado;
    private Propietario propietario;
    private LocalDateTime fechaRegistro;
    private LocalDateTime fechaActualizacion;
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

    public EstadoLocalComercial getEstado() {
        return estado;
    }

    public void setEstado(EstadoLocalComercial estado) {
        this.estado = estado;
    }

    public Propietario getPropietario() {
        return propietario;
    }

    public void setPropietario(Propietario propietario) {
        this.propietario = propietario;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public List<Captacion> getCaptaciones() {
        return captaciones;
    }

    public void setCaptaciones(List<Captacion> captaciones) {
        this.captaciones = captaciones;
    }

    public void actualizarDatos() {
    }

    public void cambiarEstado(EstadoLocalComercial estado) {
    }

    public String obtenerResumen() {
        return "";
    }
}
