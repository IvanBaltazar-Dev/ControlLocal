package com.controllocal.web.dto;

import com.controllocal.service.VisitaService;

import java.time.LocalDate;
import java.time.LocalTime;

/** Contrato CONGELADO: espejo de Dtos.VisitaRequest de la v1. */
public record VisitaRequest(Long idOportunidad, LocalDate fechaVisita, LocalTime horaVisita,
                            String observaciones) {

    public VisitaService.DatosVisita aDatos() {
        return new VisitaService.DatosVisita(idOportunidad, fechaVisita, horaVisita, observaciones);
    }
}
