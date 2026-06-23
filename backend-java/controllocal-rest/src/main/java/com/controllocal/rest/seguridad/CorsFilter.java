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
public class CorsFilter implements Filter {

    private final String origenPermitido = cargarOrigen();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String origen = request.getHeader("Origin");

        if (origen != null && !origenPermitido.equalsIgnoreCase(origen)) {
            JsonUtils.responderError(response, 403, "Origen no permitido.");
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

    // Origen del frontend local. Se puede sobrescribir con api.cors.origin si el
    // frontend corre en otro puerto; por defecto el Blazor Server de desarrollo.
    private String cargarOrigen() {
        String configurado = ApiConfig.get("api.cors.origin", "API_CORS_ORIGIN", "http://localhost:5232");
        return configurado == null || configurado.isBlank()
                ? "http://localhost:5232"
                : configurado.trim();
    }
}
