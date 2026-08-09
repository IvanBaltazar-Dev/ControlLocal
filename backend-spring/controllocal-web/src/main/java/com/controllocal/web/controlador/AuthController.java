package com.controllocal.web.controlador;

import com.controllocal.service.AutenticacionService;
import com.controllocal.service.MfaService;
import com.controllocal.service.ContrasenaService;
import com.controllocal.service.OrganizacionService;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.BloqueoAccesos;
import com.controllocal.service.soporte.EventosSeguridad;
import com.controllocal.web.dto.ContrasenaDtos.CanjeRequest;
import com.controllocal.web.dto.ContrasenaDtos.RecuperacionRequest;
import com.controllocal.web.dto.LoginRequest;
import com.controllocal.web.dto.MfaDtos.DesafioRequest;
import com.controllocal.web.dto.MfaDtos.DesafioResponse;
import com.controllocal.web.dto.MfaDtos.VerificacionRequest;
import com.controllocal.web.dto.LoginResponse;
import com.controllocal.web.http.DemasiadasSolicitudesException;
import com.controllocal.web.seguridad.IpDelCliente;
import com.controllocal.web.seguridad.SesionActual;
import com.controllocal.web.seguridad.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;

/**
 * Login contra el modelo Party-Role v2, respondiendo el contrato CONGELADO
 * del AuthRest Jakarta (misma forma, mismos mensajes, mismo token HS256).
 *
 * <p>Desde D-S0-21 el login tambien es el punto donde se aplica el
 * <b>bloqueo por cuenta e IP</b> y donde se emite la <b>auditoria de
 * seguridad</b>. Ninguna de las dos cosas cambia el cable: los codigos y los
 * cuerpos siguen siendo los mismos, lo que cambia es <b>cuando</b> se emite
 * cada uno.
 */
@RestController
@RequestMapping("auth")
public class AuthController {

    private final AutenticacionService autenticacion;
    private final TokenService tokens;
    private final BloqueoAccesos bloqueo;
    private final EventosSeguridad auditoria;
    private final OrganizacionService organizaciones;
    private final IpDelCliente ipDelCliente;
    private final ContrasenaService contrasenas;
    private final MfaService mfa;

    public AuthController(AutenticacionService autenticacion, TokenService tokens,
                          BloqueoAccesos bloqueo, EventosSeguridad auditoria,
                          OrganizacionService organizaciones, IpDelCliente ipDelCliente,
                          ContrasenaService contrasenas, MfaService mfa) {
        this.autenticacion = autenticacion;
        this.tokens = tokens;
        this.bloqueo = bloqueo;
        this.auditoria = auditoria;
        this.organizaciones = organizaciones;
        this.ipDelCliente = ipDelCliente;
        this.contrasenas = contrasenas;
        this.mfa = mfa;
    }

