package com.controllocal.web.dto;

import com.controllocal.service.RequerimientoService;

import java.math.BigDecimal;
import java.util.List;

/** Contrato CONGELADO: espejo de Dtos.RequerimientoRequest de la v1 (distritos por NOMBRE). */
public record RequerimientoRequest(Long idCliente, String rubro, String tipoInmueble, BigDecimal rentaMin,
                                   BigDecimal rentaMax, String moneda, BigDecimal metrajeMin,
                                   BigDecimal metrajeMax, BigDecimal frenteMinimo, String estado,
                                   String observaciones, List<String> distritos) {

    public RequerimientoService.DatosRequerimiento aDatos() {
        return new RequerimientoService.DatosRequerimiento(idCliente, rubro, tipoInmueble, rentaMin,
                rentaMax, moneda, metrajeMin, metrajeMax, frenteMinimo, estado, observaciones, distritos);
    }
}
