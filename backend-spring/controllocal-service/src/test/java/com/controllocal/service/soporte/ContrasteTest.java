package com.controllocal.service.soporte;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El contraste, y sobre todo <b>su degradacion</b>.
 *
 * <p>Con los datos del 2026-08-19 el camino que se recorre siempre es el de la
 * degradacion, asi que es el que mas pruebas lleva. No es defensivo: es el
 * comportamiento principal mientras la cartera no tenga muestra.
 */
class ContrasteTest {

    private static BigDecimal soles(int monto) {
        return BigDecimal.valueOf(monto);
    }

    // ------------------------------------------------------------------
    // Cuando hay muestra
    // ------------------------------------------------------------------

    @Test
    @DisplayName("con rango real, situa el valor dentro y dice de cuantas propiedades sale")
    void unRangoConMuestraSituaElValor() {
        Contraste c = Contraste.enRango(soles(3200), soles(5100), soles(4500), "PEN",
                "Miraflores", "100 a 200 m2", 12);

        assertEquals(Contraste.Forma.POSICION_EN_RANGO, c.forma());
        assertEquals(Contraste.Motivo.NINGUNO, c.motivo());
        assertEquals(68, c.posicionPorcentaje(), "(4500-3200)/(5100-3200) = 68 %");
        assertEquals(12, c.observaciones());
        assertTrue(c.concluye());
    }

    @Test
    @DisplayName("una renta por encima del rango propio se pega al borde, y su importe viaja")
    void unValorFueraDelRangoNoRompe() {
        Contraste c = Contraste.enRango(soles(3200), soles(5100), soles(9000), "PEN",
                "Miraflores", "100 a 200 m2", 12);

        assertEquals(100, c.posicionPorcentaje(),
                "Estar fuera del rango es informacion, no un error de calculo.");
        assertEquals(soles(9000), c.valor(), "El importe real se conserva.");
    }

    @Test
    @DisplayName("con todas las observaciones al mismo importe no hay posicion que dar")
    void unRangoDeAnchoCeroNoTienePosicion() {
        Contraste c = Contraste.enRango(soles(7000), soles(7000), soles(7000), "PEN",
                "Miraflores", "100 a 200 m2", 12);

        assertNull(c.posicionPorcentaje(),
                "Decir 'en el 0 %' o 'en el 100 %' de un rango sin ancho es un numero "
                        + "sin significado.");
    }

    // ------------------------------------------------------------------
    // Cuando no hay muestra, que es el caso de hoy
    // ------------------------------------------------------------------

    @Test
    @DisplayName("con pocas observaciones no hay rango, y la N se conserva")
    void conPocaMuestraSeDegradaPeroSeDiceCuanta() {
        Contraste c = Contraste.sinReferenciaSuficiente("Miraflores", "100 a 200 m2", 4);

        assertEquals(Contraste.Forma.NINGUNA, c.forma());
        assertEquals(Contraste.Motivo.SIN_REFERENCIA_INTERNA_SUFICIENTE, c.motivo());
        assertEquals(4, c.observaciones(),
                "'4 propiedades' informa; 'sin datos' no dice si falta poco o todo.");
        assertNull(c.minimo());
        assertNull(c.maximo());
        assertFalse(c.concluye());
    }

    @Test
    @DisplayName("sin ninguna observacion el motivo es otro: falta el hecho, no el volumen")
    void ceroObservacionesEsUnMotivoDistinto() {
        Contraste c = Contraste.sinReferenciaSuficiente("Barranco", "50 a 100 m2", 0);

        assertEquals(Contraste.Motivo.SIN_OBSERVACIONES, c.motivo());
        assertEquals(0, c.observaciones());
    }

    @Test
    @DisplayName("sin zona o sin metraje no se sabe contra que grupo comparar")
    void sinGrupoComparableNoSeInventaUno() {
        Contraste c = Contraste.sinGrupoComparable();

        assertEquals(Contraste.Motivo.SIN_GRUPO_COMPARABLE, c.motivo());
        assertNull(c.zona());
        assertFalse(c.concluye());
    }

    /**
     * El umbral que separa «esto es nuestra operacion» de «esto son dos casos».
     * Se fijo en diez tras medir la cartera el 2026-08-19: la mejor celda tenia
     * cuatro propiedades.
     */
    @Test
    @DisplayName("el rango nace a las diez propiedades distintas, ni una menos")
    void elUmbralDelRangoSaleDeLaPolitica() {
        assertEquals(10, PoliticaComercial.RANGO_MUESTRA_MINIMA.valor());
        assertFalse(PoliticaComercial.rangoPublicable(9));
        assertTrue(PoliticaComercial.rangoPublicable(10));
        assertFalse(PoliticaComercial.rangoPublicable(4),
                "La mejor celda medida en agosto de 2026 no llegaba.");
    }

