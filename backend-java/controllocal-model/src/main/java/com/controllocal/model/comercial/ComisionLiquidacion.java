package com.controllocal.model.comercial;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.controllocal.model.comercial.enums.EstadoComision;
import com.controllocal.model.comercial.enums.Moneda;

public class ComisionLiquidacion {
    private Long idComisionLiquidacion;
    private ContratoAlquiler contratoAlquiler;
    private BigDecimal monto;
    private Moneda moneda;
    private BigDecimal montoAgente;
    private BigDecimal montoEmpresa;
    private LocalDate fechaCobro;
    private EstadoComision estado;

    public Long getIdComisionLiquidacion() { return idComisionLiquidacion; }
    public void setIdComisionLiquidacion(Long idComisionLiquidacion) { this.idComisionLiquidacion = idComisionLiquidacion; }
    public ContratoAlquiler getContratoAlquiler() { return contratoAlquiler; }
    public void setContratoAlquiler(ContratoAlquiler contratoAlquiler) { this.contratoAlquiler = contratoAlquiler; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public Moneda getMoneda() { return moneda; }
    public void setMoneda(Moneda moneda) { this.moneda = moneda; }
    public BigDecimal getMontoAgente() { return montoAgente; }
    public void setMontoAgente(BigDecimal montoAgente) { this.montoAgente = montoAgente; }
    public BigDecimal getMontoEmpresa() { return montoEmpresa; }
    public void setMontoEmpresa(BigDecimal montoEmpresa) { this.montoEmpresa = montoEmpresa; }
    public LocalDate getFechaCobro() { return fechaCobro; }
    public void setFechaCobro(LocalDate fechaCobro) { this.fechaCobro = fechaCobro; }
    public EstadoComision getEstado() { return estado; }
    public void setEstado(EstadoComision estado) { this.estado = estado; }
}
