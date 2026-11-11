package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.app.arranque.ConservacionMaterialIa;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.*;
import com.controllocal.service.materialia.AvisosTratamientoMaterialIa.*;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.AmbitoTratamiento;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto;
import com.controllocal.service.materialia.FuentesMaterialIa.DestinoTratamiento;
import com.controllocal.service.materialia.MaterialIaService.Accion;
import com.controllocal.service.materialia.ResultadosMaterialIa.Version;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.web.almacen.*;
import com.controllocal.web.seguridad.SesionDeRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PG/T4 de la proyección146 con C1/E44/B0/144 reales. Sólo almacén, procesador,
 * QC y base jurídica son TEST. La primera MEDIDA histórica es una simulación
 * declarada: no acredita una cadena cronológica realmente envejecida siete días,
 * calidad de IA, legitimación productiva, envío externo o gasto autorizado.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes={ControlLocalApplication.class,AvisosTemporalesMaterialIaC6IntegrationTest.Configuracion.class},
        webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties={"spring.datasource.hikari.maximum-pool-size=6",
                "controllocal.almacen.proveedor=TEST",
                "controllocal.material-ia.conservacion.habilitada=true",
                "controllocal.material-ia.conservacion.intervalo-ms=3600000"})
class AvisosTemporalesMaterialIaC6IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired TareaService tareas;
    @Autowired ResultadosMaterialIa resultados;
    @Autowired GaleriaDeFotos galeria;
    @Autowired FuenteLegalTest legal;
    @Autowired AlmacenTest almacen;
    OrigenesDePrueba origenes;
    OrigenesDePrueba.Equipo equipo;
    DerivadasMaterialIaDePrueba banco;
    ContextoSesion sid;
    Actor actor;
    long propiedad;
    UUID original;

    @BeforeEach void preparar(TestInfo info)throws Exception{
        EvidenciaC4DePrueba.iniciar(info);
        var contextos=contexto.getBean(ContextosService.class);
        origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(MotorDeCaptura.class),contextos);
        equipo=origenes.empresa();actor=equipo.agente();
        SesionDeRequest principal=SesionesDePrueba.abrir(jdbc,contextos,actor);sid=ContextoSesion.de(principal.acceso());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of()));
        propiedad=origenes.propiedad(actor,"Aviso agrupado C6");
        banco=new DerivadasMaterialIaDePrueba(contexto,jdbc,gestor);banco.prepararAlmacenamiento(actor);
        legal.destino=banco.destino();legal.permitir(actor,propiedad);
        var imagen=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new BufferedImage(8,9,BufferedImage.TYPE_INT_RGB),"png",imagen);
        var foto=galeria.agregar(propiedad,"original-test.png",Base64.getEncoder().encodeToString(imagen.toByteArray()),12,actor);
        original=jdbc.queryForObject("SELECT archivo_ref FROM foto_propiedad WHERE organizacion_id=? AND id_foto=?",UUID.class,actor.idOrganizacion(),foto.id());
    }
    @AfterEach void terminar(){SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}
    private DerivadasMaterialIaDePrueba.Caso generar(){
        return banco.generarConObservacionHistoricaTEST(propiedad,original,actor,sid,Instant.now().minus(Duration.ofDays(6)).minus(Duration.ofHours(23)));
    }
    private Version version(DerivadasMaterialIaDePrueba.Caso c){return resultados.versiones(original,actor,sid).stream()
            .filter(v->v.operacion().equals(c.solicitud().operacion())).findFirst().orElseThrow();}
    private int cantidad(String tabla){return jdbc.queryForObject("SELECT count(*) FROM "+tabla+" WHERE organizacion_id=?",Integer.class,actor.idOrganizacion());}
    private List<Map<String,Object>> historiaAviso(){return jdbc.queryForList("SELECT id_observacion,id_tarea,huella,revision,tipo FROM observacion_aviso_material_ia_c6 WHERE organizacion_id=? ORDER BY id_observacion",actor.idOrganizacion());}
    private String estadoTx(long id){return jdbc.queryForObject("SELECT txid_status(CAST(? AS bigint))",String.class,id);}
    private <T>T tx(Supplier<T> f){return new TransactionTemplate(gestor).execute(s->f.get());}
    private String sqlState(Runnable r){
        var e=assertThrows(DataAccessException.class,()->tx(()->{r.run();return null;}));
        for(Throwable c=e;c!=null;c=c.getCause())if(c instanceof SQLException sql)return sql.getSQLState();
        throw new AssertionError("El fallo no conserva la causa SQL esperada.",e);
    }

    @Test void dosTemporalesSonUnaTarjetaPersonalSinInventarImportanciaNiReiniciarFechas(){
        generar();generar();var grupo=resultados.avisosDeCaducidad(actor).getFirst();
        var primera=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();var segunda=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();
        assertEquals(primera.id(),segunda.id());assertEquals(1,cantidad("tarea"));assertNull(primera.prioridad());
        assertEquals(grupo.avisoEn(),primera.fechaProgramada().toInstant());assertEquals(primera.fechaProgramada(),segunda.fechaProgramada());
        assertEquals(grupo.vencePrimeroEn().atZone(ZoneId.of("America/Lima")).toLocalDate(),primera.fechaVencimiento());
        assertTrue(primera.descripcion().contains("2 resultados temporales"));assertTrue(primera.interpretacion().expediente().isEmpty());
        assertEquals(1,cantidad("observacion_aviso_material_ia_c6"));
    }
    @Test void perdidaDeLegitimacionDeUsoOcultaSinCompletarYRecuperacionConservaOcurrencia(){
        generar();var ficha=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();var historia=historiaAviso();legal.uso.put(legal.llave(actor,propiedad),false);
        long[] padre={0};
        try{
            new TransactionTemplate(gestor).executeWithoutResult(status->{
                padre[0]=jdbc.queryForObject("SELECT txid_current()",Long.class);assertFalse(status.isRollbackOnly());
                assertTrue(tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).isEmpty());assertFalse(status.isRollbackOnly());
                assertEquals(padre[0],jdbc.queryForObject("SELECT txid_current()",Long.class).longValue());
            });
        }finally{legal.uso.put(legal.llave(actor,propiedad),true);}
        assertEquals("committed",estadoTx(padre[0]));assertEquals(historia,historiaAviso());
        assertEquals("P",jdbc.queryForObject("SELECT estado FROM tarea WHERE organizacion_id=? AND id_tarea=?",String.class,actor.idOrganizacion(),ficha.id()));
        assertNull(jdbc.queryForObject("SELECT fecha_completada FROM tarea WHERE organizacion_id=? AND id_tarea=?",Object.class,actor.idOrganizacion(),ficha.id()));
        var vuelta=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();assertEquals(ficha.id(),vuelta.id());
        assertEquals(ficha.fechaProgramada(),vuelta.fechaProgramada());assertEquals(1,cantidad("tarea"));
    }
    @Test void falloTecnicoRealDeFuenteTestPropagaYAbortaElPadreSinAlterarTarjetaNiHistoria(){
        generar();var ficha=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();var historia=historiaAviso();long[] padre={0};
        String llave=legal.llave(actor,propiedad);legal.averiasUso.add(llave);
        try{
            var fallo=assertThrows(IllegalStateException.class,()->new TransactionTemplate(gestor).executeWithoutResult(status->{
                padre[0]=jdbc.queryForObject("SELECT txid_current()",Long.class);assertFalse(status.isRollbackOnly());
                try{tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor);fail("Una avería técnica no puede convertirse en bandeja vacía.");}
                catch(IllegalStateException e){assertTrue(status.isRollbackOnly());throw e;}
            }));
            assertEquals("AVERIA_LEGITIMACION_TEST146",fallo.getMessage());
        }finally{legal.averiasUso.remove(llave);}
        assertEquals("aborted",estadoTx(padre[0]));assertEquals(historia,historiaAviso());
        var vuelta=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();assertEquals(ficha.id(),vuelta.id());assertEquals(ficha.fechaProgramada(),vuelta.fechaProgramada());
        assertEquals("P",vuelta.estado());assertEquals(1,cantidad("tarea"));assertEquals(historia,historiaAviso());
    }
    @Test void retirarOriginalDeGaleriaDeniegaLecturaActualSinMarcarRollbackNiBorrarOriginalOHistoria(){
        var c=generar();var ficha=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();var historia=historiaAviso();
        var foto=jdbc.queryForMap("SELECT id_foto,clave FROM foto_propiedad WHERE organizacion_id=? AND archivo_ref=?",actor.idOrganizacion(),original);
        galeria.eliminar(((Number)foto.get("id_foto")).longValue(),actor); // Retirada lógica Core; no elimina el binario original.
        long[] padre={0};
        new TransactionTemplate(gestor).executeWithoutResult(status->{
            padre[0]=jdbc.queryForObject("SELECT txid_current()",Long.class);assertFalse(status.isRollbackOnly());
            assertEquals(EstadoUsoArtefacto.PERMISO_INSUFICIENTE,contexto.getBean(FuentesMaterialIaCore.class)
                    .evaluarLecturaArtefacto(c.solicitud(),c.snapshot(),actor));
            assertFalse(status.isRollbackOnly());assertTrue(tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).isEmpty());assertFalse(status.isRollbackOnly());
            assertEquals(padre[0],jdbc.queryForObject("SELECT txid_current()",Long.class).longValue());
        });
        assertEquals("committed",estadoTx(padre[0]));assertEquals(historia,historiaAviso());
        var tarea=jdbc.queryForMap("SELECT estado,estado_causa_material_ia_c6,fecha_completada FROM tarea WHERE organizacion_id=? AND id_tarea=?",actor.idOrganizacion(),ficha.id());
        assertEquals("P",tarea.get("estado"));assertEquals("VIGENTE",tarea.get("estado_causa_material_ia_c6"));assertNull(tarea.get("fecha_completada"));
        assertEquals(1,cantidad("tarea"));assertTrue(almacen.abrir((String)foto.get("clave")).isPresent());
    }
    @Test void guardarLaVersionFinalizaLaCausaSinRegistrarUnaActuacionHumanaRealizada(){
        var c=generar();var ficha=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();var v=version(c);
        resultados.guardarAlternativa(v.operacion(),v.archivo(),v.revision(),"guardar-"+UUID.randomUUID(),actor,sid);
        assertTrue(tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).isEmpty());
        var fila=jdbc.queryForMap("SELECT estado,estado_causa_material_ia_c6,fecha_completada FROM tarea WHERE organizacion_id=? AND id_tarea=?",actor.idOrganizacion(),ficha.id());
        assertEquals("P",fila.get("estado"));assertEquals("FINALIZADA",fila.get("estado_causa_material_ia_c6"));assertNull(fila.get("fecha_completada"));
        assertEquals(List.of("CREACION","CAUSA_FINALIZADA"),jdbc.queryForList("SELECT tipo FROM observacion_aviso_material_ia_c6 WHERE organizacion_id=? ORDER BY id_observacion",String.class,actor.idOrganizacion()));
    }
    @Test void cancelacionNoReabreUnRecortePeroOtroEpisodioCreaOcurrenciaVinculada(){
        generar();var b=generar();var anterior=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();tareas.cancelar(anterior.id(),actor);
        var vb=version(b);resultados.descartarTemporal(vb.operacion(),vb.archivo(),vb.revision(),"descartar-"+UUID.randomUUID(),actor,sid);
        assertTrue(tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).isEmpty());assertEquals(1,cantidad("tarea"));
        generar();var nueva=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();assertNotEquals(anterior.id(),nueva.id());assertEquals(2,cantidad("tarea"));
        assertEquals(anterior.id(),jdbc.queryForObject("SELECT id_antecedente FROM tarea WHERE organizacion_id=? AND id_tarea=?",Long.class,actor.idOrganizacion(),nueva.id()));
        assertEquals("A",jdbc.queryForObject("SELECT estado FROM tarea WHERE organizacion_id=? AND id_tarea=?",String.class,actor.idOrganizacion(),anterior.id()));
        assertEquals("SUSTITUIDA",jdbc.queryForObject("SELECT estado_causa_material_ia_c6 FROM tarea WHERE organizacion_id=? AND id_tarea=?",String.class,actor.idOrganizacion(),anterior.id()));
    }
    @Test void tarjetaNuevaPuedeVolverAlContenidoDeUnaCancelacionHistoricaSinColisionNiPerdidaDeAntecedente(){
        generar();var antigua=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();tareas.cancelar(antigua.id(),actor);
        String huellaAnterior=jdbc.queryForObject("SELECT marca_causa FROM tarea WHERE organizacion_id=? AND id_tarea=?",String.class,actor.idOrganizacion(),antigua.id());
        var b=generar();var nueva=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();assertNotEquals(antigua.id(),nueva.id());
        var vb=version(b);resultados.descartarTemporal(vb.operacion(),vb.archivo(),vb.revision(),"recortar-"+UUID.randomUUID(),actor,sid);
        var recortada=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();assertEquals(nueva.id(),recortada.id());assertEquals(2,cantidad("tarea"));
        assertEquals(huellaAnterior,jdbc.queryForObject("SELECT marca_causa FROM tarea WHERE organizacion_id=? AND id_tarea=?",String.class,actor.idOrganizacion(),recortada.id()));
        var antecedente=jdbc.queryForMap("SELECT estado,estado_causa_material_ia_c6,marca_causa FROM tarea WHERE organizacion_id=? AND id_tarea=?",actor.idOrganizacion(),antigua.id());
        assertEquals("A",antecedente.get("estado"));assertEquals("SUSTITUIDA",antecedente.get("estado_causa_material_ia_c6"));assertEquals(huellaAnterior,antecedente.get("marca_causa"));
        assertEquals(antigua.id(),jdbc.queryForObject("SELECT id_antecedente FROM tarea WHERE organizacion_id=? AND id_tarea=?",Long.class,actor.idOrganizacion(),recortada.id()));
        assertEquals(recortada.id(),tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst().id());
    }
    @Test void brokerAdminYOrganizacionAjenaNoHeredanTarjetaNiGestion(){
        generar();var propia=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();
        assertThrows(AccesoNoAutorizadoException.class,()->tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),equipo.broker()));
        assertThrows(AccesoNoAutorizadoException.class,()->tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),equipo.admin()));
        var otro=origenes.empresa().agente();var sidOtro=origenes.sesion(otro);
        assertTrue(tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),otro).isEmpty());assertThrows(ReglaNegocioException.class,()->tareas.cancelar(propia.id(),otro));
        assertThrows(NoEncontradoException.class,()->resultados.versiones(original,otro,sidOtro));assertEquals(1,cantidad("tarea"));
        long foto=jdbc.queryForObject("SELECT id_foto FROM foto_propiedad WHERE organizacion_id=? AND archivo_ref=?",Long.class,actor.idOrganizacion(),original);
        var grupo=resultados.avisosDeCaducidad(actor).getFirst();
        assertEquals("23503",sqlState(()->jdbc.update("""
                INSERT INTO tarea(organizacion_id,tipo,entidad_tipo,entidad_id,id_rol_agente,descripcion,estado,prioridad,marca_causa,
                  original_material_ia_c6,revision_material_ia_c6,estado_causa_material_ia_c6)
                VALUES(?,'SEGUIMIENTO','FOTO_PROPIEDAD',?,?,'CRUCE TEST','P',NULL,?,?,?,'VIGENTE')
                """,otro.idOrganizacion(),foto,otro.idRolOperativo(),grupo.huellaCausa(),original,grupo.revision())));
    }
    @Test void restriccionesConservanImportanciaLegacyOrigenEHistoriaSinCumplimientosFicticios(){
        generar();var ficha=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst();
        assertEquals("23514",sqlState(()->jdbc.update("""
                INSERT INTO tarea(organizacion_id,tipo,entidad_tipo,entidad_id,id_rol_agente,descripcion,estado,prioridad)
                VALUES(?,'SEGUIMIENTO','INMUEBLE',?,?,'PRIORIDAD LEGACY TEST','P',NULL)
                """,actor.idOrganizacion(),propiedad,actor.idRolOperativo())));
        assertEquals("23514",sqlState(()->jdbc.update("UPDATE tarea SET prioridad='MEDIA' WHERE organizacion_id=? AND id_tarea=?",actor.idOrganizacion(),ficha.id())));
        assertEquals("23514",sqlState(()->jdbc.update("UPDATE tarea SET estado='C',fecha_completada=clock_timestamp() WHERE organizacion_id=? AND id_tarea=?",actor.idOrganizacion(),ficha.id())));
        assertEquals("23514",sqlState(()->jdbc.update("UPDATE tarea SET id_rol_agente=? WHERE organizacion_id=? AND id_tarea=?",equipo.colega().idRolOperativo(),actor.idOrganizacion(),ficha.id())));
        assertEquals("23514",sqlState(()->jdbc.update("UPDATE observacion_aviso_material_ia_c6 SET tipo='ACTUALIZACION' WHERE organizacion_id=?",actor.idOrganizacion())));
        assertEquals("23514",sqlState(()->jdbc.update("DELETE FROM observacion_aviso_material_ia_c6 WHERE organizacion_id=?",actor.idOrganizacion())));
        assertNull(tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst().prioridad());assertEquals(1,cantidad("observacion_aviso_material_ia_c6"));
    }
    @Test void dosTransaccionesReconciliandoNoDuplicanTarjetaNiObservacion()throws Exception{
        generar();var listos=new CountDownLatch(2);var abrir=new CountDownLatch(1);
        try(var hilos=Executors.newFixedThreadPool(2)){
            Callable<Long> consulta=()->{listos.countDown();if(!abrir.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Barrera TEST agotada.");return tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),actor).getFirst().id();};
            var a=hilos.submit(consulta);var b=hilos.submit(consulta);assertTrue(listos.await(20,TimeUnit.SECONDS));abrir.countDown();
            assertEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,cantidad("tarea"));assertEquals(1,cantidad("observacion_aviso_material_ia_c6"));
    }
    @Test void workerRegistradoEnRuntimeRetiraUnaVencidaSinNuevoProcesamientoYLiberaSoloBytesVerificados(){
        var worker=contexto.getBean(ConservacionMaterialIa.class);
        assertNotNull(worker);
        // Task.getRunnable() está envuelto por Spring. El nombre público conserva el método real.
        var programadas=contexto.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().stream()
                .filter(t->t.getTask().toString().equals(ConservacionMaterialIa.class.getName()+".tick")).toList();
        assertEquals(1,programadas.size());var programada=programadas.getFirst();
        var c=banco.generarConObservacionHistoricaTEST(propiedad,original,actor,sid,Instant.now().minus(Duration.ofDays(8)));
        assertNotEquals("DISPONIBLE",c.resultado().estado());assertNotNull(c.archivo());
        long antes=jdbc.queryForObject("SELECT confirmados FROM b0_uso_almacenamiento WHERE organizacion_id=?",Long.class,actor.idOrganizacion());
        int eventosAntes=jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='MEDIDA'",Integer.class,actor.idOrganizacion(),c.solicitud().operacion());
        assertTrue(resultados.candidatosConservacion(almacen.identidadTecnica(),null,128).candidatos().stream()
                .anyMatch(candidata->candidata.organizacion()==actor.idOrganizacion()&&candidata.operacion().equals(c.solicitud().operacion())));
        // Ejecuta exactamente el runnable registrado, no una llamada alternativa al servicio.
        programada.getTask().getRunnable().run();
        assertEquals("RETIRADA_VERIFICADA",resultados.version(c.solicitud().operacion(),actor).estadoGestion());
        long despues=jdbc.queryForObject("SELECT confirmados FROM b0_uso_almacenamiento WHERE organizacion_id=?",Long.class,actor.idOrganizacion());
        assertEquals(antes-c.salida().length,despues);
        assertEquals(eventosAntes,jdbc.queryForObject("SELECT count(*) FROM material_ia_evento WHERE organizacion_id=? AND id_operacion=? AND estado='MEDIDA'",Integer.class,actor.idOrganizacion(),c.solicitud().operacion()).intValue());
        assertThrows(NoEncontradoException.class,()->resultados.descargar(c.solicitud().operacion(),c.archivo(),actor,sid));
        programada.getTask().getRunnable().run();assertEquals(despues,jdbc.queryForObject("SELECT confirmados FROM b0_uso_almacenamiento WHERE organizacion_id=?",Long.class,actor.idOrganizacion()).longValue());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM foto_propiedad WHERE organizacion_id=? AND archivo_ref=?",Integer.class,actor.idOrganizacion(),original).intValue());
        String claveOriginal=jdbc.queryForObject("SELECT clave FROM foto_propiedad WHERE organizacion_id=? AND archivo_ref=?",String.class,actor.idOrganizacion(),original);
        assertTrue(almacen.abrir(claveOriginal).isPresent());
    }

    @TestConfiguration(proxyBeanMethods=false)
    static class Configuracion {
        @Bean @Primary AlmacenTest almacenAvisosC6Test(){return new AlmacenTest();}
        @Bean FuenteLegalTest fuenteLegalAvisosC6Test(){return new FuenteLegalTest();}
    }
    /** Puerto binario TEST, sin red. Todas las escrituras comerciales pasan primero por Core/B0/journal. */
    static final class AlmacenTest implements AlmacenDocumentos {
        private final String namespace=AlmacenDocumentos.huellaIdentidad("TEST","avisos146-"+UUID.randomUUID());
        final ConcurrentMap<String,byte[]> objetos=new ConcurrentHashMap<>();
        public String proveedor(){return "TEST";}
        public String identidadTecnica(){return namespace;}
        public void guardarEnClave(String clave,byte[] contenido,String tipo){objetos.put(clave,contenido.clone());}
        public Optional<ArchivoDescargado> abrir(String clave){byte[] b=objetos.get(clave);return b==null?Optional.empty():Optional.of(new ArchivoDescargado(b.clone(),AlmacenDocumentos.nombreDe(clave),AlmacenDocumentos.contentTypeDe(clave)));}
        public void eliminar(String clave){objetos.remove(clave);}
        public Set<String> listarClaves(){return Set.copyOf(objetos.keySet());}
    }
    /** Acreditaciones SINTÉTICAS allowlist por actor/recurso. No son una política ni consentimiento productivos. */
    static final class FuenteLegalTest implements AvisosTratamientoMaterialIa {
        volatile DestinoTratamiento destino;
        final Set<String> permitidos=ConcurrentHashMap.newKeySet();final ConcurrentMap<String,Boolean> uso=new ConcurrentHashMap<>();
        final Set<String> averiasUso=ConcurrentHashMap.newKeySet();
        String llave(Actor actor,long propiedad){return actor.idOrganizacion()+":"+actor.idPersona()+":"+actor.idRolOperativo()+":"+propiedad;}
        void permitir(Actor actor,long propiedad){permitidos.add(llave(actor,propiedad));uso.put(llave(actor,propiedad),true);}
        private String llave(Actor actor,AmbitoTratamiento a){return llave(actor,Long.parseLong(a.solicitud().recurso().referencia()));}
        private boolean permitida(AmbitoTratamiento a,Actor actor){return actor.esAgente()&&"PROPIEDAD".equals(a.solicitud().recurso().tipo())&&permitidos.contains(llave(actor,a));}
        public Optional<Contenido> vigente(AmbitoTratamiento a,Actor actor){
            if(destino==null||!permitida(a,actor))return Optional.empty();
            String texto="CONDICIONES SINTÉTICAS TEST146, sin proveedor, base jurídica ni transferencia productivos.";
            return Optional.of(new Contenido("AVISO_TEST146_"+destino.versionPerfil(),1,"Aviso TEST146 informado sólo para este banco.",
                    Set.of(Accion.MEJORAR_FOTO),Set.of("INSTRUCCION","FOTO_COMERCIAL"),"PROCESADOR_LEGAL_TEST146","TEST146-1",texto,
                    "FUENTE_LEGAL_SINTETICA_TEST146",Idempotencia.huellaExacta(texto),Set.of(destino)));
        }
        public Optional<Legitimacion> legitimacionActual(AmbitoTratamiento a,Contenido aviso,Actor actor){return permitida(a,actor)?Optional.of(base(a,aviso,"TRANSFERENCIA_TEST")):Optional.empty();}
        public Optional<Legitimacion> legitimacionUsoArtefacto(AmbitoTratamiento a,Contenido historico,Actor actor){
            if(averiasUso.contains(llave(actor,a)))throw new IllegalStateException("AVERIA_LEGITIMACION_TEST146");
            return permitida(a,actor)&&Boolean.TRUE.equals(uso.get(llave(actor,a)))?Optional.of(base(a,historico,"USO_ACTUAL_TEST")):Optional.empty();
        }
        private Legitimacion base(AmbitoTratamiento a,Contenido aviso,String tipo){return new Legitimacion(tipo,"ACREDITACION_SINTETICA_SIN_VALIDEZ_PRODUCTIVA","c".repeat(64),
                a.solicitud().recurso(),Set.of(Accion.MEJORAR_FOTO),Set.of("INSTRUCCION","FOTO_COMERCIAL"),aviso.proveedorLegal(),aviso.condicionesVersion());}
    }
}
