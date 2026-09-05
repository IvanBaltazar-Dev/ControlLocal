package com.kairos.interpretacion;

import com.kairos.brox.ClienteBrox;
import com.kairos.brox.SesionBrox;
import com.kairos.brox.Traza;
import com.kairos.catalogo.CatalogoReal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <b>Un numero con unidad se declara, en los siete tipos.</b>
 *
 * <h2>Que se esta protegiendo</h2>
 * Que ninguna medida se pierda en silencio. Hasta N28 el tokenizador no podia
 * ver las unidades que mas aparecen en una frase inmobiliaria —{@code m} mide un
 * caracter y el patron exigia dos; {@code m²} lleva un superindice que NFD no
 * descompone—, asi que «500 m²» y «8 m de frente» <b>se descartaban sin dejar
 * constancia</b>. Un dato que no se sabe interpretar se declara.
 *
 * <h2>Y que se declara TAMBIEN cuando la unidad la usa un solo campo</h2>
 * Es la decision que N28 dejo abierta. {@code años} la usa una sola clave en los
 * siete tipos, y aun asi «lo compre hace 8 años» no escribe una antiguedad: una
 * unidad identifica una <b>magnitud</b>, no un campo. Que el criterio no dependa
 * del recuento es lo que impide que la misma frase se escriba en un tipo y se
 * pregunte en otro — {@code m²} la usan doce claves en la union de los siete y
 * <b>una sola</b> en OTRO.
 *
 * <h2>Todo se mide desde {@code catalogoDe}, nunca desde {@code catalogo_atributo}</h2>
 * {@code catalogoDe} devuelve {@code comunes + delTipo}, y {@code comunes} no
 * sale de la tabla: sale de {@code GuionRegistroPropiedad}, que es quien aporta
 * {@code latitud} y {@code longitud} con la unidad {@code grados}. Medir sobre
 * la tabla es lo que hizo que un analisis anterior concluyera que la rama de
 * unidades era inalcanzable, y era falso.
 */
class UnidadesDelCatalogoTest {

    private static final SesionBrox AGENTE = new SesionBrox("token-de-la-persona", 1L);
    private static final Traza TRAZA = new Traza(Traza.CANAL_API, "KAIROS", "determinista", "0.1.0",
            "conv-1", "turno-1", null, null, null);

    /** La palabra con la que una frase nombra cada tipo. */
    private static final Map<String, String> COMO_SE_DICE = Map.of(
            "LOCAL", "local", "OFICINA", "oficina", "DEPARTAMENTO", "departamento",
            "CASA", "casa", "TERRENO", "terreno", "ALMACEN", "almacen", "OTRO", "otro");

    private InterpreteDeterminista interprete;

    @BeforeEach
    void preparar() {
        ClienteBrox brox = mock(ClienteBrox.class);
        when(brox.distritos(any(), any())).thenReturn(CatalogoReal.distritos());
        when(brox.catalogoDe(any(), anyString(), any())).thenAnswer(
                invocacion -> CatalogoReal.catalogoDe(invocacion.getArgument(1)));
        interprete = new InterpreteDeterminista(brox);
    }

    static List<String> tipos() {
        return CatalogoReal.TIPOS;
    }

    // ==================================================================

