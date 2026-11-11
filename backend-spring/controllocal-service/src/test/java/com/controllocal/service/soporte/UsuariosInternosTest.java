package com.controllocal.service.soporte;
import com.controllocal.domain.organizacion.UsuarioOrganizacion;
import com.controllocal.domain.persona.*;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.domain.seguridad.CuentaAcceso;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.excepcion.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
/** Vínculo local posterior a aceptación comprobada; no certifica registro ni autenticación de cuenta. */
class UsuariosInternosTest {
 private final PersonaRolRepository roles=mock(PersonaRolRepository.class);
 private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
 private final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
 private final SesionAccesoRepository sesiones=mock(SesionAccesoRepository.class);
 private final Clock reloj=Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"),ZoneOffset.UTC);
 private final UsuariosInternos servicio=new UsuariosInternos(roles,cuentas,miembros,sesiones,reloj);
 private CuentaAcceso cuenta;private Persona persona;
 @BeforeEach void preparar(){
  cuenta=new CuentaAcceso();cuenta.setId(10L);cuenta.setCorreoPropio("propio@brox.test");cuenta.setCorreoConfirmadoEn(Instant.parse("2026-01-01T00:00:00Z"));
  persona=new Persona();persona.setOrganizacionId(7L);ReflectionTestUtils.setField(persona,"id",9L);
  when(cuentas.bloquearCambioAcceso(10)).thenReturn(Optional.of(cuenta));when(cuentas.bloquearLecturaContexto(10)).thenReturn(Optional.of(cuenta));
  when(roles.save(any())).thenAnswer(i->{PersonaRol r=i.getArgument(0);ReflectionTestUtils.setField(r,"id",r.getTipoRol()==TipoRol.ADMIN?301L:201L);return r;});
  when(miembros.save(any())).thenAnswer(i->{UsuarioOrganizacion m=i.getArgument(0);if(m.getId()==null)ReflectionTestUtils.setField(m,"id",42L);return m;});
 }
 @Test void aceptarVinculaRolesLocalesSinCrearNiModificarCuenta(){
  var v=servicio.vincularPertenenciaAceptada(7,10,persona,TipoRol.BROKER,true);
  assertEquals(LocalDate.of(2026,10,27),v.rolOperativo().getVigenciaDesde());
  assertEquals(reloj.instant(),v.pertenencia().getAltaRealEn().toInstant());assertNull(v.pertenencia().getBajaRealEn());
  assertEquals(TipoRol.BROKER,v.rolOperativo().getTipoRol());assertTrue(v.pertenencia().isAdministrar());assertEquals("BROKER",v.pertenencia().getRol());
  assertFalse(cuenta.isDebeEnrolarMfa());assertNull(cuenta.getContrasenaHash());assertEquals("propio@brox.test",cuenta.getCorreoPropio());
  verify(cuentas,never()).save(any());verify(cuentas,never()).saveAndFlush(any());
 }
 @Test void pertenenciaActivaInclusoEnOtroEspacioImpideSegundaAlta(){
  when(miembros.contextosDeCuenta(10)).thenReturn(List.of(new MembresiaContextoFila(1,10,8,11L,null,true,"A","A","Otra","EMPRESARIAL")));
  assertThrows(ReglaNegocioException.class,()->servicio.vincularPertenenciaAceptada(7,10,persona,TipoRol.BROKER,false));
  verify(miembros,never()).save(any());
 }
 @Test void reingresoReutilizaSoloRelacionHistoricaExacta(){
  var m=miembro();m.setEstado("I");m.setBajaRealEn(OffsetDateTime.parse("2026-10-27T20:00:00Z"));when(miembros.buscarPorCuenta(7,10)).thenReturn(Optional.of(m));
  var v=servicio.vincularPertenenciaAceptada(7,10,persona,TipoRol.BROKER,false);
  assertSame(m,v.pertenencia());assertEquals("A",m.getEstado());assertEquals(42L,m.getId());
  assertEquals(reloj.instant(),m.getAltaRealEn().toInstant());assertNull(m.getBajaRealEn());
  verify(sesiones).revocarDeMembresia(42);
 }
 @Test void otraPersonaNoSeFusionaParaReingreso(){
  var m=miembro();m.setEstado("I");m.setIdPersona(99L);when(miembros.buscarPorCuenta(7,10)).thenReturn(Optional.of(m));
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.vincularPertenenciaAceptada(7,10,persona,TipoRol.BROKER,false));
 }
 @Test void administracionLocalUsaUnLoteSinLeerSeguridadGlobal(){
  when(miembros.administracionesDePersonas(7,List.of(9L,11L))).thenReturn(List.of(new Object[]{9L,true},new Object[]{11L,false}));
  assertEquals(Map.of(9L,true,11L,false),servicio.administracionesLocalesPorPersona(7,List.of(9L,11L)));
  verify(miembros).administracionesDePersonas(7,List.of(9L,11L));verifyNoInteractions(cuentas,roles,sesiones);
 }
 @Test void reingresoComoBrokerNoBorraRolAgenteHistoricoNiLoEjercita(){
  var m=miembro();m.setEstado("I");m.setRol("AGENTE");when(miembros.buscarPorCuenta(7,10)).thenReturn(Optional.of(m));
  var anterior=new PersonaRol();anterior.setTipoRol(TipoRol.AGENTE);
  when(roles.buscarVigente(9L,TipoRol.AGENTE)).thenReturn(Optional.of(anterior));
  var v=servicio.vincularPertenenciaAceptada(7,10,persona,TipoRol.BROKER,false);
  assertEquals("BROKER",v.pertenencia().getRol());assertEquals(TipoRol.BROKER,v.rolOperativo().getTipoRol());
  assertNull(anterior.getVigenciaHasta());verify(roles,never()).buscarVigente(9L,TipoRol.AGENTE);
 }
 @Test void estadoDeOtraFuncionNoMarcaAgenteActivo(){
  when(miembros.estadosDePersonasDelRol(7,List.of(9L),"AGENTE")).thenReturn(List.of());
  assertNull(servicio.estadoLocalDelRol(7,9,"AGENTE"));verifyNoInteractions(cuentas,roles,sesiones);
 }
 private UsuarioOrganizacion miembro(){var m=new UsuarioOrganizacion();m.setOrganizacionId(7L);m.setIdUsuario(10L);m.setIdPersona(9L);m.setRol("BROKER");ReflectionTestUtils.setField(m,"id",42L);return m;}
}
