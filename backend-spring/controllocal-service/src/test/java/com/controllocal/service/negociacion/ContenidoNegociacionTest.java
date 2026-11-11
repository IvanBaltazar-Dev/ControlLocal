package com.controllocal.service.negociacion;

import com.controllocal.service.OfertaService.*;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContenidoNegociacionTest {
    @Test void dineroConservaDeclaracionSinEquivalenciaNiConversion() {
        var d=ContenidoNegociacion.dinero(new Dinero(new BigDecimal("123.40")," usd ","Base declarada","Quincenal"));
        assertEquals(new BigDecimal("123.40"),d.importe());assertEquals("USD",d.moneda());
        assertEquals("Base declarada",d.base());assertEquals("Quincenal",d.periodo());
    }
    @Test void dineroNoRedondeaCantidadQueNoCabeEnPersistencia() {
        assertThrows(ReglaNegocioException.class,()->ContenidoNegociacion.dinero(new Dinero(new BigDecimal("123.456"),"USD",null,null)));
        assertThrows(ReglaNegocioException.class,()->ContenidoNegociacion.dinero(new Dinero(new BigDecimal("1000000000000"),"USD",null,null)));
    }
    @Test void dineroNoCompletaMonedaAusente() {
        assertThrows(ReglaNegocioException.class,()->ContenidoNegociacion.dinero(new Dinero(BigDecimal.TEN,null,null,null)));
    }
    @Test void parteRealNoEsSoloUnLadoSinPersona() {
        assertThrows(ReglaNegocioException.class,()->ContenidoNegociacion.parte(new ParteReal("I",null)));
        assertEquals(new ParteReal("I",7L),ContenidoNegociacion.parte(new ParteReal(" i ",7L)));
    }
    @Test void fechaNoSeCompletaNiRecibeLimiteTemporalInventado() {

        assertEquals(LocalDate.of(2026,10,29),ContenidoNegociacion.fecha(LocalDate.of(2026,10,29)));
        assertEquals(LocalDate.of(2026,10,30),ContenidoNegociacion.fecha(LocalDate.of(2026,10,30)));
        assertThrows(ReglaNegocioException.class,()->ContenidoNegociacion.fecha(null));
    }
    @Test void composicionNoAtribuyeSuObjetoAUnaPropiedadAncla() {
        assertThrows(ReglaNegocioException.class,()->ContenidoNegociacion.objeto(new ObjetoExacto(TipoObjeto.COMPOSICION,1L,0L,2L,0L,3L,1L,List.of(2L,4L))));
        var o=new ObjetoExacto(TipoObjeto.COMPOSICION,1L,0L,null,null,3L,1L,List.of(2L,4L));
        assertSame(o,ContenidoNegociacion.objeto(o));
        assertThrows(ReglaNegocioException.class,()->ContenidoNegociacion.objeto(new ObjetoExacto(TipoObjeto.UNIDAD,1L,0L,2L,null,null,null,List.of(2L))));
    }
}
