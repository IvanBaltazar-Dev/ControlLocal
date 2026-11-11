package com.controllocal.service.materialia;

import com.controllocal.persistence.materialia.RegistroMaterialIaRepository;
import com.controllocal.persistence.materialia.RegistroMaterialIaRepository.Operacion;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.FuentesMaterialIa.*;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.ProveedorMaterialIa.*;
import com.controllocal.service.materialia.ControlesMaterialIa.*;
import com.controllocal.domain.presupuesto.UnidadesDeConsumo.*;
import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.ReferenciaInterna;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.controllocal.service.materialia.ControlesMaterialIa.ETAPA_FINANCIERA;

/** Unidad de la frontera de transferencia. TX, cobro y persistencia reales se demuestran en PG. */
class MaterialIaServiceImplTest {
    final RegistroMaterialIaRepository repo=mock(RegistroMaterialIaRepository.class);
    final PresupuestoIaService presupuesto=mock(PresupuestoIaService.class);
    final ContextoPresupuestoB0 contexto=mock(ContextoPresupuestoB0.class);
    final PerfilesMaterialIa perfiles=mock(PerfilesMaterialIa.class);
    final FuentesMaterialIa fuente=mock(FuentesMaterialIa.class);
    final ProveedorMaterialIa proveedor=mock(ProveedorMaterialIa.class);
    final ControlesMaterialIa controles=mock(ControlesMaterialIa.class);
    final PlatformTransactionManager gestor=mock(PlatformTransactionManager.class);
    final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules());
    final Actor actor=new Actor(7,11,13,Actor.AGENTE);
    final ContextoSesion sid=new ContextoSesion("s".repeat(64),Instant.now().plusSeconds(600).getEpochSecond(),"SID-SINTETICO-TEST",1);
    final Solicitud solicitud=new Solicitud(UUID.randomUUID(),Accion.REDACTAR_FICHA,new Recurso("PROPIEDAD","123"),
            "ORIGEN-TEST-1",null,"PETICION_LIBRE_PRIVADA_TEST_NO_ENVIAR");
    final DestinoTratamiento destino=new DestinoTratamiento("PROVEEDOR_TEST","PERFIL_CONGELADO_TEST","VERSION_CONGELADA_TEST");
    final AtomicReference<Operacion> estado=new AtomicReference<>();
    final Map<String,String> eventos=new LinkedHashMap<>();
    Snapshot snapshot;MaterialIaServiceImpl servicio;

    @BeforeEach void preparar(){
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        when(gestor.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        snapshot=snapshot("INSTRUCCION_PERMITIDA_TEST",EstadoEvidencia.ACREDITADA);estado.set(operacion(snapshot));
        when(repo.obtener(actor.idOrganizacion(),solicitud.operacion())).thenAnswer(i->Optional.of(estado.get()));
        // El doble sólo conserva la fase observada; no simula ni certifica presupuesto/commit/locks.
        doAnswer(i->{estado.set(fase(estado.get(),i.getArgument(1),i.getArgument(2)));return null;})
                .when(repo).fase(any(),anyString(),nullable(String.class));
        when(repo.evidencia(any(),anyString(),anyString())).thenAnswer(i->{eventos.put(i.getArgument(1),i.getArgument(2));return (long)eventos.size();});
        when(repo.ultimoHecho(anyLong(),any(),anyString())).thenAnswer(i->Optional.ofNullable(eventos.get(i.getArgument(2))));
        when(repo.ahora()).thenAnswer(i->Instant.now());
        when(presupuesto.iniciar(any(),eq(actor),eq(sid))).thenReturn(true);
        when(fuente.preparar(any(),eq(actor),eq(sid))).thenAnswer(i->snapshot);
        when(proveedor.identificador()).thenReturn(destino.proveedor());
        when(proveedor.atiende(solicitud.accion(),destino.perfil(),destino.versionPerfil())).thenReturn(true);
        when(controles.atiende(solicitud.accion(),destino.perfil(),destino.versionPerfil())).thenReturn(true);
        when(proveedor.ejecutar(any(),any())).thenThrow(new ProcesamientoIncierto());
        var autoridad=new AutoridadMaterialIa(repo,json,List.of(fuente),perfiles);
        servicio=new MaterialIaServiceImpl(repo,presupuesto,contexto,autoridad,perfiles,json,List.of(proveedor),List.of(controles),List.of(),gestor);
    }
    Snapshot snapshot(String instruccion,EstadoEvidencia evidencia){return new Snapshot("ORIGEN-TEST-1","a".repeat(64),
            Map.of("tipo","TERRENO"),instruccion,Map.of("utilidad","LOCAL_TEST"),List.of(),
            new EvidenciaTratamiento(evidencia,"FINALIDAD_TEST",List.of("DATOS_SINTETICOS"),"CONDICIONES_TEST",
                    "PROVEEDOR_TEST","DECISION_TEST","REFERENCIA_PRIVADA_TEST","LEGITIMACION_TEST","PREFERENCIA_TEST"));}
    Operacion operacion(Snapshot fuente){return operacion(fuente,solicitud);}
    Operacion operacion(Snapshot fuente,Solicitud solicitud){String contenido=json.objetoCanonico(fuente);
        return new Operacion(actor.idOrganizacion(),solicitud.operacion(),actor.idPersona(),actor.idRolOperativo(),actor.rolEfectivo(),sid.identidad(),
                solicitud.accion().name(),Idempotencia.huellaExacta(json.objetoCanonico(Map.of("solicitud",solicitud,"actor",actor))),json.objetoCanonico(solicitud),
                contenido,Idempotencia.huellaExacta(contenido),destino.perfil(),destino.versionPerfil(),
                PresupuestoIaService.claveDeIntento(new ReferenciaInterna(actor.idOrganizacion(),solicitud.operacion())),
                "RESERVADA",null,null,null,null,null,0);
    }
    static Operacion fase(Operacion o,String fase,String motivo){return new Operacion(o.organizacion(),o.id(),o.persona(),o.rol(),o.rolEfectivo(),o.sesion(),
            o.accion(),o.peticion(),o.solicitud(),o.snapshot(),o.huellaSnapshot(),o.perfil(),o.versionPerfil(),o.claveExterna(),fase,
            o.medicion(),o.salida(),o.control(),o.resultado(),motivo,o.version()+1);}
    void solicitar(){servicio.solicitar(solicitud,actor,sid);}

    PresupuestoIaService.ResultadoFacturable medicionTest(String proveedor,String referencia){return new PresupuestoIaService.ResultadoFacturable(List.of(
            new PresupuestoIaService.MedicionFacturable(UUID.randomUUID(),proveedor,"MODELO_SINTETICO","TARIFA_TEST",
                    List.of(new Medicion("unidad_test","u",BigDecimal.ONE,null)),List.of(new Tarifa("unidad_test","u",new BigDecimal("0.1"),BigDecimal.ONE)),null,referencia)));}
    static Operacion datos(Operacion o,String fase,String medicion,String salida,String control,String resultado){return new Operacion(o.organizacion(),o.id(),o.persona(),o.rol(),o.rolEfectivo(),o.sesion(),
            o.accion(),o.peticion(),o.solicitud(),o.snapshot(),o.huellaSnapshot(),o.perfil(),o.versionPerfil(),o.claveExterna(),fase,medicion,salida,control,resultado,null,o.version()+1);}
    void prepararControlFacturable(Facturable externo){
        estado.set(fase(estado.get(),"INTENCION",null));
        when(externo.atiende(any(),anyString(),anyString())).thenReturn(true);when(externo.destino(anyString(),anyString())).thenReturn(destino);
        when(presupuesto.iniciarEtapa(any(),eq(ETAPA_FINANCIERA),eq(actor),eq(sid))).thenReturn(true);
        when(presupuesto.conciliar(any(),any())).thenReturn(new PresupuestoIaService.Admision(solicitud.operacion(),"LIQUIDADA"));
        doAnswer(i->{var o=estado.get();estado.set(datos(o,"MEDIDA",i.getArgument(1),i.getArgument(2),null,null));return null;}).when(repo).medir(any(),anyString(),anyString());
        doAnswer(i->{var o=estado.get();estado.set(datos(o,i.getArgument(2),o.medicion(),o.salida(),i.getArgument(1),null));return null;}).when(repo).controlar(any(),anyString(),anyString());
        doAnswer(i->{var o=estado.get();estado.set(datos(o,"DISPONIBLE",o.medicion(),o.salida(),o.control(),i.getArgument(1)));return null;}).when(repo).resultado(any(),anyString());
        var autoridad=new AutoridadMaterialIa(repo,json,List.of(fuente),perfiles);
        servicio=new MaterialIaServiceImpl(repo,presupuesto,contexto,autoridad,perfiles,json,List.of(proveedor),List.of(externo),List.of(),gestor);
    }
    com.controllocal.service.presupuesto.ObservacionHttpIa observacion(String clave,com.controllocal.service.presupuesto.ObservacionHttpIa.Resultado resultado){
        return new com.controllocal.service.presupuesto.ObservacionHttpIa("HTTP-IA-1",clave,"PROVEEDOR_TEST","MODELO_SINTETICO",destino.versionPerfil(),
                Instant.parse("2026-11-04T12:00:00Z"),17,resultado,resultado==com.controllocal.service.presupuesto.ObservacionHttpIa.Resultado.RESPUESTA_COMPLETA?200:null,true);
    }
    @Test void observacionDeIntentoInciertoSeIntentaConservarSinReclamarNiMedirCero(){
        var fallo=new ProcesamientoIncierto();var observada=observacion(estado.get().claveExterna(),com.controllocal.service.presupuesto.ObservacionHttpIa.Resultado.TIMEOUT);
        doAnswer(i->{java.util.function.Consumer<com.controllocal.service.presupuesto.ObservacionHttpIa> capturar=i.getArgument(1);capturar.accept(observada);throw fallo;})
                .when(proveedor).ejecutar(any(),any());
        assertSame(fallo,assertThrows(ProcesamientoIncierto.class,this::solicitar));
        var orden=inOrder(presupuesto,gestor);orden.verify(presupuesto).marcarIncierta(any());orden.verify(gestor).commit(any());
        orden.verify(presupuesto).registrarObservacionHttp(eq(new ReferenciaInterna(actor.idOrganizacion(),solicitud.operacion())),isNull(),eq(observada));
        verify(proveedor,times(1)).ejecutar(any(),any());verify(presupuesto,never()).registrarMediciones(any(),any());
    }
    @Test void observacionAusenteNoSeInventaComoUnIntentoConCostoOTiempoCero(){
        assertThrows(ProcesamientoIncierto.class,this::solicitar);
        verify(presupuesto,never()).registrarObservacionHttp(any(),any(),any());verify(proveedor,times(1)).ejecutar(any(),any());
    }
    @Test void fallaAlGuardarObservacionNoImpideConservarConsumoGenYQcConocidos(){
        var externo=mock(Facturable.class);prepararControlFacturable(externo);
        var gen=medicionTest("PROVEEDOR_TEST","GEN_TEST");var qc=medicionTest("PROVEEDOR_TEST","QC_TEST");
        doAnswer(i->{Peticion p=i.getArgument(0);java.util.function.Consumer<com.controllocal.service.presupuesto.ObservacionHttpIa> c=i.getArgument(1);
            c.accept(observacion(p.claveExterna(),com.controllocal.service.presupuesto.ObservacionHttpIa.Resultado.RESPUESTA_COMPLETA));
            return new Salida("Texto TEST",null,null,"PROCESO_TEST","1",gen);}).when(proveedor).ejecutar(any(),any());
        when(externo.evaluarFacturable(any(PeticionControl.class),any())).thenAnswer(i->{PeticionControl p=i.getArgument(0);
            java.util.function.Consumer<com.controllocal.service.presupuesto.ObservacionHttpIa> c=i.getArgument(1);
            c.accept(observacion(p.claveEtapa(),com.controllocal.service.presupuesto.ObservacionHttpIa.Resultado.RESPUESTA_COMPLETA));
            return new EvaluacionFacturable(new Control(Decision.RECHAZADA,"QC_TEST",Instant.now(),List.of()),qc);});
        doThrow(new IllegalStateException("PERSISTENCIA_OBSERVACION_TEST")).when(presupuesto).registrarObservacionHttp(any(),nullable(String.class),any());
        // El control incompleto sigue siendo la misma causa final, después de conservar las dos mediciones.
        var fallo=assertThrows(IllegalStateException.class,this::solicitar);assertFalse(fallo.getMessage().contains("PERSISTENCIA_OBSERVACION_TEST"));
        verify(presupuesto).registrarMediciones(any(),eq(gen));var conjunto=ArgumentCaptor.forClass(PresupuestoIaService.ResultadoFacturable.class);
        verify(presupuesto).registrarResultadoEtapa(any(),eq(ETAPA_FINANCIERA),conjunto.capture());
        assertEquals(List.of(gen.mediciones().getFirst(),qc.mediciones().getFirst()),conjunto.getValue().mediciones());
        verify(presupuesto,times(2)).registrarObservacionHttp(any(),nullable(String.class),any());
        assertNotNull(estado.get().medicion());assertTrue(eventos.containsKey("QC_RESULTADO"));verify(proveedor,times(1)).ejecutar(any(),any());
        // La telemetría nunca se intercala antes del intento de commit de respuesta y consumo.
        var orden=inOrder(repo,presupuesto,gestor);
        orden.verify(repo).medir(any(),anyString(),anyString());orden.verify(presupuesto).registrarMediciones(any(),eq(gen));
        orden.verify(gestor).commit(any());orden.verify(presupuesto).registrarObservacionHttp(any(),isNull(),any());
        orden.verify(repo).evidencia(any(),eq("QC_RESULTADO"),anyString());
        orden.verify(presupuesto).registrarResultadoEtapa(any(),eq(ETAPA_FINANCIERA),any());orden.verify(gestor).commit(any());
        orden.verify(presupuesto).conciliar(any(),any());orden.verify(gestor).commit(any());
        orden.verify(presupuesto).registrarObservacionHttp(any(),eq(ETAPA_FINANCIERA),any());
    }
    @Test void controlFacturableConservaGeneracionYControlAntesDeUnaSolaConciliacion(){
        var externo=mock(Facturable.class);prepararControlFacturable(externo);
        var gen=medicionTest("PROVEEDOR_TEST","GEN_TEST");var qc=medicionTest("PROVEEDOR_TEST","QC_TEST");
        doReturn(new Salida("Texto TEST",null,null,"PROCESO_TEST","1",gen)).when(proveedor).ejecutar(any(),any());
        when(externo.evaluarFacturable(any(PeticionControl.class),any())).thenReturn(new EvaluacionFacturable(
                new Control(Decision.RECHAZADA,"QC_TEST",Instant.now(),List.of()),qc));
        // El control sintético es deliberadamente incompleto: se cobra lo causado antes de rechazarlo.
        assertThrows(IllegalStateException.class,this::solicitar);
        var conjunto=ArgumentCaptor.forClass(PresupuestoIaService.ResultadoFacturable.class);
        var orden=inOrder(presupuesto,externo,repo);
        orden.verify(presupuesto).registrarMediciones(any(),eq(gen));orden.verify(presupuesto).programarEtapa(any(),eq(ETAPA_FINANCIERA),eq(destino.proveedor()),anyString());
        orden.verify(presupuesto).iniciarEtapa(any(),eq(ETAPA_FINANCIERA),eq(actor),eq(sid));
        orden.verify(externo).evaluarFacturable(any(PeticionControl.class),any());orden.verify(repo).evidencia(any(),eq("QC_RESULTADO"),anyString());
        orden.verify(presupuesto).registrarResultadoEtapa(any(),eq(ETAPA_FINANCIERA),conjunto.capture());orden.verify(presupuesto).conciliar(any(),any());
        assertEquals(List.of(gen.mediciones().getFirst(),qc.mediciones().getFirst()),conjunto.getValue().mediciones());
        verify(presupuesto,times(1)).conciliar(any(),any());assertNotNull(estado.get().medicion());assertTrue(eventos.containsKey("QC_RESULTADO"));assertNull(estado.get().resultado());
        var entrada=ArgumentCaptor.forClass(PeticionControl.class);verify(externo).evaluarFacturable(entrada.capture(),any());
        assertNull(entrada.getValue().originalAutorizado());verify(fuente,never()).fotoParaProcesar(any(),any(),any(),any());
    }
    @Test void controlVisualRecibeCopiaAutorizadaDelMismoSnapshotTrasClaimYAntesDeTransferencia(){
        var s=new Solicitud(solicitud.operacion(),Accion.ANALIZAR_FOTO,solicitud.recurso(),solicitud.versionOrigen(),UUID.randomUUID(),solicitud.instruccion());
        estado.set(operacion(snapshot,s));var externo=mock(Facturable.class);prepararControlFacturable(externo);
        when(proveedor.atiende(eq(s.accion()),anyString(),anyString())).thenReturn(true);
        byte[] minimizada={11,22,33};when(fuente.fotoParaProcesar(eq(s),eq(snapshot),eq(actor),eq(sid))).thenReturn(minimizada);
        var gen=medicionTest("PROVEEDOR_TEST","GEN_TEST");var salida=new Salida("Analisis TEST",null,null,"PROCESO_TEST","1",gen);
        doReturn(salida).when(proveedor).ejecutar(any(),any());
        when(externo.evaluarFacturable(any(PeticionControl.class),any())).thenReturn(new EvaluacionFacturable(null,medicionTest("PROVEEDOR_TEST","QC_TEST")));
        assertThrows(IllegalStateException.class,()->servicio.solicitar(s,actor,sid));
        var entrada=ArgumentCaptor.forClass(PeticionControl.class);var orden=inOrder(presupuesto,fuente,externo);
        orden.verify(presupuesto).iniciarEtapa(any(),eq(ETAPA_FINANCIERA),eq(actor),eq(sid));
        orden.verify(fuente).revalidar(eq(s),eq(snapshot),eq(actor));
        orden.verify(fuente).fotoParaProcesar(eq(s),eq(snapshot),eq(actor),eq(sid));
        orden.verify(fuente).exigirTransferencia(eq(s),eq(snapshot),eq(destino),eq(actor),eq(sid));
        orden.verify(externo).evaluarFacturable(entrada.capture(),any());
        assertSame(salida,entrada.getValue().salida());
        assertEquals(PresupuestoIaService.claveDeEtapa(new ReferenciaInterna(actor.idOrganizacion(),s.operacion()),ETAPA_FINANCIERA),entrada.getValue().claveEtapa());
        assertEquals(s.accion(),entrada.getValue().accion());
        assertEquals(snapshot.datosPermitidos(),entrada.getValue().datosPermitidos());
        assertEquals(snapshot.instruccionPermitida(),entrada.getValue().instruccionPermitida());
        assertArrayEquals(minimizada,entrada.getValue().originalAutorizado());assertNotSame(minimizada,entrada.getValue().originalAutorizado());
        verify(fuente,times(2)).fotoParaProcesar(eq(s),eq(snapshot),eq(actor),eq(sid));
        verify(presupuesto).registrarMediciones(any(),eq(gen));assertNotNull(estado.get().medicion());
    }
    @ParameterizedTest @ValueSource(strings={"NULA","VACIA","REVOCADA"})
    void originalAusenteODenegadoDespuesDeGenerarCierraSoloQcNoInvocado(String falta){
        var s=new Solicitud(solicitud.operacion(),Accion.ANALIZAR_FOTO,solicitud.recurso(),solicitud.versionOrigen(),UUID.randomUUID(),solicitud.instruccion());
        estado.set(operacion(snapshot,s));var externo=mock(Facturable.class);prepararControlFacturable(externo);
        when(proveedor.atiende(eq(s.accion()),anyString(),anyString())).thenReturn(true);
        var lectura=when(fuente.fotoParaProcesar(eq(s),eq(snapshot),eq(actor),eq(sid))).thenReturn(new byte[]{11,22,33});
        if(falta.equals("REVOCADA"))lectura.thenThrow(new AccesoNoAutorizadoException());else lectura.thenReturn(falta.equals("NULA")?null:new byte[0]);
        var gen=medicionTest("PROVEEDOR_TEST","GEN_TEST");doReturn(new Salida("Analisis TEST",null,null,"PROCESO_TEST","1",gen)).when(proveedor).ejecutar(any(),any());
        var fallo=assertThrows(RuntimeException.class,()->servicio.solicitar(s,actor,sid));
        if(falta.equals("REVOCADA"))assertInstanceOf(AccesoNoAutorizadoException.class,fallo);else assertInstanceOf(IllegalArgumentException.class,fallo);
        verify(externo,never()).evaluarFacturable(any(PeticionControl.class),any());verify(proveedor,times(1)).ejecutar(any(),any());
        verify(fuente,times(1)).exigirTransferencia(eq(s),eq(snapshot),eq(destino),eq(actor),eq(sid));
        verify(presupuesto).acreditarEtapaNoInvocada(any(),eq(ETAPA_FINANCIERA),any());verify(presupuesto).conciliar(any(),any());
        verify(presupuesto,never()).cancelarNoProcesada(any(),any());verify(presupuesto,never()).marcarEtapaIncierta(any(),anyString());
        assertEquals(gen,json.comoObjeto(estado.get().medicion(),PresupuestoIaService.ResultadoFacturable.class));
        assertTrue(eventos.containsKey("QC_NO_INVOCADA"));assertFalse(eventos.containsKey("QC_DESTINO_VERIFICADO"));assertFalse(eventos.containsKey("QC_RESULTADO"));
        assertEquals("FALLO_RESULTADO",estado.get().estado());
    }
    @Test void controlInvocadoSinRespuestaMantieneIncertidumbreYNoSeReclamaOtraVez(){
        var externo=mock(Facturable.class);prepararControlFacturable(externo);var gen=medicionTest("PROVEEDOR_TEST","GEN_TEST");
        doReturn(new Salida("Texto TEST",null,null,"PROCESO_TEST","1",gen)).when(proveedor).ejecutar(any(),any());
        when(externo.evaluarFacturable(any(PeticionControl.class),any())).thenThrow(new ProcesamientoIncierto());
        assertThrows(ProcesamientoIncierto.class,this::solicitar);assertEquals("INCIERTA",estado.get().estado());
        solicitar();verify(externo,times(1)).evaluarFacturable(any(PeticionControl.class),any());verify(proveedor,times(1)).ejecutar(any(),any());
        verify(presupuesto).marcarEtapaIncierta(any(),eq(ETAPA_FINANCIERA));verify(presupuesto,never()).conciliar(any(),any());
        verify(presupuesto,never()).acreditarEtapaNoInvocada(any(),anyString(),any());assertTrue(eventos.containsKey("QC_CLAIM"));assertFalse(eventos.containsKey("QC_RESULTADO"));
    }
    @Test void controlNullConMedicionSeConservaYCierraSinInventarSenales(){
        var externo=mock(Facturable.class);prepararControlFacturable(externo);var gen=medicionTest("PROVEEDOR_TEST","GEN_TEST");var qc=medicionTest("PROVEEDOR_TEST","QC_TEST");
        doReturn(new Salida("Texto TEST",null,null,"PROCESO_TEST","1",gen)).when(proveedor).ejecutar(any(),any());
        when(externo.evaluarFacturable(any(PeticionControl.class),any())).thenReturn(new EvaluacionFacturable(null,qc));
        assertThrows(IllegalStateException.class,this::solicitar);verify(presupuesto).registrarResultadoEtapa(any(),eq(ETAPA_FINANCIERA),any());verify(presupuesto).conciliar(any(),any());
        var respuesta=json.comoObjeto(eventos.get("QC_RESULTADO"),MaterialIaServiceImpl.RespuestaControl.class);
        assertEquals("RECIBIDA",respuesta.estado());assertNull(respuesta.resultado().control());assertEquals(qc,respuesta.resultado().medicion());assertNull(estado.get().resultado());
    }

    @Test void preparacionLocalAdmiteInstruccionPendienteSinProveedorPerfilOReserva(){
        snapshot=snapshot(null,EstadoEvidencia.PENDIENTE);
        var local=servicio.preparar(solicitud,actor,sid);assertEquals("LOCAL_TEST",local.utilidadLocal().get("utilidad"));
        verifyNoInteractions(repo,presupuesto,proveedor,controles,perfiles);
        verify(fuente,never()).exigirTransferencia(any(),any(),any(),any(),any());
    }
    @Test void payloadYDestinoProcedenDelSnapshotYPerfilCongeladosYSeAnotanAntesDeInvocar(){
        assertThrows(ProcesamientoIncierto.class,this::solicitar);
        var peticion=ArgumentCaptor.forClass(Peticion.class);var orden=inOrder(presupuesto,fuente,repo,proveedor);
        orden.verify(presupuesto).iniciar(any(),eq(actor),eq(sid));
        orden.verify(fuente).exigirTransferencia(eq(solicitud),eq(snapshot),eq(destino),eq(actor),eq(sid));
        orden.verify(repo).evidencia(any(),eq("DESTINO_VERIFICADO"),anyString());orden.verify(proveedor).ejecutar(peticion.capture(),any());
        assertEquals(snapshot.instruccionPermitida(),peticion.getValue().instruccion());assertNotEquals(solicitud.instruccion(),peticion.getValue().instruccion());
        assertEquals(snapshot.datosPermitidos(),peticion.getValue().datosPermitidos());assertNull(peticion.getValue().fotoAutorizada());
        String evento=eventos.get("DESTINO_VERIFICADO");assertTrue(evento.contains(destino.versionPerfil()));assertTrue(evento.contains("PREVIA_A_INVOCACION"));
        assertFalse(evento.contains(solicitud.instruccion()));assertFalse(evento.contains(snapshot.instruccionPermitida()));assertFalse(evento.contains("LEGITIMACION_TEST"));
        verifyNoInteractions(perfiles);verify(presupuesto).marcarIncierta(any());verify(presupuesto,never()).cancelarNoProcesada(any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"RECHAZADO","REVOCADO"})
    void ultimaGuardaRechazadaORevocadaNoInvocaYConservaNoInvocacionAcreditada(String motivo){
        RuntimeException rechazo=motivo.equals("REVOCADO")?new AccesoNoAutorizadoException():new CapacidadDeshabilitadaException();
        doThrow(rechazo).when(fuente).exigirTransferencia(any(),any(),any(),any(),any());
        assertSame(rechazo,assertThrows(RuntimeException.class,this::solicitar));
        verify(proveedor,never()).ejecutar(any(),any());verify(presupuesto).cancelarNoProcesada(eq(new ReferenciaInterna(actor.idOrganizacion(),solicitud.operacion())),any());
        assertTrue(eventos.containsKey("DESTINO_DENEGADO"));assertTrue(eventos.containsKey("NO_INVOCACION_PREVIA"));assertFalse(eventos.containsKey("DESTINO_VERIFICADO"));
        assertEquals("CANCELADA",estado.get().estado());verify(presupuesto,never()).conciliar(any(),any());
    }
    @Test void cambioDeIdentidadDelAdaptadorEntreSeleccionYTransferenciaNoLlegaAGuardaNiProveedor(){
        when(proveedor.identificador()).thenReturn(destino.proveedor(),"OTRO_PROVEEDOR_TEST");
        assertThrows(ConflictoException.class,this::solicitar);verify(fuente,never()).exigirTransferencia(any(),any(),any(),any(),any());
        verify(proveedor,never()).ejecutar(any(),any());assertTrue(eventos.get("DESTINO_DENEGADO").contains("OTRO_PROVEEDOR_TEST"));
        assertEquals("CANCELADA",estado.get().estado());verify(presupuesto).cancelarNoProcesada(any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"NULA","VACIA"})
    void snapshotSinInstruccionPermitidaNuncaReutilizaTextoLibre(String caso){
        snapshot=snapshot(caso.equals("NULA")?null:" ",EstadoEvidencia.ACREDITADA);estado.set(operacion(snapshot));
        assertThrows(IllegalStateException.class,this::solicitar);verify(proveedor,never()).ejecutar(any(),any());
        verify(fuente,never()).exigirTransferencia(any(),any(),any(),any(),any());
        assertTrue(eventos.containsKey("DESTINO_DENEGADO"));assertEquals("CANCELADA",estado.get().estado());
        verify(presupuesto).cancelarNoProcesada(any(),any());verify(presupuesto,never()).conciliar(any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"NULA","VACIA"})
    void instruccionPersonalizadaOpcionalNoImpidePrepararOSolicitarConPropositoServidor(String caso){
        var s=new Solicitud(solicitud.operacion(),solicitud.accion(),solicitud.recurso(),solicitud.versionOrigen(),null,caso.equals("NULA")?null:"");
        estado.set(operacion(snapshot,s));assertFalse(servicio.preparar(s,actor,sid).utilidadLocal().isEmpty());
        assertThrows(ProcesamientoIncierto.class,()->servicio.solicitar(s,actor,sid));
        var p=ArgumentCaptor.forClass(Peticion.class);verify(proveedor).ejecutar(p.capture(),any());assertEquals(snapshot.instruccionPermitida(),p.getValue().instruccion());
    }
    @ParameterizedTest @ValueSource(strings={"GASTO","PERFIL","PROVEEDOR","QC"})
    void disponibilidadDistingueUtilidadLocalDeSolicitudRealSinConcederAdmision(String falta){
        when(presupuesto.gastoExternoHabilitado()).thenReturn(!falta.equals("GASTO"));
        when(perfiles.de(solicitud.accion())).thenReturn(new PerfilesMaterialIa.Perfil(destino.perfil(),destino.versionPerfil()));
        switch(falta){case "PERFIL"->when(perfiles.de(solicitud.accion())).thenThrow(new CapacidadDeshabilitadaException());
            case "PROVEEDOR"->when(proveedor.atiende(any(),anyString(),anyString())).thenReturn(false);
            case "QC"->when(controles.atiende(any(),anyString(),anyString())).thenReturn(false);}
        var d=servicio.disponibilidad(solicitud.accion());assertTrue(d.preparacionLocal());assertFalse(d.solicitud());assertNotNull(d.motivo());
        verify(presupuesto,never()).admitir(any(),any(),any());verify(presupuesto,never()).iniciar(any(),any(),any());verify(proveedor,never()).ejecutar(any(),any());
    }
    @Test void disponibilidadCompletaEsConfiguracionYSigueSinReservarNiEnviar(){
        when(presupuesto.gastoExternoHabilitado()).thenReturn(true);when(perfiles.de(solicitud.accion())).thenReturn(new PerfilesMaterialIa.Perfil(destino.perfil(),destino.versionPerfil()));
        var d=servicio.disponibilidad(solicitud.accion());assertTrue(d.preparacionLocal());assertTrue(d.solicitud());assertNull(d.motivo());
        verify(presupuesto,never()).iniciar(any(),any(),any());verify(proveedor,never()).ejecutar(any(),any());
    }
    @Test void guardaInternaRevalidaFuenteActualSinReutilizarElSidDeCreacion(){
        doThrow(new CapacidadDeshabilitadaException()).when(fuente).exigirLecturaHistorica(any(),any(),eq(actor));
        assertThrows(CapacidadDeshabilitadaException.class,()->servicio.exigirLectura(solicitud.operacion(),actor));
        verify(proveedor,never()).ejecutar(any(),any());verify(presupuesto,never()).iniciar(any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"CAPACIDAD_DISPONIBLE","CAPACIDAD_DENEGADA","NAMESPACE_CAMBIADO","LIMITE_AUSENTE"})
    void fotoReservaLugarYCapacidadAntesDeFondoYClaimSinInferirSiNoCabe(String caso){
        var foto=new Solicitud(solicitud.operacion(),Accion.MEJORAR_FOTO,solicitud.recurso(),solicitud.versionOrigen(),UUID.randomUUID(),null);
        estado.set(fase(operacion(snapshot,foto),"INTENCION",null));
        when(proveedor.atiende(Accion.MEJORAR_FOTO,destino.perfil(),destino.versionPerfil())).thenReturn(true);
        when(controles.atiende(Accion.MEJORAR_FOTO,destino.perfil(),destino.versionPerfil())).thenReturn(true);
        when(fuente.fotoParaProcesar(eq(foto),eq(snapshot),eq(actor),eq(sid))).thenReturn(new byte[]{1,2,3});
        var almacenamiento=mock(ResultadosMaterialIa.class);
        var limite=new com.controllocal.service.planes.UsoAlmacenamientoB0.LimiteRecepcion(
                com.controllocal.service.planes.UsoAlmacenamientoB0.referenciaPrevision(actor.idOrganizacion(),foto.operacion()),
                12345,"VERSION_LIMITE_TEST","HUELLA_LIMITE_TEST","f".repeat(64));
        if(!caso.equals("LIMITE_AUSENTE"))when(almacenamiento.limiteRecepcionParaInvocar(foto.operacion(),actor)).thenReturn(limite);
        if(caso.equals("CAPACIDAD_DENEGADA"))when(almacenamiento.previsionar(foto,snapshot,actor,sid)).thenThrow(new CapacidadDeshabilitadaException());
        if(caso.equals("NAMESPACE_CAMBIADO"))when(almacenamiento.limiteRecepcionParaInvocar(foto.operacion(),actor)).thenThrow(new ConflictoException("Namespace TEST cambiado"));
        var autoridad=new AutoridadMaterialIa(repo,json,List.of(fuente),perfiles);
        servicio=new MaterialIaServiceImpl(repo,presupuesto,contexto,autoridad,perfiles,json,List.of(proveedor),List.of(controles),List.of(almacenamiento),gestor);
        if(caso.equals("CAPACIDAD_DENEGADA")){
            assertThrows(CapacidadDeshabilitadaException.class,()->servicio.solicitar(foto,actor,sid));
            verify(presupuesto,never()).admitir(any(),any(),any());verify(presupuesto,never()).iniciar(any(),any(),any());verify(proveedor,never()).ejecutar(any(),any());
        }else if(caso.equals("NAMESPACE_CAMBIADO")||caso.equals("LIMITE_AUSENTE")){
            if(caso.equals("NAMESPACE_CAMBIADO"))assertThrows(ConflictoException.class,()->servicio.solicitar(foto,actor,sid));
            else assertThrows(IllegalArgumentException.class,()->servicio.solicitar(foto,actor,sid));
            verify(presupuesto).iniciar(any(),eq(actor),eq(sid));verify(presupuesto).cancelarNoProcesada(any(),any());
            verify(proveedor,never()).ejecutar(any(),any());verify(presupuesto,never()).conciliar(any(),any());
            assertEquals("CANCELADA",estado.get().estado());verify(almacenamiento).resolverSinArchivo(actor.idOrganizacion(),foto.operacion(),"CANCELADA");
        }else{
            assertThrows(ProcesamientoIncierto.class,()->servicio.solicitar(foto,actor,sid));
            var orden=inOrder(almacenamiento,presupuesto,proveedor);
            orden.verify(almacenamiento).bloquearOriginal(foto.fotoOriginal(),actor);
            orden.verify(almacenamiento).previsionar(foto,snapshot,actor,sid);orden.verify(presupuesto).admitir(any(),eq(actor),eq(sid));
            orden.verify(almacenamiento).bloquearOriginal(foto.fotoOriginal(),actor);
            orden.verify(almacenamiento).exigirPrevisionAntesDeClaim(foto.operacion(),actor,sid);orden.verify(presupuesto).iniciar(any(),eq(actor),eq(sid));
            orden.verify(almacenamiento).limiteRecepcionParaInvocar(foto.operacion(),actor);
            var enviada=ArgumentCaptor.forClass(Peticion.class);orden.verify(proveedor).ejecutar(enviada.capture(),any());
            assertSame(limite,enviada.getValue().limiteRecepcion());
            String serializada=json.objetoCanonico(enviada.getValue());
            assertFalse(serializada.contains("limiteRecepcion"));assertFalse(serializada.contains("VERSION_LIMITE_TEST"));
            assertFalse(serializada.contains(limite.namespaceHash()));
        }
    }
    /** El gestor registra fronteras simuladas, no acredita locks/commit PostgreSQL. */
    @ParameterizedTest @ValueSource(strings={"NUEVA","INTENCION","RESERVADA"})
    void c1SeConservaEnLaMismaTransaccionAntesDeJobYAdmisionOClaim(String fase){
        record Paso(String nombre,int transaccion) { }
        var pasos=new ArrayList<Paso>();var pila=new ArrayDeque<TransactionStatus>();
        var ids=new IdentityHashMap<TransactionStatus,Integer>();var secuencia=new AtomicInteger();
        when(gestor.getTransaction(any())).thenAnswer(i->{
            TransactionDefinition definicion=i.getArgument(0);assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,definicion.getPropagationBehavior());
            var status=new SimpleTransactionStatus();ids.put(status,secuencia.incrementAndGet());pila.push(status);
            TransactionSynchronizationManager.setActualTransactionActive(true);return status;
        });
        org.mockito.stubbing.Answer<Void> terminar=i->{
            assertSame(pila.pop(),i.getArgument(0));TransactionSynchronizationManager.setActualTransactionActive(!pila.isEmpty());return null;
        };
        doAnswer(terminar).when(gestor).commit(any());doAnswer(terminar).when(gestor).rollback(any());
        java.util.function.Consumer<String> anotar=nombre->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertFalse(pila.isEmpty());
            pasos.add(new Paso(nombre,ids.get(pila.peek())));
        };
        doAnswer(i->{anotar.accept("C1");return null;}).when(contexto).iniciarConsumo(actor,sid);
        doAnswer(i->{anotar.accept("J");return null;}).when(repo).bloquear(anyLong());
        doAnswer(i->{anotar.accept("INSERTAR");estado.set(i.getArgument(0));return null;}).when(repo).insertar(any());
        when(presupuesto.admitir(any(),eq(actor),eq(sid))).thenAnswer(i->{anotar.accept("ADMITIR");return new PresupuestoIaService.Admision(solicitud.operacion(),"RESERVADA");});
        when(presupuesto.iniciar(any(),eq(actor),eq(sid))).thenAnswer(i->{anotar.accept("INICIAR");return true;});
        when(repo.obtener(actor.idOrganizacion(),solicitud.operacion())).thenAnswer(i->Optional.ofNullable(estado.get()));
        when(perfiles.de(solicitud.accion())).thenReturn(new PerfilesMaterialIa.Perfil(destino.perfil(),destino.versionPerfil()));
        estado.set(fase.equals("NUEVA")?null:fase(operacion(snapshot),fase,null));
        doAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertTrue(pila.isEmpty());throw new ProcesamientoIncierto();}).when(proveedor).ejecutar(any(),any());
        try{
            assertThrows(ProcesamientoIncierto.class,this::solicitar);assertTrue(pila.isEmpty());
            var porTransaccion=new LinkedHashMap<Integer,List<String>>();
            for(var paso:pasos)porTransaccion.computeIfAbsent(paso.transaccion(),id->new ArrayList<>()).add(paso.nombre());
            for(String operacion:List.of("INSERTAR","ADMITIR","INICIAR")){
                var relevantes=porTransaccion.values().stream().filter(p->p.contains(operacion)).toList();
                int esperadas=operacion.equals("INSERTAR")?(fase.equals("NUEVA")?1:0):operacion.equals("ADMITIR")?(fase.equals("RESERVADA")?0:1):1;
                assertEquals(esperadas,relevantes.size(),operacion);
                for(var observada:relevantes)assertEquals(List.of("C1","J",operacion),observada,"La guarda C1 no puede comprometerse en una TX anidada antes de J");
            }
            verify(proveedor).ejecutar(any(),any());verify(presupuesto).marcarIncierta(any());
        }finally{TransactionSynchronizationManager.clear();}
    }
}
