package com.controllocal.service.materialia.openai;

import com.controllocal.domain.presupuesto.UnidadesDeConsumo.Medicion;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Único parser nativo/usage de Responses: consumidores texto comercial y QC. Sin coste confirmado supuesto. */
final class RespuestaNativaOpenAi {
    record Resultado(ResultadoFacturable medicion,String contenido,String referenciaRespuesta,String huellaRespuesta){
        @Override public String toString(){return "RespuestaNativaOpenAi[privada]";}
    }
    private final String proveedor,modelo;
    private final ConfiguracionRespuestasOpenAi.Envoltura envoltura;
    private final ConfiguracionRespuestasOpenAi.ContratoUsage usage;
    private final ObjectMapper json;
    RespuestaNativaOpenAi(String proveedor,String modelo,ConfiguracionRespuestasOpenAi.Envoltura envoltura,
                         ConfiguracionRespuestasOpenAi.ContratoUsage usage){
        this.proveedor=Objects.requireNonNull(proveedor);this.modelo=Objects.requireNonNull(modelo);
        this.envoltura=Objects.requireNonNull(envoltura);this.usage=Objects.requireNonNull(usage);json=json(envoltura);
    }
    static ObjectMapper json(ConfiguracionRespuestasOpenAi.Envoltura e){
        return new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(e.profundidadJson())
                        .maxStringLength(e.maximoRespuestaBytes()).build())
                .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(e.profundidadJson()).build()).build())
                // Los datos comerciales conservan su valor y precisión también al pasar por JsonNode.
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .configure(com.fasterxml.jackson.databind.cfg.JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES,false);
    }
    Resultado leer(TransporteOpenAi.Respuesta r)throws IOException{
        String tipo=r.cabeceras().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        if(!tipo.matches("application/json(?:\\s*;\\s*charset=utf-8)?")
                ||!"identity".equalsIgnoreCase(r.cabeceras().firstValue("Content-Encoding").orElse("identity")))
            throw new IOException("Representación no acreditada.");
        byte[] cuerpo=r.cuerpo();exigir(cuerpo.length<=envoltura.maximoRespuestaBytes());
        String id=null,modelo=null,estado=null,tier=null;JsonNode uso=null;
        try(JsonParser p=json.getFactory().createParser(cuerpo)){
            exigir(p.nextToken()==JsonToken.START_OBJECT);
            while(p.nextToken()!=JsonToken.END_OBJECT){
                exigir(p.currentToken()==JsonToken.FIELD_NAME);String campo=p.currentName();var valor=p.nextToken();
                switch(campo){
                    case "id" -> {exigir(valor==JsonToken.VALUE_STRING);id=p.getText();}
                    case "model" -> {exigir(valor==JsonToken.VALUE_STRING);modelo=p.getText();}
                    case "service_tier" -> {if(valor==JsonToken.VALUE_STRING)tier=p.getText();else p.skipChildren();}
                    case "status" -> {if(valor==JsonToken.VALUE_STRING)estado=p.getText();else p.skipChildren();}
                    case "usage" -> {exigir(valor==JsonToken.START_OBJECT);uso=json.readTree(p);}
                    default -> p.skipChildren();
                }
            }
            exigir(p.nextToken()==null);
        }
        exigir(id!=null&&id.matches("[!-~]{1,1000}")&&!id.contains(",")&&this.modelo.equals(modelo)&&"default".equals(tier));
        String referencia="OPENAI_RESPONSE:"+id;var medida=medicion(uso,referencia);String contenido=null;
        if(r.estado()==200&&"completed".equals(estado)){
            try{contenido=extraer(cuerpo);}catch(IOException|RuntimeException noUtilizable){contenido=null;}
        }
        return new Resultado(new ResultadoFacturable(List.of(medida)),contenido,referencia,huella(cuerpo));
    }
    static String huella(byte[] cuerpo){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cuerpo));}
        catch(NoSuchAlgorithmException imposible){throw new IllegalStateException("Falta SHA-256 del runtime.");}
    }
    /** PostgreSQL jsonb/UTF-8 no representan NUL ni sustitutos UTF-16 aislados. No corregir el texto recibido. */
    static boolean textoRepresentable(String texto){
        if(texto==null)return false;
        for(int i=0;i<texto.length();i++){
            char c=texto.charAt(i);if(c==0)return false;
            if(Character.isHighSurrogate(c)){
                if(++i==texto.length()||!Character.isLowSurrogate(texto.charAt(i)))return false;
            }else if(Character.isLowSurrogate(c))return false;
        }
        return true;
    }
    private MedicionFacturable medicion(JsonNode u,String referencia)throws IOException{
        exigir(u!=null&&u.isObject());campos(u,Set.of("input_tokens","input_tokens_details","output_tokens","output_tokens_details","total_tokens"));
        long entrada=entero(u,"input_tokens"),salida=entero(u,"output_tokens"),total=entero(u,"total_tokens");
        JsonNode d=u.get("input_tokens_details");exigir(d!=null&&d.isObject());
        campos(d,Set.of("cached_tokens","cache_write_tokens","cache_write_tokens_details"));
        // Ausencia de un contador no se transforma en cero ni en un descuento supuesto.
        long lectura=entero(d,"cached_tokens"),escritura=entero(d,"cache_write_tokens");
        try{exigir(Math.addExact(entrada,salida)==total&&Math.addExact(lectura,escritura)<=entrada);}
        catch(ArithmeticException exceso){throw new IOException("Usage incoherente.");}
        var c=usage;var unidades=new ArrayList<Medicion>();
        unidades.add(unidad(c.claseEntrada(),entrada,null));
        unidades.add(unidad(c.claseCacheLectura(),lectura,c.claseEntrada()));
        if(escritura>0||d.has("cache_write_tokens_details")){
            var w=d.get("cache_write_tokens_details");exigir(w!=null&&w.isObject());
            campos(w,Set.of("ttl_30m_tokens","ttl_12h_tokens"));
            long a=entero(w,"ttl_30m_tokens"),b=entero(w,"ttl_12h_tokens");
            try{exigir(Math.addExact(a,b)==escritura);}catch(ArithmeticException e){throw new IOException("Escritura incoherente.");}
            unidades.add(unidad(c.claseEscritura30m(),a,c.claseEntrada()));
            unidades.add(unidad(c.claseEscritura12h(),b,c.claseEntrada()));
        }
        unidades.add(unidad(c.claseSalida(),salida,null));
        if(u.has("output_tokens_details")){
            var o=u.get("output_tokens_details");exigir(o!=null&&o.isObject());campos(o,Set.of("reasoning_tokens"));
            if(o.has("reasoning_tokens")){
                long razonamiento=entero(o,"reasoning_tokens");exigir(razonamiento<=salida);
                unidades.add(unidad(c.claseRazonamiento(),razonamiento,c.claseSalida()));
            }
        }
        UUID id=UUID.nameUUIDFromBytes((proveedor+"\u0000"+referencia).getBytes(StandardCharsets.UTF_8));
        // id de respuesta/usage es correlación, NO factura ni costo confirmado. Las tarifas son configuración acreditada.
        return new MedicionFacturable(id,proveedor,modelo,c.versionTarifa(),
                unidades,c.tarifas(),null,referencia);
    }
    private String extraer(byte[] cuerpo)throws IOException{
        JsonNode raiz=json.readTree(cuerpo),salidas=raiz.get("output");exigir(salidas!=null&&salidas.isArray());
        String codificado=null;
        for(var item:salidas){
            String tipo=item.path("type").asText();
            if("reasoning".equals(tipo))continue; // No se entrega razonamiento interno.
            exigir("message".equals(tipo)&&"assistant".equals(item.path("role").asText())
                    &&"completed".equals(item.path("status").asText()));
            var contenido=item.get("content");exigir(contenido!=null&&contenido.isArray());
            for(var fragmento:contenido){
                exigir("output_text".equals(fragmento.path("type").asText())&&codificado==null
                        &&fragmento.path("text").isTextual());
                codificado=fragmento.get("text").textValue();
            }
        }
        exigir(codificado!=null);
        return codificado;
    }
    private static Medicion unidad(String c,long n,String padre){return new Medicion(c,"token",BigDecimal.valueOf(n),padre);}
    private static long entero(JsonNode n,String campo)throws IOException{
        var x=n.get(campo);exigir(x!=null&&x.isIntegralNumber()&&x.canConvertToLong()&&x.longValue()>=0);return x.longValue();
    }
    private static void campos(JsonNode n,Set<String> permitidos)throws IOException{
        var it=n.fieldNames();while(it.hasNext())exigir(permitidos.contains(it.next()));
    }
    private static void exigir(boolean c)throws IOException{if(!c)throw new IOException("Respuesta nativa no confirmada.");}
}
