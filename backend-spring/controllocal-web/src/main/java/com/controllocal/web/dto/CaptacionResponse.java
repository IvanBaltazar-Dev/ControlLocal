package com.controllocal.web.dto;

import com.controllocal.service.CaptacionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Contrato CONGELADO: espejo de Dtos.CaptacionResponse de la v1. */
public record CaptacionResponse(Long id, String codigoCaptacion, LocalDate fechaCaptacion,
                                LocalDate fechaInicioVigencia, LocalDate fechaFinVigencia,
                                BigDecimal comisionPactada, String observaciones, String estado,
                                String motivoOperacion, Integer urgencia, Boolean exclusividad,
                                String observacionRevision, LocalDateTime fechaRevision, Long idLocal,
                                String direccionLocal, String distritoLocal, BigDecimal areaM2, String rubro,
                                String propietarioNombre, Long idAgente, String agenteNombre,
                                Long idBrokerRevisor, String fotoPortadaClave, String tipoOperacion,
                                BigDecimal importeReferencia, String monedaReferencia,
                                String tipoComision, String baseCalculo, BigDecimal valorComision,
                                String monedaComision, String tratamientoIgv, String motivoSinComision,
                                LocalDate fechaCierre, String motivoCierre, String detalleMotivoCierre) {

    public static CaptacionResponse desde(CaptacionService.FichaCaptacion f) {
        return new CaptacionResponse(f.id(), f.codigoCaptacion(), f.fechaCaptacion(), f.fechaInicioVigencia(),
                f.fechaFinVigencia(), f.comisionPactada(), f.observaciones(), f.estado(), f.motivoOperacion(),
                f.urgencia(), f.exclusividad(), f.observacionRevision(), f.fechaRevision(), f.idLocal(),
                f.direccionLocal(), f.distritoLocal(), f.areaM2(), f.rubro(), f.propietarioNombre(),
                f.idAgente(), f.agenteNombre(), f.idBrokerRevisor(), f.fotoPortadaClave(),
                f.tipoOperacion(), f.importeReferencia(), f.monedaReferencia(), f.tipoComision(),
                f.baseCalculo(), f.valorComision(), f.monedaComision(), f.tratamientoIgv(),
                f.motivoSinComision(), f.fechaCierre(), f.motivoCierre(), f.detalleMotivoCierre());
    }
}
