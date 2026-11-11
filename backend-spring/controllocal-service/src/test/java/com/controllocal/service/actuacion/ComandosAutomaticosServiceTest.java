package com.controllocal.service.actuacion;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.persistence.repositorio.ComandoIdempotenteRepository;
import com.controllocal.persistence.repositorio.EventoDominioRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.InteraccionService;
import com.controllocal.service.PropietarioService;
import com.controllocal.service.PropietarioService.DatosPropietario;
import com.controllocal.service.PropietarioService.FichaPropietario;
import com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.captura.MotorDeCaptura.EstadoCaptura;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.ComandosIdempotentes;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.controllocal.service.actuacion.ComandosAutomaticosService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Identidad y snapshot: no duplica las reglas de validación de los casos canónicos. */
class ComandosAutomaticosServiceTest {
    private final PropietarioService propietarios = mock(PropietarioService.class);
    private final InteraccionService interacciones = mock(InteraccionService.class);
    private final com.controllocal.service.VisitaService visitas = mock(com.controllocal.service.VisitaService.class);
    private final com.controllocal.service.ProspeccionService prospecciones = mock(com.controllocal.service.ProspeccionService.class);
    private final com.controllocal.service.actividad.ContextosDeActividad contextos = mock(com.controllocal.service.actividad.ContextosDeActividad.class);
    private final MotorDeCaptura captura = mock(MotorDeCaptura.class);
    private final ComandoIdempotenteRepository repo = mock(ComandoIdempotenteRepository.class);
    private final EventoDominioRepository eventos = mock(EventoDominioRepository.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Documentos documentos = new Documentos(json);
    private final Map<String, ComandoIdempotente> guardados = new HashMap<>();
    private final Actor actor = new Actor(7, 20, 30, Actor.AGENTE);
    private final CorrelacionAutomatico correlacion = new CorrelacionAutomatico("tarea-uno", "turno-uno", "mensaje-uno");
    private final Procedencia origen = Procedencia.deAgente("API", "KAIROS", "determinista", "1",
            "tarea-uno", "turno-uno", "mensaje-uno", "Registrar a María");
    private ComandosAutomaticosService servicio;

    @BeforeEach void preparar() {
        when(repo.findByOrganizacionIdAndClaveIdempotencia(anyLong(), anyString())).thenAnswer(i ->
                Optional.ofNullable(guardados.get(i.getArgument(0) + ":" + i.getArgument(1))));
        when(repo.save(any())).thenAnswer(i -> {
            ComandoIdempotente c = i.getArgument(0);
            guardados.put(c.getOrganizacionId() + ":" + c.getClaveIdempotencia(), c);
            return c;
        });
        servicio = instancia();
        when(propietarios.registrar(any(), eq(actor))).thenReturn(ficha("María"));
        when(propietarios.obtener(90, actor)).thenReturn(ficha("María"));
    }

    private ComandosAutomaticosService instancia() {
        return new ComandosAutomaticosService(propietarios, interacciones, visitas, prospecciones, contextos, captura, new ComandosIdempotentes(repo),
                repo, eventos, documentos, json,new IdentidadComandoAutomatico(documentos,json));
    }
    private DatosPropietario datos(String nombre) {
        return new DatosPropietario("N", "D", "12345678", nombre, "900000001", null, true, null);
    }
    private FichaPropietario ficha(String nombre) {
        return new FichaPropietario(90L, "N", "D", "12345678", nombre, "900000001", null, "A",
                true, LocalDateTime.of(2026, 10, 27, 12, 3), 0);
    }
    private EstadoCaptura estado(String distrito) {
        return new EstadoCaptura(100L, "BOR-100", "REGISTRAR_PROPIEDAD", "EN_CURSO", "API",
                Map.of("distrito", distrito), List.of("tipoPropiedad"), null, false, null, null,
                LocalDateTime.of(2026, 10, 27, 12, 3),0,null,Map.of(),null,null,List.of());
    }

    /** Versión observada del fixture, nunca una lectura actual para ocultar un conflicto. */
    private MotorDeCaptura.Avance avance(Long id,Integer version,Map<String,String> datos) {
        var respuestas = new LinkedHashMap<String,MotorDeCaptura.Respuesta>();
        datos.forEach((clave,valor)->respuestas.put(clave,MotorDeCaptura.Respuesta.conocida(valor)));
        return new MotorDeCaptura.Avance("REGISTRAR_PROPIEDAD",id,version,respuestas,null);
    }

    @Test void elReplayDevuelveElOriginalSinReejecutarNiRecrearEvento() {
        var original = servicio.registrarPropietario(datos("María"), actor, correlacion, origen);
        when(propietarios.obtener(90, actor)).thenReturn(ficha("María actualizada"));
        assertEquals(original, servicio.registrarPropietario(datos("María"), actor, correlacion, origen));
        verify(propietarios, times(1)).registrar(any(), eq(actor));
        verify(repo, times(1)).save(any());
        verify(eventos, times(1)).save(any());
        assertEquals(64, original.clave().length());
    }

    @Test void unaInstanciaNuevaReconsultaSinMemoriaNiReescritura() {
        var original = servicio.registrarPropietario(datos("María"), actor, correlacion, origen);
        var reiniciada = instancia();
        assertEquals(original, reiniciada.consultarPropietario(actor, original.clave()).orElseThrow());
        verify(propietarios, times(1)).registrar(any(), eq(actor));
        verify(repo, times(1)).bloquearIdentidad(anyLong());
    }

    @Test void cambiarSoloMayusculasDelCuerpoEsContenidoDistinto() {
        servicio.registrarPropietario(datos("María"), actor, correlacion, origen);
        assertThrows(ConflictoException.class,
                () -> servicio.registrarPropietario(datos("MARÍA"), actor, correlacion, origen));
        verify(propietarios, times(1)).registrar(any(), eq(actor));
    }

    @Test void identidadNoDependeDelContenidoNiDelModeloPosterior() {
        String clave = servicio.clavePara(REGISTRAR_PROPIETARIO, actor, correlacion);
        assertEquals(clave, servicio.clavePara(REGISTRAR_PROPIETARIO, actor, correlacion));
        assertNotEquals(clave, servicio.clavePara(REGISTRAR_INTERACCION, actor, correlacion));
        assertNotEquals(clave, servicio.clavePara(REGISTRAR_PROPIETARIO, new Actor(8, 20, 30, Actor.AGENTE), correlacion));
        assertNotEquals(clave, servicio.clavePara(REGISTRAR_PROPIETARIO, new Actor(7, 21, 30, Actor.AGENTE), correlacion));
        assertNotEquals(clave, servicio.clavePara(REGISTRAR_PROPIETARIO, new Actor(7, 20, 31, Actor.AGENTE), correlacion));
        assertNotEquals(clave, servicio.clavePara(REGISTRAR_PROPIETARIO, new Actor(7, 20, 30, Actor.BROKER), correlacion));
        verifyNoInteractions(propietarios, interacciones, captura, repo, eventos);
    }

    @Test void correlacionCompletaYConMayusculasNoColisionaPorRecorteDeProcedencia() {
        String prefijo = "a".repeat(140);
        assertNotEquals(servicio.clavePara(REGISTRAR_PROPIETARIO, actor, new CorrelacionAutomatico(prefijo + "A", "turno", "mensaje")),
                servicio.clavePara(REGISTRAR_PROPIETARIO, actor, new CorrelacionAutomatico(prefijo + "a", "turno", "mensaje")));
        assertNotEquals(servicio.clavePara(REGISTRAR_PROPIETARIO, actor, new CorrelacionAutomatico("c", prefijo + "1", "m")),
                servicio.clavePara(REGISTRAR_PROPIETARIO, actor, new CorrelacionAutomatico("c", prefijo + "2", "m")));
        assertNotEquals(servicio.clavePara(REGISTRAR_PROPIETARIO, actor, new CorrelacionAutomatico("c", "t", prefijo + "1")),
                servicio.clavePara(REGISTRAR_PROPIETARIO, actor, new CorrelacionAutomatico("c", "t", prefijo + "2")));
    }

    @Test void otraCorrelacionConElMismoContenidoRepresentaOtraOperacion() {
        servicio.registrarPropietario(datos("María"), actor, correlacion, origen);
        servicio.registrarPropietario(datos("María"), actor, new CorrelacionAutomatico("tarea-uno", "turno-dos", "mensaje-dos"), origen);
        verify(propietarios, times(2)).registrar(any(), eq(actor));
        assertEquals(2, guardados.size());
    }

    @Test void consultarOtraCapacidadActorOTenantNoEntregaDatos() {
        var original = servicio.registrarPropietario(datos("María"), actor, correlacion, origen);
        assertTrue(servicio.consultarInteraccion(REGISTRAR_INTERACCION, actor, original.clave()).isEmpty());
        assertTrue(servicio.consultarPropietario(new Actor(8, 20, 30, Actor.AGENTE), original.clave()).isEmpty());
        assertTrue(servicio.consultarPropietario(new Actor(7, 21, 31, Actor.AGENTE), original.clave()).isEmpty());
        assertThrows(AccesoNoAutorizadoException.class, () -> servicio.consultarPropietario(
                new Actor(7, 21, 30, Actor.AGENTE), original.clave()));
    }

    @Test void revocarAccesoBloqueaConsultaYReplayDelResultado() {
        var original = servicio.registrarPropietario(datos("María"), actor, correlacion, origen);
        when(propietarios.obtener(90, actor)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class, () -> servicio.consultarPropietario(actor, original.clave()));
        assertThrows(AccesoNoAutorizadoException.class,
                () -> servicio.registrarPropietario(datos("María"), actor, correlacion, origen));
        verify(propietarios, times(1)).registrar(any(), eq(actor));
    }

    @Test void noLocalizadoNoEsRechazoNiAutorizaReejecutar() {
        assertTrue(servicio.consultarPropietario(actor, "desconocida").isEmpty());
        verifyNoInteractions(propietarios, interacciones, captura, eventos);
        verify(repo, never()).save(any());
        verify(repo, never()).bloquearIdentidad(anyLong());
    }

    @Test void eventoConservaActorCorrelacionYCapacidadSinInventarNotificaciones() {
        var original = servicio.registrarPropietario(datos("María"), actor, correlacion, origen);
        var captor = ArgumentCaptor.forClass(EventoDominio.class);
        verify(eventos).save(captor.capture());
        var e = captor.getValue();
        assertEquals("PROPIETARIO_REGISTRADO", e.getTipo());
        assertEquals(actor.idOrganizacion(), e.getOrganizacionId());
        assertEquals(actor.idRolOperativo(), e.getIdPersonaRol());
        assertEquals("KAIROS", e.getAgente());
        assertEquals("tarea-uno", e.getConversacionId());
        assertEquals("turno-uno", e.getTurnoId());
        assertEquals("mensaje-uno", e.getMensajeId());
        assertEquals(REGISTRAR_PROPIETARIO, e.getHerramienta());
        assertEquals(original.clave(), documentos.comoMapa(e.getCargaUtil()).get("claveComando"));
    }

    @Test void falloDelCasoCanonicoNoEscribeExitoNiEvento() {
        when(propietarios.registrar(any(), eq(actor))).thenThrow(new ReglaNegocioException("Consentimiento requerido"));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.registrarPropietario(datos("María"), actor, correlacion, origen));
        verify(repo, never()).save(any());
        verifyNoInteractions(eventos);
    }

