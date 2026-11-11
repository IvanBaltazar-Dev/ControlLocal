package com.controllocal.arquitectura;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;

/** Frontera de artefactos del gate: ninguna exclusión por paquete, clase o nombre Test. */
final class ImportacionesDeProducto {
    // ArchUnit Location ya contiene una URI normalizada, con '/' incluso en Windows.
    // El filtro estándar no reconoce <salida aislada>/<módulo>/test-classes.
    static final List<ImportOption> OPCIONES = List.of(
            new ImportOption.DoNotIncludeTests(),
            location -> !location.contains("/test-classes/"));

    private ImportacionesDeProducto() { }

    static ClassFileImporter importador() {
        // ArchUnit combina las opciones mediante AND; se conserva su filtro estándar.
        return new ClassFileImporter().withImportOptions(OPCIONES);
    }
}
