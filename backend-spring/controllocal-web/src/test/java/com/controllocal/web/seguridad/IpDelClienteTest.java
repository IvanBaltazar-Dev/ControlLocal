package com.controllocal.web.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * IP real del cliente para el bloqueo por IP (D-S0-21).
 *
 * <p>El eje de esta suite es una sola idea: <b>{@code X-Forwarded-For} solo
 * vale si viene de un proxy declarado</b>. Creerle a cualquiera convierte el
 * bloqueo por IP en un adorno — basta variar la cabecera en cada intento.
 */
class IpDelClienteTest {

    private static final String PROXY = "10.0.0.1";

    @Test
    @DisplayName("sin proxies declarados se usa la IP del socket, venga o no la cabecera")
    void sinProxiesDeclaradosSeIgnoraLaCabecera() {
        IpDelCliente resolutor = new IpDelCliente("");

        // Es el caso de hoy, sin NGINX: se comporta exactamente como antes.
        assertEquals("203.0.113.9",
                resolutor.de(pedir("203.0.113.9", "1.2.3.4")));
    }

    @Test
    @DisplayName("una cabecera FALSIFICADA desde una IP cualquiera no se cree")
    void laCabeceraFalsificadaNoSeCree() {
        IpDelCliente resolutor = new IpDelCliente(PROXY);

        // El atacante manda X-Forwarded-For distinto en cada intento para
        // esquivar el contador. Como su conexion no viene del proxy, no cuela.
        assertEquals("203.0.113.9",
                resolutor.de(pedir("203.0.113.9", "9.9.9.9")));
    }

    @Test
    @DisplayName("desde un proxy declarado SI se lee la cabecera")
    void desdeUnProxyDeclaradoSeLee() {
        IpDelCliente resolutor = new IpDelCliente(PROXY);

        // Sin esto, detras de NGINX todo el trafico compartiria una sola IP
        // — un unico cupo para todo el mundo (H-07).
        assertEquals("198.51.100.23",
                resolutor.de(pedir(PROXY, "198.51.100.23")));
    }

    @Test
    @DisplayName("de la lista se toma el PRIMERO: el cliente original")
    void seTomaElPrimeroDeLaLista() {
        IpDelCliente resolutor = new IpDelCliente(PROXY);

        assertEquals("198.51.100.23",
                resolutor.de(pedir(PROXY, "198.51.100.23, 10.0.0.1, 10.0.0.2")));
    }

    @Test
    @DisplayName("varios proxies declarados, separados por coma")
    void admiteVariosProxies() {
        IpDelCliente resolutor = new IpDelCliente("10.0.0.1, 10.0.0.2");

        assertEquals("198.51.100.23", resolutor.de(pedir("10.0.0.2", "198.51.100.23")));
    }

    @Test
    @DisplayName("una cabecera vacia o absurda cae a la IP del socket")
    void cabeceraInutilCaeAlSocket() {
        IpDelCliente resolutor = new IpDelCliente(PROXY);

        assertEquals(PROXY, resolutor.de(pedir(PROXY, null)));
        assertEquals(PROXY, resolutor.de(pedir(PROXY, "   ")));
        // Mas larga que la columna: la escribe alguien de fuera, asi que se
        // descarta en vez de reventar la insercion.
        assertEquals(PROXY, resolutor.de(pedir(PROXY, "x".repeat(60))));
    }

    private static HttpServletRequest pedir(String remota, String reenviada) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remota);
        when(request.getHeader("X-Forwarded-For")).thenReturn(reenviada);
        return request;
    }
}
