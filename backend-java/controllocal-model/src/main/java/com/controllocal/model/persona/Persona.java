package com.controllocal.model.persona;

import java.time.LocalDateTime;

import com.controllocal.model.persona.enums.EstadoActivoInactivo;
import com.controllocal.model.persona.enums.TipoDocumentoIdentidad;
import com.controllocal.model.persona.enums.TipoPersona;

public class Persona {

    private Long idPersona;
    private TipoPersona tipoPersona;
    private TipoDocumentoIdentidad tipoDocumento;
    private String numeroDocumento;
    private String nombresORazonSocial;
    private String telefono;
    private String correo;
    private EstadoActivoInactivo estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    // Consentimiento de uso de datos personales (Diccionario v2 P1).
    private Boolean consentimientoUsoDato;

    public Boolean getConsentimientoUsoDato() {
        return consentimientoUsoDato;
    }

    public void setConsentimientoUsoDato(Boolean consentimientoUsoDato) {
        this.consentimientoUsoDato = consentimientoUsoDato;
    }

    public Long getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(Long idPersona) {
        this.idPersona = idPersona;
    }

    public TipoPersona getTipoPersona() {
        return tipoPersona;
    }

    public void setTipoPersona(TipoPersona tipoPersona) {
        this.tipoPersona = tipoPersona;
    }

    public TipoDocumentoIdentidad getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(TipoDocumentoIdentidad tipoDocumento) {
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

    public void activar() {
        this.estado = EstadoActivoInactivo.ACTIVO;
    }

    public void desactivar() {
        this.estado = EstadoActivoInactivo.INACTIVO;
    }

    public void actualizarDatos(String telefono, String correo, String nombresORazonSocial) {
        this.telefono = telefono;
        this.correo = correo;
        this.nombresORazonSocial = nombresORazonSocial;
        this.fechaActualizacion = LocalDateTime.now();
    }
}