    @Test void respuestaAntiguaNoPisaUnAvancePosteriorNiReconstruyeSuSnapshot() {
        var respuestas = new LinkedHashMap<String, String>();
        respuestas.put("distrito", "Surco");
        when(captura.avanzar(eq(avance(100L,0,respuestas)), any(), eq(actor)))
                .thenReturn(estado("Surco"));
        when(captura.consultar(100, actor)).thenReturn(estado("Miraflores"));
        var original = servicio.avanzarCaptura(avance(100L,0,respuestas), actor, correlacion, origen);
        assertEquals(original, servicio.avanzarCaptura(avance(100L,0,Map.of("distrito", "Surco")), actor, correlacion, origen));
        assertEquals("Surco", servicio.consultarCaptura(actor, RESPONDER_CAPTURA, original.clave()).orElseThrow().resultado().conocido().get("distrito"));
        verify(captura, times(1)).avanzar(any(MotorDeCaptura.Avance.class), any(), eq(actor));
    }

    @Test void mapaEnOtroOrdenTieneLaMismaHuellaDeCaptura() {
        when(captura.avanzar(any(MotorDeCaptura.Avance.class), any(), eq(actor))).thenReturn(estado("Surco"));
        when(captura.consultar(100, actor)).thenReturn(estado("Surco"));
        var uno = new LinkedHashMap<String, String>(); uno.put("distrito", "Surco"); uno.put("tipoPropiedad", "DEPARTAMENTO");
        var dos = new LinkedHashMap<String, String>(); dos.put("tipoPropiedad", "DEPARTAMENTO"); dos.put("distrito", "Surco");
        assertEquals(servicio.avanzarCaptura(avance(null,null,uno), actor, correlacion, origen),
                servicio.avanzarCaptura(avance(null,null,dos), actor, correlacion, origen));
        verify(captura, times(1)).avanzar(any(MotorDeCaptura.Avance.class), any(), eq(actor));
    }

