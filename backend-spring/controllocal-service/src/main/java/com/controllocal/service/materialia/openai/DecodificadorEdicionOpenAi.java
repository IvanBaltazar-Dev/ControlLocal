package com.controllocal.service.materialia.openai;

import com.controllocal.domain.presupuesto.UnidadesDeConsumo.Medicion;
import com.controllocal.service.materialia.ProveedorMaterialIa.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dos pasadas sobre envoltura YA acotada: conservar usage aunque no se pueda aceptar la foto. */
final class DecodificadorEdicionOpenAi {
    private final ConfiguracionEdicionOpenAi configuracion;private final ObjectMapper json;
    DecodificadorEdicionOpenAi(ConfiguracionEdicionOpenAi c){
        configuracion=c;
        JsonFactory fabrica=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(c.envoltura().profundidadJson())
                        .maxStringLength(c.envoltura().maximoRespuestaBytes()).build()).build();
        json=new ObjectMapper(fabrica);
    }
    Salida leer(Peticion peticion,TransporteOpenAi.Respuesta respuesta) throws IOException {
        String tipo=respuesta.cabeceras().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        String codificacion=respuesta.cabeceras().firstValue("Content-Encoding").orElse("identity");
        if(!tipo.matches("application/json(?:\\s*;\\s*charset=utf-8)?")||!"identity".equalsIgnoreCase(codificacion))
            throw new IOException("Respuesta sin representación JSON acreditada.");
        var ids=respuesta.cabeceras().allValues("x-request-id");
        if(ids.size()!=1||!ids.getFirst().matches("[!-~]{1,1000}")||ids.getFirst().contains(","))
            throw new IOException("Falta correlación atribuible del proveedor.");
        String referencia="OPENAI_REQUEST:"+ids.getFirst();
        byte[] cuerpo=respuesta.cuerpo();
        if(cuerpo.length>configuracion.envoltura().maximoRespuestaBytes())throw new IOException("Envoltura no admitida.");
        JsonNode usage=null;boolean metadataCompatible=true;
        try(JsonParser p=json.getFactory().createParser(cuerpo)){
            exigir(p.nextToken()==JsonToken.START_OBJECT);
            while(p.nextToken()!=JsonToken.END_OBJECT){
                exigir(p.currentToken()==JsonToken.FIELD_NAME);String campo=p.currentName();JsonToken valor=p.nextToken();
                switch(campo){
                    case "usage" -> {exigir(valor==JsonToken.START_OBJECT);usage=json.readTree(p);}
                    case "output_format" -> {if(valor!=JsonToken.VALUE_STRING||!configuracion.parametros().formato().equals(p.getText()))metadataCompatible=false;p.skipChildren();}
                    case "size" -> {if(valor!=JsonToken.VALUE_STRING||!configuracion.parametros().size().equals(p.getText()))metadataCompatible=false;p.skipChildren();}
                    default -> p.skipChildren();
                }
            }
            exigir(p.nextToken()==null);
        }
        var medicion=medicion(usage,referencia);
        // HTTP no demuestra ausencia de procesamiento: usage coherente se conserva incluso con error/foto inválida.
        byte[] foto=null;
        if(respuesta.estado()==200&&metadataCompatible){
            try{
                foto=foto(cuerpo,peticion.limiteRecepcion().maximoBytesContenido());
                ImagenEdicionOpenAi.exigirSalida(foto,configuracion.parametros());
            }catch(IOException|RuntimeException invalida){foto=null;}
        }
        return new Salida(null,configuracion.parametros().mime(),foto,ProveedorEdicionOpenAi.PROCESO,
                configuracion.versionProceso(),new ResultadoFacturable(List.of(medicion)));
    }
    private MedicionFacturable medicion(JsonNode uso,String referencia)throws IOException{
        if(uso==null||!uso.isObject())throw new IOException("Consumo nativo no disponible.");
        exigirCampos(uso,Set.of("input_tokens","input_tokens_details","output_tokens","output_tokens_details","total_tokens"));
        long entrada=entero(uso,"input_tokens"),salida=entero(uso,"output_tokens"),total=entero(uso,"total_tokens");
        JsonNode detalle=uso.get("input_tokens_details");
        if(detalle==null||!detalle.isObject())throw new IOException("Falta desglose nativo de entrada.");
        exigirCampos(detalle,Set.of("text_tokens","image_tokens"));
        long texto=entero(detalle,"text_tokens"),imagen=entero(detalle,"image_tokens");
        try{exigir(Math.addExact(texto,imagen)==entrada&&Math.addExact(entrada,salida)==total);}
        catch(ArithmeticException exceso){throw new IOException("Consumo nativo incoherente.");}
        // Sunburst sólo produce imagen. Si existe desglose de salida, todo el total debe estar acreditado.
        if(uso.has("output_tokens_details")){
            JsonNode d=uso.get("output_tokens_details");exigir(d!=null&&d.isObject());
            exigirCampos(d,Set.of("text_tokens","image_tokens"));
            exigir(entero(d,"text_tokens")==0&&entero(d,"image_tokens")==salida);
        }
        var c=configuracion.usage();
        var unidades=List.of(new Medicion(c.claseTexto(),"token",BigDecimal.valueOf(texto),null),
                new Medicion(c.claseImagen(),"token",BigDecimal.valueOf(imagen),null),
                new Medicion(c.claseSalida(),"token",BigDecimal.valueOf(salida),null));
        UUID id=UUID.nameUUIDFromBytes((ProveedorEdicionOpenAi.IDENTIFICADOR+"\u0000"+referencia).getBytes(StandardCharsets.UTF_8));
        // x-request-id es correlación de respuesta/uso, NO factura ni importe confirmado.
        return new MedicionFacturable(id,ProveedorEdicionOpenAi.IDENTIFICADOR,ProveedorEdicionOpenAi.MODELO,
                c.versionTarifa(),unidades,c.tarifas(),null,referencia);
    }
    private static long entero(JsonNode n,String campo)throws IOException{
        var v=n.get(campo);if(v==null||!v.isIntegralNumber()||!v.canConvertToLong()||v.longValue()<0)
            throw new IOException("Falta una cantidad nativa entera comprobable.");return v.longValue();
    }
    private static void exigirCampos(JsonNode n,Set<String> permitidos)throws IOException{
        var campos=n.fieldNames();while(campos.hasNext())if(!permitidos.contains(campos.next()))
            throw new IOException("La versión del consumo nativo no está acreditada.");
    }
    private byte[] foto(byte[] cuerpo,long maximo)throws IOException{
        byte[] resultado=null;boolean datos=false;
        try(JsonParser p=json.getFactory().createParser(cuerpo)){
            exigir(p.nextToken()==JsonToken.START_OBJECT);
            while(p.nextToken()!=JsonToken.END_OBJECT){
                exigir(p.currentToken()==JsonToken.FIELD_NAME);String campo=p.currentName();JsonToken valor=p.nextToken();
                if(!"data".equals(campo)){p.skipChildren();continue;}
                datos=true;exigir(valor==JsonToken.START_ARRAY&&p.nextToken()==JsonToken.START_OBJECT);
                boolean codificada=false;
                while(p.nextToken()!=JsonToken.END_OBJECT){
                    exigir(p.currentToken()==JsonToken.FIELD_NAME);String k=p.currentName();JsonToken v=p.nextToken();
                    if("b64_json".equals(k)){
                        exigir(v==JsonToken.VALUE_STRING);var salida=new BinarioAcotado(maximo);
                        p.readBinaryValue(Base64Variants.MIME_NO_LINEFEEDS,salida);resultado=salida.contenido();codificada=true;
                    }else if("url".equals(k)){exigir(v==JsonToken.VALUE_NULL);}
                    else p.skipChildren();
                }
                exigir(codificada&&p.nextToken()==JsonToken.END_ARRAY);
            }
            exigir(datos&&resultado!=null&&resultado.length>0&&p.nextToken()==null);
        }
        return resultado;
    }
    static final class BinarioAcotado extends OutputStream {
        private final long maximo;private final ByteArrayOutputStream bytes;
        BinarioAcotado(long maximo){if(maximo<=0||maximo>Integer.MAX_VALUE)throw new IllegalArgumentException("Cota binaria no representable.");this.maximo=maximo;bytes=new ByteArrayOutputStream((int)Math.min(maximo,8192));}
        @Override public void write(int b)throws IOException{exigir(1<=maximo-bytes.size());bytes.write(b);}
        @Override public void write(byte[] b,int off,int len)throws IOException{Objects.checkFromIndexSize(off,len,b.length);exigir(len<=maximo-bytes.size());bytes.write(b,off,len);}
        byte[] contenido(){return bytes.toByteArray();}
    }
    private static void exigir(boolean condicion)throws IOException{if(!condicion)throw new IOException("Respuesta de edición no confirmada.");}
}
