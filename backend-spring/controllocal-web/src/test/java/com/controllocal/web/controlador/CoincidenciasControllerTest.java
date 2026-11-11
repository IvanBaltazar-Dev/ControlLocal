package com.controllocal.web.controlador;

import com.controllocal.service.*;
import com.controllocal.service.CoincidenciaService.*;
import com.controllocal.web.http.ManejadorErroresApi;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** Binding HTTP y transporte. La autorización y las reglas económicas corresponden a las pruebas Core. */
class CoincidenciasControllerTest {
    private final CoincidenciaService core=mock(CoincidenciaService.class);
    private MockMvc http;
    @BeforeEach void preparar() {
        var acceso=new ContextosService.SesionAccesoDTO("s".repeat(43),5,
                new ContextosService.IdentidadAutenticada(2,"cuenta-test","cuenta@example.invalid",null),
                new ContextosService.Contexto("ctx-test",7,30,41,Actor.AGENTE,"INDIVIDUAL","TEST"),
                Instant.now(),Instant.now().plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);
        var principal=new SesionDeRequest(new TokenService("").de(acceso),acceso);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        http=MockMvcBuilders.standaloneSetup(new CoincidenciasController(core)).setControllerAdvice(new ManejadorErroresApi()).build();
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
    @Test void omitirLosTresParametrosConservaLaConsultaOrdinaria() throws Exception {
        resultadoVacio();http.perform(consulta()).andExpect(status().isOk());
        var enviada=capturar();assertNull(enviada.exploracion());assertEquals(Set.of(Capa.L0),enviada.capas());
    }
    @Test void descubrirTransportaElModoSinInventarLimiteOMoneda() throws Exception {
        resultadoVacio();http.perform(consulta().param("modoExploracion","DESCUBRIR")).andExpect(status().isOk());
        assertEquals(new ExploracionPrecio(ModoExploracion.DESCUBRIR,null,null),capturar().exploracion());
    }
    @Test void explorarTransportaExactamenteElLimiteDeclaradoYDejaLaReglaEconomicaEnCore() throws Exception {
        resultadoVacio();http.perform(consulta().param("modoExploracion","EXPLORAR").param("limiteExploracion","110000.50")
                .param("monedaExploracion","USD")).andExpect(status().isOk());
        assertEquals(new ExploracionPrecio(ModoExploracion.EXPLORAR,new BigDecimal("110000.50"),"USD"),capturar().exploracion());
    }
    @ParameterizedTest @ValueSource(strings={"limiteExploracion","monedaExploracion"})
    void unParametroAisladoNoSeIgnoraNiEligeElModo(String parametro) throws Exception {
        http.perform(consulta().param(parametro,parametro.startsWith("limite")?"110000":"USD")).andExpect(status().isBadRequest());
        verifyNoInteractions(core);
    }
    @Test void unModoDesconocidoNoSeConvierteEnConsultaOrdinaria() throws Exception {
        http.perform(consulta().param("modoExploracion","AUTOMATICO")).andExpect(status().isBadRequest());
        verifyNoInteractions(core);
    }
    @ParameterizedTest
    @ValueSource(strings={"origen","idOrigen","versionOrigen","tipoVersion","modalidad","capas","categoria"})
    void omitirUnParametroObligatorioEsPeticionInvalidaSinInvocarMatching(String parametro) throws Exception {
        var solicitud=consulta();
        solicitud.with(request->{request.removeParameter(parametro);return request;});
        http.perform(solicitud).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        verifyNoInteractions(core);
    }
    @Test void unaVersionVaciaNoSeInventaNiSeClasificaComoFalloTecnico() throws Exception {
        http.perform(consulta().with(request->{request.setParameter("versionOrigen", "");return request;}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        verifyNoInteractions(core);
    }
    private static MockHttpServletRequestBuilder consulta(){return get("/coincidencias").param("origen","DEMANDA")
            .param("idOrigen","47").param("versionOrigen","2").param("tipoVersion","DEMANDA")
            .param("modalidad","COMERCIAL").param("capas","L0").param("categoria","INCOMPATIBLE");}
    private void resultadoVacio(){when(core.consultar(any(),any(),any())).thenReturn(new Coincidencias(EstadoResultado.DISPONIBLE,null,
            Instant.parse("2026-10-30T17:00:00Z"),"D1-D4-R3","consulta",List.of(),0L,Cobertura.COMPLETA,Set.of(),null));}
    private Consulta capturar(){var enviada=ArgumentCaptor.forClass(Consulta.class);verify(core).consultar(enviada.capture(),any(),any());return enviada.getValue();}
}
