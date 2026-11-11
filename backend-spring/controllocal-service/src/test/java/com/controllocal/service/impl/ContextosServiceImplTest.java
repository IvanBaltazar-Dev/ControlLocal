package com.controllocal.service.impl;
import com.controllocal.domain.organizacion.UsuarioOrganizacion;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.domain.seguridad.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
/** Cuenta personal sin organización, selección explícita y revisiones por sesión. */
class ContextosServiceImplTest {
 private static final String MAIL="titular@brox.test",PASS="ClavePersonal2026!",HASH=PasswordHasher.hash(PASS.toCharArray());
 private final CuentaAccesoRepository cuentas=mock(CuentaAccesoRepository.class);
 private final SesionAccesoRepository sesiones=mock(SesionAccesoRepository.class);
 private final UsuarioOrganizacionRepository miembros=mock(UsuarioOrganizacionRepository.class);
 private final PersonaRolRepository roles=mock(PersonaRolRepository.class);
 private final FactorAutenticacionRepository factores=mock(FactorAutenticacionRepository.class);
 private final EventosSeguridad eventos=mock(EventosSeguridad.class);private final MfaService mfa=mock(MfaService.class);
 private final Reloj reloj=new Reloj();
 private final ContextosServiceImpl servicio=new ContextosServiceImpl(cuentas,sesiones,miembros,roles,eventos,mfa,factores,reloj);
 private final Map<String,SesionAcceso> estado=new HashMap<>();
 private final List<MembresiaContextoFila> pertenencias=new ArrayList<>();private final List<RolDatoTest> atribuciones=new ArrayList<>();
 private CuentaAccesoFila cuenta;private CuentaAcceso canonica;
 @BeforeEach void preparar(){
  cuenta=fila("A",Instant.parse("2026-01-01T00:00:00Z"),null,false,false);
  canonica=new CuentaAcceso();canonica.setId(10L);canonica.setNombreUsuario("titular");canonica.setCorreoPropio(MAIL);
  canonica.setCorreoConfirmadoEn(cuenta.correoConfirmadoEn());canonica.setContrasenaHash(HASH);
  when(cuentas.vista(10)).thenAnswer(i->Optional.of(cuenta));
  when(cuentas.buscarPorCorreo(anyString())).thenAnswer(i->MAIL.equalsIgnoreCase(i.getArgument(0))?Optional.of(canonica):Optional.empty());
  when(cuentas.bloquearLecturaContexto(10)).thenReturn(Optional.of(canonica));when(cuentas.bloquearCambioAcceso(10)).thenReturn(Optional.of(canonica));
  when(cuentas.findById(10L)).thenReturn(Optional.of(canonica));
  when(cuentas.accesoActual(10)).thenAnswer(i->Optional.of(new AccesoAutenticacionFila(10,MAIL,cuenta.correoConfirmadoEn(),"titular",HASH,cuenta.sesionesInvalidasDesde())));
  when(cuentas.saveAndFlush(any())).thenAnswer(i->{var c=(CuentaAcceso)i.getArgument(0);cuenta=fila(c.getEstado(),c.getCorreoConfirmadoEn(),c.getSesionesInvalidasDesde(),c.isDebeCambiarContrasena(),c.isDebeEnrolarMfa());return c;});
  when(miembros.lecturaContextosDeCuenta(10)).thenAnswer(i->filasDePrueba());
  when(miembros.bloquearParaContexto(anyLong(),eq(10L))).thenReturn(Optional.of(new UsuarioOrganizacion()));
  when(roles.bloquearParaContexto(anyLong())).thenReturn(Optional.of(new PersonaRol()));
  when(sesiones.saveAndFlush(any())).thenAnswer(i->{SesionAcceso s=i.getArgument(0);if(s.getId()==null)ReflectionTestUtils.setField(s,"id",(long)estado.size()+1);estado.put(s.getReferenciaHash(),s);return s;});
  when(sesiones.vista(anyString())).thenAnswer(i->Optional.ofNullable(estado.get(i.getArgument(0))).map(this::sesion));
  when(sesiones.bloquear(anyString())).thenAnswer(i->Optional.ofNullable(estado.get(i.getArgument(0))));
  when(sesiones.bloquearLecturaContexto(anyString())).thenAnswer(i->Optional.ofNullable(estado.get(i.getArgument(0))));
  pertenencias.add(new MembresiaContextoFila(1,10,1,101L,"BROKER",true,"A","A","Empresa","EMPRESARIAL"));
  atribuciones.add(new RolDatoTest(201,1,101,"A",TipoRol.BROKER,LocalDate.of(2025,1,1),null));
  atribuciones.add(new RolDatoTest(301,1,101,"A",TipoRol.ADMIN,LocalDate.of(2025,1,1),null));
 }
 // Datos relacionales TEST: no hay DTO/consulta de roles anterior en Main.
 private record RolDatoTest(long id,long idOrganizacion,long idPersona,String estadoPersona,
  TipoRol tipoRol,LocalDate desde,LocalDate hasta) { }
 private List<ContextoCuentaFila> filasDePrueba(){
  List<ContextoCuentaFila> filas=new ArrayList<>();
  for(var m:pertenencias){boolean unido=false;
   for(var r:atribuciones)if(m.idPersona()!=null&&m.idOrganizacion()==r.idOrganizacion()&&m.idPersona()==r.idPersona()){
    filas.add(filaContexto(m,r));unido=true;
   }
   if(!unido)filas.add(filaContexto(m,null));
  }
  return filas;
 }
 private static ContextoCuentaFila filaContexto(MembresiaContextoFila m,RolDatoTest r){
  return new ContextoCuentaFila(m.id(),m.idOrganizacion(),m.idPersona(),m.rol(),m.administrar(),m.estado(),
   m.estadoOrganizacion(),m.nombreOrganizacion(),m.modalidad(),r==null?null:r.id(),r==null?null:r.estadoPersona(),
   r==null?null:r.tipoRol(),r==null?null:r.desde(),r==null?null:r.hasta());
 }
 @AfterEach void limpiar(){TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);}
 private CuentaAccesoFila fila(String estado,Instant confirmada,OffsetDateTime revocadas,boolean pwd,boolean mfa){return new CuentaAccesoFila(10,"titular",MAIL,confirmada,"Profesional",estado,revocadas,pwd,mfa);}
 private SesionAccesoFila sesion(SesionAcceso s){return new SesionAccesoFila(s.getId(),10,s.getRevision(),s.getCreadaEn(),s.getEmitidaEn(),s.getVenceEn(),s.isRevocada(),s.getIdMembresia(),s.getIdRolOperativo(),s.getRolEfectivo(),s.getSemillaContextos(),s.getContextoRefHash());}
 private ContextosService.SesionAccesoDTO abrir(){return servicio.iniciarAcceso(MAIL,PASS.toCharArray(),true).sesion();}
 private ContextosService.SesionAccesoDTO elegir(ContextosService.SesionAccesoDTO s,String rol){var c=servicio.contextos(s.referencia(),s.revision()).stream().filter(x->x.rol().equals(rol)).findFirst().orElseThrow();return servicio.seleccionar(s.referencia(),s.revision(),c.referencia());}
 @Test void identidadNoEligeContextoNiPrioridad(){var s=abrir();assertNull(s.activo());assertEquals("IDENTIDAD",s.restricciones().rolEfectivo());assertEquals(2,servicio.contextos(s.referencia(),s.revision()).size());assertEquals(0,s.emitidaEn().getNano());}
 @Test void sinOrganizacionConservaAccesoPersonal(){pertenencias.clear();atribuciones.clear();var s=abrir();assertTrue(servicio.contextos(s.referencia(),s.revision()).isEmpty());assertEquals(10,servicio.exigirIdentidad(s.referencia(),s.revision()).identidad().idCuenta());}
 @Test void modalidadNoDeclaradaNoFabricaAutoridadComercial(){pertenencias.clear();pertenencias.add(new MembresiaContextoFila(1,10,1,101L,"BROKER",true,"A","A","Sin declarar",null));var s=abrir();assertTrue(servicio.contextos(s.referencia(),s.revision()).isEmpty());assertEquals(10,servicio.exigirIdentidad(s.referencia(),s.revision()).identidad().idCuenta());}
 @Test void correoNoConfirmadoNoAutentica(){cuenta=fila("A",null,null,false,false);assertThrows(CredencialesInvalidasException.class,this::abrir);verifyNoInteractions(sesiones);}
 @Test void contrasenaIncorrectaNoCreaSesion(){assertThrows(CredencialesInvalidasException.class,()->servicio.iniciarAcceso(MAIL,"Incorrecta2026!".toCharArray(),true));verifyNoInteractions(sesiones);}
 @Test void nombreUsuarioNoEsViaAlternativaDeAcceso(){assertThrows(CredencialesInvalidasException.class,()->servicio.iniciarAcceso("titular",PASS.toCharArray(),true));verifyNoInteractions(sesiones);}
 @Test void gobiernoYBrokerNoSumanFacultades(){var g=elegir(abrir(),Actor.TENANT_ADMIN);assertEquals(301,g.activo().idRolOperativo());assertTrue(g.restricciones().debeEnrolarMfa());var b=elegir(g,Actor.BROKER);assertEquals(201,b.activo().idRolOperativo());assertFalse(b.restricciones().debeEnrolarMfa());assertFalse(canonica.isDebeEnrolarMfa());}
 @Test void contextoInventadoNoEsSeleccionable(){var s=abrir();assertThrows(AccesoNoAutorizadoException.class,()->servicio.seleccionar(s.referencia(),s.revision(),"f".repeat(64)));}
 @Test void gobiernoSinPrincipalNoSeConvierteEnBroker(){pertenencias.clear();atribuciones.removeIf(r->r.tipoRol()==TipoRol.BROKER);pertenencias.add(new MembresiaContextoFila(1,10,1,101L,null,true,"A","A","Empresa","EMPRESARIAL"));var s=abrir();var opciones=servicio.contextos(s.referencia(),s.revision());assertEquals(1,opciones.size());assertEquals(Actor.TENANT_ADMIN,opciones.getFirst().rol());}
 @Test void brokerActualNoEjercitaAgenteHistoricoDeLaMismaPersona(){pertenencias.clear();pertenencias.add(new MembresiaContextoFila(1,10,1,101L,"BROKER",false,"A","A","Empresa","EMPRESARIAL"));atribuciones.add(new RolDatoTest(401,1,101,"A",TipoRol.AGENTE,LocalDate.of(2024,1,1),null));var s=abrir();var opciones=servicio.contextos(s.referencia(),s.revision());assertEquals(1,opciones.size());assertEquals(Actor.BROKER,opciones.getFirst().rol());assertEquals(201,opciones.getFirst().idRolOperativo());}
 @Test void brokerAdminBrokerNoReviveAprobacionAnterior(){var a=elegir(abrir(),Actor.BROKER);var b=elegir(a,Actor.TENANT_ADMIN);var a2=elegir(b,Actor.BROKER);assertEquals(a.emitidaEn(),a2.emitidaEn());assertEquals(a.revision()+2,a2.revision());assertNotEquals(a.activo().referencia(),a2.activo().referencia());assertThrows(CredencialesInvalidasException.class,()->servicio.comprobarSesion(a.referencia(),a.revision(),null,null));}
 @Test void renovarMismoSegundoCambiaRevision(){var s=elegir(abrir(),Actor.BROKER);var r=servicio.renovar(s.referencia(),s.revision());assertEquals(s.emitidaEn(),r.emitidaEn());assertThrows(CredencialesInvalidasException.class,()->servicio.contextos(s.referencia(),s.revision()));}
 @Test void unDispositivoNoSeleccionaOtro(){var a=abrir();var b=elegir(abrir(),Actor.BROKER);elegir(a,Actor.TENANT_ADMIN);assertEquals(Actor.BROKER,servicio.comprobarSesion(b.referencia(),b.revision(),null,null).activo().rol());}
 @Test void bajaLocalInvalidaSesionSeleccionada(){var s=elegir(abrir(),Actor.BROKER);pertenencias.clear();assertThrows(CredencialesInvalidasException.class,()->servicio.comprobarSesion(s.referencia(),s.revision(),null,null));}
 @Test void rolRetiradoNoSeDegradaNiSeSustituye(){var s=elegir(abrir(),Actor.BROKER);atribuciones.removeIf(r->r.tipoRol()==TipoRol.BROKER);assertThrows(CredencialesInvalidasException.class,()->servicio.comprobarSesion(s.referencia(),s.revision(),null,null));}
 @Test void cuentaGlobalSuspendidaNoQuedaIrrestricta(){var s=abrir();cuenta=fila("I",cuenta.correoConfirmadoEn(),null,false,false);assertThrows(CredencialesInvalidasException.class,()->servicio.comprobarSesion(s.referencia(),s.revision(),null,null));}
 @Test void claimsTemporalesSeLigaronALaFilaReal(){var s=abrir();assertThrows(CredencialesInvalidasException.class,()->servicio.comprobarSesion(s.referencia(),s.revision(),s.emitidaEn().plusSeconds(1),s.venceEn()));}
 @Test void repeatableReadNoAutorizaConSnapshotViejo(){var s=abrir();TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);assertThrows(IllegalStateException.class,()->servicio.comprobarSesion(s.referencia(),s.revision(),null,null));}
 @Test void efectoExigeActorExactoYBloqueaRol(){var s=elegir(abrir(),Actor.BROKER);var a=new Actor(1,101,201,Actor.BROKER);var actual=servicio.exigirContexto(s.referencia(),s.revision(),a);assertEquals(201,actual.activo().idRolOperativo());verify(roles).bloquearParaContexto(201);assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigirContexto(s.referencia(),s.revision(),new Actor(1,101,301,Actor.TENANT_ADMIN)));}
 @Test void revocacionNoBloqueaSesionPosteriorConMismoIat(){var antigua=abrir();cuenta=fila("A",cuenta.correoConfirmadoEn(),OffsetDateTime.parse("2026-10-27T18:34:11.200000Z"),false,false);reloj.ahora=Instant.parse("2026-10-27T18:34:11.300000Z");var nueva=abrir();assertEquals(antigua.emitidaEn(),nueva.emitidaEn());assertNotEquals(antigua.referencia(),nueva.referencia());assertThrows(CredencialesInvalidasException.class,()->servicio.comprobarSesion(antigua.referencia(),antigua.revision(),null,null));assertEquals(nueva.referencia(),servicio.comprobarSesion(nueva.referencia(),nueva.revision(),null,null).referencia());}
 @Test void dosPertenenciasActivasIncluidoGobiernoSeRechazan(){pertenencias.add(new MembresiaContextoFila(2,10,2,102L,null,true,"A","A","Otra","EMPRESARIAL"));var s=abrir();assertThrows(CredencialesInvalidasException.class,()->servicio.contextos(s.referencia(),s.revision()));}
 @Test void loginNoOmiteFactorRequerido(){when(mfa.exigeSegundoFactor(10)).thenReturn(true);assertThrows(CredencialesInvalidasException.class,()->servicio.iniciarAcceso(MAIL,PASS.toCharArray(),false));verifyNoInteractions(sesiones);}
 @Test void desafioNoCreaSesion(){when(mfa.exigeSegundoFactor(10)).thenReturn(true);var d=new MfaService.Desafio("reto",OffsetDateTime.now().plusMinutes(5));when(mfa.emitirDesafio(10)).thenReturn(d);var r=servicio.iniciarAcceso(MAIL,PASS.toCharArray(),true);assertNull(r.sesion());assertEquals(d,r.desafio());verifyNoInteractions(sesiones);}
 @Test void factorUsaCuentaDelReto(){when(mfa.verificarDesafio("reto","123456",null,null)).thenReturn(new MfaService.Verificacion(10,false,8));assertEquals(10,servicio.completarSegundoFactor("reto","123456",null,null).identidad().idCuenta());}
 @Test void cambiarContrasenaPendienteBloqueaNegocioPeroPermiteIdentidad(){var s=elegir(abrir(),Actor.BROKER);cuenta=fila("A",cuenta.correoConfirmadoEn(),null,true,false);assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigirContexto(s.referencia(),s.revision(),new Actor(1,101,201,Actor.BROKER)));assertEquals(10,servicio.exigirIdentidad(s.referencia(),s.revision()).identidad().idCuenta());}
 @Test void lecturaProtegidaConservaRevisionYSoloBloqueaCuentaYSidParaLeer(){
  var s=elegir(abrir(),Actor.BROKER);clearInvocations(cuentas,sesiones);
  var actual=servicio.exigirContextoConLecturaProtegida(s.referencia(),s.revision(),new Actor(1,101,201,Actor.BROKER));
  assertEquals(s.revision(),actual.revision());var orden=inOrder(cuentas,sesiones);
  orden.verify(cuentas).bloquearLecturaContexto(10);
  orden.verify(sesiones).bloquearLecturaContexto(Idempotencia.huellaExacta(s.referencia()));
  verify(cuentas,never()).bloquearCambioAcceso(anyLong());verify(sesiones,never()).bloquear(anyString());
  verify(sesiones,never()).saveAndFlush(any());
 }
 @Test void entradaAnteriorConservaSuLockExclusivoDeSid(){
  var s=elegir(abrir(),Actor.BROKER);clearInvocations(cuentas,sesiones);
  servicio.exigirContexto(s.referencia(),s.revision(),new Actor(1,101,201,Actor.BROKER));
  verify(sesiones).bloquear(Idempotencia.huellaExacta(s.referencia()));
  verify(sesiones,never()).bloquearLecturaContexto(anyString());
 }
 @Test void lecturaProtegidaNoAceptaRevisionRevocadaNiCambioDeContrasenaPendiente(){
  var s=elegir(abrir(),Actor.BROKER);var nueva=servicio.renovar(s.referencia(),s.revision());var actor=new Actor(1,101,201,Actor.BROKER);
  assertThrows(CredencialesInvalidasException.class,()->servicio.exigirContextoConLecturaProtegida(s.referencia(),s.revision(),actor));
  cuenta=fila("A",cuenta.correoConfirmadoEn(),null,true,false);
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigirContextoConLecturaProtegida(nueva.referencia(),nueva.revision(),actor));
 }
 @Test void opcionesUsaUnaLecturaSinListasSeparadasNiRolesPorFila(){
  var s=abrir();clearInvocations(miembros,roles);
  assertEquals(2,servicio.contextos(s.referencia(),s.revision()).size());
  verify(miembros,times(1)).lecturaContextosDeCuenta(10);verifyNoMoreInteractions(miembros);verifyNoInteractions(roles);
 }
 @Test void dosRolesDeUnMiembroNoSonDosPertenenciasYOrdenNoDependeDelJoin(){
  var s=abrir();var esperado=servicio.contextos(s.referencia(),s.revision());Collections.reverse(atribuciones);
  assertEquals(esperado,servicio.contextos(s.referencia(),s.revision()));
  assertEquals(List.of(Actor.BROKER,Actor.TENANT_ADMIN),esperado.stream().map(ContextosService.Contexto::rol).toList());
 }
 @Test void segundaPertenenciaActivaSinRolNiOrganizacionActivaSigueDenegando(){
  pertenencias.add(new MembresiaContextoFila(2,10,2,null,null,false,"A","I","Inactiva sin rol","EMPRESARIAL"));
  var s=abrir();assertThrows(CredencialesInvalidasException.class,()->servicio.contextos(s.referencia(),s.revision()));
 }
 @Test void miembroSinRolNoDesapareceDelConteoPeroNoOfreceContexto(){
  atribuciones.clear();var s=abrir();assertTrue(servicio.contextos(s.referencia(),s.revision()).isEmpty());
  pertenencias.add(new MembresiaContextoFila(2,10,2,102L,null,false,"A","I","Otra","INDIVIDUAL"));
  assertThrows(CredencialesInvalidasException.class,()->servicio.contextos(s.referencia(),s.revision()));
 }
 @Test void fechasUsanLimaYTodoHastaConocidoExcluyeAunqueSeaFuturo(){
  reloj.ahora=Instant.parse("2026-10-28T03:00:00Z"); // Lima continúa en27/10.
  var s=abrir();atribuciones.clear();
  for(var r:List.of(new RolDatoTest(201,1,101,"A",TipoRol.BROKER,null,null),
   new RolDatoTest(201,1,101,"A",TipoRol.BROKER,LocalDate.of(2026,10,28),null),
   new RolDatoTest(201,1,101,"A",TipoRol.BROKER,LocalDate.of(2025,1,1),LocalDate.of(2027,1,1)),
   new RolDatoTest(201,1,101,"I",TipoRol.BROKER,LocalDate.of(2025,1,1),null))){
   atribuciones.clear();atribuciones.add(r);assertTrue(servicio.contextos(s.referencia(),s.revision()).isEmpty());
  }
  atribuciones.clear();atribuciones.add(new RolDatoTest(201,1,101,"A",TipoRol.BROKER,LocalDate.of(2026,10,27),null));
  assertEquals(Actor.BROKER,servicio.contextos(s.referencia(),s.revision()).getFirst().rol());
 }
 @Test void organizacionOMiembroInactivoNoHabilitaRolAunVigente(){
  var s=abrir();for(var m:List.of(new MembresiaContextoFila(1,10,1,101L,"BROKER",true,"A","I","Empresa","EMPRESARIAL"),
   new MembresiaContextoFila(1,10,1,101L,"BROKER",true,"I","A","Empresa","EMPRESARIAL"))){
   pertenencias.clear();pertenencias.add(m);assertTrue(servicio.contextos(s.referencia(),s.revision()).isEmpty());
  }
 }
 @Test void individualConservaSuPrincipalSinFabricarBrokerNiGobierno(){
  pertenencias.clear();atribuciones.clear();
  pertenencias.add(new MembresiaContextoFila(1,10,1,101L,"AGENTE",false,"A","A","Individual","INDIVIDUAL"));
  atribuciones.add(new RolDatoTest(501,1,101,"A",TipoRol.AGENTE,LocalDate.of(2025,1,1),null));
  var s=abrir();var opciones=servicio.contextos(s.referencia(),s.revision());
  assertEquals(1,opciones.size());assertEquals(Actor.AGENTE,opciones.getFirst().rol());assertEquals("INDIVIDUAL",opciones.getFirst().modalidad());
 }
 static class Reloj extends Clock {Instant ahora=Instant.parse("2026-10-27T18:34:11.100000Z");public Instant instant(){return ahora;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return Clock.fixed(ahora,z);}}
}
