package com.controllocal.web.seguridad;

import com.controllocal.service.ContextosService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/** JWT V2 HS256 ligado a una sesión durable y revisión contextual de Core. */
@Component
public final class TokenService {

    /** La emisión y expiración tienen precisión de segundo, como la sesión Core. */
    public record Sesion(String usuario, String rol, long idUsuario, long idDominio,
                         Instant emitidoEn, Instant expiraEn, String referenciaSesion, long revisionContexto) {
    }

    public static final long DURACION_SEGUNDOS = ContextosService.DURACION_SEGUNDOS;
    private static final Set<String> ROLES = Set.of("AGENTE", "BROKER", "TENANT_ADMIN", "IDENTIDAD");
    private static final String CABECERA = codificar("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

    // Secreto fijo de desarrollo, IDENTICO al del backend Jakarta para que los
    // tokens crucen entre ambos mientras convivan. En despliegue se configura
    // controllocal.token.secreto (>= 32 caracteres).
    private static final String SECRETO_DEV = "ControlLocal-dev-fallback-token-secret-0001";

    private final byte[] secreto;
    private final boolean usandoFallback;
    private final ObjectMapper json = new ObjectMapper();

    public TokenService(@Value("${controllocal.token.secreto:}") String configurado) {
        boolean configuradoValido = configurado != null && configurado.length() >= 32
                && !esFallbackDeDesarrollo(configurado);
        this.usandoFallback = !configuradoValido;
        this.secreto = configuradoValido
                ? configurado.getBytes(StandardCharsets.UTF_8)
                : SECRETO_DEV.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * true si esta instancia esta firmando con el secreto fijo de desarrollo,
     * sea porque no se configuro ninguno, porque es demasiado corto o porque
     * alguien copio el literal de desarrollo en la variable de entorno.
     * <p>
     * Lo consulta el validador de arranque (D-S0-1/D-S0-2): en perfil prod
     * esto detiene el contexto; en dev solo emite un WARN. El secreto no sale
     * de esta clase: el resto del sistema pregunta, no compara literales.
     */
    public boolean usandoFallbackDeDesarrollo() {
        return usandoFallback;
    }

    /** Comparacion en tiempo constante contra el literal de desarrollo. */
    public static boolean esFallbackDeDesarrollo(String candidato) {
        if (candidato == null) {
            return false;
        }
        return MessageDigest.isEqual(candidato.getBytes(StandardCharsets.UTF_8),
                SECRETO_DEV.getBytes(StandardCharsets.UTF_8));
    }

    /** Firma la sesión durable creada por Core; el JWT no elige pertenencia ni rol. */
    public Sesion de(ContextosService.SesionAccesoDTO sesion) {
        var c = sesion.activo();
        return new Sesion(sesion.identidad().nombreUsuario(), c == null ? "IDENTIDAD" : c.rol(),
                c == null ? 0 : c.idPersona(),
                c == null ? 0 : c.idRolOperativo(), sesion.emitidaEn(), sesion.venceEn(),
                sesion.referencia(), sesion.revision());
    }

    public String firmar(Sesion sesion) {
        if (sesion.referenciaSesion() == null || sesion.referenciaSesion().isBlank()
                || sesion.revisionContexto() < 0) throw new IllegalArgumentException("Se requiere la sesión Core.");
        ObjectNode carga = json.createObjectNode();
        carga.put("v", 2);
        carga.put("sid", sesion.referenciaSesion());
        carga.put("ctx", sesion.revisionContexto());
        carga.put("sub", sesion.usuario());
        carga.put("rol", sesion.rol());
        carga.put("idUsuario", sesion.idUsuario());
        carga.put("idDominio", sesion.idDominio());
        carga.put("iat", sesion.emitidoEn().getEpochSecond());
        carga.put("exp", sesion.expiraEn().getEpochSecond());

        String cargaCodificada = codificar(carga.toString());
        String contenidoFirmado = CABECERA + "." + cargaCodificada;
        return contenidoFirmado + "." + firma(contenidoFirmado);
    }

    public Optional<Sesion> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String[] partes = token.split("\\.", -1);
        if (partes.length != 3 || partes[0].isBlank() || partes[1].isBlank() || partes[2].isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode cabecera = json.readTree(decodificar(partes[0]));
            if (!"HS256".equals(cabecera.path("alg").asText())
                    || !"JWT".equals(cabecera.path("typ").asText())) {
                return Optional.empty();
            }

            String contenidoFirmado = partes[0] + "." + partes[1];
            byte[] esperada = firma(contenidoFirmado).getBytes(StandardCharsets.US_ASCII);
            byte[] recibida = partes[2].getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(esperada, recibida)) {
                return Optional.empty();
            }

            JsonNode carga = json.readTree(decodificar(partes[1]));
            if (carga.path("v").asInt() != 2 || !carga.hasNonNull("sid") || !carga.hasNonNull("ctx")
                    || !carga.hasNonNull("iat") || !carga.hasNonNull("exp") || !carga.hasNonNull("rol")
                    || !carga.hasNonNull("idUsuario") || !carga.hasNonNull("idDominio")) {
                return Optional.empty();
            }

            String usuario = carga.path("sub").asText("");
            String rol = carga.path("rol").asText();
            long idUsuario = carga.path("idUsuario").asLong();
            long idDominio = carga.path("idDominio").asLong();
            String referenciaSesion = carga.path("sid").asText();
            long revision = carga.path("ctx").asLong(-1);
            Instant expira = Instant.ofEpochSecond(carga.path("exp").asLong());
            Instant emitido = Instant.ofEpochSecond(carga.path("iat").asLong());

            if (usuario.isBlank() || !ROLES.contains(rol)
                    || ("IDENTIDAD".equals(rol) ? (idUsuario != 0 || idDominio != 0) : (idUsuario <= 0 || idDominio <= 0))
                    || !referenciaSesion.matches("[A-Za-z0-9_-]{43}") || revision < 0
                    || !Instant.now().isBefore(expira) || !emitido.isBefore(expira)) {
                return Optional.empty();
            }
            return Optional.of(new Sesion(usuario, rol, idUsuario, idDominio, emitido, expira, referenciaSesion, revision));
        } catch (Exception error) {
            return Optional.empty();
        }
    }

    private String firma(String contenido) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(contenido.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("No se pudo firmar el token.", error);
        }
    }

    private static String codificar(String valor) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodificar(String valor) {
        return new String(Base64.getUrlDecoder().decode(valor), StandardCharsets.UTF_8);
    }
}