    @Test void unaInstantaneaConEntradaAlteradaFallaCerrada() {
        var original = servicio.registrarPropietario(datos("María"), actor, correlacion, origen);
        String indice = actor.idOrganizacion() + ":" + original.clave();
        var c = guardados.get(indice);
        var contenido = documentos.comoMapa(c.getResultado()); contenido.put("entrada", Map.of("nombre", "alterado"));
        guardados.put(indice, ComandoIdempotente.de(actor.idOrganizacion(), original.clave(), c.getTipoComando(),
                c.getHuella(), c.getEntidadTipo(), c.getEntidadId(), actor.idRolOperativo(), "API", "KAIROS", "mensaje-uno",
                documentos.objetoCanonico(contenido)));
        assertThrows(IllegalStateException.class, () -> servicio.consultarPropietario(actor, original.clave()));
    }

    @Test void unaInstantaneaConResultadoAlteradoFallaAntesDeEntregarlo() {
        var original = servicio.registrarPropietario(datos("María"), actor, correlacion, origen);
        String indice = actor.idOrganizacion() + ":" + original.clave();
        var c = guardados.get(indice);
        var contenido = documentos.comoMapa(c.getResultado());
        var resultado = json.convertValue(contenido.get("resultado"), new TypeReference<LinkedHashMap<String, Object>>() { });
        resultado.put("nombre", "alterado"); contenido.put("resultado", resultado);
        guardados.put(indice, ComandoIdempotente.de(actor.idOrganizacion(), original.clave(), c.getTipoComando(),
                c.getHuella(), c.getEntidadTipo(), c.getEntidadId(), actor.idRolOperativo(), "API", "KAIROS", "mensaje-uno",
                documentos.objetoCanonico(contenido)));
        assertThrows(IllegalStateException.class, () -> servicio.consultarPropietario(actor, original.clave()));
        verify(propietarios, times(1)).obtener(90, actor);
    }

