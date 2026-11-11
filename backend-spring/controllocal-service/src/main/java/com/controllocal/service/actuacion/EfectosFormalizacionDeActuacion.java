package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.formalizacion.FormalizacionService;
import com.controllocal.service.soporte.Procedencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** RF051/053/058–060: los cinco efectos comparten la puerta humana durable. */
@Component
public class EfectosFormalizacionDeActuacion implements EfectoDeActuacion {
    public static final String PRESENTAR="presentar_expediente_operacion",EVALUAR="evaluar_expediente_operacion",
            INFORMAR="informar_hito_contractual",CONFIRMAR="confirmar_hito_contractual",RECTIFICAR="rectificar_hito_contractual";
    private static final Set<String> CAPACIDADES=Set.of(PRESENTAR,EVALUAR,INFORMAR,CONFIRMAR,RECTIFICAR);
    private final FormalizacionService formalizacion;
    private final ObjectMapper json;
    public EfectosFormalizacionDeActuacion(FormalizacionService formalizacion,ObjectMapper json) {
        this.formalizacion=formalizacion;this.json=json;
    }
    public Set<String> capacidades(){return CAPACIDADES;}
    public Object validar(String c,Map<String,Object>d,Actor a){return formalizacion.validarActuacion(c,d,a);}
    public Object resumenRevision(String c,Map<String,Object>d,Actor a){return formalizacion.resumenRevision(c,d,a);}
    public void exigirLectura(String c,Map<String,Object>d,Actor a){formalizacion.exigirLecturaActuacion(c,d,a);}
    public Optional<?> consultarResultado(String c,String clave,Actor a){return formalizacion.consultarResultado(c,clave,a);}
    public Object ejecutar(String c,Map<String,Object>d,Actor a,ActuacionesService.ContextoSesion s,String clave,Procedencia p) {
        return switch(c) {
            case PRESENTAR -> formalizacion.presentar(json.convertValue(d,FormalizacionService.DatosPresentacion.class),a,s,clave,p);
            case EVALUAR -> formalizacion.evaluar(json.convertValue(d,FormalizacionService.DatosRevision.class),a,s,clave,p);
            case INFORMAR -> formalizacion.informar(json.convertValue(d,FormalizacionService.DatosInformacionHito.class),a,s,clave,p);
            case CONFIRMAR -> formalizacion.confirmar(json.convertValue(d,FormalizacionService.DatosConfirmacionHito.class),a,s,clave,p);
            case RECTIFICAR -> formalizacion.rectificar(json.convertValue(d,FormalizacionService.DatosRectificacionHito.class),a,s,clave,p);
            default -> throw new IllegalArgumentException("Actuación contractual no reconocida.");
        };
    }
}
