package com.controllocal.service.impl;

import com.controllocal.domain.comercial.*;
import com.controllocal.domain.persona.DetalleAgente;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.*;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.actividad.ContextosDeInteraccion.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** C4 sustituye defaults/funnel v1; conserva privacidad, hechos, fuente y lectura histórica exacta. */
class InteraccionServiceImplTest {
    private static final Actor AG=new Actor(1,101,201,Actor.AGENTE);
    private static final Procedencia PROC=Procedencia.deLaPantalla();
    private static final Instant AHORA=Instant.parse("2026-10-30T15:00:00Z");
    private final InteraccionComercialRepository repo=mock(InteraccionComercialRepository.class);
    private final HistoriaInteraccionRepository historia=mock(HistoriaInteraccionRepository.class);
    private final VinculosInteraccionRepository vinculos=mock(VinculosInteraccionRepository.class);
    private final LecturasInteraccionRepository lecturas=mock(LecturasInteraccionRepository.class);
    private final DetalleAgenteRepository agentes=mock(DetalleAgenteRepository.class);
    private final PersonaRolRepository roles=mock(PersonaRolRepository.class);
    private final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    private final ContextosDeInteraccion acceso=mock(ContextosDeInteraccion.class);
    private final SeguimientoDeProspeccion seguimiento=mock(SeguimientoDeProspeccion.class);
    private final RecibosDeActividad recibos=mock(RecibosDeActividad.class);
    private final Alcances alcances=mock(Alcances.class);
    private final EntityManager entidades=mock(EntityManager.class);
    private final Documentos documentos=new Documentos(new ObjectMapper().findAndRegisterModules());
    private final ContextoSesion sid=mock(ContextoSesion.class);
    private final InteraccionServiceImpl servicio=conLector(documentos);
    private final Autorizacion autoridad=new Autorizacion(201,List.of(),Map.of(),List.of(),null);
    @BeforeEach void base(){
        when(contexto.ahora()).thenReturn(AHORA);when(alcances.de(AG)).thenReturn(new Alcances.Alcance(1,false,List.of(201L)));
        when(acceso.normalizar(any(),eq(AG),nullable(Procedencia.class),anyBoolean())).thenAnswer(i->new Normalizado(i.getArgument(0),autoridad,List.of()));
        when(acceso.verificar(any(),eq(AG),anyBoolean())).thenReturn(List.of());
        when(acceso.verificarLote(anyList(),eq(AG),eq(true))).thenAnswer(i->{Map<Autorizacion,List<VinculoFicha>> m=new LinkedHashMap<>();for(var a:(List<Autorizacion>)i.getArgument(0))m.put(a,List.of());return m;});
        var a=new DetalleAgente();ReflectionTestUtils.setField(a,"id",201L);when(agentes.getReferenceById(201L)).thenReturn(a);
        when(repo.saveAndFlush(any())).thenAnswer(i->{InteraccionComercial x=i.getArgument(0);ReflectionTestUtils.setField(x,"id",71L);return x;});
    }
    @Test void sinDatosNoFabricaUnaComunicacion(){assertThrows(ReglaNegocioException.class,()->servicio.registrar(null,AG,sid,PROC));verify(repo,never()).saveAndFlush(any());}
    @Test void capturaRealParcialNoExigeResultadoCanalNiInterlocutor(){
        var f=servicio.registrar(datos(null,null,null,null),AG,sid,PROC);
        assertNull(f.datos().resultado());assertNull(f.datos().canal());assertNull(f.datos().interlocutor());assertNull(f.fechaHecho());
        assertEquals(AHORA,f.registradoEn());verify(contexto).iniciar(AG,sid);verify(historia).anexar(eq(1L),eq(71L),eq(0L),eq(201L),eq(AHORA),eq("REGISTRAR"),isNull(),anyString(),anyString());
    }
    @Test void laDefinicionDeGobiernoNoAbreCartera(){
        var gov=new Actor(1,102,202,Actor.TENANT_ADMIN);assertEquals(3,servicio.definicion(gov).tipos().size());
        verify(contexto).exigirMetadata(gov);verifyNoInteractions(repo,historia,acceso);
    }
    @Test void unContextoAusenteNoSeConvierteEnOportunidad(){
        var f=servicio.registrar(datos(null,null,null,null),AG,sid,PROC);assertTrue(f.datos().vinculos().isEmpty());assertTrue(f.vinculos().isEmpty());
    }
    @Test void elInmuebleNoSeConvierteEnInterlocutorPropietario(){
        var d=conVinculos(List.of(new VinculoActividad(TipoContexto.PROPIEDAD,9L,null,null)));
        var f=servicio.registrar(d,AG,sid,PROC);assertNull(f.datos().interlocutor());assertNull(f.datos().destinatario());
    }
    @Test void unDestinatarioLiteralRecibeReferenciaContextualSinPersonaNiFusionPorTelefono(){
        var destinatario=new ParticipanteDeclarado(null,"PROPIETARIO","Ana","999999999",null);
        var d=new DatosInteraccion(List.of(),destinatario,null,null,null,null,List.of(),null,null,null,List.of(),null,null,"Cartel",null);
        var primero=servicio.registrar(d,AG,sid,PROC);var segundo=servicio.registrar(d,AG,sid,PROC);
        assertNotNull(primero.datos().destinatario().referenciaDeclarada());
        assertNotEquals(primero.datos().destinatario().referenciaDeclarada(),segundo.datos().destinatario().referenciaDeclarada());
        assertNull(primero.datos().destinatario().contacto());assertEquals("999999999",primero.datos().destinatario().telefono());
        assertNull(d.destinatario().referenciaDeclarada());
    }
    @Test void unaSesionRevocadaSeRechazaAntesDeEscribirOLeerRecibos(){
        doThrow(new AccesoNoAutorizadoException()).when(contexto).iniciar(AG,sid);
        assertThrows(AccesoNoAutorizadoException.class,()->servicio.registrar(datos(null,null,null,null),AG,sid,PROC));
        verifyNoInteractions(repo,historia,vinculos,recibos);
    }
    @Test void faltaDePermisoNoSeConfundeConUnResultadoVacioNiSeGuarda(){
        when(acceso.normalizar(any(),eq(AG),isNull(),eq(false))).thenThrow(new NoEncontradoException("Propiedad"));
        assertThrows(NoEncontradoException.class,()->servicio.registrar(conVinculos(List.of(new VinculoActividad(TipoContexto.PROPIEDAD,99L,null,null))),AG,sid,PROC));
        verify(repo,never()).saveAndFlush(any());
    }
    @Test void declaracionMonetariaConocidaSinMonedaSeConserva(){
        var cifra=ValorLogico.deNumero(new java.math.BigDecimal("95000"));var x=new Declaracion("Ofrece95000","PRECIO",cifra,null,null,null,null,"Propietario",null);
        var d=new DatosInteraccion(List.of(),null,null,null,null,null,List.of(),null,null,null,List.of(x),null,null,null,null);
        assertEquals(cifra,servicio.registrar(d,AG,sid,PROC).datos().declaraciones().getFirst().valorDeclarado());
    }
    @Test void dosMonedasConocidasContradictoriasNoSeEligenSilenciosamente(){
        var x=new Declaracion(null,"PRECIO",ValorLogico.deImporte(java.math.BigDecimal.TEN,"USD"),null,"PEN",null,null,null,null);
        var d=new DatosInteraccion(List.of(),null,null,null,null,null,List.of(),null,null,null,List.of(x),null,null,null,null);
        assertThrows(ReglaNegocioException.class,()->servicio.validarRegistro(d,AG));verifyNoInteractions(repo);
    }
    @Test void niEvidenciaNiTextoConfirmanPorSiSolosUnDatoFisico(){
        var x=new Declaracion("Declaraárea","AREA",ValorLogico.deNumero(java.math.BigDecimal.TEN),"M2",null,null,null,"Declaración",
                new ConfirmacionContextual("CONFIRMADA","propiedad:9:area",1L,"Documento citado"));
        var d=new DatosInteraccion(List.of(),null,null,null,null,null,List.of(),null,null,null,List.of(x),null,null,null,null);
        assertThrows(ReglaNegocioException.class,()->servicio.validarRegistro(d,AG));
    }
    @Test void casAusenteNoEsVersionCero(){
        assertThrows(ReglaNegocioException.class,()->servicio.validarRectificacion(new Rectificacion(71,null,datos(null,null,null,null),"Corrección"),AG));
        assertThrows(ReglaNegocioException.class,()->servicio.vincular(71,new AdicionVinculo(null,new VinculoActividad(TipoContexto.PROPIEDAD,9L,null,null),"Relacionar"),AG,sid,PROC));
        assertThrows(ReglaNegocioException.class,()->servicio.observar(71,new ObservacionPosterior(null,null,"Se conoció rebote","Correo",ResultadoInteraccion.COMUNICACION_FALLIDA),AG,sid,PROC));
        verify(repo,never()).bloquear(anyLong(),anyLong());
    }
    @Test void laVersionHistoricaSeAutorizaAntesDeHidratarElSnapshot(){
        when(historia.acceso(1,71,0)).thenReturn(Optional.of(documentos.objetoCanonico(autoridad)));
        when(acceso.verificar(autoridad,AG,true)).thenThrow(new NoEncontradoException("Contacto original"));
        assertThrows(NoEncontradoException.class,()->servicio.obtenerVersion(71,0,AG));verify(historia,never()).version(anyLong(),anyLong(),anyLong());
    }
    @Test void unaVersionAusenteNoSeReconstruyeDesdeLaActual(){
        when(historia.acceso(1,71,0)).thenReturn(Optional.empty());assertThrows(NoEncontradoException.class,()->servicio.obtenerVersion(71,0,AG));verifyNoInteractions(repo);
    }
    @Test void elSnapshotDebeCorresponderExactamenteALaVersionAutorizada(){
        when(historia.acceso(1,71,0)).thenReturn(Optional.of(documentos.objetoCanonico(autoridad)));
        var equivocada=ficha(72,0,datos(null,null,null,null));
        when(historia.version(1,71,0)).thenReturn(Optional.of(new HistoriaInteraccionRepository.Version(0,AHORA,"REGISTRAR",null,
                documentos.objetoCanonico(new InteraccionServiceImpl.Snapshot(equivocada,autoridad)),documentos.objetoCanonico(PROC))));
        assertThrows(IllegalStateException.class,()->servicio.obtenerVersion(71,0,AG));
    }
    @Test void buscarYContarNoRecibeIdentidadesConContactoNoLegible(){
        var denegada=new Autorizacion(201,List.of(new VinculoActividad(TipoContexto.PROPIEDAD,99L,null,null)),Map.of(),List.of(),null);
        when(lecturas.cabeceras(1,List.of(201L))).thenReturn(List.of(cabecera(71,denegada),cabecera(72,autoridad),cabecera(73,denegada),cabecera(74,autoridad)));
        doReturn(Map.of(autoridad,List.of())).when(acceso).verificarLote(anyList(),eq(AG),eq(true));
        when(lecturas.buscar(eq(1L),eq(List.of(72L,74L)),isNull(),isNull(),isNull(),eq("dato"),eq(1),eq(10)))
                .thenReturn(new LecturasInteraccionRepository.PaginaIds(List.of(74L),2));
        var permitida=entidad(74,datos(null,null,null,null));
        when(repo.actuales(1,List.of(74L))).thenReturn(List.of(permitida));
        var p=servicio.listar(new FiltrosInteraccion(null,null,null,null,null,"dato",1,10),AG);
        assertEquals(2,p.total());assertEquals(74L,p.items().getFirst().id());verify(lecturas).buscar(1,List.of(72L,74L),null,null,null,"dato",1,10);
        verify(acceso,times(2)).verificarLote(List.of(denegada,autoridad),AG,true);
        verify(repo,never()).actuales(eq(1L),argThat(ids->ids.contains(71L)||ids.contains(73L)));
    }
    @Test void decodificaUnaVezPorLiteralEnCadaPoblacionSinGuardarEntreOperaciones(){
        var lector=spy(documentos);var sujeto=conLector(lector);
        String json=documentos.objetoCanonico(autoridad);
        when(lecturas.cabeceras(1,List.of(201L))).thenReturn(List.of(cabecera(71,0,json),cabecera(72,1,json),cabecera(73,2,json)));
        when(lecturas.buscar(1,List.of(71L,72L,73L),null,null,null,null,1,2))
                .thenReturn(new LecturasInteraccionRepository.PaginaIds(List.of(73L,72L),3));
        var segunda=entidad(72,datos(null,null,null,null));ReflectionTestUtils.setField(segunda,"version",1L);
        var tercera=entidad(73,datos(null,null,null,null));ReflectionTestUtils.setField(tercera,"version",2L);
        when(repo.actuales(1,List.of(73L,72L))).thenReturn(List.of(segunda,tercera));
        var f=new FiltrosInteraccion(null,null,null,null,null,null,1,2);
        var primera=sujeto.listar(f,AG);
        assertEquals(3,primera.total());assertEquals(List.of(73L,72L),primera.items().stream().map(FichaInteraccion::id).toList());
        assertEquals(List.of(2L,1L),primera.items().stream().map(FichaInteraccion::version).toList());
        verify(lector,times(2)).comoObjeto(json,Autorizacion.class);
        verify(acceso,times(2)).verificarLote(List.of(autoridad),AG,true);
        assertEquals(primera,sujeto.listar(f,AG));
        verify(lector,times(4)).comoObjeto(json,Autorizacion.class);
        verify(acceso,times(4)).verificarLote(List.of(autoridad),AG,true);
    }
    @Test void dosLiteralesEquivalentesSeDecodificanSeparadosPeroVerificanUnValor(){
        var lector=spy(documentos);var sujeto=conLector(lector);
        String json=documentos.objetoCanonico(autoridad),espaciado=" "+json+" ";
        when(lecturas.cabeceras(1,List.of(201L))).thenReturn(List.of(cabecera(71,0,json),cabecera(72,0,espaciado)));
        when(lecturas.buscar(1,List.of(71L,72L),null,null,null,null,2,10))
                .thenReturn(new LecturasInteraccionRepository.PaginaIds(List.of(),2));
        var p=sujeto.listar(new FiltrosInteraccion(null,null,null,null,null,null,2,10),AG);
        assertEquals(2,p.total());assertTrue(p.items().isEmpty());
        verify(lector,times(2)).comoObjeto(json,Autorizacion.class);
        verify(lector,times(2)).comoObjeto(espaciado,Autorizacion.class);
        verify(acceso,times(2)).verificarLote(List.of(autoridad),AG,true);
    }
    @Test void hijosConJsonIgualNoPierdenSuAntecedenteNiRevivenSiElPadreNoEsLegible(){
        var hija=new Autorizacion(201,List.of(),Map.of(),List.of(),71L);
        when(lecturas.cabeceras(1,List.of(201L))).thenReturn(List.of(cabecera(71,autoridad),cabecera(72,hija),cabecera(73,hija)));
        doReturn(Map.of(hija,List.of())).when(acceso).verificarLote(anyList(),eq(AG),eq(true));
        when(lecturas.buscar(1,List.of(),null,null,null,null,1,10))
                .thenReturn(new LecturasInteraccionRepository.PaginaIds(List.of(),0));
        var p=servicio.listar(new FiltrosInteraccion(null,null,null,null,null,null,1,10),AG);
        assertEquals(0,p.total());assertTrue(p.items().isEmpty());
        verify(acceso,times(2)).verificarLote(List.of(autoridad,hija),AG,true);
        verify(repo,never()).actuales(anyLong(),anyCollection());
    }
    @Test void permisoRetiradoEnLaSegundaPoblacionNoEntregaElConteoNiLaPaginaCalculados(){
        when(lecturas.cabeceras(1,List.of(201L))).thenReturn(List.of(cabecera(71,autoridad),cabecera(72,autoridad)));
        doReturn(Map.of(autoridad,List.of()),Map.of()).when(acceso).verificarLote(anyList(),eq(AG),eq(true));
        when(lecturas.buscar(1,List.of(71L,72L),null,null,null,null,1,1))
                .thenReturn(new LecturasInteraccionRepository.PaginaIds(List.of(72L),2));
        var permitida=entidad(72,datos(null,null,null,null));
        when(repo.actuales(1,List.of(72L))).thenReturn(List.of(permitida));
        assertThrows(ConflictoException.class,()->servicio.listar(new FiltrosInteraccion(null,null,null,null,null,null,1,1),AG));
        verify(acceso,times(2)).verificarLote(List.of(autoridad),AG,true);
        verify(lecturas,times(2)).cabeceras(1,List.of(201L));
    }
    @Test void cambiarLaVersionDeUnaFilaFueraDePaginaAbortaAunqueSuJsonSigaIgual(){
        var lector=spy(documentos);var sujeto=conLector(lector);
        String json=documentos.objetoCanonico(autoridad);
        when(lecturas.cabeceras(1,List.of(201L)))
                .thenReturn(List.of(cabecera(71,0,json),cabecera(72,0,json)),List.of(cabecera(71,1,json),cabecera(72,0,json)));
        when(lecturas.buscar(1,List.of(71L,72L),null,null,null,null,1,1))
                .thenReturn(new LecturasInteraccionRepository.PaginaIds(List.of(72L),2));
        var permitida=entidad(72,datos(null,null,null,null));
        when(repo.actuales(1,List.of(72L))).thenReturn(List.of(permitida));
        assertThrows(ConflictoException.class,()->sujeto.listar(new FiltrosInteraccion(null,null,null,null,null,null,1,1),AG));
        verify(repo).actuales(1,List.of(72L));verify(acceso,times(2)).verificarLote(List.of(autoridad),AG,true);
        verify(lector,times(2)).comoObjeto(json,Autorizacion.class);
    }
    @Test void legadoNullSeTraduceConSusColumnasPorFilaYNoConLaPrimera(){
        var lector=spy(documentos);var sujeto=conLector(lector);
        var primera=new Cabe(71,0,201,null,null,null,null,null,31L);
        var segunda=new Cabe(72,0,201,null,null,null,null,null,32L);
        when(lecturas.cabeceras(1,List.of(201L))).thenReturn(List.of(primera,segunda));
        when(lecturas.buscar(1,List.of(71L,72L),null,null,null,null,2,10))
                .thenReturn(new LecturasInteraccionRepository.PaginaIds(List.of(),2));
        var p=sujeto.listar(new FiltrosInteraccion(null,null,null,null,null,null,2,10),AG);
        assertEquals(2,p.total());assertTrue(p.items().isEmpty());
        var a=ContextosDeInteraccion.metadatos(primera,documentos);var b=ContextosDeInteraccion.metadatos(segunda,documentos);
        assertNotEquals(a,b);assertEquals(31L,a.vinculos().getFirst().referencia());assertEquals(32L,b.vinculos().getFirst().referencia());
        verify(acceso,times(2)).verificarLote(List.of(a,b),AG,true);
        verify(lector,never()).comoObjeto(anyString(),eq(Autorizacion.class));
    }
    @Test void diccionarioActualSinJsonNoSeReinterpretaComoLegadoNiPaginaVacia(){
        when(lecturas.cabeceras(1,List.of(201L))).thenReturn(List.of(new Cabe(71,0,201,null,CatalogoInteracciones.VERSION,null,null,null,31L)));
        assertThrows(IllegalStateException.class,()->servicio.listar(new FiltrosInteraccion(null,null,null,null,null,null,1,10),AG));
        verify(lecturas).cabeceras(1,List.of(201L));verifyNoMoreInteractions(lecturas);verify(acceso,never()).verificarLote(anyList(),any(),anyBoolean());
    }
    @Test void jsonMalformadoConservaFalloTecnicoSinCachearPermisoVacio(){
        var lector=spy(documentos);var sujeto=conLector(lector);
        when(lecturas.cabeceras(1,List.of(201L))).thenReturn(List.of(cabecera(71,0,"{"),cabecera(72,0,"{")));
        assertThrows(IllegalStateException.class,()->sujeto.listar(new FiltrosInteraccion(null,null,null,null,null,null,1,10),AG));
        verify(lector).comoObjeto("{",Autorizacion.class);
        verify(lecturas).cabeceras(1,List.of(201L));verifyNoMoreInteractions(lecturas);verify(acceso,never()).verificarLote(anyList(),any(),anyBoolean());
    }
    @Test void indicadoresNoHidrataResumenParticipantesNiNotas(){
        when(lecturas.cabeceras(1,List.of(201L))).thenReturn(List.of(cabecera(71,autoridad)));
        when(lecturas.mediciones(1,Set.of(71L))).thenReturn(List.of(new LecturasInteraccionRepository.Medicion(71,0,201,LocalDate.of(2026,10,30),AHORA,"LLAMADA",null,"{\"eventos\":[]}")));
        var m=servicio.indicadores(AG).registros();assertEquals(1,m.size());assertNull(m.getFirst().resultado());verify(repo,never()).actuales(anyLong(),anyCollection());
    }
    @Test void falloTecnicoNoSeConvierteEnListaVacia(){
        when(lecturas.cabeceras(1,List.of(201L))).thenThrow(new IllegalStateException("DB indisponible"));
        assertThrows(IllegalStateException.class,()->servicio.listar(new FiltrosInteraccion(null,null,null,null,null,null,1,10),AG));
    }
    @Test void legadoConservaResultadoCanalNotaYFechaRawSinInferirTipoNiFechaHecho(){
        var i=entidad(71,datos(null,null,null,null));i.setVersionDiccionario(null);i.setAutorizacionJson(null);i.setContexto("PROSPECCION");
        i.setResultado("INTERESADO");i.setCanalContacto("W");i.setObservaciones("Nota conocida");i.setTranscripcionNota("Transcripción original");
        var fecha=OffsetDateTime.parse("2026-01-02T08:20:00-05:00");i.setFechaHora(fecha);
        var h=new Cabe(71,0,201,null,null,null,null,null,null);when(repo.cabecera(1,71,List.of(201L))).thenReturn(Optional.of(h));
        when(repo.actual(1,71)).thenReturn(Optional.of(i));var f=servicio.obtener(71,AG);
        assertEquals("LEGADO",f.versionDiccionario());assertEquals("INTERESADO",f.resultadoHistorico());assertEquals("W",f.canalHistorico());
        assertEquals("Transcripción original",f.datosLegados().transcripcionNota());assertEquals(fecha,f.datosLegados().fechaHora());
        assertNull(f.fechaHecho());assertNull(f.datos().tipo());assertNull(f.datos().interlocutor());assertNull(f.agenteNombre());
    }
    private InteraccionServiceImpl conLector(Documentos lector){return new InteraccionServiceImpl(repo,historia,vinculos,lecturas,agentes,roles,
            contexto,acceso,seguimiento,recibos,alcances,lector,entidades,new PlanDeConsulta());}
    private InteraccionComercial entidad(long id,DatosInteraccion datos){
        var i=new InteraccionComercial();i.setOrganizacionId(1L);ReflectionTestUtils.setField(i,"id",id);i.setAgente(agentes.getReferenceById(201L));
        i.setVersionDiccionario(CatalogoInteracciones.VERSION);i.setRegistradoEn(AHORA);i.setAutorizacionJson(documentos.objetoCanonico(autoridad));
        i.setContextoDeclaradoJson(documentos.objetoCanonico(new InteraccionServiceImpl.Contenido(datos,List.of(),"Agente TEST",List.of())));return i;
    }
    private FichaInteraccion ficha(long id,long version,DatosInteraccion d){return new FichaInteraccion(id,version,201,null,AHORA,d,List.of(),CatalogoInteracciones.VERSION,PROC,null,null,"Agente TEST",List.of(),null,null);}
    private Cabe cabecera(long id,Autorizacion a){return new Cabe(id,0,201,documentos.objetoCanonico(a),CatalogoInteracciones.VERSION,null,null,null,null);}
    private Cabe cabecera(long id,long version,String json){return new Cabe(id,version,201,json,CatalogoInteracciones.VERSION,null,null,null,null);}
    private record Cabe(long id,long version,long agente,String autorizacion,String diccionario,Long oportunidad,Long prospeccion,Long encargo,Long cliente) implements InteraccionComercialRepository.Cabecera{
        public long getId(){return id;}public long getVersion(){return version;}public long getAgente(){return agente;}public String getAutorizacion(){return autorizacion;}public String getDiccionario(){return diccionario;}
        public Long getOportunidad(){return oportunidad;}public Long getProspeccion(){return prospeccion;}public Long getEncargo(){return encargo;}public Long getCliente(){return cliente;}
    }
    private static DatosInteraccion datos(TipoActividad tipo,CanalComunicacion canal,ResultadoInteraccion resultado,List<EventoDeclarado> eventos){return new DatosInteraccion(List.of(),null,null,tipo,canal,null,eventos,resultado,null,null,List.of(),null,null,null,null);}
    private static DatosInteraccion conVinculos(List<VinculoActividad> links){return new DatosInteraccion(links,null,null,null,null,null,List.of(),null,null,null,List.of(),null,null,null,null);}
}
