package com.controllocal.service.soporte;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Hash de contrasenas con PBKDF2-HMAC-SHA256.
 * Formato almacenado: pbkdf2$&lt;iteraciones&gt;$&lt;salBase64&gt;$&lt;hashBase64&gt;
 * MISMO formato que el PasswordHasher del backend Jakarta: los hashes de la
 * BD v1 se backfillean a la v2 sin re-hashear (los usuarios conservan su clave).
 * Nunca compara texto plano: un hash con formato desconocido simplemente no valida.
 */
public final class PasswordHasher {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACIONES = 100_000;
    private static final int LARGO_SAL = 16;
    private static final int LARGO_HASH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(char[] password) {
        byte[] sal = new byte[LARGO_SAL];
        RANDOM.nextBytes(sal);
        byte[] hash = derivar(password, sal, ITERACIONES);
        return "pbkdf2$" + ITERACIONES
                + "$" + Base64.getEncoder().encodeToString(sal)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verificar(char[] password, String almacenado) {
        if (password == null || almacenado == null || !almacenado.startsWith("pbkdf2$")) {
            return false;
        }
        String[] partes = almacenado.split("\\$");
        if (partes.length != 4) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] calculado = derivar(password, sal, iteraciones);
            return MessageDigest.isEqual(esperado, calculado);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(char[] password, byte[] sal, int iteraciones) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, sal, iteraciones, LARGO_HASH * 8);
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (java.security.NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("No se pudo derivar el hash de contrasena.", e);
        }
    }
}
