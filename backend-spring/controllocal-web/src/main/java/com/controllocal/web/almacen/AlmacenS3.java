package com.controllocal.web.almacen;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

/**
 * Almacen sobre un servicio compatible con S3.
 *
 * <h3>Por que "compatible con S3" y no "AWS S3"</h3>
 *
 * El informe de tecnologias comparo seis opciones y <b>no eligio ninguna</b>: la
 * decision depende del entorno de despliegue —un nodo o varios—, que es del
 * Bloque 9 y todavia no esta tomada. Este cliente no la prejuzga: habla el
 * protocolo, no el proveedor, asi que el mismo codigo sirve para MinIO,
 * SeaweedFS, Garage, Ceph RGW o el S3 de AWS. Lo unico que cambia es el
 * endpoint.
 *
 * <p>Por eso {@code forcePathStyle} es configurable y viene <b>encendido por
 * defecto</b>: los compatibles autohospedados sirven en
 * {@code http://host/bucket/clave}, mientras que AWS prefiere el estilo
 * virtual-host ({@code https://bucket.s3.region.amazonaws.com/clave}) que exige
 * un DNS por bucket. Encendido funciona en los dos sitios; apagado, solo en
 * AWS. El valor seguro por defecto es el que no rompe al de casa.
 *
 * <h3>La regla que no se puede romper</h3>
 *
 * <b>El mismo archivo tiene que servirse igual venga del disco o del bucket.</b>
 * Mientras el contrato siga congelado los dos backends son intercambiables, y
 * una diferencia observable —otro content-type, otro nombre de descarga— seria
 * un cambio de contrato disfrazado de cambio de infraestructura. De ahi dos
 * decisiones que parecen menores y no lo son:
 *
 * <ul>
 *   <li>la clave la construye {@link AlmacenDocumentos#claveNueva}, compartida,
 *       para que migrar sea <b>copiar</b> y no traducir;</li>
 *   <li>al leer, el content-type se <b>deduce de la clave</b> con
 *       {@link AlmacenDocumentos#contentTypeDe} en vez de devolver el que S3
 *       tenga guardado. Se guarda igualmente en el objeto —es correcto y lo
 *       aprovechara quien lea el bucket por otra via—, pero servirlo cambiaria
 *       el tipo de los archivos que hoy se sirven por extension.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "controllocal.almacen.proveedor", havingValue = "S3")
public class AlmacenS3 implements AlmacenDocumentos {

    private final S3Client s3;
    private final String bucket;

    public AlmacenS3(S3Client s3, @Value("${controllocal.almacen.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public String proveedor() {
        return "S3";
    }

    @Override
    public ArchivoGuardado guardar(String carpeta, String nombreArchivo, byte[] contenido,
                                   String contentType) {
        String clave = AlmacenDocumentos.claveNueva(carpeta, nombreArchivo);
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(clave)
                            .contentType(contentType != null && !contentType.isBlank()
                                    ? contentType
                                    : AlmacenDocumentos.contentTypeDe(clave))
                            .build(),
                    RequestBody.fromBytes(contenido));
            return new ArchivoGuardado(clave, nombreArchivo);
        } catch (SdkException error) {
            throw new AlmacenException("No se pudo escribir el archivo en el bucket.", error);
        }
    }

    @Override
    public void guardarEnClave(String clave, byte[] contenido, String contentType) {
        if (clave == null || clave.isBlank()) {
            throw new AlmacenException("Clave de almacen invalida: " + clave, null);
        }
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(clave)
                            .contentType(contentType != null && !contentType.isBlank()
                                    ? contentType
                                    : AlmacenDocumentos.contentTypeDe(clave))
                            .build(),
                    RequestBody.fromBytes(contenido));
        } catch (SdkException error) {
            throw new AlmacenException("No se pudo escribir el archivo en el bucket.", error);
        }
    }

    /**
     * Vacio cuando el objeto no existe, igual que el disco cuando el fichero no
     * esta: quien llama distingue "no hay archivo" de "el almacen fallo", y esa
     * diferencia es la que separa un 404 de un 500.
     *
     * <p>Se contemplan las dos formas en que un compatible dice "no existe":
     * {@code NoSuchKeyException}, que es la del protocolo, y un
     * {@code S3Exception} con estado 404, que devuelven algunas
     * implementaciones cuando el <b>bucket</b> tampoco esta. Un bucket ausente
     * en produccion es un fallo de configuracion, pero no hay nada que este
     * metodo pueda hacer con el que no sea decir que no encontro el archivo;
     * quien impide llegar ahi es la validacion de arranque.
     */
    @Override
    public Optional<ArchivoDescargado> abrir(String clave) {
        if (clave == null || clave.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] contenido = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(clave)
                    .build()).asByteArray();
            return Optional.of(new ArchivoDescargado(contenido,
                    AlmacenDocumentos.nombreDe(clave),
                    AlmacenDocumentos.contentTypeDe(clave)));
        } catch (NoSuchKeyException ausente) {
            return Optional.empty();
        } catch (S3Exception error) {
            if (error.statusCode() == 404) {
                return Optional.empty();
            }
            throw new AlmacenException("No se pudo leer el archivo del bucket.", error);
        } catch (SdkException error) {
            throw new AlmacenException("No se pudo leer el archivo del bucket.", error);
        }
    }

    /**
     * Best-effort, como en disco. S3 responde 204 aunque la clave no exista, de
     * modo que el caso "ya no estaba" no hace ruido por si solo; lo que se
     * tolera aqui es el fallo de red o de permisos. El registro ya se borro y
     * dejar un huerfano en el bucket es preferible a fallar la operacion de
     * negocio por un binario.
     */
    /**
     * Pagina el bucket entero. Se usa el paginador del SDK y no un
     * {@code listObjectsV2} suelto porque ese <b>corta en 1000 objetos sin
     * decirlo</b>: una conciliacion que solo mira los mil primeros informaria
     * de huerfanos que no lo son y daria por buena una migracion incompleta.
     */
    @Override
    public java.util.Set<String> listarClaves() {
        try {
            var claves = new java.util.LinkedHashSet<String>();
            s3.listObjectsV2Paginator(software.amazon.awssdk.services.s3.model.ListObjectsV2Request
                            .builder().bucket(bucket).build())
                    .contents()
                    .forEach(objeto -> claves.add(objeto.key()));
            return claves;
        } catch (SdkException error) {
            throw new AlmacenException("No se pudo listar el bucket.", error);
        }
    }

    @Override
    public void eliminar(String clave) {
        if (clave == null || clave.isBlank()) {
            return;
        }
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave).build());
        } catch (SdkException ignorada) {
            // best-effort: ver arriba.
        }
    }
}
