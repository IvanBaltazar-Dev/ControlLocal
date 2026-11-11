package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.integracion.soporte.OrigenesDePrueba;
import com.controllocal.integracion.soporte.DemandasParaColaboracionDePrueba;
import com.controllocal.persistence.repositorio.CursorMatchingRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.RequerimientoService;
import com.controllocal.service.captura.MotorDeCaptura;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** D4-29–40: PostgreSQL real de metadata técnica; no certifica autoridad comercial por SQL. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class MatchingC3MetadataIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired CursorMatchingRepository cursores;
    @Autowired RequerimientoService demandas;
    private Actor actor;
    private Actor colega;
    private UUID recorrido;
    @BeforeEach void metadataAislada(){
        var equipo=new OrigenesDePrueba(jdbc,gestor,captura,contextos).empresa();
        actor=equipo.agente();colega=equipo.colega();
        recorrido=UUID.randomUUID();
        jdbc.update("""
            insert into matching_recorrido(id_recorrido,organizacion_id,id_persona,id_rol,rol,
                huella_contexto,tipo_origen,id_origen,version_origen,huella_criterios,modalidad,
                capas,categoria,version_regla,corte,creado_en,vence_en,tamano)
            values(?,?,?,?,'AGENTE',?,'CAPTURA',1,0,?,'PRELIMINAR','L0','POTENCIAL',
                'TEST_METADATA',now(),now(),now()+interval '15 minutes',20)
            """,recorrido,actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),"a".repeat(64),"b".repeat(64));
    }
    @Test void mismoCursorConcurrenteConservaUnaReferenciaYNoAbortaTransacciones() throws Exception {
        var barrera=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<String> crear=()->new TransactionTemplate(gestor).execute(t->{
                try{barrera.await(20,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
                cursores.insertar(referencia(),actor.idOrganizacion(),recorrido,20);
                return cursores.porPosicion(actor.idOrganizacion(),recorrido,20).orElseThrow().getReferencia();
            });
            var a=pool.submit(crear);var b=pool.submit(crear);
            assertEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertEquals(1L,jdbc.queryForObject("select count(*) from matching_cursor where organizacion_id=? and id_recorrido=? and posicion=20",Long.class,actor.idOrganizacion(),recorrido));
        } finally {pool.shutdownNow();}
    }
    @Test void cursorNoPuedeVincularRecorridoDeOtraOrganizacion(){
        var otro=new OrigenesDePrueba(jdbc,gestor,captura,contextos).empresa().agente();
        assertThrows(DataAccessException.class,()->new TransactionTemplate(gestor).execute(t->cursores.insertar(referencia(),otro.idOrganizacion(),recorrido,0)));
        assertTrue(cursores.porPosicion(otro.idOrganizacion(),recorrido,0).isEmpty());
    }
    @Test void fechaOriginalNoPuedeExtenderseAlNavegar(){
        assertThrows(DataAccessException.class,()->jdbc.update("update matching_recorrido set vence_en=vence_en+interval '1 second' where id_recorrido=?",recorrido));
        assertEquals(900L,jdbc.queryForObject("select extract(epoch from vence_en-creado_en)::bigint from matching_recorrido where id_recorrido=?",Long.class,recorrido));
    }
    @Test void cursorNoPuedeReescribirseHaciaOtraPosicion(){
        var ref=referencia();new TransactionTemplate(gestor).executeWithoutResult(t->cursores.insertar(ref,actor.idOrganizacion(),recorrido,0));
        assertThrows(DataAccessException.class,()->jdbc.update("update matching_cursor set posicion=20 where referencia=?",ref));
        assertEquals(0,cursores.porReferencia(actor.idOrganizacion(),ref).orElseThrow().getPosicion());
    }
    @Test void rolDeOtraOrganizacionNoPuedeSerTitularDelRecorrido(){
        var otro=new OrigenesDePrueba(jdbc,gestor,captura,contextos).empresa().agente();
        assertThrows(DataAccessException.class,()->jdbc.update("""
            insert into matching_recorrido(id_recorrido,organizacion_id,id_persona,id_rol,rol,
              huella_contexto,tipo_origen,id_origen,version_origen,huella_criterios,modalidad,capas,
              capas_no_disponibles,categoria,version_regla,corte,creado_en,vence_en,tamano)
            select ?,organizacion_id,id_persona,? ,rol,
              huella_contexto,tipo_origen,id_origen,version_origen,huella_criterios,modalidad,capas,
              capas_no_disponibles,categoria,version_regla,corte,creado_en,vence_en,tamano
            from matching_recorrido where id_recorrido=?
            """,UUID.randomUUID(),otro.idRolOperativo(),recorrido));
    }
    @Test void personaYRolDeDosMiembrosDelMismoTenantNoFormanUnActor(){
        assertThrows(DataAccessException.class,()->jdbc.update("""
            insert into matching_recorrido(id_recorrido,organizacion_id,id_persona,id_rol,rol,
              huella_contexto,tipo_origen,id_origen,version_origen,huella_criterios,modalidad,capas,
              capas_no_disponibles,categoria,version_regla,corte,creado_en,vence_en,tamano)
            select ?,organizacion_id,id_persona,?,rol,huella_contexto,tipo_origen,id_origen,
              version_origen,huella_criterios,modalidad,capas,capas_no_disponibles,categoria,
              version_regla,corte,creado_en,vence_en,tamano
            from matching_recorrido where id_recorrido=?
            """,UUID.randomUUID(),colega.idRolOperativo(),recorrido));
    }
    @Test void hexadecimalPreexistenteSigueAceptadoEnLasTresCapas() {
        long demanda=demandaDe(actor);UUID r=recorridoDe(actor,demanda,"D1-D4-20261018-C3-R3");
        String uuid=UUID.randomUUID().toString();
        insertar(actor.idOrganizacion(),r,demanda,0,"L0","ENCARGO","11","a".repeat(64));
        insertar(actor.idOrganizacion(),r,demanda,1,"L1","INTERNA","encargo:12","b".repeat(64));
        insertar(actor.idOrganizacion(),r,demanda,2,"L2","NETWORK",uuid,"c".repeat(64));
        assertEquals(List.of("a".repeat(64),"b".repeat(64),"c".repeat(64)),jdbc.queryForList(
                "select referencia_candidato from matching_referencia where organizacion_id=? and id_recorrido=? order by ordinal",String.class,actor.idOrganizacion(),r));
    }
    @Test void uuidCanonicaNetworkSePersisteExactaSinRehash() {
        long demanda=demandaDe(actor);UUID r=recorridoDe(actor,demanda,"D1-D4-20261018-C3-R3");String ref=UUID.randomUUID().toString();
        insertar(actor.idOrganizacion(),r,demanda,0,"L2","NETWORK",ref,ref);
        var fila=jdbc.queryForMap("select referencia_candidato,referencia_recurso,capa,tipo_recurso from matching_referencia where organizacion_id=? and id_recorrido=? and ordinal=0",actor.idOrganizacion(),r);
        assertEquals(ref,fila.get("referencia_candidato"));assertEquals(ref,fila.get("referencia_recurso"));
        assertEquals("L2",fila.get("capa"));assertEquals("NETWORK",fila.get("tipo_recurso"));
    }
    @Test void uuidNoSeExtiendeAL0L1NiDesactivaLaCorrespondenciaEntreCapaYTipo() {
        long demanda=demandaDe(actor);UUID r=recorridoDe(actor,demanda,"D1-D4-20261018-C3-R3");String ref=UUID.randomUUID().toString();
        falla("23514","ck_matching_referencia_opaca",()->insertar(actor.idOrganizacion(),r,demanda,0,"L0","ENCARGO",ref,ref));
        falla("23514","ck_matching_referencia_opaca",()->insertar(actor.idOrganizacion(),r,demanda,0,"L1","INTERNA",ref,ref));
        for(var par:List.of(List.of("L0","NETWORK"),List.of("L1","NETWORK"),List.of("L2","ENCARGO"),List.of("L2","INTERNA")))
            falla("23514","ck_matching_referencia_capa",()->insertar(actor.idOrganizacion(),r,demanda,0,par.get(0),par.get(1),ref,"a".repeat(64)));
        assertEquals(0L,cuenta(r));
    }
    @Test void uuidRechazaFormatosNoCanonicosYRecursoDistintoOAusente() {
        long demanda=demandaDe(actor);UUID r=recorridoDe(actor,demanda,"D1-D4-20261018-C3-R3");String ref="abcdef01-2345-4678-9abc-def012345678";
        for(String invalida:List.of("network-TEST",ref.toUpperCase(Locale.ROOT)," "+ref,ref+" ",ref.replace("-",""),ref.replace('a','g')))
            falla("23514","ck_matching_referencia_opaca",()->insertar(actor.idOrganizacion(),r,demanda,0,"L2","NETWORK",invalida,invalida));
        falla("23514","ck_matching_referencia_opaca",()->insertar(actor.idOrganizacion(),r,demanda,0,"L2","NETWORK",UUID.randomUUID().toString(),ref));
        falla("23502","referencia_recurso",()->insertar(actor.idOrganizacion(),r,demanda,0,"L2","NETWORK",null,ref));
        falla("23502","referencia_recurso",()->insertar(actor.idOrganizacion(),recorrido,demanda,0,"L0","ENCARGO",null,null));
        assertEquals(0L,cuenta(r));
    }
    @Test void nullHistoricoSeConservaPeroElTriggerR3SigueExigiendoReferencia() {
        long demanda=demandaDe(actor);
        insertar(actor.idOrganizacion(),recorrido,demanda,0,"L0","ENCARGO","legado-TEST",null);
        var legada=jdbc.queryForMap("select referencia_candidato,referencia_recurso from matching_referencia where organizacion_id=? and id_recorrido=?",actor.idOrganizacion(),recorrido);
        assertNull(legada.get("referencia_candidato"));assertEquals("legado-TEST",legada.get("referencia_recurso"));
        UUID r3=recorridoDe(actor,demanda,"D1-D4-20261018-C3-R3");
        falla("23514","El recorrido R3 conserva la referencia exacta entregada",()->insertar(actor.idOrganizacion(),r3,demanda,0,"L2","NETWORK",UUID.randomUUID().toString(),null));
        assertEquals(0L,cuenta(r3));
    }
    @Test void uuidConservaLaUnicidadDelParYDelOrdinal() {
        long demanda=demandaDe(actor);UUID r=recorridoDe(actor,demanda,"D1-D4-20261018-C3-R3");String ref=UUID.randomUUID().toString();
        insertar(actor.idOrganizacion(),r,demanda,0,"L2","NETWORK",ref,ref);
        var repetida=falla("23505",null,()->insertar(actor.idOrganizacion(),r,demanda,1,"L2","NETWORK",ref,ref));
        String mensaje=repetida.getMostSpecificCause().getMessage();
        assertTrue(mensaje.contains("uq_matching_candidato")||mensaje.contains("uq_matching_referencia_par"));
        String otra=UUID.randomUUID().toString();
        falla("23505","uq_matching_ordinal",()->insertar(actor.idOrganizacion(),r,demanda,0,"L2","NETWORK",otra,otra));
        assertEquals(1L,cuenta(r));
    }
    @Test void uuidNoPermiteCruzarOrganizacionDeRecorridoNiDemanda() {
        long demanda=demandaDe(actor);UUID r=recorridoDe(actor,demanda,"D1-D4-20261018-C3-R3");
        var otro=new OrigenesDePrueba(jdbc,gestor,captura,contextos).empresa().agente();
        long ajena=demandaDe(otro);UUID otroRecorrido=recorridoDe(otro,ajena,"D1-D4-20261018-C3-R3");String ref=UUID.randomUUID().toString();
        falla("23503","fk_matching_referencia_recorrido",()->insertar(otro.idOrganizacion(),r,ajena,0,"L2","NETWORK",ref,ref));
        falla("23503","fk_matching_referencia_demanda",()->insertar(otro.idOrganizacion(),otroRecorrido,demanda,0,"L2","NETWORK",ref,ref));
        assertEquals(0L,cuenta(r));assertEquals(0L,jdbc.queryForObject("select count(*) from matching_referencia where organizacion_id=? and id_recorrido=?",Long.class,otro.idOrganizacion(),otroRecorrido));
    }
    @Test void uuidNoSeReescribeNiSeReactivaQuitandoSuRetirada() {
        long demanda=demandaDe(actor);UUID r=recorridoDe(actor,demanda,"D1-D4-20261018-C3-R3");String ref=UUID.randomUUID().toString();
        insertar(actor.idOrganizacion(),r,demanda,0,"L2","NETWORK",ref,ref);String otra=UUID.randomUUID().toString();
        falla("23514","La metadata original de matching no se reescribe",()->jdbc.update(
                "update matching_referencia set referencia_candidato=?,referencia_recurso=? where organizacion_id=? and id_recorrido=?",otra,otra,actor.idOrganizacion(),r));
        jdbc.update("insert into matching_referencia_retirada(organizacion_id,id_recorrido,ordinal,retirada_en) values(?,?,0,now())",actor.idOrganizacion(),r);
        falla("23514","La referencia retirada no se reactiva dentro del mismo recorrido",()->jdbc.update("delete from matching_referencia_retirada where organizacion_id=? and id_recorrido=?",actor.idOrganizacion(),r));
        assertEquals(ref,jdbc.queryForObject("select referencia_candidato from matching_referencia where organizacion_id=? and id_recorrido=?",String.class,actor.idOrganizacion(),r));
        assertEquals(1L,jdbc.queryForObject("select count(*) from matching_referencia_retirada where organizacion_id=? and id_recorrido=?",Long.class,actor.idOrganizacion(),r));
    }
    @Test void insercionConcurrenteDeLaMismaUuidConservaUnaSolaIdentidad() throws Exception {
        long demanda=demandaDe(actor);UUID r=recorridoDe(actor,demanda,"D1-D4-20261018-C3-R3");String ref=UUID.randomUUID().toString();
        var barrera=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);var ordinal=new java.util.concurrent.atomic.AtomicInteger();
        try {
            Callable<Boolean> insertar=()->{
                try{
                    new TransactionTemplate(gestor).executeWithoutResult(t->{
                        try{barrera.await(20,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
                        insertar(actor.idOrganizacion(),r,demanda,ordinal.getAndIncrement(),"L2","NETWORK",ref,ref);
                    });return true;
                }catch(DataAccessException e){assertEquals("23505",sql(e).getSQLState());return false;}
            };
            var a=pool.submit(insertar);var b=pool.submit(insertar);
            boolean primera=a.get(30,TimeUnit.SECONDS),segunda=b.get(30,TimeUnit.SECONDS);
            assertTrue(primera^segunda);assertEquals(1L,cuenta(r));
            assertEquals(ref,jdbc.queryForObject("select referencia_candidato from matching_referencia where organizacion_id=? and id_recorrido=?",String.class,actor.idOrganizacion(),r));
        } finally {pool.shutdownNow();}
    }

    private long demandaDe(Actor titular){
        var origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);
        return new DemandasParaColaboracionDePrueba(jdbc,demandas).crear(titular,origenes.sesion(titular)).id();
    }
    private UUID recorridoDe(Actor titular,long demanda,String regla){
        UUID r=UUID.randomUUID();jdbc.update("""
            insert into matching_recorrido(id_recorrido,organizacion_id,id_persona,id_rol,rol,
                huella_contexto,tipo_origen,id_origen,version_origen,huella_criterios,modalidad,
                capas,categoria,version_regla,corte,creado_en,vence_en,tamano)
            values(?,?,?,?,'AGENTE',?,'DEMANDA',?,0,?,'COMERCIAL','L0,L1,L2','OBLIGATORIAS_PENDIENTES',
                ?,now(),now(),now()+interval '15 minutes',20)
            """,r,titular.idOrganizacion(),titular.idPersona(),titular.idRolOperativo(),"a".repeat(64),demanda,"b".repeat(64),regla);return r;
    }
    private void insertar(long org,UUID r,long demanda,int ordinal,String capa,String tipo,String recurso,String candidata){
        jdbc.update("""
            insert into matching_referencia(organizacion_id,id_recorrido,ordinal,capa,tipo_recurso,
                referencia_recurso,version_recurso,huella_proyeccion,id_demanda,version_demanda,huella_criterios,referencia_candidato)
            values(?,?,?,?,?,?,1,?,?,0,?,?)
            """,org,r,ordinal,capa,tipo,recurso,"c".repeat(64),demanda,"d".repeat(64),candidata);
    }
    private long cuenta(UUID r){return jdbc.queryForObject("select count(*) from matching_referencia where organizacion_id=? and id_recorrido=?",Long.class,actor.idOrganizacion(),r);}
    private static DataAccessException falla(String estado,String causa,Runnable accion){
        var error=assertThrows(DataAccessException.class,accion::run);assertEquals(estado,sql(error).getSQLState());
        if(causa!=null)assertTrue(error.getMostSpecificCause().getMessage().contains(causa),"Debe fallar la guarda específica esperada.");return error;
    }
    private static java.sql.SQLException sql(DataAccessException e){
        assertInstanceOf(java.sql.SQLException.class,e.getMostSpecificCause());return (java.sql.SQLException)e.getMostSpecificCause();
    }
    private static String referencia(){return Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().substring(0,32).getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
}
