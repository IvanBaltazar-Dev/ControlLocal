package com.controllocal.model.persona;

import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.inmueble.LocalComercial;

public class Propietario {

    private Long idPropietario;
    private Persona persona;
    private List<LocalComercial> localesComerciales = new ArrayList<>();

    public Long getIdPropietario() {
        return idPropietario;
    }

    public void setIdPropietario(Long idPropietario) {
        this.idPropietario = idPropietario;
    }

    public Persona getPersona() {
        return persona;
    }

    public void setPersona(Persona persona) {
        this.persona = persona;
    }

    public TipoPersona getTipoPersona() {
        return persona().getTipoPersona();
    }

    public void setTipoPersona(TipoPersona tipoPersona) {
        persona().setTipoPersona(tipoPersona);
    }

    public String getTipoDocumento() {
        return persona().getTipoDocumento();
    }

    public void setTipoDocumento(String tipoDocumento) {
        persona().setTipoDocumento(tipoDocumento);
    }

    public String getNumeroDocumento() {
        return persona().getNumeroDocumento();
    }

    public void setNumeroDocumento(String numeroDocumento) {
        persona().setNumeroDocumento(numeroDocumento);
    }

    public String getNombresORazonSocial() {
        return persona().getNombresORazonSocial();
    }

    public void setNombresORazonSocial(String nombresORazonSocial) {
        persona().setNombresORazonSocial(nombresORazonSocial);
    }

    public String getTelefono() {
        return persona().getTelefono();
    }

    public void setTelefono(String telefono) {
        persona().setTelefono(telefono);
    }

    public String getCorreo() {
        return persona().getCorreo();
    }

    public void setCorreo(String correo) {
        persona().setCorreo(correo);
    }

    public EstadoActivoInactivo getEstado() {
        return persona().getEstado();
    }

    public void setEstado(EstadoActivoInactivo estado) {
        persona().setEstado(estado);
    }

    public java.time.LocalDateTime getFechaCreacion() {
        return persona().getFechaCreacion();
    }

    public void setFechaCreacion(java.time.LocalDateTime fechaCreacion) {
        persona().setFechaCreacion(fechaCreacion);
    }

    public java.time.LocalDateTime getFechaActualizacion() {
        return persona().getFechaActualizacion();
    }

    public void setFechaActualizacion(java.time.LocalDateTime fechaActualizacion) {
        persona().setFechaActualizacion(fechaActualizacion);
    }

    public List<LocalComercial> getLocalesComerciales() {
        return localesComerciales;
    }

    public void setLocalesComerciales(List<LocalComercial> localesComerciales) {
        this.localesComerciales = localesComerciales;
    }

    public void actualizarDatos() {
        persona().setFechaActualizacion(java.time.LocalDateTime.now());
    }

    public void actualizarDatos(String telefono, String correo, String nombresORazonSocial) {
        persona().actualizarDatos(telefono, correo, nombresORazonSocial);
    }

    public void activar() {
        persona().activar();
    }

    public void desactivar() {
        persona().desactivar();
    }

    private Persona persona() {
        if (persona == null) {
            persona = new Persona();
        }
        return persona;
    }
}
