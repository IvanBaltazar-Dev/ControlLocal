package com.controllocal.service.soporte;

import com.controllocal.service.soporte.HallazgoDeConcentracion.Aporte;
import com.controllocal.service.soporte.HallazgoDeConcentracion.Concentracion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El hallazgo del broker vale porque la media lo esconde</b> (D-E2-2 §9.1).
 *
 * <p>Lo que se prueba no es la frase: es <b>cuándo hay hallazgo y cuándo no</b>.
 * Un descubrimiento que aparece siempre deja de ser un descubrimiento.
 */
class HallazgoDeConcentracionTest {

    @Test
    @DisplayName("un equipo con un rezagado claro SI es una concentracion")
    void unRezagadoClaroEsUnaConcentracion() {
        Concentracion hallazgo = HallazgoDeConcentracion.de(List.of(
                new Aporte(1, "Valentina", 52),
                new Aporte(2, "Andrea", 47),
                new Aporte(3, "Carlos", 41),
                new Aporte(4, "Luis", 12)), "cartera");

        assertNotNull(hallazgo, "un 12 contra una mediana de 47 no es variacion normal");
        assertEquals("Luis", hallazgo.rezagado(),
                "se nombra a la persona: una concentracion sin sujeto no se puede resolver");
        assertTrue(hallazgo.cuerpo().contains("el resto del equipo"),
                "la conclusion RELACIONA al rezagado con el resto; decir solo «Luis 12» seria "
                        + "un dato con otro tipo de letra: " + hallazgo.cuerpo());
    }

    @Test
    @DisplayName("un equipo parejo NO produce hallazgo: no se rellena")
    void unEquipoParejoNoProduceHallazgo() {
        assertNull(HallazgoDeConcentracion.de(List.of(
                new Aporte(1, "Valentina", 52),
                new Aporte(2, "Andrea", 47),
                new Aporte(3, "Carlos", 41),
                new Aporte(4, "Luis", 38)), "cartera"),
                "sin concentracion el bloque no existe; «el equipo esta equilibrado» ensena a "
                        + "no leer los hallazgos");
    }

    @Test
    @DisplayName("con menos de tres no hay equipo del que hablar")
    void conMenosDeTresNoHayEquipo() {
        assertNull(HallazgoDeConcentracion.de(List.of(
                new Aporte(1, "Valentina", 50),
                new Aporte(2, "Luis", 2)), "cartera"));
        assertNull(HallazgoDeConcentracion.de(List.of(), "cartera"));
        assertNull(HallazgoDeConcentracion.de(null, "cartera"));
    }

    /**
     * <b>Por qué la mediana del resto y no la media del total.</b>
     *
     * <p>La media incluye al rezagado, y él mismo la arrastra hacia abajo:
     * compararse contra ella disimula justo lo que se busca.
     */
    @Test
    @DisplayName("se compara con la mediana del RESTO, no con la media que el rezagado arrastra")
    void seComparaConLaMedianaDelResto() {
        // Media del total = (40+38+36+2)/4 = 29. El 2 supera el 50 % de 29 -- no
        // llega a la mitad, pero con una media arrastrada el margen se estrecha.
        // Contra la mediana del resto (38) la concentracion es inequivoca.
        Concentracion hallazgo = HallazgoDeConcentracion.de(List.of(
                new Aporte(1, "Ana", 40),
                new Aporte(2, "Beto", 38),
                new Aporte(3, "Cira", 36),
                new Aporte(4, "Dario", 2)), "cartera");

        assertNotNull(hallazgo);
        assertEquals(38, hallazgo.medianaDelResto(),
                "la mediana dice «asi va el equipo cuando el no cuenta»");
    }

    /**
     * <b>El caso que destapó el dato real.</b>
     *
     * <p>Un equipo de cuatro donde uno lleva 107 captaciones y los otros tres,
     * cero. Mirar sólo al último no encontraba nada —comparaba 0 contra una
     * mediana de 0— cuando la concentración era evidente por el otro lado. Y
     * «concentración de cartera de un agente» es exactamente esto.
     */
    @Test
    @DisplayName("uno que acapara TAMBIEN es una concentracion")
    void unoQueAcaparaTambienEsConcentracion() {
        Concentracion hallazgo = HallazgoDeConcentracion.de(List.of(
                new Aporte(28, "Valentina", 107),
                new Aporte(29, "Andrea", 0),
                new Aporte(31, "Carlos", 0),
                new Aporte(32, "Luis", 0)), "cartera activa");

        assertNotNull(hallazgo, "«concentracion de cartera de un agente» es justo esto");
        assertEquals("Valentina", hallazgo.rezagado(),
                "se nombra a quien acapara: sin sujeto no se puede repartir");
        assertTrue(hallazgo.cuerpo().contains("el resto del equipo"),
                "y se dice relacionandolo con el resto: " + hallazgo.cuerpo());
    }

    @Test
    @DisplayName("un reparto desigual pero razonable no es concentracion")
    void unRepartoDesigualNoEsConcentracion() {
        assertNull(HallazgoDeConcentracion.de(List.of(
                new Aporte(1, "Ana", 30),
                new Aporte(2, "Beto", 22),
                new Aporte(3, "Cira", 18)), "cartera"),
                "llevar un tercio mas que el resto es reparto normal, no un cuello");
    }

    @Test
    @DisplayName("un equipo entero en cero no inventa un rezagado")
    void unEquipoEnCeroNoTieneRezagado() {
        assertNull(HallazgoDeConcentracion.de(List.of(
                new Aporte(1, "Ana", 0),
                new Aporte(2, "Beto", 0),
                new Aporte(3, "Cira", 0)), "cartera"),
                "si nadie tiene nada, el problema no es de concentracion y decirlo asi seria "
                        + "senalar a alguien por azar");
    }
}
