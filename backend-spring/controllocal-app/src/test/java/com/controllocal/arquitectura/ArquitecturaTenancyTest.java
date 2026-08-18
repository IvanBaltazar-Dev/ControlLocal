package com.controllocal.arquitectura;

import com.controllocal.domain.comun.EntidadDeOrganizacion;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda el criterio #2 del gate de V6 ("ninguna entidad privada acepta
 * organizacion_id = NULL") en el unico sitio donde se puede vigilar sin una
 * base de datos: la clasificacion de las entidades.
 *
 * <p>Una entidad nueva que se olvide del tenant ROMPE el build. Para dejarla
 * global hay que anotarlo aqui a proposito, que es exactamente la decision
 * que el §1 de {@code docs/ai/plan-migracion-v6-tenancy.md} exige documentar.
 */
class ArquitecturaTenancyTest {

    /**
     * Catalogos compartidos por todas las organizaciones. Anadir algo a esta
     * lista es declarar que sus filas son las MISMAS para toda corredora.
     */
    private static final Set<String> GLOBALES = Set.of(
            // Geografia: Miraflores es Miraflores para cualquier corredora.
            "com.controllocal.domain.inmueble.Distrito",
            // La organizacion es la raiz del tenant, no algo dentro de el.
            "com.controllocal.domain.organizacion.Organizacion",
            // Vocabulario y documentos de la plataforma (D-25).
            "com.controllocal.domain.consentimiento.FinalidadTratamiento",
            "com.controllocal.domain.consentimiento.AvisoPrivacidadVersion",
            "com.controllocal.domain.consentimiento.EvidenciaAutorizacion",
            // Checklist de documentos del alquiler (V8): el mismo para toda
            // corredora, y sus ids 1..8 son parte del cable congelado.
            "com.controllocal.domain.comercial.TipoDocumentoRequerido",
            // Contador del bloqueo (D-S0-21, V30): un intento de acceso es
            // PRE-TENANT — se cuenta antes de saber quien pregunta. Contar
            // solo los intentos contra cuentas existentes convertiria el
            // bloqueo en un oraculo del padron de usuarios. Lleva
            // organizacion_id como dato informativo y nullable.
            "com.controllocal.domain.seguridad.IntentoAcceso",
            // Catalogo HIBRIDO de caracteristicas del inmueble (D-E4-1 M2, V48).
            // Sus filas del sistema (organizacion_id NULL) son las MISMAS para
            // toda corredora y ninguna puede borrarlas ni redefinir su tipo:
            // son lo que permite que dos propiedades se puedan comparar y que
            // el matcher exista. Encima de ellas, cada organizacion anade las
            // suyas, y esas SI llevan organizacion_id. El discriminador es
            // anulable a proposito, como en IntentoAcceso, y por eso la entidad
            // no puede heredar de EntidadDeOrganizacion.
            //
            // Ojo: el VALOR de un atributo (AtributoPropiedad) no esta aqui.
            // Ese es privado de una organizacion y lleva su discriminador
            // NOT NULL como cualquier otra fila de negocio.
            "com.controllocal.domain.inmueble.CatalogoAtributo");

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.controllocal");

    @Test
    void todaEntidadPrivadaLlevaElDiscriminadorDeTenant() {
        classes()
                .that().areAnnotatedWith(Entity.class)
                .and(new DescribedPredicate<JavaClass>("no son catalogos globales declarados") {
                    @Override
                    public boolean test(JavaClass entidad) {
                        return !GLOBALES.contains(entidad.getFullName());
                    }
                })
                .should().beAssignableTo(EntidadDeOrganizacion.class)
                .because("toda fila privada pertenece a una organizacion (D-16/D-24): sin el "
                        + "discriminador, una consulta con un bug puede cruzar corredoras")
                .check(CLASES);
    }

    @Test
    void laListaDeGlobalesNoSeQuedaObsoleta() {
        List<String> entidades = CLASES.stream()
                .filter(clase -> clase.isAnnotatedWith(Entity.class))
                .map(JavaClass::getFullName)
                .toList();

        // Si una "global" se renombra o se borra, la excepcion deja de ser una
        // decision y pasa a ser basura que tapa entidades nuevas sin tenant.
        GLOBALES.forEach(global -> assertTrue(entidades.contains(global),
                "La entidad global declarada ya no existe: " + global));
    }
}
