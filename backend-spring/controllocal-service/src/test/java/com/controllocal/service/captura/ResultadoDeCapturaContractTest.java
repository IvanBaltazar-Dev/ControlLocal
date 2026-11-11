package com.controllocal.service.captura;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResultadoDeCapturaContractTest {
    @Test void productorYConsumidorCompartenElMismoResultadoSerializado() throws Exception {
        Path raiz=Path.of("").toAbsolutePath();
        while(raiz!=null && !Files.isDirectory(raiz.resolve("backend-spring")))raiz=raiz.getParent();
        assertNotNull(raiz);
        var json=new ObjectMapper();
        var actual=new MotorDeCaptura.ResultadoConsultado(MotorDeCaptura.EstadoDelResultado.EXITO,
                new MotorDeCaptura.Ejecucion(7L,19L,"PROP-19",List.of(),true));
        var esperado=Files.readString(raiz.resolve("backend-spring/controllocal-app/src/test/resources/contrato-resultado-captura-v1.json"));
        assertEquals(json.readTree(esperado),json.readTree(json.writeValueAsString(actual)));
    }

    @Test void noLocalizableConservaIncertidumbreYSinResultadoInventado() throws Exception {
        var json = new ObjectMapper();
        var resultado = new MotorDeCaptura.ResultadoConsultado(MotorDeCaptura.EstadoDelResultado.NO_LOCALIZABLE, null);
        var contrato = json.readTree(json.writeValueAsString(resultado));
        assertEquals("NO_LOCALIZABLE", contrato.get("estado").asText());
        assertTrue(contrato.get("resultado").isNull());
        assertEquals(resultado, json.readValue(json.writeValueAsString(resultado), MotorDeCaptura.ResultadoConsultado.class));
    }
}
