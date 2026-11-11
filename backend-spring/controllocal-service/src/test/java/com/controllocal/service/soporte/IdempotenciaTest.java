package com.controllocal.service.soporte;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdempotenciaTest {
    @Test void huellaExactaRespetaElVectorSha256YElContenidoOriginal() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Idempotencia.huellaExacta("abc"));
        assertNotEquals(Idempotencia.huellaExacta("abc"), Idempotencia.huellaExacta("ABC"));
        assertNotEquals(Idempotencia.huellaExacta("abc"), Idempotencia.huellaExacta(" abc "));
    }

    @Test void comandoHistoricoConservaSuNormalizacionYSeparacionDePartes() {
        assertEquals(Idempotencia.huella("usd", " 100 "), Idempotencia.huella("USD", "100"));
        assertNotEquals(Idempotencia.huella("AB", "C"), Idempotencia.huella("A", "BC"));
    }
}
