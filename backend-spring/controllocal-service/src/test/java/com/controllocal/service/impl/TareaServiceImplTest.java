package com.controllocal.service.impl;

import com.controllocal.domain.comercial.SolicitudAlquiler;
import com.controllocal.domain.comercial.Tarea;
import com.controllocal.domain.comercial.HechoAsunto;
import com.controllocal.domain.comercial.Visita;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.domain.persona.Persona;
import com.controllocal.domain.persona.PersonaRol;
import com.controllocal.domain.persona.enums.TipoRol;
import com.controllocal.persistence.query.CandidatoTarea;
import com.controllocal.persistence.repositorio.CaptacionRepository;
import com.controllocal.persistence.repositorio.ContratoAlquilerRepository;
import com.controllocal.persistence.repositorio.DetalleAgenteRepository;
import com.controllocal.persistence.repositorio.ReporteAvanceRepository;
import com.controllocal.persistence.repositorio.SolicitudAlquilerRepository;
import com.controllocal.persistence.repositorio.TareaRepository;
import com.controllocal.persistence.repositorio.VisitaRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ProspeccionService.RevisionSeguimiento;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.actividad.SeguimientoDeProspeccion;
import com.controllocal.service.actividad.SeguimientoDeProspeccion.Necesidad;
import com.controllocal.service.TareaService.FichaTarea;
import com.controllocal.service.materialia.ResultadosMaterialIa;
import com.controllocal.service.materialia.ResultadosMaterialIa.*;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.soporte.InterpreteDeLaBandeja;
import com.controllocal.service.soporte.FuentesAsuntos;
import com.controllocal.service.soporte.AutoridadAsuntos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import java.util.Comparator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;

/**
 * Bandeja personal: conserva las familias no R27, ámbito, reconciliación y despacho.
 * R27 consume necesidades/marcas canónicas; no calcula cadencia ni usa fechas antiguas.
 * La cancelación conserva su causa material, y otra necesidad puede tener otra tarea vinculada.
 * El almacenamiento Mockito sólo monta resultados del INSERT; no acredita SQL o concurrencia.
 */
class TareaServiceImplTest {

    private static final long ORG = 1L;
    private static final long ROL_AGENTE = 30L;

    private final TareaRepository tareas = mock(TareaRepository.class);
    private final ContextosDeActividad contexto = mock(ContextosDeActividad.class);
    private final SeguimientoDeProspeccion seguimiento = mock(SeguimientoDeProspeccion.class);
    private final SolicitudAlquilerRepository solicitudes = mock(SolicitudAlquilerRepository.class);
    private final VisitaRepository visitas = mock(VisitaRepository.class);
    private final CaptacionRepository captaciones = mock(CaptacionRepository.class);
    private final ContratoAlquilerRepository contratos = mock(ContratoAlquilerRepository.class);
    private final ReporteAvanceRepository reportes = mock(ReporteAvanceRepository.class);
    private final DetalleAgenteRepository agentes = mock(DetalleAgenteRepository.class);
    private final InterpreteDeLaBandeja interprete = mock(InterpreteDeLaBandeja.class);
    private final ResultadosMaterialIa material = mock(ResultadosMaterialIa.class);
    private final FuentesAsuntos fuentes=mock(FuentesAsuntos.class);
    private final AutoridadAsuntos autoridad=mock(AutoridadAsuntos.class);

    private final TareaServiceImpl service = new TareaServiceImpl(tareas, contexto, solicitudes,
            visitas, captaciones, contratos, reportes, agentes, interprete,seguimiento,material,fuentes,autoridad);
    private final LocalDate hoy=LocalDate.of(2026,10,30);

    private final Actor agente = new Actor(ORG, 3L, ROL_AGENTE, "AGENTE");
    private final Actor broker = new Actor(ORG, 2L, 20L, "BROKER");
    private final Actor admin = new Actor(ORG, 1L, 10L, "TENANT_ADMIN");

    /** Lo que el reconcile ha ido guardando; se relee como si fuera la tabla. */
    private final List<Tarea> tabla = new ArrayList<>();

