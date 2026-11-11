package com.controllocal.service.presupuesto;

import com.controllocal.domain.presupuesto.UnidadesDeConsumo.*;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.planes.DerechosDePlanB0;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T1 de hechos/etapas y precisión. El repositorio es un puerto simulado:
 * no acredita SQL, autorización real, rollback o concurrencia entre transacciones.
 * Importes, perfiles y proveedores SINTÉTICOS TEST; no calibración ni gasto.
 */
class PresupuestoIaEtapasTest {
    private final PresupuestoIaRepository repo=mock(PresupuestoIaRepository.class);
    private final Documentos json=new Documentos(new ObjectMapper().findAndRegisterModules());
    private final Actor actor=new Actor(17,7,9,Actor.AGENTE);
    private final ContextoSesion sid=mock(ContextoSesion.class);
    private final ReferenciaInterna ref=new ReferenciaInterna(17,UUID.randomUUID());
    private final UUID fondo=UUID.randomUUID();
    private final Instant relojApp=Instant.parse("2026-11-02T10:00:00Z"),programada=relojApp.plusSeconds(3600);
    private final AtomicReference<Instant> relojDb=new AtomicReference<>(programada);
    private final AtomicReference<Operacion> actual=new AtomicReference<>();
    private final UUID tecnica=UUID.randomUUID();
    private final AtomicReference<ProyeccionCosto> costo=new AtomicReference<>();
    private final Map<UUID,BigDecimal> nativos=new LinkedHashMap<>();
    private final Map<UUID,String> mediciones=new LinkedHashMap<>();
    private final Map<UUID,String> etapaDeMedicion=new HashMap<>();
    private final Map<String,UUID> intentos=new HashMap<>();
    private final Map<String,Etapa> etapas=new HashMap<>();
    private final Solicitud solicitud=new Solicitud(ref.operacion(),"PERFIL_ETAPAS_TEST","V1",
            new Recurso("PROPIEDAD","17","MEJORAR_FOTO","a".repeat(64)),"b".repeat(64));
    private PresupuestoIaService servicio;

