package com.controllocal.service.materialia.openai;

import com.controllocal.service.materialia.ControlesMaterialIa;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;

/** OFF sin ambas habilitaciones. No crea perfiles, rúbrica, permiso E44, fondos ni aprobación de ensayo. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"controllocal.ia.gasto-externo-habilitado","controllocal.material-ia.openai.qc.habilitado"},
        havingValue="true",matchIfMissing=false)
public class ConfiguracionControlQcOpenAi {
    @Bean ConfiguracionQcOpenAi configuracionQcOpenAi(Environment entorno,ObjectMapper json){
        String contenido=entorno.getProperty("controllocal.material-ia.openai.qc.configuracion");
        if(contenido==null||contenido.isBlank())throw new IllegalStateException("Falta configuración privada QC.");
        try{return json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(contenido,ConfiguracionQcOpenAi.class);}
        catch(Exception invalida){throw new IllegalStateException("La configuración privada QC no conserva el contrato.");}
    }
    @Bean(destroyMethod="close") TransporteHttpOpenAi transporteQcOpenAi(ConfiguracionQcOpenAi c){
        return new TransporteHttpOpenAi(c.conexion(),TransporteHttpOpenAi.Destino.RESPUESTAS);
    }
    @Bean ControlesMaterialIa.Facturable controlQcOpenAi(ConfiguracionQcOpenAi c,Environment entorno,
            @Qualifier("transporteQcOpenAi") TransporteHttpOpenAi transporte,EvaluacionTecnicaFoto evaluador){
        String token=entorno.getProperty("OPENAI_API_KEY");
        if(token==null||token.isBlank()||!token.equals(token.trim())||token.contains("\r")||token.contains("\n"))
            throw new IllegalStateException("Falta la credencial servidor del destino autorizado.");
        return new ControlQcOpenAi(c,()->token,transporte,evaluador);
    }
}
