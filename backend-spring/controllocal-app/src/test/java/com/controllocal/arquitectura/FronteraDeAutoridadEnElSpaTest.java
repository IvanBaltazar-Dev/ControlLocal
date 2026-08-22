package com.controllocal.arquitectura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>El SPA puede conocer la clave logica y el tipo de dato funcional; nunca la
 * autoridad fisica</b> (D-E4-3, paso 11).
 *
 * <h2>Que separa exactamente</h2>
 * <pre>
 *   PERMITIDO   un formulario con un campo llamado `ambientes`
 *               un input `type=number step=1` porque el dato es entero
 *               un DTO con `frente: number | null`
 *
 *   PROHIBIDO   saber que `ambientes` vive en `atributo_propiedad`
 *               saber que `metraje` vive en una columna del agregado
 *               un fallback entre la representacion vieja y la nueva
 *               parsear un numero porque "antes venia como texto"
 * </pre>
 *
 * <p>Nombrar {@code ambientes} en un formulario <b>no</b> viola la arquitectura:
 * es lenguaje inmobiliario, y el consumidor no tiene otra forma de pedir un dato
 * que por su nombre. Decidir que {@code ambientes} pertenece a
 * {@code atributo_propiedad} si la violaria.
 *
 * <h2>Por que el gate vive en el backend</h2>
 * Porque es aqui donde esta la autoridad, y por tanto aqui donde se puede
 * romper. Un cambio de persistencia que se filtre al cable —un valor que pase a
 * viajar como texto, un campo que se parta en dos— llegaria al SPA como
 * vocabulario de almacenamiento, y este test lo para antes de que alguien lo
 * escriba en un componente. Ademas corre dentro del gate de cierre, que es donde
 * tiene que fallar.
 *
 * <p>Es el mismo patron que {@link MatrizOperacionRolTest}: un test de este
 * modulo que lee ficheros de fuera del modulo porque la regla que vigila no cabe
 * dentro de uno solo.
 */
class FronteraDeAutoridadEnElSpaTest {

    /**
     * Vocabulario de ALMACENAMIENTO. Ninguno de estos nombres describe un dato:
     * describen donde vive, que es justo lo que el SPA no debe saber.
     */
    private static final Map<String, String> PROHIBIDO = Map.of(
            "atributo_propiedad", "es el nombre de una tabla",
            "atributoPropiedad", "es el nombre de una tabla",
            "catalogo_atributo", "es el nombre de una tabla",
            "campo_estructural", "es como el catalogo declara la autoridad",
            "campoEstructural", "es como el catalogo declara la autoridad",
            "valor_numero", "es una columna de la tabla de atributos",
            "valorNumero", "es una columna de la tabla de atributos",
            "valor_texto", "es una columna de la tabla de atributos",
            "valorTexto", "es una columna de la tabla de atributos");

    /**
     * Las seis claves que D-E4-3 movio de columna a atributo gobernado.
     *
     * <p>Estan aqui para lo contrario de lo que parece: <b>para comprobar que
     * SIGUEN existiendo por su nombre en el SPA</b>. Si desaparecieran seria la
     * senal de que alguien "migro" el frontend detras del backend, y eso
     * significaria que el contrato logico se movio cuando lo unico que debia
     * moverse era la autoridad fisica.
     */
    private static final List<String> CLAVES_QUE_NO_SE_MOVIERON = List.of(
            "ambientes", "antiguedadAnios", "frente", "zonificacion",
            "numeroEstacionamientos", "cuotaMantenimiento");

    // ==================================================================

    @Test
    @DisplayName("el SPA no nombra ninguna estructura de almacenamiento")
    void elSpaNoSabeDondeSeGuardaNada() {
        List<String> hallazgos = new ArrayList<>();

        for (Path fuente : fuentesDelSpa()) {
            List<String> lineas = leer(fuente);
            for (int i = 0; i < lineas.size(); i++) {
                String linea = lineas.get(i);
                int numero = i + 1;
                PROHIBIDO.forEach((termino, porque) -> {
                    if (linea.contains(termino)) {
                        hallazgos.add("  %s:%d  \"%s\"  (%s)"
                                .formatted(relativa(fuente), numero, termino, porque));
                    }
                });
            }
        }

        if (!hallazgos.isEmpty()) {
            fail("""
                    El SPA nombra estructuras de almacenamiento.

                    %s

                    Angular puede conocer la CLAVE LOGICA (`ambientes`) y el TIPO DE DATO
                    funcional (entero, decimal, texto). Nunca donde vive el valor: eso lo
                    resuelve `LectorPorAutoridad` en el servicio, y el cliente recibe
                    `ambientes = 5` sin enterarse de si salio de una fila o de una columna.

                    Si el dato hace falta en el SPA, pidelo por su nombre logico. Si lo que
                    hace falta es la REGLA (rango, obligatoriedad, aplicabilidad), tiene que
                    viajar como contrato desde el catalogo, no reimplementarse aqui (D-E4-3).
                    """.formatted(String.join("\n", hallazgos)));
        }
    }

