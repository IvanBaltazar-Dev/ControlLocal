package com.controllocal.service.impl;

import com.controllocal.domain.seguridad.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static com.controllocal.service.soporte.NotificadorIdentidad.Proposito.RECUPERACION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContrasenaServiceImplTest {
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final CredencialPasswordRepository historial=mock(CredencialPasswordRepository.class);
    private final SesionAccesoRepository sesiones=mock(SesionAccesoRepository.class);
    private final TokenAccesoRepository tokens=mock(TokenAccesoRepository.class);
    private final EnlacesDeCuenta enlaces=mock(EnlacesDeCuenta.class);
    private final EventosSeguridad auditoria=mock(EventosSeguridad.class);
    private final EntityManager entidades=mock(EntityManager.class);
    private final ContrasenaServiceImpl service=new ContrasenaServiceImpl(cuentas,historial,sesiones,tokens,enlaces,auditoria,entidades);
    private final CuentaAcceso cuenta=new CuentaAcceso();
    private final List<CredencialPassword> anteriores=new ArrayList<>();
    private static final char[] ACTUAL="frase anterior muy larga".toCharArray();
    private static final char[] NUEVA="frase posterior muy larga".toCharArray();
    @BeforeEach void preparar() {
        cuenta.setId(42L);cuenta.setNombreUsuario("referencia");cuenta.setCorreoPropio("persona@example.test");
        cuenta.setCorreoConfirmadoEn(Instant.now().minusSeconds(60));cuenta.setContrasenaHash(PasswordHasher.hash(ACTUAL));
        when(cuentas.bloquearCambioAcceso(42)).thenReturn(Optional.of(cuenta));
        when(cuentas.buscarPorCorreo("persona@example.test")).thenReturn(Optional.of(cuenta));
        when(historial.ultimosDe(eq(42L),any())).thenAnswer(i->List.copyOf(anteriores));
        when(historial.saveAndFlush(any())).thenAnswer(i->{CredencialPassword h=i.getArgument(0);
            ReflectionTestUtils.setField(h,"id",(long)anteriores.size()+1);anteriores.addFirst(h);return h;});
    }
    @Test void cambioPropioArchivaHashYRevocaSesionesYPruebasPendientesEnLaCuentaGlobal() {
        String anterior=cuenta.getContrasenaHash();service.cambiar(42,ACTUAL,NUEVA);
        assertTrue(PasswordHasher.verificar(NUEVA,cuenta.getContrasenaHash()));assertFalse(PasswordHasher.verificar(ACTUAL,cuenta.getContrasenaHash()));
        assertEquals(anterior,anteriores.getFirst().getContrasenaHash());assertEquals(42,anteriores.getFirst().getIdCuenta());
        assertNotNull(cuenta.getSesionesInvalidasDesde());assertNotNull(cuenta.getPasswordActualizadaEn());
        verify(sesiones).revocarDeCuenta(42);verify(tokens).invalidarVivosDe(eq(42L),eq("CAMBIO_CORREO"),any());
        verify(entidades).refresh(cuenta,LockModeType.PESSIMISTIC_WRITE);
    }
    @Test void contrasenaAnteriorNoAutorizaTrasRefrescarUnaEntidadPreviaDeOeiv() {
        doAnswer(i->{cuenta.setContrasenaHash(PasswordHasher.hash(NUEVA));return null;})
                .when(entidades).refresh(cuenta,LockModeType.PESSIMISTIC_WRITE);
        assertThrows(ReglaNegocioException.class,()->service.cambiar(42,ACTUAL,"otra frase no reutilizada".toCharArray()));
        verifyNoInteractions(historial,sesiones,tokens);
    }
    @Test void unaContrasenaIncorrectaOCuentaNoVerificadaNoModificaCredencial() {
        assertThrows(ReglaNegocioException.class,()->service.cambiar(42,"incorrecta".toCharArray(),NUEVA));
        cuenta.setCorreoConfirmadoEn(null);
        assertThrows(ReglaNegocioException.class,()->service.exigirActual(42,ACTUAL));
        verifyNoInteractions(historial,sesiones,tokens);
    }
    @Test void reutilizacionDeUnHashAnteriorSeRechaza() {
        var anterior=new CredencialPassword();anterior.setContrasenaHash(PasswordHasher.hash(NUEVA));anteriores.add(anterior);
        assertThrows(ReglaNegocioException.class,()->service.cambiar(42,ACTUAL,NUEVA));
        verify(historial,never()).saveAndFlush(any());verifyNoInteractions(sesiones,tokens);
    }
    @Test void correoAusenteYNoVerificadoNoEmitenRecuperacion() {
        service.solicitarRecuperacion("ausente@example.test",null,null);
        cuenta.setCorreoConfirmadoEn(null);service.solicitarRecuperacion("persona@example.test",null,null);
        verifyNoInteractions(enlaces,sesiones,tokens);
    }
    @Test void solicitudVaciaOInvalidaConservaAcuseUniformeSinConsultarNiEmitir() {
        for(String correo:new String[]{null,"","   ","sin-arroba","a@@b","@b","a@","a@b\r\nBcc:x@y"})
            assertDoesNotThrow(()->service.solicitarRecuperacion(correo,null,null));
        verifyNoInteractions(cuentas,historial,sesiones,tokens,enlaces,auditoria,entidades);
    }
    @Test void recuperacionUsaExclusivamenteElCorreoPropioConfirmado() {
        service.solicitarRecuperacion("  PERSONA@example.test ",null,null);
        verify(enlaces).emitir(RECUPERACION,42,"persona@example.test",null);
        verify(entidades).refresh(cuenta,LockModeType.PESSIMISTIC_WRITE);
    }
    @Test void cambiarElCorreoMientrasEsperabaElCandadoNoEnviaAlDestinoAnterior() {
        doAnswer(i->{cuenta.setCorreoPropio("otro@example.test");return null;})
                .when(entidades).refresh(cuenta,LockModeType.PESSIMISTIC_WRITE);
        service.solicitarRecuperacion("persona@example.test",null,null);verifyNoInteractions(enlaces);
    }
    @Test void canjeInvalidoOCasRechazadoNoCambiaPasswordNiConsumeHistoria() {
        var prueba=new TokenAcceso();prueba.setIdCuenta(42L);prueba.setCorreoObjetivo("persona@example.test");
        when(enlaces.buscar("secreto",RECUPERACION)).thenReturn(prueba);
        doThrow(EnlacesDeCuenta.invalido()).when(enlaces).consumir(prueba,RECUPERACION);
        assertThrows(ReglaNegocioException.class,()->service.canjear("secreto",NUEVA,null,null));
        assertTrue(PasswordHasher.verificar(ACTUAL,cuenta.getContrasenaHash()));verifyNoInteractions(historial,sesiones,tokens);
    }
    @Test void tokenDirigidoAlCorreoSustituidoNoRecuperaLaCuenta() {
        var prueba=new TokenAcceso();prueba.setIdCuenta(42L);prueba.setCorreoObjetivo("anterior@example.test");
        when(enlaces.buscar("secreto",RECUPERACION)).thenReturn(prueba);
        assertThrows(ReglaNegocioException.class,()->service.canjear("secreto",NUEVA,null,null));
        verify(enlaces,never()).consumir(any(),any());verifyNoInteractions(historial,sesiones,tokens);
    }
}
