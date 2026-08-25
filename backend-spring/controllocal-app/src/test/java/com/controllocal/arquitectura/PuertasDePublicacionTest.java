package com.controllocal.arquitectura;

import com.controllocal.domain.inmueble.Publicacion;
import com.controllocal.service.PublicacionService;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>Ningun camino de creacion de publicacion elude la publicabilidad.</b>
 *
 * <p>Antes del 2026-08-24 habia CINCO vias en {@code PublicacionService} y solo
 * dos preguntaban por el catalogo:
 *
 * <ul>
 *   <li>{@code crearEnEncargo} — crea, y llama a {@code exigirPublicable};</li>
 *   <li>{@code cambiarEstado} — publica, y llama a {@code exigirPublicable};</li>
 *   <li>{@code actualizar} — edita, y <b>no toca el estado</b>: no puede publicar;</li>
 *   <li>{@code crear(idPropiedad, …)} — <b>creaba sin preguntar nada</b>;</li>
 *   <li>{@code sincronizar(idPropiedad, …)} — <b>creaba, dejaba en PUBLICADO y
 *       escribia hito {@code P}, sin preguntar nada</b>. Residuo del formulario
 *       de la v1, borrada el 2026-08-08.</li>
 * </ul>
 *
 * <p>Las dos ultimas tenian <b>cero consumidores de produccion</b> y ninguna
 * estaba expuesta por un controlador, asi que se retiraron en vez de hacerlas
 * delegar: <b>una via que delega sigue existiendo y puede desincronizarse; una
 * via que no existe no puede eludir nada.</b>
 *
 * <h2>Que comprueba esta clase, y que NO</h2>
 *
 * <p><b>Comprueba tres cosas</b>: que la superficie publica del servicio es
 * exactamente la esperada; que el unico constructor de anuncios
 * ({@code construir}) solo se invoca desde metodos que validan; y que nadie
 * fuera del servicio instancia una {@code Publicacion}.
 *
 * <p><b>No comprueba</b> que un metodo futuro no reimplemente {@code construir}
 * a mano dentro del propio {@code PublicacionServiceImpl} sin llamar a
 * {@code exigirPublicable} — eso lo caza la primera comprobacion, porque tendria
 * que exponerse en la interfaz para servir de algo, pero un metodo privado nuevo
 * que hiciera {@code new Publicacion()} y {@code save} desde otro metodo publico
 * ya cubierto <b>no se veria aqui</b>. Se dice en vez de fingir que si.
 */
class PuertasDePublicacionTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.controllocal");

    /** Las que crean o publican. Tocar esta lista es una decision, no un detalle. */
    private static final Set<String> CREAN_O_PUBLICAN = Set.of("crearEnEncargo", "cambiarEstado");

    /** El resto de la superficie: leen o editan sin cambiar el estado. */
    private static final Set<String> NO_CREAN = Set.of(
            "listarPorInmueble", "listarDeEncargo", "codigoEstadoPublicacion",
            "codigosEstadoPublicacion", "actualizar");

    @Test
    @DisplayName("la superficie de PublicacionService es exactamente la inventariada")
    void laSuperficieEsLaInventariada() {
        Set<String> declarados = Arrays.stream(PublicacionService.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> esperados = new TreeSet<>(CREAN_O_PUBLICAN);
        esperados.addAll(NO_CREAN);

        assertEquals(esperados, declarados,
                "cambio la superficie de PublicacionService. Si el metodo nuevo CREA o PUBLICA, "
                        + "tiene que llamar a exigirPublicable y declararse en CREAN_O_PUBLICAN; "
                        + "si no, en NO_CREAN. Lo que no vale es que aparezca sin decidirlo: asi "
                        + "entraron `crear` y `sincronizar`, que creaban anuncios sin preguntar "
                        + "por el catalogo.");
    }

    /**
     * <b>El unico constructor de anuncios solo se usa desde metodos que validan.</b>
     *
     * <p>{@code construir} es donde nace una {@code Publicacion} nueva. Si un
     * metodo lo invoca sin haber llamado antes a {@code exigirPublicable}, hay una
     * puerta abierta — es exactamente lo que hacian las dos vias retiradas.
     */
    @Test
    @DisplayName("todo metodo que construye un anuncio llama antes a exigirPublicable")
    void construirSoloDesdeMetodosQueValidan() {
        JavaClass impl = CLASES.get(
                "com.controllocal.service.impl.PublicacionServiceImpl");

        List<String> sinGuarda = impl.getMethods().stream()
                .filter(m -> llama(m, "construir"))
                .filter(m -> !llama(m, "exigirPublicable"))
                .map(JavaMethod::getName)
                .sorted()
                .toList();

        assertEquals(List.of(), sinGuarda,
                "estos metodos crean una publicacion sin pasar por exigirPublicable: " + sinGuarda);
    }

    /**
     * <b>Y nadie construye anuncios fuera del servicio que los gobierna.</b>
     *
     * <p>Sin esto, cualquier servicio podria instanciar una {@code Publicacion} y
     * guardarla por el repositorio, saltandose la interfaz entera.
     * {@code ContratoServiceImpl} escribe publicaciones —las CIERRA al firmar—
     * pero no crea ninguna, y por eso no aparece aqui.
     */
    @Test
    @DisplayName("solo PublicacionServiceImpl instancia una Publicacion")
    void nadieMasConstruyeAnuncios() {
        List<String> fuera = CLASES.stream()
                .filter(c -> !c.getName().equals(
                        "com.controllocal.service.impl.PublicacionServiceImpl"))
                .flatMap(c -> c.getConstructorCallsFromSelf().stream())
                .filter(call -> call.getTargetOwner().isEquivalentTo(Publicacion.class))
                .map(call -> call.getOriginOwner().getSimpleName())
                .distinct()
                .sorted()
                .toList();

        assertEquals(List.of(), fuera,
                "estas clases construyen una Publicacion por su cuenta: " + fuera);
    }

    /** Las dos vias retiradas no vuelven, ni con otra firma. */
    @Test
    @DisplayName("crear(idPropiedad, ...) y sincronizar(...) siguen retirados")
    void lasDosViasRetiradasNoVuelven() {
        assertTrue(Arrays.stream(PublicacionService.class.getMethods())
                        .noneMatch(m -> m.getName().equals("crear")),
                "volvio `crear`: creaba anuncios nombrando solo el inmueble, sin exigirPublicable");
        assertTrue(Arrays.stream(PublicacionService.class.getMethods())
                        .noneMatch(m -> m.getName().equals("sincronizar")),
                "volvio `sincronizar`: creaba, publicaba y escribia hito sin preguntar por el catalogo");
    }

    private static boolean llama(JavaMethod metodo, String nombre) {
        return metodo.getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTarget().getName().equals(nombre));
    }
}
