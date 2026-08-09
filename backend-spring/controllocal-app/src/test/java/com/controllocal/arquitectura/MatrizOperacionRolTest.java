package com.controllocal.arquitectura;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Cierra la deuda transversal "matriz completa operacion-&gt;rol con test de
 * cobertura": hasta aqui los gates replicaban los de la v1 endpoint por
 * endpoint, sin ninguna prueba que demostrara que estaban TODOS.
 *
 * <p>La fuente de verdad es {@code docs/ai/matriz-operacion-rol.md} —un
 * documento, no una constante— porque su otro consumidor es humano: el SPA
 * Angular se apoya en el para decidir que muestra cada rol. Este test lo parsea
 * y rompe el build si el documento y el codigo dejan de coincidir, de modo que
 * <b>no puede quedar desactualizado</b>.
 *
 * <p>Lo que vigila, en las cuatro pruebas:
 * <ol>
 *   <li><b>Cobertura</b>: un endpoint nuevo no entra sin una fila que declare su
 *       decision de rol.</li>
 *   <li><b>Sin filas muertas</b>: una fila que sobrevive al endpoint que
 *       describia es peor que no tenerla.</li>
 *   <li><b>La matriz no miente</b>: los roles declarados son exactamente los que
 *       dice {@code @PreAuthorize} (de metodo, o de clase si el metodo no lo
 *       trae). Las operaciones sin gate deben declararse {@code TODOS} y traer
 *       nota de alcance: en este backend 53 operaciones autenticadas no filtran
 *       por rol sino por alcance, y ese silencio es justo lo que habia que
 *       documentar.</li>
 *   <li><b>Publico de verdad</b>: {@code PUBLICO} en el documento y
 *       {@code permitAll()} en {@code ConfiguracionSeguridad} son el mismo
 *       conjunto, en los dos sentidos.</li>
 * </ol>
 */
class MatrizOperacionRolTest {

    private static final String PAQUETE_CONTROLADORES = "com.controllocal.web.controlador";
    private static final String DOC = "docs/ai/matriz-operacion-rol.md";
    private static final String CONFIG_SEGURIDAD =
            "backend-spring/controllocal-web/src/main/java/com/controllocal/web/seguridad/"
                    + "ConfiguracionSeguridad.java";

    /** Sin gate de rol: autenticado, y lo que cambia por rol es el ALCANCE. */
    private static final String TODOS = "TODOS";
    /** Sin token: tiene que estar en la lista permitAll de ConfiguracionSeguridad. */
    private static final String PUBLICO = "PUBLICO";

    private static final Pattern ROL = Pattern.compile("'([A-Z_]+)'");
    private static final Pattern LITERAL = Pattern.compile("\"([^\"]+)\"");
    private static final Set<String> VERBOS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(PAQUETE_CONTROLADORES);

    /** Clave de la matriz: verbo + ruta completa ya normalizada. */
    private record Operacion(String metodo, String ruta) implements Comparable<Operacion> {
        @Override
        public int compareTo(Operacion otra) {
            int porRuta = ruta.compareTo(otra.ruta);
            return porRuta != 0 ? porRuta : metodo.compareTo(otra.metodo);
        }

        @Override
        public String toString() {
            return metodo + " " + ruta;
        }
    }

    /** Fila del documento: roles declarados + por que no hay gate, cuando no lo hay. */
    private record Fila(String roles, String alcance) {
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    void todaOperacionDelCodigoEstaEnLaMatriz() {
        Set<Operacion> enElCodigo = operacionesDelCodigo().keySet();
        Set<Operacion> enElDoc = filasDelDocumento().keySet();

        List<Operacion> sinDeclarar = new ArrayList<>(new TreeSet<>(enElCodigo));
        sinDeclarar.removeAll(enElDoc);

        if (!sinDeclarar.isEmpty()) {
            fail("Hay " + sinDeclarar.size() + " operacion(es) REST sin fila en " + DOC
                    + ". Un endpoint nuevo exige una decision de rol EXPLICITA; anade su fila"
                    + " (roles + alcance) antes de darlo por hecho:\n  " + unaPorLinea(sinDeclarar));
        }
    }

    @Test
    void laMatrizNoTieneFilasMuertas() {
        Set<Operacion> enElCodigo = operacionesDelCodigo().keySet();
        Set<Operacion> enElDoc = filasDelDocumento().keySet();

        List<Operacion> sobran = new ArrayList<>(new TreeSet<>(enElDoc));
        sobran.removeAll(enElCodigo);

        if (!sobran.isEmpty()) {
            fail("Hay " + sobran.size() + " fila(s) en " + DOC + " que ya no corresponden a"
                    + " ningun endpoint. Una fila que sobrevive al endpoint que describia"
                    + " desinforma al SPA; borrala:\n  " + unaPorLinea(sobran));
        }
    }

