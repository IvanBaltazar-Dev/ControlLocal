package com.controllocal.rest.reports;

public class ReportePropietarioJasperDto {

    private final String codigoCaptacion;
    private final String direccion;
    private final String distrito;
    private final String propietario;
    private final String periodo;
    private final String fechaReporte;
    private final String canalEnvio;
    private final String area;
    private final String rubro;
    private final String consultasReportadas;
    private final String visitasReportadas;
    private final String objecionesFrecuentes;
    private final String ajustesRecomendados;
    private final String fechaGeneracion;

    public ReportePropietarioJasperDto(String codigoCaptacion, String direccion, String distrito,
            String propietario, String periodo, String fechaReporte, String canalEnvio, String area,
            String rubro, String consultasReportadas, String visitasReportadas,
            String objecionesFrecuentes, String ajustesRecomendados, String fechaGeneracion) {
        this.codigoCaptacion = codigoCaptacion;
        this.direccion = direccion;
        this.distrito = distrito;
        this.propietario = propietario;
        this.periodo = periodo;
        this.fechaReporte = fechaReporte;
        this.canalEnvio = canalEnvio;
        this.area = area;
        this.rubro = rubro;
        this.consultasReportadas = consultasReportadas;
        this.visitasReportadas = visitasReportadas;
        this.objecionesFrecuentes = objecionesFrecuentes;
        this.ajustesRecomendados = ajustesRecomendados;
        this.fechaGeneracion = fechaGeneracion;
    }

    public String getCodigoCaptacion() { return codigoCaptacion; }
    public String getDireccion() { return direccion; }
    public String getDistrito() { return distrito; }
    public String getPropietario() { return propietario; }
    public String getPeriodo() { return periodo; }
    public String getFechaReporte() { return fechaReporte; }
    public String getCanalEnvio() { return canalEnvio; }
    public String getArea() { return area; }
    public String getRubro() { return rubro; }
    public String getConsultasReportadas() { return consultasReportadas; }
    public String getVisitasReportadas() { return visitasReportadas; }
    public String getObjecionesFrecuentes() { return objecionesFrecuentes; }
    public String getAjustesRecomendados() { return ajustesRecomendados; }
    public String getFechaGeneracion() { return fechaGeneracion; }
}
