package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;
import java.util.Set;

/** Vocabularios aún vigentes de Reporte/Visita/Documentos; comunicación C4 usa su catálogo propio. */
public final class Vocabulario {
    private Vocabulario() { }

    /** 'L' llamada, 'W' WhatsApp, 'E' email, 'P' presencial, 'R' reunion, 'T' portal, 'O' otro. */
    public static final Set<String> CANALES = Set.of("L", "W", "E", "P", "R", "T", "O");
    public static final Set<String> OBJECIONES_VISITA = Set.of("P", "U", "E", "C", "O");
    public static final Set<String> OPINIONES_PRECIO = Set.of("A", "J", "B");
    public static final Set<String> PROXIMAS_ACCIONES = Set.of("V", "O", "S", "D");

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
