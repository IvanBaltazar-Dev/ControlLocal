package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.network.NetworkService;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

/** Aprobación28/10: una decisión comercial humana y ejecución exacta, en la máquina existente. */
@Component
public class EfectosNetworkDeActuacion implements EfectoDeActuacion {
    public static final String SOLICITAR="solicitar_participacion_network";
    public static final String DECIDIR="decidir_participacion_network";
    public static final String DESACTIVAR="desactivar_participacion_network";
    private final NetworkService network;
    private final ObjectMapper json;
    public EfectosNetworkDeActuacion(NetworkService network,ObjectMapper json){this.network=network;this.json=json;}
    public Set<String> capacidades(){return Set.of(SOLICITAR,DECIDIR,DESACTIVAR);}
    public Object validar(String c,Map<String,Object> datos,Actor a){return switch(c){
        case SOLICITAR -> network.validarSolicitud(como(datos,NetworkService.DatosSolicitud.class),a);
        case DECIDIR -> network.validarDecision(como(datos,NetworkService.DatosDecision.class),a);
        case DESACTIVAR -> network.validarDesactivacionDirecta(como(datos,NetworkService.DatosDesactivacion.class),a);
        default -> throw desconocida();};}
    public Object resumenRevision(String c,Map<String,Object> datos,Actor a){
        return switch(c){
            case SOLICITAR -> {
                var d=como(datos,NetworkService.DatosSolicitud.class);var p=network.previsualizar(d.idEncargo(),d.campos(),a);
                // Las versiones internas del expediente sirven de procedencia, no son el contenido público.
                yield Map.of("proyeccion",p.fuente().datosPermitidos(),"diferencias",p.diferencias(),
                        "anterior",p.ultimaAprobada(),"politica",p.fuente().politicaVersion(),"finalidad",p.fuente().finalidad());
            }
            case DECIDIR -> {
                var p=network.consultarSolicitud(como(datos,NetworkService.DatosDecision.class).solicitud(),a);
                var r=new LinkedHashMap<String,Object>();
                r.put("proyeccion",p.datosPropuestos());r.put("diferencias",p.diferencias());r.put("campos",p.campos());
                r.put("accion",p.accion());r.put("finalidad",p.finalidad());r.put("desde",p.desde());r.put("hasta",p.hasta());
                yield r;
            }
            case DESACTIVAR -> network.consultar(como(datos,NetworkService.DatosDesactivacion.class).idEncargo(),a);
            default -> throw desconocida();
        };
    }
    public void exigirLectura(String c,Map<String,Object> datos,Actor a){switch(c){
        case SOLICITAR -> network.consultar(como(datos,NetworkService.DatosSolicitud.class).idEncargo(),a);
        case DECIDIR -> network.consultarSolicitud(como(datos,NetworkService.DatosDecision.class).solicitud(),a);
        case DESACTIVAR -> network.consultar(como(datos,NetworkService.DatosDesactivacion.class).idEncargo(),a);
        default -> throw desconocida();}}
    public Object ejecutar(String c,Map<String,Object> datos,Actor a,ActuacionesService.ContextoSesion s,String clave,Procedencia p){return switch(c){
        case SOLICITAR -> network.solicitar(como(datos,NetworkService.DatosSolicitud.class),a,s,clave,p);
        case DECIDIR -> network.decidir(como(datos,NetworkService.DatosDecision.class),a,s,clave,p);
        case DESACTIVAR -> network.desactivarDirectamente(como(datos,NetworkService.DatosDesactivacion.class),a,s,clave,p);
        default -> throw desconocida();};}
    public Optional<?> consultarResultado(String c,String clave,Actor a){return network.consultarResultado(c,clave,a);}
    private <T>T como(Map<String,Object> d,Class<T> tipo){return json.convertValue(d,tipo);}
    private static IllegalArgumentException desconocida(){return new IllegalArgumentException("Capacidad Network no reconocida.");}
}
