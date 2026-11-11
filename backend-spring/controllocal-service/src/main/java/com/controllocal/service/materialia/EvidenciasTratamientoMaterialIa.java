package com.controllocal.service.materialia;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;
import java.time.Instant;
import java.util.*;

/** CAT-E44/R10: evidencia privada, propia y vigente; no concede permiso comercial ni gasto. */
public interface EvidenciasTratamientoMaterialIa {
    /** Sólo la fuente Core construye el alcance; los hashes trazan el caso, no otra aceptación por job. */
    record AmbitoTratamiento(MaterialIaService.Solicitud solicitud,String huellaFuente,
                             Set<String> categorias,String huellaPayload) {
        public AmbitoTratamiento { categorias=categorias==null?Set.of():Set.copyOf(categorias); }
    }
    enum Decision { ACEPTAR, RECHAZAR, REVOCAR }
    /** Resultado interno de una evaluación de lectura; los fallos técnicos siguen siendo excepciones. */
    enum EstadoUsoArtefacto { PERMITIDO, PERMISO_INSUFICIENTE, CAPACIDAD_DESHABILITADA }
    /** Superficie humana propia, nunca herramienta del modelo ni aprobación por otro profesional. */
    record AvisoParaDecision(String referencia,long versionCobertura,String estado,String huellaAviso,
            String texto,String proveedorLegal,String condicionesVersion,String condicionesTexto,
            Set<String> categorias,Set<MaterialIaService.Accion> finalidades) { }
    record Captura(UUID evento,String referenciaPresentacion,long versionCobertura,Decision decision) { }
    record DecisionRegistrada(UUID evento,long version,Decision decision,Instant registradaEn) { }

    FuentesMaterialIa.EvidenciaTratamiento resolver(AmbitoTratamiento ambito,Actor actor,ContextoSesion sesion);
    void exigirVigente(String referenciaEvidencia,AmbitoTratamiento ambito,
            FuentesMaterialIa.DestinoTratamiento destino,Actor actor,ContextoSesion sesion);
    /** Sólo llamadas internas cuyo contexto de entrada ya fue validado; no una ruta o capacidad pública. */
    void revalidarActual(String referenciaEvidencia,AmbitoTratamiento ambito,Actor actor);
    /** Artefacto existente: evidencia histórica exacta y uso legítimo actual, sin otra transferencia o preferencia ajena. */
    void revalidarArtefacto(String referenciaEvidencia,AmbitoTratamiento historico,Actor actual);
    /** Evalúa sin convertir una denegación esperada en rollback de la proyección que filtra versiones. */
    EstadoUsoArtefacto evaluarUsoArtefacto(String referenciaEvidencia,AmbitoTratamiento historico,Actor actual);
    AvisoParaDecision presentar(AmbitoTratamiento ambito,Actor actor,ContextoSesion sesion);
    DecisionRegistrada capturar(Captura captura,Actor actor,ContextoSesion sesion,Procedencia procedencia);
}
