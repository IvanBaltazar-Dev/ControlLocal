package com.controllocal.web.dto;

/** Contrato CONGELADO: enlaza una captacion ya creada a la prospeccion. */
public record MarcarProspeccionCaptadaRequest(Long idCaptacion, String codigoCaptacion) {
}
