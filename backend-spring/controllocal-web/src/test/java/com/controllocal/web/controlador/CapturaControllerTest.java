package com.controllocal.web.controlador;

import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.seguridad.ProcedenciaDeCabeceras;
import com.controllocal.web.seguridad.SesionDeRequest;
import com.controllocal.web.seguridad.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El adaptador transmite CAS, respuesta gobernada, contexto y procedencia al único Motor. */
class CapturaControllerTest {
    private final MotorDeCaptura motor = mock(MotorDeCaptura.class);
    private final CapturaController controller = new CapturaController(motor, new ProcedenciaDeCabeceras());
    private final Actor actor = new Actor(7, 30, 41, Actor.AGENTE);
    private final MockHttpServletRequest peticion = new MockHttpServletRequest();

    @BeforeEach void sesion() {
        var acceso = new ContextosService.SesionAccesoDTO("s".repeat(43), 5,
                new ContextosService.IdentidadAutenticada(2, "cuenta-test", "cuenta@example.invalid", null),
                new ContextosService.Contexto("ctx-test", 7, 30, 41, Actor.AGENTE, "INDIVIDUAL", "TEST"),
                Instant.now(), Instant.now().plusSeconds(1800), EstadoDeAcceso.SIN_RESTRICCIONES);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SesionDeRequest(new TokenService("").de(acceso), acceso), null, List.of()));
        peticion.addHeader(ProcedenciaDeCabeceras.CANAL, "API");
    }
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test void avanceTipadoConservaLaVersionObservadaYTodosLosMetadatos() {
        var contexto = new SuficienciaInmueble.Contexto("TERRENO", "COMERCIAL", "Predio TEST", "VENTA",
                Map.of(), Set.of(), "JURISDICCION-TEST");
        var respuesta = new MotorDeCaptura.Respuesta("TEST-Z", SuficienciaInmueble.EstadoDato.CONOCIDO,
                "documento-test", null, "CAT:P:90:3", "3", LocalDate.of(2026, 10, 27), false);
        when(motor.avanzar(any(), any(), eq(actor))).thenReturn(estado());
        var resultado = controller.avanzar(new CapturaController.AvanceRequest("REGISTRAR_PROPIEDAD", 7L,
                4, Map.of("zonificacion", respuesta), contexto), peticion);
        var comando = ArgumentCaptor.forClass(MotorDeCaptura.Avance.class);
        var procedencia = ArgumentCaptor.forClass(Procedencia.class);
        verify(motor).avanzar(comando.capture(), procedencia.capture(), eq(actor));
        assertEquals(200, resultado.getStatusCode().value());
        assertEquals(4, comando.getValue().versionEsperada());
        assertEquals(Map.of("zonificacion", respuesta), comando.getValue().respuestas());
        assertEquals(contexto, comando.getValue().contexto());
        assertEquals("API", procedencia.getValue().canal());
        assertNotNull(resultado.getBody());
        assertEquals(5, resultado.getBody().version());
        assertEquals("CAT:P:90:3", resultado.getBody().siguiente().referenciaCatalogo());
        assertEquals("3", resultado.getBody().siguiente().versionDefinicion());
        verifyNoMoreInteractions(motor);
    }

    @Test void aperturaSinCuerpoNoInventaUnaVersionAnterior() {
        when(motor.avanzar(any(), any(), eq(actor))).thenReturn(estado());
        assertEquals(201, controller.avanzar(null, peticion).getStatusCode().value());
        var comando = ArgumentCaptor.forClass(MotorDeCaptura.Avance.class);
        verify(motor).avanzar(comando.capture(), any(), eq(actor));
        assertNull(comando.getValue().idBorrador());
        assertNull(comando.getValue().versionEsperada());
        assertTrue(comando.getValue().respuestas().isEmpty());
    }

    @Test void descarteYPosposicionPasanCasYProcedenciaSinConsultarUnaVersionActual() {
        when(motor.descartar(eq(7L), eq(4), any(), eq(actor))).thenReturn(estado());
        controller.descartar(7, 4, peticion);
        var hasta = LocalDate.of(2026, 10, 29);
        when(motor.posponerRevision(eq(7L), eq(4), eq(hasta), eq("Acuerdo TEST"), any(), eq(actor)))
                .thenReturn(estado());
        controller.posponer(7, new CapturaController.PosposicionRequest(4, hasta, "Acuerdo TEST"), peticion);
        var procedencia = ArgumentCaptor.forClass(Procedencia.class);
        verify(motor).descartar(eq(7L), eq(4), procedencia.capture(), eq(actor));
        verify(motor).posponerRevision(eq(7L), eq(4), eq(hasta), eq("Acuerdo TEST"), procedencia.capture(), eq(actor));
        assertTrue(procedencia.getAllValues().stream().allMatch(p -> "API".equals(p.canal())));
        verify(motor, never()).consultar(anyLong(), any());
    }

    @Test void posposicionSinCuerpoNoEmiteUnComandoIncompleto() {
        assertThrows(ReglaNegocioException.class, () -> controller.posponer(7, null, peticion));
        verifyNoInteractions(motor);
    }

    @Test void definicionConservaLaJurisdiccionSinResolverOtroCatalogoEnWeb() {
        when(motor.definicion("REGISTRAR_PROPIEDAD", "TERRENO", "VENTA", "JURISDICCION-TEST", actor))
                .thenReturn(new MotorDeCaptura.DefinicionCaptura("REGISTRAR_PROPIEDAD", "TERRENO", List.of("VENTA"), List.of(), List.of(), List.of()));
        controller.definicion("REGISTRAR_PROPIEDAD", "TERRENO", "VENTA", "JURISDICCION-TEST");
        verify(motor).definicion("REGISTRAR_PROPIEDAD", "TERRENO", "VENTA", "JURISDICCION-TEST", actor);
        verifyNoMoreInteractions(motor);
    }

    private static MotorDeCaptura.EstadoCaptura estado() {
        var siguiente = new MotorDeCaptura.Pregunta("zonificacion", "Zonificación", "TIPO", null, "CATALOGO_CERRADO",
                "LISTA", null, List.of(new MotorDeCaptura.Opcion("TEST-Z", "Zona TEST")), "OPC", false, null, 1,
                null, "CAT:P:90:3", "3");
        return new MotorDeCaptura.EstadoCaptura(7L, "CAP-7", "REGISTRAR_PROPIEDAD", "E", "API",
                Map.of("tipoPropiedad", "TERRENO"), List.of("direccion"), siguiente, false,
                null, null, null, 5, null, Map.of(), null, null, List.of());
    }
}
