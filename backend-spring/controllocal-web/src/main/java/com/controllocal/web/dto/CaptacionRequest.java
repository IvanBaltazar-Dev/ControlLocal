package com.controllocal.web.dto;

import com.controllocal.service.CaptacionService;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Contrato CONGELADO: espejo de Dtos.CaptacionRequest de la v1. */
public record CaptacionRequest(String codigoCaptacion, LocalDate fechaCaptacion, LocalDate fechaInicioVigencia,
                               LocalDate fechaFinVigencia, BigDecimal comisionPactada, String observaciones,
                               Long idLocal, Long idAgente, String motivoOperacion, Integer urgencia,
                               Boolean exclusividad, String tipoOperacion, BigDecimal importeReferencia,
                               String monedaReferencia, String tipoComision, String baseCalculo,
                               BigDecimal valorComision, String monedaComision, String tratamientoIgv,
                               String motivoSinComision) {

    public CaptacionRequest(String codigoCaptacion, LocalDate fechaCaptacion,
                            LocalDate fechaInicioVigencia, LocalDate fechaFinVigencia,
                            BigDecimal comisionPactada, String observaciones, Long idLocal,
                            Long idAgente, String motivoOperacion, Integer urgencia,
                            Boolean exclusividad) {
        this(codigoCaptacion, fechaCaptacion, fechaInicioVigencia, fechaFinVigencia,
                comisionPactada, observaciones, idLocal, idAgente, motivoOperacion,
                urgencia, exclusividad, null, null, null, null, null, null, null, null, null);
    }

    public CaptacionService.DatosCaptacion aDatos() {
        return new CaptacionService.DatosCaptacion(codigoCaptacion, fechaCaptacion, fechaInicioVigencia,
                fechaFinVigencia, comisionPactada, observaciones, idLocal, idAgente, motivoOperacion,
                urgencia, exclusividad, tipoOperacion, importeReferencia, monedaReferencia,
                tipoComision, baseCalculo, valorComision, monedaComision, tratamientoIgv,
                motivoSinComision);
    }
}
