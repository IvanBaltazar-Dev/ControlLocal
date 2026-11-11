package com.controllocal.service.materialia.openai;

import com.controllocal.service.materialia.ProveedorMaterialIa.Salida;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Usage pertenece al parser nativo compartido; la validez del texto no cambia su medición. */
final class DecodificadorRespuestasOpenAi {
    private final ConfiguracionRespuestasOpenAi.Ruta ruta;private final ObjectMapper json;
    DecodificadorRespuestasOpenAi(ConfiguracionRespuestasOpenAi.Ruta ruta){this.ruta=ruta;json=RespuestaNativaOpenAi.json(ruta.envoltura());}
    Salida leer(TransporteOpenAi.Respuesta r)throws IOException{
        var n=new RespuestaNativaOpenAi(ProveedorRespuestasOpenAi.IDENTIFICADOR,ruta.modelo(),ruta.envoltura(),ruta.usage()).leer(r);
        String texto=null;
        if(n.contenido()!=null){try{texto=texto(n.contenido());}catch(IOException|RuntimeException noUtilizable){texto=null;}}
        return new Salida(texto,"text/plain",null,ProveedorRespuestasOpenAi.PROCESO,ruta.versionProceso(),n.medicion());
    }
    private String texto(String codificado)throws IOException{
        JsonNode valor;
        try(JsonParser p=json.getFactory().createParser(codificado)){valor=json.readTree(p);exigir(p.nextToken()==null);}
        exigir(valor!=null&&valor.isObject());var it=valor.fieldNames();while(it.hasNext())exigir("texto".equals(it.next()));
        var texto=valor.get("texto");exigir(texto!=null&&texto.isTextual()&&!texto.textValue().isBlank());
        String resultado=texto.textValue();exigir(RespuestaNativaOpenAi.textoRepresentable(resultado)
                &&resultado.getBytes(StandardCharsets.UTF_8).length<=ruta.envoltura().maximoTextoBytes());
        return resultado;
    }
    private static void exigir(boolean c)throws IOException{if(!c)throw new IOException("Respuesta nativa no confirmada.");}
}
