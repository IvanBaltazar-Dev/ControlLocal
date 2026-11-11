package com.controllocal.service.materialia.openai;

import com.controllocal.domain.presupuesto.UnidadesDeConsumo.Tarifa;
import com.controllocal.service.materialia.MaterialIaService.Accion;
import java.time.Duration;
import java.util.*;

/** Configuración servidor del ensayo. No contiene tarifas, cotas o habilitaciones por defecto. */
public record ConfiguracionRespuestasOpenAi(List<Ruta> rutas,Duration conexion,Duration total) {
    public ConfiguracionRespuestasOpenAi {
        ConfiguracionEdicionOpenAi.exigirDuracion(conexion);ConfiguracionEdicionOpenAi.exigirDuracion(total);
        if(rutas==null||rutas.isEmpty())throw new IllegalArgumentException("Falta la ruta acreditada.");
        rutas=List.copyOf(rutas);Set<Accion> vistas=EnumSet.noneOf(Accion.class);
        for(var r:rutas)if(r==null||!vistas.add(r.accion()))throw new IllegalArgumentException("Ruta repetida.");
    }
    Ruta ruta(Accion accion){return rutas.stream().filter(r->r.accion()==accion).findFirst().orElseThrow(()->new IllegalArgumentException("Acción sin ruta."));}
    public record Ruta(Accion accion,String perfil,String versionPerfil,String versionProceso,
                       Parametros parametros,Envoltura envoltura,ContratoUsage usage) {
        public Ruta {
            if(accion!=Accion.REDACTAR_FICHA&&accion!=Accion.ANALIZAR_FOTO)throw new IllegalArgumentException("Acción no admitida.");
            ConfiguracionEdicionOpenAi.exigirTexto(perfil);ConfiguracionEdicionOpenAi.exigirTexto(versionPerfil);
            ConfiguracionEdicionOpenAi.exigirTexto(versionProceso);
            Objects.requireNonNull(parametros);Objects.requireNonNull(envoltura);Objects.requireNonNull(usage);
            if(accion==Accion.REDACTAR_FICHA&&(parametros.detalleImagen()!=null||envoltura.maximoFotoBytes()!=0)
                    ||accion==Accion.ANALIZAR_FOTO&&(parametros.detalleImagen()==null||envoltura.maximoFotoBytes()<=0)
                    ||accion==Accion.ANALIZAR_FOTO&&"none".equals(parametros.esfuerzo()))
                throw new IllegalArgumentException("Parámetros incompatibles con la acción/modelo.");
        }
        public String modelo(){return accion==Accion.REDACTAR_FICHA?"gpt-6-luna":"gpt-6.1-sol";}
        @Override public String toString(){return "RutaRespuestas[privada]";}
    }
    public record Parametros(String esfuerzo,String detalleImagen,int maximoSalidaTokens) {
        public Parametros {
            if(!Set.of("none","low","medium","high","xhigh","max").contains(esfuerzo)
                    ||detalleImagen!=null&&!Set.of("low","high","original","auto").contains(detalleImagen)
                    ||maximoSalidaTokens<=0||maximoSalidaTokens>128000)
                throw new IllegalArgumentException("Parámetros explícitos requeridos.");
        }
    }
    /** Cota técnica versionada: recepción HTTP y texto decodificado conservan límites separados. */
    public record Envoltura(String version,String huella,int maximoInstruccionCaracteres,int maximoDatosBytes,
                           int maximoFotoBytes,int maximoSolicitudBytes,int maximoRespuestaBytes,
                           int maximoTextoBytes,int profundidadJson) {
        public Envoltura {
            ConfiguracionEdicionOpenAi.exigirTexto(version);
            if(huella==null||!huella.matches("[0-9a-f]{64}")||maximoInstruccionCaracteres<=0||maximoDatosBytes<=0
                    ||maximoFotoBytes<0||maximoSolicitudBytes<=0||maximoRespuestaBytes<=0||maximoTextoBytes<=0
                    ||maximoDatosBytes>maximoSolicitudBytes||maximoTextoBytes>maximoRespuestaBytes||profundidadJson<=0)
                throw new IllegalArgumentException("Envoltura explícita requerida.");
        }
    }
    /** Categorías NATIVAS. Entrada total es padre de cachés; salida total ya incluye razonamiento. */
    public record ContratoUsage(String version,String evidencia,String versionTarifa,String claseEntrada,
                                String claseCacheLectura,String claseEscritura30m,String claseEscritura12h,
                                String claseSalida,String claseRazonamiento,List<Tarifa> tarifas) {
        public ContratoUsage {
            for(String s:List.of(version,evidencia,versionTarifa,claseEntrada,claseCacheLectura,claseEscritura30m,
                    claseEscritura12h,claseSalida,claseRazonamiento))ConfiguracionEdicionOpenAi.exigirTexto(s);
            Set<String> clases=Set.of(claseEntrada,claseCacheLectura,claseEscritura30m,claseEscritura12h,claseSalida,claseRazonamiento);
            if(tarifas==null||tarifas.size()!=clases.size())throw new IllegalArgumentException("Falta tarifa nativa.");
            tarifas=List.copyOf(tarifas);Map<String,Tarifa> mapa=new HashMap<>();
            for(var t:tarifas)if(t==null||!clases.contains(t.clase())||mapa.putIfAbsent(t.clase(),t)!=null
                    ||!"token".equals(t.unidad())||t.importeUsd()==null||t.importeUsd().signum()<0
                    ||t.porCantidad()==null||t.porCantidad().signum()<=0)throw new IllegalArgumentException("Tarifa nativa no acreditada.");
            var s=mapa.get(claseSalida);var r=mapa.get(claseRazonamiento);
            if(s.importeUsd().multiply(r.porCantidad()).compareTo(r.importeUsd().multiply(s.porCantidad()))!=0)
                throw new IllegalArgumentException("Razonamiento es subconjunto de la misma salida facturable.");
        }
        @Override public String toString(){return "ContratoUsageRespuestas[privado]";}
    }
    @Override public String toString(){return "ConfiguracionRespuestasOpenAi[privada]";}
}
