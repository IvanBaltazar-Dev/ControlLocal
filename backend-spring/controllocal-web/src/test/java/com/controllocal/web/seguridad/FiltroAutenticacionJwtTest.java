package com.controllocal.web.seguridad;

import com.controllocal.service.AutenticacionService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.OrganizacionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las dos comprobaciones que el filtro hace en cada peticion autenticada:
 *
 * <ol>
 *   <li><b>D-S0-12</b> — invalidacion de SESIONES (no confundir con la
 *       autorizacion de datos personales de D-27, que es una constancia del
 *       alta y no tiene flujo de revocacion). Un token <b>bien firmado y no
 *       expirado</b> deja de valer si se emitio antes de que la cuenta
 *       invalidara sus sesiones.</li>
 *   <li><b>§4.5</b> — sesion <b>capada</b> por contrasena temporal: existe,
 *       pero solo alcanza el perfil, el cambio de contrasena y el logout.</li>
 * </ol>
 */
class FiltroAutenticacionJwtTest {

    private static final long ORG = 1L;
    private static final long PERSONA = 7L;

    private final TokenService tokens = new TokenService("");
    private final OrganizacionService organizaciones = mock(OrganizacionService.class);
    private final AutenticacionService autenticacion = mock(AutenticacionService.class);
    private final FiltroAutenticacionJwt filtro =
            new FiltroAutenticacionJwt(tokens, organizaciones, autenticacion);

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("sin invalidacion, un token valido autentica")
    void tokenValidoAutentica() throws Exception {
        when(organizaciones.idOrganizacionActual()).thenReturn(ORG);
        when(autenticacion.estadoDeAcceso(ORG, PERSONA)).thenReturn(EstadoDeAcceso.SIN_RESTRICCIONES);

        HttpServletRequest request = pedirCon(tokenDe(Instant.now()));
        filtro.doFilter(request, mock(HttpServletResponse.class), mock(FilterChain.class));

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(request, never()).setAttribute(FiltroAutenticacionJwt.ATRIBUTO_TOKEN_INVALIDO, Boolean.TRUE);
    }

