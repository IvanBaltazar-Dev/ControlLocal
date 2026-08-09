package com.controllocal.service.soporte;

/**
 * Base32 en dos alfabetos, porque sirven para cosas distintas:
 *
 * <ul>
 *   <li><b>RFC 4648</b> — el que entienden las aplicaciones autenticadoras;
 *       se usa para el secreto TOTP del QR.</li>
 *   <li><b>Crockford</b> — sin {@code I}, {@code L}, {@code O} ni {@code U},
 *       para los codigos de respaldo. Se leen de un papel y se teclean a
 *       mano: confundir un 1 con una I es el fallo esperable, no el raro.</li>
 * </ul>
 *
 * <p>Ninguno lleva relleno: aqui nadie concatena cadenas codificadas.
 */
public final class Base32 {

    private static final String RFC4648 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private Base32() {
    }

    public static String codificar(byte[] datos) {
        return codificar(datos, RFC4648);
    }

    /** Crockford: para lo que un humano teclea. */
    public static String codificarLegible(byte[] datos) {
        return codificar(datos, CROCKFORD);
    }

    /**
     * Normaliza lo que teclea una persona: quita separadores, sube a
     * mayusculas y traduce las confusiones tipicas del alfabeto Crockford.
     */
    public static String normalizarLegible(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replaceAll("[\\s-]", "")
                .toUpperCase(java.util.Locale.ROOT)
                .replace('I', '1').replace('L', '1')
                .replace('O', '0')
                .replace('U', 'V');
    }

    private static String codificar(byte[] datos, String alfabeto) {
        StringBuilder salida = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte dato : datos) {
            buffer = (buffer << 8) | (dato & 0xFF);
            bits += 8;
            while (bits >= 5) {
                salida.append(alfabeto.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            salida.append(alfabeto.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return salida.toString();
    }
}
