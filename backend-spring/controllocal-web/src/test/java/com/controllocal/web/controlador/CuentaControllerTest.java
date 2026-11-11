package com.controllocal.web.controlador;

import com.controllocal.service.*;
import com.controllocal.web.dto.ContrasenaDtos.CambioContrasenaRequest;
import com.controllocal.web.dto.CuentaDtos.CambioCorreoRequest;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Gestión personal antes de pertenecer a una organización; sin Actor comercial o cuenta del cuerpo. */
class CuentaControllerTest {
    private final CuentaService cuentas=mock(CuentaService.class);
    private final CuentaController controller=new CuentaController(cuentas);
    private static final String SID="s".repeat(43);
    @BeforeEach void identidad() {
        Instant i=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var s=new ContextosService.SesionAccesoDTO(SID,3,
                new ContextosService.IdentidadAutenticada(89,"propia","propia@cuentas.example.test",null),
                null,i,i.plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SesionDeRequest(new TokenService("").de(s),s),null,List.of()));
    }
    @AfterEach void limpiar() {SecurityContextHolder.clearContext();}
    @Test void consultaCuentaPropiaSinOrganizacionNiPersonaLocal() {
        var ficha=new CuentaService.FichaCuenta(89,"propia","propia@cuentas.example.test",Instant.now(),null,false,false);
        when(cuentas.consultarPropia(SID,3)).thenReturn(ficha);
        var r=controller.propia();assertEquals(ficha,r.getBody());assertEquals("no-store",r.getHeaders().getCacheControl());
        assertEquals(0,SesionActual.sesion().idOrganizacion());verify(cuentas).consultarPropia(SID,3);
    }
    @Test void cambiarClaveUsaSoloLaSesionYLimpiaLosSecretosInclusoConFallo() {
        AtomicReference<char[]> actual=new AtomicReference<>(),nueva=new AtomicReference<>();
        doAnswer(inv->{actual.set(inv.getArgument(2));nueva.set(inv.getArgument(3));throw new IllegalStateException("fallo técnico de fixture");})
                .when(cuentas).cambiarContrasena(eq(SID),eq(3L),any(),any());
        assertThrows(IllegalStateException.class,()->controller.cambiarContrasena(new CambioContrasenaRequest("actual","nueva")));
        assertArrayEquals(new char[6],actual.get());assertArrayEquals(new char[5],nueva.get());
    }
    @Test void cambiarCorreoConservaLaPruebaMfaYLaCuentaSaleDeLaSesion() {
        AtomicReference<char[]> secreto=new AtomicReference<>();
        doAnswer(inv->{secreto.set(inv.getArgument(3));assertArrayEquals("actual".toCharArray(),secreto.get());return null;})
                .when(cuentas).solicitarCambioCorreo(eq(SID),eq(3L),eq("nueva@cuentas.example.test"),any(),eq("123456"));
        var r=controller.solicitarCambioCorreo(new CambioCorreoRequest("nueva@cuentas.example.test","actual","123456"));
        assertEquals(202,r.getStatusCode().value());assertNull(r.getBody());assertEquals("no-store",r.getHeaders().getCacheControl());
        assertArrayEquals(new char[6],secreto.get());
    }
}
