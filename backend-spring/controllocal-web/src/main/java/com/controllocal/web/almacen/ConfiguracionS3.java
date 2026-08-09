package com.controllocal.web.almacen;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Construye el {@link S3Client} y solo se activa con
 * {@code controllocal.almacen.proveedor=S3}. Sin esa propiedad el SDK ni se
 * instancia: no hay cliente que configurar, ni credenciales que buscar, ni
 * arranque que pueda fallar por algo que nadie va a usar.
 *
 * <p>Vive en el paquete del almacen a proposito. La regla de
 * {@link AlmacenDocumentos} es que nada fuera de aqui sepa donde estan los
 * bytes, y un {@code S3Client} publicado en el contexto general seria
 * exactamente eso: una invitacion a que cualquier servicio empiece a hablar con
 * el bucket por su cuenta.
 */
@Configuration
@ConditionalOnProperty(name = "controllocal.almacen.proveedor", havingValue = "S3")
public class ConfiguracionS3 {

    /**
     * @param endpoint      vacio para el S3 real de AWS; la URL del servicio para un compatible
     * @param region        el SDK la exige aunque el compatible la ignore
     * @param accessKey     vacio para delegar en la cadena por defecto (rol IAM, variables, perfil)
     * @param secretKey     idem
     * @param estiloDeRuta  {@code true} = {@code host/bucket/clave}; ver {@link AlmacenS3}
     */
    @Bean
    public S3Client s3Client(
            @Value("${controllocal.almacen.s3.endpoint:}") String endpoint,
            @Value("${controllocal.almacen.s3.region:us-east-1}") String region,
            @Value("${controllocal.almacen.s3.access-key:}") String accessKey,
            @Value("${controllocal.almacen.s3.secret-key:}") String secretKey,
            @Value("${controllocal.almacen.s3.estilo-de-ruta:true}") boolean estiloDeRuta) {

        var constructor = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(estiloDeRuta)
                        .build());

        if (!endpoint.isBlank()) {
            constructor = constructor.endpointOverride(URI.create(endpoint));
        }

        // Sin claves explicitas se usa la cadena por defecto del SDK. No es un
        // descuido: es lo que permite que en produccion las credenciales las
        // ponga el ENTORNO (un rol IAM, el agente de la nube) y no un fichero
        // de configuracion. Una credencial que no existe en ningun sitio es la
        // unica que no se puede filtrar.
        constructor = accessKey.isBlank() || secretKey.isBlank()
                ? constructor.credentialsProvider(DefaultCredentialsProvider.create())
                : constructor.credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        return constructor.build();
    }
}
