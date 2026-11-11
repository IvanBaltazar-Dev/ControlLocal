package com.controllocal.web.almacen;

import com.controllocal.service.encargo.DocumentosEncargo;
import com.controllocal.service.planes.ClasificadorDocumentosEncargoC6;
import com.controllocal.service.excepcion.ConflictoException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Adaptador PDF privado. Conserva texto, versión y faltantes que ya resolvió Core. */
@Component
public final class DocumentosEncargoPdf implements DocumentosEncargo {
    private final AlmacenDocumentos almacen;
    private final SalidaBinariaPersistente salidas;
    private final CuotaArchivosB0 cuota;
    private final ClasificadorDocumentosEncargoC6 clasificador;
    public DocumentosEncargoPdf(AlmacenDocumentos almacen,SalidaBinariaPersistente salidas,CuotaArchivosB0 cuota,
            ClasificadorDocumentosEncargoC6 clasificador) {
        this.almacen=almacen;this.salidas=salidas;this.cuota=cuota;this.clasificador=clasificador;
    }

    @Override public Guardado generar(Origen origen, Contenido contenido) {
        Objects.requireNonNull(contenido);
        var contexto=contexto(origen);
        var anterior=salidas.recuperar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(origen.actor().idOrganizacion()),origen.operacion(),fuente(origen),cuota.admision(contexto));
        if(anterior.estado()==SalidaBinariaPersistente.EstadoConsulta.EN_VERIFICACION)
            throw new ConflictoException("El documento sigue en verificación; no se repetirá su composición ni escritura a ciegas.");
        if(anterior.guardado()!=null){
            cuota.confirmar(contexto,anterior.guardado());
            return new Guardado(anterior.guardado().clave(),"encargo.pdf","application/pdf",anterior.guardado().huella());
        }
        try(PDDocument pdf=new PDDocument();
            var fuente=getClass().getResourceAsStream("/encargo/Vera.ttf");
            ByteArrayOutputStream salida=new ByteArrayOutputStream()) {
            if(fuente==null) throw new IllegalStateException("Falta la fuente del documento privado.");
            PDType0Font tipo=PDType0Font.load(pdf,fuente,true);
            List<String> parrafos=new ArrayList<>();
            parrafos.add(contenido.titulo()); parrafos.add("");
            for(Campo campo:contenido.campos())
                parrafos.add(campo.rotulo()+": "+campo.valor());
            if(!contenido.faltantes().isEmpty()) {
                parrafos.add(""); parrafos.add("Datos pendientes para la siguiente actuación:");
                parrafos.addAll(contenido.faltantes());
            }
            parrafos.add("");
            parrafos.add("Documento preparado para revisión y aceptación de las partes.");
            escribir(pdf,tipo,parrafos);
            pdf.save(salida);
            return guardar(origen,contexto,"encargo.pdf","application/pdf",salida.toByteArray());
        } catch(IOException fallo) {
            throw new IllegalStateException("No se pudo preparar el PDF privado del Encargo.",fallo);
        }
    }

    static void escribir(PDDocument pdf,PDType0Font fuente,List<String> parrafos) throws IOException {
        PDPageContentStream tinta=null;
        float y=0;
        try {
            for(String parrafo:parrafos) {
                for(String linea:lineas(parrafo,fuente,10,490)) {
                    if(tinta==null || y<52) {
                        if(tinta!=null) tinta.close();
                        PDPage pagina=new PDPage(PDRectangle.A4); pdf.addPage(pagina);
                        tinta=new PDPageContentStream(pdf,pagina); y=790;
                    }
                    tinta.beginText(); tinta.setFont(fuente,10); tinta.newLineAtOffset(52,y);
                    tinta.showText(linea); tinta.endText(); y-=15;
                }
            }
        } finally { if(tinta!=null) tinta.close(); }
    }

    private static List<String> lineas(String texto,PDType0Font fuente,float tamano,float ancho) throws IOException {
        List<String> resultado=new ArrayList<>(); StringBuilder actual=new StringBuilder();
        String limpio=texto==null?"":texto.replace('\r',' ').replace('\n',' ').replace('\t',' ');
        for(String palabra:limpio.split(" +")) {
            String candidato=actual.isEmpty()?palabra:actual+" "+palabra;
            if(!actual.isEmpty() && fuente.getStringWidth(candidato)/1000*tamano>ancho) {
                resultado.add(actual.toString()); actual.setLength(0);
            }
            for(int punto:palabra.codePoints().toArray()) {
                String caracter=new String(Character.toChars(punto));
                if(!actual.isEmpty() && fuente.getStringWidth(actual+caracter)/1000*tamano>ancho) {
                    resultado.add(actual.toString()); actual.setLength(0);
                }
                actual.append(caracter);
            }
            actual.append(' ');
        }
        resultado.add(actual.toString().stripTrailing()); return resultado;
    }

    @Override public Guardado guardar(Origen origen,String nombre,String tipo,byte[] contenido) {
        var contexto=contexto(origen);
        return guardar(origen,contexto,nombre,tipo,contenido);
    }
    private Guardado guardar(Origen origen,CuotaArchivosB0.Contexto contexto,String nombre,String tipo,byte[] contenido) {
        if(contenido==null || contenido.length==0)
            throw new IllegalArgumentException("El binario necesita organización y contenido.");
        String extension=NombresArchivo.extension(nombre).replaceFirst("^\\.","");
        if(!java.util.Set.of("pdf","png","jpg","jpeg","txt","csv").contains(extension))extension="bin";
        var guardado=salidas.guardar(new com.controllocal.service.archivo.PropietarioBinario.Organizacion(origen.actor().idOrganizacion()),origen.operacion(),fuente(origen),extension,tipo,contenido,cuota.admision(contexto));
        cuota.confirmar(contexto,guardado);
        return new Guardado(guardado.clave(),nombre,tipo,guardado.huella());
    }
    private CuotaArchivosB0.Contexto contexto(Origen o){
        if(o==null||o.actor()==null||o.sesion()==null||o.operacion()==null)throw new IllegalArgumentException("La carga conserva el contexto Core de su actuación.");
        return new CuotaArchivosB0.Contexto(clasificador.preparar(o),o.actor(),o.sesion());
    }
    private static String fuente(Origen o){return "DOCUMENTO-CORE-C6:"+o.tipoRecurso()+":"+o.referencia()+":"+o.finalidad()+":"+o.huellaOrigen();}
    @Override public Optional<Binario> abrir(String clave) {
        return almacen.abrir(clave).map(a->new Binario(a.contenido(),a.nombre(),a.contentType()));
    }
}
