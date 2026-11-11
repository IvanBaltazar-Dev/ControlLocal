package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.planes.*;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.*;
import com.controllocal.service.planes.ComprasVerificadasB0.Tipo;
import com.controllocal.service.planes.DatosCompraManual.*;
import com.controllocal.service.plataforma.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.controllocal.web.seguridad.TokenService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Flujo Main/C1/R25/PG real. Identidades y dinero SINTÉTICOS; cero WhatsApp/cobro/IA reales. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,ComprasManualesB0IntegrationTest.ConfiguracionTest.class},webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=4")
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class ComprasManualesB0IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @TestConfiguration static class ConfiguracionTest {@Bean Acreditador acreditadorCompra(){return new Acreditador();}}
    static class Acreditador implements AcreditacionesTitularPlataforma {
        final Map<UUID,Decision> decisiones=new ConcurrentHashMap<>();
        public Optional<Decision> acreditar(UUID id){return Optional.ofNullable(decisiones.get(id));}
    }
    @Autowired ComprasManualesB0 compras;@Autowired ComprasManualesVerificadasB0 fuente;
    @Autowired ContextosService contextos;@Autowired AtribucionesPlataforma atribuciones;
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;@Autowired Documentos json;
    @Autowired MotorDeCaptura captura;@Autowired CuentaService cuentas;@Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;@Autowired Acreditador acreditador;
    @Autowired MockMvc http;@Autowired TokenService tokens;
    TransactionTemplate tx;OrigenesDePrueba origenes;SesionesComercialesDePrueba sesiones;
    Actor comprador;ContextoSesion sid;SesionPlataforma financiero;long cuentaFin;UUID grant;
    @BeforeEach void preparar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);
        origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        comprador=origenes.empresa().admin();sid=sesiones.de(comprador);var identidad=cuentaPlataforma();
        cuentaFin=contextos.exigirIdentidad(identidad.referenciaSesion(),1).identidad().idCuenta();
        financiero=conceder(identidad,Instant.now().plusSeconds(3600),Set.of(Campo.ESTADO,Campo.PEDIDO_COMERCIAL,Campo.DATOS_PAGO_VERIFICADO));grant=financiero.concesion();
    }
    @AfterEach void terminar(){EvidenciaC4DePrueba.limpiar();}
    SesionPlataforma cuentaPlataforma(){return tx.execute(t->{
        String x=UUID.randomUUID().toString(),ref=Base64.getUrlEncoder().withoutPadding().encodeToString((x+x).substring(0,32).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        long id=jdbc.queryForObject("INSERT INTO cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) VALUES(?,?,clock_timestamp(),'TEST NO LOGIN') RETURNING id_cuenta_acceso",Long.class,"compra-"+x,"compra-"+x+"@example.invalid");
        var ahora=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);jdbc.update("INSERT INTO sesion_acceso(referencia_hash,semilla_contextos,id_cuenta_acceso,revision,creada_en,emitida_en,vence_en) VALUES(?,?,?,1,?,?,?)",
                Idempotencia.huellaExacta(ref),"c".repeat(64),id,utc(ahora.minusSeconds(2)),utc(ahora.minusSeconds(1)),utc(ahora.plusSeconds(1800)));
        return new SesionPlataforma(ref,1,null);
    });}
    SesionPlataforma conceder(SesionPlataforma identidad,Instant hasta,Set<Campo> campos){
        long cuenta=contextos.exigirIdentidad(identidad.referenciaSesion(),identidad.revisionSesion()).identidad().idCuenta();UUID id=UUID.randomUUID();
        var d=new Datos(id,cuenta,Atribucion.FINANCIERO_PLATFORM,null,null,null,null,AtribucionesPlataforma.FINALIDAD_COMPRAS,
                Set.of(Accion.CONSULTAR_COMPRA,Accion.INICIAR_VERIFICACION,Accion.VERIFICAR_FONDOS,Accion.REGISTRAR_INCIDENCIA),campos,
                Instant.now().minusSeconds(1),hasta,"TEST NO PERMISO REAL",RecursoCompra.COMPRAS_MANUALES_WEB_MVP);
        UUID decision=UUID.randomUUID();acreditador.decisiones.put(decision,new AcreditacionesTitularPlataforma.Decision(decision,cuenta,cuenta,"CONCEDER_ATRIBUCION",Idempotencia.huellaExacta(json.objetoCanonico(d)),"TEST ACREDITACION SINTETICA",Instant.now()));
        atribuciones.conceder(d,decision,identidad);return new SesionPlataforma(identidad.referenciaSesion(),identidad.revisionSesion(),id);
    }
    Compra preparada(){return compras.preparar(comprador,sid,UUID.randomUUID(),new Seleccion(Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0));}
    Apertura consentida(){var c=preparada();return compras.consentir(comprador,sid,c.referencia(),confirmacion(c));}
    Confirmacion confirmacion(Compra c){return new Confirmacion(UUID.randomUUID(),c.version());}
    Verificacion fondos(String importe){return new Verificacion(new FechaPago(LocalDate.now(CalendarioComercial.ZONA),null,"TEST fecha sin hora; cuenta bancaria PRIVADA"),"BANCO_TEST",UUID.randomUUID().toString(),new BigDecimal(importe),"USD","TEST verificación fondos SINTETICOS; secreto bancario no cliente");}
    DetalleFinanciero verificar(Compra c,Verificacion f){return compras.verificarFondos(financiero,c.referencia(),confirmacion(c),f);}
    @Test void referenciaNoPruebaPagoYConsentimientoAbreSoloElWhatsAppOficial(){
        var c=preparada();assertEquals(Estado.PENDIENTE,c.estado());assertFalse(c.consentida());assertTrue(fuente.consultar(c.referencia()).isEmpty());
        var comando=confirmacion(c);var a=compras.consentir(comprador,sid,c.referencia(),comando);assertTrue(a.enlaceWhatsApp().startsWith("https://wa.me/51920744084?text="));
        assertTrue(a.mensaje().contains(c.referencia().toString()));assertFalse(a.mensaje().contains("TEST origen"));assertTrue(fuente.consultar(c.referencia()).isEmpty());
        assertEquals(a,compras.consentir(comprador,sid,c.referencia(),comando));assertEquals(2,numero("b0_compra_manual_evento",c.referencia()));
    }
    @Test void cuatroEstadosYObjetivoNoAcreditanPorPagarOInformar(){
        var c=consentida().compra();var v=fondos("75.00");
        var iniciada=compras.iniciarVerificacion(financiero,c.referencia(),confirmacion(c),new PagoInformado(v.fecha(),"captura NO fondos"));
        assertEquals(Estado.EN_VERIFICACION,iniciada.compra().estado());assertTrue(fuente.consultar(c.referencia()).isEmpty());
        assertNull(iniciada.compra().pago().instante());assertNull(iniciada.compra().pago().objetivoVerificacion());
        var f=verificar(iniciada.compra(),v);assertEquals(Estado.EN_VERIFICACION,f.compra().estado());assertTrue(fuente.consultar(c.referencia()).isPresent());
        var a=compras.acreditar(financiero,c.referencia());assertEquals(Estado.ACREDITADA,a.compra().estado());assertEquals(1,numero("b0_acreditacion_pago",c.referencia(),"id_pedido"));
        assertEquals(a,compras.acreditar(financiero,c.referencia()));
    }
    @Test void diferenciaConservaFondosYNoCambiaPrecioOCreaSaldo(){
        var c=consentida().compra();var f=verificar(c,fondos("74.99"));assertThrows(ConflictoException.class,()->compras.acreditar(financiero,c.referencia()));
        var i=compras.activacionPendiente(financiero,c.referencia());assertEquals(Estado.CON_INCIDENCIA,i.compra().estado());assertEquals(f.fondos(),i.fondos());
        assertEquals(new BigDecimal("75.00"),i.compra().terminos().totalUsd());assertEquals(0,numero("b0_acreditacion_pago",c.referencia(),"id_pedido"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_ciclo WHERE organizacion_id=?",Integer.class,comprador.idOrganizacion()));
    }
    @Test void compradorNoRecibeEvidenciaBancariaNiAutoridadFinanciera(){
        var c=consentida().compra();verificar(c,fondos("75.00"));var publica=compras.consultar(comprador,sid,c.referencia());String contenido=json.objetoCanonico(publica);
        assertFalse(contenido.contains("secreto bancario"));assertFalse(contenido.contains("cuenta bancaria"));assertFalse(contenido.contains("BANCO_TEST"));assertFalse(contenido.contains("concesion"));
        assertThrows(AccesoNoAutorizadoException.class,()->compras.consultarFinanciero(new SesionPlataforma(sid.referenciaSesion(),sid.revisionContexto(),grant),c.referencia()));
    }
    @Test void otraOrganizacionNiBrokerSinGobiernoPuedenConsultarOContratar(){
        var c=consentida().compra();var otra=origenes.empresa();var otroSid=sesiones.de(otra.admin());
        assertThrows(NoEncontradoException.class,()->compras.consultar(otra.admin(),otroSid,c.referencia()));
        var brokerSid=sesiones.de(otra.broker());assertThrows(AccesoNoAutorizadoException.class,()->compras.catalogo(otra.broker(),brokerSid));
        assertThrows(AccesoNoAutorizadoException.class,()->compras.preparar(otra.broker(),brokerSid,UUID.randomUUID(),new Seleccion(Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0)));
    }
    /** Dos cuentas C1/R25 y organizaciones TEST compiten por el mismo pago; cero cobro o acreditación reales. */
    @Test void dosFinancierosDePedidosDistintosNoVerificanElMismoPagoSimultaneamente()throws Exception{
        var primera=consentida().compra();long orgPrimera=comprador.idOrganizacion();
        var otro=origenes.empresa().admin();var otroSid=sesiones.de(otro);long orgSegunda=otro.idOrganizacion();
        var preparada=compras.preparar(otro,otroSid,UUID.randomUUID(),new Seleccion(Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0));
        var segunda=compras.consentir(otro,otroSid,preparada.referencia(),confirmacion(preparada)).compra();
        var otroFinanciero=conceder(cuentaPlataforma(),Instant.now().plusSeconds(3600),Set.of(Campo.ESTADO,Campo.PEDIDO_COMERCIAL,Campo.DATOS_PAGO_VERIFICADO));
        long otraCuenta=contextos.exigirIdentidad(otroFinanciero.referenciaSesion(),otroFinanciero.revisionSesion()).identidad().idCuenta();
        assertNotEquals(orgPrimera,orgSegunda);assertNotEquals(cuentaFin,otraCuenta);assertNotEquals(financiero.concesion(),otroFinanciero.concesion());
        assertEquals(new BigDecimal("75.00"),primera.terminos().totalUsd());assertEquals(primera.terminos().totalUsd(),segunda.terminos().totalUsd());
        var bruto=fondos("75.00");var mismoPago=new Verificacion(bruto.fecha(),bruto.origen().toUpperCase(Locale.ROOT),bruto.referencia().toUpperCase(Locale.ROOT),bruto.importe(),bruto.moneda(),bruto.evidencia());
        assertEquals("USD",mismoPago.moneda());
        record Participante(SesionPlataforma sesion,long cuenta,long organizacion,Compra compra,Confirmacion comando){}
        record Intento(Participante participante,DetalleFinanciero resultado,RuntimeException fallo){}
        var uno=new Participante(financiero,cuentaFin,orgPrimera,primera,confirmacion(primera));
        var dos=new Participante(otroFinanciero,otraCuenta,orgSegunda,segunda,confirmacion(segunda));
        List<String> tablas=List.of("b0_acreditacion_pago","b0_ciclo","b0_linea_financiada","b0_fuente_capacidad","b0_plaza");
        Map<String,String> derechosAntes=new LinkedHashMap<>();
        for(String tabla:tablas)derechosAntes.put(tabla,jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM "+tabla+" t WHERE organizacion_id IN (?,?)",String.class,orgPrimera,orgSegunda));
        var listos=new CountDownLatch(2);var salir=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Set<Integer> conexiones=ConcurrentHashMap.newKeySet();Set<Long> transacciones=ConcurrentHashMap.newKeySet();
        java.util.function.Function<Participante,Callable<Intento>> tarea=p->()->{
            try{
                // Una TX por worker; la excepción cruza TransactionTemplate para asegurar rollback real.
                var resultado=tx.execute(t->{
                    assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                    conexiones.add(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
                    transacciones.add(jdbc.queryForObject("SELECT txid_current()",Long.class));
                    listos.countDown();esperar(salir); // Ambas conexiones adquiridas ANTES del servicio.
                    return compras.verificarFondos(p.sesion(),p.compra().referencia(),p.comando(),mismoPago);
                });
                return new Intento(p,resultado,null);
            }catch(RuntimeException e){return new Intento(p,null,e);}
        };
        List<Intento> intentos;
        try{
            var a=pool.submit(tarea.apply(uno));var b=pool.submit(tarea.apply(dos));
            assertTrue(listos.await(15,TimeUnit.SECONDS));salir.countDown();
            intentos=List.of(a.get(40,TimeUnit.SECONDS),b.get(40,TimeUnit.SECONDS));
        }finally{salir.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
        assertEquals(2,conexiones.size(),"Dos conexiones PostgreSQL distintas deben disputar el pago.");
        assertEquals(2,transacciones.size(),"Cada financiero conserva su propia transacción real.");
        var exitos=intentos.stream().filter(i->i.fallo()==null).toList();var fallos=intentos.stream().filter(i->i.fallo()!=null).toList();
        assertEquals(1,exitos.size());assertEquals(1,fallos.size());assertInstanceOf(ConflictoException.class,fallos.getFirst().fallo());
        var ganador=exitos.getFirst();var perdedor=fallos.getFirst();var g=ganador.participante();var p=perdedor.participante();
        assertEquals(Estado.EN_VERIFICACION,ganador.resultado().compra().estado());
        assertEquals(mismoPago,ganador.resultado().fondos().verificacion());
        assertEquals(g.cuenta(),ganador.resultado().fondos().cuenta());assertEquals(g.sesion().concesion(),ganador.resultado().fondos().concesion());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_pago_manual_identidad WHERE origen=? AND referencia=?",Integer.class,mismoPago.origen(),mismoPago.referencia()));
        assertEquals(g.compra().referencia(),jdbc.queryForObject("SELECT id_compra FROM b0_pago_manual_identidad WHERE origen=? AND referencia=?",UUID.class,mismoPago.origen(),mismoPago.referencia()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_compra_manual WHERE id_compra IN (?,?) AND fondos IS NOT NULL",Integer.class,primera.referencia(),segunda.referencia()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_compra_manual_evento WHERE id_compra IN (?,?) AND tipo='FONDOS_VERIFICADOS'",Integer.class,primera.referencia(),segunda.referencia()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_compra_manual_evento WHERE id_compra=? AND tipo='FONDOS_VERIFICADOS' AND id_comando=? AND cuenta=? AND concesion=?",Integer.class,g.compra().referencia(),g.comando().comando(),g.cuenta(),g.sesion().concesion()));
        var vigente=compras.consultarFinanciero(p.sesion(),p.compra().referencia());
        assertNull(vigente.fondos());assertEquals(p.compra(),vigente.compra(),"El perdedor conserva versión, estado, consentimiento e historia sin el efecto rechazado.");
        assertEquals(0,numero("b0_pago_manual_identidad",p.compra().referencia()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM b0_compra_manual_evento WHERE id_compra=? AND id_comando=?",Integer.class,p.compra().referencia(),p.comando().comando()));
        var fuenteGanadora=fuente.consultar(g.compra().referencia()).orElseThrow();assertTrue(fuente.consultar(p.compra().referencia()).isEmpty());
        assertEquals(g.compra().referencia(),fuenteGanadora.id());assertEquals(g.organizacion(),fuenteGanadora.organizacion());
        assertEquals(0,mismoPago.importe().compareTo(fuenteGanadora.importeUsd()));assertEquals(mismoPago.moneda(),fuenteGanadora.moneda());
        assertEquals("MANUAL_WEB_MVP:"+mismoPago.origen(),fuenteGanadora.proveedor());assertEquals(mismoPago.referencia(),fuenteGanadora.transaccion());
        for(String tabla:tablas)assertEquals(derechosAntes.get(tabla),jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM "+tabla+" t WHERE organizacion_id IN (?,?)",String.class,orgPrimera,orgSegunda),"Verificar fondos no acredita ni modifica derechos: "+tabla);
    }
    @Test void mismaReferenciaFinancieraNoSeUsaEnOtroPedidoNiTenant(){
        var c=consentida().compra();var v=fondos("75.00");verificar(c,v);
        var otro=origenes.empresa().admin();var os=sesiones.de(otro);var p=compras.preparar(otro,os,UUID.randomUUID(),new Seleccion(Tipo.ALTA_PLAN,PlanBrox.TEAM,null,0));
        var q=compras.consentir(otro,os,p.referencia(),confirmacion(p)).compra();
        assertThrows(ConflictoException.class,()->verificar(q,v));assertTrue(fuente.consultar(q.referencia()).isEmpty());
    }
    @Test void revocacionDeConcesionImpideLecturasYNuevosEfectos(){
        var c=consentida().compra();verificar(c,fondos("75.00"));
        String motivo="TEST revocada";UUID decision=UUID.randomUUID();
        acreditador.decisiones.put(decision,new AcreditacionesTitularPlataforma.Decision(decision,cuentaFin,cuentaFin,"REVOCAR_ATRIBUCION",
                Idempotencia.huellaExacta(json.objetoCanonico(Map.of("concesion",grant,"motivo",motivo))),"TEST no decisión real",Instant.now()));
        atribuciones.revocar(grant,decision,motivo,financiero);
        assertThrows(AccesoNoAutorizadoException.class,()->compras.consultarFinanciero(financiero,c.referencia()));
        assertThrows(AccesoNoAutorizadoException.class,()->compras.acreditar(financiero,c.referencia()));assertTrue(fuente.consultar(c.referencia()).isPresent());
    }
    @Test void contenidoDeComandoYVersionNoSeReutilizanParaOtroHecho(){
        var c=consentida().compra();var d=confirmacion(c);var v=fondos("75.00");var f=compras.verificarFondos(financiero,c.referencia(),d,v);
        assertEquals(f,compras.verificarFondos(financiero,c.referencia(),d,v));
        assertThrows(ConflictoException.class,()->compras.verificarFondos(financiero,c.referencia(),d,fondos("75.00")));
        assertThrows(ConflictoException.class,()->compras.incidencia(financiero,c.referencia(),confirmacion(c),"TEST versión antigua"));
    }
    @Test void dosAcreditacionesConcurrentesConservanUnSoloCicloYHecho()throws Exception{
        var c=consentida().compra();verificar(c,fondos("75.00"));var listos=new CountDownLatch(2);var salir=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Set<Integer> conexiones=ConcurrentHashMap.newKeySet();
        Callable<DetalleFinanciero> tarea=()->tx.execute(t->{conexiones.add(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));listos.countDown();esperar(salir);return compras.acreditar(financiero,c.referencia());});
        try{var a=pool.submit(tarea);var b=pool.submit(tarea);assertTrue(listos.await(15,TimeUnit.SECONDS));salir.countDown();assertEquals(Estado.ACREDITADA,a.get(40,TimeUnit.SECONDS).compra().estado());assertEquals(Estado.ACREDITADA,b.get(40,TimeUnit.SECONDS).compra().estado());}
        finally{salir.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
        assertEquals(1,numero("b0_acreditacion_pago",c.referencia(),"id_pedido"));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_ciclo WHERE organizacion_id=?",Integer.class,comprador.idOrganizacion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM b0_compra_manual_evento WHERE id_compra=? AND tipo='ACREDITADA'",Integer.class,c.referencia()));
        assertEquals(2,conexiones.size(),"La carrera necesita dos conexiones PostgreSQL distintas.");
    }
    @Test void sqlNoSobrescribeTerminosFondosNiHistoria(){
        var c=consentida().compra();verificar(c,fondos("75.00"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.executeWithoutResult(t->jdbc.update("UPDATE b0_compra_manual SET terminos='{}',version=version+1 WHERE id_compra=?",c.referencia())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.executeWithoutResult(t->jdbc.update("UPDATE b0_compra_manual SET fondos=NULL,version=version+1 WHERE id_compra=?",c.referencia())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.executeWithoutResult(t->jdbc.update("DELETE FROM b0_compra_manual_evento WHERE id_compra=?",c.referencia())));
    }
    @Test void sqlRechazaConsentimientoParcialEIncidenciaSinMotivo(){
        var c=preparada();long cuenta=jdbc.queryForObject("SELECT creada_por FROM b0_compra_manual WHERE id_compra=?",Long.class,c.referencia());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.executeWithoutResult(t->jdbc.update(
                "UPDATE b0_compra_manual SET consentida_por=?,huella_consentimiento=?,version=version+1 WHERE id_compra=?",cuenta,"a".repeat(64),c.referencia())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->tx.executeWithoutResult(t->jdbc.update(
                "UPDATE b0_compra_manual SET estado='CON_INCIDENCIA',version=version+1 WHERE id_compra=?",c.referencia())));
        assertFalse(compras.consultar(comprador,sid,c.referencia()).consentida());
    }
    @Test void fondosSueltosSinHechoFinancieroYConsentimientoTrazadosNoSonFuenteValida(){
        var c=consentida().compra();var f=new Fondos(fondos("75.00"),cuentaFin,grant,Instant.now());
        tx.executeWithoutResult(t->jdbc.update("UPDATE b0_compra_manual SET fondos=CAST(? AS jsonb),estado='EN_VERIFICACION',version=version+1 WHERE id_compra=?",json.objetoCanonico(f),c.referencia()));
        assertTrue(fuente.consultar(c.referencia()).isEmpty());assertThrows(CapacidadDeshabilitadaException.class,()->compras.acreditar(financiero,c.referencia()));
        assertEquals(0,numero("b0_acreditacion_pago",c.referencia(),"id_pedido"));
    }
    @Test void cambioDeCicloNoInvitaAPagarUnaMejoraYaInaplicable(){
        var inicial=consentida().compra();verificar(inicial,fondos("75.00"));compras.acreditar(financiero,inicial.referencia());
        var mejora=compras.preparar(comprador,sid,UUID.randomUUID(),new Seleccion(Tipo.MEJORA_PLAN,PlanBrox.GROWTH,null,0));
        var adicional=compras.preparar(comprador,sid,UUID.randomUUID(),new Seleccion(Tipo.BROKER_ADICIONAL,PlanBrox.TEAM,null,1));
        var aceptado=compras.consentir(comprador,sid,adicional.referencia(),confirmacion(adicional)).compra();
        var v=fondos(aceptado.terminos().totalUsd().toPlainString());verificar(aceptado,v);compras.acreditar(financiero,aceptado.referencia());
        assertThrows(ConflictoException.class,()->compras.consentir(comprador,sid,mejora.referencia(),confirmacion(mejora)));
        var conservada=compras.consultar(comprador,sid,mejora.referencia());assertFalse(conservada.consentida());assertEquals(mejora.terminos(),conservada.terminos());
    }
    @Test void grantSinCampoBancarioListaReducidoPeroNoLeeNiVerificaFondos(){
        var c=consentida().compra();var estrecha=conceder(financiero,Instant.now().plusSeconds(60),Set.of(Campo.ESTADO,Campo.PEDIDO_COMERCIAL));
        assertTrue(compras.listarFinanciero(estrecha,20,0).stream().anyMatch(x->x.referencia().equals(c.referencia())));
        assertThrows(AccesoNoAutorizadoException.class,()->compras.consultarFinanciero(estrecha,c.referencia()));
        assertThrows(AccesoNoAutorizadoException.class,()->compras.verificarFondos(estrecha,c.referencia(),confirmacion(c),fondos("75.00")));
    }
    @Test void servletIdentidadConGrantUsaPanelSinAbrirCarteraNiAceptarGrantAjeno()throws Exception{
        var c=consentida().compra();var s=contextos.exigirIdentidad(financiero.referenciaSesion(),1);String bearer="Bearer "+tokens.firmar(tokens.de(s));
        assertEquals(200,http.perform(get("/plataforma/atribuciones/compras").header("Authorization",bearer)).andReturn().getResponse().getStatus());
        var ok=http.perform(get("/plataforma/compras/"+c.referencia()).header("Authorization",bearer).header("X-Brox-Concesion",grant)).andReturn().getResponse();
        assertEquals(200,ok.getStatus());assertEquals("no-store",ok.getHeader("Cache-Control"));
        assertEquals(403,http.perform(get("/plataforma/compras/"+c.referencia()).header("Authorization",bearer).header("X-Brox-Concesion",UUID.randomUUID())).andReturn().getResponse().getStatus());
        assertEquals(409,http.perform(get("/clientes").header("Authorization",bearer)).andReturn().getResponse().getStatus());
        assertEquals(409,http.perform(post("/plataforma/compras/"+c.referencia()+"/saldo").header("Authorization",bearer)).andReturn().getResponse().getStatus());
    }
    @Test void concesionVencidaDuranteEsperaNoVerificaFondos()throws Exception{
        var c=consentida().compra();Instant hasta=Instant.now().plusSeconds(10);var corta=conceder(financiero,hasta,Set.of(Campo.ESTADO,Campo.PEDIDO_COMERCIAL,Campo.DATOS_PAGO_VERIFICADO));
        Throwable fallo=conEsperaDeOrganizacion(hasta,()->compras.verificarFondos(corta,c.referencia(),confirmacion(c),fondos("75.00")));
        assertInstanceOf(AccesoNoAutorizadoException.class,fallo);assertTrue(fuente.consultar(c.referencia()).isEmpty());
        assertEquals(0,numero("b0_pago_manual_identidad",c.referencia()));
    }
    @Test void sidVencidoDuranteEsperaNoConsienteCompra()throws Exception{
        var c=preparada();Instant hasta=Instant.now().plusSeconds(10);String x=UUID.randomUUID().toString();
        String ref=Base64.getUrlEncoder().withoutPadding().encodeToString((x+x).substring(0,32).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        Instant emitida=Instant.now();
        tx.executeWithoutResult(t->jdbc.update("""
            INSERT INTO sesion_acceso(referencia_hash,semilla_contextos,id_cuenta_acceso,revision,creada_en,emitida_en,vence_en,
             id_usuario_organizacion,id_persona_rol,rol_efectivo,contexto_ref_hash)
            SELECT ?,semilla_contextos,id_cuenta_acceso,revision,?,?,?,id_usuario_organizacion,id_persona_rol,rol_efectivo,contexto_ref_hash
             FROM sesion_acceso WHERE referencia_hash=?
            """,Idempotencia.huellaExacta(ref),utc(emitida),utc(emitida),utc(hasta),Idempotencia.huellaExacta(sid.referenciaSesion())));
        var corta=ContextoSesion.de(contextos.exigirContexto(ref,sid.revisionContexto(),comprador));
        Throwable fallo=conEsperaDeOrganizacion(hasta,()->compras.consentir(comprador,corta,c.referencia(),confirmacion(c)));
        assertInstanceOf(CredencialesInvalidasException.class,fallo);assertFalse(compras.consultar(comprador,sid,c.referencia()).consentida());
    }
    /** Tres conexiones reales: candado, víctima y observación pg_stat_activity; sin mock de locks. */
    Throwable conEsperaDeOrganizacion(Instant hasta,Runnable actuacion)throws Exception{
        var retenida=new CountDownLatch(1);var liberar=new CountDownLatch(1);var pid=new CompletableFuture<Integer>();var pidCandado=new CompletableFuture<Integer>();var pool=Executors.newFixedThreadPool(2);
        var candado=pool.submit(()->tx.execute(t->{pidCandado.complete(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));jdbc.queryForObject("SELECT id_organizacion FROM organizacion WHERE id_organizacion=? FOR NO KEY UPDATE",Long.class,comprador.idOrganizacion());retenida.countDown();esperar(liberar);return null;}));
        try{
            assertTrue(retenida.await(10,TimeUnit.SECONDS));var victima=pool.submit(()->{
                try{return tx.execute(t->{pid.complete(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));actuacion.run();return (Throwable)null;});}
                catch(RuntimeException e){return e;}
            });
            int conexion=pid.get(10,TimeUnit.SECONDS);int observador=jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class);
            assertEquals(3,Set.of(pidCandado.get(10,TimeUnit.SECONDS),conexion,observador).size(),"Candado/víctima/observador necesitan tres conexiones de TEST distintas.");
            boolean bloqueada=false;Instant limite=Instant.now().plusSeconds(5);
            while(Instant.now().isBefore(limite)&&!bloqueada){
                if(victima.isDone()){Throwable temprano=victima.get();throw new AssertionError("La víctima terminó antes del candado de organización.",temprano);}
                bloqueada=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock' AND query LIKE '%organizacion%')",Boolean.class,conexion));if(!bloqueada)Thread.sleep(10);
            }
            var observacion=jdbc.queryForList("SELECT pid,wait_event_type,wait_event,pg_blocking_pids(pid) AS bloqueadores,query LIKE '%organizacion%' AS consulta_organizacion FROM pg_stat_activity WHERE datname=current_database() AND state<>'idle'");
            assertTrue(bloqueada,"La víctimaPID"+conexion+" debe esperar la organización. Sólo metadataTEST: "+observacion);
            assertTrue(Instant.now().isBefore(hasta));while(Instant.now().isBefore(hasta.plusMillis(30)))Thread.sleep(10);
            liberar.countDown();Throwable resultado=victima.get(20,TimeUnit.SECONDS);candado.get(20,TimeUnit.SECONDS);return resultado;
        }finally{liberar.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
    static void esperar(CountDownLatch latch){try{if(!latch.await(30,TimeUnit.SECONDS))throw new AssertionError("Barrera de prueba agotada.");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    int numero(String tabla,UUID id){return numero(tabla,id,"id_compra");}
    int numero(String tabla,UUID id,String columna){return jdbc.queryForObject("SELECT count(*) FROM "+tabla+" WHERE "+columna+"=?",Integer.class,id);}
    static OffsetDateTime utc(Instant i){return i.atOffset(ZoneOffset.UTC);}
}