    @BeforeEach
    void sinDisparadores() {
        MontajeAsuntosC7.tareas(tareas,tabla,detalleAgente(),agente,solicitudes,fuentes,autoridad);
        // Todos los disparadores en vacio: cada test enciende solo el suyo.
        when(contexto.hoy()).thenReturn(hoy);
        when(contexto.ahora()).thenReturn(hoy.atTime(12,0).atZone(java.time.ZoneId.of("America/Lima")).toInstant());
        doThrow(new AccesoNoAutorizadoException()).when(contexto).exigirAgente(broker);
        doThrow(new AccesoNoAutorizadoException()).when(contexto).exigirAgente(admin);
        when(seguimiento.necesidadesActuales(agente)).thenReturn(List.of());
        when(seguimiento.marcasActuales(agente)).thenReturn(Set.of());
        when(material.avisosDeCaducidad(agente)).thenReturn(List.of());
        when(solicitudes.porEstadoDelAgente(anyLong(), anyLong(), anyString())).thenReturn(List.of());
        when(contratos.conComisionListaParaCobro(anyLong(), anyLong())).thenReturn(List.of());
        when(visitas.queExigenAccion(anyLong(), anyLong())).thenReturn(List.of());
        when(captaciones.activasDelAgente(anyLong(), anyLong())).thenReturn(List.of());
        when(captaciones.activasConLocalDisponible(anyLong(), anyLong())).thenReturn(List.of());
        when(agentes.getReferenceById(ROL_AGENTE)).thenReturn(detalleAgente());

        when(tareas.recontactosDeProspecciones(eq(ORG),anyCollection())).thenAnswer(inv->{
            java.util.Collection<Long> ids=inv.getArgument(1);
            return tabla.stream().filter(t->Tarea.RECONTACTO.equals(t.getTipo())&&ids.contains(t.getEntidadId())).toList();
        });
        when(tareas.antecedenteDeRecontacto(eq(ORG),anyLong())).thenAnswer(inv->{
            Long origen=inv.getArgument(1);
            return tabla.stream().filter(t->Objects.equals(origen,t.getIdSeguimientoProspeccion()))
                    .map(Tarea::getId).max(Comparator.naturalOrder());
        });
        // Almacenamiento del INSERT nativo: no calcula elegibilidad/cadencia ni demuestra la unicidad SQL.
        when(tareas.crearRecontacto(eq(ORG),anyLong(),eq(ROL_AGENTE),anyString(),anyString(),anyString(),any(),anyLong()))
                .thenAnswer(inv->{
                    Long origen=inv.getArgument(7);
                    if(tabla.stream().anyMatch(t->t.estaAbierta()&&Objects.equals(origen,t.getIdSeguimientoProspeccion())))return 0;
                    Tarea t=tareaExistente("PROSPECCION",inv.getArgument(1),Tarea.PENDIENTE);
                    t.setId((long)tabla.size()+100);t.setTipo(Tarea.RECONTACTO);t.setDescripcion(inv.getArgument(3));
                    t.setMarcaCausa(inv.getArgument(5));t.setIdAntecedente(inv.getArgument(6));t.setIdSeguimientoProspeccion(origen);
                    t.setPrioridad(inv.getArgument(4));tabla.add(t);return 1;
                });
        when(tareas.save(any(Tarea.class))).thenAnswer(inv -> {
            Tarea guardada = inv.getArgument(0);
            if (guardada.getId() == null) {
                guardada.setId((long) (tabla.size() + 100));
                tabla.add(guardada);
            }
            return guardada;
        });
    }

