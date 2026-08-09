package com.controllocal.web.dto;

import com.controllocal.service.RequerimientoService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Contrato CONGELADO: espejo de Dtos.RequerimientoResponse de la v1. */
public record RequerimientoResponse(Long id, Long idCliente, String rubro, String tipoInmueble,
                                    BigDecimal rentaMin, BigDecimal rentaMax, String moneda,
                                    BigDecimal metrajeMin, BigDecimal metrajeMax, BigDecimal frenteMinimo,
                                    String estado, String observaciones, List<String> distritos,
                                    LocalDateTime fechaCreacion, LocalDateTime fechaActualizacion) {

    public static RequerimientoResponse desde(RequerimientoService.FichaRequerimiento f) {
        return new RequerimientoResponse(f.id(), f.idCliente(), f.rubro(), f.tipoInmueble(), f.rentaMin(),
                f.rentaMax(), f.moneda(), f.metrajeMin(), f.metrajeMax(), f.frenteMinimo(), f.estado(),
                f.observaciones(), f.distritos(), f.fechaCreacion(), f.fechaActualizacion());
    }
}