    /**
     * <b>El contrato logico no se movio, y esto lo prueba.</b>
     *
     * <p>Toda la migracion de autoridad tenia una promesa: la autoridad fisica
     * cambia y el contrato logico no. La forma de comprobarla no es leer el
     * backend —ahi ya lo dicen los tests de ida y vuelta— sino mirar el otro
     * extremo: <b>el SPA sigue pidiendo las seis claves por su nombre de
     * siempre</b>, sin una sola linea de compatibilidad.
     *
     * <p>Si este test empezara a fallar habria que preguntar por que se toco el
     * frontend: la respuesta correcta a un cambio de autoridad es que no haga
     * falta tocarlo.
     */
    @Test
    @DisplayName("las seis claves siguen en el SPA con su nombre de siempre")
    void elContratoLogicoNoSeMovio() {
        String dto = String.join("\n", leer(rutaDelRepo("frontend-angular/src/app/core/api/locales.service.ts")));

        List<String> ausentes = CLAVES_QUE_NO_SE_MOVIERON.stream()
                .filter(clave -> !dto.contains(clave))
                .toList();

        if (!ausentes.isEmpty()) {
            fail("""
                    Estas claves desaparecieron del contrato del SPA: %s

                    D-E4-3 movio DONDE viven esos valores, no COMO se llaman. Que el
                    frontend deje de nombrarlas significa que el contrato logico se movio
                    tambien, y entonces la promesa de la decision —"la autoridad fisica
                    cambia, el contrato logico no"— dejo de cumplirse.
                    """.formatted(ausentes));
        }
    }

    // ==================================================================
    // BROX Web no decide QUE se pregunta para cada tipo de propiedad (D-A-1)
    // ==================================================================

    /**
     * Los siete tipos, en el vocabulario de la OFERTA. Es el del catalogo
     * ({@code catalogo_atributo}) y el que el motor de captura publica como
     * {@code opciones} de {@code tipoPropiedad}.
     *
     * <p>No incluye el vocabulario de la DEMANDA —{@code LOCAL_COMERCIAL},
     * {@code TERRENO_COMERCIAL}, {@code DEPOSITO_ALMACEN}— porque hoy son dos
     * catalogos distintos y unificarlos es trabajo del lado demanda, no de este
     * gate. Cuando se unifiquen, la lista de la demanda tendra que salir del
     * Core igual que esta, y entonces estas comprobaciones la cubriran sola.
     */
    private static final List<String> TIPOS_DE_PROPIEDAD = List.of(
            "LOCAL", "OFICINA", "DEPARTAMENTO", "CASA", "TERRENO", "ALMACEN", "OTRO");

    /**
     * {@code if (tipo === 'CASA') { mostrarDormitorios(); }} — la linea con la
     * que se pierde el modelo universal.
     *
     * <p>Parece un atajo de maquetacion y es el catalogo reescrito en Angular.
     * A partir de ella hay <b>dos</b> catalogos, y anadir un atributo deja de
     * ser una fila para pasar a ser un despliegue de dos piezas que ademas
     * pueden discrepar.
     *
     * <p>Lo que este gate NO prohibe: nombrar {@code dormitorios}, pintar un
     * control distinto para un {@code SELECTOR} que para un {@code INTERRUPTOR},
     * o agrupar los campos en las familias que el Core declara. Eso es
     * representar la pregunta, que es exactamente lo que le toca a BROX Web.
     */
    @Test
    @DisplayName("el SPA no ramifica por tipo de propiedad")
    void elSpaNoDecideQueSePreguntaSegunElTipo() {
        // Las dos direcciones y el `@case ('CASA')` del control flow de Angular:
        // `tipo === 'CASA'`, `'CASA' === tipo` y `@case ('CASA')` son la misma
        // rama escrita de tres formas, y el gate las ve igual (V76).
        String tipos = "(" + String.join("|", TIPOS_DE_PROPIEDAD) + ")";
        java.util.regex.Pattern comparacion = java.util.regex.Pattern.compile(
                "(===|!==|==|!=|\\bcase)\\s*\\(?\\s*['\"]" + tipos + "['\"]"
                        + "|['\"]" + tipos + "['\"]\\s*(===|!==|==|!=)");
        List<String> hallazgos = new ArrayList<>();

        for (Path fuente : fuentesDelSpa()) {
            if (esPrueba(fuente)) {
                continue;
            }
            List<String> lineas = leer(fuente);
            for (int i = 0; i < lineas.size(); i++) {
                java.util.regex.Matcher encontrado = comparacion.matcher(lineas.get(i));
                if (encontrado.find()) {
                    hallazgos.add("  %s:%d  %s".formatted(relativa(fuente), i + 1,
                            lineas.get(i).trim()));
                }
            }
        }

        if (!hallazgos.isEmpty()) {
            fail("""
                    El SPA ramifica por tipo de propiedad.

                    %s

                    Que un departamento pida dormitorios y un terreno pida zonificacion es
                    una regla de negocio, y su dueno es el catalogo. BROX Web pregunta
                    `GET /captura/definicion?tipoPropiedad=...&operacion=...` y pinta lo que
                    recibe en sus tres familias -- comunes, del tipo y de la operacion.

                    Con la regla escrita aqui habria dos catalogos: el real y el de Angular.
                    Y KAIROS necesitaria un tercero (D-A-1 §6).
                    """.formatted(String.join("\n", hallazgos)));
        }
    }

