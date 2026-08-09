package com.controllocal.web.seguridad;

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
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * JWT HS256 con el MISMO formato, claims y secreto de fallback que el
 * TokenService del backend Jakarta: durante la convivencia del Strangler un
 * token emitido por cualquiera de los dos backends valida en el otro
 * (SSO compartido, Doc 5 §10). No cambiar el formato hasta el corte final.
 */
@Component
public final class TokenService {

    /**
     * @param emitidoEn el claim {@code iat}, con precision de SEGUNDO porque es
     *                  como viaja en el token. Se expone —cambio interno, el
     *                  cable no cambia— para poder invalidar sesiones
     *                  comparandolo contra {@code sesiones_invalidas_desde}
     *                  (D-S0-12). Un token sin {@code iat} se lee como
     *                  {@link Instant#EPOCH}: falla del lado seguro, porque
     *                  cualquier invalidacion posterior lo mata.
     */
    public record Sesion(String usuario, String rol, long idUsuario, long idDominio,
                         Instant emitidoEn, Instant expiraEn) {
    }

    public static final long DURACION_SEGUNDOS = 30 * 60;
    private static final Set<String> ROLES = Set.of("AGENTE", "BROKER", "ADMIN");
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

    public Sesion emitir(String usuario, String rol, long idUsuario, long idDominio) {
        if (usuario == null || usuario.isBlank() || !ROLES.contains(rol)
                || idUsuario <= 0 || idDominio <= 0) {
            throw new IllegalArgumentException("No se puede emitir un token con datos de sesion invalidos.");
        }
        // Truncado a segundo: es la precision con la que `iat` viaja en el
        // token, y guardarlo aqui con mas resolucion haria que el objeto y el
        // JWT firmado dijeran cosas distintas.
        Instant emitido = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new Sesion(usuario, rol, idUsuario, idDominio,
                emitido, emitido.plusSeconds(DURACION_SEGUNDOS));
    }

    public String firmar(Sesion sesion) {
        ObjectNode carga = json.createObjectNode();
        carga.put("sub", sesion.usuario());
        carga.put("rol", sesion.rol());
        carga.put("idUsuario", sesion.idUsuario());
        carga.put("idDominio", sesion.idDominio());
        // El `iat` sale de la sesion y NO de un Instant.now() nuevo: firmar un
        // instante distinto del que lleva el objeto abriria una ventana en la
        // que el token no coincide consigo mismo. El valor emitido es el mismo
        // de siempre, asi que el cable no cambia.
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
            if (!carga.hasNonNull("exp") || !carga.hasNonNull("rol")
                    || !carga.hasNonNull("idUsuario") || !carga.hasNonNull("idDominio")) {
                return Optional.empty();
            }

            String usuario = carga.path("sub").asText("");
            String rol = carga.path("rol").asText();
            long idUsuario = carga.path("idUsuario").asLong();
            long idDominio = carga.path("idDominio").asLong();
            Instant expira = Instant.ofEpochSecond(carga.path("exp").asLong());
            // `iat` no se exige presente para no romper el SSO si el otro
            // backend lo omitiera alguna vez. Ausente = EPOCH, que es lo mismo
            // que "emitido hace siempre": cualquier invalidacion lo mata.
            Instant emitido = carga.hasNonNull("iat")
                    ? Instant.ofEpochSecond(carga.path("iat").asLong())
                    : Instant.EPOCH;

            if (usuario.isBlank() || !ROLES.contains(rol) || idUsuario <= 0 || idDominio <= 0
                    || !Instant.now().isBefore(expira)) {
                return Optional.empty();
            }
            return Optional.of(new Sesion(usuario, rol, idUsuario, idDominio, emitido, expira));
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
