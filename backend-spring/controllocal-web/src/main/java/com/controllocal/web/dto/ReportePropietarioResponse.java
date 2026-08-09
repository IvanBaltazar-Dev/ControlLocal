package com.controllocal.web.dto;

import com.controllocal.service.ReportePropietarioService;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Forma JSON congelada de un reporte periodico al propietario. */
public record ReportePropietarioResponse(
        Long id,
        Long idCaptacion,
        Long idAgente,
        LocalDate fechaReporte,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        Integer consultasReportadas,
        Integer visitasReportadas,
        String objecionesFrecuentes,
        String ajustesRecomendados,
        String canalEnvio,
        LocalDateTime fechaCreacion) {

    public static ReportePropietarioResponse desde(
            ReportePropietarioService.FichaReporte ficha) {
        return new ReportePropietarioResponse(
                ficha.id(), ficha.idCaptacion(), ficha.idAgente(),
                ficha.fechaReporte(), ficha.periodoInicio(), ficha.periodoFin(),
                ficha.consultasReportadas(), ficha.visitasReportadas(),
                ficha.objecionesFrecuentes(), ficha.ajustesRecomendados(),
                ficha.canalEnvio(), ficha.fechaCreacion());
    }
}
