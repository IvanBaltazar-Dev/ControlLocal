package com.controllocal.service.actuacion;
import com.controllocal.service.*;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

/** RF079 usa la revisión humana exacta ya existente; no abre una escritura directa paralela. */
@Component
public class EfectosReporteDeActuacion implements EfectoDeActuacion {
    public static final String RECTIFICAR="rectificar_comunicacion_reporte";
    private final ReportePropietarioService reportes;private final ObjectMapper json;
    public EfectosReporteDeActuacion(ReportePropietarioService r,ObjectMapper j){reportes=r;json=j;}
    public Set<String> capacidades(){return Set.of(RECTIFICAR);}
    private ReportePropietarioService.Rectificacion datos(Map<String,Object>d){return json.convertValue(d,ReportePropietarioService.Rectificacion.class);}
    public Object validar(String c,Map<String,Object>d,Actor a){exigir(c);return reportes.validarRectificacion(datos(d),a);}
    public Object resumenRevision(String c,Map<String,Object>d,Actor a){exigir(c);var cambio=datos(d);return Map.of("antecedente",reportes.consultarComunicacion(cambio.comunicacion(),cambio.versionEsperada(),a));}
    public void exigirLectura(String c,Map<String,Object>d,Actor a){exigir(c);reportes.exigirLecturaRectificacion(datos(d),a);}
    public Object ejecutar(String c,Map<String,Object>d,Actor a,ActuacionesService.ContextoSesion s,String clave,Procedencia p){exigir(c);return reportes.rectificar(datos(d),a,s,clave,p);}
    public Optional<?> consultarResultado(String c,String clave,Actor a){exigir(c);return reportes.resultadoRectificacion(clave,a);}
    private static void exigir(String c){if(!RECTIFICAR.equals(c))throw new IllegalArgumentException("Actuación de reporte desconocida.");}
}
