package com.controllocal.app.arranque;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Escenario A8 del Plan S0: un perfil productivo mal configurado NO arranca, y
 * el mensaje nombra la variable que hay que corregir.
 * <p>
 * Cada test parte de un entorno APTO y estropea una sola cosa: asi el fallo
 * senala exactamente la regla rota, en vez de "algo de la configuracion".
 */
class ValidadorConfiguracionSeguridadTest {

    private final ValidadorConfiguracionSeguridad validador = new ValidadorConfiguracionSeguridad();

    /** Entorno productivo correcto: es la linea base de todos los casos. */
    private static ValidadorConfiguracionSeguridad.Entorno apto() {
        return new ValidadorConfiguracionSeguridad.Entorno(
                false,                                  // no usa el fallback
                48,                                     // secreto de 48 caracteres
                "https://brox.pe",                      // CORS acotado
                false,                                  // swagger apagado
                "jdbc:postgresql://bd-produccion:5432/controllocal",
                "una-contrasena-que-no-es-la-del-compose",
                "/var/lib/controllocal/almacen",
                true,                                   // almacen persistente y escribible
                0, 0, 0,
                false,                                 // clave MFA propia, no la de desarrollo
                false, false, false); // recuperacion de emergencia apagada
    }

    private String unicoProblema(ValidadorConfiguracionSeguridad.Entorno entorno) {
        List<String> problemas = validador.problemas(entorno);
        assertEquals(1, problemas.size(), "se esperaba exactamente un problema: " + problemas);
        return problemas.get(0);
    }

    @Test
    @DisplayName("un entorno productivo bien configurado no reporta ningun problema")
    void entornoAptoNoTieneProblemas() {
        assertTrue(validador.problemas(apto()).isEmpty());
    }

    @Nested
    @DisplayName("secreto de firma (H-01)")
    class Secreto {

        @Test
        void elFallbackDeDesarrolloDetieneElArranque() {
            var entorno = new ValidadorConfiguracionSeguridad.Entorno(
                    true, 0, "https://brox.pe", false,
                    "jdbc:postgresql://bd/controllocal", "otra", "/datos", true, 0, 0, 0, false, false, false, false);

            String problema = unicoProblema(entorno);

            assertTrue(problema.contains("API_TOKEN_SECRET"), "debe nombrar la variable: " + problema);
            assertTrue(problema.contains("openssl"), "debe decir como generarlo: " + problema);
        }

        @Test
        void unSecretoDeTreintaYUnCaracteresNoAlcanza() {
            var entorno = new ValidadorConfiguracionSeguridad.Entorno(
                    false, 31, "https://brox.pe", false,
                    "jdbc:postgresql://bd/controllocal", "otra", "/datos", true, 0, 0, 0, false, false, false, false);

            assertTrue(unicoProblema(entorno).contains("31"));
        }
    }

    @Nested
    @DisplayName("recuperacion de emergencia (V38)")
    class RecuperacionDeEmergencia {

        @Test
        @DisplayName("apagada no es un problema: es el estado normal")
        void apagadaNoReportaNada() {
            assertTrue(validador.problemas(apto()).isEmpty());
        }

