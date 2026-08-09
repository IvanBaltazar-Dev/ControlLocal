package com.controllocal.web.dto;

import com.controllocal.service.ContratoService;

import java.time.LocalDate;

public record TransicionContratoRequest(LocalDate fechaEfectiva, String motivo) {
    public ContratoService.DatosTransicion aDatos() {
        return new ContratoService.DatosTransicion(fechaEfectiva, motivo);
    }
}
