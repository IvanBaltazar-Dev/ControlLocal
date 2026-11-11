package com.controllocal.service.actividad;

import com.controllocal.service.actividad.IndicadoresIndividuales.*;
import com.controllocal.service.InteraccionService.OrigenActuacion;
import com.controllocal.service.actividad.CatalogoInteracciones.TipoActividad;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IndicadoresIndividualesTest {
    private static final LocalDate DIA=LocalDate.of(2026,11,9);
    private static final Instant CORTE=Instant.parse("2026-11-10T05:00:00Z");
    private static final Ventana V=new Ventana(DIA.minusDays(29),DIA,CORTE);
    private static Fuente fuente(String tipo,long id){return new Fuente(tipo,id,0,CORTE.minusSeconds(1),8L);}
    private static Tasa seguimiento(Compromiso... c){return IndicadoresIndividuales.seguimiento(List.of(c),V);}
    private static Compromiso compromiso(long id,OrigenActuacion origen,LocalDate plazo,TipoActividad actividad,
                                         List<CambioPlazo> cambios,List<Actuacion> actos,LocalDate resolucion,String evidencia){
        return new Compromiso(fuente("COMPROMISO",id),"COMPROMISO:"+id,origen,plazo,actividad,cambios,actos,resolucion,evidencia);
    }
    private static Actuacion acto(long id,long compromiso,TipoActividad tipo,LocalDate fecha,boolean real,boolean contenido){
        return new Actuacion(fuente("INTERACCION",id),tipo,fecha,"COMPROMISO:"+compromiso,real,contenido);
    }
    private static void porcentaje(String esperado,Tasa t){assertEquals(0,new BigDecimal(esperado).compareTo(t.porcentaje()));}
    @Test void llamadaRealSinRespuestaCumpleLoPlanificado(){
        var c=compromiso(1,OrigenActuacion.INTENCION_PROFESIONAL,DIA,TipoActividad.LLAMADA,List.of(),List.of(acto(10,1,TipoActividad.LLAMADA,DIA,true,true)),null,null);
        porcentaje("100",seguimiento(c));
    }
    @Test void llamarNoCumpleElEnvioDeFicha(){
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA,TipoActividad.MENSAJERIA,List.of(),List.of(acto(10,1,TipoActividad.LLAMADA,DIA,true,true)),null,null);
        porcentaje("0",seguimiento(c));assertEquals(1,seguimiento(c).evaluables());
    }
    @Test void mensajeRealSinLaFichaNoAcreditaElContenidoComprometido(){
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA,TipoActividad.MENSAJERIA,List.of(),List.of(acto(10,1,TipoActividad.MENSAJERIA,DIA,true,false)),null,null);
        porcentaje("0",seguimiento(c));
    }
    @Test void sugerenciaDeCadenciaNoEsCompromiso(){
        var t=seguimiento(compromiso(1,OrigenActuacion.SUGERENCIA_SISTEMA,DIA,TipoActividad.LLAMADA,List.of(),List.of(),null,null));
        assertEquals(0,t.evaluables());assertNull(t.porcentaje());assertEquals("SIN_BASE",t.disponibilidad());
    }
    @Test void vencidoNoRealizadoPermaneceEnLaBase(){
        porcentaje("0",seguimiento(compromiso(1,OrigenActuacion.INTENCION_PROFESIONAL,DIA.minusDays(3),TipoActividad.LLAMADA,List.of(),List.of(),null,null)));
    }
    @Test void planPropioPuedeMoverseAntesDeVencer(){
        var c=compromiso(1,OrigenActuacion.INTENCION_PROFESIONAL,DIA,TipoActividad.LLAMADA,
                List.of(new CambioPlazo(DIA,DIA.plusDays(2),DIA.minusDays(1),"Planificación revisada",false)),List.of(),null,null);
        assertEquals(0,seguimiento(c).evaluables());assertEquals(1,seguimiento(c).pendientes());
    }
    @Test void fechaAcordadaNoPuedeMoverseSinNuevoAcuerdo(){
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA,TipoActividad.LLAMADA,
                List.of(new CambioPlazo(DIA,DIA.plusDays(2),DIA.minusDays(1),"Quiero moverlo",false)),List.of(),null,null);
        assertThrows(IllegalArgumentException.class,()->seguimiento(c));
    }
    @Test void acuerdoNuevoAnteriorAlVencimientoConservaCambioLegitimo(){
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA,TipoActividad.LLAMADA,
                List.of(new CambioPlazo(DIA,DIA.plusDays(2),DIA.minusDays(1),"Propietaria acepta jueves",true)),List.of(),null,null);
        assertEquals(1,seguimiento(c).pendientes());
    }
    @Test void reprogramarDespuesDeVencerNoBorraElRetraso(){
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA.minusDays(2),TipoActividad.LLAMADA,
                List.of(new CambioPlazo(DIA.minusDays(2),DIA.plusDays(2),DIA,"Nuevo acuerdo",true)),
                List.of(acto(10,1,TipoActividad.LLAMADA,DIA,true,true)),null,null);
        porcentaje("0",seguimiento(c));assertEquals(1,seguimiento(c).evaluables());
    }
    @Test void desistimientoLegitimoAnteriorSeInformaSeparado(){
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA,TipoActividad.LLAMADA,List.of(),List.of(),DIA.minusDays(1),"Desistimiento registrado");
        assertEquals(1,seguimiento(c).resueltosAntesDeVencer());assertEquals(0,seguimiento(c).evaluables());assertEquals(0,seguimiento(c).exitos());
    }
    @Test void resolucionTardiaNoEliminaIncumplimiento(){
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA.minusDays(2),TipoActividad.LLAMADA,List.of(),List.of(),DIA,"Desistimiento tardío");
        porcentaje("0",seguimiento(c));assertEquals(0,seguimiento(c).resueltosAntesDeVencer());
    }
    @Test void resolucionSinEvidenciaNoEliminaLaBase(){
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA,TipoActividad.LLAMADA,List.of(),List.of(),DIA.minusDays(1),null);
        assertEquals(1,seguimiento(c).evaluables());
    }
    @Test void lecturaOPreparacionSinActuacionNoCumple(){
        var c=compromiso(1,OrigenActuacion.INTENCION_PROFESIONAL,DIA,TipoActividad.MENSAJERIA,List.of(),List.of(acto(10,1,TipoActividad.MENSAJERIA,DIA,false,true)),null,null);
        porcentaje("0",seguimiento(c));
    }
    @Test void noAtribuyeActuacionAUnCompromisoCercanoSinVinculo(){
        var c=compromiso(1,OrigenActuacion.INTENCION_PROFESIONAL,DIA,TipoActividad.LLAMADA,List.of(),List.of(acto(10,2,TipoActividad.LLAMADA,DIA,true,true)),null,null);
        porcentaje("0",seguimiento(c));
    }
    @Test void seisDeOchoResueltasEs75YDosQuedanPendientes(){
        var hechos=new ArrayList<Prospeccion>();
        for(int i=1;i<=6;i++)hechos.add(new Prospeccion(fuente("PROSPECCION",i),null,List.of(DIA),false));
        for(int i=7;i<=8;i++)hechos.add(new Prospeccion(fuente("PROSPECCION",i),DIA,List.of(),false));
        for(int i=9;i<=10;i++)hechos.add(new Prospeccion(fuente("PROSPECCION",i),null,List.of(),true));
        var t=IndicadoresIndividuales.conversion(hechos,V).tasa();
        porcentaje("75",t);assertEquals(6,t.exitos());assertEquals(8,t.evaluables());assertEquals(2,t.pendientes());
        assertEquals(2,IndicadoresIndividuales.conversion(hechos,V).enPreparacion());
    }
    @Test void ventaYAlquilerDeUnaProspeccionCuentanUnaSolaConversion(){
        var p=new Prospeccion(fuente("PROSPECCION",1),null,List.of(DIA,DIA),false);
        var t=IndicadoresIndividuales.conversion(List.of(p,p),V).tasa();assertEquals(1,t.exitos());assertEquals(1,t.evaluables());
    }
    @Test void preparacionNoEsConversionNiPerdida(){
        var t=IndicadoresIndividuales.conversion(List.of(new Prospeccion(fuente("PROSPECCION",1),null,List.of(),true)),V).tasa();
        assertNull(t.porcentaje());assertEquals(1,t.pendientes());
    }
    @Test void habilitacionFueraDelPeriodoNoSeRecuenta(){
        var p=new Prospeccion(fuente("PROSPECCION",1),null,List.of(DIA.minusDays(50)),false);
        assertEquals(0,IndicadoresIndividuales.conversion(List.of(p),V).tasa().evaluables());
    }
    @Test void unCierreDeCuatroResueltasEs25ConSeisAbiertas(){
        var hechos=new ArrayList<Desenlace>();hechos.add(new Desenlace(fuente("OPORTUNIDAD",1),"VENTA",DIA,true,true));
        for(int i=2;i<=4;i++)hechos.add(new Desenlace(fuente("OPORTUNIDAD",i),"VENTA",DIA,false,true));
        for(int i=5;i<=10;i++)hechos.add(new Desenlace(fuente("OPORTUNIDAD",i),"VENTA",null,false,false));
        var t=IndicadoresIndividuales.cierres(hechos,V).get("VENTA");porcentaje("25",t);assertEquals(6,t.pendientes());
    }
    @Test void ofertaAceptadaSinDesenlaceContractualSigueAbierta(){
        var t=IndicadoresIndividuales.cierres(List.of(new Desenlace(fuente("OPORTUNIDAD",1),"ALQUILER",null,false,false)),V).get("ALQUILER");
        assertNull(t.porcentaje());assertEquals(1,t.pendientes());
    }
    @Test void ventaYAlquilerNoSeMezclan(){
        var t=IndicadoresIndividuales.cierres(List.of(new Desenlace(fuente("OPORTUNIDAD",1),"VENTA",DIA,true,true),new Desenlace(fuente("OPORTUNIDAD",2),"ALQUILER",DIA,false,true)),V);
        porcentaje("100",t.get("VENTA"));porcentaje("0",t.get("ALQUILER"));
    }
    private static final Instant NUEVE=Instant.parse("2026-11-09T14:00:00Z");
    private static Respuesta respuesta(long id,long consulta,int minutos,boolean enviada,boolean atendida){
        return new Respuesta(fuente("INTERACCION",id),NUEVE.plusSeconds(minutos*60L),"CONSULTA:"+consulta,true,enviada,atendida,true);
    }
    @Test void llamadaSinRespuestaNoInterrumpeLasDosHorasYMedia(){
        var c=new Consulta(fuente("CONSULTA",1),NUEVE,DIA,List.of(respuesta(10,1,15,false,false),respuesta(11,1,150,true,false)));
        var t=IndicadoresIndividuales.primeraRespuesta(List.of(c),V);assertEquals(0,new BigDecimal("2.5").compareTo(t.medianaHoras()));
    }
    @Test void dosDeDiezRespondidasConOchoEnEspera(){
        var cs=new ArrayList<Consulta>();
        for(int i=1;i<=10;i++)cs.add(new Consulta(fuente("CONSULTA",i),NUEVE,DIA,i<=2?List.of(respuesta(10+i,i,60*i,true,false)):List.of()));
        var t=IndicadoresIndividuales.primeraRespuesta(cs,V);
        assertEquals(10,t.recibidas());assertEquals(2,t.respondidas());assertEquals(8,t.esperando());assertEquals(8,t.antiguedades().size());assertEquals(0,new BigDecimal("1.5").compareTo(t.medianaHoras()));
    }
    @Test void respuestaDeOtraConsultaNoEsPrimeraRespuesta(){
        var c=new Consulta(fuente("CONSULTA",1),NUEVE,DIA,List.of(respuesta(10,2,15,true,false)));
        var t=IndicadoresIndividuales.primeraRespuesta(List.of(c),V);assertEquals(0,t.respondidas());assertEquals(1,t.esperando());assertNull(t.medianaHoras());
    }
    @Test void fechaSinHoraNoSeTransformaEnDuracionCero(){
        var c=new Consulta(fuente("CONSULTA",1),null,DIA,List.of(respuesta(10,1,150,true,false)));
        var t=IndicadoresIndividuales.primeraRespuesta(List.of(c),V);assertEquals(1,t.respondidas());assertEquals(1,t.sinFechasSuficientes());assertNull(t.medianaHoras());
    }
    @Test void ceroConsultasNoDaMedianaCero(){
        var t=IndicadoresIndividuales.primeraRespuesta(List.of(),V);assertEquals(0,t.recibidas());assertNull(t.medianaHoras());assertEquals("SIN_BASE",t.disponibilidad());
    }
    @Test void noUsaRespuestasPosterioresAlCorte(){
        var r=new Respuesta(fuente("INTERACCION",10),CORTE.plusSeconds(1),"CONSULTA:1",true,true,false,true);
        var t=IndicadoresIndividuales.primeraRespuesta(List.of(new Consulta(fuente("CONSULTA",1),NUEVE,DIA,List.of(r))),V);
        assertEquals(0,t.respondidas());assertEquals(1,t.esperando());
    }
    @Test void rectificacionConservaIdentidadYNoDuplicaElUniverso(){
        var primera=new Desenlace(fuente("OPORTUNIDAD",1),"VENTA",DIA,false,true);
        var corregida=new Desenlace(new Fuente("OPORTUNIDAD",1,1,CORTE,8L),"VENTA",DIA,true,true);
        var t=IndicadoresIndividuales.cierres(List.of(primera,corregida),V).get("VENTA");assertEquals(1,t.evaluables());porcentaje("100",t);
    }
    @Test void rectificacionRegistradaDespuesDelCorteNoReescribeEsaFoto(){
        var primera=new Desenlace(fuente("OPORTUNIDAD",1),"VENTA",DIA,false,true);
        var corregida=new Desenlace(new Fuente("OPORTUNIDAD",1,1,CORTE.plusSeconds(1),8L),"VENTA",DIA,true,true);
        porcentaje("0",IndicadoresIndividuales.cierres(List.of(primera,corregida),V).get("VENTA"));
    }
    @Test void responsableHistoricoDesconocidoNoSeInventaConElActual(){
        var p=new Prospeccion(new Fuente("PROSPECCION",1,0,CORTE,null),null,List.of(DIA),false);
        var t=IndicadoresIndividuales.conversion(List.of(p),V).tasa();assertEquals(1,t.sinEvidencia());assertNull(t.porcentaje());
    }
    @Test void mismaVersionConContenidoContradictorioNoSeEscogePorOrdenDeLlegada(){
        var a=new Desenlace(fuente("OPORTUNIDAD",1),"VENTA",DIA,false,true);
        var b=new Desenlace(fuente("OPORTUNIDAD",1),"VENTA",DIA,true,true);
        assertThrows(IllegalArgumentException.class,()->IndicadoresIndividuales.cierres(List.of(a,b),V));
    }
    @Test void plazoDeHoySinHoraNoEsYaIncumplimientoAlMediodia(){
        var v=new Ventana(DIA.minusDays(29),DIA,NUEVE.plusSeconds(3600));
        var c=compromiso(1,OrigenActuacion.INTENCION_PROFESIONAL,DIA,TipoActividad.LLAMADA,List.of(),List.of(),null,null);
        c=new Compromiso(new Fuente("COMPROMISO",1,0,NUEVE,8L),c.identidad(),c.origen(),c.plazo(),c.actividad(),c.cambios(),c.actuaciones(),null,null);
        var t=IndicadoresIndividuales.seguimiento(List.of(c),v);assertEquals(0,t.evaluables());assertEquals(1,t.pendientes());
    }
    @Test void cambiosSucesivosTardiosConservanElPrimerRetraso(){
        var c=compromiso(1,OrigenActuacion.INTENCION_PROFESIONAL,DIA.minusDays(4),TipoActividad.LLAMADA,
                List.of(new CambioPlazo(DIA.minusDays(4),DIA.minusDays(1),DIA.minusDays(3),"Primer cambio tardío",false),
                        new CambioPlazo(DIA.minusDays(1),DIA.plusDays(1),DIA,"Segundo cambio tardío",false)),List.of(),null,null);
        porcentaje("0",seguimiento(c));assertEquals(1,seguimiento(c).evaluables());
    }
    @Test void cambioElMismoDiaSinHorasSuficientesNoInventaPuntualidadNiRetraso(){
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA.minusDays(1),TipoActividad.LLAMADA,
                List.of(new CambioPlazo(DIA.minusDays(1),DIA.plusDays(1),DIA.minusDays(1),"Nuevo acuerdo sin hora conocida",true)),List.of(),null,null);
        // Aquí sí existe una hora límite pactada; se desconoce si el cambio la precedió.
        c=new Compromiso(c.fuente(),c.identidad(),c.origen(),c.plazo(),c.actividad(),c.cambios(),c.actuaciones(),null,null,NUEVE.minusSeconds(86400));
        var t=seguimiento(c);assertEquals(1,t.sinEvidencia());assertNull(t.porcentaje());
    }
    @Test void planDeDiaCompletoPuedeReprogramarseEseDiaSinInventarUnaHoraLimite(){
        var c=compromiso(1,OrigenActuacion.INTENCION_PROFESIONAL,DIA.minusDays(1),TipoActividad.LLAMADA,
                List.of(new CambioPlazo(DIA.minusDays(1),DIA.plusDays(1),DIA.minusDays(1),"Plan propio revisado antes de terminar el día",false)),List.of(),null,null);
        var t=seguimiento(c);assertEquals(1,t.pendientes());assertEquals(0,t.sinEvidencia());assertEquals(0,t.evaluables());
    }
    @Test void resolucionLegitimaEnDiaDePlazoSinHoraPactadaSeInformaSeparada(){
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA.minusDays(1),TipoActividad.LLAMADA,List.of(),List.of(),DIA.minusDays(1),"La familia desistió ese día");
        var t=seguimiento(c);assertEquals(1,t.resueltosAntesDeVencer());assertEquals(0,t.sinEvidencia());assertEquals(0,t.evaluables());
    }
    @Test void noInventaResolucionFuturaDeUnCompromisoTodaviaAbierto(){
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA.plusDays(4),TipoActividad.LLAMADA,List.of(),List.of(),DIA.plusDays(2),"Acuerdo futuro");
        assertEquals(1,seguimiento(c).pendientes());assertEquals(0,seguimiento(c).resueltosAntesDeVencer());
    }
    @Test void distinguePreparacionesDeOtrasProspeccionesAbiertas(){
        var t=IndicadoresIndividuales.conversion(List.of(new Prospeccion(fuente("PROSPECCION",1),null,List.of(),true),new Prospeccion(fuente("PROSPECCION",2),null,List.of(),false)),V);
        assertEquals(1,t.enPreparacion());assertEquals(1,t.otrasPendientes());assertEquals(2,t.tasa().pendientes());
    }
    @Test void dosRespuestasSeCuentanUnaVezYSeUsaLaPrimeraReal(){
        var c=new Consulta(fuente("CONSULTA",1),NUEVE,DIA,List.of(respuesta(10,1,120,true,false),respuesta(11,1,30,false,true)));
        var t=IndicadoresIndividuales.primeraRespuesta(List.of(c,c),V);assertEquals(1,t.respondidas());assertEquals(1,t.duracionesEvaluables());assertEquals(0,new BigDecimal("0.5").compareTo(t.medianaHoras()));
    }
    @Test void respuestaComunicativaAjenaAlAsuntoNoRespondeLaConsulta(){
        var r=new Respuesta(fuente("INTERACCION",10),NUEVE.plusSeconds(600),"CONSULTA:1",false,true,false,true);
        var t=IndicadoresIndividuales.primeraRespuesta(List.of(new Consulta(fuente("CONSULTA",1),NUEVE,DIA,List.of(r))),V);assertEquals(1,t.esperando());assertEquals(0,t.respondidas());
    }
    @Test void rectificarLaFechaRealDeUnaActuacionNoConservaUnCumplimientoFalso(){
        var antes=acto(10,1,TipoActividad.LLAMADA,DIA.minusDays(2),true,true);
        var despues=new Actuacion(new Fuente("INTERACCION",10,1,CORTE,8L),TipoActividad.LLAMADA,DIA,"COMPROMISO:1",true,true);
        var c=compromiso(1,OrigenActuacion.INTENCION_PROFESIONAL,DIA.minusDays(1),TipoActividad.LLAMADA,List.of(),List.of(antes,despues),null,null);
        porcentaje("0",seguimiento(c));
    }
    @Test void plazoConHoraExactaNoUsaUnaLlamadaSinHoraComoPuntual(){
        var c=new Compromiso(fuente("COMPROMISO",1),"COMPROMISO:1",OrigenActuacion.ACUERDO_EXTERNO,DIA,TipoActividad.LLAMADA,List.of(),List.of(acto(10,1,TipoActividad.LLAMADA,DIA,true,true)),null,null,NUEVE);
        assertEquals(1,seguimiento(c).sinEvidencia());assertNull(seguimiento(c).porcentaje());
    }
    @Test void respuestaConFechaDesconocidaImpideAfirmarQueOtraFueLaPrimera(){
        var desconocida=new Respuesta(fuente("INTERACCION",10),null,"CONSULTA:1",true,true,false,true);
        var c=new Consulta(fuente("CONSULTA",1),NUEVE,DIA,List.of(desconocida,respuesta(11,1,150,true,false)));
        var t=IndicadoresIndividuales.primeraRespuesta(List.of(c),V);assertEquals(1,t.respondidas());assertEquals(1,t.sinFechasSuficientes());assertNull(t.medianaHoras());
    }
    @Test void consultaSinFechaDeRecepcionNoSeAtribuyeAlPeriodoActual(){
        var t=IndicadoresIndividuales.primeraRespuesta(List.of(new Consulta(fuente("CONSULTA",1),null,null,List.of())),V);
        assertEquals(1,t.sinPeriodoConocido());assertEquals(0,t.recibidas());assertNull(t.medianaHoras());
    }
    @Test void terminoDefinitivoSinFechaNoSeConvierteEnPreparacionAbierta(){
        var p=new Prospeccion(fuente("PROSPECCION",1),null,List.of(),false,true);
        var t=IndicadoresIndividuales.conversion(List.of(p),V).tasa();assertEquals(1,t.sinEvidencia());assertEquals(0,t.pendientes());assertNull(t.porcentaje());
    }
    @Test void resolucionElMismoDiaAntesDeLaHoraPactadaSeInformaSeparada(){
        var c=new Compromiso(fuente("COMPROMISO",1),"COMPROMISO:1",OrigenActuacion.ACUERDO_EXTERNO,DIA,TipoActividad.LLAMADA,List.of(),List.of(),DIA,"Desistió antes de llamar",NUEVE.plusSeconds(3600),NUEVE);
        assertEquals(1,seguimiento(c).resueltosAntesDeVencer());assertEquals(0,seguimiento(c).evaluables());
    }
    @Test void resolucionPosteriorElMismoDiaConservaElRetraso(){
        var c=new Compromiso(fuente("COMPROMISO",1),"COMPROMISO:1",OrigenActuacion.ACUERDO_EXTERNO,DIA,TipoActividad.LLAMADA,List.of(),List.of(),DIA,"Desistió después del plazo",NUEVE,NUEVE.plusSeconds(3600));
        porcentaje("0",seguimiento(c));assertEquals(0,seguimiento(c).resueltosAntesDeVencer());
    }
    @Test void resolucionElMismoDiaSinHoraNoAcreditaOrdenInventado(){
        var c=new Compromiso(fuente("COMPROMISO",1),"COMPROMISO:1",OrigenActuacion.ACUERDO_EXTERNO,DIA,TipoActividad.LLAMADA,List.of(),List.of(),DIA,"Se desconoce hora",NUEVE);
        assertEquals(1,seguimiento(c).sinEvidencia());assertNull(seguimiento(c).porcentaje());
    }
    @Test void nuevoAcuerdoConHoraPreservaSuLimiteExacto(){
        var cambio=new CambioPlazo(DIA.minusDays(1),DIA,DIA.minusDays(2),"Nuevo acuerdo acreditado",true,null,null,NUEVE);
        var a=new Actuacion(fuente("INTERACCION",10),TipoActividad.LLAMADA,DIA,"COMPROMISO:1",true,true,NUEVE.plusSeconds(3600));
        var c=compromiso(1,OrigenActuacion.ACUERDO_EXTERNO,DIA.minusDays(1),TipoActividad.LLAMADA,List.of(cambio),List.of(a),null,null);
        porcentaje("0",seguimiento(c));
    }
}
