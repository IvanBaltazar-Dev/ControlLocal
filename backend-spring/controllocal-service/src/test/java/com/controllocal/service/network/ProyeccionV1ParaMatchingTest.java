package com.controllocal.service.network;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.matching.MotorMatchingDeterminista;
import com.controllocal.service.soporte.ValorLogico;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.controllocal.service.matching.ContratoMatching.*;
import static org.junit.jupiter.api.Assertions.*;

class ProyeccionV1ParaMatchingTest {
    private final Instant ahora=Instant.parse("2026-10-29T12:00:00Z");
    @Test void pisoConservaTextoDeSuDefinicionSinInventarEquivalenciaNumerica() {
        for(String piso:List.of("3","PB")) {
            var d=ProyeccionV1ParaMatching.datos(Map.of("PROPIEDAD:piso",Map.of("valor",piso)),"C",ahora).getFirst();
            assertEquals(TipoDato.TEXTO,d.tipo());assertEquals(piso,d.valor().texto());assertNull(d.valor().numero());
        }
        assertThrows(IllegalStateException.class,()->ProyeccionV1ParaMatching.datos(
                Map.of("PROPIEDAD:piso",Map.of("valor",3)),"C",ahora));
    }
    @Test void alquilerPedidoNoSatisfaceTotalMensualNiFabricaCargosCero() {
        var datos=ProyeccionV1ParaMatching.datos(Map.of("ENCARGO:operacion",Map.of("valor","ALQUILER"),
                "ENCARGO:precio_pedido",Map.of("valor",new BigDecimal("2500"),"moneda","PEN","periodo","MENSUAL","concepto","PRECIO_ALQUILER")),"C",ahora);
        var resultado=evaluar("ENCARGO:total_mensual_obligatorio",TipoDato.IMPORTE,ValorLogico.deImporte(new BigDecimal("3000"),"PEN"),
                new ContextoValor("TOTAL_MENSUAL_OBLIGATORIO",null,"PEN","MENSUAL","ALQUILER",null),datos);
        assertEquals(Conclusion.NO_DETERMINADO,resultado.h());
        var cota=datos.stream().filter(d->d.clave().equals("ENCARGO:total_mensual_obligatorio")).findFirst().orElseThrow(); assertTrue(cota.intervalo()); assertNull(cota.hasta());
    }
    @Test void areaTotalNoSeConvierteEnConstruida() {
        var datos=ProyeccionV1ParaMatching.datos(Map.of("PROPIEDAD:metraje_total",Map.of("valor",new BigDecimal("90"),"concepto","AREA_TOTAL_DECLARADA","unidad","m2")),"C",ahora);
        var resultado=evaluar("PROPIEDAD:metraje_construido",TipoDato.DECIMAL,ValorLogico.deNumero(new BigDecimal("80")),
                new ContextoValor("AREA_CONSTRUIDA","m2",null,null,null,null),datos);
        assertEquals(Conclusion.NO_DETERMINADO,resultado.h());
    }
    @Test void alquilerPublicadoQueYaExcedePresupuestoPermiteDescartarSinLeerCargosPrivados() {
        var datos=ProyeccionV1ParaMatching.datos(Map.of("ENCARGO:operacion",Map.of("valor","ALQUILER"),
                "ENCARGO:precio_pedido",Map.of("valor",new BigDecimal("4000"),"moneda","PEN","periodo","MENSUAL","concepto","PRECIO_ALQUILER")),"C",ahora);
        var r=evaluar("ENCARGO:total_mensual_obligatorio",TipoDato.IMPORTE,ValorLogico.deImporte(new BigDecimal("3000"),"PEN"),
                new ContextoValor("TOTAL_MENSUAL_OBLIGATORIO",null,"PEN","MENSUAL","ALQUILER",null),datos);
        assertEquals(Conclusion.INCUMPLE,r.h());
    }
    @Test void tarifaPublicadaPorMetroConservaSuUnidadSinFalsoExcesoDelTotalMensual() {
        var datos=ProyeccionV1ParaMatching.datos(Map.of("ENCARGO:operacion",Map.of("valor","ALQUILER"),
                "ENCARGO:precio_pedido",Map.of("valor",new BigDecimal("10"),"moneda","PEN","periodo","MENSUAL","concepto","PRECIO_ALQUILER","unidad","m2"),
                "PROPIEDAD:metraje_total",Map.of("valor",new BigDecimal("0.5"),"concepto","AREA_TOTAL_DECLARADA","unidad","m2")),"C",ahora);
        var r=evaluar("ENCARGO:total_mensual_obligatorio",TipoDato.IMPORTE,ValorLogico.deImporte(new BigDecimal("6"),"PEN"),
                new ContextoValor("TOTAL_MENSUAL_OBLIGATORIO",null,"PEN","MENSUAL","ALQUILER",null),datos);
        assertEquals(Conclusion.NO_DETERMINADO,r.h());
        var tarifa=datos.stream().filter(d->d.clave().equals("ENCARGO:precio_alquiler")).findFirst().orElseThrow();
        assertEquals(0,new BigDecimal("10").compareTo(tarifa.valor().numero()));assertEquals("m2",tarifa.contexto().unidad());
        var total=datos.stream().filter(d->d.clave().equals(TOTAL_MENSUAL_OBLIGATORIO)).findFirst().orElseThrow();
        assertEquals(Causa.UNIDAD_INCOMPATIBLE,total.metadatos().causa());assertNull(total.valor(),
                "Ni10 como total ni5 por multiplicar superficie sin una declaración de total autorizada.");
    }
    @Test void periodoDesconocidoNoSeConvierteEnMensual() {
        var datos=ProyeccionV1ParaMatching.datos(Map.of("ENCARGO:operacion",Map.of("valor","ALQUILER"),
                "ENCARGO:precio_pedido",Map.of("valor",new BigDecimal("2000"),"moneda","PEN","concepto","PRECIO_ALQUILER")),"C",ahora);
        var r=evaluar("ENCARGO:total_mensual_obligatorio",TipoDato.IMPORTE,ValorLogico.deImporte(new BigDecimal("3000"),"PEN"),
                new ContextoValor("TOTAL_MENSUAL_OBLIGATORIO",null,"PEN","MENSUAL","ALQUILER",null),datos);
        assertEquals(Conclusion.NO_DETERMINADO,r.h());
    }
    @Test void ceroAscensoresEsUnHechoYNoCreaLosOtrosAtributos() {
        var datos=ProyeccionV1ParaMatching.datos(Map.of("PROPIEDAD:ascensores",Map.of("valor",BigDecimal.ZERO)),"C",ahora);
        assertEquals(1,datos.size());assertEquals(BigDecimal.ZERO,datos.getFirst().valor().numero());
        assertEquals(EstadoDato.CONOCIDO,datos.getFirst().metadatos().estado());
    }
    @Test void datoPrivadoEsFalloDeContratoYNoResultadoVacio() {
        assertThrows(IllegalStateException.class,()->ProyeccionV1ParaMatching.datos(Map.of("PROPIEDAD:direccion",Map.of("valor","Privada")),"C",ahora));
        assertThrows(IllegalStateException.class,()->ProyeccionV1ParaMatching.datos(Map.of("ENCARGO:precio_pedido",Map.of("valor",BigDecimal.ONE,"presupuestoCliente",BigDecimal.TEN)),"C",ahora));
    }
    @Test void precioConservaMonedaYConceptoSinConversion() {
        var datos=ProyeccionV1ParaMatching.datos(Map.of("ENCARGO:operacion",Map.of("valor","VENTA"),
                "ENCARGO:precio_pedido",Map.of("valor",new BigDecimal("180000.25"),"moneda","USD","concepto","PRECIO_VENTA")),"C",ahora);
        var precio=datos.stream().filter(d->d.clave().equals("ENCARGO:precio_venta")).findFirst().orElseThrow();
        assertEquals("USD",precio.valor().moneda());assertEquals(new BigDecimal("180000.25"),precio.valor().numero());
        assertEquals("PRECIO_VENTA",precio.contexto().concepto());
        assertNull(precio.contexto().periodo());
    }
    private Evaluacion evaluar(String clave,TipoDato tipo,ValorLogico valor,ContextoValor contexto,List<Dato> datos) {
        var meta=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,null);
        var criterio=new Criterio("CR",clave,Exigencia.OBLIGATORIO,null,List.of(new Condicion(clave,tipo,OperadorCriterio.MAXIMO,valor,null,false,contexto,meta)));
        return new MotorMatchingDeterminista().evaluar(new Consulta("D",1,Modalidad.COMERCIAL,List.of(criterio),ahora,"D1-D4"),
                new Oferta("O",new ClaveCandidato("L2","O","C"),1,true,Conclusion.CUMPLE,new ConfirmacionDisponibilidad(ahora,false,"F"),datos));
    }
}
