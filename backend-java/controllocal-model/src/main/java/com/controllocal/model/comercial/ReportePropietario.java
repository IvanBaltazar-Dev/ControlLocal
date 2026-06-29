package com.controllocal.model.comercial;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.controllocal.model.comercial.enums.CanalContacto;
import com.controllocal.model.usuario.AgenteInmobiliario;

public class    ReportePropietario {
    private Long idReportePropietario;
    private Captacion captacion;
    private AgenteInmobiliario agente;
    private LocalDate fechaReporte;
    private LocalDate periodoInicio;
    private LocalDate periodoFin;
    private Integer consultasReportadas;
    private Integer visitasReportadas;
    private String objecionesFrecuentes;
    private String ajustesRecomendados;
    private CanalContacto canalEnvio;
    private LocalDateTime fechaCreacion;

    public Long getIdReportePropietario() { return idReportePropietario; }
    public void setIdReportePropietario(Long idReportePropietario) { this.idReportePropietario = idReportePropietario; }
    public Captacion getCaptacion() { return captacion; }
    public void setCaptacion(Captacion captacion) { this.captacion = captacion; }
    public AgenteInmobiliario getAgente() { return agente; }
    public void setAgente(AgenteInmobiliario agente) { this.agente = agente; }
    public LocalDate getFechaReporte() { return fechaReporte; }
    public void setFechaReporte(LocalDate fechaReporte) { this.fechaReporte = fechaReporte; }
    public LocalDate getPeriodoInicio() { return periodoInicio; }
    public void setPeriodoInicio(LocalDate periodoInicio) { this.periodoInicio = periodoInicio; }
    public LocalDate getPeriodoFin() { return periodoFin; }
    public void setPeriodoFin(LocalDate periodoFin) { this.periodoFin = periodoFin; }
    public Integer getConsultasReportadas() { return consultasReportadas; }
    public void setConsultasReportadas(Integer consultasReportadas) { this.consultasReportadas = consultasReportadas; }
    public Integer getVisitasReportadas() { return visitasReportadas; }
    public void setVisitasReportadas(Integer visitasReportadas) { this.visitasReportadas = visitasReportadas; }
    public String getObjecionesFrecuentes() { return objecionesFrecuentes; }
    public void setObjecionesFrecuentes(String objecionesFrecuentes) { this.objecionesFrecuentes = objecionesFrecuentes; }
    public String getAjustesRecomendados() { return ajustesRecomendados; }
    public void setAjustesRecomendados(String ajustesRecomendados) { this.ajustesRecomendados = ajustesRecomendados; }
    public CanalContacto getCanalEnvio() { return canalEnvio; }
    public void setCanalEnvio(CanalContacto canalEnvio) { this.canalEnvio = canalEnvio; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
