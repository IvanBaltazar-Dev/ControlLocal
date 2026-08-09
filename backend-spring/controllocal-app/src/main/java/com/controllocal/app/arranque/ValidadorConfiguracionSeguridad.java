package com.controllocal.app.arranque;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprueba que la configuracion de seguridad de un despliegue PRODUCTIVO sea
 * valida. Implementa D-S0-2: un entorno mal configurado debe <b>no arrancar</b>,
 * en vez de arrancar inseguro y en silencio.
 * <p>
 * La logica es <b>pura</b> a proposito: recibe una foto del entorno y devuelve
 * la lista de problemas. Quien la toma (ficheros, base de datos, Spring
 * Environment) es {@link ComprobacionArranqueSeguridad}. Asi las reglas se
 * prueban sin levantar contexto ni base de datos.
 * <p>
 * Regla de redaccion de los mensajes: <b>cada uno nombra la variable que hay
 * que corregir</b>. Un arranque que falla diciendo "configuracion invalida" se
 * acaba resolviendo desactivando la comprobacion.
 */
public final class ValidadorConfiguracionSeguridad {

    /** Longitud minima del secreto de firma (misma regla que TokenService). */
    public static final int LONGITUD_MINIMA_SECRETO = 32;

    /** Contrasena por defecto del compose de desarrollo (H-17). */
    static final String CONTRASENA_BD_POR_DEFECTO = "controllocal";

    /**
     * Los valores del MinIO del compose. Estan publicados en el repositorio a
     * proposito —ese servicio solo escucha en localhost— y por eso mismo
     * llegar con ellos a produccion es exactamente el fallo que este validador
     * existe para impedir.
     */
    static final String BUCKET_DE_DESARROLLO = "controllocal-dev";
    static final String ACCESS_KEY_DE_DESARROLLO = "controllocal";
    static final String SECRET_KEY_DE_DESARROLLO = "controllocal-dev-2026";

    /**
     * Foto del entorno en el instante del arranque. Todos los campos son
     * hechos ya resueltos: el validador no consulta nada.
     *
     * @param usandoFallbackDeSecreto   el TokenService cayo al secreto de desarrollo
     * @param longitudSecreto           longitud del secreto configurado (0 si no hay)
     * @param corsOrigenes              valor de controllocal.cors.origenes
     * @param swaggerHabilitado         springdoc expone /v3/api-docs y swagger-ui
     * @param urlBaseDatos              spring.datasource.url
     * @param contrasenaBaseDatos       spring.datasource.password
     * @param proveedorAlmacen          controllocal.almacen.proveedor (DISCO | S3)
     * @param directorioAlmacen         controllocal.almacen.directorio
     * @param almacenPersistenteYEscribible el directorio existe, es escribible y no es relativo
     * @param endpointS3                controllocal.almacen.s3.endpoint (vacio = el S3 de AWS)
     * @param bucketS3                  controllocal.almacen.s3.bucket
     * @param accessKeyS3               controllocal.almacen.s3.access-key (vacio = cadena por defecto)
     * @param secretKeyS3               controllocal.almacen.s3.secret-key
     * @param credencialesConHashDeSeed  cuantas credenciales conservan un hash del seed publicado
     * @param credencialesConHashCompartido cuantas comparten hash con otra cuenta
     * @param organizacionesSinAdministrador cuantas organizaciones se quedaron sin gobierno
     */
    public record Entorno(
            boolean usandoFallbackDeSecreto,
            int longitudSecreto,
            String corsOrigenes,
            boolean swaggerHabilitado,
            String urlBaseDatos,
            String contrasenaBaseDatos,
            String proveedorAlmacen,
            String directorioAlmacen,
            boolean almacenPersistenteYEscribible,
            String endpointS3,
            String bucketS3,
            String accessKeyS3,
            String secretKeyS3,
            long credencialesConHashDeSeed,
            long credencialesConHashCompartido,
            long organizacionesSinAdministrador,
            boolean usandoFallbackDeClaveMfa,
            boolean recuperacionEmergenciaHabilitada,
            boolean custodiosConfigurados,
            boolean notificadorExternoConfigurado) {

        /**
         * Entorno <b>sin almacen S3</b>: proveedor {@code DISCO} y los cuatro
         * ajustes del bucket vacios.
         *
         * <p>No es azucar para los tests: es la misma afirmacion que hace
         * {@code AlmacenDisco} con su {@code matchIfMissing = true} — <b>quien
         * no configura almacenamiento tiene disco</b>—, escrita una sola vez.
         * Sin esto, cada sitio que construya un entorno tendria que acordarse
         * de pasar "DISCO" y cuatro cadenas vacias, y el dia que a alguien se
         * le olvide el validador leeria {@code null} y diria que el proveedor
         * no es valido.
         */
        public Entorno(
                boolean usandoFallbackDeSecreto,
                int longitudSecreto,
                String corsOrigenes,
                boolean swaggerHabilitado,
                String urlBaseDatos,
                String contrasenaBaseDatos,
                String directorioAlmacen,
                boolean almacenPersistenteYEscribible,
                long credencialesConHashDeSeed,
                long credencialesConHashCompartido,
                long organizacionesSinAdministrador,
                boolean usandoFallbackDeClaveMfa,
                boolean recuperacionEmergenciaHabilitada,
                boolean custodiosConfigurados,
                boolean notificadorExternoConfigurado) {
            this(usandoFallbackDeSecreto, longitudSecreto, corsOrigenes, swaggerHabilitado,
                    urlBaseDatos, contrasenaBaseDatos, "DISCO", directorioAlmacen,
                    almacenPersistenteYEscribible, "", "", "", "",
                    credencialesConHashDeSeed, credencialesConHashCompartido,
                    organizacionesSinAdministrador, usandoFallbackDeClaveMfa,
                    recuperacionEmergenciaHabilitada, custodiosConfigurados,
                    notificadorExternoConfigurado);
        }
    }

