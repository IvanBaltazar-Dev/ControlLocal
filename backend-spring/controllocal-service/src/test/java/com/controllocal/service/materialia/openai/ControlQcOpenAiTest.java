package com.controllocal.service.materialia.openai;

import com.controllocal.domain.presupuesto.UnidadesDeConsumo;
import com.controllocal.service.materialia.*;
import com.controllocal.service.materialia.MaterialIaService.*;
import com.controllocal.service.materialia.ControlesMaterialIa.*;
import com.controllocal.service.materialia.ProveedorMaterialIa.*;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/** Transporte, importes, rúbrica, fotos, fondos y credencial sintéticos TEST. Cero inferencia/DB. */
class ControlQcOpenAiTest {
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    static final String CLAVE="c".repeat(64),RUBRICA="RUBRICA_SINTETICA_TEST_SIN_VALIDEZ_OPERATIVA";
    static final Instant FECHA=Instant.parse("2026-11-03T00:00:00Z");
    static ConfiguracionQcOpenAi configuracion(){return new ConfiguracionQcOpenAi(
            List.of(ruta(Accion.REDACTAR_FICHA,4000,10000),ruta(Accion.ANALIZAR_FOTO,4000,10000),ruta(Accion.MEJORAR_FOTO,4000,10000)),Duration.ofSeconds(1),Duration.ofSeconds(2));}
    static ConfiguracionQcOpenAi.Ruta ruta(Accion a,int maximoControl,int maximoRespuesta){
        var base=ProveedorRespuestasOpenAiTest.ruta(a==Accion.MEJORAR_FOTO?Accion.ANALIZAR_FOTO:a,100);
        return new ConfiguracionQcOpenAi.Ruta(a,"PERFIL_"+a,"VERSION_TEST","CONTROL_TEST","RUBRICA_TEST",
                Idempotencia.huellaExacta(RUBRICA),"EVIDENCIA_RUBRICA_SERVIDOR_TEST",RUBRICA,base.parametros(),
                new ConfiguracionRespuestasOpenAi.Envoltura("ENV_QC_TEST","d".repeat(64),2000,2000,
                        a==Accion.REDACTAR_FICHA?0:4096,20000,maximoRespuesta,maximoControl,30),base.usage(),
                new ConfiguracionQcOpenAi.Cache("implicit","30m","POLITICA_CACHE_SINTETICA_TEST"),a==Accion.MEJORAR_FOTO?0:2000);
    }
    static PeticionControl peticion(Accion a)throws Exception{
        var datos=Map.<String,Object>of("inmueble",Map.of("area",100,"unidad","m2"));
        String instruccion="INSTRUCCION_PERMITIDA_TEST";
        var medicion=new DecodificadorRespuestasOpenAi(ProveedorRespuestasOpenAiTest.ruta(Accion.REDACTAR_FICHA,100))
                .leer(ProveedorRespuestasOpenAiTest.http(200,ProveedorRespuestasOpenAiTest.respuesta(Accion.REDACTAR_FICHA,"Resultado"))).medicion();
        byte[] png=a==Accion.REDACTAR_FICHA?null:new EvaluacionTecnicaFoto(1_000_000).evaluar(pngConTexto()).contenido();
        return new PeticionControl(a,datos,instruccion,new Salida(a==Accion.MEJORAR_FOTO?null:"RESULTADO_GENERADO_TEST",a==Accion.MEJORAR_FOTO?"image/png":"text/plain",
                a==Accion.MEJORAR_FOTO?pngConTexto():null,"PROCESO_GENERADO_PRIVADO_TEST","VERSION_GENERADA_PRIVADA_TEST",medicion),CLAVE,png);
    }
    static ObjectNode control(){
        var n=JSON.createObjectNode();n.put("decision","OBSERVADA");var senales=n.putArray("senales");
        for(var a:Aspecto.values()){var s=senales.addObject();s.put("aspecto",a.name());s.put("estado","EVALUADA");
            s.put("hallazgoBloqueante",false);s.put("observacion","Observación TEST "+a.name());}
        return n;
    }
    static ObjectNode respuesta(String contenido)throws Exception{
        var n=ProveedorRespuestasOpenAiTest.respuesta(Accion.ANALIZAR_FOTO,"NO_ENTREGAR_COMO_TEXTO");
        ((ObjectNode)n.path("output").get(0).path("content").get(0)).put("text",contenido);return n;
    }
    static ControlQcOpenAi adaptador(ConfiguracionQcOpenAi c,TransporteOpenAi t){
        return new ControlQcOpenAi(c,()->"CLAVE_TEST_NO_REAL",t,new EvaluacionTecnicaFoto(1_000_000),Clock.fixed(FECHA,ZoneOffset.UTC));
    }
    @ParameterizedTest @ValueSource(strings={"REDACTAR_FICHA","ANALIZAR_FOTO","MEJORAR_FOTO"})
    void payloadMinimoDestinoFijoUnaPublicacionYEvidenciaCore(String accion)throws Exception{
        var a=Accion.valueOf(accion);var enviada=new AtomicReference<HttpRequest>();var llamadas=new AtomicInteger();
        var http=ProveedorRespuestasOpenAiTest.http(200,respuesta(control().toString()));
        var p=adaptador(configuracion(),(r,max,total,contextoHttpTest,observadorHttpTest)->{
            assertEquals(CLAVE,contextoHttpTest.claveIntento());assertEquals(ControlQcOpenAi.IDENTIFICADOR,contextoHttpTest.proveedor());
            assertEquals("gpt-6.1-sol",contextoHttpTest.modelo());assertEquals("VERSION_TEST",contextoHttpTest.versionConfiguracion());
            enviada.set(r);llamadas.incrementAndGet();assertEquals(10000,max);return http;});
        var entrada=peticion(a);var r=p.evaluarFacturable(entrada,observacionHttpTest->{});assertEquals(1,llamadas.get());
        assertEquals(ProveedorRespuestasOpenAi.DESTINO,enviada.get().uri());assertEquals("POST",enviada.get().method());
        var bytes=ProveedorRespuestasOpenAiTest.cuerpo(enviada.get());var payload=JSON.readTree(bytes);
        assertEquals("gpt-6.1-sol",payload.path("model").asText());assertEquals(RUBRICA,payload.path("instructions").asText());
        assertEquals(CLAVE,enviada.get().headers().firstValue("X-Client-Request-Id").orElseThrow());
        assertFalse(payload.path("store").asBoolean());assertFalse(payload.path("stream").asBoolean());assertFalse(payload.path("background").asBoolean());
        assertEquals("default",payload.path("service_tier").asText());assertEquals("none",payload.path("tool_choice").asText());
        assertTrue(payload.path("tools").isEmpty());assertTrue(payload.path("text").path("format").path("strict").asBoolean());
        assertEquals("implicit",payload.path("prompt_cache_options").path("mode").asText());
        assertEquals("30m",payload.path("prompt_cache_options").path("ttl").asText());assertFalse(payload.path("prompt_cache_options").path("prewarm").asBoolean());
        assertEquals(a==Accion.REDACTAR_FICHA?1:a==Accion.ANALIZAR_FOTO?3:5,payload.path("input").get(0).path("content").size());
        var permitido=JSON.readTree(payload.path("input").get(0).path("content").get(0).path("text").asText());
        assertEquals("INSTRUCCION_PERMITIDA_TEST",permitido.path("instruccion_comercial").asText());
        if(a==Accion.MEJORAR_FOTO)assertFalse(permitido.has("resultado"));else assertEquals("RESULTADO_GENERADO_TEST",permitido.path("resultado").asText());
        String serializado=new String(bytes,StandardCharsets.UTF_8);
        for(String privado:List.of(CLAVE,"CLAVE_TEST_NO_REAL","ID_RECURSO_PRIVADO_TEST","VERSION_FUENTE_PRIVADA_TEST","TEXTO_LIBRE_NO_PERMITIDO_TEST",
                "UTILIDAD_PRIVADA_TEST","PENDIENTE_PRIVADO_TEST","FINALIDAD_PRIVADA_TEST","EVIDENCIA_JURIDICA_PRIVADA_TEST","contacto_privado_test@",
                "TARIFA_TEST","EVIDENCIA_SINTETICA","EVIDENCIA_RUBRICA_SERVIDOR_TEST","previous_response_id","conversation","PROCESO_GENERADO_PRIVADO_TEST"))
            assertFalse(serializado.contains(privado),privado);
        assertEquals(Decision.OBSERVADA,r.control().decision());assertEquals(5,r.control().senales().size());
        assertEquals(FECHA,r.control().evaluadaEn());assertEquals("CONTROL_TEST",r.control().version());
        for(var s:r.control().senales()){
            assertTrue(s.referenciaEvidencia().contains("OPENAI_RESPONSE:resp_SINTETICA"));
            assertTrue(s.referenciaEvidencia().contains(RespuestaNativaOpenAi.huella(http.cuerpo())));
        }
        assertNull(r.medicion().mediciones().getFirst().costoConfirmadoUsd());
        assertEquals(ControlQcOpenAi.IDENTIFICADOR,r.medicion().mediciones().getFirst().proveedor());
        assertEquals(0,new BigDecimal("43").compareTo(UnidadesDeConsumo.costoSinCuantizar(r.medicion().mediciones().getFirst().unidades(),r.medicion().mediciones().getFirst().tarifasReales())));
        assertFalse(p.toString().contains("CLAVE_TEST_NO_REAL"));assertFalse(entrada.toString().contains("RESULTADO_GENERADO_TEST"));
    }
    @ParameterizedTest @ValueSource(strings={"0.1000000000000000001","9007199254740992.1","1.00000000000000000001E-18","1.2300"})
    void datosPermitidosConservanPrecisionYEscalaHastaElPayloadQc(String literal)throws Exception{
        var decimal=new BigDecimal(literal);var inicial=peticion(Accion.REDACTAR_FICHA);
        var entrada=new PeticionControl(inicial.accion(),Map.of("area",decimal),inicial.instruccionPermitida(),
                inicial.salida(),inicial.claveEtapa(),null);
        var enviada=new AtomicReference<HttpRequest>();var llamadas=new AtomicInteger();
        var http=ProveedorRespuestasOpenAiTest.http(200,respuesta(control().toString()));
        var resultado=adaptador(configuracion(),(r,max,total,contextoHttpTest,observadorHttpTest)->{enviada.set(r);llamadas.incrementAndGet();return http;})
                .evaluarFacturable(entrada,observacionHttpTest->{});
        var cuerpo=JSON.readTree(ProveedorRespuestasOpenAiTest.cuerpo(enviada.get()));
        String comercial=cuerpo.path("input").get(0).path("content").get(0).path("text").asText();
        var exacto=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readValue(comercial,Map.class);
        assertEquals(decimal,((Map<?,?>)exacto.get("datos_permitidos")).get("area"));
        assertEquals(1,llamadas.get());assertNotNull(resultado.medicion());
    }
    @ParameterizedTest @ValueSource(strings={"JSON","JSON_DUPLICADO","EXTRA","FALTANTE","DUPLICADO","ENUM","BOOLEANO","REFERENCIA_LLM","FECHA_LLM","OBSERVACION_VACIA","NULO","REFUSAL","INCOMPLETE","HTTP500"})
    void controlInutilizableConservaUsoNativoSinRepetir(String defecto)throws Exception{
        var c=control();String contenido;
        switch(defecto){
            case "EXTRA" -> c.put("extra","NO_ADMITIR");
            case "FALTANTE" -> c.withArray("senales").remove(0);
            case "DUPLICADO" -> ((ObjectNode)c.path("senales").get(0)).put("aspecto",Aspecto.FIDELIDAD.name());
            case "ENUM" -> c.put("decision","FALSA");
            case "BOOLEANO" -> ((ObjectNode)c.path("senales").get(0)).put("hallazgoBloqueante","false");
            case "REFERENCIA_LLM" -> ((ObjectNode)c.path("senales").get(0)).put("referenciaEvidencia","FACTURA_FALSA");
            case "FECHA_LLM" -> c.put("evaluadaEn","2000-01-01T00:00:00Z");
            case "OBSERVACION_VACIA" -> ((ObjectNode)c.path("senales").get(0)).put("observacion"," ");
            case "NULO" -> ((ObjectNode)c.path("senales").get(0)).put("observacion","detalle"+(char)0+"TEST");
        }
        contenido="JSON".equals(defecto)?"{":c.toString();
        if("JSON_DUPLICADO".equals(defecto))contenido="{\"decision\":\"OBSERVADA\",\"decision\":\"APROBADA\",\"senales\":"+c.get("senales")+"}";
        var n=respuesta(contenido);
        if("REFUSAL".equals(defecto))((ObjectNode)n.path("output").get(0).path("content").get(0)).put("type","refusal");
        if("INCOMPLETE".equals(defecto))n.put("status","incomplete");
        var http=ProveedorRespuestasOpenAiTest.http("HTTP500".equals(defecto)?500:200,n);var llamadas=new AtomicInteger();
        var r=adaptador(configuracion(),(a,b,d,contextoHttpTest,observadorHttpTest)->{llamadas.incrementAndGet();return http;}).evaluarFacturable(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{});
        assertNull(r.control());assertEquals(1,r.medicion().mediciones().size());assertEquals(1,llamadas.get());
        assertTrue(r.medicion().mediciones().getFirst().unidades().stream().anyMatch(x->x.cantidad().signum()>0));
    }
    @ParameterizedTest @ValueSource(strings={"usage","cached_tokens","cache_write_tokens","modelo","total_incoherente"})
    void usoAusenteOIncoherenteQuedaInciertoNoCero(String defecto)throws Exception{
        var n=respuesta(control().toString());
        switch(defecto){
            case "usage" -> n.remove("usage");
            case "cached_tokens","cache_write_tokens" -> ((ObjectNode)n.path("usage").path("input_tokens_details")).remove(defecto);
            case "modelo" -> n.put("model","modelo_no_aprobado");
            case "total_incoherente" -> ((ObjectNode)n.path("usage")).put("total_tokens",1);
        }
        var http=ProveedorRespuestasOpenAiTest.http(200,n);var llamadas=new AtomicInteger();
        assertThrows(ProcesamientoIncierto.class,()->adaptador(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->{llamadas.incrementAndGet();return http;})
                .evaluarFacturable(peticion(Accion.ANALIZAR_FOTO),observacionHttpTest->{}));assertEquals(1,llamadas.get());
    }
    @ParameterizedTest @ValueSource(strings={"ALTO","BAJO","PAREJA"})
    void unicodeEscapadoNoSeReemplazaYConservaUsage(String caso)throws Exception{
        String escape=switch(caso){case "ALTO"->"\\uD800";case "BAJO"->"\\uDC00";default->"\\uD83D\\uDE00";};
        String contenido=control().toString().replace("Observación TEST CALIDAD_VISUAL","detalle"+escape+"TEST");
        var http=ProveedorRespuestasOpenAiTest.http(200,respuesta(contenido));var llamadas=new AtomicInteger();
        var r=adaptador(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->{llamadas.incrementAndGet();return http;}).evaluarFacturable(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{});
        if(caso.equals("PAREJA"))assertEquals("detalle"+new String(Character.toChars(0x1F600))+"TEST",r.control().senales().getFirst().observacion());
        else assertNull(r.control());
        assertEquals(1,r.medicion().mediciones().size());assertEquals(1,llamadas.get());
    }
    @Test void limiteControlDecodificadoExactoYMasUnoEnBytesUtf8()throws Exception{
        var c=control();((ObjectNode)c.path("senales").get(0)).put("observacion","áéíóú TEST");String texto=c.toString();
        int bytes=texto.getBytes(StandardCharsets.UTF_8).length;assertTrue(bytes>texto.length());
        var http=ProveedorRespuestasOpenAiTest.http(200,respuesta(texto));
        for(int limite:List.of(bytes,bytes-1)){
            var cfg=new ConfiguracionQcOpenAi(List.of(ruta(Accion.REDACTAR_FICHA,limite,10000)),Duration.ofSeconds(1),Duration.ofSeconds(2));
            var r=adaptador(cfg,(a,b,d,contextoHttpTest,observadorHttpTest)->http).evaluarFacturable(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{});
            assertEquals(limite==bytes,r.control()!=null);assertNotNull(r.medicion());
        }
    }
    @Test void envolturaRealExactaYMasUnoNoConfiaEnContentLength()throws Exception{
        byte[] cuerpo=JSON.writeValueAsBytes(respuesta(control().toString()));
        for(boolean cabecera:List.of(true,false))for(boolean exceso:List.of(false,true)){
            byte[] recibido=Arrays.copyOf(cuerpo,cuerpo.length+(exceso?1:0));if(exceso)recibido[recibido.length-1]=' ';
            var h=new HashMap<String,List<String>>();h.put("Content-Type",List.of("application/json"));if(cabecera)h.put("Content-Length",List.of("1"));
            var http=new TransporteOpenAi.Respuesta(200,HttpHeaders.of(h,(k,v)->true),recibido);
            var cfg=new ConfiguracionQcOpenAi(List.of(ruta(Accion.REDACTAR_FICHA,1000,cuerpo.length)),Duration.ofSeconds(1),Duration.ofSeconds(2));
            var llamadas=new AtomicInteger();var p=adaptador(cfg,(a,b,c,contextoHttpTest,observadorHttpTest)->{llamadas.incrementAndGet();assertEquals(cuerpo.length,b);return http;});
            if(exceso)assertThrows(ProcesamientoIncierto.class,()->p.evaluarFacturable(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{}));
            else assertNotNull(p.evaluarFacturable(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{}).control());assertEquals(1,llamadas.get());
        }
    }
    @Test void falloPosteriorAlEnvioNoEsNoInvocadoNiReintento()throws Exception{
        var llamadas=new AtomicInteger();var p=adaptador(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->{llamadas.incrementAndGet();throw new java.util.concurrent.TimeoutException("PRIVADO_TEST");});
        var e=assertThrows(ProcesamientoIncierto.class,()->p.evaluarFacturable(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{}));
        assertEquals(1,llamadas.get());assertFalse(e.getMessage().contains("PRIVADO_TEST"));assertNull(e.getCause());
    }
    @Test void noTransfiereDentroDeSqlYConservaPruebaLocalSinPayload()throws Exception{
        var llamadas=new AtomicInteger();var p=adaptador(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->{llamadas.incrementAndGet();throw new AssertionError();});
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{var e=assertThrows(NoIniciadoComprobado.class,()->p.evaluarFacturable(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{}));
            assertEquals(CLAVE,e.evidencia().claveIntento());assertEquals(ControlQcOpenAi.IDENTIFICADOR,e.evidencia().proveedor());}
        finally{TransactionSynchronizationManager.setActualTransactionActive(false);}assertEquals(0,llamadas.get());
    }
    @Test void mejorarDepuraCopiaConEvaluadorExistenteSinCambiarRawNiMedicion()throws Exception{
        var entrada=peticion(Accion.MEJORAR_FOTO);byte[] antes=entrada.salida().foto();var recibida=new AtomicReference<HttpRequest>();
        var http=ProveedorRespuestasOpenAiTest.http(200,respuesta(control().toString()));var evaluaciones=new AtomicInteger();
        var evaluador=new EvaluacionTecnicaFoto(1_000_000){@Override public synchronized Copia evaluar(byte[] bytes){
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());evaluaciones.incrementAndGet();return super.evaluar(bytes);}};
        var p=new ControlQcOpenAi(configuracion(),()->"CLAVE_TEST_NO_REAL",(a,b,c,contextoHttpTest,observadorHttpTest)->{recibida.set(a);return http;},evaluador,Clock.fixed(FECHA,ZoneOffset.UTC));
        var r=p.evaluarFacturable(entrada,observacionHttpTest->{});assertEquals(1,evaluaciones.get());
        var cuerpo=ProveedorRespuestasOpenAiTest.cuerpo(recibida.get());var contenido=JSON.readTree(cuerpo).path("input").get(0).path("content");
        byte[] enviada=Base64.getDecoder().decode(contenido.get(4).path("image_url").asText().substring("data:image/png;base64,".length()));
        assertTrue(new String(antes,StandardCharsets.ISO_8859_1).contains("GPS_PRIVADO_DERIVADA_TEST"));
        assertFalse(new String(enviada,StandardCharsets.ISO_8859_1).contains("GPS_PRIVADO_DERIVADA_TEST"));
        assertFalse(new String(enviada,StandardCharsets.ISO_8859_1).contains("tEXt"));
        assertArrayEquals(antes,entrada.salida().foto());assertEquals("OPENAI_RESPONSE:resp_SINTETICA",entrada.salida().medicion().mediciones().getFirst().referenciaFuente());
        assertFalse(Arrays.equals(antes,enviada));String evidencia=r.control().senales().getFirst().referenciaEvidencia();
        assertTrue(evidencia.contains("COPIA_DERIVADA:FOTO-LOCAL-1"));assertTrue(evidencia.contains(RespuestaNativaOpenAi.huella(antes)));
        assertTrue(evidencia.contains(RespuestaNativaOpenAi.huella(enviada)));assertTrue(evidencia.contains(RespuestaNativaOpenAi.huella(cuerpo)));
    }
    @Test void falloDeCopiaAntesDelPostConservaNoInvocacionDeQcYRaw()throws Exception{
        var entrada=peticion(Accion.MEJORAR_FOTO);byte[] antes=entrada.salida().foto();var llamadas=new AtomicInteger();
        var evaluador=new EvaluacionTecnicaFoto(1_000_000){@Override public synchronized Copia evaluar(byte[] bytes){throw new IllegalStateException("METADATOS_PRIVADOS_TEST");}};
        var p=new ControlQcOpenAi(configuracion(),()->"CLAVE_TEST_NO_REAL",(a,b,c,contextoHttpTest,observadorHttpTest)->{llamadas.incrementAndGet();throw new AssertionError();},evaluador,Clock.fixed(FECHA,ZoneOffset.UTC));
        var e=assertThrows(NoIniciadoComprobado.class,()->p.evaluarFacturable(entrada,observacionHttpTest->{}));assertEquals(0,llamadas.get());
        assertEquals(CLAVE,e.evidencia().claveIntento());assertEquals(ControlQcOpenAi.IDENTIFICADOR,e.evidencia().proveedor());
        assertEquals(FECHA,e.evidencia().comprobadaEn());assertFalse(e.getMessage().contains("METADATOS_PRIVADOS_TEST"));assertNull(e.getCause());
        assertArrayEquals(antes,entrada.salida().foto());assertNotNull(entrada.salida().medicion());
    }
    private static byte[] pngConTexto()throws Exception{
        var imagen=new BufferedImage(6,4,BufferedImage.TYPE_INT_RGB);var escritor=ImageIO.getImageWritersByFormatName("png").next();
        var p=escritor.getDefaultWriteParam();var meta=escritor.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(imagen),p);
        var root=new IIOMetadataNode("javax_imageio_png_1.0");var textos=new IIOMetadataNode("tEXt");var texto=new IIOMetadataNode("tEXtEntry");
        texto.setAttribute("keyword","Comment");texto.setAttribute("value","GPS_PRIVADO_DERIVADA_TEST C2PA_ANTECEDENTE_SINTETICO_TEST");
        textos.appendChild(texto);root.appendChild(textos);meta.mergeTree("javax_imageio_png_1.0",root);
        var salida=new ByteArrayOutputStream();try(var out=new MemoryCacheImageOutputStream(salida)){escritor.setOutput(out);escritor.write(null,new IIOImage(imagen,null,meta),p);}
        finally{escritor.dispose();}return salida.toByteArray();
    }
}
