package com.controllocal.web.seguridad;

import com.controllocal.service.AutenticacionService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.OrganizacionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica la peticion a partir del Bearer token (formato compartido con el
 * backend Jakarta). El rol de la sesion se publica como authority
 * ROLE_ADMIN / ROLE_BROKER / ROLE_AGENTE para @PreAuthorize (RC-001: la
 * matriz operacion->rol se aplica en el service/controlador, no aqui).
 *
 * <p>Aqui tambien se ATA el tenant al request (D-20, V6): validado el token,
 * el backend resuelve la organizacion y la publica en el principal. Es el
 * punto natural — el mismo donde manana se fijara la variable de sesion de
 * Postgres cuando se active RLS — y garantiza que ningun endpoint pueda
 * operar sin frontera organizacional ni el cliente elegir la suya.
 */
@Component
public class FiltroAutenticacionJwt extends OncePerRequestFilter {

    /** Marca para que el entry point distinga "sin token" de "token invalido". */
    static final String ATRIBUTO_TOKEN_INVALIDO = "controllocal.token.invalido";

    /** Codigo estable que el SPA usa para llevar a la pantalla de cambio obligatorio. */
    public static final String CODIGO_CAMBIO_OBLIGATORIO = "CAMBIO_CONTRASENA_REQUERIDO";

    /** Gemelo del anterior para el segundo factor (D-S0-25). */
    public static final String CODIGO_ENROLAMIENTO_MFA = "ENROLAMIENTO_MFA_REQUERIDO";

    static final String MENSAJE_CAMBIO_OBLIGATORIO =
            "Debes cambiar tu contrasena temporal antes de continuar.";

    static final String MENSAJE_ENROLAMIENTO_MFA =
            "Debes enrolar tu segundo factor antes de continuar.";

    private final TokenService tokens;
    private final OrganizacionService organizaciones;
    private final AutenticacionService autenticacion;

