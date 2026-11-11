package com.controllocal.service.soporte;

import com.controllocal.service.excepcion.ReglaNegocioException;
import java.util.*;

/** Catálogo20.10 aprobado10/11. C/P/S/F conservan exclusivamente su significado histórico. */
public enum IndicadorMetaComercial {
    PROSPECCION_EFECTIVA_SALIENTE("Comunicaciones salientes de prospección efectivas","COMUNICACIONES",false),
    ENCARGO_PRIMERA_HABILITACION("Encargos habilitados por primera vez","ENCARGOS",true),
    OPORTUNIDAD_APERTURA("Nuevas oportunidades abiertas","OPORTUNIDADES",true),
    CIERRE_CONTRACTUAL_FAVORABLE("Oportunidades con cierre contractual favorable","OPORTUNIDADES",true);
    public static final String VERSION="METAS-C7-20261110-1";
    private final String rotulo,unidad;private final boolean porOperacion;
    IndicadorMetaComercial(String r,String u,boolean op){rotulo=r;unidad=u;porOperacion=op;}
    public String rotulo(){return rotulo;} public String unidad(){return unidad;} public boolean porOperacion(){return porOperacion;}
    public String operacion(String op){
        if(!porOperacion&&"GENERAL".equals(op)||porOperacion&&op!=null&&Set.of("VENTA","ALQUILER").contains(op))return op;
        throw new ReglaNegocioException(porOperacion?"Separa venta y alquiler en esta meta.":"La comunicación única usa GENERAL y no se duplica por operación.");
    }
    public static IndicadorMetaComercial exigir(String s){
        try{return valueOf(Objects.requireNonNull(s));}catch(IllegalArgumentException|NullPointerException e){throw new ReglaNegocioException("Elige una categoría vigente de metas; C/P/S/F sólo conservan su definición histórica.");}
    }
}
