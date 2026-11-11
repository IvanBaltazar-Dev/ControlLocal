package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.materialia.RegistroTratamientoMaterialIaRepository;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.documentos.SeleccionInmueblePieza;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.*;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.*;
import com.controllocal.service.materialia.FuentesMaterialIa.*;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** PG/T4 del registro E44. Toda acreditación positiva es SINTÉTICA TEST, sin proveedor/red/gasto ni validez operativa. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=2")
class TratamientoMaterialIaE44IntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext contexto;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired RegistroTratamientoMaterialIaRepository repo;
    FormalizacionesDePrueba b;
    ActividadNegociacionDePrueba.Caso caso;
    AmbitoTratamiento ambito;
    FuenteSintetica fuente;
    EvidenciasTratamientoMaterialIaImpl servicio;
    static final DestinoTratamiento DESTINO=new DestinoTratamiento("PROCESADOR_TEST_SIN_RED","PERFIL_TEST","TEST-1");

    @BeforeEach void preparar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);b=new FormalizacionesDePrueba(contexto);
        caso=b.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",false);
        ambito=ambito(UUID.randomUUID(),Set.of("DATOS_SINTETICOS"));fuente=new FuenteSintetica();servicio=crear(List.of(fuente));
    }
    private EvidenciasTratamientoMaterialIaImpl crear(List<AvisosTratamientoMaterialIa> fuentes){
        return new EvidenciasTratamientoMaterialIaImpl(repo,contexto.getBean(ContextosService.class),contexto.getBean(ContextosDeActividad.class),
                contexto.getBean(UsuarioOrganizacionRepository.class),contexto.getBean(CuentaAccesoRepository.class),
                contexto.getBean(SeleccionInmueblePieza.class),contexto.getBean(AccesoEncargo.class),contexto.getBean(Documentos.class),fuentes);
    }
    private <T>T tx(Supplier<T> trabajo){return new TransactionTemplate(gestor).execute(s->trabajo.get());}
    private AmbitoTratamiento ambito(UUID job,Set<String> categorias){return new AmbitoTratamiento(
            new Solicitud(job,Accion.REDACTAR_FICHA,new Recurso("PROPIEDAD",Long.toString(caso.propiedad())),"ORIGEN_TEST",null,"Nota privada TEST que no se duplica en E44"),
            "a".repeat(64),categorias,"b".repeat(64));}
    private AvisoParaDecision presentar(){return tx(()->servicio.presentar(ambito,caso.agente(),caso.sesion()));}
    private DecisionRegistrada capturar(AvisoParaDecision aviso,Decision d){return decidir(new Captura(UUID.randomUUID(),aviso.referencia(),aviso.versionCobertura(),d));}
    private DecisionRegistrada decidir(Captura d){return tx(()->servicio.capturar(d,caso.agente(),caso.sesion(),Procedencia.deLaPantalla()));}
    private EvidenciaTratamiento resolver(){return tx(()->servicio.resolver(ambito,caso.agente(),caso.sesion()));}
    private DecisionRegistrada aceptar(){return capturar(presentar(),Decision.ACEPTAR);}
    private void exigir(String ref,DestinoTratamiento destino){tx(()->{servicio.exigirVigente(ref,ambito,destino,caso.agente(),caso.sesion());return null;});}
    private int decisiones(){return jdbc.queryForObject("SELECT count(*) FROM material_ia_decision_e44 WHERE organizacion_id=?",Integer.class,caso.agente().idOrganizacion());}

    @Test void sinFuenteRealNoSeAcreditaNiSeAceptaUnAvisoInventado(){
        servicio=crear(List.of());assertEquals(EstadoEvidencia.PENDIENTE,resolver().estado());
        var aviso=presentar();assertEquals("PENDIENTE",aviso.estado());assertNull(aviso.proveedorLegal());
        assertThrows(CapacidadDeshabilitadaException.class,()->capturar(aviso,Decision.ACEPTAR));assertEquals(0,decisiones());
        assertEquals(Decision.RECHAZAR,capturar(aviso,Decision.RECHAZAR).decision());assertEquals(EstadoEvidencia.RECHAZADA,resolver().estado());
    }
    @Test void laAceptacionPropiaNoSustituyeLaLegitimacionDeTerceros(){
        fuente.legitima=false;aceptar();assertEquals(EstadoEvidencia.PENDIENTE,resolver().estado());
        fuente.legitima=true;assertEquals(EstadoEvidencia.ACREDITADA,resolver().estado());assertEquals(1,decisiones());
    }
    @Test void elDestinoRealSeCompruebaConLaDecisionYSusCondiciones(){
        var aviso=presentar();assertEquals(fuente.vigente(ambito,caso.agente()).orElseThrow().condicionesTexto(),aviso.condicionesTexto());
        var decision=capturar(aviso,Decision.ACEPTAR);var evidencia=resolver();assertEquals(EstadoEvidencia.ACREDITADA,evidencia.estado());
        assertEquals(decision.evento().toString(),evidencia.referenciaEvidencia());exigir(evidencia.referenciaEvidencia(),DESTINO);
        assertThrows(ReglaNegocioException.class,()->exigir(evidencia.referenciaEvidencia(),null));
    }
    @ParameterizedTest @ValueSource(strings={"PROVEEDOR","PERFIL","VERSION"})
    void noSeAutorizaOtroDestino(String cambio){
        aceptar();var destino=new DestinoTratamiento(cambio.equals("PROVEEDOR")?"OTRO_TEST":DESTINO.proveedor(),
                cambio.equals("PERFIL")?"OTRO_TEST":DESTINO.perfil(),cambio.equals("VERSION")?"OTRA_TEST":DESTINO.versionPerfil());
        assertThrows(AccesoNoAutorizadoException.class,()->exigir(resolver().referenciaEvidencia(),destino));
    }
    @Test void otroJobCompatibleNoPideOtraAceptacion(){
        aceptar();String ref=resolver().referenciaEvidencia();
        var otra=new AmbitoTratamiento(new Solicitud(UUID.randomUUID(),Accion.REDACTAR_FICHA,ambito.solicitud().recurso(),"ORIGEN_TEST_2",null,null),
                "c".repeat(64),ambito.categorias(),"d".repeat(64));
        assertEquals(ref,tx(()->servicio.resolver(otra,caso.agente(),caso.sesion())).referenciaEvidencia());
        tx(()->{servicio.exigirVigente(ref,otra,DESTINO,caso.agente(),caso.sesion());return null;});assertEquals(1,decisiones());
    }
    @Test void categoriasSinCoberturaNoSePresumenConsentidas(){
        aceptar();var ampliado=ambito(UUID.randomUUID(),Set.of("DATOS_SINTETICOS","CATEGORIA_NO_CUBIERTA_TEST"));
        assertEquals(EstadoEvidencia.PENDIENTE,tx(()->servicio.resolver(ampliado,caso.agente(),caso.sesion())).estado());
    }
    @ParameterizedTest @ValueSource(strings={"0","+"})
    void unaReferenciaAlternaNoFragmentaLaPreferencia(String prefijo){
        aceptar();String ref=resolver().referenciaEvidencia();
        var solicitud=new Solicitud(UUID.randomUUID(),Accion.REDACTAR_FICHA,new Recurso("PROPIEDAD",prefijo+caso.propiedad()),null,null,null);
        var alterno=new AmbitoTratamiento(solicitud,ambito.huellaFuente(),ambito.categorias(),ambito.huellaPayload());
        var evidencia=tx(()->servicio.resolver(alterno,caso.agente(),caso.sesion()));assertEquals(ref,evidencia.referenciaEvidencia());
        var vista=tx(()->servicio.presentar(alterno,caso.agente(),caso.sesion()));
        capturar(vista,Decision.REVOCAR);assertEquals(EstadoEvidencia.REVOCADA,resolver().estado());assertEquals(2,decisiones());
    }
    @Test void unFalloDeLaFuenteNoSeConvierteEnPendiente(){
        aceptar();fuente.fallaTecnica=true;assertThrows(IllegalStateException.class,()->resolver());assertEquals(1,decisiones());
    }
    @Test void revocarCortaNuevasTransferenciasYRevalidacionDeProcesamientoSinBorrarHistoria(){
        aceptar();String ref=resolver().referenciaEvidencia();capturar(presentar(),Decision.REVOCAR);
        assertEquals(EstadoEvidencia.REVOCADA,resolver().estado());assertEquals(2,decisiones());
        assertThrows(AccesoNoAutorizadoException.class,()->exigir(ref,DESTINO));
        assertThrows(AccesoNoAutorizadoException.class,()->tx(()->{servicio.revalidarActual(ref,ambito,caso.agente());return null;}));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM material_ia_decision_e44 WHERE organizacion_id=? AND decision='ACEPTAR'",Integer.class,caso.agente().idOrganizacion()).intValue());
    }
    @Test void noSeInventaUnaAceptacionAnteriorAlRevocar(){
        var aviso=presentar();assertThrows(ReglaNegocioException.class,()->capturar(aviso,Decision.REVOCAR));assertEquals(0,decisiones());
    }
    @Test void idempotenciaDistingueReintentoYReutilizacion(){
        var aviso=presentar();var captura=new Captura(UUID.randomUUID(),aviso.referencia(),aviso.versionCobertura(),Decision.RECHAZAR);
        var primera=decidir(captura);assertEquals(primera,decidir(captura));assertEquals(1,decisiones());
        assertThrows(ConflictoException.class,()->decidir(new Captura(captura.evento(),aviso.referencia(),aviso.versionCobertura(),Decision.ACEPTAR)));
    }
    @Test void otroProfesionalOBrokerNoDecidePorLaCuentaTitular(){
        var aviso=presentar();var captura=new Captura(UUID.randomUUID(),aviso.referencia(),aviso.versionCobertura(),Decision.ACEPTAR);
        for(Actor otro:List.of(caso.equipo().colega(),caso.equipo().broker())){
            var sid=b.sesiones.de(otro);
            assertThrows(AccesoNoAutorizadoException.class,()->tx(()->servicio.capturar(captura,otro,sid,Procedencia.deLaPantalla())));
        }
        assertEquals(0,decisiones());
    }
    @Test void gobiernoYTenantAjenoNoObtienenConsentimientoComercial(){
        aceptar();var gobierno=caso.equipo().admin();var sidGobierno=b.sesiones.de(gobierno);
        assertThrows(AccesoNoAutorizadoException.class,()->tx(()->servicio.resolver(ambito,gobierno,sidGobierno)));
        var ajeno=b.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",false);
        assertThrows(AccesoNoAutorizadoException.class,()->tx(()->servicio.resolver(ambito,ajeno.agente(),ajeno.sesion())));
    }
    @Test void brokerCompetenteConsultaSuPreferenciaSinHeredarLaDelAgente(){
        aceptar();var broker=caso.equipo().broker();var sid=b.sesiones.de(broker);
        assertEquals(EstadoEvidencia.PENDIENTE,tx(()->servicio.resolver(ambito,broker,sid)).estado());
    }
    @Test void unaNuevaSesionRecuperaCoberturaSinUsarElSidHistorico(){
        aceptar();String ref=resolver().referenciaEvidencia();
        var origenes=new OrigenesDePrueba(jdbc,gestor,contexto.getBean(com.controllocal.service.captura.MotorDeCaptura.class),contexto.getBean(ContextosService.class));
        var nueva=origenes.sesion(caso.agente());assertNotEquals(caso.sesion().identidad(),nueva.identidad());
        assertEquals(ref,tx(()->servicio.resolver(ambito,caso.agente(),nueva)).referenciaEvidencia());
        tx(()->{servicio.revalidarActual(ref,ambito,caso.agente());return null;});assertEquals(1,decisiones());
    }
    @Test void sesionRevocadaNoUsaLaAceptacionComoCredencial(){
        aceptar();contexto.getBean(ContextosService.class).cerrar(caso.sesion().referenciaSesion(),caso.sesion().revisionContexto());
        assertThrows(AccesoNoAutorizadoException.class,()->resolver());
    }
    @Test void avisoCambioOLegitimacionRetiradaNoSePermitenPorSnapshot(){
        aceptar();String ref=resolver().referenciaEvidencia();fuente.version++;
        assertEquals(EstadoEvidencia.PENDIENTE,resolver().estado());assertThrows(CapacidadDeshabilitadaException.class,()->exigir(ref,DESTINO));
        fuente.version--;fuente.legitima=false;assertEquals(EstadoEvidencia.PENDIENTE,resolver().estado());
        assertThrows(CapacidadDeshabilitadaException.class,()->tx(()->{servicio.revalidarActual(ref,ambito,caso.agente());return null;}));
    }
    @Test void unaLegitimacionDeOtroRecursoNoCubreEste(){
        fuente.otroRecurso=true;aceptar();assertEquals(EstadoEvidencia.PENDIENTE,resolver().estado());
    }
    private void artefacto(String referencia,AmbitoTratamiento a,Actor actor){
        tx(()->{servicio.revalidarArtefacto(referencia,a,actor);return null;});
    }
    @Test void artefactoUsaCondicionesHistoricasSinAceptarUnProveedorNuevo(){
        String ref=aceptar().evento().toString();fuente.version++;
        assertEquals(EstadoEvidencia.PENDIENTE,resolver().estado());
        assertThrows(CapacidadDeshabilitadaException.class,()->exigir(ref,DESTINO));
        artefacto(ref,ambito,caso.agente());assertEquals(1,decisiones());
        assertEquals("CONDICIONES_TEST_1",fuente.condicionesUsoObservadas);
    }
    @Test void revocarPreferenciaPersonalNoSustituyeLaLegitimacionActualDelArtefacto(){
        String ref=aceptar().evento().toString();capturar(presentar(),Decision.REVOCAR);
        assertThrows(AccesoNoAutorizadoException.class,()->exigir(ref,DESTINO));
        artefacto(ref,ambito,caso.agente());fuente.legitimaUso=false;
        assertThrows(CapacidadDeshabilitadaException.class,()->artefacto(ref,ambito,caso.agente()));assertEquals(2,decisiones());
    }
    @Test void autorizarTransferenciaNoImplementaPorDefectoLaLegitimacionDeUso(){
        String ref=aceptar().evento().toString();var soloTransferencia=new AvisosTratamientoMaterialIa(){
            public Optional<Contenido> vigente(AmbitoTratamiento a,Actor actor){return fuente.vigente(a,actor);}
            public Optional<Legitimacion> legitimacionActual(AmbitoTratamiento a,Contenido aviso,Actor actor){return fuente.legitimacionActual(a,aviso,actor);}
        };
        servicio=crear(List.of(soloTransferencia));
        assertEquals(EstadoEvidencia.ACREDITADA,resolver().estado());
        assertThrows(CapacidadDeshabilitadaException.class,()->artefacto(ref,ambito,caso.agente()));
    }
    @Test void referenciaHistoricaNoAcreditaRechazoCategoriaNiFinalidadAjena(){
        String ref=aceptar().evento().toString();
        var categoria=new AmbitoTratamiento(ambito.solicitud(),ambito.huellaFuente(),Set.of("NO_CONSENTIDA_TEST"),ambito.huellaPayload());
        assertThrows(AccesoNoAutorizadoException.class,()->artefacto(ref,categoria,caso.agente()));
        var s=ambito.solicitud();var otraAccion=new AmbitoTratamiento(new Solicitud(s.operacion(),Accion.MEJORAR_FOTO,s.recurso(),s.versionOrigen(),UUID.randomUUID(),null),
                ambito.huellaFuente(),ambito.categorias(),ambito.huellaPayload());
        assertThrows(AccesoNoAutorizadoException.class,()->artefacto(ref,otraAccion,caso.agente()));
        String rechazo=capturar(presentar(),Decision.RECHAZAR).evento().toString();
        assertThrows(AccesoNoAutorizadoException.class,()->artefacto(rechazo,ambito,caso.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->artefacto(UUID.randomUUID().toString(),ambito,caso.agente()));
    }
    @Test void usoRevalidaBaseActualYNoOcultaUnFalloTecnico(){
        String ref=aceptar().evento().toString();fuente.otroRecurso=true;
        assertThrows(CapacidadDeshabilitadaException.class,()->artefacto(ref,ambito,caso.agente()));
        fuente.otroRecurso=false;fuente.fallaTecnica=true;
        assertThrows(IllegalStateException.class,()->artefacto(ref,ambito,caso.agente()));
    }
    @Test void artefactoOrganizacionalNoExigeQueLaAutoraConservePertenencia(){
        String ref=aceptar().evento().toString();var broker=caso.equipo().broker();var nueva=caso.equipo().colega();
        var captaciones=contexto.getBean(CaptacionService.class);var observada=captaciones.consultarReasignable(caso.encargo(),broker);
        captaciones.reasignar(caso.encargo(),new CaptacionService.DatosReasignacion(nueva.idRolOperativo(),caso.agente().idRolOperativo(),observada.version(),
                "Reasignación TEST antes de la baja de la autora; no transmite su consentimiento ni job privado."),broker,b.sesiones.de(broker),UUID.randomUUID().toString(),Procedencia.deLaPantalla());
        long membresia=jdbc.queryForObject("SELECT id_usuario_organizacion FROM usuario_organizacion WHERE organizacion_id=? AND id_persona=? AND estado='A'",Long.class,
                caso.agente().idOrganizacion(),caso.agente().idPersona());
        long version=jdbc.queryForObject("SELECT version FROM usuario_organizacion WHERE id_usuario_organizacion=?",Long.class,membresia);
        var admin=caso.equipo().admin();contexto.getBean(MembresiasService.class).darBaja(membresia,version,"Baja formal TEST; expediente conservado por organización.",admin,b.sesiones.de(admin));
        artefacto(ref,ambito,nueva);
        assertThrows(AccesoNoAutorizadoException.class,()->artefacto(ref,ambito,caso.agente()));
        var sidNueva=b.sesiones.de(nueva);assertEquals(EstadoEvidencia.PENDIENTE,tx(()->servicio.resolver(ambito,nueva,sidNueva)).estado());
        assertEquals(1,decisiones());
    }
    @Test void artefactoDeOtroTenantNoSeRevelaPorConocerSuReferencia(){
        String ref=aceptar().evento().toString();var otro=b.negociacion.mesa("ALQUILER",new BigDecimal("2500"),"PEN",false);
        var ajeno=new AmbitoTratamiento(new Solicitud(UUID.randomUUID(),Accion.REDACTAR_FICHA,new Recurso("PROPIEDAD",Long.toString(otro.propiedad())),null,null,null),
                ambito.huellaFuente(),ambito.categorias(),ambito.huellaPayload());
        assertThrows(AccesoNoAutorizadoException.class,()->artefacto(ref,ajeno,otro.agente()));
    }
    @Test void rollbackNoDejaUnaDecisionEfectiva(){
        var aviso=presentar();
        assertThrows(IllegalStateException.class,()->tx(()->{servicio.capturar(new Captura(UUID.randomUUID(),aviso.referencia(),aviso.versionCobertura(),Decision.ACEPTAR),
                caso.agente(),caso.sesion(),Procedencia.deLaPantalla());throw new IllegalStateException("Rollback TEST");}));
        assertEquals(0,decisiones());assertEquals(EstadoEvidencia.PENDIENTE,resolver().estado());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void t4DosInstanciasNoDuplicanNiPierdenLaDecision(boolean mismoEvento)throws Exception{
        var aviso=presentar();UUID evento=UUID.randomUUID();var segunda=crear(List.of(fuente));
        var lista=List.of(new Captura(evento,aviso.referencia(),aviso.versionCobertura(),Decision.ACEPTAR),
                new Captura(mismoEvento?evento:UUID.randomUUID(),aviso.referencia(),aviso.versionCobertura(),Decision.ACEPTAR));
        var listas=new CountDownLatch(2);var iniciar=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            List<Future<Object>> futuros=new ArrayList<>();
            for(int i=0;i<2;i++){
                var s=i==0?servicio:segunda;var d=lista.get(i);
                futuros.add(pool.submit(()->{listas.countDown();if(!iniciar.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Barrera TEST");
                    try{return tx(()->s.capturar(d,caso.agente(),caso.sesion(),Procedencia.deLaPantalla()));}catch(ConflictoException ex){return ex;}}));
            }
            assertTrue(listas.await(10,TimeUnit.SECONDS));iniciar.countDown();var resultados=new ArrayList<Object>();
            for(var futuro:futuros)resultados.add(futuro.get(20,TimeUnit.SECONDS));
            assertEquals(mismoEvento?2L:1L,resultados.stream().filter(DecisionRegistrada.class::isInstance).count());
            assertEquals(mismoEvento?0L:1L,resultados.stream().filter(ConflictoException.class::isInstance).count());
            assertEquals(1,decisiones());assertEquals(EstadoEvidencia.ACREDITADA,resolver().estado());
        }
    }
    @Test void sqlImpideModificarOBorrarAntecedentesYNoDuplicaTextoLibre(){
        aceptar();
        assertThrows(DataAccessException.class,()->jdbc.update("DELETE FROM material_ia_decision_e44 WHERE organizacion_id=?",caso.agente().idOrganizacion()));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE material_ia_presentacion_e44 SET ambito='{}' WHERE organizacion_id=?",caso.agente().idOrganizacion()));
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE material_ia_aviso_e44 SET contenido='{}' WHERE referencia_servidor=?",fuente.referencia));
        assertEquals(1,decisiones());assertEquals(EstadoEvidencia.ACREDITADA,resolver().estado());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM material_ia_presentacion_e44 WHERE organizacion_id=? AND ambito LIKE '%Nota privada TEST%'",Integer.class,caso.agente().idOrganizacion()).intValue());
    }
    private final class FuenteSintetica implements AvisosTratamientoMaterialIa {
        final String referencia="AVISO_SINTETICO_SIN_VALIDEZ_"+UUID.randomUUID();
        volatile long version=1;volatile boolean legitima=true;volatile boolean legitimaUso=true;
        volatile boolean otroRecurso;volatile boolean fallaTecnica;volatile String condicionesUsoObservadas;
        public Optional<Contenido> vigente(AmbitoTratamiento a,Actor actor){
            if(fallaTecnica)throw new IllegalStateException("Fallo de fuente SINTÉTICA TEST");
            String condiciones="Condiciones SINTÉTICAS TEST. Sin proveedor, transferencia o validez operativa reales.";
            return Optional.of(new Contenido(referencia,version,"Aviso SINTÉTICO TEST, sin validez operativa.",Set.of(Accion.REDACTAR_FICHA),
                    Set.of("DATOS_SINTETICOS"),"DESTINATARIO_LEGAL_SINTETICO", "CONDICIONES_TEST_"+version,condiciones,
                    "FUENTE_CONDICIONES_SINTETICA",Idempotencia.huellaExacta(condiciones),Set.of(DESTINO)));
        }
        public Optional<Legitimacion> legitimacionActual(AmbitoTratamiento a,Contenido aviso,Actor actor){
            if(!legitima)return Optional.empty();
            return Optional.of(new Legitimacion("BASE_SINTETICA_TEST","ACREDITADOR_SINTETICO_SIN_VALIDEZ","e".repeat(64),
                    otroRecurso?new Recurso("PROPIEDAD",Long.toString(caso.propiedad()+1)):a.solicitud().recurso(),Set.of(Accion.REDACTAR_FICHA),
                    Set.of("DATOS_SINTETICOS"),aviso.proveedorLegal(),aviso.condicionesVersion()));
        }
        public Optional<Legitimacion> legitimacionUsoArtefacto(AmbitoTratamiento a,Contenido historico,Actor actual){
            if(fallaTecnica)throw new IllegalStateException("Fallo de fuente de uso SINTÉTICA TEST");
            condicionesUsoObservadas=historico.condicionesVersion();if(!legitimaUso)return Optional.empty();
            return Optional.of(new Legitimacion("USO_ARTEFACTO_SINTETICO_TEST","ACREDITADOR_USO_SIN_VALIDEZ","f".repeat(64),
                    otroRecurso?new Recurso("PROPIEDAD",Long.toString(caso.propiedad()+1)):a.solicitud().recurso(),Set.of(Accion.REDACTAR_FICHA),
                    Set.of("DATOS_SINTETICOS"),historico.proveedorLegal(),historico.condicionesVersion()));
        }
    }
}
