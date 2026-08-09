package com.controllocal.service.soporte;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cifrado reversible de los secretos TOTP (D-S0-33).
 *
 * <p><b>Por que no es hash.</b> Un TOTP hay que poder <i>recalcularlo</i>, asi
 * que su secreto tiene que volver en claro. Esa es la diferencia con una
 * contrasena, y la razon por la que la clave <b>no puede vivir en la base</b>:
 * si vive ahi, cifrar no protege de nada — quien lea la tabla ya tiene las dos
 * mitades.
 *
 * <p><b>Versionado y rotacion.</b> Cada fila guarda con que version se cifro.
 * Durante una rotacion conviven la clave <b>actual</b> (la que cifra) y la
 * <b>anterior</b> (que solo descifra), asi que rotar no obliga a reescribir
 * todas las filas en la misma ventana. Sin version, rotar seria un corte.
 *
 * <p><b>Perder la clave es un fallo de DISPONIBILIDAD</b>, no de
 * confidencialidad: deja a todos los administradores sin segundo factor. Por
 * eso su respaldo va cifrado y separado del dump —un respaldo que lleva la
 * base y su clave no esta cifrado, esta acompanado— y la prueba de
 * restauracion incluye descifrar un factor conocido.
 *
 * <p>Formato: AES-256-GCM, nonce de 12 bytes y tag de 128 bits <b>integrado en
 * el criptograma</b>, que es como lo administra el proveedor de Java. No se
 * separa a mano.
 */
@Component
public class CifradoSecretos {

    /** Clave fija de desarrollo. Igual que el secreto del token: solo vale en dev. */
    static final String CLAVE_DEV = "ControlLocal-dev-mfa-key-0001-not-for-prod";

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int LARGO_NONCE = 12;
    private static final int LARGO_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** version -> clave. La mayor es la vigente; las demas solo descifran. */
    private final Map<Short, SecretKeySpec> claves = new LinkedHashMap<>();
    private final short versionVigente;
    private final boolean usandoFallback;

    public CifradoSecretos(@Value("${controllocal.mfa.clave:}") String actual,
                           @Value("${controllocal.mfa.clave-anterior:}") String anterior,
                           @Value("${controllocal.mfa.version-clave:1}") short version) {
        boolean valida = esUtilizable(actual);
        this.usandoFallback = !valida;
        this.versionVigente = version;
        this.claves.put(version, derivar(valida ? actual : CLAVE_DEV));
        if (esUtilizable(anterior) && version > 1) {
            this.claves.put((short) (version - 1), derivar(anterior));
        }
    }

    /**
     * true si esta instancia cifra con la clave fija de desarrollo. Lo consulta
     * el validador de arranque: en {@code prod} detiene el contexto, en dev
     * emite un WARN. La clave no sale de aqui.
     */
    public boolean usandoFallbackDeDesarrollo() {
        return usandoFallback;
    }

    public short versionVigente() {
        return versionVigente;
    }

    public static boolean esFallbackDeDesarrollo(String candidato) {
        return candidato != null && MessageDigest.isEqual(
                candidato.getBytes(StandardCharsets.UTF_8),
                CLAVE_DEV.getBytes(StandardCharsets.UTF_8));
    }

    /** Criptograma + nonce; el tag va dentro del criptograma. */
    public record Cifrado(byte[] criptograma, byte[] nonce, short version) {
    }

    public Cifrado cifrar(byte[] claro) {
        byte[] nonce = new byte[LARGO_NONCE];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, claves.get(versionVigente),
                    new GCMParameterSpec(LARGO_TAG_BITS, nonce));
            return new Cifrado(cipher.doFinal(claro), nonce, versionVigente);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el secreto del factor.", e);
        }
    }

    public byte[] descifrar(byte[] criptograma, byte[] nonce, short version) {
        SecretKeySpec clave = claves.get(version);
        if (clave == null) {
            // Que se diga con todas las letras: sin la clave de esa version el
            // factor es irrecuperable y hay que revocarlo y reenrolar. Callarlo
            // convertiria un problema de operacion en un misterio de login.
            throw new IllegalStateException(
                    "No hay clave de cifrado para la version " + version
                            + ": el factor no se puede descifrar. Revise la rotacion "
                            + "de controllocal.mfa.clave y su respaldo.");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(LARGO_TAG_BITS, nonce));
            return cipher.doFinal(criptograma);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el secreto del factor.", e);
        }
    }

    private static boolean esUtilizable(String valor) {
        return valor != null && valor.length() >= 32 && !esFallbackDeDesarrollo(valor);
    }

    /**
     * SHA-256 de la clave configurada: acepta cualquier longitud y produce los
     * 256 bits que pide AES-256. La clave configurada NO es una contrasena de
     * usuario —se genera con {@code openssl rand}—, asi que aqui no hace falta
     * derivacion lenta.
     */
    private static SecretKeySpec derivar(String configurada) {
        try {
            byte[] material = configurada.startsWith("base64:")
                    ? Base64.getDecoder().decode(configurada.substring(7))
                    : configurada.getBytes(StandardCharsets.UTF_8);
            return new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256").digest(material), "AES");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.", e);
        }
    }
}
