package com.controllocal.arquitectura;

import com.controllocal.domain.comun.Transicionable;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Test de cobertura de la auditoria (Doc 5 §7, RC-002): el estado de una
 * entidad Transicionable SOLO muta a traves del componente Transiciones,
 * que emite historial_estado en la misma transaccion. Si cualquier otra
 * clase llama transicionarA (la causa de MEJ-01: llamadas manuales que se
 * olvidan), el build FALLA.
 */
class ArquitecturaAuditoriaTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.controllocal");

    @Test
    void soloElComponenteTransicionesMutaElEstado() {
        noClasses()
                .that().doNotHaveFullyQualifiedName("com.controllocal.service.soporte.Transiciones")
                .should().callMethodWhere(new DescribedPredicate<JavaMethodCall>(
                        "invocan transicionarA(..) de un Transicionable") {
                    @Override
                    public boolean test(JavaMethodCall llamada) {
                        return "transicionarA".equals(llamada.getTarget().getName())
                                && llamada.getTargetOwner().isAssignableTo(Transicionable.class);
                    }
                })
                .because("toda transicion de estado debe pasar por Transiciones para emitir "
                        + "historial_estado (RC-002); las llamadas directas se olvidan de auditar")
                .check(CLASES);
    }
}
