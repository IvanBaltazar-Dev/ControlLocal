package com.controllocal.service.soporte;
import java.math.*;
import java.time.*;
import java.util.*;

/** RF076: aritmética sobre conteos y calendario declarados; sin umbrales, ranking o predicción. */
public record AvanceDeMeta(Long actual,long realizadoConocido,Long metaPeriodo,Long faltante,BigDecimal porcentajeMeta,
                           BigDecimal esperadoHastaFechaCorte,BigDecimal necesarioPorFechaRestante,
                           Integer fechasDeclaradas,Integer fechasHastaCorte,Integer fechasRestantes,String estadoCalculo) {
    public static AvanceDeMeta de(Long actual,long conocidos,Long meta,List<LocalDate> calendario,LocalDate corte){
        if(conocidos<0||actual!=null&&actual<0||meta!=null&&meta<0)throw new IllegalArgumentException("Conteos no negativos requeridos.");
        Long falta=actual==null||meta==null?null:Math.max(0,meta-actual);
        BigDecimal porcentaje=actual==null||meta==null||meta==0?null:BigDecimal.valueOf(actual).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(meta),2,RoundingMode.HALF_UP);
        if(actual==null)return new AvanceDeMeta(null,conocidos,meta,null,null,null,null,null,null,null,"SIN_EVIDENCIA_SUFICIENTE");
        if(meta==null)return new AvanceDeMeta(actual,conocidos,null,null,null,null,null,null,null,null,"SIN_META");
        if(calendario==null||calendario.isEmpty())return new AvanceDeMeta(actual,conocidos,meta,falta,porcentaje,null,null,null,null,null,"SIN_CALENDARIO_DECLARADO");
        if(calendario.stream().anyMatch(Objects::isNull)||new HashSet<>(calendario).size()!=calendario.size())throw new IllegalArgumentException("Calendario inválido.");
        int total=calendario.size(),pasadas=(int)calendario.stream().filter(d->!d.isAfter(corte)).count(),restantes=total-pasadas;
        var esperado=BigDecimal.valueOf(meta).multiply(BigDecimal.valueOf(pasadas)).divide(BigDecimal.valueOf(total),4,RoundingMode.HALF_UP);
        var ritmo=restantes==0?null:BigDecimal.valueOf(falta).divide(BigDecimal.valueOf(restantes),4,RoundingMode.HALF_UP);
        return new AvanceDeMeta(actual,conocidos,meta,falta,porcentaje,esperado,ritmo,total,pasadas,restantes,restantes==0?"CALENDARIO_TERMINADO":"CON_CALENDARIO_DECLARADO");
    }
}
