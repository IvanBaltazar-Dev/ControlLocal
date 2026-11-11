package com.controllocal.web.almacen;

import com.controllocal.service.documentos.DocumentosReporteAvance;
import com.controllocal.service.encargo.DocumentosEncargo;
import com.controllocal.service.planes.ClasificadorReporteAvance;
import com.controllocal.service.archivo.PropietarioBinario;
import com.controllocal.service.excepcion.ConflictoException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.*;

/** Sólo I/O y composición técnica compartida; los campos y la autoridad proceden de Core. */
@Component
public class GeneradorReporteAvancePdf implements DocumentosReporteAvance {
    private final SalidaBinariaPersistente salidas;
    private final CuotaArchivosB0 cuota;
    private final ClasificadorReporteAvance clasificador;
    public GeneradorReporteAvancePdf(SalidaBinariaPersistente s,CuotaArchivosB0 q,ClasificadorReporteAvance c){salidas=s;cuota=q;clasificador=c;}
    public DocumentosEncargo.Guardado generar(Origen o,DocumentosEncargo.Contenido c){
        var contexto=new CuotaArchivosB0.Contexto(clasificador.preparar(o),o.actor(),o.sesion());
        var propietario=new PropietarioBinario.Organizacion(o.actor().idOrganizacion());String fuente="REPORTE-CORE-C7:"+o.reporte().referencia()+":"+o.reporte().version()+":"+o.huellaOrigen();
        var previo=salidas.recuperar(propietario,o.operacion(),fuente,cuota.admision(contexto));
        if(previo.estado()==SalidaBinariaPersistente.EstadoConsulta.EN_VERIFICACION)throw new ConflictoException("El reporte sigue en verificación; no se repetirá su escritura a ciegas.");
        SalidaBinariaPersistente.Guardado g=previo.guardado();
        if(g==null){
            try(PDDocument pdf=new PDDocument();var font=getClass().getResourceAsStream("/encargo/Vera.ttf");var salida=new ByteArrayOutputStream()){
                if(font==null)throw new IllegalStateException("Falta la fuente de documentos privados.");
                var lineas=new ArrayList<String>();lineas.add(c.titulo());lineas.add("");for(var campo:c.campos())lineas.add(campo.rotulo()+": "+campo.valor());
                lineas.addAll(c.faltantes());DocumentosEncargoPdf.escribir(pdf,PDType0Font.load(pdf,font,true),lineas);pdf.save(salida);
                g=salidas.guardar(propietario,o.operacion(),fuente,"pdf","application/pdf",salida.toByteArray(),cuota.admision(contexto));
            }catch(IOException e){throw new IllegalStateException("No se pudo preparar el reporte PDF.",e);}
        }
        cuota.confirmar(contexto,g);return new DocumentosEncargo.Guardado(g.clave(),"reporte-avance.pdf","application/pdf",g.huella());
    }
}
