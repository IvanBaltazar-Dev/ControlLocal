package com.controllocal.service.materialia.openai;

import java.io.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import com.controllocal.service.presupuesto.ObservacionHttpIa;
import com.controllocal.service.presupuesto.ObservacionHttpIa.Resultado;

/** JDK21. Nunca redirige, descarga URLs del resultado ni reintenta una petición. */
public final class TransporteHttpOpenAi implements TransporteOpenAi,AutoCloseable {
    /** Recepción/publicación únicas para dos destinos FIJOS de adaptador. */
    public enum Destino {
        EDICION(ProveedorEdicionOpenAi.DESTINO),RESPUESTAS(ProveedorRespuestasOpenAi.DESTINO);
        private final java.net.URI uri;
        Destino(java.net.URI uri){this.uri=uri;}
    }
    private final HttpClient cliente;
    private final java.net.URI destino;
    private final Clock reloj;private final LongSupplier monotono;
    public TransporteHttpOpenAi(Duration conexion){
        this(conexion,Destino.EDICION);
    }
    public TransporteHttpOpenAi(Duration conexion,Destino destino){
        this(crear(conexion),destino);
    }
    TransporteHttpOpenAi(HttpClient cliente){
        this(cliente,Destino.EDICION);
    }
    TransporteHttpOpenAi(HttpClient cliente,Destino destino){
        this(cliente,destino,Clock.systemUTC(),System::nanoTime);
    }
    TransporteHttpOpenAi(HttpClient cliente,Destino destino,Clock reloj,LongSupplier monotono){
        if(cliente.followRedirects()!=HttpClient.Redirect.NEVER)throw new IllegalArgumentException("No se admiten redirecciones.");
        this.cliente=cliente;this.destino=Objects.requireNonNull(destino).uri;
        this.reloj=Objects.requireNonNull(reloj);this.monotono=Objects.requireNonNull(monotono);
    }
    private static HttpClient crear(Duration conexion){
        ConfiguracionEdicionOpenAi.exigirDuracion(conexion);
        return HttpClient.newBuilder().connectTimeout(conexion).followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1).build();
    }
    @Override public Respuesta enviar(HttpRequest solicitud,int maximoEnvoltura,Duration total,
            ContextoIntento intento,Consumer<ObservacionHttpIa> observador)
            throws IOException,InterruptedException,TimeoutException {
        Objects.requireNonNull(intento);Objects.requireNonNull(observador);
        Instant iniciada=reloj.instant();long inicio=monotono.getAsLong();
        var estado=new AtomicReference<Integer>();Resultado resultado=Resultado.DESCONOCIDO;boolean envio=false;
        try{
            if(!destino.equals(solicitud.uri())||!"POST".equals(solicitud.method()))
                throw new IOException("Destino técnico no autorizado.");
            ConfiguracionEdicionOpenAi.exigirDuracion(total);
            var recibidor=new RecepcionAcotada(maximoEnvoltura);
            // Invocar sendAsync no prueba que el proveedor recibió, procesó o facturó.
            envio=true;
            CompletableFuture<HttpResponse<byte[]>> pendiente=cliente.sendAsync(solicitud,info->{estado.set(info.statusCode());return recibidor;});
            try{
                var r=pendiente.get(total.toMillis(),TimeUnit.MILLISECONDS);estado.set(r.statusCode());
                var respuesta=new Respuesta(r.statusCode(),r.headers(),r.body());resultado=Resultado.RESPUESTA_COMPLETA;return respuesta;
            }catch(ExecutionException fallo){
                resultado=fallo.getCause() instanceof HttpTimeoutException?Resultado.TIMEOUT:Resultado.ERROR_IO;
                throw new IOException("La recepción HTTP no quedó confirmada.");
            }catch(TimeoutException interrumpida){resultado=Resultado.TIMEOUT;recibidor.cancelar();pendiente.cancel(true);throw interrumpida;}
            catch(InterruptedException interrumpida){resultado=Resultado.INTERRUMPIDA;recibidor.cancelar();pendiente.cancel(true);throw interrumpida;}
        }catch(IOException fallo){if(resultado==Resultado.DESCONOCIDO)resultado=Resultado.ERROR_IO;throw fallo;}
        finally{
            // El observador sólo captura memoria. Su fallo nunca oculta una respuesta/usage.
            try{observador.accept(new ObservacionHttpIa(ObservacionHttpIa.ESQUEMA,intento.claveIntento(),intento.proveedor(),
                    intento.modelo(),intento.versionConfiguracion(),iniciada,monotono.getAsLong()-inicio,resultado,estado.get(),envio));}
            catch(RuntimeException noCapturada){org.slf4j.LoggerFactory.getLogger(TransporteHttpOpenAi.class)
                    .warn("La observación HTTP IA no pudo capturarse; su cobertura técnica permanece desconocida.");}
        }
    }
    @Override public void close(){cliente.close();}

    /** Cuenta cada ByteBuffer ANTES de copiar. Content-Length no interviene en la admisión. */
    static final class RecepcionAcotada implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximo;private final ByteArrayOutputStream bytes;
        private final CompletableFuture<byte[]> fin=new CompletableFuture<>();
        private Subscription suscripcion;
        RecepcionAcotada(int maximo){if(maximo<=0)throw new IllegalArgumentException("Cota de envoltura requerida.");this.maximo=maximo;bytes=new ByteArrayOutputStream(Math.min(maximo,8192));}
        @Override public CompletionStage<byte[]> getBody(){return fin;}
        @Override public synchronized void onSubscribe(Subscription s){
            if(suscripcion!=null||fin.isDone()){s.cancel();return;}suscripcion=s;s.request(1);
        }
        @Override public synchronized void onNext(List<ByteBuffer> lote){
            if(fin.isDone())return;
            try{
                for(var b:lote){
                    if(b.remaining()>maximo-bytes.size())throw new IOException("Envoltura excedida.");
                    byte[] trozo=new byte[Math.min(b.remaining(),8192)];
                    while(b.hasRemaining()){int n=Math.min(b.remaining(),trozo.length);b.get(trozo,0,n);bytes.write(trozo,0,n);}
                }
                suscripcion.request(1);
            }catch(IOException|RuntimeException exceso){cancelar();fin.completeExceptionally(new IOException("Recepción acotada incompleta."));}
        }
        @Override public synchronized void onError(Throwable error){fin.completeExceptionally(new IOException("Recepción HTTP incompleta."));}
        @Override public synchronized void onComplete(){fin.complete(bytes.toByteArray());}
        synchronized void cancelar(){if(suscripcion!=null)suscripcion.cancel();}
    }

    /** Un segundo intento interno del cliente no puede volver a publicar el cuerpo del POST. */
    static HttpRequest.BodyPublisher unaSuscripcion(HttpRequest.BodyPublisher original){
        var usada=new java.util.concurrent.atomic.AtomicBoolean();
        return new HttpRequest.BodyPublisher(){
            @Override public long contentLength(){return original.contentLength();}
            @Override public void subscribe(Subscriber<? super ByteBuffer> s){
                if(usada.compareAndSet(false,true)){original.subscribe(s);return;}
                s.onSubscribe(new Subscription(){public void request(long n){}public void cancel(){}});
                s.onError(new IOException("El cuerpo no admite otra publicación."));
            }
        };
    }
}
