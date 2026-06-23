package com.controllocal.rest.almacen;

import java.util.Optional;
import java.util.UUID;

/**
 * Almacen de documentos respaldado por Amazon S3 (direccionamiento virtual-hosted),
 * firmado con SigV4 sobre el HttpClient del JDK. La clave del objeto es
 * {@code carpeta/uuid_nombre.ext} y se persiste tal cual en {@code ruta_archivo}.
 */
public final class AlmacenS3 implements AlmacenDocumentos {

    private final S3SigV4Cliente s3;
    private final String prefix;

    private AlmacenS3(S3SigV4Cliente s3, String prefix) {
        this.s3 = s3;
        this.prefix = normalizarPrefijo(prefix);
    }

    // Quita barras sobrantes del prefijo configurado: "/documentos/" -> "documentos".
    private static String normalizarPrefijo(String prefix) {
        if (prefix == null) {
            return "";
        }
        String limpio = prefix.trim();
        while (limpio.startsWith("/")) {
            limpio = limpio.substring(1);
        }
        while (limpio.endsWith("/")) {
            limpio = limpio.substring(0, limpio.length() - 1);
        }
        return limpio;
    }

    /**
     * Construye el almacen S3 si la configuracion esta completa
     * (accessKeyId, secretAccessKey y bucket). Devuelve vacio si falta algo,
     * para que {@link Almacenes} pueda recurrir al disco local.
     */
    public static Optional<AlmacenS3> desdeConfig() {
        String accessKey = AwsConfig.get("aws.accessKeyId", "AWS_ACCESS_KEY_ID", "");
        String secretKey = AwsConfig.get("aws.secretAccessKey", "AWS_SECRET_ACCESS_KEY", "");
        String sessionToken = AwsConfig.get("aws.sessionToken", "AWS_SESSION_TOKEN", "");
        String region = AwsConfig.get("aws.region", "AWS_REGION", "us-east-1");
        String bucket = AwsConfig.get("aws.s3.bucket", "AWS_S3_BUCKET", "");

        if (accessKey.isBlank() || secretKey.isBlank() || bucket.isBlank()) {
            return Optional.empty();
        }
        S3SigV4Cliente cliente = new S3SigV4Cliente(accessKey, secretKey, sessionToken, region, bucket);
        // Autodescubre la region real del bucket: si HeadBucket responde 301/400 con
        // x-amz-bucket-region distinta a aws.region, se reconstruye el cliente con esa
        // region (evita tener que configurar aws.region a mano). Si la red/credenciales
        // fallan, se mantiene la region configurada y verificarConexion lo reporta.
        try {
            S3SigV4Cliente.CabeceraBucket cabecera = cliente.headBucket();
            if ((cabecera.codigo() == 301 || cabecera.codigo() == 400)
                    && cabecera.region() != null && !cabecera.region().isBlank()
                    && !cabecera.region().equals(region)) {
                cliente = new S3SigV4Cliente(accessKey, secretKey, sessionToken, cabecera.region(), bucket);
            }
        } catch (AlmacenException ignorado) {
            // Sin conectividad o credenciales: se conserva la region configurada.
        }
        // Prefijo de la clave en el bucket (carpeta logica). Por defecto "documentos",
        // asi los archivos quedan en s3://<bucket>/documentos/<solicitud>/...
        String prefix = AwsConfig.get("aws.s3.prefix", "AWS_S3_PREFIX", "documentos");
        return Optional.of(new AlmacenS3(cliente, prefix));
    }

    @Override
    public ArchivoGuardado guardar(String carpeta, String nombreArchivo, byte[] contenido, String contentType) {
        String carpetaSegura = NombresArchivo.carpetaSegura(carpeta);
        String nombreSeguro = NombresArchivo.nombreSeguro(nombreArchivo);
        String base = carpetaSegura + "/" + UUID.randomUUID().toString().replace("-", "") + "_" + nombreSeguro;
        String clave = prefix.isEmpty() ? base : prefix + "/" + base;

        int codigo = s3.putObject(clave, contenido, contentType);
        if (codigo / 100 != 2) {
            throw new AlmacenException("S3 respondio " + codigo + " al subir el documento.");
        }
        return new ArchivoGuardado(clave, nombreSeguro, contenido.length);
    }

    @Override
    public Optional<ArchivoDescargado> abrir(String clave) {
        validarClave(clave);
        return s3.getObject(clave).map(objeto -> new ArchivoDescargado(
                objeto.contenido(),
                // Se prioriza la extension de la clave para servir PDF/imagen en linea.
                NombresArchivo.contentType(clave),
                nombreDesdeClave(clave)));
    }

    @Override
    public boolean eliminar(String clave) {
        validarClave(clave);
        return s3.deleteObject(clave);
    }

    @Override
    public EstadoAlmacen verificarConexion() {
        try {
            S3SigV4Cliente.CabeceraBucket cabecera = s3.headBucket();
            boolean regionDistinta = cabecera.region() != null && !cabecera.region().isBlank()
                    && !cabecera.region().equals(s3.region());
            return switch (cabecera.codigo()) {
                case 200 -> EstadoAlmacen.ok("S3",
                        "Bucket «" + s3.bucket() + "» accesible en la region " + s3.region() + ".");
                case 301 -> EstadoAlmacen.falla("S3", "El bucket esta en la region «"
                        + (cabecera.region() != null ? cabecera.region() : "desconocida")
                        + "» y aws.region=" + s3.region() + ". Ajusta aws.region.");
                case 400 -> regionDistinta
                        ? EstadoAlmacen.falla("S3", "La region del bucket («" + cabecera.region()
                                + "») no coincide con aws.region=" + s3.region() + ".")
                        : EstadoAlmacen.falla("S3", "S3 rechazo la peticion (400): probablemente el "
                                + "token STS expiro o la firma es invalida. Renueva las credenciales.");
                case 403 -> EstadoAlmacen.falla("S3", "Sin acceso o token expirado (403).");
                case 404 -> EstadoAlmacen.falla("S3", "El bucket «" + s3.bucket() + "» no existe.");
                default -> EstadoAlmacen.falla("S3",
                        "S3 respondio " + cabecera.codigo() + " al verificar el bucket.");
            };
        } catch (AlmacenException error) {
            return EstadoAlmacen.falla("S3", "No se pudo contactar a S3: " + error.getMessage());
        }
    }

    // Diagnostico de bajo nivel: sube un objeto de prueba y devuelve la respuesta cruda de
    // S3 (status + cuerpo XML con el <Code>), para identificar la causa exacta de un fallo
    // (ExpiredToken, SignatureDoesNotMatch, AccessDenied, AuthorizationHeaderMalformed...).
    public String diagnostico() {
        return s3.diagnosticoPut("PRUEBA-DIAG/diagnostico.txt",
                "diagnostico ControlLocal".getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain");
    }

    @Override
    public String proveedor() {
        return "S3";
    }

    private static void validarClave(String clave) {
        if (clave == null || clave.isBlank() || clave.contains("..") || clave.contains("\\")) {
            throw new AlmacenException("Clave de documento invalida.");
        }
    }

    private static String nombreDesdeClave(String clave) {
        int barra = clave.lastIndexOf('/');
        return barra >= 0 ? clave.substring(barra + 1) : clave;
    }
}
