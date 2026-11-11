package com.controllocal.web.almacen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlmacenS3} contra un {@code S3Client} simulado.
 *
 * <p>Lo que se vigila aqui <b>no</b> es que el SDK funcione —eso es cosa de
 * Amazon— sino las tres decisiones propias que hacen que el disco y el bucket
 * sean intercambiables: <b>la clave exacta preasignada</b>,
 * <b>el mismo content-type servido</b> y <b>la misma respuesta
 * cuando el archivo no esta</b>. La prueba de que hablan con un servidor de
 * verdad la da el E2E contra MinIO; esta fija el comportamiento.
 */
class AlmacenS3Test {

    private static final String BUCKET = "controllocal-test";

    private final S3Client s3 = mock(S3Client.class);
    private final AlmacenS3 almacen = new AlmacenS3(s3, BUCKET);

    @Test
    void seAnunciaComoS3() {
        assertEquals("S3", almacen.proveedor());
    }

    @Test
    @DisplayName("el proveedor conserva clave, tipo y bytes del montaje TEST")
    void conservaLaClavePreasignadaYLosBytes() throws Exception {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        String clave = "tenant/7/fotos/TEST-fachada_principal.JPG";
        byte[] contenido = new byte[]{1, 2, 3};

        almacen.guardarEnClave(clave, contenido, "image/jpeg");

        var solicitud = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        var cuerpo = org.mockito.ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(solicitud.capture(), cuerpo.capture());
        assertEquals(BUCKET, solicitud.getValue().bucket());
        assertEquals(clave, solicitud.getValue().key());
        assertEquals("image/jpeg", solicitud.getValue().contentType());
        try (var entrada = cuerpo.getValue().contentStreamProvider().newStream()) {
            assertArrayEquals(contenido, entrada.readAllBytes());
        }
    }

    @Test
    @DisplayName("al leer, el content-type sale de la CLAVE y no de lo que guardo el bucket")
    void elContentTypeSeDeduceDeLaClave() {
        // Se responde a proposito con un content-type distinto del que
        // corresponde a la extension: si el almacen lo devolviera, el mismo
        // archivo se serviria distinto segun donde estuviera guardado, y eso
        // es un cambio de contrato disfrazado de cambio de infraestructura.
        darPorRespuesta("tenant/7/fotos/abc-plano.pdf", "hola".getBytes(StandardCharsets.UTF_8),
                "application/x-inventado");

        var archivo = almacen.abrir("tenant/7/fotos/abc-plano.pdf").orElseThrow();

        assertEquals("application/pdf", archivo.contentType());
        assertEquals("abc-plano.pdf", archivo.nombre());
        assertArrayEquals("hola".getBytes(StandardCharsets.UTF_8), archivo.contenido());
    }

