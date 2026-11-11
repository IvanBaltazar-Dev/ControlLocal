package com.controllocal.service.actividad;

import com.controllocal.service.*;
import com.controllocal.service.InteraccionService.*;
import com.controllocal.service.soporte.*;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.persistence.repositorio.ActividadInformeC7Repository;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.controllocal.service.actividad.CatalogoInteracciones.*;

class HechosDeMetasTest {
    final Actor broker=new Actor(1,20,40,Actor.BROKER);
    final Instant corte=Instant.parse("2026-11-10T18:00:00Z");final LocalDate hoy=LocalDate.of(2026,11,10);
    final InteraccionService interacciones=mock(InteraccionService.class);
    final ActividadInformeC7Repository fuentes=mock(ActividadInformeC7Repository.class);
    final DesenlacesDeOportunidad desenlaces=mock(DesenlacesDeOportunidad.class);
    final ContextosDeActividad contexto=mock(ContextosDeActividad.class);final Alcances alcances=mock(Alcances.class);
    final AccesoEncargo encargos=mock(AccesoEncargo.class);final AccesoOportunidad oportunidades=mock(AccesoOportunidad.class);
    final HechosDeMetas servicio=new HechosDeMetas(interacciones,fuentes,desenlaces,contexto,alcances,encargos,oportunidades);
    @BeforeEach void preparar(){when(alcances.de(broker)).thenReturn(new Alcances.Alcance(1,false,List.of(300L,400L)));
        when(interacciones.indicadores(broker)).thenReturn(new LecturaIndicadores(List.of(),Set.of()));
        when(fuentes.habilitaciones(eq(1L),anyCollection(),eq(corte))).thenReturn(List.of());
        when(fuentes.trayectorias(eq(1L),anyCollection(),eq(true),eq(corte))).thenReturn(List.of());
        when(desenlaces.leer(broker,corte)).thenReturn(List.of());}
    HechosDeMetas.Lectura leer(){return servicio.leer(hoy.withDayOfMonth(1),hoy,corte,broker);}
    MedicionInteraccion comunicacion(long id,long autor,Instant registro,DatoContactoProspeccion contacto){return new MedicionInteraccion(id,0,autor,hoy,registro,TipoActividad.LLAMADA,ResultadoInteraccion.INTERCAMBIO_EFECTIVO,List.of(),List.of(new VinculoFicha("PROPIEDAD",7,null),new VinculoFicha("CONTACTO",8,null)),contacto);}
    @Test void unaRespuestaDeOtroAgenteNoTransfiereElLogroNiDuplicaLaComunicacion(){
        var intento=comunicacion(10,300,corte.minusSeconds(60),new DatoContactoProspeccion("NO_EFECTIVO","SIN_RESPUESTA_COMERCIAL",true,null));
        var respuesta=comunicacion(11,400,corte.minusSeconds(30),new DatoContactoProspeccion("EFECTIVO","RESPUESTA_COMERCIAL",false,new ReferenciaVersion(10,0)));
        when(interacciones.indicadores(broker)).thenReturn(new LecturaIndicadores(List.of(intento,intento,respuesta),Set.of()));
        var hechos=leer().hechos();assertEquals(1,hechos.size());assertEquals(10,hechos.getFirst().id());assertEquals(300L,hechos.getFirst().responsable());assertEquals("GENERAL",hechos.getFirst().operacion());
    }
    @Test void unaRectificacionPosteriorAlCorteNoSeConvierteEnCeroHistorico(){
        var h=comunicacion(10,300,corte.plusSeconds(1),new DatoContactoProspeccion("EFECTIVO","RESPUESTA_COMERCIAL",true,null));
        when(interacciones.indicadores(broker)).thenReturn(new LecturaIndicadores(List.of(h),Set.of()));assertThrows(ConflictoException.class,this::leer);
    }
    @Test void faltaDeResponsableHistoricoNoSeRellenaConElActual(){
        when(fuentes.habilitaciones(eq(1L),anyCollection(),eq(corte))).thenReturn(List.of(new ActividadInformeC7Repository.Habilitacion(7,3,"VENTA",hoy,corte.minusSeconds(1),null,9L)));
        var l=leer();assertTrue(l.hechos().isEmpty());assertEquals(1,l.faltantes().size());assertNull(l.faltantes().getFirst().responsable());assertEquals("RESPONSABLE_HISTORICO_DESCONOCIDO",l.faltantes().getFirst().causa());
    }
    @Test void revocacionDeAmbitoImpideEntregarUnaCifraParcial(){
        when(alcances.de(broker)).thenReturn(new Alcances.Alcance(1,false,List.of(300L,400L)),new Alcances.Alcance(1,false,List.of(400L)));
        assertThrows(ConflictoException.class,this::leer);
    }
    @Test void falloDeAutorizacionDeFuenteNoEquivaleAUnConteoVacio(){
        when(fuentes.habilitaciones(eq(1L),anyCollection(),eq(corte))).thenReturn(List.of(new ActividadInformeC7Repository.Habilitacion(7,3,"VENTA",hoy,corte.minusSeconds(1),300L,9L)));
        doThrow(new NoEncontradoException("Encargo")).when(encargos).exigirLectura(7,broker);assertThrows(NoEncontradoException.class,this::leer);
    }
}
