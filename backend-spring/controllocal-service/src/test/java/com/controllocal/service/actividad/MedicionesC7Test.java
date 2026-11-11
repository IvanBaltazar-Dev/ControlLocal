package com.controllocal.service.actividad;
import com.controllocal.service.*;
import com.controllocal.service.soporte.Alcances;
import com.controllocal.service.excepcion.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.controllocal.service.actividad.IndicadoresIndividuales.*;
class MedicionesC7Test {
    final InteraccionService actividad=mock(InteraccionService.class);
    final DesenlacesDeOportunidad desenlaces=mock(DesenlacesDeOportunidad.class);
    final ConversionDeProspecciones conversion=mock(ConversionDeProspecciones.class);
    final MedicionDeCompromisos compromisos=mock(MedicionDeCompromisos.class);
    final ContextosDeActividad contexto=mock(ContextosDeActividad.class);
    final Alcances alcances=mock(Alcances.class);
    final MedicionesC7 servicio=new MedicionesC7(actividad,desenlaces,conversion,compromisos,contexto,alcances);
    final Actor a=new Actor(1,2,3,"BROKER");
    final Ventana v=new Ventana(LocalDate.of(2026,11,1),LocalDate.of(2026,11,10),Instant.parse("2026-11-10T20:00:00Z"));
    final InteraccionService.LecturaIndicadores lectura=new InteraccionService.LecturaIndicadores(List.of(),Set.of());
    @BeforeEach void preparar(){
        when(alcances.de(a)).thenReturn(new Alcances.Alcance(1,false,List.of(4L,5L)));when(actividad.indicadores(a)).thenReturn(lectura);
        when(conversion.leer(a,v)).thenReturn(new ConversionDeProspecciones.Lectura(List.of(),true));when(desenlaces.leer(a,v.corte())).thenReturn(List.of());
        when(compromisos.leer(a,v,lectura)).thenReturn(new MedicionDeCompromisos.Lectura(IndicadoresIndividuales.seguimiento(List.of(),v),true));
    }
    @Test void sinUniversoEvaluableEntregaSinBaseYNoUnaTasaCero(){var r=servicio.medir(v,a);assertEquals("SIN_BASE",r.seguimiento().estadoCalculo());assertNull(r.seguimiento().medicion().porcentaje());assertNull(r.conversion().medicion().tasa().porcentaje());assertNull(r.primeraRespuesta().medicion().medianaHoras());}
    @Test void fuentesRevocadasNoSeDisfrazanDeCero(){when(conversion.leer(a,v)).thenReturn(new ConversionDeProspecciones.Lectura(List.of(),false));when(compromisos.leer(a,v,lectura)).thenReturn(new MedicionDeCompromisos.Lectura(null,false));var r=servicio.medir(v,a);assertEquals("NO_EVALUABLE",r.conversion().estadoCalculo());assertNull(r.conversion().medicion());assertNull(r.seguimiento().medicion());}
    @Test void pedirOtroResponsableNoCompraAcceso(){assertThrows(AccesoNoAutorizadoException.class,()->servicio.porResponsable(v,a,99));verifyNoInteractions(compromisos,conversion,desenlaces);}
    @Test void cambioDeSupervisionAntesDeEntregarInvalidaLaComposicion(){when(alcances.de(a)).thenReturn(new Alcances.Alcance(1,false,List.of(4L,5L)),new Alcances.Alcance(1,false,List.of(5L)));assertThrows(ConflictoException.class,()->servicio.medir(v,a));}
    @Test void responsableHistoricoDesconocidoNoSeAtribuyeATodosLosAgentes(){
        var f=new Fuente("PROSPECCION",9,0,v.corte(),null);when(conversion.leer(a,v)).thenReturn(new ConversionDeProspecciones.Lectura(List.of(new Prospeccion(f,null,List.of(),false)),true));
        when(compromisos.leer(a,v,lectura,4L)).thenReturn(new MedicionDeCompromisos.Lectura(IndicadoresIndividuales.seguimiento(List.of(),v),true));
        assertEquals(1,servicio.medir(v,a).conversion().medicion().tasa().sinEvidencia());assertEquals(0,servicio.porResponsable(v,a,4).conversion().medicion().tasa().sinEvidencia());
    }
    @Test void hechoDeUnCasoAutorizadoNoDesaparecePorqueSuAutorHistoricoYaNoEsteEnElEquipo(){
        var f=new Fuente("PROSPECCION",9,0,v.corte(),99L);when(conversion.leer(a,v)).thenReturn(new ConversionDeProspecciones.Lectura(List.of(new Prospeccion(f,null,List.of(v.hasta()),false)),true));
        assertEquals(1,servicio.medir(v,a).conversion().medicion().tasa().exitos());
        when(compromisos.leer(a,v,lectura,4L)).thenReturn(new MedicionDeCompromisos.Lectura(IndicadoresIndividuales.seguimiento(List.of(),v),true));assertEquals(0,servicio.porResponsable(v,a,4).conversion().medicion().tasa().exitos());
    }
}
