package com.controllocal.integracion;
import com.controllocal.service.*;
import com.controllocal.service.actividad.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.integracion.soporte.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.integracion.soporte.FormalizacionesDePrueba.*;
import static com.controllocal.service.InteraccionService.*;
import static com.controllocal.service.actividad.CatalogoInteracciones.*;

@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
class CompromisosC7IntegrationTest extends OrigenConversionC7IntegrationTest {
    @DynamicPropertySource static void baseC7(DynamicPropertyRegistry p){BaseDeDatosDePruebas.registrar(p);}
    CompromisosDeSeguimiento servicio(){return contexto.getBean(CompromisosDeSeguimiento.class);}
    com.controllocal.service.actuacion.ComandosAutomaticosService automaticos(){return contexto.getBean(com.controllocal.service.actuacion.ComandosAutomaticosService.class);}
    com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico correlacion(){return new com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico("C7-TEST-"+UUID.randomUUID(),"turno-test",null);}
    ContextosDeActividad tiempo(){return contexto.getBean(ContextosDeActividad.class);}
    void reloj(Instant i){ReflectionTestUtils.setField(tiempo(),"reloj",Clock.fixed(i,ZoneOffset.UTC));}
    @AfterEach void restaurar(){ReflectionTestUtils.setField(tiempo(),"reloj",Clock.systemUTC());}
    CompromisosDeSeguimiento.Plan plan(ProspeccionService.RevisionSeguimiento s,LocalDate fecha){return new CompromisosDeSeguimiento.Plan(s.version(),null,null,OrigenActuacion.INTENCION_PROFESIONAL,fecha,TipoActividad.LLAMADA,"Llamar para recibir respuesta comercial",false,null,null);}
    @Test void entregaP0DeCompromisoVerificaOrganizacionFuenteYVersionAntesDeHidratar(){
        var c=caso();var p=prospectar(c,c.agente());var s=prospecciones.seguimientos(p.id(),c.agente()).getFirst();
        var v=servicio().planificar(p.id(),s.id(),plan(s,hoy().plusDays(2)),c.agente(),c.sesion(),clave(),procedencia());
        long id=b.jdbc.queryForObject("select id_compromiso from compromiso_seguimiento where organizacion_id=? and referencia=?",Long.class,c.agente().idOrganizacion(),v.referencia());
        var prueba=new ProgramasPersonales.PruebaEntrega(List.of(new ProgramasPersonales.Recurso("COMPROMISO_R27",id,v.version())),List.of(),List.of());
        var generador=contexto.getBean(GeneradorProgramaPersonal.class);assertDoesNotThrow(()->generador.exigirEntrega(prueba,c.agente()));
        assertThrows(AccesoNoAutorizadoException.class,()->generador.exigirEntrega(prueba,c.equipo().colega()));
        var ajeno=caso();assertThrows(NoEncontradoException.class,()->generador.exigirEntrega(prueba,ajeno.agente()));
        servicio().resolver(p.id(),v.referencia(),new CompromisosDeSeguimiento.Resolucion(v.version(),hoy(),"Desistimiento explícito TEST53","Constancia sintética de no continuidad"),c.agente(),c.sesion(),clave(),procedencia());
        assertThrows(ConflictoException.class,()->generador.exigirEntrega(prueba,c.agente()));
    }
    @Test void reasignarConservaPlazoYAutoriaSinCargarElCompromisoAlNuevoResponsable(){
        var c=caso();var p=prospecciones.registrar(new ProspeccionService.DatosProspeccion(c.propiedad(),"Gestión TEST",new ProspeccionService.DestinatarioDeclarado("GESTOR_COMERCIAL","Contacto TEST","900000000",null,"Declaró recibir propuestas TEST")),c.agente());
        var r=prospecciones.seguimientos(p.id(),c.agente()).getFirst();var comprometido=servicio().planificar(p.id(),r.id(),plan(r,hoy()),c.agente(),c.sesion(),clave(),procedencia());
        var reasignacion=contexto.getBean(ReasignacionDeProspeccion.class);var observado=reasignacion.consultar(p.id(),c.equipo().broker());
        reasignacion.reasignar(new ReasignacionDeProspeccion.Datos(p.id(),observado.version(),c.agente().idRolOperativo(),c.equipo().colega().idRolOperativo(),"Continuidad de gestión TEST"),c.equipo().broker(),b.sesiones.de(c.equipo().broker()),clave(),procedencia());
        var conservado=servicio().listar(p.id(),c.equipo().colega()).getFirst();assertEquals(comprometido.referencia(),conservado.referencia());assertEquals(comprometido.compromiso().plazo(),conservado.compromiso().plazo());assertEquals(c.agente().idRolOperativo(),conservado.responsableHistorico());assertThrows(AccesoNoAutorizadoException.class,()->servicio().listar(p.id(),c.agente()));
        reloj(Instant.now().plusSeconds(86400));var v=new IndicadoresIndividuales.Ventana(hoy().withDayOfMonth(1),hoy().plusDays(1),tiempo().ahora());var m=contexto.getBean(MedicionesC7.class);
        assertEquals(1,m.porResponsable(v,c.equipo().broker(),c.agente().idRolOperativo()).seguimiento().medicion().evaluables());assertEquals(0,m.porResponsable(v,c.equipo().broker(),c.equipo().colega().idRolOperativo()).seguimiento().medicion().evaluables());assertEquals(0,m.medir(v,c.equipo().colega()).seguimiento().medicion().evaluables());
    }
    @Test void sugerenciaNoEsCompromisoAsumirSiYRepetirNoDuplica(){
        var c=caso();var p=prospectar(c,c.agente());var r=prospecciones.seguimientos(p.id(),c.agente()).getFirst();assertTrue(servicio().listar(p.id(),c.agente()).isEmpty());
        String k=clave();var d=plan(r,hoy());var v=servicio().planificar(p.id(),r.id(),d,c.agente(),c.sesion(),k,procedencia());
        assertEquals(v,servicio().planificar(p.id(),r.id(),d,c.agente(),c.sesion(),k,procedencia()));assertEquals(1,servicio().listar(p.id(),c.agente()).size());
        assertEquals(1,b.jdbc.queryForObject("select count(*) from compromiso_seguimiento_version where organizacion_id=? and id_compromiso=(select id_compromiso from compromiso_seguimiento where referencia=?)",Integer.class,c.agente().idOrganizacion(),v.referencia()));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio().listar(p.id(),c.equipo().colega()));var ajena=caso();assertThrows(NoEncontradoException.class,()->servicio().listar(p.id(),ajena.agente()));
    }
    @Test void rollbackYCASConcurrenteConservanUnSoloCompromiso()throws Exception{
        var c=caso();var p=prospectar(c,c.agente());var r=prospecciones.seguimientos(p.id(),c.agente()).getFirst();var d=plan(r,hoy());
        new TransactionTemplate(gestor).executeWithoutResult(t->{servicio().planificar(p.id(),r.id(),d,c.agente(),c.sesion(),clave(),procedencia());t.setRollbackOnly();});assertTrue(servicio().listar(p.id(),c.agente()).isEmpty());
        String k=clave();var ex=Executors.newFixedThreadPool(2);var puerta=new CountDownLatch(1);
        try{List<Future<CompromisosDeSeguimiento.Vista>> fs=new ArrayList<>();for(int i=0;i<2;i++)fs.add(ex.submit(()->{puerta.await();return servicio().planificar(p.id(),r.id(),d,c.agente(),c.sesion(),k,procedencia());}));puerta.countDown();assertEquals(fs.get(0).get(30,TimeUnit.SECONDS),fs.get(1).get(30,TimeUnit.SECONDS));}finally{ex.shutdownNow();}
        assertEquals(1,servicio().listar(p.id(),c.agente()).size());
    }
    @Test void reprogramacionTardiaNoBorraElIncumplimientoYSuHistoriaEsInmutable(){
        var c=caso();var p=prospectar(c,c.agente());var s=prospecciones.seguimientos(p.id(),c.agente()).getFirst();var v=servicio().planificar(p.id(),s.id(),plan(s,hoy()),c.agente(),c.sesion(),clave(),procedencia());
        reloj(Instant.now().plusSeconds(2*86400));var actual=prospecciones.seguimientos(p.id(),c.agente()).getFirst();
        var nuevo=new CompromisosDeSeguimiento.Plan(actual.version(),v.referencia(),v.version(),v.compromiso().origen(),hoy().plusDays(3),v.compromiso().actividad(),v.compromiso().detalle(),false,null,"Cambio de planificación profesional TEST");
        var cambiado=servicio().planificar(p.id(),s.id(),nuevo,c.agente(),c.sesion(),clave(),procedencia());assertEquals(v.compromiso().plazo(),cambiado.compromiso().plazo());assertEquals(1,cambiado.compromiso().cambios().size());
        var medicion=contexto.getBean(MedicionDeCompromisos.class).leer(c.agente(),new IndicadoresIndividuales.Ventana(hoy().minusDays(1),hoy().plusDays(3),tiempo().ahora()),contexto.getBean(InteraccionService.class).indicadores(c.agente()));
        assertTrue(medicion.verificable());assertEquals(1,medicion.medicion().evaluables());assertEquals(0,medicion.medicion().exitos());assertEquals("EVALUABLE",medicion.medicion().disponibilidad());
        assertThrows(org.springframework.dao.DataAccessException.class,()->b.jdbc.update("delete from compromiso_seguimiento_version where organizacion_id=? and id_compromiso=(select id_compromiso from compromiso_seguimiento where referencia=?)",c.agente().idOrganizacion(),v.referencia()));
    }
    @Test void cambiarAcuerdoExternoExigeNuevaConstanciaYResolverNoInventaLlamada(){
        var c=caso();var p=prospectar(c,c.agente());var s=prospecciones.seguimientos(p.id(),c.agente()).getFirst();var d=new CompromisosDeSeguimiento.Plan(s.version(),null,null,OrigenActuacion.ACUERDO_EXTERNO,hoy().plusDays(2),TipoActividad.LLAMADA,"Llamar a la gestora el día acordado",false,null,null);
        var v=servicio().planificar(p.id(),s.id(),d,c.agente(),c.sesion(),clave(),procedencia());var r=prospecciones.seguimientos(p.id(),c.agente()).getFirst();
        assertThrows(ReglaNegocioException.class,()->servicio().planificar(p.id(),s.id(),new CompromisosDeSeguimiento.Plan(r.version(),v.referencia(),v.version(),d.origen(),hoy().plusDays(3),d.actividad(),d.detalle(),false,null,"Decisión unilateral"),c.agente(),c.sesion(),clave(),procedencia()));
        long antes=b.jdbc.queryForObject("select count(*) from interaccion_comercial where organizacion_id=?",Long.class,c.agente().idOrganizacion());
        var res=servicio().resolver(p.id(),v.referencia(),new CompromisosDeSeguimiento.Resolucion(v.version(),hoy(),"La familia desiste","Declaración recibida TEST"),c.agente(),c.sesion(),clave(),procedencia());assertNotNull(res.compromiso().resolucion());assertTrue(res.compromiso().actuaciones().isEmpty());assertEquals(antes,b.jdbc.queryForObject("select count(*) from interaccion_comercial where organizacion_id=?",Long.class,c.agente().idOrganizacion()));
    }
    DatosInteraccion llamada(long p,ParticipanteDeclarado destinatario){return new DatosInteraccion(List.of(new VinculoActividad(TipoContexto.PROSPECCION,p,null,null)),destinatario,null,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,List.of(new EventoDeclarado("llamada-test",EventoComunicacion.LLAMADA_SALIENTE,new MomentoHecho(hoy(),null,null))),ResultadoInteraccion.SIN_RESPUESTA,new MomentoHecho(hoy(),null,null),"Intento real TEST",List.of(),null,null,"Declaración del profesional TEST",null,new GestionContacto(Set.of(FinalidadContacto.PROSPECCION_COMERCIAL),Set.of(),null,false,null));}
    @Test void llamadaSinRespuestaCumpleLoPrometidoSoloConElDestinatarioCorrectoYUnaRectificacionNoHeredaCumplimiento(){
        var c=caso();var p=prospectar(c,c.agente());var s=prospecciones.seguimientos(p.id(),c.agente()).getFirst();var v=servicio().planificar(p.id(),s.id(),plan(s,hoy()),c.agente(),c.sesion(),clave(),procedencia());var interacciones=contexto.getBean(InteraccionService.class);
        var ajena=automaticos().registrarInteraccion(llamada(p.id(),new ParticipanteDeclarado(null,"GESTOR_COMERCIAL","Otra gestión TEST","900000000","otro-"+UUID.randomUUID())),c.agente(),c.sesion(),correlacion(),procedencia()).resultado();
        assertThrows(ReglaNegocioException.class,()->servicio().acreditar(p.id(),v.referencia(),new CompromisosDeSeguimiento.Acreditacion(v.version(),new ReferenciaVersion(ajena.id(),ajena.version()),"Llamada realizada"),c.agente(),c.sesion(),clave(),procedencia()));
        long rol=b.jdbc.queryForObject("select id_persona_rol from persona_rol where organizacion_id=? and id_persona=? and tipo_rol='PROPIETARIO'",Long.class,c.agente().idOrganizacion(),c.personaPropietaria());
        var datos=llamada(p.id(),new ParticipanteDeclarado(new ReferenciaContactoContextual(TipoContacto.PROPIETARIO,rol,null,null),"PROPIETARIO",null,null,null));
        var real=automaticos().registrarInteraccion(datos,c.agente(),c.sesion(),correlacion(),procedencia()).resultado();String k=clave();var d=new CompromisosDeSeguimiento.Acreditacion(v.version(),new ReferenciaVersion(real.id(),real.version()),"Se realizó la llamada comprometida, sin respuesta");
        var cumplido=servicio().acreditar(p.id(),v.referencia(),d,c.agente(),c.sesion(),k,procedencia());assertEquals(cumplido,servicio().acreditar(p.id(),v.referencia(),d,c.agente(),c.sesion(),k,procedencia()));
        var repetido=servicio().acreditar(p.id(),v.referencia(),new CompromisosDeSeguimiento.Acreditacion(cumplido.version(),d.interaccion(),d.constanciaContenido()),c.agente(),c.sesion(),clave(),procedencia());assertEquals(cumplido,repetido);
        assertEquals(1,b.jdbc.queryForObject("select count(*) from evento_dominio where organizacion_id=? and tipo='COMPROMISO_R27_ACREDITAR'",Integer.class,c.agente().idOrganizacion()));
        reloj(Instant.now().plusSeconds(2*86400));var ventana=new IndicadoresIndividuales.Ventana(hoy(),hoy().plusDays(2),tiempo().ahora());var med=contexto.getBean(MedicionDeCompromisos.class);assertEquals(1,med.leer(c.agente(),ventana,interacciones.indicadores(c.agente())).medicion().exitos());
        var propuesta=b.actuaciones.prepararRectificacionInteraccion(c.agente(),new Rectificacion(real.id(),real.version(),new DatosInteraccion(datos.vinculos(),datos.destinatario(),datos.interlocutor(),datos.tipo(),datos.canal(),datos.medioReal(),datos.eventos(),ResultadoInteraccion.COMUNICACION_FALLIDA,datos.momento(),"Rectificación: fallo técnico TEST",datos.declaraciones(),datos.proxima(),datos.interaccionAnterior(),datos.fuente(),datos.restriccion(),datos.gestionContacto()),"Precisar constancia TEST"),c.sesion(),procedencia());b.humanas.confirmar(propuesta,c.agente(),c.sesion(),FichaInteraccion.class);
        var corregida=med.leer(c.agente(),ventana,interacciones.indicadores(c.agente())).medicion();assertEquals(0,corregida.exitos());assertEquals(1,corregida.sinEvidencia());assertNull(corregida.porcentaje());
    }
    @Test void unIdDeSeguimientoAjenoNoRevelaSiTieneCompromiso(){
        var c=caso();var propia=prospectar(c,c.agente());var ajena=prospectar(c,c.equipo().colega());var s=prospecciones.seguimientos(ajena.id(),c.equipo().colega()).getFirst();
        servicio().planificar(ajena.id(),s.id(),plan(s,hoy()),c.equipo().colega(),b.sesiones.de(c.equipo().colega()),clave(),procedencia());
        assertThrows(NoEncontradoException.class,()->servicio().planificar(propia.id(),s.id(),plan(s,hoy()),c.agente(),c.sesion(),clave(),procedencia()));
        assertThrows(NoEncontradoException.class,()->servicio().planificar(propia.id(),Long.MAX_VALUE,plan(s,hoy()),c.agente(),c.sesion(),clave(),procedencia()));
    }
    @Test void unaPlanificacionAnteriorSinTipoNoSeInventaComoLlamadaNiComoCeroDeCumplimiento(){
        var c=caso();var p=prospectar(c,c.agente());var s=prospecciones.seguimientos(p.id(),c.agente()).getFirst();automaticos().planificarSeguimiento(p.id(),s.id(),new ProspeccionService.PlanificacionSeguimiento(s.version(),OrigenActuacion.INTENCION_PROFESIONAL,hoy(),"Enviar información al contacto TEST"),c.agente(),c.sesion(),correlacion(),procedencia());
        var m=contexto.getBean(MedicionDeCompromisos.class).leer(c.agente(),ventana(),contexto.getBean(InteraccionService.class).indicadores(c.agente()));assertTrue(m.verificable());assertEquals(1,m.medicion().sinEvidencia());assertNull(m.medicion().porcentaje());
    }
    @Test void prepararEncargoRetiraLaCadenciaPeroConservaElCompromisoSinInventarCumplimiento(){
        var c=caso();var p=prospectar(c,c.agente());var s=prospecciones.seguimientos(p.id(),c.agente()).getFirst();var compromiso=servicio().planificar(p.id(),s.id(),plan(s,hoy()),c.agente(),c.sesion(),clave(),procedencia());prospecciones.marcarCaptado(p.id(),c.encargo(),null,c.agente());
        assertTrue(prospecciones.seguimientos(p.id(),c.agente()).isEmpty());var historial=servicio().listar(p.id(),c.agente());assertEquals(1,historial.size());assertEquals(compromiso,historial.getFirst());assertTrue(historial.getFirst().compromiso().actuaciones().isEmpty());assertNull(historial.getFirst().compromiso().resolucion());
    }
}
