package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.OfertaService;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** RF046–049: misma revisión humana y los únicos cinco efectores canónicos de negociación. */
@Component
public class EfectosNegociacionDeActuacion implements EfectoDeActuacion {
    public static final String OFERTA="presentar_oferta",CONTRAOFERTA="registrar_contraoferta",
            ACEPTACION="registrar_aceptacion_oferta",RECHAZO="registrar_rechazo_oferta",RETIRADA="registrar_retirada_oferta";
    private static final Set<String> CAPACIDADES=Set.of(OFERTA,CONTRAOFERTA,ACEPTACION,RECHAZO,RETIRADA);
    private final OfertaService ofertas;
    private final ObjectMapper json;
    public EfectosNegociacionDeActuacion(OfertaService ofertas,ObjectMapper json){this.ofertas=ofertas;this.json=json;}
    public Set<String> capacidades(){return CAPACIDADES;}
    public Object validar(String c,Map<String,Object> d,Actor a){return switch(c){
        case OFERTA->ofertas.validarRegistroOferta(como(d,OfertaService.DatosRegistroOferta.class),a);
        case CONTRAOFERTA->ofertas.validarContraoferta(como(d,OfertaService.DatosContraoferta.class),a);
        case ACEPTACION->ofertas.validarAceptacion(como(d,OfertaService.DatosAceptacion.class),a);
        case RECHAZO->ofertas.validarRechazo(como(d,OfertaService.DatosRechazo.class),a);
        case RETIRADA->ofertas.validarRetirada(como(d,OfertaService.DatosRetirada.class),a);
        default->throw new IllegalArgumentException("Actuación de negociación no reconocida.");
    };}
    public Object resumenRevision(String c,Map<String,Object>d,Actor a){return ofertas.previsualizarActuacion(c,d,a);}
    public void exigirLectura(String c,Map<String,Object>d,Actor a){
        if(!CAPACIDADES.contains(c))throw new IllegalArgumentException("Actuación de negociación no reconocida.");
        ofertas.exigirLecturaActuacion(c,d,a);
    }
    public Object ejecutar(String c,Map<String,Object>d,Actor a,ActuacionesService.ContextoSesion s,String clave,Procedencia p){return switch(c){
        case OFERTA->ofertas.registrarOferta(como(d,OfertaService.DatosRegistroOferta.class),a,s,clave,p);
        case CONTRAOFERTA->ofertas.registrarContraoferta(como(d,OfertaService.DatosContraoferta.class),a,s,clave,p);
        case ACEPTACION->ofertas.registrarAceptacion(como(d,OfertaService.DatosAceptacion.class),a,s,clave,p);
        case RECHAZO->ofertas.registrarRechazo(como(d,OfertaService.DatosRechazo.class),a,s,clave,p);
        case RETIRADA->ofertas.registrarRetirada(como(d,OfertaService.DatosRetirada.class),a,s,clave,p);
        default->throw new IllegalArgumentException("Actuación de negociación no reconocida.");
    };}
    public Optional<?> consultarResultado(String c,String clave,Actor a){return ofertas.consultarResultado(c,clave,a);}
    private <T>T como(Map<String,Object>d,Class<T>t){return json.convertValue(d,t);}
}
