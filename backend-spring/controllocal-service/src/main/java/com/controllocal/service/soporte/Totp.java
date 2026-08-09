package com.controllocal.service.soporte;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * TOTP (RFC 6238) con los parametros de D-S0-36.
 *
 * <p><b>HMAC-SHA1, 6 digitos y 30 s</b> por interoperabilidad: el RFC admite
 * SHA-1, SHA-256 y SHA-512, pero SHA-1 es lo que implementan todas las
 * aplicaciones autenticadoras. <b>No es una debilidad practica aqui</b>: el
 * riesgo real de TOTP es el phishing, no la colision de hash — y por eso el
 * diseno deja WebAuthn como evolucion, no como capricho.
 *
 * <p><b>Solo se admiten el paso actual y el anterior. El siguiente NO.</b>
 * Aceptar {@code t+1} dejaria usar un codigo antes de su ventana natural y,
 * al sellarlo como {@code ultimo_paso} del anti-replay, tumbaria el codigo
 * actual y el siguiente: el usuario se quedaria fuera hasta un minuto por
 * haber acertado. Con relojes sincronizados la deriva que importa es la del
 * cliente atrasado, no la del adelantado.
 */
public final class Totp {

    public static final String ALGORITMO = "HmacSHA1";
    public static final int DIGITOS = 6;
    public static final int PERIODO_SEGUNDOS = 30;

    /** 160 bits: el tamano de bloque de SHA-1. Menos desperdicia, mas no aporta. */
    public static final int LARGO_SECRETO = 20;

    /** Pasos hacia atras admitidos. El futuro no se admite (D-S0-36). */
    private static final int PASOS_ATRAS = 1;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int[] POTENCIAS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000};

    private Totp() {
    }

    /** Resultado de validar: si el codigo vale y CON QUE paso, que es lo que se sella. */
    public record Validacion(boolean valido, long paso) {
        public static final Validacion INVALIDA = new Validacion(false, 0L);
    }

    public static byte[] secretoNuevo() {
        byte[] secreto = new byte[LARGO_SECRETO];
        RANDOM.nextBytes(secreto);
        return secreto;
    }

    public static long pasoDe(Instant instante) {
        return instante.getEpochSecond() / PERIODO_SEGUNDOS;
    }

    /**
     * ¿Vale este codigo en este instante?
     *
     * <p>Devuelve el paso con el que caso, porque el llamador tiene que
     * sellarlo: validar y consumir son la misma operacion (D-S0-31), y quien
     * valide sin sellar deja el codigo reutilizable durante su ventana.
     *
     * <p>El paso mas reciente se prueba primero para que el caso normal selle
     * el valor mas alto posible.
     */
    public static Validacion validar(byte[] secreto, String codigo, Instant ahora) {
        if (secreto == null || codigo == null) {
            return Validacion.INVALIDA;
        }
        String limpio = codigo.replaceAll("\\s", "");
        if (limpio.length() != DIGITOS || !limpio.chars().allMatch(Character::isDigit)) {
            return Validacion.INVALIDA;
        }
        long paso = pasoDe(ahora);
        for (int atras = 0; atras <= PASOS_ATRAS; atras++) {
            long candidato = paso - atras;
            // Comparacion en tiempo constante: comparar codigos con equals
            // filtra por cuantos digitos coinciden.
            if (MessageDigest.isEqual(generar(secreto, candidato).getBytes(),
                    limpio.getBytes())) {
                return new Validacion(true, candidato);
            }
        }
        return Validacion.INVALIDA;
    }

    public static String generar(byte[] secreto, long paso) {
        byte[] hmac = hmac(secreto, ByteBuffer.allocate(8).putLong(paso).array());
        int desplazamiento = hmac[hmac.length - 1] & 0x0F;
        int binario = ((hmac[desplazamiento] & 0x7F) << 24)
                | ((hmac[desplazamiento + 1] & 0xFF) << 16)
                | ((hmac[desplazamiento + 2] & 0xFF) << 8)
                | (hmac[desplazamiento + 3] & 0xFF);
        return String.format("%0" + DIGITOS + "d", binario % POTENCIAS[DIGITOS]);
    }

    /**
     * URI {@code otpauth://} para el QR. El secreto viaja aqui — es la unica
     * vez que sale del servidor, y por eso la respuesta que lo lleva va con
     * {@code Cache-Control: no-store}.
     */
    public static String uri(String emisor, String cuenta, byte[] secreto) {
        String etiqueta = urlEncode(emisor) + ":" + urlEncode(cuenta);
        return "otpauth://totp/" + etiqueta
                + "?secret=" + Base32.codificar(secreto)
                + "&issuer=" + urlEncode(emisor)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PERIODO_SEGUNDOS;
    }

    private static String urlEncode(String valor) {
        return java.net.URLEncoder.encode(valor, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static byte[] hmac(byte[] secreto, byte[] datos) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto, ALGORITMO));
            return mac.doFinal(datos);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("No se pudo calcular el TOTP.", e);
        }
    }
}
