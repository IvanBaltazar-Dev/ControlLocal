package com.controllocal.model.usuario;

public abstract class UsuarioInterno {

    private long idUsuarioInterno;
    private String nombres;
    private String apellidos;
    private String correo;
    private String nombreUsuario;
    private String contrasena;
    private String estado;
    private String rol;

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

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
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
