package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.presupuesto.MetricasIaB0Repository;
import com.controllocal.persistence.presupuesto.MetricasIaB0Repository.Ambito;
import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository.Caso;
import com.controllocal.service.ContextosService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.Idempotencia;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** PG/C1/R25/ledger/auditoría reales con fixture existente TEST. No proveedor, grants o fondos operativos. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,EvaluacionIaB0IntegrationTest.AcreditacionTest.class},webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class MetricasIaB0IntegrationTest {
    @DynamicPropertySource static void db(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext app;@Autowired MetricasIaB0 metricas;@TempDir Path directorio;
    EvaluacionIaB0IntegrationTest d;SesionPlataforma ops;UUID grantMetricas;
    @BeforeEach void preparar(TestInfo info){
        d=new EvaluacionIaB0IntegrationTest();app.getAutowireCapableBeanFactory().autowireBean(d);d.directorio=directorio;d.preparar(info);
        grantMetricas=d.conceder(Atribucion.OPERACION_IA_PLATFORM,Set.of(Accion.CONSULTAR_ESTADO),Set.of(Campo.METRICA_TECNICA),null,null,d.cuenta);
        ops=EvaluacionIaB0IntegrationTest.conGrant(d.admin,grantMetricas);
    }
    @AfterEach void limpiar(){d.limpiar();}
    Instant desde(){return d.ahora.minusSeconds(5);}
    Instant hasta(){return d.ahora.plusSeconds(300);}
    SolicitudEvaluacion iniciada(){var s=d.solicitud();d.admitir(s);d.iniciar(s);return s;}
    void medir(SolicitudEvaluacion s){d.tx.executeWithoutResult(t->d.presupuesto.registrarMediciones(d.ref(s),d.medicion("0.1")));}
    void observar(SolicitudEvaluacion s,String etapa,long nanos,Integer status){
        var ref=d.ref(s);String clave=etapa==null?PresupuestoIaService.claveDeIntento(ref):PresupuestoIaService.claveDeEtapa(ref,etapa);
        var observacion=new ObservacionHttpIa(ObservacionHttpIa.ESQUEMA,clave,"PRV_TEST","MODELO_TEST",d.version,d.ahora,nanos,
            ObservacionHttpIa.Resultado.RESPUESTA_COMPLETA,status,true);
        d.tx.executeWithoutResult(t->{assertEquals(1,d.jdbc.update("""
            INSERT INTO b0_antecedente(organizacion_id,id_operacion,id_operacion_b0,id_caso_evaluacion,hecho,contenido,registrado_en)
            SELECT NULL,id_operacion,id_operacion_b0,id_caso_evaluacion,'OBSERVACION_HTTP_IA',CAST(? AS jsonb),clock_timestamp()
            FROM b0_operacion_ia WHERE id_caso_evaluacion=? AND id_operacion=?
            """,d.json.objetoCanonico(observacion),s.caso(),s.operacion()));});
    }
    long auditorias(UUID concesion){return d.jdbc.queryForObject("SELECT count(*) FROM b0_consulta_metricas_ia WHERE concesion=?",Long.class,concesion);}
    @Test void soloCuentaLaCohorteAutorizadaYDeclaraObservacionDesconocida(){
        var s=iniciada();var r=metricas.tecnica(ops,desde(),hasta());
        assertEquals(1,r.cobertura().operaciones());assertEquals(0,r.cobertura().medicionesConservadas());
        assertEquals(1,r.cobertura().operacionesSinObservacionValida());assertNull(r.latencia());
        assertTrue(r.unidadesOriginales().isEmpty());assertTrue(r.disponibilidadCantidades().startsWith("NO_HABILITADAS"));
        assertEquals(1,auditorias(grantMetricas));
        assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_consulta_metricas_ia WHERE concesion=? AND caso IS NULL AND fondo IS NULL AND operacion IS NULL AND clase='TECNICA'",Integer.class,grantMetricas));
    }
    @Test void dosMedicionesDosObservacionesYEtapaNoMultiplicanUnidadesNiCostos(){
        var s=iniciada();var m1=d.medicion("0.1");var m2=d.medicion("0.1");
        d.tx.executeWithoutResult(t->{d.presupuesto.registrarMediciones(d.ref(s),m1);d.presupuesto.programarEtapa(d.ref(s),"QC","PRV_TEST","c".repeat(64));});
        assertTrue(Boolean.TRUE.equals(d.tx.execute(t->d.presupuesto.iniciarEtapaEvaluacion(s,"QC",d.operador))));
        var medidas=new ArrayList<>(m1.mediciones());medidas.addAll(m2.mediciones());
        d.tx.executeWithoutResult(t->d.presupuesto.registrarResultadoEtapa(d.ref(s),"QC",new ResultadoFacturable(medidas)));
        observar(s,null,10,200);observar(s,"QC",30,404);
        var r=metricas.tecnica(ops,desde(),hasta());
        assertEquals(1,r.cobertura().operaciones());assertEquals(2,r.cobertura().medicionesConservadas());assertEquals(1,r.cobertura().etapasConservadas());
        assertEquals(2,r.cobertura().observacionesValidas());assertEquals(1,r.cobertura().erroresHttp());
        assertEquals(1,r.unidadesOriginales().size());assertEquals(2,r.unidadesOriginales().getFirst().mediciones());assertNull(r.unidadesOriginales().getFirst().cantidadOriginal());
        assertEquals(0,new BigDecimal("20").compareTo(r.latencia().mediaNanos()));assertEquals(30L,r.latencia().p95Nanos().longValue());
        var f=metricas.financiera(d.financiero,d.caso,s.operacion(),desde(),hasta());
        assertEquals(2,f.mediciones().size());assertTrue(f.mediciones().stream().allMatch(x->"CALCULADO".equals(x.estadoCosto())));assertNull(f.costoPorTareaCorrecta());
        assertEquals(1,d.jdbc.queryForObject("SELECT count(*) FROM b0_consulta_metricas_ia WHERE concesion=? AND caso=? AND fondo=? AND operacion=? AND clase='FINANCIERA'",Integer.class,d.grantFin,d.caso,d.fondo,s.operacion()));
    }
    @Test void periodoEsDeAdmisionIncluyeHechosTardiosYExcluyeSuHastaExacto(){
        var s=iniciada();var admitida=d.dinero.evaluacion(d.caso,s.operacion()).orElseThrow().admitida();
        medir(s);observar(s,null,50,200);
        var r=metricas.tecnica(ops,admitida,admitida.plusNanos(1000));
        assertEquals(1,r.cobertura().operaciones());assertEquals(1,r.cobertura().medicionesConservadas());assertEquals(1,r.cobertura().observacionesTardiasIncluidas());
        assertEquals(0,metricas.tecnica(ops,admitida.minusSeconds(1),admitida).cobertura().operaciones());
        assertEquals(0,metricas.tecnica(ops,admitida.plusNanos(1000),hasta()).cobertura().operaciones());
    }
    @Test void casoYComandoSeResuelvenJuntosAunqueOtroCasoCompartaElComando(){
        var s=iniciada();medir(s);
        UUID otro=UUID.randomUUID();var c=new Caso(otro,d.plan,d.version,"OTRO_CASO_TEST",EvaluacionIaB0IntegrationTest.hash(d.png),
            "SINTETICO TEST","DATOS PROPIOS TEST",UUID.randomUUID(),d.png.length,"image/png");
        d.tx.executeWithoutResult(t->d.evaluaciones.registrarCaso(c,d.decision("REGISTRAR_CASO_EVALUACION",c,null),d.admin));
        UUID g=d.conceder(Atribucion.OPERACION_IA_PLATFORM,Set.of(Accion.ADMITIR,Accion.INICIAR),Set.of(Campo.ESTADO),otro,null,d.cuenta);
        var segundo=new SolicitudEvaluacion(s.operacion(),otro,d.version,"a".repeat(64),d.fin,"b".repeat(64));
        d.tx.execute(t->d.presupuesto.admitirEvaluacion(segundo,EvaluacionIaB0IntegrationTest.conGrant(d.admin,g)));
        assertEquals(1,metricas.financiera(d.financiero,d.caso,s.operacion(),desde(),hasta()).mediciones().size());
        assertTrue(metricas.financiera(d.financiero,otro,s.operacion(),desde(),hasta()).mediciones().isEmpty());
        UUID limitado=d.conceder(Atribucion.FINANCIERO_PLATFORM,Set.of(Accion.LEER_LEDGER),Set.of(Campo.LEDGER),d.caso,d.fondo,d.cuenta);
        assertThrows(AccesoNoAutorizadoException.class,()->metricas.financiera(EvaluacionIaB0IntegrationTest.conGrant(d.admin,limitado),otro,s.operacion(),desde(),hasta()));
        assertEquals(0,auditorias(limitado));
    }
    @Test void fondoAjenoNoObtieneLaOperacionAunquePlanCasoYComandoCoincidan(){
        var s=iniciada();medir(s);UUID fondoAjeno=UUID.randomUUID();
        d.tx.executeWithoutResult(t->d.jdbc.update("""
            INSERT INTO b0_fondo_tecnico(id_fondo,moneda,financiado,desde,hasta,evidencia_financiacion,habilitado)
            VALUES(?,'USD',0,?,?,'TEST NO FONDOS REALES',false)
            """,fondoAjeno,d.ahora.minusSeconds(10).atOffset(ZoneOffset.UTC),d.ahora.plusSeconds(300).atOffset(ZoneOffset.UTC)));
        UUID g=d.conceder(Atribucion.FINANCIERO_PLATFORM,Set.of(Accion.LEER_LEDGER),Set.of(Campo.LEDGER),null,fondoAjeno,d.cuenta);
        assertThrows(NoEncontradoException.class,()->metricas.financiera(EvaluacionIaB0IntegrationTest.conGrant(d.admin,g),d.caso,s.operacion(),desde(),hasta()));
        assertEquals(0,auditorias(g));assertEquals(1,metricas.financiera(d.financiero,d.caso,s.operacion(),desde(),hasta()).mediciones().size());
    }
    @Test void tenerEstadoQaOFinNoConcedeAutomaticamenteElCampoTecnico(){
        iniciada();
        assertThrows(AccesoNoAutorizadoException.class,()->metricas.tecnica(d.operador,desde(),hasta()));
        assertThrows(AccesoNoAutorizadoException.class,()->metricas.tecnica(d.qa,desde(),hasta()));
        assertThrows(AccesoNoAutorizadoException.class,()->metricas.tecnica(d.financiero,desde(),hasta()));
        assertEquals(0,auditorias(d.grantOp));assertEquals(0,auditorias(d.grantQa));assertEquals(0,auditorias(d.grantFin));
    }
    @Test void identidadDeEvaluacionSinPerfilComercialNoAbreOtroPlanOVersion(){
        var s=iniciada();medir(s);var repo=app.getBean(MetricasIaB0Repository.class);
        var o=d.dinero.evaluacion(d.caso,s.operacion()).orElseThrow();
        assertNull(o.perfil());assertNull(o.versionPerfil());
        for(var a:List.of(new Ambito(UUID.randomUUID(),d.version,"EVALUACION_TEST",d.caso,d.fondo,desde(),hasta()),
                new Ambito(d.plan,"OTRA_VERSION_TEST","EVALUACION_TEST",d.caso,d.fondo,desde(),hasta()))){
            assertEquals(0,repo.tecnica(a).operaciones());assertTrue(repo.financiera(a,s.operacion()).isEmpty());
        }
        assertEquals(1,metricas.financiera(d.financiero,d.caso,s.operacion(),desde(),hasta()).mediciones().size());
    }
    @Test void auditoriaSqlNoAdmiteAtribucionOFondoAjenos(){
        var s=iniciada();var repo=app.getBean(MetricasIaB0Repository.class);
        var propio=new Ambito(d.plan,d.version,"EVALUACION_TEST",d.caso,d.fondo,desde(),hasta());
        assertThrows(org.springframework.dao.DataAccessException.class,()->d.tx.execute(t->{repo.auditar(d.cuenta,grantMetricas,propio,"FINANCIERA",s.operacion());return null;}));
        var ajeno=new Ambito(d.plan,d.version,"EVALUACION_TEST",d.caso,UUID.randomUUID(),desde(),hasta());
        assertThrows(org.springframework.dao.DataAccessException.class,()->d.tx.execute(t->{repo.auditar(d.cuenta,d.grantFin,ajeno,"FINANCIERA",s.operacion());return null;}));
        assertEquals(0,auditorias(grantMetricas));assertEquals(0,auditorias(d.grantFin));
    }
    @Test void unaConsultaFinancieraConservaSuAuditoriaInmutable(){
        var s=iniciada();metricas.financiera(d.financiero,d.caso,s.operacion(),desde(),hasta());assertEquals(1,auditorias(d.grantFin));
        assertThrows(org.springframework.dao.DataAccessException.class,()->d.tx.execute(t->d.jdbc.update(
            "UPDATE b0_consulta_metricas_ia SET operacion=? WHERE concesion=?",UUID.randomUUID(),d.grantFin)));
        assertThrows(org.springframework.dao.DataAccessException.class,()->d.tx.execute(t->d.jdbc.update(
            "DELETE FROM b0_consulta_metricas_ia WHERE concesion=?",d.grantFin)));assertEquals(1,auditorias(d.grantFin));
    }
    @Test void revocacionAntesDeEntregaDeniegaYRestauraAuditoriaPorRollback(){
        iniciada();long antes=auditorias(grantMetricas);
        var repo=new MetricasIaB0Repository(d.jdbc){
            @Override public void auditar(long cuenta,UUID concesion,Ambito a,String clase,UUID comando){
                super.auditar(cuenta,concesion,a,clase,comando);
                d.jdbc.update("UPDATE sesion_acceso SET revocada=true WHERE referencia_hash=?",Idempotencia.huellaExacta(ops.referenciaSesion()));
            }
        };
        var servicio=new MetricasIaB0(d.atribuciones,repo,d.json);
        assertThrows(CredencialesInvalidasException.class,()->d.tx.execute(t->servicio.tecnica(ops,desde(),hasta())));
        assertEquals(antes,auditorias(grantMetricas));
        assertEquals(d.cuenta,app.getBean(ContextosService.class).exigirIdentidad(ops.referenciaSesion(),ops.revisionSesion()).identidad().idCuenta());
    }
}
