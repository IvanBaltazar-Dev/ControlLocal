package com.controllocal.web.seguridad;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El formato del token es parte del contrato compartido con el backend
 * Jakarta (SSO del Strangler): estas pruebas fijan firma, claims y rechazo.
 */
class TokenServiceTest {

    /** Sin secreto configurado usa el fallback dev compartido con el backend viejo. */
    private final TokenService tokens = new TokenService("");

    @Test
    void emiteYValidaIdaYVuelta() {
        TokenService.Sesion emitida = tokens.emitir("vmora", "AGENTE", 7, 101);
        String jwt = tokens.firmar(emitida);

        Optional<TokenService.Sesion> validada = tokens.validar(jwt);
        assertTrue(validada.isPresent());
        assertEquals("vmora", validada.get().usuario());
        assertEquals("AGENTE", validada.get().rol());
        assertEquals(7, validada.get().idUsuario());
        assertEquals(101, validada.get().idDominio());
    }

    @Test
    void rechazaTokenManipulado() {
        String jwt = tokens.firmar(tokens.emitir("rsalas", "BROKER", 2, 8));
        String manipulado = jwt.substring(0, jwt.length() - 2) + "xx";
        assertTrue(tokens.validar(manipulado).isEmpty());
    }

    @Test
    void rechazaFirmaDeOtroSecreto() {
        TokenService otro = new TokenService("un-secreto-distinto-de-al-menos-32-caracteres");
        String jwt = otro.firmar(otro.emitir("admin", "ADMIN", 1, 1));
        assertTrue(tokens.validar(jwt).isEmpty());
    }

    @Test
    void rechazaBasura() {
        assertTrue(tokens.validar(null).isEmpty());
        assertTrue(tokens.validar("").isEmpty());
        assertTrue(tokens.validar("a.b").isEmpty());
        assertTrue(tokens.validar("a.b.c").isEmpty());
    }

    @Test
    void noEmiteSesionesInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> tokens.emitir("", "AGENTE", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> tokens.emitir("x", "OTRO", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> tokens.emitir("x", "AGENTE", 0, 1));
        assertFalse(TokenService.DURACION_SEGUNDOS <= 0);
    }

    // ------------------------------------------------------------------
    // D-S0-12: `iat` expuesto, que es lo que permite invalidar sesiones
    // ------------------------------------------------------------------

    @Test
    void elIatViajaEnElTokenYVuelveEnLaSesion() {
        TokenService.Sesion emitida = tokens.emitir("vmora", "AGENTE", 7, 101);

        TokenService.Sesion validada = tokens.validar(tokens.firmar(emitida)).orElseThrow();

        // Ida y vuelta exacto: el filtro compara este instante contra
        // sesiones_invalidas_desde, asi que un desfase seria una revocacion
        // que no revoca o un token vivo que muere sin motivo.
        assertEquals(emitida.emitidoEn(), validada.emitidoEn());
    }

    @Test
    void elIatFirmadoEsElDeLaSesionYNoUnInstanteNuevo() {
        TokenService.Sesion emitida = tokens.emitir("vmora", "AGENTE", 7, 101);

        String uno = tokens.firmar(emitida);
        String otro = tokens.firmar(emitida);

        // Firmar dos veces la MISMA sesion da el mismo token. Si `firmar`
        // volviera a leer el reloj, dos firmas separadas por un segundo
        // producirian tokens distintos con `iat` distintos.
        assertEquals(uno, otro);
    }

    @Test
    void elIatTienePrecisionDeSegundo() {
        TokenService.Sesion emitida = tokens.emitir("vmora", "AGENTE", 7, 101);

        // El claim viaja en segundos epoch; guardar mas resolucion en el record
        // haria que el objeto y el JWT firmado dijeran cosas distintas.
        assertEquals(0, emitida.emitidoEn().getNano());
        assertEquals(TokenService.DURACION_SEGUNDOS,
                emitida.expiraEn().getEpochSecond() - emitida.emitidoEn().getEpochSecond());
    }

    @Test
    void unTokenSinIatSeLeeComoEmitidoEnEpoch() {
        // No se exige `iat` para no romper el SSO si el otro backend lo omitiera.
        // Ausente = EPOCH, que es "emitido hace siempre": cualquier
        // invalidacion posterior lo mata. Falla del lado seguro.
        TokenService.Sesion emitida = tokens.emitir("vmora", "AGENTE", 7, 101);
        String sinIat = firmarSin("iat", emitida);

        TokenService.Sesion validada = tokens.validar(sinIat).orElseThrow();

        assertEquals(java.time.Instant.EPOCH, validada.emitidoEn());
    }

    /** Reconstruye el token quitando un claim, para probar el borde del SSO. */
    private String firmarSin(String claim, TokenService.Sesion sesion) {
        String jwt = tokens.firmar(sesion);
        String[] partes = jwt.split("\\.", -1);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            var carga = (com.fasterxml.jackson.databind.node.ObjectNode)
                    json.readTree(java.util.Base64.getUrlDecoder().decode(partes[1]));
            carga.remove(claim);
            String cargaNueva = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(carga.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // Hay que refirmar: cambiar la carga invalida la firma anterior.
            java.lang.reflect.Method firma = TokenService.class.getDeclaredMethod("firma", String.class);
            firma.setAccessible(true);
            String contenido = partes[0] + "." + cargaNueva;
            return contenido + "." + firma.invoke(tokens, contenido);
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("No se pudo preparar el fixture", error);
        }
    }
}
