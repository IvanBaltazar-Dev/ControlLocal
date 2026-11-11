package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.RequerimientoService;
import com.controllocal.service.colaboracion.ColaboracionService;
import com.controllocal.service.colaboracion.CanalesColaboracionService;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** RF030/035/036/037: delega reglas y efectos a sus únicos casos canónicos. */
@Component
public class EfectosC3DeActuacion implements EfectoDeActuacion {
    public static final String FIN_DEMANDA="retirar_finalizar_demanda";
    public static final String SOLICITAR="solicitar_colaboracion";
    public static final String DECIDIR="decidir_colaboracion";
    public static final String REVOCAR="revocar_colaboracion";
    public static final String DECLARAR_CANAL="declarar_canal_colaboracion";
    public static final String INVITAR="invitar_colaboracion_encargo";
    private static final Set<String> CAPACIDADES=Set.of(FIN_DEMANDA,SOLICITAR,DECIDIR,REVOCAR,DECLARAR_CANAL,INVITAR);
    private final RequerimientoService demandas;
    private final ColaboracionService colaboraciones;
    private final CanalesColaboracionService canales;
    private final ObjectMapper json;
    public EfectosC3DeActuacion(RequerimientoService demandas,ColaboracionService colaboraciones,CanalesColaboracionService canales,ObjectMapper json) {
        this.demandas=demandas;this.colaboraciones=colaboraciones;this.canales=canales;this.json=json;
    }
    public Set<String> capacidades(){return CAPACIDADES;}
    public Object validar(String c,Map<String,Object> datos,Actor a) {
        return switch(c) {
            case FIN_DEMANDA -> demandas.validarFin(como(datos,RequerimientoService.FinDemanda.class),a);
            case SOLICITAR -> colaboraciones.validarSolicitud(como(datos,ColaboracionService.DatosSolicitud.class),a);
            case INVITAR -> colaboraciones.validarInvitacion(como(datos,ColaboracionService.DatosInvitacion.class),a);
            case DECIDIR -> colaboraciones.validarDecision(como(datos,ColaboracionService.DatosDecision.class),a);
            case REVOCAR -> colaboraciones.validarRevocacion(como(datos,ColaboracionService.DatosRevocacion.class),a);
            case DECLARAR_CANAL -> canales.validarDeclaracion(como(datos,CanalesColaboracionService.DatosDeclaracion.class),a);
            default -> throw new IllegalArgumentException("Capacidad C3 no reconocida.");
        };
    }
    public void exigirLectura(String c,Map<String,Object> datos,Actor a) {
        switch(c) {
            case FIN_DEMANDA -> demandas.buscar(como(datos,RequerimientoService.FinDemanda.class).idDemanda(),a);
            case SOLICITAR -> demandas.buscar(como(datos,ColaboracionService.DatosSolicitud.class).idDemanda(),a);
            case INVITAR -> colaboraciones.validarInvitacion(como(datos,ColaboracionService.DatosInvitacion.class),a);
            case DECIDIR -> colaboraciones.consultarMetadatos(como(datos,ColaboracionService.DatosDecision.class).solicitud(),a);
            case REVOCAR -> colaboraciones.consultarMetadatosPorConcesion(como(datos,ColaboracionService.DatosRevocacion.class).concesion(),a);
            case DECLARAR_CANAL -> {
                var d=como(datos,CanalesColaboracionService.DatosDeclaracion.class);
                canales.consultarDeclaracionPropia(d.solicitud(),d.lado(),a);
            }
            default -> throw new IllegalArgumentException("Capacidad C3 no reconocida.");
        }
    }
    public Object resumenRevision(String c,Map<String,Object> datos,Actor a) {
        return switch(c) {
            case DECIDIR -> colaboraciones.previsualizarDecision(como(datos,ColaboracionService.DatosDecision.class),a);
            case REVOCAR -> colaboraciones.consultarMetadatosPorConcesion(como(datos,ColaboracionService.DatosRevocacion.class).concesion(),a);
            default -> Map.of();
        };
    }
    public Object ejecutar(String c,Map<String,Object> datos,Actor a,ActuacionesService.ContextoSesion s,String clave,Procedencia p) {
        return switch(c) {
            case FIN_DEMANDA -> demandas.retirarOFinalizar(como(datos,RequerimientoService.FinDemanda.class),a,s,clave,p);
            case SOLICITAR -> colaboraciones.solicitar(como(datos,ColaboracionService.DatosSolicitud.class),a,s,clave,p);
            case INVITAR -> colaboraciones.invitar(como(datos,ColaboracionService.DatosInvitacion.class),a,s,clave,p);
            case DECIDIR -> colaboraciones.decidirPorParte(como(datos,ColaboracionService.DatosDecision.class),a,s,clave,p);
            case REVOCAR -> colaboraciones.revocarPropia(como(datos,ColaboracionService.DatosRevocacion.class),a,s,clave,p);
            case DECLARAR_CANAL -> canales.declarar(como(datos,CanalesColaboracionService.DatosDeclaracion.class),a,s,clave,p);
            default -> throw new IllegalArgumentException("Capacidad C3 no reconocida.");
        };
    }
    public Optional<?> consultarResultado(String c,String clave,Actor a) {
        return switch(c) {
            case FIN_DEMANDA -> demandas.consultarResultadoFin(clave,a);
            case DECLARAR_CANAL -> canales.consultarResultado(clave,a);
            default -> colaboraciones.consultarResultado(c,clave,a);
        };
    }
    private <T>T como(Map<String,Object> datos,Class<T> tipo){return json.convertValue(datos,tipo);}
}
