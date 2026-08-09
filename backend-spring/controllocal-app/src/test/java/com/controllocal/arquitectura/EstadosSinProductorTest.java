package com.controllocal.arquitectura;

import com.controllocal.domain.comun.EstadosDominio;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Vigila la tabla §1 de {@code docs/ai/decision-estados-sin-productor.md}, que
 * clasifica cada estado del vocabulario segun tenga o no una operacion que lo
 * produzca.
 *
 * <p><b>Por que existe.</b> La lista anterior vivia en la §2.1 del
 * {@code diagnostico-estados-valores-economicos-y-fixtures.md} y **nadie la
 * verificaba**: para cuando se reviso, cuatro de sus diez filas eran falsas —el
 * ciclo contractual y el de comision les habian dado productor y el documento
 * se quedo donde estaba—. Un inventario que se consulta para decidir y que
 * nadie comprueba no es documentacion, es una trampa. Mismo mecanismo que
 * {@code MatrizOperacionRolTest}, y por el mismo motivo.
 *
 * <p><b>Que comprueba.</b> Que el documento y el vocabulario real de
 * {@code EstadosDominio} dicen lo mismo:
 * <ol>
 *   <li>ningun estado del vocabulario queda <b>sin clasificar</b> — anadir uno
 *       obliga a decir si algo lo produce;</li>
 *   <li>ninguna fila del documento nombra un estado que <b>ya no existe</b>;</li>
 *   <li>la marca de cada fila es una de las dos permitidas, para que no se
 *       cuelen respuestas a medias.</li>
 * </ol>
 *
 * <p><b>Que NO comprueba, dicho sin adornos.</b> No detecta que un estado
 * <i>gane</i> productor. Las constantes {@code static final String} de Java se
 * <b>incrustan</b> en el bytecode, asi que ni ArchUnit ni la reflexion ven
 * quien las usa; detectarlo exigiria analizar el codigo fuente, y un analizador
 * fragil que falla sin motivo es peor que ninguno. Esa revision es
 * <b>manual y con fecha</b> — la ultima consta en el documento—. Lo que este
 * test si garantiza es que la tabla no se quede coja cuando cambie el
 * vocabulario, que es como se estropeo la anterior.
 */
class EstadosSinProductorTest {

    private static final String DOC = "docs/ai/decision-estados-sin-productor.md";

    /** Marcas admitidas en la columna "¿Productor hoy?". */
    private static final String CON_PRODUCTOR = "SI";
    private static final String SIN_PRODUCTOR = "NO";

    /**
     * Los procesos cubiertos, con el nombre exacto que usa la primera columna
     * del documento. Se declara aqui y no se deduce: el documento habla de
     * "Evaluacion" y "Resultado de propuesta", que no son enums de estado sino
     * tipos de entrada, y meterlos en la comparacion automatica obligaria a
     * inventar un vocabulario que no existe.
     */
    private static final Map<String, Class<? extends Enum<?>>> PROCESOS = new LinkedHashMap<>() {{
        put("Prospección", EstadosDominio.EstadoProspeccion.class);
        put("Captación", EstadosDominio.EstadoCaptacion.class);
        put("Oportunidad", EstadosDominio.EstadoOportunidad.class);
        put("Solicitud", EstadosDominio.EstadoSolicitud.class);
        put("Contrato", EstadosDominio.EstadoContrato.class);
        put("Comisión", EstadosDominio.EstadoComision.class);
        put("Tarea", EstadosDominio.EstadoTarea.class);
        put("Alerta", EstadosDominio.EstadoAlerta.class);
    }};

    @Test
    void el_documento_clasifica_todos_los_estados_del_vocabulario() {
        Map<String, Set<String>> documentados = documentados();

        List<String> faltan = new java.util.ArrayList<>();
        PROCESOS.forEach((proceso, tipo) -> {
            Set<String> enElDoc = documentados.getOrDefault(proceso, Set.of());
            for (String constante : nombres(tipo)) {
                if (!enElDoc.contains(constante)) {
                    faltan.add(proceso + " · " + constante);
                }
            }
        });

        if (!faltan.isEmpty()) {
            fail("""
                    Estados del vocabulario que %s no clasifica.

                    Un estado nuevo tiene que declarar si alguna operacion lo produce; si no,
                    vuelve a pasar lo de la §2.1 del diagnostico: una lista que se consulta
                    para decidir y que ya no era cierta.

                    Sin clasificar:
                      %s"""
                    .formatted(DOC, String.join("\n  ", new TreeSet<>(faltan))));
        }
    }

