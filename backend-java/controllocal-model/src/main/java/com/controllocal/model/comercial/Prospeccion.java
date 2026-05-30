package com.controllocal.model.comercial;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.controllocal.model.comercial.enums.EstadoProspeccion;
import com.controllocal.model.comercial.enums.ResultadoPropuesta;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.usuario.AgenteInmobiliario;

/**
 * Prospeccion (pre-captacion): el seguimiento del agente al propietario para
 * captar un local. Embudo: Prospecto -> Contactado -> Reunion -> Propuesta
 * entregada -> {Captado | Descartado | En seguimiento}. Las fechas de cada hito
 * sirven como historial de interacciones con el propietario.
 */
public class Prospeccion {

    public static final int DIAS_MAX_RECONTACTO = 15;

    private Long idProspeccion;
    private String codigoProspeccion;
    private LocalDateTime fechaRegistro;
    private EstadoProspeccion estado;
    private ResultadoPropuesta resultadoPropuesta;
    private LocalDate fechaContacto;
    private LocalDate fechaReunion;
    private LocalDate fechaPropuesta;
    private LocalDate fechaRecontacto;
    private String observaciones;
    private LocalComercial localComercial;
    private AgenteInmobiliario agenteResponsable;
    private Captacion captacion;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public Long getIdProspeccion() { return idProspeccion; }
    public void setIdProspeccion(Long idProspeccion) { this.idProspeccion = idProspeccion; }
    public String getCodigoProspeccion() { return codigoProspeccion; }
    public void setCodigoProspeccion(String codigoProspeccion) { this.codigoProspeccion = codigoProspeccion; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public EstadoProspeccion getEstado() { return estado; }
    public void setEstado(EstadoProspeccion estado) { this.estado = estado; }
    public ResultadoPropuesta getResultadoPropuesta() { return resultadoPropuesta; }
    public void setResultadoPropuesta(ResultadoPropuesta resultadoPropuesta) { this.resultadoPropuesta = resultadoPropuesta; }
    public LocalDate getFechaContacto() { return fechaContacto; }
    public void setFechaContacto(LocalDate fechaContacto) { this.fechaContacto = fechaContacto; }
    public LocalDate getFechaReunion() { return fechaReunion; }
    public void setFechaReunion(LocalDate fechaReunion) { this.fechaReunion = fechaReunion; }
    public LocalDate getFechaPropuesta() { return fechaPropuesta; }
    public void setFechaPropuesta(LocalDate fechaPropuesta) { this.fechaPropuesta = fechaPropuesta; }
    public LocalDate getFechaRecontacto() { return fechaRecontacto; }
    public void setFechaRecontacto(LocalDate fechaRecontacto) { this.fechaRecontacto = fechaRecontacto; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public LocalComercial getLocalComercial() { return localComercial; }
    public void setLocalComercial(LocalComercial localComercial) { this.localComercial = localComercial; }
    public AgenteInmobiliario getAgenteResponsable() { return agenteResponsable; }
    public void setAgenteResponsable(AgenteInmobiliario agenteResponsable) { this.agenteResponsable = agenteResponsable; }
    public Captacion getCaptacion() { return captacion; }
    public void setCaptacion(Captacion captacion) { this.captacion = captacion; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }

    public void registrar() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoProspeccion.PROSPECTO;
        }
    }

    public void contactar() {
        this.estado = EstadoProspeccion.CONTACTADO;
        this.fechaContacto = LocalDate.now();
        touch();
    }

    public void registrarReunion() {
        this.estado = EstadoProspeccion.REUNION;
        this.fechaReunion = LocalDate.now();
        touch();
    }

    public void entregarPropuesta() {
        this.estado = EstadoProspeccion.PROPUESTA_ENTREGADA;
        this.fechaPropuesta = LocalDate.now();
        this.resultadoPropuesta = ResultadoPropuesta.PENDIENTE;
        this.fechaRecontacto = null;
        touch();
    }

    /** El propietario acepta: la prospeccion queda CAPTADA (nace la captacion). */
    public void aceptarPropuesta() {
        this.resultadoPropuesta = ResultadoPropuesta.ACEPTADA;
        this.estado = EstadoProspeccion.CAPTADO;
        this.fechaRecontacto = null;
        touch();
    }

    /** El propietario rechaza: prospeccion descartada. */
    public void rechazarPropuesta(String motivo) {
        this.resultadoPropuesta = ResultadoPropuesta.RECHAZADA;
        this.estado = EstadoProspeccion.DESCARTADO;
        this.observaciones = motivo;
        this.fechaRecontacto = null;
        touch();
    }

    /** "Por ahora no": queda en seguimiento con fecha de recontacto. */
    public void posponer(LocalDate fechaRecontacto) {
        this.resultadoPropuesta = ResultadoPropuesta.POSPUESTA;
        this.estado = EstadoProspeccion.EN_SEGUIMIENTO;
        this.fechaRecontacto = fechaRecontacto;
        touch();
    }

    public void descartar(String motivo) {
        this.estado = EstadoProspeccion.DESCARTADO;
        this.observaciones = motivo;
        this.fechaRecontacto = null;
        touch();
    }

    /** En seguimiento cuyo recontacto ya vencio o vence dentro de {@code diasAviso}. */
    public boolean requiereRecontacto(LocalDate hoy, int diasAviso) {
        return estado == EstadoProspeccion.EN_SEGUIMIENTO
                && fechaRecontacto != null
                && !fechaRecontacto.isAfter(hoy.plusDays(diasAviso));
    }

    private void touch() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
