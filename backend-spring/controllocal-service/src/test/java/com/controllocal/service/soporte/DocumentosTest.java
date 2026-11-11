package com.controllocal.service.soporte;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentosTest {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Documentos documentos=new Documentos(mapper);

    @Test void losMapHistoricosConservanBytesDecimalesYNulos() throws Exception {
        Map<String,Object> interior=new LinkedHashMap<>();interior.put("n",new BigDecimal("0.0300"));interior.put("k",null);
        Map<String,Object> entrada=new LinkedHashMap<>();entrada.put("z","Área construida");entrada.put("a",interior);
        String anterior=mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(entrada);
        assertEquals("{\"a\":{\"k\":null,\"n\":0.0300},\"z\":\"Área construida\"}",anterior);
        assertEquals(anterior,documentos.objetoCanonico(entrada));
        assertEquals(Idempotencia.huellaExacta(anterior),Idempotencia.huellaExacta(documentos.objetoCanonico(entrada)));
        assertEquals("null",documentos.objetoCanonico(null));
    }
    record Registro(String z,BigDecimal importe,Instant fecha,Map<String,Object> interior,String ausente) { }
    @Test void recordYReciboTipadoTienenLaMismaRepresentacionCanonica() {
        var r=new Registro("Oferta",new BigDecimal("2500.00"),Instant.parse("2026-10-28T12:00:00Z"),
                Map.of("z",1,"a",2),null);
        String primero=documentos.objetoCanonico(r);
        assertTrue(primero.startsWith("{\"ausente\":null,\"fecha\":"));
        assertEquals(primero,documentos.objetoCanonico(documentos.comoObjeto(primero,Registro.class)));
        assertTrue(primero.contains("\"importe\":2500.00"));
    }
    static class Roto { public String getDato(){throw new IllegalStateException("Fallo técnico de lectura");} }
    @Test void falloDeSerializacionNoSeConvierteEnObjetoVacio() {
        var fallo=assertThrows(IllegalStateException.class,()->documentos.objetoCanonico(new Roto()));
        assertNotNull(fallo.getCause());
    }
}
