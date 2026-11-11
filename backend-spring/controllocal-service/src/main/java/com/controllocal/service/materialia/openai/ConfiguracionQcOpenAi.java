package com.controllocal.service.materialia.openai;

import com.controllocal.service.materialia.MaterialIaService.Accion;
import com.controllocal.service.soporte.Idempotencia;
import java.time.Duration;
import java.util.*;

/** Configuración servidor acreditada; ningún parámetro operativo tiene valor por defecto. */
public record ConfiguracionQcOpenAi(List<Ruta> rutas,Duration conexion,Duration total) {
    public ConfiguracionQcOpenAi {
        ConfiguracionEdicionOpenAi.exigirDuracion(conexion);ConfiguracionEdicionOpenAi.exigirDuracion(total);
        if(rutas==null||rutas.isEmpty())throw new IllegalArgumentException("Falta la ruta QC acreditada.");
        rutas=List.copyOf(rutas);Set<Accion> vistas=EnumSet.noneOf(Accion.class);
        for(var r:rutas)if(r==null||!vistas.add(r.accion()))throw new IllegalArgumentException("Ruta QC repetida.");
    }
    Ruta ruta(Accion accion){return rutas.stream().filter(r->r.accion()==accion).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Acción sin ruta QC."));}
    public record Ruta(Accion accion,String perfil,String versionPerfil,String versionControl,
                       String versionRubrica,String huellaRubrica,String evidenciaRubricaServidor,String rubricaServidor,
                       ConfiguracionRespuestasOpenAi.Parametros parametros,
                       ConfiguracionRespuestasOpenAi.Envoltura envoltura,
                       ConfiguracionRespuestasOpenAi.ContratoUsage usage,Cache cache,int maximoResultadoTextoBytes) {
        public Ruta {
            if(accion==null)
                throw new IllegalArgumentException("Acción QC sin contrato de entrada autorizado.");
            for(String v:List.of(perfil,versionPerfil,versionControl,versionRubrica,evidenciaRubricaServidor,rubricaServidor))
                ConfiguracionEdicionOpenAi.exigirTexto(v);
            if(huellaRubrica==null||!huellaRubrica.matches("[0-9a-f]{64}")||!huellaRubrica.equals(Idempotencia.huellaExacta(rubricaServidor)))
                throw new IllegalArgumentException("Falta la huella de rúbrica servidor.");
            Objects.requireNonNull(parametros);Objects.requireNonNull(envoltura);Objects.requireNonNull(usage);Objects.requireNonNull(cache);
            if("none".equals(parametros.esfuerzo())
                    ||(accion==Accion.MEJORAR_FOTO?maximoResultadoTextoBytes!=0:maximoResultadoTextoBytes<=0)
                    ||rubricaServidor.length()>envoltura.maximoInstruccionCaracteres()
                    ||accion==Accion.REDACTAR_FICHA&&(parametros.detalleImagen()!=null||envoltura.maximoFotoBytes()!=0)
                    ||accion!=Accion.REDACTAR_FICHA&&(parametros.detalleImagen()==null||envoltura.maximoFotoBytes()<=0))
                throw new IllegalArgumentException("Configuración QC incompatible con su acción/modelo.");
        }
        public String modelo(){return "gpt-6.1-sol";}
        @Override public String toString(){return "RutaQcOpenAi[privada]";}
    }
    /** Política técnica explícita del ensayo; no declara aciertos, ahorro o descuento sin contador nativo. */
    public record Cache(String modo,String ttl,String evidenciaServidor){
        public Cache {
            if(!Set.of("implicit","explicit").contains(modo)||!Set.of("30m","12h").contains(ttl))
                throw new IllegalArgumentException("Falta política cache explícita del destino.");
            ConfiguracionEdicionOpenAi.exigirTexto(evidenciaServidor);
        }
        @Override public String toString(){return "CacheQcOpenAi[privada]";}
    }
    @Override public String toString(){return "ConfiguracionQcOpenAi[privada]";}
}
