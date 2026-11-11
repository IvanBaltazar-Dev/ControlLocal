package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.archivo.AccesoArchivoService;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.almacen.AlmacenDocumentos;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** Grupo A: hechos y reasignación Core reales. No inserta avisos ni simula permisos. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class AsuntosGrupoAIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired EncargoService encargos;
    @Autowired CaptacionService captaciones;
    @Autowired AlertaService alertas;
    @Autowired TareaService tareas;
    @Autowired SolicitudService solicitudes;
    @Autowired EvaluacionService evaluaciones;
    @Autowired AsignacionService asignaciones;
    @Autowired ActuacionesService actuaciones;
    @Autowired Documentos json;
    @Autowired AccesoArchivoService archivos;
    @Autowired AlmacenDocumentos almacen;
    private FormalizacionesDePrueba banco;
    @BeforeEach void preparar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);banco=new FormalizacionesDePrueba(contexto);}
    @AfterEach void limpiar(){EvidenciaC4DePrueba.limpiar();}
    private record Caso(ActividadNegociacionDePrueba.Caso comercial,EncargosDePrueba.Resultado presentado){ }
    private Caso presentar(){
        var c=banco.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",false);
        var condiciones=EncargosDePrueba.alquiler(new BigDecimal("2500"),"PEN",BigDecimal.ONE,"Propietario TEST","Agencia TEST");
        var datos=new EncargosDePrueba.Caso(c.propiedad(),com.controllocal.domain.inmueble.OperacionInmobiliaria.ALQUILER,
                OrigenesDePrueba.hoy(),OrigenesDePrueba.hoy().plusMonths(6),false,condiciones,
                new EncargosDePrueba.Actuante(c.agente(),banco.sesiones.de(c.agente())),
                new EncargosDePrueba.Actuante(c.equipo().broker(),banco.sesiones.de(c.equipo().broker())));
        var p=new EncargosDePrueba(jdbc,encargos,gestor,archivos,almacen,actuaciones,json).presentarExistente(c.encargo(),datos);
        return new Caso(c,p);
    }
    private void decidir(Caso c,String accion){
        var actor=c.comercial().equipo().broker();var p=c.presentado().peticion();
        var d=new EncargoService.Decision(accion,p.version(),encargos.consultar(c.comercial().encargo(),actor).version(),
                "Aclarar sustento comercial TEST Grupo A",
                "APROBAR".equals(accion)?new EncargoService.RevisionManuscrita(p.firma().revisionPresentacion(),p.firma().huellaManifestacion(),true):null);
        var sid=banco.sesiones.de(actor);
        var propuesta=actuaciones.prepararDecisionEncargo(actor,p.id(),d,sid,Procedencia.deLaPantalla());
        banco.humanas.confirmar(propuesta,actor,sid,EncargoService.Peticion.class);
    }
    private AlertaService.FichaAlerta aviso(Caso c,Actor actor){
        var pagina=alertas.listar(1,20,actor);
        assertEquals(1,pagina.total());assertEquals(1,pagina.items().size());
        var a=pagina.items().getFirst();assertEquals(c.comercial().encargo(),a.entidadId());
        assertEquals("CAPTACION_REVISADA",a.tipo());return a;
    }
    @Test void profesionalFirmadoNoSeReasignaDirectamenteNiSeCambiaElAviso(){
        var c=presentar();decidir(c,"OBSERVAR");var original=aviso(c,c.comercial().agente());
        assertEquals("MEDIA",original.severidad());assertTrue(original.mensaje().contains("Aclarar sustento"));
        var antes=jdbc.queryForMap("select id_rol_agente,huella_causa_c7,fuente_causa_c7,fecha_generacion from alerta where id_alerta=?",original.id());
        var nuevo=c.comercial().equipo().colega();banco.sesiones.de(nuevo);
        assertEquals(0,alertas.listar(1,20,nuevo).total());
        var broker=c.comercial().equipo().broker();
        // El documento preparado identifica a este profesional. C7 no habilita
        // una reasignación que RF017 exige tramitar mediante adenda.
        var requiereAdenda=assertThrows(ReglaNegocioException.class,()->captaciones.reasignar(c.comercial().encargo(),new CaptacionService.DatosReasignacion(nuevo.idRolOperativo(),
                c.comercial().agente().idRolOperativo(),encargos.consultar(c.comercial().encargo(),broker).version(),"Reparto comercial TEST Grupo A"),
                broker,banco.sesiones.de(broker),"C7-reasignar-"+UUID.randomUUID(),Procedencia.deLaPantalla()));
        assertTrue(requiereAdenda.getMessage().contains("corresponde adenda"));
        assertEquals(c.comercial().agente().idRolOperativo(),encargos.consultar(c.comercial().encargo(),broker).responsable());
        assertEquals(0,alertas.listar(1,20,nuevo).total());
        assertThrows(NoEncontradoException.class,()->alertas.atender(original.id(),nuevo));
        var recibida=aviso(c,c.comercial().agente());assertEquals(original.id(),recibida.id());assertEquals(original.fechaGeneracion(),recibida.fechaGeneracion());
        assertEquals(c.comercial().agente().idRolOperativo(),recibida.idAgente());
        assertEquals(antes,jdbc.queryForMap("select id_rol_agente,huella_causa_c7,fuente_causa_c7,fecha_generacion from alerta where id_alerta=?",original.id()));
        assertEquals(c.comercial().agente().idRolOperativo(),json.comoObjeto((String)antes.get("fuente_causa_c7"),com.controllocal.service.soporte.FuentesAsuntos.Causa.class).responsableAlDetectar());
        assertTrue(alertas.atender(original.id(),c.comercial().agente()));assertFalse(alertas.atender(original.id(),c.comercial().agente()));
        assertEquals(1,numeroAvisos(c));
    }
    @Test void cambioDeSupervisorRevalidaLecturaConteoYAtencionSinRenovarLaOcurrencia(){
        var c=presentar();decidir(c,"OBSERVAR");var anterior=c.comercial().equipo().broker();
        // La devolución la proyecta la consulta de su agente destinatario.
        // El broker sólo consulta esa misma ocurrencia; no produce otra por decidir.
        var original=aviso(c,c.comercial().agente());
        assertEquals(original.id(),aviso(c,anterior).id());
        var antes=jdbc.queryForMap("select id_rol_agente,huella_causa_c7,fuente_causa_c7,fecha_generacion from alerta where id_alerta=?",original.id());
        var nuevo=brokerTecnicoInicial(anterior.idOrganizacion());banco.sesiones.de(nuevo);
        assertEquals(0,alertas.listar(1,20,nuevo).total());
        asignaciones.reasignar(new AsignacionService.DatosReasignacion(c.comercial().agente().idRolOperativo(),nuevo.idRolOperativo(),
                "Cambio competente de supervisión TEST C7"),c.comercial().equipo().admin());
        assertEquals(0,alertas.listar(1,20,anterior).total());
        assertThrows(NoEncontradoException.class,()->alertas.atender(original.id(),anterior));
        var recibida=aviso(c,nuevo);
        assertEquals(original.id(),recibida.id());assertEquals(original.fechaGeneracion(),recibida.fechaGeneracion());
        assertEquals(c.comercial().agente().idRolOperativo(),recibida.idAgente());
        assertEquals(original.agenteNombre(),recibida.agenteNombre());
        assertEquals(antes,jdbc.queryForMap("select id_rol_agente,huella_causa_c7,fuente_causa_c7,fecha_generacion from alerta where id_alerta=?",original.id()));
        assertEquals(original.id(),aviso(c,c.comercial().agente()).id());
        assertTrue(alertas.atender(original.id(),nuevo));assertFalse(alertas.atender(original.id(),nuevo));
        assertEquals(1,numeroAvisos(c));
    }
    @Test void aprobacionEntregaInfoUnaSolaVezYAdministracionNoAbreLaCampana(){
        var c=presentar();decidir(c,"APROBAR");var a=aviso(c,c.comercial().agente());
        assertEquals("INFO",a.severidad());assertEquals(a.id(),aviso(c,c.comercial().agente()).id());assertEquals(1,numeroAvisos(c));
        assertThrows(AccesoNoAutorizadoException.class,()->alertas.listar(1,20,c.comercial().equipo().admin()));
        assertEquals(0,alertas.listar(1,20,c.comercial().equipo().colega()).total());
    }
    @Test void dosTransaccionesProyectanUnaSolaCausaYAtiendenUnaSolaVez() throws Exception {
        var c=presentar();decidir(c,"OBSERVAR");var actor=c.comercial().agente();
        var resultados=carrera(()->alertas.listar(1,20,actor),()->alertas.listar(1,20,actor));
        @SuppressWarnings("unchecked") var a=(Pagina<AlertaService.FichaAlerta>)resultados.getFirst();
        @SuppressWarnings("unchecked") var b=(Pagina<AlertaService.FichaAlerta>)resultados.getLast();
        assertEquals(1,a.total());assertEquals(1,b.total());assertEquals(a.items().getFirst().id(),b.items().getFirst().id());assertEquals(1,numeroAvisos(c));
        long id=a.items().getFirst().id();
        var atendidas=carrera(()->alertas.atender(id,actor),()->alertas.atender(id,c.comercial().equipo().broker()));
        assertEquals(1,atendidas.stream().filter(Boolean.TRUE::equals).count());assertEquals(1,atendidas.stream().filter(Boolean.FALSE::equals).count());
        assertEquals("T",jdbc.queryForObject("select estado from alerta where id_alerta=?",String.class,id));
    }
    @Test void rollbackDeLaProyeccionNoDejaAvisoNiSuprimeElReintento(){
        var c=presentar();decidir(c,"OBSERVAR");
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(gestor).execute(t->{
            assertEquals(1,alertas.listar(1,20,c.comercial().agente()).total());throw new IllegalStateException("Rollback deliberado TEST C7");
        }));
        assertEquals(0,numeroAvisos(c));assertEquals("MEDIA",aviso(c,c.comercial().agente()).severidad());assertEquals(1,numeroAvisos(c));
    }
    @Test void unIdDeHitoAjenoOInexistenteNoPermiteResolverTareas(){
        var c=presentar();
        assertThrows(NoEncontradoException.class,()->tareas.resolverPorCierreConfirmado(Long.MAX_VALUE,c.comercial().agente()));
        assertEquals(0,jdbc.queryForObject("select count(*) from tarea where organizacion_id=? and estado='C'",Integer.class,c.comercial().agente().idOrganizacion()));
    }
    @Test void cierreCompetenteResuelveSolicitudDeOtroEquipoSinConcederSuLectura(){
        var c=banco.nuevo("ALQUILER",EncargosDePrueba.alquiler(new BigDecimal("2500"),"PEN",BigDecimal.ONE,"Propietario TEST","Agencia TEST"));
        var responsableSolicitud=c.comercial().equipo().colega();banco.sesiones.de(responsableSolicitud);
        // Solicitud y Encargo tienen autoridades distintas. El alta vigente
        // de Solicitud admite al agente organizacional sin cambiar el Encargo.
        var sol=solicitudes.registrar(new SolicitudService.DatosSolicitud("C7-SOL-"+UUID.randomUUID().toString().substring(0,12),OrigenesDePrueba.hoy(),
                new BigDecimal("2500"),"PEN","12 meses","Solicitud explícita TEST C7",OrigenesDePrueba.hoy().plusDays(30),
                c.oportunidad(),12,OrigenesDePrueba.hoy(),"TRANSFERENCIA",1,1),responsableSolicitud);
        solicitudes.reenviarAEvaluacion(sol.id(),responsableSolicitud);
        evaluaciones.registrar(new EvaluacionService.DatosEvaluacion("F","A","Aprobación explícita TEST C7",sol.id()),c.broker());
        var tarea=tareas.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),responsableSolicitud).stream().filter(t->"SOLICITUD_ALQUILER".equals(t.entidadTipo())&&Objects.equals(sol.id(),t.entidadId())).findFirst().orElseThrow();
        var antes=jdbc.queryForMap("select fuente_causa_c7,huella_causa_c7,fecha_programada from tarea where id_tarea=?",tarea.id());
        var segundo=brokerTecnicoInicial(c.agente().idOrganizacion());
        asignaciones.reasignar(new AsignacionService.DatosReasignacion(responsableSolicitud.idRolOperativo(),segundo.idRolOperativo(),"Distribución explícita TEST de equipos"),c.comercial().equipo().admin());
        assertThrows(AccesoNoAutorizadoException.class,()->solicitudes.obtener(sol.id(),c.broker()));
        var acuerdo=banco.alquiler(c,new BigDecimal("2500"),"PEN");
        var listo=banco.listo(c,com.controllocal.domain.comercial.TipoHitoContractual.CONTRATO_ALQUILER,OrigenesDePrueba.hoy(),acuerdo,false);
        var confirmado=banco.confirmar(listo);
        assertEquals("C",jdbc.queryForObject("select estado from solicitud_alquiler where id_solicitud=?",String.class,sol.id()));
        assertEquals("C",jdbc.queryForObject("select estado from tarea where id_tarea=?",String.class,tarea.id()));
        assertEquals("RESUELTA",jdbc.queryForObject("select estado_causa_c7 from tarea where id_tarea=?",String.class,tarea.id()));
        assertEquals(antes,jdbc.queryForMap("select fuente_causa_c7,huella_causa_c7,fecha_programada from tarea where id_tarea=?",tarea.id()));
        assertThrows(AccesoNoAutorizadoException.class,()->solicitudes.obtener(sol.id(),c.broker()));
        banco.sesiones.de(segundo);
        assertThrows(AccesoNoAutorizadoException.class,()->tareas.resolverPorCierreConfirmado(confirmado.revisionConfirmada(),segundo));
    }
    /** Sólo identidad/membresía inicial de TEST; la supervisión se cambia por su caso Core. */
    private Actor brokerTecnicoInicial(long org){
        return new TransactionTemplate(gestor).execute(t->{
            String ref=UUID.randomUUID().toString().replace("-","");
            long cuenta=jdbc.queryForObject("insert into cuenta_acceso(nombre_usuario,correo_propio,correo_confirmado_en,contrasena_hash) values(?,?,clock_timestamp(),'fixture-c7-no-login') returning id_cuenta_acceso",Long.class,"c7-"+ref,"c7-"+ref+"@example.invalid");
            long persona=jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,'Broker segundo TEST C7') returning id_persona",Long.class,org);
            long rol=jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,'BROKER',?) returning id_persona_rol",Long.class,org,persona,OrigenesDePrueba.hoy());
            jdbc.update("insert into detalle_broker(organizacion_id,id_persona_rol,codigo_broker,fecha_designacion) values(?,?,?,?)",org,rol,"C7-B-"+rol,OrigenesDePrueba.hoy());
            jdbc.update("insert into usuario_organizacion(organizacion_id,id_usuario,id_persona,rol,administrar,estado) values(?,?,?,'BROKER',false,'A')",org,cuenta,persona);
            return new Actor(org,persona,rol,Actor.BROKER);
        });
    }
    private int numeroAvisos(Caso c){return jdbc.queryForObject("select count(*) from alerta where organizacion_id=? and entidad_tipo='CAPTACION' and entidad_id=? and tipo='CAPTACION_REVISADA'",Integer.class,c.comercial().agente().idOrganizacion(),c.comercial().encargo());}
    private List<Object> carrera(Supplier<Object> uno,Supplier<Object> dos)throws Exception{
        var hilos=Executors.newFixedThreadPool(2);var comienzan=new CountDownLatch(2);var pids=ConcurrentHashMap.<Long>newKeySet();var txs=ConcurrentHashMap.<Long>newKeySet();
        java.util.function.Function<Supplier<Object>,Object> ejecutar=accion->new TransactionTemplate(gestor).execute(t->{
            var identidad=jdbc.queryForMap("select pg_backend_pid() pid,txid_current() txid");pids.add(((Number)identidad.get("pid")).longValue());txs.add(((Number)identidad.get("txid")).longValue());
            comienzan.countDown();try{assertTrue(comienzan.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
            return accion.get();
        });
        try{var a=hilos.submit(()->ejecutar.apply(uno));var b=hilos.submit(()->ejecutar.apply(dos));
            var resultado=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));assertEquals(2,pids.size());assertEquals(2,txs.size());return resultado;
        }finally{hilos.shutdownNow();assertTrue(hilos.awaitTermination(10,TimeUnit.SECONDS));}
    }
}
