package com.controllocal.service.materialia.openai;

import com.controllocal.service.materialia.ProveedorMaterialIa;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;

/** OFF salvo dos habilitaciones explícitas. No crea perfiles, tarifas, fondos, consentimiento o QC. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"controllocal.ia.gasto-externo-habilitado","controllocal.material-ia.openai.respuestas.habilitadas"},
        havingValue="true",matchIfMissing=false)
public class ConfiguracionProveedorRespuestasOpenAi {
    @Bean
    ConfiguracionRespuestasOpenAi configuracionRespuestasOpenAi(Environment entorno,ObjectMapper json){
        String contenido=entorno.getProperty("controllocal.material-ia.openai.respuestas.configuracion");
        if(contenido==null||contenido.isBlank())throw new IllegalStateException("Falta configuración privada de texto/análisis.");
        try{return json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(contenido,ConfiguracionRespuestasOpenAi.class);}
        catch(Exception invalida){throw new IllegalStateException("La configuración privada de texto/análisis no conserva el contrato.");}
    }
    @Bean(destroyMethod="close")
    TransporteHttpOpenAi transporteRespuestasOpenAi(ConfiguracionRespuestasOpenAi c){
        return new TransporteHttpOpenAi(c.conexion(),TransporteHttpOpenAi.Destino.RESPUESTAS);
    }
    @Bean
    ProveedorMaterialIa proveedorRespuestasOpenAi(ConfiguracionRespuestasOpenAi c,Environment entorno,
            @Qualifier("transporteRespuestasOpenAi") TransporteHttpOpenAi transporte){
        String token=entorno.getProperty("OPENAI_API_KEY");
        if(token==null||token.isBlank()||!token.equals(token.trim())||token.contains("\r")||token.contains("\n"))
            throw new IllegalStateException("Falta la credencial servidor del destino autorizado.");
        return new ProveedorRespuestasOpenAi(c,()->token,transporte);
    }
}