    /**
     * La otra forma de tener la matriz: no ramificar, sino <b>declarar la
     * lista</b>. Un fichero que nombra tres o mas tipos de propiedad esta
     * construyendo el catalogo de tipos o repartiendo campos entre ellos; las
     * dos cosas son del Core.
     *
     * <p>Tres y no dos porque dos literales pueden ser una comparacion legitima
     * entre dos casos concretos; tres ya es una tabla.
     *
     * <p>El selector de tipo del alta <b>no</b> necesita esta lista: sale de
     * {@code opciones} de la pregunta {@code tipoPropiedad}, que es como el
     * motor la publica desde el primer avance de la captura.
     */
    @Test
    @DisplayName("el SPA no lleva su propia lista de tipos de propiedad")
    void elSpaNoDeclaraElCatalogoDeTipos() {
        List<String> hallazgos = new ArrayList<>();

        for (Path fuente : fuentesDelSpa()) {
            if (esPrueba(fuente)) {
                continue;
            }
            String contenido = String.join("\n", leer(fuente));
            List<String> nombrados = TIPOS_DE_PROPIEDAD.stream()
                    .filter(tipo -> contenido.contains("'" + tipo + "'")
                            || contenido.contains("\"" + tipo + "\"")
                            || esClaveDeObjeto(contenido, tipo))
                    .toList();
            if (nombrados.size() >= 3) {
                hallazgos.add("  %s  nombra %s".formatted(relativa(fuente), nombrados));
            }
        }

        if (!hallazgos.isEmpty()) {
            fail("""
                    El SPA declara su propia lista de tipos de propiedad.

                    %s

                    La lista de los siete tipos es del catalogo, y viaja como `opciones` de
                    la pregunta `tipoPropiedad`. Copiarla aqui significa que anadir un tipo
                    -- que el modelo universal prometio que seria una fila -- vuelve a ser
                    un cambio en dos repositorios (D-A-1 §4).
                    """.formatted(String.join("\n", hallazgos)));
        }
    }

    /**
     * <b>La matriz incondicional</b>: {@code { LOCAL: [...], CASA: [...],
     * TERRENO: [...] }}. No compara nada y no lleva comillas, asi que ni la
     * comprobacion de ramas ni la de literales la veian -- y es exactamente la
     * tabla «tipo → campos» que el modelo universal existe para no tener. Un
     * tipo escrito como clave de objeto cuenta igual que uno entre comillas.
     */
    private static boolean esClaveDeObjeto(String contenido, String tipo) {
        return java.util.regex.Pattern
                .compile("(?m)(?:^|[{,;])\\s*" + tipo + "\\s*:")
                .matcher(contenido)
                .find();
    }

    // ------------------------------------------------------------------

    /**
     * Una prueba del SPA queda fuera de las dos comprobaciones de arriba: un
     * spec <b>afirma</b> contra el contrato —y para eso necesita literales— en
     * vez de implementarlo, y ademas no se despliega. Vigilarlo convertiria el
     * gate en un estorbo sin proteger nada.
     */
    private static boolean esPrueba(Path fichero) {
        return fichero.getFileName().toString().endsWith(".spec.ts");
    }

    private static List<Path> fuentesDelSpa() {
        Path raiz = rutaDelRepo("frontend-angular/src");
        try (Stream<Path> ficheros = Files.walk(raiz)) {
            return ficheros
                    .filter(Files::isRegularFile)
                    .filter(FronteraDeAutoridadEnElSpaTest::esFuente)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo recorrer " + raiz, e);
        }
    }

    private static boolean esFuente(Path fichero) {
        String nombre = fichero.getFileName().toString().toLowerCase(Locale.ROOT);
        return nombre.endsWith(".ts") || nombre.endsWith(".html");
    }

    private static List<String> leer(Path fichero) {
        try {
            return Files.readAllLines(fichero, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + fichero, e);
        }
    }

    private static String relativa(Path fichero) {
        return rutaDelRepo("").relativize(fichero).toString().replace('\\', '/');
    }

    /**
     * La raiz del repositorio, buscada subiendo desde el directorio de trabajo
     * —surefire lo fija en el basedir del modulo— en vez de asumir cuantos
     * niveles hay. Mismo criterio que {@link MatrizOperacionRolTest}.
     */
    private static Path rutaDelRepo(String rutaRelativa) {
        Path directorio = Path.of("").toAbsolutePath();
        while (directorio != null) {
            if (Files.isDirectory(directorio.resolve("frontend-angular/src"))) {
                return directorio.resolve(rutaRelativa);
            }
            directorio = directorio.getParent();
        }
        throw new IllegalStateException("No se encontro frontend-angular/src subiendo desde "
                + Path.of("").toAbsolutePath() + ". Sin el SPA este gate no puede vigilar nada.");
    }
}
