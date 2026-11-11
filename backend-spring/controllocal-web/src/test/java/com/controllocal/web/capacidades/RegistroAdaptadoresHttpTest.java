package com.controllocal.web.capacidades;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.capacidades.RegistroDeCapacidades;
import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.web.seguridad.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.*;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.HandlerMapping;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistroAdaptadoresHttpTest {
    private final Actor agente = new Actor(1,2,3,Actor.AGENTE);
    private final Actor broker = new Actor(1,4,5,Actor.BROKER);

    static class AdaptadorFixture {
        @CapacidadReconocida("editar_propiedad") @PreAuthorize("hasRole('AGENTE')")
        public void editar() { fail("Describir no debe invocar el handler"); }
        @CapacidadReconocida({"abrir_sesion_captura","responder_captura"})
        public void captura() { fail("No ejecutar durante consulta"); }
        @CapacidadReconocida(value={"confirmar_captura","registrar_origen_fisico_unidad"},porActuacion=true)
        @PreAuthorize("hasRole('AGENTE')")
        public void confirmar() { fail("El interceptor no ejecuta la confirmación"); }
    }

    private record Montaje(RegistroAdaptadoresHttp registro, RequestMappingHandlerMapping mapping,
                           HandlerMethod editar, HandlerMethod captura,HandlerMethod confirmar,
                           ActuacionesService actuaciones) { }

    @SuppressWarnings("unchecked")
    private Montaje montar(String deshabilitadas) throws Exception {
        var provider=mock(ObjectProvider.class);
        var mapping=mock(RequestMappingHandlerMapping.class);
        when(provider.getObject()).thenReturn(mapping);
        var bean=new AdaptadorFixture();
        var editar=new HandlerMethod(bean,AdaptadorFixture.class.getMethod("editar"));
        var captura=new HandlerMethod(bean,AdaptadorFixture.class.getMethod("captura"));
        var confirmar=new HandlerMethod(bean,AdaptadorFixture.class.getMethod("confirmar"));
        when(mapping.getHandlerMethods()).thenReturn(Map.of(RequestMappingInfo.paths("/editar").build(),editar,
                RequestMappingInfo.paths("/captura").build(),captura,
                RequestMappingInfo.paths("/actuaciones/{referencia}/confirmacion").build(),confirmar));
        var actuaciones=mock(ActuacionesService.class);
        var proveedorActuaciones=mock(ObjectProvider.class);when(proveedorActuaciones.getObject()).thenReturn(actuaciones);
        var registro=new RegistroAdaptadoresHttp(provider,new DefaultMethodSecurityExpressionHandler(),
                new RegistroDeCapacidades(deshabilitadas),proveedorActuaciones);
        return new Montaje(registro,mapping,editar,captura,confirmar,actuaciones);
    }

    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test void reusaLaExpresionRealYReevaluaCadaActor() throws Exception {
        var r=montar("").registro();
        assertTrue(r.de("editar_propiedad",agente).permitido());
        assertFalse(r.de("editar_propiedad",broker).permitido());
        assertFalse(r.de("editar_propiedad",new Actor(1,6,7,Actor.TENANT_ADMIN)).permitido());
        assertTrue(r.de("editar_propiedad",agente).permitido());
    }

    @Test void indexaSoloMetadatosYNoRepiteElBarridoPorCadaCapacidad() throws Exception {
        var m=montar("");
        for(int i=0;i<25;i++)m.registro().de("editar_propiedad",agente);
        verify(m.mapping(),times(1)).getHandlerMethods();
    }

    @Test void desconocidaNoSeInfiereDeOtraRuta() throws Exception {
        assertFalse(montar("").registro().de("presentar_oferta",agente).reconocido());
    }

    @Test void deshabilitarUnAdaptadorCierraTambienLaPuertaRealYSusAliases() throws Exception {
        var m=montar("responder_captura");
        assertFalse(m.registro().de("abrir_sesion_captura",agente).habilitado());
        autenticar();
        assertThrows(CapacidadDeshabilitadaException.class,()->m.registro().preHandle(
                new MockHttpServletRequest(),new MockHttpServletResponse(),m.captura()));
    }
    private void autenticar() {
        var core=new com.controllocal.service.ContextosService.SesionAccesoDTO("A".repeat(43),1,
                new com.controllocal.service.ContextosService.IdentidadAutenticada(99,"fixture","fixture@cuentas.example.test","Fixture"),
                new com.controllocal.service.ContextosService.Contexto("a".repeat(64),1,2,3,"AGENTE",null,"Prueba"),
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),Instant.now().plusSeconds(1800).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                com.controllocal.service.EstadoDeAcceso.SIN_RESTRICCIONES);
        var sesion=new SesionDeRequest(new TokenService("").de(core),core);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(sesion,null,
                AuthorityUtils.createAuthorityList("ROLE_AGENTE")));
    }
    @Test void deshabilitarCapturaNoApagaOrigenNiElCasoInverso() throws Exception {
        for(String apagada:java.util.List.of("confirmar_captura","registrar_origen_fisico_unidad")) {
            var m=montar(apagada);autenticar();
            String activa=apagada.equals("confirmar_captura")?"registrar_origen_fisico_unidad":"confirmar_captura";
            assertTrue(m.registro().de(activa,agente).habilitado());assertFalse(m.registro().de(apagada,agente).habilitado());
            var req=new MockHttpServletRequest();req.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,Map.of("referencia","ref-propia"));
            req.setParameter("capacidad",apagada);when(m.actuaciones().capacidadDe("ref-propia",agente)).thenReturn(activa);
            assertTrue(m.registro().preHandle(req,new MockHttpServletResponse(),m.confirmar()));
            when(m.actuaciones().capacidadDe("ref-propia",agente)).thenReturn(apagada);
            req.setParameter("capacidad",activa);
            assertThrows(CapacidadDeshabilitadaException.class,()->m.registro().preHandle(req,new MockHttpServletResponse(),m.confirmar()));
        }
    }
    @Test void referenciaAjenaPropaga404SinElegirUnEfectoDelCuerpo() throws Exception {
        var m=montar("");autenticar();
        var req=new MockHttpServletRequest();req.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,Map.of("referencia","ajena"));
        when(m.actuaciones().capacidadDe("ajena",agente)).thenThrow(new NoEncontradoException("Actuación"));
        assertThrows(NoEncontradoException.class,()->m.registro().preHandle(req,new MockHttpServletResponse(),m.confirmar()));
    }
    @Test void unEfectoNoDeclaradoEnLaPuertaNoSeEjecuta() throws Exception {
        var m=montar("");autenticar();
        var req=new MockHttpServletRequest();req.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,Map.of("referencia","propia"));
        when(m.actuaciones().capacidadDe("propia",agente)).thenReturn("presentar_oferta");
        assertThrows(IllegalStateException.class,()->m.registro().preHandle(req,new MockHttpServletResponse(),m.confirmar()));
    }
}
