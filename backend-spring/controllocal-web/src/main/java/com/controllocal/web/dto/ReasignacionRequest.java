package com.controllocal.web.dto;

/** Contrato CONGELADO: reasignacion de captacion (agente destino + motivo). */
public record ReasignacionRequest(Long idAgenteNuevo, String motivo) {
}
