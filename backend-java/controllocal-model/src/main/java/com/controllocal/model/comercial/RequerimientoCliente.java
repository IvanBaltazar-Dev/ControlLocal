package com.controllocal.model.comercial;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.comercial.enums.EstadoRequerimiento;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.comercial.enums.TipoInmuebleComercial;
import com.controllocal.model.inmueble.Distrito;
import com.controllocal.model.persona.ClienteInteresado;

public class RequerimientoCliente {
    private Long idRequerimiento;
    private ClienteInteresado cliente;
    private String rubro;
    private TipoInmuebleComercial tipoInmueble;
    private BigDecimal rentaMin;
    private BigDecimal rentaMax;
    private Moneda moneda;
    private BigDecimal metrajeMin;
    private BigDecimal metrajeMax;
    private BigDecimal frenteMinimo;
    private EstadoRequerimiento estado;
    private String observaciones;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private List<Distrito> distritos = new ArrayList<>();

    public Long getIdRequerimiento() { return idRequerimiento; }
    public void setIdRequerimiento(Long idRequerimiento) { this.idRequerimiento = idRequerimiento; }
    public ClienteInteresado getCliente() { return cliente; }
    public void setCliente(ClienteInteresado cliente) { this.cliente = cliente; }
    public String getRubro() { return rubro; }
    public void setRubro(String rubro) { this.rubro = rubro; }
    public TipoInmuebleComercial getTipoInmueble() { return tipoInmueble; }
    public void setTipoInmueble(TipoInmuebleComercial tipoInmueble) { this.tipoInmueble = tipoInmueble; }
    public BigDecimal getRentaMin() { return rentaMin; }
    public void setRentaMin(BigDecimal rentaMin) { this.rentaMin = rentaMin; }
    public BigDecimal getRentaMax() { return rentaMax; }
    public void setRentaMax(BigDecimal rentaMax) { this.rentaMax = rentaMax; }
    public Moneda getMoneda() { return moneda; }
    public void setMoneda(Moneda moneda) { this.moneda = moneda; }
    public BigDecimal getMetrajeMin() { return metrajeMin; }
    public void setMetrajeMin(BigDecimal metrajeMin) { this.metrajeMin = metrajeMin; }
    public BigDecimal getMetrajeMax() { return metrajeMax; }
    public void setMetrajeMax(BigDecimal metrajeMax) { this.metrajeMax = metrajeMax; }
    public BigDecimal getFrenteMinimo() { return frenteMinimo; }
    public void setFrenteMinimo(BigDecimal frenteMinimo) { this.frenteMinimo = frenteMinimo; }
    public EstadoRequerimiento getEstado() { return estado; }
    public void setEstado(EstadoRequerimiento estado) { this.estado = estado; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
    public List<Distrito> getDistritos() { return distritos; }
    public void setDistritos(List<Distrito> distritos) {
        this.distritos = distritos != null ? new ArrayList<>(distritos) : new ArrayList<>();
    }
}
