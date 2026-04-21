package com.controllocal.model.persona;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.inmueble.LocalComercial;

public class Propietario {

    private long idPropietario;
    private TipoPersona tipoPersona;
    private String tipoDocumento;
    private String numeroDocumento;
    private String nombresORazonSocial;
    private String telefono;
    private String correo;
    private EstadoActivoInactivo estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private List<LocalComercial> localesComerciales = new ArrayList<>();

    public long getIdPropietario() {
        return idPropietario;
    }

    public void setIdPropietario(long idPropietario) {
        this.idPropietario = idPropietario;
    }

    public TipoPersona getTipoPersona() {
        return tipoPersona;
    }

    public void setTipoPersona(TipoPersona tipoPersona) {
        this.tipoPersona = tipoPersona;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public String getNombresORazonSocial() {
        return nombresORazonSocial;
    }

    public void setNombresORazonSocial(String nombresORazonSocial) {
        this.nombresORazonSocial = nombresORazonSocial;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public EstadoActivoInactivo getEstado() {
        return estado;
    }

    public void setEstado(EstadoActivoInactivo estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public List<LocalComercial> getLocalesComerciales() {
        return localesComerciales;
    }

    public void setLocalesComerciales(List<LocalComercial> localesComerciales) {
        this.localesComerciales = localesComerciales;
    }

    public void actualizarDatos() {
    }

    public void activar() {
    }

    public void desactivar() {
    }
}
