package com.controllocal.domain.presupuesto;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Directriz8.9: una liquidación final; no redondear cada fragmento ni usar double. */
public final class PrecisionCapacidad {
    private PrecisionCapacidad(){}
    public static final MathContext DIVISION=new MathContext(50,RoundingMode.HALF_EVEN);
    public static final BigDecimal CERO=BigDecimal.ZERO.setScale(12);
    public static BigDecimal liquidar(BigDecimal valor){
        if(valor==null||valor.signum()<0)throw new IllegalArgumentException("Importe no negativo requerido.");
        BigDecimal n=valor.setScale(12,RoundingMode.HALF_EVEN);
        if(n.precision()>38)throw new IllegalArgumentException("Importe fuera de precisión.");
        return n;
    }
    public static BigDecimal usd(BigDecimal valor){
        if(valor==null||valor.signum()<0)throw new IllegalArgumentException("Importe no negativo requerido.");
        return valor.setScale(2,RoundingMode.HALF_UP);
    }
    public static BigDecimal dividir(BigDecimal a,BigDecimal b){
        if(a==null||b==null||a.signum()<0||b.signum()<=0)throw new IllegalArgumentException("División inválida.");
        return a.divide(b,DIVISION);
    }
}
