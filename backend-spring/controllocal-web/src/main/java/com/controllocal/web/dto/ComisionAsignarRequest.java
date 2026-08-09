package com.controllocal.web.dto;

import java.math.BigDecimal;

/**
 * Contrato CONGELADO: espejo de Dtos.ComisionAsignarRequest de la v1. El
 * broker supervisor define el monto real del agente; el de la empresa se
 * calcula solo.
 */
public record ComisionAsignarRequest(BigDecimal montoAgente) {
}
