package com.controllocal.web.controlador;

import com.controllocal.service.*;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.soporte.BloqueoAccesos;
import com.controllocal.service.soporte.EventosSeguridad;
import com.controllocal.web.dto.LoginRequest;
import com.controllocal.web.dto.LoginResponse;
import com.controllocal.web.dto.ContrasenaDtos.*;
import com.controllocal.web.dto.MfaDtos.*;
import com.controllocal.web.dto.CuentaDtos.*;
import com.controllocal.web.http.DemasiadasSolicitudesException;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Login, factor y recuperación conservan sus guardas sin seleccionar contexto por defecto. */
class AuthControllerTest {
    private final ContextosService contextos=mock(ContextosService.class);
    private final TokenService tokens=new TokenService("");
    private final BloqueoAccesos bloqueo=mock(BloqueoAccesos.class);
    private final EventosSeguridad eventos=mock(EventosSeguridad.class);
    private final IpDelCliente ip=mock(IpDelCliente.class);
    private final CuentaService cuentas=mock(CuentaService.class);
    private final ContrasenaService contrasenas=mock(ContrasenaService.class);
    private final AuthController controller=new AuthController(contextos,new EmisionDeSesion(tokens,contextos),bloqueo,eventos,ip,contrasenas,cuentas);
    private final ContextosService.IdentidadAutenticada identidad=new ContextosService.IdentidadAutenticada(1000,"persona","persona@cuentas.example.test","Persona");
    private ContextosService.SesionAccesoDTO acceso;
    private final MockHttpServletRequest request=new MockHttpServletRequest();
    @Test void altaEntregaAcuseSinSesionAutomaticaNiTokenEnLaRespuesta() {
        var r=controller.solicitarAlta(new AltaRequest("nueva@cuentas.example.test"),request);
        assertEquals(202,r.getStatusCode().value());assertNull(r.getBody());
        verify(cuentas).solicitarAlta("nueva@cuentas.example.test");
        verify(contextos,never()).iniciarAcceso(any(),any(),anyBoolean());
    }
    @Test void confirmarAltaEstableceClaveSoloEnElCanjeSeguroYLimpiaLaCopia() {
        AtomicReference<char[]> capturada=new AtomicReference<>();
        doAnswer(inv->{capturada.set(inv.getArgument(3));assertArrayEquals("clave".toCharArray(),capturada.get());return null;})
                .when(cuentas).confirmarAlta(eq("enlace"),eq("nueva"),isNull(),any());
        var r=controller.confirmarAlta(new ConfirmacionAltaRequest("enlace","nueva",null,"clave"));
        assertEquals(204,r.getStatusCode().value());assertNull(r.getBody());
        assertArrayEquals(new char[5],capturada.get());
        verify(cuentas).confirmarAlta(eq("enlace"),eq("nueva"),isNull(),any());verifyNoMoreInteractions(cuentas);
    }
    @Test void confirmacionCorreoNoRecibeCuentaNiModificaOrganizacion() {
        var r=controller.confirmarCorreo(new ConfirmacionCorreoRequest("enlace"));
        assertEquals(204,r.getStatusCode().value());verify(cuentas).confirmarCambioCorreo("enlace");
        verifyNoInteractions(contextos);
    }
    @BeforeEach void preparar() {
        Instant iat=Instant.now().truncatedTo(ChronoUnit.SECONDS);
        acceso=new ContextosService.SesionAccesoDTO("s".repeat(43),1,identidad,null,iat,iat.plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);
        when(ip.de(request)).thenReturn("203.0.113.10");request.addHeader("User-Agent","fixture");
        when(bloqueo.permitir(any(),any())).thenReturn(BloqueoAccesos.Veredicto.LIBRE);
        when(contextos.iniciarAcceso(eq("persona@cuentas.example.test"),any(char[].class),anyBoolean())).thenReturn(new ContextosService.AccesoIniciado(acceso,null));
        when(contextos.contextos(acceso.referencia(),1)).thenReturn(List.of());
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
    private void principal() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SesionDeRequest(tokens.de(acceso),acceso),null,List.of()));
    }
    @Test void loginAbreIdentidadDurableSinContextoPredeterminadoYLimpiaLaClave() {
        var recibida=new AtomicReference<char[]>();
        when(contextos.iniciarAcceso(eq("persona@cuentas.example.test"),any(char[].class),eq(false))).thenAnswer(inv->{
            char[] clave=inv.getArgument(1);assertArrayEquals("clave de prueba".toCharArray(),clave);recibida.set(clave);
            return new ContextosService.AccesoIniciado(acceso,null);
        });
        var r=controller.login(new LoginRequest("persona@cuentas.example.test","clave de prueba"),request);
        assertTrue(r.contextoRequerido());assertEquals("IDENTIDAD",r.rol());assertEquals(0,r.idOrganizacion());
        assertEquals(1000,r.idCuenta());assertEquals(tokens.de(acceso),tokens.validar(r.token()).orElseThrow());
        assertArrayEquals(new char["clave de prueba".length()],recibida.get());
        verify(contextos,never()).seleccionar(anyString(),anyLong(),anyString());
    }
    @Test void bloqueoDetieneLoginAntesDeComprobarCredencialOFactor() {
        when(bloqueo.permitir("persona@cuentas.example.test","203.0.113.10")).thenReturn(new BloqueoAccesos.Veredicto(true,"IP",10));
        assertThrows(DemasiadasSolicitudesException.class,()->controller.login(new LoginRequest("persona@cuentas.example.test","x"),request));
        verifyNoInteractions(contextos);verify(bloqueo,never()).registrar(any(),any(),anyBoolean(),any(),any());
    }
    @Test void credencialInvalidaRegistraFalloSinAbrirSesion() {
        when(contextos.iniciarAcceso(eq("persona@cuentas.example.test"),any(char[].class),eq(false))).thenThrow(new CredencialesInvalidasException());
        assertThrows(CredencialesInvalidasException.class,()->controller.login(new LoginRequest("persona@cuentas.example.test","x"),request));
        verify(bloqueo).registrar("persona@cuentas.example.test","203.0.113.10",false,null,"fixture");
        verify(contextos,never()).seleccionar(anyString(),anyLong(),anyString());
    }
    @Test void loginSimpleNoEvitaSegundoFactorExistente() {
        when(contextos.iniciarAcceso(eq("persona@cuentas.example.test"),any(char[].class),eq(false))).thenThrow(new CredencialesInvalidasException());
        assertThrows(CredencialesInvalidasException.class,()->controller.login(new LoginRequest("persona@cuentas.example.test","x"),request));
        verify(contextos,never()).seleccionar(anyString(),anyLong(),anyString());
        verify(contextos).iniciarAcceso(eq("persona@cuentas.example.test"),any(char[].class),eq(false));
        verify(bloqueo).registrar("persona@cuentas.example.test","203.0.113.10",false,null,"fixture");
    }
    @Test void desafioConFactorDevuelve202SinCrearSesionNiContexto() {
        when(contextos.iniciarAcceso(eq("persona@cuentas.example.test"),any(char[].class),eq(true))).thenReturn(
                new ContextosService.AccesoIniciado(null,new MfaService.Desafio("reto-opaco",OffsetDateTime.now().plusMinutes(5))));
        var r=controller.desafio(new DesafioRequest("persona@cuentas.example.test","x"),request);
        assertEquals(202,r.getStatusCode().value());assertEquals("no-store",r.getHeaders().getCacheControl());
        assertInstanceOf(DesafioResponse.class,r.getBody());verify(contextos,never()).seleccionar(anyString(),anyLong(),anyString());
        verify(bloqueo).registrar("persona@cuentas.example.test","203.0.113.10",true,null,"fixture");
    }
    @Test void desafioSinFactorAbreIdentidadDurableSinExcepcionDePermisos() {
        var r=controller.desafio(new DesafioRequest("persona@cuentas.example.test","x"),request);
        assertEquals(200,r.getStatusCode().value());assertEquals("no-store",r.getHeaders().getCacheControl());
        var login=assertInstanceOf(LoginResponse.class,r.getBody());assertTrue(login.contextoRequerido());
        verify(contextos).iniciarAcceso(eq("persona@cuentas.example.test"),any(char[].class),eq(true));verify(contextos,never()).seleccionar(anyString(),anyLong(),anyString());
    }
    @Test void verificacionValidaAbreLaCuentaAcreditadaSinUsarPersonaGlobalComoContexto() {
        when(contextos.completarSegundoFactor("reto","123456","203.0.113.10","fixture")).thenReturn(acceso);
        var r=controller.verificar(new VerificacionRequest("reto","123456"),request);
        assertEquals("no-store",r.getHeaders().getCacheControl());assertNotNull(r.getBody());
        assertTrue(r.getBody().contextoRequerido());assertEquals(0,r.getBody().idUsuario());
        verify(contextos).completarSegundoFactor("reto","123456","203.0.113.10","fixture");
        verify(contextos,never()).seleccionar(anyString(),anyLong(),anyString());
        verify(contextos,never()).iniciarAcceso(any(),any(),anyBoolean());
    }
    @Test void desafioRechazadoNoAbreSesionNiReinterpretaElFallo() {
        when(contextos.completarSegundoFactor(any(),any(),any(),any())).thenThrow(new CredencialesInvalidasException());
        assertThrows(CredencialesInvalidasException.class,()->controller.verificar(new VerificacionRequest("reto","incorrecto"),request));
        verify(contextos,never()).seleccionar(anyString(),anyLong(),anyString());verify(contextos,never()).contextos(anyString(),anyLong());
    }
    @Test void logoutGlobalFuncionaTambienAntesDeSeleccionarContexto() {
        principal();assertEquals(204,controller.logout(request).getStatusCode().value());
        verify(contextos).cerrar(acceso.referencia(),1);
        verify(eventos).registrar(eq("LOGOUT"),eq("OK"),any(EventosSeguridad.Contexto.class));
        verify(eventos).registrar(eq("SESIONES_INVALIDADAS"),eq("OK"),any(EventosSeguridad.Contexto.class));
    }
    @Test void renovacionFirmaNuevaRevisionDevueltaPorCoreSinEmitirOtraSesion() {
        principal();var nueva=new ContextosService.SesionAccesoDTO(acceso.referencia(),2,identidad,null,
                acceso.emitidaEn(),acceso.venceEn(),acceso.restricciones());
        when(contextos.renovar(acceso.referencia(),1)).thenReturn(nueva);
        when(contextos.contextos(acceso.referencia(),2)).thenReturn(List.of());
        var r=controller.renovar();assertNotNull(r.getBody());assertEquals(2,r.getBody().revisionContexto());
        assertEquals("no-store",r.getHeaders().getCacheControl());verify(contextos,never()).seleccionar(anyString(),anyLong(),anyString());
    }
    @Test void recuperacionConsumeCupoIpYNoExponeSiLaCuentaExiste() {
        for(String usuario:List.of("persona","no-existe"))
            assertEquals(202,controller.recuperacion(new RecuperacionRequest(usuario),request).getStatusCode().value());
        verify(bloqueo,times(2)).registrarSoloIp("203.0.113.10",false,null,"fixture");
        verify(contrasenas).solicitarRecuperacion("persona","203.0.113.10","fixture");
        verify(contrasenas).solicitarRecuperacion("no-existe","203.0.113.10","fixture");verifyNoInteractions(contextos);
    }
    @Test void canjeDeRecuperacionLimpiaLaNuevaClaveYNoCreaLogin() {
        var recibida=new AtomicReference<char[]>();doAnswer(inv->{recibida.set(inv.getArgument(1));return null;})
                .when(contrasenas).canjear(eq("recuperacion"),any(char[].class),eq("203.0.113.10"),eq("fixture"));
        assertEquals(204,controller.canjearRecuperacion(new CanjeRequest("recuperacion","clave nueva"),request).getStatusCode().value());
        assertArrayEquals(new char["clave nueva".length()],recibida.get());verifyNoInteractions(contextos);
    }
}
