package com.controllocal.service.impl;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.matching.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.CoincidenciaService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.disponibilidad.DisponibilidadContextualService;
import com.controllocal.service.matching.*;
import com.controllocal.service.matching.FuenteCarteraMatching.Candidata;
import com.controllocal.service.soporte.*;
import com.controllocal.service.excepcion.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.time.*;
import java.util.*;
import static com.controllocal.service.matching.ContratoMatching.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Orquestación/autoridad y continuación; estos dobles no acreditan locks ni rollback PostgreSQL. */
class CoincidenciaServiceImplTest {
    private static final Actor AGENTE=new Actor(1,2,3,Actor.AGENTE);
    private static final Actor GOBIERNO=new Actor(1,2,9,Actor.TENANT_ADMIN);
    private static final Instant CORTE=Instant.parse("2026-10-29T12:00:00Z");
    private final RequerimientoService demandas=mock(RequerimientoService.class);
    private final FuenteCarteraMatching cartera=mock(FuenteCarteraMatching.class);
    private final OfertasNetworkParaMatching network=mock(OfertasNetworkParaMatching.class);
    private final RecomendacionesInternasParaMatching internas=mock(RecomendacionesInternasParaMatching.class);
    private final ContextosService contextos=mock(ContextosService.class);
    private final DisponibilidadContextualService disponibilidad=mock(DisponibilidadContextualService.class);
    private final RecorridoMatchingRepository recorridos=mock(RecorridoMatchingRepository.class);
    private final ReferenciaRecorridoMatchingRepository referencias=mock(ReferenciaRecorridoMatchingRepository.class);
    private final CursorMatchingRepository cursores=mock(CursorMatchingRepository.class);
    private final Documentos documentos=new Documentos(new ObjectMapper().findAndRegisterModules());
    private final Reloj reloj=new Reloj();
    private final Map<UUID,RecorridoMatching> guardados=new HashMap<>();
    private final List<ReferenciaRecorridoMatching> filas=new ArrayList<>();
    private final Map<String,CursorMatching> tokens=new HashMap<>();
    private final Map<UUID,Set<Integer>> retiradas=new HashMap<>();
    private final ContextosService.SesionAccesoDTO identidad=dto(AGENTE,"S".repeat(43));
    private final ContextoSesion sesion=ContextoSesion.de(identidad);
    private CoincidenciaServiceImpl servicio;
    private Candidata candidata;

