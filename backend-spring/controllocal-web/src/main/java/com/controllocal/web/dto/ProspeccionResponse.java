package com.controllocal.web.dto;

import com.controllocal.service.ProspeccionService;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Contrato CONGELADO: espejo de Dtos.ProspeccionResponse de la v1 (22 campos). */
public record ProspeccionResponse(Long id, String codigoProspeccion, Long localId, String localCodigo,
                                  String direccion, String distrito, BigDecimal areaM2, String rubro,
                                  BigDecimal precioReferencial, String monedaReferencial,
                                  String propietarioNombre, Long idAgente,
                                  String agenteNombre, String estado, String resultadoPropuesta,
                                  LocalDate fechaContacto, LocalDate fechaReunion, LocalDate fechaPropuesta,
                                  LocalDate fechaRecontacto, String observaciones, Long idCaptacion,
                                  String captacionCodigo, String disponibilidad) {

    public static ProspeccionResponse desde(ProspeccionService.FichaProspeccion f) {
        return new ProspeccionResponse(f.id(), f.codigoProspeccion(), f.localId(), f.localCodigo(),
                f.direccion(), f.distrito(), f.areaM2(), f.rubro(), f.precioReferencial(),
                f.monedaReferencial(), f.propietarioNombre(), f.idAgente(), f.agenteNombre(),
                f.estado(), f.resultadoPropuesta(),
                f.fechaContacto(), f.fechaReunion(), f.fechaPropuesta(), f.fechaRecontacto(),
                f.observaciones(), f.idCaptacion(), f.captacionCodigo(), f.disponibilidad());
    }
}
