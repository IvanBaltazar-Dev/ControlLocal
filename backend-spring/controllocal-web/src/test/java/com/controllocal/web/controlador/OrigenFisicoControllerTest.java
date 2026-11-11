package com.controllocal.web.controlador;

import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrigenFisicoControllerTest {
    private final UnidadRelacionadaService core=mock(UnidadRelacionadaService.class);
    private final ActuacionesService actuaciones=mock(ActuacionesService.class);
    private final ProcedenciaDeCabeceras procedencias=mock(ProcedenciaDeCabeceras.class);
    private final OrigenFisicoController controller=new OrigenFisicoController(core,actuaciones,procedencias);
    private final Actor actor=new Actor(7,30,41,Actor.AGENTE);
    private ActuacionesService.ContextoSesion contexto;
    @BeforeEach void sesion() {
        var acceso=new ContextosService.SesionAccesoDTO("s".repeat(43),5,
                new ContextosService.IdentidadAutenticada(2,"cuenta-test","cuenta@example.invalid",null),
                new ContextosService.Contexto("ctx-test",7,30,41,Actor.AGENTE,"INDIVIDUAL","TEST"),
                Instant.now(),Instant.now().plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);
        var principal=new SesionDeRequest(new TokenService("").de(acceso),acceso);
        contexto=ContextoDeActuacion.de(principal);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
    @Test void lecturaConservaElCorteInstantaneoYElResultadoVacioAutorizado() {
        Instant corte=Instant.parse("2026-10-27T17:42:31Z");
        when(core.consultarOrigen(9,corte,actor)).thenReturn(List.of());
        assertEquals(List.of(),controller.consultar(9,corte));
        verify(core).consultarOrigen(9,corte,actor);verifyNoInteractions(actuaciones);
    }
    @Test void prepararDelegaSesionYProcedenciaSinEjecutarElVinculo() {
        var datos=new UnidadRelacionadaService.DatosOrigen(9L,12L,LocalDate.of(2026,10,27),null,null,
                "Documento declarado TEST",0L,2L);
        var request=new MockHttpServletRequest();var fuente=Procedencia.deLaPantalla();
        var propuesta=new ActuacionesService.Propuesta("referencia","registrar_origen_fisico_unidad",0,"huella",1800,Map.of());
        when(procedencias.de(request)).thenReturn(fuente);
        when(actuaciones.prepararOrigen(actor,datos,contexto,fuente)).thenReturn(propuesta);
        assertSame(propuesta,controller.preparar(datos,request));
        verify(actuaciones).prepararOrigen(actor,datos,contexto,fuente);verifyNoInteractions(core);
    }
}
