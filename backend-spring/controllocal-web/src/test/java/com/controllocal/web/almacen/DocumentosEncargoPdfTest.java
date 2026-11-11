package com.controllocal.web.almacen;

import com.controllocal.service.encargo.DocumentosEncargo;
import com.controllocal.service.planes.ClasificadorDocumentosEncargoC6;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import com.controllocal.service.excepcion.ConflictoException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentosEncargoPdfTest {
    private ClasificadorDocumentosEncargoC6 clasificador(){
        var c=mock(ClasificadorDocumentosEncargoC6.class);
        when(c.preparar(any())).thenAnswer(i->{DocumentosEncargo.Origen o=i.getArgument(0);
            return new UsoAlmacenamientoB0.Recurso(o.tipoRecurso(),o.referencia(),o.finalidad(),"d".repeat(64));});
        return c;
    }
    private DocumentosEncargo.Origen origen(){return new DocumentosEncargo.Origen(UUID.randomUUID(),new Actor(17,18,19,Actor.AGENTE),
            new ContextoSesion("TEST",Long.MAX_VALUE,"SID-TEST",1),"DOCUMENTO_ENCARGO_C6",UUID.randomUUID().toString(),"PDF_ENCARGO","a".repeat(64));}
    @Test void conservaUnicodeYPaginaElDocumentoRealConTextoLegible() throws Exception {
        var almacen=mock(AlmacenDocumentos.class);var bytes=new AtomicReference<byte[]>();
        var salidas=mock(SalidaBinariaPersistente.class);var cuota=mock(CuotaArchivosB0.class);var origen=origen();
        when(cuota.admision(any())).thenReturn(i->{}); // Sólo composición PDF; B0 real tiene su suite PG.
        when(salidas.recuperar(any(com.controllocal.service.archivo.PropietarioBinario.class),any(),anyString(),any())).thenReturn(new SalidaBinariaPersistente.Consulta(SalidaBinariaPersistente.EstadoConsulta.NO_INICIADO,null));
        when(salidas.guardar(eq(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(17)),eq(origen.operacion()),anyString(),eq("pdf"),eq("application/pdf"),any(),any()))
                .thenAnswer(i->{byte[] b=i.getArgument(5);bytes.set(b);return new SalidaBinariaPersistente.Guardado(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(17),origen.operacion(),1,"b".repeat(64),"tenant/17/salidas/doc/1.pdf",EvaluacionTecnicaFoto.huella(b),b.length);});
        var campos=new ArrayList<DocumentosEncargo.Campo>();
        campos.add(new DocumentosEncargo.Campo("Titular","María Muñoz — Ñaña"));
        campos.add(new DocumentosEncargo.Campo("Área construida (m²)","90"));
        campos.add(new DocumentosEncargo.Campo("Condición de comisión","3 % del precio final acordado"));
        campos.add(new DocumentosEncargo.Campo("Unidad","Av. Perú 123 · departamento 302"));
        campos.add(new DocumentosEncargo.Campo("Uso ofrecido","Vivienda"));
        campos.add(new DocumentosEncargo.Campo("Garantía (meses)","2"));
        campos.add(new DocumentosEncargo.Campo("Precio pedido de alquiler","2500 PEN / mes"));
        campos.add(new DocumentosEncargo.Campo("Precio por cochera adicional","PEN 350"));
        for(int i=0;i<100;i++)campos.add(new DocumentosEncargo.Campo("Condición declarada",("Descripción extensa para comprobar ajuste y conservación de la fuente. ").repeat(4)));
        var resultado=new DocumentosEncargoPdf(almacen,salidas,cuota,clasificador()).generar(origen,new DocumentosEncargo.Contenido("Encargo para revisión",campos,List.of("Precisar la vigencia.")));
        assertEquals("application/pdf",resultado.contentType());assertEquals(64,resultado.huella().length());
        try(var pdf=Loader.loadPDF(bytes.get())) {
            assertTrue(pdf.getNumberOfPages()>1);String texto=new PDFTextStripper().getText(pdf);
            assertTrue(texto.contains("María Muñoz"));assertTrue(texto.contains("Área construida (m²)"));
            assertTrue(texto.contains("3 % del precio final acordado"));assertTrue(texto.contains("departamento 302"));
            assertTrue(texto.contains("Uso ofrecido"));assertTrue(texto.contains("Garantía (meses)"));assertTrue(texto.contains("Precio por cochera adicional"));assertTrue(texto.contains("PEN 350"));assertTrue(texto.contains("2500 PEN / mes"));
            assertTrue(texto.contains("Precisar la vigencia."));assertTrue(texto.contains("Documento preparado para revisión y aceptación de las partes."));
            Path salida=Path.of("target","encargo-pdf-qa");Files.createDirectories(salida);Files.write(salida.resolve("encargo-revision.pdf"),bytes.get());
            ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0,110),"png",salida.resolve("pagina-1.png").toFile());
            ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(pdf.getNumberOfPages()-1,110),"png",salida.resolve("pagina-final.png").toFile());
        }
    }
    @Test void recuperaElDocumentoExactoSinRecomponerNiEscribirOtraVez() {
        var almacen=mock(AlmacenDocumentos.class);var salidas=mock(SalidaBinariaPersistente.class);var cuota=mock(CuotaArchivosB0.class);var origen=origen();
        when(cuota.admision(any())).thenReturn(i->{});
        var guardado=new SalidaBinariaPersistente.Guardado(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(17),origen.operacion(),1,"b".repeat(64),"clave-exacta","c".repeat(64),200);
        when(salidas.recuperar(any(com.controllocal.service.archivo.PropietarioBinario.class),any(),anyString(),any())).thenReturn(new SalidaBinariaPersistente.Consulta(SalidaBinariaPersistente.EstadoConsulta.DISPONIBLE,guardado));
        var resultado=new DocumentosEncargoPdf(almacen,salidas,cuota,clasificador()).generar(origen,new DocumentosEncargo.Contenido("No recomponer",List.of(),List.of()));
        assertEquals("clave-exacta",resultado.claveProveedor());assertEquals("c".repeat(64),resultado.huella());
        verify(cuota).confirmar(any(),eq(guardado));verify(salidas,never()).guardar(any(com.controllocal.service.archivo.PropietarioBinario.class),any(),anyString(),anyString(),anyString(),any(),any());
        verifyNoInteractions(almacen);
    }
    @Test void incertidumbreNoSeConvierteEnOtroPdfNiExito() {
        var almacen=mock(AlmacenDocumentos.class);var salidas=mock(SalidaBinariaPersistente.class);var cuota=mock(CuotaArchivosB0.class);
        when(cuota.admision(any())).thenReturn(i->{});
        when(salidas.recuperar(any(com.controllocal.service.archivo.PropietarioBinario.class),any(),anyString(),any())).thenReturn(new SalidaBinariaPersistente.Consulta(SalidaBinariaPersistente.EstadoConsulta.EN_VERIFICACION,null));
        assertThrows(ConflictoException.class,()->new DocumentosEncargoPdf(almacen,salidas,cuota,clasificador()).generar(origen(),new DocumentosEncargo.Contenido("Pendiente",List.of(),List.of())));
        verify(salidas,never()).guardar(any(com.controllocal.service.archivo.PropietarioBinario.class),any(),anyString(),anyString(),anyString(),any(),any());
    }
    @Test void denegacionDeCuotaConservaSuClasificacionYNoLimpiaACiegas() {
        var almacen=mock(AlmacenDocumentos.class);var salidas=mock(SalidaBinariaPersistente.class);var cuota=mock(CuotaArchivosB0.class);
        when(cuota.admision(any())).thenReturn(i->{});
        when(salidas.guardar(any(com.controllocal.service.archivo.PropietarioBinario.class),any(),anyString(),anyString(),anyString(),any(),any())).thenThrow(new CapacidadDeshabilitadaException());
        assertThrows(CapacidadDeshabilitadaException.class,()->new DocumentosEncargoPdf(almacen,salidas,cuota,clasificador()).guardar(origen(),"titulo.txt","text/plain",new byte[]{1,2,3}));
        verifyNoInteractions(almacen);verify(cuota,never()).confirmar(any(),any());
    }
}
