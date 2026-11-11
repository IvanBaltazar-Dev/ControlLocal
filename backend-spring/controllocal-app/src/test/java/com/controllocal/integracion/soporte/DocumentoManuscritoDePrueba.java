package com.controllocal.integracion.soporte;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Productor unico SOLO TEST de App/CLI; no autentica ni reproduce firmas de personas reales. */
public final class DocumentoManuscritoDePrueba {
    private DocumentoManuscritoDePrueba() { }

    /** Acta sintética común para App/HTTP; no acredita un hecho externo real. */
    public static byte[] acta(String titulo,long encargo,String detalle,boolean firmaDeclarada) {
        if(titulo==null||!titulo.toUpperCase(Locale.ROOT).contains("TEST")||encargo<=0||detalle==null)
            throw new IllegalArgumentException("Aporta título TEST, contexto y detalle ficticios explícitos.");
        try(var d=new PDDocument();var salida=new ByteArrayOutputStream()){
            var pagina=new PDPage();d.addPage(pagina);
            try(var texto=new PDPageContentStream(d,pagina)){
                texto.beginText();texto.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),11);texto.newLineAtOffset(36,750);
                texto.showText(titulo);texto.newLineAtOffset(0,-24);texto.showText("EVIDENCIA DE PRUEBA SINTETICA TEST - SIN VALOR LEGAL O BANCARIO");
                texto.newLineAtOffset(0,-24);texto.showText("Contexto inmobiliario ficticio TEST: Encargo "+encargo);
                String linea="";
                for(String palabra:detalle.split("\\s+")){
                    if(!linea.isEmpty()&&linea.length()+palabra.length()+1>80){texto.newLineAtOffset(0,-20);texto.showText(linea);linea="";}
                    linea=linea.isEmpty()?palabra:linea+" "+palabra;
                }
                if(!linea.isEmpty()){texto.newLineAtOffset(0,-20);texto.showText(linea);}texto.endText();
            }
            d.save(salida);return firmaDeclarada?transformar(salida.toByteArray(),List.of("Firmante TEST 1")):salida.toByteArray();
        }catch(IOException error){throw new IllegalArgumentException("No se pudo preparar el acta sintética TEST.",error);}
    }

    /** Conserva las paginas originales; los nombres deben ser etiquetas ficticias marcadas TEST. */
    public static byte[] transformar(byte[] original,List<String> nombresTEST) {
        Objects.requireNonNull(original,"PDF original TEST requerido");
        if(nombresTEST==null||nombresTEST.isEmpty()
                ||nombresTEST.stream().anyMatch(n->n==null||n.isBlank()||!n.toUpperCase(Locale.ROOT).contains("TEST")))
            throw new IllegalArgumentException("Aporta etiquetas de firmantes ficticios con marcador TEST.");
        try(var pdf=Loader.loadPDF(original);var salida=new ByteArrayOutputStream()){
            if(pdf.getNumberOfPages()==0)throw new IllegalArgumentException("El original debe ser un PDF real.");
            var fuente=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for(var nombre:nombresTEST){
                var pagina=new PDPage(PDRectangle.A4);pdf.addPage(pagina);
                try(var dibujo=new PDPageContentStream(pdf,pagina)){
                    texto(dibujo,fuente,52,780,"INSTRUMENTO SINTETICO DE FIRMA - SOLO TEST");
                    texto(dibujo,fuente,52,750,"No certifica ni reproduce una firma de persona real.");
                    texto(dibujo,fuente,52,700,nombre);
                    dibujo.setLineWidth(1.6f);dibujo.moveTo(70,520);
                    dibujo.curveTo(110,585,82,455,148,544);dibujo.curveTo(170,570,130,478,205,535);
                    dibujo.curveTo(255,570,215,490,305,542);dibujo.curveTo(340,560,320,510,440,532);dibujo.stroke();
                    texto(dibujo,fuente,52,475,"Trazo ficticio controlado; los terminos originales se conservan en las paginas anteriores.");
                }
            }
            pdf.save(salida);return salida.toByteArray();
        }catch(IOException e){throw new IllegalArgumentException("No se pudo preparar el instrumento PDF de ensayo.",e);}
    }

    /** CLI: original.pdf salida-test.pdf "Firmante TEST 1" ["Firmante TEST 2" ...]. */
    public static void main(String[] args)throws IOException {
        if(args.length>0&&"--acta".equals(args[0])) {
            if(args.length!=6)throw new IllegalArgumentException("Uso: --acta encargo tituloTEST detalle salida.pdf true|false");
            if(!List.of("true","false").contains(args[5]))throw new IllegalArgumentException("Declara explícitamente la firma TEST.");
            byte[] acta=acta(args[2],Long.parseLong(args[1]),args[3],Boolean.parseBoolean(args[5]));
            Files.write(Path.of(args[4]).toAbsolutePath().normalize(),acta,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            System.out.println("ACTA_SINTETICA_TEST_OK bytes="+acta.length);return;
        }
        if(args.length<3)throw new IllegalArgumentException("Uso: original.pdf salida-test.pdf nombreTEST1 [nombreTEST2 ...]");
        Path original=Path.of(args[0]).toAbsolutePath().normalize();
        Path salida=Path.of(args[1]).toAbsolutePath().normalize();
        if(original.equals(salida))throw new IllegalArgumentException("La salida TEST no puede reemplazar el original.");
        byte[] transformado=transformar(Files.readAllBytes(original),Arrays.asList(args).subList(2,args.length));
        // CREATE_NEW conserva el original y evita sobrescribir archivos o enlaces preexistentes.
        Files.write(salida,transformado,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        System.out.println("PDF_MANUSCRITO_TEST_OK bytes="+transformado.length);
    }

    private static void texto(PDPageContentStream pagina,PDType1Font fuente,float x,float y,String valor)throws IOException {
        pagina.beginText();pagina.setFont(fuente,10);pagina.newLineAtOffset(x,y);pagina.showText(valor);pagina.endText();
    }
}
