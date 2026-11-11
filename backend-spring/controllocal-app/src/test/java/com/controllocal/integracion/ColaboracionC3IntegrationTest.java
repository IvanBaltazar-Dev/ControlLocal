package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.colaboracion.*;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.colaboracion.*;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.ContratoMatching.ConfirmacionDisponibilidad;
import com.controllocal.service.matching.ContratoMatching.Categoria;
import com.controllocal.service.matching.ContratoMatching.Modalidad;
import com.controllocal.service.matching.ContratoMatching.Conclusion;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.controllocal.domain.colaboracion.LadoColaboracion.*;

/**
 * Provider, demanda, máquina humana, decisiones, proof y concesión REALES.
 * Sólo G/D2/tratamiento de oferta y hash de su operación son fixtures explícitos;
 * no acredita esa fuente canónica ni habilitación productiva L2.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=10")
class ColaboracionC3IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired EncargoService encargos;
    @Autowired NetworkService network;
    @Autowired ColaboracionService colaboraciones;
    @Autowired CanalesColaboracionService canales;
    @Autowired RequerimientoService demandas;
    @Autowired ActuacionesService actuaciones;
    @Autowired CoincidenciaService coincidencias;
    @Autowired DisponibilidadContextualService disponibilidad;
    @Autowired CuentaService cuentas;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @Autowired Documentos documentos;
    @MockitoBean FuenteDeNetwork fuente;
    @MockitoBean FuenteOfertaConcedida ofertaConcedida;
    private OrigenesDePrueba.Equipo demanda,oferta;
    private SesionesComercialesDePrueba sesiones;
    private DemandasParaColaboracionDePrueba bancoDemanda;
    private RequerimientoService.FichaRequerimiento fichaDemanda;
    private long encargo;
    private AlcanceColaboracion alcance;
    private String solicitud;
    private static final Procedencia PROC=Procedencia.deLaPantalla();
    private static final String HD=Idempotencia.huellaExacta("oferta-colab-test"),HA=Idempotencia.huellaExacta("autoridad-colab-test");
    private static final Map<String,Object> OFERTA=Map.of("ENCARGO:operacion",Map.of("valor","ALQUILER"));

    @BeforeEach void prepararDosOrganizaciones() {
        var origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);demanda=origenes.empresa();oferta=origenes.empresa();
        sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        bancoDemanda=new DemandasParaColaboracionDePrueba(jdbc,demandas);
        fichaDemanda=bancoDemanda.crear(demanda.agente(),sesion(demanda.agente()));
        long propiedad=origenes.propiedad(oferta.agente(),"Colaboración C3");
        encargo=new TransactionTemplate(gestor).execute(t->encargos.abrir(new EncargoService.DatosApertura(
                propiedad,oferta.agente().idRolOperativo(),"C3-"+UUID.randomUUID().toString().substring(0,12),
                OperacionInmobiliaria.ALQUILER,hoy(),hoy().plusMonths(6),false,"Borrador oferta TEST",
                EncargosDePrueba.alquiler(new BigDecimal("2500"),"PEN",BigDecimal.ONE,"Propietario TEST","Agencia TEST"),PROC,List.of()),oferta.agente()).id());
        when(fuente.consultarAutoridadPropia(eq(encargo),any())).thenAnswer(i->{propia(i.getArgument(1));return new FuenteDeNetwork.AutoridadActual(orgOferta(),encargo,oferta.agente().idRolOperativo(),oferta.broker().idRolOperativo(),false,true,Set.of());});
        when(fuente.consultarPropia(eq(encargo),anySet(),any(),any())).thenAnswer(i->{propia(i.getArgument(3));return new FuenteDeNetwork.FuentePropia(orgOferta(),encargo,oferta.agente().idRolOperativo(),oferta.broker().idRolOperativo(),false,0,0,PoliticaProyeccionNetworkV1.VERSION,PoliticaProyeccionNetworkV1.FINALIDAD,OFERTA,HD,HA,true,Set.of());});
        Instant confirmadaTest=Instant.now().minusSeconds(60);
        when(fuente.verificarActuales(anyCollection(),any())).thenAnswer(i->{
            Collection<FuenteDeNetwork.ReferenciaAutorizada> referencias=i.getArgument(0);
            Map<Long,FuenteDeNetwork.VerificacionActual> verificadas=new HashMap<>();
            for(var r:referencias) {
                if(r.organizacion()==orgOferta()&&r.idEncargo()==encargo)
                    verificadas.put(encargo,new FuenteDeNetwork.VerificacionActual(orgOferta(),encargo,oferta.agente().idRolOperativo(),oferta.broker().idRolOperativo(),false,PoliticaProyeccionNetworkV1.VERSION,PoliticaProyeccionNetworkV1.FINALIDAD,HD,HA,true,Set.of(),new ConfirmacionDisponibilidad(confirmadaTest,false,"D2_TEST_EXPLICITO")));
                else verificadas.put(r.idEncargo(),new FuenteDeNetwork.VerificacionActual(r.organizacion(),r.idEncargo(),r.responsable(),r.brokerActual(),r.brokerActual()==null,r.politicaVersion(),r.finalidad(),null,null,false,Set.of("FUERA_DEL_ALCANCE_DE_ESTA_FUENTE_TEST"),null));
            }
            return Map.copyOf(verificadas);
        });
        when(ofertaConcedida.verificarCamposColaboracion(any(),any())).thenAnswer(i->{ReferenciaParteConcedida p=i.getArgument(0);assertEquals(ENCARGO,p.lado());assertEquals(encargo,p.idRecurso());return new FuenteOfertaConcedida.Verificacion(orgOferta(),encargo,oferta.agente().idRolOperativo(),0,Idempotencia.huellaExacta(documentos.objetoCanonico(OFERTA)),true,Set.of());});
        publicarOferta();
        var referencia=network.referenciasParaConsulta(demanda.agente()).stream().filter(r->r.idEncargo()==encargo).findFirst().orElseThrow();
        Instant ahora=Instant.now();alcance=new AlcanceColaboracion(Set.of(DEMANDA,ENCARGO),Set.of("CONSULTAR"),Map.of(
                DEMANDA,Set.of("criterios","canalComercial"),ENCARGO,Set.of("ENCARGO:operacion","canalComercial")),PoliticaProyeccionNetworkV1.FINALIDAD,ahora.minusSeconds(1),ahora.plusSeconds(1800));
        var p=actuaciones.prepararSolicitudColaboracion(demanda.agente(),new ColaboracionService.DatosSolicitud(fichaDemanda.id(),fichaDemanda.version(),referencia.ofertaRef(),referencia.versionProyeccion(),alcance),sesion(demanda.agente()),PROC);
        var resultado=ejecutar(demanda.agente(),p);solicitud=Objects.toString(resultado.resultado().get("solicitud"));
        assertNotEquals("null",solicitud);
    }

    @Test void dosDecisionesRealesSonNecesariasYLaConcesionConservaLosDosSnapshots() {
        declarar(DEMANDA,"Canal comercial DEMANDA TEST");declarar(ENCARGO,"Canal comercial OFERTA TEST");
        assertNull(colaboraciones.consultarAcuerdo(solicitud,demanda.agente()).datosConcedidos());
        decidir(DEMANDA);var parcial=colaboraciones.consultarAcuerdo(solicitud,demanda.agente());assertFalse(parcial.efectiva());assertNull(parcial.datosConcedidos());
        decidir(ENCARGO);var completo=colaboraciones.consultarAcuerdo(solicitud,demanda.agente());
        assertTrue(completo.efectiva());assertNotNull(completo.datosConcedidos());
        assertEquals("Canal comercial OFERTA TEST",completo.datosConcedidos().get(ENCARGO).get("canalComercial"));
        assertEquals(2,cuenta("decision_colaboracion"));assertEquals(1,cuenta("concesion_colaboracion"));assertEquals(2,cuenta("canal_colaboracion"));
        assertThrows(AccesoNoAutorizadoException.class,()->colaboraciones.consultarAcuerdo(solicitud,demanda.colega()));
        var broker=colaboraciones.consultarAcuerdo(solicitud,demanda.broker());assertNull(broker.datosConcedidos());
    }

    @Test void proofPersistidoRevalidaSubsetFisicoSinConvertirPresupuestoPrivadoEnCriterioCompartido() {
        decidir(DEMANDA);decidir(ENCARGO);assertTrue(colaboraciones.consultarAcuerdo(solicitud,oferta.agente()).efectiva());
        fichaDemanda=bancoDemanda.editarPrivado(fichaDemanda,demanda.agente(),sesion(demanda.agente()));
        var privado=colaboraciones.consultarAcuerdo(solicitud,oferta.agente());assertTrue(privado.efectiva());
        String publico=documentos.objetoCanonico(privado.datosConcedidos().get(DEMANDA));assertFalse(publico.contains("9000"));assertFalse(publico.toLowerCase(Locale.ROOT).contains("presupuesto"));
        fichaDemanda=bancoDemanda.editarFisico(fichaDemanda,demanda.agente(),sesion(demanda.agente()));
        var cambiado=colaboraciones.consultarAcuerdo(solicitud,oferta.agente());assertFalse(cambiado.efectiva());assertNull(cambiado.datosConcedidos());assertNotNull(cambiado.concesion());assertEquals(1,cuenta("concesion_colaboracion"));
    }

    @Test void brokerNoDeclaraCanalAjenoYDecisionExistenteNoSeReescribe() {
        var meta=colaboraciones.consultarMetadatos(solicitud,demanda.agente());
        var d=new CanalesColaboracionService.DatosDeclaracion(solicitud,DEMANDA,meta.version(),meta.versionAlcance(),fichaDemanda.version(),"Canal TEST explícito");
        assertThrows(AccesoNoAutorizadoException.class,()->actuaciones.prepararCanalColaboracion(demanda.broker(),d,sesion(demanda.broker()),PROC));
        declarar(DEMANDA,"Canal original TEST");decidir(DEMANDA);
        var inmutable=assertThrows(ConflictoException.class,()->declarar(DEMANDA,"Cambio tras decisión TEST"));
        assertTrue(inmutable.getMessage().contains("El canal aprobado no se modifica"));
        assertEquals(1,cuenta("canal_colaboracion"));assertEquals("Canal original TEST",canales.consultarDeclaracionPropia(solicitud,DEMANDA,demanda.agente()).orElseThrow().canalComercial());
    }

    @Test void dosTransaccionesNoUsanLaMismaVersionObservadaParaDosDecisionesDiferentes() throws Exception {
        var pD=prepararDecision(DEMANDA);var pE=prepararDecision(ENCARGO);
        var rD=actuaciones.revisar(demanda.broker(),pD.referencia(),sesion(demanda.broker()));
        var rE=actuaciones.revisar(oferta.broker(),pE.referencia(),sesion(oferta.broker()));
        var puerta=new CountDownLatch(1);var ejecutor=Executors.newFixedThreadPool(2);
        try {
            var d=ejecutor.submit(()->{puerta.await();return confirmar(demanda.broker(),rD);});
            var e=ejecutor.submit(()->{puerta.await();return confirmar(oferta.broker(),rE);});
            puerta.countDown();int exitos=0;
            for(var f:List.of(d,e)) {
                try {var resultado=f.get(30,TimeUnit.SECONDS);if("EXITO".equals(resultado.estado()))exitos++;else assertEquals("RECHAZADO",resultado.estado());}
                catch(ExecutionException ex){assertInstanceOf(ConflictoException.class,ex.getCause());}
            }
            assertEquals(1,exitos);
        } finally {ejecutor.shutdownNow();}
        assertEquals(1,cuenta("decision_colaboracion"));assertEquals(0,cuenta("concesion_colaboracion"));
        var unica=colaboraciones.consultarAcuerdo(solicitud,demanda.broker()).decisiones().getFirst().parte();
        decidir(unica==DEMANDA?ENCARGO:DEMANDA);
        assertEquals(2,cuenta("decision_colaboracion"));assertEquals(1,cuenta("concesion_colaboracion"));
    }

    @Test void revocacionRealRetiraElEgresoConservaHistoriaYReplayOriginal() {
        decidir(DEMANDA);decidir(ENCARGO);
        var concedida=colaboraciones.consultarAcuerdo(solicitud,oferta.agente());assertTrue(concedida.efectiva());assertNotNull(concedida.datosConcedidos());
        var a=demanda.broker();var m=colaboraciones.consultarMetadatos(solicitud,a);long anterior=m.versionConcesion();
        var datos=new ColaboracionService.DatosRevocacion(m.concesion(),DEMANDA,m.alcanceConcedido(),"Revocación TEST competente",m.versionConcesion(),m.versionAlcance(),m.parteActual().version());
        var propuesta=actuaciones.prepararRevocacionColaboracion(a,datos,sesion(a),PROC);var revision=actuaciones.revisar(a,propuesta.referencia(),sesion(a));
        var confirmacion=new ActuacionesService.Confirmacion(revision.propuesta().version(),revision.propuesta().huella(),revision.pruebaRevision(),"humano-revocacion-test-"+UUID.randomUUID());
        var original=actuaciones.confirmar(a,propuesta.referencia(),sesion(a),confirmacion,PROC);assertEquals("EXITO",original.estado());
        var replay=actuaciones.confirmar(a,propuesta.referencia(),sesion(a),confirmacion,PROC);assertEquals(original,replay);
        var retirada=colaboraciones.consultarAcuerdo(solicitud,oferta.agente());assertFalse(retirada.efectiva());assertNull(retirada.datosConcedidos());assertNotNull(retirada.revocadaEn());
        assertEquals(anterior+1,retirada.versionConcesion());assertEquals(1,cuenta("concesion_colaboracion"));assertEquals(2,cuenta("decision_colaboracion"));
        assertEquals(1,jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and tipo='COLABORACION_REVOCADA'",Long.class,demanda.agente().idOrganizacion()));
    }

    @Test void revocacionConfirmadaDuranteLaEntregaImpideElEgresoAntesDeRetornar() throws Exception {
        decidir(DEMANDA);decidir(ENCARGO);
        var a=demanda.broker();var m=colaboraciones.consultarMetadatos(solicitud,a);
        var datos=new ColaboracionService.DatosRevocacion(m.concesion(),DEMANDA,m.alcanceConcedido(),"Revocación concurrente TEST",m.versionConcesion(),m.versionAlcance(),m.parteActual().version());
        var propuesta=actuaciones.prepararRevocacionColaboracion(a,datos,sesion(a),PROC);
        var sesionEscritor=sesion(a);var revision=actuaciones.revisar(a,propuesta.referencia(),sesionEscritor);
        var confirmacion=new ActuacionesService.Confirmacion(revision.propuesta().version(),revision.propuesta().huella(),revision.pruebaRevision(),"rev-t4-"+UUID.randomUUID());
        var lectorEnSubset=new CountDownLatch(1);var escritorConfirmado=new CountDownLatch(1);var primera=new AtomicBoolean(true);
        // Única barrera: la fuente TEST ya existente. Caso, grant, autorizaciones y revocación siguen reales.
        doAnswer(i->{
            ReferenciaParteConcedida p=i.getArgument(0);Actor receptor=i.getArgument(1);
            assertEquals(ENCARGO,p.lado());assertEquals(encargo,p.idRecurso());
            if(receptor.equals(oferta.agente())&&primera.compareAndSet(true,false)) {
                lectorEnSubset.countDown();
                if(!escritorConfirmado.await(45,TimeUnit.SECONDS))throw new IllegalStateException("El escritor no terminó mientras el lector esperaba en la fuente TEST.");
            }
            return new FuenteOfertaConcedida.Verificacion(orgOferta(),encargo,oferta.agente().idRolOperativo(),0,Idempotencia.huellaExacta(documentos.objetoCanonico(OFERTA)),true,Set.of());
        }).when(ofertaConcedida).verificarCamposColaboracion(any(),any());
        var ejecutor=Executors.newFixedThreadPool(2);
        try {
            var lector=ejecutor.submit(()->colaboraciones.consultarAcuerdo(solicitud,oferta.agente()));
            assertTrue(lectorEnSubset.await(20,TimeUnit.SECONDS),"El lector debe alcanzar el subset concedido antes de revocar.");
            var escritor=ejecutor.submit(()->actuaciones.confirmar(a,propuesta.referencia(),sesionEscritor,confirmacion,PROC));
            // La consulta no posee el lock de escritura del grant: el commit debe terminar antes de liberar al lector.
            var original=escritor.get(25,TimeUnit.SECONDS);assertEquals("EXITO",original.estado());
            escritorConfirmado.countDown();
            var entrega=lector.get(25,TimeUnit.SECONDS);assertFalse(entrega.efectiva());assertNull(entrega.datosConcedidos());
            assertNotNull(entrega.revocadaEn());assertEquals(m.versionConcesion()+1,entrega.versionConcesion());
            var replay=actuaciones.confirmar(a,propuesta.referencia(),sesionEscritor,confirmacion,PROC);assertEquals(original,replay);
            assertEquals(original,actuaciones.consultar(a,propuesta.referencia(),sesionEscritor));
            assertEquals(1,cuenta("concesion_colaboracion"));assertEquals(2,cuenta("decision_colaboracion"));
            assertEquals(1,jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and tipo='COLABORACION_REVOCADA'",Long.class,demanda.agente().idOrganizacion()));
        } finally {
            escritorConfirmado.countDown();ejecutor.shutdownNow();
        }
    }

    @Test void coordinadorL2UsaSoloV1ParaContarFiltrarYExplicarAntesDeLaConcesion() {
        assertNull(colaboraciones.consultarAcuerdo(solicitud,demanda.agente()).concesion());
        var propia=disponibilidad.consultar(DisponibilidadContextualService.TipoRecurso.DEMANDA,fichaDemanda.id(),demanda.agente());
        disponibilidad.confirmar(new DisponibilidadContextualService.DatosConfirmacion(propia.clave().tipo(),fichaDemanda.id(),propia.versionRecurso(),propia.versionConfirmacion(),true,Instant.now().minusSeconds(1),null,"Cliente TEST confirmó su búsqueda","Evidencia D2 demanda TEST"),demanda.agente(),sesion(demanda.agente()),"d2-demanda-test-"+UUID.randomUUID(),PROC);
        var consulta=new CoincidenciaService.Consulta(CoincidenciaService.Origen.DEMANDA,fichaDemanda.id(),fichaDemanda.version(),CoincidenciaService.TipoVersion.DEMANDA,Modalidad.COMERCIAL,Set.of(CoincidenciaService.Capa.L2),Categoria.OBLIGATORIAS_PENDIENTES,20,null);
        var pagina=coincidencias.consultar(consulta,demanda.agente(),sesion(demanda.agente()));
        assertEquals(CoincidenciaService.EstadoResultado.DISPONIBLE,pagina.estado());assertEquals(1,pagina.total());assertEquals(1,pagina.grupos().size());
        var candidato=pagina.grupos().getFirst().candidatos().getFirst();assertEquals(CoincidenciaService.Capa.L2,candidato.capa());
        assertEquals(OFERTA,candidato.datosPermitidos());assertEquals(Conclusion.NO_DETERMINADO,candidato.h());
        assertTrue(candidato.razones().stream().anyMatch(r->r.clave().endsWith("ascensores")&&r.conclusion()==Conclusion.NO_DETERMINADO));
        assertFalse(candidato.datosPermitidos().containsKey("nombreProfesional"));assertFalse(candidato.datosPermitidos().containsKey("canalComercial"));
        assertFalse(candidato.datosPermitidos().containsKey("inmobiliaria"));assertFalse(candidato.datosPermitidos().containsKey("presupuesto"));
        var explicacion=coincidencias.explicar(pagina.referenciaConsulta(),pagina.grupos().getFirst().referenciaDemanda(),candidato.referencia(),demanda.agente(),sesion(demanda.agente()));
        assertEquals(candidato,explicacion);
        var confirmadas=coincidencias.consultar(new CoincidenciaService.Consulta(consulta.origen(),consulta.idOrigen(),consulta.versionOrigen(),consulta.tipoVersion(),consulta.modalidad(),consulta.capas(),Categoria.COMERCIAL_CONFIRMADA,20,null),demanda.agente(),sesion(demanda.agente()));
        assertEquals(0,confirmadas.total());assertTrue(confirmadas.grupos().isEmpty());
        assertNull(colaboraciones.consultarAcuerdo(solicitud,demanda.agente()).concesion());
    }

    private void publicarOferta() {
        Instant ahora=Instant.now();var d=new NetworkService.DatosSolicitud(encargo,NetworkService.Cambio.ACTIVAR,Set.of("ENCARGO:operacion"),PoliticaProyeccionNetworkV1.FINALIDAD,ahora.minusSeconds(1),ahora.plusSeconds(3600),"PublicaciónTEST",0L,HD,HA);
        ejecutar(oferta.agente(),actuaciones.prepararSolicitudNetwork(oferta.agente(),d,sesion(oferta.agente()),PROC));
        var participacion=network.consultar(encargo,oferta.broker());var ref=participacion.solicitudPendiente();var s=network.consultarSolicitud(ref,oferta.broker());
        ejecutar(oferta.broker(),actuaciones.prepararDecisionNetwork(oferta.broker(),new NetworkService.DatosDecision(ref,NetworkService.Decision.APROBAR,"AutoridadTEST",s.version(),participacion.version(),s.huellaDatos(),s.huellaAutoridad()),sesion(oferta.broker()),PROC));
    }
    private void declarar(LadoColaboracion lado,String valor) {
        Actor a=lado==DEMANDA?demanda.agente():oferta.agente();var m=colaboraciones.consultarMetadatos(solicitud,a);
        ejecutar(a,actuaciones.prepararCanalColaboracion(a,new CanalesColaboracionService.DatosDeclaracion(solicitud,lado,m.version(),m.versionAlcance(),m.parteActual().version(),valor),sesion(a),PROC));
    }
    private void decidir(LadoColaboracion lado) {
        ejecutar(lado==DEMANDA?demanda.broker():oferta.broker(),prepararDecision(lado));
    }
    private ActuacionesService.Propuesta prepararDecision(LadoColaboracion lado) {
        Actor a=lado==DEMANDA?demanda.broker():oferta.broker();var m=colaboraciones.consultarMetadatos(solicitud,a);
        return actuaciones.prepararDecisionColaboracion(a,new ColaboracionService.DatosDecision(solicitud,lado,ColaboracionService.Decision.ACEPTAR,alcance,"Decisión TEST",m.version(),m.versionAlcance(),m.parteActual().version()),sesion(a),PROC);
    }
    private ActuacionesService.Resultado confirmar(Actor a,ActuacionesService.Revision r){return actuaciones.confirmar(a,r.propuesta().referencia(),sesion(a),new ActuacionesService.Confirmacion(r.propuesta().version(),r.propuesta().huella(),r.pruebaRevision(),"humano-test-"+UUID.randomUUID()),PROC);}
    private ActuacionesService.Resultado ejecutar(Actor a,ActuacionesService.Propuesta p){var s=sesion(a);var r=actuaciones.revisar(a,p.referencia(),s);var resultado=actuaciones.confirmar(a,p.referencia(),s,new ActuacionesService.Confirmacion(r.propuesta().version(),r.propuesta().huella(),r.pruebaRevision(),"humano-test-"+UUID.randomUUID()),PROC);assertEquals("EXITO",resultado.estado());return resultado;}
    private ContextoSesion sesion(Actor a){return sesiones.de(a);}
    private long orgOferta(){return oferta.agente().idOrganizacion();}
    private void propia(Actor a){if(a.idOrganizacion()!=orgOferta()||a.idRolOperativo()!=oferta.agente().idRolOperativo()&&a.idRolOperativo()!=oferta.broker().idRolOperativo())throw new AccesoNoAutorizadoException();}
    private long cuenta(String tabla){return jdbc.queryForObject("select count(*) from "+tabla+" where id_solicitud_colaboracion=(select id_solicitud_colaboracion from solicitud_colaboracion where organizacion_id=? and referencia=cast(? as uuid))",Long.class,demanda.agente().idOrganizacion(),solicitud);}
    private static LocalDate hoy(){return LocalDate.now(ZoneId.of("America/Lima"));}
}
