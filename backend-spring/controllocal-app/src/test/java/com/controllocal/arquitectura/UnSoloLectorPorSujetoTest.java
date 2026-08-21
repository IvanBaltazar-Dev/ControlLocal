package com.controllocal.arquitectura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * <b>Un valor gobernado se lee por un solo sitio</b> (D-E4-3, Corte 0C).
 *
 * <h2>Por que este gate existe, con nombre y fecha</h2>
 * D-E4-3 cerro la asimetria del lado del ESCRITOR: si la autoridad enruta al
 * escribir, tiene que enrutar al leer. Y aun asi, en el Corte 0B aparecio un
 * <b>segundo lector</b> dentro de {@code ficha()}: leia {@code atributo_propiedad}
 * a mano y formateaba el valor el mismo. Nadie lo escribio para hacer algo
 * distinto -- simplemente dejo de enterarse de los cambios del primero, y por eso
 * un IMPORTE llegaba a la ficha sin su moneda y un multivalor no llegaba en
 * absoluto.
 *
 * <p>Eso no se arregla arreglando aquel sitio: el siguiente cambio de forma
 * rompe el siguiente lector. Lo que lo cierra es <b>que no pueda haber un
 * segundo</b>.
 *
 * <h2>Que permite y que no</h2>
 * <pre>
 *   leer   -> solo LectorPorAutoridad             (uno por sujeto, y el mismo)
 *   escribir/borrar -> el enrutador del sujeto + el caso de uso que lo guarda
 * </pre>
 *
 * <p>La lista de permitidos es corta a proposito. Anadir una clase aqui es una
 * decision consciente, y es exactamente la conversacion que este gate quiere
 * forzar: <i>¿de verdad necesitas leer la tabla, o necesitas el valor?</i>
 */
class UnSoloLectorPorSujetoTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.controllocal");

    /**
     * Quien puede tocar los repositorios de valores gobernados.
     *
     * <p>Los dos enrutadores porque convierten y validan; los dos casos de uso
     * porque guardan lo que el enrutador les devuelve; y el lector porque es el
     * lector. Nadie mas.
     */
    private static final List<String> CON_PERMISO = List.of(
            "com.controllocal.service.soporte.LectorPorAutoridad",
            "com.controllocal.service.soporte.AtributosGobernados",
            "com.controllocal.service.soporte.AtributosDeEncargo",
            "com.controllocal.service.impl.PropiedadUniversalServiceImpl");

    private static final List<String> REPOSITORIOS_DE_VALOR = List.of(
            "com.controllocal.persistence.repositorio.AtributoPropiedadRepository",
            "com.controllocal.persistence.repositorio.AtributoEncargoRepository",
            "com.controllocal.persistence.repositorio.ValorMultipleAtributoRepository",
            "com.controllocal.persistence.repositorio.ValorMultipleEncargoRepository");

    @Test
    @DisplayName("nadie mas lee ni escribe las tablas de valores gobernados")
    void soloLosEnrutadoresTocanLosValoresGobernados() {
        noClasses()
                // El `(\$.*)?` cubre las clases internas: una anonima dentro de
                // un permitido se llama `Foo$1` y sin esto no coincidiria, asi
                // que el gate fallaria por donde no debe.
                .that().haveNameNotMatching(CON_PERMISO.stream()
                        .map(nombre -> java.util.regex.Pattern.quote(nombre) + "(\\$.*)?")
                        .reduce((a, b) -> a + "|" + b).orElseThrow())
                .should().dependOnClassesThat(new DescribedPredicate<>(
                        "son repositorios de valores gobernados") {
                    @Override
                    public boolean test(com.tngtech.archunit.core.domain.JavaClass clase) {
                        return REPOSITORIOS_DE_VALOR.contains(clase.getFullName());
                    }
                })
                .because("""
                        un segundo lector de la misma tabla no falla: DIVERGE. \
                        El de la ficha, en el Corte 0B, dejaba fuera la moneda de un \
                        importe y todos los multivalores, y llevaba asi desde que se \
                        anadieron. Quien necesite el valor pide el valor a \
                        LectorPorAutoridad; quien necesite escribirlo pasa por el \
                        enrutador de SU sujeto""")
                .check(CLASES);
    }

    /**
     * <b>Y el sujeto no se decide con un {@code if}.</b>
     *
     * <p>Cada enrutador conoce un solo sujeto. Que {@code AtributosGobernados}
     * empezara a hablar de encargos --o al reves-- devolveria la bifurcacion
     * dentro de cada metodo, y bastaria olvidarla en uno --el de borrar, el de
     * contar faltantes-- para que un dato se escribiera en un sujeto y se
     * leyera del otro.
     */
    @Test
    @DisplayName("el enrutador de la propiedad no sabe escribir en un encargo, ni al reves")
    void cadaEnrutadorConoceUnSoloSujeto() {
        noClasses()
                .that().haveFullyQualifiedName(
                        "com.controllocal.service.soporte.AtributosGobernados")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "com.controllocal.domain.comercial.AtributoEncargo")
                .because("el enrutador de la PROPIEDAD no escribe condiciones de encargo")
                .check(CLASES);

        noClasses()
                .that().haveFullyQualifiedName(
                        "com.controllocal.service.soporte.AtributosDeEncargo")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "com.controllocal.domain.inmueble.AtributoPropiedad")
                .because("el enrutador del ENCARGO no escribe hechos del inmueble")
                .check(CLASES);
    }

    /**
     * <b>El motor de captura no elige tabla.</b>
     *
     * <p>Pregunta lo que el catalogo declara y entrega lo respondido al caso de
     * uso. Que supiera de {@code atributo_encargo} seria la matriz «clave ->
     * tabla» viviendo en el guion, que es la version conversacional del mismo
     * defecto que D-A-1 prohibe en Angular.
     */
    @Test
    @DisplayName("el motor de captura no conoce ninguna tabla de valores")
    void elMotorDeCapturaNoConoceLasTablas() {
        noClasses()
                .that().resideInAPackage("com.controllocal.service.captura..")
                .should().callMethodWhere(new DescribedPredicate<JavaMethodCall>(
                        "invocan un repositorio de valores gobernados") {
                    @Override
                    public boolean test(JavaMethodCall llamada) {
                        return REPOSITORIOS_DE_VALOR.contains(
                                llamada.getTargetOwner().getFullName());
                    }
                })
                .because("el motor deriva preguntas del catalogo; donde vive cada respuesta "
                        + "lo decide el enrutador del sujeto, no el guion")
                .check(CLASES);
    }
}
