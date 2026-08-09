package com.controllocal.app.almacen;

import com.controllocal.web.almacen.AlmacenDisco;
import com.controllocal.web.almacen.AlmacenDocumentos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La migracion, probada con un disco de verdad como origen y un almacen en
 * memoria como destino.
 *
 * <p>Lo que se vigila no es "copia archivos" —eso es lo facil— sino las cuatro
 * decisiones que hacen que se pueda confiar en el informe para apagar el
 * origen: que <b>no invente</b> que copio lo que no pudo verificar, que
 * distinga una <b>referencia rota</b> de un <b>huerfano</b>, que <b>no</b>
 * migre los huerfanos y que repetirla sea <b>gratis y seguro</b>.
 */
class MigracionAlmacenTest {

    @TempDir
    Path raizOrigen;

    /** Destino en memoria: el comportamiento de S3 ya lo cubre AlmacenS3Test. */
    private static final class AlmacenEnMemoria implements AlmacenDocumentos {
        final Map<String, byte[]> objetos = new LinkedHashMap<>();
        /** Claves que deben fallar al escribir, para probar el camino del fallo. */
        final Set<String> rompeAlEscribir = new LinkedHashSet<>();
        /** Claves que se escriben CORRUPTAS: el caso que una migracion ingenua no ve. */
        final Set<String> corrompeAlEscribir = new LinkedHashSet<>();

        @Override public String proveedor() {
            return "MEMORIA";
        }

        @Override public ArchivoGuardado guardar(String c, String n, byte[] b, String t) {
            throw new UnsupportedOperationException("la migracion no acuña claves nuevas");
        }

        @Override public void guardarEnClave(String clave, byte[] contenido, String contentType) {
            if (rompeAlEscribir.contains(clave)) {
                throw new IllegalStateException("el bucket dijo que no");
            }
            objetos.put(clave, corrompeAlEscribir.contains(clave)
                    ? "otra cosa".getBytes(StandardCharsets.UTF_8)
                    : contenido);
        }

        @Override public Optional<ArchivoDescargado> abrir(String clave) {
            return Optional.ofNullable(objetos.get(clave)).map(bytes ->
                    new ArchivoDescargado(bytes, AlmacenDocumentos.nombreDe(clave),
                            AlmacenDocumentos.contentTypeDe(clave)));
        }

        @Override public void eliminar(String clave) {
            objetos.remove(clave);
        }

        @Override public Set<String> listarClaves() {
            return new LinkedHashSet<>(objetos.keySet());
        }
    }

    private final AlmacenEnMemoria destino = new AlmacenEnMemoria();
    private final InventarioDeClaves inventario = mock(InventarioDeClaves.class);

    private AlmacenDisco origen() {
        return new AlmacenDisco(raizOrigen.toString());
    }

    /** Deja un binario en el disco de origen y devuelve su clave. */
    private String enDisco(String clave, String contenido) {
        origen().guardarEnClave(clave, contenido.getBytes(StandardCharsets.UTF_8), "application/pdf");
        return clave;
    }

    private void referencia(String... claves) {
        List<InventarioDeClaves.Referencia> refs = java.util.Arrays.stream(claves)
                .map(c -> new InventarioDeClaves.Referencia(c, "documento del expediente"))
                .toList();
        when(inventario.referencias()).thenReturn(refs);
        when(inventario.cuantasFuentes()).thenReturn(3);
    }

    private MigracionAlmacen migracion() {
        return new MigracionAlmacen(inventario, destino, raizOrigen.toString(), "migrar");
    }

    @Test
    @DisplayName("copia lo referenciado y lo verifica releyendo del destino")
    void copiaYVerifica() {
        enDisco("tenant/1/documentos/abc-dni.pdf", "el dni");
        referencia("tenant/1/documentos/abc-dni.pdf");

        var informe = migracion().ejecutar(origen(), true);

        assertEquals(1, informe.copiados());
        assertTrue(informe.sinAverias());
        assertArrayEqualsTexto("el dni", destino.objetos.get("tenant/1/documentos/abc-dni.pdf"));
    }

    @Test
    @DisplayName("una fila que apunta a un binario ausente es una referencia ROTA, no un fallo de copia")
    void distingueLaReferenciaRota() {
        referencia("tenant/1/documentos/se-perdio.pdf");

        var informe = migracion().ejecutar(origen(), true);

        assertEquals(1, informe.referenciasRotas().size());
        assertEquals(0, informe.copiados());
        assertTrue(informe.fallidos().isEmpty(), "no es un fallo de copia: no habia que copiar nada");
        assertFalse(informe.sinAverias(), "una referencia rota impide dar la migracion por buena");
        assertTrue(informe.referenciasRotas().get(0).contains("documento del expediente"),
                "el informe dice DE DONDE sale la fila rota: " + informe.referenciasRotas());
    }

