package com.controllocal.web.controlador;

import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.web.config.ConfiguracionJson;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Sólo transporte: servicios controlados; permisos, revisión y transacciones se prueban en App/PG. */
class ActividadContratoHttpTest {
    private final ActuacionesService actuaciones=mock(ActuacionesService.class);
    private final OfertaService ofertas=mock(OfertaService.class);
    private final ProcedenciaDeCabeceras fuentes=mock(ProcedenciaDeCabeceras.class);
    private final org.springframework.test.web.servlet.MockMvc http=http();
    private org.springframework.test.web.servlet.MockMvc http() {
        var constructor=new Jackson2ObjectMapperBuilder();
        new ConfiguracionJson().enterosSinTruncar().customize(constructor);
        return MockMvcBuilders.standaloneSetup(new OfertasController(ofertas,actuaciones,fuentes))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(constructor.build())).build();
    }
    private String propuesta() {
        return """
                {"oportunidad":10,"versionOportunidad":0,"versionMesa":0,
                 "proponente":{"lado":"I","persona":23},
                 "importe":{"importe":95000.05,"moneda":"USD","base":"TOTAL"},
                 "objeto":{"tipo":"UNIDAD","encargo":9,"versionEncargo":0,"propiedad":6,"versionFisica":0},
                 "fechaHecho":"2026-10-30","evidencia":"Manifestación declarada"}
                """;
    }
    @Test void idsYVersionesFraccionariosSeRechazanAntesDePreparar()throws Exception {
        for(String cuerpo:java.util.List.of(propuesta().replace("\"oportunidad\":10","\"oportunidad\":10.5"),
                propuesta().replace("\"versionMesa\":0","\"versionMesa\":0.9")))
            http.perform(post("/oportunidades/10/ofertas/propuestas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(actuaciones,ofertas,fuentes);
    }
    @Test void propuestaConservaCentimosMonedaFechaCivilYVersionCeroExplicita()throws Exception {
        Actor actor=new Actor(1,10,30,Actor.AGENTE);
        var contexto=new ContextosService.Contexto("ctx-http-c4",1,10,30,Actor.AGENTE,"EMPRESARIAL","Agencia TEST");
        var acceso=new ContextosService.SesionAccesoDTO("sid-http-c4",1,
                new ContextosService.IdentidadAutenticada(100,"fixture","fixture@example.test","Fixture"),contexto,
                Instant.parse("2026-10-30T00:00:00Z"),Instant.parse("2026-10-31T00:00:00Z"),EstadoDeAcceso.SIN_RESTRICCIONES);
        try(var sesion=mockStatic(SesionActual.class)) {
            sesion.when(SesionActual::actor).thenReturn(actor);
            sesion.when(SesionActual::sesion).thenReturn(new SesionDeRequest(new TokenService("").de(acceso),acceso));
            http.perform(post("/oportunidades/10/ofertas/propuestas").contentType(MediaType.APPLICATION_JSON).content(propuesta()))
                    .andExpect(status().isOk());
        }
        var datos=ArgumentCaptor.forClass(OfertaService.DatosRegistroOferta.class);
        verify(actuaciones).prepararRegistroOferta(eq(actor),datos.capture(),any(),isNull());
        assertEquals(new BigDecimal("95000.05"),datos.getValue().importe().importe());
        assertEquals("USD",datos.getValue().importe().moneda());
        assertEquals(0L,datos.getValue().versionMesa());
        assertEquals(java.time.LocalDate.of(2026,10,30),datos.getValue().fechaHecho());
        verifyNoInteractions(ofertas);
    }
    @Test void versionOmitidaConservaAusenciaEnElDTOEnLugarDeConvertirseEnCero()throws Exception {
        var constructor=new Jackson2ObjectMapperBuilder();new ConfiguracionJson().enterosSinTruncar().customize(constructor);
        var datos=constructor.build().readValue(propuesta().replace("\"versionMesa\":0,",""),OfertaService.DatosRegistroOferta.class);
        assertNull(datos.versionMesa());
    }
    @Test void consultaHttpSinCorteConservaLaOpcionYaAprobadaEnCore()throws Exception {
        var core=mock(OportunidadService.class);var actor=new Actor(1,10,30,Actor.AGENTE);
        when(core.consultar(10,OportunidadService.Seccion.RESUMEN,null,actor))
                .thenReturn(new OportunidadService.ConsultaOportunidad(null,OportunidadService.Seccion.RESUMEN,
                        Instant.parse("2026-10-30T12:00:00Z"),null,null,null,null));
        var lectura=MockMvcBuilders.standaloneSetup(new OportunidadesController(core,actuaciones,fuentes))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new Jackson2ObjectMapperBuilder().build())).build();
        try(var sesion=mockStatic(SesionActual.class)) {
            sesion.when(SesionActual::actor).thenReturn(actor);
            lectura.perform(get("/oportunidades/10/secciones/RESUMEN")).andExpect(status().isOk());
        }
        verify(core).consultar(10,OportunidadService.Seccion.RESUMEN,null,actor);
    }

    @Test void rutasAntiguasDeEfectoDirectoNoSiguenPublicadas()throws Exception {
        http.perform(post("/oportunidades/10/ofertas").contentType(MediaType.APPLICATION_JSON).content(propuesta()))
                .andExpect(status().isMethodNotAllowed());
        http.perform(post("/oportunidades/10/ofertas/3/aceptar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(actuaciones,ofertas,fuentes);
    }
}
