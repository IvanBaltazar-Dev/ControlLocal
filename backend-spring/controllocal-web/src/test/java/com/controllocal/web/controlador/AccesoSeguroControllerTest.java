package com.controllocal.web.controlador;

import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.seguridad.InicioSeguroService;
import com.controllocal.service.seguridad.InicioSeguroService.*;
import com.controllocal.service.soporte.BloqueoAccesos;
import com.controllocal.web.dto.AccesoSeguroDtos;
import com.controllocal.web.http.ManejadorErroresApi;
import com.controllocal.web.seguridad.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Cadena HTTP real; los secretos no entran en un DTO de coordinación. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AccesoSeguroControllerTest.Configuracion.class)
@WebAppConfiguration
class AccesoSeguroControllerTest {
    private static final String FLUJO = "A".repeat(43);
    private static final String RETORNO = "B".repeat(43);
    @Autowired WebApplicationContext contexto;
    @Autowired InicioSeguroService inicio;
    @Autowired BloqueoAccesos bloqueo;
    @Autowired ContextosService contextos;
    @Autowired TokenService tokens;
    private MockMvc mvc;
    private TokenService.Sesion sesion;
    private ContextosService.SesionAccesoDTO acceso;

    @BeforeEach void preparar() {
        reset(inicio, bloqueo, contextos);
        SecurityContextHolder.clearContext();
        when(bloqueo.permitir(isNull(), anyString())).thenReturn(BloqueoAccesos.Veredicto.LIBRE);
        Instant ahora = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        acceso = new ContextosService.SesionAccesoDTO("s".repeat(43),4,
                new ContextosService.IdentidadAutenticada(1000,"fixture","fixture@cuentas.example.test","Fixture"),
                new ContextosService.Contexto("contexto",1,7,9,"AGENTE","EMPRESARIAL","Agencia"),
                ahora.minusSeconds(5),ahora.plusSeconds(1200),EstadoDeAcceso.SIN_RESTRICCIONES);
        sesion = tokens.de(acceso);
        when(contextos.comprobarSesion(sesion.referenciaSesion(),4,sesion.emitidoEn(),sesion.expiraEn())).thenReturn(acceso);
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters((Filter) contexto.getBean(FilterChainProxy.class)).build();
    }
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }
    private String token() { return "Bearer " + tokens.firmar(sesion); }

    @Test void protocoloPublicoCerradoNoConsultaPersonasNiModelo() throws Exception {
        mvc.perform(get("/acceso/protocolo")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.propositos[0]").value("ACCESO"))
                .andExpect(jsonPath("$.destinos[0].ruta").value("auth/mfa/desafio"))
                .andExpect(jsonPath("$.actor").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
        verifyNoInteractions(inicio, contextos, bloqueo);
    }

    @Test void crearAntesDeSesionConsumeElCupoIpSinTenantFicticio() throws Exception {
        when(inicio.iniciar(eq(Proposito.ACCESO), any())).thenAnswer(inv ->
                new Vinculo(FLUJO, Proposito.ACCESO, inv.getArgument(1)));
        mvc.perform(post("/acceso/flujos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"proposito\":\"ACCESO\",\"actor\":99,\"tenant\":88}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flujoRef").value(FLUJO))
                .andExpect(jsonPath("$.destinoSeguro").value("auth/mfa/desafio"))
                .andExpect(jsonPath("$.tenant").doesNotExist());
        ArgumentCaptor<Instant> vencimiento = ArgumentCaptor.forClass(Instant.class);
        verify(inicio).iniciar(eq(Proposito.ACCESO), vencimiento.capture());
        assertTrue(vencimiento.getValue().isAfter(Instant.now().plusSeconds(TokenService.DURACION_SEGUNDOS - 5)));
        assertTrue(vencimiento.getValue().isBefore(Instant.now().plusSeconds(TokenService.DURACION_SEGUNDOS + 1)));
        verify(bloqueo).registrarSoloIp(eq("203.0.113.10"), eq(false), isNull(), isNull());
        verifyNoInteractions(contextos);
    }

    @Test void flujoMalformadoTambienConsumeCupoPeroNoCreaVinculo() throws Exception {
        mvc.perform(post("/acceso/flujos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"proposito\":\"CERRAR_OPERACION\"}"))
                .andExpect(status().isBadRequest());
        verify(bloqueo).registrarSoloIp(anyString(), eq(false), isNull(), isNull());
        verifyNoInteractions(inicio);
    }

    @Test void bloqueoExistenteDetieneEmisionAntesDelServicio() throws Exception {
        when(bloqueo.permitir(isNull(), anyString())).thenReturn(new BloqueoAccesos.Veredicto(true, "IP", 10));
        mvc.perform(post("/acceso/flujos").contentType(MediaType.APPLICATION_JSON).content("{\"proposito\":\"ACCESO\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "300"));
        verifyNoInteractions(inicio);
        verify(bloqueo, never()).registrarSoloIp(any(), anyBoolean(), any(), any());
    }

    @Test void completarYRetornoNoSonPublicos() throws Exception {
        for (String sufijo : new String[]{"completar", "retorno"}) {
            mvc.perform(post("/acceso/flujos/" + FLUJO + "/" + sufijo)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"proposito\":\"ACCESO\"}"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(inicio);
    }

    @Test void completarTomaActorYContextoDeLaSesionValidada() throws Exception {
        when(inicio.completar(eq(FLUJO), eq(Proposito.ACCESO), any())).thenReturn(
                new RetornoPreparado(FLUJO, Proposito.ACCESO, RETORNO, sesion.expiraEn()));
        String cuerpo = mvc.perform(post("/acceso/flujos/" + FLUJO + "/completar").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"proposito\":\"ACCESO\",\"actor\":99,\"tenant\":88,\"completado\":true}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.retornoRef").value(RETORNO)).andReturn().getResponse().getContentAsString();
        var verificada = ArgumentCaptor.forClass(SesionVerificada.class);
        verify(inicio).completar(eq(FLUJO), eq(Proposito.ACCESO), verificada.capture());
        assertEquals(1, verificada.getValue().actor().idOrganizacion());
        assertEquals(7, verificada.getValue().actor().idPersona());
        assertEquals(9, verificada.getValue().actor().idRolOperativo());
        assertEquals(64, verificada.getValue().contexto().length());
        assertEquals(sesion.referenciaSesion(),verificada.getValue().referenciaSesion());
        assertEquals(4,verificada.getValue().revisionContexto());
        assertFalse(cuerpo.contains(tokens.firmar(sesion)));
        assertFalse(cuerpo.contains("fixture"));
        assertFalse(cuerpo.contains("contrasena"));
        assertFalse(cuerpo.contains("factor"));
    }

    @Test void retornoSoloComunicaLaReferenciaYEstadoPermitidos() throws Exception {
        when(inicio.consumir(eq(FLUJO), eq(Proposito.ACCESO), eq(RETORNO), any()))
                .thenReturn(new RetornoConfirmado(FLUJO, Proposito.ACCESO, "CONFIRMADO"));
        mvc.perform(post("/acceso/flujos/" + FLUJO + "/retorno").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"proposito\":\"ACCESO\",\"retornoRef\":\"" + RETORNO + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.actor").doesNotExist());
    }

    @Test void revocarLaSesionImpideLlegarAlRetornoAunqueElJwtEsteFirmado() throws Exception {
        when(contextos.comprobarSesion(sesion.referenciaSesion(),4,sesion.emitidoEn(),sesion.expiraEn()))
                .thenThrow(new CredencialesInvalidasException());
        mvc.perform(post("/acceso/flujos/" + FLUJO + "/retorno").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"proposito\":\"ACCESO\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inicio);
    }

    @Test void mfaPendienteNoSePresentaComoAccesoCompletado() throws Exception {
        restriccion(new EstadoDeAcceso(null,false,true,"AGENTE"));
        mvc.perform(post("/acceso/flujos/" + FLUJO + "/completar").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"proposito\":\"ACCESO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(FiltroAutenticacionJwt.CODIGO_ENROLAMIENTO_MFA));
        verifyNoInteractions(inicio);
    }

    @Test void contrasenaTemporalNoPermiteConsumirCorrelacion() throws Exception {
        restriccion(new EstadoDeAcceso(null,true,false,"AGENTE"));
        mvc.perform(post("/acceso/flujos/" + FLUJO + "/retorno").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"proposito\":\"ACCESO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(FiltroAutenticacionJwt.CODIGO_CAMBIO_OBLIGATORIO));
        verifyNoInteractions(inicio);
    }

    @Test void losDtosNoAceptanPruebasDeExitoBooleanasNiIdentidadElegidaPorChat() {
        assertEquals(Set.of("proposito"), campos(AccesoSeguroDtos.IniciarRequest.class));
        assertEquals(Set.of("proposito"), campos(AccesoSeguroDtos.CompletarRequest.class));
        assertEquals(Set.of("proposito", "retornoRef"), campos(AccesoSeguroDtos.RetornoRequest.class));
    }

    private static Set<String> campos(Class<?> tipo) {
        return Arrays.stream(tipo.getRecordComponents()).map(c -> c.getName()).collect(Collectors.toSet());
    }

    private void restriccion(EstadoDeAcceso estado) {
        when(contextos.comprobarSesion(sesion.referenciaSesion(),4,sesion.emitidoEn(),sesion.expiraEn()))
                .thenReturn(new ContextosService.SesionAccesoDTO(acceso.referencia(),acceso.revision(),acceso.identidad(),
                        acceso.activo(),acceso.emitidaEn(),acceso.venceEn(),estado));
    }

    @Configuration
    @EnableWebMvc
    @Import(ConfiguracionSeguridad.class)
    static class Configuracion {
        @Bean ObjectMapper json() { return JsonMapper.builder().addModule(new JavaTimeModule()).build(); }
        @Bean InicioSeguroService inicio() { return mock(InicioSeguroService.class); }
        @Bean BloqueoAccesos bloqueo() { return mock(BloqueoAccesos.class); }
        @Bean ContextosService contextos() { return mock(ContextosService.class); }
        @Bean TokenService tokens() { return new TokenService(""); }
        @Bean IpDelCliente ip() {
            var ip = mock(IpDelCliente.class);
            when(ip.de(any())).thenReturn("203.0.113.10");
            return ip;
        }
        @Bean FiltroAutenticacionJwt filtro(TokenService tokens, ContextosService contextos) {
            return new FiltroAutenticacionJwt(tokens, new RevalidacionDeSesion(contextos));
        }
        @Bean AccesoSeguroController controlador(InicioSeguroService inicio, BloqueoAccesos bloqueo, IpDelCliente ip) {
            return new AccesoSeguroController(inicio, bloqueo, ip);
        }
        @Bean ManejadorErroresApi errores() { return new ManejadorErroresApi(); }
    }
}
