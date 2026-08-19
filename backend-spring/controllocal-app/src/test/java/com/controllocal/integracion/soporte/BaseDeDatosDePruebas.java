package com.controllocal.integracion.soporte;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * La frontera entre una prueba de integracion y la base contra la que escribe.
 *
 * <h2>Por que existe</h2>
 *
 * <p>El 18 y el 19 de agosto de 2026 alguien corrio las pruebas de integracion
 * con {@code TEST_DB_URL} apuntando a {@code controllocal_dev}. Nada se lo
 * impidio, porque cada prueba leia la variable directamente y le pasaba a Spring
 * lo que hubiera. Resultado medido el 2026-08-19:
 *
 * <ul>
 *   <li><b>162 propiedades</b> de prueba en la base de desarrollo,</li>
 *   <li><b>120 captaciones</b> pendientes que la cabecera del Inicio contaba
 *       como trabajo real —«125 cosas necesitan tu atencion»—,</li>
 *   <li><b>184 hitos de precio</b> que convertian la unica celda con muestra del
 *       contraste de renta en 42 filas a 7000 y 21 a 7500.</li>
 * </ul>
 *
 * <p>Es un defecto de <b>evidencia</b>, no de datos: mientras el residuo este
 * ahi, ninguna pantalla de E2 se puede evaluar a ojo, que es el requisito propio
 * de la etapa. Borrarlo sin cerrar la causa solo prepara la siguiente
 * contaminacion, asi que la limpieza y esta guarda viajan en el mismo cambio.
 *
 * <h2>Que hace</h2>
 *
 * <p>Falla <b>antes de arrancar</b>. Se invoca desde {@code @DynamicPropertySource},
 * que corre durante la inicializacion del contexto: antes de que Flyway migre y
 * mucho antes de que ninguna prueba escriba una fila. El mensaje nombra la base
 * y dice cual usar, porque un fallo que no dice como arreglarse se resuelve
 * quitando la guarda.
 *
 * <h2>Que base se considera de pruebas</h2>
 *
 * <p>Una <b>lista explicita</b> ({@link #AUTORIZADAS}) mas patrones para bases
 * efimeras ({@link #PREFIJOS}, {@link #SUFIJOS}), pensados para testcontainers y
 * para una base por rama en CI. Lo que no encaja, se rechaza: la guarda deniega
 * por defecto, que es la unica forma de que una base nueva no herede permiso por
 * descuido.
 *
 * <h2>Como se mantiene cerrada</h2>
 *
 * <p>{@code AislamientoDePruebasTest} recorre el arbol de integracion y rompe el
 * build si una prueba vuelve a leer {@code TEST_DB_URL} por su cuenta. Sin ese
 * gate, la guarda dura hasta la proxima prueba escrita copiando y pegando otra.
 */
public final class BaseDeDatosDePruebas {

    /** La variable que dice contra que base corre la suite. */
    public static final String VARIABLE = "TEST_DB_URL";

    /**
     * Bases nombradas que una prueba de integracion puede escribir. Hoy solo la
     * del reactor; {@code controllocal_dev} no esta aqui y no puede estarlo.
     */
    public static final Set<String> AUTORIZADAS = Set.of("controllocal_repositorios");

    /** Bases efimeras creadas por el runner: testcontainers, CI por rama. */
    static final List<String> PREFIJOS = List.of("test_", "tc_", "controllocal_ci_");

    /** La otra convencion habitual: el proposito al final del nombre. */
    static final List<String> SUFIJOS = List.of("_pruebas", "_test", "_ci");

    private BaseDeDatosDePruebas() {
    }

    /**
     * Registra origen, usuario y contrasena de la base de pruebas, validando
     * antes que sea una base que se puede escribir.
     *
     * <p>Es el unico punto por el que una prueba de integracion obtiene su
     * origen de datos. Las tres propiedades van juntas porque separarlas invita
     * a registrar la url por un lado y validarla por otro.
     *
     * @throws IllegalStateException si la variable falta, no es PostgreSQL o
     *                               nombra una base que no es de pruebas
     */
    public static void registrar(DynamicPropertyRegistry propiedades) {
        String url = url();
        propiedades.add("spring.datasource.url", () -> url);
        propiedades.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("TEST_DB_USER", "controllocal"));
        propiedades.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "controllocal"));
    }

    /** La url ya validada. Publica para que el gate pueda ejercitarla. */
    public static String url() {
        String url = System.getenv(VARIABLE);
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    VARIABLE + " no esta definida. Una prueba de integracion necesita "
                            + "PostgreSQL real; ver verificacion/Verificar-Cierre.ps1.");
        }
        return validar(url.trim());
    }

    /**
     * Comprueba que la url apunte a una base que se puede escribir sin destruir
     * trabajo de nadie.
     *
     * <p>Separada de {@link #url()} para que el gate la pueda probar con urls
     * inventadas sin tocar el entorno.
     */
    public static String validar(String url) {
        if (!url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException(
                    VARIABLE + " tiene que apuntar a PostgreSQL real, y apunta a: " + url);
        }
        String base = nombreDeLaBase(url);
        if (base.isEmpty()) {
            throw new IllegalStateException(
                    VARIABLE + " no nombra ninguna base de datos: " + url);
        }
        if (!esDePruebas(base)) {
            throw new IllegalStateException(rechazo(base));
        }
        return url;
    }

    /**
     * El nombre de la base dentro de una url JDBC, sin parametros.
     *
     * <p>{@code jdbc:postgresql://localhost:5433/controllocal_repositorios?ssl=false}
     * da {@code controllocal_repositorios}.
     */
    static String nombreDeLaBase(String url) {
        String sinParametros = url.split("\\?", 2)[0];
        int barra = sinParametros.lastIndexOf('/');
        if (barra < 0 || barra == sinParametros.length() - 1) {
            return "";
        }
        return sinParametros.substring(barra + 1).toLowerCase(Locale.ROOT);
    }

    /** Si el nombre encaja con la lista o con alguno de los patrones efimeros. */
    static boolean esDePruebas(String base) {
        return AUTORIZADAS.contains(base)
                || PREFIJOS.stream().anyMatch(base::startsWith)
                || SUFIJOS.stream().anyMatch(base::endsWith);
    }

    /** El mensaje dice que base se rechazo, por que, y cual usar en su lugar. */
    private static String rechazo(String base) {
        return "Las pruebas de integracion escriben, borran y migran: no pueden correr contra '"
                + base + "'.\n"
                + "  Bases autorizadas: " + String.join(", ", AUTORIZADAS) + "\n"
                + "  O una efimera que empiece por " + String.join("/", PREFIJOS)
                + " o acabe en " + String.join("/", SUFIJOS) + ".\n"
                + "  Corrige " + VARIABLE + ", por ejemplo:\n"
                + "    jdbc:postgresql://localhost:5433/controllocal_repositorios\n"
                + "  (El 2026-08-18 esta guarda no existia y la suite dejo 162 propiedades "
                + "de prueba en controllocal_dev.)";
    }
}
