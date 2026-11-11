package com.controllocal.service.materialia;

import com.controllocal.service.Actor;
import com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.AmbitoTratamiento;
import java.util.*;

/**
 * Fuentes acreditadoras exclusivamente servidor de CAT-E44/RF-K080. No se alimenta de DTO comerciales.
 * Sin una fuente real instalada no hay aviso informado ni base acreditada. Los tests identifican sus
 * fuentes sintéticas; esta interfaz no registra una concesión productiva ni acepta un booleano del cliente.
 */
public interface AvisosTratamientoMaterialIa {
    record Contenido(String referencia,long version,String texto,
            Set<MaterialIaService.Accion> finalidades,Set<String> categorias,String proveedorLegal,
            String condicionesVersion,String condicionesTexto,String fuenteCondiciones,String huellaCondiciones,
            Set<FuentesMaterialIa.DestinoTratamiento> destinos) {
        public Contenido {
            finalidades=finalidades==null?Set.of():Set.copyOf(finalidades);
            categorias=categorias==null?Set.of():Set.copyOf(categorias);
            destinos=destinos==null?Set.of():Set.copyOf(destinos);
        }
    }
    /** La fuente verifica el alcance y la base aplicables, incluso cuando no se requiere consentimiento de un tercero. */
    record Legitimacion(String referencia,String fuente,String huella,MaterialIaService.Recurso recurso,
            Set<MaterialIaService.Accion> finalidades,Set<String> categorias,
            String proveedorLegal,String condicionesVersion) {
        public Legitimacion {
            finalidades=finalidades==null?Set.of():Set.copyOf(finalidades);
            categorias=categorias==null?Set.of():Set.copyOf(categorias);
        }
    }
    Optional<Contenido> vigente(AmbitoTratamiento ambito,Actor actor);
    Optional<Legitimacion> legitimacionActual(AmbitoTratamiento ambito,Contenido aviso,Actor actor);
    /**
     * Acredita el uso actual del resultado existente bajo sus condiciones históricas, no un envío nuevo.
     * La ausencia de fuente explícita deniega; nunca se hereda la preferencia de otra cuenta.
     */
    default Optional<Legitimacion> legitimacionUsoArtefacto(AmbitoTratamiento historico,Contenido avisoHistorico,Actor actual){
        return Optional.empty();
    }
}
