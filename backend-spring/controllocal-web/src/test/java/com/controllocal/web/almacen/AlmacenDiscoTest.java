package com.controllocal.web.almacen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preparacion del almacenamiento para S3 (Bloque 8), verificada sobre el
 * proveedor que hay hoy: DISCO.
 *
 * <p>Lo que fija esta suite es el <b>formato de la clave</b>, no el backend:
 * las claves nuevas cuelgan de {@code tenant/{organizacionId}/}, y las
 * anteriores —que no llevan prefijo— <b>se siguen leyendo sin migrar nada</b>.
 * Ese es justo el invariante que permite mover los binarios una sola vez,
 * cuando llegue S3, en vez de dos.
 */
class AlmacenDiscoTest {

    private static final byte[] CONTENIDO = "contenido binario".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("la clave nueva cuelga de tenant/{organizacionId}/")
    void laClaveNuevaLlevaElTenant(@TempDir Path raiz) {
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());

        var guardado = almacen.guardar(AlmacenDocumentos.carpetaDeTenant(7, "locales/42"),
                "fachada.png", CONTENIDO, "image/png");

        assertTrue(guardado.clave().startsWith("tenant/7/locales/42/"), guardado.clave());
        // El nombre original se conserva para la cabecera de descarga, aunque
        // la clave lleve el uuid que la hace no adivinable.
        assertEquals("fachada.png", guardado.nombre());
    }

    @Test
    @DisplayName("dos organizaciones no comparten prefijo aunque suban el mismo archivo")
    void dosOrganizacionesNoCompartenPrefijo(@TempDir Path raiz) {
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());

        String unaOrg = almacen.guardar(AlmacenDocumentos.carpetaDeTenant(7, "perfiles"),
                "foto.png", CONTENIDO, "image/png").clave();
        String otraOrg = almacen.guardar(AlmacenDocumentos.carpetaDeTenant(9, "perfiles"),
                "foto.png", CONTENIDO, "image/png").clave();

        assertTrue(unaOrg.startsWith("tenant/7/"), unaOrg);
        assertTrue(otraOrg.startsWith("tenant/9/"), otraOrg);
        // El prefijo es lo que en S3 sostiene la politica de aislamiento; en
        // disco ya separa los arboles.
        assertFalse(unaOrg.startsWith("tenant/9/"));
    }

    @Test
    @DisplayName("subir, leer y eliminar sobre una clave con tenant")
    void cicloCompletoConTenant(@TempDir Path raiz) {
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());
        String clave = almacen.guardar(AlmacenDocumentos.carpetaDeTenant(7, "SOL-0001"),
                "contrato.pdf", CONTENIDO, "application/pdf").clave();

        Optional<AlmacenDocumentos.ArchivoDescargado> leido = almacen.abrir(clave);
        assertTrue(leido.isPresent());
        assertArrayEquals(CONTENIDO, leido.get().contenido());
        assertEquals("application/pdf", leido.get().contentType());

        almacen.eliminar(clave);
        assertTrue(almacen.abrir(clave).isEmpty(), "tras eliminar no debe quedar el binario");
    }

    @Test
    @DisplayName("una clave ANTIGUA (sin tenant) se sigue leyendo: no hay migracion en este bloque")
    void laClaveAntiguaSeSigueLeyendo(@TempDir Path raiz) throws Exception {
        // Se escribe a mano con el formato viejo, que es lo que hay hoy en el
        // volumen: la fuente de verdad es la clave guardada en PostgreSQL, y
        // este bloque no la reescribe.
        Path antiguo = raiz.resolve("locales/42/abcd1234-fachada.png");
        Files.createDirectories(antiguo.getParent());
        Files.write(antiguo, CONTENIDO);
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());

        Optional<AlmacenDocumentos.ArchivoDescargado> leido =
                almacen.abrir("locales/42/abcd1234-fachada.png");

        assertTrue(leido.isPresent(), "los binarios ya subidos no pueden dejar de verse");
        assertArrayEquals(CONTENIDO, leido.get().contenido());
    }

    @Test
    @DisplayName("un archivo inexistente responde vacio, no explota")
    void archivoInexistenteRespondeVacio(@TempDir Path raiz) {
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());

        assertTrue(almacen.abrir("tenant/7/locales/42/no-existe.png").isEmpty());
        // Eliminar lo que ya no esta es best-effort: el registro pudo borrarse
        // antes y el huerfano no puede tumbar la operacion.
        almacen.eliminar("tenant/7/locales/42/no-existe.png");
    }

    @Test
    @DisplayName("una clave que intenta salir de la raiz no resuelve a nada")
    void noSePuedeEscaparDeLaRaiz(@TempDir Path raiz) {
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());

        assertTrue(almacen.abrir("../../etc/passwd").isEmpty());
        assertTrue(almacen.abrir("tenant/7/../../../secreto.txt").isEmpty());
    }

    @Test
    @DisplayName("el proveedor sigue siendo DISCO: S3 llega en el Bloque 8")
    void elProveedorSigueSiendoDisco(@TempDir Path raiz) {
        assertEquals("DISCO", new AlmacenDisco(raiz.toString()).proveedor());
    }
}
