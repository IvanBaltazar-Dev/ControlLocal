package com.controllocal.service.materialia.openai;

import com.controllocal.service.materialia.ProveedorMaterialIa;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** OFF si falta cualquiera de las dos habilitaciones. No crea perfiles/fondos/credenciales/consentimientos. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"controllocal.ia.gasto-externo-habilitado","controllocal.material-ia.openai.edicion.habilitada"},
        havingValue="true",matchIfMissing=false)
public class ConfiguracionProveedorEdicionOpenAi {
    /** Sólo configuración servidor explícita/versionada, sin datos ni valores predeterminados. */
    @Bean
    ConfiguracionEdicionOpenAi configuracionEdicionOpenAi(Environment entorno,ObjectMapper json){
        String contenido=entorno.getProperty("controllocal.material-ia.openai.edicion.configuracion");
        if(contenido==null||contenido.isBlank())throw new IllegalStateException("Falta la configuración privada aprobada de edición IA.");
        try{return json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(contenido,ConfiguracionEdicionOpenAi.class);}
        catch(Exception invalida){
            // La causa Jackson puede contener tarifas/datos de configuración: no propagar ni registrar su payload.
            throw new IllegalStateException("La configuración privada de edición IA no conserva el contrato requerido.");
        }
    }
    @Bean
    ProveedorEdicionOpenAi.Credencial credencialEdicionOpenAi(Environment entorno){
        String token=entorno.getProperty("OPENAI_API_KEY");
        if(token==null||token.isBlank()||!token.equals(token.trim())||token.contains("\r")||token.contains("\n"))
            throw new IllegalStateException("Falta la credencial servidor del destino IA autorizado.");
        return ()->token;
    }
    @Bean(destroyMethod="close")
    TransporteHttpOpenAi transporteEdicionOpenAi(ConfiguracionEdicionOpenAi parametros){
        return new TransporteHttpOpenAi(parametros.conexion());
    }
    @Bean
    ProveedorMaterialIa proveedorEdicionOpenAi(ConfiguracionEdicionOpenAi parametros,
            ProveedorEdicionOpenAi.Credencial credencial,
            @org.springframework.beans.factory.annotation.Qualifier("transporteEdicionOpenAi") TransporteHttpOpenAi transporte){
        return new ProveedorEdicionOpenAi(parametros,credencial,transporte);
    }
}
