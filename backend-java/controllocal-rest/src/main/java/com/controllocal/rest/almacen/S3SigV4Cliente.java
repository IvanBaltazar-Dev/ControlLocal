package com.controllocal.rest.almacen;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Cliente minimo de Amazon S3 firmado con AWS Signature Version 4, construido sobre
 * el {@code java.net.http.HttpClient} del JDK: no agrega ninguna dependencia Maven
 * (evita el SDK de AWS y posibles choques de classloader en GlassFish).
 *
 * Usa direccionamiento virtual-hosted ({@code bucket.s3.region.amazonaws.com}) y
 * soporta credenciales temporales STS (cabecera {@code x-amz-security-token}).
 */
final class S3SigV4Cliente {

    private static final String SERVICIO = "s3";
    private static final String ALGORITMO = "AWS4-HMAC-SHA256";
    private static final String TERMINADOR = "aws4_request";
    // SHA-256 de un cuerpo vacio (peticiones GET/HEAD/DELETE sin payload).
    private static final String HASH_VACIO =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final DateTimeFormatter FECHA_AMZ =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FECHA_DIA =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final Logger LOG = Logger.getLogger(S3SigV4Cliente.class.getName());

    private final String accessKey;
    private final String secretKey;
    private final String sessionToken; // nullable (credenciales permanentes)
    private final String region;
    private final String bucket;
    private final String host;
    private final HttpClient http;

