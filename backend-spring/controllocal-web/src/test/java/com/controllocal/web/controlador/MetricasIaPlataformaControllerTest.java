package com.controllocal.web.controlador;

import com.controllocal.service.*;
import com.controllocal.service.presupuesto.MetricasIaB0;
import com.controllocal.service.presupuesto.ConfirmacionesCostoIaB0;
import com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma;
import com.controllocal.web.seguridad.*;
import com.controllocal.web.http.ManejadorErroresApi;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Sesión IDENTIDAD TEST y delegación. R25/SQL y filtro se prueban aparte con PG/HTTP por Root. */
class MetricasIaPlataformaControllerTest {
    final MetricasIaB0 servicio=mock(MetricasIaB0.class);
    final ConfirmacionesCostoIaB0 costos=mock(ConfirmacionesCostoIaB0.class);
    final MetricasIaPlataformaController controller=new MetricasIaPlataformaController(servicio,costos);
    final UUID concesion=UUID.randomUUID(),caso=UUID.randomUUID(),comando=UUID.randomUUID();
    final Instant desde=Instant.parse("2026-11-01T00:00:00Z"),hasta=desde.plusSeconds(60);
    SesionPlataforma sesion;
    @BeforeEach void identidad(){
        var acceso=new ContextosService.SesionAccesoDTO("A".repeat(43),5,
            new ContextosService.IdentidadAutenticada(99,"METRICA_TEST","metrica@example.invalid","TEST SIN CUENTA REAL"),null,
            Instant.now().minusSeconds(1),Instant.now().plusSeconds(60),EstadoDeAcceso.SIN_RESTRICCIONES);
        var p=new SesionDeRequest(new TokenService("").de(acceso),acceso);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,AuthorityUtils.createAuthorityList("ROLE_IDENTIDAD")));
        sesion=new SesionPlataforma(p.token().referenciaSesion(),p.token().revisionContexto(),concesion);
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
    @Test void tecnicaUsaSidYConcesionSinPedirActorYNoSePuedeCachear(){
        var salida=new MetricasIaB0.Tecnica(new MetricasIaB0.Periodo(desde,hasta,hasta,"TEST"),null,List.of(),List.of(),List.of(),List.of(),null,"NO_HABILITADAS_TEST");
        when(servicio.tecnica(sesion,desde,hasta)).thenReturn(salida);
        var r=controller.tecnicas(concesion,desde,hasta);assertSame(salida,r.getBody());assertEquals("no-store",r.getHeaders().getCacheControl());
        verify(servicio).tecnica(sesion,desde,hasta);
    }
    @Test void financieraConservaCasoYComandoExplicitosYSidActual(){
        var r=controller.financieras(concesion,caso,comando,desde,hasta);
        assertEquals("no-store",r.getHeaders().getCacheControl());verify(servicio).financiera(sesion,caso,comando,desde,hasta);
    }
    @Test void escrituraDevuelveSoloReciboYNoConsultaLedger()throws Exception{
        var solicitud=mock(ConfirmacionesCostoIaB0.Solicitud.class);var id=UUID.randomUUID();
        var recibo=new ConfirmacionesCostoIaB0.Recibo(id,4,true,"APLICACION_CONSERVADA");
        when(costos.confirmar(caso,comando,solicitud,sesion)).thenReturn(recibo);
        var r=controller.confirmarCosto(concesion,caso,comando,solicitud);assertSame(recibo,r.getBody());
        assertEquals("no-store",r.getHeaders().getCacheControl());verifyNoInteractions(servicio);
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(r.getBody());
        assertFalse(json.contains("Usd"));assertFalse(json.contains("mediciones"));assertFalse(json.contains("historia"));
    }
    @Test void recuperacionUsaElMismoHechoSinOtraSolicitudEconomica(){
        var rec=new ConfirmacionesCostoIaB0.Recuperacion(UUID.randomUUID());
        controller.aplicarCosto(concesion,caso,comando,rec);verify(costos).aplicar(caso,comando,rec,sesion);verifyNoInteractions(servicio);
    }
    @Test void headerOPeriodoAusentesCortanAntesDeCore()throws Exception{
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ManejadorErroresApi()).build();
        assertEquals(400,mvc.perform(get("/plataforma/metricas-ia/tecnicas").param("desde",desde.toString()).param("hasta",hasta.toString())).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(get("/plataforma/metricas-ia/tecnicas").header("X-Brox-Concesion",concesion)).andReturn().getResponse().getStatus());
        verifyNoInteractions(servicio);
    }
    @Test void financieraNoAceptaUnComandoOFechaNoTipados()throws Exception{
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ManejadorErroresApi()).build();
        assertEquals(400,mvc.perform(get("/plataforma/metricas-ia/financieras/"+caso+"/invalido")
            .header("X-Brox-Concesion",concesion).param("desde",desde.toString()).param("hasta",hasta.toString())).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(get("/plataforma/metricas-ia/tecnicas").header("X-Brox-Concesion",concesion)
            .param("desde","invalida").param("hasta",hasta.toString())).andReturn().getResponse().getStatus());
        verifyNoInteractions(servicio);
    }
}
