package com.controllocal.service.impl;
import com.controllocal.domain.seguridad.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.excepcion.ErrorMfaException;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class MfaRevocacionDeRetoTest {
 private final FactorAutenticacionRepository factores=mock(FactorAutenticacionRepository.class);
 private final CodigoRespaldoMfaRepository codigos=mock(CodigoRespaldoMfaRepository.class);
 private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
 private final TokenAccesoRepository tokens=mock(TokenAccesoRepository.class);
 private final BloqueoMfa bloqueo=mock(BloqueoMfa.class);
 private final MfaServiceImpl servicio=new MfaServiceImpl(factores,codigos,cuentas,tokens,mock(UsuarioOrganizacionRepository.class),
   mock(OrganizacionRepository.class),mock(CifradoSecretos.class),mock(GobiernoOperativo.class),bloqueo,mock(EventosSeguridad.class));
 @Test void retoAnteriorACambioDeContrasenaNoConservaPrueba(){rechazaAnterior(1);}
 @Test void retoCoincidenteConMarcaGlobalQuedaRevocado(){rechazaAnterior(0);}
 private void rechazaAnterior(long delta){
  OffsetDateTime creado=OffsetDateTime.now().minusMinutes(1);var t=reto(creado);var c=cuenta();
  when(tokens.buscarPorHashYTipo(anyString(),eq(TokenAcceso.TIPO_DESAFIO_MFA))).thenReturn(Optional.of(t));
  when(cuentas.bloquearCambioAcceso(10)).thenReturn(Optional.of(c));
  when(cuentas.accesoActual(10)).thenReturn(Optional.of(new AccesoAutenticacionFila(10,c.getCorreoPropio(),c.getCorreoConfirmadoEn(),"titular","no-usado",creado.plusSeconds(delta))));
  assertEquals(ErrorMfaException.DESAFIO_INVALIDO,assertThrows(ErrorMfaException.class,()->servicio.verificarDesafio("reto","123456",null,null)).getCodigo());
  verifyNoInteractions(factores,codigos,bloqueo);verify(tokens,never()).consumirDesafio(anyLong());
 }
 @Test void caducidadSeRevalidaDespuesDeEsperarCuenta(){
  var t=reto(OffsetDateTime.now());when(tokens.buscarPorHashYTipo(anyString(),eq(TokenAcceso.TIPO_DESAFIO_MFA))).thenReturn(Optional.of(t));
  when(cuentas.bloquearCambioAcceso(10)).thenAnswer(i->{t.setExpiraEn(OffsetDateTime.now().minusSeconds(1));return Optional.of(cuenta());});
  assertEquals(ErrorMfaException.DESAFIO_VENCIDO,assertThrows(ErrorMfaException.class,()->servicio.verificarDesafio("reto","123456",null,null)).getCodigo());
  verifyNoInteractions(factores,codigos,bloqueo);verify(tokens,never()).consumirDesafio(anyLong());
 }
 private CuentaAcceso cuenta(){var c=new CuentaAcceso();c.setId(10L);c.setCorreoPropio("titular@brox.test");c.setCorreoConfirmadoEn(Instant.parse("2026-01-01T00:00:00Z"));return c;}
 private TokenAcceso reto(OffsetDateTime creado){var t=new TokenAcceso();t.setTipo(TokenAcceso.TIPO_DESAFIO_MFA);t.setIdCuenta(10L);t.setCreadoEn(creado);t.setExpiraEn(OffsetDateTime.now().plusMinutes(4));return t;}
}
