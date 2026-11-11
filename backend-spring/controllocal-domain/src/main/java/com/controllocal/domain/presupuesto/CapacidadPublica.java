package com.controllocal.domain.presupuesto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Única proyección comercial: no transporta cuotas internas, fracciones o economía técnica. */
public record CapacidadPublica(int porcentajeMensual,boolean menosDeUnoPorCiento,long boostEntero,
                               boolean menosDeUnCredito,boolean financiacionDisponible){
    public static CapacidadPublica de(BigDecimal mensualDisponible,BigDecimal cuotaLegitima,BigDecimal boost){
        if(mensualDisponible==null||cuotaLegitima==null||boost==null||mensualDisponible.signum()<0||cuotaLegitima.signum()<0
                ||boost.signum()<0||mensualDisponible.compareTo(cuotaLegitima)>0)throw new IllegalArgumentException("Capacidad inválida.");
        BigDecimal porcentaje=cuotaLegitima.signum()==0?BigDecimal.ZERO:
                mensualDisponible.multiply(BigDecimal.valueOf(100)).divide(cuotaLegitima,0,RoundingMode.DOWN);
        int entero=porcentaje.intValueExact();long b=boost.setScale(0,RoundingMode.DOWN).longValueExact();
        return new CapacidadPublica(entero,mensualDisponible.signum()>0&&entero==0,b,boost.signum()>0&&b==0,
                mensualDisponible.signum()>0||boost.signum()>0);
    }
}
