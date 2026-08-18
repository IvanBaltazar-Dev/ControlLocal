package com.controllocal.service.soporte;

import com.controllocal.service.soporte.InterpretacionDelAsunto.Hecho;
import com.controllocal.service.soporte.InterpretacionDelAsunto.Renglon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Las tres reglas de redacción que D-E2-1 §10 exige comprobar</b> (E2.4).
 *
 * <p>No se prueba «qué frase sale»: eso cambiaría con cada retoque y el test
 * pasaría a ser una copia del código. Se prueba lo que la decisión declara
 * inaceptable — recitar, colar un código técnico, o pasar de tres hechos.
 */
class InterpretacionDelAsuntoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 8, 18);

    // ==================================================================
    // La lectura sintetiza; no recita
    // ==================================================================

    @Test
    @DisplayName("recitar el valor de un renglon NO es leer el expediente")
    void recitarNoEsLeer() {
        List<Renglon> expediente = List.of(
                Renglon.historial("Encargo", "Alta el 12 de mayo · vence en 12 dias"),
                Renglon.historial("Renta", "US$ 4,500 sin cambios desde hace 54 dias"));

        assertTrue(InterpretacionDelAsunto.recita(
                        "Alta el 12 de mayo · vence en 12 dias, y poco mas.", expediente),
                "si repite el renglon entero, el usuario ya lo tiene dos centimetros mas abajo");

        assertFalse(InterpretacionDelAsunto.recita(
                        "La exclusiva casi agotada y la renta sin moverse.", expediente),
                "relacionar los renglones SI es leerlos: eso es una conclusion, no un eco");
    }

    @Test
    @DisplayName("recitar con otro traje sigue siendo recitar")
    void elAcentoYLaMayusculaNoDisimulan() {
        List<Renglon> expediente = List.of(
                Renglon.historial("Renta", "US$ 4,500 sin cambios desde hace 54 días"));

        assertTrue(InterpretacionDelAsunto.recita(
                        "us$ 4,500  SIN CAMBIOS DESDE HACE 54 dias", expediente),
                "se compara normalizado: mismo recitado, otras mayusculas");
    }

    @Test
    @DisplayName("un valor corto no convierte cualquier frase en un recitado")
    void unValorCortoNoEsRecitado() {
        List<Renglon> expediente = List.of(Renglon.historial("Actividad", "8 visitas"));

        assertFalse(InterpretacionDelAsunto.recita(
                        "Van 8 visitas y ninguna propuesta.", expediente),
                "exigir longitud evita el falso positivo que haria imposible escribir nada");
    }

    // ==================================================================
    // Ningun codigo tecnico en el texto visible
    // ==================================================================

    @Test
    @DisplayName("los codigos no aparecen en el texto que se lee")
    void ningunCodigoTecnicoVisible() {
        assertTrue(InterpretacionDelAsunto.llevaCodigoTecnico("Abierta el 22 jul · OPO-0098"));
        assertTrue(InterpretacionDelAsunto.llevaCodigoTecnico("Propon CAP-0010 al cliente"));
        assertTrue(InterpretacionDelAsunto.llevaCodigoTecnico("Recontacta PRO-0002"));

        assertFalse(InterpretacionDelAsunto.llevaCodigoTecnico(
                        "Av. Larco 123 · Miraflores · Sr. Aliaga"),
                "quien opera identifica la operacion por la direccion y la persona");
        assertFalse(InterpretacionDelAsunto.llevaCodigoTecnico("US$ 4,500 desde el 18 de junio"));
    }

    // ==================================================================
    // Tres hechos, sin parrafos
    // ==================================================================

    @Test
    @DisplayName("como esta lleva como maximo tres hechos")
    void comoEstaSeQuedaEnTres() {
        var como = InterpretacionDelAsunto.ComoEsta.de(null, List.of(
                new Hecho(EstadoDelHecho.HECHO, "uno"),
                new Hecho(EstadoDelHecho.FALTA, "dos"),
                new Hecho(EstadoDelHecho.PLAZO, "tres"),
                new Hecho(EstadoDelHecho.FRENO, "cuatro")));

        assertEquals(3, como.hechos().size(), "tres vinetas, sin parrafos (D-E2-1 seccion 10)");
        assertEquals(EstadoDelHecho.HECHO, como.hechos().get(0).estado(),
                "y se queda con los PRIMEROS: el orden es narrativo, no por gravedad");
    }

    @Test
    @DisplayName("el vocabulario de estados es de cinco y no crece")
    void elVocabularioNoCrece() {
        assertEquals(5, EstadoDelHecho.values().length,
                "un sexto estado no se cuela sin decidirlo (D-E2-1 seccion 10.1)");
        assertEquals(List.of("HECHO", "FALTA", "PLAZO", "FRENO", "DATO"),
                java.util.Arrays.stream(EstadoDelHecho.values()).map(Enum::name).toList());
    }

    // ==================================================================
    // Como se dicen las cosas
    // ==================================================================

    @Test
    @DisplayName("hoy y manana no son 'en 0 dias' ni 'en 1 dia'")
    void losPlazosSeDicenComoSeHabla() {
        assertEquals("vence hoy", InterpretacionDelAsunto.enDias(HOY, HOY));
        assertEquals("vence manana", InterpretacionDelAsunto.enDias(HOY, HOY.plusDays(1)));
        assertEquals("vence en 12 dias", InterpretacionDelAsunto.enDias(HOY, HOY.plusDays(12)));
        assertEquals("vencio hace 3 dias", InterpretacionDelAsunto.enDias(HOY, HOY.minusDays(3)));
        assertEquals("vencio hace 1 dia", InterpretacionDelAsunto.enDias(HOY, HOY.minusDays(1)));
    }

    @Test
    @DisplayName("un fragmento vacio no deja un separador colgando")
    void laFraseNoDejaSeparadoresHuerfanos() {
        assertEquals("Alta el 12 de mayo · vence en 12 dias",
                InterpretacionDelAsunto.frase("Alta el 12 de mayo", null, "vence en 12 dias"));
        assertEquals("Alta el 12 de mayo",
                InterpretacionDelAsunto.frase("Alta el 12 de mayo", "", "  "));
        assertEquals("", InterpretacionDelAsunto.frase(null, null),
                "sin nada que decir no se dice nada, ni un separador");
    }
}