    // ------------------------------------------------------------------
    // Las bandas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cada metraje cae en su tramo, con el limite inferior dentro")
    void lasBandasCortanDondeDiceLaPolitica() {
        assertEquals(BandaDeMetraje.HASTA_50, BandaDeMetraje.de(soles(49)));
        assertEquals(BandaDeMetraje.DE_50_A_100, BandaDeMetraje.de(soles(50)));
        assertEquals(BandaDeMetraje.DE_50_A_100, BandaDeMetraje.de(soles(99)));
        assertEquals(BandaDeMetraje.DE_100_A_200, BandaDeMetraje.de(soles(100)));
        assertEquals(BandaDeMetraje.DE_100_A_200, BandaDeMetraje.de(soles(199)));
        assertEquals(BandaDeMetraje.MAS_DE_200, BandaDeMetraje.de(soles(200)));
        assertEquals(BandaDeMetraje.MAS_DE_200, BandaDeMetraje.de(soles(5000)));
    }

    @Test
    @DisplayName("sin metraje no hay banda: adivinarla la pondria a competir con otro grupo")
    void sinMetrajeNoHayBanda() {
        assertNull(BandaDeMetraje.de(null));
        assertNull(BandaDeMetraje.de(BigDecimal.ZERO));
        assertNull(BandaDeMetraje.de(soles(-10)));
    }

    @Test
    @DisplayName("los cortes salen de la politica, no de un literal del enum")
    void losCortesTienenUnSoloDueno() {
        assertEquals(BigDecimal.valueOf(PoliticaComercial.BANDA_METRAJE_PEQUENO.valor()),
                BandaDeMetraje.DE_50_A_100.desde());
        assertEquals(BigDecimal.valueOf(PoliticaComercial.BANDA_METRAJE_ESTANDAR.valor()),
                BandaDeMetraje.DE_100_A_200.desde());
        assertEquals(BigDecimal.valueOf(PoliticaComercial.BANDA_METRAJE_GRANDE.valor()),
                BandaDeMetraje.MAS_DE_200.desde());
    }

    // ------------------------------------------------------------------
    // Las medias propias
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cero visitas realizadas no es cero propuestas por visita: es sin base")
    void sinDenominadorNoHayMedia() {
        MediasPropias.Media m = MediasPropias.proporcion(0, 0, "solicitudes", "visitas realizadas");

        assertFalse(m.concluye());
        assertNull(m.valor(), "Un cero aqui sonaria a reproche por trabajo que nadie registro.");
        assertEquals(0, m.base());
        assertTrue(m.descripcion().startsWith("Todavia no hay"));
    }

    @Test
    @DisplayName("con muestra corta tampoco concluye, pero se dice cuanta hay")
    void conMuestraCortaSeDiceLaN() {
        // Cuatro contratos con cronologia valida, y la muestra minima es cinco.
        MediasPropias.Media m = MediasPropias.magnitud(BigDecimal.valueOf(3), 4, "dias",
                "contratos firmados");

        assertFalse(m.concluye());
        assertEquals(4, m.base());
        assertTrue(m.descripcion().contains("4 contratos firmados"));
    }

    @Test
    @DisplayName("con muestra suficiente, la media se afirma y dice sobre cuantos casos")
    void conMuestraSuficienteSiConcluye() {
        MediasPropias.Media m = MediasPropias.proporcion(4, 12, "solicitudes",
                "visitas realizadas");

        assertTrue(m.concluye());
        assertEquals(0, m.valor().compareTo(new BigDecimal("0.33")));
        assertTrue(m.descripcion().contains("4 solicitudes cada 12 visitas realizadas"));
    }

    @Test
    @DisplayName("las tres medias degradan por separado")
    void cadaMediaDegradaPorSuCuenta() {
        MediasPropias medias = new MediasPropias(
                MediasPropias.proporcion(0, 0, "solicitudes", "visitas realizadas"),
                MediasPropias.magnitud(BigDecimal.valueOf(9), 20, "dias", "contratos firmados"),
                MediasPropias.proporcion(0, 0, "contactos", "recontactos registrados"));

        assertFalse(medias.propuestasPorVisita().concluye());
        assertTrue(medias.diasHastaContrato().concluye(),
                "Que falten las otras dos no invalida esta.");
        assertFalse(medias.plazoRealDeRecontacto().concluye());
    }
}