    /**
     * @return lista vacia si el entorno es apto para produccion; si no, un
     *         problema por linea, cada uno nombrando su variable.
     */
    public List<String> problemas(Entorno e) {
        List<String> problemas = new ArrayList<>();

        // --- H-01: el secreto de firma -----------------------------------
        if (e.usandoFallbackDeSecreto()) {
            problemas.add("API_TOKEN_SECRET: ausente, mas corto de "
                    + LONGITUD_MINIMA_SECRETO + " caracteres o igual al secreto de desarrollo. "
                    + "En produccion el token se firmaria con un secreto publicado en el repositorio, "
                    + "asi que cualquiera podria fabricar un token ADMIN valido. "
                    + "Genere uno con: openssl rand -base64 48");
        } else if (e.longitudSecreto() < LONGITUD_MINIMA_SECRETO) {
            problemas.add("API_TOKEN_SECRET: tiene " + e.longitudSecreto()
                    + " caracteres y el minimo es " + LONGITUD_MINIMA_SECRETO + ".");
        }

        // --- V37: la clave que cifra los secretos TOTP --------------------
        // Perderla no filtra nada: deja a TODOS los administradores sin
        // segundo factor. Es un fallo de DISPONIBILIDAD, y por eso arrancar
        // en produccion con la clave de desarrollo es tan grave como hacerlo
        // con el secreto del token: el dia que se rote de verdad, ninguno de
        // los factores existentes se podra descifrar.
        if (e.usandoFallbackDeClaveMfa()) {
            problemas.add("MFA_CLAVE_CIFRADO: ausente, mas corta de 32 caracteres o igual "
                    + "a la clave de desarrollo. Los secretos TOTP quedarian cifrados con "
                    + "una clave publicada en el repositorio. Genere una con: "
                    + "openssl rand -base64 48 (y respaldela CIFRADA y APARTE del dump).");
        }

        // --- CORS: superficie de desarrollo abierta -----------------------
        String cors = e.corsOrigenes() == null ? "" : e.corsOrigenes().trim();
        if (cors.isEmpty()) {
            problemas.add("CORS_ORIGENES: vacio. Declare el origen exacto del SPA.");
        } else if (cors.contains("*")) {
            problemas.add("CORS_ORIGENES: contiene '*'. Un comodin en produccion permite que "
                    + "cualquier sitio hable con el API desde el navegador de un usuario autenticado.");
        } else if (cors.contains("localhost") || cors.contains("127.0.0.1")) {
            problemas.add("CORS_ORIGENES: apunta a localhost ('" + cors
                    + "'). Es configuracion de desarrollo.");
        }

        // --- H-13: documentacion del contrato publica ---------------------
        if (e.swaggerHabilitado()) {
            problemas.add("SWAGGER_HABILITADO: Swagger UI y /v3/api-docs quedarian accesibles. "
                    + "Publican el contrato completo del API sin token. Pongalo en false.");
        }

        // --- H-17 y valores de desarrollo en prod -------------------------
        if (CONTRASENA_BD_POR_DEFECTO.equals(e.contrasenaBaseDatos())) {
            problemas.add("DB_PASSWORD: es la contrasena por defecto del compose de desarrollo "
                    + "('" + CONTRASENA_BD_POR_DEFECTO + "').");
        }
        String url = e.urlBaseDatos() == null ? "" : e.urlBaseDatos();
        if (url.isBlank()) {
            problemas.add("DB_URL: no configurada.");
        } else if (url.contains("localhost") || url.contains("127.0.0.1")) {
            problemas.add("DB_URL: apunta a localhost ('" + url + "'). "
                    + "Es la base de la maquina del desarrollador, no la productiva.");
        }

        // --- Almacen: primero QUE proveedor, y despues lo que ese exige ----
        // Desde el Bloque 8 hay dos, asi que las comprobaciones dejan de ser
        // una lista fija: exigir ALMACEN_DIR con S3 haria que el operador
        // inventara una ruta que nadie lee, y no exigir nada con S3 dejaria
        // pasar un despliegue sin bucket.
        String proveedor = e.proveedorAlmacen() == null ? "" : e.proveedorAlmacen().trim().toUpperCase();
        if (!proveedor.equals("DISCO") && !proveedor.equals("S3")) {
            problemas.add("ALMACEN_PROVEEDOR: '" + e.proveedorAlmacen()
                    + "' no es un proveedor valido. Los unicos son DISCO y S3.");
        } else if (proveedor.equals("DISCO")) {
            problemas.addAll(problemasDeDisco(e));
        } else {
            problemas.addAll(problemasDeS3(e));
        }

        // --- H-03: credenciales conocidas ---------------------------------
        if (e.credencialesConHashDeSeed() > 0) {
            problemas.add("Credenciales del seed activas: " + e.credencialesConHashDeSeed()
                    + " cuenta(s) conservan un hash publicado en el repositorio "
                    + "(Admin2026 / Broker2026 / Agente2026). "
                    + "Falta aplicar V900__neutraliza_credenciales_semilla.sql "
                    + "(location db/migration-prod, solo perfil prod).");
        }
        if (e.credencialesConHashCompartido() > 0) {
            problemas.add("Contrasenas compartidas: " + e.credencialesConHashCompartido()
                    + " cuenta(s) usan un hash que tambien usa otra cuenta. "
                    + "Una contrasena compartida no identifica a nadie y no se puede auditar.");
        }

        // --- Invariante de gobierno ---------------------------------------
        if (e.organizacionesSinAdministrador() > 0) {
            problemas.add("Gobierno: " + e.organizacionesSinAdministrador()
                    + " organizacion(es) sin administrador activo. "
                    + "Quedarian sin quien administre cuentas y sin salida salvo SQL directo.");
        }

        // --- V38: la recuperacion de emergencia --------------------------
        // Se comprueba solo si esta ENCENDIDA: apagada no es un problema, es
        // el estado normal de una instalacion que no la necesita.
        if (e.recuperacionEmergenciaHabilitada()) {
            if (!e.custodiosConfigurados()) {
                problemas.add("RECUPERACION_EMERGENCIA_HABILITADA: encendida sin los dos "
                        + "custodios configurados (RECUPERACION_CUSTODIO_A_ID/HASH y _B_ID/HASH, "
                        + "con identificadores distintos). Sin dos hashes reales la doble "
                        + "aprobacion es decorativa: cualquiera que llegue al conector local "
                        + "podria reponer gobierno.");
            }
            if (!e.notificadorExternoConfigurado()) {
                problemas.add("RECUPERACION_EMERGENCIA_HABILITADA: encendida sin canal externo "
                        + "de aviso. Es una condicion tecnica, no documental: una recuperacion "
                        + "de emergencia puede usarse PRECISAMENTE cuando nadie esta dentro de "
                        + "la aplicacion para ver la campana, y entonces el unico registro que "
                        + "alguien leeria a tiempo es el que sale fuera.");
            }
        }

        return problemas;
    }

