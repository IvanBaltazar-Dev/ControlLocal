package com.controllocal.web.dto;

/**
 * Contrato CONGELADO: espejo de Dtos.RevisionDocumentoRequest de la v1.
 * Resultado "C" (conforme, deja el documento VALIDADO) u "O" (observado, con
 * la observacion que el agente debe subsanar). Cualquier otro resultado
 * tambien deja el documento observado: solo "conforme" valida.
 */
public record RevisionDocumentoRequest(String resultado, String observaciones) {
}
