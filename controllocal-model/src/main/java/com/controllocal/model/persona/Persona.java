package com.controllocal.model.persona;

import java.time.LocalDateTime;

public class Persona {

    private Long idPersona;
    private TipoPersona tipoPersona;
    private String tipoDocumento;
    private String numeroDocumento;
    private String nombresORazonSocial;
    private String telefono;
    private String correo;
    private EstadoActivoInactivo estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

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
