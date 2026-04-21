package com.controllocal.model.persona;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.comercial.InteraccionComercial;
import com.controllocal.model.comercial.SolicitudAlquiler;
import com.controllocal.model.comercial.Visita;
import com.controllocal.model.enums.EstadoActivoInactivo;
import com.controllocal.model.enums.TipoPersona;

public class ClienteInteresado {

    private long idCliente;
    private TipoPersona tipoPersona;
    private String tipoDocumento;
    private String numeroDocumento;
    private String nombresORazonSocial;
    private String telefono;
    private String correo;
    private String rubroComercial;
    private EstadoActivoInactivo estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private List<InteraccionComercial> interaccionesComerciales = new ArrayList<>();
    private List<Visita> visitas = new ArrayList<>();
    private List<SolicitudAlquiler> solicitudesAlquiler = new ArrayList<>();

    public long getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(long idCliente) {
        this.idCliente = idCliente;
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

    public String getRubroComercial() {
        return rubroComercial;
    }

    public void setRubroComercial(String rubroComercial) {
        this.rubroComercial = rubroComercial;
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

    public List<InteraccionComercial> getInteraccionesComerciales() {
        return interaccionesComerciales;
    }

    public void setInteraccionesComerciales(List<InteraccionComercial> interaccionesComerciales) {
        this.interaccionesComerciales = interaccionesComerciales;
    }

    public List<Visita> getVisitas() {
        return visitas;
    }

    public void setVisitas(List<Visita> visitas) {
        this.visitas = visitas;
    }

    public List<SolicitudAlquiler> getSolicitudesAlquiler() {
        return solicitudesAlquiler;
    }

    public void setSolicitudesAlquiler(List<SolicitudAlquiler> solicitudesAlquiler) {
        this.solicitudesAlquiler = solicitudesAlquiler;
    }

    public void actualizarDatos() {
    }

    public void activar() {
    }

    public void desactivar() {
    }
}
