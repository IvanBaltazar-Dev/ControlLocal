package com.controllocal.model.usuario;

import java.time.LocalDateTime;

import com.controllocal.model.persona.EstadoActivoInactivo;
import com.controllocal.model.persona.Persona;
import com.controllocal.model.persona.TipoPersona;

public class UsuarioInterno {

    private Long idUsuarioInterno;
    private Persona persona;
    private String nombreUsuario;
    private String contrasenaHash;
    private EstadoActivoInactivo estadoAdministrativo;
    private RolUsuarioInterno rol;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public Long getIdUsuarioInterno() {
        return idUsuarioInterno;
    }

    public void setIdUsuarioInterno(Long idUsuarioInterno) {
        this.idUsuarioInterno = idUsuarioInterno;
    }

    public Persona getPersona() {
        return persona;
    }

    public void setPersona(Persona persona) {
        this.persona = persona;
    }

    public String getNombres() {
        String nombre = persona().getNombresORazonSocial();
        if (nombre == null) {
            return null;
        }
        int indice = nombre.lastIndexOf(' ');
        return indice > 0 ? nombre.substring(0, indice) : nombre;
    }

    public void setNombres(String nombres) {
        String apellidos = getApellidos();
        persona().setNombresORazonSocial(apellidos == null || apellidos.isBlank() ? nombres : nombres + " " + apellidos);
        if (persona().getTipoPersona() == null) {
            persona().setTipoPersona(TipoPersona.NATURAL);
        }
    }

    public String getApellidos() {
        String nombre = persona().getNombresORazonSocial();
        if (nombre == null) {
            return null;
        }
        int indice = nombre.lastIndexOf(' ');
        return indice > 0 ? nombre.substring(indice + 1) : "";
    }

    public void setApellidos(String apellidos) {
        String nombres = getNombres();
        persona().setNombresORazonSocial(nombres == null || nombres.isBlank() ? apellidos : nombres + " " + apellidos);
        if (persona().getTipoPersona() == null) {
            persona().setTipoPersona(TipoPersona.NATURAL);
        }
    }

    public String getCorreo() {
        return persona().getCorreo();
    }

    public void setCorreo(String correo) {
        persona().setCorreo(correo);
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getTelefono() {
        return persona().getTelefono();
    }

    public void setTelefono(String telefono) {
        persona().setTelefono(telefono);
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public void setContrasenaHash(String contrasenaHash) {
        this.contrasenaHash = contrasenaHash;
    }

    public EstadoActivoInactivo getEstado() {
        return estadoAdministrativo;
    }

    public void setEstado(EstadoActivoInactivo estado) {
        this.estadoAdministrativo = estado;
    }

    public EstadoActivoInactivo getEstadoAdministrativo() {
        return estadoAdministrativo;
    }

    public void setEstadoAdministrativo(EstadoActivoInactivo estadoAdministrativo) {
        this.estadoAdministrativo = estadoAdministrativo;
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
        return estadoAdministrativo == EstadoActivoInactivo.ACTIVO;
    }

    public void cerrarSesion() {
    }

    public void activar() {
        this.estadoAdministrativo = EstadoActivoInactivo.ACTIVO;
    }

    public void desactivar() {
        this.estadoAdministrativo = EstadoActivoInactivo.INACTIVO;
    }

    private Persona persona() {
        if (persona == null) {
            persona = new Persona();
        }
        return persona;
    }
}
