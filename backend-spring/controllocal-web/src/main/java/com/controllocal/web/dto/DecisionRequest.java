package com.controllocal.web.dto;

/** Contrato CONGELADO: decision del broker sobre una captacion (accion A/O/R + observacion). */
public record DecisionRequest(String accion, String observacion) {
}
