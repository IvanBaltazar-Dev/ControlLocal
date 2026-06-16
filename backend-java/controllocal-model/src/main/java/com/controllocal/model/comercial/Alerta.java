package com.controllocal.model.comercial;

import java.time.LocalDateTime;

import com.controllocal.model.comercial.enums.EstadoAlerta;
import com.controllocal.model.comercial.enums.Severidad;
import com.controllocal.model.comercial.enums.TipoAlerta;
import com.controllocal.model.comercial.enums.TipoEntidad;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class Alerta {
    private Long idAlerta;
    private TipoAlerta tipo;
    private Severidad severidad;
    private TipoEntidad entidadTipo;
    private Long entidadId;
    private AgenteInmobiliario agente;
    private String mensaje;
    private EstadoAlerta estado;
    private LocalDateTime fechaGeneracion;
    private LocalDateTime fechaResolucion;

    public Long getIdAlerta() { return idAlerta; }
    public void setIdAlerta(Long idAlerta) { this.idAlerta = idAlerta; }
    public TipoAlerta getTipo() { return tipo; }
    public void setTipo(TipoAlerta tipo) { this.tipo = tipo; }
    public Severidad getSeveridad() { return severidad; }
    public void setSeveridad(Severidad severidad) { this.severidad = severidad; }
    public TipoEntidad getEntidadTipo() { return entidadTipo; }
    public void setEntidadTipo(TipoEntidad entidadTipo) { this.entidadTipo = entidadTipo; }
    public Long getEntidadId() { return entidadId; }
    public void setEntidadId(Long entidadId) { this.entidadId = entidadId; }
    public AgenteInmobiliario getAgente() { return agente; }
    public void setAgente(AgenteInmobiliario agente) { this.agente = agente; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public EstadoAlerta getEstado() { return estado; }
    public void setEstado(EstadoAlerta estado) { this.estado = estado; }
    public LocalDateTime getFechaGeneracion() { return fechaGeneracion; }
    public void setFechaGeneracion(LocalDateTime fechaGeneracion) { this.fechaGeneracion = fechaGeneracion; }
    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(LocalDateTime fechaResolucion) { this.fechaResolucion = fechaResolucion; }
}