    @Test void identificadorDelResultadoDebeCoincidirConLaEntidadDelComando() {
        var original = servicio.registrarPropietario(datos("María"), actor, correlacion, origen);
        String indice = actor.idOrganizacion() + ":" + original.clave();
        var c = guardados.get(indice);
        guardados.put(indice, ComandoIdempotente.de(actor.idOrganizacion(), original.clave(), c.getTipoComando(),
                c.getHuella(), c.getEntidadTipo(), 91L, actor.idRolOperativo(), "API", "KAIROS", "mensaje-uno", c.getResultado()));
        assertThrows(IllegalStateException.class, () -> servicio.consultarPropietario(actor, original.clave()));
        verify(propietarios, never()).obtener(91, actor);
    }

    @Test void recuperarAntesDeBuscarPropietarioNoReescribeNiOcultaConflictoDeContenido() {
        assertTrue(servicio.recuperarPropietario(datos("María"), actor, correlacion).isEmpty());
        var original = servicio.registrarPropietario(datos("María"), actor, correlacion, origen);
        assertEquals(original, servicio.recuperarPropietario(datos("María"), actor, correlacion).orElseThrow());
        assertThrows(ConflictoException.class, () -> servicio.recuperarPropietario(datos("MARÍA"), actor, correlacion));
        verify(propietarios, times(1)).registrar(any(), eq(actor));
        verify(repo, times(1)).bloquearIdentidad(anyLong());
    }

