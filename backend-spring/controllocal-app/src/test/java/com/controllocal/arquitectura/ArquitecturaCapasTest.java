package com.controllocal.arquitectura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Heredero del ArquitecturaCapasTest del backend Jakarta: el build FALLA si
 * se viola el orden de capas (Doc 5 §1): app -> web -> service -> persistence -> domain.
 * La web solo habla con el service y NUNCA ve entidades ni repositorios:
 * los DTOs son la frontera (contrato congelado).
 */
class ArquitecturaCapasTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.controllocal");

    @Test
    void lasCapasSonEstrictas() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("App").definedBy("com.controllocal.app..")
                .layer("Web").definedBy("com.controllocal.web..")
                .layer("Service").definedBy("com.controllocal.service..")
                .layer("Persistence").definedBy("com.controllocal.persistence..")
                .layer("Domain").definedBy("com.controllocal.domain..")
                .whereLayer("Web").mayOnlyBeAccessedByLayers("App")
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "App")
                .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service", "App")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Service", "Persistence", "App")
                .check(CLASES);
    }

    @Test
    void laWebNoTocaPersistenciaNiDominio() {
        noClasses().that().resideInAPackage("com.controllocal.web..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.controllocal.persistence..", "com.controllocal.domain..")
                .because("la web consume DTOs del service; las entidades no cruzan la frontera REST")
                .check(CLASES);
    }

    @Test
    void soloPersistenciaUsaJpa() {
        noClasses().that().resideInAPackage("com.controllocal.web..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.springframework.data..")
                .because("el acceso a datos vive en persistence; la web no consulta la BD (regla heredada de la v1)")
                .check(CLASES);
    }
}
