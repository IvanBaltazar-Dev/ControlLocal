package com.controllocal.web.dto;

/**
 * Contrato CONGELADO: espejo del FotoLocalRequest de la v1. La imagen llega
 * en base64 porque el POST binario rompia el HttpClient de .NET contra
 * GlassFish; el formato del cable se mantiene hasta el corte.
 */
public record FotoLocalRequest(String nombreArchivo, String contenidoBase64) {
}
