package com.controllocal.service.documentos;

import com.controllocal.service.excepcion.ReglaNegocioException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

/** CAT-R14/E33: observaciones técnicas locales; no evalúa el inmueble ni llama IA. */
@Component
public class EvaluacionTecnicaFoto {
    public static final String VERSION = "FOTO-LOCAL-1";
    // V08 §7.8: copia de trabajo hasta1600px; no restringe ni sustituye el original.
    private static final int LADO_COPIA = 1600;
    private final long presupuestoDecodificacion;
    /** Presupuesto técnico por proceso, no tamaño/quota comercial ni límite del original. */
    public EvaluacionTecnicaFoto(){this(Math.min(256L*1024*1024,Runtime.getRuntime().maxMemory()/8));}
    public EvaluacionTecnicaFoto(long presupuestoDecodificacion){
        if(presupuestoDecodificacion<=0)throw new IllegalArgumentException("El procesamiento necesita presupuesto de memoria.");
        this.presupuestoDecodificacion=presupuestoDecodificacion;
    }

    public record Evaluacion(String formato, int anchoOriginal, int altoOriginal,
            int orientacionExif, String orientacion, int anchoCopia, int altoCopia,
            double luminanciaMedia, String huellaOriginal, String versionProceso) { }
    public record Copia(Evaluacion evaluacion, String huella, String contentType,
                        @JsonIgnore byte[] contenido) {
        public Copia { contenido = contenido.clone(); }
        @Override @JsonIgnore public byte[] contenido() { return contenido.clone(); }
    }