    /** Lo que exige el proveedor DISCO: un volumen de verdad, no la capa del contenedor. */
    private static List<String> problemasDeDisco(Entorno e) {
        List<String> problemas = new ArrayList<>();
        String almacen = e.directorioAlmacen() == null ? "" : e.directorioAlmacen().trim();
        if (almacen.isEmpty()) {
            problemas.add("ALMACEN_DIR: no configurado con ALMACEN_PROVEEDOR=DISCO. Sin el, los "
                    + "documentos y las fotos caerian en la capa de escritura del contenedor y se "
                    + "perderian al recrearlo.");
        } else if (almacen.startsWith(".")) {
            problemas.add("ALMACEN_DIR: es una ruta relativa ('" + almacen
                    + "'). En produccion debe ser una ruta absoluta sobre un volumen persistente.");
        } else if (!e.almacenPersistenteYEscribible()) {
            problemas.add("ALMACEN_DIR: '" + almacen
                    + "' no existe o no es escribible por el proceso del API.");
        }
        return problemas;
    }

    /**
     * Lo que exige el proveedor S3.
     *
     * <p>Las credenciales <b>vacias no son un problema</b>: significan que las
     * pone el entorno por la cadena por defecto del SDK, que es como se usa un
     * rol IAM y es la mejor de las opciones — una credencial que no esta escrita
     * en ningun sitio no se puede filtrar—. Lo que si es un problema es que
     * sean las <b>publicadas del compose de desarrollo</b>.
     *
     * <p>El endpoint sin TLS se rechaza porque por ahi viajan los documentos de
     * identidad y los contratos de los clientes. La excepcion es el endpoint
     * vacio, que significa el S3 de AWS y ya es HTTPS por definicion.
     */
    private static List<String> problemasDeS3(Entorno e) {
        List<String> problemas = new ArrayList<>();

        String bucket = e.bucketS3() == null ? "" : e.bucketS3().trim();
        if (bucket.isEmpty()) {
            problemas.add("ALMACEN_S3_BUCKET: no configurado con ALMACEN_PROVEEDOR=S3. "
                    + "No hay donde guardar los binarios.");
        } else if (bucket.equals(BUCKET_DE_DESARROLLO)) {
            problemas.add("ALMACEN_S3_BUCKET: es '" + BUCKET_DE_DESARROLLO
                    + "', el del compose de desarrollo. Declare el bucket productivo.");
        }

        String endpoint = e.endpointS3() == null ? "" : e.endpointS3().trim();
        if (!endpoint.isEmpty()) {
            if (endpoint.startsWith("http://")) {
                problemas.add("ALMACEN_S3_ENDPOINT: usa http sin cifrar ('" + endpoint
                        + "'). Por ese canal viajan documentos de identidad y contratos de "
                        + "clientes; en produccion tiene que ser https.");
            }
            if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
                problemas.add("ALMACEN_S3_ENDPOINT: apunta a localhost ('" + endpoint
                        + "'). Es el MinIO de desarrollo, no un almacen productivo.");
            }
        }

