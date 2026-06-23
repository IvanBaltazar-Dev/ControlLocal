package com.controllocal.rest.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Congela la regla de arquitectura por capas:
 *   Frontend -> REST -> BL -> DAO -> DBManager.
 * La capa REST (presentacion) solo puede hablar con la BL; nunca con el DAO ni con
 * la conexion a la BD directamente. Si alguien lo viola, el build falla aqui.
 */
class ArquitecturaCapasTest {

    private static final JavaClasses CLASES =
            new ClassFileImporter().importPackages("com.controllocal");

    @Test
    void la_capa_rest_no_accede_directamente_al_dao() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("com.controllocal.rest..")
                .should().dependOnClassesThat().resideInAPackage("com.controllocal.dao..")
                .because("La capa REST solo debe comunicarse con la BL; el DAO se accede unicamente desde la BL.");
        regla.check(CLASES);
    }

    @Test
    void la_capa_rest_no_accede_directamente_a_la_conexion_de_bd() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("com.controllocal.rest..")
                .should().dependOnClassesThat().resideInAPackage("com.controllocal.config..")
                .because("La conexion a la BD (DBManager) es responsabilidad de las capas inferiores, no de REST.");
        regla.check(CLASES);
    }
}
