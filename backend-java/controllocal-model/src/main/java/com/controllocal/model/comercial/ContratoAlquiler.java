package com.controllocal.model.comercial;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.controllocal.model.comercial.enums.EstadoContrato;
import com.controllocal.model.comercial.enums.FormaPago;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.comercial.enums.TipoReajuste;

public class ContratoAlquiler {
    private Long idContratoAlquiler;
    private OportunidadComercial oportunidad;
    private SolicitudAlquiler solicitudAlquiler;
    private BigDecimal rentaMensual;
    private Moneda moneda;
    private Integer plazoContratoMeses;
    private LocalDate fechaInicioContrato;
    private LocalDate fechaFinContrato;
    private Integer mesesGarantia;
    private BigDecimal montoGarantia;
    private Integer mesesAdelanto;
    private BigDecimal cuotaMantenimiento;
    private TipoReajuste tipoReajuste;
    private BigDecimal porcentajeReajuste;
    private FormaPago formaPago;
    private LocalDate fechaCierre;
    private BigDecimal comisionGenerada;
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
    public BigDecimal getRentaMensual() { return rentaMensual; }
    public void setRentaMensual(BigDecimal rentaMensual) { this.rentaMensual = rentaMensual; }
    public Moneda getMoneda() { return moneda; }
    public void setMoneda(Moneda moneda) { this.moneda = moneda; }
    public Integer getPlazoContratoMeses() { return plazoContratoMeses; }
    public void setPlazoContratoMeses(Integer plazoContratoMeses) { this.plazoContratoMeses = plazoContratoMeses; }
    public LocalDate getFechaInicioContrato() { return fechaInicioContrato; }
    public void setFechaInicioContrato(LocalDate fechaInicioContrato) { this.fechaInicioContrato = fechaInicioContrato; }
    public LocalDate getFechaFinContrato() { return fechaFinContrato; }
    public void setFechaFinContrato(LocalDate fechaFinContrato) { this.fechaFinContrato = fechaFinContrato; }
    public Integer getMesesGarantia() { return mesesGarantia; }
    public void setMesesGarantia(Integer mesesGarantia) { this.mesesGarantia = mesesGarantia; }
    public BigDecimal getMontoGarantia() { return montoGarantia; }
    public void setMontoGarantia(BigDecimal montoGarantia) { this.montoGarantia = montoGarantia; }
    public Integer getMesesAdelanto() { return mesesAdelanto; }
    public void setMesesAdelanto(Integer mesesAdelanto) { this.mesesAdelanto = mesesAdelanto; }
    public BigDecimal getCuotaMantenimiento() { return cuotaMantenimiento; }
    public void setCuotaMantenimiento(BigDecimal cuotaMantenimiento) { this.cuotaMantenimiento = cuotaMantenimiento; }
    public TipoReajuste getTipoReajuste() { return tipoReajuste; }
    public void setTipoReajuste(TipoReajuste tipoReajuste) { this.tipoReajuste = tipoReajuste; }
    public BigDecimal getPorcentajeReajuste() { return porcentajeReajuste; }
    public void setPorcentajeReajuste(BigDecimal porcentajeReajuste) { this.porcentajeReajuste = porcentajeReajuste; }
    public FormaPago getFormaPago() { return formaPago; }
    public void setFormaPago(FormaPago formaPago) { this.formaPago = formaPago; }
    public LocalDate getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDate fechaCierre) { this.fechaCierre = fechaCierre; }
    public BigDecimal getComisionGenerada() { return comisionGenerada; }
    public void setComisionGenerada(BigDecimal comisionGenerada) { this.comisionGenerada = comisionGenerada; }
    public EstadoContrato getEstadoContrato() { return estadoContrato; }
    public void setEstadoContrato(EstadoContrato estadoContrato) { this.estadoContrato = estadoContrato; }
    public String getIncidencias() { return incidencias; }
    public void setIncidencias(String incidencias) { this.incidencias = incidencias; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
