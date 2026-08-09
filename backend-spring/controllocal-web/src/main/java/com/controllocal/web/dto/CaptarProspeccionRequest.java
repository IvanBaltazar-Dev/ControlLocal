package com.controllocal.web.dto;

import java.math.BigDecimal;

/** Contrato CONGELADO: comision pactada al captar desde la prospeccion. */
public record CaptarProspeccionRequest(BigDecimal comisionPactada) {
}
