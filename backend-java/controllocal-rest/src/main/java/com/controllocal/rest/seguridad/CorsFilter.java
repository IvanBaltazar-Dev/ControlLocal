package com.controllocal.rest.seguridad;

import java.io.IOException;

import com.controllocal.rest.http.ErrorResponse;
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
public class CorsFilter implements Filter {

    private final String origenPermitido = cargarOrigen();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String origen = request.getHeader("Origin");

        if (origen != null && !origenPermitido.equalsIgnoreCase(origen)) {
            JsonUtils.responder(response, 403, new ErrorResponse("Origen no permitido."));
            return;
        }
        if (origen != null) {
            response.setHeader("Access-Control-Allow-Origin", origenPermitido);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
            response.setHeader("Access-Control-Max-Age", "600");
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        chain.doFilter(request, response);
    }

    private String cargarOrigen() {
        String configurado = ApiConfig.get(
                "api.cors.origin",
                "API_CORS_ORIGIN",
                Entorno.esProduccion() ? "" : "http://localhost:5232");
        if (configurado == null || configurado.isBlank()) {
            throw new IllegalStateException("api.cors.origin es obligatorio en produccion.");
        }
        if (Entorno.esProduccion() && "*".equals(configurado.trim())) {
            throw new IllegalStateException("API_CORS_ORIGIN no puede ser '*' en produccion.");
        }
        return configurado.trim();
    }
}
