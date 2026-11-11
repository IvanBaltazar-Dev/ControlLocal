package com.controllocal.service.materialia.openai;

import com.controllocal.domain.presupuesto.UnidadesDeConsumo;
import com.controllocal.domain.presupuesto.UnidadesDeConsumo.Tarifa;
import com.controllocal.service.materialia.MaterialIaService.Accion;
import com.controllocal.service.materialia.ProveedorMaterialIa.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Todo dato, tarifa y clave es artificial TEST. No fondos, inferencia ni configuración operativa. */
class ProveedorRespuestasOpenAiTest {
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    static final String CLAVE="a".repeat(64);
    static ConfiguracionRespuestasOpenAi configuracion(){
        return new ConfiguracionRespuestasOpenAi(List.of(ruta(Accion.REDACTAR_FICHA,100),ruta(Accion.ANALIZAR_FOTO,100)),
                Duration.ofSeconds(1),Duration.ofSeconds(2));
    }
    static ConfiguracionRespuestasOpenAi.Ruta ruta(Accion a,int texto){
        String[] nombres={"entrada","lectura","escritura30","escritura12","salida","razonamiento"};
        var tarifas=new ArrayList<Tarifa>();
        for(int i=0;i<nombres.length;i++)tarifas.add(new Tarifa(nombres[i],"token",BigDecimal.valueOf(Math.min(i+1,5)),BigDecimal.ONE));
        return new ConfiguracionRespuestasOpenAi.Ruta(a,"PERFIL_"+a,"VERSION_TEST","PROCESO_TEST",
                new ConfiguracionRespuestasOpenAi.Parametros("low",a==Accion.ANALIZAR_FOTO?"auto":null,50),
                new ConfiguracionRespuestasOpenAi.Envoltura("ENV_TEST","b".repeat(64),2000,2000,
                        a==Accion.ANALIZAR_FOTO?1024:0,10000,10000,texto,30),
                new ConfiguracionRespuestasOpenAi.ContratoUsage("USO_TEST","EVIDENCIA_SINTETICA","TARIFA_TEST",
                        nombres[0],nombres[1],nombres[2],nombres[3],nombres[4],nombres[5],tarifas));
    }
    static Peticion peticion(Accion a){
        byte[] png=a==Accion.ANALIZAR_FOTO?new byte[]{(byte)137,80,78,71,13,10,26,10,0}:null;
        return new Peticion(CLAVE,a,Map.of("inmueble",Map.of("area",100,"unidad","m2")),
                "INSTRUCCION_MINIMIZADA_TEST",png,null);
    }
    static ObjectNode respuesta(Accion a,String texto)throws Exception{
        var n=JSON.createObjectNode();n.put("id","resp_SINTETICA");n.put("model",a==Accion.REDACTAR_FICHA?"gpt-6-luna":"gpt-6.1-sol");
        n.put("status","completed");n.put("service_tier","default");
        var u=n.putObject("usage");u.put("input_tokens",10);u.put("output_tokens",5);u.put("total_tokens",15);
        var d=u.putObject("input_tokens_details");d.put("cached_tokens",3);d.put("cache_write_tokens",2);
        var w=d.putObject("cache_write_tokens_details");w.put("ttl_30m_tokens",1);w.put("ttl_12h_tokens",1);
        u.putObject("output_tokens_details").put("reasoning_tokens",2);
        var m=n.putArray("output").addObject();m.put("type","message");m.put("role","assistant");m.put("status","completed");
        var c=m.putArray("content").addObject();c.put("type","output_text");
        c.put("text",JSON.writeValueAsString(Map.of("texto",texto)));return n;
    }
    static TransporteOpenAi.Respuesta http(int estado,JsonNode n)throws Exception{
        return new TransporteOpenAi.Respuesta(estado,HttpHeaders.of(Map.of("Content-Type",List.of("application/json"),
                "Content-Length",List.of("1")),(k,v)->true),JSON.writeValueAsBytes(n));
    }
    static ProveedorRespuestasOpenAi proveedor(ConfiguracionRespuestasOpenAi c,TransporteOpenAi t){
        return new ProveedorRespuestasOpenAi(c,()->"CLAVE_TEST_NO_REAL",t);
    }
    static byte[] cuerpo(HttpRequest r)throws Exception{
        return ProveedorEdicionOpenAiTest.body(r.bodyPublisher().orElseThrow());
    }
    @ParameterizedTest @ValueSource(strings={"REDACTAR_FICHA","ANALIZAR_FOTO"})
    void modeloExactoPayloadMinimoYEfectosAusentes(String accion)throws Exception{
        var a=Accion.valueOf(accion);var enviada=new AtomicReference<HttpRequest>();var veces=new AtomicInteger();
        var contestacion=http(200,respuesta(a,"Resultado TEST"));
        var p=proveedor(configuracion(),(r,max,t,contextoHttpTest,observadorHttpTest)->{
            assertEquals(CLAVE,contextoHttpTest.claveIntento());assertEquals(ProveedorRespuestasOpenAi.IDENTIFICADOR,contextoHttpTest.proveedor());
            assertEquals(a==Accion.REDACTAR_FICHA?"gpt-6-luna":"gpt-6.1-sol",contextoHttpTest.modelo());assertEquals("VERSION_TEST",contextoHttpTest.versionConfiguracion());
            enviada.set(r);veces.incrementAndGet();return contestacion;});
        var resultado=p.ejecutar(peticion(a),observacionHttpTest->{});var solicitud=enviada.get();
        assertEquals(ProveedorRespuestasOpenAi.DESTINO,solicitud.uri());assertEquals(1,veces.get());
        var bytes=cuerpo(solicitud);var payload=JSON.readTree(bytes);
        assertEquals(a==Accion.REDACTAR_FICHA?"gpt-6-luna":"gpt-6.1-sol",payload.path("model").asText());
        assertFalse(payload.path("store").asBoolean());assertFalse(payload.path("stream").asBoolean());
        assertEquals("none",payload.path("tool_choice").asText());assertTrue(payload.path("tools").isEmpty());
        assertEquals("INSTRUCCION_MINIMIZADA_TEST",payload.path("instructions").asText());
        assertEquals(a==Accion.ANALIZAR_FOTO?2:1,payload.path("input").get(0).path("content").size());
        String serializado=new String(bytes,StandardCharsets.UTF_8);
        for(String privado:List.of(CLAVE,"CLAVE_TEST_NO_REAL","namespace","limiteRecepcion","huellaConsentimiento",
                "versionTarifa","TARIFA_TEST","EVIDENCIA_SINTETICA","previous_response_id","conversation"))
            assertFalse(serializado.contains(privado),privado);
        assertNull(resultado.foto());assertEquals("Resultado TEST",resultado.texto());
        assertNull(resultado.medicion().mediciones().getFirst().costoConfirmadoUsd());
        assertFalse(p.toString().contains("CLAVE_TEST_NO_REAL"));
    }
    @Test void cacheYRazonamientoNoSeCuentanDosVeces()throws Exception{
        var r=http(200,respuesta(Accion.REDACTAR_FICHA,"Texto"));
        var m=proveedor(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->r).ejecutar(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{}).medicion().mediciones().getFirst();
        assertEquals(0,new BigDecimal("43").compareTo(UnidadesDeConsumo.costoSinCuantizar(m.unidades(),m.tarifasReales())));
        assertEquals("OPENAI_RESPONSE:resp_SINTETICA",m.referenciaFuente());
        assertTrue(m.unidades().stream().anyMatch(x->x.clase().equals("razonamiento")&&x.subconjuntoDe().equals("salida")));
    }
    @ParameterizedTest @ValueSource(strings={"incomplete","failed","cancelled"})
    void incompletitudConUsageConservaMedicionSinTextoNiReintento(String estado)throws Exception{
        var n=respuesta(Accion.REDACTAR_FICHA,"No entregar");n.put("status",estado);var r=http(200,n);var veces=new AtomicInteger();
        var s=proveedor(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->{veces.incrementAndGet();return r;}).ejecutar(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{});
        assertNull(s.texto());assertNotNull(s.medicion());assertEquals(1,veces.get());
    }
    @ParameterizedTest @ValueSource(strings={"INVALIDO","RECHAZO","HERRAMIENTA","NULO"})
    void salidaNoUtilizableNoDescartaUsage(String defecto)throws Exception{
        var n=respuesta(Accion.REDACTAR_FICHA,"Texto");
        ObjectNode fragmento=(ObjectNode)n.path("output").get(0).path("content").get(0);
        if("INVALIDO".equals(defecto))fragmento.put("text","{\"ajeno\":\"NO_ENTREGAR\"}");
        if("RECHAZO".equals(defecto))fragmento.put("type","refusal");
        if("HERRAMIENTA".equals(defecto))((ObjectNode)n.path("output").get(0)).put("type","function_call");
        if("NULO".equals(defecto))fragmento.put("text",JSON.writeValueAsString(Map.of("texto","detalle"+(char)0+"TEST")));
        var r=http(200,n);var s=proveedor(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->r).ejecutar(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{});
        assertNull(s.texto());assertEquals(1,s.medicion().mediciones().size());
    }
    @ParameterizedTest @ValueSource(strings={"ALTO","BAJO","PAREJA"})
    void textoUnicodeEscapadoNoSeReemplazaYConservaUsage(String caso)throws Exception{
        String escape=switch(caso){case "ALTO"->"\\uD800";case "BAJO"->"\\uDC00";default->"\\uD83D\\uDE00";};
        var n=respuesta(Accion.REDACTAR_FICHA,"TEST");
        ((ObjectNode)n.path("output").get(0).path("content").get(0)).put("text","{\"texto\":\"detalle"+escape+"TEST\"}");
        var http=http(200,n);var llamadas=new AtomicInteger();
        var r=proveedor(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->{llamadas.incrementAndGet();return http;}).ejecutar(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{});
        if(caso.equals("PAREJA"))assertEquals("detalle"+new String(Character.toChars(0x1F600))+"TEST",r.texto());else assertNull(r.texto());
        assertEquals(1,r.medicion().mediciones().size());assertEquals(1,llamadas.get());
    }
    @Test void errorHttpConUsageConservaHechoCausado()throws Exception{
        var r=http(500,respuesta(Accion.REDACTAR_FICHA,"No entregar"));
        var s=proveedor(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->r).ejecutar(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{});
        assertNull(s.texto());assertNotNull(s.medicion());
    }
    @ParameterizedTest @ValueSource(strings={"usage","input_tokens","cached_tokens","cache_write_tokens","total_incoherente","modelo","id"})
    void ausenciaONativoIncoherenteNoSeConvierteEnCero(String falta)throws Exception{
        var n=respuesta(Accion.REDACTAR_FICHA,"Texto");
        switch(falta){
            case "usage" -> n.remove("usage");
            case "input_tokens" -> ((ObjectNode)n.get("usage")).remove("input_tokens");
            case "cached_tokens","cache_write_tokens" -> ((ObjectNode)n.path("usage").path("input_tokens_details")).remove(falta);
            case "total_incoherente" -> ((ObjectNode)n.get("usage")).put("total_tokens",14);
            case "modelo" -> n.put("model","otro_modelo_no_autorizado");
            case "id" -> n.remove("id");
        }
        var r=http(200,n);var veces=new AtomicInteger();
        assertThrows(ProcesamientoIncierto.class,()->proveedor(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->{veces.incrementAndGet();return r;}).ejecutar(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{}));
        assertEquals(1,veces.get());
    }
    @Test void timeoutDespuesDeEnviarConservaIncertidumbreYUnaInvocacion(){
        var veces=new AtomicInteger();
        var p=proveedor(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->{veces.incrementAndGet();throw new java.util.concurrent.TimeoutException("DATOS_PRIVADOS_TEST");});
        var e=assertThrows(ProcesamientoIncierto.class,()->p.ejecutar(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{}));
        assertNull(e.getCause());assertFalse(e.getMessage().contains("DATOS_PRIVADOS_TEST"));assertEquals(1,veces.get());
    }
    @ParameterizedTest @ValueSource(strings={"áá","ááa"})
    void limiteTextoUtf8ExactoYMasUnoConservaUso(String texto)throws Exception{
        var c=new ConfiguracionRespuestasOpenAi(List.of(ruta(Accion.REDACTAR_FICHA,4)),Duration.ofSeconds(1),Duration.ofSeconds(2));
        var r=http(200,respuesta(Accion.REDACTAR_FICHA,texto));var s=proveedor(c,(a,b,d,contextoHttpTest,observadorHttpTest)->r).ejecutar(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{});
        if(texto.equals("áá"))assertEquals(texto,s.texto());else assertNull(s.texto());
        assertNotNull(s.medicion());
    }
    @Test void invalidacionLocalNoTransfiereNiReintenta(){
        var veces=new AtomicInteger();var p=proveedor(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->{veces.incrementAndGet();throw new AssertionError("NO DEBE ENVIAR");});
        var q=new Peticion(CLAVE,Accion.ANALIZAR_FOTO,Map.of(),"Instrucción",new byte[]{1,2,3},null);
        var e=assertThrows(NoIniciadoComprobado.class,()->p.ejecutar(q,observacionHttpTest->{}));assertEquals(CLAVE,e.evidencia().claveIntento());assertEquals(0,veces.get());
    }
    @Test void identidadDeMedicionEstableNoEsFactura()throws Exception{
        var r=http(200,respuesta(Accion.REDACTAR_FICHA,"Texto"));var p=proveedor(configuracion(),(a,b,c,contextoHttpTest,observadorHttpTest)->r);
        var a=p.ejecutar(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{}).medicion().mediciones().getFirst();
        var b=p.ejecutar(peticion(Accion.REDACTAR_FICHA),observacionHttpTest->{}).medicion().mediciones().getFirst();
        assertEquals(a.id(),b.id());assertNull(a.costoConfirmadoUsd());
    }
}
