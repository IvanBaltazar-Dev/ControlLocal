package com.controllocal.service.soporte;

import com.controllocal.service.PropiedadUniversalService.ValorAtributo;
import com.controllocal.service.excepcion.ReglaNegocioException;
import java.util.*;

/** Forma compartida de valores y retiros; no decide sujeto, permiso, definición o almacenamiento. */
public final class EntradasDeValores {
    private EntradasDeValores(){}
    public static Map<String,ValorAtributo> atributosValidados(List<ValorAtributo> valores){
        Map<String,ValorAtributo> porClave=new LinkedHashMap<>();if(valores==null)return porClave;
        for(var valor:valores){
            if(valor==null||valor.clave()==null||valor.clave().isBlank())throw new ReglaNegocioException("Un atributo sin clave no se puede guardar.");
            if(porClave.put(valor.clave().trim(),valor)!=null)throw new ReglaNegocioException("El atributo \""+valor.clave()+"\" llegó dos veces.");
        }return porClave;
    }
    public static Map<String,ValorAtributo> atributosDeEdicion(List<ValorAtributo> valores){
        var porClave=atributosValidados(valores);
        porClave.forEach((clave,valor)->{if(valor.valores()==null&&(valor.valor()==null||valor.valor().isBlank()))
            throw new ReglaNegocioException("El atributo \""+clave+"\" llegó vacío. Retirar un valor se declara en atributosABorrar.");});
        return porClave;
    }
    public static List<String> clavesABorrar(List<String> claves,Map<String,ValorAtributo> valores){
        if(claves==null)return List.of();Set<String> limpias=new LinkedHashSet<>();
        for(String clave:claves){
            if(clave==null||clave.isBlank())throw new ReglaNegocioException("atributosABorrar trae una clave vacía.");
            String limpia=clave.trim();if(valores.containsKey(limpia))throw new ReglaNegocioException("El atributo \""+limpia+"\" llegó con valor y retirada a la vez.");
            limpias.add(limpia);
        }return List.copyOf(limpias);
    }
    public static List<ValorEntrante> entrantes(Map<String,ValorAtributo> valores,Procedencia acto){
        return valores.values().stream().map(v->entrante(v,acto)).toList();
    }
    public static ValorEntrante entrante(ValorAtributo valor,Procedencia acto){
        return new ValorEntrante(valor.clave(),valor.valor(),valor.moneda(),valor.valores(),new ProcedenciaDelValor(acto,valor.naturaleza(),valor.observadoEn(),valor.evidenciaRef(),valor.confianza()));
    }
}