    @BeforeEach void preparar(){
        var autorizador=mock(AutorizadorRecursoIa.class);when(autorizador.atiende(anyString(),anyString())).thenReturn(true);
        servicio=new PresupuestoIaService(repo,mock(ContextoPresupuestoB0.class),List.of(autorizador),json,
                mock(DerechosDePlanB0.class),true,Clock.fixed(relojApp,ZoneOffset.UTC));
        actual.set(operacion("EN_PROCESO",null));
        when(repo.bloquearOrganizacion(ref.organizacion())).thenReturn(new Organizacion("EMPRESARIAL",true));
        when(repo.operacion(ref.organizacion(),ref.operacion())).thenAnswer(i->Optional.of(actual.get()));
        when(repo.perfil(solicitud.perfil(),solicitud.versionPerfil())).thenReturn(Optional.of(new Perfil(solicitud.perfil(),solicitud.versionPerfil(),fondo,n("3"),
                json.lista(List.of(new Tarifa("unidades_test","u",n("0.1"),BigDecimal.ONE))),n("4"),n("1"),2,true)));
        when(repo.bloquearFondo(fondo)).thenReturn(Optional.of(new Fondo(fondo,n("10"),n("1"),BigDecimal.ZERO,relojApp.minusSeconds(1),programada.plusSeconds(3600),true)));
        when(repo.cicloVigente(eq(ref.organizacion()),any())).thenReturn(Optional.of(new Ciclo(UUID.randomUUID(),"TEAM",relojApp.minusSeconds(1),programada.plusSeconds(3600),false,n("75"),2,0,0)));
        when(repo.imputaciones(ref.organizacion(),ref.operacion())).thenReturn(List.of(new Imputacion(UUID.randomUUID(),n("4"),null,1)));
        when(repo.idsMediciones(ref.organizacion(),ref.operacion())).thenAnswer(i->Set.copyOf(mediciones.keySet()));
        when(repo.huellaMedicion(eq(ref.organizacion()),eq(ref.operacion()),any())).thenAnswer(i->Optional.ofNullable(mediciones.get(i.getArgument(2))));
        when(repo.medicion(anyLong(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),nullable(String.class))).thenAnswer(i->{
            UUID id=i.getArgument(2);String identidad=i.getArgument(12);
            if(intentos.containsKey(identidad)&&!id.equals(intentos.get(identidad)))return false;
            intentos.put(identidad,id);etapaDeMedicion.put(id,i.getArgument(14));nativos.putIfAbsent(id,i.getArgument(8));return mediciones.putIfAbsent(id,i.getArgument(11))==null;
        });
        doAnswer(i->{actual.set(operacion(i.getArgument(2),i.getArgument(5)));return null;})
                .when(repo).cerrar(anyLong(),any(),anyString(),any(),any(),anyString(),any());
        doAnswer(i->{actual.set(operacion("INCIERTA",i.getArgument(2)));return null;}).when(repo).incidenciaConResultado(anyLong(),any(),anyString());
        doAnswer(i->{actual.set(operacion("INCIERTA",actual.get().resultado()));return null;}).when(repo).incierta(anyLong(),any());
        when(repo.costosOperacion(tecnica)).thenAnswer(i->lineasCosto());
        when(repo.costosSinProyeccion(fondo)).thenAnswer(i->costo.get()==null?lineasCosto():List.of());
        when(repo.proyeccionCosto(tecnica)).thenAnswer(i->Optional.ofNullable(costo.get()));
        doAnswer(i->{List<ProyeccionCosto> ps=i.getArgument(0);costo.set(ps.getFirst());return null;}).when(repo).crearProyeccionesCosto(anyList());
        doAnswer(i->{assertEquals(costo.get().version(),((ProyeccionCosto)i.getArgument(0)).version());costo.set(i.getArgument(1));return null;}).when(repo).actualizarProyeccionCosto(any(),any());
        when(repo.pendienteCostoFondo(fondo)).thenAnswer(i->costo.get()==null?BigDecimal.ZERO:costo.get().pendiente());
        when(repo.ahoraEtapas()).thenAnswer(i->relojDb.get());
        when(repo.etapa(eq(ref.organizacion()),eq(ref.operacion()),anyString())).thenAnswer(i->Optional.ofNullable(etapas.get(i.getArgument(2))));
        doAnswer(i->{String clave=i.getArgument(3);etapas.putIfAbsent(clave,new Etapa(i.getArgument(2),clave,i.getArgument(4),i.getArgument(5),"PENDIENTE",null,null,null,relojDb.get()));return null;})
                .when(repo).programarEtapa(anyLong(),any(),anyString(),anyString(),anyString(),anyString());
        when(repo.reclamarEtapa(anyLong(),any(),anyString())).thenAnswer(i->{
            String clave=i.getArgument(2);var e=etapas.get(clave);
            if(e==null||!"PENDIENTE".equals(e.estado()))return false;
            etapas.put(clave,new Etapa(e.etapa(),e.clave(),e.proveedor(),e.peticion(),"RECLAMADA",null,relojDb.get(),null,e.programada()));return true;
        });
        doAnswer(i->{String clave=i.getArgument(2);var e=etapas.get(clave);
            etapas.put(clave,new Etapa(e.etapa(),e.clave(),e.proveedor(),e.peticion(),i.getArgument(3),i.getArgument(4),e.reclamada(),i.getArgument(6),e.programada()));return null;})
                .when(repo).resolverEtapa(anyLong(),any(),anyString(),anyString(),nullable(String.class),nullable(String.class),nullable(String.class));
        when(repo.etapasPendientes(ref.organizacion(),ref.operacion())).thenAnswer(i->etapas.values().stream().anyMatch(e->Set.of("PENDIENTE","RECLAMADA","INCIERTA").contains(e.estado())));
    }

