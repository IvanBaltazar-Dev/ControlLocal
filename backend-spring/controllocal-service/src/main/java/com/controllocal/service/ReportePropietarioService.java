package com.controllocal.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Caso de uso E2: reporte periodico de avance al propietario, anclado a una
 * captacion. El resumen es autoritativo del servidor; los contadores del
 * request existen solo porque forman parte del cable congelado.
 */
public interface ReportePropietarioService {

    record DatosReporte(LocalDate periodoInicio, LocalDate periodoFin,
                        Integer consultasReportadas, Integer visitasReportadas,
                        String objecionesFrecuentes, String ajustesRecomendados,
                        String canalEnvio) {
    }

    record ResumenAvance(int consultas, int visitas, String objeciones) {
    }

    record FichaReporte(Long id, Long idCaptacion, Long idAgente,
                        LocalDate fechaReporte, LocalDate periodoInicio,
                        LocalDate periodoFin, Integer consultasReportadas,
                        Integer visitasReportadas, String objecionesFrecuentes,
                        String ajustesRecomendados, String canalEnvio,
                        LocalDateTime fechaCreacion) {
    }

    List<FichaReporte> listar(long idCaptacion, Actor actor);

    ResumenAvance preview(long idCaptacion, LocalDate desde, LocalDate hasta, Actor actor);

    FichaReporte registrar(long idCaptacion, DatosReporte datos, Actor actor);
}
