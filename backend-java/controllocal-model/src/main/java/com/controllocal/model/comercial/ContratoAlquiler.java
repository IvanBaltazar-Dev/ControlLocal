package com.controllocal.model.comercial;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.controllocal.model.comercial.enums.EstadoContrato;

/**
 * Contrato minimo: formaliza el cierre del alquiler. Solo guarda el vinculo
 * (oportunidad/solicitud) y datos de formalizacion. Las condiciones del trato
 * (renta, plazo, fecha de inicio, forma de pago, garantia, adelanto) viven en
 * la solicitud; la comision vive en comision_liquidacion. No se duplican aqui.
 */
public class ContratoAlquiler {
    private Long idContratoAlquiler;
    private OportunidadComercial oportunidad;
    private SolicitudAlquiler solicitudAlquiler;
    private LocalDate fechaCierre;
    private EstadoContrato estadoContrato;
    private String incidencias;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public Long getIdContratoAlquiler() { return idContratoAlquiler; }
    public void setIdContratoAlquiler(Long idContratoAlquiler) { this.idContratoAlquiler = idContratoAlquiler; }
    public OportunidadComercial getOportunidad() { return oportunidad; }
    public void setOportunidad(OportunidadComercial oportunidad) { this.oportunidad = oportunidad; }
    public SolicitudAlquiler getSolicitudAlquiler() { return solicitudAlquiler; }
    public void setSolicitudAlquiler(SolicitudAlquiler solicitudAlquiler) { this.solicitudAlquiler = solicitudAlquiler; }
    public LocalDate getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDate fechaCierre) { this.fechaCierre = fechaCierre; }
    public EstadoContrato getEstadoContrato() { return estadoContrato; }
    public void setEstadoContrato(EstadoContrato estadoContrato) { this.estadoContrato = estadoContrato; }
    public String getIncidencias() { return incidencias; }
    public void setIncidencias(String incidencias) { this.incidencias = incidencias; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
