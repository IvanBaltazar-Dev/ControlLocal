package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.DerechosDePlanB0;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.domain.presupuesto.UnidadesDeConsumo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Delta PG/T4 B0: prueba interna de no procesamiento. Ningún proveedor, pago o gasto real. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class B0NoProcesamientoIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired PresupuestoIaRepository repo;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    TransactionTemplate tx;OrigenesDePrueba origenes;OrigenesDePrueba.Equipo equipo;
    PresupuestoIaService servicio;ContextoSesion sesion;UUID fondo,ciclo;String version;Instant ahora;
    @BeforeEach void preparar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);
        origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class));
        equipo=origenes.empresa();sesion=origenes.sesion(equipo.agente());fondo=UUID.randomUUID();ciclo=UUID.randomUUID();version=UUID.randomUUID().toString();ahora=Instant.now();
        servicio=servicio(repo);UUID fuente=UUID.randomUUID();
        tx.execute(t->{
            jdbc.update("INSERT INTO b0_fondo_tecnico(id_fondo,moneda,financiado,desde,hasta,evidencia_financiacion,habilitado) VALUES (?,'USD',100,?,?,'TEST SINTETICO SIN FONDOS REALES',true)",fondo,utc(ahora.minusSeconds(60)),utc(ahora.plusSeconds(86400)));
            jdbc.update("INSERT INTO b0_perfil_operativo(perfil,version,id_fondo,normalizador,tarifas_referencia,maximo_creditos,maximo_tecnico_usd,limite_intentos,evidencia_calibracion,evidencia_aprobacion,habilitado) VALUES ('B0_NO_PROCESAMIENTO_TEST',?,?,1,'[{\"clase\":\"unidades_test\",\"unidad\":\"u\",\"importeUsd\":1,\"porCantidad\":1}]'::jsonb,4,1,1,'TEST NO CALIBRACION','TEST NO APROBACION OPERATIVA',true)",version,fondo);
            jdbc.update("INSERT INTO b0_perfil_actual(perfil,version) VALUES ('B0_NO_PROCESAMIENTO_TEST',?) ON CONFLICT(perfil) DO UPDATE SET version=excluded.version",version);
            jdbc.update("INSERT INTO b0_ciclo(organizacion_id,id_ciclo,plan,desde,hasta,dia_aniversario,importe_financiado_usd,orden_verificada,evidencia_pago,registrado_en) VALUES (?,?,'TEAM',?,?,1,75,?,'TEST NO PAGO REAL',?)",equipo.agente().idOrganizacion(),ciclo,utc(ahora.minusSeconds(60)),utc(ahora.plusSeconds(86400)),UUID.randomUUID(),utc(ahora));
            jdbc.update("INSERT INTO b0_fuente_capacidad(organizacion_id,id_fuente,id_ciclo,origen,id_persona_destinataria,plaza,origen_verificado,inicial,desde,vence,registrado_en) VALUES (?,?,?,'MENSUAL_AGENTE',?,1,?,10,?,?,?)",equipo.agente().idOrganizacion(),fuente,ciclo,equipo.agente().idPersona(),"TEST-"+fuente,utc(ahora.minusSeconds(60)),utc(ahora.plusSeconds(86400)),utc(ahora));
            jdbc.update("INSERT INTO b0_plaza(organizacion_id,id_ciclo,tipo,numero,id_persona,id_fuente) VALUES (?,?,'AGENTE',1,?,?)",equipo.agente().idOrganizacion(),ciclo,equipo.agente().idPersona(),fuente);
            return null;
        });
    }
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
    @Test void despuesDelClaimConservaPruebaYLiberaUnaSolaVezSinMedicionInventada(){
        var s=admitida();assertTrue(iniciar(s));var c=prueba(s);var ref=ref(s);
        assertEquals("CANCELADA",cancelar(ref,c).estado());long versionFinal=versionOperacion(s);
        assertEquals("CANCELADA",cancelar(ref,c).estado());assertEquals(versionFinal,versionOperacion(s));assertFalse(iniciar(s));
        monto("0","SELECT SUM(retenido+liquidado) FROM b0_fuente_capacidad WHERE organizacion_id=?",ref.organizacion());
        monto("0","SELECT retenido+liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=?",Integer.class,ref.organizacion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND id_operacion=? AND hecho='NO_PROCESAMIENTO_COMPROBADO' AND contenido->>'huellaEvidencia'=?",Integer.class,ref.organizacion(),ref.operacion(),c.huellaEvidencia()));
        var otra=new ComprobacionNoProcesamiento(c.claveIntento(),c.proveedor(),c.referenciaIntento(),"OTRA EVIDENCIA",c.huellaEvidencia(),c.comprobadaEn());
        assertThrows(ConflictoException.class,()->cancelar(ref,otra));assertEquals(versionFinal,versionOperacion(s));
    }
    @Test void timeoutNoLiberaPeroLaPruebaPosteriorPuedeResolverAunqueLaSesionSeRevoque(){
        var s=admitida();assertTrue(iniciar(s));var ref=ref(s);
        tx.execute(t->{servicio.marcarIncierta(ref);return null;});
        assertFalse(iniciar(s));assertThrows(ConflictoException.class,()->tx.execute(t->servicio.cancelarNoIniciada(ref)));
        assertThrows(ReglaNegocioException.class,()->cancelar(ref,null));
        monto("4","SELECT SUM(retenido) FROM b0_fuente_capacidad WHERE organizacion_id=?",ref.organizacion());
        monto("1","SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
        contexto.getBean(ContextosService.class).cerrar(sesion.referenciaSesion(),sesion.revisionContexto());
        assertEquals("CANCELADA",cancelar(ref,prueba(s)).estado());
        monto("0","SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
    }
    @Test void comprobacionAjenaOAnteriorAlClaimNoLiberaNingunaFuente(){
        var s=admitida();assertTrue(iniciar(s));var c=prueba(s);var ref=ref(s);var ajeno=origenes.empresa();
        var otraRef=new ReferenciaInterna(ajeno.agente().idOrganizacion(),s.operacion());
        assertThrows(ConflictoException.class,()->cancelar(otraRef,c));
        var otraClave=new ComprobacionNoProcesamiento(PresupuestoIaService.claveDeIntento(otraRef),c.proveedor(),c.referenciaIntento(),c.referenciaEvidencia(),c.huellaEvidencia(),c.comprobadaEn());
        assertThrows(ConflictoException.class,()->cancelar(ref,otraClave));
        assertThrows(NoEncontradoException.class,()->cancelar(otraRef,otraClave));
        var anterior=new ComprobacionNoProcesamiento(c.claveIntento(),c.proveedor(),c.referenciaIntento(),c.referenciaEvidencia(),c.huellaEvidencia(),ahora.minusSeconds(1));
        assertThrows(ConflictoException.class,()->cancelar(ref,anterior));
        monto("4","SELECT SUM(retenido) FROM b0_fuente_capacidad WHERE organizacion_id=?",ref.organizacion());
        assertEquals("EN_PROCESO",repo.operacion(ref.organizacion(),ref.operacion()).orElseThrow().estado());
    }
    @Test void medicionAnteriorInciertaExigeConciliacionYNoAdmiteLaPruebaDeNoProcesamiento(){
        var s=admitida();assertTrue(iniciar(s));var ref=ref(s);
        var r=new ResultadoFacturable(List.of(new MedicionFacturable(UUID.randomUUID(),"PROVEEDOR_TEST","MODELO_TEST","TARIFA_TEST",
                List.of(new Medicion("unidades_test","u",n("5"),null)),List.of(new Tarifa("unidades_test","u",n("0.1"),BigDecimal.ONE)),null,"INTENTO SINTETICO "+UUID.randomUUID())));
        assertEquals("INCIERTA",tx.execute(t->servicio.conciliar(ref,r)).estado());
        assertThrows(ConflictoException.class,()->cancelar(ref,prueba(s)));
        monto("1","SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=?",Integer.class,ref.organizacion()));
    }
    @Test void antesDelClaimConservaSuCancelacionPropiaSinSustituirLaCausa(){
        var s=admitida();var ref=ref(s);var c=prueba(s);
        assertThrows(ConflictoException.class,()->cancelar(ref,c));
        assertEquals("CANCELADA",tx.execute(t->servicio.cancelarNoIniciada(ref)).estado());
        assertThrows(ConflictoException.class,()->cancelar(ref,c));
        assertFalse(iniciar(s));
    }
    @Test void liberacionTardiaNoReviveLaCuotaMensualYaVencida(){
        var s=admitida();assertTrue(iniciar(s));var ref=ref(s);
        jdbc.update("UPDATE b0_fuente_capacidad SET vence=? WHERE organizacion_id=?",utc(ahora.minusSeconds(1)),ref.organizacion());
        assertEquals("CANCELADA",cancelar(ref,prueba(s)).estado());
        assertEquals(0,tx.execute(t->servicio.consultarDisponibilidad(equipo.agente(),sesion)).porcentajeMensual());
        monto("0","SELECT SUM(retenido+liquidado) FROM b0_fuente_capacidad WHERE organizacion_id=?",ref.organizacion());
    }
    @Test void dosClaimsRealesYConfirmacionesConcurrentesNoReejecutanNiLiberanDosVeces()throws Exception{
        var s=admitida();var claims=competir(()->servicio.iniciar(s,equipo.agente(),sesion));
        assertEquals(1,claims.stream().filter(Boolean.TRUE::equals).count());var c=prueba(s);var ref=ref(s);
        var canceladas=competir(()->servicio.cancelarNoProcesada(ref,c));
        assertTrue(canceladas.stream().allMatch(x->"CANCELADA".equals(x.estado())));assertFalse(iniciar(s));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND id_operacion=? AND hecho='EJECUCION_RECLAMADA'",Integer.class,ref.organizacion(),ref.operacion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND id_operacion=? AND hecho='NO_PROCESAMIENTO_COMPROBADO'",Integer.class,ref.organizacion(),ref.operacion()));
        monto("0","SELECT retenido+liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
        monto("0","SELECT SUM(retenido+liquidado) FROM b0_fuente_capacidad WHERE organizacion_id=?",ref.organizacion());
    }
    @Test void falloAlCerrarRevierteLiberacionYPruebaAntesDeRepetirElMismoResultado(){
        var s=admitida();assertTrue(iniciar(s));var ref=ref(s);var c=prueba(s);
        var conFallo=servicio(new PresupuestoIaRepository(jdbc){
            @Override public void cerrar(Long org,UUID id,String estado,BigDecimal creditos,BigDecimal costo,String huella,Instant fecha){
                super.cerrar(org,id,estado,creditos,costo,huella,fecha);throw new IllegalStateException("FALLO TEST DESPUES DE CERRAR");
            }
        });
        assertThrows(IllegalStateException.class,()->tx.execute(t->conFallo.cancelarNoProcesada(ref,c)));
        assertEquals("EN_PROCESO",repo.operacion(ref.organizacion(),ref.operacion()).orElseThrow().estado());
        monto("1","SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
        monto("4","SELECT SUM(retenido) FROM b0_fuente_capacidad WHERE organizacion_id=?",ref.organizacion());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND id_operacion=? AND hecho='NO_PROCESAMIENTO_COMPROBADO'",Integer.class,ref.organizacion(),ref.operacion()));
        assertEquals("CANCELADA",cancelar(ref,c).estado());
    }
    private PresupuestoIaService servicio(PresupuestoIaRepository r){
        var autorizador=new AutorizadorRecursoIa(){
            public boolean atiende(String perfil,String tipo){return "B0_NO_PROCESAMIENTO_TEST".equals(perfil)&&"RECURSO_TEST".equals(tipo);}
            public void reautorizar(Solicitud s,Actor a){if(!a.equals(equipo.agente()))throw new AccesoNoAutorizadoException();}
        };
        return new PresupuestoIaService(r,contexto.getBean(ContextoPresupuestoB0.class),List.of(autorizador),contexto.getBean(Documentos.class),new DerechosDePlanB0(r,contexto.getBean(Documentos.class)),true);
    }
    private Solicitud admitida(){
        var s=new Solicitud(UUID.randomUUID(),"B0_NO_PROCESAMIENTO_TEST",version,new Recurso("RECURSO_TEST",UUID.randomUUID().toString(),"PRUEBA_B0","a".repeat(64)),"b".repeat(64));
        assertEquals("RESERVADA",tx.execute(t->servicio.admitir(s,equipo.agente(),sesion)).estado());return s;
    }
    private boolean iniciar(Solicitud s){return Boolean.TRUE.equals(tx.execute(t->servicio.iniciar(s,equipo.agente(),sesion)));}
    private ReferenciaInterna ref(Solicitud s){return new ReferenciaInterna(equipo.agente().idOrganizacion(),s.operacion());}
    private ComprobacionNoProcesamiento prueba(Solicitud s){return new ComprobacionNoProcesamiento(PresupuestoIaService.claveDeIntento(ref(s)),"PROVEEDOR_TEST","INTENTO_TEST "+s.operacion(),"EVIDENCIA SINTETICA SIN PROCESAMIENTO NI CARGO","e".repeat(64),Instant.now());}
    private Admision cancelar(ReferenciaInterna ref,ComprobacionNoProcesamiento c){return tx.execute(t->servicio.cancelarNoProcesada(ref,c));}
    private long versionOperacion(Solicitud s){return jdbc.queryForObject("SELECT version FROM b0_operacion_ia WHERE organizacion_id=? AND id_operacion=?",Long.class,equipo.agente().idOrganizacion(),s.operacion());}
    private <T> List<T> competir(Callable<T> tarea)throws Exception{
        var conectadas=new CountDownLatch(2);var salida=new CountDownLatch(1);var pids=ConcurrentHashMap.<Integer>newKeySet();var pool=Executors.newFixedThreadPool(2);
        Callable<T> participante=()->tx.execute(t->{
            pids.add(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));conectadas.countDown();
            try{assertTrue(salida.await(20,TimeUnit.SECONDS));return tarea.call();}
            catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}
        });
        try{var a=pool.submit(participante);var b=pool.submit(participante);assertTrue(conectadas.await(20,TimeUnit.SECONDS));salida.countDown();
            var resultados=List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS));assertEquals(2,pids.size());return resultados;
        }finally{salida.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
    private static BigDecimal n(String s){return new BigDecimal(s);}
    private static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
    private void monto(String esperado,String sql,Object... parametros){assertEquals(0,n(esperado).compareTo(jdbc.queryForObject(sql,BigDecimal.class,parametros)));}
}
