package com.controllocal.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Contrato CONGELADO: espejo de Dtos.ReprogramarVisitaRequest de la v1. */
public record ReprogramarVisitaRequest(LocalDate fechaVisita, LocalTime horaVisita) {
}
