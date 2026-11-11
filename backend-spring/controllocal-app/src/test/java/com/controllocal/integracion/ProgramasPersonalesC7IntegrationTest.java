package com.controllocal.integracion;
import com.controllocal.app.ControlLocalApplication;
import com.controllocal.integracion.soporte.*;
import com.controllocal.domain.planes.*;
import com.controllocal.persistence.presupuesto.*;
import com.controllocal.service.*;
import com.controllocal.service.actividad.*;
import com.controllocal.service.planes.DerechosDeInformesC7.Composicion;
import com.controllocal.service.excepcion.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.service.actividad.ProgramasPersonales.*;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"controllocal.programas.habilitado=false","spring.datasource.hikari.maximum-pool-size=6"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters=false)
class ProgramasPersonalesC7IntegrationTest {
    @DynamicPropertySource static void base(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    @Autowired ApplicationContext app;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager gestor;
    @Autowired ProgramasPersonales servicio;@Autowired ContextosDeActividad tiempo;
    @Autowired org.springframework.test.web.servlet.MockMvc http;
    TransactionTemplate tx;Actor a;com.controllocal.service.actuacion.ActuacionesService.ContextoSesion sid;Instant ahora;
    @BeforeEach void preparar(TestInfo info){EvidenciaC4DePrueba.iniciar(info);tx=new TransactionTemplate(gestor);a=EspacioIndividualB0DePrueba.crear(jdbc,tx);sid=com.controllocal.service.actuacion.ActuacionesService.ContextoSesion.de(SesionesDePrueba.abrir(jdbc,app.getBean(ContextosService.class),a).acceso());ahora=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);reloj(ahora);financiar();}
    @AfterEach void terminar(){if(a!=null)jdbc.update("update plan_tarea_acotado set estado='FINALIZADO' where organizacion_id=?",a.idOrganizacion());ReflectionTestUtils.setField(tiempo,"reloj",Clock.systemUTC());org.springframework.security.core.context.SecurityContextHolder.clearContext();EvidenciaC4DePrueba.limpiar();}
    void reloj(Instant i){ReflectionTestUtils.setField(tiempo,"reloj",Clock.fixed(i,ZoneOffset.UTC));}
    void financiar(){var plan=PlanBrox.ONE_PRO;tx.executeWithoutResult(t->new CiclosPagadosB0Repository(jdbc).nuevoCiclo(a.idOrganizacion(),UUID.randomUUID(),plan.name(),ahora.minusSeconds(30),ahora.plusSeconds(86400),ahora.atZone(CalendarioComercial.ZONA).getDayOfMonth(),plan.precioUsd(),UUID.randomUUID(),"TEST SINTETICO NO PAGO REAL",0,ahora));}
    Configuracion mandato(){return new Configuracion(Familia.ALERTA_CONDICIONADA,"Revisar mis pendientes TEST",null,null,new Condicion(Metrica.OPORTUNIDADES_ABIERTAS,Comparador.MAYOR_O_IGUAL,BigDecimal.ZERO),null,ahora,ahora.plusSeconds(7200),300,3,2,"BANDEJA_PRIVADA");}
    Vista crear(){return servicio.configurar(new DatosConfiguracion(null,null,mandato()),a,sid,UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());}
    @Test void resumenRecurrenteConservaFiltrosFechasYAlcanceSinReinterpretarLaCohorte(){
        var f=new FiltrosDeInforme.Filtros("VENTA",null,null,new FiltrosDeInforme.Situacion("ENCARGO","P"),null);
        var q=new Configuracion(Familia.RESUMEN_PROGRAMADO,"Resumen de mis fuentes TEST53",null,new InformesC7.Consulta(Composicion.EVOLUCION_MEJORA,null,null,null,null,f,"ALQUILER"),null,14,ahora,ahora.plusSeconds(7200),300,3,2,"BANDEJA_PRIVADA");
        var p=servicio.configurar(new DatosConfiguracion(null,null,q),a,sid,UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());
        assertEquals(f,servicio.consultar(p.referencia(),a).mandato().informe().filtros());
        var r=servicio.reclamar().orElseThrow();assertTrue(servicio.finalizar(r,servicio.producir(r)));
        var json=app.getBean(com.controllocal.service.soporte.Documentos.class).objetoCanonico(servicio.resultado(p.referencia(),r.paso().referencia(),a).resultado());
        var datos=app.getBean(com.controllocal.service.soporte.Documentos.class).comoObjeto(json,com.fasterxml.jackson.databind.JsonNode.class);
        assertEquals("VENTA",datos.path("bloques").path("filtros").path("operacion").asText());
        assertEquals("ALQUILER",datos.path("bloques").path("alcances").path("cohortes").path("operacion").asText());
        assertEquals("OPORTUNIDADES_ABIERTAS_EN_PERIODO_HASTA_CORTE",datos.path("bloques").path("alcances").path("cohortes").path("universo").asText());
        assertEquals(ahora.atZone(CalendarioComercial.ZONA).toLocalDate().minusDays(13).toString(),datos.path("desde").asText());
    }
    @Test void ocurrenciaDurableEntregaPrivadaYRepeticionNoGeneraOtroEfecto(){var p=crear();var r=servicio.reclamar().orElseThrow();assertEquals(p.referencia(),r.programa().referencia());var d=servicio.producir(r);assertTrue(servicio.finalizar(r,d));assertTrue(servicio.reclamar().isEmpty());var vista=servicio.resultado(p.referencia(),r.paso().referencia(),a);assertEquals("COMPLETADA",vista.estado());assertNotNull(vista.resultado());assertThrows(ConflictoException.class,()->servicio.finalizar(r,d));assertEquals(1,servicio.ocurrencias(p.referencia(),1,20,a).size());}
    @Test void dosEjecutoresNoReclamanLaMismaOcurrencia()throws Exception{var p=crear();var ex=Executors.newFixedThreadPool(2);var puerta=new CountDownLatch(1);try{List<Future<Optional<Reclamo>>> fs=new ArrayList<>();for(int i=0;i<2;i++)fs.add(ex.submit(()->{puerta.await();return servicio.reclamar();}));puerta.countDown();long n=0;for(var f:fs)if(f.get(30,TimeUnit.SECONDS).isPresent())n++;assertEquals(1,n);}finally{ex.shutdownNow();}assertEquals(1,servicio.ocurrencias(p.referencia(),1,20,a).size());}
    @Test void recuperacionConservaIdentidadYElTokenAnteriorNoPuedeFinalizar(){var p=crear();var r1=servicio.reclamar().orElseThrow();var d=servicio.producir(r1);reloj(ahora.plusSeconds(61));var r2=servicio.reclamar().orElseThrow();assertEquals(r1.paso().referencia(),r2.paso().referencia());assertNotEquals(r1.paso().reclamo(),r2.paso().reclamo());assertEquals(2,r2.paso().intentos());assertThrows(ConflictoException.class,()->servicio.finalizar(r1,d));servicio.fallar(r1,new RuntimeException("Proceso antiguo"));assertTrue(servicio.finalizar(r2,servicio.producir(r2)));assertEquals(1,servicio.ocurrencias(p.referencia(),1,20,a).size());}
    @Test void rollbackNoDejaMandatoNiEvento(){tx.executeWithoutResult(t->{crear();t.setRollbackOnly();});assertTrue(servicio.listar(1,20,a).isEmpty());assertTrue(servicio.reclamar().isEmpty());}
    @Test void perdidaDeProPausaYBloqueaLaEntregaDelResultado(){var p=crear();var r=servicio.reclamar().orElseThrow();servicio.finalizar(r,servicio.producir(r));jdbc.update("update b0_ciclo set revocado=true where organizacion_id=?",a.idOrganizacion());assertThrows(CapacidadDeshabilitadaException.class,()->servicio.resultado(p.referencia(),r.paso().referencia(),a));reloj(ahora.plusSeconds(301));assertTrue(servicio.reclamar().isEmpty());assertEquals("PAUSADO",servicio.consultar(p.referencia(),a).estado());}
    @Test void reintentosFinitosConservanLaOcurrenciaYSanitizanElDiagnostico(){
        var p=crear();var uno=servicio.reclamar().orElseThrow();servicio.fallar(uno,new IllegalStateException("dato privado TEST no debe salir"));assertTrue(servicio.reclamar().isEmpty());reloj(ahora.plusSeconds(301));
        var dos=servicio.reclamar().orElseThrow();assertEquals(uno.paso().referencia(),dos.paso().referencia());assertEquals(2,dos.paso().intentos());servicio.fallar(dos,new IllegalStateException("otro dato privado TEST"));
        assertEquals("PAUSADO",servicio.consultar(p.referencia(),a).estado());assertEquals("REINTENTOS_AGOTADOS",servicio.consultar(p.referencia(),a).causa());assertTrue(servicio.reclamar().isEmpty());
        var pasos=servicio.ocurrencias(p.referencia(),1,20,a);assertEquals(1,pasos.size());assertEquals("REINTENTOS_AGOTADOS",pasos.getFirst().causa());assertNull(pasos.getFirst().resultado());
    }
    @Test void comparacionIncompletaYRecurrenciaNoRepresentableSeRechazanAntesDeCrearMandato(){
        var q=mandato();var imposible=new Configuracion(q.familia(),q.finalidad(),null,null,q.condicion(),null,ahora,q.venceEn(),Long.MAX_VALUE,3,2,"BANDEJA_PRIVADA");
        assertThrows(ReglaNegocioException.class,()->servicio.configurar(new DatosConfiguracion(null,null,imposible),a,sid,UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla()));
        var incompleto=new Configuracion(Familia.RESUMEN_PROGRAMADO,"Comparación TEST",null,new InformesC7.Consulta(Composicion.EVOLUCION_MEJORA,null,null,ahora.atZone(CalendarioComercial.ZONA).toLocalDate().minusDays(20),null),null,7,ahora,q.venceEn(),300,3,2,"BANDEJA_PRIVADA");
        assertThrows(ReglaNegocioException.class,()->servicio.configurar(new DatosConfiguracion(null,null,incompleto),a,sid,UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla()));assertTrue(servicio.listar(1,20,a).isEmpty());
    }
    @Test void otraOrganizacionNoObtieneProgramaNiOcurrencias(){var p=crear();var b=EspacioIndividualB0DePrueba.crear(jdbc,tx);assertThrows(NoEncontradoException.class,()->servicio.consultar(p.referencia(),b));assertThrows(NoEncontradoException.class,()->servicio.ocurrencias(p.referencia(),1,20,b));}
    @Test void cambioDeMandatoInvalidaElResultadoAnteriorSinAvalancha(){var p=crear();var r=servicio.reclamar().orElseThrow();servicio.finalizar(r,servicio.producir(r));servicio.cambiarEstado(p.referencia(),new CambioEstado(p.version(),AccionEstado.PAUSAR,"Revisión TEST",null),a,sid,UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());assertThrows(CapacidadDeshabilitadaException.class,()->servicio.resultado(p.referencia(),r.paso().referencia(),a));var v=servicio.consultar(p.referencia(),a);servicio.cambiarEstado(p.referencia(),new CambioEstado(v.version(),AccionEstado.REANUDAR,"Reanudar TEST",ahora.plusSeconds(1000)),a,sid,UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());assertTrue(servicio.reclamar().isEmpty());}
    @Test void noPermiteDestinoExternoNiMandatoSinLimites(){var q=mandato();var externo=new Configuracion(q.familia(),q.finalidad(),null,null,q.condicion(),null,ahora,q.venceEn(),300,3,2,"WHATSAPP");assertThrows(ReglaNegocioException.class,()->servicio.configurar(new DatosConfiguracion(null,null,externo),a,sid,"externo",com.controllocal.service.soporte.Procedencia.deLaPantalla()));}
    @Test void dosCambiosConcurrentesSobreLaMismaVersionNoMezclanMandatos()throws Exception{
        var p=crear();var ex=Executors.newFixedThreadPool(2);var puerta=new CountDownLatch(1);try{
            List<Future<String>> fs=new ArrayList<>();for(int i=0;i<2;i++)fs.add(ex.submit(()->{puerta.await();try{servicio.configurar(new DatosConfiguracion(p.referencia(),p.version(),mandato()),a,sid,UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());return "APLICADO";}catch(ConflictoException esperado){return "CONFLICTO";}}));
            puerta.countDown();var resultados=new ArrayList<String>();for(var f:fs)resultados.add(f.get(30,TimeUnit.SECONDS));assertEquals(1,Collections.frequency(resultados,"APLICADO"));assertEquals(1,Collections.frequency(resultados,"CONFLICTO"));
        }finally{ex.shutdownNow();}assertEquals(p.version()+1,servicio.consultar(p.referencia(),a).version());assertEquals(1,servicio.listar(1,20,a).size());
    }
    void como(Actor actor){var principal=SesionesDePrueba.abrir(jdbc,app.getBean(ContextosService.class),actor);org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_"+actor.rolEfectivo())));}
    @Test void transporteDistingueSinBaseCapacidadDeshabilitadaYFaltaDePermiso()throws Exception{
        // Principal TEST posterior a identidad: valida MVC, contexto y método; no afirma login JWT.
        como(a);jdbc.update("update b0_ciclo set revocado=true where organizacion_id=?",a.idOrganizacion());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/indicadores/mediciones")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.seguimiento.estadoCalculo").value("SIN_BASE"));
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/indicadores/informes").contentType("application/json").content("{\"composicion\":\"EVOLUCION_MEJORA\"}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.codigo").value("CAPACIDAD_DESHABILITADA"));
        var banco=new OrigenesDePrueba(jdbc,gestor,app.getBean(com.controllocal.service.captura.MotorDeCaptura.class),app.getBean(ContextosService.class));var e=banco.empresa();como(e.admin());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/indicadores/mediciones")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/programas-personales")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
    @Test void reintentarUnaOrdenDevuelveSuReciboOriginalSinRevertirElEstadoPosterior(){
        String clave=UUID.randomUUID().toString();var datos=new DatosConfiguracion(null,null,mandato());var proc=com.controllocal.service.soporte.Procedencia.deLaPantalla();
        var creada=servicio.configurar(datos,a,sid,clave,proc);var estado=new CambioEstado(creada.version(),AccionEstado.PAUSAR,"Revisar mandato TEST",null);String pausa=UUID.randomUUID().toString();
        var pausada=servicio.cambiarEstado(creada.referencia(),estado,a,sid,pausa,proc);var cancelada=servicio.cambiarEstado(creada.referencia(),new CambioEstado(pausada.version(),AccionEstado.CANCELAR,"Concluir programa TEST",null),a,sid,UUID.randomUUID().toString(),proc);
        assertEquals(creada,servicio.configurar(datos,a,sid,clave,proc));assertEquals(pausada,servicio.cambiarEstado(creada.referencia(),estado,a,sid,pausa,proc));assertEquals(cancelada,servicio.consultar(creada.referencia(),a));
        assertEquals(1,jdbc.queryForObject("select count(*) from plan_tarea_acotado where organizacion_id=?",Integer.class,a.idOrganizacion()));
        assertEquals(3,jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and tipo in ('CONFIGURAR_PROGRAMA_PERSONAL','ESTADO_PROGRAMA_PERSONAL')",Integer.class,a.idOrganizacion()));
    }
    @Test void resumenRecurrenteProduceLasMedicionesSinOcultarLosFaltantesNiExponerFuentesPrivadas(){
        var q=new Configuracion(Familia.RESUMEN_PROGRAMADO,"Resumen privado TEST",null,new InformesC7.Consulta(Composicion.EVOLUCION_MEJORA,null,null,null,null),null,7,ahora,ahora.plusSeconds(7200),300,3,2,"BANDEJA_PRIVADA");
        var p=servicio.configurar(new DatosConfiguracion(null,null,q),a,sid,UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());var r=servicio.reclamar().orElseThrow();assertEquals(p.referencia(),r.programa().referencia());servicio.finalizar(r,servicio.producir(r));
        var salida=servicio.resultado(p.referencia(),r.paso().referencia(),a);var texto=app.getBean(com.controllocal.service.soporte.Documentos.class).objetoCanonico(salida);
        assertTrue(texto.contains("mediciones"));assertTrue(texto.contains("SIN_BASE"));assertFalse(texto.contains("fuentesInteracciones"));assertFalse(texto.contains("fuentesSeguimientos"));assertFalse(texto.contains("limiteCreditos"));
    }
    long propiedad(boolean conEncargo){
        var op=conEncargo?List.of(new PropiedadUniversalService.OperacionSolicitada("VENTA",new BigDecimal("100000"),"PEN",null,null,null,null,null,null,null,List.of())):List.<PropiedadUniversalService.OperacionSolicitada>of();
        return app.getBean(PropiedadUniversalService.class).registrar(new PropiedadUniversalService.ComandoRegistro(UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla(),null,"DEPARTAMENTO","VIVIENDA","Departamento TEST P0",new PropiedadUniversalService.Ubicacion("Dirección ficticia TEST "+UUID.randomUUID(),"Surco",null,null,null,"TEST-301",null,null,null),List.of(),List.of(),op,null),a).idPropiedad();
    }
    Vista configurarFamilia(Familia f,Recurso recurso){return servicio.configurar(new DatosConfiguracion(null,null,new Configuracion(f,"Preparación privada TEST",recurso,null,null,f==Familia.REPORTE_PROGRAMADO?7:null,ahora,ahora.plusSeconds(7200),300,3,2,"BANDEJA_PRIVADA")),a,sid,UUID.randomUUID().toString(),com.controllocal.service.soporte.Procedencia.deLaPantalla());}
    @Test void recordatorioReutilizaR27SinEnviarNiCrearUnaInteraccion(){
        long prop=propiedad(false);var ps=app.getBean(ProspeccionService.class);var p=ps.registrar(new ProspeccionService.DatosProspeccion(prop,"Gestión TEST",new ProspeccionService.DestinatarioDeclarado("GESTOR_COMERCIAL","Gestora TEST","900000000",null,"Declaración contextual TEST")),a);
        var programa=configurarFamilia(Familia.RECORDATORIO_PROPUESTA,new Recurso("PROSPECCION",p.id(),p.version()));new com.controllocal.app.arranque.EjecutorProgramasPersonales(servicio).ejecutarUna();var paso=servicio.ocurrencias(programa.referencia(),1,20,a).getFirst();assertEquals("COMPLETADA",paso.estado());assertNotNull(servicio.resultado(programa.referencia(),paso.referencia(),a).resultado());assertEquals(0,jdbc.queryForObject("select count(*) from interaccion_comercial where organizacion_id=?",Integer.class,a.idOrganizacion()));
        String prueba=jdbc.queryForObject("select prueba_json from paso_tarea where organizacion_id=? and referencia=?",String.class,a.idOrganizacion(),paso.referencia());assertFalse(prueba.contains("Gestora TEST"));assertFalse(prueba.contains("900000000"));assertFalse(prueba.contains("contactoDeclarado"));
    }
    @Test void reporteProgramadoPreparaElAvanceSinDeclararComunicacion(){
        long prop=propiedad(true);long id=jdbc.queryForObject("select id_captacion from captacion where organizacion_id=? and id_propiedad=?",Long.class,a.idOrganizacion(),prop);var e=app.getBean(com.controllocal.service.encargo.EncargoService.class).consultar(id,a);
        var p=configurarFamilia(Familia.REPORTE_PROGRAMADO,new Recurso("ENCARGO",id,e.version()));var r=servicio.reclamar().orElseThrow();servicio.finalizar(r,servicio.producir(r));String resultado=app.getBean(com.controllocal.service.soporte.Documentos.class).objetoCanonico(servicio.resultado(p.referencia(),r.paso().referencia(),a));assertTrue(resultado.contains("NO_REALIZADA"));assertTrue(resultado.contains("reportePreparado"));assertEquals(0,jdbc.queryForObject("select count(*) from reporte_propietario where organizacion_id=?",Integer.class,a.idOrganizacion()));
    }
    @Test void perderElPermisoDuranteLaEjecucionNoEntregaUnResultado(){
        var p=crear();var r=servicio.reclamar().orElseThrow();var d=servicio.producir(r);jdbc.update("update b0_ciclo set revocado=true where organizacion_id=?",a.idOrganizacion());assertThrows(CapacidadDeshabilitadaException.class,()->servicio.finalizar(r,d));servicio.fallar(r,new CapacidadDeshabilitadaException());assertEquals("PAUSADO",servicio.consultar(p.referencia(),a).estado());assertNull(servicio.ocurrencias(p.referencia(),1,20,a).getFirst().resultado());
    }
}
