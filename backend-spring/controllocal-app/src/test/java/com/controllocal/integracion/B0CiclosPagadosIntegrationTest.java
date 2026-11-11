package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.planes.*;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.service.ContextosService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.*;
import com.controllocal.service.planes.ComprasVerificadasB0.*;
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
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Productor real/PG con fuente de pedidos SINTÉTICA. No acredita PSP, cobro ni consentimiento externos. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class B0CiclosPagadosIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired PresupuestoIaRepository presupuesto;
    @Autowired CiclosPagadosB0Repository repositorio;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    Map<UUID,Pedido> pedidos;TransactionTemplate tx;OrigenesDePrueba origenes;
    static final Instant OCT1=Instant.parse("2026-10-01T15:00:00Z"),OCT22=Instant.parse("2026-10-22T15:00:00Z");
    @BeforeEach void preparar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);pedidos=new ConcurrentHashMap<>();tx=new TransactionTemplate(gestor);
        origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contexto.getBean(ContextosService.class));
    }
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
    CiclosPagadosB0 servicio(Instant ahora){
        return new CiclosPagadosB0(List.of(id->Optional.ofNullable(pedidos.get(id))),presupuesto,repositorio,contexto.getBean(Documentos.class),Clock.fixed(ahora,ZoneOffset.UTC));
    }
    void aplicar(Pedido p,Instant ahora){pedidos.put(p.id(),p);tx.execute(t->servicio(ahora).aplicar(p.id()));}
    Pedido pedido(long org,Tipo tipo,PlanBrox plan,PaqueteBoost boost,int extras,UUID ciclo,Long version,Instant corte,String importe){
        return new Pedido(UUID.randomUUID(),org,tipo,plan,boost,extras,ciclo,version,CiclosPagadosB0.VERSION_OFERTA,"a".repeat(64),corte,
                n(importe),"USD","PSP_TEST","TRANSACCION_SINTETICA_"+UUID.randomUUID(),corte,"TEST NO PAGO REAL NI CONSENTIMIENTO EXTERNO");
    }
    long empresa(){return origenes.empresa().agente().idOrganizacion();}
    long individual(){return tx.execute(t->{
        String ref=UUID.randomUUID().toString().replace("-","");
        long org=jdbc.queryForObject("INSERT INTO organizacion(codigo,nombre,modalidad,estado) VALUES (?,?,'INDIVIDUAL','I') RETURNING id_organizacion",Long.class,"B0-"+ref.substring(0,15),"TEST B0 independiente");
        long persona=jdbc.queryForObject("INSERT INTO persona(organizacion_id,nombres_o_razon_social) VALUES (?,'Independiente TEST B0') RETURNING id_persona",Long.class,org);
        long rol=jdbc.queryForObject("INSERT INTO persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) VALUES (?,?,'AGENTE',?) RETURNING id_persona_rol",Long.class,org,persona,LocalDate.now(CalendarioComercial.ZONA));
        jdbc.update("INSERT INTO detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo) VALUES (?,?,?,?, 'D')",org,rol,"B0-A-"+rol,LocalDate.now(CalendarioComercial.ZONA));
        long cuenta=jdbc.queryForObject("INSERT INTO cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) VALUES (?,?,clock_timestamp(),'TEST-SIN-CREDENCIAL') RETURNING id_cuenta_acceso",Long.class,"b0-"+ref,"b0-"+ref+"@example.invalid");
        jdbc.update("INSERT INTO usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) VALUES (?,?,?,'AGENTE',false,'A')",org,cuenta,persona);
        jdbc.update("UPDATE organizacion SET estado='A' WHERE id_organizacion=?",org);return org;
    });}
    @Test void altaTeamCreaFuentesExactasUnaBolsaYPlazasSinInvocarProveedor(){
        long org=empresa();var p=pedido(org,Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0,null,null,OCT1,"75.00");aplicar(p,OCT1);
        var c=presupuesto.cicloVigente(org,OCT1).orElseThrow();assertEquals("TEAM",c.plan());assertEquals(Instant.parse("2026-11-01T15:00:00Z"),c.hasta());
        monto("21000","SELECT SUM(inicial) FROM b0_fuente_capacidad WHERE organizacion_id=?",org);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_fuente_capacidad WHERE organizacion_id=? AND origen='MENSUAL_BROKER'",Integer.class,org));
        assertEquals(5,jdbc.queryForObject("SELECT count(*) FROM b0_plaza WHERE organizacion_id=? AND tipo='AGENTE'",Integer.class,org));
        monto("3000","SELECT inicial FROM b0_fuente_capacidad WHERE organizacion_id=? AND origen='RESERVA'",org);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_operacion_ia WHERE organizacion_id=?",Integer.class,org));
    }
    @Test void mismaConfirmacionRepetidaNoCreaOtroPeriodoNiOtraCuota(){
        long org=empresa();var p=pedido(org,Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0,null,null,OCT1,"75.00");aplicar(p,OCT1);aplicar(p,OCT22);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_ciclo WHERE organizacion_id=?",Integer.class,org));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_acreditacion_pago WHERE organizacion_id=?",Integer.class,org));
        monto("21000","SELECT SUM(inicial) FROM b0_fuente_capacidad WHERE organizacion_id=?",org);
    }
    @Test void mejoraOneAProConservaPeriodoAnteriorYNuevaCuotaIntegra(){
        long org=individual();aplicar(pedido(org,Tipo.ALTA_PLAN,PlanBrox.ONE,null,0,null,null,OCT1,"15.00"),OCT1);
        var anterior=presupuesto.cicloVigente(org,OCT22).orElseThrow();
        aplicar(pedido(org,Tipo.MEJORA_PLAN,PlanBrox.ONE_PRO,null,0,anterior.id(),anterior.version(),OCT22,"24.16"),OCT22);
        var actual=presupuesto.cicloVigente(org,OCT22).orElseThrow();assertNotEquals(anterior.id(),actual.id());
        assertEquals(Instant.parse("2026-11-22T15:00:00Z"),actual.hasta());assertEquals("ONE_PRO",actual.plan());
        monto("9000","SELECT SUM(inicial) FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_ciclo=?",org,actual.id());
        monto("3000","SELECT SUM(inicial) FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_ciclo=?",org,anterior.id());
        assertEquals(anterior.hasta(),presupuesto.ciclo(org,anterior.id()).orElseThrow().hasta());
        assertEquals(OCT22,jdbc.queryForObject("SELECT cerrado_en FROM b0_ciclo WHERE organizacion_id=? AND id_ciclo=?",OffsetDateTime.class,org,anterior.id()).toInstant());
    }
    @Test void mejoraTeamAGrowthNoSumaRemanenteAnterior(){
        long org=empresa();aplicar(pedido(org,Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0,null,null,OCT1,"75.00"),OCT1);
        var c=presupuesto.cicloVigente(org,OCT22).orElseThrow();
        aplicar(pedido(org,Tipo.MEJORA_PLAN,PlanBrox.GROWTH,null,0,c.id(),c.version(),OCT22,"125.81"),OCT22);
        var nuevo=presupuesto.cicloVigente(org,OCT22).orElseThrow();
        monto("39000","SELECT SUM(inicial) FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_ciclo=?",org,nuevo.id());
        monto("21000","SELECT SUM(inicial) FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_ciclo=?",org,c.id());
    }
    @Test void brokerExtraSeProrrateaSinNuevaBolsaNiCuotaNiAniversario(){
        long org=empresa();aplicar(pedido(org,Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0,null,null,OCT1,"75.00"),OCT1);
        var c=presupuesto.cicloVigente(org,OCT22).orElseThrow();
        aplicar(pedido(org,Tipo.BROKER_ADICIONAL,PlanBrox.TEAM,null,1,c.id(),c.version(),OCT22,"1.61"),OCT22);
        var despues=presupuesto.cicloVigente(org,OCT22).orElseThrow();assertEquals(c.id(),despues.id());assertEquals(c.hasta(),despues.hasta());assertEquals(1,despues.brokersExtra());
        monto("21000","SELECT SUM(inicial) FROM b0_fuente_capacidad WHERE organizacion_id=?",org);
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM b0_plaza WHERE organizacion_id=? AND tipo='BROKER'",Integer.class,org));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_fuente_capacidad WHERE organizacion_id=? AND origen='MENSUAL_BROKER'",Integer.class,org));
    }
    @Test void fondosDistintosNoActivanNiRecalculanSilenciosamente(){
        long org=empresa();var p=pedido(org,Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0,null,null,OCT1,"74.99");
        assertThrows(ConflictoException.class,()->aplicar(p,OCT1));
        assertTrue(presupuesto.cicloVigente(org,OCT1).isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_acreditacion_pago WHERE organizacion_id=?",Integer.class,org));
    }
    @Test void boostRequierePlanPagadoYVenceDoceMesesDesdeCompraVerificada(){
        long org=empresa();var sin=pedido(org,Tipo.BOOST,null,PaqueteBoost.B5K,0,null,null,OCT1,"15.00");
        assertThrows(CapacidadDeshabilitadaException.class,()->aplicar(sin,OCT1));
        aplicar(pedido(org,Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0,null,null,OCT1,"75.00"),OCT1);
        var p=pedido(org,Tipo.BOOST,null,PaqueteBoost.B5K,0,null,null,OCT22,"15.00");aplicar(p,OCT22);aplicar(p,OCT22.plusSeconds(1));
        monto("5000","SELECT inicial FROM b0_fuente_capacidad WHERE organizacion_id=? AND origen='BOOST'",org);
        assertEquals(Instant.parse("2027-10-22T15:00:00Z"),jdbc.queryForObject("SELECT vence FROM b0_fuente_capacidad WHERE organizacion_id=? AND origen='BOOST'",OffsetDateTime.class,org).toInstant());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_fuente_capacidad WHERE organizacion_id=? AND origen='BOOST'",Integer.class,org));
    }
    @Test void renovacionAnticipadaNoEntregaCuotaAntesDelLimite(){
        long org=individual();aplicar(pedido(org,Tipo.ALTA_PLAN,PlanBrox.ONE_PRO,null,0,null,null,OCT1,"29.00"),OCT1);
        var c=presupuesto.cicloVigente(org,OCT22).orElseThrow();
        aplicar(pedido(org,Tipo.RENOVACION,PlanBrox.ONE,null,0,c.id(),c.version(),OCT22,"15.00"),OCT22);
        assertEquals(c.id(),presupuesto.cicloVigente(org,OCT22).orElseThrow().id());
        var nuevo=presupuesto.cicloVigente(org,c.hasta()).orElseThrow();assertNotEquals(c.id(),nuevo.id());assertEquals("ONE",nuevo.plan());
        monto("3000","SELECT SUM(inicial) FROM b0_fuente_capacidad WHERE organizacion_id=? AND id_ciclo=?",org,nuevo.id());
    }
    @Test void transaccionNoSeAcreditaEnDosOrganizaciones(){
        long a=empresa(),b=empresa();var p=pedido(a,Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0,null,null,OCT1,"75.00");aplicar(p,OCT1);
        var q=new Pedido(UUID.randomUUID(),b,p.tipo(),p.plan(),p.boost(),p.brokersExtra(),null,null,p.versionOferta(),p.huellaConsentimiento(),p.corte(),p.importeUsd(),p.moneda(),p.proveedor(),p.transaccion(),p.fondosVerificadosEn(),p.evidenciaVerificacion());
        assertThrows(ConflictoException.class,()->aplicar(q,OCT1));assertTrue(presupuesto.cicloVigente(b,OCT1).isEmpty());
    }
    @Test void ausenciaDeVerificacionServidorNoAcreditaPorUnIdentificador(){
        var cerrado=new CiclosPagadosB0(List.of(),presupuesto,repositorio,contexto.getBean(Documentos.class));
        assertThrows(CapacidadDeshabilitadaException.class,()->tx.execute(t->cerrado.aplicar(UUID.randomUUID())));
    }
    @Test void compraManualSinCaducidadConservaCorteYActivaEnLaFechaReal(){
        long org=empresa();var original=pedido(org,Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0,null,null,OCT1,"75.00");
        var manual=new Pedido(original.id(),org,original.tipo(),original.plan(),null,0,null,null,original.versionOferta(),original.huellaConsentimiento(),
                OCT1,original.importeUsd(),"USD","MANUAL_WEB_MVP:TEST",original.transaccion(),OCT22,"TEST fondos verificados posteriormente");
        aplicar(manual,OCT22);assertEquals(OCT22,presupuesto.cicloVigente(org,OCT22).orElseThrow().desde());
        assertTrue(presupuesto.cicloVigente(org,OCT1).isEmpty());
    }
    @Test void dosConfirmacionesConcurrentesAcreditanUnaSolaVez()throws Exception{
        long org=empresa();var p=pedido(org,Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0,null,null,OCT1,"75.00");pedidos.put(p.id(),p);
        var listas=new CountDownLatch(2);var salida=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<CiclosPagadosB0.Resultado> trabajo=()->{listas.countDown();assertTrue(salida.await(20,TimeUnit.SECONDS));return tx.execute(t->servicio(OCT1).aplicar(p.id()));};
        try{
            var a=pool.submit(trabajo);var b=pool.submit(trabajo);assertTrue(listas.await(20,TimeUnit.SECONDS));salida.countDown();
            assertEquals("ACREDITADO",a.get(60,TimeUnit.SECONDS).estado());assertEquals("ACREDITADO",b.get(60,TimeUnit.SECONDS).estado());
        }finally{salida.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_ciclo WHERE organizacion_id=?",Integer.class,org));
        monto("21000","SELECT SUM(inicial) FROM b0_fuente_capacidad WHERE organizacion_id=?",org);
    }
    @Test void cambioDeModalidadODeVersionNoConcedeOtraOferta(){
        long org=empresa();assertThrows(ReglaNegocioException.class,()->aplicar(pedido(org,Tipo.ALTA_PLAN,PlanBrox.ONE,null,0,null,null,OCT1,"15.00"),OCT1));
        aplicar(pedido(org,Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0,null,null,OCT1,"75.00"),OCT1);
        var c=presupuesto.cicloVigente(org,OCT22).orElseThrow();
        assertThrows(ConflictoException.class,()->aplicar(pedido(org,Tipo.MEJORA_PLAN,PlanBrox.GROWTH,null,0,c.id(),c.version()+1,OCT22,"125.81"),OCT22));
        assertEquals(c.id(),presupuesto.cicloVigente(org,OCT22).orElseThrow().id());
    }
    @Test void calendarioFinDeMesConservaAniversario31(){
        long org=individual();Instant enero=Instant.parse("2024-01-31T15:00:00Z");
        aplicar(pedido(org,Tipo.ALTA_PLAN,PlanBrox.ONE,null,0,null,null,enero,"15.00"),enero);
        var c=presupuesto.cicloVigente(org,enero).orElseThrow();assertEquals(Instant.parse("2024-02-29T15:00:00Z"),c.hasta());
        Instant febrero=Instant.parse("2024-02-20T15:00:00Z");
        aplicar(pedido(org,Tipo.RENOVACION,PlanBrox.ONE,null,0,c.id(),c.version(),febrero,"15.00"),febrero);
        assertEquals(Instant.parse("2024-03-31T15:00:00Z"),presupuesto.cicloVigente(org,c.hasta()).orElseThrow().hasta());
    }
    static BigDecimal n(String s){return new BigDecimal(s);}
    void monto(String esperado,String sql,Object... p){assertEquals(0,n(esperado).compareTo(jdbc.queryForObject(sql,BigDecimal.class,p)));}
}
