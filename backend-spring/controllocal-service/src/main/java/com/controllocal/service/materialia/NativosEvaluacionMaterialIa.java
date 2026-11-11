package com.controllocal.service.materialia;
import com.controllocal.service.materialia.openai.*;
/** Construye sólo adaptadores existentes desde la configuración del plan acreditado. */
public interface NativosEvaluacionMaterialIa {
    record Sesion(ProveedorMaterialIa generacion,ControlesMaterialIa.Facturable control,AutoCloseable cierre) implements AutoCloseable {
        @Override public void close(){try{cierre.close();}catch(Exception e){org.slf4j.LoggerFactory.getLogger(NativosEvaluacionMaterialIa.class).warn("El transporte IA conserva un cierre técnico pendiente.");}}
    }
    Sesion abrir(ConfiguracionEdicionOpenAi gen,ConfiguracionQcOpenAi qc);
}
