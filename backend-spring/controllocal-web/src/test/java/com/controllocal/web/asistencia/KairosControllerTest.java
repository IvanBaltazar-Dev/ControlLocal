package com.controllocal.web.asistencia;

import com.controllocal.asistencia.brox.SesionBrox;
import com.controllocal.asistencia.conversacion.Kairos;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
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
import static org.mockito.Mockito.*;

class KairosControllerTest {
    private final Kairos kairos = mock(Kairos.class);
    private final KairosController controller = new KairosController(kairos);
    @BeforeEach void sesion() {
        var acceso = new ContextosService.SesionAccesoDTO("s".repeat(43), 5,
                new ContextosService.IdentidadAutenticada(2, "cuenta-test", "cuenta@example.invalid", null),
                new ContextosService.Contexto("ctx-test", 7, 30, 41, Actor.AGENTE, "INDIVIDUAL", "TEST"),
                Instant.now(), Instant.now().plusSeconds(1800), EstadoDeAcceso.SIN_RESTRICCIONES);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SesionDeRequest(new TokenService("").de(acceso), acceso), null, List.of()));
    }
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test void turnoConservaCasYCorrelacionYDerivaActorDeLaSesion() {
        controller.turno(new KairosController.TurnoRequest("API", "conversacion-test", "turno-test", "mensaje-test",
                "cinco ascensores", 7L, null, 4));
        var turno = ArgumentCaptor.forClass(Kairos.Turno.class);
        var sesion = ArgumentCaptor.forClass(SesionBrox.class);
        verify(kairos).turno(turno.capture(), sesion.capture());
        assertEquals(4, turno.getValue().versionBorrador());
        assertEquals("turno-test", turno.getValue().turnoId());
        assertEquals("conversacion-test", turno.getValue().conversacionId());
        assertEquals(7, sesion.getValue().idOrganizacion());
        assertEquals(30, sesion.getValue().idPersona());
        assertEquals(41, sesion.getValue().idRolOperativo());
    }

    @Test void cuerpoAusenteNoInvocaLaAsistencia() {
        assertThrows(IllegalArgumentException.class, () -> controller.turno(null));
        verifyNoInteractions(kairos);
    }
}