    @BeforeEach void preparar() {
        var evaluador=new MotorMatchingDeterminista();
        servicio=new CoincidenciaServiceImpl(demandas,cartera,network,internas,contextos,disponibilidad,
                recorridos,referencias,cursores,documentos,evaluador,new AlternativasPrecioMatching(evaluador),reloj);
        when(contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),AGENTE)).thenReturn(identidad);
        when(demandas.contextoDisponibilidad(9,AGENTE,false)).thenReturn(meta(7));
        when(demandas.demandasParaMatching(eq(AGENTE),any())).thenReturn(List.of(ref(7)));
        when(demandas.paraMatching(eq(9L),anyLong(),any(),anyString(),any(),eq(AGENTE)))
                .thenAnswer(i->consultaNormalizada(9,i.getArgument(1),i.getArgument(2),i.getArgument(3),i.getArgument(4),CoberturaCriterios.COMPLETA));
        when(disponibilidad.paraMatching(DisponibilidadContextualService.TipoRecurso.DEMANDA,9,AGENTE)).thenReturn(confirmacion());
        candidata=candidata(true,1);
        when(cartera.referencias(AGENTE)).thenReturn(List.of(11L));
        when(cartera.encargos(anyCollection(),eq(AGENTE),any())).thenAnswer(i->((Collection<?>)i.getArgument(0)).isEmpty()?List.of():List.of(candidata));
        when(cartera.encargo(eq(11L),eq(AGENTE),any())).thenAnswer(i->candidata);
        when(cartera.versionAutorizada(Origen.ENCARGO,11,AGENTE)).thenReturn(1L);
        when(recorridos.saveAndFlush(any())).thenAnswer(i->{RecorridoMatching r=i.getArgument(0);guardados.put(r.getId(),r);return r;});
        when(recorridos.delActor(anyLong(),any(),anyLong(),anyLong(),anyString(),anyString())).thenAnswer(i->{
            RecorridoMatching r=guardados.get(i.getArgument(1));
            return r!=null&&r.getOrganizacionId()==(long)i.getArgument(0)&&r.getIdPersona()==(long)i.getArgument(2)
                    &&r.getIdRol()==(long)i.getArgument(3)&&r.getRol().equals(i.getArgument(4))&&r.getHuellaContexto().equals(i.getArgument(5))?Optional.of(r):Optional.empty();
        });
        when(referencias.saveAllAndFlush(anyList())).thenAnswer(i->{List<ReferenciaRecorridoMatching> nuevas=i.getArgument(0);filas.addAll(nuevas);return nuevas;});
        when(referencias.delRecorrido(anyLong(),any())).thenAnswer(i->filas.stream().filter(f->f.getOrganizacionId()==(long)i.getArgument(0)&&f.getIdRecorrido().equals(i.getArgument(1))).toList());
        when(referencias.retiradas(anyLong(),any())).thenAnswer(i->retiradas.getOrDefault(i.getArgument(1),Set.of()).stream().sorted().toList());
        when(referencias.retirar(anyLong(),any(),anyInt(),any())).thenAnswer(i->retiradas.computeIfAbsent(i.getArgument(1),k->new HashSet<>()).add(i.getArgument(2))?1:0);
        when(cursores.porReferencia(anyLong(),anyString())).thenAnswer(i->{var c=tokens.get(i.getArgument(1));return c!=null&&c.getOrganizacionId()==(long)i.getArgument(0)?Optional.of(c):Optional.empty();});
        when(cursores.porPosicion(anyLong(),any(),anyInt())).thenAnswer(i->tokens.values().stream().filter(c->c.getOrganizacionId()==(long)i.getArgument(0)&&c.getIdRecorrido().equals(i.getArgument(1))&&c.getPosicion()==(int)i.getArgument(2)).findFirst());
        when(cursores.insertar(anyString(),anyLong(),any(),anyInt())).thenAnswer(i->{var c=new CursorMatching();c.setReferencia(i.getArgument(0));c.setOrganizacionId(i.getArgument(1));c.setIdRecorrido(i.getArgument(2));c.setPosicion(i.getArgument(3));tokens.put(c.getReferencia(),c);return 1;});
    }
    @Test void gobiernoNoObtieneUniversoPorUnaConsultaVacia() {
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(peticion(Set.of(Capa.L0)),GOBIERNO,sesion));
        verifyNoInteractions(demandas,cartera,network,internas,recorridos,referencias,cursores);
    }
    @Test void restriccionesActualesDelMismoSidSeCompruebanAntesDeLeer() {
        var limitada=new ContextosService.SesionAccesoDTO(identidad.referencia(),1,identidad.identidad(),identidad.activo(),identidad.emitidaEn(),identidad.venceEn(),new EstadoDeAcceso(null,false,true,Actor.AGENTE));
        when(contextos.exigirContexto(sesion.referenciaSesion(),1,AGENTE)).thenReturn(limitada);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion));
        verifyNoInteractions(demandas,cartera,network,internas,recorridos);
    }
    @Test void versionAusenteNoSeCompletaConLaActual() {
        var q=new CoincidenciaService.Consulta(Origen.DEMANDA,9L,null,TipoVersion.DEMANDA,Modalidad.COMERCIAL,Set.of(Capa.L0),Categoria.COMERCIAL_CONFIRMADA,null,null);
        assertThrows(ReglaNegocioException.class,()->servicio.consultar(q,AGENTE,sesion));verifyNoInteractions(demandas,cartera,recorridos);
    }
    @Test void inversaL2SeRechazaAntesDelUniverso() {
        var q=new CoincidenciaService.Consulta(Origen.ENCARGO,11L,1L,TipoVersion.ENCARGO,Modalidad.COMERCIAL,Set.of(Capa.L2),Categoria.COMERCIAL_CONFIRMADA,20,null);
        assertThrows(ReglaNegocioException.class,()->servicio.consultar(q,AGENTE,sesion));verifyNoInteractions(demandas,cartera,network);
    }
    @Test void resultadoUtilTieneReferenciaOpacaSinPorcentajeYCaducidadAbsoluta() {
        var r=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);
        assertEquals(1L,r.total());assertTrue(r.referenciaConsulta().matches("[A-Za-z0-9_-]{43}"));assertNull(r.continuacion());
        assertEquals(0,r.grupos().getFirst().candidatos().getFirst().w());
        var guardado=guardados.values().iterator().next();assertEquals(CORTE.plus(Duration.ofMinutes(15)),guardado.getVenceEn());
        assertEquals("L0",filas.getFirst().getCapa());assertEquals(CoberturaCriterios.COMPLETA,r.grupos().getFirst().candidatos().getFirst().coberturaCriterios());
    }
    @Test void cursorNoAutorizaAOtroTenant() {
        var r=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);
        var otro=new Actor(2,4,5,Actor.AGENTE);var identidadOtra=dto(otro,"T".repeat(43));var sesionOtra=ContextoSesion.de(identidadOtra);
        when(contextos.exigirContexto(sesionOtra.referenciaSesion(),1,otro)).thenReturn(identidadOtra);
        assertThrows(NoEncontradoException.class,()->servicio.continuar(r.referenciaConsulta(),otro,sesionOtra));
    }
    @Test void revocacionDeSesionSeCompruebaAunqueConozcaElCursor() {
        var r=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);
        when(contextos.exigirContexto(sesion.referenciaSesion(),1,AGENTE)).thenThrow(new AccesoNoAutorizadoException());
        clearInvocations(cartera,demandas,network,internas);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.continuar(r.referenciaConsulta(),AGENTE,sesion));verifyNoInteractions(cartera,demandas,network,internas);
    }
    @Test void quinceMinutosExactosVencenSinExtenderNiVolverAEvaluar() {
        var r=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);reloj.actual=CORTE.plus(Duration.ofMinutes(15));
        clearInvocations(cartera,demandas,network,internas);
        var vencida=servicio.continuar(r.referenciaConsulta(),AGENTE,sesion);
        assertEquals(EstadoResultado.CONTINUIDAD_VENCIDA,vencida.estado());assertNull(vencida.total());assertTrue(vencida.grupos().isEmpty());
        verify(cartera,never()).encargos(anyCollection(),any(),any());verify(demandas,never()).paraMatching(anyLong(),anyLong(),any(),anyString(),any(),any());
        assertEquals(CORTE.plus(Duration.ofMinutes(15)),guardados.values().iterator().next().getVenceEn());
    }
    @Test void continuarUnRecorridoDeOtroEvaluadorExigeActualizarSinRecalcularNiRetirar() {
        var primera=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);
        var original=guardados.values().iterator().next();
        // Fixture en memoria de un recorrido creado por el ejecutable anterior; no UPDATE SQL del original.
        original.setVersionRegla("D1-D4-20261018-C3-R2");
        var corteOriginal=original.getCorte();var vencimientoOriginal=original.getVenceEn();
        clearInvocations(contextos,demandas,cartera,network,internas,disponibilidad,referencias);

        var resultado=servicio.continuar(primera.referenciaConsulta(),AGENTE,sesion);
        assertEquals(EstadoResultado.CONTEXTO_CAMBIADO,resultado.estado());assertNull(resultado.total());assertTrue(resultado.grupos().isEmpty());assertNull(resultado.continuacion());
        assertEquals(primera.referenciaConsulta(),resultado.referenciaConsulta());assertEquals(corteOriginal,resultado.corte());
        assertEquals(vencimientoOriginal,original.getVenceEn());assertEquals("D1-D4-20261018-C3-R2",original.getVersionRegla());assertEquals(1,filas.size());
        verify(contextos).exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),AGENTE);
        verifyNoInteractions(demandas,cartera,network,internas,disponibilidad,referencias);
    }
    @Test void explicarUnParDeOtroEvaluadorRevalidaSesionPeroNoReinterpretaLaEvaluacion() {
        var primera=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);var grupo=primera.grupos().getFirst();
        var original=guardados.values().iterator().next();original.setVersionRegla("D1-D4-20261018-C3-R2");
        var vencimientoOriginal=original.getVenceEn();
        clearInvocations(contextos,demandas,cartera,network,internas,disponibilidad,referencias);

        assertThrows(ConflictoException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),grupo.candidatos().getFirst().referencia(),AGENTE,sesion));
        assertEquals(vencimientoOriginal,original.getVenceEn());assertEquals("D1-D4-20261018-C3-R2",original.getVersionRegla());assertEquals(1,filas.size());
        verify(contextos).exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),AGENTE);
        verifyNoInteractions(demandas,cartera,network,internas,disponibilidad,referencias);
    }
    @Test void ofertaCambiadaNoSeEntregaConLaEvaluacionAnteriorNiReordenaElAntecedente() {
        var r=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);candidata=candidata(false,2);
        var cambio=servicio.continuar(r.referenciaConsulta(),AGENTE,sesion);
        assertEquals(EstadoResultado.DISPONIBLE,cambio.estado());assertEquals(0L,cambio.total());assertTrue(cambio.grupos().isEmpty());
        assertEquals(1,filas.size());assertEquals(1,filas.getFirst().getVersionRecurso());
    }
    @Test void cambiarLosCriteriosDelOrigenInvalidaElRecorridoCompleto() {
        var r=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);
        when(demandas.paraMatching(eq(9L),anyLong(),any(),anyString(),any(),eq(AGENTE)))
                .thenAnswer(i->consultaConPreferencia(9,i.getArgument(1),2,i.getArgument(2),i.getArgument(3),i.getArgument(4)));
        var cambio=servicio.continuar(r.referenciaConsulta(),AGENTE,sesion);
        assertEquals(EstadoResultado.CONTEXTO_CAMBIADO,cambio.estado());assertNull(cambio.total());assertTrue(cambio.grupos().isEmpty());
        assertEquals(1,filas.size());
    }
    @Test void ofertaQueVuelveASuContenidoOriginalNoReapareceSinActualizarLaConsulta() {
        var primera=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);
        candidata=candidata(false,2);
        assertEquals(0L,servicio.continuar(primera.referenciaConsulta(),AGENTE,sesion).total());
        candidata=candidata(true,3);
        var misma=servicio.continuar(primera.referenciaConsulta(),AGENTE,sesion);
        assertEquals(EstadoResultado.DISPONIBLE,misma.estado());assertEquals(0L,misma.total());assertTrue(misma.grupos().isEmpty());
        assertThrows(NoEncontradoException.class,()->servicio.explicar(primera.referenciaConsulta(),primera.grupos().getFirst().referenciaDemanda(),candidata.oferta().referenciaEstable(),AGENTE,sesion));
        var actualizada=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);
        assertEquals(1L,actualizada.total());assertEquals(3,actualizada.grupos().getFirst().candidatos().getFirst().version());
        assertNotEquals(primera.referenciaConsulta(),actualizada.referenciaConsulta());
        verify(referencias,times(1)).retirar(eq(AGENTE.idOrganizacion()),any(),eq(0),eq(CORTE));
    }
    @Test void versionTecnicaSinCambioMaterialConservaOrdenYCorte() {
        var r=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);candidata=candidata(true,2);
        when(demandas.contextoDisponibilidad(9,AGENTE,false)).thenReturn(meta(8));when(demandas.demandasParaMatching(eq(AGENTE),any())).thenReturn(List.of(ref(8)));
        var pagina=servicio.continuar(r.referenciaConsulta(),AGENTE,sesion);
        assertEquals(EstadoResultado.DISPONIBLE,pagina.estado());assertEquals(CORTE,pagina.corte());assertEquals(8,pagina.grupos().getFirst().versionDemanda());
        assertEquals(2,pagina.grupos().getFirst().candidatos().getFirst().version());assertEquals(7,guardados.values().iterator().next().getVersionOrigen());
    }
    @Test void capaTecnicaFallidaSeDeclaraYNoSeReintentaComoVaciaEnElRecorrido() {
        when(network.candidatas(eq(9L),eq(7L),eq(AGENTE),any())).thenThrow(new DataAccessResourceFailureException("fallo TEST"));
        var r=servicio.consultar(peticion(Set.of(Capa.L0,Capa.L2)),AGENTE,sesion);
        assertEquals(Cobertura.LIMITADA,r.cobertura());assertEquals(Set.of(Capa.L2),r.capasNoDisponibles());assertNull(r.total());
        var repetida=servicio.continuar(r.referenciaConsulta(),AGENTE,sesion);assertEquals(Set.of(Capa.L2),repetida.capasNoDisponibles());
        verify(network,times(1)).candidatas(anyLong(),anyLong(),any(),any());verify(network,never()).revalidar(anyLong(),anyLong(),anyList(),any(),any());
    }
    @Test void todasLasCapasFallidasSonFalloExplicitoSinSnapshotExitoso() {
        when(cartera.referencias(AGENTE)).thenThrow(new DataAccessResourceFailureException("fallo TEST"));
        assertThrows(IllegalStateException.class,()->servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion));verify(recorridos,never()).saveAndFlush(any());
    }
    @Test void l1NoExigeActivacionL2NiRevelaReferenciaInterna() {
        var oferta=candidata.oferta();var referencia=new RecomendacionesInternasParaMatching.ReferenciaOferta("INTERNA_PRIVADA_11",1,"a".repeat(64));
        var colega=new RecomendacionesInternasParaMatching.ResponsableVisible("COMPANERO","Colega TEST");
        var p=new RecomendacionesInternasParaMatching.OfertaInterna(referencia,oferta,Map.of("tipo","D"),colega);
        when(internas.ofertas(any(),eq(AGENTE),any())).thenReturn(List.of(p));when(internas.revalidarOfertas(any(),anyList(),eq(AGENTE),any())).thenReturn(List.of(p));
        var r=servicio.consultar(peticion(Set.of(Capa.L1)),AGENTE,sesion);var c=r.grupos().getFirst().candidatos().getFirst();
        assertEquals(Capa.L1,c.capa());assertNotEquals("INTERNA_PRIVADA_11",c.referencia());assertEquals(colega,c.datosPermitidos().get("responsableCoincidente"));verifyNoInteractions(network);
    }
    @Test void inversaL1NuncaCargaPresupuestoAjenoYExponeAlcanceParcial() {
        var consulta=consultaNormalizada(13,1,Modalidad.COMERCIAL,CoincidenciaServiceImpl.VERSION_REGLA,CORTE,CoberturaCriterios.PARCIAL);
        var d=new RecomendacionesInternasParaMatching.DemandaInterna(13,1,consulta,new RecomendacionesInternasParaMatching.ResponsableVisible("COMPANERO","Colega TEST"),confirmacion());
        when(internas.demandas(any(),any(),anyString(),any(),eq(AGENTE))).thenReturn(List.of(d));
        when(internas.revalidarDemandas(any(),anyList(),any(),anyString(),any(),eq(AGENTE),any())).thenReturn(List.of(d));
        var q=new CoincidenciaService.Consulta(Origen.ENCARGO,11L,1L,TipoVersion.ENCARGO,Modalidad.COMERCIAL,Set.of(Capa.L1),Categoria.OBLIGATORIAS_PENDIENTES,20,null);
        var r=servicio.consultar(q,AGENTE,sesion);var c=r.grupos().getFirst().candidatos().getFirst();
        assertEquals(CoberturaCriterios.PARCIAL,c.coberturaCriterios());assertEquals(Conclusion.NO_DETERMINADO,c.h());
        assertEquals(Set.of("responsableCoincidente","recursoResponsable"),c.datosPermitidos().keySet());
        verify(demandas,never()).paraMatching(eq(13L),anyLong(),any(),anyString(),any(),any());verify(disponibilidad,never()).paraMatching(any(),eq(13L),any());
    }
    @Test void cambioDeUnaOfertaRetiraSoloEsaReferenciaYCompletaConLasVigentesDelRecorrido() {
        var actuales=new ArrayList<>(List.of(ofertaParaRecorrido(11,3,1,1),ofertaParaRecorrido(12,3,1,1),ofertaParaRecorrido(13,3,1,1)));
        when(cartera.referencias(AGENTE)).thenReturn(List.of(11L,12L,13L));
        when(cartera.encargos(anyCollection(),eq(AGENTE),any())).thenAnswer(i->{
            Collection<Long> pedidas=i.getArgument(0);
            return actuales.stream().filter(c->pedidas.contains(Long.valueOf(c.referenciaInterna()))).toList();
        });
        var consulta=new CoincidenciaService.Consulta(Origen.DEMANDA,9L,7L,TipoVersion.DEMANDA,
                Modalidad.COMERCIAL,Set.of(Capa.L0),Categoria.COMERCIAL_CONFIRMADA,1,null);
        var primera=servicio.consultar(consulta,AGENTE,sesion);
        assertEquals(3L,primera.total());assertNotNull(primera.continuacion());
        var retirada=primera.grupos().getFirst().candidatos().getFirst();
        actuales.set(0,ofertaParaRecorrido(11,3,0,2));

        var siguiente=servicio.continuar(primera.continuacion(),AGENTE,sesion);
        assertEquals(EstadoResultado.DISPONIBLE,siguiente.estado());
        assertEquals(2L,siguiente.total());assertEquals(12L,siguiente.grupos().getFirst().candidatos().getFirst().datosPermitidos().get("idEncargo"));
        assertNotNull(siguiente.continuacion());
        var ultima=servicio.continuar(siguiente.continuacion(),AGENTE,sesion);
        assertEquals(13L,ultima.grupos().getFirst().candidatos().getFirst().datosPermitidos().get("idEncargo"));assertNull(ultima.continuacion());
        assertThrows(NoEncontradoException.class,()->servicio.explicar(primera.referenciaConsulta(),primera.grupos().getFirst().referenciaDemanda(),retirada.referencia(),AGENTE,sesion));
        assertEquals(3,filas.size(),"Retirar de la entrega no borra el antecedente ni añade candidatos después del corte.");
    }
    @Test void unaCapaFallidaConservaLosResultadosPermitidosPeroNoAfirmaUnTotalExhaustivo() {
        when(network.candidatas(eq(9L),eq(7L),eq(AGENTE),any())).thenThrow(new DataAccessResourceFailureException("fallo TEST"));
        var resultado=servicio.consultar(peticion(Set.of(Capa.L0,Capa.L2)),AGENTE,sesion);
        assertEquals(Cobertura.LIMITADA,resultado.cobertura());assertEquals(Set.of(Capa.L2),resultado.capasNoDisponibles());
        assertEquals(1,resultado.grupos().getFirst().candidatos().size());
        assertNull(resultado.total(),"D4-39: el total de las capas completas no es el total del alcance solicitado.");
    }
    @Test void brokerDeduplicaLaMismaOfertaYConfiguracionRecuperadaPorL0YL1EnAmbosSentidos() {
        var broker=new Actor(1,4,5,Actor.BROKER);var identidadBroker=dto(broker,"B".repeat(43));var sesionBroker=ContextoSesion.de(identidadBroker);
        when(contextos.exigirContexto(sesionBroker.referenciaSesion(),1,broker)).thenReturn(identidadBroker);
        when(demandas.contextoDisponibilidad(9,broker,false)).thenReturn(meta(7));
        when(demandas.demandasParaMatching(eq(broker),any())).thenReturn(List.of(ref(7)));
        when(demandas.paraMatching(eq(9L),anyLong(),any(),anyString(),any(),eq(broker)))
                .thenAnswer(i->consultaNormalizada(9,i.getArgument(1),i.getArgument(2),i.getArgument(3),i.getArgument(4),CoberturaCriterios.COMPLETA));
        when(disponibilidad.paraMatching(DisponibilidadContextualService.TipoRecurso.DEMANDA,9,broker)).thenReturn(confirmacion());
        var propia=ofertaParaRecorrido(11,3,1,1);
        when(cartera.referencias(broker)).thenReturn(List.of(11L));
        when(cartera.encargos(anyCollection(),eq(broker),any())).thenAnswer(i->((Collection<?>)i.getArgument(0)).isEmpty()?List.of():List.of(propia));
        var interna=new RecomendacionesInternasParaMatching.OfertaInterna(
                new RecomendacionesInternasParaMatching.ReferenciaOferta("encargo:11",1,"a".repeat(64)),propia.oferta(),
                Map.of("PROPIEDAD:ascensores",Map.of("valor",1)),new RecomendacionesInternasParaMatching.ResponsableVisible("AGENTE:3","Profesional TEST"));
        when(internas.ofertas(any(),eq(broker),any())).thenReturn(List.of(interna));
        when(internas.revalidarOfertas(any(),anyList(),eq(broker),any())).thenReturn(List.of(interna));

        var resultado=assertDoesNotThrow(()->servicio.consultar(peticion(Set.of(Capa.L0,Capa.L1)),broker,sesionBroker));
        assertEquals(1L,resultado.total());assertEquals(1,resultado.grupos().getFirst().candidatos().size());
        verify(cartera).referencias(broker);verify(internas).ofertas(any(),eq(broker),any());verifyNoInteractions(network);

        when(cartera.versionAutorizada(Origen.ENCARGO,11,broker)).thenReturn(1L);when(cartera.encargo(eq(11L),eq(broker),any())).thenReturn(propia);
        var reducida=consultaNormalizada(9,7,Modalidad.COMERCIAL,CoincidenciaServiceImpl.VERSION_REGLA,CORTE,CoberturaCriterios.PARCIAL);
        when(internas.demandas(any(),any(),anyString(),any(),eq(broker))).thenReturn(List.of(new RecomendacionesInternasParaMatching.DemandaInterna(9,7,reducida,
                new RecomendacionesInternasParaMatching.ResponsableVisible("AGENTE:3","Profesional TEST"),confirmacion())));
        var inversa=new CoincidenciaService.Consulta(Origen.ENCARGO,11L,1L,TipoVersion.ENCARGO,Modalidad.COMERCIAL,Set.of(Capa.L0,Capa.L1),Categoria.COMERCIAL_CONFIRMADA,20,null);
        var desdeOferta=assertDoesNotThrow(()->servicio.consultar(inversa,broker,sesionBroker));
        assertEquals(1L,desdeOferta.total());assertEquals(1,desdeOferta.grupos().getFirst().candidatos().size());
        assertEquals(CoberturaCriterios.COMPLETA,desdeOferta.grupos().getFirst().candidatos().getFirst().coberturaCriterios());
    }
    @Test void explicacionDeLaSegundaDemandaNoPuedeUsarLaEvaluacionDeLaPrimera() {
        when(demandas.demandasParaMatching(eq(AGENTE),any())).thenReturn(List.of(ref(7),new RequerimientoService.ReferenciaDemanda(13,2,3,"A",null,null)));
        when(demandas.paraMatching(eq(9L),anyLong(),any(),anyString(),any(),eq(AGENTE)))
                .thenAnswer(i->consultaConPreferencia(9,i.getArgument(1),1,i.getArgument(2),i.getArgument(3),i.getArgument(4)));
        when(demandas.paraMatching(eq(13L),anyLong(),any(),anyString(),any(),eq(AGENTE)))
                .thenAnswer(i->consultaConPreferencia(13,i.getArgument(1),2,i.getArgument(2),i.getArgument(3),i.getArgument(4)));
        when(disponibilidad.paraMatching(DisponibilidadContextualService.TipoRecurso.DEMANDA,13,AGENTE)).thenReturn(confirmacion());
        var q=new CoincidenciaService.Consulta(Origen.ENCARGO,11L,1L,TipoVersion.ENCARGO,Modalidad.COMERCIAL,Set.of(Capa.L0),Categoria.COMERCIAL_CONFIRMADA,20,null);
        var resultado=servicio.consultar(q,AGENTE,sesion);assertEquals(2,resultado.grupos().size());
        var primero=resultado.grupos().getFirst().candidatos().getFirst();
        var segundo=resultado.grupos().get(1).candidatos().getFirst();
        assertEquals(primero.referencia(),segundo.referencia(),"La identidad de oferta es la misma; son dos evaluaciones contra demandas distintas.");
        assertNotEquals(primero.s(),segundo.s(),"El montaje debe distinguir las dos evaluaciones.");
        assertEquals(segundo,servicio.explicar(resultado.referenciaConsulta(),resultado.grupos().get(1).referenciaDemanda(),segundo.referencia(),AGENTE,sesion),
                "La explicación del grupo2 no puede seleccionar el primer par que comparte oferta.");
        assertEquals(primero,servicio.explicar(resultado.referenciaConsulta(),resultado.grupos().getFirst().referenciaDemanda(),primero.referencia(),AGENTE,sesion));
        assertThrows(NoEncontradoException.class,()->servicio.explicar(resultado.referenciaConsulta(),"f".repeat(64),segundo.referencia(),AGENTE,sesion));
        assertThrows(ReglaNegocioException.class,()->servicio.explicar(resultado.referenciaConsulta(),null,segundo.referencia(),AGENTE,sesion));
    }
    @Test void explicacionQueCruzaElVencimientoNoEntregaUnResultadoDelCursorCaducado() {
        var r=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);
        reloj.actual=CORTE.plus(Duration.ofMinutes(15)).minusSeconds(1);
        when(cartera.encargos(anyCollection(),eq(AGENTE),any())).thenAnswer(i->{
            reloj.actual=CORTE.plus(Duration.ofMinutes(15));return List.of(candidata);
        });
        assertThrows(ConflictoException.class,()->servicio.explicar(r.referenciaConsulta(),r.grupos().getFirst().referenciaDemanda(),candidata.oferta().referenciaEstable(),AGENTE,sesion));
        assertEquals(CORTE.plus(Duration.ofMinutes(15)),guardados.values().iterator().next().getVenceEn());
    }
    @Test void falloL2DuranteContinuacionNoOcultaLaCapaL0NiLoConvierteEnAusenciaDeOferta() {
        var publicada=publicadaParaRecorrido(22);
        when(network.candidatas(eq(9L),eq(7L),eq(AGENTE),any())).thenReturn(List.of(publicada));
        when(network.revalidar(eq(9L),eq(7L),anyList(),eq(AGENTE),any())).thenReturn(List.of(publicada));
        var primera=servicio.consultar(peticion(Set.of(Capa.L0,Capa.L2)),AGENTE,sesion);assertEquals(2L,primera.total());
        when(network.revalidar(eq(9L),eq(7L),anyList(),eq(AGENTE),any())).thenThrow(new DataAccessResourceFailureException("fallo TEST durante continuación"));
        var limitada=servicio.continuar(primera.referenciaConsulta(),AGENTE,sesion);
        assertEquals(EstadoResultado.DISPONIBLE,limitada.estado());assertEquals(Cobertura.LIMITADA,limitada.cobertura());assertEquals(Set.of(Capa.L2),limitada.capasNoDisponibles());
        assertNull(limitada.total());assertEquals(1,limitada.grupos().getFirst().candidatos().size());
        assertEquals(Capa.L0,limitada.grupos().getFirst().candidatos().getFirst().capa());assertEquals(primera.corte(),limitada.corte());
        assertThrows(IllegalStateException.class,()->servicio.explicar(primera.referenciaConsulta(),primera.grupos().getFirst().referenciaDemanda(),publicada.referencia().ofertaRef(),AGENTE,sesion));
        when(network.revalidar(eq(9L),eq(7L),anyList(),eq(AGENTE),any())).thenReturn(List.of(publicada));
        var recuperada=servicio.continuar(primera.referenciaConsulta(),AGENTE,sesion);
        assertEquals(Cobertura.COMPLETA,recuperada.cobertura());assertEquals(2L,recuperada.total());assertEquals(primera.corte(),recuperada.corte());assertEquals(2,filas.size());
    }
    @Test void falloL1DuranteContinuacionConservaLaCapaL0SinTratarPermisosComoFalloTecnico() {
        var base=ofertaParaRecorrido(12,4,1,1);
        var interna=new RecomendacionesInternasParaMatching.OfertaInterna(
                new RecomendacionesInternasParaMatching.ReferenciaOferta("encargo:12",1,"c".repeat(64)),base.oferta(),
                Map.of("PROPIEDAD:ascensores",Map.of("valor",1)),new RecomendacionesInternasParaMatching.ResponsableVisible("AGENTE:4","Profesional TEST"));
        when(internas.ofertas(any(),eq(AGENTE),any())).thenReturn(List.of(interna));
        when(internas.revalidarOfertas(any(),anyList(),eq(AGENTE),any())).thenReturn(List.of(interna));
        var primera=servicio.consultar(peticion(Set.of(Capa.L0,Capa.L1)),AGENTE,sesion);assertEquals(2L,primera.total());
        when(internas.revalidarOfertas(any(),anyList(),eq(AGENTE),any())).thenThrow(new DataAccessResourceFailureException("fallo L1 TEST"));
        var limitada=servicio.continuar(primera.referenciaConsulta(),AGENTE,sesion);
        assertEquals(Cobertura.LIMITADA,limitada.cobertura());assertEquals(Set.of(Capa.L1),limitada.capasNoDisponibles());assertNull(limitada.total());
        assertEquals(Capa.L0,limitada.grupos().getFirst().candidatos().getFirst().capa());
        when(internas.revalidarOfertas(any(),anyList(),eq(AGENTE),any())).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.continuar(primera.referenciaConsulta(),AGENTE,sesion));
        when(internas.revalidarOfertas(any(),anyList(),eq(AGENTE),any())).thenThrow(new ReglaNegocioException("contrato inválido TEST"));
        assertThrows(ReglaNegocioException.class,()->servicio.continuar(primera.referenciaConsulta(),AGENTE,sesion));
    }
    @Test void explicarCandidatoCambiadoConservaLaRetiradaAnteSuDesenlace404() {
        var primera=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);var grupo=primera.grupos().getFirst();
        candidata=candidata(false,2);
        var desenlace=assertThrows(NoEncontradoException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),grupo.candidatos().getFirst().referencia(),AGENTE,sesion));
        assertFalse(revierteExplicacion(desenlace),"El404 de navegación posterior a revalidar no revierte la marca técnica.");
        verify(referencias).retirar(eq(AGENTE.idOrganizacion()),any(),eq(0),eq(CORTE));
    }
    @Test void contextoCambiadoAlRevalidarEsUnDesenlaceDeNavegacionSinRollback() {
        var primera=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);var grupo=primera.grupos().getFirst();
        when(demandas.paraMatching(eq(9L),anyLong(),any(),anyString(),any(),eq(AGENTE)))
                .thenAnswer(i->consultaConPreferencia(9,i.getArgument(1),2,i.getArgument(2),i.getArgument(3),i.getArgument(4)));
        var desenlace=assertThrows(ConflictoException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),grupo.candidatos().getFirst().referencia(),AGENTE,sesion));
        assertFalse(revierteExplicacion(desenlace));
    }
    @Test void vencimientoDespuesDeRevalidarNoRevierteUnaRetiradaYaDetectada() {
        var primera=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);var grupo=primera.grupos().getFirst();
        candidata=candidata(false,2);reloj.actual=CORTE.plus(Duration.ofMinutes(15)).minusSeconds(1);
        when(cartera.encargos(anyCollection(),eq(AGENTE),any())).thenAnswer(i->{reloj.actual=CORTE.plus(Duration.ofMinutes(15));return List.of(candidata);});
        var desenlace=assertThrows(ConflictoException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),grupo.candidatos().getFirst().referencia(),AGENTE,sesion));
        assertFalse(revierteExplicacion(desenlace));
        verify(referencias).retirar(eq(AGENTE.idOrganizacion()),any(),eq(0),eq(CORTE.plus(Duration.ofMinutes(15)).minusSeconds(1)));
    }
    @Test void fallosPreviosDeNavegacionYErroresDeFuenteConservanRollback() {
        var primera=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);var grupo=primera.grupos().getFirst();
        var inexistente=assertThrows(NoEncontradoException.class,()->servicio.explicar("cursor-invalido",grupo.referenciaDemanda(),grupo.candidatos().getFirst().referencia(),AGENTE,sesion));
        assertTrue(revierteExplicacion(inexistente),"No se excluyen todos los404 del rollback.");
        reloj.actual=CORTE.plus(Duration.ofMinutes(15));
        var vencido=assertThrows(ConflictoException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),grupo.candidatos().getFirst().referencia(),AGENTE,sesion));
        assertTrue(revierteExplicacion(vencido),"El vencimiento previo no es el desenlace posterior a revalidar.");
        reloj.actual=CORTE;
        when(cartera.encargos(anyCollection(),eq(AGENTE),any())).thenThrow(new DataAccessResourceFailureException("fuente TEST caída"));
        var tecnico=assertThrows(IllegalStateException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),grupo.candidatos().getFirst().referencia(),AGENTE,sesion));
        assertTrue(revierteExplicacion(tecnico));
    }
    @Test void permisoRevocadoTrasDetectarRetiradaMantieneRollback() {
        var primera=servicio.consultar(peticion(Set.of(Capa.L0)),AGENTE,sesion);var grupo=primera.grupos().getFirst();
        candidata=candidata(false,2);
        when(contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),AGENTE))
                .thenReturn(identidad).thenThrow(new AccesoNoAutorizadoException());
        var permiso=assertThrows(AccesoNoAutorizadoException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),grupo.candidatos().getFirst().referencia(),AGENTE,sesion));
        assertTrue(revierteExplicacion(permiso));
        verify(referencias).retirar(eq(AGENTE.idOrganizacion()),any(),eq(0),eq(CORTE));
    }
    @Test void descubrirPaginaPorPasosDePrecioSinUsarLosOrdinalesDelOrdenPorPreferencias() {
        configurarPrecios();
        var primera=servicio.consultar(peticionPrecio(ModoExploracion.DESCUBRIR,null,Set.of(Capa.L0),1),AGENTE,sesion);
        assertEquals(2L,primera.total());assertEquals(5,filas.size(),"El universo elegible se conserva para revalidar los pasos, sin duplicar valores.");
        var uno=primera.grupos().getFirst().candidatos().getFirst();assertEquals(11L,uno.datosPermitidos().get("idEncargo"));
        assertEquals(Conclusion.INCUMPLE,uno.h());assertEquals(Categoria.INCOMPATIBLE,uno.categoria());assertNotNull(uno.alternativaPrecio());
        assertEquals(0,uno.alternativaPrecio().precio().compareTo(new java.math.BigDecimal("110000")));assertEquals(0,uno.s());
        assertNotNull(primera.continuacion());
        var segunda=servicio.continuar(primera.continuacion(),AGENTE,sesion);var dos=segunda.grupos().getFirst().candidatos().getFirst();
        assertEquals(13L,dos.datosPermitidos().get("idEncargo"));assertEquals(2,dos.s());assertNull(segunda.continuacion());
        assertEquals(dos,servicio.explicar(primera.referenciaConsulta(),segunda.grupos().getFirst().referenciaDemanda(),dos.referencia(),AGENTE,sesion));
        var original=guardados.values().iterator().next();assertNotNull(original.getHuellaEscalones());
        assertEquals(ModoExploracion.DESCUBRIR,documentos.comoObjeto(original.getContextoExploracion(),ExploracionPrecio.class).modo());
        var noPropuesta=filas.stream().filter(f->"12".equals(f.getReferenciaRecurso())).findFirst().orElseThrow();
        assertThrows(NoEncontradoException.class,()->servicio.explicar(primera.referenciaConsulta(),primera.grupos().getFirst().referenciaDemanda(),noPropuesta.getReferenciaCandidato(),AGENTE,sesion));
    }
    @Test void descubrirNoCertificaUnMinimoSiUnaCapaSolicitadaEstaIndisponible() {
        configurarPrecios();
        when(network.candidatas(eq(9L),eq(7L),eq(AGENTE),any())).thenThrow(new DataAccessResourceFailureException("L2 TEST indisponible"));
        var resultado=servicio.consultar(peticionPrecio(ModoExploracion.DESCUBRIR,null,Set.of(Capa.L0,Capa.L2),20),AGENTE,sesion);
        assertEquals(EstadoResultado.DISPONIBLE,resultado.estado());assertEquals(Cobertura.LIMITADA,resultado.cobertura());
        assertEquals(Set.of(Capa.L2),resultado.capasNoDisponibles());assertNull(resultado.total());assertTrue(resultado.grupos().isEmpty());assertNull(resultado.continuacion());
        assertNull(guardados.values().iterator().next().getHuellaEscalones());
        var repetida=servicio.continuar(resultado.referenciaConsulta(),AGENTE,sesion);
        assertEquals(Cobertura.LIMITADA,repetida.cobertura());assertTrue(repetida.grupos().isEmpty());
        var acotada=servicio.consultar(peticionPrecio(ModoExploracion.EXPLORAR,"120000",Set.of(Capa.L0,Capa.L2),20),AGENTE,sesion);
        assertEquals(Cobertura.LIMITADA,acotada.cobertura());assertNull(acotada.total());assertEquals(3,acotada.grupos().getFirst().candidatos().size());
        assertTrue(acotada.grupos().getFirst().candidatos().stream().allMatch(c->c.alternativaPrecio()!=null));
    }
    @Test void unCambioDeEscalonRequiereActualizarSinReemplazarLaPropuestaSilenciosamente() {
        var actuales=configurarPrecios();
        var primera=servicio.consultar(peticionPrecio(ModoExploracion.DESCUBRIR,null,Set.of(Capa.L0),1),AGENTE,sesion);
        actuales.set(0,ofertaConPrecio(11,"111000",1,0,2));
        var cambiada=servicio.continuar(primera.continuacion(),AGENTE,sesion);
        assertEquals(EstadoResultado.CONTEXTO_CAMBIADO,cambiada.estado());assertNull(cambiada.total());assertTrue(cambiada.grupos().isEmpty());
        actuales.set(0,ofertaConPrecio(11,"110000",1,0,3));
        assertEquals(EstadoResultado.CONTEXTO_CAMBIADO,servicio.continuar(primera.referenciaConsulta(),AGENTE,sesion).estado());
        var actualizada=servicio.consultar(peticionPrecio(ModoExploracion.DESCUBRIR,null,Set.of(Capa.L0),20),AGENTE,sesion);
        assertEquals(11L,actualizada.grupos().getFirst().candidatos().getFirst().datosPermitidos().get("idEncargo"));
        assertNotEquals(primera.referenciaConsulta(),actualizada.referenciaConsulta());
    }
    @Test void unaAlternativaNoElegidaQueAhoraEsMasBarataInvalidaElDescubrimiento() {
        var actuales=configurarPrecios();
        var primera=servicio.consultar(peticionPrecio(ModoExploracion.DESCUBRIR,null,Set.of(Capa.L0),1),AGENTE,sesion);
        var noElegida=filas.stream().filter(f->"12".equals(f.getReferenciaRecurso())).findFirst().orElseThrow();
        assertEquals(11L,primera.grupos().getFirst().candidatos().getFirst().datosPermitidos().get("idEncargo"));
        actuales.set(1,ofertaConPrecio(12,"105000",1,0,2));
        var cambiada=servicio.continuar(primera.continuacion(),AGENTE,sesion);
        assertEquals(EstadoResultado.CONTEXTO_CAMBIADO,cambiada.estado());assertNull(cambiada.total());assertTrue(cambiada.grupos().isEmpty());
        verify(referencias).retirar(AGENTE.idOrganizacion(),noElegida.getIdRecorrido(),noElegida.getOrdinal(),CORTE);
        var actualizada=servicio.consultar(peticionPrecio(ModoExploracion.DESCUBRIR,null,Set.of(Capa.L0),20),AGENTE,sesion);
        assertEquals(12L,actualizada.grupos().getFirst().candidatos().getFirst().datosPermitidos().get("idEncargo"));
        actuales.set(1,ofertaConPrecio(12,"115000",1,0,3));
        assertEquals(EstadoResultado.CONTEXTO_CAMBIADO,servicio.continuar(primera.referenciaConsulta(),AGENTE,sesion).estado(),
                "El retorno A→B→A no rehabilita el universo original que ya tuvo una retirada material.");
    }
    @Test void explicarInvalidaElUniversoPorUnaNoElegidaYConservaSuRetirada() {
        var actuales=configurarPrecios();
        var primera=servicio.consultar(peticionPrecio(ModoExploracion.DESCUBRIR,null,Set.of(Capa.L0),20),AGENTE,sesion);
        var grupo=primera.grupos().getFirst();var ofrecida=grupo.candidatos().getFirst();
        actuales.set(1,ofertaConPrecio(12,"105000",1,0,2));
        var cambio=assertThrows(ConflictoException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),ofrecida.referencia(),AGENTE,sesion));
        assertFalse(revierteExplicacion(cambio),"El desenlace de navegación conserva la retirada ya revalidada.");
        actuales.set(1,ofertaConPrecio(12,"115000",1,0,3));
        var retorno=assertThrows(ConflictoException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),ofrecida.referencia(),AGENTE,sesion));
        assertFalse(revierteExplicacion(retorno));
    }
    @Test void retiradaMaterialYFalloDeOtraCapaConservanLaMarcaSinEximirAlFalloTecnicoSolo() {
        var actuales=configurarPrecios();var base=ofertaConPrecio(21,"140000",1,0,1);
        var refPublica="network-TEST-21";var oferta=new Oferta(refPublica,new ClaveCandidato("L2",refPublica,base.oferta().clave().configuracion()),
                1,true,Conclusion.CUMPLE,confirmacion(),base.oferta().datos());
        var publicada=new OfertasNetworkParaMatching.Candidata(new OfertasNetworkParaMatching.Referencia(refPublica,1,"e".repeat(64)),oferta,Map.of("tipo","D"));
        when(network.candidatas(eq(9L),eq(7L),eq(AGENTE),any())).thenReturn(List.of(publicada));
        when(network.revalidar(eq(9L),eq(7L),anyList(),eq(AGENTE),any())).thenReturn(List.of(publicada));
        var primera=servicio.consultar(peticionPrecio(ModoExploracion.DESCUBRIR,null,Set.of(Capa.L0,Capa.L2),20),AGENTE,sesion);
        var grupo=primera.grupos().getFirst();var ofrecida=grupo.candidatos().getFirst();
        when(network.revalidar(eq(9L),eq(7L),anyList(),eq(AGENTE),any())).thenThrow(new DataAccessResourceFailureException("L2 TEST caída al revalidar"));
        var tecnico=assertThrows(IllegalStateException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),ofrecida.referencia(),AGENTE,sesion));
        assertTrue(revierteExplicacion(tecnico));verify(referencias,never()).retirar(anyLong(),any(),anyInt(),any());
        actuales.set(1,ofertaConPrecio(12,"105000",1,0,2));
        var cambio=assertThrows(ConflictoException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),ofrecida.referencia(),AGENTE,sesion));
        assertFalse(revierteExplicacion(cambio));
        var noElegida=filas.stream().filter(f->"12".equals(f.getReferenciaRecurso())).findFirst().orElseThrow();
        verify(referencias).retirar(AGENTE.idOrganizacion(),noElegida.getIdRecorrido(),noElegida.getOrdinal(),CORTE);
        actuales.set(1,ofertaConPrecio(12,"115000",1,0,3));
        var retorno=assertThrows(ConflictoException.class,()->servicio.explicar(primera.referenciaConsulta(),grupo.referenciaDemanda(),ofrecida.referencia(),AGENTE,sesion));
        assertFalse(revierteExplicacion(retorno));
    }
    @Test void explorarConservaHOriginalLimiteYPaginacionSUYNoIncorporaOfertasPosteriores() {
        var actuales=configurarPrecios();
        var primera=servicio.consultar(peticionPrecio(ModoExploracion.EXPLORAR,"120000",Set.of(Capa.L0),1),AGENTE,sesion);
        assertEquals(3L,primera.total());var uno=primera.grupos().getFirst().candidatos().getFirst();
        assertEquals(13L,uno.datosPermitidos().get("idEncargo"));assertEquals(2,uno.s());assertEquals(2,uno.w());
        assertEquals(Conclusion.CUMPLE,uno.g());assertEquals(Conclusion.INCUMPLE,uno.h());assertEquals(Categoria.INCOMPATIBLE,uno.categoria());
        assertEquals(0,uno.alternativaPrecio().maximoOriginal().compareTo(new java.math.BigDecimal("100000")));
        assertEquals(0,uno.alternativaPrecio().excesoAbsoluto().compareTo(new java.math.BigDecimal("20000")));
        actuales.add(ofertaConPrecio(19,"102000",1,1,1));
        var segunda=servicio.continuar(primera.continuacion(),AGENTE,sesion);assertEquals(11L,segunda.grupos().getFirst().candidatos().getFirst().datosPermitidos().get("idEncargo"));
        var tercera=servicio.continuar(segunda.continuacion(),AGENTE,sesion);assertEquals(12L,tercera.grupos().getFirst().candidatos().getFirst().datosPermitidos().get("idEncargo"));assertNull(tercera.continuacion());
        assertEquals(primera.corte(),tercera.corte());assertEquals(3L,tercera.total());
        assertEquals(0,tercera.seleccion().exploracion().limite().compareTo(new java.math.BigDecimal("120000")));
        assertEquals(CORTE.plus(Duration.ofMinutes(15)),guardados.values().iterator().next().getVenceEn());
        assertEquals(3,filas.size());assertNull(guardados.values().iterator().next().getHuellaEscalones());
    }
    @Test void modoAlternativasValidaPresupuestoYLimiteAntesDeRecuperarElUniverso() {
        configurarPrecios();clearInvocations(cartera,network,internas,referencias,recorridos);
        assertThrows(ReglaNegocioException.class,()->servicio.consultar(peticionPrecio(ModoExploracion.EXPLORAR,"99000",Set.of(Capa.L0),20),AGENTE,sesion));
        verifyNoInteractions(cartera,network,internas,referencias,recorridos);
        when(demandas.paraMatching(eq(9L),anyLong(),any(),anyString(),any(),eq(AGENTE)))
                .thenAnswer(i->consultaNormalizada(9,i.getArgument(1),i.getArgument(2),i.getArgument(3),i.getArgument(4),CoberturaCriterios.COMPLETA));
        assertThrows(ReglaNegocioException.class,()->servicio.consultar(peticionPrecio(ModoExploracion.DESCUBRIR,null,Set.of(Capa.L0),20),AGENTE,sesion));
        verifyNoInteractions(cartera,network,internas,referencias,recorridos);
    }
    @Test void exploracionNoAutorizaInversaNiPreliminarNiUnaCategoriaConfirmada() {
        var contexto=new ExploracionPrecio(ModoExploracion.DESCUBRIR,null,null);
        for(var q:List.of(
                new CoincidenciaService.Consulta(Origen.ENCARGO,11L,1L,TipoVersion.ENCARGO,Modalidad.COMERCIAL,Set.of(Capa.L0),Categoria.INCOMPATIBLE,20,contexto),
                new CoincidenciaService.Consulta(Origen.DEMANDA,9L,7L,TipoVersion.DEMANDA,Modalidad.PRELIMINAR,Set.of(Capa.L0),Categoria.INCOMPATIBLE,20,contexto),
                new CoincidenciaService.Consulta(Origen.DEMANDA,9L,7L,TipoVersion.DEMANDA,Modalidad.COMERCIAL,Set.of(Capa.L0),Categoria.COMERCIAL_CONFIRMADA,20,contexto)))
            assertThrows(ReglaNegocioException.class,()->servicio.consultar(q,AGENTE,sesion));
        verifyNoInteractions(demandas,cartera,network,internas,recorridos,referencias);
    }
    @Test void consultaOrdinariaConservaSuCategoriaSinEtiquetarIncompatiblesComoAlternativas() {
        configurarPrecios();
        var ordinaria=new CoincidenciaService.Consulta(Origen.DEMANDA,9L,7L,TipoVersion.DEMANDA,Modalidad.COMERCIAL,Set.of(Capa.L0),Categoria.INCOMPATIBLE,20,null);
        var resultado=servicio.consultar(ordinaria,AGENTE,sesion);
        assertNull(resultado.seleccion().exploracion());assertEquals(6L,resultado.total());
        assertTrue(resultado.grupos().getFirst().candidatos().stream().allMatch(c->c.alternativaPrecio()==null));
        assertTrue(resultado.grupos().getFirst().candidatos().stream().anyMatch(c->Objects.equals(15L,c.datosPermitidos().get("idEncargo"))));
    }
    private List<Candidata> configurarPrecios() {
        var actuales=new ArrayList<>(List.of(ofertaConPrecio(11,"110000",1,0,1),ofertaConPrecio(12,"115000",1,0,1),
                ofertaConPrecio(13,"120000",1,1,1),ofertaConPrecio(14,"130000",1,1,1),ofertaConPrecio(15,"105000",0,1,1),ofertaConPrecio(16,"125000",1,null,1)));
        when(demandas.paraMatching(eq(9L),anyLong(),any(),anyString(),any(),eq(AGENTE)))
                .thenAnswer(i->consultaEconomica(9,i.getArgument(1),i.getArgument(2),i.getArgument(3),i.getArgument(4)));
        when(cartera.referencias(AGENTE)).thenAnswer(i->actuales.stream().map(c->Long.valueOf(c.referenciaInterna())).toList());
        when(cartera.encargos(anyCollection(),eq(AGENTE),any())).thenAnswer(i->{Collection<Long> ids=i.getArgument(0);
            return actuales.stream().filter(c->ids.contains(Long.valueOf(c.referenciaInterna()))).toList();});
        return actuales;
    }
    private static CoincidenciaService.Consulta peticionPrecio(ModoExploracion modo,String limite,Set<Capa> capas,int tamano) {
        return new CoincidenciaService.Consulta(Origen.DEMANDA,9L,7L,TipoVersion.DEMANDA,Modalidad.COMERCIAL,capas,Categoria.INCOMPATIBLE,tamano,
                new ExploracionPrecio(modo,limite==null?null:new java.math.BigDecimal(limite),limite==null?null:"PEN"));
    }
    private static ContratoMatching.Consulta consultaEconomica(long id,long version,Modalidad modo,String regla,Instant corte) {
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
        var precio=new Condicion(PRECIO_VENTA,TipoDato.IMPORTE,OperadorCriterio.MAXIMO,ValorLogico.deImporte(new java.math.BigDecimal("100000"),"PEN"),null,false,
                new ContextoValor("PRECIO_VENTA",null,"PEN",null,"VENTA",null),meta);
        var contexto=new ContextoValor(null,null,null,null,"VENTA",null);
        var ascensor=new Condicion("PROPIEDAD:ascensores",TipoDato.ENTERO,OperadorCriterio.MINIMO,ValorLogico.deNumero(java.math.BigDecimal.ONE),null,false,contexto,meta);
        var cochera=new Condicion("PROPIEDAD:estacionamientos",TipoDato.ENTERO,OperadorCriterio.MINIMO,ValorLogico.deNumero(java.math.BigDecimal.ONE),null,false,contexto,meta);
        return new ContratoMatching.Consulta("demanda:"+id,version,modo,List.of(new Criterio("base:presupuesto","base:presupuesto",Exigencia.OBLIGATORIO,null,List.of(precio)),
                new Criterio("ascensores","ascensores",Exigencia.OBLIGATORIO,null,List.of(ascensor)),new Criterio("cocheras","cocheras",Exigencia.PREFERENCIA,Prioridad.NORMAL,List.of(cochera))),corte,regla);
    }
    private static Candidata ofertaConPrecio(long id,String precio,int ascensores,Integer cocheras,long version) {
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);var config="UNIDAD:"+id;
        List<Dato> datos=new ArrayList<>();
        datos.add(new Dato(PRECIO_VENTA,TipoDato.IMPORTE,ValorLogico.deImporte(new java.math.BigDecimal(precio),"PEN"),null,false,
                new ContextoValor("PRECIO_VENTA",null,"PEN",null,"VENTA",config),meta));
        datos.add(new Dato("PROPIEDAD:ascensores",TipoDato.ENTERO,ValorLogico.deNumero(java.math.BigDecimal.valueOf(ascensores)),null,false,new ContextoValor(null,null,null,null,"VENTA",config),meta));
        if(cocheras!=null)datos.add(new Dato("PROPIEDAD:estacionamientos",TipoDato.ENTERO,ValorLogico.deNumero(java.math.BigDecimal.valueOf(cocheras)),null,false,new ContextoValor(null,null,null,null,"VENTA",config),meta));
        var oferta=new Oferta(String.format(Locale.ROOT,"%064d",id),new ClaveCandidato("1","ENCARGO:"+id,config),version,true,Conclusion.CUMPLE,confirmacion(),datos);
        return new Candidata("ENCARGO",Long.toString(id),version,3L,oferta,Map.of("idEncargo",id,"codigoEncargo","TEST-"+id));
    }
    private static CoincidenciaService.Consulta peticion(Set<Capa> capas){return new CoincidenciaService.Consulta(Origen.DEMANDA,9L,7L,TipoVersion.DEMANDA,Modalidad.COMERCIAL,capas,Categoria.COMERCIAL_CONFIRMADA,null,null);}
    private static RequerimientoService.ReferenciaDemanda ref(long version){return new RequerimientoService.ReferenciaDemanda(9,version,3,"A",null,null);}
    private static RequerimientoService.ContextoDisponibilidadDemanda meta(long version){return new RequerimientoService.ContextoDisponibilidadDemanda(9,version,OperacionInmobiliaria.VENTA,3,"A",1,null,null);}
    private static ConfirmacionDisponibilidad confirmacion(){return new ConfirmacionDisponibilidad(CORTE.minusSeconds(3600),false,"HECHO_TEST");}
    private static ContratoMatching.Consulta consultaNormalizada(long id,long version,Modalidad modo,String regla,Instant corte,CoberturaCriterios cobertura) {
        var ctx=new ContextoValor(null,null,null,null,"VENTA",null);var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
        var condicion=new Condicion("PROPIEDAD:ascensores",TipoDato.ENTERO,OperadorCriterio.MINIMO,ValorLogico.deNumero(java.math.BigDecimal.ONE),null,false,ctx,meta);
        return new ContratoMatching.Consulta("demanda:"+id,version,modo,List.of(new Criterio("ascensores","ascensores",Exigencia.OBLIGATORIO,null,List.of(condicion))),corte,regla,cobertura);
    }
    private static Candidata candidata(boolean ascensor,long version) {
        var ctx=new ContextoValor(null,null,null,null,"VENTA","UNIDAD:12");var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
        var dato=new Dato("PROPIEDAD:ascensores",TipoDato.ENTERO,ValorLogico.deNumero(ascensor?java.math.BigDecimal.ONE:java.math.BigDecimal.ZERO),null,false,ctx,meta);
        var oferta=new Oferta("OFERTA_OPACA",new ClaveCandidato("1","ENCARGO:11","UNIDAD:12"),version,true,Conclusion.CUMPLE,confirmacion(),List.of(dato));
        return new Candidata("ENCARGO","11",version,3L,oferta,Map.of("idEncargo",11L,"codigoEncargo","TEST-11"));
    }
    private static Candidata ofertaParaRecorrido(long id,long responsable,int ascensores,long version) {
        var contexto=new ContextoValor(null,null,null,null,"VENTA","UNIDAD:"+id);
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
        var dato=new Dato("PROPIEDAD:ascensores",TipoDato.ENTERO,ValorLogico.deNumero(java.math.BigDecimal.valueOf(ascensores)),null,false,contexto,meta);
        var oferta=new Oferta(String.format(Locale.ROOT,"%064d",id),new ClaveCandidato("1","ENCARGO:"+id,"UNIDAD:"+id),version,true,Conclusion.CUMPLE,confirmacion(),List.of(dato));
        return new Candidata("ENCARGO",Long.toString(id),version,responsable,oferta,Map.of("idEncargo",id,"codigoEncargo","TEST-"+id));
    }
    private static ContratoMatching.Consulta consultaConPreferencia(long id,long version,int minimo,Modalidad modo,String regla,Instant corte) {
        var contexto=new ContextoValor(null,null,null,null,"VENTA",null);
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
        var condicion=new Condicion("PROPIEDAD:ascensores",TipoDato.ENTERO,OperadorCriterio.MINIMO,ValorLogico.deNumero(java.math.BigDecimal.valueOf(minimo)),null,false,contexto,meta);
        return new ContratoMatching.Consulta("demanda:"+id,version,modo,List.of(new Criterio("ascensores","ascensores",Exigencia.PREFERENCIA,Prioridad.NORMAL,List.of(condicion))),corte,regla);
    }
    private static OfertasNetworkParaMatching.Candidata publicadaParaRecorrido(long id) {
        var base=ofertaParaRecorrido(id,4,1,1);var referencia="network-TEST-"+id;
        var oferta=new Oferta(referencia,new ClaveCandidato("L2",referencia,base.oferta().clave().configuracion()),1,true,Conclusion.CUMPLE,confirmacion(),base.oferta().datos());
        return new OfertasNetworkParaMatching.Candidata(new OfertasNetworkParaMatching.Referencia(referencia,1,"d".repeat(64)),oferta,Map.of("PROPIEDAD:ascensores",Map.of("valor",1)));
    }
    private static boolean revierteExplicacion(Throwable desenlace) {
        var metodo=assertDoesNotThrow(()->CoincidenciaServiceImpl.class.getMethod("explicar",String.class,String.class,String.class,Actor.class,ContextoSesion.class));
        var politica=new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource().getTransactionAttribute(metodo,CoincidenciaServiceImpl.class);
        assertNotNull(politica);return politica.rollbackOn(desenlace);
    }
    private static ContextosService.SesionAccesoDTO dto(Actor actor,String sid){return new ContextosService.SesionAccesoDTO(sid,1,
            new ContextosService.IdentidadAutenticada(actor.idPersona()+100,"test"+actor.idPersona(),"test"+actor.idPersona()+"@example.invalid","TEST"),new ContextosService.Contexto("CTX",actor.idOrganizacion(),actor.idPersona(),actor.idRolOperativo(),actor.rolEfectivo(),"EMPRESARIAL","TEST"),
            CORTE.minusSeconds(60),CORTE.plusSeconds(1800),new EstadoDeAcceso(null,false,false,actor.rolEfectivo()));}
    private static final class Reloj extends Clock {
        Instant actual=CORTE;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zona){return this;}public Instant instant(){return actual;}
    }
}
