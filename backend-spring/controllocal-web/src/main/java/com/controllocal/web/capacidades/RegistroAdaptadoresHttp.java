package com.controllocal.web.capacidades;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.capacidades.AdaptadoresDeCapacidad;
import com.controllocal.service.capacidades.RegistroDeCapacidades;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.web.seguridad.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.*;

/** Lee la política REAL del handler. No mantiene una segunda tabla de bandas o rutas. */
@Component
public class RegistroAdaptadoresHttp implements AdaptadoresDeCapacidad, HandlerInterceptor {
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;
    private final MethodSecurityExpressionHandler expresiones;
    private final RegistroDeCapacidades registro;
    private final ObjectProvider<ActuacionesService> actuaciones;
    private volatile Map<String, List<HandlerMethod>> indice;

    public RegistroAdaptadoresHttp(ObjectProvider<RequestMappingHandlerMapping> mappings,
                                   MethodSecurityExpressionHandler expresiones, RegistroDeCapacidades registro,
                                   ObjectProvider<ActuacionesService> actuaciones) {
        this.mappings = mappings;
        this.expresiones = expresiones;
        this.registro = registro;
        this.actuaciones=actuaciones;
    }

    public Map<String, List<HandlerMethod>> reconocidos() {
        Map<String, List<HandlerMethod>> existente = indice;
        if (existente != null) return existente;
        synchronized (this) {
            if (indice == null) indice = indexar();
            return indice;
        }
    }

    private Map<String, List<HandlerMethod>> indexar() {
        Map<String, List<HandlerMethod>> resultado = new LinkedHashMap<>();
        // Se resuelve al atender: no fuerza la creación circular de controladores en el arranque.
        for (HandlerMethod handler : mappings.getObject().getHandlerMethods().values()) {
            CapacidadReconocida anotacion = handler.getMethodAnnotation(CapacidadReconocida.class);
            if (anotacion == null) continue;
            for (String id : anotacion.value()) {
                registro.de(id); // Un ID mal escrito es un defecto, no una capacidad inferida.
                resultado.computeIfAbsent(id, k -> new ArrayList<>()).add(handler);
            }
        }
        Map<String, List<HandlerMethod>> inmutable = new LinkedHashMap<>();
        resultado.forEach((id, handlers) -> inmutable.put(id, List.copyOf(handlers)));
        return Collections.unmodifiableMap(inmutable);
    }

    @Override
    public Disponibilidad de(String idCore, Actor actor) {
        List<HandlerMethod> handlers = reconocidos().get(idCore);
        if (handlers == null || handlers.isEmpty()) return new Disponibilidad(false, false, false);
        return new Disponibilidad(true, handlers.stream().allMatch(h -> permite(h, actor)),
                handlers.stream().allMatch(h -> registro.habilitadas(h.getMethodAnnotation(CapacidadReconocida.class).porActuacion()
                        ? List.of(idCore) : ids(h))));
    }

    private static List<String> ids(HandlerMethod handler) {
        return List.of(handler.getMethodAnnotation(CapacidadReconocida.class).value());
    }

    private boolean permite(HandlerMethod handler, Actor actor) {
        PreAuthorize politica = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
        if (politica == null) politica = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
        if (politica == null) return true; // El handler exige la sesión por la cadena de seguridad.
        var autenticacion = new UsernamePasswordAuthenticationToken(actor, null,
                AuthorityUtils.createAuthorityList("ROLE_" + actor.rolEfectivo()));
        MethodInvocation invocacion = new MethodInvocation() {
            public Method getMethod() { return handler.getMethod(); }
            public Object[] getArguments() { return new Object[getMethod().getParameterCount()]; }
            public Object proceed() { throw new UnsupportedOperationException("Una consulta de capacidad no ejecuta el caso."); }
            public Object getThis() { return handler.createWithResolvedBean().getBean(); }
            public AccessibleObject getStaticPart() { return getMethod(); }
        };
        var contexto = expresiones.createEvaluationContext(autenticacion, invocacion);
        Boolean permitida = expresiones.getExpressionParser().parseExpression(politica.value())
                .getValue(contexto, Boolean.class);
        return Boolean.TRUE.equals(permitida);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object destino) {
        if (destino instanceof HandlerMethod handler && handler.hasMethodAnnotation(CapacidadReconocida.class)) {
            // La bandera se exige también en la puerta real: ningún cliente la elude omitiendo el descriptor.
            if (!permite(handler, SesionActual.actor())) throw new AccesoNoAutorizadoException();
            if(handler.getMethodAnnotation(CapacidadReconocida.class).porActuacion()) {
                Object variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                String ref=variables instanceof Map<?,?> mapa && mapa.get("referencia") instanceof String valor?valor:null;
                String capacidad=actuaciones.getObject().capacidadDe(ref,SesionActual.actor());
                if(!ids(handler).contains(capacidad))throw new IllegalStateException("El efecto de la actuación no pertenece a esta puerta.");
                registro.exigirHabilitadas(List.of(capacidad));
            } else registro.exigirHabilitadas(ids(handler));
        }
        return true;
    }
}
