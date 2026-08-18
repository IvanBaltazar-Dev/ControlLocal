package com.controllocal.arquitectura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El quinto gate: BROX tiene que poder funcionar sin KAIROS.</b>
 *
 * <h2>Que decision protege</h2>
 * KAIROS es un proyecto aparte —{@code kairos-service/}— con su propio ciclo de
 * despliegue. Puede reiniciarse, cambiar de modelo de IA o caerse entero, y
 * BROX tiene que seguir registrando propiedades, agendando visitas, negociando
 * y cerrando operaciones.
 *
 * <p>Eso no se sostiene con buena voluntad. Un import "solo para esta consulta"
 * basta para que dentro de seis meses BROX no arranque sin KAIROS, y para
 * entonces nadie recordara cual fue la linea que lo ato. Por eso es un gate y
 * no una nota en un documento.
 *
 * <h2>Las tres cosas que vigila</h2>
 * <ol>
 *   <li><b>Ninguna clase de BROX depende de KAIROS.</b> Ni por paquete, ni por
 *       nombre de clase.</li>
 *   <li><b>Ningun proveedor de IA aparece en el dominio ni en el servicio.</b>
 *       Cambiar de modelo no puede obligar a migrar el dominio inmobiliario.</li>
 *   <li><b>El reactor de BROX no incluye a KAIROS como modulo.</b> Si algun dia
 *       apareciera ahi, se desplegarian juntos otra vez y la separacion seria
 *       nominal.</li>
 * </ol>
 *
 * <h2>Lo que este gate NO dice</h2>
 * No dice que KAIROS no exista ni que no importe. Dice que la dependencia va en
 * <b>un solo sentido</b>: KAIROS conoce las capacidades publicas de BROX; BROX
 * no conoce nada de KAIROS. Las piezas reusables que salieron de este trabajo
 * —procedencia, trazabilidad, motor de captura, capacidades— se quedan en BROX
 * precisamente porque sirven a cualquier canal, no solo a una conversacion.
 */
class FronteraKairosTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.controllocal");

    /**
     * Nombres de proveedores y librerias de IA. Si alguno aparece dentro del
     * dominio o del servicio, cambiar de modelo dejaria de ser un cambio de
     * configuracion de KAIROS y pasaria a ser una migracion de BROX.
     */
    private static final List<String> PROVEEDORES_DE_IA = List.of(
            "openai", "anthropic", "langchain", "langgraph", "ollama", "huggingface",
            "whisper", "cohere", "mistral", "moonshot", "deepseek", "gemini");

    @Test
    void ningunaClaseDeBroxDependeDeKairos() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                        "..kairos..", "com.kairos..")
                .because("KAIROS es un proyecto aparte y BROX tiene que seguir operativo con "
                        + "KAIROS apagado. Si BROX necesita algo que hoy solo existe en KAIROS, "
                        + "ese algo es una capacidad de BROX y va a BROX -- no un import")
                .check(CLASES);
    }

    /**
     * El nombre tambien cuenta. Una clase {@code AsistenteKairos} dentro de
     * {@code service} no violaria la regla de paquetes y seria exactamente lo
     * que esta frontera existe para impedir: KAIROS creciendo dentro de BROX.
     */
    @Test
    void ningunaClaseDeBroxSeLlamaComoElAsistente() {
        noClasses().should().haveSimpleNameContaining("Kairos")
                .because("una pieza que existe porque interpreta conversacion, orquesta "
                        + "herramientas o habla con WhatsApp pertenece a kairos-service")
                .check(CLASES);
    }

    @Test
    void ningunProveedorDeIaEntraEnElDominioNiEnElServicio() {
        for (String proveedor : PROVEEDORES_DE_IA) {
            noClasses().that().resideInAnyPackage(
                            "com.controllocal.domain..", "com.controllocal.service..")
                    .should().dependOnClassesThat().resideInAnyPackage(".." + proveedor + "..")
                    .because("cambiar de modelo de IA no puede obligar a migrar el dominio "
                            + "inmobiliario: los proveedores viven en KAIROS")
                    .check(CLASES);
        }
    }

    /**
     * El reactor de BROX no puede declarar a KAIROS como modulo: compartirian
     * build y despliegue, que es justo lo que la separacion vino a evitar.
     */
    @Test
    void elReactorDeBroxNoIncluyeAKairos() throws IOException {
        String reactor = Files.readString(Path.of("..", "pom.xml"), StandardCharsets.UTF_8);
        assertFalse(reactor.contains("kairos"),
                "backend-spring/pom.xml declara a KAIROS como modulo: se desplegarian juntos y "
                        + "la separacion seria solo de carpetas.");
    }

    /**
     * Y KAIROS, por su lado, no puede depender de los jar de BROX ni de su base
     * de datos. Se comprueba desde aqui —y no desde KAIROS— porque este es el
     * build que rompe el gate de cierre; si KAIROS no esta en el disco, la
     * prueba no aplica y no se inventa un fallo.
     */
    @Test
    void kairosNoDependeDeLosJarDeBroxNiDeSuBaseDeDatos() throws IOException {
        Path pom = Path.of("..", "..", "kairos-service", "pom.xml");
        if (!Files.isRegularFile(pom)) {
            // KAIROS todavia no existe en este arbol, o se borro para comprobar
            // que BROX funciona sin el. Las dos son situaciones legitimas.
            return;
        }
        String declarado = Files.readString(pom, StandardCharsets.UTF_8);
        assertFalse(declarado.contains("<artifactId>controllocal-"),
                "kairos-service depende de un jar de BROX: entonces no se puede desplegar "
                        + "aparte, y un cambio en BROX obligaria a recompilarlo.");
        assertFalse(declarado.contains("postgresql"),
                "kairos-service trae el driver de PostgreSQL. La relacion correcta es "
                        + "KAIROS -> API de BROX -> PostgreSQL; con el driver dentro, el atajo "
                        + "de leer una tabla 'solo para esta consulta' esta a un import.");

        try (Stream<Path> fuentes = Files.walk(pom.getParent())) {
            List<String> conImportDeBrox = fuentes
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .filter(FronteraKairosTest::importaBrox)
                    .map(Path::toString)
                    .toList();
            assertEquals(List.of(), conImportDeBrox,
                    "una clase de KAIROS importa com.controllocal: la frontera es el contrato "
                            + "HTTP, no las clases.");
        }
    }

    /** El gate de arriba no vale nada si el proyecto de KAIROS no esta donde dice. */
    @Test
    void kairosViveFueraDelReactorDeBrox() {
        Path dentro = Path.of("..", "kairos-service");
        assertTrue(!Files.exists(dentro),
                "kairos-service aparecio dentro de backend-spring/. Su sitio es la raiz del "
                        + "repositorio, al lado de backend-spring y frontend-angular.");
    }

    private static boolean importaBrox(Path fuente) {
        try {
            return Files.readString(fuente, StandardCharsets.UTF_8)
                    .contains("import com.controllocal");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