    @ParameterizedTest(name = "{0}")
    @MethodSource("tipos")
    @DisplayName("toda unidad del catalogo se declara, y ninguna escribe una clave")
    void todaUnidadSeDeclara(String tipo) {
        Map<String, Set<String>> unidades = unidadesDe(tipo);
        assertFalse(unidades.isEmpty(), "el catalogo de " + tipo + " no publica ninguna unidad");

        unidades.forEach((unidad, claves) -> {
            Interprete.Lectura lectura = leer(
                    "registra una propiedad " + COMO_SE_DICE.get(tipo) + " de 7 " + unidad);

            assertTrue(lectura.noEntendido().contains("7 " + unidad),
                    () -> "en " + tipo + ", «7 " + unidad + "» tenia que declararse: la usan "
                            + claves + " y la frase no dice cual. Declarado: "
                            + lectura.noEntendido());
            claves.forEach(clave -> assertNull(lectura.datos().get(clave),
                    () -> "en " + tipo + ", «7 " + unidad + "» escribio " + clave
                            + ": la unidad dice que se midio, no que campo es"));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tipos")
    @DisplayName("'grados' la comparten latitud y longitud: se declara en los SIETE")
    void losGradosSeDeclaranEnLosSiete(String tipo) {
        assertEquals(Set.of("latitud", "longitud"), unidadesDe(tipo).get("grados"),
                "latitud y longitud las aporta el guion comun, no la tabla del catalogo");

        Interprete.Lectura lectura = leer(
                "registra una propiedad " + COMO_SE_DICE.get(tipo) + " en 12.05 grados");
        assertTrue(lectura.noEntendido().contains("12.05 grados"));
        assertNull(lectura.datos().get("latitud"));
        assertNull(lectura.datos().get("longitud"));
    }

    @Test
    @DisplayName("una unidad usada por UNA sola clave tampoco se asigna: '8 años' no es antiguedad")
    void laUnidadUnicaTampocoAsigna() {
        // "anos" y no "años": el catalogo publica la unidad con enye y lo que se
        // compara contra la frase es lo que devuelve llano().
        assertEquals(Set.of("antiguedad_anios"), unidadesDe("CASA").get("anos"),
                "la unica clave del catalogo que se mide en años");

        Interprete.Lectura lectura = leer("registra una propiedad casa que compre hace 8 años");

        assertNull(lectura.datos().get("antiguedad_anios"),
                "«compre hace 8 años» habla de cuando se compro, no de la antiguedad del "
                        + "inmueble: con una sola clave candidata sigue sin haber nadie que diga "
                        + "que el numero es esa");
        assertTrue(lectura.noEntendido().contains("8 anos"));
    }

    @Test
    @DisplayName("m² la usan 12 claves en la union y UNA sola en OTRO, y se declara igual")
    void laMismaUnidadEnElTipoDondeNoEsAmbigua() {
        assertEquals(Set.of("metraje_total"), unidadesDe("OTRO").get("m2"),
                "en OTRO solo hay un campo en metros cuadrados");

        Interprete.Lectura lectura = leer("registra una propiedad otro de 500 m²");

        assertNull(lectura.datos().get("metraje_total"),
                "un criterio que dependiera del recuento haria que la misma frase se escribiera "
                        + "en OTRO y se preguntara en CASA");
        assertTrue(lectura.noEntendido().contains("500 m2"));
    }

    @Test
    @DisplayName("una unidad que ese tipo no usa no se declara: en un depa no hay nada en 'm'")
    void loQueNoEsUnidadDeEseTipoNoSeDeclara() {
        assertNull(unidadesDe("DEPARTAMENTO").get("m"),
                "ningun campo de un departamento se mide en metros lineales");
        assertEquals(6, unidadesDe("ALMACEN").get("m").size(),
                "en un almacen si: frente, altura libre, patio de maniobras y las puertas");

        assertTrue(leer("registra una propiedad departamento de 8 m").noEntendido().isEmpty(),
                "«8 m» en un departamento no es una medida perdida: es que la frase decia otra "
                        + "cosa. Declararlo todo convertiria noEntendido en ruido");
        assertTrue(leer("registra una propiedad almacen de 8 m").noEntendido().contains("8 m"));
    }

    @Test
    @DisplayName("el reparto de unidades de los siete, medido: si el Core lo cambia, esto avisa")
    void elRepartoDeUnidadesEsElQueSeCreeQueEs() {
        // El recuento de la union: cuantas claves DISTINTAS usan cada unidad en
        // los siete tipos juntos.
        Map<String, Set<String>> porUnidad = new TreeMap<>();
        for (String tipo : CatalogoReal.TIPOS) {
            unidadesDe(tipo).forEach((unidad, claves) ->
                    porUnidad.computeIfAbsent(unidad, u -> new LinkedHashSet<>()).addAll(claves));
        }
        Map<String, Integer> medido = new TreeMap<>();
        porUnidad.forEach((unidad, claves) -> medido.put(unidad, claves.size()));

        assertEquals(Map.of(
                        "%", 1,
                        "anos", 1,
                        "grados", 2,
                        "kw", 1,
                        "m", 8,
                        "m2", 12,
                        "personas", 1,
                        "pisos", 1,
                        "t/m2", 1),
                medido,
                "las unidades del catalogo grabado el 2026-09-05. Si esto cambia, el catalogo "
                        + "cambio: se vuelve a grabar el fixture y se mira si alguna frase "
                        + "empieza a significar otra cosa");
    }

    // ==================================================================

    /**
     * Unidad → claves que la usan, <b>en la forma en que el interprete las ve</b>.
     *
     * <p>Se normaliza con {@code llano} —el mismo que usa el interprete— porque
     * el catalogo publica {@code m²} y {@code años}, y lo que se compara contra
     * la frase es {@code m2} y {@code anos}.
     */
    private static Map<String, Set<String>> unidadesDe(String tipo) {
        Map<String, Set<String>> unidades = new LinkedHashMap<>();
        for (ClienteBrox.Pregunta pregunta : CatalogoReal.catalogoDe(tipo)) {
            if (pregunta.unidad() != null && !pregunta.unidad().isBlank()) {
                unidades.computeIfAbsent(InterpreteDeterminista.llano(pregunta.unidad()),
                        u -> new LinkedHashSet<>()).add(pregunta.clave());
            }
        }
        return unidades;
    }

    private Interprete.Lectura leer(String frase) {
        return interprete.leer(frase, AGENTE, TRAZA);
    }

    /** Los siete se nombran con una palabra que el interprete reconoce. */
    @Test
    @DisplayName("los siete tipos se nombran en una frase y se reconocen")
    void losSieteSeNombran() {
        for (String tipo : CatalogoReal.TIPOS) {
            Interprete.Lectura lectura =
                    leer("registra una propiedad " + COMO_SE_DICE.get(tipo));
            assertEquals(tipo, lectura.datos().get("tipoPropiedad"),
                    "sin el tipo no hay catalogo que consultar, y todo lo de arriba seria vacio");
        }
        assertEquals(Set.copyOf(CatalogoReal.TIPOS), COMO_SE_DICE.keySet(),
                "si el Core anade un tipo, esta prueba lo dice antes que ninguna otra");
    }
}
