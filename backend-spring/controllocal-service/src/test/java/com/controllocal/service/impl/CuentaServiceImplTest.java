package com.controllocal.service.impl;

import com.controllocal.domain.seguridad.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static com.controllocal.service.soporte.NotificadorIdentidad.Proposito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CuentaServiceImplTest {
    private static final String SID="s".repeat(43);
    private final ContextosService contextos=mock(ContextosService.class);
    private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
    private final ContrasenaService contrasenas=mock(ContrasenaService.class);
    private final MfaService mfa=mock(MfaService.class);
    private final EnlacesDeCuenta enlaces=mock(EnlacesDeCuenta.class);
    private final TokenAccesoRepository tokens=mock(TokenAccesoRepository.class);
    private final SesionAccesoRepository sesiones=mock(SesionAccesoRepository.class);
    private final NotificadorIdentidad canal=mock(NotificadorIdentidad.class);
    private final EventosSeguridad auditoria=mock(EventosSeguridad.class);
    private final EntityManager entidades=mock(EntityManager.class);
    private final CuentaServiceImpl service=new CuentaServiceImpl(contextos,cuentas,contrasenas,mfa,enlaces,tokens,sesiones,canal,auditoria,entidades);
    private final CuentaAcceso cuenta=new CuentaAcceso();
    @BeforeEach void preparar() {
        cuenta.setId(42L);cuenta.setNombreUsuario("referencia");cuenta.setCorreoPropio("persona@example.test");
        cuenta.setCorreoConfirmadoEn(Instant.now().minusSeconds(3600));
        var identidad=new ContextosService.IdentidadAutenticada(42,"referencia","persona@example.test",null);
        var acceso=new ContextosService.SesionAccesoDTO(SID,4,identidad,null,Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(1800),EstadoDeAcceso.SIN_RESTRICCIONES);
        when(contextos.exigirIdentidad(SID,4)).thenReturn(acceso);
        when(cuentas.bloquearCambioAcceso(42)).thenReturn(Optional.of(cuenta));
        when(cuentas.saveAndFlush(any())).thenAnswer(i->{CuentaAcceso c=i.getArgument(0);if(c.getId()==null)c.setId(99L);return c;});
    }
    @Test void solicitarAltaNoCreaCuentaNiPasswordAntesDeProbarElCorreo() {
        service.solicitarAlta("  Persona@Example.Test ");
        verify(enlaces).bloquearAlta("persona@example.test");verify(enlaces).emitirAlta("persona@example.test");
        verify(cuentas,never()).saveAndFlush(any());verifyNoInteractions(contextos,contrasenas,mfa,sesiones);
    }
    @Test void repetirAltaDeCorreoExistenteNoCreaCuentaNiEmiteOtroSecreto() {
        when(cuentas.buscarPorCorreo("persona@example.test")).thenReturn(Optional.of(cuenta));
        service.solicitarAlta("persona@example.test");
        verify(enlaces,never()).emitirAlta(anyString());verify(cuentas,never()).saveAndFlush(any());
    }
    @Test void confirmarAltaConsumePruebaYGuardaCorreoVerificadoConPasswordDelTitularSinSesion() {
        var prueba=prueba(null,"persona@example.test");when(enlaces.buscar("prueba",ALTA_CORREO)).thenReturn(prueba);
        char[] password="mi frase personal de acceso".toCharArray();
        service.confirmarAlta("prueba"," referencia-nueva ",null,password);
        var guardada=ArgumentCaptor.forClass(CuentaAcceso.class);verify(cuentas).saveAndFlush(guardada.capture());
        assertEquals("persona@example.test",guardada.getValue().getCorreoPropio());assertNotNull(guardada.getValue().getCorreoConfirmadoEn());
        assertEquals("referencia-nueva",guardada.getValue().getNombreUsuario());
        assertTrue(PasswordHasher.verificar(password,guardada.getValue().getContrasenaHash()));
        verify(enlaces).consumir(prueba,ALTA_CORREO);verifyNoInteractions(contextos,sesiones,mfa,contrasenas);
    }
    @Test void usuarioOContrasenaNoValidosNoConsumenLaPruebaDeCorreo() {
        var prueba=prueba(null,"persona@example.test");when(enlaces.buscar("prueba",ALTA_CORREO)).thenReturn(prueba);
        when(cuentas.existsByNombreUsuario("ocupado")).thenReturn(true);
        assertThrows(ConflictoException.class,()->service.confirmarAlta("prueba","ocupado",null,"mi frase personal muy larga".toCharArray()));
        assertThrows(ReglaNegocioException.class,()->service.confirmarAlta("prueba","otro",null,"corta".toCharArray()));
        verify(enlaces,never()).consumir(any(),any());verify(cuentas,never()).saveAndFlush(any());
    }
    @Test void cuentaPropiaSeObtieneDeIdentidadSeguraSinNecesitarPertenenciaOrganizacional() {
        when(cuentas.vista(42)).thenReturn(Optional.of(new CuentaAccesoFila(42,"referencia","persona@example.test",
                cuenta.getCorreoConfirmadoEn(),null,"A",null,false,true)));
        var ficha=service.consultarPropia(SID,4);assertEquals(42,ficha.idCuenta());assertTrue(ficha.debeEnrolarMfa());
        verify(contextos).exigirIdentidad(SID,4);verifyNoInteractions(mfa,contrasenas);
    }
    @Test void sesionInvalidaNoAlcanzaPasswordFactorNiPerfilDeOtraCuenta() {
        when(contextos.exigirIdentidad(SID,4)).thenThrow(new CredencialesInvalidasException());
        assertThrows(CredencialesInvalidasException.class,()->service.cambiarContrasena(SID,4,new char[0],new char[0]));
        assertThrows(CredencialesInvalidasException.class,()->service.estadoMfa(SID,4));
        assertThrows(CredencialesInvalidasException.class,()->service.consultarPropia(SID,4));
        verifyNoInteractions(cuentas,mfa,contrasenas,enlaces);
    }
    @Test void solicitudDeCorreoExigePasswordYMfaAntesDeEmitirYNoCambiaElAnterior() {
        Instant confirmacion=cuenta.getCorreoConfirmadoEn();char[] password="actual".toCharArray();
        service.solicitarCambioCorreo(SID,4,"nuevo@example.test",password,"123456");
        var orden=inOrder(contextos,contrasenas,mfa,enlaces);
        orden.verify(contextos).exigirIdentidad(SID,4);orden.verify(contrasenas).exigirActual(42,password);
        orden.verify(mfa).comprobarFactorPropio(42,"123456");
        orden.verify(enlaces).emitir(CAMBIO_CORREO,42,"nuevo@example.test",null);
        assertEquals("persona@example.test",cuenta.getCorreoPropio());assertEquals(confirmacion,cuenta.getCorreoConfirmadoEn());
        verifyNoInteractions(sesiones);
    }
    @Test void factorRechazadoNoEnviaCambioNiSustituyeElCorreoActual() {
        doThrow(new ReglaNegocioException("Factor no valido")).when(mfa).comprobarFactorPropio(42,"incorrecto");
        assertThrows(ReglaNegocioException.class,()->service.solicitarCambioCorreo(SID,4,"nuevo@example.test",new char[]{'x'},"incorrecto"));
        verifyNoInteractions(enlaces,sesiones,tokens);assertEquals("persona@example.test",cuenta.getCorreoPropio());
    }
    @Test void confirmarNuevoCorreoRevocaTodasLasSesionesYRecuperacionesDelAnterior() {
        var prueba=prueba(42L,"nuevo@example.test");when(enlaces.buscar("prueba",CAMBIO_CORREO)).thenReturn(prueba);
        service.confirmarCambioCorreo("prueba");assertEquals("nuevo@example.test",cuenta.getCorreoPropio());
        assertNotNull(cuenta.getSesionesInvalidasDesde());verify(sesiones).revocarDeCuenta(42);
        verify(tokens).invalidarVivosDe(eq(42L),eq("RECUPERACION"),any());verify(enlaces).consumir(prueba,CAMBIO_CORREO);
        verifyNoInteractions(contextos,contrasenas,mfa);
    }
    @Test void pruebaDeCorreoAnteriorAUnaRevocacionNoReautorizaCambioGlobal() {
        var prueba=prueba(42L,"nuevo@example.test");when(enlaces.buscar("prueba",CAMBIO_CORREO)).thenReturn(prueba);
        cuenta.setSesionesInvalidasDesde(prueba.getCreadoEn().plusSeconds(1));
        assertThrows(ReglaNegocioException.class,()->service.confirmarCambioCorreo("prueba"));
        verify(enlaces,never()).consumir(any(),any());verifyNoInteractions(sesiones,tokens);
        assertEquals("persona@example.test",cuenta.getCorreoPropio());
    }
    @Test void mfaPropioSiempreRecibeLaCuentaDeLaSesionYNoUnActorLocal() {
        when(mfa.confirmar(42,"123456")).thenReturn(List.of("respaldo"));
        assertEquals(List.of("respaldo"),service.confirmarMfa(SID,4,"123456"));
        verify(contextos).exigirIdentidad(SID,4);verify(mfa).confirmar(42,"123456");
        verifyNoInteractions(cuentas,enlaces,contrasenas,sesiones);
    }
    @Test void contrasenaPendienteNiegaMfaYCorreoAntesDeDelegarEnCore() {
        restricciones(true,false);
        assertThrows(AccesoNoAutorizadoException.class,()->service.estadoMfa(SID,4));
        assertThrows(AccesoNoAutorizadoException.class,()->service.iniciarMfa(SID,4));
        assertThrows(AccesoNoAutorizadoException.class,()->service.confirmarMfa(SID,4,"codigo"));
        assertThrows(AccesoNoAutorizadoException.class,()->service.regenerarCodigosMfa(SID,4,new char[0],"codigo"));
        assertThrows(AccesoNoAutorizadoException.class,()->service.revocarMfaPropio(SID,4,new char[0],"codigo",null,null));
        assertThrows(AccesoNoAutorizadoException.class,()->service.solicitarCambioCorreo(SID,4,"nuevo@example.test",new char[0],"codigo"));
        verifyNoInteractions(cuentas,mfa,contrasenas,enlaces,tokens,sesiones,auditoria);
    }
    @ParameterizedTest @CsvSource({"false,false","false,true","true,false","true,true"})
    void consultaYContrasenaRespetanLaPrioridadDeLosCuatroEstadosDeAcceso(boolean debePwd,boolean debeMfa) {
        restricciones(debePwd,debeMfa);
        when(cuentas.vista(42)).thenReturn(Optional.of(new CuentaAccesoFila(42,"referencia","persona@example.test",
                cuenta.getCorreoConfirmadoEn(),null,"A",null,debePwd,debeMfa)));
        var ficha=service.consultarPropia(SID,4);
        assertEquals(debePwd,ficha.debeCambiarContrasena());assertEquals(debeMfa,ficha.debeEnrolarMfa());
        char[] actual="actual".toCharArray(),nueva="nueva".toCharArray();
        if(!debePwd&&debeMfa) {
            assertThrows(AccesoNoAutorizadoException.class,()->service.cambiarContrasena(SID,4,actual,nueva));
            verifyNoInteractions(contrasenas);
        } else {service.cambiarContrasena(SID,4,actual,nueva);verify(contrasenas).cambiar(42,actual,nueva);}
        verifyNoInteractions(mfa,enlaces,tokens,sesiones);
    }
    @Test void enrolamientoPendientePermiteConsultarIniciarYConfirmarSuFactor() {
        restricciones(false,true);
        var estado=new MfaService.EstadoFactor(false,true,0,null);
        when(mfa.estado(42)).thenReturn(estado);when(mfa.confirmar(42,"codigo")).thenReturn(List.of("respaldo"));
        assertSame(estado,service.estadoMfa(SID,4));service.iniciarMfa(SID,4);
        assertEquals(List.of("respaldo"),service.confirmarMfa(SID,4,"codigo"));
        verify(mfa).estado(42);verify(mfa).iniciar(42);verify(mfa).confirmar(42,"codigo");
        verifyNoInteractions(cuentas,contrasenas,enlaces,tokens,sesiones);
    }
    @Test void enrolamientoPendienteNiegaRegenerarRevocarYCambiarCorreoAntesDeDelegar() {
        restricciones(false,true);
        assertThrows(AccesoNoAutorizadoException.class,()->service.regenerarCodigosMfa(SID,4,new char[0],"codigo"));
        assertThrows(AccesoNoAutorizadoException.class,()->service.revocarMfaPropio(SID,4,new char[0],"codigo",null,null));
        assertThrows(AccesoNoAutorizadoException.class,()->service.solicitarCambioCorreo(SID,4,"nuevo@example.test",new char[0],"codigo"));
        verifyNoInteractions(cuentas,mfa,contrasenas,enlaces,tokens,sesiones,auditoria);
    }
    @Test void sinPendientesPermiteRegenerarYRevocarElFactorPropio() {
        restricciones(false,false);char[] actual="actual".toCharArray();
        when(mfa.regenerarCodigos(42,actual,"codigo")).thenReturn(List.of("nuevo respaldo"));
        assertEquals(List.of("nuevo respaldo"),service.regenerarCodigosMfa(SID,4,actual,"codigo"));
        service.revocarMfaPropio(SID,4,actual,"codigo",null,null);
        verify(mfa).regenerarCodigos(42,actual,"codigo");verify(mfa).revocarPropio(42,actual,"codigo",null,null);
        verifyNoInteractions(cuentas,contrasenas,enlaces,tokens,sesiones);
    }
    private void restricciones(boolean contrasena,boolean enrolamiento) {
        var identidad=new ContextosService.IdentidadAutenticada(42,"referencia","persona@example.test",null);
        when(contextos.exigirIdentidad(SID,4)).thenReturn(new ContextosService.SesionAccesoDTO(SID,4,identidad,null,
                Instant.now().minusSeconds(5),Instant.now().plusSeconds(1800),new EstadoDeAcceso(null,contrasena,enrolamiento,"IDENTIDAD")));
    }
    private TokenAcceso prueba(Long cuenta,String correo) {
        var p=new TokenAcceso();p.setIdCuenta(cuenta);p.setCorreoObjetivo(correo);
        p.setCreadoEn(OffsetDateTime.now().minusMinutes(1));p.setExpiraEn(OffsetDateTime.now().plusMinutes(29));return p;
    }
}
