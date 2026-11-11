package com.controllocal.service.materialia.openai;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.util.Locale;

/** Comprueba formato/geometría; no recorta, reescala, reorienta ni modifica ningún original. */
final class ImagenEdicionOpenAi {
    private ImagenEdicionOpenAi(){}
    static void exigirEntrada(byte[] png,ConfiguracionEdicionOpenAi.Parametros parametros)throws IOException{
        examinar(png,"PNG",parametros,false);
    }
    static void exigirSalida(byte[] bytes,ConfiguracionEdicionOpenAi.Parametros parametros)throws IOException{
        examinar(bytes,"png".equals(parametros.formato())?"PNG":"JPEG",parametros,true);
    }
    private static void examinar(byte[] bytes,String formato,ConfiguracionEdicionOpenAi.Parametros p,boolean salida)throws IOException{
        if(bytes==null||bytes.length==0)throw invalida();
        try(var in=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var lectores=ImageIO.getImageReaders(in);if(!lectores.hasNext())throw invalida();var lector=lectores.next();
            try{
                lector.setInput(in,true,true);String f=lector.getFormatName().toUpperCase(Locale.ROOT);
                if(!formato.equals(f))throw invalida();int ancho=lector.getWidth(0),alto=lector.getHeight(0);
                // No impone igualdad de razón a las fuentes comerciales. La elección de size
                // pertenece al ensayo; encuadre/fidelidad se juzgan por QC y rúbrica Core.
                if(ancho<=0||alto<=0)throw invalida();
                if(salida){
                    // Salida vs size explícita del ensayo, no vs píxeles de la copia de entrada.
                    if(ancho!=p.ancho()||alto!=p.alto()||(long)ancho*alto>p.maximoPixelesDecodificados())throw invalida();
                    var imagen=lector.read(0);if(imagen==null||imagen.getWidth()!=ancho||imagen.getHeight()!=alto)throw invalida();
                }
            }finally{lector.dispose();}
        }
    }
    private static IOException invalida(){return new IOException("La imagen no conserva formato y dimensiones acreditados.");}
}
