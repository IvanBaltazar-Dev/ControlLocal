package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.integracion.soporte.ActividadNegociacionDePrueba.Caso;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.*;
import com.controllocal.service.OfertaService.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.*;
import com.controllocal.service.actuacion.ComandosAutomaticosService;
import com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico;
import com.controllocal.service.actuacion.EfectosNegociacionDeActuacion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.demanda.InteresadosDeDemanda.*;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.ContratoMatching.*;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/**
 * C4-17–20/RF040–050 sobre Core y PostgreSQL reales. No simula servicios ni
 * acredita navegador/HTTP. Actuaciones revisa y confirma; AUTO conserva sus recibos.
 * Cada prueba dispone de una organización TEST nueva con pertenencias y SID actuales.
 * No aplica migraciones ni modifica reglas o hechos de cierre/dinero C5.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class ActividadNegociacionIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.archivo.AccesoArchivoService archivosFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.web.almacen.AlmacenDocumentos almacenFirmaTest;

    @BeforeEach void evidenciarCaso(TestInfo info){EvidenciaC4DePrueba.iniciar(info);}
    @AfterEach void cerrarEvidencia(){EvidenciaC4DePrueba.limpiar();}
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired EncargoService encargos;
    @Autowired PropietarioService propietarios;
    @Autowired ClienteService clientes;
    @Autowired OportunidadService oportunidades;
    @Autowired OfertaService ofertas;
    @Autowired VisitaService visitas;
    @Autowired ActuacionesService actuaciones;
    @Autowired ComandosAutomaticosService automaticos;
    @Autowired Documentos documentos;
    @Autowired ContextosService contextos;
    @Autowired CuentaService cuentas;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @Autowired MotorDeCaptura captura;
    @Autowired RequerimientoService demandas;
    @Autowired CoincidenciaService coincidencias;
    @Autowired DisponibilidadContextualService disponibilidad;
    private SesionesComercialesDePrueba sesiones;
    private ActividadNegociacionDePrueba banco;
    private synchronized ActividadNegociacionDePrueba banco(){
        if(banco==null){
            sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
            banco=new ActividadNegociacionDePrueba(jdbc,propiedades,encargos,propietarios,clientes,
                    oportunidades,ofertas,actuaciones,documentos,new OrigenesDePrueba(jdbc,gestor,captura,contextos),sesiones,gestor, archivosFirmaTest, almacenFirmaTest);
        }
        return banco;
    }
    private Caso mesa(){return banco().mesa("VENTA",new BigDecimal("400000"),"USD",true);}
    private ActuacionesHumanasC4DePrueba humanas(){return new ActuacionesHumanasC4DePrueba(actuaciones,documentos);}

    @Test void nuevaManifestacionDelMismoLadoSuperaVersionAtomicaYNoReviveLaAnterior(){
        var c=mesa();var v1=banco().presentar(c,new BigDecimal("95000"));
        var v2=banco().contraofertar(c,v1,new BigDecimal("98000"),"P");
        var propuesta=banco().registro(c,new BigDecimal("95000"),"P",v2.id(),v2.version());
        var v3=banco().presentar(c,propuesta,c.agente());
        assertEquals("P",v2.presentadaPor());assertEquals("P",v3.presentadaPor());
        assertEquals(v2.id(),v3.ofertaAnterior());assertEquals(3,v3.ronda());
        assertNotEquals(v1.id(),v3.id());assertEquals(0,v1.monto().compareTo(v3.monto()));
        assertEquals(List.of("S","S","P"),ofertas.listar(c.oportunidad(),c.agente()).stream()
                .sorted(Comparator.comparing(FichaOferta::ronda)).map(FichaOferta::estado).toList());
        assertThrows(ConflictoException.class,()->banco().aceptar(c,v2,"Intento sobre versión superada TEST"));
        assertEquals(3,ofertas.capacidades(c.oportunidad(),c.agente()).versionMesa());
        assertEquals(1,contar("select count(*) from oferta where organizacion_id=? and id_oportunidad=? and estado='P'",c.agente().idOrganizacion(),c.oportunidad()));
        assertTrue(ofertas.consultarAcuerdoAceptado(c.oportunidad(),null,c.agente()).isEmpty());
    }

    @Test void confirmacionesConcurrentesDeLaMismaVersionTienenUnSoloEfectoYRecibosDurables() throws Exception {
        var c=mesa();var d1=banco().registro(c,new BigDecimal("350000"),"I",null,null);
        var d2=banco().registro(c,new BigDecimal("360000"),"I",null,null);
        assertEquals(d1.versionMesa(),d2.versionMesa());assertEquals(d1.versionOportunidad(),d2.versionOportunidad());
        var p1=actuaciones.prepararRegistroOferta(c.agente(),d1,c.sesion(),Procedencia.deLaPantalla());
        var p2=actuaciones.prepararRegistroOferta(c.agente(),d2,c.sesion(),Procedencia.deLaPantalla());
        EvidenciaC4DePrueba.accion("OFERTA_COMPETIDOR_1",c.agente(),"MESA_NEGOCIACION",c.oportunidad(),d1.versionMesa());
        EvidenciaC4DePrueba.accion("OFERTA_COMPETIDOR_2",c.agente(),"MESA_NEGOCIACION",c.oportunidad(),d2.versionMesa());
        var r1=humanas().revisar(p1,c.agente(),c.sesion());var r2=humanas().revisar(p2,c.agente(),c.sesion());
        var carrera=confirmarConcurrentemente(c,p1,r1,p2,r2);
        var resultados=carrera.resultados();var transacciones=carrera.transacciones();
        EvidenciaC4DePrueba.estado("CONFIRMAR_OFERTA_1",c.agente(),resultados.get(0).estado());
        EvidenciaC4DePrueba.estado("CONFIRMAR_OFERTA_2",c.agente(),resultados.get(1).estado());
        assertEquals(Set.of("EXITO","RECHAZADO"),new HashSet<>(resultados.stream().map(Resultado::estado).toList()));
        assertEquals("CONTENIDO_CAMBIADO",resultados.stream().filter(r->"RECHAZADO".equals(r.estado())).findFirst().orElseThrow().motivo());
        assertEquals(2,transacciones.stream().map(TxObservada::hilo).distinct().count());
        assertTrue(transacciones.stream().map(TxObservada::transaccion).distinct().count()>=2,"TX reales distintas, no dos llamadas dentro de una TX externa");
        assertTrue(transacciones.stream().map(TxObservada::conexion).distinct().count()>=2,"Conexiones PostgreSQL de ambos competidores");
        assertEquals(1,ofertas.listar(c.oportunidad(),c.agente()).size());assertEquals(1,ofertas.capacidades(c.oportunidad(),c.agente()).versionMesa());
        assertEquals(1,contar("select count(*) from precio_propiedad where organizacion_id=? and id_captacion=? and hito='O'",c.agente().idOrganizacion(),c.encargo()));
        assertEquals(1,contar("select count(*) from evento_dominio where organizacion_id=? and tipo='NEGOCIACION_REGISTRADA'",c.agente().idOrganizacion()));
        assertEquals(resultados.get(0),actuaciones.consultar(c.agente(),p1.referencia(),c.sesion()));
        assertEquals(resultados.get(1),actuaciones.consultar(c.agente(),p2.referencia(),c.sesion()));
    }

    @Test void dosAceptacionesConcurrentesDeLaMismaPresentadaConservanUnAcuerdoYUnSoloHecho() throws Exception {
        var c=mesa();var presentada=banco().presentar(c,new BigDecimal("350000"));
        long versionOportunidad=oportunidades.obtener(c.oportunidad(),c.agente()).version();
        long versionMesa=ofertas.capacidades(c.oportunidad(),c.agente()).versionMesa();
        long versionEncargo=encargos.consultar(c.encargo(),c.agente()).version();
        var d1=new DatosAceptacion(c.oportunidad(),versionOportunidad,versionMesa,versionEncargo,
                presentada.id(),presentada.version(),banco().parte(c,"P"),hoy(),"Aceptación expresa TEST A","Confirmación A del mismo propietario");
        var d2=new DatosAceptacion(c.oportunidad(),versionOportunidad,versionMesa,versionEncargo,
                presentada.id(),presentada.version(),banco().parte(c,"P"),hoy(),"Aceptación expresa TEST B","Confirmación B del mismo propietario");
        var p1=actuaciones.prepararAceptacionOferta(c.agente(),d1,c.sesion(),Procedencia.deLaPantalla());
        var p2=actuaciones.prepararAceptacionOferta(c.agente(),d2,c.sesion(),Procedencia.deLaPantalla());
        assertNotEquals(p1.referencia(),p2.referencia(),"Dos propuestas y claves técnicas propias, no un replay");
        var r1=humanas().revisar(p1,c.agente(),c.sesion());var r2=humanas().revisar(p2,c.agente(),c.sesion());
        long eventosAntes=contar("select count(*) from evento_dominio where organizacion_id=? and tipo='NEGOCIACION_REGISTRADA'",c.agente().idOrganizacion());
        var carrera=confirmarConcurrentemente(c,p1,r1,p2,r2);var resultados=carrera.resultados();
        EvidenciaC4DePrueba.estado("CONFIRMAR_ACEPTACION_1",c.agente(),resultados.get(0).estado());
        EvidenciaC4DePrueba.estado("CONFIRMAR_ACEPTACION_2",c.agente(),resultados.get(1).estado());
        assertEquals(Set.of("EXITO","RECHAZADO"),new HashSet<>(resultados.stream().map(Resultado::estado).toList()));
        assertEquals("CONTENIDO_CAMBIADO",resultados.stream().filter(r->"RECHAZADO".equals(r.estado())).findFirst().orElseThrow().motivo());
        assertEquals(2,carrera.transacciones().stream().map(TxObservada::hilo).distinct().count());
        assertTrue(carrera.transacciones().stream().map(TxObservada::conexion).distinct().count()>=2);
        assertTrue(carrera.transacciones().stream().map(TxObservada::transaccion).distinct().count()>=2);
        assertEquals(versionMesa+1,ofertas.capacidades(c.oportunidad(),c.agente()).versionMesa());
        assertEquals(1,contar("select count(*) from oferta where organizacion_id=? and id_oportunidad=? and estado='A'",c.agente().idOrganizacion(),c.oportunidad()));
        assertEquals(0,contar("select count(*) from oferta where organizacion_id=? and id_oportunidad=? and estado='P'",c.agente().idOrganizacion(),c.oportunidad()));
        assertEquals(1,contar("select count(*) from acuerdo_aceptado where organizacion_id=? and id_oferta=?",c.agente().idOrganizacion(),presentada.id()));
        var acuerdo=ofertas.consultarAcuerdoAceptado(c.oportunidad(),null,c.agente()).orElseThrow();
        assertEquals(presentada.id().longValue(),acuerdo.oferta());assertEquals(presentada.version(),acuerdo.versionOferta());
        assertEquals(eventosAntes+1,contar("select count(*) from evento_dominio where organizacion_id=? and tipo='NEGOCIACION_REGISTRADA'",c.agente().idOrganizacion()));
        assertEquals(resultados.get(0),actuaciones.consultar(c.agente(),p1.referencia(),c.sesion()));
        assertEquals(resultados.get(1),actuaciones.consultar(c.agente(),p2.referencia(),c.sesion()));
        assertEquals(1,contar("select count(*) from precio_propiedad where organizacion_id=? and id_captacion=? and hito='O'",c.agente().idOrganizacion(),c.encargo()));
    }
    private record TxObservada(String hilo,long conexion,long transaccion) { }
    private record Carrera(List<Resultado> resultados,List<TxObservada> transacciones) { }
    /** Montaje compartido por las dos carreras reales: no introduce otra puerta de negocio. */
    private Carrera confirmarConcurrentemente(Caso c,Propuesta p1,Confirmacion r1,Propuesta p2,Confirmacion r2) throws Exception {
        String etiqueta="C4-T4-"+UUID.randomUUID();
        var transacciones=new ConcurrentLinkedQueue<TxObservada>();
        var contador=new AtomicInteger();var inicio=new CountDownLatch(1);var transaccionesIniciadas=new CountDownLatch(2);
        var hilos=Executors.newFixedThreadPool(2,r->new Thread(r,etiqueta+"-"+contador.incrementAndGet()));
        var configurable=(ConfigurableTransactionManager)gestor;
        var anteriores=new ArrayList<>(configurable.getTransactionExecutionListeners());
        var durante=new ArrayList<>(anteriores);durante.add(new TransactionExecutionListener(){
            @Override public void afterBegin(TransactionExecution t,Throwable error){
                if(error==null&&Thread.currentThread().getName().startsWith(etiqueta)){
                    var fila=jdbc.queryForMap("select pg_backend_pid() pid, txid_current() txid");
                    transacciones.add(new TxObservada(Thread.currentThread().getName(),((Number)fila.get("pid")).longValue(),((Number)fila.get("txid")).longValue()));
                    transaccionesIniciadas.countDown();
                    try {assertTrue(transaccionesIniciadas.await(10,TimeUnit.SECONDS),"Ambas TX comienzan antes de competir por Cuenta/mesa");}
                    catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError("Interrumpida la barrera de TX TEST",e);}
                }
            }
        });
        configurable.setTransactionExecutionListeners(durante);
        try {
            var a=hilos.submit(()->EvidenciaC4DePrueba.enHilo(()->{inicio.await();return actuaciones.confirmar(c.agente(),p1.referencia(),c.sesion(),r1,Procedencia.deLaPantalla());}));
            var b=hilos.submit(()->EvidenciaC4DePrueba.enHilo(()->{inicio.await();return actuaciones.confirmar(c.agente(),p2.referencia(),c.sesion(),r2,Procedencia.deLaPantalla());}));
            inicio.countDown();var resultados=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            transacciones.forEach(t->EvidenciaC4DePrueba.transaccion(t.hilo().endsWith("-1")?"COMPETIDOR_1":"COMPETIDOR_2",t.conexion(),t.transaccion()));
            return new Carrera(resultados,List.copyOf(transacciones));
        } finally {
            inicio.countDown();hilos.shutdownNow();
            try {assertTrue(hilos.awaitTermination(10,TimeUnit.SECONDS));}
            finally {configurable.setTransactionExecutionListeners(anteriores);}
        }
    }

    @Test void falloAntesDelCommitRevierteSuperadaNuevaPresentadaHitoYReciboSinReejecutar(){
        var c=mesa();var primera=banco().presentar(c,new BigDecimal("350000"));
        var datos=banco().registro(c,new BigDecimal("360000"),"I",primera.id(),primera.version());
        var propuesta=actuaciones.prepararRegistroOferta(c.agente(),datos,c.sesion(),Procedencia.deLaPantalla());
        var revision=humanas().revisar(propuesta,c.agente(),c.sesion());
        Thread hilo=Thread.currentThread();var inyectado=new AtomicBoolean();
        var configurable=(ConfigurableTransactionManager)gestor;
        var anteriores=new ArrayList<>(configurable.getTransactionExecutionListeners());
        var durante=new ArrayList<>(anteriores);
        durante.add(new TransactionExecutionListener(){
            @Override public void beforeCommit(TransactionExecution t){
                if(Thread.currentThread()==hilo&&!inyectado.get()
                        &&contar("select count(*) from oferta where organizacion_id=? and id_oportunidad=? and ronda=2",
                            c.agente().idOrganizacion(),c.oportunidad())==1){
                    // El swap y su hito ya están escritos dentro de la TX real, todavía sin commit.
                    assertEquals("S",jdbc.queryForObject("select estado from oferta where id_oferta=?",String.class,primera.id()));
                    assertEquals(2,contar("select count(*) from precio_propiedad where organizacion_id=? and id_captacion=? and hito='O'",
                            c.agente().idOrganizacion(),c.encargo()));
                    inyectado.set(true);throw new IllegalStateException("Fallo técnico TEST antes del commit C4");
                }
            }
        });
        configurable.setTransactionExecutionListeners(durante);
        Resultado incierto;
        try {incierto=actuaciones.confirmar(c.agente(),propuesta.referencia(),c.sesion(),revision,Procedencia.deLaPantalla());}
        finally {configurable.setTransactionExecutionListeners(anteriores);}
        EvidenciaC4DePrueba.estado("CONFIRMAR_OFERTA_CON_FALLO",c.agente(),incierto.estado());
        assertTrue(inyectado.get(),"El sabotaje debe alcanzar el efecto real, no fallar antes por el montaje");
        assertEquals("EN_VERIFICACION",incierto.estado());
        assertEquals(primera,banco().ficha(c,primera.id()));
        assertEquals(1,ofertas.listar(c.oportunidad(),c.agente()).size());
        assertEquals(1,ofertas.capacidades(c.oportunidad(),c.agente()).versionMesa());
        assertEquals(1,contar("select count(*) from precio_propiedad where organizacion_id=? and id_captacion=? and hito='O'",c.agente().idOrganizacion(),c.encargo()));
        assertEquals(1,contar("select count(*) from evento_dominio where organizacion_id=? and tipo='NEGOCIACION_REGISTRADA'",c.agente().idOrganizacion()));
        assertTrue(ofertas.consultarResultado(EfectosNegociacionDeActuacion.OFERTA,"actuacion:"+propuesta.referencia(),c.agente()).isEmpty());
        assertEquals(incierto,actuaciones.confirmar(c.agente(),propuesta.referencia(),c.sesion(),revision,Procedencia.deLaPantalla()));
        assertEquals(1,ofertas.listar(c.oportunidad(),c.agente()).size(),"La incertidumbre no autoriza una segunda ejecución");
    }

    @Test void dosMesasDelMismoEncargoNoMezclanInteresadosOfertasNiVersiones(){
        var a=mesa();var b=banco().otraMesa(a);
        assertEquals(a.encargo(),b.encargo());assertNotEquals(a.personaInteresada(),b.personaInteresada());
        var ofertaA=banco().presentar(a,new BigDecimal("350000"));var ofertaB=banco().presentar(b,new BigDecimal("360000"));
        assertEquals(1,ofertas.capacidades(a.oportunidad(),a.agente()).versionMesa());
        assertEquals(1,ofertas.capacidades(b.oportunidad(),b.agente()).versionMesa());
        var ajena=new DatosAceptacion(a.oportunidad(),oportunidades.obtener(a.oportunidad(),a.agente()).version(),1L,
                encargos.consultar(a.encargo(),a.agente()).version(),ofertaB.id(),ofertaB.version(),banco().parte(a,"P"),hoy(),"TEST cruce de mesa",null);
        assertThrows(NoEncontradoException.class,()->actuaciones.prepararAceptacionOferta(a.agente(),ajena,a.sesion(),Procedencia.deLaPantalla()));
        assertEquals(List.of(ofertaA.id()),ofertas.listar(a.oportunidad(),a.agente()).stream().map(FichaOferta::id).toList());
        assertEquals(List.of(ofertaB.id()),ofertas.listar(b.oportunidad(),b.agente()).stream().map(FichaOferta::id).toList());
        assertThrows(AccesoNoAutorizadoException.class,()->ofertas.listar(a.oportunidad(),a.equipo().colega()));
        assertThrows(AccesoNoAutorizadoException.class,()->ofertas.listar(a.oportunidad(),a.equipo().admin()));
        var otroEquipo=new OrigenesDePrueba(jdbc,gestor,captura,contextos).empresa();
        EvidenciaC4DePrueba.accion("CONSULTAR_MESA_AJENA",otroEquipo.agente(),"OPORTUNIDAD",a.oportunidad(),null);
        assertThrows(NoEncontradoException.class,()->ofertas.listar(a.oportunidad(),otroEquipo.agente()));
    }

    @Test void precioAceptadoNoModificaUCondicionesNiLaEvaluacionL0(){
        var c=mesa();long cliente=banco().cliente(c.agente());
        var referencia=new Referencia(TipoReferencia.CLIENTE,cliente,null,null,null);
        var datos=new RequerimientoService.DatosRequerimiento(null,null,null,null,null,null,"VENTA",List.of("LOCAL_COMERCIAL"),
                null,new BigDecimal("375000"),"USD",null,null,null,null,"Presupuesto propio TEST",List.of("Miraflores"),List.of(),
                referencia,null,hoy(),"PRECIO_VENTA",null,Set.of());
        var demanda=demandas.crear(datos,c.agente(),c.sesion(),clave(),Procedencia.deLaPantalla());
        confirmarDisponibilidad(c,DisponibilidadContextualService.TipoRecurso.ENCARGO,c.encargo(),encargos.consultar(c.encargo(),c.agente()).version());
        confirmarDisponibilidad(c,DisponibilidadContextualService.TipoRecurso.DEMANDA,demanda.id(),demanda.version());
        var consulta=new CoincidenciaService.Consulta(CoincidenciaService.Origen.DEMANDA,demanda.id(),demanda.version(),CoincidenciaService.TipoVersion.DEMANDA,
                Modalidad.COMERCIAL,Set.of(CoincidenciaService.Capa.L0),Categoria.INCOMPATIBLE,20,null);
        var antes=unica(coincidencias.consultar(consulta,c.agente(),c.sesion()));
        assertEquals(Conclusion.INCUMPLE,antes.h());assertEquals(Conclusion.CUMPLE,antes.g());
        var condiciones=encargos.consultar(c.encargo(),c.agente()).condiciones();
        var propuesta=banco().presentar(c,new BigDecimal("350000"));banco().aceptar(c,propuesta,"Aceptación TEST, sin cierre contractual");
        var despues=unica(coincidencias.consultar(consulta,c.agente(),c.sesion()));
        assertEquals(antes.referencia(),despues.referencia());assertEquals(antes.categoria(),despues.categoria());
        assertEquals(antes.h(),despues.h());assertEquals(antes.g(),despues.g());assertEquals(antes.s(),despues.s());
        assertEquals(antes.u(),despues.u());assertEquals(antes.w(),despues.w());assertEquals(antes.datosPermitidos(),despues.datosPermitidos());
        assertEquals(condiciones,encargos.consultar(c.encargo(),c.agente()).condiciones());
        assertEquals(0,new BigDecimal("400000").compareTo(jdbc.queryForObject("select monto from precio_propiedad where organizacion_id=? and id_captacion=? and hito='U'",BigDecimal.class,c.agente().idOrganizacion(),c.encargo())));
        var acuerdo=ofertas.consultarAcuerdoAceptado(c.oportunidad(),null,c.agente()).orElseThrow();
        assertEquals(0,new BigDecimal("350000").compareTo(acuerdo.propuesta().importe().importe()));assertEquals("USD",acuerdo.propuesta().importe().moneda());
        assertEquals("A",oportunidades.obtener(c.oportunidad(),c.agente()).estado());
        assertEquals(0,contar("select count(*) from contrato_compraventa where organizacion_id=? and id_captacion=?",c.agente().idOrganizacion(),c.encargo()));
    }

    @Test void replayDevuelveLaVersionOriginalSinRecrearElHitoDespuesDeSerSuperada(){
        var c=mesa();var d=banco().registro(c,new BigDecimal("350000"),"I",null,null);
        var p=actuaciones.prepararRegistroOferta(c.agente(),d,c.sesion(),Procedencia.deLaPantalla());
        var original=humanas().confirmar(p,c.agente(),c.sesion(),ResultadoNegociacion.class);
        var primera=banco().ficha(c,original.datos().oferta());
        var reemplazo=banco().registro(c,new BigDecimal("360000"),"I",primera.id(),primera.version());
        banco().presentar(c,reemplazo,c.agente());
        var replay=actuaciones.confirmar(c.agente(),p.referencia(),c.sesion(),original.confirmacion(),Procedencia.deLaPantalla());
        assertEquals(original.resultado(),replay);assertEquals("P",original.datos().estado());
        assertEquals("S",banco().ficha(c,primera.id()).estado());
        assertEquals(2,contar("select count(*) from precio_propiedad where organizacion_id=? and id_captacion=? and hito='O'",c.agente().idOrganizacion(),c.encargo()));
        assertEquals(2,ofertas.capacidades(c.oportunidad(),c.agente()).versionMesa());
        var mala=new Confirmacion(original.confirmacion().version(),"otra-huella",original.confirmacion().pruebaRevision(),original.confirmacion().eventoHumano());
        assertThrows(ConflictoException.class,()->actuaciones.confirmar(c.agente(),p.referencia(),c.sesion(),mala,Procedencia.deLaPantalla()));
        assertEquals(2,ofertas.listar(c.oportunidad(),c.agente()).size());
    }

    @Test void visitaConservaIdentidadOriginalCorreccionYReciboSinCrearReunionNiCambiarNegocio(){
        var c=mesa();long interacciones=contar("select count(*) from interaccion_comercial where organizacion_id=?",c.agente().idOrganizacion());
        var entrada=new VisitaService.DatosVisita(c.oportunidad(),oportunidades.obtener(c.oportunidad(),c.agente()).version(),hoy(),LocalTime.of(10,0),
                "Agenda explícita TEST",List.of(new VisitaService.ParticipanteConocido("Comprador TEST","Participante declarado TEST")),"Local comercial TEST C4");
        var programada=automaticos.programarVisita(entrada,c.agente(),c.sesion(),correlacion(),Procedencia.deLaPantalla()).resultado();
        EvidenciaC4DePrueba.resultado("PROGRAMAR_VISITA",c.agente(),programada);
        var datos=new VisitaService.DesenlaceVisita(programada.version(),VisitaService.Ocurrencia.REALIZADA,hoy(),
                "Solicita revisar las condiciones",null,3,null,null,null,null);
        var correlacionResultado=correlacion();
        var recibo=automaticos.registrarResultadoVisita(programada.id(),datos,c.agente(),c.sesion(),correlacionResultado,Procedencia.deLaPantalla());
        var realizada=recibo.resultado();
        EvidenciaC4DePrueba.resultado("REGISTRAR_RESULTADO_VISITA",c.agente(),realizada);
        var correccion=new VisitaService.DesenlaceVisita(realizada.version(),VisitaService.Ocurrencia.NO_REALIZADA,hoy(),
                "El participante aclaró que no asistió",null,null,null,null,null,"Rectificación explícita documentada TEST");
        var rectificada=automaticos.registrarResultadoVisita(programada.id(),correccion,c.agente(),c.sesion(),correlacion(),Procedencia.deLaPantalla()).resultado();
        EvidenciaC4DePrueba.resultado("RECTIFICAR_VISITA",c.agente(),rectificada);
        assertEquals(programada.id(),rectificada.id());assertEquals("N",rectificada.estado());
        assertEquals(realizada,visitas.obtenerVersion(realizada.id(),realizada.version(),c.agente()));
        assertEquals(recibo,automaticos.registrarResultadoVisita(programada.id(),datos,c.agente(),c.sesion(),correlacionResultado,Procedencia.deLaPantalla()));
        assertEquals(3,visitas.historia(programada.id(),c.agente()).size());
        assertEquals("A",oportunidades.obtener(c.oportunidad(),c.agente()).estado());
        assertEquals(0,ofertas.listar(c.oportunidad(),c.agente()).size());
        assertEquals(interacciones,contar("select count(*) from interaccion_comercial where organizacion_id=?",c.agente().idOrganizacion()));
        assertEquals(3,contar("select count(*) from evento_dominio where organizacion_id=? and entidad_tipo='VISITA' and entidad_id=?",c.agente().idOrganizacion(),programada.id()));
    }

    @Test void cancelarAgendaTieneUnaRevisionYUnSoloHechoSinAfirmarOcurrencia(){
        var c=mesa();
        var programada=automaticos.programarVisita(new VisitaService.DatosVisita(c.oportunidad(),oportunidades.obtener(c.oportunidad(),c.agente()).version(),
                hoy().plusDays(1),LocalTime.of(11,0),"Agenda TEST",List.of(),"Local TEST"),c.agente(),c.sesion(),correlacion(),Procedencia.deLaPantalla()).resultado();
        var cancelacion=new VisitaService.Cancelacion(programada.id(),programada.version(),"Cancelación expresa TEST",hoy());
        EvidenciaC4DePrueba.resultado("PROGRAMAR_VISITA",c.agente(),programada);
        var propuesta=actuaciones.prepararCancelacionVisita(c.agente(),cancelacion,c.sesion(),Procedencia.deLaPantalla());
        var confirmada=humanas().confirmar(propuesta,c.agente(),c.sesion(),VisitaService.FichaVisita.class);
        assertEquals("C",confirmada.datos().estado());assertNull(confirmada.datos().resultado());
        assertEquals("A",oportunidades.obtener(c.oportunidad(),c.agente()).estado());
        assertEquals(2,contar("select count(*) from evento_dominio where organizacion_id=? and entidad_tipo='VISITA' and entidad_id=?",c.agente().idOrganizacion(),programada.id()));
        assertEquals(confirmada.resultado(),actuaciones.confirmar(c.agente(),propuesta.referencia(),c.sesion(),confirmada.confirmacion(),Procedencia.deLaPantalla()));
        assertEquals(2,visitas.historia(programada.id(),c.agente()).size());
    }

    private void confirmarDisponibilidad(Caso c,DisponibilidadContextualService.TipoRecurso tipo,long id,long version){
        disponibilidad.confirmar(new DisponibilidadContextualService.DatosConfirmacion(tipo,id,version,0,true,Instant.now(),null,
                "Disponibilidad explícita TEST C4","EVIDENCIA-DISPONIBILIDAD-TEST-C4"),c.agente(),c.sesion(),clave(),Procedencia.deLaPantalla());
    }
    private static CoincidenciaService.Coincidencia unica(CoincidenciaService.Coincidencias r){
        var candidatas=r.grupos().stream().flatMap(g->g.candidatos().stream()).toList();
        assertEquals(1,candidatas.size(),"Oferta habilitada y contexto propio explícitos del escenario");return candidatas.getFirst();
    }
    private long contar(String sql,Object... argumentos){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,argumentos));}
    private static LocalDate hoy(){return ActividadNegociacionDePrueba.hoy();}
    private static String clave(){return ActividadNegociacionDePrueba.clave();}
    private static CorrelacionAutomatico correlacion(){return new CorrelacionAutomatico("TEST-C4-"+UUID.randomUUID(),"turno-TEST",UUID.randomUUID().toString());}
}
