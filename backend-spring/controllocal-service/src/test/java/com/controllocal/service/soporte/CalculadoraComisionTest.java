package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.CondicionEconomicaCaptacion;
import com.controllocal.service.excepcion.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculadoraComisionTest {

    private static final BigDecimal RENTA = new BigDecimal("7200.00");

    @Test
    void calculaMedioUnoUnoYMedioYDosMesesSinHeuristicas() {
        assertEquals(new BigDecimal("3600.00"), calcularEquivalente("0.50"));
        assertEquals(new BigDecimal("7200.00"), calcularEquivalente("1.00"));
        assertEquals(new BigDecimal("10800.00"), calcularEquivalente("1.50"));
        assertEquals(new BigDecimal("14400.00"), calcularEquivalente("2.00"));
    }

    @Test
    void calculaPorcentajeSobreRenta() {
        CondicionEconomicaCaptacion condicion = condicion("P", "R", "25.00", "USD", "USD");
        assertEquals(new BigDecimal("1800.00"),
                CalculadoraComision.calcular(condicion, RENTA, null));
    }

    @Test
    void calculaPorcentajeSobreVenta() {
        CondicionEconomicaCaptacion condicion = condicion("P", "V", "3.00", "USD", "USD");
        assertEquals(new BigDecimal("15000.00"), CalculadoraComision.calcular(
                condicion, null, new BigDecimal("500000.00")));
    }

    @Test
    void calculaMontoFijoEnSuMoneda() {
        CondicionEconomicaCaptacion condicion = condicion("F", "N", "3000.00", "PEN", "USD");
        assertEquals(new BigDecimal("3000.00"),
                CalculadoraComision.calcular(condicion, null, null));
    }

    @Test
    void montoFijoExigeMoneda() {
        CondicionEconomicaCaptacion condicion = condicion("F", "N", "3000.00", "PEN", null);
        assertThrows(ReglaNegocioException.class,
                () -> CalculadoraComision.calcular(condicion, null, null));
    }

    @Test
    void rechazaCombinacionTipoBaseInvalida() {
        CondicionEconomicaCaptacion condicion = condicion("E", "V", "1.00", "USD", "USD");
        assertThrows(ReglaNegocioException.class,
                () -> CalculadoraComision.calcular(condicion, RENTA, new BigDecimal("500000")));
    }

    @Test
    void rechazaMonedasDistintasEnComisionDerivada() {
        CondicionEconomicaCaptacion condicion = condicion("P", "R", "25.00", "USD", "PEN");
        assertThrows(ReglaNegocioException.class,
                () -> CalculadoraComision.calcular(condicion, RENTA, null));
    }

    private static BigDecimal calcularEquivalente(String valor) {
        return CalculadoraComision.calcular(
                condicion("E", "R", valor, "USD", "USD"), RENTA, null);
    }

    private static CondicionEconomicaCaptacion condicion(String tipo, String base,
                                                          String valor, String monedaBase,
                                                          String monedaComision) {
        CondicionEconomicaCaptacion condicion = new CondicionEconomicaCaptacion();
        condicion.setTipoComision(tipo);
        condicion.setBaseCalculo(base);
        condicion.setValorComision(new BigDecimal(valor));
        condicion.setMonedaReferencia(monedaBase);
        condicion.setMonedaComision(monedaComision);
        return condicion;
    }
}
