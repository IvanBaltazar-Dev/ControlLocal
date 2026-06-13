package com.controllocal.model.inmueble;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import com.controllocal.model.comercial.enums.HitoPrecio;
import com.controllocal.model.comercial.enums.Moneda;

/**
 * Historico de precios de un local: una fila por hito o cambio (tabla precio_local).
 * El precio_referencial del local se mantiene como vigente; el historico y el
 * precio CERRADO real viven aqui.
 */
public class PrecioLocal {

    private Long idPrecio;
    private Long idLocal;
    private HitoPrecio hito;
    private Moneda moneda;
    private BigDecimal monto;
    private LocalDate fecha;
    private LocalDateTime fechaCreacion;

    public PrecioLocal() {
    }

    public PrecioLocal(Long idLocal, HitoPrecio hito, Moneda moneda, BigDecimal monto, LocalDate fecha) {
        this.idLocal = idLocal;
        this.hito = hito;
        this.moneda = moneda;
        this.monto = monto;
        this.fecha = fecha;
    }

    public Long getIdPrecio() {
        return idPrecio;
    }

    public void setIdPrecio(Long idPrecio) {
        this.idPrecio = idPrecio;
    }

    public Long getIdLocal() {
        return idLocal;
    }

    public void setIdLocal(Long idLocal) {
        this.idLocal = idLocal;
    }

    public HitoPrecio getHito() {
        return hito;
    }

    public void setHito(HitoPrecio hito) {
        this.hito = hito;
    }

    public Moneda getMoneda() {
        return moneda;
    }

    public void setMoneda(Moneda moneda) {
        this.moneda = moneda;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    @Override
    public String toString() {
        return "PrecioLocal{idPrecio=" + idPrecio + ", idLocal=" + idLocal
                + ", hito=" + hito + ", moneda=" + moneda + ", monto=" + monto
                + ", fecha=" + fecha + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PrecioLocal that)) {
            return false;
        }
        if (idPrecio == null || that.idPrecio == null) {
            return false;
        }
        return Objects.equals(idPrecio, that.idPrecio);
    }

    @Override
    public int hashCode() {
        return idPrecio != null ? Objects.hash(idPrecio) : System.identityHashCode(this);
    }
}
