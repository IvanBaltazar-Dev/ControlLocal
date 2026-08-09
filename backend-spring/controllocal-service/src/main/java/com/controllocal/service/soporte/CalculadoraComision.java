package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.service.excepcion.ReglaNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Aritmetica unica de comisiones; cada operando llega con tipo y base. */
public final class CalculadoraComision {
    private CalculadoraComision() { }

    public static BigDecimal calcular(CondicionEconomicaCaptacion condicion,
                                      BigDecimal renta, BigDecimal precioVenta) {
        if (condicion == null) throw new ReglaNegocioException("La condicion economica es obligatoria.");
        BigDecimal valor = condicion.getValorComision();
        if (valor == null || valor.signum() < 0) {
            throw new ReglaNegocioException("El valor de comision debe ser cero o positivo.");
        }
        validarTipoBaseYMoneda(condicion);
        BigDecimal resultado = switch (condicion.getTipoComision()) {
            case CondicionEconomicaCaptacion.EQUIVALENTE_MENSUALIDADES ->
                    base(renta, "renta mensual").multiply(valor);
            case CondicionEconomicaCaptacion.PORCENTAJE -> {
                BigDecimal base = CondicionEconomicaCaptacion.RENTA_MENSUAL.equals(condicion.getBaseCalculo())
                        ? base(renta, "renta mensual") : base(precioVenta, "precio de venta");
                yield base.multiply(valor).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
            }
            case CondicionEconomicaCaptacion.MONTO_FIJO -> valor;
            default -> throw new ReglaNegocioException("Tipo de comision invalido.");
        };
        return resultado.setScale(2, RoundingMode.HALF_UP);
    }

    private static void validarTipoBaseYMoneda(CondicionEconomicaCaptacion condicion) {
        String tipo = condicion.getTipoComision();
        String base = condicion.getBaseCalculo();
        boolean combinacionValida =
                CondicionEconomicaCaptacion.EQUIVALENTE_MENSUALIDADES.equals(tipo)
                        && CondicionEconomicaCaptacion.RENTA_MENSUAL.equals(base)
                || CondicionEconomicaCaptacion.PORCENTAJE.equals(tipo)
                        && (CondicionEconomicaCaptacion.RENTA_MENSUAL.equals(base)
                            || CondicionEconomicaCaptacion.PRECIO_VENTA.equals(base))
                || CondicionEconomicaCaptacion.MONTO_FIJO.equals(tipo)
                        && CondicionEconomicaCaptacion.NO_APLICA.equals(base);
        if (!combinacionValida) {
            throw new ReglaNegocioException("La combinacion de tipo y base de comision es invalida.");
        }
        if (CondicionEconomicaCaptacion.MONTO_FIJO.equals(tipo)) {
            CondicionesEconomicas.moneda(condicion.getMonedaComision(), "de la comision fija");
        } else if (condicion.getMonedaReferencia() == null
                || !condicion.getMonedaReferencia().equals(condicion.getMonedaComision())) {
            throw new ReglaNegocioException("La comision derivada debe usar la moneda de su base.");
        }
    }

    private static BigDecimal base(BigDecimal importe, String nombre) {
        if (importe == null || importe.signum() <= 0) {
            throw new ReglaNegocioException("La " + nombre + " debe ser mayor que cero.");
        }
        return importe;
    }
}