    @Test
    void losRolesDeclaradosSonLosQueAplicaSpringSecurity() {
        Map<Operacion, String> codigo = operacionesDelCodigo();
        Map<Operacion, Fila> doc = filasDelDocumento();

        List<String> desviaciones = new ArrayList<>();
        for (Operacion operacion : new TreeSet<>(codigo.keySet())) {
            Fila fila = doc.get(operacion);
            if (fila == null) {
                continue; // lo reporta todaOperacionDelCodigoEstaEnLaMatriz
            }
            String efectivos = codigo.get(operacion);
            // PUBLICO tambien es "sin gate de rol"; que ademas sea permitAll lo
            // comprueba lasRutasPublicasSonExactamenteLasDePermitAll.
            String declarados = PUBLICO.equals(fila.roles()) ? TODOS : ordenados(fila.roles());

            if (!efectivos.equals(declarados)) {
                desviaciones.add(operacion + " -> la matriz dice [" + fila.roles()
                        + "] y @PreAuthorize aplica [" + efectivos + "]");
            } else if (TODOS.equals(efectivos) && fila.alcance().isBlank()) {
                desviaciones.add(operacion + " -> declarada TODOS con la columna Alcance VACIA:"
                        + " sin gate de rol, el alcance es lo unico que limita lo que devuelve");
            }
        }

        if (!desviaciones.isEmpty()) {
            fail("La matriz miente en " + desviaciones.size() + " operacion(es) de " + DOC
                    + ":\n  " + String.join("\n  ", desviaciones));
        }
    }

    @Test
    void lasRutasPublicasSonExactamenteLasDePermitAll() {
        Set<String> enElDoc = new TreeSet<>();
        filasDelDocumento().forEach((operacion, fila) -> {
            if (PUBLICO.equals(fila.roles())) {
                enElDoc.add(operacion.ruta());
            }
        });
        Set<String> permitAll = rutasPermitAll();

        // 1) Nada se declara publico en el doc sin estar en permitAll.
        enElDoc.forEach(ruta -> assertTrue(permitAll.contains(ruta),
                "La matriz declara PUBLICO " + ruta + ", pero ConfiguracionSeguridad no lo deja"
                        + " en permitAll: sin token responde 401, no la respuesta publica"
                        + " que el doc promete"));

        // 2) Ni al reves: abrir una ruta propia en permitAll y no declararla es
        //    como se cuela un endpoint sin autenticar.
        Set<String> rutasDelCodigo = new TreeSet<>();
        operacionesDelCodigo().keySet().forEach(operacion -> rutasDelCodigo.add(operacion.ruta()));
        Set<String> abiertasSinDeclarar = new TreeSet<>(permitAll);
        abiertasSinDeclarar.retainAll(rutasDelCodigo);
        abiertasSinDeclarar.removeAll(enElDoc);

        assertEquals(Set.of(), abiertasSinDeclarar,
                "Estas rutas estan en permitAll de ConfiguracionSeguridad pero la matriz no las"
                        + " declara PUBLICO. Abrir un endpoint sin dejarlo escrito es exactamente"
                        + " el descuido que este test viene a evitar");
    }

    // ------------------------------------------------------------- el codigo

    /** Operacion -&gt; roles efectivos (lista ordenada separada por coma, o TODOS). */
    private static Map<Operacion, String> operacionesDelCodigo() {
        Map<Operacion, String> operaciones = new LinkedHashMap<>();
        for (JavaClass controlador : CLASES) {
            String base = primerValor(anotacion(controlador, RequestMapping.class));
            String rolesDeClase = roles(controlador.tryGetAnnotationOfType(PreAuthorize.class)
                    .map(PreAuthorize::value).orElse(null));

            for (JavaMethod metodo : controlador.getMethods()) {
                String rolesDeMetodo = metodo.tryGetAnnotationOfType(PreAuthorize.class)
                        .map(PreAuthorize::value)
                        .map(MatrizOperacionRolTest::roles)
                        .orElse(rolesDeClase);

                for (Map.Entry<String, String[]> mapeo : mapeos(metodo).entrySet()) {
                    String[] rutas = mapeo.getValue().length == 0 ? new String[] {""} : mapeo.getValue();
                    for (String ruta : rutas) {
                        operaciones.put(new Operacion(mapeo.getKey(), unir(base, ruta)), rolesDeMetodo);
                    }
                }
            }
        }
        return operaciones;
    }

    /** Verbo HTTP -&gt; rutas declaradas, para el unico mapeo que puede traer un metodo. */
    private static Map<String, String[]> mapeos(JavaMethod metodo) {
        Map<String, String[]> mapeos = new LinkedHashMap<>();
        metodo.tryGetAnnotationOfType(GetMapping.class)
                .ifPresent(a -> mapeos.put("GET", a.value()));
        metodo.tryGetAnnotationOfType(PostMapping.class)
                .ifPresent(a -> mapeos.put("POST", a.value()));
        metodo.tryGetAnnotationOfType(PutMapping.class)
                .ifPresent(a -> mapeos.put("PUT", a.value()));
        metodo.tryGetAnnotationOfType(PatchMapping.class)
                .ifPresent(a -> mapeos.put("PATCH", a.value()));
        metodo.tryGetAnnotationOfType(DeleteMapping.class)
                .ifPresent(a -> mapeos.put("DELETE", a.value()));
        return mapeos;
    }

