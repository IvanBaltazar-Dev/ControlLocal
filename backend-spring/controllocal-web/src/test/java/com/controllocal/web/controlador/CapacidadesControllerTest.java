package com.controllocal.web.controlador;

import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.capacidades.CapacidadesService;
import com.controllocal.web.seguridad.SesionDeRequest;
import com.controllocal.web.seguridad.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CapacidadesControllerTest {
    private final CapacidadesService capacidades = mock(CapacidadesService.class);
    private final CapacidadesController controller = new CapacidadesController(capacidades);
    private final Actor actor = new Actor(7, 30, 41, Actor.AGENTE);
    @BeforeEach void sesion() {
        var acceso = new ContextosService.SesionAccesoDTO("s".repeat(43), 5,
                new ContextosService.IdentidadAutenticada(2, "cuenta-test", "cuenta@example.invalid", null),
                new ContextosService.Contexto("ctx-test", 7, 30, 41, Actor.AGENTE, "INDIVIDUAL", "TEST"),
                Instant.now(), Instant.now().plusSeconds(1800), EstadoDeAcceso.SIN_RESTRICCIONES);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SesionDeRequest(new TokenService("").de(acceso), acceso), null, List.of()));
    }
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test void consultaPropagaRecursoOperacionYAtributoConElActorResueltoEnServidor() {
        var catalogo = new CapacidadesService.CatalogoCapacidades(2, List.of());
        when(capacidades.consultar(any(), eq(actor))).thenReturn(catalogo);
        assertSame(catalogo, controller.consultar("consultar_encargo", "ENCARGO", "9", "VENTA", null, "A", null));
        var consulta = ArgumentCaptor.forClass(CapacidadesService.Consulta.class);
        verify(capacidades).consultar(consulta.capture(), eq(actor));
        assertEquals(new CapacidadesService.Consulta("consultar_encargo", "ENCARGO", "9", "VENTA", null, "A", null), consulta.getValue());
        verifyNoMoreInteractions(capacidades);
    }
    @Test void origenPropagaLaReferenciaRelacionadaSinPermitirElegirActor() {
        var catalogo=new CapacidadesService.CatalogoCapacidades(2,List.of());
        when(capacidades.consultar(any(),eq(actor))).thenReturn(catalogo);
        assertSame(catalogo,controller.consultar("registrar_origen_fisico_unidad","PROPIEDAD","9",null,null,null,"12"));
        var q=ArgumentCaptor.forClass(CapacidadesService.Consulta.class);verify(capacidades).consultar(q.capture(),eq(actor));
        assertEquals("9",q.getValue().referencia());assertEquals("12",q.getValue().referenciaRelacionada());
    }
}
