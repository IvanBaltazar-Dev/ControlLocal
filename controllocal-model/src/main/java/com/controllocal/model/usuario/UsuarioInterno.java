package com.controllocal.model.usuario;

import java.time.LocalDateTime;

import com.controllocal.model.persona.EstadoActivoInactivo;

public abstract class UsuarioInterno {

    private long idUsuarioInterno;
    private String nombres;
    private String apellidos;
    private String correo;
    private String telefono;
    private String nombreUsuario;
    private String contrasenaHash;
    private EstadoActivoInactivo estado;
    private RolUsuarioInterno rol;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public long getIdUsuarioInterno() {
        return idUsuarioInterno;
    }

    public void setIdUsuarioInterno(long idUsuarioInterno) {
        this.idUsuarioInterno = idUsuarioInterno;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public void setContrasenaHash(String contrasenaHash) {
        this.contrasenaHash = contrasenaHash;
    }

    public EstadoActivoInactivo getEstado() {
        return estado;
    }

    public void setEstado(EstadoActivoInactivo estado) {
        this.estado = estado;
    }

    public RolUsuarioInterno getRol() {
        return rol;
    }

    public void setRol(RolUsuarioInterno rol) {
        this.rol = rol;
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

    public boolean autenticar() {
        return false;
    }

    public void cerrarSesion() {
    }

    public void activar() {
    }

    public void desactivar() {
    }
}
