package com.controllocal.arquitectura;

import com.controllocal.service.soporte.KpiCanonico;
import com.controllocal.service.soporte.PoliticaComercial;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Gate de E1: <b>la politica comercial no se re-implementa</b>.
 *
 * <h2>Que problema cierra</h2>
 *
 * <p>El plazo de recontacto llego a estar en cuatro sitios a la vez —bandeja,
 * indicadores, campana y {@code dashboard.ts}— y la coherencia dependia de un
 * comentario que pedia que los cuatro numeros cuadraran. Nada rompia si uno se
 * quedaba atras: la bandeja diria una cosa y el indicador otra, en silencio.
 * Centralizarlo no basta, porque <b>nada impide que la quinta copia aparezca
 * manana</b>: alguien escribe {@code hoy.minusDays(7)} en un servicio nuevo y el
 * build sigue verde.
 *
 * <p>Esto lo impide: busca en el codigo fuente —backend y SPA— las formas en que
 * estas reglas se re-escriben a mano, y falla nombrando el archivo y la
 * alternativa.
 *
 * <h2>Que NO vigila, a proposito</h2>
 *
 * <p>No persigue el numero suelto: un 7 puede ser cualquier cosa y perseguirlo
 * daria falsos positivos hasta volver el gate inutil. Vigila el <b>7 aplicado
 * como plazo</b> ({@code minusDays(7)}), el <b>10 aplicado como longitud
 * minima</b> ({@code length < 10}) y los nombres de las constantes que se
 * retiraron. Es un cedazo, no una red: cubre la forma en que estas reglas
 * volverian de verdad.
 *
 * <p>Tampoco mira los tests: un test puede —y debe— escribir el valor esperado.
 */
class PoliticaUnicaTest {

    /** El unico archivo donde estos valores pueden estar escritos. */
    private static final String POLITICA =
            "PoliticaComercial.java";

    /**
     * El espejo del SPA. Existe porque el formulario necesita dos valores antes
     * de enviar; esta declarado, documentado y cubierto por el test de la
     * politica, asi que no es una copia furtiva.
     */
    private static final String ESPEJO_SPA = "politica-comercial.ts";

    private record Prohibido(Pattern patron, String regla, String enSuLugar) {
    }

    private static final List<Prohibido> EN_JAVA = List.of(
            new Prohibido(Pattern.compile("(minus|plus)Days\\(\\s*7\\s*\\)"),
                    "recontacto.dias",
                    "PoliticaComercial.limiteDeRecontacto(hoy)"),
            new Prohibido(Pattern.compile("(minus|plus)Days\\(\\s*3\\s*\\)"),
                    "visita.dias-de-aviso",
                    "PoliticaComercial.horizonteDeVisitas(hoy)"),
            new Prohibido(Pattern.compile("(minus|plus)Days\\(\\s*15\\s*\\)"),
                    "reporte-propietario.dias",
                    "PoliticaComercial.proximoReporteAlPropietario(desde)"),
            new Prohibido(Pattern.compile("plusMonths\\(\\s*6\\s*\\)"),
                    "encargo.meses-por-defecto",
                    "PoliticaComercial.finDelEncargo(inicio)"),
            new Prohibido(Pattern.compile(">=?\\s*60\\b"),
                    "coincidencia.puntaje-minimo",
                    "PoliticaComercial.valeLaPenaProponer(puntaje)"),
            new Prohibido(Pattern.compile("BigDecimal\\(\"200\"\\)|BigDecimal\\.valueOf\\(\\s*200\\s*\\)"),
                    "comision.porcentaje-maximo",
                    "PoliticaComercial.comisionMaxima()"),
            new Prohibido(Pattern.compile(
                    "\\b(DIAS_RECONTACTO|DIAS_VISITA\\w*|DIAS_REPORTE|UMBRAL_PROPUESTA"
                            + "|MESES_ENCARGO\\w*|UMBRAL_RECONTACTO)\\b"),
                    "una constante de plazo propia",
                    "la Regla correspondiente de PoliticaComercial"));

    private static final List<Prohibido> EN_TYPESCRIPT = List.of(
            new Prohibido(Pattern.compile(">=?\\s*7\\b"),
                    "recontacto.dias",
                    "el `nivelAtencion` que ya viene en `senales`"),
            new Prohibido(Pattern.compile("<\\s*10\\b"),
                    "reasignacion.caracteres-minimos-del-motivo",
                    "POLITICA_COMERCIAL.motivoReasignacionCaracteres"),
            new Prohibido(Pattern.compile("fechaEnMeses\\(\\s*6\\s*\\)|setMonth\\([^)]*\\+\\s*6\\s*\\)"),
                    "encargo.meses-por-defecto",
                    "POLITICA_COMERCIAL.encargoMesesPorDefecto"));

