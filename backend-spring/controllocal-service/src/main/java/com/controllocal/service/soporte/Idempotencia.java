package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Identidad de un COMANDO monetario, que no es lo mismo que su contenido.
 *
 * <p>Deduplicar por {@code (tipo, monto, moneda, fecha)} seria perder dinero:
 * dos abonos de 300 el mismo dia son perfectamente legitimos y el servidor no
 * tiene forma de saber si el segundo es un abono nuevo o el reintento del
 * primero. <b>Solo el cliente lo sabe</b>, y lo dice con una clave explicita:
 * un UUID por operacion, reenviado igual en cada reintento de ESA operacion.
 *
 * <p>La huella no identifica el comando —eso lo hace la clave—: sirve para
 * distinguir un reintento honesto de una clave reutilizada para otra cosa. Si
 * llega la misma clave con otra huella, la respuesta es 409 en vez de un exito
 * que devolveria el resultado de una operacion distinta.
 */
public final class Idempotencia {

    /** Cabe en `comision_movimiento.clave_idempotencia`, que es VARCHAR(64). */
    private static final int LARGO_MAXIMO = 64;

    private Idempotencia() { }

    /**
     * {@code null} si no viaja cabecera —sigue siendo opcional mientras el
     * contrato legado este congelado—; si viaja, tiene que ser utilizable.
     */
    public static String normalizar(String clave) {
        if (clave == null || clave.isBlank()) {
            return null;
        }
        String limpia = clave.trim();
        if (limpia.length() > LARGO_MAXIMO) {
            throw new ReglaNegocioException(
                    "La clave de idempotencia no puede superar " + LARGO_MAXIMO + " caracteres.");
        }
        return limpia;
    }

    /**
     * SHA-256 de los campos que definen el comando. El separador va explicito
     * para que dos comandos distintos no puedan producir la misma cadena por
     * concatenacion ambigua.
     */
    public static String huella(Object... partes) {
        String texto = Stream.of(partes)
                .map(p -> p == null ? "" : p.toString().trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.joining(""));
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, el entorno esta roto.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
