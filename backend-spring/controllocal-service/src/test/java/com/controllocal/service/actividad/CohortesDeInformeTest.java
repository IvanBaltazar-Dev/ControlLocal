package com.controllocal.service.actividad;
import com.controllocal.persistence.repositorio.ActividadInformeC7Repository.Trayectoria;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.service.actividad.IndicadoresIndividuales.*;
class CohortesDeInformeTest {
    final Ventana v=new Ventana(dia(1),dia(10),Instant.parse("2026-11-10T20:00:00Z"));
    static LocalDate dia(int d){return LocalDate.of(2026,11,d);}
    Trayectoria fila(long id,String estado,LocalDate visita,LocalDate oferta){return new Trayectoria(id,2,7,"V",estado,50,4L,dia(1),visita,oferta,null);}
    Desenlace cierre(long id,boolean c5){return new Desenlace(new Fuente("OPORTUNIDAD",id,2,v.corte(),4L),"VENTA",dia(9),c5,true);}
    CohortesDeInforme.Cohorte informe(List<Trayectoria> fs,List<Desenlace> ds){return CohortesDeInforme.componer(fs,ds,v).getFirst();}
    @Test void rutaSinVisitaConservaOfertaYCierreSinInventarUnPaso(){var r=informe(List.of(fila(1,"F",null,dia(4))),List.of(cierre(1,true)));assertEquals(1,r.casos());assertEquals(0,r.conVisita());assertEquals(1,r.conOferta());assertEquals(1,r.cierresContractuales());var etapa=r.duraciones().get(2);assertNull(etapa.mediana());assertEquals(1,etapa.sinFechasSuficientes());assertEquals(0,etapa.terminadas());}
    @Test void medianaDeTerminadasNoMezclaLaAntiguedadAbierta(){var r=informe(List.of(fila(1,"F",dia(2),dia(3)),fila(2,"F",dia(2),dia(5)),fila(3,"A",null,null)),List.of(cierre(1,true),cierre(2,true)));var dur=r.duraciones().getLast();assertEquals(2,dur.terminadas());assertEquals(0,new java.math.BigDecimal("8").compareTo(dur.mediana()));assertEquals(1,dur.pendientes().size());assertEquals(9,dur.pendientes().getFirst().dias());}
    @Test void ofertaAceptadaOEstadoSinConfirmacionC5NoSonCierreContractual(){var r=informe(List.of(fila(1,"A",null,dia(4)),fila(2,"F",null,dia(4))),List.of(cierre(2,false)));assertEquals(0,r.cierresContractuales());assertEquals(1,r.abiertas());assertEquals(1,r.resueltasSinFechaOConfirmacion());assertNull(r.duraciones().getLast().mediana());}
    @Test void nuevaResponsabilidadNoSeApropiaDelOrigenHistorico(){var r=informe(List.of(fila(1,"A",null,null)),List.of());assertEquals(4L,r.fuentes().getFirst().responsableHistorico());assertNotEquals(50L,r.fuentes().getFirst().responsableHistorico());}
    @Test void ausenciaDeResponsableHistoricoNoSeRellenaConElActual(){var f=fila(1,"A",null,null);var r=informe(List.of(new Trayectoria(f.id(),f.version(),f.encargo(),f.operacion(),f.situacion(),f.responsableActual(),null,f.apertura(),null,null,null)),List.of());assertEquals(1,r.sinResponsableHistorico());assertNull(r.fuentes().getFirst().responsableHistorico());}
    @Test void fechaContradictoriaNoSeConvierteEnCeroDias(){var f=fila(1,"F",null,dia(10));var r=informe(List.of(f),List.of(cierre(1,true)));assertEquals(1,r.duraciones().get(3).fechasInconsistentes());assertNull(r.duraciones().get(3).mediana());}
    @Test void mismaOportunidadNoSeDuplicaPorNumeroDeActividades(){var f=fila(1,"A",dia(2),dia(4));assertThrows(IllegalArgumentException.class,()->informe(List.of(f,f),List.of()));}
}