    public FiltroAutenticacionJwt(TokenService tokens, OrganizacionService organizaciones,
                                  AutenticacionService autenticacion) {
        this.tokens = tokens;
        this.organizaciones = organizaciones;
        this.autenticacion = autenticacion;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String autorizacion = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (autorizacion != null && autorizacion.startsWith("Bearer ")) {
            var valida = tokens.validar(autorizacion.substring(7).trim());
            if (valida.isPresent()) {
                var sesion = valida.get();
                long idOrganizacion = organizaciones.idOrganizacionActual();
                EstadoDeAcceso estado = estadoDe(idOrganizacion, sesion);
                if (estado.sesionInvalidada(sesion.emitidoEn())) {
                    request.setAttribute(ATRIBUTO_TOKEN_INVALIDO, Boolean.TRUE);
                } else {
                    if (estado.debeCambiarContrasena() && !esOperacionPermitidaConSesionCapada(request)) {
                        responderCapada(response, CODIGO_CAMBIO_OBLIGATORIO,
                                MENSAJE_CAMBIO_OBLIGATORIO);
                        return;
                    }
                    // El orden importa: si una cuenta debe las dos cosas, primero
                    // la contrasena. Enrolar un segundo factor con una clave
                    // temporal que todavia no es suya seria atarlo a una
                    // credencial prestada.
                    if (estado.debeEnrolarMfa() && !esOperacionPermitidaSinSegundoFactor(request)) {
                        responderCapada(response, CODIGO_ENROLAMIENTO_MFA,
                                MENSAJE_ENROLAMIENTO_MFA);
                        return;
                    }
                    // La authority es la BANDA EFECTIVA, no el rol del token
                    // (D-S0-8): el token solo admite AGENTE/BROKER/ADMIN
                    // mientras GlassFish conviva (R1), y ese ADMIN heredado no
                    // es el TENANT_ADMIN que gobierna. Aqui muere.
                    String banda = estado.bandaEfectiva(sesion.rol());
                    var principal = new SesionDeRequest(sesion, idOrganizacion, banda);
                    var autenticado = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + banda)));
                    SecurityContextHolder.getContext().setAuthentication(autenticado);
                }
            } else {
                request.setAttribute(ATRIBUTO_TOKEN_INVALIDO, Boolean.TRUE);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * La sesion capada (§4.5) <b>existe</b>: el usuario entro y su token es
     * valido. Lo que no puede es operar hasta cambiar la contrasena temporal.
     *
     * <p>Se deja pasar el minimo imprescindible para que la pantalla de cambio
     * obligatorio funcione: leer su propio perfil y cambiar la clave. Tambien
     * el logout — encerrar a alguien en una sesion de la que no puede salir
     * seria un fallo, no una medida de seguridad.
     *
     * <p>La lista se compara por ruta y metodo, no por rol: el capado no
     * depende de quien seas.
     */
    private static boolean esOperacionPermitidaConSesionCapada(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        String metodo = request.getMethod();
        return (metodo.equals("POST") && ruta.endsWith("/perfil/contrasena"))
                || (metodo.equals("GET") && ruta.endsWith("/perfil"))
                || (metodo.equals("POST") && ruta.endsWith("/auth/logout"));
    }

    /**
     * Sesion capada por el segundo factor (D-S0-25). Misma mecanica que la de
     * la contrasena temporal y por el mismo motivo: exigir MFA a los
     * administradores el dia del despliegue los dejaria fuera de su propia
     * organizacion, porque todavia no tienen factor.
     *
     * <p>Pasa el minimo para salir de la situacion: ver el perfil, consultar el
     * estado del factor, enrolarlo y confirmarlo. <b>Y el logout</b> — encerrar
     * a alguien en una sesion de la que no puede salir seria un fallo, no una
     * medida.
     *
     * <p>Lo que NO alcanza: administrar miembros, clientes ni ninguna operacion
     * de gobierno.
     */
    private static boolean esOperacionPermitidaSinSegundoFactor(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        String metodo = request.getMethod();
        return (metodo.equals("GET") && ruta.endsWith("/perfil"))
                || (metodo.equals("GET") && ruta.endsWith("/perfil/mfa"))
                || (metodo.equals("POST") && ruta.endsWith("/perfil/mfa"))
                || (metodo.equals("POST") && ruta.endsWith("/perfil/mfa/confirmar"))
                || (metodo.equals("POST") && ruta.endsWith("/auth/logout"));
    }

    /**
     * 403 con {@code codigo} para que el SPA sepa que esto no es "no tienes
     * permisos" sino "te falta un paso". Distinguirlo por el texto ataria el
     * cliente a una cadena traducible.
     */
    private static void responderCapada(HttpServletResponse response, String codigo,
                                        String mensaje) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"error\":\"" + mensaje + "\",\"codigo\":\"" + codigo + "\"}");
    }

    /**
     * D-S0-12 — revocacion de SESIONES (nada que ver con la autorizacion de
     * datos personales, que no tiene flujo de revocacion).
     * <p>
     * Un token firmado y no expirado puede estar muerto igualmente: si su
     * {@code iat} es anterior a {@code sesiones_invalidas_desde} de la cuenta,
     * alguien cerro sesion —o cambio su contrasena, o la cuenta se desactivo—
     * y el token ya no vale. El 401 resultante es el mismo mensaje congelado
     * que un token corrupto: no se le dice al cliente <b>por que</b> dejo de
     * valer.
     * <p>
     * <b>Sin cache, a proposito.</b> El Plan S0 admitia una cache de 30-60 s
     * para ahorrar una lectura por request; se descarta porque abriria una
     * ventana en la que una sesion revocada sigue viva, y ese es justo el fallo
     * que la pieza viene a cerrar. La consulta es una proyeccion estrecha sobre
     * claves primarias; si algun dia la sonda de transporte la señala, la cache
     * es la palanca, no el punto de partida.
     * <p>
     * Borde conocido y aceptado: {@code iat} tiene precision de segundo, asi
     * que un login que ocurra <b>dentro del mismo segundo</b> que un logout
     * nace invalidado. Falla del lado seguro (pide entrar otra vez) y con
     * tokens de 30 minutos es una coincidencia sin consecuencia practica.
     * <p>
     * La misma lectura trae la <b>banda efectiva</b> (D-S0-8): es una columna
     * mas de la misma consulta, asi que resolver el gobierno por peticion no
     * anade ninguna ida a la base.
     */
    private EstadoDeAcceso estadoDe(long idOrganizacion, TokenService.Sesion sesion) {
        return autenticacion.estadoDeAcceso(idOrganizacion, sesion.idUsuario());
    }
}
