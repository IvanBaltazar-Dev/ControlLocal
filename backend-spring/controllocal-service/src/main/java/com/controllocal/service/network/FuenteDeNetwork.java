package com.controllocal.service.network;

import com.controllocal.domain.auditoria.EventoDominio;

import com.controllocal.service.Actor;
import java.util.*;

/** Fuente canónica de datos/autoridad; el gestor Network conserva todas las decisiones y transiciones. */
public interface FuenteDeNetwork {
    /** Permiso derivado de colaboración privada, sin sustituir actor o habilitar participación Network. */
    Map<String,Object> camposDeInvitacion(com.controllocal.service.colaboracion.AccesoFuenteDeInvitacion.Permiso permiso);
    enum Accion { SOLICITAR, CONSULTAR, DECIDIR, DESACTIVAR }
    String FRESCURA="FRESCURA";
    record FuentePropia(long organizacion,long idEncargo,long responsable,Long brokerActual,boolean individual,
                        long versionEncargo,long versionFisica,String politicaVersion,String finalidad,
                        Map<String,Object> datosPermitidos,String huellaRelevante,String huellaAutoridad,
                        boolean elegible,Set<String> causas) {
        public FuentePropia {datosPermitidos=Collections.unmodifiableMap(new LinkedHashMap<>(datosPermitidos));causas=Set.copyOf(causas);}
    }
    /** Sólo creada a partir de una versión aprobada persistida, nunca de IDs arbitrarios del cliente. */
    record ReferenciaAutorizada(long organizacion,long idEncargo,String ofertaRef,String referenciaVersion,long versionProyeccion,
                                long responsable,Long brokerActual,String politicaVersion,String finalidad,
                                Set<String> campos,String huellaRelevante,String huellaAutoridad) {
        public ReferenciaAutorizada {campos=Set.copyOf(campos);}
    }
    /** Metadatos actuales suficientes para revalidar; no transporta atributos privados de otra organización. */
    record VerificacionActual(long organizacion,long idEncargo,long responsable,Long brokerActual,boolean individual,
                               String politicaVersion,String finalidad,String huellaRelevante,String huellaAutoridad,
                               boolean elegible,Set<String> causas,
                               com.controllocal.service.matching.ContratoMatching.ConfirmacionDisponibilidad disponibilidad) {
        public VerificacionActual {causas=Set.copyOf(causas);}
    }
    record AutoridadActual(long organizacion,long idEncargo,long responsable,Long brokerActual,
                           boolean individual,boolean elegible,Set<String> causas) {
        public AutoridadActual {causas=Set.copyOf(causas);}
    }
    /** Misma autoridad canónica, sin construir los valores completos de la proyección para Capa. */
    AutoridadActual consultarAutoridadPropia(long idEncargo,Actor actor);
    FuentePropia consultarPropia(long idEncargo,Set<String> campos,Accion accion,Actor actor);
    Map<Long,VerificacionActual> verificarActuales(Collection<ReferenciaAutorizada> versiones,Actor actorReal);
    /** Verificación causal de un hecho Core autorizado, mismo tenant; nunca entrega valores compartidos. */
    Map<Long,VerificacionActual> verificarCambioCore(Collection<ReferenciaAutorizada> versiones,Actor autorReal,EventoDominio eventoCausal);
}