    /**
     * Los dos unicos archivos exentos, y los dos por la misma razon: son la
     * definicion, no una copia. {@code CondicionesEconomicas} <b>no</b> esta
     * aqui —valida la comision pero ya no fija el tope, se lo pide a la
     * politica—, asi que si alguien le devuelve su propio {@code 200} el gate
     * lo ve.
     */
    private static final Set<String> EXENTOS = Set.of(POLITICA, ESPEJO_SPA);

    @Test
    @DisplayName("ningun servicio vuelve a escribir a mano un plazo de la politica")
    void elBackendNoReimplementaLaPolitica() {
        List<String> hallazgos = new ArrayList<>();
        for (String modulo : List.of("controllocal-domain", "controllocal-persistence",
                "controllocal-service", "controllocal-web", "controllocal-app")) {
            revisar(raiz().resolve("backend-spring").resolve(modulo).resolve("src/main/java"),
                    ".java", EN_JAVA, hallazgos);
        }
        reportar(hallazgos);
    }

    @Test
    @DisplayName("ninguna pantalla vuelve a decidir cuando algo esta atrasado (R-07)")
    void elSpaNoReimplementaLaPolitica() {
        List<String> hallazgos = new ArrayList<>();
        revisar(raiz().resolve("frontend-angular/src/app"), ".ts", EN_TYPESCRIPT, hallazgos);
        reportar(hallazgos);
    }

    /**
     * El gate solo sirve si los archivos que vigila existen. Si alguien mueve o
     * renombra la politica, esta prueba lo dice en vez de dejar el escaner
     * pasando sobre un arbol vacio y dando verde para siempre.
     */
    @Test
    @DisplayName("la politica y su espejo siguen donde el gate los busca")
    void elGateNoSeQuedaSinObjeto() {
        Path politica = raiz().resolve("backend-spring/controllocal-service/src/main/java/com/"
                + "controllocal/service/soporte/" + POLITICA);
        Path espejo = raiz().resolve("frontend-angular/src/app/core/" + ESPEJO_SPA);
        assertTrue(Files.isRegularFile(politica), "No esta " + politica);
        assertTrue(Files.isRegularFile(espejo), "No esta " + espejo);
    }

    /**
     * <b>La maqueta no puede contradecir a la politica.</b>
     *
     * <p>{@code docs/ai/prototipos/nucleo-brox.js} lleva su propio bloque de
     * umbrales, y tiene que llevarlo: una maqueta necesita pintar numeros. El
     * problema nunca fue que existiera, sino que <b>nadie comprobaba que
     * coincidiera</b>. Hasta el 2026-08-19 se llamaba {@code POLITICA} y decia
     * "los umbrales, en un solo sitio (E1)" mientras divergia en dos valores:
     * 10 dias de reporte al propietario donde la politica dice 15, y 60 de renta
     * sin ajustar donde el codigo aplicaba 45. Quien buscara "10 dias" lo
     * encontraba alli sin ninguna senal de que no gobernaba nada.
     *
     * <p>Esta prueba lee el espejo y compara valor por valor. La maqueta puede
     * seguir pintando; lo que ya no puede es decir otra cosa.
     */
    @Test
    @DisplayName("el espejo de la maqueta no puede divergir de la politica")
    void laMaquetaNoContradiceALaPolitica() {
        Path nucleo = raiz().resolve("docs/ai/prototipos/nucleo-brox.js");
        assertTrue(Files.isRegularFile(nucleo),
                "No esta " + nucleo + ": sin el, este gate no vigila nada.");
        String texto = leer(nucleo);

        assertFalse(texto.contains("var POLITICA = {"),
                "El bloque de la maqueta volvio a llamarse POLITICA a secas. Tiene que "
                        + "llamarse POLITICA_ESPEJO y llevar la advertencia: la fuente es "
                        + POLITICA + ", no un prototipo.");
        assertTrue(texto.contains("NO ES LA FUENTE"),
                "El espejo perdio su advertencia. Sin ella, quien busque un umbral lo "
                        + "encontrara alli creyendo que gobierna.");

        Map<String, Integer> esperado = Map.of(
                "recontactoDias", PoliticaComercial.RECONTACTO.valor(),
                "reporteAlPropietarioDias", PoliticaComercial.REPORTE_PROPIETARIO.valor(),
                "rentaSinAjustarDias", PoliticaComercial.RENTA_SIN_AJUSTAR.valor(),
                "volumenMinimo", PoliticaComercial.RITMO_VOLUMEN_MINIMO.valor(),
                "muestraMinima", PoliticaComercial.MUESTRA_MINIMA.valor(),
                "rangoMuestraMinima", PoliticaComercial.RANGO_MUESTRA_MINIMA.valor());

        List<String> divergen = new ArrayList<>();
        esperado.forEach((clave, valorDeLaPolitica) -> {
            Integer enLaMaqueta = enteroDe(texto, clave);
            if (enLaMaqueta == null) {
                divergen.add(clave + ": la maqueta ya no lo declara");
            } else if (!enLaMaqueta.equals(valorDeLaPolitica)) {
                divergen.add(clave + ": la maqueta dice " + enLaMaqueta
                        + " y la politica " + valorDeLaPolitica);
            }
        });

        // Los tres del ritmo viajan como fraccion en JS y como porcentaje aqui.
        comprobarFraccion(texto, "llega", PoliticaComercial.RITMO_LLEGA.valor(), divergen);
        comprobarFraccion(texto, "cerca", PoliticaComercial.RITMO_CERCA.valor(), divergen);
        comprobarFraccion(texto, "arranquePc", PoliticaComercial.RITMO_ARRANQUE.valor(), divergen);

        assertEquals(List.of(), divergen,
                "La maqueta y la politica dicen cosas distintas. Corrige nucleo-brox.js y "
                        + "vuelve a correr construir.mjs; la fuente es " + POLITICA + ".");
    }

