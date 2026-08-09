package com.controllocal.service.soporte;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Codigos de respaldo del segundo factor (D-S0-24).
 *
 * <pre>
 *   IDENT-SSSS-SSSS-SSSS-SSSS
 *   \___/ \_________________/
 *     |            |
 *     |            +-- 16 caracteres Base32 Crockford = 80 bits aleatorios
 *     +--------------- identificador PUBLICO, unico por factor
 * </pre>
 *
 * <p><b>El identificador es lo que hace viable el hash lento.</b> Sin el habria
 * que probar el codigo tecleado contra las 8 filas —ocho derivaciones PBKDF2
 * por intento, casi un segundo de CPU y una palanca de denegacion de servicio
 * regalada—, y ese era el argumento con el que la primera version del diseno
 * justificaba un SHA-256 simple. Con identificador se verifica <b>una sola
 * fila</b>, asi que se paga el hash lento sin coste practico.
 *
 * <p>El identificador es publico a proposito: no es un secreto, es un indice.
 * Lo que protege el codigo son los 80 bits de la parte secreta.
 */
public final class CodigosRespaldo {

    /** Ocho: suficientes para varias perdidas, pocos para caber en un papel. */
    public static final int CANTIDAD = 8;

    private static final int BYTES_IDENTIFICADOR = 3;  // 24 bits -> 5 chars Crockford
    private static final int BYTES_SECRETO = 10;       // 80 bits -> 16 chars Crockford
    private static final int LARGO_IDENTIFICADOR = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodigosRespaldo() {
    }

    /**
     * Un codigo recien generado: lo que se le muestra al usuario UNA vez y lo
     * que se guarda (que no es lo mismo).
     */
    public record Generado(String identificador, String visible, String hash) {
    }

    public static List<Generado> generar() {
        List<Generado> codigos = new ArrayList<>(CANTIDAD);
        List<String> identificadores = new ArrayList<>(CANTIDAD);
        while (codigos.size() < CANTIDAD) {
            String identificador = aleatorio(BYTES_IDENTIFICADOR).substring(0, LARGO_IDENTIFICADOR);
            // Unico por factor: es lo que localiza UNA fila al verificar.
            if (identificadores.contains(identificador)) {
                continue;
            }
            identificadores.add(identificador);
            String secreto = aleatorio(BYTES_SECRETO).substring(0, 16);
            codigos.add(new Generado(identificador, formatear(identificador, secreto),
                    PasswordHasher.hash(secreto.toCharArray())));
        }
        return codigos;
    }

    /** Lo que teclea el usuario, partido en identificador y secreto. */
    public record Tecleado(String identificador, String secreto) {
        public boolean completo() {
            return identificador.length() == LARGO_IDENTIFICADOR && secreto.length() == 16;
        }
    }

    /**
     * Tolera guiones, espacios y minusculas, y traduce las confusiones del
     * alfabeto Crockford (I/L por 1, O por 0, U por V). Quien copia un codigo
     * de un papel se equivoca en eso, no en otra cosa.
     */
    public static Tecleado partir(String tecleado) {
        String limpio = Base32.normalizarLegible(tecleado);
        if (limpio.length() < LARGO_IDENTIFICADOR) {
            return new Tecleado("", "");
        }
        return new Tecleado(limpio.substring(0, LARGO_IDENTIFICADOR),
                limpio.substring(LARGO_IDENTIFICADOR));
    }

    private static String formatear(String identificador, String secreto) {
        return identificador + "-" + secreto.substring(0, 4)
                + "-" + secreto.substring(4, 8)
                + "-" + secreto.substring(8, 12)
                + "-" + secreto.substring(12, 16);
    }

    private static String aleatorio(int bytes) {
        byte[] material = new byte[bytes + 1];
        RANDOM.nextBytes(material);
        return Base32.codificarLegible(material);
    }
}
