package com.controllocal.web.seguridad;

import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.EstadoDeAcceso;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** La firma no sustituye sesión durable, revisión ni atribución vigente. */
class FiltroAutenticacionJwtTest {
    private static final String SID="s".repeat(43);
    private final TokenService tokens=new TokenService("");
    private final ContextosService contextos=mock(ContextosService.class);
    private final FiltroAutenticacionJwt filtro=new FiltroAutenticacionJwt(tokens,new RevalidacionDeSesion(contextos));
    private ContextosService.SesionAccesoDTO actual;
    private TokenService.Sesion token;
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
    private void preparar(String rol,EstadoDeAcceso estado) {
        Instant iat=Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var identidad=new ContextosService.IdentidadAutenticada(1000,"persona","persona@cuentas.example.test","Persona");
        var activo=rol==null?null:new ContextosService.Contexto("opaco",92,99,880,rol,"EMPRESARIAL","Agencia");
        actual=new ContextosService.SesionAccesoDTO(SID,4,identidad,activo,iat,iat.plusSeconds(TokenService.DURACION_SEGUNDOS),estado);
        token=tokens.de(actual);
        when(contextos.comprobarSesion(SID,4,actual.emitidaEn(),actual.venceEn())).thenReturn(actual);
    }
    private MockHttpServletRequest peticion(String metodo,String ruta) {
        var r=new MockHttpServletRequest(metodo,ruta);r.addHeader(HttpHeaders.AUTHORIZATION,"Bearer "+tokens.firmar(token));return r;
    }
    private void pasan(String[][] rutas)throws Exception {
        for(String[] r:rutas){
            SecurityContextHolder.clearContext();var cadena=mock(FilterChain.class);
            filtro.doFilter(peticion(r[0],r[1]),new MockHttpServletResponse(),cadena);
            assertNotNull(SecurityContextHolder.getContext().getAuthentication(),r[1]);verify(cadena).doFilter(any(),any());
        }
    }
    @Test void autenticaContextoElegidoYActorUsaPersonaLocal()throws Exception {
        preparar(Actor.AGENTE,EstadoDeAcceso.SIN_RESTRICCIONES);
        var request=peticion("GET","/controllocal/Api/clientes");var cadena=mock(FilterChain.class);
        filtro.doFilter(request,new MockHttpServletResponse(),cadena);
        assertEquals(new Actor(92,99,880,Actor.AGENTE),SesionActual.actor());assertEquals(1000,SesionActual.idCuenta());
        assertNull(request.getAttribute(FiltroAutenticacionJwt.ATRIBUTO_TOKEN_INVALIDO));verify(cadena).doFilter(any(),any());
    }
    @Test void cadaRequestReconsultaSesionYRevisionSinCache()throws Exception {
        preparar(Actor.AGENTE,EstadoDeAcceso.SIN_RESTRICCIONES);
        pasan(new String[][]{{"GET","/clientes"},{"GET","/clientes"}});
        verify(contextos,times(2)).comprobarSesion(SID,4,token.emitidoEn(),token.expiraEn());
    }
    @Test void sinBearerNoConsultaBaseNiPublicaPrincipal()throws Exception {
        var cadena=mock(FilterChain.class);
        filtro.doFilter(new MockHttpServletRequest("GET","/publico"),new MockHttpServletResponse(),cadena);
        verifyNoInteractions(contextos);assertNull(SecurityContextHolder.getContext().getAuthentication());verify(cadena).doFilter(any(),any());
    }
    @Test void firmaAlteradaSeMarcaInvalidaSinConsultarSesion()throws Exception {
        var request=new MockHttpServletRequest("GET","/clientes");request.addHeader(HttpHeaders.AUTHORIZATION,"Bearer a.b.c");
        var cadena=mock(FilterChain.class);filtro.doFilter(request,new MockHttpServletResponse(),cadena);
        verifyNoInteractions(contextos);assertEquals(Boolean.TRUE,request.getAttribute(FiltroAutenticacionJwt.ATRIBUTO_TOKEN_INVALIDO));
        assertNull(SecurityContextHolder.getContext().getAuthentication());verify(cadena).doFilter(any(),any());
    }
    @Test void sesionRevocadaORevisionAnteriorNoAutenticaConFirmaValida()throws Exception {
        preparar(Actor.AGENTE,EstadoDeAcceso.SIN_RESTRICCIONES);
        when(contextos.comprobarSesion(SID,4,token.emitidoEn(),token.expiraEn())).thenThrow(new CredencialesInvalidasException());
        var request=peticion("GET","/clientes");var cadena=mock(FilterChain.class);
        filtro.doFilter(request,new MockHttpServletResponse(),cadena);
        assertEquals(Boolean.TRUE,request.getAttribute(FiltroAutenticacionJwt.ATRIBUTO_TOKEN_INVALIDO));
        assertNull(SecurityContextHolder.getContext().getAuthentication());verify(cadena).doFilter(any(),any());
    }
    @Test void falloTecnicoNoSeConfundeConCredencialesInvalidas(){
        preparar(Actor.AGENTE,EstadoDeAcceso.SIN_RESTRICCIONES);
        when(contextos.comprobarSesion(SID,4,token.emitidoEn(),token.expiraEn())).thenThrow(new IllegalStateException("fixture base"));
        var request=peticion("GET","/clientes");
        assertThrows(IllegalStateException.class,()->filtro.doFilter(request,new MockHttpServletResponse(),mock(FilterChain.class)));
        assertNull(request.getAttribute(FiltroAutenticacionJwt.ATRIBUTO_TOKEN_INVALIDO));assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void identidadSinContextoNoAbreNegocioNiCatalogos()throws Exception {
        preparar(null,EstadoDeAcceso.SIN_RESTRICCIONES);var response=new MockHttpServletResponse();var cadena=mock(FilterChain.class);
        filtro.doFilter(peticion("GET","/capacidades"),response,cadena);
        assertEquals(409,response.getStatus());assertTrue(response.getContentAsString().contains("CONTEXTO_REQUERIDO"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());verifyNoInteractions(cadena);
    }
    @Test void identidadPuedeConsultarOpcionesSeleccionarYSalir()throws Exception {
        preparar(null,EstadoDeAcceso.SIN_RESTRICCIONES);
        pasan(new String[][]{{"GET","/sesion"},{"GET","/sesion/contextos"},{"POST","/sesion/contexto"},{"POST","/auth/logout"}});
        assertEquals("IDENTIDAD",SesionActual.sesion().rolEfectivo());
        assertThrows(com.controllocal.service.excepcion.ConflictoException.class,SesionActual::actor);
    }
    @Test void identidadEntraSoloPorMetodosYRutasExactosDelPanelFinanciero()throws Exception {
        preparar(null,EstadoDeAcceso.SIN_RESTRICCIONES);String id="d42bb234-e158-4a8e-83b2-d73ebfbb824e";
        pasan(new String[][]{{"GET","/plataforma/atribuciones/compras"},{"GET","/plataforma/compras"},{"GET","/plataforma/compras/"+id},
                {"POST","/plataforma/compras/"+id+"/verificacion"},{"POST","/plataforma/compras/"+id+"/fondos"},
                {"POST","/plataforma/compras/"+id+"/acreditacion"},{"POST","/plataforma/compras/"+id+"/incidencia"}});
        for(String[] r:new String[][]{{"DELETE","/plataforma/compras/"+id},{"POST","/plataforma/compras"},{"GET","/plataforma/cartera"},
                {"GET","/otra/plataforma/compras"},{"POST","/plataforma/compras/"+id+"/saldo"},{"GET","/compras/catalogo"}}){
            SecurityContextHolder.clearContext();var respuesta=new MockHttpServletResponse();var cadena=mock(FilterChain.class);
            filtro.doFilter(peticion(r[0],r[1]),respuesta,cadena);assertEquals(409,respuesta.getStatus(),r[1]);verifyNoInteractions(cadena);
        }
    }
    @Test void panelFinancieroNoEvitaContrasenaOMfaPendientes()throws Exception {
        for(var estado:new EstadoDeAcceso[]{new EstadoDeAcceso(null,true,false,"IDENTIDAD"),new EstadoDeAcceso(null,false,true,"IDENTIDAD")}){
            preparar(null,estado);SecurityContextHolder.clearContext();var respuesta=new MockHttpServletResponse();var cadena=mock(FilterChain.class);
            filtro.doFilter(peticion("GET","/plataforma/atribuciones/compras"),respuesta,cadena);assertEquals(403,respuesta.getStatus());verifyNoInteractions(cadena);
        }
    }
    @Test void metricasIaConservanIdentidadYAllowlistExactaSinActorFicticio()throws Exception {
        preparar(null,EstadoDeAcceso.SIN_RESTRICCIONES);String id="d42bb234-e158-4a8e-83b2-d73ebfbb824e";
        String tecnica="/plataforma/metricas-ia/tecnicas",financiera="/plataforma/metricas-ia/financieras/"+id+"/"+id;
        pasan(new String[][]{{"GET",tecnica},{"GET",financiera},{"POST",financiera+"/confirmaciones-costo"},{"POST",financiera+"/aplicacion-costo"}});
        assertEquals("IDENTIDAD",SesionActual.sesion().rolEfectivo());
        assertThrows(com.controllocal.service.excepcion.ConflictoException.class,SesionActual::actor);
        for(String[] r:new String[][]{{"POST",tecnica},{"POST",financiera},{"DELETE",financiera},{"GET",financiera+"/exportar"},
                {"GET",financiera+"/confirmaciones-costo"},{"PATCH",financiera+"/aplicacion-costo"},
                {"POST",financiera+"/confirmaciones-costo/otra"},{"POST","/otra"+financiera+"/aplicacion-costo"},
                {"GET","/otra"+tecnica},{"GET",tecnica+"/"},{"GET","/plataforma/metricas-ia/financieras/"+id},
                {"GET","/plataforma/metricas-ia"},{"GET","/plataforma/cartera"}}){
            SecurityContextHolder.clearContext();var respuesta=new MockHttpServletResponse();var cadena=mock(FilterChain.class);
            filtro.doFilter(peticion(r[0],r[1]),respuesta,cadena);assertEquals(409,respuesta.getStatus(),r[1]);verifyNoInteractions(cadena);
        }
    }
    @Test void metricasIaNoEvitanMfaOCambioDeContrasenaPendientes()throws Exception {
        for(var estado:new EstadoDeAcceso[]{new EstadoDeAcceso(null,true,false,"IDENTIDAD"),new EstadoDeAcceso(null,false,true,"IDENTIDAD")}){
            preparar(null,estado);String id="d42bb234-e158-4a8e-83b2-d73ebfbb824e";
            for(String ruta:new String[]{"/plataforma/metricas-ia/tecnicas","/plataforma/metricas-ia/financieras/"+id+"/"+id}){
                SecurityContextHolder.clearContext();var respuesta=new MockHttpServletResponse();var cadena=mock(FilterChain.class);
                filtro.doFilter(peticion("GET",ruta),respuesta,cadena);assertEquals(403,respuesta.getStatus());verifyNoInteractions(cadena);
            }
            for(String accion:new String[]{"confirmaciones-costo","aplicacion-costo"}){
                SecurityContextHolder.clearContext();var respuesta=new MockHttpServletResponse();var cadena=mock(FilterChain.class);
                filtro.doFilter(peticion("POST","/plataforma/metricas-ia/financieras/"+id+"/"+id+"/"+accion),respuesta,cadena);
                assertEquals(403,respuesta.getStatus());verifyNoInteractions(cadena);
            }
        }
    }
    @Test void identidadSoloAceptaLaRutaExactaDeInvitacionSinAbrirGobierno()throws Exception {
        preparar(null,EstadoDeAcceso.SIN_RESTRICCIONES);
        pasan(new String[][]{{"POST","/membresias/invitaciones/d42bb234-e158-4a8e-83b2-d73ebfbb824e/aceptar"}});
        for(String ruta:new String[]{"/membresias/invitaciones","/membresias/invitaciones/d42bb234-e158-4a8e-83b2-d73ebfbb824e/retirar","/otra/membresias/invitaciones/d42bb234-e158-4a8e-83b2-d73ebfbb824e/aceptar"}){
            SecurityContextHolder.clearContext();var respuesta=new MockHttpServletResponse();var cadena=mock(FilterChain.class);
            filtro.doFilter(peticion("POST",ruta),respuesta,cadena);assertEquals(409,respuesta.getStatus(),ruta);verifyNoInteractions(cadena);
        }
    }
    @Test void contrasenaTemporalImpideOperarConCodigo()throws Exception {
        preparar(Actor.AGENTE,new EstadoDeAcceso(null,true,false,Actor.AGENTE));
        var response=new MockHttpServletResponse();var cadena=mock(FilterChain.class);
        filtro.doFilter(peticion("GET","/clientes"),response,cadena);
        assertEquals(403,response.getStatus());assertTrue(response.getContentAsString().contains(FiltroAutenticacionJwt.CODIGO_CAMBIO_OBLIGATORIO));
        verifyNoInteractions(cadena);assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void contrasenaTemporalConservaPerfilCambioLogoutYSeleccion()throws Exception {
        preparar(Actor.AGENTE,new EstadoDeAcceso(null,true,false,Actor.AGENTE));
        pasan(new String[][]{{"GET","/cuenta"},{"POST","/cuenta/contrasena"},{"POST","/auth/logout"},{"GET","/sesion/contextos"},{"POST","/sesion/contexto"}});
    }
    @Test void mfaPendienteImpideNegocioYConservaEnrolamientoPropio()throws Exception {
        preparar(Actor.TENANT_ADMIN,new EstadoDeAcceso(null,false,true,Actor.TENANT_ADMIN));
        var response=new MockHttpServletResponse();var cadena=mock(FilterChain.class);
        filtro.doFilter(peticion("GET","/clientes"),response,cadena);
        assertEquals(403,response.getStatus());assertTrue(response.getContentAsString().contains(FiltroAutenticacionJwt.CODIGO_ENROLAMIENTO_MFA));verifyNoInteractions(cadena);
        pasan(new String[][]{{"GET","/cuenta/mfa"},{"POST","/cuenta/mfa"},{"POST","/cuenta/mfa/confirmar"},{"POST","/auth/logout"}});
    }
    @Test void identidadCapadaPorMfaPuedeElegirContextoAntesDeEnrolarSinAbrirNegocio()throws Exception {
        preparar(null,new EstadoDeAcceso(null,false,true,"IDENTIDAD"));
        pasan(new String[][]{{"GET","/sesion"},{"GET","/sesion/contextos"},{"POST","/sesion/contexto"},{"POST","/auth/logout"}});
    }
    @Test void contrasenaTemporalDebeCambiarseAntesDeEnrolarMfa()throws Exception {
        preparar(Actor.TENANT_ADMIN,new EstadoDeAcceso(null,true,true,Actor.TENANT_ADMIN));
        var response=new MockHttpServletResponse();var cadena=mock(FilterChain.class);
        filtro.doFilter(peticion("POST","/cuenta/mfa"),response,cadena);
        assertEquals(403,response.getStatus());assertTrue(response.getContentAsString().contains(FiltroAutenticacionJwt.CODIGO_CAMBIO_OBLIGATORIO));
        verifyNoInteractions(cadena);
        pasan(new String[][]{{"POST","/cuenta/contrasena"},{"GET","/cuenta"},{"POST","/auth/logout"}});
    }
    @Test void revocacionPrecedeContrasenaYMfaPendientes()throws Exception {
        preparar(Actor.AGENTE,new EstadoDeAcceso(null,true,true,Actor.AGENTE));
        when(contextos.comprobarSesion(SID,4,token.emitidoEn(),token.expiraEn())).thenThrow(new CredencialesInvalidasException());
        var request=peticion("GET","/clientes");var response=new MockHttpServletResponse();filtro.doFilter(request,response,mock(FilterChain.class));
        assertEquals(Boolean.TRUE,request.getAttribute(FiltroAutenticacionJwt.ATRIBUTO_TOKEN_INVALIDO));assertNotEquals(403,response.getStatus());
    }
    @Test void claimsDeOtroRolNoSeAceptanComoCambioImplicito()throws Exception {
        preparar(Actor.TENANT_ADMIN,EstadoDeAcceso.SIN_RESTRICCIONES);
        token=new TokenService.Sesion(token.usuario(),Actor.AGENTE,token.idUsuario(),token.idDominio(),token.emitidoEn(),token.expiraEn(),SID,4);
        var request=peticion("GET","/clientes");filtro.doFilter(request,new MockHttpServletResponse(),mock(FilterChain.class));
        assertEquals(Boolean.TRUE,request.getAttribute(FiltroAutenticacionJwt.ATRIBUTO_TOKEN_INVALIDO));assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void gobiernoPublicaSoloSuAtribucionSinHerenciaBroker()throws Exception {
        preparar(Actor.TENANT_ADMIN,EstadoDeAcceso.SIN_RESTRICCIONES);
        filtro.doFilter(peticion("GET","/organizacion"),new MockHttpServletResponse(),mock(FilterChain.class));
        var a=SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().map(Object::toString).toList();
        assertEquals(java.util.List.of("ROLE_TENANT_ADMIN"),a);
    }
    @Test void mercadoSoloAbrePuertasPlataformaExactasConIdentidad()throws Exception {
        preparar(null,EstadoDeAcceso.SIN_RESTRICCIONES);String base="/plataforma/mercado",config=base+"/configuraciones/d42bb234-e158-4a8e-83b2-d73ebfbb824e/TEST_V1",trabajo=base+"/trabajos/123";
        pasan(new String[][]{{"GET",base+"/atribuciones"},{"POST",base+"/configuraciones"},{"GET",config},{"POST",config+"/retiro"},{"POST",base+"/mantenimiento"},{"GET",base+"/colas/ejecucion"},{"GET",base+"/colas/revision"},{"GET",base+"/colas/finanzas"},{"GET",trabajo+"/operador"},{"GET",trabajo+"/revision"},{"GET",trabajo+"/finanzas"},{"POST",trabajo+"/tomar"},{"POST",trabajo+"/entrega"},{"POST",trabajo+"/revision"},{"POST",trabajo+"/publicacion"},{"POST",trabajo+"/liquidacion"},{"POST",trabajo+"/liberacion-no-iniciada"}});
        for(String[] r:new String[][]{{"DELETE",trabajo+"/revision"},{"GET",trabajo+"/entrega"},{"GET",base+"/trabajos/0/revision"},{"GET","/otra"+base+"/atribuciones"},{"GET",trabajo+"/revision/extra"},{"POST",config},{"GET","/mercado/referencias/d42bb234-e158-4a8e-83b2-d73ebfbb824e/TEST_V1"}}){SecurityContextHolder.clearContext();var response=new MockHttpServletResponse();var cadena=mock(FilterChain.class);filtro.doFilter(peticion(r[0],r[1]),response,cadena);assertEquals(409,response.getStatus(),r[1]);verifyNoInteractions(cadena);}
    }
    @Test void mercadoNoEvitaMfaOCambioDeContrasenaPendientes()throws Exception {
        for(var estado:new EstadoDeAcceso[]{new EstadoDeAcceso(null,true,false,"IDENTIDAD"),new EstadoDeAcceso(null,false,true,"IDENTIDAD")}){preparar(null,estado);var response=new MockHttpServletResponse();var cadena=mock(FilterChain.class);filtro.doFilter(peticion("GET","/plataforma/mercado/atribuciones"),response,cadena);assertEquals(403,response.getStatus());verifyNoInteractions(cadena);}
    }

    @Test void versionDeMercadoNoPuedeConfundirseConRutaDeCuentaOSesion()throws Exception {
        for(var estado:new EstadoDeAcceso[]{new EstadoDeAcceso(null,true,false,"IDENTIDAD"),new EstadoDeAcceso(null,false,true,"IDENTIDAD")}){
            preparar(null,estado);
            for(String version:new String[]{"cuenta","sesion"}){var respuesta=new MockHttpServletResponse();var cadena=mock(FilterChain.class);filtro.doFilter(peticion("GET","/plataforma/mercado/configuraciones/d42bb234-e158-4a8e-83b2-d73ebfbb824e/"+version),respuesta,cadena);assertEquals(403,respuesta.getStatus());verifyNoInteractions(cadena);}
        }
    }
    @Test void rutasDeCuentaMantienenContextPathRealYSinAliasPorSufijo()throws Exception {
        preparar(null,EstadoDeAcceso.SIN_RESTRICCIONES);var correcta=peticion("GET","/api/cuenta");correcta.setContextPath("/api");var respuesta=new MockHttpServletResponse();var cadena=mock(FilterChain.class);filtro.doFilter(correcta,respuesta,cadena);verify(cadena).doFilter(correcta,respuesta);
        SecurityContextHolder.clearContext();respuesta=new MockHttpServletResponse();cadena=mock(FilterChain.class);filtro.doFilter(peticion("GET","/otra/cuenta"),respuesta,cadena);assertEquals(409,respuesta.getStatus());verifyNoInteractions(cadena);
    }

}