    public synchronized Copia evaluar(byte[] original) {
        if (original == null || original.length == 0)
            throw new ReglaNegocioException("La fotografía está vacía.");
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) throw invalida();
            ImageReader lector = lectores.next();
            try {
                String formato = lector.getFormatName().toUpperCase(java.util.Locale.ROOT);
                if (!"PNG".equals(formato) && !"JPEG".equals(formato) && !"JPG".equals(formato))
                    throw invalida();
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0), alto = lector.getHeight(0);
                if (ancho <= 0 || alto <= 0) throw invalida();
                // El PNGImageReader reserva filas al ancho original aun con subsampling.
                // Estimar antes de read también acota buffers/coficientes de JPEG progresivo.
                try {
                    long estimada=Math.addExact(Math.multiplyExact(Math.multiplyExact((long)ancho,alto),8L),
                            Math.multiplyExact((long)ancho,32L));
                    if(estimada>presupuestoDecodificacion)throw memoria();
                }catch(ArithmeticException fueraDeRango){throw memoria();}
                int orientacion = "PNG".equals(formato) ? 1 : orientacionExif(original);
                // Muestrear ANTES de decodificar evita asignar todos los píxeles de una imagen enorme.
                int muestreo = (int)Math.max(1L, (Math.max((long)ancho, alto) + LADO_COPIA - 1) / LADO_COPIA);
                ImageReadParam parametros = lector.getDefaultReadParam();
                parametros.setSourceSubsampling(muestreo, muestreo, 0, 0);
                BufferedImage leida = lector.read(0, parametros);
                if (leida == null) throw invalida();
                BufferedImage depurada = orientar(leida, orientacion);
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                // Nueva codificación de píxeles, sin copiar IIOMetadata, EXIF, GPS, comentarios o miniaturas.
                if (!ImageIO.write(depurada, "png", salida)) throw new IOException("Codificador PNG no disponible.");
                byte[] copia = salida.toByteArray();
                var e = new Evaluacion("JPG".equals(formato) ? "JPEG" : formato, ancho, alto,
                        orientacion, depurada.getWidth() == depurada.getHeight() ? "CUADRADA" :
                        depurada.getWidth() > depurada.getHeight() ? "HORIZONTAL" : "VERTICAL",
                        depurada.getWidth(), depurada.getHeight(), luminancia(depurada),
                        huella(original), VERSION);
                return new Copia(e, huella(copia), "image/png", copia);
            } finally { lector.dispose(); }
        } catch (IOException | IllegalArgumentException ex) {
            throw invalida();
        }
    }

    private static BufferedImage orientar(BufferedImage fuente, int orientacion) {
        int w=fuente.getWidth(), h=fuente.getHeight();
        boolean intercambia=orientacion>=5 && orientacion<=8;
        BufferedImage salida=new BufferedImage(intercambia?h:w,intercambia?w:h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int dx=x,dy=y;
            switch(orientacion) {
                case 2 -> dx=w-1-x;
                case 3 -> {dx=w-1-x;dy=h-1-y;}
                case 4 -> dy=h-1-y;
                case 5 -> {dx=y;dy=x;}
                case 6 -> {dx=h-1-y;dy=x;}
                case 7 -> {dx=h-1-y;dy=w-1-x;}
                case 8 -> {dx=y;dy=w-1-x;}
                default -> { }
            }
            salida.setRGB(dx,dy,fuente.getRGB(x,y));
        }
        return salida;
    }

    private static double luminancia(BufferedImage imagen) {
        double total=0; long cuenta=(long)imagen.getWidth()*imagen.getHeight();
        for(int y=0;y<imagen.getHeight();y++)for(int x=0;x<imagen.getWidth();x++) {
            int p=imagen.getRGB(x,y);
            total+=0.2126*((p>>16)&255)+0.7152*((p>>8)&255)+0.0722*(p&255);
        }
        // Es una medición de los píxeles muestreados, no una puntuación ni diagnóstico de iluminación.
        return Math.round(total/cuenta*100.0)/100.0;
    }

    private static int orientacionExif(byte[] datos) {
        if(datos.length<4 || (datos[0]&255)!=255 || (datos[1]&255)!=216)return 1;
        int p=2;
        while(p+4<=datos.length) {
            if((datos[p++]&255)!=255)break;
            int marcador=datos[p++]&255;
            if(marcador==218 || marcador==217)break;
            if(marcador==0 || marcador==255 || marcador==1 || marcador>=208 && marcador<=215)continue;
            int largo=((datos[p]&255)<<8)|(datos[p+1]&255);
            if(largo<2 || (long)p+largo>datos.length)break;
            int inicio=p+2,fin=p+largo;
            if(marcador==225 && inicio+14<=fin && datos[inicio]=='E' && datos[inicio+1]=='x'
                    && datos[inicio+2]=='i' && datos[inicio+3]=='f' && datos[inicio+4]==0 && datos[inicio+5]==0) {
                int t=inicio+6; boolean le=datos[t]=='I' && datos[t+1]=='I';
                if(!le && !(datos[t]=='M' && datos[t+1]=='M'))return 1;
                if(u16(datos,t+2,le)!=42)return 1;
                long offset=u32(datos,t+4,le),dir=(long)t+offset;
                if(dir<t+8 || dir+2>fin)return 1;
                int n=u16(datos,(int)dir,le);
                if(dir+2L+12L*n>fin)return 1;
                for(int i=0;i<n;i++) {
                    int e=(int)dir+2+12*i;
                    if(u16(datos,e,le)==274 && u16(datos,e+2,le)==3 && u32(datos,e+4,le)==1) {
                        int valor=u16(datos,e+8,le);return valor>=1 && valor<=8?valor:1;
                    }
                }
            }
            p=fin;
        }
        return 1;
    }
    private static int u16(byte[] d,int p,boolean le){return le?(d[p]&255)|((d[p+1]&255)<<8):((d[p]&255)<<8)|(d[p+1]&255);}
    private static long u32(byte[] d,int p,boolean le){return le?(d[p]&255L)|((d[p+1]&255L)<<8)|((d[p+2]&255L)<<16)|((d[p+3]&255L)<<24):((d[p]&255L)<<24)|((d[p+1]&255L)<<16)|((d[p+2]&255L)<<8)|(d[p+3]&255L);}
    public static String huella(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private static ReglaNegocioException invalida(){return new ReglaNegocioException("No se pudo decodificar una fotografía PNG o JPG válida.");}
    private static ReglaNegocioException memoria(){return new ReglaNegocioException("La evaluación local no puede procesar esta resolución de forma segura; el original se conserva.");}
}
