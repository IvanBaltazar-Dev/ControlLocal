package com.controllocal.rest.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.controllocal.rest.util.JsonUtils;

public final class TokenService {

    public record Sesion(String usuario, String rol, long idUsuario, long idDominio, Instant expiraEn) {
    }

    public static final long DURACION_SEGUNDOS = 30 * 60;
    private static final Set<String> ROLES = Set.of("AGENTE", "BROKER", "ADMIN");
    private static final String CABECERA = codificar("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    private static final byte[] SECRETO = cargarSecreto();

    public Sesion emitir(String usuario, String rol, long idUsuario, long idDominio) {
        if (usuario == null || usuario.isBlank() || !ROLES.contains(rol)
                || idUsuario <= 0 || idDominio <= 0) {
            throw new IllegalArgumentException("No se puede emitir un token con datos de sesion invalidos.");
        }
        return new Sesion(usuario, rol, idUsuario, idDominio,
                Instant.now().plusSeconds(DURACION_SEGUNDOS));
    }

    public String firmar(Sesion sesion) {
        ObjectNode carga = JsonUtils.mapper().createObjectNode();
        carga.put("sub", sesion.usuario());
        carga.put("rol", sesion.rol());
        carga.put("idUsuario", sesion.idUsuario());
        carga.put("idDominio", sesion.idDominio());
        carga.put("iat", Instant.now().getEpochSecond());
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
            JsonNode cabecera = JsonUtils.mapper().readTree(decodificar(partes[0]));
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

            JsonNode carga = JsonUtils.mapper().readTree(decodificar(partes[1]));
            if (!carga.hasNonNull("exp") || !carga.hasNonNull("rol")
                    || !carga.hasNonNull("idUsuario") || !carga.hasNonNull("idDominio")) {
                return Optional.empty();
            }

            String usuario = carga.path("sub").asText();
            String rol = carga.path("rol").asText();
            long idUsuario = carga.path("idUsuario").asLong();
            long idDominio = carga.path("idDominio").asLong();
            Instant expira = Instant.ofEpochSecond(carga.path("exp").asLong());

            if (usuario.isBlank() || !ROLES.contains(rol) || idUsuario <= 0 || idDominio <= 0
                    || !Instant.now().isBefore(expira)) {
                return Optional.empty();
            }
            return Optional.of(new Sesion(usuario, rol, idUsuario, idDominio, expira));
        } catch (Exception error) {
            return Optional.empty();
        }
    }

    private static byte[] cargarSecreto() {
        String configurado = ApiConfig.get("api.token.secret", "API_TOKEN_SECRET", "");
        if (configurado != null && configurado.length() >= 32) {
            return configurado.getBytes(StandardCharsets.UTF_8);
        }
        if (Entorno.esProduccion()) {
            throw new IllegalStateException(
                    "API_TOKEN_SECRET es obligatorio en produccion y debe tener al menos 32 caracteres.");
        }

        byte[] temporal = new byte[32];
        new SecureRandom().nextBytes(temporal);
        System.err.println("[ControlLocal API] API_TOKEN_SECRET no configurado; se usa un secreto temporal de desarrollo.");
        return temporal;
    }

    private String firma(String contenido) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRETO, "HmacSHA256"));
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
