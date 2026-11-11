package com.controllocal.service.materialia.openai;

import com.controllocal.domain.presupuesto.UnidadesDeConsumo.Tarifa;
import java.time.Duration;
import java.util.*;

/** Configuración privada del ensayo/perfil acreditado. No contiene valores operativos por defecto. */
public record ConfiguracionEdicionOpenAi(String perfil,String versionPerfil,String versionProceso,
        Parametros parametros,Envoltura envoltura,ContratoUsage usage,Duration conexion,Duration total) {
    public ConfiguracionEdicionOpenAi {
        exigirTexto(perfil);exigirTexto(versionPerfil);exigirTexto(versionProceso);
        Objects.requireNonNull(parametros);Objects.requireNonNull(envoltura);Objects.requireNonNull(usage);
        exigirDuracion(conexion);exigirDuracion(total);
    }
    public record Parametros(String calidad,String formato,Integer compresion,String fondo,
            int ancho,int alto,long maximoPixelesDecodificados) {
        public Parametros {
            if(!Set.of("low","medium","high","xhigh","max","auto").contains(calidad)
                    ||!Set.of("png","jpeg").contains(formato)||!Set.of("opaque","transparent").contains(fondo)
                    ||ancho<=0||alto<=0||ancho%16!=0||alto%16!=0
                    ||(long)ancho>3L*alto||(long)alto>3L*ancho
                    // Restricciones del protocolo Sunburst, guía oficial consultada 03/11/2026.
                    ||ancho>3840||alto>3840||(long)ancho*alto<655360||(long)ancho*alto>8294400
                    ||maximoPixelesDecodificados<=0||(long)ancho*alto>maximoPixelesDecodificados
                    ||"transparent".equals(fondo)&&!"png".equals(formato)
                    ||"png".equals(formato)&&compresion!=null
                    ||"jpeg".equals(formato)&&(compresion==null||compresion<0||compresion>100))
                throw new IllegalArgumentException("Falta una configuración explícita compatible de edición.");
        }
        /** Tamaño de SALIDA explícito del ensayo, no una transformación local de la fuente. */
        public String size(){return ancho+"x"+alto;}
        public String mime(){return "png".equals(formato)?"image/png":"image/jpeg";}
    }
    /** Cota de JSON HTTP distinta del binario B0. No se deriva de un factor universal de Base64. */
    public record Envoltura(String version,String huella,int maximoEntradaBytes,int maximoRespuestaBytes,int profundidadJson) {
        public Envoltura {
            exigirTexto(version);
            if(huella==null||!huella.matches("[0-9a-f]{64}")||maximoEntradaBytes<=0
                    ||maximoRespuestaBytes<=0||profundidadJson<=0)
                throw new IllegalArgumentException("Falta la envoltura técnica versionada y acotada.");
        }
    }
    /** Mapping privado acreditado de usage y tarifas NATIVAS, nunca coeficientes comerciales. */
    public record ContratoUsage(String version,String evidencia,String versionTarifa,
            String claseTexto,String claseImagen,String claseSalida,List<Tarifa> tarifas) {
        public ContratoUsage {
            exigirTexto(version);exigirTexto(evidencia);exigirTexto(versionTarifa);
            exigirTexto(claseTexto);exigirTexto(claseImagen);exigirTexto(claseSalida);
            if(Set.of(claseTexto,claseImagen,claseSalida).size()!=3||tarifas==null||tarifas.size()!=3)
                throw new IllegalArgumentException("La medición exige sus tres categorías nativas y tarifas.");
            tarifas=List.copyOf(tarifas);Set<String> clases=new HashSet<>();
            for(var t:tarifas)if(t==null||!clases.add(t.clase())||!"token".equals(t.unidad())
                    ||t.importeUsd()==null||t.importeUsd().signum()<0||t.porCantidad()==null||t.porCantidad().signum()<=0)
                throw new IllegalArgumentException("Tarifa nativa no acreditada.");
            if(!clases.equals(Set.of(claseTexto,claseImagen,claseSalida)))throw new IllegalArgumentException("Falta tarifa de una categoría nativa.");
        }
        @Override public String toString(){return "ContratoUsage[privado]";}
    }
    static void exigirTexto(String x){if(x==null||x.isBlank()||!x.equals(x.trim()))throw new IllegalArgumentException("Configuración privada incompleta.");}
    static void exigirDuracion(Duration d){if(d==null||d.isZero()||d.isNegative()||d.toMillis()<=0)throw new IllegalArgumentException("Tiempo técnico explícito requerido.");}
    @Override public String toString(){return "ConfiguracionEdicionOpenAi[privada]";}
}
