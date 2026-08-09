package com.controllocal.web.almacen;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Almacen en disco local para desarrollo: los binarios cuelgan de un
 * directorio raiz configurable y la clave opaca es la ruta relativa
 * (carpeta + uuid + nombre saneado). Toda resolucion se normaliza y se
 * exige que quede DENTRO de la raiz (anti path-traversal).
 *
 * <p>Es el proveedor <b>por defecto</b> ({@code matchIfMissing = true}): quien
 * no configure nada sigue teniendo exactamente el comportamiento de siempre.
 * Elegir S3 es un acto deliberado, no un efecto secundario de actualizar.
 */
@Component
@ConditionalOnProperty(name = "controllocal.almacen.proveedor", havingValue = "DISCO",
        matchIfMissing = true)
public class AlmacenDisco implements AlmacenDocumentos {

    private final Path raiz;

    public AlmacenDisco(@Value("${controllocal.almacen.directorio:./almacen-dev}") String directorio) {
        this.raiz = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Override
    public String proveedor() {
        return "DISCO";
    }

    @Override
    public ArchivoGuardado guardar(String carpeta, String nombreArchivo, byte[] contenido, String contentType) {
        String clave = AlmacenDocumentos.claveNueva(carpeta, nombreArchivo);
        Path destino = resolver(clave)
                .orElseThrow(() -> new AlmacenException("Clave de almacen invalida: " + clave, null));
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
            return new ArchivoGuardado(clave, nombreArchivo);
        } catch (IOException error) {
            throw new AlmacenException("No se pudo escribir el archivo en el disco.", error);
        }
    }

    @Override
    public void guardarEnClave(String clave, byte[] contenido, String contentType) {
        Path destino = resolver(clave)
                .orElseThrow(() -> new AlmacenException("Clave de almacen invalida: " + clave, null));
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
        } catch (IOException error) {
            throw new AlmacenException("No se pudo escribir el archivo en el disco.", error);
        }
    }

    @Override
    public Optional<ArchivoDescargado> abrir(String clave) {
        return resolver(clave)
                .filter(Files::isRegularFile)
                .map(archivo -> {
                    try {
                        return new ArchivoDescargado(Files.readAllBytes(archivo),
                                archivo.getFileName().toString(),
                                AlmacenDocumentos.contentTypeDe(clave));
                    } catch (IOException error) {
                        throw new AlmacenException("No se pudo leer el archivo del disco.", error);
                    }
                });
    }

    @Override
    public void eliminar(String clave) {
        resolver(clave).ifPresent(archivo -> {
            try {
                Files.deleteIfExists(archivo);
            } catch (IOException ignorada) {
                // best-effort: el registro ya se elimino; el huerfano se tolera.
            }
        });
    }

    /**
     * Recorre el arbol y devuelve las rutas relativas a la raiz, con barras
     * normales: una clave de disco ES su ruta relativa, y en Windows el
     * separador seria {@code \}, que no casa con lo guardado en PostgreSQL.
     *
     * <p>Una raiz que todavia no existe devuelve vacio en vez de reventar: un
     * almacen recien creado no tiene nada, y eso no es un error.
     */
    @Override
    public java.util.Set<String> listarClaves() {
        if (!Files.isDirectory(raiz)) {
            return java.util.Set.of();
        }
        try (var rutas = Files.walk(raiz)) {
            return rutas.filter(Files::isRegularFile)
                    .map(archivo -> raiz.relativize(archivo).toString().replace('\\', '/'))
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        } catch (IOException error) {
            throw new AlmacenException("No se pudo recorrer el almacen en disco.", error);
        }
    }

    /** Resuelve la clave bajo la raiz; vacia si intenta escapar de ella. */
    private Optional<Path> resolver(String clave) {
        if (clave == null || clave.isBlank()) {
            return Optional.empty();
        }
        Path destino = raiz.resolve(clave).normalize();
        return destino.startsWith(raiz) ? Optional.of(destino) : Optional.empty();
    }

}
