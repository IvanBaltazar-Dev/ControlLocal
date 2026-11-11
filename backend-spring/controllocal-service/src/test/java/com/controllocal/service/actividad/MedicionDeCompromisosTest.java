package com.controllocal.service.actividad;

import com.controllocal.persistence.repositorio.CompromisoSeguimientoRepository;
import com.controllocal.persistence.repositorio.CompromisoSeguimientoRepository.Fila;
import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.excepcion.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.controllocal.service.actividad.CatalogoInteracciones.*;

class MedicionDeCompromisosTest {
    final CompromisoSeguimientoRepository repo=mock(CompromisoSeguimientoRepository.class);
    final CompromisosDeSeguimiento compromisos=mock(CompromisosDeSeguimiento.class);
    final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    final Alcances alcances=mock(Alcances.class);
    final MedicionDeCompromisos motor=new MedicionDeCompromisos(repo,compromisos,contexto,alcances);
    final Actor actor=new Actor(1,2,3,"AGENTE");
    final Instant corte=Instant.parse("2026-11-10T20:00:00Z");
    final IndicadoresIndividuales.Ventana ventana=new IndicadoresIndividuales.Ventana(LocalDate.of(2026,11,1),LocalDate.of(2026,11,10),corte);
    final Fila fila=new Fila(1,UUID.randomUUID(),2,4,3,0,"{}",Instant.parse("2026-11-01T15:00:00Z"),Instant.parse("2026-11-01T15:00:00Z"));
    @BeforeEach void preparar(){
        when(alcances.de(actor)).thenReturn(new Alcances.Alcance(1,false,List.of(3L)));
        when(repo.alCorte(eq(1L),anyCollection(),any(),eq(corte))).thenReturn(List.of(fila));
        contenido(List.of());
    }
    void contenido(List<CompromisosDeSeguimiento.ActuacionAcreditada> acts){when(compromisos.contenidoAutorizado(fila,actor)).thenReturn(new CompromisosDeSeguimiento.Contenido(0,OrigenActuacion.INTENCION_PROFESIONAL,LocalDate.of(2026,11,5),TipoActividad.LLAMADA,"Llamar para revisar propuesta",List.of(),acts,null,null,null));}
    InteraccionService.LecturaIndicadores lectura(List<MedicionInteraccion> i){return new LecturaIndicadores(i,Set.of());}
    MedicionInteraccion llamada(long version,TipoActividad tipo,ResultadoInteraccion resultado){return new MedicionInteraccion(8,version,3,LocalDate.of(2026,11,5),Instant.parse("2026-11-05T18:00:00Z"),tipo,resultado,List.of(new EventoDeclarado("saliente",EventoComunicacion.LLAMADA_SALIENTE,null)),List.of(new VinculoFicha("PROSPECCION",4L,null)),null);}
    void acreditar(){contenido(List.of(new CompromisosDeSeguimiento.ActuacionAcreditada(new ReferenciaVersion(8,0),"Llamada realizada para lo comprometido",Instant.parse("2026-11-05T19:00:00Z"))));}
    @Test void vencidoSinActuacionPermaneceEnElDenominador(){var r=motor.leer(actor,ventana,lectura(List.of()));assertTrue(r.verificable());assertEquals(1,r.medicion().evaluables());assertEquals(0,r.medicion().exitos());assertEquals(0,r.medicion().porcentaje().signum());}
    @Test void sinCasosNoFabricaCero(){when(repo.alCorte(eq(1L),anyCollection(),any(),eq(corte))).thenReturn(List.of());var r=motor.leer(actor,ventana,lectura(List.of()));assertEquals("SIN_BASE",r.medicion().disponibilidad());assertNull(r.medicion().porcentaje());}
    @Test void llamadaSinRespuestaPuedeCumplirLaLlamadaExpresamenteVinculada(){acreditar();var r=motor.leer(actor,ventana,lectura(List.of(llamada(0,TipoActividad.LLAMADA,ResultadoInteraccion.SIN_RESPUESTA))));assertEquals(1,r.medicion().exitos());assertEquals(0,r.medicion().porcentaje().compareTo(new java.math.BigDecimal("100")));}
    @Test void llamadaNoCumpleEnviarFicha(){acreditar();var c=compromisos.contenidoAutorizado(fila,actor);when(compromisos.contenidoAutorizado(fila,actor)).thenReturn(new CompromisosDeSeguimiento.Contenido(0,c.origen(),c.plazo(),TipoActividad.MENSAJERIA,"Enviar ficha",List.of(),c.actuaciones(),null,null,null));assertEquals(0,motor.leer(actor,ventana,lectura(List.of(llamada(0,TipoActividad.LLAMADA,ResultadoInteraccion.SIN_RESPUESTA)))).medicion().exitos());}
    @Test void rectificacionNoHeredaAcreditacionDeContenido(){acreditar();var r=motor.leer(actor,ventana,lectura(List.of(llamada(1,TipoActividad.LLAMADA,ResultadoInteraccion.SIN_RESPUESTA))));assertEquals(0,r.medicion().evaluables());assertEquals(1,r.medicion().sinEvidencia());assertNull(r.medicion().porcentaje());}
    @Test void perderLecturaDeLaFuenteNoEntregaUnCeroComoSiEstuvieraVerificado(){doThrow(new AccesoNoAutorizadoException()).when(compromisos).exigirOriginal(fila,actor);assertFalse(motor.leer(actor,ventana,lectura(List.of())).verificable());}
    @Test void fuenteCambiadaDuranteLecturaExigeReintentar(){var otra=new Fila(1,fila.referencia(),2,4,3,1,"{}",fila.registrada(),corte);when(repo.alCorte(eq(1L),anyCollection(),any(),eq(corte))).thenReturn(List.of(fila),List.of(otra));assertThrows(ConflictoException.class,()->motor.leer(actor,ventana,lectura(List.of())));}
}
