package com.controllocal.service.soporte;

import com.controllocal.domain.seguridad.IntentoAcceso;
import com.controllocal.domain.seguridad.TokenAcceso;
import com.controllocal.persistence.repositorio.IntentoAccesoRepository;
import com.controllocal.persistence.repositorio.TokenAccesoRepository;
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
 * Conteo <b>acumulado por cuenta</b> de fallos de segundo factor (D-S0-32).
 *
 * <p><b>El problema que resuelve.</b> Un TOTP de seis digitos son 10⁶
 * combinaciones y hay dos codigos validos a la vez, asi que quien ya tenga la
 * contrasena acierta con ~500.000 intentos. Limitar solo <i>por desafio</i>
 * no sirve: se piden desafios nuevos y el contador vuelve a cero. <b>Emitir un
 * secreto nuevo no puede reiniciar el conteo acumulado.</b>
 *
 * <p>Por eso hay tres controles a la vez y este es el del medio:
 * <ol>
 *   <li>por desafio — 5 intentos, y el desafio muere ({@code TokenAcceso});</li>
 *   <li><b>acumulado por cuenta</b> — esta clase, con espera progresiva;</li>
 *   <li>por IP — {@link BloqueoAccesos}, que ya protege el login.</li>
 * </ol>
 *
 * <p><b>Nunca bloquea de forma indefinida.</b> Un bloqueo permanente por
 * fallos de MFA seria una denegacion de servicio contra el administrador —y
 * gratuita para quien la provoque, porque no necesita acertar nada—. Lo que
 * hace es imponer una espera que crece.
 *
 * <p><b>Un acierto reduce, no borra.</b> {@link BloqueoAccesos} limpia el
 * contador desde el ultimo exito; aqui se descuenta una parte, para que una
 * rafaga de fallos no se limpie entera con un solo acierto.
 */
@Component
public class BloqueoMfa {

    private static final Duration VENTANA = Duration.ofMinutes(30);

    /** Fallos que exigen desafio nuevo (lo mata el propio token, §4.3). */
    public static final int UMBRAL_DESAFIO_NUEVO = 5;

    /** {fallos, espera en segundos}, de mas grave a menos. */
    private static final int[][] ESCALONES = {
            {15, 900},
            {10, 300},
    };

    /**
     * Cuantos fallos "perdona" un acierto. <b>Uno</b>, no varios: si un acierto
     * descontara media docena, a quien consiga un solo codigo —por phishing, o
     * por suerte— le bastaria para limpiar casi todo el presupuesto y seguir
     * probando. "Reduce gradualmente" es esto, no "casi lo borra".
     */
    private static final int FALLOS_PERDONADOS_POR_ACIERTO = 1;

    private final IntentoAccesoRepository intentos;
    private final TokenAccesoRepository tokens;

    public BloqueoMfa(IntentoAccesoRepository intentos, TokenAccesoRepository tokens) {
        this.intentos = intentos;
        this.tokens = tokens;
    }

    /**
     * Suma un fallo al desafio y lo mata al llegar al maximo.
     *
     * <p>En su PROPIA transaccion, y esto no es un detalle: el fallo se anota
     * <b>justo antes de lanzar</b>, asi que con el contador dentro de la
     * transaccion que lanza, el rollback lo borraria. El limite de cinco
     * intentos por desafio no contaria nada y bastaria insistir.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void anotarFalloDeDesafio(long idToken) {
        tokens.sumarIntentoFallido(idToken, (short) TokenAcceso.INTENTOS_MAXIMOS,
                OffsetDateTime.now());
    }

    /** Cuanto hay que esperar, en segundos. {@code 0} = adelante. */
    @Transactional(readOnly = true)
    public int esperaExigida(String usuario) {
        long fallos = fallosVigentes(usuario);
        for (int[] escalon : ESCALONES) {
            if (fallos >= escalon[0]) {
                return escalon[1];
            }
        }
        return 0;
    }

    @Transactional(readOnly = true)
    public long fallosVigentes(String usuario) {
        if (usuario == null || usuario.isBlank()) {
            return 0;
        }
        String hash = hashear(normalizar(usuario));
        OffsetDateTime inicio = OffsetDateTime.now().minus(VENTANA);
        long fallos = intentos.contarFallosDesde(IntentoAcceso.CLAVE_MFA_CUENTA, hash, inicio);
        long aciertos = intentos.contarExitosDesde(IntentoAcceso.CLAVE_MFA_CUENTA, hash, inicio);
        return Math.max(0, fallos - aciertos * FALLOS_PERDONADOS_POR_ACIERTO);
    }

    /**
     * En su PROPIA transaccion, por lo mismo que la auditoria: el contador de
     * un intento fallido no puede irse con el rollback de la operacion que
     * fallo, o no contaria nada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String usuario, boolean exito, Long idOrganizacion,
                          String ip, String agenteUsuario) {
        if (usuario == null || usuario.isBlank()) {
            return;
        }
        IntentoAcceso fila = new IntentoAcceso();
        fila.setOrganizacionId(idOrganizacion);
        fila.setClaveTipo(IntentoAcceso.CLAVE_MFA_CUENTA);
        fila.setClaveValorHash(hashear(normalizar(usuario)));
        fila.setOcurridoEn(OffsetDateTime.now());
        fila.setExito(exito);
        fila.setIp(ip);
        fila.setAgenteUsuario(agenteUsuario);
        intentos.save(fila);
    }

    private static String normalizar(String usuario) {
        return usuario.trim().toLowerCase(Locale.ROOT);
    }

    /** El identificador se guarda hasheado: la tabla no es un padron de cuentas. */
    private static String hashear(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.", e);
        }
    }
}