    @Test void medicionParcialConservaHechoSinLiquidarNiLiberarProvision(){
        var generacion=medicion("GENERADOR_TEST","RESPUESTA_GENERACION");
        assertEquals("EN_PROCESO",servicio.registrarMediciones(ref,resultado(generacion)).estado());
        assertEquals(Set.of(generacion.id()),mediciones.keySet());assertEquals("EN_PROCESO",actual.get().estado());
        verify(repo,never()).liquidarFondo(any(),any(),any());verify(repo,never()).liquidarFuente(anyLong(),any(),any(),any());
        verify(repo,never()).cerrar(anyLong(),any(),any(),any(),any(),any(),any());
    }
    @Test void conjuntoSeLiquidaUnaVezConAmbasMedicionesYUnaSolaCuantizacion(){
        var g=medicion("GENERADOR_TEST","RESPUESTA_GENERACION");var q=medicion("JUEZ_TEST","RESPUESTA_QC");
        servicio.registrarMediciones(ref,resultado(g));programar();assertTrue(servicio.iniciarEtapa(solicitud,"QC",actor,sid));
        servicio.registrarResultadoEtapa(ref,"QC",resultado(g,q));
        assertEquals("EN_PROCESO",actual.get().estado());assertEquals("MEDIDA",etapa().estado());
        assertEquals("LIQUIDADA",servicio.conciliar(ref,resultado(g,q)).estado());
        assertEquals("LIQUIDADA",servicio.conciliar(ref,resultado(g,q)).estado());
        servicio.registrarResultadoEtapa(ref,"QC",resultado(g,q));
        assertEquals(Set.of(g.id(),q.id()),mediciones.keySet());
        assertNull(etapaDeMedicion.get(g.id()));
        assertEquals(PresupuestoIaService.claveDeEtapa(ref,"QC"),etapaDeMedicion.get(q.id()));
        verify(repo,times(1)).liquidarFuente(eq(ref.organizacion()),eq(ref.operacion()),any(),eq(n("0.066666666667")));
        verify(repo,times(1)).liquidarFondo(fondo,n("1"),n("0.010000000000"));
        verify(repo,times(1)).cerrar(eq(ref.organizacion()),eq(ref.operacion()),eq("LIQUIDADA"),eq(n("0.066666666667")),eq(n("0.010000000000")),anyString(),any());
    }
    @Test void qcPendienteImpideCerrarAunqueGeneracionYaEsteMedida(){
        var g=medicion("GENERADOR_TEST","G");servicio.registrarMediciones(ref,resultado(g));programar();
        assertThrows(ConflictoException.class,()->servicio.conciliar(ref,resultado(g)));
        assertEquals("PENDIENTE",etapa().estado());assertEquals(Set.of(g.id()),mediciones.keySet());
        verify(repo,never()).liquidarFondo(any(),any(),any());
    }
    @Test void costosConfirmadosNativosSeSumanAntesDeCuantizarElCierre(){
        var a=medicion("GENERADOR_TEST","G");var b=medicion("JUEZ_TEST","Q");
        var g=new MedicionFacturable(a.id(),a.proveedor(),a.modelo(),a.versionTarifa(),a.unidades(),a.tarifasReales(),n("0.0000000000006"),a.referenciaFuente());
        var q=new MedicionFacturable(b.id(),b.proveedor(),b.modelo(),b.versionTarifa(),b.unidades(),b.tarifasReales(),n("0.0000000000006"),b.referenciaFuente());
        servicio.registrarMediciones(ref,resultado(g));programar();assertTrue(servicio.iniciarEtapa(solicitud,"QC",actor,sid));
        servicio.registrarResultadoEtapa(ref,"QC",resultado(g,q));
        assertEquals("LIQUIDADA",servicio.conciliar(ref,resultado(g,q)).estado());
        verify(repo,times(1)).liquidarFondo(fondo,n("1"),n("0.000000000001"));
        verify(repo,times(1)).cerrar(eq(ref.organizacion()),eq(ref.operacion()),eq("LIQUIDADA"),any(),eq(n("0.000000000001")),anyString(),any());
        assertEquals(n("0.0000000000006"),g.costoConfirmadoUsd());assertEquals(n("0.0000000000006"),q.costoConfirmadoUsd());
    }
    @Test void qcReclamadaOInciertaNoSeConvierteEnNoInvocadaPorRetirarArchivo(){
        for(String estado:List.of("RECLAMADA","INCIERTA")){
            programar();var e=etapa();etapas.put(e.clave(),new Etapa(e.etapa(),e.clave(),e.proveedor(),e.peticion(),estado,null,programada,null,programada));
            assertFalse(servicio.acreditarEtapaNoReclamada(ref,"QC",prueba()));assertEquals(estado,etapa().estado());
            assertThrows(ConflictoException.class,()->servicio.conciliar(ref,resultado(medicion("GENERADOR_TEST","G"))));
        }
        verify(repo,never()).resolverEtapa(anyLong(),any(),any(),any(),any(),any(),any());
        verify(repo,never()).liquidarFondo(any(),any(),any());
    }
    @Test void noInvocadaEsTerminalInclusoConNuevaSesionDespuesDeLiquidar(){
        var g=medicion("GENERADOR_TEST","G");servicio.registrarMediciones(ref,resultado(g));programar();
        assertTrue(servicio.acreditarEtapaNoReclamada(ref,"QC",prueba()));
        assertEquals("NO_INVOCADA",etapa().estado());assertEquals("LIQUIDADA",servicio.conciliar(ref,resultado(g)).estado());
        assertFalse(servicio.iniciarEtapa(solicitud,"QC",actor,mock(ContextoSesion.class)));
        assertThrows(ConflictoException.class,()->servicio.marcarEtapaIncierta(ref,"QC"));
        assertFalse(servicio.acreditarEtapaNoReclamada(ref,"QC",prueba()));
        verify(repo,never()).reclamarEtapa(anyLong(),any(),any());verify(repo,times(1)).liquidarFondo(any(),any(),any());
    }
    @Test void evidenciaUsaProgramacionYHoraDeBaseNoElRelojAnteriorDeAplicacion(){
        programar();relojDb.set(programada.plusSeconds(30));
        var valida=pruebaEn(programada.plusSeconds(15));assertTrue(valida.comprobadaEn().isAfter(relojApp));
        servicio.acreditarEtapaNoInvocada(ref,"QC",valida);assertEquals("NO_INVOCADA",etapa().estado());
        verify(repo).ahoraEtapas();
    }
    @Test void pruebaAnteriorALaProgramacionOFuturaSegunBaseNoResuelveEtapa(){
        programar();relojDb.set(programada.plusSeconds(30));
        assertThrows(ReglaNegocioException.class,()->servicio.acreditarEtapaNoInvocada(ref,"QC",pruebaEn(programada.minusNanos(1))));
        assertThrows(ReglaNegocioException.class,()->servicio.acreditarEtapaNoInvocada(ref,"QC",pruebaEn(relojDb.get().plusNanos(1))));
        assertEquals("PENDIENTE",etapa().estado());verify(repo,never()).resolverEtapa(anyLong(),any(),any(),any(),any(),any(),any());
    }
    @Test void replayExactoDePruebaNoSobrescribeYUnContenidoDistintoFalla(){
        programar();var prueba=prueba();servicio.acreditarEtapaNoInvocada(ref,"QC",prueba);
        servicio.acreditarEtapaNoInvocada(ref,"QC",prueba);
        var otra=new ComprobacionNoProcesamiento(prueba.claveIntento(),prueba.proveedor(),prueba.referenciaIntento(),"OTRA PRUEBA",prueba.huellaEvidencia(),prueba.comprobadaEn());
        assertThrows(ConflictoException.class,()->servicio.acreditarEtapaNoInvocada(ref,"QC",otra));
        verify(repo,times(1)).resolverEtapa(anyLong(),any(),any(),eq("NO_INVOCADA"),isNull(),isNull(),anyString());
    }
    @Test void otraClaveOProveedorNoAcreditanQueLaEtapaNoFueInvocada(){
        programar();var c=prueba();
        for(var mala:List.of(new ComprobacionNoProcesamiento("a".repeat(64),c.proveedor(),c.referenciaIntento(),c.referenciaEvidencia(),c.huellaEvidencia(),c.comprobadaEn()),
                new ComprobacionNoProcesamiento(c.claveIntento(),"OTRO_JUEZ",c.referenciaIntento(),c.referenciaEvidencia(),c.huellaEvidencia(),c.comprobadaEn())))
            assertThrows(ReglaNegocioException.class,()->servicio.acreditarEtapaNoInvocada(ref,"QC",mala));
        assertEquals("PENDIENTE",etapa().estado());
    }
    @Test void resultadoDeQcRequiereMedicionPropiaDelProveedorYConservaReplay(){
        var g=medicion("GENERADOR_TEST","G");servicio.registrarMediciones(ref,resultado(g));programar();assertTrue(servicio.iniciarEtapa(solicitud,"QC",actor,sid));
        assertThrows(ConflictoException.class,()->servicio.registrarResultadoEtapa(ref,"QC",resultado(g)));
        assertThrows(ConflictoException.class,()->servicio.registrarResultadoEtapa(ref,"QC",resultado(g,medicion("OTRO_PROVEEDOR","Q"))));
        var q=medicion("JUEZ_TEST","Q");servicio.registrarResultadoEtapa(ref,"QC",resultado(g,q));
        servicio.registrarResultadoEtapa(ref,"QC",resultado(g,q));
        var alterada=new MedicionFacturable(q.id(),q.proveedor(),q.modelo(),q.versionTarifa(),q.unidades(),q.tarifasReales(),n("0.01"),q.referenciaFuente());
        assertThrows(ConflictoException.class,()->servicio.registrarResultadoEtapa(ref,"QC",resultado(g,alterada)));
        assertEquals("MEDIDA",etapa().estado());assertEquals(Set.of(g.id(),q.id()),mediciones.keySet());
    }
    @Test void cerrarNoPuedeOmitirUnaMedicionDurableAnterior(){
        var g=medicion("GENERADOR_TEST","G");servicio.registrarMediciones(ref,resultado(g));
        assertThrows(ConflictoException.class,()->servicio.conciliar(ref,resultado(medicion("JUEZ_TEST","Q"))));
        verify(repo,never()).liquidarFondo(any(),any(),any());
        // El rollback de la TX real pertenece a PG; este mock no lo simula.
    }
    @Test void medicionAlteradaODobleIdentidadDeUnMismoHechoNoSeLiquida(){
        var g=medicion("GENERADOR_TEST","G");servicio.registrarMediciones(ref,resultado(g));
        var alterada=new MedicionFacturable(g.id(),g.proveedor(),g.modelo(),g.versionTarifa(),g.unidades(),g.tarifasReales(),n("0.01"),g.referenciaFuente());
        assertThrows(ConflictoException.class,()->servicio.registrarMediciones(ref,resultado(alterada)));
        assertThrows(ConflictoException.class,()->servicio.conciliar(ref,resultado(g,medicion("GENERADOR_TEST","G"))));
        verify(repo,never()).liquidarFondo(any(),any(),any());
    }
    @Test void excesoRealConservaLaMedicionYLaIncertidumbreSinLiberarSaldo(){
        var g=medicion("GENERADOR_TEST","G");var exceso=new MedicionFacturable(g.id(),g.proveedor(),g.modelo(),g.versionTarifa(),g.unidades(),g.tarifasReales(),n("2"),g.referenciaFuente());
        assertEquals("INCIERTA",servicio.registrarMediciones(ref,resultado(exceso)).estado());
        assertEquals(Set.of(g.id()),mediciones.keySet());assertEquals("INCIERTA",actual.get().estado());
        verify(repo,never()).liquidarFondo(any(),any(),any());verify(repo,never()).liquidarFuente(anyLong(),any(),any(),any());
    }
    @Test void incertidumbreDeQcSeConservaSinPermitirOtraReclamacion(){
        programar();assertTrue(servicio.iniciarEtapa(solicitud,"QC",actor,sid));servicio.marcarEtapaIncierta(ref,"QC");
        servicio.marcarEtapaIncierta(ref,"QC");assertEquals("INCIERTA",etapa().estado());assertEquals("INCIERTA",actual.get().estado());
        assertFalse(servicio.iniciarEtapa(solicitud,"QC",actor,sid));assertFalse(servicio.acreditarEtapaNoReclamada(ref,"QC",prueba()));
        verify(repo,times(1)).reclamarEtapa(anyLong(),any(),any());verify(repo,never()).liquidarFondo(any(),any(),any());
    }
    @Test void claveDeEtapaEsEstableYSeparaOrganizacionOperacionYEtapa(){
        String clave=PresupuestoIaService.claveDeEtapa(ref,"QC");assertEquals(clave,PresupuestoIaService.claveDeEtapa(ref,"QC"));
        assertNotEquals(clave,PresupuestoIaService.claveDeEtapa(new ReferenciaInterna(18,ref.operacion()),"QC"));
        assertNotEquals(clave,PresupuestoIaService.claveDeEtapa(new ReferenciaInterna(17,UUID.randomUUID()),"QC"));
        assertNotEquals(clave,PresupuestoIaService.claveDeEtapa(ref,"GENERACION"));assertThrows(ReglaNegocioException.class,()->PresupuestoIaService.claveDeEtapa(ref," "));
    }