        String accessKey = e.accessKeyS3() == null ? "" : e.accessKeyS3().trim();
        String secretKey = e.secretKeyS3() == null ? "" : e.secretKeyS3().trim();
        if (accessKey.equals(ACCESS_KEY_DE_DESARROLLO) || secretKey.equals(SECRET_KEY_DE_DESARROLLO)) {
            problemas.add("ALMACEN_S3_ACCESS_KEY/SECRET_KEY: son las credenciales publicadas en "
                    + "el compose de desarrollo. Cualquiera que lea el repositorio entraria al "
                    + "bucket. Use un usuario propio, o dejelas vacias para delegar en el rol "
                    + "del entorno.");
        }
        if (accessKey.isEmpty() != secretKey.isEmpty()) {
            problemas.add("ALMACEN_S3_ACCESS_KEY/SECRET_KEY: solo una de las dos esta puesta. "
                    + "O ambas, o ninguna (y entonces manda la cadena de credenciales del SDK).");
        }

        return problemas;
    }

    /** Mensaje unico y accionable para detener el arranque. */
    public String mensajeDeFallo(List<String> problemas) {
        StringBuilder sb = new StringBuilder();
        sb.append("El perfil 'prod' esta activo pero la configuracion de seguridad NO es apta ")
                .append("para produccion. Se encontraron ").append(problemas.size())
                .append(" problema(s):");
        for (int i = 0; i < problemas.size(); i++) {
            sb.append("\n  ").append(i + 1).append(") ").append(problemas.get(i));
        }
        sb.append("\n\nEl arranque se detiene a proposito (D-S0-20): es preferible no levantar ")
                .append("a levantar inseguro y en silencio.");
        return sb.toString();
    }
}
