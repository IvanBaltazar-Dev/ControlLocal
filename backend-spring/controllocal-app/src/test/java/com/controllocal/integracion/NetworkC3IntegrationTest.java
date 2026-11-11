package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.colaboracion.FuenteOfertaConcedida;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.ContratoMatching.ConfirmacionDisponibilidad;
import com.controllocal.service.network.*;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * C3 PG/T4 del gestor, máquina humana y destinatarios. La fuente G/D2/tratamiento es
 * fixture explícito: esta suite NO acredita la fuente canónica ni habilita producción.
 * Cuentas, sesiones, Encargo borrador, SQL, CAS, recibos y outbox son reales.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=10")
class NetworkC3IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired EncargoService encargos;
    @Autowired NetworkService network;
    @Autowired AvisosNetworkService avisos;
    @Autowired ActuacionesService actuaciones;
    @Autowired CuentaService cuentas;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @MockitoBean FuenteDeNetwork fuente;
    @MockitoBean FuenteOfertaConcedida ofertaConcedida;
    @MockitoSpyBean EventoDominioRepository eventos;
    private OrigenesDePrueba origenes;
    private OrigenesDePrueba.Equipo equipo;
    private SesionesComercialesDePrueba sesiones;
    private long encargo;
    private volatile boolean elegible;
    private static final String HD=Idempotencia.huellaExacta("datos-v1-test"),HA=Idempotencia.huellaExacta("autoridad-v1-test");
    private static final Procedencia PROC=Procedencia.deLaPantalla();
    private static final Set<String> CAMPOS=Set.of("ENCARGO:operacion");

    @BeforeEach void preparar() {
        origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);equipo=origenes.empresa();
        sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        long propiedad=origenes.propiedad(equipo.agente(),"Network C3");
        encargo=new TransactionTemplate(gestor).execute(t->encargos.abrir(new EncargoService.DatosApertura(
                propiedad,equipo.agente().idRolOperativo(),"N3-"+UUID.randomUUID().toString().substring(0,12),
                OperacionInmobiliaria.ALQUILER,hoy(),hoy().plusMonths(6),false,"Borrador TEST del gestor",
                EncargosDePrueba.alquiler(new BigDecimal("2500"),"PEN",BigDecimal.ONE,"Propietario TEST","Agencia TEST"),PROC,List.of()),equipo.agente()).id());
        elegible=true;
        when(fuente.consultarAutoridadPropia(eq(encargo),any())).thenAnswer(i->{Actor a=i.getArgument(1);propia(a);return autoridad();});
        when(fuente.consultarPropia(eq(encargo),anySet(),any(),any())).thenAnswer(i->{Actor a=i.getArgument(3);propia(a);return new FuenteDeNetwork.FuentePropia(
                org(),encargo,equipo.agente().idRolOperativo(),equipo.broker().idRolOperativo(),false,0,0,
                PoliticaProyeccionNetworkV1.VERSION,PoliticaProyeccionNetworkV1.FINALIDAD,
                Map.of("ENCARGO:operacion",Map.of("valor","ALQUILER")),HD,HA,elegible,causas());});
        when(fuente.verificarActuales(anyCollection(),any())).thenAnswer(i->Map.of(encargo,new FuenteDeNetwork.VerificacionActual(
                org(),encargo,equipo.agente().idRolOperativo(),equipo.broker().idRolOperativo(),false,
                PoliticaProyeccionNetworkV1.VERSION,PoliticaProyeccionNetworkV1.FINALIDAD,HD,HA,
                elegible,causas(),new ConfirmacionDisponibilidad(Instant.now().minusSeconds(60),false,"D2_TEST_EXPLICITO"))));
    }
    @AfterEach void limpiarSpies(){reset(eventos);}

    @Test void solicitudNoPublicaYDecisionBrokerActivaUnaSolaVezConHistoria() {
        var solicitud=solicitar();assertEquals("N",solicitud.estadoBase());assertNotNull(solicitud.solicitudPendiente());
        assertEquals(0,cuenta("version_participacion_network"));
        var p=prepararDecision(solicitud.solicitudPendiente());var s=sesion(equipo.broker());
        var revision=actuaciones.revisar(equipo.broker(),p.referencia(),s);
        var confirmacion=confirmacion(revision);var r=actuaciones.confirmar(equipo.broker(),p.referencia(),s,confirmacion,PROC);
        assertEquals("EXITO",r.estado());
        actuaciones.confirmar(equipo.broker(),p.referencia(),s,confirmacion,PROC);
        assertEquals("A",network.consultar(encargo,equipo.agente()).estadoBase());
        assertEquals(1,cuenta("version_participacion_network"));assertEquals(1,cuenta("decision_participacion_network"));
        assertEquals(1,eventos("NETWORK_DECIDIDA"));
    }

    @Test void colegaYOrganizacionAjenaNoDecidenNiConsultanLaSolicitud() {
        var vista=solicitar();var otra=origenes.empresa();
        assertThrows(AccesoNoAutorizadoException.class,()->network.consultarSolicitud(vista.solicitudPendiente(),equipo.colega()));
        assertThrows(RuntimeException.class,()->network.consultarSolicitud(vista.solicitudPendiente(),otra.broker()));
        var d=datosDecision(vista.solicitudPendiente());
        assertThrows(AccesoNoAutorizadoException.class,()->actuaciones.prepararDecisionNetwork(equipo.agente(),d,sesion(equipo.agente()),PROC));
        assertEquals(0,cuenta("decision_participacion_network"));assertEquals(0,cuenta("version_participacion_network"));
    }

    @Test void confirmacionesConcurrentesNoDuplicanPublicacionNiDecision() throws Exception {
        var vista=solicitar();var p=prepararDecision(vista.solicitudPendiente());var actor=equipo.broker();var s=sesion(actor);
        var revision=actuaciones.revisar(actor,p.referencia(),s);var c=confirmacion(revision);
        var salida=Executors.newFixedThreadPool(2);var puerta=new CountDownLatch(1);
        try {
            var tareas=new ArrayList<Future<?>>();for(int n=0;n<2;n++)tareas.add(salida.submit(()->{puerta.await();return actuaciones.confirmar(actor,p.referencia(),s,c,PROC);}));
            puerta.countDown();for(var t:tareas)t.get(30,TimeUnit.SECONDS);
        } finally {salida.shutdownNow();}
        assertEquals(1,cuenta("version_participacion_network"));assertEquals(1,cuenta("decision_participacion_network"));assertEquals(1,eventos("NETWORK_DECIDIDA"));
    }

    @Test void perdidaConocidaSuspendeAntesDeEntregarYNoBorraLaVersionAprobada() {
        activar();elegible=false;
        var v=network.consultar(encargo,equipo.agente());assertEquals("S",v.estadoBase());
        assertEquals(1,cuenta("version_participacion_network"));assertEquals(1,eventos("NETWORK_SUSPENDIDA"));
        network.consultar(encargo,equipo.agente());assertEquals(1,eventos("NETWORK_SUSPENDIDA"));
    }

    @Test void solicitudAvisaSoloAlBrokerYDecisionAResponsableYBrokerSinDatosV1() {
        var vista=solicitar();
        assertEquals(0,avisos.consultar(1,20,equipo.agente(),sesion(equipo.agente())).total());
        assertEquals(1,avisos.consultar(1,20,equipo.broker(),sesion(equipo.broker())).total());
        aprobar(vista.solicitudPendiente());
        var agente=avisos.consultar(1,20,equipo.agente(),sesion(equipo.agente()));
        assertEquals(1,agente.total());assertEquals("NETWORK_DECIDIDA",agente.items().getFirst().tipo());
        assertEquals(2,avisos.consultar(1,20,equipo.broker(),sesion(equipo.broker())).total());
        assertEquals(0,avisos.consultar(1,20,equipo.colega(),sesion(equipo.colega())).total());
        var otra=origenes.empresa();assertEquals(0,avisos.consultar(1,20,otra.broker(),sesion(otra.broker())).total());
        assertThrows(AccesoNoAutorizadoException.class,()->avisos.consultar(1,20,equipo.admin(),sesion(equipo.broker())));
    }

    private NetworkService.Vista solicitar() {
        var actor=equipo.agente();var ahora=Instant.now();
        var d=new NetworkService.DatosSolicitud(encargo,NetworkService.Cambio.ACTIVAR,CAMPOS,
                PoliticaProyeccionNetworkV1.FINALIDAD,ahora.minusSeconds(1),ahora.plusSeconds(3600),
                "Solicitud TEST expresa",0L,HD,HA);
        ejecutar(actor,actuaciones.prepararSolicitudNetwork(actor,d,sesion(actor),PROC));return network.consultar(encargo,actor);
    }
    private void activar(){aprobar(solicitar().solicitudPendiente());}
    private void aprobar(String ref){ejecutar(equipo.broker(),prepararDecision(ref));}
    private ActuacionesService.Propuesta prepararDecision(String ref){return actuaciones.prepararDecisionNetwork(equipo.broker(),datosDecision(ref),sesion(equipo.broker()),PROC);}
    private NetworkService.DatosDecision datosDecision(String ref){var s=network.consultarSolicitud(ref,equipo.broker());var p=network.consultar(s.idEncargo(),equipo.broker());return new NetworkService.DatosDecision(ref,NetworkService.Decision.APROBAR,"Aprobación TEST",s.version(),p.version(),s.huellaDatos(),s.huellaAutoridad());}
    private void ejecutar(Actor a,ActuacionesService.Propuesta p){var s=sesion(a);var revision=actuaciones.revisar(a,p.referencia(),s);assertEquals("EXITO",actuaciones.confirmar(a,p.referencia(),s,confirmacion(revision),PROC).estado());}
    private ActuacionesService.Confirmacion confirmacion(ActuacionesService.Revision r){return new ActuacionesService.Confirmacion(r.propuesta().version(),r.propuesta().huella(),r.pruebaRevision(),"humano-test-"+UUID.randomUUID());}
    private ContextoSesion sesion(Actor a){return sesiones.de(a);}
    private long org(){return equipo.agente().idOrganizacion();}
    private FuenteDeNetwork.AutoridadActual autoridad(){return new FuenteDeNetwork.AutoridadActual(org(),encargo,equipo.agente().idRolOperativo(),equipo.broker().idRolOperativo(),false,elegible,causas());}
    private Set<String> causas(){return elegible?Set.of():Set.of("LEGITIMACION_RETIRADA_TEST");}
    private void propia(Actor a){if(a.idOrganizacion()!=org()||a.idRolOperativo()!=equipo.agente().idRolOperativo()&&a.idRolOperativo()!=equipo.broker().idRolOperativo())throw new AccesoNoAutorizadoException();}
    private long cuenta(String tabla){return jdbc.queryForObject("select count(*) from "+tabla+" where organizacion_id=?",Long.class,org());}
    private long eventos(String tipo){return jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and tipo=?",Long.class,org(),tipo);}
    private static LocalDate hoy(){return LocalDate.now(ZoneId.of("America/Lima"));}
}
