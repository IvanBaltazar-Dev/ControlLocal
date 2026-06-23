package com.controllocal.rest.almacen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Almacen de documentos en disco local. Es el respaldo del almacen hibrido cuando S3
 * no esta configurado o no responde. La raiz se resuelve desde {@code almacen.local.ruta}
 * (absoluta o relativa al home del usuario) y nunca se permite que una clave escape de
 * esa raiz (anti path-traversal).
 */
public final class AlmacenLocal implements AlmacenDocumentos {

    private final Path raiz;

    private AlmacenLocal(Path raiz) {
        this.raiz = raiz;
    }

    public static AlmacenLocal desdeConfig() {
        String configurada = AwsConfig.get("almacen.local.ruta", "ALMACEN_LOCAL_RUTA", "");
        Path raiz;
        if (configurada.isBlank()) {
            raiz = Path.of(System.getProperty("user.home"), "controllocal", "almacen-documentos");
        } else {
            Path candidata = Path.of(configurada);
            raiz = candidata.isAbsolute()
                    ? candidata
                    : Path.of(System.getProperty("user.home")).resolve(candidata);
        }
        return new AlmacenLocal(raiz.toAbsolutePath().normalize());
    }

    @Override
    public ArchivoGuardado guardar(String carpeta, String nombreArchivo, byte[] contenido, String contentType) {
        String carpetaSegura = NombresArchivo.carpetaSegura(carpeta);
        String nombreSeguro = NombresArchivo.nombreSeguro(nombreArchivo);
        String clave = carpetaSegura + "/" + UUID.randomUUID().toString().replace("-", "") + "_" + nombreSeguro;

        Path destino = rutaFisica(clave);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
        } catch (IOException error) {
            throw new AlmacenException("No se pudo guardar el documento en disco: " + error.getMessage(), error);
        }
        return new ArchivoGuardado(clave, nombreSeguro, contenido.length);
    }

    @Override
    public Optional<ArchivoDescargado> abrir(String clave) {
        Path ruta = rutaFisica(clave);
        if (!Files.isRegularFile(ruta)) {
            return Optional.empty();
        }
        try {
            byte[] contenido = Files.readAllBytes(ruta);
            return Optional.of(new ArchivoDescargado(
                    contenido, NombresArchivo.contentType(clave), nombreDesdeClave(clave)));
        } catch (IOException error) {
            throw new AlmacenException("No se pudo leer el documento de disco: " + error.getMessage(), error);
        }
    }

    @Override
    public boolean eliminar(String clave) {
        try {
            return Files.deleteIfExists(rutaFisica(clave));
        } catch (IOException error) {
            throw new AlmacenException("No se pudo eliminar el documento de disco: " + error.getMessage(), error);
        }
    }

    @Override
    public EstadoAlmacen verificarConexion() {
        try {
            Files.createDirectories(raiz);
            Path sonda = raiz.resolve(".sonda-" + UUID.randomUUID().toString().replace("-", ""));
            Files.writeString(sonda, "ok");
            Files.deleteIfExists(sonda);
            return EstadoAlmacen.ok("Local", "Carpeta «" + raiz + "» escribible.");
        } catch (IOException error) {
            return EstadoAlmacen.falla("Local", "Carpeta no escribible: " + error.getMessage());
        }
    }

    @Override
    public String proveedor() {
        return "Local";
    }

    // La clave nunca puede escapar de la raiz del almacen (anti path-traversal).
    private Path rutaFisica(String clave) {
        if (clave == null || clave.isBlank() || clave.contains("..") || clave.contains("\\")) {
            throw new AlmacenException("Clave de documento invalida.");
        }
        Path completa = raiz.resolve(clave).normalize();
        if (!completa.startsWith(raiz)) {
            throw new AlmacenException("Clave de documento invalida.");
        }
        return completa;
    }

    private static String nombreDesdeClave(String clave) {
        int barra = clave.lastIndexOf('/');
        return barra >= 0 ? clave.substring(barra + 1) : clave;
    }
}