    private List<LineaCosto> lineasCosto(){
        var o=actual.get();if(nativos.isEmpty())return List.of(new LineaCosto(tecnica,o.estado(),o.maximoTecnico(),BigDecimal.ZERO,null,null,null,null));
        return nativos.entrySet().stream().map(e->new LineaCosto(tecnica,o.estado(),o.maximoTecnico(),BigDecimal.ZERO,e.getKey(),e.getValue(),null,null)).toList();
    }
    private void programar(){servicio.programarEtapa(ref,"QC","JUEZ_TEST","e".repeat(64));}
    private Etapa etapa(){return etapas.get(PresupuestoIaService.claveDeEtapa(ref,"QC"));}
    private ComprobacionNoProcesamiento prueba(){return pruebaEn(relojDb.get());}
    private ComprobacionNoProcesamiento pruebaEn(Instant cuando){return new ComprobacionNoProcesamiento(PresupuestoIaService.claveDeEtapa(ref,"QC"),"JUEZ_TEST","ETAPA_TEST","PRUEBA_CORE_TEST","f".repeat(64),cuando);}
    private MedicionFacturable medicion(String proveedor,String referencia){return new MedicionFacturable(UUID.randomUUID(),proveedor,"MODELO_SINTETICO","V1",
            List.of(new Medicion("unidades_test","u",BigDecimal.ONE,null)),List.of(new Tarifa("unidades_test","u",n("0.005"),BigDecimal.ONE)),null,referencia);}
    private ResultadoFacturable resultado(MedicionFacturable... valores){return new ResultadoFacturable(List.of(valores));}
    private Operacion operacion(String estado,String resultado){return new Operacion(ref.organizacion(),ref.operacion(),actor.idPersona(),actor.idRolOperativo(),solicitud.perfil(),solicitud.versionPerfil(),
            solicitud.recurso().tipo(),solicitud.recurso().referencia(),solicitud.recurso().finalidad(),solicitud.recurso().huellaOrigen(),
            Idempotencia.huellaExacta(json.objetoCanonico(Map.of("solicitud",solicitud,"actor",actor))),estado,n("4"),n("1"),fondo,relojApp.minusSeconds(1),resultado,tecnica,null,null,null,null);}
    private static BigDecimal n(String s){return new BigDecimal(s);}
}
