package com.controllocal.web.dto;

import com.controllocal.service.ReportePropietarioService;

import java.time.LocalDate;

/** Request congelado del alta E2; los tres valores derivados son ignorados. */
public record ReportePropietarioRequest(
        LocalDate periodoInicio,
        LocalDate periodoFin,
        Integer consultasReportadas,
        Integer visitasReportadas,
        String objecionesFrecuentes,
        String ajustesRecomendados,
        String canalEnvio) {

    public ReportePropietarioService.DatosReporte aDatos() {
        return new ReportePropietarioService.DatosReporte(
                periodoInicio, periodoFin, consultasReportadas,
                visitasReportadas, objecionesFrecuentes,
                ajustesRecomendados, canalEnvio);
    }
}
