package com.controllocal.service.documentos;

import com.controllocal.service.excepcion.ReglaNegocioException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.IIOImage;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class EvaluacionTecnicaFotoTest {
    private final EvaluacionTecnicaFoto evaluador=new EvaluacionTecnicaFoto();
    @Test void conservaOriginalYEliminaMetadatosDeLaCopia() throws Exception {
        byte[] original=pngConTexto();byte[] antes=original.clone();
        var copia=evaluador.evaluar(original);
        assertArrayEquals(antes,original);
        assertTrue(new String(original,StandardCharsets.ISO_8859_1).contains("GPS-PRIVADO-PRUEBA"));
        assertFalse(new String(copia.contenido(),StandardCharsets.ISO_8859_1).contains("GPS-PRIVADO-PRUEBA"));
        assertFalse(new String(copia.contenido(),StandardCharsets.ISO_8859_1).contains("tEXt"));
        assertEquals(EvaluacionTecnicaFoto.huella(original),copia.evaluacion().huellaOriginal());
        assertEquals(EvaluacionTecnicaFoto.huella(copia.contenido()),copia.huella());
    }
    @Test void midePixelesSinInventarPuntuacionNiAnalisisIa() throws Exception {
        var copia=evaluador.evaluar(imagen(12,8,"png",0xff808080));
        assertEquals(12,copia.evaluacion().anchoOriginal());assertEquals(8,copia.evaluacion().altoOriginal());
        assertEquals("HORIZONTAL",copia.evaluacion().orientacion());
        assertEquals(128,copia.evaluacion().luminanciaMedia());
        assertEquals("FOTO-LOCAL-1",copia.evaluacion().versionProceso());
    }
    @Test void orientaJpegSegunExifSinExponerElExif() throws Exception {
        byte[] jpg=imagen(12,8,"jpeg",0xff558811);
        byte[] exif={ (byte)255,(byte)225,0,34,'E','x','i','f',0,0,'I','I',42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0 };
        var bytes=new ByteArrayOutputStream();bytes.write(jpg,0,2);bytes.write(exif);bytes.write(jpg,2,jpg.length-2);
        var copia=evaluador.evaluar(bytes.toByteArray());
        assertEquals(6,copia.evaluacion().orientacionExif());
        assertEquals(8,copia.evaluacion().anchoCopia());assertEquals(12,copia.evaluacion().altoCopia());
        assertFalse(new String(copia.contenido(),StandardCharsets.ISO_8859_1).contains("Exif"));
        assertEquals("PNG",ImageIO.getImageReaders(ImageIO.createImageInputStream(new ByteArrayInputStream(copia.contenido()))).next().getFormatName().toUpperCase());
    }
    @Test void limitaDecodificacionDeLaCopiaSinCambiarResolucionOriginal() throws Exception {
        var copia=evaluador.evaluar(imagen(3201,7,"png",0xff224488));
        assertEquals(3201,copia.evaluacion().anchoOriginal());
        assertTrue(copia.evaluacion().anchoCopia()<=1600);assertTrue(copia.evaluacion().altoCopia()<=1600);
    }
    @Test void noAceptaUnaFirmaPngSinImagenCompleta() {
        assertThrows(ReglaNegocioException.class,()->evaluador.evaluar(new byte[]{(byte)137,80,78,71,13,10,26,10}));
        assertThrows(ReglaNegocioException.class,()->evaluador.evaluar(new byte[]{(byte)255,(byte)216,(byte)255,0}));
        assertThrows(ReglaNegocioException.class,()->evaluador.evaluar(new byte[0]));
        assertThrows(ReglaNegocioException.class,()->evaluador.evaluar(null));
    }
    @Test void resultadoEsDefensivoYNoSerializaBytesPrivados() throws Exception {
        var copia=evaluador.evaluar(imagen(2,2,"png",0xff123456));byte[] salida=copia.contenido();salida[0]=0;
        assertNotEquals(0,copia.contenido()[0]);
        String json=new ObjectMapper().writeValueAsString(copia);
        assertFalse(json.contains("contenido"));assertFalse(json.contains("proveedor"));
    }
    @Test void rechazaAnchoAdversarioAntesDeReservarFilasDelDecoder() throws Exception {
        byte[] png=imagen(1,1,"png",0xff000000);
        // IHDR correcto y CRC recalculado, dimensión hostil sin construir el raster enorme.
        png[16]=0x77;png[17]=0x35;png[18]=(byte)0x94;png[19]=0;
        var crc=new java.util.zip.CRC32();crc.update(png,12,17);long valor=crc.getValue();
        for(int i=0;i<4;i++)png[29+i]=(byte)(valor>>>(24-8*i));
        var error=assertThrows(ReglaNegocioException.class,()->new EvaluacionTecnicaFoto(1024*1024).evaluar(png));
        assertTrue(error.getMessage().contains("segura"));
    }
    @Test void presupuestoTecnicoNoSobrescribeOriginalNiInventaCuotaComercial() throws Exception {
        byte[] png=imagen(100,100,"png",0xff228844);byte[] original=png.clone();
        assertThrows(ReglaNegocioException.class,()->new EvaluacionTecnicaFoto(100).evaluar(png));
        assertArrayEquals(original,png);
        assertNotNull(new EvaluacionTecnicaFoto(1_000_000).evaluar(png));
    }
    private static byte[] imagen(int w,int h,String formato,int color) throws Exception {
        var imagen=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)imagen.setRGB(x,y,color);
        var out=new ByteArrayOutputStream();assertTrue(ImageIO.write(imagen,formato,out));return out.toByteArray();
    }
    private static byte[] pngConTexto() throws Exception {
        var imagen=new BufferedImage(6,4,BufferedImage.TYPE_INT_RGB);
        var escritor=ImageIO.getImageWritersByFormatName("png").next();var p=escritor.getDefaultWriteParam();
        var meta=escritor.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(imagen),p);
        var root=new IIOMetadataNode("javax_imageio_png_1.0");var textos=new IIOMetadataNode("tEXt");
        var texto=new IIOMetadataNode("tEXtEntry");texto.setAttribute("keyword","Comment");texto.setAttribute("value","GPS-PRIVADO-PRUEBA");
        textos.appendChild(texto);root.appendChild(textos);meta.mergeTree("javax_imageio_png_1.0",root);
        var out=new ByteArrayOutputStream();try(var s=new MemoryCacheImageOutputStream(out)){
            escritor.setOutput(s);escritor.write(null,new IIOImage(imagen,null,meta),p);
        }finally{escritor.dispose();}return out.toByteArray();
    }
}
