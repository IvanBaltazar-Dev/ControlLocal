package com.controllocal.web.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * IP real del cliente, para el bloqueo por IP (D-S0-21).
 *
 * <p><b>El problema que resuelve</b> (H-07): hoy se lee
 * {@code request.getRemoteAddr()}, y detras de un proxy <b>todo el trafico
 * comparte una sola IP</b> — o sea, un solo cupo para todo el mundo. La Fase 5
 * introduce NGINX, asi que esto pasa de latente a real.
 *
 * <p><b>Y el problema que NO se crea al resolverlo</b>: confiar en
 * {@code X-Forwarded-For} sin lista blanca es peor que no leerla. Cualquiera
 * puede mandar esa cabecera, y creerle convierte el bloqueo por IP en un
 * adorno: basta variar la cabecera en cada intento. Por eso la cabecera
 * <b>solo</b> se lee cuando la conexion viene de un proxy declarado en
 * configuracion; si no, se usa la IP del socket.
 *
 * <p>Sin proxies declarados —el caso de hoy, sin NGINX— se comporta
 * exactamente como antes.
 */
@Component
public class IpDelCliente {

    private final Set<String> proxiesDeConfianza;

    public IpDelCliente(@Value("${controllocal.seguridad.proxies-de-confianza:}") String configurados) {
        this.proxiesDeConfianza = configurados == null || configurados.isBlank()
                ? Set.of()
                : new LinkedHashSet<>(Arrays.stream(configurados.split(","))
                        .map(String::trim)
                        .filter(valor -> !valor.isEmpty())
                        .toList());
    }

    public String de(HttpServletRequest request) {
        String directa = request.getRemoteAddr();
        if (!proxiesDeConfianza.contains(directa)) {
            // La conexion no viene de un proxy conocido: su cabecera no vale
            // nada, venga o no venga.
            return directa;
        }
        String reenviada = primeraDe(request.getHeader("X-Forwarded-For"));
        return reenviada != null ? reenviada : directa;
    }

    /**
     * {@code X-Forwarded-For} es una lista "cliente, proxy1, proxy2". El
     * cliente original es el PRIMERO; los siguientes los anadio cada salto.
     */
    private static String primeraDe(String cabecera) {
        if (cabecera == null || cabecera.isBlank()) {
            return null;
        }
        String primera = cabecera.split(",")[0].trim();
        // Recorte defensivo: la columna admite 45 (IPv6) y la cabecera la
        // escribe alguien de fuera.
        if (primera.isEmpty() || primera.length() > 45) {
            return null;
        }
        return primera;
    }
}
