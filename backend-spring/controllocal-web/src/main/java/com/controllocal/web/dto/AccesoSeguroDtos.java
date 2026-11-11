package com.controllocal.web.dto;

import java.time.Instant;
import java.util.List;

/** Protocolo mínimo: no recibe actor/tenant/rol ni produce credenciales. */
public final class AccesoSeguroDtos {
    private AccesoSeguroDtos() { }
    public record DestinoSeguro(String finalidad, String metodo, String ruta) { }
    public record Protocolo(int version, List<String> propositos, List<DestinoSeguro> destinos) { }
    public record IniciarRequest(String proposito) { }
    public record VinculoResponse(String flujoRef, String proposito, Instant venceEn, String destinoSeguro) { }
    public record CompletarRequest(String proposito) { }
    /** Esta referencia opaca se procesa sólo en la superficie segura. No es un JWT. */
    public record RetornoPreparadoResponse(String flujoRef, String proposito, String retornoRef, Instant venceEn) { }
    public record RetornoRequest(String proposito, String retornoRef) { }
    public record RetornoResponse(String flujoRef, String proposito, String estado) { }
}
