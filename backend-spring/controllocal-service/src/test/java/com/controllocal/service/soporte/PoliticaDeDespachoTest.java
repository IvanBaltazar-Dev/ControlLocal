package com.controllocal.service.soporte;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.controllocal.service.soporte.PoliticaDeDespacho.*;

/** Sustituye las expectativas de pesos por el contrato aprobado D5-01–44. */
class PoliticaDeDespachoTest {
    final LocalDate hoy=LocalDate.of(2026,11,11);
    final Instant ahora=hoy.atTime(10,0).atZone(ZONA).toInstant();
    final Instant detectado=ahora.minusSeconds(1000);
    Asunto a(String id,long recurso,LocalDate fecha,LocalTime hora){return new Asunto(id,"ENCARGO",recurso,true,fecha,hora,detectado,false,null,false,false);}
    List<String> ids(List<Asunto> datos,Vista vista){return despachar(datos,x->x,new Consulta(vista,null,null),ahora).stream().map(Asunto::id).toList();}
    @ParameterizedTest @CsvSource({"false,false,false,false","false,false,true,false","false,true,false,false","false,true,true,false","true,false,false,false","true,false,true,false","true,true,false,false","true,true,true,true"})
    void r0ExigeLasTresCondicionesAcreditadas(boolean evidencia,boolean riesgo,boolean humana,boolean r0){
        var a=new Asunto("incidencia","ENCARGO",1,true,null,null,detectado,false,evidencia?"HECHO:17":null,riesgo,humana);
        assertEquals(r0?Banda.R0:Banda.R3,clasificar(a,ahora).banda());
    }
    @Test void evidenciaVaciaNoAcreditaIncertidumbre(){assertEquals(Banda.R3,clasificar(new Asunto("x","ENCARGO",1,true,null,null,detectado,false," ",true,true),ahora).banda());}
    @Test void hoyConHoraFuturaEsR1SinEstarVencido(){var c=clasificar(a("a",1,hoy,LocalTime.NOON),ahora);assertEquals(Banda.R1,c.banda());assertTrue(c.correspondeHoy());assertFalse(c.plazoVencido());}
    @Test void igualdadDeHoraYaVencio(){assertTrue(clasificar(a("a",1,hoy,LocalTime.of(10,0)),ahora).plazoVencido());}
    @Test void fechaSinHoraDeHoyNoVenceAlComenzarElDia(){var c=clasificar(a("a",1,hoy,null),ahora);assertEquals(Banda.R1,c.banda());assertFalse(c.plazoVencido());}
    @Test void diaPasadoSinHoraSiEsVencido(){assertTrue(clasificar(a("a",1,hoy.minusDays(1),null),ahora).plazoVencido());}
    @Test void futuroSinAnticipacionInventada(){assertEquals(Banda.R2,clasificar(a("a",1,hoy.plusYears(2),null),ahora).banda());}
    @Test void antiguedadNoInventaAtrasoSinFecha(){var c=clasificar(new Asunto("a","ENCARGO",1,true,null,null,detectado.minusSeconds(86400L*400),false,null,false,false),ahora);assertEquals(Banda.R3,c.banda());assertFalse(c.plazoVencido());}
    @Test void fechaEnConflictoNoAcreditaAtraso(){var c=clasificar(new Asunto("a","ENCARGO",1,true,hoy.minusDays(40),null,detectado,true,null,false,false),ahora);assertEquals(Banda.R3,c.banda());assertFalse(c.plazoVencido());}
    @Test void informativoNoAdquiereBanda(){assertNull(clasificar(new Asunto("a","FOTO_PROPIEDAD",1,false,hoy,null,detectado,false,null,false,false),ahora).banda());}
    @Test void hoyNoQuedaOcultoPorVencidosAntiguos(){var d=List.of(a("viejo",1,hoy.minusDays(40),null),a("hoy",2,hoy,LocalTime.NOON));assertEquals(List.of("hoy"),ids(d,Vista.HOY));assertEquals(List.of("viejo"),ids(d,Vista.VENCIDAS));}
    @Test void futurasYSinFechaSonConsultasSeparadas(){var d=List.of(a("futuro",1,hoy.plusYears(1),null),a("sin",2,null,null));assertEquals(List.of("futuro"),ids(d,Vista.FUTURAS));assertEquals(List.of("sin"),ids(d,Vista.SIN_FECHA));}
    @Test void procesoDevuelveElSinFechaAunqueHayaUrgentesFuera(){var d=List.of(a("urgente",2,hoy.minusDays(1),null),a("privado",1,null,null));assertEquals(List.of("privado"),despachar(d,x->x,new Consulta(Vista.PROCESO,"ENCARGO",1L),ahora).stream().map(Asunto::id).toList());}
    @Test void r0NecesarioPrecedeALaActuacionConsultadaSinTraerOtroRecurso(){var incierto=new Asunto("r0","ENCARGO",1,true,null,null,detectado,false,"OPERACION:17",true,true);var ajeno=new Asunto("ajeno","ENCARGO",2,true,null,null,detectado,false,"OPERACION:18",true,true);assertEquals(List.of("r0","hoy"),ids(List.of(a("hoy",1,hoy,null),ajeno,incierto),Vista.HOY));}
    @Test void ordenFechaHoraDeteccionReferenciaSinOrdenDeLecturaPrevio(){var d=List.of(a("sinHora",1,hoy,null),a("12",2,hoy,LocalTime.NOON),a("11",3,hoy,LocalTime.of(11,0)),a("viejo",4,hoy.minusDays(2),null));assertEquals(List.of("viejo","11","12","sinHora"),ids(d,Vista.TODAS));Collections.reverse(d=new ArrayList<>(d));assertEquals(List.of("viejo","11","12","sinHora"),ids(d,Vista.TODAS));}
    @Test void r3ConservaDeteccionYDesempataPorReferencia(){var b=a("b",1,null,null);var a=a("a",2,null,null);var viejo=new Asunto("z","ENCARGO",3,true,null,null,detectado.minusSeconds(1),false,null,false,false);assertEquals(List.of("z","a","b"),ids(List.of(b,a,viejo),Vista.SIN_FECHA));}
    @Test void unaActuacionNoDuplicaIdentidadAlSolaparseConsultas(){var d=List.of(a("a",1,hoy,LocalTime.of(9,0)));assertEquals(ids(d,Vista.HOY),ids(d,Vista.VENCIDAS));assertThrows(IllegalStateException.class,()->ids(List.of(d.getFirst(),d.getFirst()),Vista.TODAS));}
    @Test void noHayTopeGlobalDeCinco(){var d=new ArrayList<Asunto>();for(int i=1;i<=60;i++)d.add(a("a"+i,i,null,null));assertEquals(60,ids(d,Vista.SIN_FECHA).size());}
    @Test void relojCoreLimaDefineElDia(){var t=Instant.parse("2026-11-12T02:00:00Z");assertTrue(clasificar(a("a",1,hoy,null),t).correspondeHoy());assertFalse(clasificar(a("a",1,hoy,null),t).plazoVencido());}
    @Test void consultaNecesitaRecursoCompleto(){assertThrows(RuntimeException.class,()->new Consulta(Vista.PROCESO,null,null));assertThrows(RuntimeException.class,()->new Consulta(Vista.TODAS,"ENCARGO",null));}
    @Test void posponerRevisionConservaElVencimientoYLaMismaIdentidadEnAmbasConsultas(){
        var a=new Asunto("r27","PROSPECCION",1,true,hoy.minusDays(1),null,detectado,false,null,false,false,hoy.plusDays(5));
        var c=clasificar(a,ahora);assertEquals(Banda.R1,c.banda());assertTrue(c.plazoVencido());assertEquals(hoy.minusDays(1),c.fecha());assertEquals(hoy.plusDays(5),c.revisarEn());
        assertEquals(List.of("r27"),ids(List.of(a),Vista.VENCIDAS));assertEquals(List.of("r27"),ids(List.of(a),Vista.FUTURAS));assertTrue(ids(List.of(a),Vista.HOY).isEmpty());
    }
    @Test void revisionDeHoySinHoraNoHeredaHoraDelVencimientoOriginal(){
        var a=new Asunto("r27","PROSPECCION",1,true,hoy.plusDays(4),LocalTime.of(8,0),detectado,false,null,false,false,hoy);
        var c=clasificar(a,ahora);assertEquals(Banda.R1,c.banda());assertTrue(c.correspondeHoy());assertFalse(c.plazoVencido());
        assertEquals(List.of("r27"),ids(List.of(a),Vista.HOY));assertEquals(List.of("r27"),ids(List.of(a),Vista.FUTURAS));
    }
    @Test void posponerNoOcultaR0NiAcreditaLaFechaEnConflicto(){
        var a=new Asunto("r0","PROSPECCION",1,true,hoy.minusDays(40),null,detectado,true,"HECHO:1",true,true,hoy.plusDays(1));
        var c=clasificar(a,ahora);assertEquals(Banda.R0,c.banda());assertFalse(c.plazoVencido());assertTrue(ids(List.of(a),Vista.SIN_FECHA).isEmpty());
        assertEquals(List.of("r0"),ids(List.of(a),Vista.FUTURAS));
    }
    @Test void ordenarPorRevisionNoInventaSuHoraNiDependeDelOrdenDeLectura(){
        var revision=new Asunto("revision","PROSPECCION",1,true,hoy.plusDays(4),LocalTime.of(8,0),detectado,false,null,false,false,hoy);
        var conHora=a("conHora",2,hoy,LocalTime.of(9,0));
        assertEquals(List.of("conHora","revision"),ids(List.of(revision,conHora),Vista.HOY));
        assertEquals(List.of("conHora","revision"),ids(List.of(conHora,revision),Vista.HOY));
    }
}
