package com.controllocal.service.actividad;

import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AtencionDeConsultasTest {
    private static final LocalDate DIA=LocalDate.of(2026,11,9);
    private static final MomentoHecho NUEVE=new MomentoHecho(DIA,LocalTime.of(9,0),ZoneOffset.ofHours(-5));
    private static final MomentoHecho ONCE=new MomentoHecho(DIA,LocalTime.of(11,30),ZoneOffset.ofHours(-5));
    private static DatosInteraccion datos(EventoComunicacion e,ResultadoInteraccion resultado,AtencionConsultas a){
        var tipo=e==EventoComunicacion.LLAMADA_ENTRANTE||e==EventoComunicacion.LLAMADA_SALIENTE?TipoActividad.LLAMADA:TipoActividad.MENSAJERIA;
        return new DatosInteraccion(List.of(new VinculoActividad(TipoContexto.PROPIEDAD,10L,null,null)),null,null,tipo,
                tipo==TipoActividad.LLAMADA?CanalComunicacion.TELEFONO:CanalComunicacion.WHATSAPP,null,
                List.of(new EventoDeclarado("evento",e,e==EventoComunicacion.MENSAJE_ENVIADO?ONCE:NUEVE)),resultado,null,"Consulta comercial",List.of(),null,null,"Declaración del agente",null,null,a);
    }
    @Test void soloUnaEntradaExpresamenteDeclaradaEsConsulta(){
        var simple=datos(EventoComunicacion.MENSAJE_RECIBIDO,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,null);
        assertNull(AtencionDeConsultas.proyectar(simple,0));
        var marcada=datos(EventoComunicacion.MENSAJE_RECIBIDO,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,new AtencionConsultas(new ConsultaRecibida("evento",null),null));
        assertDoesNotThrow(()->AtencionDeConsultas.validar(marcada));assertEquals(NUEVE,AtencionDeConsultas.proyectar(marcada,0).momentoConsulta());
    }
    @Test void llamadaPerdidaNoAcreditaHaberRecibidoUnaConsulta(){
        var d=datos(EventoComunicacion.LLAMADA_ENTRANTE,ResultadoInteraccion.SIN_RESPUESTA,new AtencionConsultas(new ConsultaRecibida("evento",null),null));
        assertThrows(ReglaNegocioException.class,()->AtencionDeConsultas.validar(d));
    }
    @Test void llamadaSinContestarNoRespondeUnaConsulta(){
        var d=datos(EventoComunicacion.LLAMADA_SALIENTE,ResultadoInteraccion.SIN_RESPUESTA,new AtencionConsultas(null,List.of(new RespuestaComercial(new ReferenciaVersion(1,0),"evento",null,"Llamé"))));
        assertThrows(ReglaNegocioException.class,()->AtencionDeConsultas.validar(d));
    }
    @Test void envioFallidoNoAcreditaRespuesta(){
        var d=datos(EventoComunicacion.ENVIO_FALLIDO,ResultadoInteraccion.COMUNICACION_FALLIDA,new AtencionConsultas(null,List.of(new RespuestaComercial(new ReferenciaVersion(1,0),"evento",null,"No salió"))));
        assertThrows(ReglaNegocioException.class,()->AtencionDeConsultas.validar(d));
    }
    @Test void mensajeEnviadoRespondeAunqueTodaviaNoSeRecibaOtraContestacion(){
        var d=datos(EventoComunicacion.MENSAJE_ENVIADO,ResultadoInteraccion.SIN_RESPUESTA,new AtencionConsultas(null,List.of(new RespuestaComercial(new ReferenciaVersion(1,0),"evento",null,"Envié la respuesta sobre disponibilidad"))));
        assertDoesNotThrow(()->AtencionDeConsultas.validar(d));assertEquals(ONCE,AtencionDeConsultas.proyectar(d,0).respuestas().getFirst().momento());
    }
    @Test void inicioDeLaLlamadaNoInventaHoraDeRespuesta(){
        var d=datos(EventoComunicacion.LLAMADA_ENTRANTE,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,new AtencionConsultas(new ConsultaRecibida("evento",null),List.of(new RespuestaComercial(null,"evento",null,"Respondí durante la llamada"))));
        assertDoesNotThrow(()->AtencionDeConsultas.validar(d));var p=AtencionDeConsultas.proyectar(d,0);
        assertNull(p.momentoConsulta().hora());assertNull(p.respuestas().getFirst().momento().hora());assertNull(AtencionDeConsultas.instante(p.momentoConsulta()));
    }
    @Test void respuestaEnLaMismaLlamadaConHorasConocidasConservaDuracion(){
        var despues=new MomentoHecho(DIA,LocalTime.of(9,5),ZoneOffset.ofHours(-5));
        var d=datos(EventoComunicacion.LLAMADA_ENTRANTE,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,new AtencionConsultas(new ConsultaRecibida("evento",NUEVE),List.of(new RespuestaComercial(null,"evento",despues,"Respondí su pregunta"))));
        assertDoesNotThrow(()->AtencionDeConsultas.validar(d));assertEquals(despues,AtencionDeConsultas.proyectar(d,0).respuestas().getFirst().momento());
    }
    @Test void noSePuedeResponderAntesDeLaPregunta(){
        var d=datos(EventoComunicacion.LLAMADA_ENTRANTE,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,new AtencionConsultas(new ConsultaRecibida("evento",ONCE),List.of(new RespuestaComercial(null,"evento",NUEVE,"Respuesta"))));
        assertThrows(ReglaNegocioException.class,()->AtencionDeConsultas.validar(d));
    }
    @Test void noSeSustituyeLaHoraDeUnMensajeRealPorOtraConveniente(){
        var d=datos(EventoComunicacion.MENSAJE_RECIBIDO,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,new AtencionConsultas(new ConsultaRecibida("evento",ONCE),null));
        assertThrows(ReglaNegocioException.class,()->AtencionDeConsultas.validar(d));
    }
    @Test void respuestaSinConsultaNoSeAtribuyePorProximidad(){
        var d=datos(EventoComunicacion.MENSAJE_ENVIADO,ResultadoInteraccion.SIN_RESPUESTA,new AtencionConsultas(null,List.of(new RespuestaComercial(null,"evento",null,"Respuesta"))));
        assertThrows(ReglaNegocioException.class,()->AtencionDeConsultas.validar(d));
    }
    @Test void responderDosConsultasNoDuplicaLaComunicacion(){
        var d=datos(EventoComunicacion.MENSAJE_ENVIADO,ResultadoInteraccion.SIN_RESPUESTA,new AtencionConsultas(null,List.of(
                new RespuestaComercial(new ReferenciaVersion(1,0),"evento",null,"Disponibilidad"),new RespuestaComercial(new ReferenciaVersion(2,0),"evento",null,"Condiciones"))));
        assertDoesNotThrow(()->AtencionDeConsultas.validar(d));assertEquals(2,AtencionDeConsultas.proyectar(d,0).respuestas().size());
    }
    @Test void dosVersionesDeLaMismaConsultaNoDuplicanRespuestaEnLaMismaComunicacion(){
        var d=datos(EventoComunicacion.MENSAJE_ENVIADO,ResultadoInteraccion.SIN_RESPUESTA,new AtencionConsultas(null,List.of(
                new RespuestaComercial(new ReferenciaVersion(1,0),"evento",null,"Disponibilidad"),new RespuestaComercial(new ReferenciaVersion(1,1),"evento",null,"Condiciones"))));
        assertThrows(ReglaNegocioException.class,()->AtencionDeConsultas.validar(d));
    }
    @Test void seExigeVersionPresenteEnJsonEnLugarDeSuponerCero() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();
        assertThrows(com.fasterxml.jackson.databind.exc.MismatchedInputException.class,()->mapper.readValue("{\"id\":1}",ReferenciaVersion.class));
        assertEquals(new ReferenciaVersion(1,0),mapper.readValue("{\"id\":1,\"version\":0}",ReferenciaVersion.class));
    }
    @Test void proyeccionMinimaNoIncluyeElTextoComercialPrivado() throws Exception {
        var d=datos(EventoComunicacion.MENSAJE_ENVIADO,ResultadoInteraccion.SIN_RESPUESTA,new AtencionConsultas(null,List.of(new RespuestaComercial(new ReferenciaVersion(1,0),"evento",null,"DETALLE PRIVADO"))));
        var json=new ObjectMapper().findAndRegisterModules().writeValueAsString(AtencionDeConsultas.proyectar(d,0));
        assertFalse(json.contains("DETALLE PRIVADO"));assertFalse(json.contains("Consulta comercial"));assertFalse(json.contains("Declaración del agente"));
    }
    @Test void medirLosMensajesDeclaradosConservaLasDosHorasYMedia(){
        var entrada=datos(EventoComunicacion.MENSAJE_RECIBIDO,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,new AtencionConsultas(new ConsultaRecibida("evento",null),null));
        var salida=datos(EventoComunicacion.MENSAJE_ENVIADO,ResultadoInteraccion.SIN_RESPUESTA,new AtencionConsultas(null,List.of(new RespuestaComercial(new ReferenciaVersion(1,0),"evento",null,"Respuesta"))));
        var base=Instant.parse("2026-11-09T20:00:00Z");
        var q=new MedicionInteraccion(1,0,201,DIA,base,TipoActividad.MENSAJERIA,entrada.resultado(),entrada.eventos(),List.of(),null,AtencionDeConsultas.proyectar(entrada,0));
        var r=new MedicionInteraccion(2,0,201,DIA,base,TipoActividad.MENSAJERIA,salida.resultado(),salida.eventos(),List.of(),null,AtencionDeConsultas.proyectar(salida,0));
        var t=AtencionDeConsultas.medir(List.of(q,r),new IndicadoresIndividuales.Ventana(DIA,DIA,base));
        assertEquals(0,new java.math.BigDecimal("2.5").compareTo(t.medianaHoras()));assertEquals(1,t.respondidas());
    }
    @Test void rectificacionMaterialRequiereRevisarLaReferenciaYNoInventaEspera(){
        var q=new MedicionInteraccion(1,2,201,DIA,Instant.parse("2026-11-09T20:00:00Z"),TipoActividad.MENSAJERIA,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,List.of(),List.of(),null,new DatoAtencionConsultas(true,NUEVE,List.of(),2));
        var r=new MedicionInteraccion(2,0,201,DIA,q.registradoEn(),TipoActividad.MENSAJERIA,ResultadoInteraccion.SIN_RESPUESTA,List.of(),List.of(),null,new DatoAtencionConsultas(false,null,List.of(new DatoRespuestaConsulta(new ReferenciaVersion(1,0),ONCE)),0));
        var t=AtencionDeConsultas.medir(List.of(q,r),new IndicadoresIndividuales.Ventana(DIA,DIA,q.registradoEn()));
        assertEquals(1,t.porVerificarVinculo());assertEquals(0,t.esperando());assertEquals(0,t.respondidas());assertNull(t.medianaHoras());
    }
    @Test void unaFuenteRestringidaDeOtroPeriodoNoBloqueaLaMedicionActual(){
        var fecha=DIA.minusDays(60);var q=new MedicionInteraccion(1,0,201,fecha,Instant.parse("2026-11-09T20:00:00Z"),TipoActividad.MENSAJERIA,ResultadoInteraccion.SIN_INTERCAMBIO,List.of(),List.of(),null,
                new DatoAtencionConsultas(true,new MomentoHecho(fecha,LocalTime.NOON,ZoneOffset.ofHours(-5)),List.of(),0));
        var lectura=new LecturaIndicadores(List.of(q),Set.of(1L));
        assertTrue(AtencionDeConsultas.verificable(lectura,new IndicadoresIndividuales.Ventana(DIA,DIA,q.registradoEn())));
    }
    @Test void noSeAsignaPeriodoConvenienteALaConsultaSinFechaParaOmitirLaRestriccion(){
        var q=new MedicionInteraccion(1,0,201,null,Instant.parse("2026-11-09T20:00:00Z"),TipoActividad.MENSAJERIA,ResultadoInteraccion.SIN_INTERCAMBIO,List.of(),List.of(),null,new DatoAtencionConsultas(true,null,List.of(),0));
        var lectura=new LecturaIndicadores(List.of(q),Set.of(1L));assertFalse(AtencionDeConsultas.verificable(lectura,new IndicadoresIndividuales.Ventana(DIA,DIA,q.registradoEn())));
    }
    @Test void laMetadataDePermisoInsuficienteNoSeSerializaComoDatoComercial() throws Exception {
        var lectura=new LecturaIndicadores(List.of(),Set.of(91L));var json=new ObjectMapper().findAndRegisterModules().writeValueAsString(lectura);
        assertFalse(json.contains("consultasConFuentesNoVerificables"));assertFalse(json.contains("91"));
    }
}
