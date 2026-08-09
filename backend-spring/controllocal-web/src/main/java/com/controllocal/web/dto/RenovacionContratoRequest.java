package com.controllocal.web.dto;

import com.controllocal.service.ContratoService;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RenovacionContratoRequest(LocalDate fechaInicioContrato, LocalDate fechaFinContrato,
                                        BigDecimal rentaContractual, String moneda, String motivo) {
    public ContratoService.DatosRenovacion aDatos() {
        return new ContratoService.DatosRenovacion(fechaInicioContrato, fechaFinContrato,
                rentaContractual, moneda, motivo);
    }
}
