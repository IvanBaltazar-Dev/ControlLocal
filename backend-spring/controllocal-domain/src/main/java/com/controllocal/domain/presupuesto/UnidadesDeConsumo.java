package com.controllocal.domain.presupuesto;

import java.math.BigDecimal;
import java.util.*;

/** Mediciones originales conservadas. Un subconjunto (p.ej. caché) no se suma dos veces. */
public final class UnidadesDeConsumo {
    private UnidadesDeConsumo(){}
    public record Medicion(String clase,String unidad,BigDecimal cantidad,String subconjuntoDe){}
    public record Tarifa(String clase,String unidad,BigDecimal importeUsd,BigDecimal porCantidad){}
    public static BigDecimal costoSinCuantizar(List<Medicion> mediciones,List<Tarifa> tarifas){
        if(mediciones==null||mediciones.isEmpty()||tarifas==null)throw new IllegalArgumentException("Medición/tarifa requerida.");
        Map<String,Medicion> m=new LinkedHashMap<>();
        for(var x:mediciones){
            if(x==null||x.clase()==null||x.clase().isBlank()||x.unidad()==null||x.unidad().isBlank()||x.cantidad()==null
                    ||x.cantidad().signum()<0||m.putIfAbsent(x.clase(),x)!=null)throw new IllegalArgumentException("Medición inválida o repetida.");
        }
        Map<String,Tarifa> t=new HashMap<>();
        for(var x:tarifas){
            if(x==null||x.clase()==null||x.unidad()==null||x.importeUsd()==null||x.importeUsd().signum()<0
                    ||x.porCantidad()==null||x.porCantidad().signum()<=0||t.putIfAbsent(x.clase(),x)!=null)
                throw new IllegalArgumentException("Tarifa inválida o repetida.");
        }
        BigDecimal total=BigDecimal.ZERO;
        for(var x:m.values()){
            Set<String> visitados=new HashSet<>();Medicion actual=x;
            while(actual.subconjuntoDe()!=null){
                if(!visitados.add(actual.clase()))throw new IllegalArgumentException("Subconjuntos circulares.");
                Medicion padre=m.get(actual.subconjuntoDe());
                if(padre==null||!padre.unidad().equals(actual.unidad()))throw new IllegalArgumentException("Subconjunto sin medición compatible.");
                actual=padre;
            }
            BigDecimal cantidad=x.cantidad();
            for(var hija:m.values())if(x.clase().equals(hija.subconjuntoDe()))cantidad=cantidad.subtract(hija.cantidad());
            if(cantidad.signum()<0)throw new IllegalArgumentException("Subconjuntos mayores que la medición original.");
            Tarifa precio=t.get(x.clase());
            if(precio==null||!precio.unidad().equals(x.unidad()))throw new IllegalArgumentException("Falta tarifa para la unidad medida.");
            total=total.add(PrecisionCapacidad.dividir(cantidad.multiply(precio.importeUsd()),precio.porCantidad()));
        }
        return total;
    }
    public static BigDecimal costoLiquidado(List<Medicion> m,List<Tarifa> t){return PrecisionCapacidad.liquidar(costoSinCuantizar(m,t));}
    public static BigDecimal creditosLiquidados(List<Medicion> m,List<Tarifa> t,BigDecimal normalizador){
        return PrecisionCapacidad.liquidar(PrecisionCapacidad.dividir(costoSinCuantizar(m,t),normalizador));
    }
}
