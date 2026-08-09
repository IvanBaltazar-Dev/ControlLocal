package com.controllocal.service.soporte;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que importa de esta clase no es que cifre —AES-GCM lo hace el JDK— sino
 * la <b>gestion de la clave</b> (D-S0-33): version por fila, convivencia de la
 * actual con la anterior durante una rotacion, y un fallo que se entienda
 * cuando falta la clave de una version.
 */
class CifradoSecretosTest {

    private static final String CLAVE_A = "una-clave-de-produccion-de-mas-de-32-caracteres-A";
    private static final String CLAVE_B = "otra-clave-de-produccion-de-mas-de-32-caracteres-B";

    private final byte[] secreto = "secreto-totp-de-prueba".getBytes(StandardCharsets.UTF_8);

    @Test
    void loQueCifraLoDescifra() {
        var cifrado = new CifradoSecretos(CLAVE_A, "", (short) 1);

        var guardado = cifrado.cifrar(secreto);
        assertArrayEquals(secreto,
                cifrado.descifrar(guardado.criptograma(), guardado.nonce(), guardado.version()));
    }

    @Test
    @DisplayName("cada cifrado usa un nonce nuevo")
    void elNonceNoSeRepite() {
        var cifrado = new CifradoSecretos(CLAVE_A, "", (short) 1);

        var uno = cifrado.cifrar(secreto);
        var otro = cifrado.cifrar(secreto);

        assertNotEquals(java.util.Arrays.toString(uno.nonce()),
                java.util.Arrays.toString(otro.nonce()));
        // Y por tanto el criptograma tampoco: repetir nonce en GCM es el fallo
        // clasico que filtra el texto en claro.
        assertNotEquals(java.util.Arrays.toString(uno.criptograma()),
                java.util.Arrays.toString(otro.criptograma()));
    }

    @Test
    @DisplayName("ROTACION: lo cifrado con la clave vieja sigue descifrando")
    void durantLaRotacionConvivenDosClaves() {
        // Se cifro con la version 1...
        var antes = new CifradoSecretos(CLAVE_A, "", (short) 1);
        var guardado = antes.cifrar(secreto);

        // ...y ahora la instancia cifra con la 2 y conserva la 1 solo para leer.
        var despues = new CifradoSecretos(CLAVE_B, CLAVE_A, (short) 2);

        assertEquals(2, despues.versionVigente(),
                "lo nuevo se cifra con la version nueva");
        assertArrayEquals(secreto,
                despues.descifrar(guardado.criptograma(), guardado.nonce(), (short) 1),
                "sin esto, rotar la clave seria un corte: ningun factor existente validaria");
        assertEquals(2, despues.cifrar(secreto).version());
    }

    @Test
    @DisplayName("sin la clave de esa version, el error DICE que falta")
    void sinLaClaveDeLaVersionElErrorSeEntiende() {
        var cifrado = new CifradoSecretos(CLAVE_A, "", (short) 1);
        var guardado = cifrado.cifrar(secreto);

        // Una instancia que ya no conserva la version 1.
        var soloNueva = new CifradoSecretos(CLAVE_B, "", (short) 3);

        var error = assertThrows(IllegalStateException.class, () ->
                soloNueva.descifrar(guardado.criptograma(), guardado.nonce(), (short) 1));

        assertTrue(error.getMessage().contains("version 1"), error.getMessage());
        assertTrue(error.getMessage().contains("respaldo"),
                "callarlo convertiria un problema de operacion en un misterio de login: "
                        + error.getMessage());
    }

    @Test
    @DisplayName("delata cuando esta usando la clave de desarrollo")
    void avisaDelFallbackDeDesarrollo() {
        assertTrue(new CifradoSecretos("", "", (short) 1).usandoFallbackDeDesarrollo());
        assertTrue(new CifradoSecretos("corta", "", (short) 1).usandoFallbackDeDesarrollo());
        assertTrue(new CifradoSecretos(CifradoSecretos.CLAVE_DEV, "", (short) 1)
                .usandoFallbackDeDesarrollo(),
                "copiar el literal de desarrollo en la variable no lo convierte en propio");
        assertFalse(new CifradoSecretos(CLAVE_A, "", (short) 1).usandoFallbackDeDesarrollo());
    }

    @Test
    @DisplayName("un criptograma manipulado no se descifra: GCM autentica")
    void detectaManipulacion() {
        var cifrado = new CifradoSecretos(CLAVE_A, "", (short) 1);
        var guardado = cifrado.cifrar(secreto);
        byte[] alterado = guardado.criptograma().clone();
        alterado[0] ^= 0x01;

        assertThrows(IllegalStateException.class, () ->
                cifrado.descifrar(alterado, guardado.nonce(), (short) 1));
    }
}
