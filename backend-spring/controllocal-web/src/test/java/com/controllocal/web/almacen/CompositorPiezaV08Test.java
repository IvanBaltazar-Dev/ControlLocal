package com.controllocal.web.almacen;

import com.controllocal.service.documentos.*;
import com.controllocal.service.excepcion.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.fasterxml.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

/** Composición real en Chrome local, sin proveedor IA ni recursos de red. */
class CompositorPiezaV08Test {
    private static String chrome(){return System.getProperty("test.piezas.chrome",Path.of(System.getProperty("user.home"),
            ".cache/puppeteer/chrome/win64-150.0.7871.24/chrome-win64/chrome.exe").toString());}
    @Test void producePdfRealV08ConMarcaDeRevisionPrivada()throws Exception{
        try(var c=new SesionCompositor()){
            var b=c.compositor.componer(datos(),Map.of("vista","a4","regional","es-PE"),"PDF_A4");
            conservar("vista-previa-v08.pdf",b.contenido());
            assertEquals("application/pdf",b.contentType());
            try(var pdf=Loader.loadPDF(b.contenido())){
                assertTrue(pdf.getNumberOfPages()>0);String texto=new PDFTextStripper().getText(pdf);
                // V08§7.7: el aviso vive fuera del cuerpo; el PDF conserva título de vista previa.
                assertTrue(pdf.getDocumentInformation().getTitle().startsWith("Vista previa · "));
                assertTrue(texto.contains("Miraflores"));
                assertNotNull(pdf.getDocumentCatalog().getMarkInfo());assertTrue(pdf.getDocumentCatalog().getMarkInfo().isMarked());
            }
        }
    }
    @Test void produceVerticalRealSinSubirNiPublicar()throws Exception{
        try(var c=new SesionCompositor()){
            var b=c.compositor.componer(datos(),Map.of("vista","vertical","regional","es-PE"),"PNG_VERTICAL");
            conservar("vista-previa-v08.png",b.contenido());
            var imagen=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(b.contenido()));
            assertEquals("image/png",b.contentType());assertNotNull(imagen);assertEquals(780,imagen.getWidth());
            // V08 fija ancho, permite variar altura para conservar el contenido; no exige900px mínimos.
            assertTrue(imagen.getHeight()>0);
            var colores=new HashSet<Integer>();for(int y=0;y<imagen.getHeight();y+=5)for(int x=0;x<imagen.getWidth();x+=5)colores.add(imagen.getRGB(x,y));
            assertTrue(colores.size()>2,"La imagen debe contener la ficha y no un lienzo vacío.");
        }
    }
    @Test void diagnosticoJfrEsOptativoYNoConservaDatosComerciales(@TempDir Path temporal)throws Exception{
        var d=datos();String marcador="DATO-COMERCIAL-QUE-NO-DEBE-ESTAR-EN-JFR";
        @SuppressWarnings("unchecked")var inmueble=(Map<String,Object>)d.get("inmueble");
        inmueble.put("ubicacion",Map.of("distrito",dato(marcador),"zonaUrbanizacion",Map.of("estado","FALTANTE")));
        try(var c=new SesionCompositor()){
            Path apagada=temporal.resolve("apagada.jfr");
            try(var r=new Recording()){
                r.start();assertTrue(c.compositor.componer(d,Map.of("vista","a4","regional","es-PE"),"PDF_A4").contenido().length>0);
                r.stop();r.dump(apagada);
            }
            assertTrue(RecordingFile.readAllEvents(apagada).stream().noneMatch(e->e.getEventType().getName().equals("brox.C6Compositor")));
            Path activa=temporal.resolve("activa.jfr");
            try(var r=new Recording()){
                r.enable("brox.C6Compositor").withoutThreshold().withoutStackTrace();r.start();
                assertTrue(c.compositor.componer(d,Map.of("vista","a4","regional","es-PE"),"PDF_A4").contenido().length>0);
                r.stop();r.dump(activa);
            }
            var eventos=RecordingFile.readAllEvents(activa).stream().filter(e->e.getEventType().getName().equals("brox.C6Compositor")).toList();
            assertFalse(eventos.isEmpty());
            for(String fase:List.of("TOTAL","ESPERA_CUPO","CICLO","ACTIVO"))
                assertEquals(1,eventos.stream().filter(e->e.getString("fase").equals(fase)).count(),fase);
            assertTrue(eventos.stream().filter(e->e.getString("fase").equals("CDP")).count()>10);
            Set<String> campos=Set.of("startTime","duration","eventThread","stackTrace","fase","metodo","correcta");
            for(var e:eventos){
                assertEquals(campos,e.getEventType().getFields().stream().map(f->f.getName()).collect(java.util.stream.Collectors.toSet()));
                assertTrue(e.getBoolean("correcta"));assertNull(e.getStackTrace());
                assertFalse(e.getDuration().isNegative());assertFalse(e.toString().contains(marcador));
                assertTrue(Set.of("TOTAL","ESPERA_CUPO","CICLO","ACTIVO","CDP").contains(e.getString("fase")));
                assertTrue(e.getString("metodo").isEmpty()||Set.of("Target.createBrowserContext","Target.createTarget","Target.attachToTarget",
                        "CONFIGURAR_SESION","Network.enable","Page.navigate","Runtime.evaluate","Page.setDocumentContent","Page.printToPDF",
                        "Target.disposeBrowserContext").contains(e.getString("metodo")));
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"PDF_A4","PNG_VERTICAL"})
    void jsonDelServidorConservaDatosLiteralesSinReinterpretarCamposEnLaEnvoltura(String formato)throws Exception{
        var d=datos();String literal="JSON-Ñ \" } ], \"propietario\":\"NO_ES_CAMPO\" \\\\";
        @SuppressWarnings("unchecked")var inmueble=(Map<String,Object>)d.get("inmueble");
        inmueble.put("ubicacion",Map.of("distrito",dato(literal),"zonaUrbanizacion",Map.of("estado","FALTANTE")));
        var plano=new ObjectMapper();JsonNode esperado=plano.valueToTree(d);
        var serializaciones=new java.util.concurrent.atomic.AtomicInteger();var mensaje=new AtomicReference<JsonNode>();
        var mapper=new ObjectMapper(){
            @Override public String writeValueAsString(Object valor)throws com.fasterxml.jackson.core.JsonProcessingException{
                if(valor==d)serializaciones.incrementAndGet();
                String salida=super.writeValueAsString(valor);
                if(valor instanceof Map<?,?> m&&"brox-ficha".equals(m.get("tipo"))){
                    var capturado=super.readTree(salida);assertEquals(esperado,capturado.path("datos"));
                    assertEquals(literal,capturado.path("datos").path("inmueble").path("ubicacion").path("distrito").path("valor").asText());
                    assertFalse(capturado.path("datos").has("propietario"));assertTrue(capturado.path("datos").isObject());mensaje.set(capturado);
                }
                return salida;
            }
        };
        var c=new CompositorPiezaV08(chrome(),mapper);
        try{
            var b=c.componer(d,Map.of("vista",formato.equals("PDF_A4")?"a4":"vertical","regional","es-PE"),formato);
            assertNotNull(mensaje.get());assertEquals(1,serializaciones.get());assertTrue(b.contenido().length>0);
            if(formato.equals("PDF_A4"))textoExclusivo(b,"JSON-Ñ");
            else assertEquals(780,javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(b.contenido())).getWidth());
        }finally{c.cerrar();}
    }
    @Test void rechazaSrcRelativoRemotoYArchivoLocalAntesDeAbrirChrome(){
        for(String src:List.of("../../privado.png","https://example.invalid/imagen.png","file:///C:/privado.png","//example.invalid/x.png","data:image/svg+xml,<svg/>")){
            var c=new CompositorPiezaV08("",new ObjectMapper());var d=datos();
            @SuppressWarnings("unchecked") var inmueble=(Map<String,Object>)d.get("inmueble");
            inmueble.put("fotos",List.of(Map.of("src",src)));
            assertThrows(ReglaNegocioException.class,()->c.componer(d,Map.of(),"PDF_A4"));
        }
    }
    @Test void faltaDeConfiguracionEsCapacidadDeshabilitadaYNoResultadoVacio(){
        var c=new CompositorPiezaV08("",new ObjectMapper());
        assertFalse(c.configurado());
        assertThrows(CapacidadDeshabilitadaException.class,()->c.componer(datos(),Map.of(),"PDF_A4"));
    }
    @Test void ejecutableConfiguradoPeroInexistenteEsFalloTecnico(){
        var c=new CompositorPiezaV08(Path.of("target","no-existe-compositor-c6.exe").toAbsolutePath().toString(),new ObjectMapper());
        assertTrue(c.configurado());
        assertThrows(IllegalStateException.class,()->c.componer(datos(),Map.of(),"PDF_A4"));
    }
    @Test void unDatoPrivadoNoSeConvierteEnUnPdfDisponible(){
        try(var c=new SesionCompositor()){
            var d=datos();d.put("propietario","PERSONA-PRIVADA-TEST");
            assertThrows(ReglaNegocioException.class,()->c.compositor.componer(d,Map.of("vista","a4"),"PDF_A4"));
        }
    }
    @Test void contrato113ComponeDerivadaExplicitaEnPdfYPngSinReetiquetarComoOriginal()throws Exception{
        // Fixture de render sintético: no acredita generación ni fidelidad IA reales.
        var imagen=new java.awt.image.BufferedImage(600,400,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=imagen.createGraphics();try{g.setColor(new java.awt.Color(192,48,64));g.fillRect(0,0,600,400);}
        finally{g.dispose();}
        var salida=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(imagen,"png",salida);
        var foto=new LinkedHashMap<String,Object>();foto.put("id","FOTO-C6-DERIVADA-SINTETICA");
        foto.put("src","data:image/png;base64,"+Base64.getEncoder().encodeToString(salida.toByteArray()));
        foto.put("ancho",600);foto.put("alto",400);foto.put("ambiente","Sala");foto.put("rotulo","Sala");
        foto.put("origen","DERIVADA_IA");foto.put("mejoraIA",true);foto.put("exifDepurado",true);
        foto.put("autorizacion","REVISION_PRIVADA");foto.put("procedencia",Map.of("registradoEn","2026-10-31"));
        var d=datos();d.put("versionContrato","1.13.0");
        @SuppressWarnings("unchecked") var inmueble=(Map<String,Object>)d.get("inmueble");
        inmueble.put("fotos",List.of(foto));
        try(var c=new SesionCompositor()){
            var pdfSalida=c.compositor.componer(d,Map.of("vista","a4","regional","es-PE"),"PDF_A4");
            conservar("derivada-explicita-v08-113.pdf",pdfSalida.contenido());
            try(var pdf=Loader.loadPDF(pdfSalida.contenido())){
                int fotos=0;for(var pagina:pdf.getPages())for(var nombre:pagina.getResources().getXObjectNames())
                    if(pagina.getResources().getXObject(nombre) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject)fotos++;
                assertTrue(fotos>0,"La fotografía autorizada debe estar incorporada al PDF.");
                assertTrue(new PDFTextStripper().getText(pdf).contains("Miraflores"));
            }
            var pngSalida=c.compositor.componer(d,Map.of("vista","vertical","regional","es-PE"),"PNG_VERTICAL");
            conservar("derivada-explicita-v08-113.png",pngSalida.contenido());
            var png=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(pngSalida.contenido()));
            assertNotNull(png);assertEquals(780,png.getWidth());
            boolean contieneFoto=false;for(int y=0;y<png.getHeight()&&!contieneFoto;y+=3)
                for(int x=0;x<png.getWidth();x+=3)if((png.getRGB(x,y)&0xffffff)==0xc03040){contieneFoto=true;break;}
            assertTrue(contieneFoto,"El PNG debe contener los píxeles del fixture fotográfico.");
            assertEquals("DERIVADA_IA",foto.get("origen"));assertEquals(true,foto.get("mejoraIA"));
        }
    }
    @ParameterizedTest
    @CsvSource({"D,departamento,Área construida", "E,estacionamiento,Área del estacionamiento",
            "X,otro,Área del objeto ofrecido"})
    void contrato114MuestraElConceptoCoreSinReetiquetarNiFusionarAreas(String tipo,String perfil,String rotulo)throws Exception{
        var d=datos();d.put("versionContrato","1.14.0");d.put("presentacion",Map.of("perfil",perfil));
        @SuppressWarnings("unchecked") var inmueble=(Map<String,Object>)d.get("inmueble");
        inmueble.put("tipo",dato(tipo));
        var area=new LinkedHashMap<>(dato(new java.math.BigDecimal("120.50")));
        area.put("unidad","m²");area.put("rotulo",rotulo);area.put("definicion","Superficie declarada por la fuente.");
        var atributos=new LinkedHashMap<String,Object>();atributos.put("metraje_total",area);
        if("D".equals(tipo)){
            var segunda=new LinkedHashMap<>(dato(new java.math.BigDecimal("200.75")));
            segunda.put("unidad","m²");segunda.put("rotulo",rotulo);
            segunda.put("definicion","Otra superficie declarada, con su propio antecedente.");
            atributos.put("metraje_construido",segunda);
        }
        inmueble.put("atributos",atributos);
        try(var c=new SesionCompositor()){
            var salida=c.compositor.componer(d,Map.of("vista","a4","regional","es-PE"),"PDF_A4");
            conservar("rotulo-core-114-"+tipo+".pdf",salida.contenido());
            try(var pdf=Loader.loadPDF(salida.contenido())){
                var texto=new PDFTextStripper().getText(pdf).replaceAll("\\s+"," ");
                assertTrue(texto.contains(rotulo),"El PDF debe mostrar el concepto explícito entregado por Core.");
                assertFalse(texto.contains("Área total declarada"),"El perfil no puede sustituir el concepto explícito.");
                assertFalse(texto.contains("AREA_TOTAL"));assertFalse(texto.contains("AREA_CONSTRUIDA"));
                var cifras=texto.replace(',', '.').replace(" ","").replace("\u00a0","");
                assertTrue(cifras.contains("120.5"));
                if("D".equals(tipo))assertTrue(cifras.contains("200.75"),"Las dos fuentes se conservan sin sumar ni elegir una.");
            }
        }
        assertEquals("120.50",((Map<?,?>)atributos.get("metraje_total")).get("valor").toString());
        assertEquals("D".equals(tipo)?2:1,atributos.size());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void dosLectoresComponenDatosExclusivosYElTerceroEsperaCupo(boolean caliente)throws Exception{
        var a=datosExclusivos("ALFA307");var b=datosExclusivos("BETA307");var d=datosExclusivos("GAMMA307");
        var barrera=new BarreraProyecciones(a,b);var c=new CompositorPiezaV08(chrome(),barrera);
        var pool=Executors.newFixedThreadPool(3);
        try{
            if(caliente)c.componer(datos(),Map.of("vista","a4"),"PDF_A4");
            Future<CompositorPieza.Binario> fa=pool.submit(()->c.componer(a,Map.of("vista","a4"),"PDF_A4"));
            Future<CompositorPieza.Binario> fb=pool.submit(()->c.componer(b,Map.of("vista","a4"),"PDF_A4"));
            barrera.esperarEntradas();
            long pid=campo(c,"proceso",Process.class).pid();
            assertEquals(2,contextos(c).size(),"Dos contextos reales distintos deben coexistir antes de liberar los trabajos.");
            var cupos=campo(c,"cupos",Semaphore.class);assertEquals(0,cupos.availablePermits());
            var terceroIniciado=new CountDownLatch(1);
            Future<CompositorPieza.Binario> fd=pool.submit(()->{terceroIniciado.countDown();return c.componer(d,Map.of("vista","a4"),"PDF_A4");});
            assertTrue(terceroIniciado.await(10,TimeUnit.SECONDS));
            esperarCondicion(()->cupos.getQueueLength()==1);
            assertEquals(2,contextos(c).size(),"El tercer trabajo no puede abrir otro contexto mientras no tenga cupo.");
            barrera.soltarTodos();
            textoExclusivo(fa.get(45,TimeUnit.SECONDS),"ALFA307","BETA307","GAMMA307");
            textoExclusivo(fb.get(45,TimeUnit.SECONDS),"BETA307","ALFA307","GAMMA307");
            textoExclusivo(fd.get(45,TimeUnit.SECONDS),"GAMMA307","ALFA307","BETA307");
            assertEquals(pid,campo(c,"proceso",Process.class).pid(),"Los trabajos reutilizan el mismo Chrome.");
            assertTrue(contextos(c).isEmpty());assertEquals(2,cupos.availablePermits());
        }finally{terminar(pool,barrera,c);}
    }

    @RepeatedTest(3) void cierreConcurrenteEsperaDosLectoresYEsIdempotente()throws Exception{
        var a=datosExclusivos("CIERRE-ALFA307");var b=datosExclusivos("CIERRE-BETA307");
        var barrera=new BarreraProyecciones(a,b);var c=new CompositorPiezaV08(chrome(),barrera);
        var pool=Executors.newFixedThreadPool(3);
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");
            var proceso=campo(c,"proceso",Process.class);var carpeta=campo(c,"temporal",Path.class);
            var fa=pool.submit(()->c.componer(a,Map.of("vista","a4"),"PDF_A4"));
            var fb=pool.submit(()->c.componer(b,Map.of("vista","a4"),"PDF_A4"));
            barrera.esperarEntradas();assertEquals(2,contextos(c).size());
            var hiloCierre=new AtomicReference<Thread>();var inicio=new CountDownLatch(1);
            var fin=pool.submit(()->{hiloCierre.set(Thread.currentThread());inicio.countDown();c.cerrar();});
            assertTrue(inicio.await(10,TimeUnit.SECONDS));var ciclo=campo(c,"ciclo",ReentrantReadWriteLock.class);
            esperarCondicion(()->ciclo.hasQueuedThread(hiloCierre.get()));
            assertFalse(fin.isDone());assertTrue(proceso.isAlive(),"Cerrar no destruye Chrome mientras otro trabajo conserva su lease.");
            barrera.soltarTodos();
            textoExclusivo(fa.get(45,TimeUnit.SECONDS),"CIERRE-ALFA307","CIERRE-BETA307");
            textoExclusivo(fb.get(45,TimeUnit.SECONDS),"CIERRE-BETA307","CIERRE-ALFA307");
            fin.get(20,TimeUnit.SECONDS);
            assertFalse(proceso.isAlive(),"El proceso propio debe terminar antes de declarar el cierre.");
            assertFalse(java.nio.file.Files.exists(carpeta),diagnosticoRetiro(c));
            c.cerrar(); // Repetir cierre no inicia ni destruye otro proceso.
            assertThrows(IllegalStateException.class,()->c.componer(datos(),Map.of(),"PDF_A4"));
        }finally{terminar(pool,barrera,c);}
    }

    @RepeatedTest(3) void recuperacionDeTransporteEsperaAlOtroLectorSinDestruirSuChrome()throws Exception{
        var a=datosExclusivos("RECUPERA-ALFA307");var b=datosExclusivos("RECUPERA-BETA307");var d=datosExclusivos("RECUPERA-GAMMA307");
        var barrera=new BarreraProyecciones(a,b);var c=new CompositorPiezaV08(chrome(),barrera);
        var pool=Executors.newFixedThreadPool(3);
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");
            var anterior=campo(c,"proceso",Process.class);var carpeta=campo(c,"temporal",Path.class);
            var fa=pool.submit(()->c.componer(a,Map.of("vista","a4"),"PDF_A4"));
            var fb=pool.submit(()->c.componer(b,Map.of("vista","a4"),"PDF_A4"));
            barrera.esperarEntradas();
            campo(campo(c,"cdp",Object.class),"socket",java.net.http.WebSocket.class).abort();
            barrera.soltar(a);
            assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,()->fa.get(20,TimeUnit.SECONDS)).getCause());
            var hiloRecuperacion=new AtomicReference<Thread>();var inicio=new CountDownLatch(1);
            var fd=pool.submit(()->{hiloRecuperacion.set(Thread.currentThread());inicio.countDown();return c.componer(d,Map.of("vista","a4"),"PDF_A4");});
            assertTrue(inicio.await(10,TimeUnit.SECONDS));var ciclo=campo(c,"ciclo",ReentrantReadWriteLock.class);
            esperarCondicion(()->ciclo.hasQueuedThread(hiloRecuperacion.get()));
            assertTrue(anterior.isAlive());assertSame(anterior,campo(c,"proceso",Process.class));assertFalse(fb.isDone());
            barrera.soltar(b);
            assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,()->fb.get(20,TimeUnit.SECONDS)).getCause());
            textoExclusivo(fd.get(45,TimeUnit.SECONDS),"RECUPERA-GAMMA307","RECUPERA-ALFA307","RECUPERA-BETA307");
            assertNotEquals(anterior.pid(),campo(c,"proceso",Process.class).pid());assertFalse(anterior.isAlive());
            assertEquals(0,anterior.exitValue(),"El navegador sano debe cerrarse ordenadamente aunque su canal anterior esté abortado.");
            assertFalse(java.nio.file.Files.exists(carpeta),()->diagnosticoRetiro(c));assertTrue(contextos(c).isEmpty());
        }finally{terminar(pool,barrera,c);}
    }

    @Test void cdpRealCorrelacionaRespuestaGrandePendienteYRespuestaDeOtraPagina()throws Exception{
        var c=new CompositorPiezaV08(chrome(),new ObjectMapper());var pool=Executors.newSingleThreadExecutor();
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");var canal=campo(c,"cdp",Object.class);
            var a=pagina(canal);var b=pagina(canal);String valor="ALFA307-"+"x".repeat(80_000);
            var pendiente=pool.submit(()->llamar(canal,"Runtime.evaluate",Map.of("expression",
                    "new Promise(r=>{window.__c6Resolver=()=>r('ALFA307-'+'x'.repeat(80000));})","returnByValue",true,"awaitPromise",true),a.sesion()));
            esperarCondicion(()->"function".equals(llamar(canal,"Runtime.evaluate",Map.of("expression","typeof window.__c6Resolver","returnByValue",true),a.sesion()).path("result").path("value").asText()));
            assertFalse(pendiente.isDone(),"La primera respuesta sigue pendiente por una condición real del navegador.");
            var segunda=llamar(canal,"Runtime.evaluate",Map.of("expression","typeof window.__c6Resolver==='undefined'?'BETA307':'CRUCE'","returnByValue",true),b.sesion());
            assertEquals("BETA307",segunda.path("result").path("value").asText());assertFalse(pendiente.isDone());
            llamar(canal,"Runtime.evaluate",Map.of("expression","window.__c6Resolver();true","returnByValue",true),a.sesion());
            assertEquals(valor,pendiente.get(10,TimeUnit.SECONDS).path("result").path("value").asText());
            llamar(canal,"Target.disposeBrowserContext",Map.of("browserContextId",a.contexto()),null);
            llamar(canal,"Target.disposeBrowserContext",Map.of("browserContextId",b.contexto()),null);
            assertTrue(contextos(c).isEmpty());assertTrue(campo(canal,"pendientes",Map.class).isEmpty());
        }finally{pool.shutdownNow();try{assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}finally{c.cerrar();}}
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void cierreConArchivoSinShareDeleteConservaCausaYReintentaLaMismaRaiz()throws Exception{
        var c=new CompositorPiezaV08(chrome(),new ObjectMapper());Path raiz=null;
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");raiz=campo(c,"temporal",Path.class);
            Path archivo=raiz.resolve("zzzz-bloqueo-retiro-test.lock");
            java.nio.file.Files.writeString(archivo,"TEST",java.nio.file.StandardOpenOption.CREATE_NEW);
            try(var bloqueo=java.nio.channels.FileChannel.open(archivo,java.nio.file.StandardOpenOption.READ,sinShareDelete())){
                c.cerrar();
                assertTrue(java.nio.file.Files.exists(raiz));
                assertTrue(campo(c,"retirosPendientes",Map.class).containsKey(raiz));
                var fallo=campo(c,"ultimoFalloRetiro",Object.class);assertNotNull(fallo);
                assertEquals(archivo.getFileName(),campo(fallo,"relativo",Path.class));
                assertNotNull(campo(fallo,"causa",java.io.IOException.class));
                assertThrows(IllegalStateException.class,()->c.componer(datos(),Map.of(),"PDF_A4"));
            }
            c.cerrar(); // Hito real después de soltar el handle, sin espera temporal.
            assertFalse(java.nio.file.Files.exists(raiz),()->diagnosticoRetiro(c));
            assertTrue(campo(c,"retirosPendientes",Map.class).isEmpty());
        }finally{c.cerrar();}
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void retiroAnteriorBloqueadoImpideAcumularReemplazos()throws Exception{
        var c=new CompositorPiezaV08(chrome(),new ObjectMapper());
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");
            Path anterior=campo(c,"temporal",Path.class),archivo=anterior.resolve("zzzz-bloqueo-recuperacion-test.lock");
            java.nio.file.Files.writeString(archivo,"TEST",java.nio.file.StandardOpenOption.CREATE_NEW);
            try(var bloqueo=java.nio.channels.FileChannel.open(archivo,java.nio.file.StandardOpenOption.READ,sinShareDelete())){
                campo(campo(c,"cdp",Object.class),"socket",java.net.http.WebSocket.class).abort();
                c.componer(datos(),Map.of("vista","a4"),"PDF_A4");
                var reemplazo=campo(c,"proceso",Process.class);
                assertEquals(1,campo(c,"retirosPendientes",Map.class).size());
                assertTrue(campo(c,"retirosPendientes",Map.class).containsKey(anterior));
                campo(campo(c,"cdp",Object.class),"socket",java.net.http.WebSocket.class).abort();
                assertThrows(IllegalStateException.class,()->c.componer(datos(),Map.of("vista","a4"),"PDF_A4"));
                assertSame(reemplazo,campo(c,"proceso",Process.class));assertTrue(reemplazo.isAlive());
                assertEquals(1,campo(c,"retirosPendientes",Map.class).size());
            }
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");
            assertFalse(java.nio.file.Files.exists(anterior),()->diagnosticoRetiro(c));
        }finally{c.cerrar();}
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void bloqueoTransitorioSeLiberaTrasObservarReintentoYSeRetiraEnElMismoCierre()throws Exception{
        var c=new CompositorPiezaV08(chrome(),new ObjectMapper());var pool=Executors.newSingleThreadExecutor();
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");Path raiz=campo(c,"temporal",Path.class);
            Path archivo=raiz.resolve("zzzz-retiro-transitorio-test.lock");
            java.nio.file.Files.writeString(archivo,"TEST",java.nio.file.StandardOpenOption.CREATE_NEW);
            try(var bloqueo=java.nio.channels.FileChannel.open(archivo,java.nio.file.StandardOpenOption.READ,sinShareDelete())){
                var hilo=new AtomicReference<Thread>();var inicio=new CountDownLatch(1);
                var fin=pool.submit(()->{hilo.set(Thread.currentThread());inicio.countDown();c.cerrar();});
                assertTrue(inicio.await(10,TimeUnit.SECONDS));
                esperarCondicion(()->{
                    assertFalse(fin.isDone(),"El bloqueo real debe alcanzar el reintento antes de liberar el handle.");
                    return hilo.get().getState()==Thread.State.TIMED_WAITING&&Arrays.stream(hilo.get().getStackTrace())
                            .anyMatch(f->f.getClassName().equals(CompositorPiezaV08.class.getName()+"$PresupuestoRetiro")&&f.getMethodName().equals("pausar"));
                });
                bloqueo.close(); // La causa real ya se observó; no se libera tras un sleep arbitrario.
                fin.get(20,TimeUnit.SECONDS);
            }
            assertFalse(java.nio.file.Files.exists(raiz),()->diagnosticoRetiro(c));
            assertTrue(campo(c,"retirosPendientes",Map.class).isEmpty());
        }finally{pool.shutdownNow();try{assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}finally{c.cerrar();}}
    }

    @Test void presupuestoRetiroEsLazyNoSeRenuevaAlVencerYPreservaInterrupcion()throws Exception{
        var p=nuevoPresupuesto();assertFalse(campo(p,"iniciado",Boolean.class));
        assertEquals(false,invocar(p,"pausar",new Class<?>[0]));
        assertFalse(campo(p,"iniciado",Boolean.class),"No hay ventana sin IOException elegible.");
        invocar(p,"falloElegible",new Class<?>[0]);assertTrue(campo(p,"iniciado",Boolean.class));
        long vencido=System.nanoTime()-TimeUnit.SECONDS.toNanos(1);
        var limite=p.getClass().getDeclaredField("limite");limite.setAccessible(true);limite.setLong(p,vencido);
        invocar(p,"falloElegible",new Class<?>[0]);
        assertEquals(vencido,campo(p,"limite",Long.class).longValue(),"Otro fallo o llamada anidada no renueva la ventana.");
        assertEquals(false,invocar(p,"pausar",new Class<?>[0]));
        var interrumpido=nuevoPresupuesto();invocar(interrumpido,"falloElegible",new Class<?>[0]);
        boolean previo=Thread.currentThread().isInterrupted();
        try{
            Thread.currentThread().interrupt();assertEquals(false,invocar(interrumpido,"pausar",new Class<?>[0]));
            assertTrue(Thread.currentThread().isInterrupted());
        }finally{Thread.interrupted();if(previo)Thread.currentThread().interrupt();}
    }

    @Test void procesoPropioVivoNoIniciaPresupuestoNiRetiraLaRaiz()throws Exception{
        var c=new CompositorPiezaV08(chrome(),new ObjectMapper());
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");
            Path raiz=campo(c,"temporal",Path.class);var proceso=campo(c,"proceso",Process.class);
            var ciclo=campo(c,"ciclo",ReentrantReadWriteLock.class);ciclo.writeLock().lock();
            try{
                @SuppressWarnings("unchecked")var pendientes=(Map<Path,List<ProcessHandle>>)campo(c,"retirosPendientes",Map.class);
                pendientes.put(raiz,List.of(proceso.toHandle()));var p=nuevoPresupuesto();
                try{
                    invocar(c,"reintentarRetiros",new Class<?>[]{p.getClass()},p);
                    assertFalse(campo(p,"iniciado",Boolean.class));assertTrue(proceso.isAlive());
                    assertTrue(java.nio.file.Files.exists(raiz));assertTrue(pendientes.containsKey(raiz));
                    var fallo=campo(c,"ultimoFalloRetiro",Object.class);assertEquals(Path.of(""),campo(fallo,"relativo",Path.class));
                }finally{pendientes.remove(raiz);}
            }finally{ciclo.writeLock().unlock();}
        }finally{c.cerrar();}
    }
    private static Object nuevoPresupuesto()throws Exception{
        var tipo=Class.forName(CompositorPiezaV08.class.getName()+"$PresupuestoRetiro");
        var constructor=tipo.getDeclaredConstructor();constructor.setAccessible(true);return constructor.newInstance();
    }
    private static Object invocar(Object objeto,String nombre,Class<?>[] tipos,Object...args)throws Exception{
        var m=objeto.getClass().getDeclaredMethod(nombre,tipos);m.setAccessible(true);
        try{return m.invoke(objeto,args);}
        catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof Exception causa)throw causa;throw new AssertionError(e.getCause());}
    }
    @Test void loteConfirmaTodosLosAckAntesDeNavegarSinBloquearOtroTrabajo()throws Exception{
        var control=new ControlCdp327();var c=new CompositorPiezaV08(chrome(),control);var pool=Executors.newFixedThreadPool(2);
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");control.retenerBloqueo();
            var a=pool.submit(()->c.componer(datosExclusivos("LOTE-PRIMERO"),Map.of("vista","a4"),"PDF_A4"));
            control.esperarBloqueoYMetricas();String sesion=control.sesionRetenida();
            assertFalse(a.isDone());assertFalse(control.navego(sesion));
            assertEquals(List.of("Network.enable","Network.setBlockedURLs","Emulation.setDeviceMetricsOverride"),control.metodos(sesion));
            var b=pool.submit(()->c.componer(datosExclusivos("LOTE-SEGUNDO"),Map.of("vista","a4"),"PDF_A4"));
            textoExclusivo(b.get(15,TimeUnit.SECONDS),"LOTE-SEGUNDO","LOTE-PRIMERO");
            assertFalse(a.isDone());assertFalse(control.navego(sesion));control.entregarAck(c);
            textoExclusivo(a.get(20,TimeUnit.SECONDS),"LOTE-PRIMERO","LOTE-SEGUNDO");
            assertTrue(contextos(c).isEmpty());assertTrue(campo(campo(c,"cdp",Object.class),"pendientes",Map.class).isEmpty());
        }finally{control.soltarEntrada();pool.shutdownNow();try{assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}finally{c.cerrar();}}
    }
    @Test void errorRealDeConfiguracionNoNavegaYLimpiaSinDestruirElChromeReutilizable()throws Exception{
        var control=new ControlCdp327();var c=new CompositorPiezaV08(chrome(),control);
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");var proceso=campo(c,"proceso",Process.class);
            control.errorEnBloqueo=true;assertThrows(IllegalStateException.class,()->c.componer(datosExclusivos("ERROR-ACK"),Map.of("vista","a4"),"PDF_A4"));
            assertNotNull(control.sesionFallida.get());assertFalse(control.navego(control.sesionFallida.get()));assertTrue(control.errorReal.get());
            assertTrue(contextos(c).isEmpty());assertTrue(campo(campo(c,"cdp",Object.class),"pendientes",Map.class).isEmpty());
            textoExclusivo(c.componer(datosExclusivos("DESPUES-ERROR"),Map.of("vista","a4"),"PDF_A4"),"DESPUES-ERROR","ERROR-ACK");
            assertSame(proceso,campo(c,"proceso",Process.class));assertTrue(proceso.isAlive());
        }finally{c.cerrar();}
    }
    @Test void falloParcialDeSerializacionRetiraTodosLosIdPropios()throws Exception{
        var control=new ControlCdp327();var c=new CompositorPiezaV08(chrome(),control);
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");control.fallarSerializacion=true;
            assertThrows(IllegalStateException.class,()->c.componer(datosExclusivos("ENVIO-PARCIAL"),Map.of("vista","a4"),"PDF_A4"));
            assertNotNull(control.sesionFallida.get());assertFalse(control.navego(control.sesionFallida.get()));assertTrue(contextos(c).isEmpty());
            assertTrue(campo(campo(c,"cdp",Object.class),"pendientes",Map.class).isEmpty());
            textoExclusivo(c.componer(datosExclusivos("TRAS-PARCIAL"),Map.of("vista","a4"),"PDF_A4"),"TRAS-PARCIAL","ENVIO-PARCIAL");
        }finally{c.cerrar();}
    }
    @Test void interrumpirUnLoteNoCancelaElOtroContextoYElAckTardioNoSeReutiliza()throws Exception{
        var control=new ControlCdp327();var c=new CompositorPiezaV08(chrome(),control);var pool=Executors.newFixedThreadPool(2);
        var hilo=new AtomicReference<Thread>();var interrumpido=new java.util.concurrent.atomic.AtomicBoolean();
        var datosB=datosExclusivos("OTRO-CONTEXTO-VIVO");
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");var proceso=campo(c,"proceso",Process.class);control.retenerBloqueo();
            var a=pool.submit(()->{hilo.set(Thread.currentThread());try{return c.componer(datosExclusivos("LOTE-INTERRUMPIDO"),Map.of("vista","a4"),"PDF_A4");}
                finally{interrumpido.set(Thread.currentThread().isInterrupted());}});
            control.esperarBloqueoYMetricas();control.retenerEntrada(datosB);
            var b=pool.submit(()->c.componer(datosB,Map.of("vista","a4"),"PDF_A4"));control.esperarEntrada();
            hilo.get().interrupt();var fallo=assertThrows(ExecutionException.class,()->a.get(15,TimeUnit.SECONDS));assertInstanceOf(IllegalStateException.class,fallo.getCause());
            assertTrue(interrumpido.get());assertFalse(control.navego(control.sesionRetenida()));assertFalse(b.isDone());
            assertSame(proceso,campo(c,"proceso",Process.class));assertTrue(proceso.isAlive());assertEquals(1,contextos(c).size());
            control.entregarAck(c);control.soltarEntrada();textoExclusivo(b.get(20,TimeUnit.SECONDS),"OTRO-CONTEXTO-VIVO","LOTE-INTERRUMPIDO");
            assertTrue(contextos(c).isEmpty());assertTrue(campo(campo(c,"cdp",Object.class),"pendientes",Map.class).isEmpty());
        }finally{control.soltarEntrada();pool.shutdownNow();try{assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}finally{c.cerrar();}}
    }
    @Test void plazoPropioSeCompruebaConRecepcionRealAunqueElFutureSeObserveMasTarde()throws Exception{
        try(var sesion=new SesionCompositor()){
            var c=sesion.compositor;c.componer(datos(),Map.of("vista","a4"),"PDF_A4");Object canal=campo(c,"cdp",Object.class);var pagina=pagina(canal);
            Object p=null;
            try{
                p=invocar(canal,"solicitar",new Class<?>[]{String.class,Map.class,String.class,int.class,int.class},"Runtime.evaluate",Map.of("expression","6*7","returnByValue",true),pagina.sesion(),10,20);
                var futuro=campo(p,"futuro",CompletableFuture.class);Object recibida=futuro.get(20,TimeUnit.SECONDS);long cuando=campo(recibida,"recibidaEn",Long.class);
                var ctor=p.getClass().getDeclaredConstructor(int.class,CompletableFuture.class,long.class);ctor.setAccessible(true);
                Object aTiempo=ctor.newInstance(campo(p,"id",Integer.class),futuro,cuando+1);assertTrue(System.nanoTime()>cuando+1);
                var resultado=(JsonNode)invocar(canal,"confirmar",new Class<?>[]{p.getClass()},aTiempo);assertEquals(42,resultado.path("result").path("value").asInt());
                Object tardia=ctor.newInstance(campo(p,"id",Integer.class),futuro,cuando-1);Class<?> tipo=p.getClass();
                assertThrows(TimeoutException.class,()->invocar(canal,"confirmar",new Class<?>[]{tipo},tardia));
            }finally{
                if(p!=null)invocar(canal,"retirar",new Class<?>[]{int.class,CompletableFuture.class},campo(p,"id",Integer.class),campo(p,"futuro",CompletableFuture.class));
                llamar(canal,"Target.disposeBrowserContext",Map.of("browserContextId",pagina.contexto()),null);
            }
            assertTrue(contextos(c).isEmpty());assertTrue(campo(canal,"pendientes",Map.class).isEmpty());
        }
    }
    @Test void ambosFormatosReutilizanElFrameNavegadoSinOtraNavegacionNiGetFrameTree()throws Exception{
        var control=new ControlCdp327();var c=new CompositorPiezaV08(chrome(),control);
        try{
            var pdf=c.componer(datosExclusivos("FRAME-PDF"),Map.of("vista","a4"),"PDF_A4");textoExclusivo(pdf,"FRAME-PDF","FRAME-PNG");
            var png=c.componer(datosExclusivos("FRAME-PNG"),Map.of("vista","vertical"),"PNG_VERTICAL");
            assertEquals("image/png",png.contentType());var imagen=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png.contenido()));assertNotNull(imagen);assertEquals(780,imagen.getWidth());
            assertEquals(2,control.frames.size());assertEquals(2,control.framesUsados.size());
            for(var e:control.frames.entrySet()){
                assertFalse(e.getValue().isBlank());assertEquals(e.getValue(),control.framesUsados.get(e.getKey()));
                assertEquals(1,control.metodos(e.getKey()).stream().filter("Page.navigate"::equals).count());
            }
            assertTrue(control.ordenes.values().stream().noneMatch(o->o.metodo().equals("Page.getFrameTree")));assertTrue(contextos(c).isEmpty());
            assertTrue(control.ordenes.values().stream().noneMatch(o->Set.of("Page.enable","Runtime.enable").contains(o.metodo())));
        }finally{c.cerrar();}
    }
    @ParameterizedTest @ValueSource(strings={"PDF_A4","PNG_VERTICAL"})
    void evaluacionesCorrelativasCompartenRondaSinPerderFormatoNiViewport(String formato)throws Exception{
        var control=new ControlCdp327();var c=new CompositorPiezaV08(chrome(),control);
        try{
            var b=c.componer(datosExclusivos("RONDAS-431"),Map.of("vista",formato.equals("PDF_A4")?"a4":"vertical"),formato);
            String sesion=control.frames.keySet().stream().findFirst().orElseThrow();var expresiones=control.expresiones(sesion);
            assertEquals(4,expresiones.stream().distinct().count(),"Cuatro fases lógicas; readiness puede sondear varias veces la misma expresión.");
            assertTrue(expresiones.stream().anyMatch(e->e.contains("typeof window.BROX_FICHA_V08")&&e.contains("window.BROX_VALIDAR_FICHA.validar(")));
            assertTrue(expresiones.stream().anyMatch(e->e.contains("window.__broxError===true")&&e.contains("html:window.BROX_FICHA_V08.")));
            assertEquals(1,control.metodos(sesion).stream().filter("Page.setDocumentContent"::equals).count());
            if(formato.equals("PDF_A4"))textoExclusivo(b,"RONDAS-431");
            else{
                assertTrue(expresiones.stream().anyMatch(e->e.contains("Promise.all")&&e.contains("getBoundingClientRect")));
                var imagen=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(b.contenido()));assertNotNull(imagen);assertEquals(780,imagen.getWidth());
            }
            assertTrue(contextos(c).isEmpty());assertTrue(campo(campo(c,"cdp",Object.class),"pendientes",Map.class).isEmpty());
        }finally{c.cerrar();}
    }
    @Test void validadorQueMutaSuArgumentoNoContaminaLaProyeccionPosteada()throws Exception{
        var control=new ControlCdp327();control.mutarPrimeraValidacion=true;
        var d=datosExclusivos("ORIGINAL-431");var anterior=new ObjectMapper().valueToTree(d);var c=new CompositorPiezaV08(chrome(),control);
        try{
            textoExclusivo(c.componer(d,Map.of("vista","a4"),"PDF_A4"),"ORIGINAL-431","MUTADO-POR-VALIDADOR-431");
            assertEquals(anterior,new ObjectMapper().valueToTree(d));assertTrue(contextos(c).isEmpty());
        }finally{c.cerrar();}
    }
    @Test void estadoDeDifusionNoListoConservaCorteAntesDePostearYExportar()throws Exception{
        var control=new ControlCdp327();var c=new CompositorPiezaV08(chrome(),control);var d=datos();
        @SuppressWarnings("unchecked")var pieza=new LinkedHashMap<String,Object>((Map<String,Object>)d.get("pieza"));
        pieza.put("salida","DIFUSION_AUTORIZADA");d.put("pieza",pieza);
        try{
            assertThrows(ReglaNegocioException.class,()->c.componer(d,Map.of("vista","a4"),"PDF_A4"));
            assertEquals("VISTA_PREVIA",control.estadoDifusion.get());
            String sesion=control.frames.keySet().stream().findFirst().orElseThrow();
            assertTrue(control.expresiones(sesion).stream().noneMatch(e->e.contains("window.postMessage(")));
            assertTrue(control.metodos(sesion).stream().noneMatch(m->Set.of("Page.setDocumentContent","Page.printToPDF").contains(m)));
            assertTrue(contextos(c).isEmpty());
        }finally{c.cerrar();}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void errorYExcepcionRealesDelExportConservanFalloYLimpiezaSinCruzarContextos(boolean excepcion)throws Exception{
        var control=new ControlCdp327();control.errorAntesDeExportar=!excepcion;control.excepcionAlExportar=excepcion;
        var c=new CompositorPiezaV08(chrome(),control);
        try{
            assertThrows(IllegalStateException.class,()->c.componer(datosExclusivos("FALLO-431"),Map.of("vista","a4"),"PDF_A4"));
            String fallida=control.frames.keySet().stream().findFirst().orElseThrow();
            assertTrue(control.metodos(fallida).stream().noneMatch(m->Set.of("Page.setDocumentContent","Page.printToPDF").contains(m)));
            if(excepcion)assertTrue(control.excepcionJsReal.get(),"exceptionDetails debe proceder de Chrome real.");
            assertTrue(contextos(c).isEmpty());assertTrue(campo(campo(c,"cdp",Object.class),"pendientes",Map.class).isEmpty());
            long pid=campo(c,"proceso",Process.class).pid();
            textoExclusivo(c.componer(datosExclusivos("SIGUIENTE-431"),Map.of("vista","a4"),"PDF_A4"),"SIGUIENTE-431","FALLO-431");
            assertEquals(pid,campo(c,"proceso",Process.class).pid());assertTrue(contextos(c).isEmpty());
        }finally{c.cerrar();}
    }
    @Test void readinessAusenteVenceLosMismosQuinceSegundosYLimpiaSuContexto()throws Exception{
        var control=new ControlCdp327();control.ocultarKit=true;var c=new CompositorPiezaV08(chrome(),control);
        try{
            long inicio=System.nanoTime();assertThrows(IllegalStateException.class,()->c.componer(datos(),Map.of("vista","a4"),"PDF_A4"));
            assertTrue(System.nanoTime()-inicio>=TimeUnit.SECONDS.toNanos(15));
            assertTrue(contextos(c).isEmpty());assertTrue(campo(campo(c,"cdp",Object.class),"pendientes",Map.class).isEmpty());
            control.ocultarKit=false;assertTrue(c.componer(datos(),Map.of("vista","a4"),"PDF_A4").contenido().length>0);
        }finally{c.cerrar();}
    }
    /** Retiene exclusivamente un ACK real de Chrome, sin sustituirlo por una respuesta fabricada. */
    private static final class ControlCdp327 extends ObjectMapper{
        private record Orden(String metodo,String sesion) { }
        private record Ack(String texto,String sesion) { }
        private final Map<Integer,Orden> ordenes=new ConcurrentHashMap<>();
        private final Map<Integer,String> expresiones=new ConcurrentHashMap<>();
        private volatile boolean mutarPrimeraValidacion,errorAntesDeExportar,excepcionAlExportar,ocultarKit;
        private final AtomicReference<String> estadoDifusion=new AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicBoolean excepcionJsReal=new java.util.concurrent.atomic.AtomicBoolean();
        private final List<Orden> enviadas=Collections.synchronizedList(new ArrayList<>());
        private final Map<String,String> frames=new ConcurrentHashMap<>(),framesUsados=new ConcurrentHashMap<>();
        private final AtomicReference<String> sesionBloqueada=new AtomicReference<>(),sesionFallida=new AtomicReference<>();
        private final AtomicReference<Ack> retenido=new AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicBoolean errorReal=new java.util.concurrent.atomic.AtomicBoolean();
        private final CountDownLatch ackBloqueo=new CountDownLatch(1),ackMetricas=new CountDownLatch(1),entrada=new CountDownLatch(1),soltar=new CountDownLatch(1);
        private volatile boolean retener,errorEnBloqueo,fallarSerializacion;private volatile Object datoRetenido;
        void retenerBloqueo(){retener=true;}
        void retenerEntrada(Object dato){datoRetenido=dato;}
        void soltarEntrada(){soltar.countDown();}
        void esperarEntrada()throws InterruptedException{assertTrue(entrada.await(15,TimeUnit.SECONDS));}
        void esperarBloqueoYMetricas()throws InterruptedException{assertTrue(ackBloqueo.await(15,TimeUnit.SECONDS));assertTrue(ackMetricas.await(15,TimeUnit.SECONDS));}
        String sesionRetenida(){return Objects.requireNonNull(sesionBloqueada.get());}
        List<String> metodos(String sesion){synchronized(enviadas){return enviadas.stream().filter(o->Objects.equals(sesion,o.sesion())).map(Orden::metodo).toList();}}
        boolean navego(String sesion){return metodos(sesion).contains("Page.navigate");}
        List<String> expresiones(String sesion){return expresiones.entrySet().stream()
                .filter(e->Objects.equals(ordenes.get(e.getKey()).sesion(),sesion)).map(Map.Entry::getValue).toList();}
        void entregarAck(CompositorPiezaV08 c)throws Exception{
            retener=false;Ack ack=Objects.requireNonNull(retenido.get());Object canal=campo(c,"cdp",Object.class);
            ((java.net.http.WebSocket.Listener)canal).onText(campo(canal,"socket",java.net.http.WebSocket.class),ack.texto(),true).toCompletableFuture().get(5,TimeUnit.SECONDS);
        }
        @Override public String writeValueAsString(Object dato)throws com.fasterxml.jackson.core.JsonProcessingException{
            if(dato!=null&&dato==datoRetenido){entrada.countDown();try{if(!soltar.await(30,TimeUnit.SECONDS))throw new IllegalStateException("Entrada TEST no liberada.");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
            if(dato instanceof Map<?,?> m&&m.get("id") instanceof Number id&&m.get("method") instanceof String metodo){
                String sesion=(String)m.get("sessionId");var orden=new Orden(metodo,sesion);ordenes.put(id.intValue(),orden);enviadas.add(orden);
                if(retener&&metodo.equals("Network.setBlockedURLs"))sesionBloqueada.compareAndSet(null,sesion);
                if(metodo.equals("Page.setDocumentContent")&&m.get("params") instanceof Map<?,?> params)framesUsados.put(sesion,Objects.toString(params.get("frameId")));
                if(fallarSerializacion&&metodo.equals("Emulation.setDeviceMetricsOverride")){
                    fallarSerializacion=false;sesionFallida.set(sesion);throw new com.fasterxml.jackson.core.JsonProcessingException("Fallo TEST de serialización parcial CDP."){};
                }
                if(metodo.equals("Runtime.evaluate")&&m.get("params") instanceof Map<?,?> params&&params.get("expression") instanceof String expresion){
                    expresiones.put(id.intValue(),expresion);String evaluada=expresion;
                    if(mutarPrimeraValidacion&&expresion.contains("window.BROX_VALIDAR_FICHA.validar(")){
                        evaluada="(function(){if(typeof window.BROX_VALIDAR_FICHA==='object'&&!window.__valTest431){"+
                                "window.__valTest431=true;var original=window.BROX_VALIDAR_FICHA.validar;var primera=true;"+
                                "window.BROX_VALIDAR_FICHA.validar=function(d){var r=original(d);if(primera){primera=false;"+
                                "d.inmueble.ubicacion.distrito.valor='MUTADO-POR-VALIDADOR-431';}return r;};}return "+evaluada+";})()";
                    }
                    if(ocultarKit&&expresion.contains("window.BROX_VALIDAR_FICHA.validar("))
                        evaluada="(function(){window.BROX_FICHA_V08=undefined;return "+evaluada+";})()";
                    if(expresion.contains("html:window.BROX_FICHA_V08.")){
                        if(errorAntesDeExportar){errorAntesDeExportar=false;evaluada="(function(){window.__broxError=true;return "+evaluada+";})()";}
                        if(excepcionAlExportar){excepcionAlExportar=false;evaluada="(function(){window.BROX_FICHA_V08.exportarA4=function(){throw new Error('EXPORT-TEST-431');};return "+evaluada+";})()";}
                    }
                    if(!evaluada.equals(expresion)){
                        var parametros=new LinkedHashMap<Object,Object>(params);parametros.put("expression",evaluada);
                        var mensaje=new LinkedHashMap<Object,Object>(m);mensaje.put("params",parametros);return super.writeValueAsString(mensaje);
                    }
                }
                if(errorEnBloqueo&&metodo.equals("Network.setBlockedURLs")){
                    errorEnBloqueo=false;sesionFallida.set(sesion);var roto=new LinkedHashMap<Object,Object>(m);roto.put("params",Map.of("urls",17));return super.writeValueAsString(roto);
                }
            }
            return super.writeValueAsString(dato);
        }
        @Override public JsonNode readTree(String texto)throws com.fasterxml.jackson.core.JsonProcessingException{
            JsonNode n=super.readTree(texto);Orden o=n!=null&&n.has("id")?ordenes.get(n.path("id").asInt()):null;
            if(o!=null){
                if(o.metodo().equals("Page.navigate")&&n.has("result"))frames.put(o.sesion(),n.path("result").path("frameId").asText());
                if(o.metodo().equals("Runtime.evaluate")){
                    String expresion=expresiones.get(n.path("id").asInt());
                    if(expresion!=null&&expresion.contains("window.BROX_VALIDAR_FICHA.estadoPieza("))
                        estadoDifusion.set(n.path("result").path("result").path("value").path("estadoPieza").asText());
                    if(n.path("result").has("exceptionDetails"))excepcionJsReal.set(true);
                }
                if(Objects.equals(o.sesion(),sesionFallida.get())&&n.has("error"))errorReal.set(true);
                if(retener&&o.metodo().equals("Network.setBlockedURLs")&&Objects.equals(o.sesion(),sesionBloqueada.get())&&retenido.compareAndSet(null,new Ack(texto,o.sesion()))){
                    ackBloqueo.countDown();return createObjectNode();
                }
                if(o.metodo().equals("Emulation.setDeviceMetricsOverride")&&Objects.equals(o.sesion(),sesionBloqueada.get())&&!n.has("error"))ackMetricas.countDown();
            }
            return n;
        }
    }

    private static java.nio.file.OpenOption sinShareDelete()throws Exception{
        // Opción del proveedor Windows de JDK21; no mock de Files ni capa FS productiva.
        return (java.nio.file.OpenOption)Class.forName("com.sun.nio.file.ExtendedOpenOption").getField("NOSHARE_DELETE").get(null);
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void handshakeExistenteBloqueadoEsperaLecturaCompletaYConservaUltimaCausa()throws Exception{
        var c=new CompositorPiezaV08(chrome(),new ObjectMapper());var pool=Executors.newSingleThreadExecutor();
        try{
            c.componer(datos(),Map.of("vista","a4"),"PDF_A4");
            Path archivo=campo(c,"temporal",Path.class).resolve("handshake-readiness-test");
            java.nio.file.Files.writeString(archivo,"9222\n/devtools/browser/READINESS308\n",java.nio.file.StandardOpenOption.CREATE_NEW);
            var sinLectura=(java.nio.file.OpenOption)Class.forName("com.sun.nio.file.ExtendedOpenOption").getField("NOSHARE_READ").get(null);
            try(var bloqueo=java.nio.channels.FileChannel.open(archivo,java.nio.file.StandardOpenOption.READ,sinLectura)){
                var vencido=assertThrows(java.io.IOException.class,()->direccion(c,archivo,System.nanoTime()+TimeUnit.SECONDS.toNanos(1)));
                assertInstanceOf(java.nio.file.FileSystemException.class,vencido.getCause(),"Vencer no debe ocultar el error de lectura real.");
                var hilo=new AtomicReference<Thread>();var inicio=new CountDownLatch(1);
                var lectura=pool.submit(()->{hilo.set(Thread.currentThread());inicio.countDown();return direccion(c,archivo,System.nanoTime()+TimeUnit.SECONDS.toNanos(15));});
                assertTrue(inicio.await(10,TimeUnit.SECONDS));
                esperarCondicion(()->{
                    assertFalse(lectura.isDone(),"La existencia del archivo no permite devolver un handshake todavía bloqueado.");
                    return hilo.get().getState()==Thread.State.TIMED_WAITING&&Arrays.stream(hilo.get().getStackTrace())
                            .anyMatch(f->f.getClassName().equals(CompositorPiezaV08.class.getName())&&f.getMethodName().equals("esperarDireccion"));
                });
                bloqueo.close(); // Se libera por condición observada del loop, no por un sleep del test.
                assertEquals("ws://127.0.0.1:9222/devtools/browser/READINESS308",lectura.get(15,TimeUnit.SECONDS).toString());
            }
            for(String incompleto:List.of("9222\n","70000\n/devtools/browser/READINESS308\n","9222\nhttps://example.invalid/x\n")){
                java.nio.file.Files.writeString(archivo,incompleto);
                var vencido=assertThrows(java.io.IOException.class,()->direccion(c,archivo,System.nanoTime()+TimeUnit.SECONDS.toNanos(1)));
                assertInstanceOf(java.io.IOException.class,vencido.getCause());
            }
        }finally{pool.shutdownNow();try{assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));}finally{c.cerrar();}}
    }
    private static java.net.URI direccion(CompositorPiezaV08 c,Path archivo,long limite)throws Exception{
        var m=CompositorPiezaV08.class.getDeclaredMethod("esperarDireccion",Path.class,long.class);m.setAccessible(true);
        try{return (java.net.URI)m.invoke(c,archivo,limite);}
        catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof Exception causa)throw causa;throw new AssertionError(e.getCause());}
    }
    private static String diagnosticoRetiro(CompositorPiezaV08 c){
        try{var m=CompositorPiezaV08.class.getDeclaredMethod("diagnosticoRetiro");m.setAccessible(true);return (String)m.invoke(c);}
        catch(ReflectiveOperationException e){throw new AssertionError("No se obtuvo el diagnóstico interno de retirada.",e);}
    }

    private static Map<String,Object> datosExclusivos(String etiqueta){
        var d=datos();@SuppressWarnings("unchecked")var i=(Map<String,Object>)d.get("inmueble");
        i.put("ubicacion",Map.of("distrito",dato(etiqueta),"zonaUrbanizacion",Map.of("estado","FALTANTE")));
        var pieza=new LinkedHashMap<>((Map<String,Object>)d.get("pieza"));pieza.put("id","TEST-"+etiqueta);d.put("pieza",pieza);return d;
    }
    private static void textoExclusivo(CompositorPieza.Binario b,String propio,String...ajenos)throws Exception{
        assertEquals("application/pdf",b.contentType());try(var pdf=Loader.loadPDF(b.contenido())){
            String texto=new PDFTextStripper().getText(pdf);assertTrue(texto.contains(propio));for(String ajeno:ajenos)assertFalse(texto.contains(ajeno));
        }
    }
    private static <T>T campo(Object instancia,String nombre,Class<T> tipo)throws Exception{
        var f=instancia.getClass().getDeclaredField(nombre);f.setAccessible(true);return tipo.cast(f.get(instancia));
    }
    private static JsonNode llamar(Object canal,String metodo,Map<String,?> parametros,String sesion)throws Exception{
        var m=canal.getClass().getDeclaredMethod("llamar",String.class,Map.class,String.class);m.setAccessible(true);
        try{return (JsonNode)m.invoke(canal,metodo,parametros,sesion);}
        catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof Exception causa)throw causa;throw new AssertionError(e.getCause());}
    }
    private static List<String> contextos(CompositorPiezaV08 c)throws Exception{
        var resultado=new ArrayList<String>();llamar(campo(c,"cdp",Object.class),"Target.getBrowserContexts",Map.of(),null)
                .path("browserContextIds").forEach(n->resultado.add(n.asText()));return resultado;
    }
    private record Pagina(String contexto,String sesion){}
    private static Pagina pagina(Object canal)throws Exception{
        String contexto=llamar(canal,"Target.createBrowserContext",Map.of("disposeOnDetach",true),null).path("browserContextId").asText();
        String target=llamar(canal,"Target.createTarget",Map.of("url","about:blank","browserContextId",contexto),null).path("targetId").asText();
        String sesion=llamar(canal,"Target.attachToTarget",Map.of("targetId",target,"flatten",true),null).path("sessionId").asText();
        llamar(canal,"Runtime.enable",Map.of(),sesion);return new Pagina(contexto,sesion);
    }
    @FunctionalInterface private interface Condicion{boolean comprobar()throws Exception;}
    private static void esperarCondicion(Condicion condicion)throws Exception{
        long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(!condicion.comprobar()){
            if(System.nanoTime()>=fin)fail("No se alcanzó la condición de coordinación real.");
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }
    /** Sólo retiene la primera serialización de la proyección, después de abrir y cargar su contexto real. */
    private static final class BarreraProyecciones extends ObjectMapper{
        private final Map<Object,CountDownLatch> salidas=new IdentityHashMap<>();
        private final Set<Object> vistas=Collections.newSetFromMap(new IdentityHashMap<>());
        private final CountDownLatch entradas;
        BarreraProyecciones(Object...datos){entradas=new CountDownLatch(datos.length);for(Object d:datos)salidas.put(d,new CountDownLatch(1));}
        @Override public String writeValueAsString(Object dato)throws com.fasterxml.jackson.core.JsonProcessingException{
            CountDownLatch salida=salidas.get(dato);boolean primera;
            synchronized(vistas){primera=salida!=null&&vistas.add(dato);}
            if(primera){entradas.countDown();try{if(!salida.await(45,TimeUnit.SECONDS))throw new IllegalStateException("No se liberó la proyección TEST.");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Coordinación TEST interrumpida.",e);}}
            return super.writeValueAsString(dato);
        }
        void esperarEntradas()throws InterruptedException{assertTrue(entradas.await(30,TimeUnit.SECONDS),"Ambos trabajos deben llegar al navegador real; no basta encolarlos.");}
        void soltar(Object dato){salidas.get(dato).countDown();}
        void soltarTodos(){salidas.values().forEach(CountDownLatch::countDown);}
    }
    private static void terminar(ExecutorService pool,BarreraProyecciones barrera,CompositorPiezaV08 c)throws Exception{
        barrera.soltarTodos();pool.shutdownNow();try{assertTrue(pool.awaitTermination(45,TimeUnit.SECONDS));}finally{c.cerrar();}
    }
    static Map<String,Object> datos(){
        var d=new LinkedHashMap<String,Object>();d.put("contrato","brox-ficha-datos");d.put("versionContrato","1.12.0");
        d.put("procedencias",Map.of());
        d.put("pieza",Map.of("id","TEST-C6","encargo","1","versionContenido",1,"plantilla","BROX-FICHA",
                "versionPlantilla","V08","naturaleza","FUENTE_REAL","salida","REVISION_PRIVADA","fechaCorte","2026-10-31","versionesOrigen",List.of()));
        var inm=new LinkedHashMap<String,Object>();inm.put("tipo",dato("D"));
        inm.put("ubicacion",Map.of("distrito",dato("Miraflores"),"zonaUrbanizacion",Map.of("estado","FALTANTE")));
        inm.put("atributos",Map.of());inm.put("fotos",List.of());inm.put("fueraDeCatalogo",List.of());d.put("inmueble",inm);
        d.put("encargo",Map.of("id","1","version",1,"estado","EN_PREPARACION","vigenteHasta","2026-12-31",
                "operacion",dato("VENTA"),"precio",Map.of("importe",dato(150000),"moneda",dato("USD"),"hito","U"),"condiciones",Map.of()));
        d.put("marcaYContacto",Map.of("estado","FALTANTE"));d.put("presentacion",Map.of("perfil","departamento"));return d;
    }
    private static Map<String,Object> dato(Object valor){return Map.of("estado","VIGENTE","valor",valor,"procedencia",Map.of("registradoEn","2026-10-31"));}
    private static void conservar(String nombre,byte[] bytes)throws Exception{
        String salida=System.getProperty("test.piezas.salida");if(salida==null)return;
        var carpeta=Path.of(salida);java.nio.file.Files.createDirectories(carpeta);java.nio.file.Files.write(carpeta.resolve(nombre),bytes);
    }
    private static class SesionCompositor implements AutoCloseable{
        final CompositorPiezaV08 compositor=new CompositorPiezaV08(chrome(),new ObjectMapper().findAndRegisterModules());
        public void close(){compositor.cerrar();}
    }
}
