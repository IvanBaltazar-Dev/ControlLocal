package com.controllocal.web.controlador;

import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SesionControllerTest {
    private final ContextosService contextos=mock(ContextosService.class);
    private final TokenService tokens=new TokenService("");
    private final SesionController controller=new SesionController(contextos,new EmisionDeSesion(tokens,contextos));
    private ContextosService.SesionAccesoDTO actual;
    private final ContextosService.Contexto elegido=new ContextosService.Contexto("elegido",92,99,880,"AGENTE","EMPRESARIAL","Agencia");
    @BeforeEach void identidad() {
        Instant iat=Instant.now().truncatedTo(ChronoUnit.SECONDS);
        actual=new ContextosService.SesionAccesoDTO("s".repeat(43),1,
                new ContextosService.IdentidadAutenticada(1000,"persona","persona@cuentas.example.test","Persona"),null,
                iat,iat.plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);
        principal(actual);
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
    private void principal(ContextosService.SesionAccesoDTO s) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SesionDeRequest(tokens.de(s),s),null,List.of()));
    }
    @Test void consultaActualSinContextoIdentificaCuentaSinInventarTenant() {
        var r=controller.actual();assertTrue(r.contextoRequerido());assertEquals("IDENTIDAD",r.rol());
        assertEquals(1000,r.idCuenta());assertEquals(0,r.idOrganizacion());assertEquals(0,r.idDominio());
        assertEquals(0,r.idPersona());assertEquals(actual.referencia(),r.referenciaSesion());
        verifyNoInteractions(contextos);
    }
    @Test void opcionesProcedenDeLaSesionYNoDeUnTenantEnviadoPorCliente() {
        when(contextos.contextos(actual.referencia(),1)).thenReturn(List.of(elegido));
        var r=controller.contextos();assertEquals(List.of(elegido),r.getBody());
        assertEquals("no-store",r.getHeaders().getCacheControl());verify(contextos).contextos(actual.referencia(),1);
    }
    @Test void seleccionSoloEnviaReferenciaAutorizadaYDevuelveNuevaRevision() {
        var nueva=new ContextosService.SesionAccesoDTO(actual.referencia(),2,actual.identidad(),elegido,
                actual.emitidaEn(),actual.venceEn(),actual.restricciones());
        when(contextos.seleccionar(actual.referencia(),1,"elegido")).thenReturn(nueva);
        when(contextos.contextos(actual.referencia(),2)).thenReturn(List.of(elegido));
        var r=controller.seleccionar(new SesionController.Seleccion("elegido"));
        assertEquals("no-store",r.getHeaders().getCacheControl());assertNotNull(r.getBody());
        assertEquals(2,r.getBody().revisionContexto());assertEquals(92,r.getBody().idOrganizacion());
        assertEquals(99,r.getBody().idUsuario());assertFalse(r.getBody().contextoRequerido());
        assertEquals(tokens.de(nueva),tokens.validar(r.getBody().token()).orElseThrow());
        verify(contextos).seleccionar(actual.referencia(),1,"elegido");
    }
    @Test void referenciaInvalidaNoSeleccionaElPrimerContextoDisponible() {
        when(contextos.seleccionar(actual.referencia(),1,"ajeno")).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->controller.seleccionar(new SesionController.Seleccion("ajeno")));
        verify(contextos,never()).contextos(anyString(),anyLong());
    }
    @Test void estadoActualConservaReferenciaYModalidadDelContextoSeleccionado() {
        actual=new ContextosService.SesionAccesoDTO(actual.referencia(),2,actual.identidad(),elegido,
                actual.emitidaEn(),actual.venceEn(),actual.restricciones());principal(actual);
        var r=controller.actual();assertEquals(99,r.idPersona());assertEquals("elegido",r.contextoRef());
        assertEquals("EMPRESARIAL",r.modalidad());assertFalse(r.contextoRequerido());
    }
}
