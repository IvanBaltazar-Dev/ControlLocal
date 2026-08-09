package com.controllocal.service.soporte;

import com.controllocal.domain.seguridad.IntentoAcceso;
import com.controllocal.persistence.repositorio.IntentoAccesoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Bloqueo por intentos fallidos <b>por cuenta y por IP</b> (D-S0-21).
 *
 * <p>Sustituye al limitador anterior, que contaba 10/min <b>solo por IP</b> y
 * <b>en memoria del proceso</b>. Eso no alcanzaba (H-07): un atacante con 50
 * IPs probaba 500/min contra <b>una sola cuenta</b>, un reinicio borraba el
 * contador y con N instancias el limite efectivo pasaba a ser 10xN.
 *
 * <h2>Las cinco dimensiones</h2>
 * <ol>
 *   <li><b>Cuenta</b> — se cuenta por nombre de usuario normalizado,
 *       <b>exista o no la cuenta</b>. Si solo contaran las existentes, el
 *       propio bloqueo revelaria el padron: bastaria ver quien se bloquea.</li>
 *   <li><b>IP</b> — la IP real la resuelve la capa web y llega ya limpia.</li>
 *   <li><b>Ventana deslizante</b> de 15 minutos; el contador de la cuenta se
 *       "limpia" con un login correcto — sin borrar filas: la lectura arranca
 *       en el ultimo acierto.</li>
 *   <li><b>Progresividad</b> — 5 fallos esperan 1 min, 10 esperan 5, 15
 *       esperan 15 y 20 exigen desbloqueo administrativo. Sin escalado, o se
 *       molesta al usuario legitimo o no se frena al atacante.</li>
 *   <li><b>Desbloqueo</b> — por caducidad de la ventana. El desbloqueo
 *       explicito por administrador llega con el bloque de gobierno.</li>
 * </ol>
 *
 * <p><b>Lo que NUNCA hace:</b> distinguir en la respuesta una cuenta que
 * existe de una que no, ni un bloqueo por cuenta de uno por IP. Los dos son
 * 429 con el mismo cuerpo congelado; un codigo distinto seria un oraculo.
 */
@Component
public class BloqueoAccesos {

    /** Ventana deslizante en la que se cuentan los fallos. */
    private static final Duration VENTANA = Duration.ofMinutes(15);

    /**
     * Escalones: a partir de N fallos, hay que esperar M desde el ultimo.
     * El ultimo escalon (20) es el bloqueo administrativo, y por eso su espera
     * cubre la ventana entera.
     */
    private static final int[][] ESCALONES = {
            {20, 15 * 60},
            {15, 15 * 60},
            {10, 5 * 60},
            {5, 60}
    };

    /** A partir de aqui, el desbloqueo deja de ser cuestion de esperar poco. */
    public static final int FALLOS_BLOQUEO_ADMINISTRATIVO = 20;

    private final IntentoAccesoRepository intentos;
    private final int umbralCuenta;
    private final int umbralIp;

    /**
     * Los umbrales son configurables por perfil, y no es un lujo: los 13
     * scripts E2E hacen 3-4 logins cada uno, asi que en perfil {@code test}
     * el umbral por cuenta se sube y dos corridas seguidas del mismo script
     * dejan de bloquear al usuario del fixture (aviso del Plan S0 §4.8).
     */
    public BloqueoAccesos(IntentoAccesoRepository intentos,
                          @Value("${controllocal.login.max-fallos-por-cuenta:5}") int umbralCuenta,
                          @Value("${controllocal.login.max-fallos-por-ip:10}") int umbralIp) {
        this.intentos = intentos;
        this.umbralCuenta = umbralCuenta;
        this.umbralIp = umbralIp;
    }

    /** Veredicto del bloqueo, con el detalle que la auditoria necesita. */
    public record Veredicto(boolean bloqueado, String dimension, long fallos) {

        public static final Veredicto LIBRE = new Veredicto(false, null, 0);

        /** El bloqueo dejo de ser "espera un poco" y exige intervencion. */
        public boolean exigeDesbloqueoAdministrativo() {
            return bloqueado && fallos >= FALLOS_BLOQUEO_ADMINISTRATIVO;
        }
    }

    /**
     * ¿Se admite este intento? Se consulta ANTES de comprobar la contrasena,
     * para que un atacante no gane informacion por el tiempo de respuesta.
     *
     * <p>Se evalua primero la CUENTA: es la dimension que protege al usuario
     * concreto y la que un atacante distribuido no puede esquivar rotando IPs.
     */
    @Transactional(readOnly = true)
    public Veredicto permitir(String usuario, String ip) {
        Veredicto porCuenta = evaluar(IntentoAcceso.CLAVE_CUENTA, normalizarUsuario(usuario), umbralCuenta);
        if (porCuenta.bloqueado()) {
            return porCuenta;
        }
        return evaluar(IntentoAcceso.CLAVE_IP, ip, umbralIp);
    }

