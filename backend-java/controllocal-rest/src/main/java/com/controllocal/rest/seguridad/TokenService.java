package com.controllocal.rest.seguridad;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

public final class TokenService {

    public record Sesion(String usuario, String rol, long idUsuario, long idDominio, Instant expiraEn) {
    }

    public static final long DURACION_SEGUNDOS = 30 * 60;
    private static final Set<String> ROLES = Set.of("AGENTE", "BROKER", "ADMIN");
    private static final String CABECERA = codificar(Json.createObjectBuilder()
            .add("alg", "HS256")
            .add("typ", "JWT")
            .build()
            .toString());
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
        JsonObject carga = Json.createObjectBuilder()
                .add("sub", sesion.usuario())
                .add("rol", sesion.rol())
                .add("idUsuario", sesion.idUsuario())
                .add("idDominio", sesion.idDominio())
                .add("iat", Instant.now().getEpochSecond())
                .add("exp", sesion.expiraEn().getEpochSecond())
                .build();

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
            JsonObject cabecera = leerJson(partes[0]);
            if (!"HS256".equals(cabecera.getString("alg", ""))
                    || !"JWT".equals(cabecera.getString("typ", ""))) {
                return Optional.empty();
            }

            String contenidoFirmado = partes[0] + "." + partes[1];
            byte[] esperada = firma(contenidoFirmado).getBytes(StandardCharsets.US_ASCII);
            byte[] recibida = partes[2].getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(esperada, recibida)) {
                return Optional.empty();
            }

            JsonObject carga = leerJson(partes[1]);
            if (!tieneValor(carga, "exp") || !tieneValor(carga, "rol")
                    || !tieneValor(carga, "idUsuario") || !tieneValor(carga, "idDominio")) {
                return Optional.empty();
            }

            String usuario = carga.getString("sub", "");
            String rol = carga.getString("rol");
            long idUsuario = carga.getJsonNumber("idUsuario").longValue();
            long idDominio = carga.getJsonNumber("idDominio").longValue();
            Instant expira = Instant.ofEpochSecond(carga.getJsonNumber("exp").longValue());

            if (usuario.isBlank() || !ROLES.contains(rol) || idUsuario <= 0 || idDominio <= 0
                    || !Instant.now().isBefore(expira)) {
                return Optional.empty();
            }
            return Optional.of(new Sesion(usuario, rol, idUsuario, idDominio, expira));
        } catch (Exception error) {
            return Optional.empty();
        }
    }

    private static JsonObject leerJson(String valorCodificado) {
        try (JsonReader reader = Json.createReader(new StringReader(decodificar(valorCodificado)))) {
            return reader.readObject();
        }
    }

    private static boolean tieneValor(JsonObject objeto, String nombre) {
        return objeto.containsKey(nombre) && !objeto.isNull(nombre);
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
