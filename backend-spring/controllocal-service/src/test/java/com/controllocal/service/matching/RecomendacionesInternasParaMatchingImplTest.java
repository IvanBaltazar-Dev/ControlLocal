package com.controllocal.service.matching;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.CoincidenciaService.*;
import com.controllocal.service.captura.SuficienciaInmueble;
import com.controllocal.service.captura.SuficienciaRegistrada;
import com.controllocal.service.encargo.EncargoService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.controllocal.service.matching.ContratoMatching.*;
import static com.controllocal.service.matching.RecomendacionesInternasParaMatching.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Admisión contextual L1 y mínimos de datos, sin sustituir G ni la autoridad del origen. */
@ExtendWith(MockitoExtension.class)
class RecomendacionesInternasParaMatchingImplTest {
    private static final Actor ACTOR=new Actor(1,11,21,Actor.AGENTE);
    private static final Instant CORTE=Instant.parse("2026-10-29T17:00:00Z");
    private static final LocalDate DIA=LocalDate.of(2026,10,29);
    private static final OrigenReferencia DEMANDA=new OrigenReferencia(Origen.DEMANDA,101,0,TipoVersion.DEMANDA);
    private static final OrigenReferencia ENCARGO=new OrigenReferencia(Origen.ENCARGO,91,3,TipoVersion.ENCARGO);
    @Mock ProyeccionOfertaL1Repository ofertas;
    @Mock ProyeccionDemandaCompartibleRepository proyecciones;
    @Mock RequerimientoClienteRepository actores;
    @Mock RequerimientoService demandas;
    @Mock FuenteCarteraMatching propia;
    @Mock EncargoService encargos;
    @Mock ConfirmacionDisponibilidadContextualRepository confirmaciones;
    @Mock LectorPorAutoridad lector;
    @Mock GobiernoCatalogoCapturaService catalogo;
    private RecomendacionesInternasParaMatchingImpl fuente;
    @BeforeEach void preparar() {
        var mapper=new ObjectMapper().findAndRegisterModules();
        fuente=new RecomendacionesInternasParaMatchingImpl(ofertas,proyecciones,actores,demandas,propia,encargos,confirmaciones,
                new SuficienciaRegistrada(lector,mapper,new SuficienciaInmueble(),catalogo),catalogo,new Documentos(mapper));
    }
    @Test void gobiernoNoRecuperaElUniversoDeOfertasNiCriteriosAjenos() {
        assertThrows(AccesoNoAutorizadoException.class,()->fuente.ofertas(DEMANDA,new Actor(1,11,31,Actor.TENANT_ADMIN),CORTE));
        verifyNoInteractions(ofertas,proyecciones,demandas,propia,encargos,confirmaciones,actores);
    }
    @Test void origenDenegadoSePropagaAntesDeLeerCandidatos() {
        activo();when(demandas.contextoDisponibilidad(101,ACTOR,false)).thenThrow(new AccesoNoAutorizadoException());
        assertThrows(AccesoNoAutorizadoException.class,()->fuente.ofertas(DEMANDA,ACTOR,CORTE));
        verifyNoInteractions(ofertas,proyecciones,encargos,confirmaciones);
    }
    @Test void versionObservadaDelOrigenNoSeRefrescaParaOcultarConflicto() {
        activo();when(demandas.contextoDisponibilidad(101,ACTOR,false)).thenReturn(new RequerimientoService.ContextoDisponibilidadDemanda(101,1,OperacionInmobiliaria.ALQUILER,21,"A",1,null,null));
        assertThrows(ConflictoException.class,()->fuente.ofertas(DEMANDA,ACTOR,CORTE));
        verifyNoInteractions(ofertas,proyecciones,encargos,confirmaciones);
    }
    @Test void demandaInversaEsParcialConResponsableRealSinNombreDeClienteNiPresupuesto() {
        origenEncargo();var base=demanda(4);when(proyecciones.candidatasL1(1,DIA)).thenReturn(List.of(base));
        when(proyecciones.actualesL1(1,List.of(501L),DIA)).thenReturn(List.of(base));
        var r=fuente.demandas(ENCARGO,Modalidad.COMERCIAL,"D1-TEST",CORTE,ACTOR).getFirst();
        assertEquals(CoberturaCriterios.PARCIAL,r.consulta().coberturaCriterios());assertEquals("Compañera TEST",r.responsable().nombre());
        assertEquals(4,r.version());assertNull(r.disponibilidad());
        assertTrue(r.consulta().criterios().stream().flatMap(c->c.alternativas().stream()).noneMatch(a->a.tipo()==com.controllocal.domain.inmueble.TipoDato.IMPORTE));
        verifyNoInteractions(demandas,ofertas);verify(actores,times(2)).contextoOperativoActual(1,11,21,Actor.AGENTE,DIA);
    }
    @Test void disponibilidadNegativaConocidaRetiraLaDemandaAntesDeRecuperarCriterios() {
        origenEncargo();when(proyecciones.candidatasL1(1,DIA)).thenReturn(List.of(demanda(4)));
        when(confirmaciones.hechosParaMatching(1,"DEMANDA",501,"ALQUILER","DISPONIBILIDAD_COMERCIAL","DEMANDA:501")).thenReturn(List.of(hecho(false)));
        assertTrue(fuente.demandas(ENCARGO,Modalidad.COMERCIAL,"D1-TEST",CORTE,ACTOR).isEmpty());
        verify(proyecciones,never()).criterios(anyLong(),anyLong(),anyLong(),anySet());
    }
    @Test void falloSqlNoSeConvierteEnUniversoVacio() {
        origenEncargo();when(proyecciones.candidatasL1(1,DIA)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("Fallo SQL TEST"));
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,()->fuente.demandas(ENCARGO,Modalidad.COMERCIAL,"D1-TEST",CORTE,ACTOR));
    }
    @Test void revalidarDevuelveVersionActualAutorizadaDeLaDemandaConLaMismaCoberturaParcial() {
        origenEncargo();var actual=demanda(5);when(proyecciones.actualesL1(1,List.of(501L),DIA)).thenReturn(List.of(actual));
        var r=fuente.revalidarDemandas(ENCARGO,List.of(501L),Modalidad.COMERCIAL,"D1-TEST",CORTE,ACTOR,CORTE).getFirst();
        assertEquals(5,r.version());assertEquals(CoberturaCriterios.PARCIAL,r.consulta().coberturaCriterios());assertEquals(CORTE,r.consulta().corte());
    }
    @Test void gNoHabilitadoNoLeePrecioOEntregaComercialDeLaConfiguracion() {
        origenDemanda();var base=oferta();oferta(base,false);
        var r=fuente.ofertas(DEMANDA,ACTOR,CORTE).getFirst();assertEquals(Conclusion.INCUMPLE,r.oferta().habilitacion());
        assertFalse(r.camposV1().containsKey("ENCARGO:precio_pedido"));assertFalse(r.camposV1().containsKey("ENCARGO:se_ofrece_amoblado"));
        verify(ofertas,never()).precio(anyLong(),anyLong(),anyLong());verify(ofertas,never()).booleanos(anyLong(),anyLong());
        verify(encargos,never()).consultar(anyLong(),any());verify(encargos,never()).consultarComposicion(anyLong(),any(),any());
    }
    @Test void gHabilitadoUsaPactoActualYNoTotalDeDesgloseParaElPrecio() {
        origenDemanda();var base=oferta();oferta(base,true);
        when(confirmaciones.hechosParaMatching(1,"ENCARGO",104,"ALQUILER","DISPONIBILIDAD_COMERCIAL","UNIDAD:5")).thenReturn(List.of(hecho(true)));
        when(ofertas.precio(1,104,2)).thenReturn(Optional.of(new ProyeccionOfertaL1Repository.Precio(new BigDecimal("2700"),"PEN","MENSUAL",null)));
        var r=fuente.ofertas(DEMANDA,ACTOR,CORTE).getFirst();assertEquals(Conclusion.CUMPLE,r.oferta().habilitacion());
        @SuppressWarnings("unchecked") var precio=(Map<String,Object>)r.camposV1().get("ENCARGO:precio_pedido");
        assertEquals(new BigDecimal("2700"),precio.get("valor"));assertEquals("PRECIO_ALQUILER",precio.get("concepto"));
        assertFalse(precio.containsKey("unidad"),"El monto mensual del objeto conserva su contexto ordinario.");
        verify(encargos,never()).consultarComposicion(anyLong(),any(),any());
    }
    @Test void precioUnitarioDeOtroResponsableConservaUnidadSinRecuperarCargosPrivados() {
        origenDemanda();oferta(oferta(),true);
        when(confirmaciones.hechosParaMatching(1,"ENCARGO",104,"ALQUILER","DISPONIBILIDAD_COMERCIAL","UNIDAD:5")).thenReturn(List.of(hecho(true)));
        when(ofertas.precio(1,104,2)).thenReturn(Optional.of(new ProyeccionOfertaL1Repository.Precio(new BigDecimal("10"),"PEN","MENSUAL","m2")));
        var r=fuente.ofertas(DEMANDA,ACTOR,CORTE).getFirst();
        @SuppressWarnings("unchecked") var precio=(Map<String,Object>)r.camposV1().get("ENCARGO:precio_pedido");
        assertEquals("m2",precio.get("unidad"));assertEquals(new BigDecimal("10"),precio.get("valor"));
        assertTrue(com.controllocal.service.network.PoliticaProyeccionNetworkV1.CAMPOS.containsAll(r.camposV1().keySet()));
        var total=r.oferta().datos().stream().filter(d->d.clave().equals(TOTAL_MENSUAL_OBLIGATORIO)).findFirst().orElseThrow();
        assertNull(total.valor());assertEquals(Causa.UNIDAD_INCOMPATIBLE,total.metadatos().causa());
        verify(ofertas,times(1)).precio(1,104,2);verifyNoInteractions(lector);
        verify(encargos,never()).consultar(anyLong(),any());verify(encargos,never()).consultarComposicion(anyLong(),any(),any());
    }
    @Test void pisoLiteralConTipoTextoRealNoSeConvierteEnUnaCantidad() {
        origenDemanda();var base=oferta();oferta(base,false);
        when(ofertas.fisicos(eq(1L),eq(5L),anySet())).thenReturn(List.of(new ProyeccionOfertaL1Repository.Fisico("piso","TEXTO",null,"3",false,null,null,null,false,null)));
        var r=fuente.ofertas(DEMANDA,ACTOR,CORTE).getFirst();
        var piso=r.oferta().datos().stream().filter(d->d.clave().equals("PROPIEDAD:piso")).findFirst().orElseThrow();
        assertEquals(com.controllocal.domain.inmueble.TipoDato.TEXTO,piso.tipo());assertEquals("3",piso.valor().texto());assertNull(piso.valor().numero());
    }
    private void activo(){when(actores.contextoOperativoActual(1,11,21,Actor.AGENTE,DIA)).thenReturn(true);}
    private void origenEncargo(){activo();when(propia.versionAutorizada(Origen.ENCARGO,91,ACTOR)).thenReturn(3L);}
    private void origenDemanda(){activo();when(demandas.contextoDisponibilidad(101,ACTOR,false)).thenReturn(new RequerimientoService.ContextoDisponibilidadDemanda(101,0,OperacionInmobiliaria.ALQUILER,21,"A",1,null,null));}
    private static ProyeccionDemandaCompartibleRepository.Base demanda(long version){return new ProyeccionDemandaCompartibleRepository.Base(501,version,99,"A","A",null,null,List.of("D"),List.of("7"),null,"Compañera TEST");}
    private static ProyeccionOfertaL1Repository.Base oferta(){return new ProyeccionOfertaL1Repository.Base(104,2,1,5,2,99,"A",null,"D",7L,null,"Compañero TEST");}
    private void oferta(ProyeccionOfertaL1Repository.Base b,boolean habilitada) {
        when(ofertas.candidatas(1,DIA)).thenReturn(List.of(b));when(ofertas.actuales(1,List.of(104L),DIA)).thenReturn(List.of(b));
        when(encargos.habilitacionParaRecomendacionInterna(104,DIA,ACTOR)).thenReturn(new EncargoService.AptitudRecomendacion(104,2,1,5,2,99,"ALQUILER",null,habilitada,habilitada?List.of():List.of("REVISION_APROBATORIA")));
    }
    private static ConfirmacionDisponibilidadContextualRepository.HechoParaMatching hecho(boolean disponible) {
        return new ConfirmacionDisponibilidadContextualRepository.HechoParaMatching(){
            public Long getId(){return 1L;}public long getVersion(){return 1;}public boolean getDisponible(){return disponible;}
            public String getPrecision(){return "INSTANTE";}public Instant getBaseFrescura(){return CORTE.minusSeconds(60);}
            public LocalDate getFechaSinHora(){return null;}public Instant getRegistradoEn(){return CORTE.minusSeconds(60);}
        };
    }
}
