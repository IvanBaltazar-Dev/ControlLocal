package com.controllocal.arquitectura;

import com.controllocal.asistencia.brox.ClienteBrox;
import com.controllocal.asistencia.brox.SesionBrox;
import com.controllocal.asistencia.conversacion.Kairos;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

/** P-07: mismo Core, módulos cohesionados y negocio independiente de coordinación/proveedor. */
class FronteraKairosTest {
    private static final JavaClasses CLASES = ImportacionesDeProducto.importador().importPackages("com.controllocal");
    private static final List<String> PROVEEDORES = List.of("openai", "anthropic", "langchain",
            "langgraph", "ollama", "huggingface", "whisper", "cohere", "mistral", "gemini");

    @Test void elNegocioNoDependeDeLaCoordinacionNiDeLaWeb() {
        noClasses().that().resideInAnyPackage("com.controllocal.domain..",
                        "com.controllocal.persistence..", "com.controllocal.service..")
                .should().dependOnClassesThat().resideInAnyPackage("com.controllocal.asistencia..",
                        "com.controllocal.web..")
                .because("la operación determinista no depende de la interfaz ni del intérprete")
                .check(CLASES);
    }

    @Test void laCoordinacionSoloDisponeDelPuertoTipado() {
        noClasses().that().resideInAPackage("com.controllocal.asistencia..")
                .should().dependOnClassesThat().resideInAnyPackage("com.controllocal.domain..",
                        "com.controllocal.persistence..", "com.controllocal.service..", "com.controllocal.web..",
                        "java.sql..", "jakarta.persistence..", "org.springframework.jdbc..",
                        "org.springframework.data..", "java.net..", "org.springframework.web..")
                .because("la asistencia invoca capacidades; no usa SQL, entidades o rutas arbitrarias")
                .check(CLASES);
    }

    @Test void losProveedoresNoEntranEnElDominioNiEnLosServiciosComerciales() {
        for (String proveedor : PROVEEDORES) {
            noClasses().that().resideInAnyPackage("com.controllocal.domain..", "com.controllocal.service..")
                    .should().dependOnClassesThat().resideInAnyPackage(".." + proveedor + "..")
                    .check(CLASES);
        }
    }

    @Test void elReactorIntegraUnaBibliotecaSinServidorOBasePropios() throws IOException {
        String reactor = Files.readString(Path.of("..", "pom.xml"), StandardCharsets.UTF_8);
        assertTrue(reactor.contains("<module>controllocal-asistencia</module>"));
        String modulo = Files.readString(Path.of("..", "controllocal-asistencia", "pom.xml"), StandardCharsets.UTF_8);
        assertFalse(modulo.contains("spring-boot-starter-web"));
        assertFalse(modulo.contains("spring-boot-maven-plugin"));
        assertFalse(modulo.contains("spring-boot-starter-data"));
        assertFalse(modulo.contains("<artifactId>postgresql</artifactId>"));
        assertFalse(modulo.contains("<artifactId>controllocal-service</artifactId>"));
    }

    @Test void seRetiraElServidorYElClienteHttpAnteriores() {
        assertFalse(Files.exists(Path.of("..", "..", "kairos-service")),
                "el servidor anterior debe retirarse al migrar consumidores y pruebas");
        assertTrue(CLASES.stream().noneMatch(c -> c.getName().startsWith("com.kairos.")));
        assertTrue(CLASES.stream().noneMatch(c -> c.getSimpleName().equals("ClienteBroxHttp")));
    }

    @Test void elPuertoNoPuedeFabricarLaConfirmacionHumana() {
        assertTrue(Arrays.stream(ClienteBrox.class.getDeclaredMethods()).noneMatch(m -> {
            String nombre = m.getName().toLowerCase(Locale.ROOT);
            return nombre.contains("confirmar") || nombre.contains("revisar") || nombre.contains("ejecutar");
        }), "la prueba humana y su consumo sólo pertenecen a la superficie segura Core");
        assertTrue(Arrays.stream(Kairos.Turno.class.getRecordComponents()).noneMatch(c ->
                c.getType() == boolean.class || c.getName().toLowerCase(Locale.ROOT).contains("prueba")));
    }

    @Test void elContextoDeLaAsistenciaNoContieneCredenciales() {
        assertTrue(Arrays.stream(SesionBrox.class.getRecordComponents()).noneMatch(c ->
                c.getName().toLowerCase(Locale.ROOT).matches(".*(token|password|contrasena|secreto|factor).*")));
    }
}