    S3SigV4Cliente(String accessKey, String secretKey, String sessionToken, String region, String bucket) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.sessionToken = sessionToken == null || sessionToken.isBlank() ? null : sessionToken;
        this.region = region;
        this.bucket = bucket;
        this.host = bucket + ".s3." + region + ".amazonaws.com";
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .sslContext(contextoTls())
                .build();
    }

    String bucket() {
        return bucket;
    }

    String region() {
        return region;
    }

    // GlassFish fija javax.net.ssl.trustStore a su propio cacerts.jks, que a veces no
    // incluye la CA raiz de Amazon S3 -> "PKIX path building failed". Para no depender de
    // ese truststore, se confia en el cacerts del propio JDK (java.home/lib/security/cacerts),
    // que si trae las CA publicas. Con almacen.s3.tlsInseguro=true (SOLO desarrollo local)
    // se omite la verificacion, util si hay un proxy TLS de por medio.
    private static SSLContext contextoTls() {
        boolean inseguro = Boolean.parseBoolean(
                AwsConfig.get("almacen.s3.tlsInseguro", "ALMACEN_S3_TLS_INSEGURO", "false"));
        if (inseguro) {
            LOG.warning("S3 con verificacion TLS DESACTIVADA (almacen.s3.tlsInseguro=true). "
                    + "Solo para desarrollo local.");
            return contextoSinVerificacion();
        }
        try {
            Path cacerts = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
            char[] clave = System.getProperty("javax.net.ssl.trustStorePassword", "changeit").toCharArray();
            KeyStore ks = KeyStore.getInstance(cacerts.toFile(), clave);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception error) {
            LOG.warning("No se pudo cargar el truststore del JDK para S3 (" + error.getMessage()
                    + "); se usa el contexto TLS por defecto.");
            try {
                return SSLContext.getDefault();
            } catch (Exception fallback) {
                throw new AlmacenException("No se pudo inicializar TLS para S3.", fallback);
            }
        }
    }

    private static SSLContext contextoSinVerificacion() {
        try {
            TrustManager[] confiaEnTodo = {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] cadena, String tipo) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] cadena, String tipo) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, confiaEnTodo, new SecureRandom());
            return ctx;
        } catch (Exception error) {
            throw new AlmacenException("No se pudo crear el contexto TLS inseguro para S3.", error);
        }
    }

    /** Sube un objeto. Devuelve el codigo HTTP (200 = creado). */
    int putObject(String clave, byte[] contenido, String contentType) {
        TreeMap<String, String> headers = new TreeMap<>();
        if (contentType != null && !contentType.isBlank()) {
            headers.put("content-type", contentType);
        }
        HttpResponse<Void> respuesta = enviar("PUT", clave, headers, contenido,
                BodyPublishers.ofByteArray(contenido), BodyHandlers.discarding());
        return respuesta.statusCode();
    }

    /**
     * Igual que putObject pero captura el cuerpo de la respuesta (el XML de error de S3 con
     * el {@code <Code>}), para diagnosticar la causa exacta de un fallo.
     */
    String diagnosticoPut(String clave, byte[] contenido, String contentType) {
        TreeMap<String, String> headers = new TreeMap<>();
        if (contentType != null && !contentType.isBlank()) {
            headers.put("content-type", contentType);
        }
        HttpResponse<byte[]> respuesta = enviar("PUT", clave, headers, contenido,
                BodyPublishers.ofByteArray(contenido), BodyHandlers.ofByteArray());
        String cuerpo = respuesta.body() != null && respuesta.body().length > 0
                ? new String(respuesta.body(), StandardCharsets.UTF_8) : "";
        if (cuerpo.length() > 2000) {
            cuerpo = cuerpo.substring(0, 2000);
        }
        return "status=" + respuesta.statusCode()
                + (cuerpo.isBlank() ? "" : " body=" + cuerpo.replaceAll("\\s+", " ").trim());
    }

    /** Descarga un objeto. Vacio si el objeto no existe (404). */
    Optional<RespuestaObjeto> getObject(String clave) {
        HttpResponse<byte[]> respuesta = enviar("GET", clave, new TreeMap<>(), null,
                BodyPublishers.noBody(), BodyHandlers.ofByteArray());
        if (respuesta.statusCode() == 404) {
            return Optional.empty();
        }
        if (respuesta.statusCode() / 100 != 2) {
            throw new AlmacenException("S3 respondio " + respuesta.statusCode()
                    + " al descargar " + clave + ".");
        }
        String contentType = respuesta.headers().firstValue("content-type").orElse(null);
        return Optional.of(new RespuestaObjeto(respuesta.body(), contentType));
    }

    /** Elimina un objeto. true si S3 lo acepto (200/204). */
    boolean deleteObject(String clave) {
        HttpResponse<Void> respuesta = enviar("DELETE", clave, new TreeMap<>(), null,
                BodyPublishers.noBody(), BodyHandlers.discarding());
        int codigo = respuesta.statusCode();
        return codigo == 200 || codigo == 204;
    }

    /**
     * HeadBucket: prueba de conectividad. Devuelve el codigo HTTP y, cuando S3 la informa,
     * la region real del bucket (cabecera x-amz-bucket-region), util para autocorregir
     * aws.region cuando no coincide.
     */
    CabeceraBucket headBucket() {
        HttpResponse<Void> respuesta = enviar("HEAD", "", new TreeMap<>(), null,
                BodyPublishers.noBody(), BodyHandlers.discarding());
        String regionReal = respuesta.headers().firstValue("x-amz-bucket-region").orElse(null);
        return new CabeceraBucket(respuesta.statusCode(), regionReal);
    }

    // ---------------------------------------------------------------------
    // Firma y envio
    // ---------------------------------------------------------------------

    private <T> HttpResponse<T> enviar(String metodo, String clave, TreeMap<String, String> headersExtra,
                                       byte[] payload, HttpRequest.BodyPublisher cuerpo,
                                       HttpResponse.BodyHandler<T> manejador) {
        ZonedDateTime ahora = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = FECHA_AMZ.format(ahora);
        String dateStamp = FECHA_DIA.format(ahora);
        String hashPayload = payload != null ? hex(sha256(payload)) : HASH_VACIO;

        String canonicalUri = "/" + uriEncode(clave, false);

        TreeMap<String, String> headersFirmados = new TreeMap<>(headersExtra);
        headersFirmados.put("host", host);
        headersFirmados.put("x-amz-content-sha256", hashPayload);
        headersFirmados.put("x-amz-date", amzDate);
        if (sessionToken != null) {
            headersFirmados.put("x-amz-security-token", sessionToken);
        }

        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaders = new StringBuilder();
        for (var entrada : headersFirmados.entrySet()) {
            canonicalHeaders.append(entrada.getKey()).append(':')
                    .append(entrada.getValue().trim()).append('\n');
            if (signedHeaders.length() > 0) {
                signedHeaders.append(';');
            }
            signedHeaders.append(entrada.getKey());
        }

        String canonicalRequest = metodo + '\n'
                + canonicalUri + '\n'
                + "" + '\n' // canonical query string (sin query)
                + canonicalHeaders + '\n'
                + signedHeaders + '\n'
                + hashPayload;

        String scope = dateStamp + '/' + region + '/' + SERVICIO + '/' + TERMINADOR;
        String stringToSign = ALGORITMO + '\n'
                + amzDate + '\n'
                + scope + '\n'
                + hex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        byte[] signingKey = firmaDerivada(dateStamp);
        String firma = hex(hmac(signingKey, stringToSign));

        String authorization = ALGORITMO
                + " Credential=" + accessKey + '/' + scope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + firma;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("https://" + host + canonicalUri))
                .timeout(Duration.ofSeconds(30))
                .method(metodo, cuerpo)
                .header("Authorization", authorization)
                .header("x-amz-content-sha256", hashPayload)
                .header("x-amz-date", amzDate);
        if (sessionToken != null) {
            builder.header("x-amz-security-token", sessionToken);
        }
        for (var entrada : headersExtra.entrySet()) {
            // "host" lo gestiona el HttpClient; el resto (p. ej. content-type) se reenvia.
            builder.header(entrada.getKey(), entrada.getValue());
        }

        try {
            return http.send(builder.build(), manejador);
        } catch (java.io.IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AlmacenException("No se pudo contactar a S3 (" + host + "): " + error.getMessage(), error);
        }
    }

    private byte[] firmaDerivada(String dateStamp) {
        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, SERVICIO);
        return hmac(kService, TERMINADOR);
    }

    // ---------------------------------------------------------------------
    // Utilidades cripto / codificacion
    // ---------------------------------------------------------------------

    // Codifica segun las reglas UriEncode de AWS (RFC 3986). El '/' se conserva como
    // separador de ruta cuando encodeSlash es false (caso de la clave del objeto).
    static String uriEncode(String input, boolean encodeSlash) {
        StringBuilder result = new StringBuilder();
        for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '~' || c == '.') {
                result.append((char) c);
            } else if (c == '/') {
                result.append(encodeSlash ? "%2F" : "/");
            } else {
                result.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return result.toString();
    }

    private static byte[] sha256(byte[] datos) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(datos);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new AlmacenException("SHA-256 no disponible en la JVM.", error);
        }
    }

    private static byte[] hmac(byte[] clave, String datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(datos.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException error) {
            throw new AlmacenException("HmacSHA256 no disponible en la JVM.", error);
        }
    }

    private static String hex(byte[] datos) {
        StringBuilder sb = new StringBuilder(datos.length * 2);
        for (byte b : datos) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    record RespuestaObjeto(byte[] contenido, String contentType) {
    }

    record CabeceraBucket(int codigo, String region) {
    }
}
