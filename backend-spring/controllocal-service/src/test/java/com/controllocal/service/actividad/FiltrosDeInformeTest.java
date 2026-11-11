package com.controllocal.service.actividad;
import com.controllocal.persistence.repositorio.ActividadInformeC7Repository.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.excepcion.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static com.controllocal.service.actividad.FiltrosDeInforme.*;
import static com.controllocal.service.actividad.CatalogoInteracciones.*;
import static org.junit.jupiter.api.Assertions.*;
class FiltrosDeInformeTest {
    final LocalDate hoy=LocalDate.of(2026,11,10);final Instant corte=hoy.atTime(12,0).atZone(ZoneId.of("America/Lima")).toInstant();
    CasoInforme c(String tipo,long id,long rol,String op,String estado,Long encargo){return new CasoInforme(tipo,id,1,rol,op,estado,hoy,encargo);}
    final List<CasoInforme> datos=List.of(c("ENCARGO",1,7,"V","A",1L),c("ENCARGO",2,8,"A","P",2L),c("OPORTUNIDAD",3,7,"V","A",1L),c("OPORTUNIDAD",4,8,"V","N",1L),c("PROSPECCION",5,7,null,"P",null));
    @Test void operacionSinInferirLaProspeccionNiLaDireccion(){var s=seleccionar(datos,new Filtros("VENTA",null,null,null,null),false);assertEquals(Set.of(1L,3L,4L),s.stream().map(CasoInforme::id).collect(java.util.stream.Collectors.toSet()));}
    @Test void situacionNecesitaFamiliaParaNoConfundirActivaConAbierta(){var s=seleccionar(datos,new Filtros(null,null,null,new Situacion("OPORTUNIDAD","A"),null),false);assertEquals(Set.of(1L,3L),s.stream().map(CasoInforme::id).collect(java.util.stream.Collectors.toSet()));assertThrows(ReglaNegocioException.class,()->validar(new Filtros(null,null,null,new Situacion(null,"A"),null),List.of(7L)));}
    @Test void responsableNoSeConvierteEnUnaConcesionDeCartera(){assertThrows(NoEncontradoException.class,()->validar(new Filtros(null,9L,null,null,null),List.of(7L,8L)));assertEquals(3,seleccionar(datos,new Filtros(null,7L,null,null,null),true).size());}
    @Test void filtrosNoAceptanCodigosInventados(){assertThrows(ReglaNegocioException.class,()->validar(new Filtros("GENERAL",null,null,null,null),List.of(7L)));assertThrows(ReglaNegocioException.class,()->validar(new Filtros(null,null,null,new Situacion("ENCARGO","INVENTADO"),null),List.of(7L)));}
    @Test void unaComunicacionConDosVinculosEsUnHecho(){var m=interaccion(new DatoProximaActuacion(OrigenActuacion.ACUERDO_EXTERNO,hoy.plusDays(2)));assertTrue(vinculada(m,Set.of("ENCARGO:1","OPORTUNIDAD:3"),Filtros.ninguno()));assertEquals(1,List.of(m).stream().filter(i->vinculada(i,Set.of("ENCARGO:1","OPORTUNIDAD:3"),Filtros.ninguno())).count());}
    MedicionInteraccion interaccion(DatoProximaActuacion p){return new MedicionInteraccion(10,1,7,hoy,corte.minusSeconds(1),TipoActividad.LLAMADA,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,List.of(),List.of(new VinculoFicha("ENCARGO",1,null),new VinculoFicha("OPORTUNIDAD",3,null)),null,null,p);}
    @Test void sugerenciaNoSustituyeUnaActuacionAsumidaYFechaDesconocidaNoSeInventa(){var casos=List.of(datos.get(2));assertEquals(1,InformesC7.sinProxima(casos,List.of(interaccion(new DatoProximaActuacion(OrigenActuacion.SUGERENCIA_SISTEMA,hoy.plusDays(2)))),List.of(),corte).size());assertTrue(InformesC7.sinProxima(casos,List.of(interaccion(new DatoProximaActuacion(OrigenActuacion.ACUERDO_EXTERNO,null))),List.of(),corte).isEmpty());}
    @Test void visitaProgramadaEsProximaActuacionSinCrearUnaReunion(){var x=new ActoInforme("VISITA",12,1,3,7,"P",null,corte.minusSeconds(1),false,hoy.plusDays(1),null,null);assertTrue(InformesC7.sinProxima(List.of(datos.get(2)),List.of(),List.of(x),corte).isEmpty());var cancelada=new ActoInforme("VISITA",12,2,3,7,"C",null,corte.minusSeconds(1),false,hoy.plusDays(1),null,null);assertEquals(1,InformesC7.sinProxima(List.of(datos.get(2)),List.of(),List.of(cancelada),corte).size());}
    @Test void noSeImputaUnaComunicacionDeOtraOportunidadPorCompartirEncargo(){var i=new MedicionInteraccion(11,1,7,hoy,corte.minusSeconds(1),TipoActividad.LLAMADA,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,List.of(),List.of(new VinculoFicha("ENCARGO",1,null),new VinculoFicha("OPORTUNIDAD",4,null)));assertFalse(vinculada(i,Set.of("ENCARGO:1","OPORTUNIDAD:3"),new Filtros(null,null,null,new Situacion("OPORTUNIDAD","A"),null)));}
    @Test void soloUnEncargoExpresamenteVinculadoAcreditaOperacionDeProspeccion(){var l=new ArrayList<>(datos);l.add(c("PROSPECCION",6,7,null,"T",1L));var x=seleccionar(l,new Filtros("VENTA",null,null,null,null),false);assertTrue(x.stream().anyMatch(c->c.id()==6&&c.tipo().equals("PROSPECCION")));assertFalse(x.stream().anyMatch(c->c.id()==5));assertTrue(InformesC7.agrupar(x).stream().filter(g->g.recurso().equals("PROSPECCION")).allMatch(g->g.procedenciaOperacion().equals("ENCARGO_VINCULADO")));}
    @Test void gruposSeparanUnidadesYNoSumasMonedasNiEstados(){var gs=InformesC7.agrupar(datos);assertEquals(5,gs.size());assertEquals(5,gs.stream().mapToLong(InformesC7.GrupoSituacion::casos).sum());assertTrue(gs.stream().anyMatch(g->g.operacion().equals("SIN_OPERACION_ACREDITADA")));}
    @Test void resultadosDelPeriodoNoConviertenFaltantesEnNoContinuidadNiDuplicanUnaTasa(){
        var casos=List.of(c("OPORTUNIDAD",3,7,"V","F",1L),c("OPORTUNIDAD",4,7,"V","N",1L),c("OPORTUNIDAD",6,7,"V","F",1L));
        var hechos=List.of(new IndicadoresIndividuales.Desenlace(new IndicadoresIndividuales.Fuente("OPORTUNIDAD",3,1,corte.minusSeconds(1),7L),"VENTA",hoy,true,true),
                new IndicadoresIndividuales.Desenlace(new IndicadoresIndividuales.Fuente("OPORTUNIDAD",4,1,corte.minusSeconds(1),7L),"VENTA",hoy,false,true),
                new IndicadoresIndividuales.Desenlace(new IndicadoresIndividuales.Fuente("OPORTUNIDAD",6,1,corte.minusSeconds(1),7L),"VENTA",null,false,true));
        var r=InformesC7.resultadosDelPeriodo(hechos,Set.of(3L,4L,6L),casos,new Filtros("VENTA",null,null,null,null),new IndicadoresIndividuales.Ventana(hoy,hoy,corte));
        assertEquals(1,r.size());assertEquals(1,r.getFirst().cierresContractualesConfirmados());assertEquals(1,r.getFirst().noContinuidadRegistrada());assertEquals(1,r.getFirst().resueltasSinFechaAcreditada());
        assertEquals(0,InformesC7.resultadosDelPeriodo(hechos,Set.of(3L),casos,new Filtros("VENTA",8L,null,null,null),new IndicadoresIndividuales.Ventana(hoy,hoy,corte)).getFirst().cierresContractualesConfirmados());
    }
}
