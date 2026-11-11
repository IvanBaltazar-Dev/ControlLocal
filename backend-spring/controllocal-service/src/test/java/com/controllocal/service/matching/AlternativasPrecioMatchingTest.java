package com.controllocal.service.matching;

import com.controllocal.domain.comercial.OperadorCriterio;
import com.controllocal.domain.inmueble.TipoDato;
import com.controllocal.service.captura.SuficienciaInmueble.EstadoDato;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.ValorLogico;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.controllocal.service.matching.ContratoMatching.*;
import static com.controllocal.service.matching.ContratoMatching.Conclusion.*;
import static org.junit.jupiter.api.Assertions.*;

/** Aprobación30/10: una diferencia económica explícita, sin reinterpretar Demanda ni H original. */
class AlternativasPrecioMatchingTest {
    private static final Instant CORTE=Instant.parse("2026-10-30T15:00:00Z");
    private static final String REGLA="D1-D4-20261018-C3-R2";
    private static final String BASE="base:presupuesto";
    private static final ContextoValor VENTA=new ContextoValor("PRECIO_VENTA",null,"PEN",null,"VENTA",null);
    private static final ContextoValor ALQUILER=new ContextoValor("TOTAL_MENSUAL_OBLIGATORIO",null,"PEN","MENSUAL","ALQUILER",null);
    private static final ContextoValor SIMPLE=new ContextoValor(null,null,null,null,"VENTA",null);
    private final MotorMatchingDeterminista motor=new MotorMatchingDeterminista();
    private final AlternativasPrecioMatching selector=new AlternativasPrecioMatching(motor);

