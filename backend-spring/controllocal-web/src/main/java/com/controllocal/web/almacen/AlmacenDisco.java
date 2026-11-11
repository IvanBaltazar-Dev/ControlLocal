package com.controllocal.web.almacen;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Optional;

/**
 * Almacen en disco local para desarrollo: los binarios cuelgan de un
 * directorio raiz configurable y la clave opaca preasignada por Core es la
 * ruta relativa. Toda resolucion se normaliza y se
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

    static final String MARCADOR_IDENTIDAD=".brox-almacen-id";

    private final Path raiz;

    public AlmacenDisco(@Value("${controllocal.almacen.directorio:./almacen-dev}") String directorio) {
        this.raiz = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Override
    public String proveedor() {
        return "DISCO";
    }

    @Override public String identidadTecnica() {
        // No caché: reemplazar el volumen o su marcador no debe heredar la identidad
        // del objeto Java que estaba abierto. El UUID acompaña al almacén al restaurarlo.
        synchronized(AlmacenDisco.class) {
            try {
                Files.createDirectories(raiz);
                Path marcador=raiz.resolve(MARCADOR_IDENTIDAD);
                if(Files.isSymbolicLink(marcador))throw new IOException("Marcador no regular");
                try {
                    byte[] valor=UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII);
                    try(var canal=java.nio.channels.FileChannel.open(marcador,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                        var contenido=java.nio.ByteBuffer.wrap(valor);while(contenido.hasRemaining())canal.write(contenido);
                        canal.force(true);
                    }
                } catch(FileAlreadyExistsException existente) { /* Nunca sustituir un marcador, ni siquiera corrupto. */ }
                if(!Files.isRegularFile(marcador)||Files.size(marcador)!=36)throw new IOException("Marcador inválido");
                byte[] bytes=new byte[37];int longitud;
                try(var entrada=Files.newInputStream(marcador)){longitud=entrada.readNBytes(bytes,0,bytes.length);}
                String valor=new String(bytes,0,longitud,StandardCharsets.US_ASCII);
                if(longitud!=36||!UUID.fromString(valor).toString().equals(valor))throw new IOException("Marcador inválido");
                return AlmacenDocumentos.huellaIdentidad(proveedor(),valor);
            } catch(IOException|IllegalArgumentException fallo) {
                throw new AlmacenException("No se pudo verificar la identidad del almacén privado.",null);
            }
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
        var resuelto=resolver(clave);if(resuelto.isEmpty())return Optional.empty();
        Path archivo=resuelto.get();
        try {
            return Optional.of(new ArchivoDescargado(Files.readAllBytes(archivo),
                    archivo.getFileName().toString(),AlmacenDocumentos.contentTypeDe(clave)));
        } catch(java.nio.file.NoSuchFileException ausente) {
            return Optional.empty();
        } catch(IOException error) {
            // isRegularFile devuelve false también ante errores de acceso: no
            // permite certificar ausencia ni el retiro de una salida durable.
            throw new AlmacenException("No se pudo leer el archivo del disco.",error);
        }
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
            return rutas.filter(Files::isRegularFile).filter(archivo->!esMarcador(archivo))
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
        return destino.startsWith(raiz)&&!esMarcador(destino) ? Optional.of(destino) : Optional.empty();
    }

    private boolean esMarcador(Path archivo) {
        Path marcador=raiz.resolve(MARCADOR_IDENTIDAD);
        if(archivo.equals(marcador))return true;
        try{return Files.exists(archivo)&&Files.exists(marcador)&&Files.isSameFile(archivo,marcador);}
        catch(IOException noVerificable){return true;}
    }

}