    private static <A extends Annotation> A anotacion(JavaClass clase, Class<A> tipo) {
        return clase.tryGetAnnotationOfType(tipo).orElse(null);
    }

    private static String primerValor(RequestMapping mapeo) {
        if (mapeo == null) {
            return "";
        }
        if (mapeo.value().length > 0) {
            return mapeo.value()[0];
        }
        return mapeo.path().length > 0 ? mapeo.path()[0] : "";
    }

    /**
     * {@code hasAnyRole('BROKER', 'ADMIN')} -&gt; {@code "ADMIN, BROKER"}.
     * Se ordena para que la matriz no dependa del orden en que se escribio la
     * anotacion. Sin expresion, TODOS.
     */
    private static String roles(String expresion) {
        if (expresion == null || expresion.isBlank()) {
            return TODOS;
        }
        Set<String> roles = new TreeSet<>();
        Matcher matcher = ROL.matcher(expresion);
        while (matcher.find()) {
            roles.add(matcher.group(1));
        }
        return roles.isEmpty() ? TODOS : String.join(", ", roles);
    }

    /**
     * Canoniza la lista de roles de una fila del documento. El orden en que se
     * escriben no significa nada —"BROKER, ADMIN" y "ADMIN, BROKER" son el mismo
     * permiso—, asi que la comparacion no puede depender de el; el documento se
     * escribe como se lee mejor.
     */
    private static String ordenados(String declarados) {
        if (TODOS.equals(declarados) || declarados.isBlank()) {
            return declarados;
        }
        Set<String> roles = new TreeSet<>();
        for (String rol : declarados.split(",")) {
            if (!rol.isBlank()) {
                roles.add(rol.trim());
            }
        }
        return String.join(", ", roles);
    }

    /** Ruta de clase + ruta de metodo, con las barras colapsadas: {@code /a/b}. */
    private static String unir(String base, String metodo) {
        String ruta = ("/" + base + "/" + metodo).replaceAll("/+", "/");
        return ruta.length() > 1 && ruta.endsWith("/") ? ruta.substring(0, ruta.length() - 1) : ruta;
    }

    // ---------------------------------------------------------- el documento

    /** Filas de las tablas del documento; ignora encabezados, separadores y prosa. */
    private static Map<Operacion, Fila> filasDelDocumento() {
        Map<Operacion, Fila> filas = new LinkedHashMap<>();
        for (String linea : lineas(DOC)) {
            if (!linea.startsWith("|")) {
                continue;
            }
            String[] celdas = linea.split("\\|", -1);
            if (celdas.length < 6) {
                continue; // tablas de menos columnas (la leyenda de Roles)
            }
            String metodo = celdas[1].trim();
            if (!VERBOS.contains(metodo)) {
                continue; // encabezado, separador o fila de otra tabla
            }
            String ruta = celdas[2].trim().replace("`", "");
            Operacion operacion = new Operacion(metodo, ruta);

            Fila previa = filas.put(operacion, new Fila(celdas[3].trim(), celdas[4].trim()));
            assertTrue(previa == null, "La matriz declara dos veces " + operacion
                    + ": una operacion con dos filas es una contradiccion esperando a pasar");
        }
        assertTrue(filas.size() > 100, "Solo se parsearon " + filas.size() + " filas de " + DOC
                + ": el formato de la tabla cambio y el test dejo de leerla de verdad");
        return filas;
    }

    /** Rutas abiertas sin token, leidas de la fuente de ConfiguracionSeguridad. */
    private static Set<String> rutasPermitAll() {
        Set<String> rutas = new LinkedHashSet<>();
        for (String linea : lineas(CONFIG_SEGURIDAD)) {
            if (!linea.contains("requestMatchers(") || !linea.contains("permitAll()")) {
                continue;
            }
            Matcher matcher = LITERAL.matcher(linea);
            while (matcher.find()) {
                rutas.add(matcher.group(1));
            }
        }
        assertTrue(!rutas.isEmpty(), "No se pudo leer ninguna ruta permitAll de " + CONFIG_SEGURIDAD
                + ": si la configuracion cambio de forma, este test dejo de vigilar lo publico");
        return rutas;
    }

    // ------------------------------------------------------------- utilidades

    /**
     * Los dos archivos que este test lee viven fuera del modulo, asi que se
     * busca la raiz del repositorio subiendo desde el directorio de trabajo
     * (surefire lo fija en el basedir del modulo) en vez de asumir cuantos
     * niveles hay.
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
                + Path.of("").toAbsolutePath() + ". Es la fuente de verdad de la matriz"
                + " operacion-rol; sin ella este test no puede vigilar nada.");
    }

    private static String unaPorLinea(List<Operacion> operaciones) {
        return operaciones.stream().map(Operacion::toString)
                .reduce((a, b) -> a + "\n  " + b).orElse("");
    }
}
