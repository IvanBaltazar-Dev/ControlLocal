package com.controllocal.service.plataforma;

import com.controllocal.persistence.presupuesto.AtribucionesPlataformaRepository;
import com.controllocal.persistence.presupuesto.AtribucionesPlataformaRepository.Concesion;
import com.controllocal.service.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.controllocal.service.plataforma.AtribucionesPlataforma.FINALIDAD_COMPRAS;

/** Guardas unitarias, sin credenciales/grants reales ni sustitución de los casos PG C1. */
class AtribucionesPlataformaTest {
 @Test void investigacionManualNoRecibeFinanzasNiPermisosDeEvaluacionApi(){
  var c=new Concesion(grant,7,7,"INVESTIGACION_PLATFORM",null,null,null,null,AtribucionesPlataforma.FINALIDAD_MERCADO,
   "[\"CONSULTAR_TRABAJO\",\"TOMAR_TRABAJO\"]","[\"METADATOS_TRABAJO\",\"CONTEXTO_MINIMO\"]",Instant.now().minusSeconds(10),Instant.now().plusSeconds(100),"Sólo TEST","e".repeat(64),null,null,RecursoMercado.MERCADO_MANUAL_I0.name());
  when(repo.bloquear(grant)).thenReturn(Optional.of(c));
  assertEquals(RecursoMercado.MERCADO_MANUAL_I0,servicio.exigirMercado(sid,Atribucion.INVESTIGACION_PLATFORM,Accion.TOMAR_TRABAJO,Set.of(Campo.CONTEXTO_MINIMO)).recursoMercado());
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigirMercado(sid,Atribucion.INVESTIGACION_PLATFORM,Accion.TOMAR_TRABAJO,Set.of(Campo.LEDGER)));
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigirMercado(sid,Atribucion.FINANCIERO_PLATFORM,Accion.CONCILIAR_TRABAJO,Set.of(Campo.LEDGER)));
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.resolver(sid,Atribucion.INVESTIGACION_PLATFORM,Accion.ADMITIR,Campo.ESTADO));
 }
 final ContextosService contextos=mock(ContextosService.class);
 final AtribucionesPlataformaRepository repo=mock(AtribucionesPlataformaRepository.class);
 @SuppressWarnings("unchecked") final ObjectProvider<AcreditacionesTitularPlataforma> proveedores=mock(ObjectProvider.class);
 final UUID grant=UUID.randomUUID(),plan=UUID.randomUUID(),caso=UUID.randomUUID();
 final SesionPlataforma sid=new SesionPlataforma("SID_TEST_NO_TOKEN",1,grant);
 final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules());
 final AtribucionesPlataforma servicio=new AtribucionesPlataforma(contextos,repo,proveedores,json);
 @BeforeEach void preparar(){
  var identidad=new ContextosService.IdentidadAutenticada(7,"TEST","test@example.invalid","TEST");
  when(contextos.exigirIdentidad(sid.referenciaSesion(),1)).thenReturn(new ContextosService.SesionAccesoDTO(
   sid.referenciaSesion(),1,identidad,null,Instant.now().minusSeconds(10),Instant.now().plusSeconds(600),EstadoDeAcceso.SIN_RESTRICCIONES));
  when(repo.bloquear(grant)).thenReturn(Optional.of(concesion(7,Instant.now().plusSeconds(100),null)));
 }
 Concesion concesion(long cuenta,Instant hasta,Instant revocada){
  return new Concesion(grant,7,cuenta,"OPERACION_IA_PLATFORM",plan,"TEST_V1",caso,null,"TEST",
   "[\"ADMITIR\",\"INICIAR\"]","[\"ESTADO\"]",Instant.now().minusSeconds(10),hasta,"TEST NO GRANT REAL","a".repeat(64),revocada);
 }
 Actual exigir(){return servicio.exigir(sid,Atribucion.OPERACION_IA_PLATFORM,Accion.ADMITIR,Campo.ESTADO,plan,"TEST_V1","TEST");}
 @Test void identidadSinPertenenciaNoSeConvierteEnActor(){assertEquals(7,exigir().cuenta());verify(contextos).exigirIdentidad(sid.referenciaSesion(),1);}
 @Test void sinAcreditadorAdministrativoSeDeniegaLaHabilitacion(){
  assertThrows(CapacidadDeshabilitadaException.class,()->servicio.acreditar(UUID.randomUUID(),"TEST","a".repeat(64),7L,sid));
 }
 @Test void referenciaConocidaNoAutorizaOtraCuenta(){
  when(repo.bloquear(grant)).thenReturn(Optional.of(concesion(8,Instant.now().plusSeconds(100),null)));
  assertThrows(AccesoNoAutorizadoException.class,this::exigir);
 }
 @Test void cuentaSinConcesionNoTienePrivilegioInicial(){when(repo.bloquear(grant)).thenReturn(Optional.empty());assertThrows(AccesoNoAutorizadoException.class,this::exigir);}
 @Test void unaConcesionRevocadaNoIniciaOtraActuacion(){
  when(repo.bloquear(grant)).thenReturn(Optional.of(concesion(7,Instant.now().plusSeconds(100),Instant.now())));
  assertThrows(AccesoNoAutorizadoException.class,this::exigir);
 }
 @Test void elVencimientoEsExcluyente(){
  when(repo.bloquear(grant)).thenReturn(Optional.of(concesion(7,Instant.now().minusSeconds(1),null)));
  assertThrows(AccesoNoAutorizadoException.class,this::exigir);
 }
 @Test void otroPlanNoHeredaElPermiso(){assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigir(sid,Atribucion.OPERACION_IA_PLATFORM,Accion.ADMITIR,Campo.ESTADO,UUID.randomUUID(),"TEST_V1","TEST"));}
 @Test void otraVersionNoHeredaElPermiso(){assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigir(sid,Atribucion.OPERACION_IA_PLATFORM,Accion.ADMITIR,Campo.ESTADO,plan,"TEST_V2","TEST"));}
 @Test void otraFinalidadNoHeredaElPermiso(){assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigir(sid,Atribucion.OPERACION_IA_PLATFORM,Accion.ADMITIR,Campo.ESTADO,plan,"TEST_V1","OTRA"));}
 @Test void operadorNoLeeLedgerExacto(){assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigir(sid,Atribucion.FINANCIERO_PLATFORM,Accion.LEER_LEDGER,Campo.LEDGER,plan,"TEST_V1","TEST"));}
 @Test void ejecutarNoConcedeLecturaDeMuestras(){assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigir(sid,Atribucion.OPERACION_IA_PLATFORM,Accion.ADMITIR,Campo.MUESTRA,plan,"TEST_V1","TEST"));}

 Concesion compra(long cuenta,Instant desde,Instant hasta,Instant revocada,String acciones,String campos){
  return new Concesion(grant,7,cuenta,"FINANCIERO_PLATFORM",null,null,null,null,FINALIDAD_COMPRAS,
   acciones,campos,desde,hasta,"TEST NO GRANT REAL","b".repeat(64),revocada,RecursoCompra.COMPRAS_MANUALES_WEB_MVP.name());
 }
 Concesion compraVigente(){return compra(7,Instant.now().minusSeconds(60),Instant.now().plusSeconds(600),null,
  "[\"CONSULTAR_COMPRA\",\"INICIAR_VERIFICACION\",\"VERIFICAR_FONDOS\",\"REGISTRAR_INCIDENCIA\"]",
  "[\"ESTADO\",\"PEDIDO_COMERCIAL\",\"DATOS_PAGO_VERIFICADO\"]");}
 Actual exigirCompra(){return servicio.exigirCompra(sid,Accion.VERIFICAR_FONDOS,
  Set.of(Campo.ESTADO,Campo.PEDIDO_COMERCIAL,Campo.DATOS_PAGO_VERIFICADO));}
 @Test void comprasUsanCuentaGlobalYConcesionSinInventarPlanFondoOActor(){
  when(repo.bloquear(grant)).thenReturn(Optional.of(compraVigente()));var actual=exigirCompra();
  assertEquals(7,actual.cuenta());assertEquals(grant,actual.concesion());assertEquals(FINALIDAD_COMPRAS,actual.finalidad());
  assertEquals(RecursoCompra.COMPRAS_MANUALES_WEB_MVP,actual.recursoCompra());
  assertAll(()->assertNull(actual.plan()),()->assertNull(actual.versionPlan()),()->assertNull(actual.caso()),()->assertNull(actual.fondo()));
  verify(contextos).exigirIdentidad(sid.referenciaSesion(),1);
 }
 @ParameterizedTest @ValueSource(strings={"CUENTA_AJENA","REVOCADA","VENCIDA","FUTURA"})
 void compraRevalidaTitularYVigencia(String caso){
  Instant ahora=Instant.now();var c=compra(caso.equals("CUENTA_AJENA")?8:7,
   caso.equals("FUTURA")?ahora.plusSeconds(60):ahora.minusSeconds(60),
   caso.equals("VENCIDA")?ahora.minusSeconds(1):ahora.plusSeconds(600),caso.equals("REVOCADA")?ahora:null,
   "[\"VERIFICAR_FONDOS\"]","[\"ESTADO\",\"PEDIDO_COMERCIAL\",\"DATOS_PAGO_VERIFICADO\"]");
  when(repo.bloquear(grant)).thenReturn(Optional.of(c));assertThrows(AccesoNoAutorizadoException.class,this::exigirCompra);
 }
 @Test void compraExigeTodosLosCamposSolicitadosYNoSoloElEstado(){
  var c=compra(7,Instant.now().minusSeconds(60),Instant.now().plusSeconds(600),null,"[\"VERIFICAR_FONDOS\"]","[\"ESTADO\"]");
  when(repo.bloquear(grant)).thenReturn(Optional.of(c));assertThrows(AccesoNoAutorizadoException.class,this::exigirCompra);
 }
 @Test void consultaNoAutorizaVerificarFondos(){
  var c=compra(7,Instant.now().minusSeconds(60),Instant.now().plusSeconds(600),null,"[\"CONSULTAR_COMPRA\"]",
   "[\"ESTADO\",\"PEDIDO_COMERCIAL\",\"DATOS_PAGO_VERIFICADO\"]");
  when(repo.bloquear(grant)).thenReturn(Optional.of(c));assertThrows(AccesoNoAutorizadoException.class,this::exigirCompra);
 }
 @Test void grantDeEvaluacionNoSeUsaParaCompra(){assertThrows(AccesoNoAutorizadoException.class,this::exigirCompra);}
 @Test void financieroDeEvaluacionConAccionesSimilaresTampocoEsScopeCompra(){
  var c=compraVigente();when(repo.bloquear(grant)).thenReturn(Optional.of(new Concesion(c.id(),c.otorgante(),c.cuenta(),c.atribucion(),
   plan,"TEST_V1",caso,UUID.randomUUID(),c.finalidad(),c.acciones(),c.campos(),c.desde(),c.hasta(),c.evidencia(),c.huella(),null)));
  assertThrows(AccesoNoAutorizadoException.class,this::exigirCompra);
 }
 @Test void compraNoSeUsaParaEvaluacionAunqueCompartaAtribucionOCampo(){
  when(repo.bloquear(grant)).thenReturn(Optional.of(compraVigente()));
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.resolver(sid,Atribucion.FINANCIERO_PLATFORM,Accion.CONSULTAR_COMPRA,Campo.ESTADO));
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.resolver(sid,Atribucion.FINANCIERO_PLATFORM,Accion.FINANCIAR,Campo.LEDGER));
 }
 @Test void scopeCompraSeRechazaAntesDeDereferenciarUnPlanAunqueLosPermisosFueranAlterados(){
  var c=compraVigente();when(repo.bloquear(grant)).thenReturn(Optional.of(new Concesion(c.id(),c.otorgante(),c.cuenta(),c.atribucion(),
   null,null,null,null,c.finalidad(),"[\"FINANCIAR\"]","[\"LEDGER\"]",c.desde(),c.hasta(),c.evidencia(),c.huella(),null,c.recursoCompra())));
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.resolver(sid,Atribucion.FINANCIERO_PLATFORM,Accion.FINANCIAR,Campo.LEDGER));
 }
 @Test void compraNoAceptaAccionesDeEvaluacionNiCamposPrivadosAjenos(){
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigirCompra(sid,Accion.FINANCIAR,Set.of(Campo.ESTADO)));
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigirCompra(sid,Accion.CONSULTAR_COMPRA,Set.of(Campo.LEDGER)));
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.exigirCompra(sid,Accion.CONSULTAR_COMPRA,Set.of()));
 }
 @ParameterizedTest @ValueSource(strings={"PLAN","VERSION","CASO","FONDO","FINALIDAD","ATRIBUCION"})
 void alcanceCompraMezcladoNoAutoriza(String defecto){
  var c=compraVigente();c=new Concesion(c.id(),c.otorgante(),c.cuenta(),defecto.equals("ATRIBUCION")?"BROX_PLATFORM":c.atribucion(),
   defecto.equals("PLAN")?plan:null,defecto.equals("VERSION")?"TEST":null,defecto.equals("CASO")?caso:null,
   defecto.equals("FONDO")?UUID.randomUUID():null,defecto.equals("FINALIDAD")?"AUDITORIA_TEST":c.finalidad(),c.acciones(),c.campos(),
   c.desde(),c.hasta(),c.evidencia(),c.huella(),null,c.recursoCompra());
  when(repo.bloquear(grant)).thenReturn(Optional.of(c));assertThrows(AccesoNoAutorizadoException.class,this::exigirCompra);
 }
 @Test void opcionesCompraSoloMuestranConcesionesPropiasVigentesYNoElPlanDeEvaluacion(){
  Instant ahora=Instant.now();var valida=compraVigente();
  when(repo.comprasVigentes(eq(7L),any())).thenReturn(List.of(valida,concesion(7,ahora.plusSeconds(600),null),
   compra(8,ahora.minusSeconds(60),ahora.plusSeconds(600),null,valida.acciones(),valida.campos()),
   compra(7,ahora.minusSeconds(60),ahora.plusSeconds(600),ahora,valida.acciones(),valida.campos()),
   compra(7,ahora.minusSeconds(60),ahora.minusSeconds(1),null,valida.acciones(),valida.campos())));
  var opciones=servicio.atribucionesCompra(sid.referenciaSesion(),1);assertEquals(1,opciones.size());
  assertEquals(grant,opciones.getFirst().referencia());assertEquals(FINALIDAD_COMPRAS,opciones.getFirst().finalidad());
  assertThrows(UnsupportedOperationException.class,()->opciones.getFirst().campos().add(Campo.LEDGER));
  verify(repo,never()).bloquear(any());
 }
 @Test void conocerUnaOpcionNoConservaPermisoTrasRevocarla(){
  var c=compraVigente();when(repo.comprasVigentes(eq(7L),any())).thenReturn(List.of(c));
  assertEquals(1,servicio.atribucionesCompra(sid.referenciaSesion(),1).size());
  when(repo.bloquear(grant)).thenReturn(Optional.of(compra(7,c.desde(),c.hasta(),Instant.now(),c.acciones(),c.campos())));
  assertThrows(AccesoNoAutorizadoException.class,this::exigirCompra);
 }
 @Test void opcionesCompraVaciasNoSeConviertenEnPrivilegioInicial(){
  when(repo.comprasVigentes(eq(7L),any())).thenReturn(List.of());
  assertTrue(servicio.atribucionesCompra(sid.referenciaSesion(),1).isEmpty());
 }
 @ParameterizedTest @ValueSource(strings={"CONTRASENA","MFA"})
 void comprasYListadoConservanLasRestriccionesDeLaCuenta(String restriccion){
  var actual=contextos.exigirIdentidad(sid.referenciaSesion(),1);
  when(contextos.exigirIdentidad(sid.referenciaSesion(),1)).thenReturn(new ContextosService.SesionAccesoDTO(
   actual.referencia(),actual.revision(),actual.identidad(),null,actual.emitidaEn(),actual.venceEn(),
   new EstadoDeAcceso(null,restriccion.equals("CONTRASENA"),restriccion.equals("MFA"),null)));
  assertThrows(AccesoNoAutorizadoException.class,this::exigirCompra);
  assertThrows(AccesoNoAutorizadoException.class,()->servicio.atribucionesCompra(sid.referenciaSesion(),1));
  verify(repo,never()).comprasVigentes(anyLong(),any());verify(repo,never()).bloquear(any());
 }
 @Test void sesionRevocadaNoSeOcultaComoListadoVacio(){
  when(contextos.exigirIdentidad(sid.referenciaSesion(),1)).thenThrow(new CredencialesInvalidasException());
  assertThrows(CredencialesInvalidasException.class,()->servicio.atribucionesCompra(sid.referenciaSesion(),1));
  verify(repo,never()).comprasVigentes(anyLong(),any());
 }
 Datos datosCompra(){return new Datos(grant,7,Atribucion.FINANCIERO_PLATFORM,null,null,null,null,FINALIDAD_COMPRAS,
  Set.of(Accion.CONSULTAR_COMPRA,Accion.VERIFICAR_FONDOS),Set.of(Campo.ESTADO,Campo.PEDIDO_COMERCIAL,Campo.DATOS_PAGO_VERIFICADO),
  Instant.parse("2026-11-01T00:00:00Z"),Instant.parse("2026-12-01T00:00:00Z"),"TEST NO GRANT REAL",RecursoCompra.COMPRAS_MANUALES_WEB_MVP);}
 @Test void concederCompraRequiereLaMismaDecisionAdministrativaAcreditada(){
  var d=datosCompra();UUID decision=UUID.randomUUID();String h=Idempotencia.huellaExacta(json.objetoCanonico(d));
  var acreditador=mock(AcreditacionesTitularPlataforma.class);when(proveedores.getIfAvailable()).thenReturn(acreditador);
  when(acreditador.acreditar(decision)).thenReturn(Optional.of(new AcreditacionesTitularPlataforma.Decision(decision,7,7L,
   "CONCEDER_ATRIBUCION",h,"ACREDITADOR TEST",Instant.now().minusSeconds(1))));
  when(repo.decision(decision)).thenReturn(Optional.of(new AtribucionesPlataformaRepository.DecisionRegistrada(7,"CONCEDER_ATRIBUCION",h)));
  when(repo.bloquear(grant)).thenReturn(Optional.empty());servicio.conceder(d,decision,sid);
  var creada=org.mockito.ArgumentCaptor.forClass(Concesion.class);verify(repo).insertar(creada.capture(),eq(decision),any());
  assertEquals(RecursoCompra.COMPRAS_MANUALES_WEB_MVP.name(),creada.getValue().recursoCompra());assertNull(creada.getValue().plan());assertNull(creada.getValue().fondo());
 }
 @ParameterizedTest @ValueSource(strings={"PLAN","FONDO","FINALIDAD","ATRIBUCION","ACCION","CAMPO"})
 void concederCompraNoPermiteMezclarAlcancesAntesDePedirAcreditacion(String defecto){
  var d=datosCompra();var otra=new Datos(d.id(),d.destinatario(),defecto.equals("ATRIBUCION")?Atribucion.OPERACION_IA_PLATFORM:d.atribucion(),
   defecto.equals("PLAN")?plan:null,null,null,defecto.equals("FONDO")?UUID.randomUUID():null,
   defecto.equals("FINALIDAD")?"OTRA_FINALIDAD":d.finalidad(),defecto.equals("ACCION")?Set.of(Accion.FINANCIAR):d.acciones(),
   defecto.equals("CAMPO")?Set.of(Campo.LEDGER):d.campos(),d.desde(),d.hasta(),d.evidencia(),d.recursoCompra());
  assertThrows(ReglaNegocioException.class,()->servicio.conceder(otra,UUID.randomUUID(),sid));verify(proveedores,never()).getIfAvailable();
 }
 @Test void datosDeEvaluacionConservanRepresentacionCanonicaAnteriorSinCampoNuevoNull(){
  var d=new Datos(grant,7,Atribucion.OPERACION_IA_PLATFORM,plan,"TEST_V1",caso,null,"TEST",
   Set.of(Accion.ADMITIR,Accion.INICIAR),Set.of(Campo.ESTADO),Instant.parse("2026-11-01T00:00:00Z"),Instant.parse("2026-12-01T00:00:00Z"),"TEST");
  var anterior=new DatosPrevios(d.id(),d.destinatario(),d.atribucion(),d.plan(),d.versionPlan(),d.caso(),d.fondo(),d.finalidad(),
   d.acciones(),d.campos(),d.desde(),d.hasta(),d.evidencia());
  assertEquals(json.objetoCanonico(anterior),json.objetoCanonico(d));assertFalse(json.comoMapa(json.objetoCanonico(d)).containsKey("recursoCompra"));
 }
 /** Forma congelada del record previo: la prueba recorre la misma normalización Record, no Map. */
 record DatosPrevios(UUID id,long destinatario,Atribucion atribucion,UUID plan,String versionPlan,UUID caso,UUID fondo,
  String finalidad,Set<Accion> acciones,Set<Campo> campos,Instant desde,Instant hasta,String evidencia) {}
 @Test void actualAnteriorOmiteSoloRecursoCompraYConservaLosOtrosNullHistoricos(){
  var a=new Actual(7,grant,plan,"TEST_V1","TEST",caso,null);var mapa=json.comoMapa(json.objetoCanonico(a));
  assertFalse(mapa.containsKey("recursoCompra"));assertTrue(mapa.containsKey("fondo"));assertNull(a.recursoCompra());
 }
}
