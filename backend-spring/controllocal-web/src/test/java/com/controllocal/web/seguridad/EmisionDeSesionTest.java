package com.controllocal.web.seguridad;

import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmisionDeSesionTest {
    private final TokenService tokens=new TokenService("");
    private final ContextosService contextos=mock(ContextosService.class);
    private final EmisionDeSesion emision=new EmisionDeSesion(tokens,contextos);
    private ContextosService.SesionAccesoDTO sesion(ContextosService.Contexto activo) {
        Instant iat=Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new ContextosService.SesionAccesoDTO("s".repeat(43),8,
                new ContextosService.IdentidadAutenticada(1000,"persona","persona@cuentas.example.test","Persona"),activo,
                iat,iat.plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);
    }
    @Test void identidadPideContextoSinPredeterminarLaPrimeraOpcion() {
        var s=sesion(null);
        var opcion=new ContextosService.Contexto("ref",92,99,880,"AGENTE","EMPRESARIAL","Agencia");
        when(contextos.contextos(s.referencia(),8)).thenReturn(List.of(opcion));
        var r=emision.emitir(s);
        assertTrue(r.contextoRequerido());assertEquals("IDENTIDAD",r.rol());assertEquals(0,r.idOrganizacion());
        assertEquals(0,r.idDominio());assertEquals(0,r.idUsuario());assertEquals(1000,r.idCuenta());
        assertEquals(List.of(opcion),r.contextos());assertNull(r.modalidad());
        assertEquals(tokens.de(s),tokens.validar(r.token()).orElseThrow());
        verify(contextos,never()).seleccionar(anyString(),anyLong(),anyString());
    }
    @Test void contextoElegidoUsaPersonaLocalYRevisionCoreSinModificarFechas() {
        var activo=new ContextosService.Contexto("ref",92,99,880,"BROKER","EMPRESARIAL","Agencia");
        var s=sesion(activo);when(contextos.contextos(s.referencia(),8)).thenReturn(List.of(activo));
        var r=emision.emitir(s);
        assertFalse(r.contextoRequerido());assertEquals(92,r.idOrganizacion());assertEquals(99,r.idUsuario());
        assertEquals(880,r.idDominio());assertEquals(8,r.revisionContexto());assertEquals("BROKER",r.rol());
        assertEquals("EMPRESARIAL",r.modalidad());
        assertEquals(s.venceEn(),r.expiraEn());
        assertEquals(tokens.de(s),tokens.validar(r.token()).orElseThrow());
        assertTrue(r.expiraEnSegundos()>1790&&r.expiraEnSegundos()<=1800);
    }
    @Test void falloAlConsultarContextosNoSeConvierteEnListaVaciaNiEmisionValida() {
        var s=sesion(null);when(contextos.contextos(s.referencia(),8)).thenThrow(new IllegalStateException("fixture"));
        assertThrows(IllegalStateException.class,()->emision.emitir(s));
    }
}
