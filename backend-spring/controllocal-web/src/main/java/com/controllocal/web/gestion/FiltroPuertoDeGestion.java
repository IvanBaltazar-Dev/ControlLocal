package com.controllocal.web.gestion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Separa las dos superficies <b>en los dos sentidos</b>, y las dos direcciones
 * importan:
 *
 * <ul>
 *   <li>la recuperacion de emergencia <b>solo</b> se atiende en el conector de
 *       gestion local. Pedirla por el puerto publico responde 404 —no 403—
 *       porque un 403 confirmaria que esa ruta existe;</li>
 *   <li>y por el conector de gestion <b>no se atiende nada mas</b>. Si por ahi
 *       pasara el API entero, el puerto de gestion seria una copia del producto
 *       sin autenticacion, que es exactamente lo contrario de lo que se
 *       pretende.</li>
 * </ul>
 *
 * <p>Se ejecuta <b>antes</b> de la cadena de seguridad: lo primero que hay que
 * decidir es si la peticion llego por donde debia.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "controllocal.recuperacion.habilitada", havingValue = "true")
public class FiltroPuertoDeGestion extends OncePerRequestFilter {

    private final int puertoGestion;

    public FiltroPuertoDeGestion(ConectorGestionLocal conector) {
        this.puertoGestion = conector.puerto();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean porGestion = request.getLocalPort() == puertoGestion;
        boolean esRutaDeGestion = request.getServletPath().startsWith(ConectorGestionLocal.RUTA_GESTION);

        if (porGestion != esRutaDeGestion) {
            // Mismo cuerpo en los dos casos: "aqui no hay nada".
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Recurso no encontrado.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
