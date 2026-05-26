package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.comercial.enums.EstadoOportunidadComercial;
import com.controllocal.model.persona.ClienteInteresado;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class OportunidadComercial {

    private Long idOportunidad;
    private String codigoOportunidad;
    private LocalDateTime fechaRegistro;
    private EstadoOportunidadComercial estado;
    private LocalDateTime fechaActualizacionEstado;
    private String motivoCierre;
    private String observaciones;
    private ClienteInteresado clienteInteresado;
    private Captacion captacion;
    private AgenteInmobiliario agenteResponsable;
    private LocalDateTime fechaCierre;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public Long getIdOportunidad() { return idOportunidad; }
    public void setIdOportunidad(Long idOportunidad) { this.idOportunidad = idOportunidad; }
    public String getCodigoOportunidad() { return codigoOportunidad; }
    public void setCodigoOportunidad(String codigoOportunidad) { this.codigoOportunidad = codigoOportunidad; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public EstadoOportunidadComercial getEstado() { return estado; }
    public void setEstado(EstadoOportunidadComercial estado) { this.estado = estado; }
    public LocalDateTime getFechaActualizacionEstado() { return fechaActualizacionEstado; }
    public void setFechaActualizacionEstado(LocalDateTime fechaActualizacionEstado) { this.fechaActualizacionEstado = fechaActualizacionEstado; }
    public String getMotivoCierre() { return motivoCierre; }
    public void setMotivoCierre(String motivoCierre) { this.motivoCierre = motivoCierre; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public ClienteInteresado getClienteInteresado() { return clienteInteresado; }
    public void setClienteInteresado(ClienteInteresado clienteInteresado) { this.clienteInteresado = clienteInteresado; }
    public Captacion getCaptacion() { return captacion; }
    public void setCaptacion(Captacion captacion) { this.captacion = captacion; }
    public AgenteInmobiliario getAgenteResponsable() { return agenteResponsable; }
    public void setAgenteResponsable(AgenteInmobiliario agenteResponsable) { this.agenteResponsable = agenteResponsable; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }

    public void abrir() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
        actualizarEstado(EstadoOportunidadComercial.ABIERTA);
    }

    public void marcarSolicitudCreada() {
        actualizarEstado(EstadoOportunidadComercial.SOLICITUD_CREADA);
    }

    public void cerrarNoContinua(String motivo) {
        this.motivoCierre = motivo;
        this.fechaCierre = LocalDateTime.now();
        actualizarEstado(EstadoOportunidadComercial.NO_CONTINUA);
    }

    public void cerrarExitosa() {
        this.fechaCierre = LocalDateTime.now();
        actualizarEstado(EstadoOportunidadComercial.FINALIZADA_EXITOSA);
    }

    public void cerrarNoFavorable(String motivo) {
        this.motivoCierre = motivo;
        this.fechaCierre = LocalDateTime.now();
        actualizarEstado(EstadoOportunidadComercial.FINALIZADA_NO_FAVORABLE);
    }

    public void actualizarEstado(EstadoOportunidadComercial estado) {
        this.estado = estado;
        this.fechaActualizacionEstado = LocalDateTime.now();
        this.fechaActualizacion = this.fechaActualizacionEstado;
    }
}
