package com.controllocal.rest.reports;

public record ReporteIndicadoresJasperDto(
        String titulo,
        String subtitulo,
        String periodo,
        String fechaGeneracion,
        String kpi1Etiqueta,
        String kpi1Valor,
        String kpi1Delta,
        String kpi2Etiqueta,
        String kpi2Valor,
        String kpi2Delta,
        String kpi3Etiqueta,
        String kpi3Valor,
        String kpi3Delta,
        String kpi4Etiqueta,
        String kpi4Valor,
        String kpi4Delta,
        String operativoResumen,
        String cierresPorMes,
        String captacionesPorMes,
        String saludCaptaciones,
        String embudo,
        String desempeno) {

    public String getTitulo() { return titulo; }
    public String getSubtitulo() { return subtitulo; }
    public String getPeriodo() { return periodo; }
    public String getFechaGeneracion() { return fechaGeneracion; }
    public String getKpi1Etiqueta() { return kpi1Etiqueta; }
    public String getKpi1Valor() { return kpi1Valor; }
    public String getKpi1Delta() { return kpi1Delta; }
    public String getKpi2Etiqueta() { return kpi2Etiqueta; }
    public String getKpi2Valor() { return kpi2Valor; }
    public String getKpi2Delta() { return kpi2Delta; }
    public String getKpi3Etiqueta() { return kpi3Etiqueta; }
    public String getKpi3Valor() { return kpi3Valor; }
    public String getKpi3Delta() { return kpi3Delta; }
    public String getKpi4Etiqueta() { return kpi4Etiqueta; }
    public String getKpi4Valor() { return kpi4Valor; }
    public String getKpi4Delta() { return kpi4Delta; }
    public String getOperativoResumen() { return operativoResumen; }
    public String getCierresPorMes() { return cierresPorMes; }
    public String getCaptacionesPorMes() { return captacionesPorMes; }
    public String getSaludCaptaciones() { return saludCaptaciones; }
    public String getEmbudo() { return embudo; }
    public String getDesempeno() { return desempeno; }
}
