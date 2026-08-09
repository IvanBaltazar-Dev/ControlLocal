package com.controllocal.service.soporte;

import com.controllocal.domain.seguridad.EventoSeguridad;
import com.controllocal.persistence.repositorio.EventoSeguridadRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Auditoria de accesos y privilegios (Plan S0 §6.3). Un solo sitio por el que
 * se escribe {@code evento_seguridad}, por el mismo motivo por el que
 * {@code Transiciones} es el unico sitio que muta estados: las llamadas
 * dispersas se olvidan.
 *
 * <p><b>Cada evento se graba en su PROPIA transaccion</b>
 * ({@code REQUIRES_NEW}). Es deliberado y es lo que hace util a la tabla: un
 * login fallido debe quedar registrado <b>aunque</b> la operacion que lo
 * provoco termine lanzando, y un evento que se va con el rollback de lo que
 * intenta auditar no audita nada.
 *
 * <p><b>Regla de higiene (§6.3), verificada por test:</b> en {@code detalle}
 * no entran contrasenas, hashes, tokens ni secretos. No es una recomendacion:
 * las claves se filtran aqui y el test comprueba que la lista negra sigue
 * cubriendo lo que debe. Una auditoria que filtra secretos es un agujero con
 * sello de calidad.
 */
@Component
public class EventosSeguridad {

    /**
     * Fragmentos prohibidos en las CLAVES de {@code detalle}. Se comparan en
     * minusculas y por contencion: {@code "contrasenaNueva"} cae por
     * {@code "contrasena"}.
     */
    static final Set<String> CLAVES_PROHIBIDAS = Set.of(
            "contrasena", "password", "clave", "secreto", "secret",
            "hash", "token", "jwt", "authorization", "bearer",
            "totp", "mfa", "otp", "salt", "sal", "credencial", "cookie");

    /** Recorte defensivo: un detalle enorme es un log, no una auditoria. */
    private static final int MAXIMO_VALOR = 200;

    private final EventoSeguridadRepository eventos;

    public EventosSeguridad(EventoSeguridadRepository eventos) {
        this.eventos = eventos;
    }

    /**
     * Datos del actor y del transporte. Todo puede ser {@code null} salvo la
     * organizacion: un login fallido contra un usuario inexistente no tiene
     * persona ni credencial, y es justo el que mas interesa registrar.
     */
    public record Contexto(long idOrganizacion, Long idPersona, Long idCredencial,
                           String rolEfectivo, String ip, String agenteUsuario) {

        public static Contexto anonimo(long idOrganizacion, String ip, String agenteUsuario) {
            return new Contexto(idOrganizacion, null, null, null, ip, agenteUsuario);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String tipo, String resultado, Contexto contexto) {
        registrar(tipo, resultado, contexto, null, null, Map.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String tipo, String resultado, Contexto contexto,
                          String motivo, Long idObjetivo, Map<String, ?> detalle) {
        EventoSeguridad evento = new EventoSeguridad();
        evento.setOrganizacionId(contexto.idOrganizacion());
        evento.setFecha(OffsetDateTime.now());
        evento.setTipo(tipo);
        evento.setResultado(resultado);
        evento.setIdPersona(contexto.idPersona());
        evento.setIdCredencial(contexto.idCredencial());
        evento.setRolEfectivo(contexto.rolEfectivo());
        evento.setIp(contexto.ip());
        evento.setAgenteUsuario(recortar(contexto.agenteUsuario(), 300));
        evento.setMotivo(recortar(motivo, 300));
        evento.setIdObjetivo(idObjetivo);
        evento.setDetalleJson(serializarSinSecretos(detalle));
        eventos.save(evento);
    }

    /**
     * Serializa el detalle <b>descartando toda clave sospechosa de llevar un
     * secreto</b>. Se descarta, no se enmascara: un {@code "***"} en la
     * auditoria confirmaria que el campo existia, y no aporta nada.
     * <p>
     * Es intencionadamente conservador — prefiere perder un dato util a dejar
     * pasar uno sensible.
     */
    static String serializarSinSecretos(Map<String, ?> detalle) {
        if (detalle == null || detalle.isEmpty()) {
            return null;
        }
        Map<String, String> limpio = new LinkedHashMap<>();
        detalle.forEach((clave, valor) -> {
            if (clave != null && valor != null && !esProhibida(clave)) {
                limpio.put(clave, recortar(String.valueOf(valor), MAXIMO_VALOR));
            }
        });
        if (limpio.isEmpty()) {
            return null;
        }
        StringBuilder json = new StringBuilder("{");
        limpio.forEach((clave, valor) -> {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(escapar(clave)).append("\":\"").append(escapar(valor)).append('"');
        });
        return json.append('}').toString();
    }

    static boolean esProhibida(String clave) {
        String normal = clave.toLowerCase(java.util.Locale.ROOT);
        return CLAVES_PROHIBIDAS.stream().anyMatch(normal::contains);
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }

    private static String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
