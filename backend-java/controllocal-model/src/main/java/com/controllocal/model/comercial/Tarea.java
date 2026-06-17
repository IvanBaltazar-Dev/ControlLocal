package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.comercial.enums.EstadoTarea;
import com.controllocal.model.comercial.enums.Prioridad;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.comercial.enums.TipoTarea;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class Tarea {
    private Long idTarea;
    private TipoTarea tipo;
    private TipoEntidad entidadTipo;
    private Long entidadId;
    private AgenteInmobiliario agente;
    private String descripcion;
    private LocalDateTime fechaProgramada;
    private LocalDateTime fechaRecordatorio;
    private LocalDateTime fechaCompletada;
    private EstadoTarea estado;
    private Prioridad prioridad;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public Long getIdTarea() { return idTarea; }
    public void setIdTarea(Long idTarea) { this.idTarea = idTarea; }
    public TipoTarea getTipo() { return tipo; }
    public void setTipo(TipoTarea tipo) { this.tipo = tipo; }
    public TipoEntidad getEntidadTipo() { return entidadTipo; }
    public void setEntidadTipo(TipoEntidad entidadTipo) { this.entidadTipo = entidadTipo; }
    public Long getEntidadId() { return entidadId; }
    public void setEntidadId(Long entidadId) { this.entidadId = entidadId; }
    public AgenteInmobiliario getAgente() { return agente; }
    public void setAgente(AgenteInmobiliario agente) { this.agente = agente; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getFechaProgramada() { return fechaProgramada; }
    public void setFechaProgramada(LocalDateTime fechaProgramada) { this.fechaProgramada = fechaProgramada; }
    public LocalDateTime getFechaRecordatorio() { return fechaRecordatorio; }
    public void setFechaRecordatorio(LocalDateTime fechaRecordatorio) { this.fechaRecordatorio = fechaRecordatorio; }
    public LocalDateTime getFechaCompletada() { return fechaCompletada; }
    public void setFechaCompletada(LocalDateTime fechaCompletada) { this.fechaCompletada = fechaCompletada; }
    public EstadoTarea getEstado() { return estado; }
    public void setEstado(EstadoTarea estado) { this.estado = estado; }
    public Prioridad getPrioridad() { return prioridad; }
    public void setPrioridad(Prioridad prioridad) { this.prioridad = prioridad; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
