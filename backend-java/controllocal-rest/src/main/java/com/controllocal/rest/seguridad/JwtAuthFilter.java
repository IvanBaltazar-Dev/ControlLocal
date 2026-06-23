package com.controllocal.rest.seguridad;

import java.io.IOException;

import com.controllocal.rest.util.JsonUtils;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebFilter(urlPatterns = "/Api/*", asyncSupported = true)
public class JwtAuthFilter implements Filter {

    private final TokenService tokens = new TokenService();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String ruta = request.getRequestURI().substring(request.getContextPath().length());

        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || esPublica(ruta)) {
            chain.doFilter(request, response);
            return;
        }

        String autorizacion = request.getHeader("Authorization");
        if (autorizacion == null || !autorizacion.startsWith("Bearer ")) {
            JsonUtils.responderError(response, 401, "Token requerido.");
            return;
        }

        TokenService.Sesion sesion = tokens.validar(autorizacion.substring(7).trim()).orElse(null);
        if (sesion == null) {
            JsonUtils.responderError(response, 401, "Token invalido o expirado.");
            return;
        }
        if (requiereBroker(ruta) && !("BROKER".equals(sesion.rol()) || "ADMIN".equals(sesion.rol()))) {
            JsonUtils.responderError(response, 403, "No tienes permisos para esta operacion.");
            return;
        }

        request.setAttribute("usuarioAutenticado",
                new UsuarioAutenticado(sesion.idUsuario(), sesion.idDominio(), sesion.usuario(), sesion.rol()));
        chain.doFilter(request, response);
    }

    private boolean esPublica(String ruta) {
        return "/Api/salud".equalsIgnoreCase(ruta)
                || "/Api/auth/login".equalsIgnoreCase(ruta)
                // Descarga por clave opaca (capability): el visor del frontend la consume
                // tras su proxy "/documento", sin propagar el token JWT al navegador.
                || "/Api/documentos/contenido".equalsIgnoreCase(ruta);
    }

    private boolean requiereBroker(String ruta) {
        String normalizada = ruta.toLowerCase();
        return normalizada.equals("/api/captaciones/pendientes")
                || normalizada.matches("/api/captaciones/\\d+/(decision|reasignar|cierre)");
    }
}
