package com.controllocal.web.seguridad;

import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
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

/** Revalida la sesión Core y sus restricciones antes de publicar el actor contextual. */
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
    private final RevalidacionDeSesion sesiones;

    public FiltroAutenticacionJwt(TokenService tokens, RevalidacionDeSesion sesiones) {
        this.tokens = tokens;
        this.sesiones = sesiones;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String autorizacion = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (autorizacion != null && autorizacion.startsWith("Bearer ")) {
            var valida = tokens.validar(autorizacion.substring(7).trim());
            if (valida.isPresent()) {
                var sesion = valida.get();
                SesionDeRequest principal;
                try {
                    principal = sesiones.comprobar(sesion);
                } catch (CredencialesInvalidasException invalida) {
                    request.setAttribute(ATRIBUTO_TOKEN_INVALIDO, Boolean.TRUE);
                    chain.doFilter(request, response);
                    return;
                }
                EstadoDeAcceso estado = principal.acceso().restricciones();
                {
                    if ("IDENTIDAD".equals(principal.rolEfectivo()) && !esGestionIdentidad(request)) {
                        response.setStatus(409);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"error\":\"Selecciona un contexto autorizado antes de operar.\",\"codigo\":\"CONTEXTO_REQUERIDO\"}");
                        return;
                    }
                    if (estado.debeCambiarContrasena() && !esOperacionPermitidaConSesionCapada(request)) {
                        responderCapada(response, CODIGO_CAMBIO_OBLIGATORIO,
                                MENSAJE_CAMBIO_OBLIGATORIO);
                        return;
                    }
                    // El orden importa: si una cuenta debe las dos cosas, primero
                    // la contrasena. Enrolar un segundo factor con una clave
                    // temporal que todavia no es suya seria atarlo a una
                    // credencial prestada.
                    if (!estado.debeCambiarContrasena() && estado.debeEnrolarMfa()
                            && !esOperacionPermitidaSinSegundoFactor(request)) {
                        responderCapada(response, CODIGO_ENROLAMIENTO_MFA,
                                MENSAJE_ENROLAMIENTO_MFA);
                        return;
                    }
                    String banda = principal.rolEfectivo();
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
        String ruta = rutaLocal(request);
        String metodo = request.getMethod();
        return (metodo.equals("POST") && ruta.equals("/cuenta/contrasena"))
                || (metodo.equals("GET") && ruta.equals("/cuenta"))
                || esSeleccionDeContexto(request)
                || (metodo.equals("POST") && ruta.equals("/auth/logout"));
    }

    private static String rutaLocal(HttpServletRequest request){return request.getRequestURI().substring(request.getContextPath().length());}

    private static boolean esSeleccionDeContexto(HttpServletRequest request) {
        String ruta = rutaLocal(request);
        return ("GET".equals(request.getMethod()) && (ruta.equals("/sesion") || ruta.equals("/sesion/contextos")))
                || ("POST".equals(request.getMethod()) && ruta.equals("/sesion/contexto"));
    }

    private static boolean esGestionIdentidad(HttpServletRequest request) {
        if (esSeleccionDeContexto(request)) return true;
        String ruta = rutaLocal(request);
        String local = ruta;
        if (esCompraPlataforma(request.getMethod(),local)||esMetricasPlataforma(request.getMethod(),local)||esMercadoPlataforma(request.getMethod(),local)) return true;
        if ("POST".equals(request.getMethod()) && local.matches(
                "/membresias/invitaciones/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/aceptar")) return true;
        if ("GET".equals(request.getMethod()) && (ruta.equals("/cuenta") || ruta.equals("/cuenta/mfa"))) return true;
        if ("DELETE".equals(request.getMethod()) && ruta.equals("/cuenta/mfa")) return true;
        return "POST".equals(request.getMethod()) && java.util.List.of(
                "/cuenta/contrasena", "/cuenta/mfa", "/cuenta/mfa/confirmar", "/cuenta/mfa/codigos", "/cuenta/correo",
                "/auth/alta", "/auth/alta/confirmar", "/auth/correo/confirmar",
                "/auth/login", "/auth/mfa/desafio", "/auth/mfa/verificar", "/auth/logout", "/auth/renovar",
                "/auth/recuperacion", "/auth/recuperacion/canje").contains(ruta);
    }

    /** Puerta exacta; la concesión R25 se revalida en Core para cada lectura y efecto. */
    private static boolean esCompraPlataforma(String metodo,String ruta){
        if("GET".equals(metodo)&&java.util.Set.of("/plataforma/atribuciones/compras","/plataforma/compras").contains(ruta))return true;
        String uuid="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
        return "GET".equals(metodo)&&ruta.matches("/plataforma/compras/"+uuid)
                ||"POST".equals(metodo)&&ruta.matches("/plataforma/compras/"+uuid+"/(verificacion|fondos|acreditacion|incidencia)");
    }
    private static boolean esMercadoPlataforma(String metodo,String ruta){
        String raiz="/plataforma/mercado",uuid="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
        String politica=raiz+"/configuraciones/"+uuid+"/[^/]{1,80}",trabajo=raiz+"/trabajos/[1-9][0-9]{0,18}";
        return "GET".equals(metodo)&&(ruta.equals(raiz+"/atribuciones")||ruta.matches(raiz+"/colas/(ejecucion|revision|finanzas)")||ruta.matches(politica)||ruta.matches(trabajo+"/(operador|revision|finanzas)"))
            ||"POST".equals(metodo)&&(ruta.equals(raiz+"/configuraciones")||ruta.equals(raiz+"/mantenimiento")||ruta.matches(politica+"/retiro")||ruta.matches(trabajo+"/(tomar|entrega|revision|publicacion|liquidacion|liberacion-no-iniciada)"));
    }
    /** Lecturas y ajustes R25 exactos; la atribucion/accion/campos se comprueban en Core. */
    private static boolean esMetricasPlataforma(String metodo,String ruta){
        String uuid="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
        String financiera="/plataforma/metricas-ia/financieras/"+uuid+"/"+uuid;
        return "GET".equals(metodo)&&("/plataforma/metricas-ia/tecnicas".equals(ruta)||ruta.matches(financiera))
            ||"POST".equals(metodo)&&ruta.matches(financiera+"/(confirmaciones-costo|aplicacion-costo)");
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
        String ruta = rutaLocal(request);
        String metodo = request.getMethod();
        return esSeleccionDeContexto(request)
                || (metodo.equals("GET") && ruta.equals("/cuenta"))
                || (metodo.equals("GET") && ruta.equals("/cuenta/mfa"))
                || (metodo.equals("POST") && ruta.equals("/cuenta/mfa"))
                || (metodo.equals("POST") && ruta.equals("/cuenta/mfa/confirmar"))
                || (metodo.equals("POST") && ruta.equals("/auth/logout"));
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

}
