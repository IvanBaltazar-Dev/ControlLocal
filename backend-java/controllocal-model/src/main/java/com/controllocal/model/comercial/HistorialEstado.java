package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.usuario.UsuarioInterno;

public class HistorialEstado {
    private Long idHistorialEstado;
    private TipoEntidad entidadTipo;
    private Long entidadId;
    private String estadoAnterior;
    private String estadoNuevo;
    private UsuarioInterno usuario;
    private LocalDateTime fechaEvento;
    private String observacion;

    public Long getIdHistorialEstado() { return idHistorialEstado; }
    public void setIdHistorialEstado(Long idHistorialEstado) { this.idHistorialEstado = idHistorialEstado; }
    public TipoEntidad getEntidadTipo() { return entidadTipo; }
    public void setEntidadTipo(TipoEntidad entidadTipo) { this.entidadTipo = entidadTipo; }
    public Long getEntidadId() { return entidadId; }
    public void setEntidadId(Long entidadId) { this.entidadId = entidadId; }
    public String getEstadoAnterior() { return estadoAnterior; }
    public void setEstadoAnterior(String estadoAnterior) { this.estadoAnterior = estadoAnterior; }
    public String getEstadoNuevo() { return estadoNuevo; }
    public void setEstadoNuevo(String estadoNuevo) { this.estadoNuevo = estadoNuevo; }
    public UsuarioInterno getUsuario() { return usuario; }
    public void setUsuario(UsuarioInterno usuario) { this.usuario = usuario; }
    public LocalDateTime getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(LocalDateTime fechaEvento) { this.fechaEvento = fechaEvento; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