    private static void comprobarFraccion(String texto, String clave, int porcentaje,
                                          List<String> divergen) {
        Matcher m = Pattern.compile(clave + "[ ]*:[ ]*([0-9.]+)").matcher(texto);
        if (!m.find()) {
            divergen.add(clave + ": la maqueta ya no lo declara");
            return;
        }
        int enLaMaqueta = (int) Math.round(Double.parseDouble(m.group(1)) * 100);
        if (enLaMaqueta != porcentaje) {
            divergen.add(clave + ": la maqueta dice " + enLaMaqueta + " % y la politica "
                    + porcentaje + " %");
        }
    }

    private static Integer enteroDe(String texto, String clave) {
        Matcher m = Pattern.compile(clave + "[ ]*:[ ]*([0-9]+)").matcher(texto);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /**
     * <b>Los cuatro nombres canonicos, en un solo sitio.</b>
     *
     * <p>Es el gate que no se podia escribir hasta el 2026-08-19, porque los dos
     * documentos que gobiernan decian cosas distintas: D-E2-2 §1 hablaba de
     * «Prospeccion efectiva / Captaciones activadas / Solicitudes generadas» y
     * D-E2-1 §6.2 de «Propietarios contactados / Locales captados / Solicitudes
     * ingresadas», con una comprobacion que exigia los cuatro «letra por letra».
     * Pedia dos verdades.
     *
     * <p>Resuelto a favor del hecho de negocio, la autoridad es
     * {@code KpiCanonico}. La maqueta los repite y esta prueba comprueba que los
     * repita bien: un pie que contradice a Indicadores es peor que no tener pie.
     *
     * <p>Angular no entra aqui porque no puede divergir: <b>lee el rotulo del
     * cable</b> en vez de escribirlo.
     */
    @Test
    @DisplayName("los cuatro nombres canonicos salen del dominio, y la maqueta los repite")
    void losCuatroNombresTienenUnSoloDueno() {
        assertEquals(List.of("Propietarios contactados", "Propiedades captadas",
                        "Solicitudes ingresadas", "Contratos firmados"),
                KpiCanonico.rotulos(),
                "Cambiaron los cuatro nombres canonicos. Si es a proposito, actualiza tambien "
                        + "D-E2-1 §6.2, D-E2-2 §1 y docs/ai/prototipos/nucleo-brox.js -- los "
                        + "cuatro tienen que decir lo mismo, letra por letra.");

        String nucleo = leer(raiz().resolve("docs/ai/prototipos/nucleo-brox.js"));
        for (String rotulo : KpiCanonico.rotulos()) {
            assertTrue(nucleo.contains('"' + rotulo + '"'),
                    "La maqueta no usa el nombre canonico \"" + rotulo + "\". Corrige "
                            + "nucleo-brox.js y vuelve a correr construir.mjs.");
        }

        assertFalse(nucleo.contains("Locales captados"),
                "La maqueta conserva el nombre viejo. BROX dejo de ser un sistema de alquiler "
                        + "de locales el 2026-08-17.");
    }

    // ------------------------------------------------------------- utilidades

    private static String leer(Path archivo) {
        try {
            return Files.readString(archivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
    }

    private static void revisar(Path arbol, String extension, List<Prohibido> prohibidos,
                                List<String> hallazgos) {
        if (!Files.isDirectory(arbol)) {
            fail("No se encontro " + arbol + ": sin ese arbol este gate no vigila nada.");
        }
        try (Stream<Path> ficheros = Files.walk(arbol)) {
            ficheros.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(extension))
                    .filter(f -> !f.getFileName().toString().endsWith(".spec" + extension))
                    .filter(f -> !EXENTOS.contains(f.getFileName().toString()))
                    .forEach(f -> revisarArchivo(f, prohibidos, hallazgos));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo recorrer " + arbol, e);
        }
    }

    private static void revisarArchivo(Path archivo, List<Prohibido> prohibidos,
                                       List<String> hallazgos) {
        List<String> lineas;
        try {
            lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
        boolean enBloque = false;
        for (int i = 0; i < lineas.size(); i++) {
            String cruda = lineas.get(i);
            String codigo = sinComentarios(cruda, enBloque);
            enBloque = quedaBloqueAbierto(cruda, enBloque);
            if (codigo.isBlank()) {
                continue;
            }
            for (Prohibido p : prohibidos) {
                if (p.patron().matcher(codigo).find()) {
                    hallazgos.add(archivo.getFileName() + ":" + (i + 1)
                            + "  reimplementa `" + p.regla() + "`\n      " + cruda.trim()
                            + "\n      usa en su lugar: " + p.enSuLugar());
                }
            }
        }
    }

    /**
     * Quita comentarios de linea y de bloque. Es deliberadamente simple: los
     * comentarios de este repositorio EXPLICAN las reglas —"la cuarta copia del
     * plazo", "`> 7`"— y sin esto el gate se dispararia con su propia
     * documentacion.
     */
    private static String sinComentarios(String linea, boolean veniaEnBloque) {
        StringBuilder codigo = new StringBuilder();
        boolean enBloque = veniaEnBloque;
        for (int i = 0; i < linea.length(); i++) {
            if (enBloque) {
                if (linea.startsWith("*/", i)) {
                    enBloque = false;
                    i++;
                }
                continue;
            }
            if (linea.startsWith("/*", i)) {
                enBloque = true;
                i++;
                continue;
            }
            if (linea.startsWith("//", i)) {
                break;
            }
            codigo.append(linea.charAt(i));
        }
        return codigo.toString();
    }

    private static boolean quedaBloqueAbierto(String linea, boolean veniaEnBloque) {
        boolean enBloque = veniaEnBloque;
        for (int i = 0; i < linea.length(); i++) {
            if (enBloque) {
                if (linea.startsWith("*/", i)) {
                    enBloque = false;
                    i++;
                }
            } else if (linea.startsWith("/*", i)) {
                enBloque = true;
                i++;
            } else if (linea.startsWith("//", i)) {
                return false;
            }
        }
        return enBloque;
    }

    private static void reportar(List<String> hallazgos) {
        if (hallazgos.isEmpty()) {
            return;
        }
        fail("La politica comercial se esta reimplementando en "
                + hallazgos.size() + " sitio(s). Cada regla vive en UN solo lugar "
                + "(PoliticaComercial) y el resto la consulta; el problema de la copia no es "
                + "el numero, es que las copias divergen en silencio.\n\n  "
                + String.join("\n  ", hallazgos));
    }

    /** La raiz del repositorio, subiendo desde el basedir del modulo. */
    private static Path raiz() {
        Path directorio = Path.of("").toAbsolutePath();
        while (directorio != null) {
            if (Files.isDirectory(directorio.resolve("backend-spring"))
                    && Files.isDirectory(directorio.resolve("frontend-angular"))) {
                return directorio;
            }
            directorio = directorio.getParent();
        }
        throw new IllegalStateException("No se encontro la raiz del repositorio subiendo desde "
                + Path.of("").toAbsolutePath());
    }
}
