package com.controllocal.service.soporte;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que de verdad hay que fijar de TOTP no es que genere seis digitos —eso lo
 * hace cualquier implementacion— sino <b>que pasos admite</b> (D-S0-36) y que
 * devuelva CUAL caso, porque el llamador tiene que sellarlo (D-S0-31).
 */
class TotpTest {

    private final byte[] secreto = Totp.secretoNuevo();
    private final Instant ahora = Instant.parse("2026-08-06T10:00:00Z");

    @Test
    void elSecretoTieneLos160BitsDeSha1() {
        assertEquals(20, Totp.secretoNuevo().length);
    }

    @Test
    @DisplayName("el codigo del paso actual vale y dice con que paso caso")
    void elPasoActualVale() {
        long paso = Totp.pasoDe(ahora);
        var validacion = Totp.validar(secreto, Totp.generar(secreto, paso), ahora);

        assertTrue(validacion.valido());
        assertEquals(paso, validacion.paso(),
                "el paso viaja en el resultado porque hay que SELLARLO: "
                        + "validar sin sellar deja el codigo reutilizable");
    }

    @Test
    @DisplayName("el paso anterior vale: el cliente atrasado es el caso real")
    void elPasoAnteriorVale() {
        long paso = Totp.pasoDe(ahora) - 1;
        var validacion = Totp.validar(secreto, Totp.generar(secreto, paso), ahora);

        assertTrue(validacion.valido());
        assertEquals(paso, validacion.paso());
    }

    @Test
    @DisplayName("el paso FUTURO no vale (D-S0-36)")
    void elPasoFuturoNoVale() {
        // Admitirlo dejaria usar un codigo antes de su ventana natural y, al
        // sellarlo como ultimo_paso, tumbaria el codigo actual y el siguiente:
        // el usuario se quedaria fuera hasta un minuto por haber acertado.
        String futuro = Totp.generar(secreto, Totp.pasoDe(ahora) + 1);

        assertFalse(Totp.validar(secreto, futuro, ahora).valido());
    }

    @Test
    @DisplayName("dos pasos atras ya no vale: la ventana es de 60 s, no de 90")
    void dosPasosAtrasNoVale() {
        String viejo = Totp.generar(secreto, Totp.pasoDe(ahora) - 2);

        assertFalse(Totp.validar(secreto, viejo, ahora).valido());
    }

    @Test
    void unCodigoDeOtroSecretoNoVale() {
        String ajeno = Totp.generar(Totp.secretoNuevo(), Totp.pasoDe(ahora));

        assertFalse(Totp.validar(secreto, ajeno, ahora).valido());
    }

    @Test
    void loQueNoEsSeisDigitosSeRechazaSinCalcularNada() {
        assertFalse(Totp.validar(secreto, "12345", ahora).valido());
        assertFalse(Totp.validar(secreto, "1234567", ahora).valido());
        assertFalse(Totp.validar(secreto, "abcdef", ahora).valido());
        assertFalse(Totp.validar(secreto, null, ahora).valido());
    }

    @Test
    @DisplayName("los espacios se toleran: se copia del telefono a mano")
    void toleraEspacios() {
        String codigo = Totp.generar(secreto, Totp.pasoDe(ahora));
        String conEspacio = codigo.substring(0, 3) + " " + codigo.substring(3);

        assertTrue(Totp.validar(secreto, conEspacio, ahora).valido());
    }

    @Test
    @DisplayName("la URI lleva lo que el autenticador necesita y nada mas")
    void laUriEsInteroperable() {
        String uri = Totp.uri("ControlLocal", "admin@controllocal.test", secreto);

        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("algorithm=SHA1"), uri);
        assertTrue(uri.contains("digits=6"), uri);
        assertTrue(uri.contains("period=30"), uri);
        assertTrue(uri.contains("secret=" + Base32.codificar(secreto)), uri);
    }
}