        @Test
        @DisplayName("encendida sin los dos custodios detiene el arranque")
        void encendidaSinCustodiosDetieneElArranque() {
            var e = apto();
            String problema = unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), e.urlBaseDatos(), e.contrasenaBaseDatos(),
                    e.directorioAlmacen(), e.almacenPersistenteYEscribible(), 0, 0, 0, false,
                    true, false, true));

            assertTrue(problema.contains("RECUPERACION_CUSTODIO_A_ID"),
                    "debe nombrar las variables que faltan: " + problema);
        }

        @Test
        @DisplayName("encendida sin canal externo detiene el arranque")
        void encendidaSinCanalExternoDetieneElArranque() {
            var e = apto();
            String problema = unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), e.urlBaseDatos(), e.contrasenaBaseDatos(),
                    e.directorioAlmacen(), e.almacenPersistenteYEscribible(), 0, 0, 0, false,
                    true, true, false));

            // Es condicion TECNICA, no documental: la emergencia puede ocurrir
            // justo cuando nadie esta dentro para ver la campana.
            assertTrue(problema.contains("canal externo"), "debe decir por que: " + problema);
        }
    }

    @Nested
    @DisplayName("superficie expuesta")
    class Superficie {

        @Test
        void corsConComodinDetieneElArranque() {
            var e = apto();
            assertTrue(unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), "*", e.swaggerHabilitado(),
                    e.urlBaseDatos(), e.contrasenaBaseDatos(), e.directorioAlmacen(),
                    e.almacenPersistenteYEscribible(), 0, 0, 0, false, false, false, false)).contains("CORS_ORIGENES"));
        }

        @Test
        void corsApuntandoALocalhostDetieneElArranque() {
            var e = apto();
            assertTrue(unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), "http://localhost:4200",
                    e.swaggerHabilitado(), e.urlBaseDatos(), e.contrasenaBaseDatos(),
                    e.directorioAlmacen(), e.almacenPersistenteYEscribible(), 0, 0, 0, false, false, false, false))
                    .contains("localhost"));
        }

        @Test
        void swaggerPublicoDetieneElArranque() {
            var e = apto();
            assertTrue(unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(), true,
                    e.urlBaseDatos(), e.contrasenaBaseDatos(), e.directorioAlmacen(),
                    e.almacenPersistenteYEscribible(), 0, 0, 0, false, false, false, false)).contains("SWAGGER_HABILITADO"));
        }
    }

    @Nested
    @DisplayName("valores de desarrollo que se cuelan en prod")
    class ValoresDeDesarrollo {

        @Test
        void laContrasenaPorDefectoDeLaBaseDetieneElArranque() {
            var e = apto();
            assertTrue(unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), e.urlBaseDatos(), "controllocal",
                    e.directorioAlmacen(), e.almacenPersistenteYEscribible(), 0, 0, 0, false, false, false, false))
                    .contains("DB_PASSWORD"));
        }

        @Test
        void laBaseEnLocalhostDetieneElArranque() {
            var e = apto();
            assertTrue(unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), "jdbc:postgresql://localhost:5433/controllocal_dev",
                    e.contrasenaBaseDatos(), e.directorioAlmacen(),
                    e.almacenPersistenteYEscribible(), 0, 0, 0, false, false, false, false)).contains("DB_URL"));
        }
    }

    @Nested
    @DisplayName("almacen S3 (Bloque 8)")
    class AlmacenEnS3 {

        /** Entorno productivo correcto pero con los binarios en un bucket. */
        private ValidadorConfiguracionSeguridad.Entorno conS3(
                String endpoint, String bucket, String accessKey, String secretKey) {
            var e = apto();
            return new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), e.urlBaseDatos(), e.contrasenaBaseDatos(),
                    "S3",
                    // Con S3 el directorio deja de importar, y eso es el punto:
                    // exigirlo obligaria a inventar una ruta que nadie lee.
                    "", false,
                    endpoint, bucket, accessKey, secretKey,
                    0, 0, 0, false, false, false, false);
        }

        @Test
        @DisplayName("con S3 bien configurado NO se exige ALMACEN_DIR")
        void conS3NoSeExigeElDirectorio() {
            assertTrue(validador.problemas(
                    conS3("https://s3.brox.pe", "controllocal-prod", "", "")).isEmpty());
        }

        @Test
        void sinBucketDetieneElArranque() {
            assertTrue(unicoProblema(conS3("https://s3.brox.pe", "", "", ""))
                    .contains("ALMACEN_S3_BUCKET"));
        }

        @Test
        @DisplayName("un endpoint sin TLS detiene el arranque: por ahi viajan documentos de identidad")
        void unEndpointSinCifrarDetieneElArranque() {
            String problema = unicoProblema(conS3("http://almacen.interno", "controllocal-prod", "", ""));

            assertTrue(problema.contains("ALMACEN_S3_ENDPOINT"), problema);
            assertTrue(problema.contains("https"), problema);
        }

        @Test
        void elMinioDeDesarrolloDetieneElArranque() {
            // Las tres marcas de desarrollo a la vez: endpoint local, bucket y
            // credenciales publicadas. Un despliegue asi apuntaria a la maquina
            // de quien lo lanzo, si es que apunta a algo.
            List<String> problemas = validador.problemas(conS3(
                    "http://localhost:9000", "controllocal-dev",
                    "controllocal", "controllocal-dev-2026"));

            assertEquals(4, problemas.size(), "se esperaban los cuatro hallazgos: " + problemas);
            assertTrue(problemas.stream().anyMatch(p -> p.contains("localhost")), "" + problemas);
            assertTrue(problemas.stream().anyMatch(p -> p.contains("compose de desarrollo")),
                    "" + problemas);
        }

        @Test
        @DisplayName("credenciales vacias NO son un problema: son el rol del entorno")
        void credencialesVaciasSonValidas() {
            assertTrue(validador.problemas(
                    conS3("https://s3.brox.pe", "controllocal-prod", "", "")).isEmpty());
        }

        @Test
        @DisplayName("media credencial si lo es: o las dos, o ninguna")
        void unaSolaCredencialDetieneElArranque() {
            assertTrue(unicoProblema(conS3("https://s3.brox.pe", "controllocal-prod", "usuario", ""))
                    .contains("solo una de las dos"));
        }

        @Test
        void unProveedorInventadoDetieneElArranque() {
            var e = apto();
            String problema = unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), e.urlBaseDatos(), e.contrasenaBaseDatos(),
                    "AZURE", "/var/lib/controllocal/almacen", true,
                    "", "", "", "", 0, 0, 0, false, false, false, false));

            assertTrue(problema.contains("ALMACEN_PROVEEDOR"), problema);
            assertTrue(problema.contains("DISCO y S3"), problema);
        }
    }

    @Nested
    @DisplayName("almacen persistente (2026-08-04)")
    class Almacen {

        @Test
        void unaRutaRelativaDetieneElArranque() {
            var e = apto();
            String problema = unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), e.urlBaseDatos(), e.contrasenaBaseDatos(),
                    "./almacen-dev", true, 0, 0, 0, false, false, false, false));

            assertTrue(problema.contains("ALMACEN_DIR"));
            assertTrue(problema.contains("relativa"), problema);
        }

        @Test
        void unDirectorioQueNoSePuedeEscribirDetieneElArranque() {
            var e = apto();
            assertTrue(unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), e.urlBaseDatos(), e.contrasenaBaseDatos(),
                    "/var/lib/controllocal/almacen", false, 0, 0, 0, false, false, false, false))
                    .contains("no existe o no es escribible"));
        }

        @Test
        void sinConfigurarDetieneElArranque() {
            var e = apto();
            assertTrue(unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), e.urlBaseDatos(), e.contrasenaBaseDatos(),
                    "  ", true, 0, 0, 0, false, false, false, false)).contains("ALMACEN_DIR"));
        }
    }

    @Nested
    @DisplayName("credenciales conocidas (H-03) y gobierno")
    class Credenciales {

        @Test
        void unaSolaCredencialDelSeedDetieneElArranque() {
            var e = apto();
            String problema = unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), e.urlBaseDatos(), e.contrasenaBaseDatos(),
                    e.directorioAlmacen(), e.almacenPersistenteYEscribible(), 1, 0, 0, false, false, false, false));

            assertTrue(problema.contains("V900"), "debe decir que migracion falta: " + problema);
        }

        @Test
        void lasContrasenasCompartidasDetienenElArranque() {
            var e = apto();
            assertTrue(unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), e.urlBaseDatos(), e.contrasenaBaseDatos(),
                    e.directorioAlmacen(), e.almacenPersistenteYEscribible(), 0, 20, 0, false, false, false, false))
                    .contains("compartidas"));
        }

        @Test
        void unaOrganizacionSinAdministradorDetieneElArranque() {
            var e = apto();
            assertTrue(unicoProblema(new ValidadorConfiguracionSeguridad.Entorno(
                    e.usandoFallbackDeSecreto(), e.longitudSecreto(), e.corsOrigenes(),
                    e.swaggerHabilitado(), e.urlBaseDatos(), e.contrasenaBaseDatos(),
                    e.directorioAlmacen(), e.almacenPersistenteYEscribible(), 0, 0, 1, false, false, false, false))
                    .contains("sin administrador activo"));
        }
    }

    @Test
    @DisplayName("el mensaje de fallo enumera TODOS los problemas, no solo el primero")
    void elMensajeDeFalloLosEnumeraTodos() {
        var todoMal = new ValidadorConfiguracionSeguridad.Entorno(
                true, 0, "*", true,
                "jdbc:postgresql://localhost:5433/controllocal_dev", "controllocal",
                "./almacen-dev", false, 21, 20, 1, true, false, false, false);

        List<String> problemas = validador.problemas(todoMal);
        String mensaje = validador.mensajeDeFallo(problemas);

        // Diez reglas independientes: secreto, CLAVE MFA (V37), CORS, Swagger,
        // contrasena de BD, URL de BD, almacen, hashes del seed, contrasenas
        // compartidas y gobierno.
        assertEquals(10, problemas.size(), "los 10 hallazgos a la vez: " + problemas);
        assertTrue(mensaje.contains("10 problema(s)"), mensaje);
        assertTrue(mensaje.contains("D-S0-20"), "el mensaje cita la decision que lo justifica");
        assertTrue(mensaje.contains("MFA_CLAVE_CIFRADO"),
                "perder esa clave deja a todos los administradores sin factor: "
                        + "el arranque tiene que nombrarla");
        // Un arranque que falla debe poder arreglarse de una sola pasada: si
        // solo dijera el primer problema, harian falta diez despliegues.
        assertTrue(mensaje.contains("10)"), "debe numerar hasta el ultimo: " + mensaje);
    }
}
