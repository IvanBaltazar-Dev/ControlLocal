package com.controllocal.web.almacen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Proveedor DISCO sobre claves tecnicas preasignadas por el montaje TEST.
 *
 * <p>Conserva la clave exacta, separacion de prefijos, bytes, lectura y retiro.
 * El montaje no representa una nueva actuacion comercial ni acredita cuota B0.
 * Las referencias anteriores sin prefijo siguen siendo legibles.
 */
class AlmacenDiscoTest {

    private static final byte[] CONTENIDO = "contenido binario".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("el proveedor conserva la clave TEST preasignada bajo su tenant")
    void conservaLaClavePreasignadaConTenant(@TempDir Path raiz) {
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());
        String clave = "tenant/7/locales/42/fachada.png";

        almacen.guardarEnClave(clave, CONTENIDO, "image/png");

        assertEquals(java.util.Set.of(clave), almacen.listarClaves());
        var leido = almacen.abrir(clave).orElseThrow();
        assertEquals("fachada.png", leido.nombre());
        assertArrayEquals(CONTENIDO, leido.contenido());
    }

    @Test
    @DisplayName("dos organizaciones no comparten prefijo aunque suban el mismo archivo")
    void dosOrganizacionesNoCompartenPrefijo(@TempDir Path raiz) {
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());

        String unaOrg = "tenant/7/perfiles/foto.png";
        String otraOrg = "tenant/9/perfiles/foto.png";
        almacen.guardarEnClave(unaOrg, CONTENIDO, "image/png");
        almacen.guardarEnClave(otraOrg, CONTENIDO, "image/png");

        assertTrue(unaOrg.startsWith("tenant/7/"), unaOrg);
        assertTrue(otraOrg.startsWith("tenant/9/"), otraOrg);
        // El prefijo es lo que en S3 sostiene la politica de aislamiento; en
        // disco ya separa los arboles.
        assertFalse(unaOrg.startsWith("tenant/9/"));
        assertEquals(java.util.Set.of(unaOrg, otraOrg), almacen.listarClaves());
        assertArrayEquals(CONTENIDO, almacen.abrir(unaOrg).orElseThrow().contenido());
        assertArrayEquals(CONTENIDO, almacen.abrir(otraOrg).orElseThrow().contenido());
    }

    @Test
    @DisplayName("subir, leer y eliminar sobre una clave con tenant")
    void cicloCompletoConTenant(@TempDir Path raiz) {
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());
        String clave = "tenant/7/SOL-0001/contrato.pdf";
        almacen.guardarEnClave(clave, CONTENIDO, "application/pdf");

        Optional<AlmacenDocumentos.ArchivoDescargado> leido = almacen.abrir(clave);
        assertTrue(leido.isPresent());
        assertArrayEquals(CONTENIDO, leido.get().contenido());
        assertEquals("application/pdf", leido.get().contentType());

        almacen.eliminar(clave);
        assertTrue(almacen.abrir(clave).isEmpty(), "tras eliminar no debe quedar el binario");
    }

    @Test
    @DisplayName("una clave ANTIGUA (sin tenant) se sigue leyendo: no hay migracion en este bloque")
    void laClaveAntiguaSeSigueLeyendo(@TempDir Path raiz) throws Exception {
        // Se escribe a mano con el formato viejo, que es lo que hay hoy en el
        // volumen: la fuente de verdad es la clave guardada en PostgreSQL, y
        // este bloque no la reescribe.
        Path antiguo = raiz.resolve("locales/42/abcd1234-fachada.png");
        Files.createDirectories(antiguo.getParent());
        Files.write(antiguo, CONTENIDO);
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());

        Optional<AlmacenDocumentos.ArchivoDescargado> leido =
                almacen.abrir("locales/42/abcd1234-fachada.png");

        assertTrue(leido.isPresent(), "los binarios ya subidos no pueden dejar de verse");
        assertArrayEquals(CONTENIDO, leido.get().contenido());
    }

    @Test
    @DisplayName("un archivo inexistente responde vacio, no explota")
    void archivoInexistenteRespondeVacio(@TempDir Path raiz) {
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());

        assertTrue(almacen.abrir("tenant/7/locales/42/no-existe.png").isEmpty());
        // Eliminar lo que ya no esta es best-effort: el registro pudo borrarse
        // antes y el huerfano no puede tumbar la operacion.
        almacen.eliminar("tenant/7/locales/42/no-existe.png");
    }

    @Test
    @DisplayName("una clave que intenta salir de la raiz no resuelve a nada")
    void noSePuedeEscaparDeLaRaiz(@TempDir Path raiz) {
        AlmacenDisco almacen = new AlmacenDisco(raiz.toString());

        assertTrue(almacen.abrir("../../etc/passwd").isEmpty());
        assertTrue(almacen.abrir("tenant/7/../../../secreto.txt").isEmpty());
    }

    @Test
    @DisplayName("el proveedor sigue siendo DISCO: S3 llega en el Bloque 8")
    void elProveedorSigueSiendoDisco(@TempDir Path raiz) {
        assertEquals("DISCO", new AlmacenDisco(raiz.toString()).proveedor());
    }

    @Test void marcadorEsDurableEntreInstanciasYSinCache(@TempDir Path raiz)throws Exception{
        var a=new AlmacenDisco(raiz.toString());String id=a.identidadTecnica();assertEquals(id,new AlmacenDisco(raiz.toString()).identidadTecnica());
        Files.writeString(raiz.resolve(AlmacenDisco.MARCADOR_IDENTIDAD),java.util.UUID.randomUUID().toString());assertNotEquals(id,a.identidadTecnica());
    }
    @Test void almacenesIndependientesNoCompartenIdentidad(@TempDir Path raiz){assertNotEquals(new AlmacenDisco(raiz.resolve("uno").toString()).identidadTecnica(),new AlmacenDisco(raiz.resolve("dos").toString()).identidadTecnica());}
    @Test void marcadorCorruptoDeniegaYSigueIntacto(@TempDir Path raiz)throws Exception{Files.writeString(raiz.resolve(AlmacenDisco.MARCADOR_IDENTIDAD),"corrupto-TEST");assertThrows(AlmacenException.class,()->new AlmacenDisco(raiz.toString()).identidadTecnica());assertEquals("corrupto-TEST",Files.readString(raiz.resolve(AlmacenDisco.MARCADOR_IDENTIDAD)));}
    @Test void marcadorNoEsBinarioServibleNiEliminableOSobrescribible(@TempDir Path raiz)throws Exception{
        var a=new AlmacenDisco(raiz.toString());String id=a.identidadTecnica();assertTrue(a.listarClaves().isEmpty());
        for(String clave:java.util.List.of(AlmacenDisco.MARCADOR_IDENTIDAD,"tenant/../"+AlmacenDisco.MARCADOR_IDENTIDAD)){
            assertTrue(a.abrir(clave).isEmpty());a.eliminar(clave);assertThrows(AlmacenException.class,()->a.guardarEnClave(clave,new byte[]{1},"application/octet-stream"));
        }
        assertEquals(id,a.identidadTecnica());assertTrue(a.listarClaves().isEmpty());
    }
    @Test void listarConservaSoloBinariosYProtegeAliasDelMarcador(@TempDir Path raiz)throws Exception{
        var a=new AlmacenDisco(raiz.toString());a.identidadTecnica();a.guardarEnClave("tenant/1/test.pdf",CONTENIDO,"application/pdf");
        Files.createLink(raiz.resolve("alias-control-test"),raiz.resolve(AlmacenDisco.MARCADOR_IDENTIDAD));
        assertEquals(java.util.Set.of("tenant/1/test.pdf"),a.listarClaves());assertTrue(a.abrir("alias-control-test").isEmpty());a.eliminar("alias-control-test");assertTrue(Files.exists(raiz.resolve("alias-control-test")));
    }
    @Test void creacionConcurrenteConservaUnaIdentidad(@TempDir Path raiz)throws Exception{
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{var uno=pool.submit(()->new AlmacenDisco(raiz.toString()).identidadTecnica());var dos=pool.submit(()->new AlmacenDisco(raiz.toString()).identidadTecnica());assertEquals(uno.get(10,java.util.concurrent.TimeUnit.SECONDS),dos.get(10,java.util.concurrent.TimeUnit.SECONDS));}
        finally{pool.shutdownNow();assertTrue(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS));}
    }
    @Test void errorAlLeerDirectorioNoEsAusenciaDeObjeto(@TempDir Path raiz)throws Exception{Files.createDirectories(raiz.resolve("tenant/1/directorio-test"));assertThrows(AlmacenException.class,()->new AlmacenDisco(raiz.toString()).abrir("tenant/1/directorio-test"));}
}
