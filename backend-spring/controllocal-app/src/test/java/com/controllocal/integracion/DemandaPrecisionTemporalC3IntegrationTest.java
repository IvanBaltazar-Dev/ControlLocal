package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.RequerimientoService.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.demanda.InteresadosDeDemanda.*;
import com.controllocal.service.soporte.Procedencia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** T4 RF030: mismos casos Core/SID/revisión; sólo reloj técnico controlado en contexto TEST propio. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="brox.test.precision-rf030=true")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class DemandaPrecisionTemporalC3IntegrationTest {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private static final Procedencia PROC=Procedencia.deLaPantalla();
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired RequerimientoService demandas;
    @Autowired ActuacionesService actuaciones;
    private Object objetivo;
    private Clock original;
    @BeforeEach void observarRelojOriginal() {
        objetivo=AopTestUtils.getUltimateTargetObject(demandas);
        original=(Clock)ReflectionTestUtils.getField(objetivo,"reloj");assertNotNull(original);
    }
    @AfterEach void restaurarReloj(){if(objetivo!=null&&original!=null)ReflectionTestUtils.setField(objetivo,"reloj",original);}

    @Test void retiroEnEmpatesTemporalesConservaUnSoloInstantePersistido(){comprobar(TipoFinDemanda.RETIRADA);}
    @Test void finalizacionEnEmpatesTemporalesConservaUnSoloInstantePersistido(){comprobar(TipoFinDemanda.FINALIZADA);}

    private void comprobar(TipoFinDemanda tipo) {
        var banco=new OrigenesDePrueba(jdbc,gestor,captura,contextos);var equipo=banco.empresa();var actor=equipo.agente();var sesion=banco.sesion(actor);
        // Empate par primero: antes de normalizar, JDBC y castJSON discrepan y el guard rechaza.
        for(int nanos:List.of(123456500,123456100,123457500,999999500)) {
            ReflectionTestUtils.setField(objetivo,"reloj",original);
            var contacto=new Referencia(TipoReferencia.CONTACTO_NUEVO,null,null,null,new DatosContacto(null,null,null,null,"999000664",null));
            var datos=new DatosRequerimiento(null,null,null,null,null,null,"ALQUILER",List.of("DEPARTAMENTO"),null,null,null,null,null,
                    null,null,"Reloj controlado TEST RF030",null,List.of(),contacto,null,null,null,null,Set.of());
            var d=demandas.crear(datos,actor,sesion,clave(),PROC);
            Instant real=Instant.now(),base=real.truncatedTo(ChronoUnit.SECONDS);
            if(base.minusSeconds(2).atZone(LIMA).toLocalDate().equals(real.atZone(LIMA).toLocalDate()))base=base.minusSeconds(2);
            Instant declarado=base.plusNanos(nanos),persistible=declarado.truncatedTo(ChronoUnit.MICROS);
            ReflectionTestUtils.setField(objetivo,"reloj",Clock.fixed(declarado,ZoneOffset.UTC));
            long observada=d.version();
            var propuesta=actuaciones.prepararFinDemanda(actor,new FinDemanda(d.id(),observada,tipo,declarado.atZone(LIMA).toLocalDate(),"Fin con precisión declarada TEST"),sesion,PROC);
            var revision=actuaciones.revisar(actor,propuesta.referencia(),sesion);
            var confirmacion=new ActuacionesService.Confirmacion(revision.propuesta().version(),revision.propuesta().huella(),revision.pruebaRevision(),clave());
            var resultado=actuaciones.confirmar(actor,propuesta.referencia(),sesion,confirmacion,PROC);
            assertEquals("EXITO",resultado.estado(),()->"nanos="+nanos+", ref="+resultado.referencia()+", motivo="+resultado.motivo()+", resultado="+resultado.resultado());
            var recibo=demandas.consultarResultadoFin("actuacion:"+propuesta.referencia(),actor).orElseThrow();
            assertEquals(persistible,recibo.registradoEn());assertEquals(tipo,recibo.tipo());assertEquals(observada+1,recibo.version());
            var actual=demandas.buscar(d.id(),actor);var historica=demandas.consultar(d.id(),recibo.version(),null,actor);
            assertEquals(persistible,actual.fin().registradoEn());assertEquals(actual.fin(),historica.fin());
            Instant fila=jdbc.queryForObject("select fin_registrado_en from requerimiento_cliente where organizacion_id=? and id_requerimiento=?",
                    (r,n)->r.getObject(1,OffsetDateTime.class).toInstant(),actor.idOrganizacion(),d.id());
            Instant historia=jdbc.queryForObject("select registrado_en from requerimiento_version where organizacion_id=? and id_requerimiento=? and version=?",
                    (r,n)->r.getObject(1,OffsetDateTime.class).toInstant(),actor.idOrganizacion(),d.id(),recibo.version());
            assertEquals(persistible,fila);assertEquals(persistible,historia);
            assertEquals(Boolean.TRUE,jdbc.queryForObject("select snapshot_demanda_conserva_fila(v.snapshot_json::jsonb,r) from requerimiento_cliente r join requerimiento_version v on v.organizacion_id=r.organizacion_id and v.id_requerimiento=r.id_requerimiento and v.version=r.version where r.organizacion_id=? and r.id_requerimiento=?",Boolean.class,actor.idOrganizacion(),d.id()));
            assertEquals(resultado,actuaciones.confirmar(actor,propuesta.referencia(),sesion,confirmacion,PROC));
            assertEquals(recibo,demandas.consultarResultadoFin("actuacion:"+propuesta.referencia(),actor).orElseThrow());
            assertEquals(2,jdbc.queryForObject("select count(*) from requerimiento_version where organizacion_id=? and id_requerimiento=?",Integer.class,actor.idOrganizacion(),d.id()));
        }
    }
    private static String clave(){return "precision-rf030-test-"+UUID.randomUUID();}
}
