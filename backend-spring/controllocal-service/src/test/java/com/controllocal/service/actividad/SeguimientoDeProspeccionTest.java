package com.controllocal.service.actividad;

import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.actividad.ContextosDeInteraccion.ContactoOriginal;
import com.controllocal.service.actividad.SeguimientoDeProspeccion.*;
import com.controllocal.service.actividad.CatalogoInteracciones.*;
import com.controllocal.service.actividad.ContextosDeInteraccion.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SeguimientoDeProspeccionTest {
    private static final LocalDate DIA=LocalDate.of(2026,10,1);
    private static final ContactoOriginal DESTINO=new ContactoOriginal(new ReferenciaContactoContextual(TipoContacto.PERSONA,99L,null,null),99L,true);
    private static Entrada fallo(long id,LocalDate fecha){return entrada(id,true,false,fecha,false,null,null,false,false);}
    private static Entrada entrada(long id,boolean fallo,boolean respuesta,LocalDate fecha,boolean proxima,OrigenActuacion origen,LocalDate hasta,boolean restringido,boolean precisar){
        return new Entrada(id,0,Instant.parse("2026-10-01T15:00:00Z").plusSeconds(id),new HechoR27(10,"PERSONA:99",DESTINO,true,true,fallo,respuesta,false,fecha,proxima,origen,hasta,restringido,precisar,false,fallo));
    }
    private static DatosInteraccion comunicacionComercial(ResultadoInteraccion resultado,Set<FinalidadContacto> fines,Set<FuncionContacto> funciones,boolean continuar){
        return new DatosInteraccion(List.of(new VinculoActividad(TipoContexto.PROSPECCION,10L,null,null)),
                new ParticipanteDeclarado(null,"PROPIETARIO","Madre",null,"madre"),
                resultado==ResultadoInteraccion.SIN_RESPUESTA?null:new ParticipanteDeclarado(null,"FAMILIAR","Hija",null,"hija"),
                TipoActividad.LLAMADA,CanalComunicacion.TELEFONO,null,List.of(new EventoDeclarado("llamada",EventoComunicacion.LLAMADA_SALIENTE,new MomentoHecho(DIA,null,null))),
                resultado,new MomentoHecho(DIA,null,null),null,List.of(),null,null,"Declaración del agente",null,
                new GestionContacto(fines,funciones,funciones.isEmpty()?null:"La hija declaró su función",continuar,null));
    }
    private static Normalizado normalizadoComercial(DatosInteraccion d){return new Normalizado(d,new Autorizacion(201,List.of(),Map.of(),List.of(),null),List.of());}
    private static MedicionInteraccion medicionComercial(long id,DatosInteraccion d){
        return new MedicionInteraccion(id,0,201,DIA,Instant.parse("2026-10-01T15:00:00Z"),d.tipo(),d.resultado(),d.eventos(),List.of(new VinculoFicha("PROSPECCION",10,null)),ContactoEfectivoProspeccion.calificar(normalizadoComercial(d)));
    }
    @Test void tresIntentosUnContactoEfectivoYLogisticaFueraDelCalculo(){
        var comercial=Set.of(FinalidadContacto.PROSPECCION_COMERCIAL);
        var a=medicionComercial(1,comunicacionComercial(ResultadoInteraccion.SIN_RESPUESTA,comercial,Set.of(),false));
        var b=medicionComercial(2,comunicacionComercial(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,comercial,Set.of(FuncionContacto.INFORMACION_CONTACTO),false));
        var c=medicionComercial(3,comunicacionComercial(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,comercial,Set.of(FuncionContacto.GESTION_COMERCIAL),true));
        var d=medicionComercial(4,comunicacionComercial(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,Set.of(FinalidadContacto.COORDINACION_ACCESO),Set.of(FuncionContacto.COORDINACION_ACCESO),false));
        var r=ContactoEfectivoProspeccion.medir(List.of(a,b,c,c,d),DIA,DIA,Instant.parse("2026-10-02T00:00:00Z"));
        assertEquals(3,r.intentosEvaluables());assertEquals(1,r.efectivos());assertEquals(new java.math.BigDecimal("33.3"),r.porcentaje());assertEquals(1,r.fueraDelCalculo());
    }
    @Test void unaNegativaComercialCuentaComoContactoSinCrearAceptacion(){
        var d=comunicacionComercial(ResultadoInteraccion.CONVERSACION_RECHAZADA,Set.of(FinalidadContacto.PROSPECCION_COMERCIAL),Set.of(FuncionContacto.GESTION_COMERCIAL),false);
        ContactoEfectivoProspeccion.validar(d);assertEquals("EFECTIVO",ContactoEfectivoProspeccion.calificar(normalizadoComercial(d)).estado());
    }
    @Test void coordinadoraDeLlavesNoSeVuelveGestoraPorSerHija(){
        var d=comunicacionComercial(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,Set.of(FinalidadContacto.PROSPECCION_COMERCIAL),Set.of(FuncionContacto.COORDINACION_ACCESO),true);
        assertThrows(com.controllocal.service.excepcion.ReglaNegocioException.class,()->ContactoEfectivoProspeccion.validar(d));
        assertEquals("NO_EFECTIVO",ContactoEfectivoProspeccion.calificar(normalizadoComercial(d)).estado());
    }
    @Test void laFuncionDesconocidaNoSeConvierteEnFracasoYLaBaseVaciaNoEsCero(){
        var d=comunicacionComercial(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,Set.of(FinalidadContacto.PROSPECCION_COMERCIAL),Set.of(FuncionContacto.POR_PRECISAR),false);
        var r=ContactoEfectivoProspeccion.medir(List.of(medicionComercial(1,d)),DIA,DIA,Instant.parse("2026-10-02T00:00:00Z"));
        assertEquals(1,r.porPrecisar());assertEquals(0,r.intentosEvaluables());assertNull(r.porcentaje());
    }
    @Test void unaRespuestaCausalNoSumaOtroIntentoNiHaceEfectivosLosDemas(){
        var fines=Set.of(FinalidadContacto.PROSPECCION_COMERCIAL);
        var a=medicionComercial(1,comunicacionComercial(ResultadoInteraccion.SIN_RESPUESTA,fines,Set.of(),false));
        var b=medicionComercial(2,comunicacionComercial(ResultadoInteraccion.SIN_RESPUESTA,fines,Set.of(),false));
        var respuesta=new MedicionInteraccion(3,0,201,DIA,Instant.parse("2026-10-01T16:00:00Z"),TipoActividad.LLAMADA,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,List.of(),a.vinculos(),new DatoContactoProspeccion("EFECTIVO","RESPUESTA_COMERCIAL",false,new ReferenciaVersion(2,0)));
        var r=ContactoEfectivoProspeccion.medir(List.of(a,b,respuesta),DIA,DIA,Instant.parse("2026-10-02T00:00:00Z"));
        assertEquals(2,r.intentosEvaluables());assertEquals(1,r.efectivos());
        var antes=ContactoEfectivoProspeccion.medir(List.of(a,b,respuesta),DIA,DIA,Instant.parse("2026-10-01T15:30:00Z"));assertEquals(0,antes.efectivos());
    }
    @Test void cambiarDeInterlocutorPreservaElDestinatarioBuscadoYAnclaLaSustitucion(){
        var d=comunicacionComercial(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,Set.of(FinalidadContacto.PROSPECCION_COMERCIAL),Set.of(FuncionContacto.GESTION_COMERCIAL),true);
        var servicio=new SeguimientoDeProspeccion(null,null,null,null,null,null);
        var hechos=servicio.proyectar(normalizadoComercial(d),null);assertEquals(1,hechos.size());
        assertEquals("DECLARADO:"+com.controllocal.service.soporte.Idempotencia.huellaExacta("hija"),hechos.getFirst().destinatario());
        assertEquals("DECLARADO:"+com.controllocal.service.soporte.Idempotencia.huellaExacta("madre"),hechos.getFirst().sustituyeDestinatario());
        assertEquals("madre",d.destinatario().referenciaDeclarada());assertTrue(hechos.getFirst().respuestaConDestinatario());
    }
    @Test void unaLlamadaLogisticaNoReiniciaLaCadenciaComercial(){
        var d=comunicacionComercial(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,Set.of(FinalidadContacto.COORDINACION_ACCESO),Set.of(FuncionContacto.COORDINACION_ACCESO),false);
        assertTrue(new SeguimientoDeProspeccion(null,null,null,null,null,null).proyectar(normalizadoComercial(d),null).isEmpty());
    }
    @Test void finalidadComercialSinEntidadProspeccionNoExigeCrearUnExpedienteFicticio(){
        var d=comunicacionComercial(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,Set.of(FinalidadContacto.PROSPECCION_COMERCIAL),Set.of(FuncionContacto.GESTION_COMERCIAL),false);
        var soloPropiedad=new DatosInteraccion(List.of(new VinculoActividad(TipoContexto.PROPIEDAD,19L,null,null)),d.destinatario(),d.interlocutor(),d.tipo(),d.canal(),d.medioReal(),d.eventos(),d.resultado(),d.momento(),d.resumen(),d.declaraciones(),d.proxima(),null,d.fuente(),null,d.gestionContacto());
        assertEquals("EFECTIVO",ContactoEfectivoProspeccion.calificar(normalizadoComercial(soloPropiedad)).estado());
    }
    @Test void unaFuncionComercialConocidaNoSePierdePorqueOtraFuncionSeaDesconocida(){
        var d=comunicacionComercial(ResultadoInteraccion.INTERCAMBIO_EFECTIVO,Set.of(FinalidadContacto.PROSPECCION_COMERCIAL,FinalidadContacto.COORDINACION_ACCESO),Set.of(FuncionContacto.GESTION_COMERCIAL,FuncionContacto.POR_PRECISAR),false);
        assertEquals("EFECTIVO",ContactoEfectivoProspeccion.calificar(normalizadoComercial(d)).estado());
    }
    @Test void anexarUnaObservacionNoInvalidaLaRespuestaCausalPeroRectificarElIntentoSiExigeRevisarla(){
        var a=new MedicionInteraccion(1,1,201,DIA,Instant.parse("2026-10-01T15:00:00Z"),TipoActividad.LLAMADA,ResultadoInteraccion.SIN_RESPUESTA,List.of(),List.of(),new DatoContactoProspeccion("NO_EFECTIVO","SIN_RESPUESTA",true,null,0L));
        var b=new MedicionInteraccion(2,0,201,DIA,Instant.parse("2026-10-01T16:00:00Z"),TipoActividad.LLAMADA,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,List.of(),List.of(),new DatoContactoProspeccion("EFECTIVO","RESPUESTA",false,new ReferenciaVersion(1,0),0L));
        assertEquals(1,ContactoEfectivoProspeccion.medir(List.of(a,b),DIA,DIA,Instant.parse("2026-10-02T00:00:00Z")).efectivos());
        var corregida=new MedicionInteraccion(1,2,201,DIA,a.registradoEn(),a.tipo(),a.resultado(),a.eventos(),a.vinculos(),new DatoContactoProspeccion("NO_EFECTIVO","SIN_RESPUESTA",true,null,2L));
        assertEquals(0,ContactoEfectivoProspeccion.medir(List.of(corregida,b),DIA,DIA,Instant.parse("2026-10-02T00:00:00Z")).efectivos());
    }
    @Test void calendarioDosCincoDiezYRevisionSinCuartoRecontacto(){
        var uno=SeguimientoDeProspeccion.calcular(List.of(fallo(1,DIA)),null,DIA);assertEquals(DIA.plusDays(2),uno.proxima());assertEquals(0,uno.recontactos());
        var dos=SeguimientoDeProspeccion.calcular(List.of(fallo(1,DIA),fallo(2,DIA.plusDays(2))),null,DIA);assertEquals(DIA.plusDays(7),dos.proxima());assertEquals(1,dos.recontactos());
        var tres=SeguimientoDeProspeccion.calcular(List.of(fallo(1,DIA),fallo(2,DIA.plusDays(2)),fallo(3,DIA.plusDays(7))),null,DIA);assertEquals(DIA.plusDays(17),tres.proxima());assertEquals(2,tres.recontactos());
        var cuatro=SeguimientoDeProspeccion.calcular(List.of(fallo(1,DIA),fallo(2,DIA.plusDays(2)),fallo(3,DIA.plusDays(7)),fallo(4,DIA.plusDays(17))),null,DIA);
        assertEquals(DIA.plusDays(17),cuatro.proxima());assertTrue(cuatro.contexto().revisarContinuidad());assertEquals("REVISAR_CONTINUIDAD",cuatro.contexto().causa());assertEquals(3,cuatro.recontactos());
    }
    @Test void unRecontactoTardioParteDelHechoRealYNoDeLaFechaQueSeIncumplio(){
        var c=SeguimientoDeProspeccion.calcular(List.of(fallo(1,DIA),fallo(2,DIA.plusDays(4))),null,DIA);
        assertEquals(DIA.plusDays(9),c.proxima());
    }
    @Test void sinFechaDelHechoNoSeInventaVencimiento(){
        var c=SeguimientoDeProspeccion.calcular(List.of(fallo(1,null)),null,DIA);
        assertNull(c.proxima());assertTrue(c.contexto().faltaFecha());assertEquals("PRECISAR_FECHA",c.contexto().causa());
    }
    @Test void responderElDestinatarioRetiraLaCadenciaSinCambiarElEmbudo(){
        var c=SeguimientoDeProspeccion.calcular(List.of(fallo(1,DIA),entrada(2,false,true,DIA.plusDays(1),false,null,null,false,false)),null,DIA);
        assertFalse(c.necesidad());assertNull(c.proxima());assertNull(c.ultimo());
    }
    @Test void unAcuerdoPosteriorConFechaSustituyeLaCadencia(){
        var c=SeguimientoDeProspeccion.calcular(List.of(fallo(1,DIA),entrada(2,false,false,DIA,true,OrigenActuacion.ACUERDO_EXTERNO,DIA.plusDays(12),false,false)),null,DIA);
        assertEquals(DIA.plusDays(12),c.proxima());assertEquals("ACORDADA",c.origen());
    }
    @Test void unaEsperaExplicitaSinFechaSigueSinFechaAunqueSeRegistreOtraLlamada(){
        var c=SeguimientoDeProspeccion.calcular(List.of(entrada(1,false,false,DIA,true,OrigenActuacion.ACUERDO_EXTERNO,null,false,false),fallo(2,DIA.plusDays(1))),null,DIA);
        assertNull(c.proxima());assertEquals("SIN_FECHA",c.origen());assertTrue(c.necesidad());
    }
    @Test void unaComunicacionEntranteNoLevantaLaRestriccionDeclarada(){
        var c=SeguimientoDeProspeccion.calcular(List.of(entrada(1,false,false,DIA,false,null,null,true,false),entrada(2,false,true,DIA.plusDays(1),false,null,null,false,false)),null,DIA);
        assertTrue(c.contexto().restringido());assertNull(c.proxima());assertTrue(c.necesidad());
    }
    @Test void alcancePorPrecisarNoSeConvierteEnFechaNiProhibicionGlobal(){
        var c=SeguimientoDeProspeccion.calcular(List.of(entrada(1,false,false,DIA,false,null,null,false,true)),null,DIA);
        assertFalse(c.contexto().restringido());assertTrue(c.contexto().alcancePorPrecisar());assertNull(c.proxima());
    }
    @Test void elCalculoOrdenaPorFechaRealSinUsarLaFechaDeDigitacionComoFechaDelHecho(){
        var c=SeguimientoDeProspeccion.calcular(List.of(fallo(3,DIA.plusDays(7)),fallo(1,DIA),fallo(2,DIA.plusDays(2))),null,DIA);
        assertEquals(3L,c.ultimo());assertEquals(DIA.plusDays(17),c.proxima());
    }
    @Test void unaPlanificacionPropiaConservaSuOrigenYLaRevisionMaterial(){
        var previo=new ContextoEstado(DESTINO,"PROXIMA_ACTUACION",false,false,false,false,false,OrigenActuacion.INTENCION_PROFESIONAL,DIA.plusDays(20),"Revisar",Instant.parse("2026-10-01T13:00:00Z"),"Ausencia",7,null,null);
        var c=SeguimientoDeProspeccion.calcular(List.of(fallo(1,DIA)),previo,DIA);
        assertEquals(DIA.plusDays(20),c.proxima());assertEquals("EXPLICITA",c.origen());assertEquals(7,c.contexto().revisionPlanMaterial());assertEquals("Ausencia",c.contexto().motivoPosposicion());
    }
    @Test void unCanalDePropietarioDeclaradoSinPersonaRecibeRevisionInicialYCadenciaDelMismoDestino(){
        var inicial=new HechoR27(10,"DECLARADO:contexto",null,true,true,false,false,false,null,false,null,null,false,false,false,false);
        var c=SeguimientoDeProspeccion.calcular(List.of(new Entrada(1,0,null,inicial)),null,DIA);
        assertTrue(c.necesidad());assertEquals(DIA,c.proxima());assertEquals("CONTACTO_INICIAL",c.contexto().causa());assertNull(c.contexto().destinatarioOriginal());
        var llamada=new HechoR27(10,"DECLARADO:contexto",null,true,true,true,false,false,DIA,false,null,null,false,false,false,true);
        var despues=SeguimientoDeProspeccion.calcular(List.of(new Entrada(1,0,null,inicial),new Entrada(2,0,null,llamada)),null,DIA);
        assertEquals(DIA.plusDays(2),despues.proxima());assertEquals(0,despues.recontactos());
    }
}