    @PostMapping("login")
    public LoginResponse login(@RequestBody(required = false) LoginRequest credenciales,
                               HttpServletRequest request) {
        String usuario = credenciales == null ? null : credenciales.usuario();
        String ip = ipDelCliente.de(request);
        String agente = request.getHeader("User-Agent");
        long organizacion = organizaciones.idOrganizacionActual();
        var contexto = EventosSeguridad.Contexto.anonimo(organizacion, ip, agente);

        // El bloqueo se evalua ANTES de tocar la contrasena: si se comprobara
        // primero, el tiempo de respuesta delataria si la cuenta existe.
        BloqueoAccesos.Veredicto veredicto = bloqueo.permitir(usuario, ip);
        if (veredicto.bloqueado()) {
            auditoria.registrar(EventoTipo.LOGIN_BLOQUEADO_429, EventoTipo.BLOQUEADO, contexto,
                    // El motivo dice la DIMENSION, no la cuenta: la fila no
                    // puede convertirse en un padron de usuarios probados.
                    "bloqueado por " + veredicto.dimension(), null,
                    Map.of("fallos", veredicto.fallos()));
            throw new DemasiadasSolicitudesException(
                    BloqueoAccesos.esperaSegundos(veredicto.fallos()));
        }

        if (credenciales == null || usuario == null || usuario.isBlank()
                || credenciales.contrasena() == null || credenciales.contrasena().isBlank()) {
            // Una peticion malformada TAMBIEN consume cupo: si no, bastaria
            // mandar basura para sondear sin coste.
            registrarFallo(usuario, ip, organizacion, agente, contexto);
            throw new CredencialesInvalidasException();
        }

        char[] password = credenciales.contrasena().toCharArray();
        try {
            var sesion = autenticacion.autenticar(usuario, password);
            // Una cuenta con segundo factor NO entra por el camino que no lo
            // pide (D-S0-22). Es duro y es correcto: el Blazor —que no va a
            // tener cuentas con MFA— no se entera, y el SPA usa el camino nuevo.
            if (mfa.exigeSegundoFactor(organizacion, sesion.idUsuario())) {
                registrarFallo(usuario, ip, organizacion, agente, contexto);
                throw new CredencialesInvalidasException();
            }
            bloqueo.registrar(usuario, ip, true, organizacion, agente);
            auditoria.registrar(EventoTipo.LOGIN_OK, EventoTipo.OK,
                    new EventosSeguridad.Contexto(organizacion, sesion.idUsuario(), null,
                            sesion.rol(), ip, agente));
            return emitirSesion(sesion);
        } catch (CredencialesInvalidasException error) {
            registrarFallo(usuario, ip, organizacion, agente, contexto);
            throw error;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * <b>Primer paso del login con segundo factor (D-S0-22).</b>
     *
     * <p>Responde de dos formas y por eso el SPA usa <b>un solo camino</b> sin
     * adivinar si la cuenta tiene MFA —que no puede saberlo—:
     * <ul>
     *   <li><b>200</b> + el {@code LoginResponse} congelado, si no hay factor;</li>
     *   <li><b>202</b> + un desafio, si lo hay.</li>
     * </ul>
     *
     * <p>Se descarto la variante de un solo cuerpo (usuario + contrasena +
     * codigo): obliga al cliente a tener el codigo listo antes de saber si hace
     * falta, y <b>quema codigos legitimos contra contrasenas mal escritas</b>.
     */
    @PostMapping("mfa/desafio")
    public ResponseEntity<Object> desafio(@RequestBody(required = false) DesafioRequest dto,
                                          HttpServletRequest request) {
        String usuario = dto == null ? null : dto.usuario();
        String ip = ipDelCliente.de(request);
        String agente = request.getHeader("User-Agent");
        long organizacion = organizaciones.idOrganizacionActual();
        var contexto = EventosSeguridad.Contexto.anonimo(organizacion, ip, agente);

        BloqueoAccesos.Veredicto veredicto = bloqueo.permitir(usuario, ip);
        if (veredicto.bloqueado()) {
            auditoria.registrar(EventoTipo.LOGIN_BLOQUEADO_429, EventoTipo.BLOQUEADO, contexto,
                    "bloqueado por " + veredicto.dimension(), null,
                    Map.of("fallos", veredicto.fallos()));
            throw new DemasiadasSolicitudesException(
                    BloqueoAccesos.esperaSegundos(veredicto.fallos()));
        }
        if (dto == null || usuario == null || usuario.isBlank()
                || dto.contrasena() == null || dto.contrasena().isBlank()) {
            registrarFallo(usuario, ip, organizacion, agente, contexto);
            throw new CredencialesInvalidasException();
        }

        char[] password = dto.contrasena().toCharArray();
        try {
            var sesion = autenticacion.autenticar(usuario, password);
            bloqueo.registrar(usuario, ip, true, organizacion, agente);

            if (!mfa.exigeSegundoFactor(organizacion, sesion.idUsuario())) {
                auditoria.registrar(EventoTipo.LOGIN_OK, EventoTipo.OK,
                        new EventosSeguridad.Contexto(organizacion, sesion.idUsuario(), null,
                                sesion.rol(), ip, agente));
                return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(emitirSesion(sesion));
            }
            // Todavia NO hay sesion: el desafio no autoriza nada.
            var emitido = mfa.emitirDesafio(organizacion, sesion.idUsuario());
            return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                    .body(new DesafioResponse(emitido.token(), emitido.expiraEn(), "TOTP"));
        } catch (CredencialesInvalidasException error) {
            registrarFallo(usuario, ip, organizacion, agente, contexto);
            throw error;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Segundo paso: canjea el desafio por la sesion. Admite un codigo TOTP o un
     * codigo de respaldo — el service decide cual es sin que el cliente lo
     * declare, porque pedirle que lo distinga solo aniade una forma de
     * equivocarse.
     */
    @PostMapping("mfa/verificar")
    public ResponseEntity<LoginResponse> verificar(
            @RequestBody(required = false) VerificacionRequest dto,
            HttpServletRequest request) {
        String ip = ipDelCliente.de(request);
        String agente = request.getHeader("User-Agent");
        var verificacion = mfa.verificarDesafio(dto == null ? null : dto.desafio(),
                dto == null ? null : dto.codigo(), ip, agente);
        var sesion = autenticacion.identidadDe(
                organizaciones.idOrganizacionActual(), verificacion.idPersona());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(emitirSesion(sesion));
    }

    /** El cuerpo congelado del login, en un solo sitio para los tres caminos. */
    private LoginResponse emitirSesion(AutenticacionService.SesionAutenticada sesion) {
        TokenService.Sesion token = tokens.emitir(
                sesion.nombreUsuario(), sesion.rol(), sesion.idUsuario(), sesion.idDominio());
        return new LoginResponse(
                tokens.firmar(token),
                TokenService.DURACION_SEGUNDOS,
                sesion.rol(),
                sesion.idUsuario(),
                sesion.idDominio(),
                sesion.nombre(),
                sesion.nombreUsuario(),
                LocalDateTime.ofInstant(token.expiraEn(), ZoneId.systemDefault()));
    }

    /**
     * Logout con efecto en servidor (D-S0-12). ADITIVO: la v1 no lo tiene, y
     * hasta hoy "cerrar sesion" era solo un {@code localStorage.removeItem} —
     * el token seguia siendo valido hasta expirar.
     *
     * <p><b>Cierra TODAS las sesiones de la cuenta</b>, no solo la del
     * navegador que llama. Sesiones individuales exigirian un {@code jti} que
     * no cabe en el token congelado; el SPA lo dice en pantalla en vez de
     * prometer lo contrario.
     *
     * <p>Responde 204 siempre que haya token valido, incluso si la persona no
     * tuviera credencial: el cliente no gana nada distinguiendo casos al
     * cerrar sesion, y devolver 404 seria un oraculo.
     */
    @PostMapping("logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var actor = SesionActual.actor();
        boolean invalidadas = autenticacion.invalidarSesiones(actor.idOrganizacion(), actor.idPersona());

        var contexto = new EventosSeguridad.Contexto(actor.idOrganizacion(), actor.idPersona(),
                null, actor.rolEfectivo(), ipDelCliente.de(request), request.getHeader("User-Agent"));
        auditoria.registrar(EventoTipo.LOGOUT, EventoTipo.OK, contexto);
        if (invalidadas) {
            // Dos eventos y no uno: "salio" y "sus sesiones dejaron de valer"
            // son hechos distintos, y el segundo tambien lo produciran el
            // cambio de contrasena y la baja de la cuenta.
            auditoria.registrar(EventoTipo.SESIONES_INVALIDADAS, EventoTipo.OK, contexto);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Renovacion de la sesion por ACTIVIDAD. ADITIVO: la v1 no lo tiene.
     *
     * <p>Convierte los 30 minutos del token en un limite de <b>inactividad</b>
     * en vez de uno absoluto: quien esta trabajando sigue trabajando, quien
     * deja el navegador quieto media hora cae. Antes de esto la sesion moria a
     * los 30 minutos aunque estuvieras escribiendo, y el 401 llegaba en mitad
     * de un formulario.
     *
     * <p><b>No relaja nada, y conviene ver por que.</b> No hay refresh token ni
     * credencial nueva: para renovar hay que presentar un token que
     * {@code FiltroAutenticacionJwt} ya dio por bueno, y ese filtro comprueba
     * en CADA request la firma, la caducidad y —desde D-S0-12— si la sesion
     * fue revocada ({@code sesiones_invalidas_desde} contra el {@code iat}).
     * Un token revocado no llega hasta aqui, asi que renovar no puede resucitar
     * una sesion muerta. Cerrar sesion o cambiar la contrasena siguen matando
     * todo al instante.
     *
     * <p><b>Deliberadamente NO esta en las listas de sesion capada</b>: quien
     * arrastra una contrasena temporal o le falta enrolar el segundo factor
     * recibe el 403 con su codigo y el SPA lo manda al paso que le falta.
     * Renovar indefinidamente una sesion capada seria dejar viva justo la que
     * hay que resolver.
     *
     * <p>La identidad se vuelve a leer con {@code identidadDe} en vez de
     * copiarse del token: asi una cuenta desactivada —o cuyo rol cambio— no
     * arrastra su rol viejo durante otros 30 minutos.
     *
     * <p>No emite evento de auditoria a proposito. Una renovacion no es una
     * autenticacion nueva —el {@code LOGIN_OK} de esta sesion ya esta
     * registrado— y anotar una fila por usuario activo cada 25 minutos
     * enterraria los eventos que si importan bajo ruido de fondo.
     */
    @PostMapping("renovar")
    public ResponseEntity<LoginResponse> renovar() {
        var actor = SesionActual.actor();
        var sesion = autenticacion.identidadDe(actor.idOrganizacion(), actor.idPersona());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(emitirSesion(sesion));
    }

    /**
     * Recuperacion pedida por el titular (§4.3). <b>Publico y aditivo.</b>
     *
     * <p><b>Responde 202 SIEMPRE</b>, exista o no la cuenta y venga o no vacio
     * el cuerpo. Cualquier otra cosa —un 404, un mensaje distinto, incluso
     * tardar menos— convertiria el endpoint en un padron de usuarios: bastaria
     * probar nombres y mirar la respuesta.
     *
     * <p>Consume cupo del bloqueo por IP, como el login: un endpoint publico
     * que emite tokens es igual de atacable, y sin contarlo seria la puerta
     * por la que se esquiva el bloqueo del login.
     *
     * <p><b>Limitacion de hoy, dicha sin adornos</b>: no hay transporte
     * configurado (D-S0-11), asi que el token emitido aqui <b>no llega a
     * nadie</b>. El camino que funciona es la invitacion, que emite el gobierno
     * del tenant y entrega a mano. Este endpoint existe para que el dia que
     * haya correo no haya que tocar contrato ni esquema.
     */
    @PostMapping("recuperacion")
    public ResponseEntity<Void> recuperacion(
            @RequestBody(required = false) RecuperacionRequest dto,
            HttpServletRequest request) {
        String ip = ipDelCliente.de(request);
        String agente = request.getHeader("User-Agent");
        long organizacion = organizaciones.idOrganizacionActual();

        // Solo la dimension IP: contar por CUENTA aqui permitiria bloquear la
        // cuenta ajena pidiendo su recuperacion en bucle.
        BloqueoAccesos.Veredicto veredicto = bloqueo.permitir(null, ip);
        if (veredicto.bloqueado()) {
            throw new DemasiadasSolicitudesException(
                    BloqueoAccesos.esperaSegundos(veredicto.fallos()));
        }
        bloqueo.registrarSoloIp(ip, false, organizacion, agente);
        contrasenas.solicitarRecuperacion(organizacion,
                dto == null ? null : dto.usuario(), ip, agente);
        return ResponseEntity.accepted().build();
    }

    /**
     * Canje del token de un solo uso (§4.3). <b>Publico</b>: quien lo usa no
     * tiene sesion — es justo lo que viene a recuperar.
     *
     * <p>Sirve para los dos tipos, recuperacion e invitacion: el efecto es el
     * mismo y el titular define su clave. Responde 204; a partir de ahi entra
     * por el login normal.
     */
    @PostMapping("recuperacion/canje")
    public ResponseEntity<Void> canjearRecuperacion(
            @RequestBody(required = false) CanjeRequest dto,
            HttpServletRequest request) {
        if (dto == null) {
            throw new ReglaNegocioException("El token es obligatorio.");
        }
        char[] nueva = dto.contrasenaNueva() == null
                ? new char[0] : dto.contrasenaNueva().toCharArray();
        try {
            contrasenas.canjear(dto.token(), nueva,
                    ipDelCliente.de(request), request.getHeader("User-Agent"));
            return ResponseEntity.noContent().build();
        } finally {
            Arrays.fill(nueva, '\0');
        }
    }

    /**
     * Un fallo cuenta en las dos dimensiones y deja su evento. Si con este
     * intento la cuenta cruza el umbral administrativo, se emite ademas
     * {@code CUENTA_BLOQUEADA}: es el evento que un operador busca cuando
     * alguien llama diciendo que no puede entrar.
     */
    private void registrarFallo(String usuario, String ip, long organizacion, String agente,
                                EventosSeguridad.Contexto contexto) {
        bloqueo.registrar(usuario, ip, false, organizacion, agente);
        auditoria.registrar(EventoTipo.LOGIN_FALLIDO, EventoTipo.FALLO, contexto);

        BloqueoAccesos.Veredicto tras = bloqueo.permitir(usuario, ip);
        if (tras.exigeDesbloqueoAdministrativo()) {
            auditoria.registrar(EventoTipo.CUENTA_BLOQUEADA, EventoTipo.BLOQUEADO, contexto,
                    "umbral administrativo por " + tras.dimension(), null,
                    Map.of("fallos", tras.fallos()));
        }
    }

    /** Alias locales para no arrastrar la entidad de dominio hasta la web. */
    private static final class EventoTipo {
        static final String LOGIN_OK = "LOGIN_OK";
        static final String LOGIN_FALLIDO = "LOGIN_FALLIDO";
        static final String LOGIN_BLOQUEADO_429 = "LOGIN_BLOQUEADO_429";
        static final String LOGOUT = "LOGOUT";
        static final String SESIONES_INVALIDADAS = "SESIONES_INVALIDADAS";
        static final String CUENTA_BLOQUEADA = "CUENTA_BLOQUEADA";
        static final String OK = "OK";
        static final String FALLO = "FALLO";
        static final String BLOQUEADO = "BLOQUEADO";

        private EventoTipo() {
        }
    }
}
