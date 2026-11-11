package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.BaseDeDatosDePruebas;
import com.controllocal.service.*;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.capacidades.*;
import com.controllocal.web.capacidades.RegistroAdaptadoresHttp;
import com.controllocal.web.seguridad.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import static com.controllocal.service.capacidades.CapacidadesService.*;
import static com.controllocal.service.PropiedadUniversalService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.doReturn;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties={"spring.jpa.properties.hibernate.generate_statistics=true",
                "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc(addFilters=false)
class CapacidadesContextualesIntegrationTest {
    @DynamicPropertySource static void datos(DynamicPropertyRegistry p) { BaseDeDatosDePruebas.registrar(p); }
    @Autowired CapacidadesService capacidades;
    @MockitoSpyBean PropiedadUniversalService propiedades;
    @MockitoSpyBean com.controllocal.service.planes.DerechosDeInformesC7 derechos;
    @MockitoSpyBean MetaComercialService metas;
    @Autowired com.controllocal.service.captura.MotorDeCaptura captura;
    @MockitoSpyBean RegistroDeCapacidades registro;
    @Autowired RegistroAdaptadoresHttp adaptadores;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.controllocal.service.ContextosService contextos;
    @Autowired EntityManagerFactory emf;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.transaction.PlatformTransactionManager gestor;
    private com.controllocal.integracion.soporte.OrigenesDePrueba.Equipo equipo;
    private Actor agente;

    @BeforeEach void actorDeFixture() {
        // No tomar la primera semilla histórica: el contrato definitivo requiere cuenta/pertenencia/rol reales del fixture.
        equipo=new com.controllocal.integracion.soporte.OrigenesDePrueba(jdbc,gestor,captura,contextos).empresa();
        agente=equipo.agente();
        sesion(agente);
    }
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }
    private void sesion(Actor actor) {
        var principal=com.controllocal.integracion.soporte.SesionesDePrueba.abrir(jdbc,contextos,actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,
                AuthorityUtils.createAuthorityList("ROLE_"+actor.rolEfectivo())));
    }
    private long propiedad() {
        return propiedades.registrar(new ComandoRegistro(null,null,null,"CASA","VIVIENDA","Prueba C0/K0",
                new Ubicacion("Av. C0K0 "+UUID.randomUUID(),"Miraflores",null,null,null,null,null,null,null),
                List.of(),List.of(new ValorAtributo("metraje_total","120"),new ValorAtributo("area_terreno","150"),
                        new ValorAtributo("metraje_construido","90"),new ValorAtributo("dormitorios","3"),
                        new ValorAtributo("banos","2")),List.of(),null),agente).idPropiedad();
    }
    private DescriptorCapacidad de(String id,String recurso,long referencia,Actor actor) {
        return capacidades.consultar(new Consulta(id,recurso,Long.toString(referencia),null,null,null, null),actor).capacidades().getFirst();
    }

    @Test void losAdaptadoresRegistradosEstanEnlazadosConHandlersReales() {
        assertEquals(new HashSet<>(registro.todas().stream().map(RegistroDeCapacidades.Definicion::idCore).toList()),
                adaptadores.reconocidos().keySet());
    }

    @Test void consultaGeneralNoDisparaConsultasDeCartera() {
        adaptadores.reconocidos();
        var definiciones=registro.todas();
        var p0=Set.of("configurar_programa_personal","cambiar_estado_programa_personal","consultar_resultado_programa_personal","establecer_meta","decidir_propuesta_meta","proponer_meta");
        doReturn(definiciones.stream().filter(d->!p0.contains(d.idCore())).toList()).when(registro).todas();
        var stats=emf.unwrap(SessionFactory.class).getStatistics();
        long antes=stats.getPrepareStatementCount();
        assertFalse(capacidades.consultar(Consulta.general(),agente).capacidades().isEmpty());
        assertEquals(antes,stats.getPrepareStatementCount(),"El catálogo estático conserva cero lecturas SQL");
        doReturn(definiciones).when(registro).todas();
        org.mockito.Mockito.clearInvocations(propiedades,derechos,metas);
        var catalogo=capacidades.consultar(Consulta.general(),agente);
        assertFalse(catalogo.capacidades().isEmpty());
        org.mockito.Mockito.verifyNoInteractions(propiedades);
        org.mockito.Mockito.verify(derechos,org.mockito.Mockito.times(1)).actuales(org.mockito.ArgumentMatchers.eq(agente),org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(metas,org.mockito.Mockito.times(1)).puedeDecidir(agente);
        org.mockito.Mockito.verifyNoMoreInteractions(metas);
        assertTrue(stats.getPrepareStatementCount()-antes<=2,"P0 y autoridad de metas revalidan contexto una vez cada uno, sin recorrer cartera");
        System.out.println("C0/K0 consultas SQL de capacidades generales: "+(stats.getPrepareStatementCount()-antes));
    }

    @Test void elContextoDePropiedadUsaProyeccionLigeraUnaVezPorPeticion() {
        long id=propiedad();
        var definiciones=registro.todas();
        var fisicas=java.util.Set.of("estado_de_propiedad","editar_propiedad","registrar_observacion_mercado",
                "consultar_fotos_contextuales","seleccionar_fotos_contextuales",
                "consultar_origen_fisico_unidad","registrar_origen_fisico_unidad");
        doReturn(definiciones.stream().filter(d->fisicas.contains(d.idCore())).toList()).when(registro).todas();
        var stats=emf.unwrap(SessionFactory.class).getStatistics();
        long antes=stats.getPrepareStatementCount();
        var lista=capacidades.consultar(new Consulta(null,"PROPIEDAD",Long.toString(id),null,null,null, null),agente).capacidades();
        assertEquals(java.util.Set.of("estado_de_propiedad","editar_propiedad","registrar_observacion_mercado",
                        "consultar_fotos_contextuales","seleccionar_fotos_contextuales",
                        "consultar_origen_fisico_unidad","registrar_origen_fisico_unidad"),
                lista.stream().map(DescriptorCapacidad::idCore).collect(java.util.stream.Collectors.toSet()));
        assertTrue(lista.stream().filter(d->!"registrar_origen_fisico_unidad".equals(d.idCore())).allMatch(d->d.estado()==Estado.HABILITADA));
        assertEquals(Estado.CONTEXTO_REQUERIDO,lista.stream().filter(d->"registrar_origen_fisico_unidad".equals(d.idCore())).findFirst().orElseThrow().estado());
        long consultas=stats.getPrepareStatementCount()-antes;
        assertTrue(consultas<=3,"No cargar una ficha completa, fotos, historia o un resultado por atributo");
        System.out.println("C0/K0 consultas SQL del contexto ligero de propiedad: "+consultas);
        doReturn(definiciones).when(registro).todas();
        org.mockito.Mockito.clearInvocations(propiedades);
        var actuales=capacidades.consultar(new Consulta(null,"PROPIEDAD",Long.toString(id),null,null,null,null),agente).capacidades();
        var ids=new HashSet<>(fisicas);
        ids.addAll(Set.of("buscar_coincidencias","registrar_interaccion","evaluar_foto_local",
                "preparar_material_ia","solicitar_material_ia","presentar_tratamiento_material_ia"));
        assertEquals(ids,actuales.stream().map(DescriptorCapacidad::idCore).collect(java.util.stream.Collectors.toSet()));
        for(var d:actuales) {
            var esperado="registrar_origen_fisico_unidad".equals(d.idCore())?Estado.CONTEXTO_REQUERIDO:
                    "solicitar_material_ia".equals(d.idCore())?Estado.DESHABILITADA:Estado.HABILITADA;
            assertEquals(esperado,d.estado(),d.idCore());
        }
        org.mockito.Mockito.verify(propiedades,org.mockito.Mockito.times(1)).capacidadSobre(id,null,agente);
    }

    @Test void otroTenantNoObtieneNiLaExistenciaDelInmueble() {
        long id=propiedad();
        Actor vecino=EspacioIndividualB0DePrueba.crear(jdbc,new org.springframework.transaction.support.TransactionTemplate(gestor));
        assertThrows(com.controllocal.service.excepcion.NoEncontradoException.class,
                ()->de("estado_de_propiedad","PROPIEDAD",id,vecino));
    }

    @Test void agenteConProspeccionAutorizadaLeeSinObtenerEdicionFisica() {
        long id=propiedad();
        var otro=equipo.colega();
        sesion(otro);
        assertThrows(com.controllocal.service.excepcion.NoEncontradoException.class,
                ()->de("estado_de_propiedad","PROPIEDAD",id,otro));
        jdbc.update("""
            insert into prospeccion(codigo_prospeccion,estado,id_propiedad,id_rol_agente,organizacion_id)
            values(?,'P',?,?,?)
            ""","PRO-CAP-"+id,id,otro.idRolOperativo(),otro.idOrganizacion());
        assertEquals(Estado.HABILITADA,de("estado_de_propiedad","PROPIEDAD",id,otro).estado());
        assertEquals(Estado.PERMISO_INSUFICIENTE,de("editar_propiedad","PROPIEDAD",id,otro).estado());
        assertEquals(agente.idRolOperativo(),jdbc.queryForObject("select id_rol_responsable from propiedad where id_propiedad=?",Long.class,id));
    }

    @Test void gobernarNoConcedeLasCapacidadesComerciales() {
        var gobierno=equipo.admin();
        var lista=capacidades.consultar(Consulta.general(),gobierno).capacidades();
        for(String id:List.of("confirmar_captura","decidir_captacion","evaluar_solicitud","registrar_cobro","editar_propiedad")) {
            assertEquals(Estado.PERMISO_INSUFICIENTE,lista.stream().filter(c->id.equals(c.idCore())).findFirst().orElseThrow().estado(),id);
        }
    }

    @Test void losParametrosNoPuedenSuplantarElContextoDeSesion() throws Exception {
        mvc.perform(get("/capacidades").param("idCore","buscar_propiedades")
                        .param("organizacionId","9999").param("personaId","9999").param("rol","BROKER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(CapacidadesService.VERSION))
                .andExpect(jsonPath("$.capacidades[0].contexto.organizacionId").value(agente.idOrganizacion()))
                .andExpect(jsonPath("$.capacidades[0].contexto.personaId").value(agente.idPersona()))
                .andExpect(jsonPath("$.capacidades[0].contexto.rol").value("AGENTE"));
    }

    @Test void definicionActualYResultadoAutorizadoVacioNoRestauranElCatalogoRetirado() throws Exception {
        mvc.perform(get("/interacciones/definicion"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(com.controllocal.service.actividad.CatalogoInteracciones.VERSION));
        mvc.perform(get("/interacciones").param("contexto","PROPIEDAD").param("referencia",Long.toString(propiedad())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRecords").value(0));
        // El antiguo catálogo por contexto se retiró enC4: no volver a implementarlo para hacer verde esta prueba.
        mvc.perform(get("/interacciones/resultados").param("contexto","PROSPECCION"))
                .andExpect(status().isBadRequest());
    }

    @Test void atributoHistoricoNoAdquiereEdicionAlCambiarDeTipo() {
        long id=propiedad();
        var respuesta=capacidades.consultar(new Consulta("editar_propiedad","PROPIEDAD",Long.toString(id),
                null,"servicios_disponibles",null, null),agente).capacidades().getFirst();
        assertEquals(Estado.DESHABILITADA,respuesta.estado());
        assertEquals("ATRIBUTO_NO_EDITABLE",respuesta.motivo());
    }

    @Test void puertaRealDeshabilitadaNoSeEjecutaAunqueElClienteOmitaElDescriptor() throws Exception {
        doReturn(false).when(registro).habilitadas(List.of("buscar_propiedades"));
        mvc.perform(get("/propiedades").param("texto","no ejecutar esta búsqueda"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CAPACIDAD_DESHABILITADA"));
    }

    @Test void consultasConcurrentesNoCompartenLaDecisionDeOtroTenant() throws Exception {
        long id=propiedad();
        var vecino=EspacioIndividualB0DePrueba.crear(jdbc,new org.springframework.transaction.support.TransactionTemplate(gestor));
        var piscina=java.util.concurrent.Executors.newFixedThreadPool(4);
        var salida=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
        var barrera=new java.util.concurrent.CyclicBarrier(4);
        try {
            for(int i=0;i<4;i++) {
                final boolean propio=i%2==0;
                salida.add(piscina.submit(()->{
                    barrera.await(30,java.util.concurrent.TimeUnit.SECONDS);
                    try {
                        var d=de("estado_de_propiedad","PROPIEDAD",id,propio?agente:vecino);
                        return propio && d.estado()==Estado.HABILITADA && d.contexto().organizacionId()==agente.idOrganizacion();
                    } catch(com.controllocal.service.excepcion.NoEncontradoException e) { return !propio; }
                }));
            }
            for(var resultado:salida)assertTrue(resultado.get(60,java.util.concurrent.TimeUnit.SECONDS));
        } finally { piscina.shutdownNow(); }
    }

    @Test void capturaUsaSuDefinicionRealParaAtributoYOperacionSinPersistirOtroAvance() {
        var b=captura.avanzar(new MotorDeCaptura.Avance("REGISTRAR_PROPIEDAD", null, null, respuestas(Map.of("tipoPropiedad","DEPARTAMENTO","operaciones","VENTA,ALQUILER")), null), null, agente);
        var fisico=capacidades.consultar(new Consulta("responder_captura","CAPTURA",b.idBorrador().toString(),null,"piso",null, null),agente)
                .capacidades().getFirst();
        assertEquals(Estado.HABILITADA,fisico.estado());
        var ambiguo=capacidades.consultar(new Consulta("responder_captura","CAPTURA",b.idBorrador().toString(),null,"importe",null, null),agente)
                .capacidades().getFirst();
        assertEquals(Estado.CONTEXTO_REQUERIDO,ambiguo.estado());
        assertEquals("OPERACION_REQUERIDA",ambiguo.motivo());
        assertEquals(b.conocido(),captura.consultar(b.idBorrador(),agente).conocido());
    }

    @Test void describirNoCreaHechosAuditoriaNiOtraCaptura() {
        long id=propiedad();
        var antes=conteos();
        capacidades.consultar(new Consulta(null,"PROPIEDAD",Long.toString(id),null,null,null, null),agente);
        capacidades.consultar(Consulta.general(),agente);
        assertEquals(antes,conteos());
    }

    private Map<String,Long> conteos() {
        Map<String,Long> r=new LinkedHashMap<>();
        for(String tabla:List.of("propiedad","borrador_captura","historial_estado","evento_dominio","atributo_propiedad")) {
            r.put(tabla,jdbc.queryForObject("select count(*) from "+tabla,Long.class));
        }
        return r;
    }

    private static java.util.Map<String,MotorDeCaptura.Respuesta> respuestas(java.util.Map<String,String> datos) {
        var resultado=new java.util.LinkedHashMap<String,MotorDeCaptura.Respuesta>();
        datos.forEach((clave,valor)->resultado.put(clave,MotorDeCaptura.Respuesta.conocida(valor)));
        return resultado;
    }
}
