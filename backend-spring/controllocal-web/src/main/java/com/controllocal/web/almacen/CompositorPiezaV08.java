package com.controllocal.web.almacen;

import com.controllocal.service.documentos.CompositorPieza;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import jakarta.annotation.PreDestroy;
import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Adaptador local Chrome/CDP del renderer aprobado. No Node/servidor ni reglas comerciales paralelas. */
@Component
public class CompositorPiezaV08 implements CompositorPieza {
    private final String ejecutable;private final ObjectMapper json;
    // Dos trabajos en el entorno EV-001 (2 vCPU); cada uno conserva su propio contexto/página.
    private final Semaphore cupos=new Semaphore(2,true);
    private final ReentrantReadWriteLock ciclo=new ReentrantReadWriteLock(true);
    private boolean cerrado;
    private HttpClient cliente;
    private Path temporal,activos,perfil;private Process proceso;private Cdp cdp;
    private final Map<Path,List<ProcessHandle>> retirosPendientes=new LinkedHashMap<>();
    private FalloRetiro ultimoFalloRetiro;
    public CompositorPiezaV08(@Value("${controllocal.piezas.chrome:}")String ejecutable,ObjectMapper json){
        this.ejecutable=ejecutable;this.json=json;
    }
    @Override public boolean configurado(){return ejecutable!=null&&!ejecutable.isBlank();}
    /** Cada trabajo obtiene contexto de navegador propio; no comparte páginas o datos entre tenants. */
    @Override public Binario componer(Map<String,Object> proyeccion,Map<String,Object> opciones,String formato){
        if(!Set.of("PDF_A4","PNG_VERTICAL").contains(formato))throw new ReglaNegocioException("Formato de pieza no admitido.");
        exigirActivosInline(proyeccion);boolean cupo=false,lectura=false;
        try(var total=new Paso("TOTAL","")){
            try(var espera=new Paso("ESPERA_CUPO","")){cupos.acquire();cupo=true;espera.correcta=true;}
            try(var ciclo=new Paso("CICLO","")){adquirirLectura();lectura=true;ciclo.correcta=true;}
            try(var activo=new Paso("ACTIVO","")){
                var binario=componerActivo(proyeccion,opciones,formato);activo.correcta=true;total.correcta=true;
                return binario;
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Se interrumpió la composición local de la pieza.",e);}
        finally{if(lectura)ciclo.readLock().unlock();if(cupo)cupos.release();}
    }
    /** Retorna con READ retenido; la inicialización nunca asciende un lector a escritor. */
    private void adquirirLectura()throws InterruptedException{
        while(true){
            ciclo.readLock().lockInterruptibly();
            if(cerrado){ciclo.readLock().unlock();throw new IllegalStateException("El compositor local ya está cerrado.");}
            if(disponible())return;
            ciclo.readLock().unlock();
            ciclo.writeLock().lockInterruptibly();
            try{
                if(cerrado)throw new IllegalStateException("El compositor local ya está cerrado.");
                iniciar();
            }finally{ciclo.writeLock().unlock();}
        }
    }
    private boolean disponible(){return cdp!=null&&cdp.disponible()&&proceso!=null&&proceso.isAlive();}
    /** El lease de ciclo mantiene vivos CDP/activos durante trabajo y limpieza del contexto. */
    private Binario componerActivo(Map<String,Object> proyeccion,Map<String,Object> opciones,String formato){
        String contexto=null,target=null;
        try{
            contexto=cdp.llamar("Target.createBrowserContext",Map.of("disposeOnDetach",true),null).path("browserContextId").asText();
            target=cdp.llamar("Target.createTarget",Map.of("url","about:blank","browserContextId",contexto),null).path("targetId").asText();
            String sesion=cdp.llamar("Target.attachToTarget",Map.of("targetId",target,"flatten",true),null).path("sessionId").asText();
            cdp.configurarSesion(sesion);
            var navegacion=cdp.llamar("Page.navigate",Map.of("url",activos.resolve("ficha.html").toUri().toString()),sesion);
            if(navegacion.has("errorText"))throw fallo();
            String frame=navegacion.path("frameId").asText();if(frame.isBlank())throw fallo();
            String jsonProyeccion=json.writeValueAsString(proyeccion);
            // El sondeo de readiness devuelve también la validación síncrona del mismo kit.
            // Estado y postMessage conservan literales independientes: no se presupone pureza.
            var validacion=esperar(sesion,"(function(){if(typeof window.BROX_FICHA_V08 !== 'object')return {lista:false};"+
                    "return {lista:true,validacion:window.BROX_VALIDAR_FICHA.validar("+jsonProyeccion+")};})()").path("validacion");
            if(validacion.path("errores").isArray()&&validacion.path("errores").size()>0)
                throw new ReglaNegocioException("La proyección no cumple el contrato V08; revisa los campos comerciales seleccionados.");
            String salida=String.valueOf(((Map<?,?>)proyeccion.get("pieza")).get("salida"));
            if("DIFUSION_AUTORIZADA".equals(salida)){
                var estado=evaluar(sesion,"window.BROX_VALIDAR_FICHA.estadoPieza("+jsonProyeccion+")",true);
                if(!"LISTA_PARA_DIFUSION".equals(estado.path("estadoPieza").asText()))
                    throw new ReglaNegocioException("La pieza conserva faltantes o incertidumbre material; revísala antes de difundir.");
            }
            evaluar(sesion,"window.__broxError=null;window.addEventListener('message',function(e){if(e.data&&e.data.tipo==='brox-ficha-error')window.__broxError=true;});"+
                    "window.postMessage("+json.writeValueAsString(Map.of("tipo","brox-ficha","version","V08","datos",new com.fasterxml.jackson.databind.util.RawValue(jsonProyeccion),"opciones",opciones))+",'*');true",false);
            boolean pdf="PDF_A4".equals(formato);
            // Una única observación de lista/error y exportación evita dos rondas posteriores.
            var vista=esperar(sesion,"(function(){if(window.__broxError===true)return {lista:true,error:true};"+
                    "if(!(window.BROX_FICHA_ESTADO&&window.BROX_FICHA_ESTADO.lista))return {lista:false};"+
                    "return {lista:true,error:false,html:window.BROX_FICHA_V08."+(pdf?"exportarA4":"exportarVertical")+
                    "("+json.writeValueAsString(activos.toUri().toString())+")};})()");
            if(vista.path("error").asBoolean())throw fallo();
            cdp.llamar("Page.setDocumentContent",Map.of("frameId",frame,"html",vista.path("html").asText()),sesion);
            // Bounds se leen después de fuentes/fotos en la misma promesa; PDF conserva su espera.
            var recursos=evaluar(sesion,"Promise.all([document.fonts.ready,...Array.from(document.images).map(i=>i.complete?Promise.resolve():new Promise((r,j)=>{i.onload=r;i.onerror=j;}))]).then(()=>"+
                    (pdf?"true":"(function(){var r=document.querySelector('.vertical').getBoundingClientRect();return {x:r.x,y:r.y,ancho:r.width,alto:r.height};})()")+")",true);
            if(pdf){
                byte[] bytes=Base64.getDecoder().decode(cdp.llamar("Page.printToPDF",Map.of("preferCSSPageSize",true,"printBackground",true,"generateTaggedPDF",true),sesion).path("data").asText());
                if(bytes.length<5||!new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))throw fallo();
                return new Binario(bytes,"application/pdf","pdf");
            }
            var caja=recursos;
            int alto=(int)Math.ceil(caja.path("alto").asDouble());
            if(alto<1)throw fallo();
            var clip=Map.of("x",caja.path("x").asDouble(),"y",caja.path("y").asDouble(),"width",caja.path("ancho").asDouble(),"height",alto,"scale",1);
            byte[] bytes=Base64.getDecoder().decode(cdp.llamar("Page.captureScreenshot",Map.of("format","png","captureBeyondViewport",true,"clip",clip),sesion).path("data").asText());
            if(bytes.length<8||bytes[0]!=(byte)137||bytes[1]!=80||bytes[2]!=78||bytes[3]!=71)throw fallo();
            return new Binario(bytes,"image/png","png");
        }catch(ReglaNegocioException e){throw e;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Se interrumpió el renderer V08.",e);}
        catch(Exception e){throw new IllegalStateException("No se pudo componer la pieza con el renderer V08.",e);}
        finally{
            // Una cancelación no debe saltarse la limpieza por el flag de interrupción del caller.
            boolean interrumpido=Thread.interrupted();
            try{if(contexto!=null)cdp.llamar("Target.disposeBrowserContext",Map.of("browserContextId",contexto),null);}
            catch(RuntimeException noRetirado){cdp.retirarTrasLectores();}
            finally{if(interrumpido)Thread.currentThread().interrupt();}
        }
    }
    private JsonNode evaluar(String sesion,String expresion,boolean promesa){
        var r=cdp.llamar("Runtime.evaluate",Map.of("expression",expresion,"returnByValue",true,"awaitPromise",promesa),sesion);
        if(r.has("exceptionDetails"))throw fallo();return r.path("result").path("value");
    }
    private JsonNode esperar(String sesion,String expresion)throws InterruptedException{
        long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<limite){
            var resultado=evaluar(sesion,expresion,false);
            if(resultado.path("lista").asBoolean())return resultado;
            Thread.sleep(15);
        }
        throw fallo();
    }
    private void iniciar(){
        if(disponible())return;
        if(!configurado())throw new CapacidadDeshabilitadaException();
        if(!Files.isRegularFile(Path.of(ejecutable)))throw new IllegalStateException("El ejecutable configurado del compositor no está disponible.");
        var presupuesto=new PresupuestoRetiro();reintentarRetiros(presupuesto);
        // No crear una sucesión de perfiles si una retirada anterior sigue pendiente.
        if(!retirosPendientes.isEmpty())throw new IllegalStateException("El compositor conserva un temporal propio pendiente de retirada.");
        if(proceso!=null||temporal!=null)cerrarEstado(presupuesto);
        try{
            if(cliente==null)cliente=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            temporal=Files.createTempDirectory("brox-c6-renderer-").toAbsolutePath().normalize();
            activos=Files.createDirectory(temporal.resolve("v08"));perfil=Files.createDirectory(temporal.resolve("perfil"));
            extraerActivos();
            proceso=new ProcessBuilder(ejecutable,"--headless=new","--remote-debugging-port=0","--remote-debugging-address=127.0.0.1",
                    "--user-data-dir="+perfil,"--no-first-run","--no-default-browser-check","--disable-background-networking",
                    "--disable-component-update","--disable-sync","--disable-extensions","--allow-file-access-from-files",
                    "--disable-breakpad","--disable-crash-reporter",
                    "--force-color-profile=srgb","--font-render-hinting=none","--disable-features=TranslateUI")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            Path puerto=perfil.resolve("DevToolsActivePort");long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            cdp=new Cdp(esperarDireccion(puerto,limite),json,cliente);
            reintentarRetiros(presupuesto);
        }catch(Exception e){cerrarEstado(presupuesto);if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("No se pudo iniciar el compositor local V08.",e);}
    }
    /** Existir no acredita un handshake legible/completo; cada intento comparte el plazo original. */
    private URI esperarDireccion(Path archivo,long limite)throws IOException,InterruptedException{
        IOException ultimo=new IOException("Todavía no se observó la dirección privada de depuración.");
        while(proceso.isAlive()&&System.nanoTime()<limite){
            try{
                List<String> lineas=Files.readAllLines(archivo);
                if(lineas.size()<2)throw new IOException("La dirección privada de depuración todavía está incompleta.");
                int numero;
                try{numero=Integer.parseInt(lineas.getFirst());}
                catch(NumberFormatException e){throw new IOException("El puerto privado de depuración todavía no es válido.",e);}
                String ruta=lineas.get(1);
                if(numero<1||numero>65535||!ruta.matches("/devtools/browser/[A-Za-z0-9-]+"))
                    throw new IOException("La dirección privada de depuración todavía no es válida.");
                if(proceso.isAlive()&&System.nanoTime()<limite)return URI.create("ws://127.0.0.1:"+numero+ruta);
                break;
            }catch(IOException e){ultimo=e;}
            if(proceso.isAlive()&&System.nanoTime()<limite)Thread.sleep(20);
        }
        throw new IOException("El proceso propio no confirmó una dirección privada válida dentro del arranque.",ultimo);
    }
    private void extraerActivos()throws IOException{
        List<Map<String,String>> manifest;
        try(var in=getClass().getResourceAsStream("/piezas/v08/manifest.json")){
            if(in==null)throw fallo();manifest=json.readValue(in,new TypeReference<List<Map<String,String>>>(){});
        }
        for(var entrada:manifest){
            Path destino=activos.resolve(entrada.get("ruta")).normalize();
            if(!destino.startsWith(activos))throw fallo();Files.createDirectories(destino.getParent());
            try(var in=getClass().getResourceAsStream("/piezas/v08/"+entrada.get("ruta"))){
                if(in==null)throw fallo();byte[] bytes=in.readAllBytes();
                if(!EvaluacionTecnicaFoto.huella(bytes).equals(entrada.get("sha256")))throw fallo();
                Files.write(destino,bytes,StandardOpenOption.CREATE_NEW);
            }
        }
    }
    /** El validador visual admite rutas relativas; Core entrega sólo copias PNG data: ya verificadas. */
    static void exigirActivosInline(Object objeto){
        if(objeto instanceof Map<?,?> mapa){
            for(var e:mapa.entrySet()){
                if("src".equals(e.getKey())&&(!(e.getValue() instanceof String s)||!s.startsWith("data:image/png;base64,")))
                    throw new ReglaNegocioException("La composición sólo admite fotografías de salida preparadas por Core.");
                exigirActivosInline(e.getValue());
            }
        }else if(objeto instanceof Iterable<?> lista)lista.forEach(CompositorPiezaV08::exigirActivosInline);
    }
    @PreDestroy public void cerrar(){
        ciclo.writeLock().lock();
        try{
            var presupuesto=new PresupuestoRetiro();
            if(!cerrado){cerrado=true;cerrarEstado(presupuesto);if(cliente!=null){cliente.close();cliente=null;}}
            reintentarRetiros(presupuesto);
        }
        finally{ciclo.writeLock().unlock();}
    }
    /** Sólo bajo WRITE, después de terminar los lectores; no cierra permanentemente el servicio al recuperar. */
    private void cerrarEstado(PresupuestoRetiro presupuesto){
        Set<ProcessHandle> propios=new LinkedHashSet<>();
        if(proceso!=null){propios.add(proceso.toHandle());proceso.descendants().forEach(propios::add);}
        if(cdp!=null){cdp.cerrar();cdp=null;}
        if(proceso!=null){try{if(!proceso.waitFor(3,TimeUnit.SECONDS)){
                proceso.descendants().forEach(propios::add);proceso.destroyForcibly();proceso.waitFor(3,TimeUnit.SECONDS);}}
            catch(InterruptedException e){proceso.destroyForcibly();Thread.currentThread().interrupt();}proceso=null;}
        propios.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        try{CompletableFuture.allOf(propios.stream().filter(ProcessHandle::isAlive)
                .map(ProcessHandle::onExit).toArray(CompletableFuture[]::new)).get(3,TimeUnit.SECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();}catch(ExecutionException|TimeoutException noTerminados){ }
        if(temporal!=null){
            retirosPendientes.put(temporal,List.copyOf(propios));
            temporal=null;
        }
        activos=null;perfil=null;reintentarRetiros(presupuesto);
    }
    /** Sólo WRITE y raíces creadas por esta instancia. No barrido, timer o retirada declarada sin ausencia. */
    private void reintentarRetiros(PresupuestoRetiro presupuesto){
        Path base=Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        boolean repetir;
        do{
            repetir=false;var pendientes=retirosPendientes.entrySet().iterator();
            while(pendientes.hasNext()){
                var retiro=pendientes.next();Path raiz=retiro.getKey(),actual=raiz;
                if(!raiz.startsWith(base)||!raiz.getFileName().toString().startsWith("brox-c6-renderer-"))
                    throw new IllegalStateException("El temporal no pertenece al compositor local.");
                if(retiro.getValue().stream().anyMatch(ProcessHandle::isAlive)){
                    ultimoFalloRetiro=new FalloRetiro(raiz,Path.of(""),new IOException("PROCESOS_PROPIOS_PENDIENTES"));continue;
                }
                try{
                    if(!ausente(raiz)){
                        List<Path> rutas;try(var arbol=Files.walk(raiz)){rutas=arbol.sorted(Comparator.reverseOrder()).toList();}
                        for(Path ruta:rutas){actual=ruta;Files.deleteIfExists(ruta);}
                    }
                    if(!ausente(raiz))throw new IOException("AUSENCIA_NO_VERIFICADA");
                    pendientes.remove();
                }catch(IOException e){ultimoFalloRetiro=new FalloRetiro(raiz,raiz.relativize(actual),e);presupuesto.falloElegible();repetir=true;}
                catch(UncheckedIOException e){ultimoFalloRetiro=new FalloRetiro(raiz,raiz.relativize(actual),e.getCause());presupuesto.falloElegible();repetir=true;}
            }
        }while(repetir&&presupuesto.pausar());
    }
    /** Una ventana técnica lazy por iniciar/cerrar; ninguna espera antes del primer fallo real. */
    private static final class PresupuestoRetiro{
        private boolean iniciado;private long limite;
        void falloElegible(){if(!iniciado){iniciado=true;limite=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(1000);}}
        boolean pausar(){
            if(!iniciado||Thread.currentThread().isInterrupted())return false;
            long restante=limite-System.nanoTime();if(restante<=0)return false;
            try{TimeUnit.NANOSECONDS.sleep(Math.min(restante,TimeUnit.MILLISECONDS.toNanos(20)));}
            catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
            return limite-System.nanoTime()>0;
        }
    }
    private static boolean ausente(Path raiz)throws IOException{
        try{Files.readAttributes(raiz,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);return false;}
        catch(NoSuchFileException noExiste){return true;}
    }
    private record FalloRetiro(Path temporal,Path relativo,IOException causa){}
    /** Diagnóstico privado para las comprobaciones del ciclo; nunca DTO/log con datos del perfil. */
    private String diagnosticoRetiro(){
        if(ultimoFalloRetiro==null)return "pendientes="+retirosPendientes.size()+";sin_fallo_capturado";
        var f=ultimoFalloRetiro;String motivo=f.causa() instanceof FileSystemException fs?fs.getReason():f.causa().getMessage();
        return "pendientes="+retirosPendientes.size()+";temporal="+f.temporal().getFileName()+";relativo="+f.relativo()
                +";tipo="+f.causa().getClass().getSimpleName()+";motivo="+motivo;
    }
    private static IllegalStateException fallo(){return new IllegalStateException("El renderer no produjo una pieza válida.");}

    /** Diagnóstico optativo: sólo fases/métodos técnicos; nunca datos, expresiones, IDs o binarios. */
    @Name("brox.C6Compositor") @Label("Compositor C6") @Category({"BROX","C6"})
    @Enabled(false) @StackTrace(false)
    private static final class Paso extends Event implements AutoCloseable {
        @Label("Fase") public String fase;
        @Label("Método CDP") public String metodo;
        @Label("Correcta") public boolean correcta;
        private Paso(String fase,String metodo){this.fase=fase;this.metodo=metodo;begin();}
        @Override public void close(){end();commit();}
    }

    private static final class Cdp implements WebSocket.Listener {
        private final ObjectMapper json;private final URI uri;private final HttpClient cliente;
        private final AtomicInteger secuencia=new AtomicInteger();
        private final Map<Integer,CompletableFuture<Respuesta>> pendientes=new ConcurrentHashMap<>();
        private final ReentrantLock envio=new ReentrantLock();
        private volatile boolean operativa=true;
        private volatile boolean reutilizable=true;
        private final StringBuilder fragmentos=new StringBuilder();private WebSocket socket;
        Cdp(URI uri,ObjectMapper json,HttpClient cliente)throws Exception{
            this.json=json;this.uri=uri;this.cliente=cliente;
            socket=cliente.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(uri,this).get(5,TimeUnit.SECONDS);
        }
        public void onOpen(WebSocket ws){ws.request(1);}
        public CompletionStage<?> onText(WebSocket ws,CharSequence texto,boolean ultimo){
            try{
                JsonNode respuesta=null;
                synchronized(fragmentos){
                    fragmentos.append(texto);
                    if(ultimo){try{respuesta=json.readTree(fragmentos.toString());}finally{fragmentos.setLength(0);}}
                }
                if(respuesta!=null&&respuesta.has("id")){
                    var f=pendientes.remove(respuesta.path("id").asInt());if(f!=null)f.complete(new Respuesta(respuesta,System.nanoTime()));
                }
            }catch(IOException e){inutilizar(e);}
            if(operativa)ws.request(1);return CompletableFuture.completedFuture(null);
        }
        public void onError(WebSocket ws,Throwable error){inutilizar(error);}
        public CompletionStage<?> onClose(WebSocket ws,int estado,String motivo){inutilizar(fallo());return CompletableFuture.completedFuture(null);}
        boolean disponible(){return reutilizable&&transporteVivo();}
        private boolean transporteVivo(){return operativa&&socket!=null&&!socket.isInputClosed()&&!socket.isOutputClosed();}
        void retirarTrasLectores(){reutilizable=false;}
        private void inutilizar(Throwable error){operativa=false;pendientes.values().forEach(f->f.completeExceptionally(error));}
        private record Comando(String metodo,Map<String,?> parametros) { }
        private record Respuesta(JsonNode contenido,long recibidaEn) { }
        private record Pendiente(int id,CompletableFuture<Respuesta> futuro,long limiteRespuesta) { }
        JsonNode llamar(String metodo,Map<String,?> parametros,String sesion){return ejecutar(List.of(new Comando(metodo,parametros)),sesion).getFirst();}
        void configurarSesion(String sesion){
            if(sesion==null||sesion.isBlank())throw fallo();
            // Page/Runtime.enable suscriben notificaciones que este adaptador no consume.
            ejecutar(List.of(new Comando("Network.enable",Map.of())),sesion);
            ejecutar(List.of(new Comando("Network.setBlockedURLs",Map.of("urls",List.of("http://*","https://*","ftp://*","ws://*","wss://*"))),
                    new Comando("Emulation.setDeviceMetricsOverride",Map.of("width",390,"height",900,"deviceScaleFactor",2,"mobile",false))),sesion);
        }
        private List<JsonNode> ejecutar(List<Comando> comandos,String sesion){
            var propios=new ArrayList<Pendiente>();
            try(var paso=new Paso("CDP",comandos.size()==1?comandos.getFirst().metodo():"CONFIGURAR_SESION")){
                for(var c:comandos)propios.add(solicitar(c.metodo(),c.parametros(),sesion,10,20));
                var resultados=new ArrayList<JsonNode>();for(var p:propios)resultados.add(confirmar(p));paso.correcta=true;return List.copyOf(resultados);
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Se interrumpió la comunicación local del compositor.",e);}
            catch(Exception e){throw new IllegalStateException("Falló la composición local de la pieza.",e);}
            finally{for(var p:propios)retirar(p.id(),p.futuro());}
        }
        private Pendiente solicitar(String metodo,Map<String,?> parametros,String sesion,int segundosEnvio,int segundosRespuesta)throws Exception{
            if(!transporteVivo())throw fallo();
            int id=secuencia.incrementAndGet();var f=new CompletableFuture<Respuesta>();pendientes.put(id,f);boolean entregado=false;
            try{
                var mensaje=new LinkedHashMap<String,Object>();mensaje.put("id",id);mensaje.put("method",metodo);mensaje.put("params",parametros);
                if(sesion!=null)mensaje.put("sessionId",sesion);
                long enviadoEn=enviar(json.writeValueAsString(mensaje),segundosEnvio);
                var p=new Pendiente(id,f,enviadoEn+TimeUnit.SECONDS.toNanos(segundosRespuesta));entregado=true;return p;
            }finally{if(!entregado)retirar(id,f);}
        }
        private JsonNode confirmar(Pendiente p)throws InterruptedException,ExecutionException,TimeoutException{
            long restante=p.limiteRespuesta()-System.nanoTime();
            var r=restante>0?p.futuro().get(restante,TimeUnit.NANOSECONDS):p.futuro().getNow(null);
            if(r==null||r.recibidaEn()>p.limiteRespuesta())throw new TimeoutException("La respuesta CDP superó su plazo.");
            if(r.contenido().has("error"))throw fallo();return r.contenido().path("result");
        }
        private void retirar(int id,CompletableFuture<Respuesta> futuro){pendientes.remove(id,futuro);futuro.cancel(false);}
        /** JDK21 permite un envío text/bin pendiente; la respuesta CDP se espera fuera de este lock. */
        private long enviar(String texto,int segundos)throws InterruptedException,ExecutionException,TimeoutException{
            envio.lockInterruptibly();
            try{
                if(!transporteVivo())throw fallo();long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(segundos);
                var fin=socket.sendText(texto,true).thenApply(ignorado->System.nanoTime());long restante=limite-System.nanoTime();
                Long enviado=restante>0?fin.get(restante,TimeUnit.NANOSECONDS):fin.getNow(null);
                if(enviado==null||enviado>limite)throw new TimeoutException("El envío CDP superó su plazo.");return enviado;
            }
            finally{envio.unlock();}
        }
        void cerrar(){
            if(socket!=null){Cdp cierre=this;Pendiente solicitud=null;
                try{
                    // WRITE ya retiró a los lectores. Un canal abortado no impide
                    // cerrar ordenadamente el mismo navegador privado aún vivo.
                    if(!transporteVivo())cierre=new Cdp(uri,json,cliente);
                    solicitud=cierre.solicitar("Browser.close",Map.of(),null,1,1);
                    cierre.confirmar(solicitud);
                }
                catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception ignorado){ }
                finally{
                    if(solicitud!=null)cierre.retirar(solicitud.id(),solicitud.futuro());
                    cierre.inutilizar(fallo());if(cierre.socket!=null)cierre.socket.abort();
                    if(cierre!=this){inutilizar(fallo());socket.abort();}
                }}
        }
    }
}
