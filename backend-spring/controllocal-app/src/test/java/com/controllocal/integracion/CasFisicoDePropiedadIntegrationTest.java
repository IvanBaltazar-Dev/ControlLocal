package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropiedadUniversalService.*;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/** RF013/AC053/055: versión observada real, recibo físico y T4 PostgreSQL. No certifica login. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties="spring.datasource.hikari.maximum-pool-size=8")
class CasFisicoDePropiedadIntegrationTest {
    private static final Procedencia ORIGEN=Procedencia.deLaPantalla();
    @DynamicPropertySource static void datos(DynamicPropertyRegistry registro){BaseDeDatosDePruebas.registrar(registro);}
    @Autowired PropiedadUniversalService propiedades;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired EntityManager entidades;
    @Autowired ObjectMapper json;
    @MockitoSpyBean EventoDominioRepository eventos;
    @MockitoSpyBean SuficienciaInmueble suficiencia;
    private Actor actor;

    @BeforeEach void identidadTecnicaPropia(){
        // Cuenta/pertenencia ficticias explícitas: no passwords de terceros ni sesión de producto fabricada.
        actor=new TransactionTemplate(gestor).execute(tx->{
            String ref=UUID.randomUUID().toString().replace("-","");
            long org=jdbc.queryForObject("insert into organizacion(codigo,nombre,modalidad,estado) values(?,?,'INDIVIDUAL','A') returning id_organizacion",
                    Long.class,"CAS-"+ref.substring(0,16),"TEST CAS físico");
            long cuenta=jdbc.queryForObject("""
                    insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash)
                    values(?,?,clock_timestamp(),'fixture-cas-no-autenticable') returning id_cuenta_acceso
                    """,Long.class,"cas-"+ref,"cas-"+ref+"@example.invalid");
            long persona=jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,'Profesional TEST CAS físico') returning id_persona",Long.class,org);
            long rol=jdbc.queryForObject("""
                    insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde)
                    values(?,?,'AGENTE',(clock_timestamp() at time zone 'America/Lima')::date) returning id_persona_rol
                    """,Long.class,org,persona);
            jdbc.update("insert into detalle_agente(organizacion_id,id_persona_rol,codigo_agente,fecha_ingreso,estado_operativo) values(?,?,?,current_date,'D')",org,rol,"CAS-"+rol);
            jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,'AGENTE',false,'A')",org,cuenta,persona);
            return new Actor(org,persona,rol,Actor.AGENTE);
        });
    }
    @AfterEach void retirarSabotajes(){Mockito.reset(eventos,suficiencia);}

    @Test void lecturaExponeVersionYEditarDevuelveSoloElReciboFisico(){
        long id=registrar();long observada=propiedades.consultar(id,actor).versionFisica();
        assertEquals(0,observada);
        var hecho=propiedades.editar(id,descripcion("Descripción física corregida").conVersion(observada),actor);
        assertEquals(observada+1,hecho.versionFisica());
        assertEquals("Descripción física corregida",hecho.descripcion());
        assertTrue(hecho.clavesAfectadas().contains("descripcion"));
        assertEquals("DISPONIBLE",hecho.estadoSuficiencia());
        assertEquals(SuficienciaInmueble.Accion.IDENTIFICAR,hecho.suficiencia().accion());
        assertEquals(hecho.versionFisica(),propiedades.consultar(id,actor).versionFisica());
        assertFalse(json.valueToTree(hecho).has("encargos"));
        assertFalse(json.valueToTree(hecho).has("historia"));
    }

    @Test void noSeInventaLaVersionObservadaAusenteNiSeAceptaLaNegativa(){
        long id=registrar();int antes=eventosDe(id);
        assertThrows(ReglaNegocioException.class,()->propiedades.editar(id,descripcion("Sin observación"),actor));
        assertThrows(ReglaNegocioException.class,()->propiedades.editar(id,descripcion("Negativa").conVersion(-1),actor));
        assertEquals(0,versionDe(id));assertEquals("Descripción inicial TEST",descripcionDe(id));assertEquals(antes,eventosDe(id));
    }

    @Test void laEdicionFisicaRechazaElBloqueComercialSinEfectos(){
        long id=registrar();long observada=propiedades.consultar(id,actor).versionFisica();
        // ID deliberadamente inexistente: el transporte antiguo debe rechazarse, no resolver otro Encargo.
        var bloque=new CondicionesDeEncargo(Long.MAX_VALUE,List.of(new ValorAtributo("garantia","2")),null,0L);
        var comando=new ComandoEdicion(clave(),ORIGEN,"No debe quedar esta mezcla",null,null,null,null,null,
                List.of(bloque),null).conVersion(observada);
        var rechazo=assertThrows(ReglaNegocioException.class,()->propiedades.editar(id,comando,actor));
        assertTrue(rechazo.getMessage().contains("/encargos/{id}/condiciones"));
        assertEquals(observada,versionDe(id));assertEquals("Descripción inicial TEST",descripcionDe(id));
        assertEquals(0,eventosDe(id));
    }

    @Test void edicionObsoletaNoPisaElDatoYUnControlConNuevaObservacionSi(){
        long id=registrar();long original=propiedades.consultar(id,actor).versionFisica();
        propiedades.editar(id,descripcion("Primer cambio").conVersion(original),actor);
        int antes=eventosDe(id);
        assertThrows(ConflictoException.class,()->propiedades.editar(id,descripcion("Dato obsoleto").conVersion(original),actor));
        assertEquals("Primer cambio",descripcionDe(id));assertEquals(antes,eventosDe(id));
        long nuevaObservacion=propiedades.consultar(id,actor).versionFisica();
        var segundo=propiedades.editar(id,descripcion("Segundo cambio").conVersion(nuevaObservacion),actor);
        assertEquals(nuevaObservacion+1,segundo.versionFisica());assertEquals("Segundo cambio",descripcionDe(id));
    }

    @Test void retirarVigenteExigeMotivoYConservaElValorAnterior() throws Exception {
        long id=registrar();long observada=propiedades.consultar(id,actor).versionFisica();
        var retiro=new ComandoEdicion(clave(),ORIGEN,null,null,null,null,null,List.of("descripcion"),null,null).conVersion(observada);
        assertThrows(ReglaNegocioException.class,()->propiedades.editar(id,retiro,actor));
        assertEquals(observada,versionDe(id));assertEquals("Descripción inicial TEST",descripcionDe(id));
        var hecho=propiedades.editar(id,retiro.conMotivoRetiro("La descripción se transcribió de otro inmueble TEST"),actor);
        assertNull(hecho.descripcion());assertEquals(observada+1,hecho.versionFisica());
        var rastro=json.readTree(jdbc.queryForObject("select carga_util from evento_dominio where entidad_tipo='PROPIEDAD' and entidad_id=? and tipo='PROPIEDAD_EDITADA' order by id_evento desc limit 1",String.class,id));
        assertEquals("Descripción inicial TEST",rastro.path("fisicoAnterior").path("descripcion").asText());
        assertFalse(rastro.path("motivoRetiro").asText().isBlank());
    }

    @Test void cambiarUnValorConocidoAFaltanteEsRetiroYNoPierdeSuProcedencia(){
        long id=registrar();long observada=propiedades.consultar(id,actor).versionFisica();
        var valor=new ValorAtributo("metraje_total",null,null,null,null,null,null,"RECTIFICACION_TEST",
                new MetadatosDeCaptura(EstadoDato.FALTANTE,null,null,null,false));
        var retiro=new ComandoEdicion(clave(),ORIGEN,null,null,null,List.of(valor),null,null,null,null).conVersion(observada);
        assertThrows(ReglaNegocioException.class,()->propiedades.editar(id,retiro,actor));
        var hecho=propiedades.editar(id,retiro.conMotivoRetiro("La medición declarada correspondía a una unidad distinta TEST"),actor);
        assertNull(hecho.valoresFisicos().get("metraje_total").valor());
        assertEquals(EstadoDato.FALTANTE,hecho.valoresFisicos().get("metraje_total").estado());
        assertEquals("RECTIFICACION_TEST",hecho.metadatos().get("metraje_total").evidenciaRef());
    }

    @Test void declararNoAplicabilidadNuevaNoInventaUnRetiroPrevioNiExigeMotivo(){
        long id=registrar();long observada=propiedades.consultar(id,actor).versionFisica();
        var valor=new ValorAtributo("contexto.potencialOCambioUso",null,null,null,null,null,null,"DECLARACION_TEST_SIN_CAMBIO_USO",
                new MetadatosDeCaptura(EstadoDato.NO_APLICABLE,null,null,null,false));
        var hecho=propiedades.editar(id,new ComandoEdicion(clave(),ORIGEN,null,null,null,List.of(valor),null,null,null,null)
                .conVersion(observada),actor);
        assertEquals(observada+1,hecho.versionFisica());
        assertEquals(SuficienciaInmueble.Aplicabilidad.NO_APLICA,hecho.contextoCaptura().aplicabilidad().get("potencialOCambioUso"));
        assertEquals("DECLARACION_TEST_SIN_CAMBIO_USO",hecho.metadatos().get("contexto.potencialOCambioUso").evidenciaRef());
    }

    @Test void atributoEnTablaHijaTambienAvanzaLaVersionFisica(){
        long id=registrar();long observada=propiedades.consultar(id,actor).versionFisica();
        var hecho=propiedades.editar(id,new ComandoEdicion(clave(),ORIGEN,null,null,null,
                List.of(new ValorAtributo("tipo_acceso","A_PIE_DE_CALLE")),null,null,null,null).conVersion(observada),actor);
        assertEquals(observada+1,hecho.versionFisica());
        assertEquals("A_PIE_DE_CALLE",hecho.valoresFisicos().get("tipo_acceso").valor());
        assertEquals(1,jdbc.queryForObject("select count(*) from atributo_propiedad where id_propiedad=? and clave='tipo_acceso'",Integer.class,id));
    }

    @Test void replayRestauraLaVersionOriginalAunqueHayaOtraEdicionPosterior(){
        long id=registrar();long v=propiedades.consultar(id,actor).versionFisica();
        var primero=descripcion("Primer resultado").conVersion(v);
        var recibo=propiedades.editar(id,primero,actor);
        propiedades.editar(id,descripcion("Estado posterior").conVersion(recibo.versionFisica()),actor);
        int antes=eventosDe(id);long actual=versionDe(id);
        var replay=propiedades.editar(id,primero,actor);
        assertEquals(recibo.comoReintento(),replay);
        assertEquals("Primer resultado",replay.descripcion());assertEquals("Estado posterior",descripcionDe(id));
        assertEquals(actual,versionDe(id));assertEquals(antes,eventosDe(id));
        var distinto=new ComandoEdicion(primero.claveIdempotencia(),ORIGEN,"PRIMER RESULTADO",null,null,null,null,null,null,null).conVersion(v);
        assertThrows(ConflictoException.class,()->propiedades.editar(id,distinto,actor),"Las mayúsculas no convierten otro contenido en el mismo comando.");
    }

    @Test void falloDeSuficienciaInformativaConservaElHechoYSuRecibo(){
        long id=registrar();long observada=propiedades.consultar(id,actor).versionFisica();
        Mockito.doThrow(new IllegalStateException("Fallo de recompensa TEST")).when(suficiencia)
                .evaluar(eq(SuficienciaInmueble.Accion.IDENTIFICAR),any(),anyMap());
        var hecho=propiedades.editar(id,descripcion("Dato correcto conservado").conVersion(observada),actor);
        assertEquals("FALLO_TECNICO",hecho.estadoSuficiencia());assertNull(hecho.suficiencia());
        assertEquals(observada+1,versionDe(id));assertEquals("Dato correcto conservado",descripcionDe(id));
        assertEquals(1,eventosDe(id));
    }

    @Test void falloDeRastroRevierteDatosYContadorEnLaMismaTransaccion(){
        long id=registrar();long observada=propiedades.consultar(id,actor).versionFisica();
        Mockito.doThrow(new IllegalStateException("Rastro indisponible TEST")).when(eventos)
                .save(argThat(e->e!=null&&"PROPIEDAD_EDITADA".equals(e.getTipo())));
        assertThrows(IllegalStateException.class,()->propiedades.editar(id,descripcion("No puede quedar").conVersion(observada),actor));
        assertEquals(observada,versionDe(id));assertEquals("Descripción inicial TEST",descripcionDe(id));assertEquals(0,eventosDe(id));
    }

    @Test void dosTransaccionesConLaMismaObservacionTienenUnSoloEfecto() throws Exception {
        long id=registrar();long observada=propiedades.consultar(id,actor).versionFisica();
        var primero=descripcion("Ganador TEST").conVersion(observada);
        var segundo=descripcion("Observación obsoleta TEST").conVersion(observada);
        var retenida=new CountDownLatch(1);var liberar=new CountDownLatch(1);var primera=new AtomicBoolean();
        Mockito.doAnswer(i->{
            EventoDominio e=i.getArgument(0);
            if("PROPIEDAD_EDITADA".equals(e.getTipo())&&primera.compareAndSet(false,true)){
                retenida.countDown();assertTrue(liberar.await(15,TimeUnit.SECONDS));
            }
            return Mockito.mockingDetails(eventos).getMockCreationSettings().getDefaultAnswer().answer(i);
        }).when(eventos).save(any());
        var hilos=Executors.newFixedThreadPool(2);
        try {
            var ganador=hilos.submit(()->propiedades.editar(id,primero,actor));
            assertTrue(retenida.await(15,TimeUnit.SECONDS));
            var obsoleto=hilos.submit(()->propiedades.editar(id,segundo,actor));
            esperarLockReal(obsoleto);liberar.countDown();
            var hecho=ganador.get(20,TimeUnit.SECONDS);
            var rechazo=assertThrows(ExecutionException.class,()->obsoleto.get(20,TimeUnit.SECONDS));
            assertInstanceOf(ConflictoException.class,rechazo.getCause());
            assertEquals(observada+1,hecho.versionFisica());assertEquals(hecho.versionFisica(),versionDe(id));
            assertEquals("Ganador TEST",descripcionDe(id));assertEquals(1,eventosDe(id));
        } finally {liberar.countDown();hilos.shutdownNow();assertTrue(hilos.awaitTermination(15,TimeUnit.SECONDS));}
    }

    @Test void unaEntidadYaGestionadaSeRefrescaAntesDeComprobarSuVersion() throws Exception {
        long id=registrar();long observada=propiedades.consultar(id,actor).versionFisica();
        var antiguo=descripcion("No debe pisar la caché TEST").conVersion(observada);
        var leida=new CountDownLatch(1);var seguir=new CountDownLatch(1);var hilo=Executors.newSingleThreadExecutor();
        try {
            var pendiente=hilo.submit(()->new TransactionTemplate(gestor).execute(tx->{
                assertEquals(observada,entidades.find(Propiedad.class,id).getVersionFisica());leida.countDown();
                esperar(seguir);return propiedades.editar(id,antiguo,actor);
            }));
            assertTrue(leida.await(15,TimeUnit.SECONDS));
            var actual=propiedades.editar(id,descripcion("Versión confirmada TEST").conVersion(observada),actor);
            seguir.countDown();var rechazo=assertThrows(ExecutionException.class,()->pendiente.get(20,TimeUnit.SECONDS));
            assertInstanceOf(ConflictoException.class,rechazo.getCause());
            assertEquals(actual.versionFisica(),versionDe(id));assertEquals("Versión confirmada TEST",descripcionDe(id));
        } finally {seguir.countDown();hilo.shutdownNow();assertTrue(hilo.awaitTermination(15,TimeUnit.SECONDS));}
    }

    @Test void dirtyCheckingDeOtroCasoNoSobrescribeElContadorFisico() throws Exception {
        long id=registrar();long observada=propiedades.consultar(id,actor).versionFisica();
        var leida=new CountDownLatch(1);var seguir=new CountDownLatch(1);var hilo=Executors.newSingleThreadExecutor();
        try {
            var otro=hilo.submit(()->new TransactionTemplate(gestor).execute(tx->{
                Propiedad antigua=entidades.find(Propiedad.class,id);assertEquals(observada,antigua.getVersionFisica());
                leida.countDown();esperar(seguir);
                // Montaje técnico de otro escritor gestionado: sólo ataca que su flush no incluya version_fisica.
                // No certifica una actuación comercial ni concede facultades sobre ella.
                antigua.setPrecioReferencial(new BigDecimal("1234"));entidades.flush();return null;
            }));
            assertTrue(leida.await(15,TimeUnit.SECONDS));
            var hecho=propiedades.editar(id,descripcion("Edición física confirmada TEST").conVersion(observada),actor);
            seguir.countDown();otro.get(20,TimeUnit.SECONDS);
            assertEquals(hecho.versionFisica(),versionDe(id),"El contador es de escritura nativa exclusiva, no del dirty checking comercial.");
        } finally {seguir.countDown();hilo.shutdownNow();assertTrue(hilo.awaitTermination(15,TimeUnit.SECONDS));}
    }

    private long registrar(){
        return propiedades.registrar(new ComandoRegistro(clave(),ORIGEN,null,"LOCAL_COMERCIAL","COMERCIAL","Descripción inicial TEST",
                new Ubicacion("Av. CAS TEST "+UUID.randomUUID().toString().substring(0,8),"Surco",null,null,null,null,null,null,null),
                null,List.of(new ValorAtributo("metraje_total","120")),null,null),actor).idPropiedad();
    }
    private static ComandoEdicion descripcion(String texto){return new ComandoEdicion(clave(),ORIGEN,texto,null,null,null,null,null,null,null);}
    private long versionDe(long id){return jdbc.queryForObject("select version_fisica from propiedad where id_propiedad=?",Long.class,id);}
    private String descripcionDe(long id){return jdbc.queryForObject("select descripcion from propiedad where id_propiedad=?",String.class,id);}
    private int eventosDe(long id){return jdbc.queryForObject("select count(*) from evento_dominio where entidad_tipo='PROPIEDAD' and entidad_id=? and tipo='PROPIEDAD_EDITADA'",Integer.class,id);}
    private static String clave(){return "fisico-"+UUID.randomUUID();}
    private static void esperar(CountDownLatch latch){try{assertTrue(latch.await(15,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    private void esperarLockReal(Future<?> segundo) throws Exception {
        long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<limite){
            if(segundo.isDone())fail("La segunda actuación terminó sin demostrar el bloqueo concurrente.");
            Integer n=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and wait_event_type='Lock' and query ilike '%propiedad%'",Integer.class);
            if(n!=null&&n>0)return;Thread.sleep(30);
        }
        fail("No se observó un lock PostgreSQL real; la carrera no queda demostrada.");
    }
}
