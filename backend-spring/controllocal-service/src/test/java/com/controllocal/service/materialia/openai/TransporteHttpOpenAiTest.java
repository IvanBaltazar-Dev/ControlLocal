package com.controllocal.service.materialia.openai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.controllocal.service.presupuesto.ObservacionHttpIa;
import com.controllocal.service.presupuesto.ObservacionHttpIa.Resultado;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HttpClient simulado; alimenta el BodySubscriber real sin abrir un socket. No ejecutada. */
class TransporteHttpOpenAiTest {
    final List<com.controllocal.service.presupuesto.ObservacionHttpIa> observaciones=new ArrayList<>();
    static TransporteOpenAi.ContextoIntento contexto(){return new TransporteOpenAi.ContextoIntento("a".repeat(64),"PROVEEDOR_TEST","MODELO_TEST","CONFIG_TEST");}
    final HttpClient cliente=mock(HttpClient.class);final AtomicBoolean cancelada=new AtomicBoolean();final AtomicInteger envios=new AtomicInteger();
    HttpRequest solicitud(){return HttpRequest.newBuilder(ProveedorEdicionOpenAi.DESTINO).POST(HttpRequest.BodyPublishers.ofString("TEST")).build();}
    @SuppressWarnings("unchecked")
    void servidor(int estado,Map<String,List<String>> cabeceras,List<byte[]> trozos){
        when(cliente.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        doAnswer(i->{
            envios.incrementAndGet();HttpResponse.BodyHandler<byte[]> handler=i.getArgument(1);
            var h=HttpHeaders.of(cabeceras,(a,b)->true);var sub=handler.apply(new HttpResponse.ResponseInfo(){
                public int statusCode(){return estado;}public HttpHeaders headers(){return h;}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}});
            var demanda=new AtomicLong();sub.onSubscribe(new Subscription(){public void request(long n){demanda.addAndGet(n);}public void cancel(){cancelada.set(true);}});
            for(var t:trozos){if(cancelada.get())break;assertTrue(demanda.getAndDecrement()>0);sub.onNext(List.of(ByteBuffer.wrap(t)));}
            if(!cancelada.get())sub.onComplete();
            return sub.getBody().toCompletableFuture().thenApply(bytes->{
                HttpResponse<byte[]> r=mock(HttpResponse.class);when(r.statusCode()).thenReturn(estado);when(r.headers()).thenReturn(h);when(r.body()).thenReturn(bytes);return r;});
        }).when(cliente).sendAsync(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }
    @ParameterizedTest @ValueSource(strings={"AUSENTE","MENOR","MAYOR"})
    void cotaSeCuentaPorChunksAunqueContentLengthFalteOMienta(String caso)throws Exception{
        var h=new HashMap<String,List<String>>();if(!caso.equals("AUSENTE"))h.put("Content-Length",List.of(caso.equals("MENOR")?"1":Long.toString(Long.MAX_VALUE)));
        servidor(200,h,List.of("01234".getBytes(StandardCharsets.UTF_8),"56789".getBytes(StandardCharsets.UTF_8)));
        var t=new TransporteHttpOpenAi(cliente);var r=t.enviar(solicitud(),10,Duration.ofSeconds(2),contexto(),observaciones::add);
        assertEquals("0123456789",new String(r.cuerpo(),StandardCharsets.UTF_8));assertFalse(cancelada.get());assertEquals(1,envios.get());
    }
    @Test void byteDeEnvolturaAdicionalCancelaAntesDeAcumularlo(){
        servidor(200,Map.of("Content-Length",List.of("1")),List.of(new byte[10],new byte[]{1}));
        var t=new TransporteHttpOpenAi(cliente);var e=assertThrows(IOException.class,()->t.enviar(solicitud(),10,Duration.ofSeconds(2),contexto(),observaciones::add));
        assertTrue(cancelada.get());assertEquals(1,envios.get());assertNull(e.getCause());
        assertEquals(Resultado.ERROR_IO,observaciones.getFirst().resultado());assertEquals(200,observaciones.getFirst().estadoHttp());
    }
    @Test void chunkUnicoExcesivoTambienCancela(){
        servidor(200,Map.of(),List.of(new byte[11]));var t=new TransporteHttpOpenAi(cliente);
        assertThrows(IOException.class,()->t.enviar(solicitud(),10,Duration.ofSeconds(2),contexto(),observaciones::add));assertTrue(cancelada.get());assertEquals(1,envios.get());
    }
    @Test void redireccionNoProduceOtraLlamada(){
        servidor(302,Map.of("Location",List.of("https://destino-ajeno.invalid/secreto")),List.of(new byte[0]));
        var t=new TransporteHttpOpenAi(cliente);assertDoesNotThrow(()->assertEquals(302,t.enviar(solicitud(),100,Duration.ofSeconds(2),contexto(),observaciones::add).estado()));assertEquals(1,envios.get());
    }
    @Test void clienteQueSigueRedireccionesNoSePuedeInyectar(){when(cliente.followRedirects()).thenReturn(HttpClient.Redirect.ALWAYS);assertThrows(IllegalArgumentException.class,()->new TransporteHttpOpenAi(cliente));verify(cliente,never()).sendAsync(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());}
    @Test void timeoutHastaFinDeCuerpoCancelaElExchangeSinRepetir(){
        when(cliente.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);var pendiente=new CompletableFuture<HttpResponse<byte[]>>();
        doAnswer(i->{envios.incrementAndGet();return pendiente;}).when(cliente).sendAsync(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        var t=new TransporteHttpOpenAi(cliente);assertThrows(TimeoutException.class,()->t.enviar(solicitud(),100,Duration.ofMillis(20),contexto(),observaciones::add));
        assertTrue(pendiente.isCancelled());assertEquals(1,envios.get());
        assertEquals(Resultado.TIMEOUT,observaciones.getFirst().resultado());assertNull(observaciones.getFirst().estadoHttp());
        assertTrue(observaciones.getFirst().envioIntentado());
    }
    @Test void respuestasUsaMismaRecepcionAcotadaConDestinoFijo()throws Exception{
        servidor(200,Map.of("Content-Length",List.of("1")),List.of(new byte[5],new byte[5]));
        var t=new TransporteHttpOpenAi(cliente,TransporteHttpOpenAi.Destino.RESPUESTAS);
        var r=HttpRequest.newBuilder(ProveedorRespuestasOpenAi.DESTINO).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(10,t.enviar(r,10,Duration.ofSeconds(2),contexto(),observaciones::add).cuerpo().length);assertEquals(1,envios.get());
    }
    @Test void instanciaRespuestasNoAdmiteEdicionYMasUnoCancela()throws Exception{
        servidor(200,Map.of(),List.of(new byte[10],new byte[]{1}));
        var t=new TransporteHttpOpenAi(cliente,TransporteHttpOpenAi.Destino.RESPUESTAS);
        assertThrows(IOException.class,()->t.enviar(solicitud(),10,Duration.ofSeconds(2),contexto(),observaciones::add));assertEquals(0,envios.get());
        var r=HttpRequest.newBuilder(ProveedorRespuestasOpenAi.DESTINO).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThrows(IOException.class,()->t.enviar(r,10,Duration.ofSeconds(2),contexto(),observaciones::add));assertEquals(1,envios.get());assertTrue(cancelada.get());
    }
    @Test void uriAjenaSeRechazaAntesDeHttp(){
        when(cliente.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);var t=new TransporteHttpOpenAi(cliente);
        var ajena=HttpRequest.newBuilder(URI.create("https://otro.invalid/v1/images/edits")).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThrows(IOException.class,()->t.enviar(ajena,100,Duration.ofSeconds(2),contexto(),observaciones::add));verify(cliente,never()).sendAsync(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        assertFalse(observaciones.getFirst().envioIntentado());assertNull(observaciones.getFirst().estadoHttp());
    }
    @Test void relojUtcYDuracionMonotonicaConHttpNoExitosoNoDeclaranFactura()throws Exception{
        servidor(429,Map.of("Authorization",List.of("SECRETO_NO_OBSERVAR")),List.of(new byte[]{1,2}));
        Instant fecha=Instant.parse("2026-11-04T12:00:00Z");var nanos=new AtomicLong(100);
        var t=new TransporteHttpOpenAi(cliente,TransporteHttpOpenAi.Destino.EDICION,Clock.fixed(fecha,ZoneOffset.UTC),()->nanos.getAndAdd(25));
        assertEquals(429,t.enviar(solicitud(),10,Duration.ofSeconds(2),contexto(),observaciones::add).estado());
        assertEquals(1,observaciones.size());var o=observaciones.getFirst();assertEquals(fecha,o.iniciadaEn());assertEquals(25,o.duracionNanos());
        assertEquals(Resultado.RESPUESTA_COMPLETA,o.resultado());assertEquals(429,o.estadoHttp());assertTrue(o.envioIntentado());
        assertEquals(contexto().claveIntento(),o.claveIntento());assertEquals("MODELO_TEST",o.modelo());assertEquals("CONFIG_TEST",o.versionConfiguracion());
        String dato=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(o);
        assertFalse(dato.contains("SECRETO"));assertFalse(dato.contains("Authorization"));assertFalse(dato.contains("cuerpo"));assertEquals(1,envios.get());
    }
    @Test void timeoutDespuesDeCabecerasConservaSuEstadoSinInventarCuerpo(){
        when(cliente.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);var pendiente=new CompletableFuture<HttpResponse<byte[]>>();
        doAnswer(i->{envios.incrementAndGet();HttpResponse.BodyHandler<byte[]> h=i.getArgument(1);
            h.apply(new HttpResponse.ResponseInfo(){public int statusCode(){return 202;}public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}});
            return pendiente;}).when(cliente).sendAsync(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        var t=new TransporteHttpOpenAi(cliente);assertThrows(TimeoutException.class,()->t.enviar(solicitud(),10,Duration.ofMillis(20),contexto(),observaciones::add));
        assertEquals(1,observaciones.size());assertEquals(202,observaciones.getFirst().estadoHttp());assertEquals(Resultado.TIMEOUT,observaciones.getFirst().resultado());assertEquals(1,envios.get());
    }
    @Test void errorIoYTimeoutDelClienteNoExponenCausaNiRepiten(){
        when(cliente.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        doReturn(CompletableFuture.failedFuture(new HttpTimeoutException("PRIVADO_NO_OBSERVAR")))
                .when(cliente).sendAsync(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        var t=new TransporteHttpOpenAi(cliente);var e=assertThrows(IOException.class,()->t.enviar(solicitud(),10,Duration.ofSeconds(2),contexto(),observaciones::add));
        assertNull(e.getCause());assertFalse(e.getMessage().contains("PRIVADO"));assertEquals(Resultado.TIMEOUT,observaciones.getFirst().resultado());
        verify(cliente,times(1)).sendAsync(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }
    @Test void interrupcionCancelaUnSoloEnvioYConservaObservacion(){
        when(cliente.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);var pendiente=new CompletableFuture<HttpResponse<byte[]>>();
        doReturn(pendiente).when(cliente).sendAsync(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        var t=new TransporteHttpOpenAi(cliente);boolean anterior=Thread.interrupted();
        try{Thread.currentThread().interrupt();assertThrows(InterruptedException.class,()->t.enviar(solicitud(),10,Duration.ofSeconds(2),contexto(),observaciones::add));
            assertEquals(Resultado.INTERRUMPIDA,observaciones.getFirst().resultado());assertTrue(pendiente.isCancelled());
            verify(cliente,times(1)).sendAsync(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        }finally{Thread.interrupted();if(anterior)Thread.currentThread().interrupt();}
    }
    @Test void falloSincronoEsDesconocidoYUnObservadorDefectuosoNoOcultaRespuesta()throws Exception{
        when(cliente.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
        doThrow(new IllegalStateException("PRIVADO_NO_OBSERVAR")).when(cliente).sendAsync(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        var t=new TransporteHttpOpenAi(cliente);assertThrows(IllegalStateException.class,()->t.enviar(solicitud(),10,Duration.ofSeconds(2),contexto(),observaciones::add));
        assertEquals(Resultado.DESCONOCIDO,observaciones.getFirst().resultado());assertTrue(observaciones.getFirst().envioIntentado());
        servidor(200,Map.of(),List.of(new byte[]{7}));
        assertArrayEquals(new byte[]{7},t.enviar(solicitud(),10,Duration.ofSeconds(2),contexto(),o->{throw new IllegalStateException("ERROR_OBSERVADOR_TEST");}).cuerpo());
    }
}
