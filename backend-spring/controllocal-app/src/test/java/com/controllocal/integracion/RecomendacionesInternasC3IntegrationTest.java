package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.persistence.repositorio.ProyeccionOfertaL1Repository;
import com.controllocal.service.*;
import com.controllocal.service.RequerimientoService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.demanda.InteresadosDeDemanda.*;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.matching.RecomendacionesInternasParaMatching;
import com.controllocal.service.network.PoliticaProyeccionNetworkV1;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.controllocal.service.matching.ContratoMatching.*;
import static com.controllocal.service.matching.RecomendacionesInternasParaMatching.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** L1 aprobado29/10: actores reales de TEST, G canónico y fronteras de datos antes de divulgar. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties="spring.datasource.hikari.maximum-pool-size=10")
class RecomendacionesInternasC3IntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.archivo.AccesoArchivoService archivosFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.web.almacen.AlmacenDocumentos almacenFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.actuacion.ActuacionesService actuacionesFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.soporte.Documentos documentosFirmaTest;

    private static final Procedencia PROC=Procedencia.deLaPantalla();
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired ProspeccionService prospecciones;
    @Autowired EncargoService encargos;
    @Autowired RequerimientoService demandas;
    @Autowired CoincidenciaService coincidencias;
    @Autowired RecomendacionesInternasParaMatching fuente;
    @Autowired GobiernoCatalogoCapturaService catalogos;
    @Autowired DisponibilidadContextualService disponibilidad;
    @Autowired CuentaService cuentas;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    @MockitoSpyBean ProyeccionOfertaL1Repository ofertas;
    private OrigenesDePrueba identidades;
    private OrigenesDePrueba.Equipo equipo;
    private SesionesComercialesDePrueba sesiones;
    private PreparacionPublicacionDePrueba materiales;

    @BeforeEach void identidadAislada() {
        identidades=new OrigenesDePrueba(jdbc,gestor,captura,contextos);equipo=identidades.empresa();
        sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        // ADMIN y BROKER comparten cuenta: se enrola primero y después se observan SID comerciales.
        sesiones.de(equipo.admin());sesiones.de(equipo.broker());sesiones.de(equipo.agente());sesiones.de(equipo.colega());
        materiales=new PreparacionPublicacionDePrueba(jdbc,propiedades,encargos,catalogos,contextos,cuentas,factores,cifrado,gestor, archivosFirmaTest, almacenFirmaTest, actuacionesFirmaTest, documentosFirmaTest);
    }
    @AfterEach void retirarInstrumentacion(){reset(ofertas);}

    @Test void coordinadorPaginaUnaDemandaConOrdenSUYReferenciaSinDuplicarCandidatos() {
        var actor=equipo.agente();List<Long> idsEncargo=new ArrayList<>();
        for(String frente:Arrays.asList("15","15","5",null)) {
            var e=oferta(actor,true);
            if(frente!=null){long observada=propiedades.consultar(e.propiedad(),actor).versionFisica();
                propiedades.editar(e.propiedad(),new PropiedadUniversalService.ComandoEdicion(clave(),PROC,null,null,null,
                        List.of(hechoTerreno("frente",frente)),null,null,null,null).conVersion(observada),actor);}
            habilitar(e,List.of());confirmarDisponible(e);idsEncargo.add(e.id());
        }
        var d=demanda(actor,null,List.of(frentePreferido()),List.of("TERRENO"));confirmarDemandaDisponible(d,actor);
        var pagina=coincidencias.consultar(new CoincidenciaService.Consulta(CoincidenciaService.Origen.DEMANDA,d.id(),d.version(),CoincidenciaService.TipoVersion.DEMANDA,
                Modalidad.COMERCIAL,Set.of(CoincidenciaService.Capa.L0),Categoria.COMERCIAL_CONFIRMADA,1,null),actor,sesion(actor));
        String consulta=pagina.referenciaConsulta(),grupo=null;List<CoincidenciaService.Coincidencia> ordenadas=new ArrayList<>();
        for(int i=0;i<4;i++) {
            assertEquals(CoincidenciaService.EstadoResultado.DISPONIBLE,pagina.estado());assertEquals(Long.valueOf(4),pagina.total());
            assertEquals(1,pagina.grupos().size());var g=pagina.grupos().getFirst();assertEquals(d.version(),g.versionDemanda());
            if(grupo==null)grupo=g.referenciaDemanda();else assertEquals(grupo,g.referenciaDemanda());
            assertEquals(1,g.candidatos().size());var c=g.candidatos().getFirst();ordenadas.add(c);
            assertEquals(Categoria.COMERCIAL_CONFIRMADA,c.categoria());assertEquals(Conclusion.CUMPLE,c.g());assertEquals(Conclusion.CUMPLE,c.h());assertEquals(2,c.w());
            assertEquals(c,coincidencias.explicar(consulta,g.referenciaDemanda(),c.referencia(),actor,sesion(actor)));
            if(i<3){assertNotNull(pagina.continuacion());pagina=coincidencias.continuar(pagina.continuacion(),actor,sesion(actor));}
            else assertNull(pagina.continuacion());
        }
        assertEquals(4,ordenadas.stream().map(CoincidenciaService.Coincidencia::referencia).distinct().count());
        assertEquals(Set.copyOf(idsEncargo),ordenadas.stream().map(c->((Number)c.datosPermitidos().get("idEncargo")).longValue()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2,ordenadas.get(0).s());assertEquals(0,ordenadas.get(0).u());assertEquals(2,ordenadas.get(1).s());assertEquals(0,ordenadas.get(1).u());
        assertTrue(ordenadas.get(0).referencia().compareTo(ordenadas.get(1).referencia())<0);
        assertEquals(0,ordenadas.get(2).s());assertEquals(0,ordenadas.get(2).u());assertEquals(0,ordenadas.get(3).s());assertEquals(2,ordenadas.get(3).u());
        assertEquals(ordenadas.getFirst(),coincidencias.continuar(consulta,actor,sesion(actor)).grupos().getFirst().candidatos().getFirst());
    }
    @Test void coordinadorPreliminarDeCapturaParcialConservaBorradorSinMaterializar() {
        var actor=equipo.agente();var d=demanda(actor,null,null,List.of("TERRENO"));
        var b=captura.avanzar(new MotorDeCaptura.Avance(MotorDeCaptura.REGISTRAR_PROPIEDAD,null,null,
                Map.of("tipoPropiedad",MotorDeCaptura.Respuesta.conocida("TERRENO"),"distrito",MotorDeCaptura.Respuesta.conocida("Santiago de Surco"),
                        "operaciones",MotorDeCaptura.Respuesta.conocida("ALQUILER")),null),PROC,actor);
        assertFalse(b.listoParaEjecutar());assertNotNull(b.idBorrador());long personas=filas("select count(*) from persona where organizacion_id=?",org());
        var r=coincidencias.consultar(new CoincidenciaService.Consulta(CoincidenciaService.Origen.CAPTURA,b.idBorrador(),(long)b.version(),CoincidenciaService.TipoVersion.CAPTURA,
                Modalidad.PRELIMINAR,Set.of(CoincidenciaService.Capa.L0),Categoria.POTENCIAL,1,null),actor,sesion(actor));
        assertEquals(CoincidenciaService.EstadoResultado.DISPONIBLE,r.estado());assertEquals(Long.valueOf(1),r.total());
        assertEquals(d.version(),r.grupos().getFirst().versionDemanda());var c=r.grupos().getFirst().candidatos().getFirst();
        assertEquals(Categoria.POTENCIAL,c.categoria());assertEquals(Conclusion.NO_DETERMINADO,c.g());
        assertEquals(c,coincidencias.explicar(r.referenciaConsulta(),r.grupos().getFirst().referenciaDemanda(),c.referencia(),actor,sesion(actor)));
        var despues=captura.consultar(b.idBorrador(),actor);assertEquals(b.version(),despues.version());assertEquals(b.conocido(),despues.conocido());assertEquals(b.metadatos(),despues.metadatos());
        for(String tabla:List.of("propiedad","captacion","prospeccion","oportunidad_comercial","publicacion"))
            assertEquals(0,filas("select count(*) from "+tabla+" where organizacion_id=?",org()),tabla);
        assertEquals(personas,filas("select count(*) from persona where organizacion_id=?",org()));
    }

    @Test void coordinadorL1ConsultaYExplicaElMismoParRealSinAbrirLaCarteraDelColega() {
        var actor=equipo.agente();var d=demanda(actor,null,null,List.of("TERRENO"));var e=oferta(equipo.colega(),true);
        var h=habilitar(e,List.of());confirmarDisponible(e);confirmarDemandaDisponible(d,actor);
        var consulta=new CoincidenciaService.Consulta(CoincidenciaService.Origen.DEMANDA,d.id(),d.version(),CoincidenciaService.TipoVersion.DEMANDA,
                Modalidad.COMERCIAL,Set.of(CoincidenciaService.Capa.L1),Categoria.COMERCIAL_CONFIRMADA,20,null);
        var r=coincidencias.consultar(consulta,actor,sesion(actor));assertEquals(CoincidenciaService.EstadoResultado.DISPONIBLE,r.estado());
        assertEquals(Long.valueOf(1),r.total());assertEquals(1,r.grupos().size());assertEquals(d.version(),r.grupos().getFirst().versionDemanda());
        assertTrue(r.referenciaConsulta().matches("[A-Za-z0-9_-]{43}"));
        var c=r.grupos().getFirst().candidatos().getFirst();assertEquals(CoincidenciaService.Capa.L1,c.capa());
        assertEquals(Categoria.COMERCIAL_CONFIRMADA,c.categoria());assertEquals(Conclusion.CUMPLE,c.g());assertEquals(Conclusion.CUMPLE,c.h());
        assertTrue(c.referencia().matches("[a-f0-9]{64}"));assertFalse(c.razones().isEmpty());
        assertTrue(c.datosPermitidos().keySet().stream().allMatch(k->PoliticaProyeccionNetworkV1.CAMPOS.contains(k)
                ||Set.of("responsableCoincidente","recursoResponsable").contains(k)));
        assertEquals(c,coincidencias.explicar(r.referenciaConsulta(),r.grupos().getFirst().referenciaDemanda(),c.referencia(),actor,sesion(actor)));
        assertThrows(NoEncontradoException.class,()->coincidencias.explicar(r.referenciaConsulta(),r.grupos().getFirst().referenciaDemanda(),c.referencia(),equipo.colega(),sesion(equipo.colega())));
        assertThrows(AccesoNoAutorizadoException.class,()->encargos.consultar(e.id(),actor));
        assertThrows(AccesoNoAutorizadoException.class,()->encargos.consultarDocumento(h.documento().id(),actor));
        assertEquals(0,filas("select count(*) from oportunidad_comercial where organizacion_id=?",org()));
        assertEquals(0,filas("select count(*) from publicacion where organizacion_id=?",org()));
    }
    @Test void busquedaPublicaYCoordinadorL0UsanSoloElAmbitoPropioReal() {
        var actor=equipo.agente();var d=demanda(actor,null,null,List.of("TERRENO"));var propia=oferta(actor,true);
        habilitar(propia,List.of());confirmarDisponible(propia);confirmarDemandaDisponible(d,actor);
        var ajena=oferta(equipo.colega(),false);var conocida=propiedades.consultar(propia.propiedad(),actor);
        var privada=propiedades.consultar(ajena.propiedad(),equipo.colega());
        var listado=propiedades.listar(new PropiedadUniversalService.FiltrosPropiedad(conocida.codigo(),"TERRENO",null,null,"ALQUILER",1,20),actor);
        assertEquals(1,listado.total());assertEquals(propia.propiedad(),listado.items().getFirst().id());
        var sinPermiso=propiedades.listar(new PropiedadUniversalService.FiltrosPropiedad(privada.ubicacion().direccion(),null,null,null,null,1,20),actor);
        assertEquals(0,sinPermiso.total());assertTrue(sinPermiso.items().isEmpty());
        var r=coincidencias.consultar(new CoincidenciaService.Consulta(CoincidenciaService.Origen.DEMANDA,d.id(),d.version(),CoincidenciaService.TipoVersion.DEMANDA,
                Modalidad.COMERCIAL,Set.of(CoincidenciaService.Capa.L0),Categoria.COMERCIAL_CONFIRMADA,20,null),actor,sesion(actor));
        assertEquals(CoincidenciaService.EstadoResultado.DISPONIBLE,r.estado());assertEquals(Long.valueOf(1),r.total());
        var c=r.grupos().getFirst().candidatos().getFirst();assertEquals(CoincidenciaService.Capa.L0,c.capa());
        assertEquals(Conclusion.CUMPLE,c.g());assertEquals(Conclusion.CUMPLE,c.h());
        assertEquals(propia.id(),((Number)c.datosPermitidos().get("idEncargo")).longValue());
        assertEquals(c,coincidencias.explicar(r.referenciaConsulta(),r.grupos().getFirst().referenciaDemanda(),c.referencia(),actor,sesion(actor)));
        assertEquals(0,filas("select count(*) from oportunidad_comercial where organizacion_id=?",org()));
        assertEquals(0,filas("select count(*) from publicacion where organizacion_id=?",org()));
    }
    @Test void coordinadorPreliminarDesdeFisicoYProspeccionNoMaterializaUnEncargoNiUnInteres() {
        var actor=equipo.agente();var d=demanda(actor,null,null,List.of("TERRENO"));
        long p=identidades.propiedad(actor,"Objeto preliminar identificado por captura Core");
        var prospeccion=prospecciones.registrar(new ProspeccionService.DatosProspeccion(p,"Prospección explícita TEST sin Encargo", null),actor);
        long versionObservada=propiedades.consultar(p,actor).versionFisica();
        long personas=filas("select count(*) from persona where organizacion_id=?",org());
        for(var tipo:List.of(CoincidenciaService.Origen.FISICO,CoincidenciaService.Origen.PROSPECCION)) {
            long id=tipo==CoincidenciaService.Origen.FISICO?p:prospeccion.id();
            var r=coincidencias.consultar(new CoincidenciaService.Consulta(tipo,id,versionObservada,CoincidenciaService.TipoVersion.FISICA,
                    Modalidad.PRELIMINAR,Set.of(CoincidenciaService.Capa.L0),Categoria.POTENCIAL,20,null),actor,sesion(actor));
            assertEquals(CoincidenciaService.EstadoResultado.DISPONIBLE,r.estado());assertEquals(Long.valueOf(1),r.total());
            var c=r.grupos().getFirst().candidatos().getFirst();assertEquals(Categoria.POTENCIAL,c.categoria());
            assertEquals(Conclusion.NO_DETERMINADO,c.g());assertEquals(d.version(),r.grupos().getFirst().versionDemanda());
            assertEquals(c,coincidencias.explicar(r.referenciaConsulta(),r.grupos().getFirst().referenciaDemanda(),c.referencia(),actor,sesion(actor)));
        }
        assertThrows(ReglaNegocioException.class,()->coincidencias.consultar(new CoincidenciaService.Consulta(CoincidenciaService.Origen.FISICO,p,versionObservada,CoincidenciaService.TipoVersion.FISICA,
                Modalidad.COMERCIAL,Set.of(CoincidenciaService.Capa.L0),Categoria.COMERCIAL_CONFIRMADA,20,null),actor,sesion(actor)));
        assertEquals(0,filas("select count(*) from captacion where organizacion_id=?",org()));
        assertEquals(0,filas("select count(*) from oportunidad_comercial where organizacion_id=?",org()));
        assertEquals(0,filas("select count(*) from publicacion where organizacion_id=?",org()));
        assertEquals(personas,filas("select count(*) from persona where organizacion_id=?",org()));
    }

    @Test void fuenteL1SinActivacionNetworkIdentificaColegaYConservaExpedientePrivado() {
        var d=demanda(equipo.agente(),null,null);var e=oferta(equipo.colega(),true);
        var habilitada=habilitar(e,List.of());confirmarDisponible(e);
        assertTrue(encargos.consultarVigencia(e.id(),hoy(),e.actor()).habilitado());
        var g=encargos.habilitacionParaRecomendacionInterna(e.id(),hoy(),equipo.agente());assertTrue(g.habilitado());
        var salida=fuente.ofertas(origen(d),equipo.agente(),Instant.now());assertEquals(1,salida.size());var r=salida.getFirst();
        assertEquals(Conclusion.CUMPLE,r.oferta().habilitacion());assertEquals("AGENTE:"+equipo.colega().idRolOperativo(),r.responsable().referencia());
        assertNotNull(r.responsable().nombre());assertFalse(r.responsable().nombre().isBlank());
        assertEquals(0,new BigDecimal("2700").compareTo((BigDecimal)campo(r,"ENCARGO:precio_pedido").get("valor")));
        assertFalse(r.camposV1().keySet().stream().anyMatch(k->k.contains("cliente")||k.contains("comision")||k.contains("direccion")||k.contains("titular")));
        assertThrows(AccesoNoAutorizadoException.class,()->encargos.consultar(e.id(),equipo.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->encargos.consultarDocumento(habilitada.documento().id(),equipo.agente()));
        assertThrows(NoEncontradoException.class,()->propiedades.consultar(e.propiedad(),equipo.agente()));
        assertEquals(0,filas("select count(*) from participacion_network where organizacion_id=?",org()));
    }
    @Test void composicionUsaElMismoSnapshotCanonicoSinEntregarUnidadesNiConfundirTotalYPacto() {
        var d=demanda(equipo.agente(),null,null);var e=oferta(equipo.colega(),true);
        long titular=jdbc.queryForObject("select id_rol_propietario from titularidad_propiedad where organizacion_id=? and id_propiedad=? and vigente_hasta is null",Long.class,org(),e.propiedad());
        long segunda=registrarTerreno(e.actor(),true,titular);
        prospecciones.registrar(new ProspeccionService.DatosProspeccion(segunda,"Contexto privado TEST de la segunda unidad", null),e.actor());
        var unidades=List.of(new EncargoService.Unidad(e.propiedad(),new BigDecimal("3000"),"PEN","Desglose TEST explícito"),
                new EncargoService.Unidad(segunda,new BigDecimal("4000"),"PEN","Desglose TEST explícito"));
        long observada=encargos.consultar(e.id(),e.actor()).version();
        var composicion=encargos.declararComposicion(e.id(),new EncargoService.DatosComposicion(unidades,new BigDecimal("7000"),"PEN","Dos unidades TEST",observada),e.actor(),sesion(e.actor()),clave(),PROC);
        // Se acepta realmente el pacto actual2700. El desglose7000 no lo sustituye.
        var h=habilitar(e,unidades);confirmarDisponible(e);
        var propio=encargos.consultarVigencia(e.id(),hoy(),e.actor());assertTrue(propio.habilitado());
        var reducido=encargos.habilitacionParaRecomendacionInterna(e.id(),hoy(),equipo.agente());
        assertEquals(propio.habilitado(),reducido.habilitado());assertEquals(propio.faltantes(),reducido.causas());assertEquals(composicion.id(),reducido.composicion());
        var r=fuente.ofertas(origen(d),equipo.agente(),Instant.now()).getFirst();assertEquals(Conclusion.CUMPLE,r.oferta().habilitacion());
        assertEquals(0,new BigDecimal("2700").compareTo((BigDecimal)campo(r,"ENCARGO:precio_pedido").get("valor")));
        assertFalse(r.camposV1().containsKey("PROPIEDAD:tipo_propiedad"));assertFalse(r.camposV1().containsKey("unidades"));assertFalse(r.camposV1().containsKey("totalDeclarado"));
        assertThrows(AccesoNoAutorizadoException.class,()->encargos.consultarComposicion(e.id(),null,equipo.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->encargos.consultarDocumento(h.documento().id(),equipo.agente()));
        assertThrows(NoEncontradoException.class,()->propiedades.consultar(segunda,equipo.agente()));
    }
    @Test void borradorNoHabilitadoNoPresentaElPrecioPrivadoComoOfertaAutorizada() {
        var d=demanda(equipo.agente(),null,null);var e=oferta(equipo.colega(),false);
        var r=fuente.ofertas(origen(d),equipo.agente(),Instant.now()).getFirst();
        assertEquals(Conclusion.INCUMPLE,r.oferta().habilitacion());assertFalse(r.camposV1().containsKey("ENCARGO:precio_pedido"));
        verify(ofertas,never()).precio(eq(org()),eq(e.id()),anyLong());
    }
    @Test void demandaAjenaEsSiempreParcialYElCambioDePresupuestoNoAfectaSuSubsetFisico() {
        var propio=oferta(equipo.agente(),false);var ajena=demanda(equipo.colega(),new BigDecimal("41732"),List.of(ascensores()));
        var origen=new OrigenReferencia(CoincidenciaService.Origen.ENCARGO,propio.id(),encargos.consultar(propio.id(),propio.actor()).version(),CoincidenciaService.TipoVersion.ENCARGO);
        Instant corte=Instant.now();var primera=fuente.demandas(origen,Modalidad.COMERCIAL,"D1-TEST",corte,equipo.agente()).getFirst();
        assertEquals(CoberturaCriterios.PARCIAL,primera.consulta().coberturaCriterios());assertEquals(ajena.id(),primera.id());
        assertTrue(primera.consulta().criterios().stream().flatMap(c->c.alternativas().stream()).anyMatch(a->a.claveDato().equals("PROPIEDAD:ascensores")));
        assertTrue(primera.consulta().criterios().stream().flatMap(c->c.alternativas().stream()).noneMatch(a->a.tipo()==com.controllocal.domain.inmueble.TipoDato.IMPORTE||a.valor()!=null&&a.valor().moneda()!=null));
        assertTrue(primera.consulta().criterios().stream().flatMap(c->c.alternativas().stream()).allMatch(a->a.metadatos().evidenciaRef()==null&&a.metadatos().fuente()==null&&a.contexto().referenciaCatalogo()==null));
        long observada=ajena.version();var editada=demandas.actualizar(ajena.id(),observada,datosEdicionPresupuesto(new BigDecimal("97389")),equipo.colega(),sesion(equipo.colega()),clave(),PROC);
        var segunda=fuente.revalidarDemandas(origen,List.of(ajena.id()),Modalidad.COMERCIAL,"D1-TEST",corte,equipo.agente(),Instant.now()).getFirst();
        assertEquals(editada.version(),segunda.version());assertEquals(primera.consulta().criterios(),segunda.consulta().criterios());
        assertEquals(CoberturaCriterios.PARCIAL,segunda.consulta().coberturaCriterios());
        assertThrows(AccesoNoAutorizadoException.class,()->demandas.buscar(ajena.id(),equipo.agente()));
    }
    @Test void recursoDeOrigenAjenoNoEsPermisoParaEnumerarCartera() {
        var ajena=demanda(equipo.colega(),null,null);
        assertThrows(AccesoNoAutorizadoException.class,()->fuente.ofertas(origen(ajena),equipo.agente(),Instant.now()));
        verify(ofertas,never()).candidatas(anyLong(),any());
        assertThrows(AccesoNoAutorizadoException.class,()->fuente.ofertas(origen(ajena),equipo.admin(),Instant.now()));
    }
    @Test void nombresIgualesDeOtraOrganizacionNoAparecenEnLasRecomendaciones() {
        var d=demanda(equipo.agente(),null,null);var local=oferta(equipo.colega(),false);
        var otros=identidades.empresa();sesiones.de(otros.agente());long p=identidades.propiedad(otros.agente(),"Oferta ajena que no se comparte");
        long ajeno=abrir(p,otros.agente());
        var salida=fuente.ofertas(origen(d),equipo.agente(),Instant.now());assertEquals(1,salida.size());assertEquals("encargo:"+local.id(),salida.getFirst().referencia().referencia());
        assertTrue(salida.stream().noneMatch(r->r.referencia().referencia().equals("encargo:"+ajeno)));
    }
    @Test void t4RevocacionEntreRecuperacionYEntregaRetiraLaReferenciaSinPistas() throws Exception {
        var d=demanda(equipo.agente(),null,null);var e=oferta(equipo.colega(),false);
        var leido=new CountDownLatch(1);var continuar=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
        doAnswer(invocacion->{var r=invocacion.callRealMethod();leido.countDown();
            if(!continuar.await(15,TimeUnit.SECONDS))throw new AssertionError("Faltó completar la revocación TEST");return r;
        }).when(ofertas).fisicos(eq(org()),eq(e.propiedad()),anySet());
        Future<List<OfertaInterna>> futura=executor.submit(()->fuente.ofertas(origen(d),equipo.agente(),Instant.now()));
        try {
            assertTrue(leido.await(15,TimeUnit.SECONDS));
            // Sabotaje TEST acotado de pertenencia, en otra transacción ya confirmada.
            // No simula que se haya ejecutado el procedimiento C1 de baja.
            new TransactionTemplate(gestor).executeWithoutResult(t->assertEquals(1,jdbc.update("update usuario_organizacion set estado='I' where organizacion_id=? and id_persona=?",org(),equipo.colega().idPersona())));
            continuar.countDown();assertTrue(futura.get(20,TimeUnit.SECONDS).isEmpty());
        } finally {continuar.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }

    private record OfertaDePrueba(long propiedad,long id,Actor actor) { }
    private OfertaDePrueba oferta(Actor actor,boolean suficiente){long p=terreno(actor,suficiente);return new OfertaDePrueba(p,abrir(p,actor),actor);}
    private long abrir(long p,Actor actor){return new TransactionTemplate(gestor).execute(t->encargos.abrir(new EncargoService.DatosApertura(p,actor.idRolOperativo(),null,OperacionInmobiliaria.ALQUILER,hoy(),hoy().plusMonths(6),false,"Notas privadas TEST del pacto",pacto(),PROC,List.of()),actor)).id();}
    private long terreno(Actor actor,boolean suficiente) {
        long propietario=new TransactionTemplate(gestor).execute(t->{long persona=jdbc.queryForObject("insert into persona(organizacion_id,nombres_o_razon_social) values(?,?) returning id_persona",Long.class,actor.idOrganizacion(),"Titular declarado sólo en TEST "+UUID.randomUUID());
            return jdbc.queryForObject("insert into persona_rol(organizacion_id,id_persona,tipo_rol,vigencia_desde) values(?,?,'PROPIETARIO',?) returning id_persona_rol",Long.class,actor.idOrganizacion(),persona,hoy());});
        return registrarTerreno(actor,suficiente,propietario);
    }
    private long registrarTerreno(Actor actor,boolean suficiente,long propietario) {
        // Hechos elegidos para ESTE positivo: no son defaults del producto ni del banco compartido.
        long p=propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(clave(),PROC,null,"TERRENO","VIVIENDA","Descripción privada TEST",
                new PropiedadUniversalService.Ubicacion("Dirección privada TEST "+UUID.randomUUID(),"Surco",null,null,null,null,null,null,null),
                List.of(new PropiedadUniversalService.Titular(propietario,new BigDecimal("100"),false)),List.of(),List.of(),null),actor).idPropiedad();
        if(suficiente){materiales.prepararTerreno(p,actor);long observada=propiedades.consultar(p,actor).versionFisica();
            var hechos=List.of(hechoTerreno("agua_desague","CONECTADO"),hechoTerreno("energia_electrica","CONECTADO"),hechoTerreno("condicion_terreno","URBANO_HABILITADO"));
            propiedades.editar(p,new PropiedadUniversalService.ComandoEdicion(clave(),PROC,null,null,null,hechos,null,null,null,null).conVersion(observada),actor);
        }return p;
    }
    /** Documento de la misma composición: null conserva selección actual; vacío la sustituiría. */
    private EncargosDePrueba.Resultado habilitar(OfertaDePrueba e,List<EncargoService.Unidad> unidades) {
        if(!unidades.isEmpty())assertEquals(unidades,encargos.consultarComposicion(e.id(),null,e.actor()).unidades());
        var titulo=adjuntar(e,null,"TITULARIDAD_ENCARGO","Título declarado de TEST para "+e.propiedad());
        var partes=jdbc.query("select t.id_titularidad,r.id_persona,t.cuota,p.nombres_o_razon_social from titularidad_propiedad t join persona_rol r on r.organizacion_id=t.organizacion_id and r.id_persona_rol=t.id_rol_propietario join persona p on p.organizacion_id=r.organizacion_id and p.id_persona=r.id_persona where t.organizacion_id=? and t.id_propiedad=? and t.vigente_hasta is null",(r,n)->new EncargoService.Parte(r.getLong(1),r.getLong(2),r.getBigDecimal(3),titulo.referencia(),null,null,null,null,null,r.getString(4),null),org(),e.propiedad());
        assertFalse(partes.isEmpty());var actual=encargos.consultar(e.id(),e.actor());
        var doc=encargos.prepararDocumento(new EncargoService.Preparacion(e.id(),actual.version(),e.propiedad(),null,partes,OperacionInmobiliaria.ALQUILER,null,pacto(),hoy(),hoy().plusMonths(6),false,null,null),e.actor(),sesion(e.actor()),clave(),PROC);
        assertTrue(doc.faltantes().isEmpty(),()->"Fixture material explícito: "+doc.faltantes());
        var firmantes=partes.stream().map(p->new EncargoService.Firmante(p.titularidad(),p.persona())).toList();
        var firmas=new FirmasManuscritasDePrueba(encargos,archivosFirmaTest,almacenFirmaTest)
                .aportar(e.id(),doc,firmantes,hoy(),e.actor(),sesion(e.actor()));actual=encargos.consultar(e.id(),e.actor());
        var peticion=encargos.presentar(e.id(),new EncargoService.Presentacion(doc.id(),actual.version(),partes,firmas,null,null),e.actor(),sesion(e.actor()),clave(),PROC);
        var revisada=encargos.consultar(e.id(),equipo.broker());
        var datosDecision=new EncargoService.Decision("APROBAR",peticion.version(),revisada.version(),"Revisión competente TEST del mismo snapshot",
                new EncargoService.RevisionManuscrita(peticion.firma().revisionPresentacion(),peticion.firma().huellaManifestacion(),true));
        var propuesta=actuacionesFirmaTest.prepararDecisionEncargo(equipo.broker(),peticion.id(),datosDecision,sesion(equipo.broker()),PROC);
        var decision=new ActuacionesHumanasC4DePrueba(actuacionesFirmaTest,documentosFirmaTest)
                .confirmar(propuesta,equipo.broker(),sesion(equipo.broker()),EncargoService.Peticion.class).datos();
        return new EncargosDePrueba.Resultado(e.id(),doc,decision,encargos.consultar(e.id(),e.actor()));
    }
    private EncargoService.ArchivoRegistrado adjuntar(OfertaDePrueba e,Long doc,String finalidad,String texto) {
        long version=encargos.consultar(e.id(),e.actor()).version();var carga=encargos.cargarArchivo(new EncargoService.CargaArchivo(e.id(),doc,finalidad,"evidencia-l1-test.txt","text/plain",texto.getBytes(StandardCharsets.UTF_8),version),e.actor(),sesion(e.actor()),clave(),PROC);
        return encargos.adjuntarOReutilizar(new EncargoService.VinculoArchivo(carga.referencia(),e.id(),doc,finalidad,version),e.actor(),sesion(e.actor()),clave(),PROC);
    }
    private void confirmarDisponible(OfertaDePrueba e){long observada=encargos.consultar(e.id(),e.actor()).version();disponibilidad.confirmar(new DisponibilidadContextualService.DatosConfirmacion(DisponibilidadContextualService.TipoRecurso.ENCARGO,e.id(),observada,0,true,Instant.now(),null,"Confirmación actual explícita TEST","DECLARACION-TEST-DISPONIBILIDAD-ENCARGO:"+e.id()),e.actor(),sesion(e.actor()),clave(),PROC);}
    private void confirmarDemandaDisponible(FichaRequerimiento d,Actor actor){disponibilidad.confirmar(new DisponibilidadContextualService.DatosConfirmacion(DisponibilidadContextualService.TipoRecurso.DEMANDA,d.id(),d.version(),0,true,Instant.now(),null,"Demanda disponible declarada expresamente TEST","DECLARACION-TEST-DISPONIBILIDAD-DEMANDA:"+d.id()),actor,sesion(actor),clave(),PROC);}
    private FichaRequerimiento demanda(Actor actor,BigDecimal presupuesto,List<CriterioPedido> criterios) {
        return demanda(actor,presupuesto,criterios,List.of("DEPARTAMENTO"));
    }
    private FichaRequerimiento demanda(Actor actor,BigDecimal presupuesto,List<CriterioPedido> criterios,List<String> tipos) {
        var contacto=new Referencia(TipoReferencia.CONTACTO_NUEVO,null,null,null,new DatosContacto(null,null,null,null,"999000991",null));
        var d=new DatosRequerimiento(null,null,null,null,null,null,"ALQUILER",tipos,null,presupuesto,presupuesto==null?null:"PEN",null,null,null,null,"Notas privadas TEST de la búsqueda",null,criterios,contacto,null,null,presupuesto==null?null:"TOTAL_MENSUAL_OBLIGATORIO",presupuesto==null?null:"MENSUAL",Set.of());
        return demandas.crear(d,actor,sesion(actor),clave(),PROC);
    }
    private static DatosRequerimiento datosEdicionPresupuesto(BigDecimal maximo){return new DatosRequerimiento(null,null,null,null,null,null,null,null,null,maximo,null,null,null,null,null,"Otra nota privada TEST",null,null,null,null,null,null,null,Set.of());}
    private CriterioPedido ascensores(){return jdbc.queryForObject("select id_catalogo_atributo,version_definicion,unidad from catalogo_atributo where organizacion_id is null and activo and clave='ascensores'",(r,n)->new CriterioPedido("ascensores","MINIMO","I",null,BigDecimal.ZERO,null,null,null,null,null,null,null,new ContextoValor(null,r.getString(3),null,null,"ALQUILER",null),new MetadatosDato(EstadoDato.CONOCIDO,null,"CAT:D:"+r.getLong(1)+":"+r.getLong(2),r.getLong(2),"evidencia-privada-no-compartida","Fuente privada TEST",hoy(),null,null),false,null));}
    private CriterioPedido frentePreferido(){return jdbc.queryForObject("select id_catalogo_atributo,version_definicion,unidad from catalogo_atributo where organizacion_id is null and activo and clave='frente'",(r,n)->
            new CriterioPedido("frente","MINIMO","D",null,new BigDecimal("10"),null,null,null,null,null,null,null,
                    new ContextoValor(null,r.getString(3),null,null,"ALQUILER",null),new MetadatosDato(EstadoDato.CONOCIDO,null,"CAT:D:"+r.getLong(1)+":"+r.getLong(2),r.getLong(2),
                    "DECLARACION-TEST-PREFERENCIA-FRENTE","Preferencia explícita TEST",hoy(),null,null),false,null));}
    private static EncargoService.Condiciones pacto(){return EncargosDePrueba.alquiler(new BigDecimal("2700"),"PEN",BigDecimal.ONE,"Titular TEST","Agencia TEST");}
    private static PropiedadUniversalService.ValorAtributo hechoTerreno(String clave,String valor){
        return new PropiedadUniversalService.ValorAtributo(clave,valor,null,null,"DECLARADO",null,hoy(),
                "Hecho explícito ficticio del positivo TEST L1; no certificación de un predio real",
                new PropiedadUniversalService.MetadatosDeCaptura(EstadoDato.CONOCIDO,null,null,null,false));
    }
    private static OrigenReferencia origen(FichaRequerimiento d){return new OrigenReferencia(CoincidenciaService.Origen.DEMANDA,d.id(),d.version(),CoincidenciaService.TipoVersion.DEMANDA);}
    @SuppressWarnings("unchecked") private static Map<String,Object> campo(OfertaInterna r,String clave){return (Map<String,Object>)r.camposV1().get(clave);}
    private ContextoSesion sesion(Actor actor){return sesiones.de(actor);}
    private long org(){return equipo.agente().idOrganizacion();}
    private long filas(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    private static LocalDate hoy(){return OrigenesDePrueba.hoy();}
    private static String clave(){return "l1-c3-test-"+UUID.randomUUID();}
}
