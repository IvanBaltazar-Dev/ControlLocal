package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.planes.CategoriaAlmacenamiento;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0.*;
import com.controllocal.service.planes.ClasificadorCargaAlmacenamientoB0.*;
import com.controllocal.service.presupuesto.ContextoPresupuestoB0;
import com.controllocal.service.soporte.Documentos;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Bytes exactos PG/T4. GB aprobados; financiación/márgenes sintéticos, sin proveedores reales. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class B0AlmacenamientoIntegrationTest {
    static final long UNIDAD_SINTETICA=20_000_000L;
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired PresupuestoIaRepository presupuestos;
    @Autowired UsoAlmacenamientoB0Repository repo;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    UsoAlmacenamientoB0 servicio;Actor agente;ContextoSesion sid;TransactionTemplate tx;Instant ahora;
    final Map<String,Clasificacion> autorizados=new ConcurrentHashMap<>();
    final ClasificadorCargaAlmacenamientoB0 clasificador=new ClasificadorCargaAlmacenamientoB0(){
        public boolean atiende(Recurso r){return "OBJETO_B0_TEST".equals(r.tipo());}
        public Clasificacion clasificar(Solicitud s,Actor a){
            var c=autorizados.get(a.idOrganizacion()+":"+a.idPersona()+":"+s.recurso().referencia());
            if(c==null)throw new AccesoNoAutorizadoException();return c;
        }
    };
    @BeforeEach void preparar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);ahora=Instant.now();
        var origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class));
        agente=independiente();sid=origenes.sesion(agente);servicio=servicio(Clock.systemUTC());politica("FREE",bytes(10));
    }
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
    Actor independiente(){return EspacioIndividualB0DePrueba.crear(jdbc,tx);}
    void usarEmpresa(){var origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class));agente=origenes.empresa().agente();sid=origenes.sesion(agente);}
    UsoAlmacenamientoB0 servicio(Clock reloj){return new UsoAlmacenamientoB0(contexto.getBean(ContextoPresupuestoB0.class),presupuestos,repo,
            contexto.getBean(Documentos.class),List.of(clasificador),reloj);}
    void politica(String plan,Long margen){
        String v="TEST-"+UUID.randomUUID();
        jdbc.update("INSERT INTO b0_politica_almacenamiento(version,plan,margen_esencial_bytes,evidencia_aprobacion,evidencia_margen_financiado,desde,hasta,habilitada) VALUES (?,?,?,'TEST NO APROBACION DE MARGEN','TEST MARGEN SINTETICO SIN FONDOS REALES',?,?,true)",
                v,plan,margen,utc(ahora.minusSeconds(3600)),utc(ahora.plus(Duration.ofDays(100))));
        jdbc.update("INSERT INTO b0_politica_almacenamiento_actual(plan,version) VALUES (?,?) ON CONFLICT(plan) DO UPDATE SET version=excluded.version",plan,v);
    }
    Solicitud solicitud(long unidades,CategoriaAlmacenamiento categoria,Necesidad necesidad,String conocida){
        String referencia=UUID.randomUUID().toString();autorizados.put(agente.idOrganizacion()+":"+agente.idPersona()+":"+referencia,new Clasificacion(categoria,necesidad,"a".repeat(64)));
        return new Solicitud(UUID.randomUUID(),new Recurso("OBJETO_B0_TEST",referencia,"FINALIDAD_TEST","a".repeat(64)),bytes(unidades),"b".repeat(64),conocida);
    }
    Resultado reservar(Solicitud s){return tx.execute(t->servicio.reservar(s,agente,sid));}
    boolean iniciar(Solicitud s){return Boolean.TRUE.equals(tx.execute(t->servicio.iniciar(s,agente,sid)));}
    RefInterna ref(Solicitud s){return new RefInterna(agente.idOrganizacion(),s.reserva());}
    ComprobacionObjeto objeto(Solicitud s,String id){return new ComprobacionObjeto(id,s.bytes(),s.huellaContenido(),"COMPROBACION SINTETICA TEST SIN BINARIO");}
    Resultado confirmar(Solicitud s,String id){return tx.execute(t->servicio.confirmar(ref(s),objeto(s,id)));}
    UsoAlmacenamientoB0Repository.Uso uso(){return tx.execute(t->repo.bloquearUso(agente.idOrganizacion()));}
    @Test void repetirReservaReclamacionYConfirmacionNoDuplicaBytes(){
        var s=solicitud(60,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);
        assertEquals(reservar(s),reservar(s));assertEquals(bytes(60),uso().reservados());assertTrue(iniciar(s));assertFalse(iniciar(s));
        assertEquals("CONFIRMADA",confirmar(s,"DISCO:TEST:VERSION1").estado());assertEquals("CONFIRMADA",confirmar(s,"DISCO:TEST:VERSION1").estado());
        assertEquals(bytes(60),uso().confirmados());assertEquals(0,uso().reservados());
    }
    @Test void mismoObjetoConfirmadoEnOtraReferenciaNoMultiplicaCuota(){
        var a=solicitud(60,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);reservar(a);iniciar(a);confirmar(a,"S3:TEST:V1");
        var b=solicitud(60,CategoriaAlmacenamiento.DOCUMENTO,Necesidad.ORDINARIA,"S3:TEST:V1");reservar(b);assertEquals(0,uso().reservados());
        iniciar(b);confirmar(b,"S3:TEST:V1");assertEquals(bytes(60),uso().confirmados());
        assertThrows(ConflictoException.class,()->confirmar(b,"S3:OTRO:V1"));
    }
    @Test void mismaIdentidadNoPermiteCambiarContenidoOTamano(){
        var a=solicitud(60,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);reservar(a);iniciar(a);confirmar(a,"DISCO:ORIGINAL:1");
        var b=solicitud(59,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,"DISCO:ORIGINAL:1");assertThrows(ConflictoException.class,()->reservar(b));
        var distinto=new Solicitud(a.reserva(),a.recurso(),bytes(61),a.huellaContenido(),a.identidadObjetoConocida());assertThrows(ConflictoException.class,()->reservar(distinto));
        assertEquals(bytes(60),uso().confirmados());
    }
    @Test void previewsYCopiasTecnicasNoComputanCapacidadComercial(){
        for(var c:List.of(CategoriaAlmacenamiento.PREVIEW,CategoriaAlmacenamiento.CACHE,CategoriaAlmacenamiento.MINIATURA,CategoriaAlmacenamiento.COPIA_TECNICA)){
            var s=solicitud(1000000,c,Necesidad.ORDINARIA,null);reservar(s);assertTrue(iniciar(s));confirmar(s,"TECNICO:"+s.reserva());
        }
        assertEquals(0,uso().confirmados());assertEquals(0,uso().reservados());
    }
    @Test void timeoutConservaReservaYSoloAusenciaComprobadaPermiteLiberarla(){
        var s=solicitud(60,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);reservar(s);iniciar(s);
        tx.execute(t->{servicio.marcarIncierta(ref(s));return null;});assertEquals("INCIERTA",reservar(s).estado());assertFalse(iniciar(s));
        assertThrows(ConflictoException.class,()->tx.execute(t->servicio.cancelarNoAlmacenado(ref(s),null)));assertEquals(bytes(60),uso().reservados());
        assertEquals("CANCELADA",tx.execute(t->servicio.cancelarNoAlmacenado(ref(s),"AUSENCIA COMPROBADA TEST")).estado());
        assertEquals("CANCELADA",tx.execute(t->servicio.cancelarNoAlmacenado(ref(s),"REPETICION TEST")).estado());assertEquals(0,uso().reservados());
    }
    @Test void margenEsencialEsFinitoYNoProvieneDeUnCampoDelCliente(){
        tx.execute(t->{servicio.observarExistenteComprobado(agente.idOrganizacion(),new ComprobacionObjeto("EXISTENTE:TEST",bytes(100),"c".repeat(64),"INVENTARIO REAL SIMULADO TEST"),CategoriaAlmacenamiento.DOCUMENTO);return null;});
        var ordinaria=solicitud(1,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);
        var rotulada=new Solicitud(ordinaria.reserva(),new Recurso(ordinaria.recurso().tipo(),ordinaria.recurso().referencia(),"CIERRE",ordinaria.recurso().huellaOrigen()),bytes(1),ordinaria.huellaContenido(),null);
        assertThrows(ReglaNegocioException.class,()->reservar(rotulada));
        var esencial=solicitud(8,CategoriaAlmacenamiento.DOCUMENTO,Necesidad.CIERRE,null);reservar(esencial);assertEquals(bytes(8),uso().reservados());
        assertThrows(ReglaNegocioException.class,()->reservar(solicitud(3,CategoriaAlmacenamiento.DOCUMENTO,Necesidad.CIERRE,null)));
    }
    @Test void gbAprobadoFuncionaSinInventarUnMargenEsencial(){
        jdbc.update("DELETE FROM b0_politica_almacenamiento_actual WHERE plan='FREE'");
        var ordinaria=solicitud(1,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);assertEquals("RESERVADA",reservar(ordinaria).estado());
        tx.execute(t->servicio.cancelarNoAlmacenado(ref(ordinaria),null));
        var preview=solicitud(1000,CategoriaAlmacenamiento.PREVIEW,Necesidad.ORDINARIA,null);assertEquals("RESERVADA",reservar(preview).estado());
        politica("FREE",null);
        tx.execute(t->{servicio.observarExistenteComprobado(agente.idOrganizacion(),new ComprobacionObjeto("EXISTENTE:TEST:2",bytes(100),"d".repeat(64),"INVENTARIO SINTETICO TEST"),CategoriaAlmacenamiento.DOCUMENTO);return null;});
        assertThrows(ReglaNegocioException.class,()->reservar(solicitud(1,CategoriaAlmacenamiento.DOCUMENTO,Necesidad.RECTIFICACION,null)));
    }
    @Test void conservarUsoExistenteSobreElLimiteNoLoBorraNiFabricaEspacio(){
        var o=new ComprobacionObjeto("EXCESO:TEST",bytes(200),"e".repeat(64),"INVENTARIO SINTETICO TEST");
        tx.execute(t->{servicio.observarExistenteComprobado(agente.idOrganizacion(),o,CategoriaAlmacenamiento.ORIGINAL);servicio.observarExistenteComprobado(agente.idOrganizacion(),o,CategoriaAlmacenamiento.ORIGINAL);return null;});
        assertEquals(bytes(200),uso().confirmados());assertThrows(ReglaNegocioException.class,()->reservar(solicitud(1,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null)));
        assertEquals(bytes(200),uso().confirmados());
    }
    @Test void perderSesionNoAutorizaRepetirUnaCarga(){
        var s=solicitud(20,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);reservar(s);iniciar(s);
        contexto.getBean(ContextosService.class).cerrar(sid.referenciaSesion(),sid.revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class,()->reservar(s));assertThrows(AccesoNoAutorizadoException.class,()->iniciar(s));
        // Callback técnico de un hecho conservado; no entrega el archivo al usuario revocado.
        assertEquals("CONFIRMADA",confirmar(s,"ORIGINAL:TRAS_LOGOUT").estado());
    }
    @Test void dosTransaccionesNoSobreReservanCapacidad()throws Exception{
        var a=solicitud(60,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);var b=solicitud(60,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);
        var listas=new CountDownLatch(2);var salida=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        java.util.function.Function<Solicitud,Object> ejecutar=s->{try{return reservar(s);}catch(RuntimeException e){return e;}};
        try{
            var fa=pool.submit(()->{listas.countDown();assertTrue(salida.await(20,TimeUnit.SECONDS));return ejecutar.apply(a);});
            var fb=pool.submit(()->{listas.countDown();assertTrue(salida.await(20,TimeUnit.SECONDS));return ejecutar.apply(b);});
            assertTrue(listas.await(20,TimeUnit.SECONDS));salida.countDown();var resultados=List.of(fa.get(60,TimeUnit.SECONDS),fb.get(60,TimeUnit.SECONDS));
            assertEquals(1,resultados.stream().filter(Resultado.class::isInstance).count());assertEquals(1,resultados.stream().filter(ReglaNegocioException.class::isInstance).count());
        }finally{salida.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
        assertEquals(bytes(60),uso().reservados());assertEquals(0,uso().confirmados());
    }
    @Test void resultadoConOtroTamanoNoSeDeclaraConfirmado(){
        var s=solicitud(60,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);reservar(s);iniciar(s);
        assertThrows(ConflictoException.class,()->tx.execute(t->servicio.confirmar(ref(s),new ComprobacionObjeto("TRUNCADO:TEST",59,s.huellaContenido(),"RESULTADO TRUNCADO TEST"))));
        assertEquals(0,uso().confirmados());assertEquals(bytes(60),uso().reservados());
    }
    @Test void ultimoByteDeDosGbSeAdmiteYElSiguienteNo(){
        tx.execute(t->{servicio.observarExistenteComprobado(agente.idOrganizacion(),new ComprobacionObjeto("BASE:2GB-MENOS-1",1_999_999_999L,"f".repeat(64),"INVENTARIO SINTETICO TEST"),CategoriaAlmacenamiento.ORIGINAL);return null;});
        var base=solicitud(1,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);
        var ultimo=new Solicitud(base.reserva(),base.recurso(),1,base.huellaContenido(),null);reservar(ultimo);iniciar(ultimo);confirmar(ultimo,"ULTIMO_BYTE:TEST");
        assertEquals(2_000_000_000L,uso().confirmados());
        var otra=solicitud(1,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);
        assertThrows(ReglaNegocioException.class,()->reservar(new Solicitud(otra.reserva(),otra.recurso(),1,otra.huellaContenido(),null)));
    }
    @Test void cientoVeinteGbSeReservanConLongExactoSinConvertirAGib(){
        usarEmpresa();
        UUID ciclo=UUID.randomUUID();jdbc.update("INSERT INTO b0_ciclo(organizacion_id,id_ciclo,plan,desde,hasta,dia_aniversario,importe_financiado_usd,orden_verificada,evidencia_pago,registrado_en) VALUES (?,?,'PRIME',?,?,1,300,?,'TEST SIN PAGO REAL',?)",
                agente.idOrganizacion(),ciclo,utc(ahora.minusSeconds(3600)),utc(ahora.plusSeconds(86400)),UUID.randomUUID(),utc(ahora));
        var base=solicitud(1,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);
        var grande=new Solicitud(base.reserva(),base.recurso(),120_000_000_000L,base.huellaContenido(),null);reservar(grande);
        assertEquals(120_000_000_000L,uso().reservados());assertEquals(0,uso().confirmados());
    }
    @Test void intentoTecnicoHijoConFkOrgConfirmaAntesDelCommitDelPadre()throws Exception{
        comprobarHijoDurable(false);
    }
    @Test void empresaSinFuenteNoRecibeUnFreeOrganizacionalImaginario(){
        usarEmpresa();
        assertThrows(CapacidadDeshabilitadaException.class,()->reservar(solicitud(1,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null)));
        assertEquals(0,uso().reservados());assertEquals(0,uso().confirmados());
        tx.execute(t->{servicio.observarExistenteComprobado(agente.idOrganizacion(),new ComprobacionObjeto("ANTECEDENTE_REAL_TEST",17,"f".repeat(64),"LECTURA COMPROBADA TEST"),CategoriaAlmacenamiento.DOCUMENTO);return null;});
        assertEquals(17,uso().confirmados());
    }
    @Test void intentoTecnicoHijoSobreviveAlRollbackDelPadreSinBloquearSuFk()throws Exception{
        comprobarHijoDurable(true);
    }
    void comprobarHijoDurable(boolean rollback)throws Exception{
        var s=solicitud(1,CategoriaAlmacenamiento.ORIGINAL,Necesidad.ORDINARIA,null);
        String marca="TEST-IO-DURABLE-"+UUID.randomUUID();
        var nueva=new TransactionTemplate(gestor);nueva.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.execute(status->{
            servicio.reservar(s,agente,sid);Integer padre=jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class);
            assertTrue(Boolean.TRUE.equals(nueva.execute(otra->{
                jdbc.execute("SET LOCAL lock_timeout='3s'");assertNotEquals(padre,jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
                // FK conservada, dos conexiones: el hijo confirma mientras el padre sigue abierto.
                presupuestos.antecedente(agente.idOrganizacion(),null,marca,"{}",Instant.now());return true;
            })));
            if(rollback)status.setRollbackOnly();return null;
        });
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND hecho=?",Integer.class,agente.idOrganizacion(),marca));
        assertEquals(rollback?0:bytes(1),uso().reservados());
        assertEquals(rollback?0:1,jdbc.queryForObject("SELECT count(*) FROM b0_reserva_almacenamiento WHERE organizacion_id=? AND id_reserva=?",Integer.class,agente.idOrganizacion(),s.reserva()));
    }
    static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
    static long bytes(long unidades){return Math.multiplyExact(unidades,UNIDAD_SINTETICA);}
}
