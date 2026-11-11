package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
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

/** PG/T4 de financiación con perfiles SINTÉTICOS. No calibra IA ni ejecuta proveedores. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=6")
class B0PresupuestoIntegrationTest {
    private static final Set<String> TABLAS_ORGANIZACION_PRIVADAS=Set.of(
        "b0_ciclo","b0_acreditacion_pago","b0_linea_financiada","b0_fuente_capacidad","b0_plaza","b0_imputacion","b0_uso_almacenamiento",
        "b0_compra_manual");
    private static final Set<String> TABLAS_DUENO_TIPADO=Set.of(
        "b0_operacion_ia","b0_medicion_tecnica","b0_antecedente","b0_etapa_operacion_ia",
        "b0_objeto_almacenamiento","b0_reserva_almacenamiento","b0_presencia_objeto_almacenamiento","b0_propietario_binario");
    /** Global no equivale a público. Configuración y datos de evaluación permanecen privados. */
    private static final Set<String> TABLAS_PLATAFORMA_PRIVADAS=Set.of(
        "b0_fondo_tecnico","b0_perfil_operativo","b0_perfil_actual","b0_politica_almacenamiento",
        "b0_politica_almacenamiento_actual","b0_limite_salida_ia","b0_politica_capacidad_fisica",
        "b0_capacidad_fisica_actual","b0_uso_capacidad_fisica","b0_decision_plataforma","b0_atribucion_plataforma",
        "b0_evaluacion_plan","b0_evaluacion_caso","b0_evaluacion_financiacion","b0_archivo_evaluacion",
        "b0_pago_manual_identidad","b0_compra_manual_evento","b0_consulta_metricas_ia","b0_costo_operacion_ia","b0_regla_trabajo_manual");
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired PresupuestoIaRepository repo;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    TransactionTemplate tx;OrigenesDePrueba origenes;OrigenesDePrueba.Equipo equipo;PresupuestoIaService servicio;
    UUID fondo,ciclo;String version;Instant ahora;ContextoSesion sesion;
    final Map<String,Long> permisos=new ConcurrentHashMap<>();
    final AutorizadorRecursoIa autorizador=new AutorizadorRecursoIa(){
        public boolean atiende(String perfil,String tipo){return "B0_TEST".equals(perfil)&&"RECURSO_TEST".equals(tipo);}
        public void reautorizar(Solicitud d,Actor a){
            if(!Objects.equals(permisos.get(a.idOrganizacion()+":"+d.recurso().referencia()),a.idPersona()))throw new AccesoNoAutorizadoException();
        }
    };
    @BeforeEach void preparar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);
        origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class));
        equipo=origenes.empresa();sesion=origenes.sesion(equipo.agente());ahora=Instant.now();fondo=UUID.randomUUID();ciclo=UUID.randomUUID();version=UUID.randomUUID().toString();
        servicio=servicio(repo,true);datosFinancieros(equipo.agente(),"10","100");
    }
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
    PresupuestoIaService servicio(PresupuestoIaRepository r,boolean habilitado){
        return new PresupuestoIaService(r,contexto.getBean(ContextoPresupuestoB0.class),List.of(autorizador),contexto.getBean(Documentos.class),
                new com.controllocal.service.planes.DerechosDePlanB0(r,contexto.getBean(Documentos.class)),habilitado);
    }
    void datosFinancieros(Actor a,String mensual,String financiado){
        tx.execute(t->{
            jdbc.update("INSERT INTO b0_fondo_tecnico(id_fondo,moneda,financiado,desde,hasta,evidencia_financiacion,habilitado) VALUES (?,'USD',?,?,?,'TEST SINTETICO SIN FONDOS REALES',true)",
                    fondo,n(financiado),utc(ahora.minusSeconds(3600)),utc(ahora.plusSeconds(86400)));
            jdbc.update("INSERT INTO b0_perfil_operativo(perfil,version,id_fondo,normalizador,tarifas_referencia,maximo_creditos,maximo_tecnico_usd,limite_intentos,evidencia_calibracion,evidencia_aprobacion,habilitado) VALUES ('B0_TEST',?,?,1,'[{\"clase\":\"unidades_test\",\"unidad\":\"u\",\"importeUsd\":1,\"porCantidad\":1}]'::jsonb,4,1,2,'TEST NO CALIBRACION','TEST NO APROBACION OPERATIVA',true)",version,fondo);
            jdbc.update("INSERT INTO b0_perfil_actual(perfil,version) VALUES ('B0_TEST',?) ON CONFLICT(perfil) DO UPDATE SET version=excluded.version",version);
            jdbc.update("INSERT INTO b0_ciclo(organizacion_id,id_ciclo,plan,desde,hasta,dia_aniversario,importe_financiado_usd,orden_verificada,evidencia_pago,registrado_en) VALUES (?,?,'TEAM',?,?,1,75,?,'TEST NO PAGO REAL',?)",
                    a.idOrganizacion(),ciclo,utc(ahora.minusSeconds(3600)),utc(ahora.plusSeconds(86400)),UUID.randomUUID(),utc(ahora));
            UUID origen=fuente(a,"MENSUAL_AGENTE",mensual,ahora.plusSeconds(86400),null);
            jdbc.update("INSERT INTO b0_plaza(organizacion_id,id_ciclo,tipo,numero,id_persona,id_fuente) VALUES (?,?,'AGENTE',1,?,?)",a.idOrganizacion(),ciclo,a.idPersona(),origen);return null;
        });
    }
    UUID fuente(Actor a,String tipo,String saldo,Instant vence,UUID origen){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO b0_fuente_capacidad(organizacion_id,id_fuente,id_ciclo,origen,id_persona_destinataria,plaza,origen_verificado,inicial,desde,vence,registrado_en,id_fuente_origen) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                a.idOrganizacion(),id,"BOOST".equals(tipo)?null:ciclo,tipo,Set.of("MENSUAL_AGENTE","RESERVA_ASIGNADA").contains(tipo)?a.idPersona():null,
                "MENSUAL_AGENTE".equals(tipo)?1:null,"TEST-"+id,n(saldo),utc(ahora.minusSeconds(3600)),utc(vence),utc(ahora),origen);return id;
    }
    Solicitud solicitud(Actor a){
        String recurso=UUID.randomUUID().toString();permisos.put(a.idOrganizacion()+":"+recurso,a.idPersona());
        return new Solicitud(UUID.randomUUID(),"B0_TEST",version,new Recurso("RECURSO_TEST",recurso,"PRUEBA_B0","a".repeat(64)),"b".repeat(64));
    }
    Admision admitir(Solicitud d){return tx.execute(t->servicio.admitir(d,equipo.agente(),sesion));}
    boolean iniciar(Solicitud d){return Boolean.TRUE.equals(tx.execute(t->servicio.iniciar(d,equipo.agente(),sesion)));}
    Admision conciliar(Solicitud d,ResultadoFacturable r){return tx.execute(t->servicio.conciliar(new ReferenciaInterna(equipo.agente().idOrganizacion(),d.operacion()),r));}
    ResultadoFacturable resultado(String cantidad,String costo){
        return new ResultadoFacturable(List.of(new MedicionFacturable(UUID.randomUUID(),"PROVEEDOR_TEST","MODELO_TEST","TARIFA_TEST",
                List.of(new Medicion("unidades_test","u",n(cantidad),null)),List.of(new Tarifa("unidades_test","u",n("0.1"),n("1"))),
                costo==null?null:n(costo),"RESULTADO SINTETICO NO FACTURA "+UUID.randomUUID())));
    }
    @Test void repeticionConservaUnaReservaUnaReclamacionYUnaLiquidacion(){
        var d=solicitud(equipo.agente());assertEquals("RESERVADA",admitir(d).estado());assertEquals(admitir(d),admitir(d));
        assertEquals("RESERVADA",tx.execute(t->servicio.consultarEstado(d,equipo.agente(),sesion)).orElseThrow().estado());
        assertTrue(iniciar(d));assertFalse(iniciar(d));var r=resultado("2.5",null);
        assertEquals("LIQUIDADA",conciliar(d,r).estado());assertEquals(conciliar(d,r),conciliar(d,r));
        assertEquals("LIQUIDADA",servicio.estadoInterno(new ReferenciaInterna(equipo.agente().idOrganizacion(),d.operacion())).orElseThrow().estado());
        monto("2.5","SELECT SUM(liquidado) FROM b0_fuente_capacidad WHERE organizacion_id=?",equipo.agente().idOrganizacion());
        monto("0","SELECT SUM(retenido) FROM b0_fuente_capacidad WHERE organizacion_id=?",equipo.agente().idOrganizacion());
        monto("0.25","SELECT liquidado FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_operacion_ia WHERE organizacion_id=?",Integer.class,equipo.agente().idOrganizacion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=?",Integer.class,equipo.agente().idOrganizacion()));
        assertThrows(ConflictoException.class,()->conciliar(d,resultado("2.6",null)));
    }
    @Test void mismoIdentificadorNoPermiteCambiarContenidoOActor(){
        var d=solicitud(equipo.agente());admitir(d);
        var otra=new Solicitud(d.operacion(),d.perfil(),d.versionPerfil(),d.recurso(),"c".repeat(64));
        assertThrows(ConflictoException.class,()->admitir(otra));
        var colega=origenes.sesion(equipo.colega());
        assertThrows(AccesoNoAutorizadoException.class,()->tx.execute(t->servicio.admitir(d,equipo.colega(),colega)));
        assertEquals("RESERVADA",repo.operacion(equipo.agente().idOrganizacion(),d.operacion()).orElseThrow().estado());
    }
    @Test void gastoDeshabilitadoYPerfilAusenteNoCreanReservas(){
        var d=solicitud(equipo.agente());var cerrada=servicio(repo,false);
        assertTrue(tx.execute(t->cerrada.consultarEstado(d,equipo.agente(),sesion)).isEmpty());
        assertThrows(CapacidadDeshabilitadaException.class,()->tx.execute(t->cerrada.admitir(d,equipo.agente(),sesion)));
        var sinPerfil=new Solicitud(d.operacion(),d.perfil(),"NO_EXISTE",d.recurso(),d.huellaContenido());
        assertThrows(CapacidadDeshabilitadaException.class,()->admitir(sinPerfil));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_operacion_ia WHERE organizacion_id=?",Integer.class,equipo.agente().idOrganizacion()));
        monto("0","SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
    }
    @Test void faltaAutorizadorYTenantAdminNoAdquierenConsumo(){
        var d=solicitud(equipo.agente());
        var sinRecurso=new Solicitud(d.operacion(),"NO_ATENDIDO",d.versionPerfil(),d.recurso(),d.huellaContenido());
        assertThrows(CapacidadDeshabilitadaException.class,()->admitir(sinRecurso));
        assertThrows(AccesoNoAutorizadoException.class,()->tx.execute(t->servicio.admitir(d,equipo.admin(),sesion)));
    }
    @Test void timeoutRetieneYNoVuelveAEjecutarHastaConciliarLaMismaOperacion(){
        var d=solicitud(equipo.agente());admitir(d);assertTrue(iniciar(d));
        tx.execute(t->{servicio.marcarIncierta(new ReferenciaInterna(equipo.agente().idOrganizacion(),d.operacion()));return null;});
        assertEquals("INCIERTA",admitir(d).estado());assertFalse(iniciar(d));
        monto("4","SELECT SUM(retenido) FROM b0_fuente_capacidad WHERE organizacion_id=?",equipo.agente().idOrganizacion());
        monto("1","SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
        assertThrows(ConflictoException.class,()->tx.execute(t->servicio.cancelarNoIniciada(new ReferenciaInterna(equipo.agente().idOrganizacion(),d.operacion()))));
        assertEquals("LIQUIDADA",conciliar(d,resultado("1",null)).estado());
    }
    @Test void cancelarAntesDeComenzarDevuelveSoloSuFuente(){
        var d=solicitud(equipo.agente());admitir(d);var ref=new ReferenciaInterna(equipo.agente().idOrganizacion(),d.operacion());
        assertEquals("CANCELADA",tx.execute(t->servicio.cancelarNoIniciada(ref)).estado());
        assertEquals("CANCELADA",tx.execute(t->servicio.cancelarNoIniciada(ref)).estado());assertFalse(iniciar(d));
        monto("0","SELECT SUM(retenido+liquidado) FROM b0_fuente_capacidad WHERE organizacion_id=?",equipo.agente().idOrganizacion());
    }
    @Test void reservaSinAsignarNoFinanciaYMensualPrecedeBoostPorVencimiento(){
        jdbc.update("UPDATE b0_fuente_capacidad SET inicial=1 WHERE organizacion_id=?",equipo.agente().idOrganizacion());
        fuente(equipo.agente(),"RESERVA","100",ahora.plusSeconds(86400),null);
        assertThrows(ReglaNegocioException.class,()->admitir(solicitud(equipo.agente())));
        UUID tardio=fuente(equipo.agente(),"BOOST","3",ahora.plusSeconds(7200),null),temprano=fuente(equipo.agente(),"BOOST","2",ahora.plusSeconds(3600),null);
        var d=solicitud(equipo.agente());admitir(d);var imp=repo.imputaciones(equipo.agente().idOrganizacion(),d.operacion());
        assertEquals(3,imp.size());assertEquals(temprano,imp.get(1).fuente());assertEquals(tardio,imp.get(2).fuente());
        assertEquals(0,n("1").compareTo(imp.getFirst().retenido()));assertEquals(0,n("2").compareTo(imp.get(1).retenido()));
        assertTrue(iniciar(d));conciliar(d,resultado("2.000000000001",null));
        monto("1","SELECT SUM(liquidado) FROM b0_fuente_capacidad WHERE organizacion_id=? AND origen='MENSUAL_AGENTE'",equipo.agente().idOrganizacion());
        monto("1.000000000001","SELECT liquidado FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_fuente=?",equipo.agente().idOrganizacion(),temprano);
        monto("0","SELECT liquidado FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_fuente=?",equipo.agente().idOrganizacion(),tardio);
    }
    @Test void liquidacionTardiaNoReviveMensualVencidoNiDebitaCicloNuevo(){
        var d=solicitud(equipo.agente());admitir(d);assertTrue(iniciar(d));
        jdbc.update("UPDATE b0_fuente_capacidad SET vence=? WHERE organizacion_id=?",utc(ahora.minusSeconds(1)),equipo.agente().idOrganizacion());
        conciliar(d,resultado("1",null));
        assertEquals(0,tx.execute(t->servicio.consultarDisponibilidad(equipo.agente(),sesion)).porcentajeMensual());
        monto("1","SELECT SUM(liquidado) FROM b0_fuente_capacidad WHERE organizacion_id=?",equipo.agente().idOrganizacion());
        monto("0","SELECT SUM(retenido) FROM b0_fuente_capacidad WHERE organizacion_id=?",equipo.agente().idOrganizacion());
    }
    @Test void reservaAnteriorSeLiquidaEnSuFuenteSinDebitarLaNuevaCuota(){
        var d=solicitud(equipo.agente());admitir(d);assertTrue(iniciar(d));UUID anterior=ciclo;Instant cambio=Instant.now();
        tx.execute(t->{
            jdbc.update("UPDATE b0_ciclo SET cerrado_en=?,version=version+1 WHERE organizacion_id=? AND id_ciclo=?",utc(cambio),equipo.agente().idOrganizacion(),anterior);
            ciclo=UUID.randomUUID();
            jdbc.update("INSERT INTO b0_ciclo(organizacion_id,id_ciclo,plan,desde,hasta,dia_aniversario,importe_financiado_usd,orden_verificada,evidencia_pago,registrado_en) VALUES (?,?,'TEAM',?,?,1,75,?,'TEST NUEVO CICLO SINTETICO',?)",
                    equipo.agente().idOrganizacion(),ciclo,utc(cambio),utc(cambio.plusSeconds(86400)),UUID.randomUUID(),utc(cambio));
            UUID nueva=fuente(equipo.agente(),"MENSUAL_AGENTE","3000",cambio.plusSeconds(86400),null);
            jdbc.update("INSERT INTO b0_plaza(organizacion_id,id_ciclo,tipo,numero,id_persona,id_fuente) VALUES (?,?,'AGENTE',1,?,?)",equipo.agente().idOrganizacion(),ciclo,equipo.agente().idPersona(),nueva);return null;
        });
        conciliar(d,resultado("1.000000000001",null));
        monto("1.000000000001","SELECT SUM(liquidado) FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_ciclo=?",equipo.agente().idOrganizacion(),anterior);
        monto("0","SELECT SUM(liquidado+retenido) FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_ciclo=?",equipo.agente().idOrganizacion(),ciclo);
        assertEquals(100,tx.execute(t->servicio.consultarDisponibilidad(equipo.agente(),sesion)).porcentajeMensual());
    }
    @Test void mismoHechoFacturadoNoPuedeDuplicarseConOtroUuid(){
        var d=solicitud(equipo.agente());admitir(d);assertTrue(iniciar(d));
        var original=resultado("1",null).mediciones().getFirst();
        var copia=new MedicionFacturable(UUID.randomUUID(),original.proveedor(),original.modelo(),original.versionTarifa(),original.unidades(),original.tarifasReales(),null,original.referenciaFuente());
        assertThrows(ConflictoException.class,()->conciliar(d,new ResultadoFacturable(List.of(original,copia))));
        assertEquals("LIQUIDADA",conciliar(d,new ResultadoFacturable(List.of(original))).estado());
        var otra=solicitud(equipo.agente());admitir(otra);assertTrue(iniciar(otra));
        assertThrows(ConflictoException.class,()->conciliar(otra,new ResultadoFacturable(List.of(copia))));
        monto("1","SELECT SUM(liquidado) FROM b0_fuente_capacidad WHERE organizacion_id=?",equipo.agente().idOrganizacion());
    }
    @Test void perdidaDePermisoImpideNuevoUsoPeroNoPierdeElHechoFacturado(){
        var d=solicitud(equipo.agente());admitir(d);assertTrue(iniciar(d));permisos.clear();
        assertThrows(AccesoNoAutorizadoException.class,()->admitir(d));assertThrows(AccesoNoAutorizadoException.class,()->iniciar(d));
        assertEquals("LIQUIDADA",conciliar(d,resultado("1",null)).estado());
    }
    @Test void cerrarSesionRealRevocaAdmisionYConservaConciliacionDelHechoYaIniciado(){
        var d=solicitud(equipo.agente());admitir(d);assertTrue(iniciar(d));
        contexto.getBean(ContextosService.class).cerrar(sesion.referenciaSesion(),sesion.revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class,()->admitir(d));
        assertThrows(AccesoNoAutorizadoException.class,()->iniciar(d));
        assertEquals("LIQUIDADA",conciliar(d,resultado("1",null)).estado());
    }
    @Test void resultadoFueraDeLimiteConservaMedicionYProvisionSinNegativos(){
        var d=solicitud(equipo.agente());admitir(d);assertTrue(iniciar(d));var r=resultado("5",null);
        assertEquals("INCIERTA",conciliar(d,r).estado());
        assertEquals("INCIERTA",conciliar(d,r).estado());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_antecedente WHERE organizacion_id=? AND id_operacion=? AND hecho='INCIDENCIA_LIMITE'",Integer.class,equipo.agente().idOrganizacion(),d.operacion()));
        monto("4","SELECT SUM(retenido) FROM b0_fuente_capacidad WHERE organizacion_id=?",equipo.agente().idOrganizacion());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_medicion_tecnica WHERE organizacion_id=?",Integer.class,equipo.agente().idOrganizacion()));
        assertThrows(ConflictoException.class,()->conciliar(d,resultado("1",null)));
    }
    record Intento(Admision admision,RuntimeException fallo){}
    Intento intentar(Solicitud d){try{return new Intento(admitir(d),null);}catch(RuntimeException e){return new Intento(null,e);}}
    List<Intento> competir(Solicitud a,Solicitud b)throws Exception{return competirTareas(()->intentar(a),()->intentar(b));}
    List<Intento> competirTareas(Callable<Intento> a,Callable<Intento> b)throws Exception{
        var listas=new CountDownLatch(2);var salida=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var fa=pool.submit(()->{listas.countDown();assertTrue(salida.await(20,TimeUnit.SECONDS));return a.call();});
            var fb=pool.submit(()->{listas.countDown();assertTrue(salida.await(20,TimeUnit.SECONDS));return b.call();});
            assertTrue(listas.await(20,TimeUnit.SECONDS));salida.countDown();return List.of(fa.get(60,TimeUnit.SECONDS),fb.get(60,TimeUnit.SECONDS));
        }finally{salida.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
    @Test void dosTransaccionesNoReservanDosVecesLaUltimaCapacidad()throws Exception{
        jdbc.update("UPDATE b0_fuente_capacidad SET inicial=4 WHERE organizacion_id=?",equipo.agente().idOrganizacion());
        var r=competir(solicitud(equipo.agente()),solicitud(equipo.agente()));
        assertEquals(1,r.stream().filter(x->x.admision()!=null).count());
        assertTrue(r.stream().filter(x->x.fallo()!=null).allMatch(x->x.fallo() instanceof ReglaNegocioException));
        monto("4","SELECT SUM(retenido) FROM b0_fuente_capacidad WHERE organizacion_id=?",equipo.agente().idOrganizacion());
        monto("1","SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
    }
    @Test void mismaSolicitudConcurrenteConservaUnaReserva()throws Exception{
        var d=solicitud(equipo.agente());var r=competir(d,d);assertTrue(r.stream().allMatch(x->x.fallo()==null));
        assertEquals(r.getFirst().admision(),r.get(1).admision());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_operacion_ia WHERE organizacion_id=?",Integer.class,equipo.agente().idOrganizacion()));
        monto("1","SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
    }
    @Test void dosOrganizacionesCompartenElMismoTechoFinanciadoSinSobregiro()throws Exception{
        var otro=origenes.empresa();Actor a=otro.agente();var sid=origenes.sesion(a);UUID otroCiclo=UUID.randomUUID(),otraFuente=UUID.randomUUID();
        tx.execute(t->{
            jdbc.update("UPDATE b0_fondo_tecnico SET financiado=1 WHERE id_fondo=?",fondo);
            jdbc.update("INSERT INTO b0_ciclo(organizacion_id,id_ciclo,plan,desde,hasta,dia_aniversario,importe_financiado_usd,orden_verificada,evidencia_pago,registrado_en) VALUES (?,?,'TEAM',?,?,1,75,?,'TEST OTRO ESPACIO SIN FONDOS REALES',?)",
                    a.idOrganizacion(),otroCiclo,utc(ahora.minusSeconds(3600)),utc(ahora.plusSeconds(86400)),UUID.randomUUID(),utc(ahora));
            jdbc.update("INSERT INTO b0_fuente_capacidad(organizacion_id,id_fuente,id_ciclo,origen,id_persona_destinataria,plaza,origen_verificado,inicial,desde,vence,registrado_en) VALUES (?,?,?,'MENSUAL_AGENTE',?,1,?,10,?,?,?)",
                    a.idOrganizacion(),otraFuente,otroCiclo,a.idPersona(),"TEST-"+otraFuente,utc(ahora.minusSeconds(3600)),utc(ahora.plusSeconds(86400)),utc(ahora));
            jdbc.update("INSERT INTO b0_plaza(organizacion_id,id_ciclo,tipo,numero,id_persona,id_fuente) VALUES (?,?,'AGENTE',1,?,?)",a.idOrganizacion(),otroCiclo,a.idPersona(),otraFuente);return null;
        });
        var propia=solicitud(equipo.agente());var ajena=solicitud(a);
        var resultados=competirTareas(()->intentar(propia),()->{
            try{return new Intento(tx.execute(t->servicio.admitir(ajena,a,sid)),null);}catch(RuntimeException e){return new Intento(null,e);}
        });
        assertEquals(1,resultados.stream().filter(x->x.admision()!=null).count());
        assertTrue(resultados.stream().filter(x->x.fallo()!=null).allMatch(x->x.fallo() instanceof CapacidadDeshabilitadaException));
        monto("1","SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
        monto("4","SELECT SUM(retenido) FROM b0_fuente_capacidad WHERE organizacion_id IN (?,?)",equipo.agente().idOrganizacion(),a.idOrganizacion());
    }
    @Test void falloTrasRetenerRevierteFuentesPresupuestoYOperacion(){
        var falla=new PresupuestoIaRepository(jdbc){@Override public void imputar(Long org,UUID op,UUID f,BigDecimal m,int orden){throw new IllegalStateException("Fallo TEST tras retención");}};
        var s=servicio(falla,true);var d=solicitud(equipo.agente());
        assertThrows(IllegalStateException.class,()->tx.execute(t->s.admitir(d,equipo.agente(),sesion)));
        assertTrue(repo.operacion(equipo.agente().idOrganizacion(),d.operacion()).isEmpty());
        monto("0","SELECT SUM(retenido) FROM b0_fuente_capacidad WHERE organizacion_id=?",equipo.agente().idOrganizacion());
        monto("0","SELECT retenido FROM b0_fondo_tecnico WHERE id_fondo=?",fondo);
    }
    @Test void clavesCompuestasImpidenImputarFuenteAjena(){
        var otro=origenes.empresa();var d=solicitud(equipo.agente());admitir(d);
        UUID fuente=repo.imputaciones(equipo.agente().idOrganizacion(),d.operacion()).getFirst().fuente();
        new PlanesFinanciadosDePrueba(jdbc,gestor).asegurarTeam(otro.agente().idOrganizacion());
        var sidOtro=origenes.sesion(otro.agente());var local=solicitud(otro.agente());
        tx.execute(t->servicio.admitir(local,otro.agente(),sidOtro));
        assertTrue(repo.operacion(otro.agente().idOrganizacion(),local.operacion()).isPresent(),"El padre de la nueva imputación sí es local y válido.");
        int anteriores=repo.imputaciones(otro.agente().idOrganizacion(),local.operacion()).size();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(t->{
            jdbc.update("INSERT INTO b0_imputacion(organizacion_id,id_operacion,id_fuente,retenido,orden) VALUES (?,?,?,?,9)",otro.agente().idOrganizacion(),local.operacion(),fuente,n("1"));return null;}));
        assertEquals(anteriores,repo.imputaciones(otro.agente().idOrganizacion(),local.operacion()).size());
    }
    @Test void tablasB0ConservanAmbitoFisicoPrivado(){
        var esperadas=new TreeSet<>(TABLAS_ORGANIZACION_PRIVADAS);esperadas.addAll(TABLAS_PLATAFORMA_PRIVADAS);esperadas.addAll(TABLAS_DUENO_TIPADO);
        var fisicas=new TreeSet<>(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema=current_schema() AND table_type='BASE TABLE'
                  AND table_name LIKE 'b0!_%' ESCAPE '!'
                """,String.class));
        assertEquals(36,esperadas.size());assertEquals(esperadas,fisicas,"Una tabla JDBC nueva debe clasificarse expresamente; los gates @Entity no la cubren.");
        assertEquals(8,TABLAS_ORGANIZACION_PRIVADAS.size());assertEquals(8,TABLAS_DUENO_TIPADO.size());assertEquals(20,TABLAS_PLATAFORMA_PRIVADAS.size());
        for(String tabla:esperadas){
            var columnas=jdbc.queryForList("""
                    SELECT is_nullable,column_default FROM information_schema.columns
                    WHERE table_schema=current_schema() AND table_name=? AND column_name='organizacion_id'
                    """,tabla);
            if(TABLAS_ORGANIZACION_PRIVADAS.contains(tabla)){
                assertEquals(1,columnas.size(),tabla);assertEquals("NO",columnas.getFirst().get("is_nullable"),tabla);
                assertNull(columnas.getFirst().get("column_default"),"No se crea tenant implícito en "+tabla);
            }else if(TABLAS_DUENO_TIPADO.contains(tabla)){
                assertEquals(1,columnas.size(),tabla);assertEquals("YES",columnas.getFirst().get("is_nullable"),tabla);
                assertNull(columnas.getFirst().get("column_default"),tabla);
            }else{
                assertTrue(columnas.isEmpty(),tabla+" es privada de plataforma; no pertenece al antiguo tenant ni se hace pública por ser global.");
            }
        }
    }
    private record ClaveFisica(String origen,String destino,String columnasOrigen,String columnasDestino,String esquemaDestino,boolean validada){
        String arista(){return origen+"->"+destino;}
    }
    @Test void clavesB0ConservanElAmbitoDeLasReferencias(){
        List<ClaveFisica> claves=jdbc.query("""
                SELECT src.relname,dst.relname,
                  (SELECT string_agg(a.attname,',' ORDER BY k.orden)
                   FROM unnest(c.conkey) WITH ORDINALITY k(numero,orden)
                   JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.numero),
                  (SELECT string_agg(a.attname,',' ORDER BY k.orden)
                   FROM unnest(c.confkey) WITH ORDINALITY k(numero,orden)
                   JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.numero),
                  nd.nspname,c.convalidated
                FROM pg_constraint c JOIN pg_class src ON src.oid=c.conrelid
                  JOIN pg_namespace ns ON ns.oid=src.relnamespace
                  JOIN pg_class dst ON dst.oid=c.confrelid JOIN pg_namespace nd ON nd.oid=dst.relnamespace
                WHERE c.contype='f' AND ns.nspname=current_schema() AND src.relname LIKE 'b0!_%' ESCAPE '!'
                ORDER BY src.relname,dst.relname,c.conname
                """,(r,n)->new ClaveFisica(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getBoolean(6)));
        Set<String> esperadas=Set.of(
                "b0_perfil_operativo->b0_fondo_tecnico","b0_ciclo->organizacion","b0_perfil_actual->b0_perfil_operativo",
                "b0_acreditacion_pago->organizacion","b0_linea_financiada->b0_ciclo","b0_linea_financiada->b0_acreditacion_pago",
                "b0_fuente_capacidad->organizacion","b0_fuente_capacidad->b0_ciclo","b0_fuente_capacidad->persona","b0_fuente_capacidad->b0_fuente_capacidad",
                "b0_plaza->b0_ciclo","b0_plaza->persona","b0_plaza->b0_fuente_capacidad",
                "b0_operacion_ia->organizacion","b0_operacion_ia->b0_fondo_tecnico","b0_operacion_ia->persona","b0_operacion_ia->persona_rol","b0_operacion_ia->b0_perfil_operativo",
                "b0_imputacion->b0_operacion_ia","b0_imputacion->b0_fuente_capacidad","b0_medicion_tecnica->b0_operacion_ia",
                "b0_etapa_operacion_ia->b0_operacion_ia","b0_medicion_tecnica->b0_etapa_operacion_ia",
                "b0_antecedente->organizacion","b0_antecedente->b0_operacion_ia","b0_uso_almacenamiento->organizacion",
                "b0_politica_almacenamiento_actual->b0_politica_almacenamiento","b0_objeto_almacenamiento->organizacion",
                "b0_reserva_almacenamiento->organizacion","b0_reserva_almacenamiento->persona","b0_reserva_almacenamiento->persona_rol",
                "b0_limite_salida_ia->b0_perfil_operativo","b0_capacidad_fisica_actual->b0_politica_capacidad_fisica",
                "b0_uso_capacidad_fisica->b0_politica_capacidad_fisica","b0_reserva_almacenamiento->material_ia_operacion",
                "b0_reserva_almacenamiento->b0_limite_salida_ia","b0_reserva_almacenamiento->b0_politica_capacidad_fisica",
                "b0_presencia_objeto_almacenamiento->b0_objeto_almacenamiento","b0_presencia_objeto_almacenamiento->b0_politica_capacidad_fisica",
                "b0_decision_plataforma->cuenta_acceso","b0_evaluacion_plan->b0_decision_plataforma","b0_evaluacion_plan->cuenta_acceso",
                "b0_evaluacion_caso->b0_decision_plataforma","b0_evaluacion_caso->cuenta_acceso","b0_evaluacion_caso->b0_evaluacion_plan",
                "b0_atribucion_plataforma->cuenta_acceso","b0_atribucion_plataforma->b0_fondo_tecnico",
                "b0_atribucion_plataforma->b0_decision_plataforma","b0_atribucion_plataforma->b0_evaluacion_plan","b0_atribucion_plataforma->b0_evaluacion_caso",
                "b0_evaluacion_financiacion->b0_fondo_tecnico","b0_evaluacion_financiacion->b0_evaluacion_plan",
                "b0_evaluacion_financiacion->b0_atribucion_plataforma","b0_evaluacion_financiacion->cuenta_acceso",
                "b0_operacion_ia->b0_evaluacion_caso","b0_operacion_ia->cuenta_acceso",
                "b0_operacion_ia->b0_atribucion_plataforma","b0_operacion_ia->b0_evaluacion_financiacion",
                "b0_propietario_binario->organizacion","b0_propietario_binario->b0_evaluacion_caso",
                "b0_objeto_almacenamiento->b0_propietario_binario","b0_reserva_almacenamiento->b0_propietario_binario",
                "b0_presencia_objeto_almacenamiento->b0_propietario_binario","b0_reserva_almacenamiento->b0_operacion_ia",
                "b0_archivo_evaluacion->b0_evaluacion_caso","b0_archivo_evaluacion->b0_propietario_binario",
                "b0_archivo_evaluacion->intento_salida_binaria","b0_archivo_evaluacion->b0_operacion_ia","b0_archivo_evaluacion->b0_etapa_operacion_ia",
                "b0_compra_manual->organizacion","b0_compra_manual->cuenta_acceso",
                "b0_pago_manual_identidad->b0_compra_manual","b0_compra_manual_evento->b0_compra_manual",
                "b0_compra_manual_evento->cuenta_acceso","b0_compra_manual_evento->b0_atribucion_plataforma",
                "b0_consulta_metricas_ia->cuenta_acceso","b0_consulta_metricas_ia->b0_fondo_tecnico",
                "b0_consulta_metricas_ia->b0_atribucion_plataforma","b0_consulta_metricas_ia->b0_evaluacion_caso",
                "b0_costo_operacion_ia->b0_operacion_ia","b0_operacion_ia->b0_regla_trabajo_manual",
                "b0_regla_trabajo_manual->b0_decision_plataforma","b0_regla_trabajo_manual->cuenta_acceso");
        assertEquals(105,claves.size(),"También se detecta una FK eliminada; no basta examinar las que sobreviven.");
        assertEquals(new TreeSet<>(esperadas),new TreeSet<>(claves.stream().map(ClaveFisica::arista).toList()));
        String esquema=jdbc.queryForObject("SELECT current_schema()",String.class);
        for(var clave:claves){
            assertTrue(clave.validada(),clave.arista());assertEquals(esquema,clave.esquemaDestino(),clave.arista());
            if(clave.arista().equals("b0_operacion_ia->b0_regla_trabajo_manual")){
                assertEquals("regla_manual,version_regla_manual",clave.columnasOrigen());assertEquals("id,version",clave.columnasDestino());
            }
            if(clave.arista().equals("b0_regla_trabajo_manual->b0_decision_plataforma")){
                assertEquals("decision",clave.columnasOrigen());assertEquals("id",clave.columnasDestino());
            }
            if(clave.arista().equals("b0_regla_trabajo_manual->cuenta_acceso")){
                assertEquals("responsable",clave.columnasOrigen());assertEquals("id_cuenta_acceso",clave.columnasDestino());
            }
            if(clave.arista().equals("b0_etapa_operacion_ia->b0_operacion_ia")&&clave.columnasOrigen().equals("organizacion_id,id_operacion")){
                assertEquals("organizacion_id,id_operacion",clave.columnasOrigen());
                assertEquals("organizacion_id,id_operacion",clave.columnasDestino());
            }
            if(clave.arista().equals("b0_medicion_tecnica->b0_etapa_operacion_ia")){
                assertEquals("id_operacion_b0,etapa_clave",clave.columnasOrigen());
                assertEquals("id_operacion_b0,clave",clave.columnasDestino());
            }
            var origen=Arrays.asList(clave.columnasOrigen().split(","));var destino=Arrays.asList(clave.columnasDestino().split(","));
            assertEquals(origen.size(),destino.size(),clave.arista());
            // V152: hechos financieros privados vinculados a la compra por su UUID global único.
            // No conceden lectura comercial por pertenecer a la organización de la compra.
            if(Set.of("b0_pago_manual_identidad","b0_compra_manual_evento").contains(clave.origen())
                    &&"b0_compra_manual".equals(clave.destino())){
                assertEquals(List.of("id_compra"),origen,clave.arista());
                assertEquals(List.of("id_compra"),destino,clave.arista());continue;
            }
            if("organizacion".equals(clave.destino())){
                assertEquals(List.of("organizacion_id"),origen,clave.arista());assertEquals(List.of("id_organizacion"),destino,clave.arista());
            }else if(TABLAS_ORGANIZACION_PRIVADAS.contains(clave.destino())||Set.of("persona","persona_rol","material_ia_operacion").contains(clave.destino())){
                int indice=origen.indexOf("organizacion_id");assertTrue(indice>=0,"Falta ámbito de origen: "+clave.arista());
                assertEquals("organizacion_id",destino.get(indice),"La FK debe vincular la MISMA organización: "+clave.arista());
            }else if(TABLAS_DUENO_TIPADO.contains(clave.destino())){
                assertTrue(origen.contains("id_operacion_b0")||origen.contains("operacion_b0")||origen.contains("operacion_evaluacion_b0")
                    ||origen.contains("dueno_binario")||origen.contains("organizacion_id"),"Falta identidad tipada en "+clave.arista());
                if(origen.contains("organizacion_id"))assertEquals("organizacion_id",destino.get(origen.indexOf("organizacion_id")));
                if(origen.contains("id_caso_evaluacion"))assertEquals(
                    "b0_propietario_binario".equals(clave.destino())?"caso":"id_caso_evaluacion",
                    destino.get(origen.indexOf("id_caso_evaluacion")),clave.arista());
            }else{
                assertTrue(TABLAS_PLATAFORMA_PRIVADAS.contains(clave.destino())||Set.of("cuenta_acceso","intento_salida_binaria").contains(clave.destino()),"Referencia global no clasificada como privada: "+clave.arista());
            }
        }
    }
    @Test void operacionNoPuedeReferenciarUnaPersonaAjenaAunqueSuRolSeaLocal(){
        var d=solicitud(equipo.agente());admitir(d);var original=repo.operacion(equipo.agente().idOrganizacion(),d.operacion()).orElseThrow();
        var ajeno=origenes.empresa().agente();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(t->{insertarCopia(original,ajeno.idPersona(),original.rol());return null;}));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_operacion_ia WHERE organizacion_id=?",Integer.class,original.organizacion()));
    }
    @Test void operacionNoPuedeReferenciarUnRolAjenoAunqueSuPersonaSeaLocal(){
        var d=solicitud(equipo.agente());admitir(d);var original=repo.operacion(equipo.agente().idOrganizacion(),d.operacion()).orElseThrow();
        var ajeno=origenes.empresa().agente();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.execute(t->{insertarCopia(original,original.persona(),ajeno.idRolOperativo());return null;}));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_operacion_ia WHERE organizacion_id=?",Integer.class,original.organizacion()));
    }
    private void insertarCopia(PresupuestoIaRepository.Operacion o,long persona,long rol){
        repo.insertar(new PresupuestoIaRepository.Operacion(o.organizacion(),UUID.randomUUID(),persona,rol,o.perfil(),o.versionPerfil(),o.tipo(),o.recurso(),
                o.finalidad(),o.origen(),o.peticion(),o.estado(),o.maximoCreditos(),o.maximoTecnico(),o.fondo(),o.admitida(),o.resultado()));
    }
    static BigDecimal n(String s){return new BigDecimal(s);}
    static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
    void monto(String esperado,String sql,Object... p){assertEquals(0,n(esperado).compareTo(jdbc.queryForObject(sql,BigDecimal.class,p)));}
}