    @Test
    @DisplayName("un objeto ausente devuelve vacio, no excepcion: eso separa un 404 de un 500")
    void laClaveInexistenteDevuelveVacio() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("no such key").build());

        assertFalse(almacen.abrir("tenant/7/fotos/no-existe.pdf").isPresent());
    }

    @Test
    @DisplayName("un bucket ausente es fallo técnico, no prueba de retiro de un objeto")
    void bucketAusenteNoAcreditaAusenciaDelObjeto() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder()
                        .statusCode(404)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucket").build())
                        .message("no such bucket")
                        .build());

        assertThrows(AlmacenException.class,()->almacen.abrir("tenant/7/fotos/x.pdf"));
    }

    @Test void un404SinCodigoEsFalloTecnico(){when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow((S3Exception)S3Exception.builder().statusCode(404).build());assertThrows(AlmacenException.class,()->almacen.abrir("tenant/7/fotos/x.pdf"));}
    @Test void noSuchKeyGenericoSiEsAusencia(){when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow((S3Exception)S3Exception.builder().statusCode(404).awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchKey").build()).build());assertTrue(almacen.abrir("tenant/7/fotos/x.pdf").isEmpty());}
    void endpoint(String valor){var b=software.amazon.awssdk.services.s3.S3ServiceClientConfiguration.builder().region(software.amazon.awssdk.regions.Region.US_EAST_1);if(valor!=null)b.endpointOverride(java.net.URI.create(valor));when(s3.serviceClientConfiguration()).thenReturn(b.build());}
    @Test void namespaceS3UsaEndpointRealYBucketSinRed(){endpoint("https://almacen-test.example.invalid");String id=almacen.identidadTecnica();assertEquals(id,new AlmacenS3(s3,BUCKET).identidadTecnica());assertFalse(id.equals(new AlmacenS3(s3,"otro-bucket-test").identidadTecnica()));endpoint("https://otro-test.example.invalid");assertFalse(id.equals(almacen.identidadTecnica()));verify(s3,never()).getObjectAsBytes(any(GetObjectRequest.class));}
    @Test void awsSinOverrideTieneNamespaceEstable(){endpoint(null);String id=almacen.identidadTecnica();assertTrue(id.matches("[0-9a-f]{64}"));endpoint(null);assertEquals(id,almacen.identidadTecnica());}
    @Test void namespaceNoAceptaCredencialesNiQueryDeEndpoint(){for(String uri:java.util.List.of("https://user:secret@example.invalid","https://example.invalid?token=secret","https://example.invalid#fragment")){endpoint(uri);var fallo=assertThrows(AlmacenException.class,almacen::identidadTecnica);assertFalse(fallo.getMessage().contains("secret"));assertEquals(null,fallo.getCause());}}
    @Test void clienteSinMetadataNoInventaNamespace(){assertThrows(AlmacenException.class,almacen::identidadTecnica);}
    @Test void awsDistingueRegionRealYNoInventaUnaAusente(){endpoint(null);String id=almacen.identidadTecnica();when(s3.serviceClientConfiguration()).thenReturn(software.amazon.awssdk.services.s3.S3ServiceClientConfiguration.builder().region(software.amazon.awssdk.regions.Region.CN_NORTH_1).build());assertFalse(id.equals(almacen.identidadTecnica()));when(s3.serviceClientConfiguration()).thenReturn(software.amazon.awssdk.services.s3.S3ServiceClientConfiguration.builder().build());assertThrows(AlmacenException.class,almacen::identidadTecnica);}
    @Test void cambiarCredencialNoCambiaElNamespace(){endpoint("https://almacen-test.example.invalid");String id=almacen.identidadTecnica();when(s3.serviceClientConfiguration()).thenReturn(software.amazon.awssdk.services.s3.S3ServiceClientConfiguration.builder().region(software.amazon.awssdk.regions.Region.EU_WEST_1).endpointOverride(java.net.URI.create("https://almacen-test.example.invalid")).credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("TEST_ACCESS","TEST_SECRET_FICTICIO"))).build());assertEquals(id,almacen.identidadTecnica());}
    @Test void pathEscapadoNoColisionaConUnSegmentoDistinto(){endpoint("https://almacen-test.example.invalid/a%2Fb");String id=almacen.identidadTecnica();endpoint("https://almacen-test.example.invalid/a/b");assertFalse(id.equals(almacen.identidadTecnica()));}

    @Test
    @DisplayName("un fallo real SI revienta: quedarse callado convertiria una caida en 'no hay foto'")
    void unFalloDelAlmacenNoSeConfundeConAusencia() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create("la red no responde"));

        assertThrows(AlmacenException.class, () -> almacen.abrir("tenant/7/fotos/x.pdf"));
    }

    @Test
    void unaClaveVaciaNiSiquieraLlegaAlBucket() {
        assertFalse(almacen.abrir("  ").isPresent());
        verify(s3, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("borrar es best-effort: un fallo no tumba la operacion de negocio")
    void elBorradoSeTragaElFallo() {
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("sin permisos"));

        // El registro ya se elimino; dejar un huerfano en el bucket es
        // preferible a fallar por un binario.
        almacen.eliminar("tenant/7/fotos/x.pdf");
    }

    @Test
    void elBorradoDeUnaClaveVaciaNoLlamaAlBucket() {
        almacen.eliminar(null);
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    private void darPorRespuesta(String clave, byte[] contenido, String contentTypeGuardado) {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().contentType(contentTypeGuardado).build(),
                        contenido));
    }

    @Test
    @DisplayName("las claves antiguas SIN prefijo de tenant se siguen leyendo tal cual")
    void lasClavesHeredadasSiguenFuncionando() {
        darPorRespuesta("documentos/abc-dni.pdf", new byte[]{9}, "application/pdf");

        Optional<AlmacenDocumentos.ArchivoDescargado> archivo =
                almacen.abrir("documentos/abc-dni.pdf");

        assertTrue(archivo.isPresent());
        assertEquals("abc-dni.pdf", archivo.get().nombre());
    }
}