    @Test
    void el_documento_no_nombra_estados_que_ya_no_existen() {
        Map<String, Set<String>> documentados = documentados();

        List<String> muertas = new java.util.ArrayList<>();
        documentados.forEach((proceso, constantes) -> {
            Class<? extends Enum<?>> tipo = PROCESOS.get(proceso);
            if (tipo == null) {
                return;
            }
            Set<String> reales = nombres(tipo);
            constantes.stream().filter(c -> !reales.contains(c))
                    .forEach(c -> muertas.add(proceso + " · " + c));
        });

        if (!muertas.isEmpty()) {
            fail("""
                    Filas de %s que nombran un estado inexistente.

                    Una fila que sobrevive al estado que describia induce a error a quien la
                    lee para decidir.

                    Sobran:
                      %s"""
                    .formatted(DOC, String.join("\n  ", new TreeSet<>(muertas))));
        }
    }

    @Test
    void cada_fila_responde_si_o_no_sin_medias_tintas() {
        List<String[]> filas = filas();
        assertTrue(filas.size() >= PROCESOS.size(),
                "La tabla de " + DOC + " tiene " + filas.size() + " filas, menos que los "
                        + PROCESOS.size() + " procesos cubiertos: se ha vaciado o el formato cambio.");

        for (String[] fila : filas) {
            String marca = normalizar(fila[2]);
            assertTrue(marca.contains(CON_PRODUCTOR) || marca.contains(SIN_PRODUCTOR),
                    "La fila '" + fila[0] + " · " + fila[1] + "' de " + DOC + " no responde"
                            + " si tiene productor o no. Respuesta leida: '" + fila[2] + "'.");
        }
    }

    // ------------------------------------------------------------- utilidades

    /** Constantes declaradas por cada proceso en el documento. */
    private static Map<String, Set<String>> documentados() {
        Map<String, Set<String>> porProceso = new LinkedHashMap<>();
        for (String[] fila : filas()) {
            porProceso.computeIfAbsent(fila[0], p -> new LinkedHashSet<>())
                    .addAll(constantesDe(fila[0], fila[1]));
        }
        return porProceso;
    }

    /**
     * La celda de codigo se escribe para leerla ("`P`, `R`, `F`, `S`, `A`",
     * "`E` Propuesta entregada"), asi que se traduce del <b>codigo</b> —lo que
     * ve un humano— al <b>nombre de la constante</b>, que es lo comparable.
     * Los codigos son locales al agregado, de modo que la traduccion necesita
     * saber de que proceso habla la fila.
     */
    private static Set<String> constantesDe(String proceso, String celdaCodigo) {
        Class<? extends Enum<?>> tipo = PROCESOS.get(proceso);
        if (tipo == null) {
            return Set.of();
        }
        Set<String> encontradas = new LinkedHashSet<>();
        for (Enum<?> constante : tipo.getEnumConstants()) {
            String codigo = ((EstadosDominio.Codigo) constante).codigo();
            if (celdaCodigo.contains("`" + codigo + "`") || celdaCodigo.contains(constante.name())) {
                encontradas.add(constante.name());
            }
        }
        return encontradas;
    }

    private static Set<String> nombres(Class<? extends Enum<?>> tipo) {
        return java.util.Arrays.stream(tipo.getEnumConstants()).map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Filas de la primera tabla del documento, como
     * {@code [proceso, codigo, productor]}. Se para en la tabla siguiente para
     * no arrastrar las de las secciones de decision.
     */
    private static List<String[]> filas() {
        List<String[]> filas = new java.util.ArrayList<>();
        boolean dentro = false;
        for (String linea : lineas(DOC)) {
            String limpia = linea.strip();
            if (!limpia.startsWith("|")) {
                if (dentro && !filas.isEmpty()) {
                    break;
                }
                continue;
            }
            String[] celdas = limpia.split("\\|", -1);
            if (celdas.length < 5) {
                continue;
            }
            String proceso = celdas[1].strip();
            if (proceso.equalsIgnoreCase("Proceso")) {
                dentro = true;
                continue;
            }
            if (!dentro || proceso.startsWith("---") || proceso.isEmpty()) {
                continue;
            }
            filas.add(new String[]{proceso, celdas[2].strip(), celdas[3].strip()});
        }
        return filas;
    }

    /** Quita acentos y marcas para comparar "✅ **Sí**" con "SI". */
    private static String normalizar(String valor) {
        return java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * El documento vive fuera del modulo, asi que se busca la raiz del
     * repositorio subiendo desde el directorio de trabajo, igual que
     * {@code MatrizOperacionRolTest}.
     */
    private static List<String> lineas(String rutaRelativa) {
        Path directorio = Path.of("").toAbsolutePath();
        while (directorio != null) {
            Path candidato = directorio.resolve(rutaRelativa);
            if (Files.isRegularFile(candidato)) {
                try {
                    return Files.readAllLines(candidato, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException("No se pudo leer " + candidato, e);
                }
            }
            directorio = directorio.getParent();
        }
        throw new IllegalStateException("No se encontro " + rutaRelativa + " subiendo desde "
                + Path.of("").toAbsolutePath() + ". Es la fuente de verdad de los estados sin"
                + " productor; sin ella este test no puede vigilar nada.");
    }
}
