package com.controllocal.app.plataforma;

import com.controllocal.service.plataforma.AcreditacionesTitularPlataforma;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Sólo archivos TempDir y decisiones TEST; no cuentas, fondos ni autoridad productiva. */
class AcreditacionesTitularArchivoTest {
    private static final String PREFIJO="controllocal.plataforma.acreditaciones-archivo.";
    private static final UUID ID=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTRA=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final ObjectMapper JSON=new ObjectMapper();
    @TempDir Path dir;

    @Test
    void offPorDefectoYExplicitoNoInstancianFuente() {
        var runner=new ApplicationContextRunner().withUserConfiguration(AcreditacionesTitularArchivo.class);
        runner.run(c->{assertNull(c.getStartupFailure());assertTrue(c.getBeansOfType(AcreditacionesTitularPlataforma.class).isEmpty());});
        runner.withPropertyValues(PREFIJO+"habilitada=false").run(c->{
            assertNull(c.getStartupFailure());assertTrue(c.getBeansOfType(AcreditacionesTitularPlataforma.class).isEmpty());
        });
    }

    @Test
    void habilitarSinRutaNoLeeEnArranqueYSoloFallaAlConsultar() {
        new ApplicationContextRunner().withUserConfiguration(AcreditacionesTitularArchivo.class)
                .withPropertyValues(PREFIJO+"habilitada=true").run(c->{
                    assertNull(c.getStartupFailure());
                    var fuente=c.getBean(AcreditacionesTitularPlataforma.class);
                    falloSeguro(()->fuente.acreditar(ID));
                });
    }

    @Test
    void preservaLaTuplaExactaYDestinatarioNuloSinJuzgarLaAutoridad() throws IOException {
        var p=archivo(documento(decision()));
        var fuente=new AcreditacionesTitularArchivo(p.toString());
        var d=fuente.acreditar(ID).orElseThrow();
        assertEquals(new AcreditacionesTitularPlataforma.Decision(ID,77L,88L,"ACTUACION_TEST",
                "HUELLA_TEST_NO_OPERATIVA","EVIDENCIA_TEST_NO_OPERATIVA",
                Instant.parse("2026-11-01T12:34:56.123456789Z")),d);
        var sinDestino=decision();sinDestino.putNull("destinatario");
        // Fecha futura y texto no canónico no conceden autoridad: el Service conserva ese cotejo.
        sinDestino.put("acreditadaEn","2099-01-01T00:00:00Z");
        Files.writeString(p,documento(sinDestino),StandardCharsets.UTF_8);
        var futuro=fuente.acreditar(ID).orElseThrow();
        assertNull(futuro.destinatario());assertEquals(Instant.parse("2099-01-01T00:00:00Z"),futuro.acreditadaEn());
        assertEquals("HUELLA_TEST_NO_OPERATIVA",futuro.huellaContenido());
        assertTrue(fuente.acreditar(OTRA).isEmpty());
    }

    @Test
    void releeCambiosYRetiroDeReferenciaSinCacheNiMutacionesDeConcesion() throws IOException {
        var p=archivo(documento(decision()));
        var fuente=new AcreditacionesTitularArchivo(p.toString());
        assertTrue(fuente.acreditar(ID).isPresent());
        var cambiada=decision();cambiada.put("evidencia","EVIDENCIA_TEST_CAMBIADA");
        Files.writeString(p,documento(cambiada),StandardCharsets.UTF_8);
        assertEquals("EVIDENCIA_TEST_CAMBIADA",fuente.acreditar(ID).orElseThrow().evidencia());
        Files.writeString(p,"{\"version\":1,\"decisiones\":[]}",StandardCharsets.UTF_8);
        assertTrue(fuente.acreditar(ID).isEmpty());
        // Esta fuente no revoca concesiones R25 persistidas ni ejecuta comandos de administración.
        Files.delete(p);
        falloSeguro(()->fuente.acreditar(ID));
    }

    @ParameterizedTest
    @ValueSource(strings={"", "null", "[]", "{\"version\":1,\"decisiones\":null}",
            "{\"version\":null,\"decisiones\":[]}", "{\"version\":\"1\",\"decisiones\":[]}",
            "{\"version\":1.0,\"decisiones\":[]}", "{\"version\":2,\"decisiones\":[]}",
            "{\"decisiones\":[]}", "{\"version\":1,\"decisiones\":[],\"extra\":1}",
            "{\"version\":1,\"version\":1,\"decisiones\":[]}", "{\"version\":1,\"decisiones\":[]} {}"})
    void documentoNoValidoEsFalloTecnicoNoAusencia(String contenido) throws IOException {
        var fuente=new AcreditacionesTitularArchivo(archivo(contenido).toString());
        falloSeguro(()->fuente.acreditar(ID));
    }

    @ParameterizedTest
    @ValueSource(strings={"referencia","otorgante","destinatario","actuacion","huellaContenido","evidencia","acreditadaEn"})
    void faltaCualquierCampoIncluidoNullableEsErrorDeDocumento(String campo) throws IOException {
        var d=decision();d.remove(campo);
        var fuente=new AcreditacionesTitularArchivo(archivo(documento(d)).toString());
        falloSeguro(()->fuente.acreditar(ID));
    }

    @ParameterizedTest
    @ValueSource(strings={"referencia","otorgante","actuacion","huellaContenido","evidencia","acreditadaEn"})
    void nullNoSeConvierteEnCeroNiEnTexto(String campo) throws IOException {
        var d=decision();d.putNull(campo);
        var fuente=new AcreditacionesTitularArchivo(archivo(documento(d)).toString());
        falloSeguro(()->fuente.acreditar(ID));
    }

