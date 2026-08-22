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
        CondicionEconomicaCaptacion condicion = condicion("A", "P", "R", "25.00", "USD", "USD");
        assertEquals(new BigDecimal("1800.00"),
                CalculadoraComision.calcular(condicion, RENTA, null));
    }

    @Test
    void calculaPorcentajeSobreVenta() {
        CondicionEconomicaCaptacion condicion = condicion("V", "P", "V", "3.00", "USD", "USD");
        assertEquals(new BigDecimal("15000.00"), CalculadoraComision.calcular(
                condicion, null, new BigDecimal("500000.00")));
    }

    @Test
    void calculaMontoFijoEnSuMoneda() {
        CondicionEconomicaCaptacion condicion = condicion("A", "F", "N", "3000.00", "PEN", "USD");
        assertEquals(new BigDecimal("3000.00"),
                CalculadoraComision.calcular(condicion, null, null));
    }

    @Test
    void montoFijoExigeMoneda() {
        CondicionEconomicaCaptacion condicion = condicion("A", "F", "N", "3000.00", "PEN", null);
        assertThrows(ReglaNegocioException.class,
                () -> CalculadoraComision.calcular(condicion, null, null));
    }

    @Test
    void rechazaCombinacionTipoBaseInvalida() {
        CondicionEconomicaCaptacion condicion = condicion("A", "E", "V", "1.00", "USD", "USD");
        assertThrows(ReglaNegocioException.class,
                () -> CalculadoraComision.calcular(condicion, RENTA, new BigDecimal("500000")));
    }

    /**
     * <b>La combinacion tiene que caber en la operacion</b> (V76).
     *
     * <p>Las dos que se prueban aqui pasaban antes: la tabla de combinaciones
     * validas miraba tipo y base entre si, pero no contra la operacion del
     * encargo. Una venta comisionada «un mes de alquiler» multiplicaba el
     * precio de venta entero por uno, y un alquiler con base «precio de venta»
     * se apoyaba en un importe que ese encargo nunca pacto.
     */
    @Test
    void unaVentaNoSeComisionaEnMensualidadesNiSobreUnaRenta() {
        assertThrows(ReglaNegocioException.class, () -> CalculadoraComision.calcular(
                condicion("V", "E", "R", "1.00", "USD", "USD"), RENTA, null));
        assertThrows(ReglaNegocioException.class, () -> CalculadoraComision.calcular(
                condicion("V", "P", "R", "3.00", "USD", "USD"), RENTA, null));
    }

    @Test
    void unAlquilerNoSeComisionaSobreUnPrecioDeVenta() {
        assertThrows(ReglaNegocioException.class, () -> CalculadoraComision.calcular(
                condicion("A", "P", "V", "3.00", "USD", "USD"), null, new BigDecimal("500000")));
    }

    /** Sin operacion no se sabe sobre que importe se apoya: se dice, no se supone. */
    @Test
    void unaCondicionSinOperacionNoSeCalcula() {
        assertThrows(ReglaNegocioException.class, () -> CalculadoraComision.calcular(
                condicion(null, "P", "R", "25.00", "USD", "USD"), RENTA, null));
    }

    @Test
    void rechazaMonedasDistintasEnComisionDerivada() {
        CondicionEconomicaCaptacion condicion = condicion("A", "P", "R", "25.00", "USD", "PEN");
        assertThrows(ReglaNegocioException.class,
                () -> CalculadoraComision.calcular(condicion, RENTA, null));
    }

    private static BigDecimal calcularEquivalente(String valor) {
        return CalculadoraComision.calcular(
                condicion("A", "E", "R", valor, "USD", "USD"), RENTA, null);
    }

    private static CondicionEconomicaCaptacion condicion(String operacion, String tipo, String base,
                                                          String valor, String monedaBase,
                                                          String monedaComision) {
        CondicionEconomicaCaptacion condicion = new CondicionEconomicaCaptacion();
        condicion.setTipoOperacion(operacion);
        condicion.setTipoComision(tipo);
        condicion.setBaseCalculo(base);
        condicion.setValorComision(new BigDecimal(valor));
        condicion.setMonedaReferencia(monedaBase);
        condicion.setMonedaComision(monedaComision);
        return condicion;
    }
}