    @Test void devuelvePrecioComparableYExcesoSinCambiarLaEvaluacionOriginal() {
        var q=consultaVenta();var o=venta("A","2800",5);var original=motor.evaluar(q,o);
        var a=selector.evaluar(q,o,original,null,null).orElseThrow();
        assertSame(original,a.original());assertEquals(INCUMPLE,original.h());assertEquals(Categoria.INCOMPATIBLE,original.categoria());
        igual("2800",a.precio());igual("2500",a.maximoOriginal());igual("300",a.excesoAbsoluto());igual("12",a.excesoPorcentaje());
        assertEquals("PEN",a.moneda());assertEquals("PRECIO_VENTA",a.concepto());assertNull(a.periodo());
        assertEquals(q.criterios(),consultaVenta().criterios());
    }
    @ParameterizedTest @CsvSource({"2500,false","2500.01,true","3000,true","3000.01,false"})
    void limitesInclusivosSinRedondearAntesDeComparar(String precio,boolean elegible) {
        assertEquals(elegible,alternativa(consultaVenta(),venta("A",precio,0),bd("3000"),"PEN").isPresent());
    }
    @Test void descubrimientoNoImponePorcentajeOTechoUniversal() {
        assertTrue(alternativa(consultaVenta(),venta("A","1000000",0),null,null).isPresent());
    }
    @Test void presupuestoEntreConservaElPisoAunqueSeaLaUnicaObligatoriaIncumplida() {
        var q=consulta(presupuesto(OperadorCriterio.ENTRE,"2000","2500",VENTA));
        var porDebajo=venta("BAJO","1900",0);
        assertEquals(INCUMPLE,motor.evaluar(q,porDebajo).h());
        assertTrue(alternativa(q,porDebajo,bd("3000"),"PEN").isEmpty());
        assertTrue(alternativa(q,venta("ALTO","2800",0),bd("3000"),"PEN").isPresent());
    }
    @Test void ausenciaDeTechoNoSeConvierteEnCero() {
        var minimo=consulta(presupuesto(OperadorCriterio.MINIMO,"2000",null,VENTA));
        assertTrue(alternativa(minimo,venta("A","2800",0),null,null).isEmpty());
        assertTrue(alternativa(consulta(),venta("A","2800",0),null,null).isEmpty());
    }
    @Test void presupuestoPreferidoNoSePromueveAObligatorio() {
        var b=presupuesto(OperadorCriterio.MAXIMO,"2500",null,VENTA);
        var preferido=new Criterio(BASE,BASE,Exigencia.PREFERENCIA,Prioridad.NORMAL,b.alternativas());
        assertTrue(alternativa(consulta(preferido),venta("A","2800",0),null,null).isEmpty());
    }
    @Test void noEscogeUnaRamaDePresupuestoNiUnaExclusionComoSiFueraTechoUnico() {
        var b=presupuesto(OperadorCriterio.MAXIMO,"2500",null,VENTA);var c=b.alternativas().getFirst();
        var otra=presupuesto(OperadorCriterio.MAXIMO,"2600",null,VENTA).alternativas().getFirst();
        assertTrue(alternativa(consulta(new Criterio(BASE,BASE,Exigencia.OBLIGATORIO,null,List.of(c,otra))),
                venta("A","2800",0),null,null).isEmpty());
        var negada=new Condicion(c.claveDato(),c.tipo(),c.operador(),c.valor(),c.hasta(),true,c.contexto(),c.metadatos());
        assertTrue(alternativa(consulta(new Criterio(BASE,BASE,Exigencia.OBLIGATORIO,null,List.of(negada))),
                venta("A","2000",0),null,null).isEmpty());
    }
    @Test void noRelajaNingunaOtraObligatoriaFallidaODesconocida() {
        var q=consultaVenta();var o=venta("A","2800",7);
        var falsa=reemplazar(o,"TEST:obligatoria",bool("TEST:obligatoria",false,o.clave().configuracion()));
        assertTrue(alternativa(q,falsa,null,null).isEmpty());
        assertTrue(alternativa(q,sin(o,"TEST:obligatoria"),null,null).isEmpty());
    }
    @Test void conservaTambienTopesEconomicosAdicionales() {
        var b=presupuesto(OperadorCriterio.MAXIMO,"2500",null,VENTA);
        var adicional=new Criterio("tope-adicional","tope-adicional",Exigencia.OBLIGATORIO,null,
                presupuesto(OperadorCriterio.MAXIMO,"2700",null,VENTA).alternativas());
        assertTrue(alternativa(consulta(b,adicional),venta("A","2800",7),null,null).isEmpty());
    }
    @Test void preferenciasIncumplidasOPendientesConservanSuPuntajeSinRescatarObligatorias() {
        var q=consultaVenta();var o=sin(venta("A","2800",0),"TEST:p1");
        var original=motor.evaluar(q,o);var a=selector.evaluar(q,o,original,null,null).orElseThrow();
        assertEquals(0,a.original().s());assertEquals(3,a.original().u());assertEquals(6,a.original().w());
    }
    @Test void exigeModalidadComercialCoberturaCompletaYHabilitacionActual() {
        var q=consultaVenta();var o=venta("A","2800",7);
        var parcial=new Consulta(q.referencia(),q.version(),q.modalidad(),q.criterios(),q.corte(),q.versionRegla(),CoberturaCriterios.PARCIAL);
        assertTrue(alternativa(parcial,o,null,null).isEmpty());
        var preliminar=new Consulta(q.referencia(),q.version(),Modalidad.PRELIMINAR,q.criterios(),q.corte(),q.versionRegla());
        assertTrue(alternativa(preliminar,o,null,null).isEmpty());
        var noHabilitada=new Oferta(o.referenciaEstable(),o.clave(),o.version(),true,INCUMPLE,o.disponibilidad(),o.datos());
        assertTrue(alternativa(q,noHabilitada,null,null).isEmpty());
        var vencida=new Oferta(o.referenciaEstable(),o.clave(),o.version(),true,CUMPLE,
                new ConfirmacionDisponibilidad(CORTE.minusSeconds(37L*86400),false,"confirmacion-vencida"),o.datos());
        assertTrue(alternativa(q,vencida,null,null).isEmpty());
    }
    @Test void recursoNoAutorizadoNoSeConvierteEnResultadoVacio() {
        var q=consultaVenta();var o=venta("A","2800",0);var original=motor.evaluar(q,o);
        var prohibida=new Oferta(o.referenciaEstable(),o.clave(),o.version(),false,o.habilitacion(),o.disponibilidad(),o.datos());
        assertThrows(AccesoNoAutorizadoException.class,()->selector.evaluar(q,prohibida,original,null,null));
    }
    @ParameterizedTest @CsvSource({"2800,,false",",2900,false","2800,2900,false","2800,2800,true","3100,,false"})
    void unaCotaOUnIntervaloAnchoNoSeExtraeComoPrecioExacto(String bajo,String alto,boolean exacto) {
        var q=consultaVenta();var o=venta("A","2800",0);var contexto=o.datos().getFirst().contexto();
        var d=new Dato(PRECIO_VENTA,TipoDato.IMPORTE,importe(bajo,"PEN"),importe(alto,"PEN"),true,contexto,conocido());
        assertEquals(exacto,alternativa(q,reemplazar(o,PRECIO_VENTA,d),null,null).isPresent());
    }
    @Test void conflictoEntreDosPreciosNoEligeElFavorableAunqueAmbosExcedanElTecho() {
        var q=consultaVenta();var o=venta("A","2800",0);var datos=new ArrayList<>(o.datos());
        datos.add(precio("2900",o.clave().configuracion(),VENTA,conocido()));
        assertTrue(alternativa(q,conDatos(o,datos),null,null).isEmpty());
        Collections.reverse(datos);assertTrue(alternativa(q,conDatos(o,datos),null,null).isEmpty());
    }
    @Test void copiasExactasDelPrecioNoGeneranOtraAlternativaNiInestabilidad() {
        var q=consultaVenta();var o=venta("A","2800.00",0);var datos=new ArrayList<>(o.datos());
        datos.add(precio("2800",o.clave().configuracion(),VENTA,conocido()));
        igual("2800",alternativa(q,conDatos(o,datos),null,null).orElseThrow().precio());
    }
    @Test void noUsaDatoVencidoIncompletoOEnConflictoExplicito() {
        var q=consultaVenta();var o=venta("A","2800",0);
        var metas=List.of(new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,CORTE),
                new MetadatosDato(EstadoDato.CONOCIDO,Causa.DATO_FALTANTE,null,null,null,null,null,null,null),
                new MetadatosDato(EstadoDato.CONOCIDO,Causa.CONFLICTO,null,null,null,null,null,null,null),
                new MetadatosDato(EstadoDato.FALTANTE,Causa.CONFLICTO,null,null,null,null,null,null,null));
        for(var m:metas)assertTrue(alternativa(q,reemplazar(o,PRECIO_VENTA,precio("2800",o.clave().configuracion(),VENTA,m)),null,null).isEmpty());
    }
    @Test void condicionDePresupuestoVencidaNoProduceUnaPropuesta() {
        var b=presupuesto(OperadorCriterio.MAXIMO,"2500",null,VENTA);var c=b.alternativas().getFirst();
        var m=new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,null,null,null,CORTE);
        var vencida=new Condicion(c.claveDato(),c.tipo(),c.operador(),c.valor(),c.hasta(),false,c.contexto(),m);
        assertTrue(alternativa(consulta(new Criterio(BASE,BASE,Exigencia.OBLIGATORIO,null,List.of(vencida))),venta("A","2800",0),null,null).isEmpty());
    }
    @Test void datosPrivadosAusentesNoSeCompletanConOtraClaveNiConUnaReserva() {
        var q=consultaVenta();var o=venta("A","2800",0);
        var sinPrecio=sin(o,PRECIO_VENTA);var datos=new ArrayList<>(sinPrecio.datos());
        datos.add(new Dato("TEST:reserva_privada",TipoDato.IMPORTE,importe("2700","PEN"),null,false,
                precio("2800",o.clave().configuracion(),VENTA,conocido()).contexto(),conocido()));
        assertTrue(alternativa(q,conDatos(sinPrecio,datos),null,null).isEmpty());
    }
    @Test void alquilerRequiereTotalExactoYNoUsaLaRentaAislada() {
        var q=consulta(presupuesto(OperadorCriterio.MAXIMO,"2500",null,ALQUILER));
        var total=precio("2800","CFG",ALQUILER,conocido());
        assertEquals("MENSUAL",alternativa(q,oferta("A",List.of(total)),bd("3000"),"PEN").orElseThrow().periodo());
        var renta=new Dato("ENCARGO:precio_alquiler",TipoDato.IMPORTE,importe("2800","PEN"),null,false,
                new ContextoValor("PRECIO_ALQUILER",null,"PEN","MENSUAL","ALQUILER","CFG"),conocido());
        assertTrue(alternativa(q,oferta("A",List.of(renta)),bd("3000"),"PEN").isEmpty());
        var cota=new Dato(TOTAL_MENSUAL_OBLIGATORIO,TipoDato.IMPORTE,importe("2800","PEN"),null,true,total.contexto(),
                new MetadatosDato(EstadoDato.CONOCIDO,Causa.DATO_FALTANTE,null,null,null,null,null,null,null));
        assertTrue(alternativa(q,oferta("A",List.of(cota,renta)),bd("3000"),"PEN").isEmpty());
    }
    @Test void contextoIncompatibleNoSeConvierteNiSeCompleta() {
        var q=consultaVenta();var o=venta("A","2800",0);
        for(var contexto:List.of(new ContextoValor("PRECIO_VENTA",null,"USD",null,"VENTA",null),
                new ContextoValor("PRECIO_ALQUILER",null,"PEN",null,"VENTA",null),
                new ContextoValor("PRECIO_VENTA",null,"PEN","MENSUAL","VENTA",null),
                new ContextoValor("PRECIO_VENTA",null,"PEN",null,"ALQUILER",null))) {
            var d=precio("2800",o.clave().configuracion(),contexto,conocido());
            assertTrue(alternativa(q,reemplazar(o,PRECIO_VENTA,d),null,null).isEmpty());
        }
    }
    @Test void intervaloPuntualNoPuedeMezclarLaMonedaDelOtroExtremo() {
        var q=consultaVenta();var o=venta("A","2800",0);
        var d=new Dato(PRECIO_VENTA,TipoDato.IMPORTE,importe("2800","PEN"),importe("2800","USD"),true,
                o.datos().getFirst().contexto(),conocido());
        assertTrue(alternativa(q,reemplazar(o,PRECIO_VENTA,d),null,null).isEmpty());
    }
    @Test void validaLimiteAunqueNoHayaCandidatas() {
        var q=consultaVenta();
        for(var monto:List.of("2400","2500"))assertThrows(ReglaNegocioException.class,()->selector.validarExploracion(q,bd(monto),"PEN"));
        assertThrows(ReglaNegocioException.class,()->selector.validarExploracion(q,bd("3000"),null));
        assertThrows(ReglaNegocioException.class,()->selector.validarExploracion(q,null,"PEN"));
        assertThrows(ReglaNegocioException.class,()->selector.validarExploracion(q,bd("3000"),"USD"));
        assertThrows(ReglaNegocioException.class,()->selector.validarExploracion(q,bd("3000"),"$"));
        assertDoesNotThrow(()->selector.validarExploracion(q,null,null));
        assertDoesNotThrow(()->selector.validarExploracion(q,bd("3000"),"S/."));
        assertThrows(ReglaNegocioException.class,()->selector.validarExploracion(consulta(),null,null));
        assertThrows(ReglaNegocioException.class,()->selector.validarExploracion(consulta(),bd("3000"),"PEN"));
    }
    @Test void porcentajeEsDescriptivoYNoDividePorCero() {
        var cero=consulta(presupuesto(OperadorCriterio.MAXIMO,"0",null,VENTA));
        assertNull(alternativa(cero,venta("A","100",0),null,null).orElseThrow().excesoPorcentaje());
        var tres=consulta(presupuesto(OperadorCriterio.MAXIMO,"3",null,VENTA));
        assertEquals(new BigDecimal("33.33333333"),alternativa(tres,venta("A","4",0),null,null).orElseThrow().excesoPorcentaje());
    }
    @Test void noReutilizaEvaluacionDeOtraConsultaOfertaOVersion() {
        var q=consultaVenta();var o=venta("A","2800",0);var e=motor.evaluar(q,o);
        var otraConsulta=new Consulta("OTRA",q.version(),q.modalidad(),q.criterios(),CORTE,REGLA);
        assertThrows(ReglaNegocioException.class,()->selector.evaluar(otraConsulta,o,e,null,null));
        assertThrows(ReglaNegocioException.class,()->selector.evaluar(q,venta("B","2800",0),e,null,null));
        var version=new Oferta(o.referenciaEstable(),o.clave(),o.version()+1,true,o.habilitacion(),o.disponibilidad(),o.datos());
        assertThrows(ReglaNegocioException.class,()->selector.evaluar(q,version,e,null,null));
    }
    @Test void escalonesSonPreciosRealesYSegundoExigeMasSNoSoloMenosU() {
        var q=consultaVenta();
        var primero=alternativa(q,sin(venta("A","2600",1),"TEST:p2"),null,null).orElseThrow(); // S3/U2
        var menosU=alternativa(q,venta("B","2700",1),null,null).orElseThrow(); // S3/U0
        var masS=alternativa(q,venta("C","2800",3),null,null).orElseThrow(); // S5
        var caro=alternativa(q,venta("D","2900",7),null,null).orElseThrow(); // S6
        assertEquals(List.of(primero,masS),selector.escalones(List.of(caro,menosU,masS,primero)));
        assertEquals(List.of(primero),selector.escalones(List.of(menosU,primero)));
        assertTrue(selector.escalones(List.of()).isEmpty());
    }
    @Test void empateDePrecioUsaSDespuesUYReferenciaYNoElOrdenDeEntrada() {
        var q=consultaVenta();
        var a=alternativa(q,venta("A","2600",1),null,null).orElseThrow();
        var b=alternativa(q,venta("B","2600",1),null,null).orElseThrow();
        var pendiente=alternativa(q,sin(venta("0","2600",1),"TEST:p2"),null,null).orElseThrow();
        var menorS=alternativa(q,venta("MENOR_S","2600",2),null,null).orElseThrow();
        assertEquals(List.of(a),selector.escalones(List.of(b,menorS,pendiente,a)));
    }
    @Test void unMejorSAlMismoPrecioSeEligeSinInventarSegundoEscalon() {
        var q=consultaVenta();
        var menor=alternativa(q,venta("A","2600",1),null,null).orElseThrow();
        var mejor=alternativa(q,venta("B","2600",3),null,null).orElseThrow();
        assertEquals(List.of(mejor),selector.escalones(List.of(menor,mejor)));
    }
    @Test void aleatorizacionDeCandidatosYDatosConservaLosDosEscalonesEsperados() {
        var q=consultaVenta();var aleatorio=new Random(0xA17E2026L);
        for(int vuelta=0;vuelta<64;vuelta++) {
            var alternativas=new ArrayList<AlternativasPrecioMatching.Alternativa>();
            for(var o:List.of(venta("A","2600",1),venta("B","2600",1),venta("C","2700",2),venta("D","2800",3),venta("E","2900",7))) {
                var datos=new ArrayList<>(o.datos());Collections.shuffle(datos,aleatorio);
                alternativas.add(alternativa(q,conDatos(o,datos),null,null).orElseThrow());
            }
            Collections.shuffle(alternativas,aleatorio);
            assertEquals(List.of("A","D"),selector.escalones(alternativas).stream().map(a->a.original().referenciaEstable()).toList());
        }
    }
    @Test void escalonesRechazanMezclaDeConsultasReglasCortesPreferenciasOContextoMonetario() {
        var q=consultaVenta();var a=alternativa(q,venta("A","2600",1),null,null).orElseThrow();
        var variaciones=List.of(new Consulta("OTRA",q.version(),q.modalidad(),q.criterios(),CORTE,REGLA),
                new Consulta(q.referencia(),q.version()+1,q.modalidad(),q.criterios(),CORTE,REGLA),
                new Consulta(q.referencia(),q.version(),q.modalidad(),q.criterios(),CORTE,"OTRA_REGLA"),
                new Consulta(q.referencia(),q.version(),q.modalidad(),q.criterios(),CORTE.plusSeconds(1),REGLA),
                consulta(presupuesto(OperadorCriterio.MAXIMO,"2500",null,VENTA)),
                consulta(presupuesto(OperadorCriterio.MAXIMO,"2400",null,VENTA),q.criterios().subList(1,q.criterios().size()).toArray(Criterio[]::new)));
        for(var otra:variaciones) {
            var b=alternativa(otra,venta("B","2800",3),null,null).orElseThrow();
            assertThrows(ReglaNegocioException.class,()->selector.escalones(List.of(a,b)));
        }
        var alquiler=consulta(presupuesto(OperadorCriterio.MAXIMO,"2500",null,ALQUILER));
        var b=alternativa(alquiler,oferta("R",List.of(precio("2800","CFG",ALQUILER,conocido()))),null,null).orElseThrow();
        assertThrows(ReglaNegocioException.class,()->selector.escalones(List.of(a,b)));
    }
    @Test void elContextoMonetarioNoSeMezclaAunqueLosPuntajesSeanDeLaMismaConsulta() {
        var q=consultaVenta();var a=alternativa(q,venta("A","2600",1),null,null).orElseThrow();
        for(var b:List.of(new AlternativasPrecioMatching.Alternativa(a.original(),a.precio(),a.maximoOriginal(),
                        "USD",a.concepto(),a.periodo(),a.excesoAbsoluto(),a.excesoPorcentaje()),
                new AlternativasPrecioMatching.Alternativa(a.original(),a.precio(),a.maximoOriginal(),
                        a.moneda(),"TOTAL_MENSUAL_OBLIGATORIO","MENSUAL",a.excesoAbsoluto(),a.excesoPorcentaje())))
            assertThrows(ReglaNegocioException.class,()->selector.escalones(List.of(a,b)));
    }
    @Test void duplicadoConPrecioOVersionIncompatibleNoEligeLaVarianteFavorable() {
        var q=consultaVenta();var a=alternativa(q,venta("A","2600",1),null,null).orElseThrow();
        var b=alternativa(q,venta("A","2700",3),null,null).orElseThrow();
        assertThrows(ReglaNegocioException.class,()->selector.escalones(List.of(a,b)));
        assertEquals(List.of(a),selector.escalones(List.of(a,a)));
    }

    private Optional<AlternativasPrecioMatching.Alternativa> alternativa(Consulta q,Oferta o,BigDecimal limite,String moneda) {
        return selector.evaluar(q,o,motor.evaluar(q,o),limite,moneda);
    }
    private static Consulta consultaVenta() {
        return consulta(presupuesto(OperadorCriterio.MAXIMO,"2500",null,VENTA),obligatoria(),
                preferencia("TEST:p1",Prioridad.IMPORTANTE),preferencia("TEST:p2",Prioridad.NORMAL),preferencia("TEST:p3",Prioridad.COMPLEMENTARIA));
    }
    private static Consulta consulta() {
        return new Consulta("demanda:TEST",4,Modalidad.COMERCIAL,List.of(),CORTE,REGLA);
    }
    private static Consulta consulta(Criterio primero,Criterio... otros) {
        var criterios=new ArrayList<Criterio>();criterios.add(primero);criterios.addAll(List.of(otros));
        return new Consulta("demanda:TEST",4,Modalidad.COMERCIAL,criterios,CORTE,REGLA);
    }
    private static Criterio presupuesto(OperadorCriterio operador,String bajo,String alto,ContextoValor contexto) {
        String selector="VENTA".equals(contexto.operacion())?PRECIO_VENTA:TOTAL_MENSUAL_OBLIGATORIO;
        return new Criterio(BASE,BASE,Exigencia.OBLIGATORIO,null,List.of(new Condicion(selector,TipoDato.IMPORTE,operador,
                importe(bajo,contexto.moneda()),importe(alto,contexto.moneda()),false,contexto,conocido())));
    }
    private static Criterio obligatoria() {
        return new Criterio("TEST:obligatoria","TEST:obligatoria",Exigencia.OBLIGATORIO,null,List.of(
                new Condicion("TEST:obligatoria",TipoDato.BOOLEANO,OperadorCriterio.VERDADERO,ValorLogico.deBooleano(true),null,false,SIMPLE,conocido())));
    }
    private static Criterio preferencia(String clave,Prioridad prioridad) {
        return new Criterio(clave,clave,Exigencia.PREFERENCIA,prioridad,List.of(new Condicion(clave,TipoDato.BOOLEANO,
                OperadorCriterio.VERDADERO,ValorLogico.deBooleano(true),null,false,SIMPLE,conocido())));
    }
    private static Oferta venta(String referencia,String monto,int mascara) {
        String cfg="CFG";var datos=new ArrayList<Dato>();datos.add(precio(monto,cfg,VENTA,conocido()));
        datos.add(bool("TEST:obligatoria",true,cfg));
        for(int i=0;i<3;i++)datos.add(bool("TEST:p"+(i+1),(mascara&(1<<i))!=0,cfg));
        return oferta(referencia,datos);
    }
    private static Oferta oferta(String referencia,List<Dato> datos) {
        return new Oferta(referencia,new ClaveCandidato("TENANT_TEST","ENCARGO:"+referencia,"CFG"),2,true,CUMPLE,
                new ConfirmacionDisponibilidad(CORTE.minusSeconds(60),false,"CONF_TEST"),datos);
    }
    private static Dato precio(String monto,String cfg,ContextoValor contexto,MetadatosDato meta) {
        var c=new ContextoValor(contexto.concepto(),contexto.unidad(),contexto.moneda(),contexto.periodo(),contexto.operacion(),cfg);
        String selector="TOTAL_MENSUAL_OBLIGATORIO".equals(c.concepto())?TOTAL_MENSUAL_OBLIGATORIO:PRECIO_VENTA;
        return new Dato(selector,TipoDato.IMPORTE,importe(monto,c.moneda()),null,false,c,meta);
    }
    private static Dato bool(String clave,boolean valor,String cfg) {
        return new Dato(clave,TipoDato.BOOLEANO,ValorLogico.deBooleano(valor),null,false,
                new ContextoValor(null,null,null,null,"VENTA",cfg),conocido());
    }
    private static Oferta reemplazar(Oferta o,String clave,Dato dato) {
        var datos=new ArrayList<>(o.datos().stream().filter(d->!d.clave().equals(clave)).toList());datos.add(dato);return conDatos(o,datos);
    }
    private static Oferta sin(Oferta o,String clave) {return conDatos(o,o.datos().stream().filter(d->!d.clave().equals(clave)).toList());}
    private static Oferta conDatos(Oferta o,List<Dato> datos) {
        return new Oferta(o.referenciaEstable(),o.clave(),o.version(),o.accesoAutorizado(),o.habilitacion(),o.disponibilidad(),datos);
    }
    private static MetadatosDato conocido() {return new MetadatosDato(EstadoDato.CONOCIDO,null,null,null,null,"FUENTE_TEST",null,null,null);}
    private static ValorLogico importe(String valor,String moneda) {return valor==null?null:ValorLogico.deImporte(bd(valor),moneda);}
    private static BigDecimal bd(String valor) {return new BigDecimal(valor);}
    private static void igual(String esperado,BigDecimal actual) {assertNotNull(actual);assertEquals(0,bd(esperado).compareTo(actual));}
}
