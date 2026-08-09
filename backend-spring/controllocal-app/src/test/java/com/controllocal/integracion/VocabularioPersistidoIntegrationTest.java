package com.controllocal.integracion;

import com.controllocal.app.ControlLocalApplication;
import com.controllocal.arquitectura.CatalogoProductoresTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * <b>Capa 1 del gate de productores: ESTRUCTURAL.</b>
 *
 * <p>Contrasta el catálogo contra lo que PostgreSQL admite de verdad. Si
 * alguien añade una letra a un {@code CHECK} sin clasificarla, el build cae —y
 * cae aunque no toque una sola línea de Java, que es justo el agujero por el
 * que se colaron los ocho huérfanos del Bloque 7.
 *
 * <h2>Por qué no hay un parser inteligente</h2>
 * Se leen <b>solo</b> las {@code CHECK} de UNA columna con lista {@code IN}. Las
 * compuestas se declaran a mano en el catálogo, y no por pereza: extraer
 * {@code ck_evaluacion_tipo_derivado} con una expresión regular daba el
 * vocabulario <b>falso</b> {@code A,F,O,P,R} para {@code tipo_evaluacion},
 * cuando el real es {@code F,O,P}. Una heurística equivocada es peor que una
 * declaración explícita: la declaración se ve, la heurística miente en
 * silencio.
 *
 * <p>Por la misma razón el ALCANCE lo fija el catálogo y no un patrón de
 * nombres: las taxonomías de evento ({@code alerta.tipo},
 * {@code evento_seguridad.tipo}…) quedan fuera a propósito porque no son
 * máquinas de estado.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = ControlLocalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VocabularioPersistidoIntegrationTest {

    @DynamicPropertySource
    static void datos(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        propiedades.add("spring.datasource.username", () -> System.getenv().getOrDefault(
                "TEST_DB_USER", "controllocal"));
        propiedades.add("spring.datasource.password", () -> System.getenv().getOrDefault(
                "TEST_DB_PASSWORD", "controllocal"));
    }

    @Autowired JdbcTemplate jdbc;

    /**
     * Vocabulario real por columna, tomado solo de las {@code CHECK} simples de
     * las columnas que el catálogo declara en alcance.
     */
    private Map<String, Set<String>> vocabularioReal(Set<String> columnasEnAlcance) {
        Map<String, Set<String>> real = new TreeMap<>();
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select c.relname || '.' || a.attname as columna,
                       pg_get_constraintdef(t.oid)   as definicion
                  from pg_constraint t
                  join pg_class c      on c.oid = t.conrelid
                  join pg_namespace n  on n.oid = c.relnamespace and n.nspname = 'public'
                  join pg_attribute a  on a.attrelid = c.oid and a.attnum = any(t.conkey)
                 where t.contype = 'c'
                   and array_length(t.conkey, 1) = 1
                   and pg_get_constraintdef(t.oid) like '%ANY%'
                """);
        for (Map<String, Object> fila : filas) {
            String columna = (String) fila.get("columna");
            if (!columnasEnAlcance.contains(columna)) {
                continue;
            }
            var matcher = java.util.regex.Pattern
                    .compile("'([A-Za-z_]+)'::character varying")
                    .matcher((String) fila.get("definicion"));
            Set<String> codigos = real.computeIfAbsent(columna, k -> new TreeSet<>());
            while (matcher.find()) {
                codigos.add(matcher.group(1));
            }
        }
        return real;
    }

    @Test
    @DisplayName("todo codigo que PostgreSQL admite esta clasificado, y todo clasificado existe")
    void elCatalogoYLaBaseDicenLoMismo() throws IOException {
        Map<String, Set<String>> clasificado = CatalogoProductoresTest.leerCatalogo().stream()
                .collect(Collectors.groupingBy(CatalogoProductoresTest.Fila::columna,
                        TreeMap::new,
                        Collectors.mapping(CatalogoProductoresTest.Fila::codigo,
                                Collectors.toCollection(TreeSet::new))));

        Map<String, Set<String>> real = vocabularioReal(clasificado.keySet());

        // (a) Toda columna del catalogo tiene que existir en la base.
        List<String> inexistentes = clasificado.keySet().stream()
                .filter(c -> !real.containsKey(c))
                .toList();
        assertEquals(List.of(), inexistentes,
                "el catalogo clasifica columnas que la base no tiene (o cuya CHECK dejo de ser "
                        + "simple): revisa si una migracion la cambio");

        // (b) Y el vocabulario tiene que coincidir EXACTAMENTE en ambos sentidos:
        //     un codigo nuevo sin clasificar, o uno clasificado que ya no existe.
        List<String> discrepancias = real.entrySet().stream()
                .filter(e -> !e.getValue().equals(clasificado.get(e.getKey())))
                .map(e -> e.getKey()
                        + "  base=" + e.getValue()
                        + "  catalogo=" + clasificado.get(e.getKey()))
                .toList();

        assertEquals(List.of(), discrepancias, """
                El vocabulario persistido y el catalogo no coinciden.

                Si anadiste un codigo a un CHECK, clasificalo en
                docs/ai/catalogo-productores-canonico.md con una de las cinco clases
                y su evidencia. Si lo quitaste, borra su fila.

                Un codigo que la base admite y nadie clasifico es exactamente el
                huerfano que este gate existe para impedir.""");
    }
}