    /**
     * Registra el desenlace del intento en sus dos dimensiones.
     *
     * <p>En su PROPIA transaccion, por lo mismo que la auditoria: el contador
     * de un intento fallido no puede irse con el rollback de la operacion que
     * fallo, o el bloqueo no contaria nada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String usuario, String ip, boolean exito,
                          Long idOrganizacion, String agenteUsuario) {
        guardar(IntentoAcceso.CLAVE_CUENTA, normalizarUsuario(usuario), exito, idOrganizacion, ip, agenteUsuario);
        guardar(IntentoAcceso.CLAVE_IP, ip, exito, idOrganizacion, ip, agenteUsuario);
    }

    /**
     * Registra el intento <b>solo en la dimension IP</b>. Lo usa la solicitud
     * de recuperacion (§4.3).
     *
     * <p><b>Por que ahi NO se cuenta por cuenta</b>, aunque el endpoint reciba
     * un nombre de usuario: contarlo permitiria a cualquiera <b>bloquear la
     * cuenta ajena</b> pidiendo su recuperacion en bucle. Seria convertir una
     * proteccion contra fuerza bruta en una herramienta de denegacion de
     * servicio dirigida, y encima gratuita, porque el atacante no necesita
     * acertar ninguna contrasena.
     *
     * <p>La IP si se cuenta: es lo que hace real el cupo de un endpoint publico
     * que emite tokens. Sin esto, {@code /auth/recuperacion} seria la puerta
     * por la que se esquiva el bloqueo del login.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarSoloIp(String ip, boolean exito, Long idOrganizacion, String agenteUsuario) {
        guardar(IntentoAcceso.CLAVE_IP, ip, exito, idOrganizacion, ip, agenteUsuario);
    }

    /** Retencion de 30 dias (Plan S0 §4.8). La invoca la tarea de mantenimiento. */
    @Transactional
    public int purgar() {
        return intentos.purgarAnterioresA(OffsetDateTime.now().minusDays(30));
    }

    private Veredicto evaluar(String claveTipo, String valor, int umbral) {
        if (valor == null || valor.isBlank()) {
            // Sin clave no hay dimension que contar. No se inventa un bloqueo
            // "por si acaso": eso convertiria un proxy mal configurado en una
            // caida de servicio.
            return Veredicto.LIBRE;
        }
        String hash = hashear(valor);
        OffsetDateTime inicioVentana = OffsetDateTime.now().minus(VENTANA);
        // Un acierto dentro de la ventana limpia el contador SIN borrar filas:
        // simplemente se deja de mirar lo anterior a el.
        OffsetDateTime ultimoExito = intentos.ultimoExitoDesde(claveTipo, hash, inicioVentana);
        OffsetDateTime desde = ultimoExito == null ? inicioVentana : ultimoExito;

        long fallos = intentos.contarFallosDesde(claveTipo, hash, desde);
        if (fallos < umbral) {
            return Veredicto.LIBRE;
        }
        return new Veredicto(true, claveTipo, fallos);
    }

    /**
     * Espera exigida para un numero de fallos. Publico porque la capa web lo
     * usa para el {@code Retry-After}, que es informacion que SI conviene dar:
     * no revela nada y evita que un cliente legitimo reintente en bucle.
     */
    public static int esperaSegundos(long fallos) {
        for (int[] escalon : ESCALONES) {
            if (fallos >= escalon[0]) {
                return escalon[1];
            }
        }
        return 0;
    }

    private void guardar(String claveTipo, String valor, boolean exito,
                         Long idOrganizacion, String ip, String agenteUsuario) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        IntentoAcceso intento = new IntentoAcceso();
        intento.setClaveTipo(claveTipo);
        intento.setClaveValorHash(hashear(valor));
        intento.setOcurridoEn(OffsetDateTime.now());
        intento.setExito(exito);
        intento.setOrganizacionId(idOrganizacion);
        intento.setIp(ip);
        intento.setAgenteUsuario(agenteUsuario == null || agenteUsuario.length() <= 300
                ? agenteUsuario : agenteUsuario.substring(0, 300));
        intentos.save(intento);
    }

    /** Minusculas y sin espacios: 'VMora ' y 'vmora' son la misma cuenta. */
    private static String normalizarUsuario(String usuario) {
        return usuario == null ? null : usuario.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * SHA-256 hex. La tabla guarda el hash y nunca el identificador: si no,
     * seria un padron de los nombres de usuario que alguien probo, que es
     * exactamente el dato que un atacante querria robar de ahi.
     */
    static String hashear(String valor) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
        }
    }
}
