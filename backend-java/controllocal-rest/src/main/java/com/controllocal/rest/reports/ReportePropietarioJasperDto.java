package com.controllocal.rest.reports;

import java.awt.Image;

public class ReportePropietarioJasperDto {

    private final String codigoCaptacion;
    private final String direccion;
    private final String distrito;
    private final String propietario;
    private final String agente;
    private final String estadoCaptacion;
    private final String periodo;
    private final String fechaReporte;
    private final String canalEnvio;
    private final String area;
    private final String rubro;
    private final String precioReferencial;
    private final String comision;
    private final String vigencia;
    private final String exclusividad;
    private final String consultasReportadas;
    private final String visitasReportadas;
    private final String conversionVisitas;
    private final String lecturaEjecutiva;
    private final String objecionesFrecuentes;
    private final String ajustesRecomendados;
    private final String fechaGeneracion;
    private final Image graficoInteres;

    public ReportePropietarioJasperDto(String codigoCaptacion, String direccion, String distrito,
            String propietario, String agente, String estadoCaptacion, String periodo, String fechaReporte,
            String canalEnvio, String area, String rubro, String precioReferencial, String comision,
            String vigencia, String exclusividad, String consultasReportadas, String visitasReportadas,
            String conversionVisitas, String lecturaEjecutiva, String objecionesFrecuentes,
            String ajustesRecomendados, String fechaGeneracion, Image graficoInteres) {
        this.codigoCaptacion = codigoCaptacion;
        this.direccion = direccion;
        this.distrito = distrito;
        this.propietario = propietario;
        this.agente = agente;
        this.estadoCaptacion = estadoCaptacion;
        this.periodo = periodo;
        this.fechaReporte = fechaReporte;
        this.canalEnvio = canalEnvio;
        this.area = area;
        this.rubro = rubro;
        this.precioReferencial = precioReferencial;
        this.comision = comision;
        this.vigencia = vigencia;
        this.exclusividad = exclusividad;
        this.consultasReportadas = consultasReportadas;
        this.visitasReportadas = visitasReportadas;
        this.conversionVisitas = conversionVisitas;
        this.lecturaEjecutiva = lecturaEjecutiva;
        this.objecionesFrecuentes = objecionesFrecuentes;
        this.ajustesRecomendados = ajustesRecomendados;
        this.fechaGeneracion = fechaGeneracion;
        this.graficoInteres = graficoInteres;
    }

    public String getCodigoCaptacion() { return codigoCaptacion; }
    public String getDireccion() { return direccion; }
    public String getDistrito() { return distrito; }
    public String getPropietario() { return propietario; }
    public String getAgente() { return agente; }
    public String getEstadoCaptacion() { return estadoCaptacion; }
    public String getPeriodo() { return periodo; }
    public String getFechaReporte() { return fechaReporte; }
    public String getCanalEnvio() { return canalEnvio; }
    public String getArea() { return area; }
    public String getRubro() { return rubro; }
    public String getPrecioReferencial() { return precioReferencial; }
    public String getComision() { return comision; }
    public String getVigencia() { return vigencia; }
    public String getExclusividad() { return exclusividad; }
    public String getConsultasReportadas() { return consultasReportadas; }
    public String getVisitasReportadas() { return visitasReportadas; }
    public String getConversionVisitas() { return conversionVisitas; }
    public String getLecturaEjecutiva() { return lecturaEjecutiva; }
    public String getObjecionesFrecuentes() { return objecionesFrecuentes; }
    public String getAjustesRecomendados() { return ajustesRecomendados; }
    public String getFechaGeneracion() { return fechaGeneracion; }
    public Image getGraficoInteres() { return graficoInteres; }
}
