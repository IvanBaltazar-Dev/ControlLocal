package com.controllocal.service.actividad;
import com.controllocal.persistence.repositorio.ProspeccionRepository;
import com.controllocal.persistence.repositorio.ProspeccionRepository.FuenteConversion;
import com.controllocal.service.Actor;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Alcances;
import org.junit.jupiter.api.Test;
import java.time.*;import java.math.BigDecimal;import java.util.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.service.actividad.IndicadoresIndividuales.*;
class ConversionDeProspeccionesTest {
    final ProspeccionRepository repo=mock(ProspeccionRepository.class);final AccesoEncargo acceso=mock(AccesoEncargo.class);
    final Alcances alcances=mock(Alcances.class);final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    final ConversionDeProspecciones sut=new ConversionDeProspecciones(repo,acceso,alcances,contexto);
    final Actor actor=new Actor(1L,3L,30L,"AGENTE");final Instant corte=Instant.parse("2026-12-10T18:00:00Z");
    final Ventana v=new Ventana(LocalDate.of(2026,11,1),LocalDate.of(2026,12,10),corte);
    FuenteConversion fila(long p,Long c,String estado,LocalDate habilitada){
        var f=mock(FuenteConversion.class);when(f.getId()).thenReturn(p);when(f.getResponsable()).thenReturn(30L);when(f.getEstado()).thenReturn(estado);
        when(f.getRegistrada()).thenReturn(Instant.parse("2026-11-01T18:00:00Z"));when(f.getEncargo()).thenReturn(c);
        if(c!=null){when(f.getResponsableEncargo()).thenReturn(30L);when(f.getVersionEncargo()).thenReturn(1L);when(f.getTipoOrigen()).thenReturn("PROSPECCION");when(f.getOrigen()).thenReturn(p);
            if(habilitada!=null){when(f.getHabilitada()).thenReturn(habilitada);when(f.getHabilitacionRegistrada()).thenReturn(habilitada.atTime(12,0).toInstant(ZoneOffset.ofHours(-5)));
                when(f.getRevisionHabilitacion()).thenReturn(c);when(f.getProspeccionHabilitada()).thenReturn(p);when(f.getResponsableHabilitacion()).thenReturn(30L);}}
        if("D".equals(estado)){when(f.getTermino()).thenReturn(LocalDate.of(2026,11,2));when(f.getTerminoRegistrado()).thenReturn(Instant.parse("2026-11-02T18:00:00Z"));when(f.getResponsableTermino()).thenReturn(30L);when(f.getHistorial()).thenReturn(p);}
        return f;
    }
    void fuentes(List<FuenteConversion> filas,Set<Long> permitidos){when(alcances.de(actor)).thenReturn(new Alcances.Alcance(1L,false,List.of(30L)));when(repo.fuentesConversion(1L,List.of(30L),corte)).thenReturn(filas);when(acceso.permitidosActuales(anyMap(),eq(actor))).thenReturn(permitidos);}
    @Test void seisDeOchoResueltasYDosPreparacionesDanSetentaYCinco(){
        var f=new ArrayList<FuenteConversion>();var ids=new HashSet<Long>();for(long i=1;i<=6;i++){f.add(fila(i,i,"T",LocalDate.of(2026,11,2)));ids.add(i);}for(long i=7;i<=8;i++)f.add(fila(i,null,"D",null));for(long i=9;i<=10;i++){f.add(fila(i,i,"T",null));ids.add(i);}
        fuentes(f,ids);var l=sut.leer(actor,v);assertTrue(l.verificable());var m=conversion(l.hechos(),v);assertEquals(6,m.tasa().exitos());assertEquals(8,m.tasa().evaluables());assertEquals(2,m.enPreparacion());assertEquals(0,new BigDecimal("75").compareTo(m.tasa().porcentaje()));
    }
    @Test void segundaOperacionEnOtroMesNoDuplicaNiMueveLaPrimeraConversion(){
        var f=List.of(fila(1,10L,"T",LocalDate.of(2026,11,2)),fila(1,20L,"T",LocalDate.of(2026,12,2)));fuentes(f,Set.of(10L,20L));var hechos=sut.leer(actor,v).hechos();assertEquals(1,hechos.size());
        assertEquals(1,conversion(hechos,v).tasa().exitos());assertEquals(0,conversion(hechos,new Ventana(LocalDate.of(2026,12,1),LocalDate.of(2026,12,10),corte)).tasa().exitos());
    }
    @Test void otroVinculoComoAntecedenteNoConvierteNiResuelveLaGestion(){
        var f=fila(2,10L,"P",LocalDate.of(2026,11,2));when(f.getOrigen()).thenReturn(1L);fuentes(List.of(f),Set.of(10L));var m=conversion(sut.leer(actor,v).hechos(),v);
        assertEquals(0,m.tasa().exitos());assertEquals(1,m.otrasPendientes());assertEquals(0,m.enPreparacion());
    }
    @Test void ausenciaDeOrigenHistoricoNoSeInterpretaComoDirectoOConversion(){
        var f=fila(1,10L,"T",LocalDate.of(2026,11,2));when(f.getTipoOrigen()).thenReturn("DESCONOCIDO");when(f.getOrigen()).thenReturn(null);fuentes(List.of(f),Set.of(10L));var m=conversion(sut.leer(actor,v).hechos(),v);
        assertEquals(1,m.tasa().sinEvidencia());assertEquals(0,m.tasa().exitos());assertNull(m.tasa().porcentaje());
    }
    @Test void permisoRevocadoNoSeDisfrazaComoCeroConversiones(){fuentes(List.of(fila(1,10L,"T",LocalDate.of(2026,11,2))),Set.of());assertFalse(sut.leer(actor,v).verificable());}
    @Test void historiaDeResponsableDesconocidaNoSeRellenaConElActual(){
        var f=fila(1,10L,"T",LocalDate.of(2026,11,2));when(f.getResponsableHabilitacion()).thenReturn(null);fuentes(List.of(f),Set.of(10L));var m=conversion(sut.leer(actor,v).hechos(),v);assertEquals(1,m.tasa().sinEvidencia());assertEquals(0,m.tasa().exitos());
    }
    @Test void concurrenciaEnLaFuenteExigeActualizar(){var f=fila(1,10L,"T",null);fuentes(List.of(f),Set.of(10L));when(repo.fuentesConversion(1L,List.of(30L),corte)).thenReturn(List.of(f),List.of());assertThrows(ConflictoException.class,()->sut.leer(actor,v));}
    @Test void falloTecnicoNoSeConvierteEnPoblacionVacia(){fuentes(List.of(),Set.of());when(repo.fuentesConversion(1L,List.of(30L),corte)).thenThrow(new IllegalStateException("Fallo PG TEST"));assertThrows(IllegalStateException.class,()->sut.leer(actor,v));}
    @Test void aprobadoConInicioFuturoNoEsConversionNiBorrador(){
        var f=fila(1,10L,"T",null);when(f.getEstadoEncargo()).thenReturn("A");when(f.getDesdeEncargo()).thenReturn(LocalDate.of(2026,12,11));fuentes(List.of(f),Set.of(10L));
        var m=conversion(sut.leer(actor,v).hechos(),v);assertEquals(0,m.tasa().exitos());assertEquals(0,m.enPreparacion());assertEquals(1,m.otrasPendientes());assertEquals(0,m.tasa().sinEvidencia());
    }
    @Test void plazoLlegadoSinConstanciaDePrimeraHabilitacionQuedaSinEvidenciaNoInventaFecha(){
        var f=fila(1,10L,"T",null);when(f.getEstadoEncargo()).thenReturn("A");when(f.getDesdeEncargo()).thenReturn(LocalDate.of(2026,12,9));fuentes(List.of(f),Set.of(10L));
        var m=conversion(sut.leer(actor,v).hechos(),v);assertEquals(0,m.tasa().exitos());assertEquals(0,m.enPreparacion());assertEquals(1,m.tasa().sinEvidencia());assertNull(m.tasa().porcentaje());
    }
}
