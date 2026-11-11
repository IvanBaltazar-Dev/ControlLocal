package com.controllocal.service.network;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Formato único de campos V1 conocidos; las fuentes conservan autorización y selección previa. */
public final class CamposProyeccionComercial {
    private CamposProyeccionComercial() { }
    public static Map<String,Object> campo(Object valor,String concepto,String unidad,String moneda,String periodo) {
        Map<String,Object> d=new TreeMap<>();d.put("valor",valor);
        if(concepto!=null)d.put("concepto",concepto);if(unidad!=null)d.put("unidad",unidad);
        if(moneda!=null)d.put("moneda",moneda);if(periodo!=null)d.put("periodo",periodo);return d;
    }
    public static Map<String,Object> territorial(Object valor,String concepto,String unidad,String jurisdiccion,String versionFuente) {
        var d=campo(valor,concepto,unidad,null,null);
        if(jurisdiccion!=null)d.put("jurisdiccion",jurisdiccion);
        if(versionFuente!=null)d.put("versionFuente",versionFuente);
        return d;
    }
    public static void poner(Map<String,Object> datos,Set<String> campos,String selector,Map<String,Object> valor) {
        if(!PoliticaProyeccionNetworkV1.CAMPOS.contains(selector))throw new IllegalArgumentException("Selector fuera de V1.");
        if(campos.contains(selector)&&valor.get("valor")!=null)datos.put(selector,valor);
    }
}