    private final UUID originalC6=UUID.randomUUID(),varianteA=UUID.randomUUID(),varianteB=UUID.randomUUID(),varianteC=UUID.randomUUID();
    private GrupoCaducidad grupoC6(String letra,long revision,UUID... jobs){
        var fecha=Instant.parse("2026-11-08T15:00:00Z");
        var versiones=new ArrayList<Version>();
        for(int i=0;i<jobs.length;i++){
            var vence=fecha.plusSeconds(i*3600L);versiones.add(new Version(jobs[i],originalC6,jobs[i],revision,
                    vence.minusSeconds(604800),vence.minusSeconds(604800),vence,false,false,true,"DISPONIBLE"));
        }
        return new GrupoCaducidad(originalC6,revision,letra.repeat(64),fecha.minusSeconds(86400),fecha,List.copyOf(versiones));
    }
    private void montarC6(GrupoCaducidad grupo){
        when(contexto.hoy()).thenReturn(LocalDate.of(2026,11,7));when(contexto.ahora()).thenReturn(Instant.parse("2026-11-07T17:00:00Z"));
        when(material.avisosDeCaducidad(agente)).thenReturn(List.of(grupo));
        when(material.registrarCausaCaducidad(eq(ORG),any())).thenReturn(true);
        when(material.estadoCausaCaducidad(eq(ORG),eq(originalC6),anyString())).thenReturn(EstadoCausaCaducidad.VIGENTE);
        var foto=mock(TareaRepository.FotoParaAvisoC6.class);when(foto.getIdFoto()).thenReturn(7L);when(foto.getOriginal()).thenReturn(originalC6);
        when(tareas.fotosParaAvisosC6(eq(ORG),anyCollection())).thenReturn(List.of(foto));
        when(tareas.crearAvisoC6(eq(ORG),eq(7L),eq(ROL_AGENTE),anyString(),any(),anyString(),any(),eq(originalC6),anyLong()))
                .thenAnswer(inv->{
                    if(tabla.stream().anyMatch(t->t.esAvisoMaterialIaC6()&&t.estaAbierta()))return 0;
                    Tarea t=tareaExistente(Tarea.ENTIDAD_FOTO_ORIGINAL_C6,7L,Tarea.PENDIENTE);t.setId((long)tabla.size()+100);
                    t.setTipo(Tarea.SEGUIMIENTO);t.setOriginalMaterialIaC6(originalC6);t.setRevisionMaterialIaC6(inv.getArgument(8));
                    t.setEstadoCausaMaterialIaC6("VIGENTE");t.setMarcaCausa(inv.getArgument(5));t.setIdAntecedente(inv.getArgument(6));
                    t.setDescripcion(inv.getArgument(3));t.setFechaProgramada(((Instant)inv.getArgument(4)).atOffset(ZoneOffset.UTC));t.setPrioridad(null);
                    tabla.add(t);return 1;
                });
        when(tareas.avisoAbiertoC6(ORG,ROL_AGENTE,originalC6)).thenAnswer(inv->tabla.stream()
                .filter(t->t.esAvisoMaterialIaC6()&&t.estaAbierta()).map(Tarea::getId).findFirst());
        when(tareas.avisoPropioC6(eq(ORG),anyLong(),eq(ROL_AGENTE))).thenAnswer(inv->tabla.stream().anyMatch(t->t.esAvisoMaterialIaC6()&&Objects.equals(t.getId(),inv.getArgument(1))));
        when(tareas.buscarFicha(eq(ORG),anyLong())).thenAnswer(inv->tabla.stream().filter(t->Objects.equals(t.getId(),inv.getArgument(1))).findFirst());
    }
    @Test void dosTemporalesProducenUnaTarjetaSinImportanciaInventada(){
        var g=grupoC6("a",1,varianteA,varianteB);montarC6(g);
        var ficha=service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).getFirst();assertEquals(1,tabla.size());assertNull(ficha.prioridad());
        assertEquals(0,ficha.diasSinAccion().intValue());assertEquals(g.avisoEn(),ficha.fechaProgramada().toInstant());
        assertEquals(LocalDate.of(2026,11,8),ficha.fechaVencimiento());assertTrue(ficha.descripcion().contains("10:00 (Lima)"));
        assertTrue(ficha.descripcion().contains("2 resultados temporales"));assertTrue(ficha.rutaResolver().contains(originalC6.toString()));
        assertThrows(IllegalStateException.class,tabla.getFirst()::completar);
    }
    @Test void avisoC6DisponibleAyerNoInventaUnDiaDeAtrasoComercial(){
        var grupo=grupoC6("a",1,varianteA);montarC6(grupo);
        when(contexto.hoy()).thenReturn(LocalDate.of(2026,11,8));when(contexto.ahora()).thenReturn(Instant.parse("2026-11-08T14:00:00Z"));
        var ficha=service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).getFirst();assertEquals(0,ficha.diasSinAccion().intValue());
        assertEquals(grupo.avisoEn(),ficha.fechaProgramada().toInstant());assertEquals(LocalDate.of(2026,11,8),ficha.fechaVencimiento());
        assertNull(ficha.prioridad());
    }
    @Test void cambiarComposicionDelGrupoActualizaLaMismaTarjetaYConservaDeteccion(){
        var anterior=grupoC6("a",1,varianteA,varianteB);montarC6(anterior);var primera=service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).getFirst();
        var actual=grupoC6("b",2,varianteA);when(material.avisosDeCaducidad(agente)).thenReturn(List.of(actual));
        var segunda=service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).getFirst();assertEquals(1,tabla.size());assertEquals(primera.id(),segunda.id());
        assertEquals(primera.fechaProgramada(),segunda.fechaProgramada());assertEquals(actual.huellaCausa(),tabla.getFirst().getMarcaCausa());
        assertTrue(segunda.descripcion().contains("el resultado temporal"));verify(tareas).observarAvisoC6(ORG,primera.id(),"ACTUALIZACION");
    }
    @Test void perderLecturaOcultaSinCompletarYRecuperarConservaLaMismaOcurrencia(){
        var g=grupoC6("a",1,varianteA);montarC6(g);long id=service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).getFirst().id();
        when(material.avisosDeCaducidad(agente)).thenReturn(List.of());assertTrue(service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).isEmpty());
        assertEquals(Tarea.PENDIENTE,tabla.getFirst().getEstado());assertEquals("VIGENTE",tabla.getFirst().getEstadoCausaMaterialIaC6());assertNull(tabla.getFirst().getFechaCompletada());
        when(material.avisosDeCaducidad(agente)).thenReturn(List.of(g));assertEquals(id,service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).getFirst().id().longValue());assertEquals(1,tabla.size());
    }
    @Test void soloElHechoDeFuenteFinalizaLaCausaSinActuacionHumanaPositiva(){
        var g=grupoC6("a",1,varianteA);montarC6(g);service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);
        when(material.avisosDeCaducidad(agente)).thenReturn(List.of());
        when(material.estadoCausaCaducidad(ORG,originalC6,g.huellaCausa())).thenReturn(EstadoCausaCaducidad.FINALIZADA);
        assertTrue(service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).isEmpty());var fila=tabla.getFirst();assertEquals("FINALIZADA",fila.getEstadoCausaMaterialIaC6());
        assertEquals(Tarea.PENDIENTE,fila.getEstado());assertNull(fila.getFechaCompletada());assertTrue(!fila.estaAbierta());
        verify(tareas).observarAvisoC6(ORG,fila.getId(),"CAUSA_FINALIZADA");
    }
    @Test void cancelacionNoReaparecePorRecortePeroNuevoEpisodioCreaAntecedenteVinculado(){
        var g=grupoC6("a",1,varianteA,varianteB);montarC6(g);long id=service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).getFirst().id();service.cancelar(id,agente);
        var recorte=grupoC6("b",2,varianteA);when(material.avisosDeCaducidad(agente)).thenReturn(List.of(recorte));
        when(material.estadoCausaCaducidad(ORG,originalC6,g.huellaCausa(),recorte.huellaCausa())).thenReturn(EstadoCausaCaducidad.VIGENTE);
        assertTrue(service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).isEmpty());assertEquals(1,tabla.size());
        var nuevo=grupoC6("c",3,varianteA,varianteC);when(material.avisosDeCaducidad(agente)).thenReturn(List.of(nuevo));
        when(material.estadoCausaCaducidad(ORG,originalC6,g.huellaCausa(),nuevo.huellaCausa())).thenReturn(EstadoCausaCaducidad.SUSTITUIDA);
        var ficha=service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).getFirst();assertNotEquals(id,ficha.id().longValue());assertEquals(2,tabla.size());
        assertEquals(id,tabla.getLast().getIdAntecedente().longValue());assertEquals(Tarea.CANCELADA,tabla.getFirst().getEstado());
        assertEquals("SUSTITUIDA",tabla.getFirst().getEstadoCausaMaterialIaC6());
    }
    @Test void causaNoEvaluableNoSeDisfrazaComoTerminadaNiDuplicaTarjeta(){
        var g=grupoC6("a",1,varianteA);montarC6(g);service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);
        when(material.estadoCausaCaducidad(ORG,originalC6,g.huellaCausa())).thenReturn(EstadoCausaCaducidad.NO_EVALUABLE);
        assertTrue(service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).isEmpty());assertEquals(1,tabla.size());assertEquals("VIGENTE",tabla.getFirst().getEstadoCausaMaterialIaC6());
    }
    @Test void snapshotQueYaCambioNoMaterializaUnaCausaFalsa(){
        montarC6(grupoC6("a",1,varianteA));when(material.registrarCausaCaducidad(eq(ORG),any())).thenReturn(false);
        assertThrows(com.controllocal.service.excepcion.ConflictoException.class,()->service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente));assertTrue(tabla.isEmpty());
    }

    // ------------------------------------------------------------------
    // Alcance: el unico recurso sin ADMIN
    // ------------------------------------------------------------------

    @Test
    void laBandejaEsEstrictamentePersonalDelAGENTENiBrokerNiAdminEntran() {
        assertThrows(AccesoNoAutorizadoException.class, () -> service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),broker));
        assertThrows(AccesoNoAutorizadoException.class, () -> service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),admin));
    }

    // ------------------------------------------------------------------
    // Derivacion y reconcile
    // ------------------------------------------------------------------

    @Test
    void unDisparadorVigenteCreaSuTareaYLaDevuelveEnLaBandeja() {
        activarNecesidades(List.of(necesidad(11,hoy.minusDays(9))));

        List<FichaTarea> bandeja = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertEquals(1, bandeja.size());
        FichaTarea tarea = bandeja.get(0);
        assertEquals(Tarea.RECONTACTO, tarea.tipo());
        assertEquals("PROSPECCION", tarea.entidadTipo());
        assertEquals("#11", tarea.entidadCodigo());
        assertEquals(Tarea.ALTA, tarea.prioridad());
        assertEquals("prospeccion-detail/11", tarea.rutaResolver());
        assertEquals("Revisa la próxima actuación con el propietario o representante.", tarea.descripcion());
    }

    @Test
    void unSegundoPaseNoDuplicaLaTareaDeLaMismaEntidad() {
        activarNecesidades(List.of(necesidad(11,hoy.minusDays(9))));

        service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);
        service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertEquals(1, tabla.size());
    }

    @Test
    void filaAnteriorSinFuenteNoSuprimeNiSeTransformaEnLaNecesidadAcreditada() {
        var anterior=tareaExistente("SOLICITUD_ALQUILER", 5L, Tarea.PENDIENTE);tabla.add(anterior);
        when(solicitudes.porEstadoDelAgente(ORG, ROL_AGENTE, SolicitudAlquiler.OBSERVADA))
                .thenReturn(List.of(candidato(5L, "SOL-0001", hoy.minusDays(2), null)));

        List<FichaTarea> bandeja = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertEquals(1, bandeja.size());
        assertEquals(Tarea.SUBIR_DOCUMENTOS, bandeja.get(0).tipo());
        assertEquals(2, tabla.size());assertEquals(Tarea.PENDIENTE,anterior.getEstado());assertNull(anterior.getHuellaCausaC7());
    }

    @Test
    void fuenteQueCambioEntreConsultasNoProduceDosEstadosIncompatibles() {
        CandidatoTarea solicitud = candidato(5L, "SOL-0001", hoy.minusDays(2), null);
        when(solicitudes.porEstadoDelAgente(ORG, ROL_AGENTE, SolicitudAlquiler.APROBADA))
                .thenReturn(List.of(solicitud));
        when(solicitudes.porEstadoDelAgente(ORG, ROL_AGENTE, SolicitudAlquiler.OBSERVADA))
                .thenReturn(List.of(solicitud));

        assertThrows(com.controllocal.service.excepcion.ConflictoException.class,()->service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente));
        assertTrue(tabla.isEmpty());
    }

    @Test
    void cuandoElDisparadorDesapareceLaTareaSeDaPorHECHA() {
        activarNecesidades(List.of(necesidad(11,hoy.minusDays(9))));
        service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        // El agente recontacto: el disparador ya no aplica.
        activarNecesidades(List.of());
        List<FichaTarea> bandeja = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertTrue(bandeja.isEmpty());
        assertEquals(Tarea.COMPLETADA, tabla.get(0).getEstado());
    }

    @Test
    void unaTareaSobreUnaEntidadFUERADeEntidadesAutoNoSeCierraSolaNUNCA() {
        // Trampa 2: OPORTUNIDAD no esta en ENTIDADES_AUTO. Aunque no haya
        // disparador que la justifique, sigue abierta en la bandeja.
        tabla.add(tareaExistente("OPORTUNIDAD", 7L, Tarea.PENDIENTE));

        List<FichaTarea> bandeja = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertEquals(1, bandeja.size());
        assertEquals(Tarea.PENDIENTE, tabla.get(0).getEstado());
    }

    @Test
    void cancelarNoReviveLaMismaCausaMaterial() {
        var cancelada=tareaExistente("PROSPECCION",11,Tarea.CANCELADA);
        cancelada.setTipo(Tarea.RECONTACTO);cancelada.setMarcaCausa(marca(11));cancelada.setIdSeguimientoProspeccion(11L);
        tabla.add(cancelada);
        activarNecesidades(List.of(necesidad(11,hoy.minusDays(9))));

        List<FichaTarea> bandeja = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertTrue(bandeja.isEmpty());
        assertEquals(1, tabla.size());
        verify(tareas, never()).save(any(Tarea.class));
    }

    // ------------------------------------------------------------------
    // Enriquecimiento
    // ------------------------------------------------------------------

    @Test
    void diasSinAccionSeCuentaDesdeElPlazoDeLaENTIDADNoDesdeLaTarea() {
        // Trampa 4: es el error mas facil al portar. La tarea nace hoy; si el
        // contador saliera de ella daria 0 en vez de 9.
        activarNecesidades(List.of(necesidad(11,hoy.minusDays(9))));

        assertEquals(9, service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).get(0).diasSinAccion());
    }

    @Test
    void unPlazoEnElFuturoNoDaDiasNegativos() {
        when(visitas.queExigenAccion(anyLong(), anyLong()))
                .thenReturn(List.of(candidato(21L, "", hoy.plusDays(2), Visita.PROGRAMADA)));

        assertEquals(0, service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).get(0).diasSinAccion());
    }

    @Test
    void unaVisitaProximaEsMEDIAYUnaVencidaOCaidaEsALTA() {
        when(visitas.queExigenAccion(anyLong(), anyLong())).thenReturn(List.of(
                candidato(21L, "", hoy.plusDays(2), Visita.PROGRAMADA),
                candidato(22L, "", hoy.minusDays(1), Visita.PROGRAMADA),
                candidato(23L, "", hoy.plusDays(1), Visita.NO_REALIZADA)));

        List<FichaTarea> bandeja = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertEquals(Tarea.ALTA, buscar(bandeja, 22L).prioridad());
        assertEquals(Tarea.ALTA, buscar(bandeja, 23L).prioridad());
        assertEquals("Visita no realizada: reprograma o descarta.", buscar(bandeja, 23L).descripcion());
        assertEquals(Tarea.MEDIA, buscar(bandeja, 21L).prioridad());
        assertEquals("visitas?focus=21", buscar(bandeja, 21L).rutaResolver());
    }

    @Test
    void laSolicitudEnrutaPorCODIGOYLaProspeccionPorID() {
        when(solicitudes.porEstadoDelAgente(ORG, ROL_AGENTE, SolicitudAlquiler.APROBADA))
                .thenReturn(List.of(candidato(5L, "SOL-260715103000", null, null)));

        assertEquals("solicitud-detail/SOL-260715103000",
                service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).get(0).rutaResolver());
    }

    @Test
    void sinCodigoLaSolicitudCaeAlIdYLaCaptacionASuLista() {
        when(solicitudes.porEstadoDelAgente(ORG, ROL_AGENTE, SolicitudAlquiler.APROBADA))
                .thenReturn(List.of(candidato(5L, "", null, null)));
        when(captaciones.activasDelAgente(anyLong(), anyLong()))
                .thenReturn(List.of(candidato(9L, "", hoy.minusDays(30), null)));
        when(reportes.ultimasComunicaciones(anyLong(), anyCollection())).thenReturn(List.of());

        List<FichaTarea> bandeja = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertEquals("solicitud-detail/5", buscar(bandeja, 5L).rutaResolver());
        assertEquals("captaciones", buscar(bandeja, 9L).rutaResolver());
    }

    // ------------------------------------------------------------------
    // Orden y corte
    // ------------------------------------------------------------------

    @Test
    void ordenaPorFechaDentroDeR1YLuegoLasFuturasR2() {
        when(visitas.queExigenAccion(anyLong(), anyLong())).thenReturn(List.of(
                candidato(21L, "", hoy.plusDays(2), Visita.PROGRAMADA)));   // MEDIA
        activarNecesidades(List.of(necesidad(11,hoy.minusDays(3)),necesidad(12,hoy.minusDays(20))));

        List<FichaTarea> bandeja = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertEquals(12L, bandeja.get(0).entidadId());
        assertEquals(11L, bandeja.get(1).entidadId());
        assertEquals(21L, bandeja.get(2).entidadId());
    }

    @Test
    void laBandejaDevuelveTODASlasTareasAbiertas() {
        // Descongelado 2026-08-08 (era D-F7-2). La v1 cortaba en 10 y descartaba
        // el resto en silencio: sin total ni marca de truncado, el agente veia
        // la misma bandeja con 10 tareas que con 40, y las 30 que faltaban no
        // aparecian en ninguna parte. Las tareas se creaban igual, asi que lo
        // que se perdia no era trabajo: era saber que existia.
        List<Necesidad> muchas = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            muchas.add(necesidad(i,hoy.minusDays(8+i)));
        }
        activarNecesidades(muchas);

        List<FichaTarea> bandeja = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertEquals(40, bandeja.size(), "ninguna se descarta");
        assertEquals(40, tabla.size(), "y siguen siendo las mismas que se crearon");
        // El orden ya NO lo decide "la mas rezagada primero".
        //
        // Catálogo16/D5-37: R1 conserva orden por fecha real sin topes de antigüedad.
        assertEquals(40L,bandeja.getFirst().entidadId(),"la fecha más antigua precede, sin ocultar las demás");
        assertEquals(bandeja.stream().map(FichaTarea::entidadId).toList(),
                service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).stream().map(FichaTarea::entidadId).toList(),
                "y dos llamadas seguidas con los mismos datos devuelven el mismo orden");
    }

    // ------------------------------------------------------------------
    // Cancelar y efecto 7 de la cascada de F4
    // ------------------------------------------------------------------

    @Test
    void cancelarUnaTareaAjenaOInexistenteRompeConElMensajeDelCable() {
        assertEquals("Tarea no encontrada.",
                assertThrows(ReglaNegocioException.class, () -> service.cancelar(77L, agente))
                        .getMessage());

        Tarea deOtro = tareaExistente("PROSPECCION", 11L, Tarea.PENDIENTE);
        ReflectionTestUtils.setField(deOtro.getAgente(), "id", 99L);
        tabla.add(deOtro);
        assertEquals("Tarea no encontrada.",
                assertThrows(ReglaNegocioException.class, () -> service.cancelar(77L, agente))
                        .getMessage());
        verify(tareas,times(2)).cabeceraPersonal(ORG,ROL_AGENTE,77L);
        verify(tareas,never()).buscarFicha(anyLong(),anyLong());
    }

    @Test
    void cancelarDejaLaTareaEnCANCELADA() {
        Tarea propia = tareaExistente("PROSPECCION", 11L, Tarea.PENDIENTE);
        tabla.add(propia);
        when(tareas.buscarFicha(ORG, 77L)).thenReturn(Optional.of(propia));

        service.cancelar(77L, agente);

        assertEquals(Tarea.CANCELADA, propia.getEstado());
        var orden=inOrder(tareas);orden.verify(tareas).cabeceraPersonal(ORG,ROL_AGENTE,77L);
        orden.verify(tareas).buscarFicha(ORG,77L);orden.verify(tareas).save(propia);
    }

    @Test
    void resolverPorCierreUsaSoloLosRecursosDelHechoYRevalidaAntesDeTerminar() {
        // La autoridad y cobertura reales pertenecen a la fuente canónica;
        // este puerto TEST declara el hecho, no acredita permisos ni UPDATE SQL.
        var recursos=Map.of("CAPTACION",Set.of(9L),"OPORTUNIDAD",Set.of(8L),"SOLICITUD_ALQUILER",Set.of(5L));
        var cierre=new FuentesAsuntos.CierreConfirmado(new HechoAsunto("REVISION_HITO_CONTRACTUAL",700L,null),9L,recursos);
        when(fuentes.cierreConfirmado(700L,broker)).thenReturn(cierre);

        service.resolverPorCierreConfirmado(700L,broker);

        var orden=inOrder(fuentes,tareas);orden.verify(fuentes).cierreConfirmado(700L,broker);
        for(var recurso:recursos.entrySet())verify(tareas).resolverPorCierre(ORG,recurso.getKey(),recurso.getValue());
        orden.verify(tareas,times(recursos.size())).resolverPorCierre(eq(ORG),anyString(),anyCollection());
        orden.verify(fuentes).cierreConfirmado(700L,broker);
        verify(tareas,never()).abiertasDeEntidad(anyLong(),anyString(),anyLong());
        verify(tareas,never()).save(any());
    }

    @Test
    void cierreSinAutoridadNoActualizaNiHidrataTareas() {
        when(fuentes.cierreConfirmado(700L,broker)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->service.resolverPorCierreConfirmado(700L,broker));
        verify(tareas,never()).resolverPorCierre(anyLong(),anyString(),anyCollection());
        verify(tareas,never()).abiertasDeEntidad(anyLong(),anyString(),anyLong());
        verify(tareas,never()).save(any());
    }

    @Test
    void cambioDeHechoOAutoridadAlCerrarNoSeEntregaComoExito() {
        var primero=new FuentesAsuntos.CierreConfirmado(new HechoAsunto("REVISION_HITO_CONTRACTUAL",700L,null),9L,Map.of("CAPTACION",Set.of(9L)));
        var cambiado=new FuentesAsuntos.CierreConfirmado(new HechoAsunto("REVISION_HITO_CONTRACTUAL",701L,null),9L,Map.of("CAPTACION",Set.of(9L)));
        when(fuentes.cierreConfirmado(700L,broker)).thenReturn(primero,cambiado);
        assertThrows(ConflictoException.class,()->service.resolverPorCierreConfirmado(700L,broker));
        verify(tareas).resolverPorCierre(ORG,"CAPTACION",Set.of(9L));
        verify(fuentes,times(2)).cierreConfirmado(700L,broker);
        // El rollback de la TX pertenece a la prueba PostgreSQL de este caso.
        verify(tareas,never()).save(any());
    }

    // ------------------------------------------------------------------
    // Disparador 6: reporte periodico al propietario
    // ------------------------------------------------------------------

    @Test
    void elReporteAlPropietarioSePideCada15DiasContadosDesdeElUltimo() {
        when(captaciones.activasDelAgente(anyLong(), anyLong())).thenReturn(List.of(
                candidato(9L, "CAP-0001", hoy.minusDays(60), null),   // reporte viejo
                candidato(8L, "CAP-0002", hoy.minusDays(60), null))); // reporte reciente
        when(reportes.ultimasComunicaciones(anyLong(), anyCollection())).thenReturn(List.of(
                new Object[]{9L, hoy.minusDays(20)},
                new Object[]{8L, hoy.minusDays(3)}));

        List<FichaTarea> bandeja = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertEquals(1, bandeja.size());
        assertEquals(9L, bandeja.get(0).entidadId());
        assertEquals(Tarea.REPORTE_PROPIETARIO, bandeja.get(0).tipo());
        assertEquals("Reporta avances al propietario de la captacion CAP-0001.",
                bandeja.get(0).descripcion());
    }

    @Test
    void sinReportePrevioElRelojArrancaEnLaFechaDeCaptacion() {
        when(captaciones.activasDelAgente(anyLong(), anyLong())).thenReturn(List.of(
                candidato(9L, "CAP-0001", hoy.minusDays(30), null),
                candidato(8L, "CAP-0002", hoy.minusDays(2), null)));
        when(reportes.ultimasComunicaciones(anyLong(), anyCollection())).thenReturn(List.of());

        List<FichaTarea> bandeja = service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);

        assertEquals(1, bandeja.size());
        assertEquals(9L, bandeja.get(0).entidadId());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private void activarNecesidades(List<Necesidad> necesidades) {
        when(seguimiento.necesidadesActuales(agente)).thenReturn(necesidades);
        when(seguimiento.marcasActuales(agente)).thenReturn(necesidades.stream().map(Necesidad::marcaCausa).collect(Collectors.toSet()));
    }
    private static String marca(long dato){return String.format("%064x",dato);}
    private static Necesidad necesidad(long prospeccion,LocalDate proxima) {
        return necesidad(prospeccion,prospeccion,marca(prospeccion),proxima);
    }
    private static Necesidad necesidad(long prospeccion,long idSeguimiento,String marca,LocalDate proxima) {
        var r=new RevisionSeguimiento(idSeguimiento,1,prospeccion,"Propietario o representante declarado",
                "PROXIMA_ACTUACION",null,0,proxima,"EXPLICITA",null,null,false,false,false,null);
        return new Necesidad(r,marca,ROL_AGENTE);
    }

    @Test
    void dosDestinatariosNoSeColapsanPorCompartirLaProspeccion() {
        var a=necesidad(11,101,marca(1),hoy.minusDays(1));var b=necesidad(11,102,marca(2),hoy.minusDays(1));
        activarNecesidades(List.of(a,b));
        var bandeja=service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);
        assertEquals(2,bandeja.size());assertEquals(Set.of(101L,102L),tabla.stream().map(Tarea::getIdSeguimientoProspeccion).collect(Collectors.toSet()));
        assertEquals(Set.of(marca(1),marca(2)),tabla.stream().map(Tarea::getMarcaCausa).collect(Collectors.toSet()));
        verify(interprete).contextoDe(eq(agente),argThat(asuntos->asuntos.size()==2&&asuntos.stream()
                .map(InterpreteDeLaBandeja.AsuntoADescribir::marcaCausa).collect(Collectors.toSet()).equals(Set.of(marca(1),marca(2)))));
    }

    @Test
    void hechoMaterialNuevoTrasCancelacionCreaOtraTareaVinculadaSinModificarLaAnterior() {
        var anterior=tareaExistente("PROSPECCION",11,Tarea.CANCELADA);anterior.setTipo(Tarea.RECONTACTO);
        anterior.setMarcaCausa(marca(1));anterior.setIdSeguimientoProspeccion(101L);tabla.add(anterior);
        activarNecesidades(List.of(necesidad(11,101,marca(2),hoy.minusDays(1))));
        assertEquals(1,service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).size());assertEquals(2,tabla.size());
        var nueva=tabla.getLast();assertNotEquals(anterior.getId(),nueva.getId());assertEquals(77L,nueva.getIdAntecedente());
        assertEquals(marca(2),nueva.getMarcaCausa());assertEquals(Tarea.CANCELADA,anterior.getEstado());assertEquals(marca(1),anterior.getMarcaCausa());
        assertEquals(1,service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).size());assertEquals(2,tabla.size());
    }

    @Test
    void unaCancelacionAnteriorSinIdentidadMaterialNoSuprimeTodaNecesidadDelRecurso() {
        tabla.add(tareaExistente("PROSPECCION",11,Tarea.CANCELADA));
        activarNecesidades(List.of(necesidad(11,hoy.minusDays(1))));
        assertEquals(1,service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).size());assertEquals(2,tabla.size());
    }

    @Test
    void posposicionUOcultacionConservaLaCausaSinFingirResolucionNiReiniciarFechas() {
        var n=necesidad(11,hoy.minusDays(1));activarNecesidades(List.of(n));service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente);
        var original=tabla.getFirst();var fecha=original.getFechaProgramada();
        var r=n.revision();var pospuesta=new Necesidad(new RevisionSeguimiento(r.id(),2,r.idProspeccion(),r.destinatario(),
                r.causa(),r.fechaUltimoIntento(),r.recontactosSinRespuesta(),r.proximaFecha(),r.origenFecha(),
                hoy.plusDays(5),"Posposición propia",r.revisarContinuidad(),r.restringido(),r.alcancePorPrecisar(),r.contactoDeclarado()),n.marcaCausa(),n.responsableActual());
        when(seguimiento.necesidadesActuales(agente)).thenReturn(List.of(pospuesta));
        var futura=service.bandejaDe(new com.controllocal.service.soporte.PoliticaDeDespacho.Consulta(com.controllocal.service.soporte.PoliticaDeDespacho.Vista.FUTURAS,null,null),agente);
        assertEquals(1,futura.size());assertEquals(hoy.minusDays(1),futura.getFirst().fechaVencimiento(),"D6-08: posponer la revisión no traslada el vencimiento original.");
        assertEquals(com.controllocal.service.soporte.PoliticaDeDespacho.Banda.R1,futura.getFirst().orden().banda());
        assertEquals(hoy.plusDays(5),futura.getFirst().orden().revisarEn());
        var vencidas=service.bandejaDe(new com.controllocal.service.soporte.PoliticaDeDespacho.Consulta(com.controllocal.service.soporte.PoliticaDeDespacho.Vista.VENCIDAS,null,null),agente);
        assertEquals(futura.getFirst().id(),vencidas.getFirst().id());assertTrue(vencidas.getFirst().orden().plazoVencido());
        assertTrue(service.bandejaDe(new com.controllocal.service.soporte.PoliticaDeDespacho.Consulta(com.controllocal.service.soporte.PoliticaDeDespacho.Vista.HOY,null,null),agente).isEmpty());assertEquals(Tarea.PENDIENTE,original.getEstado());
        when(contexto.hoy()).thenReturn(hoy.plusDays(6));
        activarNecesidades(List.of(pospuesta));assertEquals(1,service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).size());
        assertEquals(1,tabla.size());assertEquals(fecha,original.getFechaProgramada());
    }

    @Test
    void cancelarUnaCausaYaNoAutorizadaNoMutaLaTarea() {
        var t=tareaExistente("PROSPECCION",11,Tarea.PENDIENTE);t.setTipo(Tarea.RECONTACTO);
        tabla.add(t);
        t.setMarcaCausa(marca(1));t.setIdSeguimientoProspeccion(101L);
        when(tareas.buscarFicha(ORG,77L)).thenReturn(Optional.of(t));
        assertThrows(AccesoNoAutorizadoException.class,()->service.cancelar(77,agente));
        assertEquals(Tarea.PENDIENTE,t.getEstado());verify(tareas,never()).save(any());
    }

    @Test
    void unaTareaDeOtroTenantNoRevelaSuExistenciaPorUnaBusquedaGlobal() {
        var ajena=tareaExistente("OPORTUNIDAD",11,Tarea.PENDIENTE);ajena.setOrganizacionId(99L);
        ReflectionTestUtils.setField(ajena.getAgente(),"id",330L);
        tabla.add(ajena);
        assertEquals("Tarea no encontrada.",assertThrows(ReglaNegocioException.class,()->service.cancelar(77,agente)).getMessage());
        verify(tareas).cabeceraPersonal(ORG,ROL_AGENTE,77L);verify(tareas,never()).findById(anyLong());
        verify(tareas,never()).buscarFicha(anyLong(),anyLong());
        verify(tareas,never()).buscarFicha(eq(99L),anyLong());
    }

    @Test
    void unCambioEntreProyeccionYEntregaNoProduceUnaBandejaFalsamenteVacia() {
        var n=necesidad(11,hoy.minusDays(1));activarNecesidades(List.of(n));
        when(seguimiento.necesidadesActuales(agente)).thenReturn(List.of(n),List.of());
        assertThrows(com.controllocal.service.excepcion.ConflictoException.class,()->service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente));
        // Rollback y aislamiento del INSERT se demuestran en PG; la tabla Mockito no simula una transacción.
    }

    @Test
    void esperaSinFechaNoFabricaUnDisparadorDesdeElDiaDeLectura() {
        when(seguimiento.necesidadesActuales(agente)).thenReturn(List.of(necesidad(11,null)));
        when(seguimiento.marcasActuales(agente)).thenReturn(Set.of(marca(11)));
        assertTrue(service.bandejaDe(com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.todas(),agente).isEmpty());
        verify(tareas,never()).crearRecontacto(anyLong(),anyLong(),anyLong(),anyString(),anyString(),anyString(),any(),anyLong());
    }

    @Test void recontactoFuturoEsConsultableSinEsperarVencimientoNiDuplicarLaOcurrencia(){
        activarNecesidades(List.of(necesidad(11,hoy.plusDays(60))));
        var q=new com.controllocal.service.soporte.PoliticaDeDespacho.Consulta(com.controllocal.service.soporte.PoliticaDeDespacho.Vista.FUTURAS,null,null);
        var primera=service.bandejaDe(q,agente).getFirst();assertEquals(com.controllocal.service.soporte.PoliticaDeDespacho.Banda.R2,primera.orden().banda());
        assertEquals(primera.id(),service.bandejaDe(q,agente).getFirst().id());assertEquals(1,tabla.size());
        assertTrue(service.bandejaDe(new com.controllocal.service.soporte.PoliticaDeDespacho.Consulta(com.controllocal.service.soporte.PoliticaDeDespacho.Vista.VENCIDAS,null,null),agente).isEmpty());
    }

    private static FichaTarea buscar(List<FichaTarea> bandeja, long entidadId) {
        return bandeja.stream()
                .filter(t -> t.entidadId() == entidadId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("sin tarea para la entidad " + entidadId));
    }

    private Tarea tareaExistente(String entidadTipo, long entidadId, String estado) {
        Tarea tarea = new Tarea();
        tarea.setOrganizacionId(ORG);
        tarea.setTipo(Tarea.SEGUIMIENTO);
        tarea.setEntidadTipo(entidadTipo);
        tarea.setEntidadId(entidadId);
        tarea.setAgente(detalleAgente());
        tarea.setDescripcion("descripcion previa");
        tarea.setFechaProgramada(OffsetDateTime.now());
        tarea.nacer(Tarea.MEDIA);
        tarea.setEstado(estado);
        tarea.setId(77L);
        return tarea;
    }

    /**
     * Proyeccion de disparador. Implementacion real y no un mock a proposito:
     * estos candidatos se construyen DENTRO de un {@code when(...)}, y un mock
     * anidado ahi deja a Mockito con un stubbing a medias.
     */
    private record Candidato(Long entidadId, String entidadCodigo, LocalDate fechaPlazo, String marca)
            implements CandidatoTarea {
        public java.time.LocalTime getHoraPlazo(){return null;}
        public java.time.OffsetDateTime getDetectadoEn(){return null;}

        @Override
        public Long getEntidadId() {
            return entidadId;
        }

        @Override
        public String getEntidadCodigo() {
            return entidadCodigo;
        }

        @Override
        public LocalDate getFechaPlazo() {
            return fechaPlazo;
        }

        @Override
        public String getMarca() {
            return marca;
        }
    }

    private static CandidatoTarea candidato(Long id, String codigo, LocalDate plazo, String marca) {
        return new Candidato(id, codigo, plazo, marca);
    }

    private static DetalleAgente detalleAgente() {
        Persona persona = new Persona();
        persona.setNombresORazonSocial("Valentina Mora");
        PersonaRol rol = new PersonaRol();
        rol.setPersona(persona);
        rol.setTipoRol(TipoRol.AGENTE);

        DetalleAgente detalle = new DetalleAgente();
        detalle.setOrganizacionId(ORG);
        detalle.setRol(rol);
        detalle.setCodigoAgente("AGE-001");
        ReflectionTestUtils.setField(detalle, "id", ROL_AGENTE);
        return detalle;
    }
}
