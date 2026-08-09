package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** Reglas transversales del contrato economico de alquiler. */
public final class CondicionesEconomicas {

    public static final BigDecimal COMISION_MINIMA = BigDecimal.ZERO;
    public static final BigDecimal COMISION_MAXIMA = new BigDecimal("200");
    public static final Set<String> MONEDAS = Set.of("PEN", "USD");

    private CondicionesEconomicas() {
    }

    public static BigDecimal comisionPactada(BigDecimal porcentaje) {
        if (porcentaje == null || porcentaje.compareTo(COMISION_MINIMA) < 0) {
            throw new ReglaNegocioException(
                    "La comision pactada es obligatoria y no puede ser negativa.");
        }
        if (porcentaje.compareTo(COMISION_MAXIMA) > 0) {
            throw new ReglaNegocioException(
                    "La comision pactada no puede superar 200 % de la renta mensual.");
        }
        return porcentaje;
    }

    public static String moneda(String valor, String concepto) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaNegocioException("La moneda " + concepto + " es obligatoria.");
        }
        String normalizada = valor.trim().toUpperCase(Locale.ROOT);
        if (!MONEDAS.contains(normalizada)) {
            throw new ReglaNegocioException("Valor invalido para moneda " + concepto + ": " + valor);
        }
        return normalizada;
    }
}