    @Test void aislamientoAmbientalDistintoSeRechazaAntesDeBloquearOEjecutar() {
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try {
            assertThrows(IllegalStateException.class,
                    () -> servicio.registrarPropietario(datos("María"), actor, correlacion, origen));
            verifyNoInteractions(propietarios, interacciones, captura, repo, eventos);
        } finally { TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null); }
    }

    private ActuacionesService.ContextoSesion sesionActividad() {
        return new ActuacionesService.ContextoSesion("identidad",Long.MAX_VALUE,"sid-actividad",4);
    }
    private InteraccionService.DatosInteraccion comunicacion(String resumen) {
        return new InteraccionService.DatosInteraccion(List.of(),null,null,
                com.controllocal.service.actividad.CatalogoInteracciones.TipoActividad.LLAMADA,
                com.controllocal.service.actividad.CatalogoInteracciones.CanalComunicacion.TELEFONO,null,
                List.of(new InteraccionService.EventoDeclarado("intento-real",
                        com.controllocal.service.actividad.CatalogoInteracciones.EventoComunicacion.LLAMADA_SALIENTE,
                        new InteraccionService.MomentoHecho(java.time.LocalDate.of(2026,10,30),null,null))),
                com.controllocal.service.actividad.CatalogoInteracciones.ResultadoInteraccion.SIN_RESPUESTA,
                new InteraccionService.MomentoHecho(java.time.LocalDate.of(2026,10,30),null,null),
                resumen,List.of(),null,null,"Declaración del profesional",null);
    }
    private InteraccionService.FichaInteraccion comunicacionGuardada(long version,String resumen) {
        var datos=new LinkedHashMap<String,Object>();
        datos.put("id",91L);datos.put("version",version);datos.put("idAgente",actor.idRolOperativo());
        datos.put("datos",comunicacion(resumen));datos.put("vinculos",List.of());
        datos.put("hechosPosteriores",List.of());datos.put("versionDiccionario","C4-COMUNICACION-20261030-1");
        datos.put("registradoEn",java.time.Instant.parse("2026-10-30T17:00:00Z"));
        return json.convertValue(datos,InteraccionService.FichaInteraccion.class);
    }
    @Test void c4ReplayAutorizaVersionOriginalAunqueLaActualSeaDistinta() {
        var d=comunicacion("No atendieron");var f=comunicacionGuardada(0,"No atendieron");var s=sesionActividad();
        when(interacciones.registrar(eq(d),eq(actor),eq(s),any())).thenReturn(f);
        when(interacciones.obtenerVersion(91,0,actor)).thenReturn(f);
        var original=servicio.registrarInteraccion(d,actor,s,correlacion,origen);
        assertEquals(original,instancia().registrarInteraccion(d,actor,s,correlacion,origen));
        verify(interacciones,times(1)).registrar(any(),eq(actor),eq(s),any());
        verify(interacciones,times(2)).obtenerVersion(91,0,actor);
        verify(interacciones,never()).obtener(anyLong(),any());
        verify(repo,times(1)).save(any());verify(eventos,times(1)).save(any());
    }
    @Test void c4RevocarCampoDeVersionOriginalImpideReciboAunqueActualSigaAccesible() {
        var d=comunicacion("Contacto antiguo");var s=sesionActividad();
        when(interacciones.registrar(eq(d),eq(actor),eq(s),any())).thenReturn(comunicacionGuardada(0,"Contacto antiguo"));
        var original=servicio.registrarInteraccion(d,actor,s,correlacion,origen);
        when(interacciones.obtener(91,actor)).thenReturn(comunicacionGuardada(1,"Sin vínculo anterior"));
        when(interacciones.obtenerVersion(91,0,actor)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultarInteraccion(REGISTRAR_INTERACCION,actor,original.clave()));
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.registrarInteraccion(d,actor,s,correlacion,origen));
        verify(interacciones,times(1)).registrar(any(),eq(actor),eq(s),any());
        verify(repo,times(1)).save(any());verify(eventos,times(1)).save(any());
    }
    @Test void c4MismaCorrelacionOtroContenidoNoEjecutaOtraComunicacion() {
        var s=sesionActividad();
        when(interacciones.registrar(any(),eq(actor),eq(s),any())).thenReturn(comunicacionGuardada(0,"Primero"));
        servicio.registrarInteraccion(comunicacion("Primero"),actor,s,correlacion,origen);
        assertThrows(ConflictoException.class,()->servicio.registrarInteraccion(comunicacion("Otro"),actor,s,correlacion,origen));
        verify(interacciones,times(1)).registrar(any(),eq(actor),eq(s),any());
    }
    @Test void c4SesionRevocadaSeDeniegaAntesDeBuscarReciboOEscribir() {
        var s=sesionActividad();doThrow(new AccesoNoAutorizadoException()).when(contextos).iniciar(actor,s);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.registrarInteraccion(comunicacion("Llamada"),actor,s,correlacion,origen));
        verifyNoInteractions(repo,eventos,interacciones,visitas);
    }
    @Test void c4PantallaConservaProcedenciaHumanaSinInventarAgenteIA() {
        var s=sesionActividad();
        when(interacciones.registrar(any(),eq(actor),eq(s),any())).thenReturn(comunicacionGuardada(0,"Llamada"));
        servicio.registrarInteraccion(comunicacion("Llamada"),actor,s,correlacion,Procedencia.deLaPantalla());
        var p=ArgumentCaptor.forClass(Procedencia.class);
        verify(interacciones).registrar(any(),eq(actor),eq(s),p.capture());
        assertFalse(p.getValue().laFormuloUnAgente());assertEquals("SPA",p.getValue().canal());
        assertEquals("turno-uno",p.getValue().turnoId());assertEquals(REGISTRAR_INTERACCION,p.getValue().herramienta());
    }
    @Test void c4VisitaRecuperaProgramacionExactaSinReprogramarEnReintento() {
        var s=sesionActividad();
        var d=new com.controllocal.service.VisitaService.DatosVisita(12,0L,java.time.LocalDate.of(2026,10,31),null,"Visita",List.of(),null);
        var f=json.convertValue(Map.of("id",92L,"idOportunidad",12L,"version",0L,"estado","P","participantes",List.of()),com.controllocal.service.VisitaService.FichaVisita.class);
        when(visitas.programar(eq(d),eq(actor),eq(s),any())).thenReturn(f);
        var original=servicio.programarVisita(d,actor,s,correlacion,origen);
        assertEquals(original,servicio.programarVisita(d,actor,s,correlacion,origen));
        assertEquals(original,instancia().consultarVisita(PROGRAMAR_VISITA,actor,original.clave()).orElseThrow());
        verify(visitas,times(1)).programar(any(),eq(actor),eq(s),any());
        verify(visitas,times(3)).obtenerVersion(92,0,actor);
        verify(visitas,never()).obtener(anyLong(),any());verify(eventos,times(1)).save(any());
    }
    @Test void c4ConsultaDeVisitaNoAdmiteOtraFamiliaNiUsaSuRepositorio() {
        assertThrows(ReglaNegocioException.class,()->servicio.consultarVisita(REGISTRAR_INTERACCION,actor,"clave"));
        assertThrows(ReglaNegocioException.class,()->servicio.consultarInteraccion(PROGRAMAR_VISITA,actor,"clave"));
        verifyNoInteractions(repo,interacciones,visitas);
    }

    @Test void motorYHuellaUsanLaMismaCopiaPrivadaAunqueElLlamadorCambieSuMapa() {
        var respuestas = new LinkedHashMap<String, String>();
        respuestas.put("distrito", "Surco"); respuestas.put("comentario", null);
        when(captura.avanzar(any(MotorDeCaptura.Avance.class), any(), eq(actor))).thenAnswer(i -> {
            respuestas.put("distrito", "Valor posterior del llamador");
            MotorDeCaptura.Avance recibido = i.getArgument(0);
            assertNull(recibido.respuestas().get("comentario").valor());
            assertThrows(UnsupportedOperationException.class, () -> recibido.respuestas().put("distrito", MotorDeCaptura.Respuesta.conocida("Intento de mutación")));
            return estado(recibido.respuestas().get("distrito").valor());
        });
        when(captura.consultar(100, actor)).thenReturn(estado("Surco"));
        var original = servicio.avanzarCaptura(avance(null,null,respuestas), actor, correlacion, origen);
        assertEquals("Surco", original.resultado().conocido().get("distrito"));
        assertEquals(original, servicio.consultarCaptura(actor, ABRIR_CAPTURA, original.clave()).orElseThrow());
    }
}
