package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;

import java.util.Set;

/**
 * Codigos del cable CONGELADO que comparten visitas e interacciones, con los
 * mensajes de error exactos de {@code CodigoEnum.fromCodigo} de la v1.
 *
 * <p>{@code ResultadoInteraccion} es el enum raro de F3 (§1 del contrato):
 * MIXTO, conviven codigos de 1 caracter heredados (P, I, N, S, D) con
 * codigos-palabra por contexto (CONTACTADO, VISITA_AGENDADA...). No se
 * normaliza nada durante la convivencia; aqui solo se valida.
 */
public final class Vocabulario {

    private Vocabulario() {
    }

    /** Todos los codigos de ResultadoInteraccion de la v1, en su forma mixta. */
    public static final Set<String> RESULTADOS = Set.of(
            "P", "I", "N", "S", "D",
            "CONTACTADO", "REUNION_AGENDADA", "PROPUESTA_ENVIADA", "ACEPTA_CAPTAR", "NO_ACEPTA", "RECONTACTAR",
            "DOCS_SOLICITADOS", "CONDICIONES_AJUSTADAS", "PUBLICACION_COORDINADA", "PROPIETARIO_OBSERVA",
            "LISTO_PARA_PUBLICAR", "PAUSAR_GESTION",
            "INTERESADO", "VISITA_AGENDADA", "OFERTA_SOLICITADA", "NEGOCIANDO", "NO_INTERESADO", "DESCARTADO",
            "BUSQUEDA_LEVANTADA", "REQUIERE_OPCIONES", "NO_RESPONDE", "SEGUIMIENTO");

    /**
     * Resultados que significan "el cliente no continua". Cuando aparecen como
     * desenlace de una visita, el flujo cierra la oportunidad con su motivo
     * tipificado. Son los codigos de NO_INTERESADO y DESCARTADO en sus DOS
     * formas (la corta heredada y la palabra del contexto oportunidad).
     */
    private static final Set<String> NO_CONTINUIDAD = Set.of("N", "D", "NO_INTERESADO", "DESCARTADO");

    /** 'L' llamada, 'W' WhatsApp, 'E' email, 'P' presencial, 'R' reunion, 'T' portal, 'O' otro. */
    public static final Set<String> CANALES = Set.of("L", "W", "E", "P", "R", "T", "O");

    /** 'P' precio, 'U' ubicacion, 'C' condiciones, 'L' local, 'N' no responde, 'E' otra opcion, 'O' otro. */
    public static final Set<String> RAZONES_NO_CONTINUIDAD = Set.of("P", "U", "C", "L", "N", "E", "O");

    public static final Set<String> OBJECIONES_VISITA = Set.of("P", "U", "E", "C", "O");

    public static final Set<String> OPINIONES_PRECIO = Set.of("A", "J", "B");

    public static final Set<String> PROXIMAS_ACCIONES = Set.of("V", "O", "S", "D");

    public static boolean implicaNoContinuidad(String resultado) {
        return resultado != null && NO_CONTINUIDAD.contains(resultado);
    }

    /** Allow-list de resultado por contexto de interaccion (§6 del contrato). */
    public static boolean resultadoPermitido(String contexto, String resultado) {
        return switch (contexto == null ? "OPORTUNIDAD" : contexto.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "PROSPECCION" -> Set.of(
                    "CONTACTADO", "REUNION_AGENDADA", "PROPUESTA_ENVIADA",
                    "ACEPTA_CAPTAR", "NO_ACEPTA", "RECONTACTAR").contains(resultado);
            case "CAPTACION" -> Set.of(
                    "DOCS_SOLICITADOS", "CONDICIONES_AJUSTADAS", "PUBLICACION_COORDINADA",
                    "PROPIETARIO_OBSERVA", "LISTO_PARA_PUBLICAR", "PAUSAR_GESTION").contains(resultado);
            case "CLIENTE" -> Set.of(
                    "BUSQUEDA_LEVANTADA", "PROPUESTA_ENVIADA", "REQUIERE_OPCIONES",
                    "NO_RESPONDE", "SEGUIMIENTO", "DESCARTADO").contains(resultado);
            default -> Set.of(
                    "INTERESADO", "VISITA_AGENDADA", "OFERTA_SOLICITADA",
                    "NEGOCIANDO", "NO_INTERESADO", "DESCARTADO").contains(resultado);
        };
    }

    /** Codigo obligatorio: mensajes identicos a CodigoEnum.fromCodigo de la v1. */
    public static String exigir(String codigo, Set<String> validos, String enumSimpleName) {
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaNegocioException("El codigo de " + enumSimpleName + " es obligatorio.");
        }
        String valor = codigo.trim();
        if (!validos.contains(valor)) {
            throw new ReglaNegocioException("Codigo invalido para " + enumSimpleName + ": " + codigo);
        }
        return valor;
    }

    /** Codigo opcional: null/vacio pasa; un codigo presente e invalido falla. */
    public static String opcional(String codigo, Set<String> validos, String enumSimpleName) {
        return codigo == null || codigo.isBlank() ? null : exigir(codigo, validos, enumSimpleName);
    }
}
