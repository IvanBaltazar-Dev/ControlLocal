package com.controllocal.service.impl;

import com.controllocal.domain.persona.CredencialUsuario;
import com.controllocal.domain.seguridad.CredencialPassword;
import com.controllocal.domain.seguridad.TokenAcceso;
import com.controllocal.persistence.repositorio.CredencialPasswordRepository;
import com.controllocal.persistence.repositorio.CredencialUsuarioRepository;
import com.controllocal.persistence.repositorio.TokenAccesoRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContrasenaService;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.EventosSeguridad;
import com.controllocal.service.soporte.NotificadorIdentidad;
import com.controllocal.service.soporte.PasswordHasher;
import com.controllocal.service.soporte.PoliticaContrasenas;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementacion de {@link ContrasenaService}.
 *
 * <h2>Dos invariantes que explican casi todo el codigo</h2>
 * <ol>
 *   <li><b>Toda ruta que cambia una clave pasa por
 *       {@link #fijarContrasena}</b>, igual que todo cambio de estado pasa por
 *       {@code Transiciones}: guardar el hash, sellar la fecha, apagar el
 *       capado, archivar el hash viejo e invalidar las sesiones son cinco
 *       efectos que <b>no pueden desparejarse</b>. Repartidos por tres metodos,
 *       tarde o temprano uno se olvida de uno.</li>
 *   <li><b>Nada de lo publico revela si una cuenta existe.</b> Por eso
 *       {@link #solicitarRecuperacion} no devuelve nada y no lanza nunca.</li>
 * </ol>
 */
@Service
public class ContrasenaServiceImpl implements ContrasenaService {

    /** Vigencia corta (§4.3): un token que dura un dia es media contrasena. */
    private static final Duration VIGENCIA_TOKEN = Duration.ofMinutes(30);

    /** 32 bytes de entropia: el token es tan bueno como su generador. */
    private static final int BYTES_TOKEN = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CredencialUsuarioRepository credenciales;
    private final TokenAccesoRepository tokens;
    private final CredencialPasswordRepository historial;
    private final EventosSeguridad auditoria;
    private final NotificadorIdentidad notificador;

    public ContrasenaServiceImpl(CredencialUsuarioRepository credenciales,
                                 TokenAccesoRepository tokens,
                                 CredencialPasswordRepository historial,
                                 EventosSeguridad auditoria,
                                 NotificadorIdentidad notificador) {
        this.credenciales = credenciales;
        this.tokens = tokens;
        this.historial = historial;
        this.auditoria = auditoria;
        this.notificador = notificador;
    }

    // ------------------------------------------------------------------
    // §4.2 — cambio autenticado
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void cambiar(Actor actor, char[] actual, char[] nueva) {
        CredencialUsuario credencial = credenciales
                .buscarPorPersona(actor.idOrganizacion(), actor.idPersona())
                .orElseThrow(() -> new NoEncontradoException("La cuenta no tiene credencial."));

        if (actual == null || actual.length == 0) {
            throw new ReglaNegocioException("La contrasena actual es obligatoria.");
        }
        if (!PasswordHasher.verificar(actual, credencial.getContrasenaHash())) {
            // Mismo mensaje que cualquier otro rechazo de credenciales: aqui no
            // hay padron que proteger (el actor ya esta autenticado), pero si
            // hay que evitar que un mensaje distinto sirva de sonda.
            auditoria.registrar("PASSWORD_CAMBIADA", "FALLO", contextoDe(actor),
                    "contrasena actual incorrecta", null, Map.of());
            throw new ReglaNegocioException("La contrasena actual es incorrecta.");
        }
        if (Arrays.equals(actual, nueva)) {
            throw new ReglaNegocioException("La contrasena nueva debe ser distinta de la actual.");
        }

        fijarContrasena(credencial, nueva, actor.idPersona());
        auditoria.registrar("PASSWORD_CAMBIADA", "OK", contextoDe(actor));
    }

    // ------------------------------------------------------------------
    // §4.3 — recuperacion y canje
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void solicitarRecuperacion(long idOrganizacion, String nombreUsuario,
                                      String ip, String agenteUsuario) {
        var contexto = EventosSeguridad.Contexto.anonimo(idOrganizacion, ip, agenteUsuario);
        Optional<CredencialUsuario> credencial = nombreUsuario == null || nombreUsuario.isBlank()
                ? Optional.empty()
                : credenciales.buscarPorNombreUsuario(idOrganizacion, nombreUsuario.trim());

        if (credencial.isEmpty()) {
            // Se registra el intento contra una cuenta inexistente —es
            // informacion util para el operador— pero el llamador recibe
            // exactamente la misma respuesta que si existiera.
            auditoria.registrar("RECUPERACION_EMITIDA", "FALLO", contexto,
                    "solicitud sobre una cuenta inexistente", null, Map.of());
            return;
        }

        CredencialUsuario cuenta = credencial.get();
        Emision emitido = emitirToken(cuenta, TokenAcceso.TIPO_RECUPERACION, null,
                "solicitada por el titular");
        notificador.enviarRecuperacion(destinoDe(cuenta), tokenEmitidoDe(emitido));
        auditoria.registrar("RECUPERACION_EMITIDA", "OK",
                new EventosSeguridad.Contexto(idOrganizacion, personaDe(cuenta), cuenta.getId(),
                        null, ip, agenteUsuario));
    }

    @Override
    @Transactional
    public void canjear(String token, char[] nueva, String ip, String agenteUsuario) {
        if (token == null || token.isBlank()) {
            throw new ReglaNegocioException("El token es obligatorio.");
        }
        OffsetDateTime ahora = OffsetDateTime.now();
        // Solo los DOS tipos de contrasena (D-S0-23). Un DESAFIO_MFA o una
        // ELEVACION comparten tabla y no pueden canjearse por aqui: seria
        // convertir un segundo factor en un cambio de clave.
        TokenAcceso fila = tokens.buscarPorHashEntreTipos(hashear(token),
                        java.util.List.of(TokenAcceso.TIPO_RECUPERACION, TokenAcceso.TIPO_INVITACION))
                .filter(t -> t.vigenteEn(ahora))
                // Caducado, ya usado, reemplazado o inventado: el mismo error.
                // Distinguirlos diria a un atacante si acerto el token pero
                // llego tarde, que es informacion que no necesita.
                .orElseThrow(() -> new ReglaNegocioException(
                        "El enlace no es valido o ya fue utilizado."));

        CredencialUsuario cuenta = credenciales.findById(fila.getIdCredencial())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El enlace no es valido o ya fue utilizado."));

        fijarContrasena(cuenta, nueva, personaDe(cuenta));
        // Un solo uso: se sella DENTRO de la misma transaccion. Si algo de
        // arriba lanza, el token sigue vivo y el usuario puede reintentar.
        fila.setUsadoEn(ahora);
        tokens.save(fila);

        var contexto = new EventosSeguridad.Contexto(cuenta.getOrganizacionId(),
                personaDe(cuenta), cuenta.getId(), null, ip, agenteUsuario);
        auditoria.registrar(TokenAcceso.TIPO_INVITACION.equals(fila.getTipo())
                ? "INVITACION_CANJEADA" : "RECUPERACION_CANJEADA", "OK", contexto);
        auditoria.registrar("PASSWORD_RESTABLECIDA", "OK", contexto);
    }

    // ------------------------------------------------------------------
    // §4.4 — invitacion y contrasena temporal (gobierno del tenant)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public TokenEntregado emitirInvitacion(Actor actor, long idPersonaObjetivo, String motivo) {
        CredencialUsuario objetivo = objetivoDe(actor, idPersonaObjetivo);
        Emision emitido = emitirToken(objetivo, TokenAcceso.TIPO_INVITACION,
                actor.idPersona(), motivo);
        notificador.enviarInvitacion(destinoDe(objetivo), tokenEmitidoDe(emitido));

        auditoria.registrar("INVITACION_EMITIDA", "OK", contextoDe(actor), motivo,
                idPersonaObjetivo, Map.of("expiraEn", emitido.fila().getExpiraEn()));
        return new TokenEntregado(emitido.enClaro(), emitido.fila().getExpiraEn(),
                notificador.entregaAlTitular());
    }

    @Override
    @Transactional
    public TemporalEntregada emitirContrasenaTemporal(Actor actor, long idPersonaObjetivo,
                                                      String motivo) {
        CredencialUsuario objetivo = objetivoDe(actor, idPersonaObjetivo);
        char[] temporal = PoliticaContrasenas.generarTemporal();
        try {
            fijarContrasena(objetivo, temporal, actor.idPersona());
            // Nace capada: sirve para entrar una vez y cambiarla. Se enciende
            // DESPUES de fijarContrasena, que la apaga por diseno (un cambio
            // normal siempre descapa).
            objetivo.setDebeCambiarContrasena(true);
            credenciales.save(objetivo);

            auditoria.registrar("PASSWORD_RESTABLECIDA", "OK", contextoDe(actor), motivo,
                    idPersonaObjetivo, Map.of("temporal", true));
            return new TemporalEntregada(objetivo.getNombreUsuario(), new String(temporal));
        } finally {
            Arrays.fill(temporal, '\0');
        }
    }

    // ------------------------------------------------------------------
    // Nucleo compartido
    // ------------------------------------------------------------------

    /**
     * El unico sitio que cambia una contrasena. Cinco efectos que van juntos o
     * no van:
     * <ol>
     *   <li>valida la politica (§4.5) y la no-reutilizacion;</li>
     *   <li>archiva el hash que se abandona;</li>
     *   <li>escribe el hash nuevo y sella la fecha;</li>
     *   <li>apaga el capado —cambiar la clave es justo lo que se le pedia—;</li>
     *   <li><b>invalida todas las sesiones vivas</b> (§4.7): si alguien tenia
     *       la clave anterior y una sesion abierta, la pierde.</li>
     * </ol>
     */
    private void fijarContrasena(CredencialUsuario credencial, char[] nueva, long idActor) {
        PoliticaContrasenas.exigirValida(nueva, credencial.getNombreUsuario());
        exigirNoReutilizada(credencial, nueva);

        archivar(credencial);

        credencial.setContrasenaHash(PasswordHasher.hash(nueva));
        credencial.setAlgoritmoHash("pbkdf2");
        credencial.setPasswordActualizadaEn(OffsetDateTime.now());
        credencial.setDebeCambiarContrasena(false);
        // Invalidar aqui y no en el llamador: es el efecto que mas facilmente
        // se olvida y el que convierte "cambie mi clave" en algo con efecto
        // real sobre una sesion robada.
        credencial.setSesionesInvalidasDesde(OffsetDateTime.now());
        credenciales.save(credencial);

        var contexto = new EventosSeguridad.Contexto(credencial.getOrganizacionId(),
                idActor, credencial.getId(), null, null, null);
        auditoria.registrar("SESIONES_INVALIDADAS", "OK", contexto,
                "cambio de contrasena", personaDe(credencial), Map.of());
    }

    /**
     * §4.5: no se puede volver a la clave que se acaba de abandonar. Se compara
     * verificando la candidata contra cada hash guardado — un hash no se puede
     * leer, asi que no hay otra forma.
     */
    private void exigirNoReutilizada(CredencialUsuario credencial, char[] nueva) {
        if (credencial.getContrasenaHash() != null
                && PasswordHasher.verificar(nueva, credencial.getContrasenaHash())) {
            throw new ReglaNegocioException(
                    "La contrasena nueva debe ser distinta de la actual.");
        }
        boolean repetida = historial
                .ultimosDe(credencial.getId(), PageRequest.of(0, PoliticaContrasenas.HISTORIAL))
                .stream()
                .anyMatch(anterior -> PasswordHasher.verificar(nueva, anterior.getContrasenaHash()));
        if (repetida) {
            throw new ReglaNegocioException(
                    "No puedes reutilizar una de tus ultimas "
                            + PoliticaContrasenas.HISTORIAL + " contrasenas.");
        }
    }

    /** Archiva el hash saliente y poda el historial al tamano de la politica. */
    private void archivar(CredencialUsuario credencial) {
        if (credencial.getContrasenaHash() == null) {
            return;
        }
        CredencialPassword anterior = new CredencialPassword();
        anterior.setOrganizacionId(credencial.getOrganizacionId());
        anterior.setIdCredencial(credencial.getId());
        anterior.setContrasenaHash(credencial.getContrasenaHash());
        anterior.setAlgoritmoHash(credencial.getAlgoritmoHash());
        anterior.setCreadoEn(OffsetDateTime.now());
        historial.save(anterior);

        List<Long> conservar = historial
                .ultimosDe(credencial.getId(), PageRequest.of(0, PoliticaContrasenas.HISTORIAL))
                .stream().map(CredencialPassword::getId).toList();
        if (!conservar.isEmpty()) {
            historial.podar(credencial.getId(), conservar);
        }
    }

    /**
     * Token recien emitido: la fila persistida (que solo guarda el hash) y el
     * valor en claro, que <b>no se persiste en ningun sitio</b> y vive solo
     * hasta que se escribe la respuesta.
     */
    private record Emision(TokenAcceso fila, String enClaro) {
    }

    /**
     * Emite un token nuevo y <b>mata el anterior del MISMO tipo</b>. El orden
     * importa: si se insertara antes de invalidar, el indice unico parcial
     * {@code uq_token_acceso_activo (id_credencial, tipo)} rechazaria la fila.
     *
     * <p>Acotado por tipo desde V37: emitir una invitacion no debe matar un
     * desafio de MFA en curso, ni al reves.
     */
    private Emision emitirToken(CredencialUsuario credencial, String tipo,
                                Long creadoPor, String motivo) {
        OffsetDateTime ahora = OffsetDateTime.now();
        tokens.invalidarVivosDe(credencial.getId(), tipo, ahora);

        byte[] material = new byte[BYTES_TOKEN];
        RANDOM.nextBytes(material);
        String enClaro = Base64.getUrlEncoder().withoutPadding().encodeToString(material);

        TokenAcceso token = new TokenAcceso();
        token.setOrganizacionId(credencial.getOrganizacionId());
        token.setIdCredencial(credencial.getId());
        token.setTipo(tipo);
        token.setHashToken(hashear(enClaro));
        token.setCreadoEn(ahora);
        token.setExpiraEn(ahora.plus(VIGENCIA_TOKEN));
        token.setCreadoPor(creadoPor);
        token.setMotivo(motivo);
        tokens.save(token);

        return new Emision(token, enClaro);
    }

    /**
     * La credencial sobre la que actua el gobierno del tenant. El 404 (y no un
     * 403) cuando la persona es de otro tenant es deliberado: responder "no
     * tienes permiso" confirmaria que esa persona existe en algun sitio.
     */
    private CredencialUsuario objetivoDe(Actor actor, long idPersonaObjetivo) {
        if (actor.idPersona() == idPersonaObjetivo) {
            // Emitirse un token a uno mismo saltaria la comprobacion de
            // contrasena actual del cambio normal: seria una puerta trasera
            // para una sesion robada.
            throw new ReglaNegocioException(
                    "Para cambiar tu propia contrasena usa el cambio de contrasena.");
        }
        return credenciales.buscarPorPersona(actor.idOrganizacion(), idPersonaObjetivo)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado."));
    }

    private EventosSeguridad.Contexto contextoDe(Actor actor) {
        // Banda EFECTIVA, no la del token: el evento de seguridad tiene que
        // decir que un TENANT_ADMIN invito o restablecio, no un "ADMIN" que ya
        // no significa lo mismo (H-09).
        return new EventosSeguridad.Contexto(actor.idOrganizacion(), actor.idPersona(),
                null, actor.rolEfectivo(), null, null);
    }

    private static NotificadorIdentidad.Destino destinoDe(CredencialUsuario credencial) {
        var persona = credencial.getRol().getPersona();
        return new NotificadorIdentidad.Destino(persona.getId(),
                persona.getNombresORazonSocial(), persona.getCorreo());
    }

    private static NotificadorIdentidad.TokenEmitido tokenEmitidoDe(Emision emision) {
        return new NotificadorIdentidad.TokenEmitido(
                emision.enClaro(), emision.fila().getExpiraEn());
    }

    private static long personaDe(CredencialUsuario credencial) {
        return credencial.getRol().getPersona().getId();
    }

    /** SHA-256 hex: en la base vive el hash, nunca el token. */
    static String hashear(String valor) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
        }
    }
}