    @Test
    @DisplayName("un token EMITIDO ANTES de invalidar deja de valer aunque su firma sea buena")
    void tokenAnteriorALaInvalidacionNoAutentica() throws Exception {
        when(organizaciones.idOrganizacionActual()).thenReturn(ORG);
        // La cuenta cerro sesion hace un minuto; este token es de hace una hora.
        when(autenticacion.estadoDeAcceso(ORG, PERSONA))
                .thenReturn(new EstadoDeAcceso(OffsetDateTime.now().minusSeconds(60), false, false, null));

        HttpServletRequest request = pedirCon(tokenDe(Instant.now().minusSeconds(3600)));
        filtro.doFilter(request, mock(HttpServletResponse.class), mock(FilterChain.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "un token revocado no puede dejar sesion en el contexto");
        // Se marca como INVALIDO, no como ausente: el 401 sale con el mensaje
        // congelado "Token invalido o expirado." y no revela el motivo real.
        verify(request).setAttribute(FiltroAutenticacionJwt.ATRIBUTO_TOKEN_INVALIDO, Boolean.TRUE);
    }

    @Test
    @DisplayName("un token EMITIDO DESPUES de invalidar vale: el usuario volvio a entrar")
    void tokenPosteriorALaInvalidacionSiAutentica() throws Exception {
        when(organizaciones.idOrganizacionActual()).thenReturn(ORG);
        when(autenticacion.estadoDeAcceso(ORG, PERSONA))
                .thenReturn(new EstadoDeAcceso(OffsetDateTime.now().minusSeconds(3600), false, false, null));

        HttpServletRequest request = pedirCon(tokenDe(Instant.now()));
        filtro.doFilter(request, mock(HttpServletResponse.class), mock(FilterChain.class));

        assertNotNull(SecurityContextHolder.getContext().getAuthentication(),
                "invalidar sesiones no puede impedir volver a entrar");
    }

    @Test
    @DisplayName("el estado se consulta por la persona del TOKEN y dentro del tenant")
    void seConsultaPorPersonaYTenant() throws Exception {
        when(organizaciones.idOrganizacionActual()).thenReturn(ORG);
        when(autenticacion.estadoDeAcceso(anyLong(), anyLong()))
                .thenReturn(EstadoDeAcceso.SIN_RESTRICCIONES);

        filtro.doFilter(pedirCon(tokenDe(Instant.now())), mock(HttpServletResponse.class),
                mock(FilterChain.class));

        // Sin la organizacion, un id de persona de otra corredora resolveria
        // contra una credencial que no toca.
        verify(autenticacion).estadoDeAcceso(ORG, PERSONA);
    }

    @Test
    @DisplayName("una sola consulta por request, aunque sean dos comprobaciones")
    void unaSolaConsultaPorRequest() throws Exception {
        when(organizaciones.idOrganizacionActual()).thenReturn(ORG);
        when(autenticacion.estadoDeAcceso(ORG, PERSONA)).thenReturn(EstadoDeAcceso.SIN_RESTRICCIONES);

        filtro.doFilter(pedirCon(tokenDe(Instant.now())), mock(HttpServletResponse.class),
                mock(FilterChain.class));

        // Revocacion y capado son dos preguntas sobre la MISMA fila: separarlas
        // costaria dos consultas en el camino caliente.
        verify(autenticacion, times(1)).estadoDeAcceso(ORG, PERSONA);
    }

    @Test
    @DisplayName("sin cabecera Authorization no se consulta nada")
    void sinCabeceraNoSeConsultaNada() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        filtro.doFilter(request, mock(HttpServletResponse.class), mock(FilterChain.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        // Las rutas publicas no pueden pagar una consulta por request.
        verify(autenticacion, never()).estadoDeAcceso(anyLong(), anyLong());
    }

    @Test
    @DisplayName("la cadena sigue cuando el token es invalido: el 401 lo escribe el entry point")
    void laCadenaSigueConTokenInvalido() throws Exception {
        when(organizaciones.idOrganizacionActual()).thenReturn(ORG);
        when(autenticacion.estadoDeAcceso(ORG, PERSONA))
                .thenReturn(new EstadoDeAcceso(OffsetDateTime.now(), false, false, null));
        FilterChain cadena = mock(FilterChain.class);

        filtro.doFilter(pedirCon(tokenDe(Instant.now().minusSeconds(3600))),
                mock(HttpServletResponse.class), cadena);

        verify(cadena).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ------------------------------------------------------- §4.5 sesion capada

    @Test
    @DisplayName("con contrasena temporal, una operacion cualquiera responde 403 con codigo")
    void sesionCapadaRebotaConCodigo() throws Exception {
        when(organizaciones.idOrganizacionActual()).thenReturn(ORG);
        when(autenticacion.estadoDeAcceso(ORG, PERSONA))
                .thenReturn(new EstadoDeAcceso(null, true, false, null));

        StringWriter cuerpo = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(cuerpo));
        FilterChain cadena = mock(FilterChain.class);

        filtro.doFilter(pedirCon(tokenDe(Instant.now()), "GET", "/controllocal/Api/clientes"),
                response, cadena);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        // El SPA distingue por el CODIGO, no por el texto: el texto es
        // traducible y atarse a el rompe el cliente en cuanto se reescribe.
        assertTrue(cuerpo.toString().contains(FiltroAutenticacionJwt.CODIGO_CAMBIO_OBLIGATORIO),
                cuerpo.toString());
        // Y la cadena NO sigue: el 403 lo escribe el propio filtro.
        verify(cadena, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("con contrasena temporal SI pasan el perfil, el cambio y el logout")
    void sesionCapadaDejaPasarLoImprescindible() throws Exception {
        when(organizaciones.idOrganizacionActual()).thenReturn(ORG);
        when(autenticacion.estadoDeAcceso(ORG, PERSONA))
                .thenReturn(new EstadoDeAcceso(null, true, false, null));

        // Sin estas tres, la pantalla de cambio obligatorio no podria ni
        // pintarse ni resolverse, y el usuario quedaria encerrado en una
        // sesion de la que tampoco puede salir.
        for (String[] permitida : new String[][]{
                {"POST", "/controllocal/Api/perfil/contrasena"},
                {"GET", "/controllocal/Api/perfil"},
                {"POST", "/controllocal/Api/auth/logout"}}) {
            SecurityContextHolder.clearContext();
            filtro.doFilter(pedirCon(tokenDe(Instant.now()), permitida[0], permitida[1]),
                    mock(HttpServletResponse.class), mock(FilterChain.class));
            assertNotNull(SecurityContextHolder.getContext().getAuthentication(),
                    permitida[0] + " " + permitida[1] + " tiene que pasar con la sesion capada");
        }
    }

    @Test
    @DisplayName("la revocacion gana al capado: un token muerto no llega ni al 403")
    void revocacionAntesQueCapado() throws Exception {
        when(organizaciones.idOrganizacionActual()).thenReturn(ORG);
        when(autenticacion.estadoDeAcceso(ORG, PERSONA))
                .thenReturn(new EstadoDeAcceso(OffsetDateTime.now().minusSeconds(60), true, false, null));

        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpServletRequest request = pedirCon(tokenDe(Instant.now().minusSeconds(3600)),
                "GET", "/controllocal/Api/clientes");
        filtro.doFilter(request, response, mock(FilterChain.class));

        // 401, no 403: el token ya no vale, y decir "cambia tu contrasena"
        // seria admitir que la sesion existe.
        verify(request).setAttribute(FiltroAutenticacionJwt.ATRIBUTO_TOKEN_INVALIDO, Boolean.TRUE);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("el mensaje del 401 es el congelado y no distingue revocado de corrupto")
    void elMensajeDelCuatrocientosUnoNoRevelaElMotivo() {
        // Contrato congelado: el atributo es uno solo, asi que un token
        // revocado y uno manipulado producen exactamente la misma respuesta.
        // Un mensaje distinto seria un oraculo sobre el estado de la cuenta.
        assertEquals("controllocal.token.invalido", FiltroAutenticacionJwt.ATRIBUTO_TOKEN_INVALIDO);
    }

    /**
     * Token firmado con el `iat` pedido. Se construye por el camino real
     * (emitir + firmar) y se corrige el instante, para que la prueba ejercite
     * el mismo formato que produce el login.
     */
    private String tokenDe(Instant emitidoEn) {
        TokenService.Sesion base = tokens.emitir("vmora", "AGENTE", PERSONA, 101);
        Instant emitido = emitidoEn.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return tokens.firmar(new TokenService.Sesion(base.usuario(), base.rol(), base.idUsuario(),
                base.idDominio(), emitido, Instant.now().plusSeconds(TokenService.DURACION_SEGUNDOS)));
    }

    private HttpServletRequest pedirCon(String jwt) {
        return pedirCon(jwt, "GET", "/controllocal/Api/clientes");
    }

    private HttpServletRequest pedirCon(String jwt, String metodo, String ruta) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + jwt);
        when(request.getMethod()).thenReturn(metodo);
        when(request.getRequestURI()).thenReturn(ruta);
        return request;
    }
}
