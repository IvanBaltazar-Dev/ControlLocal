package com.controllocal.arquitectura;

import com.controllocal.service.Actor;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class ImportacionesDeProductoTest {
    @Test void importaMainYExcluyeTestEnElClasspathRealSinVacuidad() {
        // Importación explícita e intencional del control negativo: demuestra que el artefacto existe.
        var sinFiltro = new ClassFileImporter().importClasses(ImportacionesDeProductoTest.class);
        assertTrue(sinFiltro.stream().anyMatch(c -> c.isEquivalentTo(ImportacionesDeProductoTest.class)));
        var producto = ImportacionesDeProducto.importador().importClasses(Actor.class,ImportacionesDeProductoTest.class);
        assertTrue(producto.stream().anyMatch(c -> c.isEquivalentTo(Actor.class)),"El filtro debe conservar Main real.");
        assertFalse(producto.stream().anyMatch(c -> c.isEquivalentTo(ImportacionesDeProductoTest.class)),"El filtro debe excluir TEST real, también en salida aislada.");
    }

    @ParameterizedTest @ValueSource(strings={
            "file:/D:/repo/modulo/target/test-classes/com/Prueba.class",
            "file:/D:/ensayo/modulo/test-classes/com/Prueba.class",
            "file:/D:/ensayo%20con%20espacios/modulo/test-classes/com/Prueba.class",
            "file:/D:/ensayo/modulo/./test-classes/com/Prueba.class",
            "file:/D:/ensayo/modulo/classes/../test-classes/com/Prueba.class",
            "file:/D:/repo/modulo/build/classes/test/com/Prueba.class",
            "file:/D:/repo/modulo/build/classes/java/test/com/Prueba.class",
            "file:/D:/repo/modulo/out/test/com/Prueba.class",
            "jar:file:/D:/ensayo/modulo/test-classes/fixtures.jar!/com/Prueba.class",
            "file:/D:/ensayo/modulo/test-classes"})
    void rechazaDirectoriosDePruebaSinDependerDeTarget(String uri) {
        assertFalse(incluida(uri),uri);
    }

    @ParameterizedTest @ValueSource(strings={
            "file:/D:/ensayo/modulo/classes/com/Producto.class",
            "file:/D:/repo/modulo/target/classes/com/Producto.class",
            "file:/D:/ensayo/modulo/classes/com/ProductoTest.class",
            "file:/D:/ensayo/modulo/classes/com/controllocal/integracion/Producto.class",
            "file:/D:/ensayo/modulo/classes/com/controllocal/arquitectura/Producto.class",
            "file:/D:/ensayo/modulo/test-classes-extra/com/Producto.class",
            "file:/D:/ensayo/modulo/test-classes/../classes/com/Producto.class",
            "jar:file:/D:/lib/producto.jar!/com/ProductoTest.class"})
    void conservaProductoSinExcluirNombresPaquetesNiSegmentosParecidos(String uri) {
        assertTrue(incluida(uri),uri);
    }

    private static boolean incluida(String uri) {
        var location = Location.of(URI.create(uri));
        return ImportacionesDeProducto.OPCIONES.stream().allMatch(opcion -> opcion.includes(location));
    }
}
