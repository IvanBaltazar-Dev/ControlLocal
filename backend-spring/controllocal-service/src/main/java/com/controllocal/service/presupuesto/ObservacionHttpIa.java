package com.controllocal.service.presupuesto;

import java.time.Instant;

/** Hecho tecnico interno de UN intento. No prueba recepcion, procesamiento o facturacion remotos. */
public record ObservacionHttpIa(String esquema,String claveIntento,String proveedor,String modelo,
        String versionConfiguracion,Instant iniciadaEn,long duracionNanos,Resultado resultado,
        Integer estadoHttp,boolean envioIntentado) {
    public static final String ESQUEMA="HTTP-IA-1";
    public enum Resultado { RESPUESTA_COMPLETA,ERROR_IO,TIMEOUT,INTERRUMPIDA,DESCONOCIDO }
    public ObservacionHttpIa {
        if(!ESQUEMA.equals(esquema)||claveIntento==null||!claveIntento.matches("[0-9a-f]{64}")
                ||!texto(proveedor,100)||!texto(modelo,150)||!texto(versionConfiguracion,100)
                ||iniciadaEn==null||duracionNanos<0||resultado==null
                ||estadoHttp!=null&&(estadoHttp<100||estadoHttp>599)
                ||resultado==Resultado.RESPUESTA_COMPLETA&&(!envioIntentado||estadoHttp==null))
            throw new IllegalArgumentException("La observacion conserva la identidad y el resultado tecnico del intento.");
    }
    private static boolean texto(String s,int max){return s!=null&&!s.isBlank()&&s.equals(s.trim())&&s.length()<=max;}
    @Override public String toString(){return "ObservacionHttpIa[metadatos internos]";}
}
