package com.controllocal.service.mercado;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.mercado.CalculoPrecioMercado.*;
import com.controllocal.service.mercado.CalculoPrecioMercado.Comparable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Números y fuentes TEST: prueban fórmulas, no ajustes de mercado o suficiencia productiva. */
class CalculoPrecioMercadoTest {
    final Contexto ctx=new Contexto(OperacionInmobiliaria.VENTA,"DEPARTAMENTO","SEGMENTO_TEST","PEN",null,Naturaleza.OFERTA,"AREA_CONSTRUIDA");
    static BigDecimal n(String v){return new BigDecimal(v);}
    Dinero dinero(String v){return new Dinero(n(v),"PEN",null);}
    Area area(String v){return new Area(n(v),"AREA_CONSTRUIDA","Documento TEST");}
    Componente ausente(){return new Componente(null,true,"Ausencia documentada TEST");}
    Componente incluido(String v){return new Componente(dinero(v),false,"Componente documentado TEST");}
    Normalizacion normalizacion(){return new Normalizacion(n("80"),n("120"),"Segmento y rango justificados TEST","TEST_V1");}
    Objetivo paquete(){return new Objetivo(ctx,Procedimiento.PAQUETE,area("100"),null,null,"PAQUETE_EQUIVALENTE_TEST",null);}
    Objetivo unitario(){return new Objetivo(ctx,Procedimiento.UNITARIO,area("90"),incluido("10000"),ausente(),null,normalizacion());}
    Comparable comparable(String id,String precio,List<Ajuste> ajustes){return new Comparable(id,"Fuente pública TEST "+id,ctx,dinero(precio),area("100"),incluido("15000"),incluido("5000"),"PAQUETE_EQUIVALENTE_TEST",ajustes,null,null);}
    Ajuste ajuste(String efecto,String valor,Escala escala){return new Ajuste(efecto,efecto,n(valor),escala,"PEN","Fuente local TEST","Revisor TEST","TEST_V1","Justificación TEST, no coeficiente productivo");}
    Resultado calcular(Objetivo o,Comparable... c){return CalculoPrecioMercado.calcular(o,List.of(c),Metodo.MEDIANA,1);}
    void importe(String esperado,BigDecimal actual){assertNotNull(actual);assertEquals(0,n(esperado).compareTo(actual));}
    Comparable cambiar(Comparable c,Dinero p,Area a,Componente g,Componente d,String composicion,List<Ajuste> ajustes,BigDecimal peso,String fuentePeso){return new Comparable(c.inmuebleVerificado(),c.referencia(),c.contexto(),p,a,g,d,composicion,ajustes,peso,fuentePeso);}

