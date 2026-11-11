package com.controllocal.service.materialia;

import com.controllocal.service.materialia.MaterialIaService.Solicitud;
import com.controllocal.service.materialia.FuentesMaterialIa.Snapshot;
import com.controllocal.service.materialia.ProveedorMaterialIa.Salida;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import com.controllocal.service.presupuesto.ObservacionHttpIa;

/** Control automático de todos los resultados; no crea una revisión humana ni garantiza fidelidad absoluta. */
public interface ControlesMaterialIa {
    String ETAPA_FINANCIERA="CONTROL_AUTOMATICO";
    record EvaluacionFacturable(Control control,com.controllocal.service.presupuesto.PresupuestoIaService.ResultadoFacturable medicion){}
    /** Entrada privada del control: copia minimizada autorizada; nunca se persiste ni publica. */
    record PeticionControl(MaterialIaService.Accion accion,Map<String,Object> datosPermitidos,
                           String instruccionPermitida,Salida salida,String claveEtapa,
                           @JsonIgnore byte[] originalAutorizado) {
        public PeticionControl {
            Objects.requireNonNull(accion);Objects.requireNonNull(salida);
            datosPermitidos=Map.copyOf(datosPermitidos);
            if(claveEtapa==null||claveEtapa.isBlank())throw new IllegalArgumentException("El control conserva su clave financiada.");
            originalAutorizado=originalAutorizado==null?null:originalAutorizado.clone();
            if(accion==MaterialIaService.Accion.REDACTAR_FICHA?originalAutorizado!=null:
                    originalAutorizado==null||originalAutorizado.length==0)
                throw new IllegalArgumentException("El control visual requiere la copia autorizada del original; redacción no recibe fotografía.");
        }
        @Override @JsonIgnore public byte[] originalAutorizado(){return originalAutorizado==null?null:originalAutorizado.clone();}
        @Override public String toString(){return "PeticionControl[privada]";}
    }
    /** Un juez externo declara su destino y devuelve la medición real; jamás se oculta como control local. */
    interface Facturable extends ControlesMaterialIa {
        FuentesMaterialIa.DestinoTratamiento destino(String perfil,String versionPerfil);
        EvaluacionFacturable evaluarFacturable(PeticionControl peticion,Consumer<ObservacionHttpIa> observador);
        @Override default Control evaluar(Solicitud s,Snapshot f,Salida r){throw new IllegalStateException("El juez externo requiere su etapa financiada.");}
    }
    enum Decision { APROBADA, OBSERVADA, RECHAZADA }
    enum Aspecto { CALIDAD_VISUAL, FIDELIDAD, ALTERACION_INVENCION, PERDIDA_RELEVANTE, DATOS_SENSIBLES }
    enum EstadoSenal { EVALUADA, NO_APLICA, NO_EVALUABLE }
    /** Hallazgos del control real, sin puntuaciones ni umbrales implícitos. Ausencia de hallazgo no es garantía. */
    record Senal(Aspecto aspecto,EstadoSenal estado,boolean hallazgoBloqueante,
                 String observacion,String referenciaEvidencia) { }
    /** Fecha Core del control automático; no es una revisión humana ni identifica un administrador ficticio. */
    record Control(Decision decision,String version,Instant evaluadaEn,List<Senal> senales) {
        public Control { senales=senales==null?null:List.copyOf(senales); }
    }
    boolean atiende(MaterialIaService.Accion accion,String perfil,String versionPerfil);
    Control evaluar(Solicitud solicitud,Snapshot snapshot,Salida salida);
}
