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
 * sean intercambiables mientras el contrato siga congelado: <b>la misma forma
 * de clave</b>, <b>el mismo content-type servido</b> y <b>la misma respuesta
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
    @DisplayName("la clave tiene la MISMA forma que en disco: migrar es copiar, no traducir")
    void laClaveMantieneLaFormaCompartida() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        var guardado = almacen.guardar(AlmacenDocumentos.carpetaDeTenant(7, "fotos"),
                "fachada principal.JPG", new byte[]{1, 2, 3}, "image/jpeg");

        // tenant/7/fotos/xxxxxxxx-fachada_principal.JPG
        assertTrue(guardado.clave().startsWith("tenant/7/fotos/"), guardado.clave());
        assertTrue(guardado.clave().endsWith("-fachada_principal.JPG"), guardado.clave());
        // El nombre que se devuelve es el ORIGINAL, no el saneado: es lo que
        // ve el usuario, y el saneo solo protege la clave.
        assertEquals("fachada principal.JPG", guardado.nombre());
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
    @DisplayName("tambien vale el 404 generico: algunos compatibles lo usan si falta el bucket")
    void el404GenericoTambienEsAusencia() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder()
                        .statusCode(404)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucket").build())
                        .message("no such bucket")
                        .build());

        assertFalse(almacen.abrir("tenant/7/fotos/x.pdf").isPresent());
    }

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