    @Test void paqueteAplicaAjustesConSignoSinExigirUnDesgloseQueNoUsa(){
        var c=comparable("A","100000",List.of(ajuste("PISO","2000",Escala.IMPORTE),ajuste("CONSERVACION","-1000",Escala.IMPORTE)));
        c=cambiar(c,c.precio(),c.area(),null,null,c.composicionEquivalente(),c.ajustes(),null,null);
        var r=calcular(paquete(),c);importe("101000",r.referencia());assertNull(r.comparables().getFirst().importeViviendaPorM2());
    }
    @Test void unitarioSeparaComponentesNormalizaSoloElRangoAcreditadoYReincorporaElObjetivo(){
        var r=calcular(unitario(),comparable("A","120000",List.of(ajuste("ALTURA","100",Escala.POR_M2))));
        importe("1100",r.comparables().getFirst().importeViviendaPorM2());importe("109000",r.referencia());
    }
    @Test void componenteDesconocidoNoEsCeroNiActivaUnFallbackSilencioso(){
        var c=comparable("A","120000",List.of());c=cambiar(c,c.precio(),c.area(),null,c.deposito(),c.composicionEquivalente(),c.ajustes(),null,null);
        var r=calcular(unitario(),c);assertNull(r.referencia());assertEquals("COCHERA_COMPARABLE_FALTANTE",r.exclusiones().getFirst().causa());
        assertNotNull(calcular(paquete(),c).referencia(),"El paquete requiere su propia equivalencia documentada, no se activa por inferencia.");
    }
    @Test void ausenciaConfirmadaEsDistintaDeImporteDesconocido(){
        var o=new Objetivo(ctx,Procedimiento.UNITARIO,area("100"),ausente(),ausente(),null,normalizacion());
        var c=comparable("A","100000",List.of());c=cambiar(c,c.precio(),c.area(),ausente(),ausente(),c.composicionEquivalente(),List.of(),null,null);
        importe("100000",calcular(o,c).referencia());
    }
    @Test void areaTotalDeclaradaNoSeTransformaEnConstruida(){
        var c=comparable("A","120000",List.of());c=cambiar(c,c.precio(),new Area(n("100"),"AREA_TOTAL_DECLARADA","Documento TEST"),c.cochera(),c.deposito(),c.composicionEquivalente(),List.of(),null,null);
        var r=calcular(unitario(),c);assertNull(r.referencia());assertEquals("CONCEPTO_DE_AREA_NO_EQUIVALENTE",r.exclusiones().getFirst().causa());
    }
    @Test void cantidadDeAdsNoMultiplicaInmueblesVerificados(){
        var c=comparable("MISMO_INMUEBLE","120000",List.of());var otro=new Comparable(c.inmuebleVerificado(),"Otro anuncio TEST",ctx,c.precio(),c.area(),c.cochera(),c.deposito(),c.composicionEquivalente(),List.of(),null,null);
        assertThrows(ReglaNegocioException.class,()->calcular(paquete(),c,otro));
        var unidadDistinta=new Comparable("UNIDAD_DISTINTA","Misma dirección textual TEST",ctx,c.precio(),c.area(),c.cochera(),c.deposito(),c.composicionEquivalente(),List.of(),null,null);
        assertEquals(2,calcular(paquete(),c,unidadDistinta).comparables().size());
    }
    @ParameterizedTest @ValueSource(strings={"79","121"}) void noExtrapolaElRangoLocalAprobado(String metros){
        var o=new Objetivo(ctx,Procedimiento.UNITARIO,area(metros),incluido("10000"),ausente(),null,normalizacion());
        var r=calcular(o,comparable("A","120000",List.of()));assertNull(r.referencia());assertEquals("FUERA_DEL_RANGO_DE_NORMALIZACION",r.exclusiones().getFirst().causa());
    }
    @Test void mismosM2SinSustentoLocalNoAutorizanEscalamientoLineal(){
        var o=new Objetivo(ctx,Procedimiento.UNITARIO,area("90"),incluido("10000"),ausente(),null,null);
        assertEquals("NORMALIZACION_LOCAL_NO_SUSTENTADA",calcular(o,comparable("A","120000",List.of())).exclusiones().getFirst().causa());
    }
    @Test void monedasDeComponentesNoSeConviertenPorSuposicion(){
        var c=comparable("A","120000",List.of());c=cambiar(c,c.precio(),c.area(),new Componente(new Dinero(n("15000"),"USD",null),false,"Documento TEST"),c.deposito(),c.composicionEquivalente(),List.of(),null,null);
        assertEquals("COCHERA_COMPARABLE_MONEDA_O_PERIODO_NO_EQUIVALENTE",calcular(unitario(),c).exclusiones().getFirst().causa());
    }
    @Test void fechasPeriodosYComposicionesNoSeInfierenDesdeUnPrecioConocido(){
        var c=comparable("A","120000",List.of());var anual=cambiar(c,new Dinero(n("120000"),"PEN","ANUAL"),c.area(),c.cochera(),c.deposito(),c.composicionEquivalente(),List.of(),null,null);
        assertNull(calcular(paquete(),anual).referencia());
        var sinComposicion=cambiar(c,c.precio(),c.area(),c.cochera(),c.deposito(),null,List.of(),null,null);
        assertEquals("PAQUETE_SIN_COMPOSICION_EQUIVALENTE",calcular(paquete(),sinComposicion).exclusiones().getFirst().causa());
    }
    @Test void noSumaDosVecesUnEfectoNiConvierteAjusteUnitarioEnImporte(){
        var repetido=comparable("A","120000",List.of(ajuste("PISO","1000",Escala.IMPORTE),ajuste("PISO","2000",Escala.IMPORTE)));
        assertEquals("EFECTO_AJUSTADO_DOS_VECES",calcular(paquete(),repetido).exclusiones().getFirst().causa());
        assertEquals("UNIDAD_DE_AJUSTE_NO_EQUIVALENTE",calcular(paquete(),comparable("A","120000",List.of(ajuste("PISO","20",Escala.POR_M2)))).exclusiones().getFirst().causa());
    }
    @Test void noPonderaPorUnNumeroSinFuente(){
        var c=comparable("A","120000",List.of());c=cambiar(c,c.precio(),c.area(),c.cochera(),c.deposito(),c.composicionEquivalente(),List.of(),n("3"),null);
        assertEquals("PESO_NO_SUSTENTADO",calcular(paquete(),c).exclusiones().getFirst().causa());
    }
    @Test void medianaPonderadaResuelveLaMitadExactaConPromedioAdyacente(){
        var a=new ValorComparable("A","TEST",n("10"),null,BigDecimal.ONE);var b=new ValorComparable("B","TEST",n("20"),null,BigDecimal.ONE);
        importe("15",CalculoPrecioMercado.agregar(List.of(b,a),Metodo.MEDIANA_PONDERADA));
        a=new ValorComparable("A","TEST",n("10"),null,n("3"));
        importe("10",CalculoPrecioMercado.agregar(List.of(b,a),Metodo.MEDIANA_PONDERADA));
        importe("12.5",CalculoPrecioMercado.agregar(List.of(b,a),Metodo.MEDIA_PONDERADA));
        importe("15",CalculoPrecioMercado.agregar(List.of(b,a),Metodo.MEDIANA));
    }
    @Test void losTresMetodosConservanElMismoCorpusYSuNaturaleza(){
        var valores=List.of(comparable("A","100000",List.of()),comparable("B","110000",List.of()),comparable("C","180000",List.of()));
        for(var metodo:Metodo.values()){
            var r=CalculoPrecioMercado.calcular(paquete(),valores,metodo,1);assertEquals(Set.of("A","B","C"),new HashSet<>(r.comparables().stream().map(ValorComparable::inmueble).toList()));assertEquals(Naturaleza.OFERTA,r.contexto().naturaleza());
            var invertidos=new ArrayList<>(valores);Collections.reverse(invertidos);importe(r.referencia().toPlainString(),CalculoPrecioMercado.calcular(paquete(),invertidos,metodo,1).referencia());
        }
        var c=valores.getFirst();var cierre=new Contexto(ctx.operacion(),ctx.tipo(),ctx.segmento(),ctx.moneda(),ctx.periodo(),Naturaleza.CIERRE,ctx.conceptoArea());
        var distinto=new Comparable("CIERRE","Fuente TEST",cierre,c.precio(),c.area(),c.cochera(),c.deposito(),c.composicionEquivalente(),List.of(),null,null);
        assertEquals("CONTEXTO_NO_EQUIVALENTE",calcular(paquete(),distinto).exclusiones().getFirst().causa());
    }
    @Test void suficienciaDelPerfilNoEsUnPrecioCeroNiCincoAnunciosUniversales(){
        var c=comparable("A","100000",List.of());var uno=CalculoPrecioMercado.calcular(paquete(),List.of(c),Metodo.MEDIANA,1);assertNotNull(uno.referencia());
        var dos=CalculoPrecioMercado.calcular(paquete(),List.of(c),Metodo.MEDIANA,2);assertNull(dos.referencia());assertEquals(List.of("COMPARABLES_PERTINENTES_INSUFICIENTES"),dos.faltantes());
        var vacio=CalculoPrecioMercado.calcular(paquete(),List.of(),Metodo.MEDIANA,2);assertNull(vacio.referencia());assertNull(vacio.minimoDescriptivo());assertNull(vacio.maximoDescriptivo());
    }
    BaseCosto costo(String valor){return new BaseCosto(dinero(valor),"Base de costos TEST","TEST_V1");}
    @Test void controlDeCasaConservaNivelesYNoSeSumaAlComparativo(){
        var casa=new Contexto(ctx.operacion(),"CASA",ctx.segmento(),ctx.moneda(),null,ctx.naturaleza(),ctx.conceptoArea());
        var niveles=List.of(new NivelCasa(area("60"),costo("1000"),n("0.2"),"Depreciación TEST"),new NivelCasa(area("40"),costo("1200"),n("0.1"),"Depreciación TEST"));
        importe("196200",CalculoPrecioMercado.controlCasa(casa,costo("100000"),niveles,costo("5000")));
        importe("100000",calcular(paquete(),comparable("A","100000",List.of())).referencia());
    }
    @Test void faltaDeConstruccionODepreciacionNoSeSustituyePorCero(){
        assertThrows(ReglaNegocioException.class,()->CalculoPrecioMercado.controlCasa(ctx,costo("100000"),List.of(),costo("0")));
        assertThrows(ReglaNegocioException.class,()->CalculoPrecioMercado.controlCasa(ctx,costo("100000"),List.of(new NivelCasa(area("60"),costo("1000"),null,"TEST")),costo("0")));
        assertThrows(ReglaNegocioException.class,()->CalculoPrecioMercado.controlCasa(ctx,costo("100000"),List.of(new NivelCasa(area("60"),costo("1000"),n("0.2"),"TEST")),costo("-100")));
    }
    @Test void controlCapitalNoSePresentaComoUnaRentaMensual(){
        var renta=new Contexto(OperacionInmobiliaria.ALQUILER,"CASA",ctx.segmento(),"PEN","MENSUAL",Naturaleza.OFERTA,ctx.conceptoArea());
        assertThrows(ReglaNegocioException.class,()->CalculoPrecioMercado.controlCasa(renta,costo("100000"),List.of(new NivelCasa(area("60"),costo("1000"),n("0.2"),"TEST")),costo("0")));
    }
    @Test void lecturasConcurrentesNoCambianElCorpusNiElResultado()throws Exception{
        var datos=List.of(comparable("A","100000",List.of()),comparable("B","120000",List.of()));
        var esperado=CalculoPrecioMercado.calcular(paquete(),datos,Metodo.MEDIANA,1);
        try(var hilos=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var a=hilos.submit(()->CalculoPrecioMercado.calcular(paquete(),datos,Metodo.MEDIANA,1));
            var b=hilos.submit(()->CalculoPrecioMercado.calcular(paquete(),datos,Metodo.MEDIANA,1));
            assertEquals(esperado,a.get(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(esperado,b.get(5,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(2,datos.size());
    }
    @Test void promedioDeSegmentoSinAreaObjetivoConservaComponentesYNoFabricaTasacion(){
        var r=CalculoPrecioMercado.describirUnitarios(ctx,List.of(comparable("A","120000",List.of()),comparable("B","140000",List.of())),normalizacion());
        importe("1100",r.promedio());importe("1000",r.minimo());importe("1200",r.maximo());assertEquals(2,r.comparables().size());
    }
    @Test void sinNormalizacionOComponenteNoSeInventanCerosParaDarPromedio(){
        var c=comparable("A","120000",List.of());assertNull(CalculoPrecioMercado.describirUnitarios(ctx,List.of(c),null).promedio());
        c=cambiar(c,c.precio(),c.area(),null,c.deposito(),c.composicionEquivalente(),List.of(),null,null);
        var r=CalculoPrecioMercado.describirUnitarios(ctx,List.of(c),normalizacion());assertNull(r.promedio());assertEquals("COCHERA_COMPARABLE_FALTANTE",r.exclusiones().getFirst().causa());
    }
    @Test void promedioNoAplicaAjustesDeUnObjetivoInexistenteNiMultiplicaDuplicados(){
        var c=comparable("A","120000",List.of(ajuste("PISO","100",Escala.POR_M2)));
        assertNull(CalculoPrecioMercado.describirUnitarios(ctx,List.of(c),normalizacion()).promedio());
        assertThrows(ReglaNegocioException.class,()->CalculoPrecioMercado.describirUnitarios(ctx,List.of(c,c),normalizacion()));
    }

}
