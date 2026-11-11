package com.controllocal.domain.planes;

import com.controllocal.domain.presupuesto.PrecisionCapacidad;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Dinero de la oferta, separado de capacidad IA y consumo. Directriz7.1. */
public record CotizacionDePeriodo(BigDecimal periodoNuevoUsd,BigDecimal abonoTemporalUsd,BigDecimal totalUsd){
    public record LineaFinanciada(BigDecimal importeUsd,Instant desde,Instant hasta){}
    public static CotizacionDePeriodo mejoraConLineas(BigDecimal nuevo,List<LineaFinanciada> lineas,Instant corte){
        if(nuevo==null||nuevo.signum()<=0||lineas==null||lineas.isEmpty())throw new IllegalArgumentException("Período y líneas financiadas requeridos.");
        BigDecimal sinRedondear=BigDecimal.ZERO;
        for(var linea:lineas){
            if(linea==null)throw new IllegalArgumentException("Línea financiada requerida.");
            validar(linea.importeUsd(),linea.desde(),linea.hasta(),corte);
            sinRedondear=sinRedondear.add(linea.importeUsd().multiply(fraccion(linea.desde(),linea.hasta(),corte)));
        }
        BigDecimal abono=PrecisionCapacidad.usd(sinRedondear);
        BigDecimal nuevoMonetario=PrecisionCapacidad.usd(nuevo);
        if(abono.compareTo(nuevoMonetario)>0)throw new IllegalArgumentException("Abono superior al período.");
        return new CotizacionDePeriodo(nuevoMonetario,abono,nuevoMonetario.subtract(abono));
    }
    public static BigDecimal brokerAdicional(Instant desde,Instant hasta,Instant corte){
        validar(PlanBrox.precioBrokerExtraUsd(),desde,hasta,corte);
        return PrecisionCapacidad.usd(PlanBrox.precioBrokerExtraUsd().multiply(fraccion(desde,hasta,corte)));
    }
    private static BigDecimal fraccion(Instant desde,Instant hasta,Instant corte){
        return PrecisionCapacidad.dividir(segundos(Duration.between(corte,hasta)),segundos(Duration.between(desde,hasta)));
    }
    private static BigDecimal segundos(Duration d){return BigDecimal.valueOf(d.getSeconds()).add(BigDecimal.valueOf(d.getNano(),9));}
    private static void validar(BigDecimal financiado,Instant desde,Instant hasta,Instant corte){
        if(financiado==null||financiado.signum()<0||desde==null||hasta==null||corte==null||!hasta.isAfter(desde)
                ||corte.isBefore(desde)||corte.isAfter(hasta))throw new IllegalArgumentException("Período financiado inválido.");
    }
}
