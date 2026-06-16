package com.controllocal.model.comercial;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.controllocal.model.comercial.enums.CanalPublicacion;
import com.controllocal.model.comercial.enums.Moneda;
import com.controllocal.model.inmueble.LocalComercial;
import com.controllocal.model.inmueble.enums.EstadoPublicacion;

public class Publicacion {
    private Long idPublicacion;
    private LocalComercial inmueble;
    private CanalPublicacion canal;
    private String urlPublicacion;
    private Integer versionAnuncio;
    private String tituloAnuncio;
    private BigDecimal rentaPublicada;
    private Moneda moneda;
    private BigDecimal inversionPauta;
    private String codigoOrigen;
    private LocalDateTime fechaPublicacion;
    private LocalDateTime fechaBaja;
    private EstadoPublicacion estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    public Long getIdPublicacion() { return idPublicacion; }
    public void setIdPublicacion(Long idPublicacion) { this.idPublicacion = idPublicacion; }
    public LocalComercial getInmueble() { return inmueble; }
    public void setInmueble(LocalComercial inmueble) { this.inmueble = inmueble; }
    public CanalPublicacion getCanal() { return canal; }
    public void setCanal(CanalPublicacion canal) { this.canal = canal; }
    public String getUrlPublicacion() { return urlPublicacion; }
    public void setUrlPublicacion(String urlPublicacion) { this.urlPublicacion = urlPublicacion; }
    public Integer getVersionAnuncio() { return versionAnuncio; }
    public void setVersionAnuncio(Integer versionAnuncio) { this.versionAnuncio = versionAnuncio; }
    public String getTituloAnuncio() { return tituloAnuncio; }
    public void setTituloAnuncio(String tituloAnuncio) { this.tituloAnuncio = tituloAnuncio; }
    public BigDecimal getRentaPublicada() { return rentaPublicada; }
    public void setRentaPublicada(BigDecimal rentaPublicada) { this.rentaPublicada = rentaPublicada; }
    public Moneda getMoneda() { return moneda; }
    public void setMoneda(Moneda moneda) { this.moneda = moneda; }
    public BigDecimal getInversionPauta() { return inversionPauta; }
    public void setInversionPauta(BigDecimal inversionPauta) { this.inversionPauta = inversionPauta; }
    public String getCodigoOrigen() { return codigoOrigen; }
    public void setCodigoOrigen(String codigoOrigen) { this.codigoOrigen = codigoOrigen; }
    public LocalDateTime getFechaPublicacion() { return fechaPublicacion; }
    public void setFechaPublicacion(LocalDateTime fechaPublicacion) { this.fechaPublicacion = fechaPublicacion; }
    public LocalDateTime getFechaBaja() { return fechaBaja; }
    public void setFechaBaja(LocalDateTime fechaBaja) { this.fechaBaja = fechaBaja; }
    public EstadoPublicacion getEstado() { return estado; }
    public void setEstado(EstadoPublicacion estado) { this.estado = estado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
