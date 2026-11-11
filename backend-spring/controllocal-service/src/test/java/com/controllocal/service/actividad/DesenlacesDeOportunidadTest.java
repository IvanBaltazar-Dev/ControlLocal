package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository.FuenteDesenlace;
import com.controllocal.service.Actor;
import com.controllocal.service.soporte.*;
import com.controllocal.service.excepcion.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.service.actividad.IndicadoresIndividuales.*;

class DesenlacesDeOportunidadTest {
    private final OportunidadComercialRepository repo=mock(OportunidadComercialRepository.class);
    private final AccesoOportunidad acceso=mock(AccesoOportunidad.class);
    private final Alcances alcances=mock(Alcances.class);
    private final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    private final DesenlacesDeOportunidad sut=new DesenlacesDeOportunidad(repo,acceso,alcances,contexto);
    private final Actor actor=new Actor(1L,3L,30L,"AGENTE");
    private final Instant corte=Instant.parse("2026-11-10T18:00:00Z");
    private final Ventana ventana=new Ventana(LocalDate.of(2026,11,1),LocalDate.of(2026,11,10),corte);
    private FuenteDesenlace fila(long id,String estado,boolean confirmado,Long historico){
        var f=mock(FuenteDesenlace.class);when(f.getId()).thenReturn(id);when(f.getVersion()).thenReturn(3L);
        when(f.getResponsable()).thenReturn(30L);when(f.getResponsableEncargo()).thenReturn(40L);
        when(f.getEstado()).thenReturn(estado);when(f.getOperacion()).thenReturn("V");
        when(f.getResponsableHistorico()).thenReturn(historico);when(f.getConfirmadoC5()).thenReturn(confirmado);
        when(f.getRegistrado()).thenReturn(Instant.parse("2026-11-09T18:00:00Z"));
        when(f.getFechaResolucion()).thenReturn(LocalDate.of(2026,11,8));when(f.getRevisionHito()).thenReturn(confirmado?123L:null);return f;
    }
    private void fuente(List<FuenteDesenlace> filas,Set<Long> permitidas){
        when(alcances.de(actor)).thenReturn(new Alcances.Alcance(1L,false,List.of(30L)));
        when(repo.fuentesDesenlace(1L,List.of(30L),corte)).thenReturn(filas);
        when(acceso.permitidasActuales(anyMap(),eq(actor))).thenReturn(permitidas);
    }
    @Test void unaFavorableTresNegativasYSeisAbiertasProducenUnCuarto(){
        var filas=new ArrayList<FuenteDesenlace>();filas.add(fila(1,"F",true,30L));
        for(long i=2;i<=4;i++)filas.add(fila(i,"N",false,30L));for(long i=5;i<=10;i++)filas.add(fila(i,i==5?"S":"A",false,30L));
        fuente(filas,new HashSet<>(filas.stream().map(FuenteDesenlace::getId).toList()));
        var tasa=cierres(sut.leer(actor,corte),ventana).get("VENTA");
        assertEquals(1,tasa.exitos());assertEquals(4,tasa.evaluables());assertEquals(6,tasa.pendientes());assertEquals(0,new BigDecimal("25").compareTo(tasa.porcentaje()));
    }
    @Test void estadoFsinHechoC5NoEsExitoNiFracasoAcreditado(){
        fuente(List.of(fila(1,"F",false,30L)),Set.of(1L));var tasa=cierres(sut.leer(actor,corte),ventana).get("VENTA");
        assertEquals(0,tasa.evaluables());assertEquals(1,tasa.sinEvidencia());assertNull(tasa.porcentaje());
    }
    @Test void responsableHistoricoAusenteNoSeCompletaConElActual(){
        fuente(List.of(fila(1,"F",true,null)),Set.of(1L));var tasa=cierres(sut.leer(actor,corte),ventana).get("VENTA");
        assertEquals(1,tasa.sinEvidencia());assertEquals(0,tasa.exitos());
    }
    @Test void finalNoFavorableEsDesenlaceYNoCasoAbierto(){
        fuente(List.of(fila(1,"X",false,30L)),Set.of(1L));var tasa=cierres(sut.leer(actor,corte),ventana).get("VENTA");
        assertEquals(1,tasa.evaluables());assertEquals(0,tasa.pendientes());assertEquals(0,tasa.porcentaje().compareTo(BigDecimal.ZERO));
    }
    @Test void autoridadCanonicaFiltraAntesDeCalcular(){
        fuente(List.of(fila(1,"F",true,30L),fila(2,"N",false,40L)),Set.of(1L));
        assertEquals(List.of(1L),sut.leer(actor,corte).stream().map(d->d.fuente().id()).toList());
        verify(acceso,times(2)).permitidasActuales(anyMap(),eq(actor));
    }
    @Test void cambioDeRevisionConcurrenteNoDevuelveUnaMedicionMezclada(){
        var inicial=fila(1,"F",true,30L);var posterior=fila(1,"F",true,30L);when(posterior.getRevisionHito()).thenReturn(124L);
        fuente(List.of(inicial),Set.of(1L));when(repo.fuentesDesenlace(1L,List.of(30L),corte)).thenReturn(List.of(inicial),List.of(posterior));
        assertThrows(ConflictoException.class,()->sut.leer(actor,corte));
    }
    @Test void falloTecnicoConservaSuNaturaleza(){
        fuente(List.of(),Set.of());when(repo.fuentesDesenlace(1L,List.of(30L),corte)).thenThrow(new IllegalStateException("Base no disponible TEST"));
        assertThrows(IllegalStateException.class,()->sut.leer(actor,corte));
    }
    @Test void mutacionPosteriorAlCorteNoSeConvierteEnFaltaDeEvidenciaHistorica(){
        var f=fila(1,"F",true,30L);when(f.getActualizado()).thenReturn(corte.plusSeconds(1));fuente(List.of(f),Set.of(1L));
        assertThrows(ConflictoException.class,()->sut.leer(actor,corte));
    }
    @Test void tenantAdminNoObtieneDatosNiAunqueLaPoblacionEstuvieraVacia(){
        var admin=new Actor(1L,5L,50L,"TENANT_ADMIN");doThrow(new AccesoNoAutorizadoException()).when(contexto).exigirOperador(admin);
        assertThrows(AccesoNoAutorizadoException.class,()->sut.leer(admin,corte));verifyNoInteractions(repo);
    }
    @Test void duplicidadDeUnaFuenteNoInflaDenominador(){
        var f=fila(1,"N",false,30L);fuente(List.of(f,f),Set.of(1L));assertThrows(IllegalStateException.class,()->sut.leer(actor,corte));
    }
}
