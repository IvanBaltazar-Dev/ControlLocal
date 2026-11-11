package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.service.ComisionService.Calculo;
import com.controllocal.service.ComisionService.Dinero;
import com.controllocal.service.encargo.PactosDeEncargo.ReglaComision;
import com.controllocal.service.formalizacion.HitosConfirmados.FuenteEconomicaFinal;
import com.controllocal.service.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/** RF063/Spec8: única aritmética. Porcentaje fraccionario; HALF_UP2 vigente. */
public final class CalculadoraComision {
    private CalculadoraComision() { }

    public static Calculo calcular(ReglaComision regla, OperacionInmobiliaria operacion,
                                    FuenteEconomicaFinal fuente) {
        if(regla==null || operacion==null)
            throw new ReglaNegocioException("Identifica el pacto y su operación.");
        String tipo=regla.tipoComision(), base=regla.baseCalculo();
        BigDecimal valor=regla.valorComision();
        if(valor==null || valor.signum()<0)
            throw new ReglaNegocioException("El valor pactado de comisión debe ser conocido y no negativo.");
        if(!Set.of("P","E","F").contains(tipo==null?"":tipo)
                || !("F".equals(tipo)?"N".equals(base):
                    "E".equals(tipo)?"R".equals(base):Set.of("R","V").contains(base==null?"":base)))
            throw new ReglaNegocioException("La combinación de tipo y base de comisión es inválida.");
        CondicionesEconomicas.exigirBaseCoherente(operacion.codigo(),tipo,base);
        String moneda=CondicionesEconomicas.moneda(regla.monedaComision(),"de la comisión pactada");
        if(valor.signum()==0 && (regla.motivoSinComision()==null || regla.motivoSinComision().isBlank()))
            throw new ReglaNegocioException("La comisión cero conserva su motivo pactado.");
        if("F".equals(tipo)||valor.signum()==0)
            return conocido(valor,moneda);
        var dinero=fuente==null?null:("R".equals(base)?fuente.rentaMensual():fuente.precioVenta());
        if(dinero==null || dinero.importe()==null)
            return pendiente(moneda,"R".equals(base)?"RENTA_MENSUAL_FINAL":"PRECIO_VENTA_FINAL");
        if(dinero.importe().signum()<=0)
            throw new ReglaNegocioException("La base final de comisión debe ser mayor que cero.");
        if(dinero.moneda()==null || dinero.moneda().isBlank())
            return pendiente(moneda,"MONEDA_BASE_FINAL");
        String monedaBase=CondicionesEconomicas.moneda(dinero.moneda(),"de la base final");
        if(!moneda.equals(monedaBase))
            return pendiente(moneda,"MONEDA_BASE_FINAL_NO_COMPARABLE");
        // La fuente tipada distingue renta mensual contractual de cargos/total mensual.
        // P ya es una fracción: 0.03 × 500000 = 15000; no dividir otra vez entre100.
        return conocido(dinero.importe().multiply(valor),moneda);
    }

    private static Calculo conocido(BigDecimal valor,String moneda) {
        return new Calculo(new Dinero(valor.setScale(2,RoundingMode.HALF_UP),moneda),List.of());
    }
    private static Calculo pendiente(String moneda,String causa) {
        return new Calculo(new Dinero(null,moneda),List.of(causa));
    }
}
