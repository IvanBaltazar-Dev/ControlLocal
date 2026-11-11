package com.controllocal.service.materialia.openai;

import com.controllocal.domain.presupuesto.UnidadesDeConsumo;
import com.controllocal.domain.presupuesto.UnidadesDeConsumo.Tarifa;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.materialia.MaterialIaService.Accion;
import com.controllocal.service.materialia.ProveedorMaterialIa.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Propuesta sin ejecutar. Todo HTTP/usage/precio es TEST explícito, sin proveedor ni gasto. */
class ProveedorEdicionOpenAiTest {
    final ObjectMapper json=new ObjectMapper();final AtomicInteger llamadas=new AtomicInteger();
    byte[] fuente,salida;HttpRequest enviada;TransporteOpenAi.Respuesta respuesta;
    ConfiguracionEdicionOpenAi c;ProveedorEdicionOpenAi proveedor;
    @BeforeEach void preparar()throws Exception{
        fuente=png(64,64);salida=png(1024,1024);c=configuracion();respuesta=respuesta(200,cuerpo(salida,uso()),"req_TEST_UNICO");
        proveedor=new ProveedorEdicionOpenAi(c,()->"CLAVE_TEST_NO_REAL",(p,max,total,contextoHttpTest,observadorHttpTest)->{
            assertEquals(ProveedorEdicionOpenAi.IDENTIFICADOR,contextoHttpTest.proveedor());assertEquals(ProveedorEdicionOpenAi.MODELO,contextoHttpTest.modelo());
            assertEquals(c.versionPerfil(),contextoHttpTest.versionConfiguracion());assertEquals(p.headers().firstValue("X-Client-Request-Id").orElseThrow(),contextoHttpTest.claveIntento());
            llamadas.incrementAndGet();enviada=p;return respuesta;});
    }
    static ConfiguracionEdicionOpenAi configuracion(){
        // Números puramente sintéticos, no tabla de precios ni cotas del ensayo real.
        var tarifas=List.of(new Tarifa("TEXTO_TEST","token",BigDecimal.ONE,BigDecimal.ONE),
                new Tarifa("IMAGEN_TEST","token",new BigDecimal("2"),BigDecimal.ONE),
                new Tarifa("SALIDA_TEST","token",new BigDecimal("3"),BigDecimal.ONE));
        return new ConfiguracionEdicionOpenAi("PERFIL_TEST","VERSION_TEST","PROCESO_TEST",
                new ConfiguracionEdicionOpenAi.Parametros("medium","png",null,"opaque",1024,1024,1048576),
                new ConfiguracionEdicionOpenAi.Envoltura("ENV_TEST","a".repeat(64),16384,32768,16),
                new ConfiguracionEdicionOpenAi.ContratoUsage("USAGE_TEST","EVIDENCIA_TEST_NO_REAL","TARIFA_TEST",
                        "TEXTO_TEST","IMAGEN_TEST","SALIDA_TEST",tarifas),Duration.ofSeconds(2),Duration.ofSeconds(2));
    }
    Peticion peticion(long max){return new Peticion("b".repeat(64),Accion.MEJORAR_FOTO,
            Map.of("contacto_privado","NO_ENVIAR","url","https://no-se-debe-abrir.invalid"),"INSTRUCCION_PERMITIDA_TEST",fuente,
            new UsoAlmacenamientoB0.LimiteRecepcion(UsoAlmacenamientoB0.referenciaPrevision(1,UUID.randomUUID()),max,"VERSION_TEST","FUENTE_PRIVADA_TEST","c".repeat(64)));}
    static Map<String,Object> uso(){return new LinkedHashMap<>(Map.of("input_tokens",30,"output_tokens",40,"total_tokens",70,
            "input_tokens_details",Map.of("text_tokens",10,"image_tokens",20)));}
    @Test void transporteRealConHttpSimuladoObservaErrorAunqueElAdaptadorLoDeclareIncierto(){
        var cliente=org.mockito.Mockito.mock(HttpClient.class);org.mockito.Mockito.when(cliente.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        @SuppressWarnings("unchecked")HttpResponse<byte[]> http=org.mockito.Mockito.mock(HttpResponse.class);
        org.mockito.Mockito.when(http.statusCode()).thenReturn(503);org.mockito.Mockito.when(http.headers()).thenReturn(HttpHeaders.of(Map.of(),(k,v)->true));
        org.mockito.Mockito.when(http.body()).thenReturn("PRIVADO_NO_OBSERVAR".getBytes(StandardCharsets.UTF_8));
        org.mockito.Mockito.doReturn(CompletableFuture.completedFuture(http)).when(cliente).sendAsync(org.mockito.ArgumentMatchers.any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        var t=new TransporteHttpOpenAi(cliente);var p=new ProveedorEdicionOpenAi(c,()->"CLAVE_TEST_NO_REAL",t);
        var observadas=new ArrayList<com.controllocal.service.presupuesto.ObservacionHttpIa>();
        var fallo=assertThrows(ProcesamientoIncierto.class,()->p.ejecutar(peticion(salida.length),observadas::add));
        assertFalse(fallo.getMessage().contains("PRIVADO"));assertEquals(1,observadas.size());assertEquals(503,observadas.getFirst().estadoHttp());
        assertEquals(com.controllocal.service.presupuesto.ObservacionHttpIa.Resultado.RESPUESTA_COMPLETA,observadas.getFirst().resultado());
        org.mockito.Mockito.verify(cliente,org.mockito.Mockito.times(1)).sendAsync(org.mockito.ArgumentMatchers.any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }
    byte[] cuerpo(byte[] foto,Map<String,Object> uso)throws Exception{
        var m=new LinkedHashMap<String,Object>();m.put("data",List.of(Map.of("b64_json",Base64.getEncoder().encodeToString(foto))));
        if(uso!=null)m.put("usage",uso);m.put("size","1024x1024");m.put("output_format","png");return json.writeValueAsBytes(m);
    }
    static TransporteOpenAi.Respuesta respuesta(int codigo,byte[] cuerpo,String requestId){
        var h=new HashMap<String,List<String>>();h.put("Content-Type",List.of("application/json"));if(requestId!=null)h.put("x-request-id",List.of(requestId));
        return new TransporteOpenAi.Respuesta(codigo,HttpHeaders.of(h,(a,b)->true),cuerpo);
    }
    @Test void exactoMinimizaYConservaOriginalSinPublicarMetadataPrivada()throws Exception{
        byte[] original=fuente.clone();var p=peticion(salida.length);var r=proveedor.ejecutar(p,observacionHttpTest->{});assertArrayEquals(salida,r.foto());
        assertEquals(1,llamadas.get());assertEquals(ProveedorEdicionOpenAi.DESTINO,enviada.uri());assertEquals("POST",enviada.method());
        assertEquals("Bearer CLAVE_TEST_NO_REAL",enviada.headers().firstValue("Authorization").orElseThrow());
        assertEquals(p.claveExterna(),enviada.headers().firstValue("X-Client-Request-Id").orElseThrow());
        var body=body(enviada.bodyPublisher().orElseThrow());var texto=new String(body,StandardCharsets.UTF_8);
        assertTrue(texto.contains("gpt-image-2.5-sunburst"));assertTrue(texto.contains(p.instruccion()));assertTrue(texto.contains("1024x1024"));
        for(var prohibido:List.of("NO_ENVIAR","contacto_privado","no-se-debe-abrir.invalid","limiteRecepcion","FUENTE_PRIVADA_TEST","VERSION_TEST","c".repeat(64),"CLAVE_TEST_NO_REAL"))
            assertFalse(texto.contains(prohibido),prohibido);
        assertArrayEquals(original,fuente);assertArrayEquals(original,p.fotoAutorizada());
        var medicion=r.medicion().mediciones().getFirst();assertNull(medicion.costoConfirmadoUsd());
        assertEquals("TARIFA_TEST",medicion.versionTarifa());assertEquals("OPENAI_REQUEST:req_TEST_UNICO",medicion.referenciaFuente());
        assertEquals(0,new BigDecimal("170").compareTo(UnidadesDeConsumo.costoSinCuantizar(medicion.unidades(),medicion.tarifasReales())));
        assertFalse(json.writeValueAsString(p).contains("limiteRecepcion"));assertFalse(json.writeValueAsString(r).contains("CLAVE_TEST_NO_REAL"));
    }
    @Test void byteAdicionalDeBinarioConservaMedicionSinImagenNiReintento(){
        var r=proveedor.ejecutar(peticion(salida.length-1),observacionHttpTest->{});assertNull(r.foto());assertNotNull(r.medicion());assertNull(r.medicion().mediciones().getFirst().costoConfirmadoUsd());assertEquals(1,llamadas.get());
    }
    @Test void fotoInvalidaConUsagePosteriorConservaLasUnidades()throws Exception{
        respuesta=respuesta(200,cuerpo(new byte[]{1,2,3},uso()),"req_MALFORMADA");
        var r=proveedor.ejecutar(peticion(1000),observacionHttpTest->{});assertNull(r.foto());assertNotNull(r.medicion());assertEquals(1,llamadas.get());
    }
    @Test void dimensionesDeSalidaDistintasConservanMedicionSinRecortar()throws Exception{
        respuesta=respuesta(200,cuerpo(png(32,32),uso()),"req_DIMENSION");var r=proveedor.ejecutar(peticion(10000),observacionHttpTest->{});assertNull(r.foto());assertNotNull(r.medicion());
    }
    @Test void fuenteMinimizadaPorCoreSeTransfiereSinOtraTransformacion()throws Exception{
        var evaluada=new EvaluacionTecnicaFoto().evaluar(fuente);fuente=evaluada.contenido();var p=peticion(10000);proveedor.ejecutar(p,observacionHttpTest->{});
        var body=body(enviada.bodyPublisher().orElseThrow());assertTrue(contiene(body,fuente));assertArrayEquals(evaluada.contenido(),p.fotoAutorizada());
    }
    @Test void fuenteRectangularNoRecibeBloqueoUniversalDeRelacion()throws Exception{
        fuente=png(96,64);var r=proveedor.ejecutar(peticion(10000),observacionHttpTest->{});assertNotNull(r.foto());assertEquals(1,llamadas.get());
        // Transportar la size explícita no acredita que este encuadre supere QC.
    }
    @Test void mismaProporcionConResolucionDistintaAceptaSizeSolicitada()throws Exception{
        fuente=png(32,32);var r=proveedor.ejecutar(peticion(10000),observacionHttpTest->{});assertArrayEquals(salida,r.foto());assertEquals(1,llamadas.get());
    }
    @Test void copia1600Por1067NoSeBloqueaPorIgualdadExactaNiSeTransformaLocalmente()throws Exception{
        fuente=png(1600,1067);var original=fuente.clone();var r=proveedor.ejecutar(peticion(10000),observacionHttpTest->{});
        assertNotNull(r.medicion());assertEquals(1,llamadas.get());assertArrayEquals(original,fuente);
        assertTrue(contiene(body(enviada.bodyPublisher().orElseThrow()),original));
    }
    @ParameterizedTest @ValueSource(strings={"AUSENTE","NULL_CAMPO","INCOHERENTE","NEGATIVO","FRACCION","OTRA_CATEGORIA"})
    void usageNoAcreditadoNoSeConvierteEnCero(String caso)throws Exception{
        var uso=uso();switch(caso){case "AUSENTE"->uso=null;case "NULL_CAMPO"->uso.put("output_tokens",null);
            case "INCOHERENTE"->uso.put("total_tokens",71);case "NEGATIVO"->uso.put("output_tokens",-1);
            case "FRACCION"->uso.put("output_tokens",new BigDecimal("0.1"));case "OTRA_CATEGORIA"->uso.put("cached_tokens",5);}
        respuesta=respuesta(200,cuerpo(salida,uso),"req_USO");assertThrows(ProcesamientoIncierto.class,()->proveedor.ejecutar(peticion(10000),observacionHttpTest->{}));assertEquals(1,llamadas.get());
    }
    @Test void cabeceraDeCorrelacionNoEsFacturaNiCostoConfirmado(){
        var r=proveedor.ejecutar(peticion(10000),observacionHttpTest->{});assertNull(r.medicion().mediciones().getFirst().costoConfirmadoUsd());
    }
    @Test void releerLaMismaEvidenciaMantieneLaIdentidadDeMedicionSinOtraLlamada()throws Exception{
        var d=new DecodificadorEdicionOpenAi(c);var p=peticion(10000);var a=d.leer(p,respuesta);var b=d.leer(p,respuesta);
        assertEquals(a.medicion(),b.medicion());assertEquals(0,llamadas.get());
    }
    @Test void sinRequestIdNoInventaReferenciaConClaveCliente(){
        respuesta=respuesta(200,respuesta.cuerpo(),null);assertThrows(ProcesamientoIncierto.class,()->proveedor.ejecutar(peticion(10000),observacionHttpTest->{}));assertEquals(1,llamadas.get());
    }
    @ParameterizedTest @ValueSource(ints={302,400,429,500})
    void errorHttpNoDemuestraNoProcesamientoYSinUsageQuedaIncierto(int estado){
        respuesta=respuesta(estado,"{\"error\":{\"message\":\"SECRETO_TEST_NO_PROPAGAR\"}}".getBytes(StandardCharsets.UTF_8),"req_ERROR");
        var e=assertThrows(ProcesamientoIncierto.class,()->proveedor.ejecutar(peticion(10000),observacionHttpTest->{}));assertNull(e.getCause());assertFalse(e.getMessage().contains("SECRETO_TEST"));assertEquals(1,llamadas.get());
    }
    @Test void httpConUsageCoherenteConservaMedicionPeroNoEntregaFoto(){
        respuesta=respuesta(500,respuesta.cuerpo(),"req_HTTP_CON_USO");var r=proveedor.ejecutar(peticion(10000),observacionHttpTest->{});assertNull(r.foto());assertNotNull(r.medicion());assertEquals(1,llamadas.get());
    }
    @Test void urlDeRespuestaNoSeSigueNiReintenta()throws Exception{
        var m=new LinkedHashMap<String,Object>();m.put("data",List.of(Map.of("url","https://otra-red.invalid/privado")));m.put("usage",uso());
        respuesta=respuesta(200,json.writeValueAsBytes(m),"req_URL");var r=proveedor.ejecutar(peticion(10000),observacionHttpTest->{});assertNull(r.foto());assertNotNull(r.medicion());assertEquals(1,llamadas.get());
    }
    @Test void cuerpoNoAdmiteSegundaSuscripcion()throws Exception{
        proveedor.ejecutar(peticion(10000),observacionHttpTest->{});var body=enviada.bodyPublisher().orElseThrow();assertTrue(body(body).length>0);
        assertThrows(ExecutionException.class,()->body(body));assertEquals(1,llamadas.get());
    }
    @Test void timeoutNoDaEvidenciaDeNoProcesamientoNiReintenta(){
        proveedor=new ProveedorEdicionOpenAi(c,()->"CLAVE_TEST",(p,max,total,contextoHttpTest,observadorHttpTest)->{llamadas.incrementAndGet();throw new TimeoutException("NO_EXPONER");});
        assertThrows(ProcesamientoIncierto.class,()->proveedor.ejecutar(peticion(10000),observacionHttpTest->{}));assertEquals(1,llamadas.get());
    }
    @Test void versionDePrevisionAjenaNoInvoca(){
        var p=peticion(10000);var l=p.limiteRecepcion();var otra=new Peticion(p.claveExterna(),p.accion(),p.datosPermitidos(),p.instruccion(),p.fotoAutorizada(),
                new UsoAlmacenamientoB0.LimiteRecepcion(l.reserva(),l.maximoBytesContenido(),"OTRA_VERSION",l.huellaFuente(),l.namespaceHash()));
        assertThrows(NoIniciadoComprobado.class,()->proveedor.ejecutar(otra,observacionHttpTest->{}));assertEquals(0,llamadas.get());
    }
    @Test void metadataDelAdaptadorNuncaIncluyeCredencial(){assertFalse(proveedor.toString().contains("CLAVE_TEST"));assertFalse(c.toString().contains("TARIFA_TEST"));assertEquals(0,llamadas.get());}
    static byte[] png(int ancho,int alto)throws IOException{
        var imagen=new BufferedImage(ancho,alto,BufferedImage.TYPE_INT_RGB);var out=new ByteArrayOutputStream();assertTrue(ImageIO.write(imagen,"png",out));return out.toByteArray();
    }
    static byte[] body(HttpRequest.BodyPublisher publisher)throws Exception{
        var f=new CompletableFuture<byte[]>();var out=new ByteArrayOutputStream();publisher.subscribe(new Subscriber<ByteBuffer>(){
            public void onSubscribe(Subscription s){s.request(Long.MAX_VALUE);}public void onNext(ByteBuffer b){byte[] x=new byte[b.remaining()];b.get(x);out.writeBytes(x);}
            public void onError(Throwable t){f.completeExceptionally(t);}public void onComplete(){f.complete(out.toByteArray());}});return f.get(2,TimeUnit.SECONDS);
    }
    static boolean contiene(byte[] cuerpo,byte[] foto){outer:for(int i=0;i<=cuerpo.length-foto.length;i++){for(int j=0;j<foto.length;j++)if(cuerpo[i+j]!=foto[j])continue outer;return true;}return false;}
}
