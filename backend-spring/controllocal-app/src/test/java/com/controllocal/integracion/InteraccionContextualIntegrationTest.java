package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.integracion.soporte.*;
import com.controllocal.persistence.repositorio.FactorAutenticacionRepository;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actuacion.ComandosAutomaticosService;
import com.controllocal.service.actuacion.ComandosAutomaticosService.*;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.demanda.InteresadosDeDemanda;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.controllocal.service.actuacion.ComandosAutomaticosService.REGISTRAR_INTERACCION;
import static org.junit.jupiter.api.Assertions.*;

/**
 * C4-01–16/DAT-01/03/04: hechos y permisos por Core y PostgreSQL reales.
 * Sin mocks SUT, asignaciones de permisos por SQL ni envío a terceros.
 * La identidad inicial TEST y las sesiones se obtienen de los helpers compartidos.
 */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(classes=ControlLocalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK)
class InteraccionContextualIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.service.archivo.AccesoArchivoService archivosFirmaTest;
    @org.springframework.beans.factory.annotation.Autowired com.controllocal.web.almacen.AlmacenDocumentos almacenFirmaTest;

    @DynamicPropertySource static void datos(DynamicPropertyRegistry p){
        BaseDeDatosDePruebas.registrar(p);
        // Dos transacciones simultáneas y una conexión observadora de pg_blocking_pids.
        // El observador no debe esperar a que termine la transacción que está midiendo.
        p.add("spring.datasource.hikari.maximum-pool-size",()->3);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired MotorDeCaptura captura;
    @Autowired ContextosService contextos;
    @Autowired InteraccionService interacciones;
    @Autowired IndicadorService indicadoresContacto;
    @Autowired ComandosAutomaticosService automaticos;
    @Autowired ActuacionesService actuaciones;
    @Autowired Documentos documentos;
    @Autowired PropiedadUniversalService propiedades;
    @Autowired RequerimientoService demandas;
    @Autowired ProspeccionService prospecciones;
    @Autowired EncargoService encargos;
    @Autowired ClienteService clientes;
    @Autowired PropietarioService propietarios;
    @Autowired OportunidadService oportunidades;
    @Autowired OfertaService ofertas;
    @Autowired VisitaService visitas;
    @Autowired CuentaService cuentas;
    @Autowired FactorAutenticacionRepository factores;
    @Autowired CifradoSecretos cifrado;
    private OrigenesDePrueba origenes;
    private OrigenesDePrueba.Equipo equipo;
    private Actor actor;
    private ContextoSesion sid;
    private Long inmueble;
    @BeforeEach void preparar(TestInfo info){
        EvidenciaC4DePrueba.iniciar(info);
        origenes=new OrigenesDePrueba(jdbc,gestor,captura,contextos);equipo=origenes.empresa();
        actor=equipo.agente();sid=origenes.sesion(actor);inmueble=null;
        EvidenciaC4DePrueba.actor(actor);
    }
    @AfterEach void cerrarEvidencia(){EvidenciaC4DePrueba.limpiar();}

    @Test void primeraRespuestaConservaDosHorasYMediaYUnaSolaConsultaEnDosContextos(){
        long p=propiedad();var inicial=registrar(consultaComercial(p,"Consulta de disponibilidad TEST")).resultado();
        var nuevo=nuevo("Contacto de consulta TEST","900000141");
        var q=Objects.requireNonNull(new TransactionTemplate(gestor).execute(t->interacciones.vincular(inicial.id(),
                new AdicionVinculo(inicial.version(),new VinculoActividad(TipoContexto.CONTACTO,null,null,nuevo.contacto()),"Añadir contacto real de la consulta"),actor,sid,Procedencia.deLaPantalla())));
        long contacto=q.vinculos().stream().filter(v->"CONTACTO".equals(v.tipo())).findFirst().orElseThrow().referencia();
        var intento=datos(List.of(propiedad(p)),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("sin-respuesta",EventoComunicacion.LLAMADA_SALIENTE,ayer(9,15))),ResultadoInteraccion.SIN_RESPUESTA,null,null,"Intento de devolver la consulta",List.of(),null);
        registrar(intento);
        var respuesta=respuestaComercial(p,new ReferenciaVersion(q.id(),q.version()));var correlacion=correlacion();
        var enviada=automaticos.registrarInteraccion(respuesta,actor,sid,correlacion,Procedencia.deLaPantalla());
        var repetida=automaticos.registrarInteraccion(respuesta,actor,sid,correlacion,Procedencia.deLaPantalla());assertEquals(enviada.resultado().id(),repetida.resultado().id());
        var salida=indicadoresContacto.resumen("1m",actor).primeraRespuesta();assertEquals("EVALUABLE",salida.estadoCalculo());
        var m=salida.medicion();assertEquals(1,m.recibidas());assertEquals(1,m.respondidas());assertEquals(0,m.esperando());
        assertEquals(0,new BigDecimal("2.5").compareTo(m.medianaHoras()));assertEquals(1,m.duracionesEvaluables());
        assertEquals(List.of(q.id()),interacciones.listar(filtro(TipoContexto.CONTACTO,contacto,null),actor).items().stream().map(FichaInteraccion::id).toList());
        assertEquals(1,interacciones.listar(filtro(TipoContexto.PROPIEDAD,p,null),actor).items().stream().filter(f->f.id()==q.id()).count());
        assertEquals(3,filas("interaccion_comercial"));assertEquals(0,filas("prospeccion"));assertEquals(0,filas("captacion"));assertEquals(0,filas("detalle_cliente"));assertEquals(0,filas("oportunidad_comercial"));
    }
    @Test void observarNoInvalidaLaRespuestaYRectificarNoReescribeSuAntecedente(){
        long p=propiedad();var q=registrar(consultaComercial(p,"Consulta original TEST")).resultado();
        var r=registrar(respuestaComercial(p,new ReferenciaVersion(q.id(),q.version()))).resultado();
        var observada=Objects.requireNonNull(new TransactionTemplate(gestor).execute(t->interacciones.observar(q.id(),
                new ObservacionPosterior(q.version(),ayer(12,0),"Conocimiento posterior TEST","Declaración TEST",null),actor,sid,Procedencia.deLaPantalla())));
        assertEquals(0,new BigDecimal("2.5").compareTo(indicadoresContacto.resumen("1m",actor).primeraRespuesta().medicion().medianaHoras()));
        var corregida=Objects.requireNonNull(new TransactionTemplate(gestor).execute(t->interacciones.rectificar(
                new Rectificacion(q.id(),observada.version(),cambiarResumen(observada.datos(),"Se rectifica el contenido comercial de la consulta"),"Corrección acreditada TEST"),actor,sid,clave(),Procedencia.deLaPantalla())));
        assertEquals(observada.version()+1,corregida.version());assertNotNull(corregida.datos().atencionConsultas());
        var m=indicadoresContacto.resumen("1m",actor).primeraRespuesta().medicion();
        assertEquals(1,m.recibidas());assertEquals(1,m.porVerificarVinculo());assertNull(m.medianaHoras());assertEquals(0,m.esperando());
        assertEquals(r.id(),interacciones.obtener(r.id(),actor).id());assertEquals(3,interacciones.historia(q.id(),actor).size());assertEquals(2,filas("interaccion_comercial"));
    }
    @Test void consultaDeOtraOrganizacionNoPuedeAtribuirseComoRespuestaPropia(){
        var otro=origenes.empresa();long externo=propiedadDeclarada(otro.agente(),"Consulta privada de otro espacio TEST "+UUID.randomUUID());
        var q=automaticos.registrarInteraccion(consultaComercial(externo,"Consulta de otra organización TEST"),otro.agente(),origenes.sesion(otro.agente()),correlacion(),Procedencia.deLaPantalla()).resultado();
        var intento=respuestaComercial(propiedad(),new ReferenciaVersion(q.id(),q.version()));
        assertThrows(NoEncontradoException.class,()->registrar(intento));assertEquals(0,filas("interaccion_comercial"));
        assertEquals(0,indicadoresContacto.resumen("1m",actor).primeraRespuesta().medicion().recibidas());
        assertEquals(1,indicadoresContacto.resumen("1m",otro.agente()).primeraRespuesta().medicion().esperando());
    }
    @Test void unaEntradaSinDeclaracionDeConsultaNoSeInterpretaYAdministrarNoConcedeLaMetrica(){
        var d=datos(List.of(propiedad(propiedad())),TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,
                List.of(evento("entrada",EventoComunicacion.MENSAJE_RECIBIDO,ayer(9,0))),ResultadoInteraccion.SIN_INTERCAMBIO,null,null,"Mensaje real sin clasificación de consulta",List.of(),null);
        var q=registrar(d).resultado();var salida=indicadoresContacto.resumen("1m",actor).primeraRespuesta();
        assertEquals("SIN_BASE",salida.estadoCalculo());assertEquals(0,salida.medicion().recibidas());assertNull(salida.medicion().medianaHoras());
        assertThrows(ReglaNegocioException.class,()->registrar(respuestaComercial(propiedad(),new ReferenciaVersion(q.id(),q.version()))));
        assertThrows(AccesoNoAutorizadoException.class,()->indicadoresContacto.resumen("1m",equipo.admin()));assertEquals(1,filas("interaccion_comercial"));
    }
    @Test void fuenteHistoricaRevocadaNoSeExponeNiSeConvierteEnCeroRespuestas(){
        long p=propiedad();var demanda=demandas.crear(datosDemanda(referenciaNuevo("Contacto original de consulta TEST","900000151"),true),actor,sid,clave(),Procedencia.deLaPantalla());
        var persona=participante(new ReferenciaContactoContextual(TipoContacto.DEMANDA_ORIGEN,demanda.id(),demanda.version(),null),"PERSONA");
        var d=datos(List.of(propiedad(p),new VinculoActividad(TipoContexto.CONTACTO,demanda.idPersonaInteresada(),null,local(TipoContacto.INTERLOCUTOR))),
                TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,List.of(evento("entrada",EventoComunicacion.MENSAJE_RECIBIDO,ayer(9,0))),ResultadoInteraccion.SIN_INTERCAMBIO,null,persona,"Consulta recibida TEST",List.of(),null);
        var q=registrar(conAtencion(d,new AtencionConsultas(new ConsultaRecibida("entrada",null),List.of()))).resultado();
        var respuesta=registrar(respuestaComercial(p,new ReferenciaVersion(q.id(),q.version())));
        var corregida=Objects.requireNonNull(new TransactionTemplate(gestor).execute(t->interacciones.rectificar(new Rectificacion(q.id(),q.version(),
                consultaComercial(p,"Consulta comercial sin atribución al contacto anterior"),"Rectificación del contacto atribuido"),actor,sid,clave(),Procedencia.deLaPantalla())));
        assertEquals(respuesta.resultado().id(),interacciones.obtener(respuesta.resultado().id(),actor).id());
        demandas.actualizar(demanda.id(),demanda.version(),datosDemanda(referenciaNuevo("Contacto posterior de consulta TEST","900000152"),false),actor,sid,clave(),Procedencia.deLaPantalla());
        assertEquals(corregida.id(),interacciones.obtener(corregida.id(),actor).id());
        assertThrows(NoEncontradoException.class,()->interacciones.obtenerVersion(q.id(),q.version(),actor));
        assertThrows(NoEncontradoException.class,()->interacciones.obtener(respuesta.resultado().id(),actor));
        assertThrows(NoEncontradoException.class,()->automaticos.consultarInteraccion(REGISTRAR_INTERACCION,actor,respuesta.clave()));
        var lectura=interacciones.indicadores(actor);assertTrue(lectura.consultasConFuentesNoVerificables().contains(q.id()));assertEquals(1,lectura.registros().size());
        var salida=indicadoresContacto.resumen("1m",actor).primeraRespuesta();assertEquals("NO_EVALUABLE",salida.estadoCalculo());assertTrue(salida.causa().contains("Permiso insuficiente"));assertNull(salida.medicion());
        var json=documentos.objeto(Map.of("primeraRespuesta",salida));assertFalse(json.contains("900000151"));assertFalse(json.contains("Contacto original"));
        assertEquals(2,filas("interaccion_comercial"));
    }

    @Test void dosRectificacionesConcurrentesConservanElBloqueoExistenteYNoCreanCiclo() throws Exception {
        long p=propiedad();var q=registrar(consultaComercial(p,"Consulta Q TEST")).resultado();var otra=registrar(consultaComercial(p,"Consulta P TEST")).resultado();
        var sid2=origenes.sesion(actor);var preparada=new java.util.concurrent.CompletableFuture<FichaInteraccion>();
        var liberar=new java.util.concurrent.CountDownLatch(1);var segundaIniciada=new java.util.concurrent.CountDownLatch(1);
        var pid1=new java.util.concurrent.atomic.AtomicInteger();var pid2=new java.util.concurrent.atomic.AtomicInteger();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var primera=pool.submit(()->new TransactionTemplate(gestor).execute(t->{
                pid1.set(Objects.requireNonNull(jdbc.queryForObject("select pg_backend_pid()",Integer.class)));
                var f=interacciones.rectificar(new Rectificacion(q.id(),q.version(),consultaRespondida(p,"Consulta Q y respuesta TEST",new ReferenciaVersion(otra.id(),otra.version())),"Completar hechos de la comunicación Q"),actor,sid,clave(),Procedencia.deLaPantalla());
                preparada.complete(f);try{if(!liberar.await(20,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("No se liberó la coordinación del ensayo");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return f;
            }));
            var sinCommit=preparada.get(20,java.util.concurrent.TimeUnit.SECONDS);
            var segunda=pool.submit(()->new TransactionTemplate(gestor).execute(t->{
                pid2.set(Objects.requireNonNull(jdbc.queryForObject("select pg_backend_pid()",Integer.class)));segundaIniciada.countDown();
                return interacciones.rectificar(new Rectificacion(otra.id(),otra.version(),consultaRespondida(p,"Consulta P y respuesta TEST",new ReferenciaVersion(sinCommit.id(),sinCommit.version())),"Intento circular concurrente TEST"),actor,sid2,clave(),Procedencia.deLaPantalla());
            }));
            assertTrue(segundaIniciada.await(20,java.util.concurrent.TimeUnit.SECONDS));boolean bloqueada=false;long limite=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<limite&&!segunda.isDone()){
                if(Boolean.TRUE.equals(jdbc.queryForObject("select ? = any(pg_blocking_pids(?))",Boolean.class,pid1.get(),pid2.get()))){bloqueada=true;break;}
                Thread.sleep(20);
            }
            assertTrue(bloqueada,"La segunda transacción debe esperar el bloqueo de cuenta ya existente");liberar.countDown();
            assertNotNull(primera.get(20,java.util.concurrent.TimeUnit.SECONDS));
            var rechazo=assertThrows(java.util.concurrent.ExecutionException.class,()->segunda.get(20,java.util.concurrent.TimeUnit.SECONDS));assertInstanceOf(ReglaNegocioException.class,rechazo.getCause());
            assertEquals(sinCommit.version(),interacciones.obtener(q.id(),actor).version());assertEquals(otra.version(),interacciones.obtener(otra.id(),actor).version());
            assertEquals(2,filas("interaccion_comercial"));assertEquals(3,filas("interaccion_version"));
        }finally{liberar.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS));}
    }

    @Test void gestoraComercialSinTitularidadNiCuotaSustituyeElSeguimientoSinCambiarResponsables(){
        long p=prospecciones.registrar(new ProspeccionService.DatosProspeccion(propiedad(),"Prospección comercial TEST",
                new ProspeccionService.DestinatarioDeclarado("PROPIETARIO","Madre TEST","900000093",null,"Declaración TEST")),actor).id();
        var previa=revision(p);var madre=new ParticipanteDeclarado(null,null,null,null,previa.contactoDeclarado().referenciaDeclarada());
        var hija=new ParticipanteDeclarado(null,"FAMILIAR","Hija TEST","900000094","HIJA-TEST-"+UUID.randomUUID());
        var d=new DatosInteraccion(List.of(new VinculoActividad(TipoContexto.PROSPECCION,p,null,null)),madre,hija,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,
                List.of(evento("conversacion",EventoComunicacion.LLAMADA_SALIENTE,momento(0))),ResultadoInteraccion.INTERCAMBIO_EFECTIVO,null,
                "La hija recibe propuestas por indicación de su madre",List.of(),new ProximaActuacion(OrigenActuacion.ACUERDO_EXTERNO,hoy().plusDays(3),"Revisar propuesta con hija"),null,"Declaración TEST",null,
                new GestionContacto(Set.of(FinalidadContacto.PROSPECCION_COMERCIAL),Set.of(FuncionContacto.GESTION_COMERCIAL),"La hija declaró su función en esta llamada",true,null));
        var correlacion=correlacion();var f=automaticos.registrarInteraccion(d,actor,sid,correlacion,Procedencia.deLaPantalla()).resultado();
        var repetida=automaticos.registrarInteraccion(d,actor,sid,correlacion,Procedencia.deLaPantalla()).resultado();assertEquals(f.id(),repetida.id());
        assertEquals(madre.referenciaDeclarada(),f.datos().destinatario().referenciaDeclarada());
        assertEquals(hija.referenciaDeclarada(),f.datos().interlocutor().referenciaDeclarada());
        var actual=revision(p);assertNotEquals(previa.id(),actual.id());assertEquals(hoy().plusDays(3),actual.proximaFecha());
        assertEquals("Hija TEST",actual.contactoDeclarado().nombre());assertEquals("GESTOR_COMERCIAL",actual.contactoDeclarado().tipo());
        assertEquals(hija.referenciaDeclarada(),actual.contactoDeclarado().referenciaDeclarada());
        assertNotNull(jdbc.queryForObject("select retirado_en from seguimiento_prospeccion where organizacion_id=? and id_seguimiento=?",java.sql.Timestamp.class,actor.idOrganizacion(),previa.id()));
        assertEquals(0,filas("titularidad_propiedad"));assertEquals(0,filas("captacion"));assertEquals(0,filas("detalle_cliente"));assertEquals(1,filas("interaccion_comercial"));
        var m=interacciones.indicadores(actor).registros();assertEquals("EFECTIVO",m.getFirst().contactoProspeccion().estado());
        assertFalse(documentos.objeto(Map.of("mediciones",m)).contains("Hija TEST"));assertFalse(documentos.objeto(Map.of("mediciones",m)).contains("900000094"));
        assertEquals("EFECTIVO",com.controllocal.web.dto.InteraccionResponse.desde(f).contactoProspeccion().estado());
        var resumen=com.controllocal.web.dto.IndicadoresResponse.desde(indicadoresContacto.resumen("7d",actor),null);
        assertEquals(1,resumen.contactoEfectivoProspeccion().efectivos());assertEquals(1,resumen.contactoEfectivoProspeccion().intentosEvaluables());
        var otro=origenes.empresa();assertThrows(NoEncontradoException.class,()->interacciones.obtener(f.id(),otro.agente()));
        assertTrue(interacciones.indicadores(otro.agente()).registros().stream().noneMatch(x->x.id()==f.id()));
        // Reconciliar de nuevo por otra lectura no revive el seguimiento de la madre ni duplica el actual.
        assertEquals(actual.id(),revision(p).id());assertEquals(actual.proximaFecha(),revision(p).proximaFecha());
        var recontacto=new DatosInteraccion(d.vinculos(),new ParticipanteDeclarado(null,null,null,null,actual.contactoDeclarado().referenciaDeclarada()),null,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,
                List.of(evento("nuevo-intento",EventoComunicacion.LLAMADA_SALIENTE,momento(0))),ResultadoInteraccion.SIN_RESPUESTA,null,null,List.of(),null,null,"Llamada real TEST",null,
                new GestionContacto(Set.of(FinalidadContacto.PROSPECCION_COMERCIAL),Set.of(),null,false,null));
        var siguiente=registrar(recontacto).resultado();assertEquals("Hija TEST",siguiente.datos().destinatario().nombre());assertEquals(1,prospecciones.seguimientos(p,actor).size());
        assertEquals(0,filas("titularidad_propiedad"));
    }

    @Test void unaRespuestaExplicitaTieneAntecedenteAutorizadoSinSumarOtroIntento(){
        long p=prospecciones.registrar(new ProspeccionService.DatosProspeccion(propiedad(),"Prospección TEST",null),actor).id();
        var madre=new ParticipanteDeclarado(null,"PROPIETARIO","Madre TEST","900000095","madre-"+UUID.randomUUID());
        var salida=new DatosInteraccion(List.of(new VinculoActividad(TipoContexto.PROSPECCION,p,null,null)),madre,null,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,
                List.of(evento("intento",EventoComunicacion.LLAMADA_SALIENTE,momento(0))),ResultadoInteraccion.SIN_RESPUESTA,null,null,List.of(),null,null,"Declaración TEST",null,
                new GestionContacto(Set.of(FinalidadContacto.PROSPECCION_COMERCIAL),Set.of(),null,false,null));
        var intento=registrar(salida).resultado();
        var hija=new ParticipanteDeclarado(null,"GESTOR_COMERCIAL","Hija TEST",null,"hija-"+UUID.randomUUID());
        var respuesta=new DatosInteraccion(salida.vinculos(),madre,hija,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,
                List.of(evento("respuesta",EventoComunicacion.LLAMADA_ENTRANTE,momento(0))),ResultadoInteraccion.INTERCAMBIO_EFECTIVO,null,null,List.of(),null,intento.id(),"Declaración TEST",null,
                new GestionContacto(Set.of(FinalidadContacto.PROSPECCION_COMERCIAL),Set.of(FuncionContacto.GESTION_COMERCIAL),"La hija recibe las propuestas",false,new ReferenciaVersion(intento.id(),intento.version())));
        var prematura=new DatosInteraccion(respuesta.vinculos(),madre,hija,respuesta.tipo(),respuesta.canal(),null,List.of(evento("antes-del-intento",EventoComunicacion.LLAMADA_ENTRANTE,new MomentoHecho(hoy(),LocalTime.of(8,0),ZoneOffset.ofHours(-5)))),respuesta.resultado(),null,null,List.of(),null,intento.id(),respuesta.fuente(),null,respuesta.gestionContacto());
        assertThrows(ReglaNegocioException.class,()->registrar(prematura));
        var f=registrar(respuesta).resultado();
        var medida=com.controllocal.service.actividad.ContactoEfectivoProspeccion.medir(interacciones.indicadores(actor).registros(),hoy(),hoy(),Instant.now());
        assertEquals(1,medida.intentosEvaluables());assertEquals(1,medida.efectivos());assertEquals(2,filas("interaccion_comercial"));
        var otro=origenes.empresa();var sesionOtra=origenes.sesion(otro.agente());
        assertThrows(NoEncontradoException.class,()->automaticos.registrarInteraccion(respuesta,otro.agente(),sesionOtra,correlacion(),Procedencia.deLaPantalla()));
        assertEquals(ResultadoInteraccion.SIN_RESPUESTA,interacciones.obtenerVersion(intento.id(),intento.version(),actor).datos().resultado());
        assertEquals(intento.id(),f.datos().interaccionAnterior());
        new TransactionTemplate(gestor).executeWithoutResult(tx->interacciones.observar(intento.id(),new ObservacionPosterior(intento.version(),momento(0),"Se recibió un documento posteriormente","Documento TEST",null),actor,sid,Procedencia.deLaPantalla()));
        assertEquals(1,com.controllocal.service.actividad.ContactoEfectivoProspeccion.medir(interacciones.indicadores(actor).registros(),hoy(),hoy(),Instant.now()).efectivos());
    }

    @Test void personaDeContactoComercialNoExigeCuotaNiAltaDeProspeccion(){
        var persona=nuevo("Hermana del propietario TEST","900000096");
        var d=new DatosInteraccion(List.of(propiedad(propiedad()),contactoDestinatario()),persona,participante(local(TipoContacto.DESTINATARIO),"PERSONA"),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,
                List.of(evento("propuesta",EventoComunicacion.LLAMADA_SALIENTE,momento(0))),ResultadoInteraccion.INTERCAMBIO_EFECTIVO,null,null,List.of(),null,null,"Conversación TEST",null,
                new GestionContacto(Set.of(FinalidadContacto.PROSPECCION_COMERCIAL),Set.of(FuncionContacto.GESTION_COMERCIAL),"La hermana manifestó que recibe propuestas",false,null));
        var f=registrar(d).resultado();assertNotNull(f.datos().destinatario().contacto().referencia());assertEquals("EFECTIVO",f.contactoProspeccion().estado());
        assertEquals(0,filas("titularidad_propiedad"));assertEquals(0,filas("prospeccion"));assertEquals(0,filas("captacion"));assertEquals(0,filas("detalle_cliente"));assertEquals(0,filas("requerimiento_cliente"));
        assertEquals(1,com.controllocal.service.actividad.ContactoEfectivoProspeccion.medir(interacciones.indicadores(actor).registros(),hoy(),hoy(),Instant.now()).efectivos());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->new TransactionTemplate(gestor).executeWithoutResult(tx->jdbc.update("update interaccion_comercial set version_diccionario='NO_APROBADA' where organizacion_id=? and id_interaccion=?",actor.idOrganizacion(),f.id())));
        assertEquals(f.versionDiccionario(),jdbc.queryForObject("select version_diccionario from interaccion_comercial where organizacion_id=? and id_interaccion=?",String.class,actor.idOrganizacion(),f.id()));
    }

    @Test void unaLlamadaConPropiedadYContactoTieneUnaIdentidadDosVistasYUnConteo(){
        long propiedad=propiedad();var destino=nuevo("Ana TEST","900000001");
        var d=datos(List.of(propiedad(propiedad),contactoDestinatario()),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("llamada",EventoComunicacion.LLAMADA_SALIENTE,momento(-2))),ResultadoInteraccion.INTERCAMBIO_EFECTIVO,
                destino,participante(local(TipoContacto.DESTINATARIO),"PERSONA"),"Conversación con Ana TEST",List.of(),null);
        var original=registrar(d);var f=original.resultado();
        long persona=f.datos().destinatario().contacto().referencia();
        assertEquals(TipoContacto.PERSONA,f.datos().destinatario().contacto().tipo());
        assertEquals(persona,f.datos().interlocutor().contacto().referencia());
        assertEquals(List.of(f.id()),interacciones.porPropiedad(propiedad,actor,FinalidadLectura.FICHA_PROPIEDAD).stream().map(FichaInteraccion::id).toList());
        var contacto=interacciones.listar(filtro(TipoContexto.CONTACTO,persona,null),actor);
        assertEquals(1,contacto.total());assertEquals(f.id(),contacto.items().getFirst().id());
        assertEquals(1,interacciones.indicadores(actor).registros().size());assertEquals(1,filas("interaccion_comercial"));
        assertEquals(0,filas("detalle_cliente"));assertEquals(0,filas("oportunidad_comercial"));
    }

    @Test void revocarElOrigenDelContactoNoDaAccesoPorConservarPermisoFisicoNiPorUnRecibo(){
        long propiedad=propiedad();long encargo=abrirPreparacion(propiedad,null);
        var p1=referenciaNuevo("Contacto original P1","900000011");
        var demanda=demandas.crear(datosDemanda(p1,true),actor,sid,clave(),Procedencia.deLaPantalla());
        EvidenciaC4DePrueba.recurso(actor,"DEMANDA",demanda.id(),demanda.version());
        var origen=new ReferenciaContactoContextual(TipoContacto.DEMANDA_ORIGEN,demanda.id(),demanda.version(),null);
        var destino=participante(origen,"PERSONA");
        var d=datos(List.of(propiedad(propiedad),new VinculoActividad(TipoContexto.ENCARGO,encargo,null,null),
                        new VinculoActividad(TipoContexto.CONTACTO,demanda.idPersonaInteresada(),null,local(TipoContacto.DESTINATARIO))),
                TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,List.of(evento("llamada",EventoComunicacion.LLAMADA_SALIENTE,momento(-2))),
                ResultadoInteraccion.SIN_RESPUESTA,destino,null,"Detalle privado de P1",List.of(),null);
        var correlacion=correlacion();var original=automaticos.registrarInteraccion(d,actor,sid,correlacion,Procedencia.deLaPantalla());
        EvidenciaC4DePrueba.resultado("REGISTRAR_INTERACCION",actor,original.resultado());
        long id=original.resultado().id();long version=original.resultado().version();
        assertEquals(1,interacciones.contarParaReporte(encargo,hoy().minusDays(10),hoy(),actor).total());
        var cambiada=demandas.actualizar(demanda.id(),demanda.version(),datosDemanda(referenciaNuevo("Contacto posterior P2","900000012"),false),actor,sid,clave(),Procedencia.deLaPantalla());
        EvidenciaC4DePrueba.recurso(actor,"DEMANDA",cambiada.id(),cambiada.version());
        assertNotEquals(demanda.idPersonaInteresada(),cambiada.idPersonaInteresada());
        assertEquals(propiedad,propiedades.consultar(propiedad,actor).id());
        assertEquals(cambiada.idPersonaInteresada(),demandas.buscar(demanda.id(),actor).idPersonaInteresada());
        long antes=filas("interaccion_comercial");
        assertThrows(NoEncontradoException.class,()->interacciones.obtener(id,actor));
        assertThrows(NoEncontradoException.class,()->interacciones.obtenerVersion(id,version,actor));
        assertThrows(NoEncontradoException.class,()->interacciones.historia(id,actor));
        assertEquals(0,interacciones.listar(filtro(null,null,"P1"),actor).total());
        assertTrue(interacciones.porPropiedad(propiedad,actor,FinalidadLectura.FICHA_PROPIEDAD).isEmpty());
        assertTrue(interacciones.indicadores(actor).registros().isEmpty());
        assertEquals(0,interacciones.contarParaReporte(encargo,hoy().minusDays(10),hoy(),actor).total());
        assertThrows(NoEncontradoException.class,()->automaticos.consultarInteraccion(REGISTRAR_INTERACCION,actor,original.clave()));
        assertThrows(NoEncontradoException.class,()->automaticos.registrarInteraccion(d,actor,sid,correlacion,Procedencia.deLaPantalla()));
        assertEquals(antes,filas("interaccion_comercial"));
    }

    @Test void identificacionFisicaDeclaradaIgualEnDosOrganizacionesNoMezclaComunicaciones(){
        String direccion="Predio ficticio compartido TEST "+UUID.randomUUID();
        var otro=origenes.empresa();long a=propiedadDeclarada(actor,direccion),b=propiedadDeclarada(otro.agente(),direccion);
        assertNotEquals(a,b); // Dato de identificación compartido no acredita fusión global del inmueble.
        var d1=soloPropiedad(a,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,EventoComunicacion.LLAMADA_SALIENTE,ResultadoInteraccion.SIN_RESPUESTA);
        var d2=soloPropiedad(b,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,EventoComunicacion.LLAMADA_SALIENTE,ResultadoInteraccion.SIN_RESPUESTA);
        var f1=registrar(d1).resultado();var otroSid=origenes.sesion(otro.agente());
        var f2=automaticos.registrarInteraccion(d2,otro.agente(),otroSid,correlacion(),Procedencia.deLaPantalla()).resultado();
        EvidenciaC4DePrueba.resultado("REGISTRAR_INTERACCION",otro.agente(),f2);
        assertNotEquals(f1.id(),f2.id());
        assertEquals(List.of(f1.id()),interacciones.listar(filtro(null,null,null),actor).items().stream().map(FichaInteraccion::id).toList());
        assertEquals(List.of(f2.id()),interacciones.listar(filtro(null,null,null),otro.agente()).items().stream().map(FichaInteraccion::id).toList());
        assertThrows(NoEncontradoException.class,()->interacciones.obtener(f1.id(),otro.agente()));
        assertThrows(NoEncontradoException.class,()->interacciones.obtenerVersion(f2.id(),f2.version(),actor));
        assertEquals(1,interacciones.indicadores(actor).registros().size());assertEquals(1,interacciones.indicadores(otro.agente()).registros().size());
    }

    @Test void definicionDeGobiernoNoConcedeFichaHistorialIndicadoresNiActividadDelEquipo(){
        EvidenciaC4DePrueba.actor(equipo.broker());
        EvidenciaC4DePrueba.actor(equipo.admin());
        var f=registrar(soloPropiedad(propiedad(),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                EventoComunicacion.LLAMADA_SALIENTE,ResultadoInteraccion.SIN_RESPUESTA)).resultado();
        var sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        sesiones.de(equipo.broker()); // MFA real del actor comercial, no broker ficticio.
        assertEquals(f.id(),interacciones.obtener(f.id(),equipo.broker()).id());
        assertNotNull(interacciones.definicion(equipo.admin()).version());
        assertThrows(AccesoNoAutorizadoException.class,()->interacciones.obtener(f.id(),equipo.admin()));
        assertThrows(AccesoNoAutorizadoException.class,()->interacciones.historia(f.id(),equipo.admin()));
        assertThrows(AccesoNoAutorizadoException.class,()->interacciones.indicadores(equipo.admin()));
        assertThrows(AccesoNoAutorizadoException.class,()->interacciones.listar(filtro(null,null,null),equipo.admin()));
        assertThrows(NoEncontradoException.class,()->interacciones.obtener(f.id(),equipo.colega()));
    }

    @Test void elAdministradorQueContestaNoSeConvierteEnElPropietarioNiReseteaSuCadencia(){
        long p=propiedad();long prospeccion=prospecciones.registrar(new ProspeccionService.DatosProspeccion(p,"Prospección TEST explícita",null),actor).id();
        var primera=datos(List.of(new VinculoActividad(TipoContexto.PROSPECCION,prospeccion,null,null)),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("intento",EventoComunicacion.LLAMADA_SALIENTE,momento(-2))),ResultadoInteraccion.SIN_RESPUESTA,nuevoPropietario(),null,"No atendió",List.of(),null);
        var inicial=registrar(primera).resultado();var antes=revision(prospeccion);
        var destino=inicial.datos().destinatario();
        var administrador=new ParticipanteDeclarado(null,"ADMINISTRADOR","Administrador TEST","900000099","Declaración del interlocutor real");
        var declaracion=new Declaracion("Informa que está ocupado","OCUPACION_DECLARADA",ValorLogico.deTexto("OCUPADO"),null,null,momento(-1),
                "INTERLOCUTOR","Administrador TEST",null);
        var atendida=registrar(datos(primera.vinculos(),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("otra-llamada",EventoComunicacion.LLAMADA_SALIENTE,momento(-1))),ResultadoInteraccion.INTERCAMBIO_EFECTIVO,
                destino,administrador,"Contestó el administrador",List.of(declaracion),null)).resultado();
        assertEquals("PROPIETARIO",atendida.datos().destinatario().tipoDeclarado());
        assertEquals("ADMINISTRADOR",atendida.datos().interlocutor().tipoDeclarado());assertNull(atendida.datos().interlocutor().contacto());
        assertEquals(antes.fechaUltimoIntento(),revision(prospeccion).fechaUltimoIntento());
        assertEquals(antes.recontactosSinRespuesta(),revision(prospeccion).recontactosSinRespuesta());
        assertEquals(antes.proximaFecha(),revision(prospeccion).proximaFecha());
        assertEquals(0,filas("captacion"));assertEquals(0,filas("titularidad_propiedad"));
    }

    @Test void llamadasPerdidasConservanDireccionEtiquetaYNoAumentanIntentosSalientes(){
        long prospeccion=prospecciones.registrar(new ProspeccionService.DatosProspeccion(propiedad(),"Prospección TEST",null),actor).id();
        var saliente=datos(List.of(new VinculoActividad(TipoContexto.PROSPECCION,prospeccion,null,null)),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("saliente",EventoComunicacion.LLAMADA_SALIENTE,momento(-2))),ResultadoInteraccion.SIN_RESPUESTA,nuevoPropietario(),null,"No respondieron",List.of(),null);
        var f1=registrar(saliente).resultado();var antes=revision(prospeccion);
        var f2=registrar(datos(saliente.vinculos(),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("entrante",EventoComunicacion.LLAMADA_ENTRANTE,momento(-1))),ResultadoInteraccion.SIN_RESPUESTA,
                f1.datos().destinatario(),null,"Llamada entrante perdida",List.of(),null)).resultado();
        assertEquals("No respondieron",f1.rotuloResultado());assertEquals("Llamada perdida",f2.rotuloResultado());
        assertEquals(antes.fechaUltimoIntento(),revision(prospeccion).fechaUltimoIntento());
        assertEquals(antes.recontactosSinRespuesta(),revision(prospeccion).recontactosSinRespuesta());
        assertEquals(antes.proximaFecha(),revision(prospeccion).proximaFecha());
    }

    @Test void mensajeEnviadoSinRespuestaNoInfiereDesinteresNiCambiaNegocio(){
        var antes=negocio();var f=registrar(soloPropiedad(propiedad(),TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,
                EventoComunicacion.MENSAJE_ENVIADO,ResultadoInteraccion.SIN_INTERCAMBIO)).resultado();
        assertEquals(ResultadoInteraccion.SIN_INTERCAMBIO,f.datos().resultado());assertNull(f.datos().proxima());
        assertEquals(antes,negocio());
    }

    @Test void mensajeRecibidoConIntencionNoCreaClienteDemandaOportunidadNiOferta(){
        long p=propiedad();var antes=negocio();long personas=filas("persona");
        var remitente=new ParticipanteDeclarado(null,"PERSONA","Remitente declarado TEST","900000088",null);
        var declaraciones=List.of(new Declaracion("Busco departamento y aceptaría95000USD","INTENCION_COMPRA",ValorLogico.deImporte(new BigDecimal("95000"),"USD"),
                null,"USD",momento(-1),"INTERLOCUTOR","Mensaje recibido TEST",null));
        var f=registrar(datos(List.of(propiedad(p)),TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,
                List.of(evento("recibido",EventoComunicacion.MENSAJE_RECIBIDO,momento(-1))),ResultadoInteraccion.SIN_INTERCAMBIO,
                null,remitente,"Intención recibida",declaraciones,null)).resultado();
        assertEquals(antes,negocio());assertEquals(personas,filas("persona"));
        assertEquals(0,new BigDecimal("95000").compareTo(f.datos().declaraciones().getFirst().valorDeclarado().numero()));
        assertEquals("POR_CONFIRMAR",f.datos().declaraciones().getFirst().confirmacion().estado());
    }

    @Test void intercambioWhatsappConservaDosMomentosYCuentaUnaSolaGestion(){
        var envio=momento(-3);var respuesta=momento(-1);
        var f=registrar(datos(List.of(propiedad(propiedad())),TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,
                List.of(evento("envio",EventoComunicacion.MENSAJE_ENVIADO,envio),evento("respuesta",EventoComunicacion.MENSAJE_RECIBIDO,respuesta)),
                ResultadoInteraccion.INTERCAMBIO_EFECTIVO,null,null,"Mismo asunto declarado expresamente",List.of(),null)).resultado();
        assertEquals(List.of(envio,respuesta),f.datos().eventos().stream().map(EventoDeclarado::momento).toList());
        assertEquals(1,filas("interaccion_comercial"));assertEquals(1,interacciones.indicadores(actor).registros().size());
        assertTrue(f.registradoEn().atZone(ZoneId.of("America/Lima")).toLocalDate().isAfter(respuesta.fecha()));
    }

    @Test void envioFallidoNoSeConvierteEnMensajeEnviado(){
        var f=registrar(soloPropiedad(propiedad(),TipoActividad.MENSAJERIA,CanalComunicacion.CORREO_ELECTRONICO,
                EventoComunicacion.ENVIO_FALLIDO,ResultadoInteraccion.COMUNICACION_FALLIDA)).resultado();
        assertEquals(List.of(EventoComunicacion.ENVIO_FALLIDO),f.datos().eventos().stream().map(EventoDeclarado::evento).toList());
        assertEquals(ResultadoInteraccion.COMUNICACION_FALLIDA,f.datos().resultado());
    }

    @Test void reboteConocidoDespuesConservaEnvioOriginalYNoCuentaOtroEnvio(){
        var original=registrar(soloPropiedad(propiedad(),TipoActividad.MENSAJERIA,CanalComunicacion.CORREO_ELECTRONICO,
                EventoComunicacion.MENSAJE_ENVIADO,ResultadoInteraccion.SIN_INTERCAMBIO)).resultado();
        var observacion=new ObservacionPosterior(original.version(),momento(-1),"Posteriormente se conoció el rebote","Notificación declarada TEST",ResultadoInteraccion.COMUNICACION_FALLIDA);
        EvidenciaC4DePrueba.accion("ANOTAR_OBSERVACION",actor,"INTERACCION",original.id(),observacion.versionEsperada());
        var actual=automaticos.anotarObservacionInteraccion(original.id(),observacion,actor,sid,correlacion(),Procedencia.deLaPantalla()).resultado();
        EvidenciaC4DePrueba.resultado("ANOTAR_OBSERVACION",actor,actual);
        assertEquals(original.id(),actual.id());assertEquals(original.datos(),actual.datos());
        assertEquals(1,actual.hechosPosteriores().size());assertEquals(observacion,actual.hechosPosteriores().getFirst().datos());
        assertEquals(original,interacciones.obtenerVersion(original.id(),original.version(),actor));
        assertEquals(1,filas("interaccion_comercial"));
        assertEquals(1,interacciones.indicadores(actor).registros().stream().flatMap(i->i.eventos().stream()).filter(e->e.evento()==EventoComunicacion.MENSAJE_ENVIADO).count());
    }

    @Test void visitaNoRealizadaConservaSuCasoPropioSinReunionComunicativa(){
        var c=bancoNegociacion().mesa("VENTA",new BigDecimal("400000"),"USD",true);
        var agenda=automaticos.programarVisita(new VisitaService.DatosVisita(c.oportunidad(),oportunidades.obtener(c.oportunidad(),c.agente()).version(),
                hoy(),LocalTime.of(10,0),"Visita TEST",List.of(),"Local TEST"),c.agente(),c.sesion(),correlacion(),Procedencia.deLaPantalla()).resultado();
        var noRealizada=automaticos.registrarResultadoVisita(agenda.id(),new VisitaService.DesenlaceVisita(agenda.version(),VisitaService.Ocurrencia.NO_REALIZADA,hoy(),
                "El visitante no pudo asistir",null,null,null,null,null,null),c.agente(),c.sesion(),correlacion(),Procedencia.deLaPantalla()).resultado();
        EvidenciaC4DePrueba.resultado("PROGRAMAR_VISITA",c.agente(),agenda);
        EvidenciaC4DePrueba.resultado("REGISTRAR_RESULTADO_VISITA",c.agente(),noRealizada);
        assertEquals(agenda.id(),noRealizada.id());assertEquals("N",noRealizada.estado());
        assertTrue(interacciones.porOportunidad(c.oportunidad(),null,c.agente()).isEmpty());
        assertEquals(0,jdbc.queryForObject("select count(*) from interaccion_comercial where organizacion_id=?",Long.class,c.agente().idOrganizacion()));
    }

    @Test void avisoRealDeCancelacionDeReunionNoDeclaraReunionRealizadaNiModificaAgenda(){
        long p=propiedad();var antes=negocio();long personas=filas("persona");
        var remitente=new ParticipanteDeclarado(null,"PERSONA","Participante declarado TEST",null,null);
        var aviso=new Declaracion("El participante comunica que la reunión fue cancelada",null,null,null,null,
                momento(-1),"INTERLOCUTOR","Aviso recibido TEST",null);
        // Se registra el aviso real, no una reunión ni un estado inferido del resumen/declaración.
        var f=registrar(datos(List.of(propiedad(p)),TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,
                List.of(evento("aviso-cancelacion",EventoComunicacion.MENSAJE_RECIBIDO,momento(-1))),ResultadoInteraccion.SIN_INTERCAMBIO,
                null,remitente,"Aviso recibido de coordinación",List.of(aviso),null)).resultado();
        assertEquals(TipoActividad.MENSAJERIA,f.datos().tipo());
        assertEquals(List.of(EventoComunicacion.MENSAJE_RECIBIDO),f.datos().eventos().stream().map(EventoDeclarado::evento).toList());
        assertEquals(aviso.texto(),f.datos().declaraciones().getFirst().texto());
        assertEquals(aviso.fuente(),f.datos().declaraciones().getFirst().fuente());
        assertTrue(interacciones.indicadores(actor).registros().stream().flatMap(i->i.eventos().stream()).noneMatch(e->e.evento()==EventoComunicacion.REUNION_REALIZADA));
        assertEquals(1,filas("interaccion_comercial"));assertEquals(antes,negocio());assertEquals(personas,filas("persona"));
        assertEquals(f.id(),interacciones.obtenerVersion(f.id(),f.version(),actor).id());
    }

    @Test void negativaAConversarNoEquivaleARechazarOfertaNiCerrarUnaOportunidad(){
        var banco=bancoNegociacion();var c=banco.mesa("VENTA",new BigDecimal("400000"),"USD",true);
        var presentada=banco.presentar(c,new BigDecimal("350000"));var oportunidad=oportunidades.obtener(c.oportunidad(),c.agente());
        assertEquals("P",presentada.estado());assertEquals("A",oportunidad.estado());
        var destinatario=participante(new ReferenciaContactoContextual(TipoContacto.CLIENTE,oportunidad.idCliente(),null,null),"CLIENTE");
        var d=datos(List.of(new VinculoActividad(TipoContexto.OPORTUNIDAD,c.oportunidad(),oportunidad.version(),null)),
                TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,List.of(evento("negativa-conversar",EventoComunicacion.LLAMADA_SALIENTE,momento(-1))),
                ResultadoInteraccion.CONVERSACION_RECHAZADA,destinatario,participante(local(TipoContacto.DESTINATARIO),"CLIENTE"),
                "No desea conversar ahora; no declaró decisión sobre la propuesta económica",List.of(),null);
        var f=automaticos.registrarInteraccion(d,c.agente(),c.sesion(),correlacion(),Procedencia.deLaPantalla()).resultado();
        EvidenciaC4DePrueba.resultado("REGISTRAR_INTERACCION",c.agente(),f);
        assertEquals(ResultadoInteraccion.CONVERSACION_RECHAZADA,f.datos().resultado());
        assertEquals(presentada,ofertas.listar(c.oportunidad(),c.agente()).getFirst());
        assertEquals(oportunidad.estado(),oportunidades.obtener(c.oportunidad(),c.agente()).estado());
        assertEquals(oportunidad.version(),oportunidades.obtener(c.oportunidad(),c.agente()).version());
        assertTrue(ofertas.consultarAcuerdoAceptado(c.oportunidad(),null,c.agente()).isEmpty());
    }

    @Test void destinatarioDeclaradoSinPersonaRecibeRevisionHoyYUnIntentoRealProgramaDosDias(){
        long propiedad=propiedad();long personasAntes=filas("persona"),rolesAntes=filas("persona_rol");
        long clientesAntes=filas("detalle_cliente"),encargosAntes=filas("captacion");
        var destinatario=new ProspeccionService.DestinatarioDeclarado("PROPIETARIO",
                "Propietaria declarada TEST","900000091",null,"Cartel observado TEST");
        var prospeccion=prospecciones.registrar(new ProspeccionService.DatosProspeccion(propiedad,
                "Seguimiento de destinatario conocido sin crear identidad personal",destinatario),actor);
        EvidenciaC4DePrueba.resultado("REGISTRAR_PROSPECCION",actor,prospeccion);
        var revisiones=prospecciones.seguimientos(prospeccion.id(),actor);assertEquals(1,revisiones.size());
        var inicial=revisiones.getFirst();assertEquals(hoy(),inicial.proximaFecha());
        assertNotNull(inicial.contactoDeclarado());
        var ref=inicial.contactoDeclarado().referenciaDeclarada();assertNotNull(ref);assertFalse(ref.isBlank());
        assertEquals(destinatario.tipo(),inicial.contactoDeclarado().tipo());

        // La referencia la emite Core; no repetir nombre, teléfono ni fabricar Persona/DNI.
        var destino=new ParticipanteDeclarado(null,null,null,null,ref);
        var datos=datos(List.of(new VinculoActividad(TipoContexto.PROSPECCION,prospeccion.id(),null,null)),
                TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("intento-declarado",EventoComunicacion.LLAMADA_SALIENTE,new MomentoHecho(hoy(),null,null))),
                ResultadoInteraccion.SIN_RESPUESTA,destino,null,"Llamada saliente realmente efectuada TEST",List.of(),null);
        var hecho=registrar(datos).resultado();
        assertTrue(Objects.equals(ref,hecho.datos().destinatario().referenciaDeclarada()),"Core conserva la referencia del destinatario elegido");
        assertNull(hecho.datos().destinatario().contacto());
        assertTrue(Objects.equals(destinatario.nombre(),hecho.datos().destinatario().nombre()),"Core reutiliza el nombre conocido sin pedirlo otra vez");
        assertTrue(Objects.equals(destinatario.telefono(),hecho.datos().destinatario().telefono()),"Core reutiliza el teléfono conocido sin pedirlo otra vez");
        var posteriores=prospecciones.seguimientos(prospeccion.id(),actor);assertEquals(1,posteriores.size());
        var posterior=posteriores.getFirst();assertEquals(inicial.id(),posterior.id());
        assertEquals(hoy(),posterior.fechaUltimoIntento());assertEquals(hoy().plusDays(2),posterior.proximaFecha());
        assertTrue(Objects.equals(ref,posterior.contactoDeclarado().referenciaDeclarada()),"La revisión conserva el destinatario declarado");
        assertEquals(personasAntes,filas("persona"));assertEquals(rolesAntes,filas("persona_rol"));
        assertEquals(clientesAntes,filas("detalle_cliente"));assertEquals(encargosAntes,filas("captacion"));
    }

    @Test void respuestaEfectivaRetiraCadenciaYNuevoIntentoDeclaradoCreaOcurrenciaVinculadaSinHeredarPlan(){
        long propiedad=propiedad();long personasAntes=filas("persona"),rolesAntes=filas("persona_rol");
        var p=prospecciones.registrar(new ProspeccionService.DatosProspeccion(propiedad,"Seguimiento declarado TEST",
                new ProspeccionService.DestinatarioDeclarado("PROPIETARIO","Propietaria declarada TEST","900000092",null,"Cartel observado TEST")),actor);
        var inicial=prospecciones.seguimientos(p.id(),actor).getFirst();
        var destino=new ParticipanteDeclarado(null,null,null,null,inicial.contactoDeclarado().referenciaDeclarada());
        var vinculos=List.of(new VinculoActividad(TipoContexto.PROSPECCION,p.id(),null,null));
        LocalDate planAnterior=hoy().plusDays(9);
        var base=datos(vinculos,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("primer-intento",EventoComunicacion.LLAMADA_SALIENTE,momento(-4))),
                ResultadoInteraccion.SIN_RESPUESTA,destino,null,"Intento real anterior TEST",List.of(),null);
        var conPlan=new DatosInteraccion(base.vinculos(),base.destinatario(),base.interlocutor(),base.tipo(),base.canal(),base.medioReal(),
                base.eventos(),base.resultado(),base.momento(),base.resumen(),base.declaraciones(),
                new ProximaActuacion(OrigenActuacion.INTENCION_PROFESIONAL,planAnterior,"Plan propio declarado TEST"),
                base.interaccionAnterior(),base.fuente(),base.restriccion());
        registrar(conPlan);var anterior=revision(p.id());assertEquals(planAnterior,anterior.proximaFecha());
        EvidenciaC4DePrueba.recurso(actor,"SEGUIMIENTO_PROSPECCION",anterior.id(),anterior.version());

        // Misma referencia Core en destinatario e interlocutor: no alias RF003 ni Persona fabricada.
        registrar(datos(vinculos,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("respuesta-real",EventoComunicacion.LLAMADA_SALIENTE,momento(-1))),
                ResultadoInteraccion.INTERCAMBIO_EFECTIVO,destino,destino,"Respuesta efectiva sin otra actuación acordada TEST",List.of(),null));
        assertTrue(prospecciones.seguimientos(p.id(),actor).isEmpty());

        registrar(datos(vinculos,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("nuevo-intento-material",EventoComunicacion.LLAMADA_SALIENTE,new MomentoHecho(hoy(),null,null))),
                ResultadoInteraccion.SIN_RESPUESTA,destino,null,"Nuevo intento real autorizado TEST",List.of(),null));
        var nueva=revision(p.id());assertNotEquals(anterior.id(),nueva.id());
        EvidenciaC4DePrueba.recurso(actor,"SEGUIMIENTO_PROSPECCION",nueva.id(),nueva.version());
        assertEquals(hoy(),nueva.fechaUltimoIntento());assertEquals(hoy().plusDays(2),nueva.proximaFecha());
        assertEquals("CADENCIA",nueva.origenFecha());assertNull(nueva.pospuestaHasta());
        assertNotEquals(planAnterior,nueva.proximaFecha());
        assertEquals(1,jdbc.queryForObject("""
                select count(*) from seguimiento_prospeccion n join seguimiento_prospeccion a
                  on a.organizacion_id=n.organizacion_id and a.id_prospeccion=n.id_prospeccion
                 and a.id_seguimiento=n.id_antecedente and a.destinatario_clave=n.destinatario_clave and a.finalidad=n.finalidad
                where n.organizacion_id=? and n.id_prospeccion=? and n.id_seguimiento=?
                  and a.id_seguimiento=? and a.retirado_en is not null and n.retirado_en is null
                """,Long.class,actor.idOrganizacion(),p.id(),nueva.id(),anterior.id()));
        assertEquals(planAnterior,jdbc.queryForObject("select proxima_fecha from seguimiento_prospeccion where organizacion_id=? and id_seguimiento=?",
                LocalDate.class,actor.idOrganizacion(),anterior.id()));
        assertEquals(p.estado(),prospecciones.obtener(p.id(),actor).estado());
        assertNull(prospecciones.obtener(p.id(),actor).idCaptacion());assertEquals(0,filas("captacion"));
        assertEquals(personasAntes,filas("persona"));assertEquals(rolesAntes,filas("persona_rol"));
    }

    @Test void restriccionExplicitaPorDestinatarioYFinalidadRetiraCadenciaIncompatible(){
        long p=prospecciones.registrar(new ProspeccionService.DatosProspeccion(propiedad(),"Prospección TEST",null),actor).id();
        var links=List.of(new VinculoActividad(TipoContexto.PROSPECCION,p,null,null));
        var inicial=registrar(datos(links,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,List.of(evento("intento",EventoComunicacion.LLAMADA_SALIENTE,momento(-2))),
                ResultadoInteraccion.SIN_RESPUESTA,nuevoPropietario(),null,"Intento declarado",List.of(),null)).resultado();
        assertNotNull(revision(p).proximaFecha());
        var restriccion=new RestriccionContacto("No vuelvan a contactar",local(TipoContacto.DESTINATARIO),"RECONTACTAR_PROPIETARIO","Negativa expresa TEST");
        registrar(datos(links,TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,List.of(evento("negativa",EventoComunicacion.MENSAJE_RECIBIDO,momento(-1))),
                ResultadoInteraccion.CONVERSACION_RECHAZADA,inicial.datos().destinatario(),participante(local(TipoContacto.DESTINATARIO),"PROPIETARIO"),
                "Restricción expresa y contextual",List.of(),restriccion));
        assertTrue(revision(p).restringido());assertNull(revision(p).proximaFecha());assertEquals("RESTRICCION_CONTACTO",revision(p).causa());
    }

    @Test void restriccionAnteriorALaProspeccionConservaPersonaFinalidadYSoloSuPropiedad(){
        var titular=propietarios.registrar(new PropietarioService.DatosPropietario("N","D","70000101","Titular TEST C4",
                "900000017",null,true,null),actor);
        long persona=jdbc.queryForObject("select id_persona from persona_rol where organizacion_id=? and id_persona_rol=?",Long.class,
                actor.idOrganizacion(),titular.id());
        long primera=propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(clave(),Procedencia.deLaPantalla(),null,
                "TERRENO","VIVIENDA","Predio de restricción TEST",new PropiedadUniversalService.Ubicacion("Predio A TEST "+UUID.randomUUID(),"Surco",null,null,null,null,null,null,null),List.of(new PropiedadUniversalService.Titular(titular.id(),null,true)),
                List.of(),List.of(),null),actor).idPropiedad();
        long segunda=propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(clave(),Procedencia.deLaPantalla(),null,
                "TERRENO","VIVIENDA","Otro predio del mismo titular TEST",new PropiedadUniversalService.Ubicacion("Predio B TEST "+UUID.randomUUID(),"Surco",null,null,null,null,null,null,null),List.of(new PropiedadUniversalService.Titular(titular.id(),null,true)),
                List.of(),List.of(),null),actor).idPropiedad();
        assertEquals(0,filas("prospeccion"));assertEquals(0,filas("captacion"));
        var destinatario=participante(new ReferenciaContactoContextual(TipoContacto.PROPIETARIO,titular.id(),null,null),"PROPIETARIO");
        var restriccion=new RestriccionContacto("No recontactar por este predio",local(TipoContacto.DESTINATARIO),"RECONTACTAR_PROPIETARIO","Declaración contextual TEST previa");
        var f=registrar(datos(List.of(propiedad(primera),contactoDestinatario()),TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,
                List.of(evento("restriccion-previa",EventoComunicacion.MENSAJE_RECIBIDO,momento(-1))),ResultadoInteraccion.CONVERSACION_RECHAZADA,
                destinatario,participante(local(TipoContacto.DESTINATARIO),"PROPIETARIO"),"Restricción de este predio",List.of(),restriccion)).resultado();
        assertEquals(0,filas("prospeccion"));assertEquals(0,filas("captacion"));
        assertEquals(2,jdbc.queryForObject("select count(*) from interaccion_vinculo where organizacion_id=? and id_interaccion=? and retirado_en is null",Long.class,
                actor.idOrganizacion(),f.id()));
        assertEquals(persona,jdbc.queryForObject("select (autorizacion_json::jsonb#>>'{contactos,restriccion,persona}')::bigint from interaccion_comercial where organizacion_id=? and id_interaccion=?",
                Long.class,actor.idOrganizacion(),f.id()));
        assertEquals(momento(-1),f.datos().eventos().getFirst().momento());assertEquals(restriccion.fuente(),f.datos().restriccion().fuente());
        assertEquals(restriccion.finalidad(),f.datos().restriccion().finalidad());
        long a=prospecciones.registrar(new ProspeccionService.DatosProspeccion(primera,"Seguimiento explícito del primer predio TEST",null),actor).id();
        long b=prospecciones.registrar(new ProspeccionService.DatosProspeccion(segunda,"Seguimiento explícito del otro predio TEST",null),actor).id();
        EvidenciaC4DePrueba.recurso(actor,"PROSPECCION",a,null);EvidenciaC4DePrueba.recurso(actor,"PROSPECCION",b,null);
        var restringida=revision(a);var permitida=revision(b);
        assertTrue(restringida.restringido());assertNull(restringida.proximaFecha());assertEquals("RESTRICCION_CONTACTO",restringida.causa());
        assertFalse(permitida.restringido());assertFalse(permitida.alcancePorPrecisar());assertEquals(hoy(),permitida.proximaFecha());
        assertEquals("true",jdbc.queryForObject("select contexto_json::jsonb->>'restringido' from seguimiento_prospeccion where organizacion_id=? and id_seguimiento=?",
                String.class,actor.idOrganizacion(),restringida.id()));
        assertEquals("false",jdbc.queryForObject("select contexto_json::jsonb->>'restringido' from seguimiento_prospeccion where organizacion_id=? and id_seguimiento=?",
                String.class,actor.idOrganizacion(),permitida.id()));
        assertEquals(1,filas("interaccion_comercial"));assertEquals(0,filas("captacion"));
    }

    @Test void prospeccionDescartadaConservaElHechoTardioSinReabrirSuSeguimiento(){
        long p=prospecciones.registrar(new ProspeccionService.DatosProspeccion(propiedad(),"Prospección TEST",null),actor).id();
        var links=List.of(new VinculoActividad(TipoContexto.PROSPECCION,p,null,null));
        var inicial=registrar(datos(links,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("intento-previo",EventoComunicacion.LLAMADA_SALIENTE,momento(-2))),ResultadoInteraccion.SIN_RESPUESTA,
                nuevoPropietario(),null,"Intento real anterior al descarte TEST",List.of(),null)).resultado();
        assertNotNull(revision(p).proximaFecha());
        assertEquals("D",prospecciones.descartar(p,"Descarte explícito del agente TEST",actor).estado());
        assertTrue(prospecciones.seguimientos(p,actor).isEmpty());
        long estadosAntes=jdbc.queryForObject("select count(*) from seguimiento_prospeccion where organizacion_id=? and id_prospeccion=?",Long.class,actor.idOrganizacion(),p);
        var tardia=registrar(datos(links,TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("conocida-despues",EventoComunicacion.LLAMADA_SALIENTE,momento(-1))),ResultadoInteraccion.SIN_RESPUESTA,
                inicial.datos().destinatario(),null,"Hecho pasado registrado después del descarte TEST",List.of(),null)).resultado();
        assertNotEquals(inicial.id(),tardia.id());assertEquals(momento(-1),tardia.datos().eventos().getFirst().momento());
        assertEquals(tardia.id(),interacciones.obtener(tardia.id(),actor).id());
        assertEquals("D",prospecciones.obtener(p,actor).estado());assertNull(prospecciones.obtener(p,actor).idCaptacion());
        assertTrue(prospecciones.seguimientos(p,actor).isEmpty());
        assertEquals(0,jdbc.queryForObject("select count(*) from seguimiento_prospeccion where organizacion_id=? and id_prospeccion=? and retirado_en is null",Long.class,actor.idOrganizacion(),p));
        assertEquals(estadosAntes,jdbc.queryForObject("select count(*) from seguimiento_prospeccion where organizacion_id=? and id_prospeccion=?",Long.class,actor.idOrganizacion(),p));
        assertEquals(2,filas("interaccion_comercial"));assertEquals(0,filas("captacion"));
    }

    @Test void encargoIniciadoRetiraLaCadenciaYUnaInteraccionTardiaNoLaReabre(){
        long propiedad=propiedad();long p=prospecciones.registrar(new ProspeccionService.DatosProspeccion(propiedad,"Prospección TEST",null),actor).id();
        var d=datos(List.of(new VinculoActividad(TipoContexto.PROSPECCION,p,null,null)),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                List.of(evento("intento",EventoComunicacion.LLAMADA_SALIENTE,momento(-2))),ResultadoInteraccion.SIN_RESPUESTA,nuevoPropietario(),null,"Intento antes del Encargo",List.of(),null);
        var inicial=registrar(d).resultado();assertNotNull(revision(p).proximaFecha());
        long encargo=abrirPreparacion(propiedad,p);assertEquals(p,jdbc.queryForObject("select id_prospeccion_origen from captacion where organizacion_id=? and id_captacion=?",Long.class,actor.idOrganizacion(),encargo));assertTrue(prospecciones.seguimientos(p,actor).isEmpty());
        registrar(datos(d.vinculos(),d.tipo(),d.canal(),List.of(evento("tardia",EventoComunicacion.LLAMADA_SALIENTE,momento(-1))),d.resultado(),
                inicial.datos().destinatario(),null,"Interacción conocida después",List.of(),null));
        assertTrue(prospecciones.seguimientos(p,actor).isEmpty());
    }

    @Test void rectificacionMotivadaYHechoPosteriorConservanAntecedentesDistintos(){
        var original=registrar(soloPropiedad(propiedad(),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,EventoComunicacion.LLAMADA_SALIENTE,ResultadoInteraccion.SIN_RESPUESTA)).resultado();
        var correccion=cambiarResumen(original.datos(),"Resumen corregido explícitamente");
        var propuesta=actuaciones.prepararRectificacionInteraccion(actor,new Rectificacion(original.id(),original.version(),correccion,"Corrección de errata TEST"),sid,Procedencia.deLaPantalla());
        var rectificada=new ActuacionesHumanasC4DePrueba(actuaciones,documentos).confirmar(propuesta,actor,sid,FichaInteraccion.class).datos();
        var posterior=new ObservacionPosterior(rectificada.version(),momento(-1),"Dato conocido después","Fuente posterior TEST",null);
        var actual=automaticos.anotarObservacionInteraccion(original.id(),posterior,actor,sid,correlacion(),Procedencia.deLaPantalla()).resultado();
        EvidenciaC4DePrueba.resultado("ANOTAR_OBSERVACION",actor,actual);
        assertEquals(original,interacciones.obtenerVersion(original.id(),original.version(),actor));
        assertEquals(rectificada,interacciones.obtenerVersion(rectificada.id(),rectificada.version(),actor));
        assertEquals(rectificada.datos(),actual.datos());assertEquals(1,actual.hechosPosteriores().size());
        assertEquals(3,interacciones.historia(original.id(),actor).size());assertEquals(1,filas("interaccion_comercial"));
    }

    @Test void reciboAlteradoSeRechazaPorLaActuacionCanonicaYSeRestauraElOriginal(){
        var original=registrar(soloPropiedad(propiedad(),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,
                EventoComunicacion.LLAMADA_SALIENTE,ResultadoInteraccion.SIN_RESPUESTA)).resultado();
        var propuesta=actuaciones.prepararRectificacionInteraccion(actor,new Rectificacion(original.id(),original.version(),
                cambiarResumen(original.datos(),"Resumen corregido legítimo TEST"),"Corrección de errata TEST"),sid,Procedencia.deLaPantalla());
        var confirmada=new ActuacionesHumanasC4DePrueba(actuaciones,documentos).confirmar(propuesta,actor,sid,FichaInteraccion.class);
        String clave="actuacion:"+propuesta.referencia();
        String guardado=jdbc.queryForObject("select resultado from comando_idempotente where organizacion_id=? and idempotency_key=?",
                String.class,actor.idOrganizacion(),clave);
        assertNotNull(guardado);
        var alterado=new LinkedHashMap<>(documentos.comoMapa(guardado));
        var detalle=new LinkedHashMap<>(documentos.comoMapa(documentos.objetoCanonico(alterado.get("datos"))));
        detalle.put("resumen","Dato corrupto TEST; id y versión intactos");alterado.put("datos",detalle);
        try {
            jdbc.update("update comando_idempotente set resultado=? where organizacion_id=? and idempotency_key=?",
                    documentos.objetoCanonico(alterado),actor.idOrganizacion(),clave);
            assertThrows(IllegalStateException.class,()->actuaciones.consultar(actor,propuesta.referencia(),sid));
            assertThrows(IllegalStateException.class,()->actuaciones.confirmar(actor,propuesta.referencia(),sid,confirmada.confirmacion(),Procedencia.deLaPantalla()));
            assertEquals(1,filas("interaccion_comercial"));assertEquals(2,interacciones.historia(original.id(),actor).size());
        } finally {
            jdbc.update("update comando_idempotente set resultado=? where organizacion_id=? and idempotency_key=?",
                    guardado,actor.idOrganizacion(),clave);
        }
        assertEquals(confirmada.resultado(),actuaciones.consultar(actor,propuesta.referencia(),sid));
        assertEquals(confirmada.datos(),interacciones.obtenerVersion(confirmada.datos().id(),confirmada.datos().version(),actor));
    }

    @Test void reintentoDeLaMismaCapturaDevuelveOriginalYCambioDeCuerpoNoCreaOtraComunicacion(){
        var d=soloPropiedad(propiedad(),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,EventoComunicacion.LLAMADA_SALIENTE,ResultadoInteraccion.SIN_RESPUESTA);
        var c=correlacion();var original=automaticos.registrarInteraccion(d,actor,sid,c,Procedencia.deLaPantalla());
        EvidenciaC4DePrueba.resultado("REGISTRAR_INTERACCION",actor,original.resultado());
        assertEquals(original,automaticos.registrarInteraccion(d,actor,sid,c,Procedencia.deLaPantalla()));
        assertEquals(original,automaticos.consultarInteraccion(REGISTRAR_INTERACCION,actor,original.clave()).orElseThrow());
        assertThrows(ConflictoException.class,()->automaticos.registrarInteraccion(cambiarResumen(d,"Otro cuerpo"),actor,sid,c,Procedencia.deLaPantalla()));
        assertEquals(1,filas("interaccion_comercial"));assertEquals(1,interacciones.indicadores(actor).registros().size());
    }

    @Test void declaracionDe120m2ConservaConceptoFuenteYConfirmacionSinEditarInmueble(){
        long p=propiedad();var atributos=propiedades.consultar(p,actor).atributos();
        var dato=new Declaracion("El tercero declara120m²","AREA_TOTAL_DECLARADA",ValorLogico.deNumero(new BigDecimal("120")),"m²",null,momento(-2),
                "INTERLOCUTOR","Administrador TEST; llamada declarada",new ConfirmacionContextual("POR_CONFIRMAR","metraje_total",null,"Referencia declarada TEST"));
        var d=datos(List.of(propiedad(p)),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,List.of(evento("declaracion",EventoComunicacion.LLAMADA_SALIENTE,momento(-2))),
                ResultadoInteraccion.INTERCAMBIO_EFECTIVO,null,new ParticipanteDeclarado(null,"ADMINISTRADOR","Administrador TEST",null,null),"Declaración de superficie",List.of(dato),null);
        var f=registrar(d).resultado();var conservada=f.datos().declaraciones().getFirst();
        assertEquals(0,new BigDecimal("120").compareTo(conservada.valorDeclarado().numero()));assertEquals("m²",conservada.unidad());
        assertEquals(dato.conceptoDeclarado(),conservada.conceptoDeclarado());assertEquals(dato.fuente(),conservada.fuente());
        assertEquals("POR_CONFIRMAR",conservada.confirmacion().estado());assertEquals(dato.confirmacion().evidencia(),conservada.confirmacion().evidencia());
        assertEquals(atributos,propiedades.consultar(p,actor).atributos());
        var confirmada=new Declaracion(dato.texto(),dato.conceptoDeclarado(),dato.valorDeclarado(),dato.unidad(),dato.moneda(),dato.momento(),dato.referenciaInterlocutor(),dato.fuente(),
                new ConfirmacionContextual("CONFIRMADO","metraje_total",1L,"No acredita autoridad"));
        assertThrows(ReglaNegocioException.class,()->interacciones.validarRegistro(datos(d.vinculos(),d.tipo(),d.canal(),d.eventos(),d.resultado(),d.destinatario(),d.interlocutor(),d.resumen(),List.of(confirmada),null),actor));
    }

    @Test void importesDeclaradosConMonedaDesconocidaODistintaNoHeredanNiSeConvierten(){
        var sinMoneda=new Declaracion("Importe cuya moneda no se conoce","IMPORTE_DECLARADO",ValorLogico.deNumero(new BigDecimal("1500")),null,null,momento(-2),null,"Tercero TEST",null);
        var usd=new Declaracion("Importe declarado enUSD","IMPORTE_DECLARADO",ValorLogico.deImporte(new BigDecimal("500"),"USD"),null,"USD",momento(-2),null,"Tercero TEST",null);
        var pen=new Declaracion("Importe declarado enPEN","IMPORTE_DECLARADO",ValorLogico.deImporte(new BigDecimal("1800"),"PEN"),null,"PEN",momento(-2),null,"Tercero TEST",null);
        var d=datos(List.of(propiedad(propiedad())),TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,List.of(evento("importes",EventoComunicacion.LLAMADA_SALIENTE,momento(-2))),
                ResultadoInteraccion.INTERCAMBIO_EFECTIVO,null,null,"Monedas declaradas",List.of(sinMoneda,usd,pen),null);
        var valores=registrar(d).resultado().datos().declaraciones();
        assertEquals(0,new BigDecimal("1500").compareTo(valores.get(0).valorDeclarado().numero()));assertNull(valores.get(0).moneda());assertNull(valores.get(0).valorDeclarado().moneda());
        assertEquals("USD",valores.get(1).moneda());assertEquals(0,new BigDecimal("500").compareTo(valores.get(1).valorDeclarado().numero()));
        assertEquals("PEN",valores.get(2).moneda());assertEquals(0,new BigDecimal("1800").compareTo(valores.get(2).valorDeclarado().numero()));
        var contradictoria=new Declaracion(usd.texto(),usd.conceptoDeclarado(),usd.valorDeclarado(),null,"PEN",usd.momento(),null,usd.fuente(),null);
        assertThrows(ReglaNegocioException.class,()->interacciones.validarRegistro(datos(d.vinculos(),d.tipo(),d.canal(),d.eventos(),d.resultado(),null,null,d.resumen(),List.of(contradictoria),null),actor));
        assertEquals(0,filas("oferta"));
    }

    @Test void faltantesDeCapturaNoSeVuelvenTipoCanalResultadoNiFechaDelHechoInventados(){
        var d=datos(List.of(propiedad(propiedad())),null,null,List.of(evento("intento-conocido",EventoComunicacion.LLAMADA_SALIENTE,null)),null,null,null,"Intento real declarado con datos aún desconocidos",List.of(),null);
        var f=registrar(d).resultado();assertNull(f.datos().tipo());assertNull(f.datos().canal());assertNull(f.datos().resultado());
        assertNull(f.fechaHecho());assertNull(f.datos().eventos().getFirst().momento());assertNotNull(f.registradoEn());
        assertEquals(0,interacciones.indicadores(actor).registros().stream().filter(i->i.resultado()!=null).count());
    }

    enum Incompatible { TELEFONO_MENSAJE,PRESENCIAL_LLAMADA,FALLO_Y_ENVIO,INTERCAMBIO_SIN_RECIPROCIDAD,DOS_DIRECCIONES,OTRO_SIN_MEDIO,REUNION_CON_LLAMADA }
    @ParameterizedTest @EnumSource(Incompatible.class)
    void combinacionesIncompatiblesNoEscribenHechoResultadoNiVinculos(Incompatible caso){
        EvidenciaC4DePrueba.accion("COMBINACION_"+caso.name(),actor,null,null,null);
        TipoActividad tipo=TipoActividad.MENSAJERIA;CanalComunicacion canal=CanalComunicacion.WHATSAPP;
        ResultadoInteraccion resultado=ResultadoInteraccion.SIN_INTERCAMBIO;List<EventoDeclarado> eventos=List.of(evento("envio",EventoComunicacion.MENSAJE_ENVIADO,momento(-2)));
        switch(caso){
            case TELEFONO_MENSAJE -> canal=CanalComunicacion.TELEFONO;
            case PRESENCIAL_LLAMADA -> {tipo=TipoActividad.LLAMADA;canal=CanalComunicacion.PRESENCIAL;resultado=ResultadoInteraccion.SIN_RESPUESTA;eventos=List.of(evento("llamada",EventoComunicacion.LLAMADA_SALIENTE,momento(-2)));}
            case FALLO_Y_ENVIO -> {resultado=ResultadoInteraccion.COMUNICACION_FALLIDA;eventos=List.of(evento("fallo",EventoComunicacion.ENVIO_FALLIDO,momento(-2)),evento("mismo-intento",EventoComunicacion.MENSAJE_ENVIADO,momento(-2)));}
            case INTERCAMBIO_SIN_RECIPROCIDAD -> resultado=ResultadoInteraccion.INTERCAMBIO_EFECTIVO;
            case DOS_DIRECCIONES -> {tipo=TipoActividad.LLAMADA;canal=CanalComunicacion.TELEFONO;resultado=ResultadoInteraccion.SIN_RESPUESTA;eventos=List.of(evento("saliente",EventoComunicacion.LLAMADA_SALIENTE,momento(-2)),evento("entrante",EventoComunicacion.LLAMADA_ENTRANTE,momento(-2)));}
            case OTRO_SIN_MEDIO -> canal=CanalComunicacion.OTRO_IDENTIFICADO;
            case REUNION_CON_LLAMADA -> {tipo=TipoActividad.REUNION;canal=CanalComunicacion.PRESENCIAL;resultado=ResultadoInteraccion.INTERCAMBIO_EFECTIVO;eventos=List.of(evento("no-reunion",EventoComunicacion.LLAMADA_ENTRANTE,momento(-2)));}
        }
        var d=datos(List.of(propiedad(propiedad())),tipo,canal,eventos,resultado,null,null,"Combinación incompatible TEST",List.of(),null);
        assertThrows(ReglaNegocioException.class,()->registrar(d));assertEquals(0,filas("interaccion_comercial"));
        assertEquals(0,filas("interaccion_vinculo"));assertEquals(0,filas("interaccion_version"));
    }

    private ResultadoAutomatico<FichaInteraccion> registrar(DatosInteraccion d){
        EvidenciaC4DePrueba.accion("REGISTRAR_INTERACCION",actor,"INTERACCION",null,null);
        if(d.vinculos()!=null)for(var v:d.vinculos())
            EvidenciaC4DePrueba.accion("VINCULAR_INTERACCION",actor,v.tipo()==null?null:v.tipo().name(),v.referencia(),v.versionEsperada());
        var r=automaticos.registrarInteraccion(d,actor,sid,correlacion(),Procedencia.deLaPantalla());
        EvidenciaC4DePrueba.resultado("REGISTRAR_INTERACCION",actor,r.resultado());
        return r;
    }
    private ActividadNegociacionDePrueba bancoNegociacion(){
        var sesiones=new SesionesComercialesDePrueba(jdbc,contextos,cuentas,factores,cifrado);
        return new ActividadNegociacionDePrueba(jdbc,propiedades,encargos,propietarios,clientes,oportunidades,ofertas,actuaciones,documentos,origenes,sesiones,gestor, archivosFirmaTest, almacenFirmaTest);
    }
    private long propiedad(){if(inmueble==null)inmueble=origenes.propiedad(actor,"Interacción contextual TEST");EvidenciaC4DePrueba.recurso(actor,"PROPIEDAD",inmueble,null);return inmueble;}
    private static VinculoActividad propiedad(long id){return new VinculoActividad(TipoContexto.PROPIEDAD,id,null,null);}
    private static VinculoActividad contactoDestinatario(){return new VinculoActividad(TipoContexto.CONTACTO,null,null,local(TipoContacto.DESTINATARIO));}
    private static ReferenciaContactoContextual local(TipoContacto t){return new ReferenciaContactoContextual(t,null,null,null);}
    private static ParticipanteDeclarado participante(ReferenciaContactoContextual ref,String tipo){return new ParticipanteDeclarado(ref,tipo,null,null,null);}
    private static ParticipanteDeclarado nuevo(String nombre,String telefono){return participante(new ReferenciaContactoContextual(TipoContacto.CONTACTO_NUEVO,null,null,new InteresadosDeDemanda.DatosContacto("N",null,null,nombre,telefono,null)),"PERSONA");}
    private static ParticipanteDeclarado nuevoPropietario(){var p=nuevo("Propietario declarado TEST","900000055");return new ParticipanteDeclarado(p.contacto(),"PROPIETARIO",p.nombre(),p.telefono(),p.referenciaDeclarada());}
    private static DatosInteraccion soloPropiedad(long p,TipoActividad t,CanalComunicacion c,EventoComunicacion e,ResultadoInteraccion r){return datos(List.of(propiedad(p)),t,c,List.of(evento("hecho",e,momento(-3))),r,null,null,"Comunicación TEST explícita",List.of(),null);}
    private static DatosInteraccion datos(List<VinculoActividad> links,TipoActividad t,CanalComunicacion c,List<EventoDeclarado> eventos,ResultadoInteraccion r,
            ParticipanteDeclarado destinatario,ParticipanteDeclarado interlocutor,String resumen,List<Declaracion> declaraciones,RestriccionContacto restriccion){
        return new DatosInteraccion(links,destinatario,interlocutor,t,c,null,eventos,r,null,resumen,declaraciones,null,null,"Declaración de hechos TEST",restriccion);
    }
    private static DatosInteraccion cambiarResumen(DatosInteraccion d,String resumen){return new DatosInteraccion(d.vinculos(),d.destinatario(),d.interlocutor(),d.tipo(),d.canal(),d.medioReal(),d.eventos(),d.resultado(),d.momento(),resumen,d.declaraciones(),d.proxima(),d.interaccionAnterior(),d.fuente(),d.restriccion(),d.gestionContacto(),d.atencionConsultas());}
    private static DatosInteraccion conAtencion(DatosInteraccion d,AtencionConsultas a){return new DatosInteraccion(d.vinculos(),d.destinatario(),d.interlocutor(),d.tipo(),d.canal(),d.medioReal(),d.eventos(),d.resultado(),d.momento(),d.resumen(),d.declaraciones(),d.proxima(),d.interaccionAnterior(),d.fuente(),d.restriccion(),d.gestionContacto(),a);}
    private static MomentoHecho ayer(int hora,int minuto){return new MomentoHecho(hoy().minusDays(1),LocalTime.of(hora,minuto),ZoneOffset.ofHours(-5));}
    private static DatosInteraccion consultaComercial(long p,String resumen){return conAtencion(datos(List.of(propiedad(p)),TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,
            List.of(evento("entrada",EventoComunicacion.MENSAJE_RECIBIDO,ayer(9,0))),ResultadoInteraccion.SIN_INTERCAMBIO,null,null,resumen,List.of(),null),new AtencionConsultas(new ConsultaRecibida("entrada",null),List.of()));}
    private static DatosInteraccion respuestaComercial(long p,ReferenciaVersion consulta){return conAtencion(datos(List.of(propiedad(p)),TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,
            List.of(evento("salida",EventoComunicacion.MENSAJE_ENVIADO,ayer(11,30))),ResultadoInteraccion.SIN_INTERCAMBIO,null,null,"Respuesta comercial TEST",List.of(),null),new AtencionConsultas(null,List.of(new RespuestaComercial(consulta,"salida",null,"Respondí sobre disponibilidad"))));}
    private static DatosInteraccion consultaRespondida(long p,String resumen,ReferenciaVersion consulta){return conAtencion(datos(List.of(propiedad(p)),TipoActividad.MENSAJERIA,CanalComunicacion.WHATSAPP,
            List.of(evento("entrada",EventoComunicacion.MENSAJE_RECIBIDO,ayer(9,0)),evento("salida",EventoComunicacion.MENSAJE_ENVIADO,ayer(11,30))),ResultadoInteraccion.INTERCAMBIO_EFECTIVO,null,null,resumen,List.of(),null),new AtencionConsultas(new ConsultaRecibida("entrada",null),List.of(new RespuestaComercial(consulta,"salida",null,"Respondí a la otra consulta"))));}
    private ProspeccionService.RevisionSeguimiento revision(long p){
        var revisiones=prospecciones.seguimientos(p,actor).stream().filter(r->r.idProspeccion()==p).toList();
        assertEquals(1,revisiones.size(),"Este fixture conserva una única relación de seguimiento de su prospección");
        return revisiones.getFirst();
    }
    private long abrirPreparacion(long p,Long prospeccion){long id=Objects.requireNonNull(new TransactionTemplate(gestor).execute(t->encargos.abrir(new EncargoService.DatosApertura(p,actor.idRolOperativo(),"INT-"+UUID.randomUUID().toString().substring(0,12),OperacionInmobiliaria.VENTA,null,null,null,"Preparación privada TEST",null,Procedencia.deLaPantalla(),List.of(),prospeccion==null?null:new EncargoService.OrigenProspeccion(EncargoService.TipoOrigenProspeccion.PROSPECCION,prospeccion)),actor))).id();EvidenciaC4DePrueba.recurso(actor,"ENCARGO",id,null);return id;}
    private static InteresadosDeDemanda.Referencia referenciaNuevo(String nombre,String telefono){return new InteresadosDeDemanda.Referencia(InteresadosDeDemanda.TipoReferencia.CONTACTO_NUEVO,null,null,null,new InteresadosDeDemanda.DatosContacto("N",null,null,nombre,telefono,null));}
    private static RequerimientoService.DatosRequerimiento datosDemanda(InteresadosDeDemanda.Referencia ref,boolean alta){return new RequerimientoService.DatosRequerimiento(null,null,null,null,null,null,alta?"VENTA":null,alta?List.of("TERRENO"):null,null,null,null,null,null,null,null,null,null,alta?List.of():null,ref,null,null,null,null,Set.of());}
    private long propiedadDeclarada(Actor a,String direccion){long id=propiedades.registrar(new PropiedadUniversalService.ComandoRegistro(clave(),Procedencia.deLaPantalla(),null,"TERRENO","VIVIENDA","Identificación física declarada TEST",
            new PropiedadUniversalService.Ubicacion(direccion,"Surco",null,null,null,null,null,null,null),List.of(),List.of(),List.of(),null),a).idPropiedad();EvidenciaC4DePrueba.recurso(a,"PROPIEDAD",id,null);return id;}
    private Map<String,Long> negocio(){var r=new LinkedHashMap<String,Long>();for(String tabla:List.of("detalle_cliente","requerimiento_cliente","prospeccion","oportunidad_comercial","captacion","visita","oferta","contrato_alquiler","contrato_compraventa"))r.put(tabla,filas(tabla));return r;}
    private long filas(String tabla){return Objects.requireNonNull(jdbc.queryForObject("select count(*) from "+tabla+" where organizacion_id=?",Long.class,actor.idOrganizacion()));}
    private static FiltrosInteraccion filtro(TipoContexto contexto,Long ref,String q){return new FiltrosInteraccion(contexto,ref,null,null,null,q,1,20);}
    private static EventoDeclarado evento(String referencia,EventoComunicacion e,MomentoHecho m){return new EventoDeclarado(referencia,e,m);}
    private static MomentoHecho momento(int dias){return new MomentoHecho(hoy().plusDays(dias),LocalTime.of(9,0),ZoneOffset.ofHours(-5));}
    private static LocalDate hoy(){return OrigenesDePrueba.hoy();}
    private static String clave(){return "C4-INTERACCION-TEST-"+UUID.randomUUID();}
    private static CorrelacionAutomatico correlacion(){return new CorrelacionAutomatico("INT-TEST-"+UUID.randomUUID(),"turno-TEST",UUID.randomUUID().toString());}
}
