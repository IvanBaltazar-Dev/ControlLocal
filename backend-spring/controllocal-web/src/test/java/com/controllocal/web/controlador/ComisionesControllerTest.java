package com.controllocal.web.controlador;

import com.controllocal.service.ComisionService;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.web.http.ManejadorErroresApi;
import com.controllocal.web.seguridad.ProcedenciaDeCabeceras;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prueba del binding MVC real; no demuestra por sí sola seguridad HTTP ni efectos SQL. */
class ComisionesControllerTest {
    private final ComisionService core=mock(ComisionService.class);
    private final ActuacionesService actuaciones=mock(ActuacionesService.class);
    private final ProcedenciaDeCabeceras procedencias=mock(ProcedenciaDeCabeceras.class);
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private MockMvc mvc;
    @BeforeEach void iniciar(){mvc=MockMvcBuilders.standaloneSetup(new ComisionesController(core,actuaciones,procedencias))
            .setControllerAdvice(new ManejadorErroresApi())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();}
    static Stream<Arguments> tiposRetirados(){return Stream.of("\"P\"","\"R\"","\"C\"","null")
            .flatMap(tipo->Stream.of(true,false).map(primero->Arguments.of(tipo,primero)));}
    @ParameterizedTest @MethodSource("tiposRetirados")
    void mvcRechazaTipoAntesYDespuesDeCamposConocidos(String tipo,boolean primero)throws Exception{
        String datos="\"comision\":7,\"versionEsperada\":2,\"monto\":5000,\"moneda\":\"PEN\",\"fecha\":\"2026-10-31\"";
        String selector="\"tipo\":"+tipo;
        mvc.perform(post("/comisiones/movimientos/propuestas").contentType(MediaType.APPLICATION_JSON)
                .content("{"+(primero?selector+","+datos:datos+","+selector)+"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(core,actuaciones,procedencias);
    }
    @ParameterizedTest @CsvSource({"seguimientos,monto","conclusiones,concluida"})
    void mvcNoDescartaCamposDeOtroEfectoEnLaGestion(String ruta,String extra)throws Exception{
        String datos="{\"comision\":7,\"versionEsperada\":2,\"motivo\":\"Contacto acordado\",\""+extra+"\":null}";
        mvc.perform(post("/comisiones/"+ruta+"/propuestas").contentType(MediaType.APPLICATION_JSON).content(datos))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(core,actuaciones,procedencias);
    }
    @Test void payloadEnfocadoConservaImporteMonedaYFechaSinSelector()throws Exception{
        var datos=json.readValue("{\"comision\":7,\"versionEsperada\":2,\"monto\":5000,\"moneda\":\"PEN\",\"fecha\":\"2026-10-31\"}",ComisionService.DatosCobro.class);
        assertEquals("5000",datos.monto().toPlainString());assertEquals("PEN",datos.moneda());
        assertEquals(java.time.LocalDate.of(2026,10,31),datos.fecha());verifyNoInteractions(core,actuaciones);
    }
}
