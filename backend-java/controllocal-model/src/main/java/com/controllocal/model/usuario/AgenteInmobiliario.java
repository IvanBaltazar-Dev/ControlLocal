package com.controllocal.model.usuario;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.controllocal.model.comercial.Captacion;
import com.controllocal.model.usuario.enums.EstadoOperativoAgente;

public class AgenteInmobiliario extends UsuarioInterno {

    private Long idAgente;
    private String codigoAgente;
    private String zonaAsignada;
    private LocalDate fechaIngreso;
    private EstadoOperativoAgente estadoOperativo;
    private List<Captacion> captacionesAsignadas = new ArrayList<>();

    public Long getIdAgente() {
        return idAgente;
    }

    public void setIdAgente(Long idAgente) {
        this.idAgente = idAgente;
    }

    public String getCodigoAgente() {
        return codigoAgente;
    }

    public void setCodigoAgente(String codigoAgente) {
        this.codigoAgente = codigoAgente;
    }

    public String getZonaAsignada() {
        return zonaAsignada;
    }

    public void setZonaAsignada(String zonaAsignada) {
        this.zonaAsignada = zonaAsignada;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(LocalDate fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public EstadoOperativoAgente getEstadoOperativo() {
        return estadoOperativo;
    }

    public void setEstadoOperativo(EstadoOperativoAgente estadoOperativo) {
        this.estadoOperativo = estadoOperativo;
    }

    public List<Captacion> getCaptacionesAsignadas() {
        return captacionesAsignadas;
    }

    public void setCaptacionesAsignadas(List<Captacion> captacionesAsignadas) {
        this.captacionesAsignadas = captacionesAsignadas;
    }
}
