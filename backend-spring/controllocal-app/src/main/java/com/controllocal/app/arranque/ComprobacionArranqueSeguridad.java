package com.controllocal.app.arranque;

import com.controllocal.service.soporte.CifradoSecretos;
import com.controllocal.web.seguridad.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Toma la foto del entorno y aplica {@link ValidadorConfiguracionSeguridad}.
 * <p>
 * En perfil <b>prod</b>: si hay algun problema, <b>lanza y detiene el arranque</b>
 * (D-S0-20). En <b>dev</b> y <b>test</b> no bloquea nada, pero avisa con un WARN
 * si se esta firmando con el secreto de desarrollo: el fallback sobrevive, pero
 * deja de ser silencioso (D-S0-1, cierra H-01).
 * <p>
 * Se engancha a {@code ApplicationReadyEvent} y no a {@code @PostConstruct}
 * porque necesita el DataSource ya inicializado y Flyway ya ejecutado: dos de
 * las comprobaciones consultan la base.
 */
@Component
public class ComprobacionArranqueSeguridad implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ComprobacionArranqueSeguridad.class);

    /**
     * Los tres hashes PBKDF2 publicados en V3__seed_identidad_base.sql
     * (Admin2026 / Broker2026 / Agente2026). Estan aqui porque la comprobacion
     * necesita reconocerlos; no son un secreto: llevan versionados desde V3, y
     * ese es justamente el problema que esta comprobacion detecta (H-03).
     */
    private static final List<String> HASHES_DEL_SEED = List.of(
            "pbkdf2$100000$3263AxQO/Xv2FaBwgkx4Hg==$WeNCBs11RBxwNeMfKQwl0cPGKBCHOjAvJP1AlDDiybw=",
            "pbkdf2$100000$Kj4WmHhqD//I1lJcBwFdqw==$7FFyOcNgYST6eqyaEz7MEHZg57rlowX6o5Yu2YBbFN8=",
            "pbkdf2$100000$uy2GnOLWMudcyeMG7pKhjA==$3twwP9cAqG+ykRGAx5BmI8ZTAPa3w2dcwviW8dqvDdE=");

    private final Environment entorno;
    private final TokenService tokenService;
    private final CifradoSecretos cifradoMfa;
    private final com.controllocal.service.soporte.CustodiosConfigurados custodios;
    private final JdbcTemplate jdbc;
    private final ValidadorConfiguracionSeguridad validador = new ValidadorConfiguracionSeguridad();

    @Value("${controllocal.token.secreto:}")
    private String secretoConfigurado;

    @Value("${controllocal.cors.origenes:}")
    private String corsOrigenes;

    @Value("${controllocal.almacen.directorio:}")
    private String directorioAlmacen;

    @Value("${controllocal.almacen.proveedor:DISCO}")
    private String proveedorAlmacen;

    @Value("${controllocal.almacen.s3.endpoint:}")
    private String endpointS3;

    @Value("${controllocal.almacen.s3.bucket:}")
    private String bucketS3;

    @Value("${controllocal.almacen.s3.access-key:}")
    private String accessKeyS3;

    @Value("${controllocal.almacen.s3.secret-key:}")
    private String secretKeyS3;

    @Value("${controllocal.recuperacion.habilitada:false}")
    private boolean recuperacionHabilitada;

    @Value("${springdoc.api-docs.enabled:true}")
    private boolean swaggerHabilitado;

    public ComprobacionArranqueSeguridad(Environment entorno, TokenService tokenService,
                                         CifradoSecretos cifradoMfa,
                                         com.controllocal.service.soporte.CustodiosConfigurados custodios,
                                         JdbcTemplate jdbc) {
        this.entorno = entorno;
        this.tokenService = tokenService;
        this.cifradoMfa = cifradoMfa;
        this.custodios = custodios;
        this.jdbc = jdbc;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent evento) {
        boolean produccion = entorno.matchesProfiles("prod");

        if (!produccion) {
            if (tokenService.usandoFallbackDeDesarrollo()) {
                log.warn("""
                        =====================================================================
                        API_TOKEN_SECRET no esta configurado (o es demasiado corto, o es el
                        literal de desarrollo): se esta firmando con el SECRETO FIJO DE
                        DESARROLLO, publicado en el repositorio y compartido con GlassFish.
                        Vale para dev; en produccion el arranque se detendria.
                        =====================================================================""");
            }
            return;
        }

        List<String> problemas = validador.problemas(fotoDelEntorno());
        if (!problemas.isEmpty()) {
            throw new IllegalStateException(validador.mensajeDeFallo(problemas));
        }
        log.info("Configuracion de seguridad validada para el perfil prod: sin hallazgos.");
    }

    private ValidadorConfiguracionSeguridad.Entorno fotoDelEntorno() {
        return new ValidadorConfiguracionSeguridad.Entorno(
                tokenService.usandoFallbackDeDesarrollo(),
                secretoConfigurado == null ? 0 : secretoConfigurado.length(),
                corsOrigenes,
                swaggerHabilitado,
                entorno.getProperty("spring.datasource.url", ""),
                entorno.getProperty("spring.datasource.password", ""),
                proveedorAlmacen,
                directorioAlmacen,
                almacenEscribible(directorioAlmacen),
                endpointS3,
                bucketS3,
                accessKeyS3,
                secretKeyS3,
                contar("""
                        SELECT count(*) FROM credencial_usuario
                         WHERE contrasena_hash IN (?, ?, ?)""",
                        HASHES_DEL_SEED.get(0), HASHES_DEL_SEED.get(1), HASHES_DEL_SEED.get(2)),
                contar("""
                        SELECT coalesce(sum(repetidas), 0) FROM (
                            SELECT count(*) AS repetidas
                              FROM credencial_usuario
                             WHERE contrasena_hash LIKE 'pbkdf2$%'
                             GROUP BY contrasena_hash
                            HAVING count(*) > 1
                        ) compartidos"""),
                // Gobierno por MEMBRESIA (D-S0-8), no por el booleano heredado:
                // desde V33 un TENANT_ADMIN puede no tener detalle_broker, y
                // varios pueden convivir. Solo se exige a las organizaciones
                // que tienen cuentas: una recien creada todavia no gobierna
                // nada, igual que en el trigger de V34.
                contar("""
                        SELECT count(*) FROM organizacion o
                         WHERE o.estado = 'A'
                           AND EXISTS (
                               SELECT 1 FROM usuario_organizacion uo
                                WHERE uo.organizacion_id = o.id_organizacion
                                  AND uo.estado = 'A')
                           AND NOT EXISTS (
                               SELECT 1 FROM usuario_organizacion uo
                                WHERE uo.organizacion_id = o.id_organizacion
                                  AND uo.estado = 'A'
                                  AND uo.rol = 'TENANT_ADMIN')"""),
                cifradoMfa.usandoFallbackDeDesarrollo(),
                recuperacionHabilitada,
                custodios.estanConfigurados(),
                // Todavia NO hay notificador externo construido, asi que esto
                // es `false` siempre y encender la bandera en prod detiene el
                // arranque. Es lo correcto: es una condicion tecnica, no una
                // nota en un documento (§17.9).
                notificadorExternoConfigurado());
    }

    /**
     * Canal externo de aviso. Devuelve {@code false} mientras no exista uno:
     * ninguna instalacion puede encender la recuperacion de emergencia en
     * produccion hasta que se construya.
     */
    private boolean notificadorExternoConfigurado() {
        return !entorno.getProperty("controllocal.notificaciones.externo.url", "").isBlank();
    }

    /**
     * El directorio del almacen tiene que existir y admitir escritura ANTES de
     * la primera subida: si se descubre al guardar el primer documento, ya se
     * perdio la subida del usuario.
     */
    private boolean almacenEscribible(String directorio) {
        if (directorio == null || directorio.isBlank()) {
            return false;
        }
        try {
            Path raiz = Path.of(directorio);
            return Files.isDirectory(raiz) && Files.isWritable(raiz);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Una consulta que falla NO puede dar por buena la comprobacion: si no se
     * puede verificar, se cuenta como hallazgo (devuelve 1) y el arranque se
     * detiene con el resto de problemas a la vista.
     */
    private long contar(String sql, Object... parametros) {
        try {
            Long valor = jdbc.queryForObject(sql, Long.class, parametros);
            return valor == null ? 0L : valor;
        } catch (RuntimeException e) {
            log.error("No se pudo ejecutar una comprobacion de arranque: {}", e.getMessage());
            return 1L;
        }
    }
}
