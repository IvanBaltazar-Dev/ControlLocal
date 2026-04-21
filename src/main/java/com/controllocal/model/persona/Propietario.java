package com.controllocal.model.persona;

import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.inmueble.LocalComercial;

public class Propietario {

    private long idPropietario;
    private String tipoDocumento;
    private String numeroDocumento;
    private String nombresORazonSocial;
    private String telefono;
    private String correo;
    private String estado;
    private List<LocalComercial> localesComerciales = new ArrayList<>();

    public long getIdPropietario() {
        return idPropietario;
    }

    public void setIdPropietario(long idPropietario) {
        this.idPropietario = idPropietario;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
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
