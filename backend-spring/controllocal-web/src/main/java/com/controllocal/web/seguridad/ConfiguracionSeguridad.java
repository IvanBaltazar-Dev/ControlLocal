package com.controllocal.web.seguridad;

import com.controllocal.web.http.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Seguridad stateless con JWT. Mensajes de 401/403 identicos a los del
 * backend Jakarta (contrato congelado). {@code @EnableMethodSecurity} habilita
 * los {@code @PreAuthorize} de los controladores.
 *
 * <p>La matriz operacion-&gt;rol completa (RC-001) esta en
 * {@code docs/ai/matriz-operacion-rol.md} y la vigila
 * {@code MatrizOperacionRolTest}: las rutas {@code permitAll} de aqui abajo se
 * leen de ESTE archivo, asi que abrir una sin declararla en el documento rompe
 * el build.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ConfiguracionSeguridad {

    private final ObjectMapper json;

    public ConfiguracionSeguridad(ObjectMapper json) {
        this.json = json;
    }

    @Bean
    public SecurityFilterChain cadenaSeguridad(HttpSecurity http, FiltroAutenticacionJwt filtroJwt) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/salud", "/auth/login").permitAll()
                        // Recuperacion de acceso (§4.3): quien la usa NO tiene
                        // sesion — es exactamente lo que viene a recuperar.
                        // Las dos consumen cupo del bloqueo por IP: un endpoint
                        // publico que emite o canjea tokens es igual de
                        // atacable que el login, y dejarlo fuera del contador
                        // seria abrir la puerta por la que se esquiva.
                        .requestMatchers("/auth/recuperacion", "/auth/recuperacion/canje").permitAll()
                        // Segundo factor (D-S0-22): los dos pasos del login son
                        // PRE-sesion por definicion. El desafio no autoriza
                        // nada por si mismo y consume el mismo cupo por IP.
                        .requestMatchers("/auth/mfa/desafio", "/auth/mfa/verificar").permitAll()
                        // Aviso de privacidad (D-27): el titular debe poder
                        // leerlo SIN cuenta. Sin parametros, sin datos
                        // personales y misma respuesta para todos.
                        .requestMatchers("/aviso-privacidad").permitAll()
                        // H-12 CERRADO (2026-08-08). `/documentos/contenido` era
                        // publico "como en la v1", justificado en que la clave
                        // seria una capability. No lo era, y con el Blazor
                        // eliminado ya no hay ni siquiera la excusa:
                        //   * la clave ES la ruta fisica y filtra el correlativo
                        //     SOL-xxxx y el nombre original del archivo;
                        //   * son 32 bits (UUID recortado a 8 hex);
                        //   * no caduca, no se revoca y viaja en el query string,
                        //     asi que queda en los access logs y en el Referer.
                        // Por ahi se descargan documentos de identidad. Ahora
                        // exige token, como todo lo demas; el SPA ya lo mandaba
                        // siempre (`ApiClient.descargar`), nunca puso la URL en
                        // un `src`, asi que no cambia nada para el.
                        // Contrato OpenAPI (RC-005): visible sin token en esta fase.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Recuperacion de emergencia (V38). Sin sesion que exigir
                        // —no hay nadie dentro, esa es la situacion— asi que lo
                        // que la protege NO es esta cadena: es que solo se
                        // atiende en el conector ligado a 127.0.0.1, y que
                        // `FiltroPuertoDeGestion` responde 404 a cualquier
                        // peticion de estas rutas que llegue por el puerto
                        // publico. Sin la bandera encendida, ni el conector ni
                        // el controlador existen.
                        .requestMatchers("/gestion/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(manejo -> manejo
                        .authenticationEntryPoint(puntoEntradaNoAutenticado())
                        .accessDeniedHandler(manejadorAccesoDenegado()))
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private AuthenticationEntryPoint puntoEntradaNoAutenticado() {
        return (request, response, excepcion) -> {
            boolean tokenInvalido = Boolean.TRUE.equals(
                    request.getAttribute(FiltroAutenticacionJwt.ATRIBUTO_TOKEN_INVALIDO));
            escribirError(response, 401, tokenInvalido ? "Token invalido o expirado." : "Token requerido.");
        };
    }

    private AccessDeniedHandler manejadorAccesoDenegado() {
        return (request, response, excepcion) ->
                escribirError(response, 403, "No tienes permisos para esta operacion.");
    }

    private void escribirError(jakarta.servlet.http.HttpServletResponse response, int estado, String mensaje)
            throws java.io.IOException {
        response.setStatus(estado);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(new ErrorResponse(mensaje)));
    }

    /** CORS para el SPA Angular en desarrollo (configurable por propiedad). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${controllocal.cors.origenes:http://localhost:4200}") List<String> origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        // PATCH lo exigen las operaciones de agenda de /visitas (F3).
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
