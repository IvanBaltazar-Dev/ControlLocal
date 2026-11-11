package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.documentos.PiezasComercialesService;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

/** Obtener para difusión confirma contenido/contacto/fotos exactos, no publica ni envía. */
@Component
public class EfectosPiezaDeActuacion implements EfectoDeActuacion {
    private final PiezasComercialesService piezas;private final ObjectMapper json;
    public EfectosPiezaDeActuacion(PiezasComercialesService piezas,ObjectMapper json){this.piezas=piezas;this.json=json;}
    public Set<String> capacidades(){return Set.of(PiezasComercialesService.OBTENER);}
    private PiezasComercialesService.Entrega datos(Map<String,Object>d){return json.convertValue(d,PiezasComercialesService.Entrega.class);}
    public Object validar(String c,Map<String,Object>d,Actor a){return piezas.validarEntrega(datos(d),a);}
    public Object resumenRevision(String c,Map<String,Object>d,Actor a){return piezas.resumenEntrega(datos(d),a);}
    public void exigirLectura(String c,Map<String,Object>d,Actor a){piezas.exigirLecturaEntrega(datos(d),a);}
    public Object ejecutar(String c,Map<String,Object>d,Actor a,ActuacionesService.ContextoSesion s,String k,Procedencia p){return piezas.obtenerParaDifusion(datos(d),a,s,k,p);}
    public Optional<?> consultarResultado(String c,String k,Actor a){return piezas.consultarResultadoEntrega(k,a);}
}