    @Test
    void rechazaDesconocidosDuplicadosYTuplasAmbiguasAunqueNoSeanLaBuscada() throws IOException {
        var desconocido=decision();desconocido.put("extra","NO_PERMITIDO_TEST");
        var p=archivo(documento(desconocido));var fuente=new AcreditacionesTitularArchivo(p.toString());
        falloSeguro(()->fuente.acreditar(OTRA));
        var duplicado=decision().toString().replace("\"otorgante\":77","\"otorgante\":77,\"otorgante\":77");
        Files.writeString(p,"{\"version\":1,\"decisiones\":["+duplicado+"]}",StandardCharsets.UTF_8);
        falloSeguro(()->fuente.acreditar(ID));
        Files.writeString(p,documento(decision(),decision()),StandardCharsets.UTF_8);
        falloSeguro(()->fuente.acreditar(ID));
    }

    @ParameterizedTest
    @ValueSource(strings={"\"77\"","77.5","9223372036854775808","true"})
    void identificadoresExigenEnteroLongSinCoercion(String valor) throws IOException {
        var contenido=documento(decision()).replace("\"otorgante\":77","\"otorgante\":"+valor);
        var fuente=new AcreditacionesTitularArchivo(archivo(contenido).toString());
        falloSeguro(()->fuente.acreditar(ID));
    }

    @Test
    void rutaRelativaUrlUncDirectorioYArchivoAusenteFallanSinDetalles() throws IOException {
        for(var ruta:new String[]{"relativo_TEST.json","https://fuente.invalid/TEST.json","\\\\servidor_TEST\\fuente.json",
                dir.toString(),dir.resolve("NO_EXISTE_TEST.json").toString()}) {
            falloSeguro(()->new AcreditacionesTitularArchivo(ruta).acreditar(ID));
        }
    }

    @Test
    void rechazaEnlaceFinalYDirectorioEnlazadoCuandoSistemaPermiteCrearlos() throws IOException {
        var p=archivo(documento(decision()));var enlace=dir.resolve("ENLACE_TEST.json");
        try {Files.createSymbolicLink(enlace,p);}catch(UnsupportedOperationException|IOException|SecurityException e){
            assumeTrue(false,"El sistema no permite crear enlaces simbólicos en este TempDir");return;
        }
        falloSeguro(()->new AcreditacionesTitularArchivo(enlace.toString()).acreditar(ID));
        var carpeta=dir.resolve("ENLACE_DIRECTORIO_TEST");Files.createSymbolicLink(carpeta,dir);
        falloSeguro(()->new AcreditacionesTitularArchivo(carpeta.resolve(p.getFileName()).toString()).acreditar(ID));
    }

    @Test
    void archivoExcesivoONestingExcesivoFallaSinDevolverAcreditacionAnterior() throws IOException {
        var p=archivo(documento(decision()));var fuente=new AcreditacionesTitularArchivo(p.toString());
        assertTrue(fuente.acreditar(ID).isPresent());
        String valido=documento(decision());
        int relleno=AcreditacionesTitularArchivo.MAX_BYTES-valido.getBytes(StandardCharsets.UTF_8).length;
        Files.writeString(p,valido+" ".repeat(relleno),StandardCharsets.UTF_8);
        assertEquals(AcreditacionesTitularArchivo.MAX_BYTES,Files.size(p));
        assertTrue(fuente.acreditar(ID).isPresent(),"Un documento válido justo en la cota técnica debe poder leerse.");
        Files.writeString(p,valido+" ".repeat(relleno+1),StandardCharsets.UTF_8);
        assertEquals(AcreditacionesTitularArchivo.MAX_BYTES+1,Files.size(p));
        falloSeguro(()->fuente.acreditar(ID));
        // Negativo de defensa del parser; no afirma que este árbol ajeno al esquema sea válido a profundidad16.
        Files.writeString(p,"[".repeat(20)+"0"+"]".repeat(20),StandardCharsets.UTF_8);
        falloSeguro(()->fuente.acreditar(ID));
    }

    private Path archivo(String contenido) throws IOException {
        return Files.writeString(dir.resolve("FUENTE_TEST_NO_OPERATIVA.json"),contenido,StandardCharsets.UTF_8);
    }
    private static ObjectNode decision() {
        var d=JSON.createObjectNode();d.put("referencia",ID.toString());d.put("otorgante",77L);d.put("destinatario",88L);
        d.put("actuacion","ACTUACION_TEST");d.put("huellaContenido","HUELLA_TEST_NO_OPERATIVA");
        d.put("evidencia","EVIDENCIA_TEST_NO_OPERATIVA");d.put("acreditadaEn","2026-11-01T12:34:56.123456789Z");return d;
    }
    private static String documento(ObjectNode... decisiones) {
        var r=JSON.createObjectNode();r.put("version",1);var lista=r.putArray("decisiones");
        for(var d:decisiones)lista.add(d);return r.toString();
    }
    private static void falloSeguro(org.junit.jupiter.api.function.Executable ejecutar) {
        var e=assertThrows(IllegalStateException.class,ejecutar);
        assertEquals("No se pudo verificar la fuente administrativa de acreditaciones.",e.getMessage());
        assertNull(e.getCause());assertEquals(0,e.getSuppressed().length);
    }
}
