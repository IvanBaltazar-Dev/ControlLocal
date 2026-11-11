package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.demanda.InteresadosDeDemanda.*;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Procedencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** CAT-E28/D2: hechos reales Core y PG, sin reiniciar frescura por registrar un antecedente. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class DisponibilidadC3IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired RequerimientoService demandas;
    @Autowired DisponibilidadContextualService disponibilidad;
    private OrigenesDePrueba identidades;
    private OrigenesDePrueba.Equipo equipo;
    private Actor agente;
    private ContextoSesion sesion;
    private RequerimientoService.FichaRequerimiento demanda;
    private Instant hecho;
    @BeforeEach void demandaReal(){
        identidades=new OrigenesDePrueba(jdbc,gestor,captura,contextos);
        equipo=identidades.empresa();agente=equipo.agente();sesion=identidades.sesion(agente);
        var contacto=new Referencia(TipoReferencia.CONTACTO_NUEVO,null,null,null,
                new DatosContacto(null,null,null,null,"999000001",null));
        var datos=new RequerimientoService.DatosRequerimiento(null,null,null,null,null,null,"ALQUILER",
                List.of("DEPARTAMENTO"),null,null,null,null,null,null,null,"Disponibilidad TEST",
                null,null,contacto,null,null,null,null,Set.of());
        demanda=demandas.crear(datos,agente,sesion,clave(),Procedencia.deLaPantalla());
        hecho=Instant.now().minus(1,ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
    }
    @Test void confirmacionEsHechoDurableConAutorContextoYFechaOriginal(){
        var r=confirmar(0,true,hecho,clave());
        assertEquals(Aplicacion.APLICADA,r.aplicacion());
        var vista=disponibilidad.consultar(TipoRecurso.DEMANDA,demanda.id(),agente);
        assertEquals(r.registrado().id(),vista.vigente().id());assertEquals(hecho,vista.vigente().fechaHecho());
        assertEquals(agente.idRolOperativo(),vista.vigente().idRolConfirmante());
        assertEquals("DEMANDA:"+demanda.id(),vista.clave().contexto());
        assertEquals(demanda.version(),vista.versionRecurso());
    }
    @Test void antecedenteNoSustituyeDisponibilidadPosteriorNiRenuevaFrescura(){
        var actual=confirmar(0,true,hecho,clave());
        var anterior=confirmar(actual.registrado().version(),false,hecho.minus(1,ChronoUnit.DAYS),clave());
        assertEquals(Aplicacion.ANTECEDENTE_CONSERVADO,anterior.aplicacion());
        var vista=disponibilidad.consultar(TipoRecurso.DEMANDA,demanda.id(),agente);
        assertEquals(actual.registrado().id(),vista.vigente().id());
        assertEquals(actual.registrado().baseFrescura(),vista.vigente().baseFrescura());
        assertTrue(vista.versionConfirmacion()>actual.registrado().version());
    }
    @Test void replayDevuelveOriginalAunqueYaExistaConfirmacionPosterior(){
        var clave=clave();var original=confirmar(0,true,hecho,clave);
        confirmar(original.registrado().version(),false,hecho.plus(1,ChronoUnit.MINUTES),clave());
        assertEquals(original,confirmar(0,true,hecho,clave));
    }
    @Test void colegaNoConfirmaNiConsultaDemandaAjena(){
        var colega=equipo.colega();var sid=identidades.sesion(colega);
        assertThrows(AccesoNoAutorizadoException.class,()->disponibilidad.confirmar(datos(0,true,hecho),colega,sid,clave(),Procedencia.deLaPantalla()));
        assertThrows(AccesoNoAutorizadoException.class,()->disponibilidad.consultar(TipoRecurso.DEMANDA,demanda.id(),colega));
    }
    @Test void brokerNoSustituyeConfirmacionEspecificaDelResponsable(){
        assertThrows(AccesoNoAutorizadoException.class,()->disponibilidad.confirmar(datos(0,true,hecho),equipo.broker(),sesion,clave(),Procedencia.deLaPantalla()));
    }
    @Test void otraOrganizacionNoObtieneElRecurso(){
        var otra=identidades.empresa().agente();
        assertThrows(NoEncontradoException.class,()->disponibilidad.consultar(TipoRecurso.DEMANDA,demanda.id(),otra));
    }
    @Test void sidRevocadoImpideRegistrarYRepetirUnHecho(){
        var clave=clave();confirmar(0,true,hecho,clave);
        contextos.cerrar(sesion.referenciaSesion(),sesion.revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class,()->confirmar(0,true,hecho,clave));
    }
    @Test void confirmacionesConcurrentesMismoCasProducenUnHechoYUnConflicto() throws Exception {
        var barrera=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<String> confirmar=()->{
                barrera.await(20,TimeUnit.SECONDS);
                try{confirmar(0,true,hecho,clave());return "APLICADO";}catch(ConflictoException e){return "CONFLICTO";}
            };
            var a=pool.submit(confirmar);var b=pool.submit(confirmar);
            assertEquals(Set.of("APLICADO","CONFLICTO"),Set.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS)));
            assertEquals(1L,jdbc.queryForObject("select count(*) from confirmacion_disponibilidad_contextual where organizacion_id=? and id_recurso=?",Long.class,agente.idOrganizacion(),demanda.id()));
        } finally {pool.shutdownNow();}
    }
    @Test void hechosDeDisponibilidadNoSeEliminanPorTruncate(){
        var original=confirmar(0,true,hecho,clave());
        assertThrows(org.springframework.dao.DataAccessException.class,
                ()->jdbc.execute("truncate confirmacion_disponibilidad_contextual"));
        assertEquals(original.registrado().id(),disponibilidad.consultar(TipoRecurso.DEMANDA,demanda.id(),agente).vigente().id());
    }
    private DatosConfirmacion datos(long version,boolean disponible,Instant fecha){
        return new DatosConfirmacion(TipoRecurso.DEMANDA,demanda.id(),demanda.version(),version,disponible,fecha,null,"Declaración TEST","Evidencia declarada TEST");
    }
    private ResultadoConfirmacion confirmar(long version,boolean disponible,Instant fecha,String clave){
        return disponibilidad.confirmar(datos(version,disponible,fecha),agente,sesion,clave,Procedencia.deLaPantalla());
    }
    private static String clave(){return UUID.randomUUID().toString();}
}
