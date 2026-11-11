package com.controllocal.asistencia.brox;

/** Procedencia de la interacción. El puente interno la entrega al caso Core, sin cabeceras de loopback. */
public record Traza(String canal, String agente, String modelo, String modeloVersion,
                    String conversacionId, String turnoId, String mensajeId, String peticion,
                    String herramienta) {
    public static final String CANAL_WHATSAPP = "WHATSAPP";
    public static final String CANAL_API = "API";
    public static final java.util.List<String> CANALES =
            java.util.List.of("SPA", CANAL_WHATSAPP, CANAL_API, "SISTEMA");

    public Traza {
        if (esVacio(agente) || esVacio(conversacionId) || esVacio(turnoId)) {
            throw new IllegalArgumentException("Se requiere agente, conversación y turno para conservar procedencia.");
        }
        canal = canalValidado(canal);
    }

    public Traza invocando(String herramienta) {
        return new Traza(canal, agente, modelo, modeloVersion, conversacionId, turnoId,
                mensajeId, peticion, herramienta);
    }

    private static String canalValidado(String canal) {
        if (esVacio(canal)) return CANAL_API;
        String limpio = canal.trim().toUpperCase(java.util.Locale.ROOT);
        return CANALES.contains(limpio) ? limpio : CANAL_API;
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