    @Test
    @DisplayName("un binario sin fila es huerfano y NO se migra: seria llevarse la basura a la casa nueva")
    void noMigraLosHuerfanos() {
        enDisco("tenant/1/documentos/nadie-me-referencia.pdf", "sobra");
        referencia(); // ninguna referencia

        var informe = migracion().ejecutar(origen(), true);

        assertEquals(1, informe.huerfanos().size());
        assertEquals(0, informe.copiados());
        assertTrue(destino.objetos.isEmpty(), "el huerfano NO debe acabar en el destino");
        assertTrue(informe.sinAverias(), "un huerfano no es una averia, es limpieza pendiente");
    }

    @Test
    @DisplayName("si el destino corrompe el contenido, cuenta como FALLO y no como copiado")
    void unaCopiaCorruptaNoSeCuentaComoCopiada() {
        String clave = enDisco("tenant/1/documentos/abc-contrato.pdf", "el contrato bueno");
        destino.corrompeAlEscribir.add(clave);
        referencia(clave);

        var informe = migracion().ejecutar(origen(), true);

        assertEquals(0, informe.copiados(), "no se puede contar como copiado lo que no se verifico");
        assertEquals(1, informe.fallidos().size());
        assertTrue(informe.fallidos().get(0).contains("NO coincide"), "" + informe.fallidos());
        assertFalse(informe.sinAverias());
    }

    @Test
    @DisplayName("un fallo de escritura no aborta el resto: se informa y se sigue")
    void unFalloNoDetieneLaMigracion() {
        String bueno = enDisco("tenant/1/documentos/aaa-uno.pdf", "uno");
        String malo = enDisco("tenant/1/documentos/bbb-dos.pdf", "dos");
        destino.rompeAlEscribir.add(malo);
        referencia(bueno, malo);

        var informe = migracion().ejecutar(origen(), true);

        // Parar en el primer fallo obligaria a descubrir las averias de una en
        // una, con una pasada completa por cada una.
        assertEquals(1, informe.copiados());
        assertEquals(1, informe.fallidos().size());
    }

    @Test
    @DisplayName("repetirla es gratis y segura: lo que ya esta no se vuelve a copiar")
    void esIdempotente() {
        String clave = enDisco("tenant/1/documentos/abc-dni.pdf", "el dni");
        referencia(clave);

        migracion().ejecutar(origen(), true);
        var segunda = migracion().ejecutar(origen(), true);

        assertEquals(0, segunda.copiados(), "la segunda pasada no copia nada");
        assertEquals(1, segunda.yaEnDestino());
        assertTrue(segunda.sinAverias());
    }

    @Test
    @DisplayName("conciliar NO escribe nada: sirve para mirar antes de tocar")
    void conciliarNoEscribe() {
        String clave = enDisco("tenant/1/documentos/abc-dni.pdf", "el dni");
        referencia(clave);

        var informe = migracion().ejecutar(origen(), false);

        assertEquals(0, informe.copiados());
        assertTrue(destino.objetos.isEmpty(), "conciliar es de solo lectura");
        assertEquals(1, informe.referenciadas());
    }

    @Test
    @DisplayName("la migracion NO borra el origen: el plan de vuelta atras son los datos viejos")
    void noBorraElOrigen() {
        String clave = enDisco("tenant/1/documentos/abc-dni.pdf", "el dni");
        referencia(clave);

        migracion().ejecutar(origen(), true);

        assertTrue(origen().abrir(clave).isPresent(), "el binario tiene que seguir en el disco");
    }

    @Test
    @DisplayName("se niega a migrar hacia DISCO: seria copiar los archivos sobre si mismos")
    void seNiegaSiElDestinoEsElOrigen() {
        var haciaDisco = new MigracionAlmacen(inventario, origen(), raizOrigen.toString(), "migrar");

        var error = assertThrows(IllegalStateException.class,
                () -> haciaDisco.run(null));

        assertTrue(error.getMessage().contains("DISCO"), error.getMessage());
    }

    @Test
    void unModoInventadoSeRechaza() {
        var conModoRaro = new MigracionAlmacen(inventario, destino, raizOrigen.toString(), "borrar");

        assertThrows(IllegalArgumentException.class, () -> conModoRaro.run(null));
    }

    @Test
    @DisplayName("el informe dice cuantas columnas miro: si alguien anade una y no la registra, se nota")
    void elInformeDeclaraCuantasFuentesMiro() {
        referencia();

        assertTrue(MigracionAlmacen.informeLegible(migracion().ejecutar(origen(), false))
                .contains("Columnas de clave miradas: 3"));
    }

    private static void assertArrayEqualsTexto(String esperado, byte[] real) {
        assertEquals(esperado, new String(real, StandardCharsets.UTF_8));
    }
}
