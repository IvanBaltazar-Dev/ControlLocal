package com.controllocal.app.plataforma;

import com.controllocal.service.plataforma.AcreditacionesTitularPlataforma;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Fuente administrativa privada CAT-R25/contrato351; no registra ni concede atribuciones. */
@Component
@ConditionalOnProperty(name="controllocal.plataforma.acreditaciones-archivo.habilitada",havingValue="true",matchIfMissing=false)
public final class AcreditacionesTitularArchivo implements AcreditacionesTitularPlataforma {
    // Presupuesto técnico de lectura/árbol en memoria; no cuota comercial ni número de decisiones.
    static final int MAX_BYTES=4*1024*1024;
    private static final Set<String> DOCUMENTO=Set.of("version","decisiones");
    private static final Set<String> DECISION=Set.of("referencia","otorgante","destinatario","actuacion",
            "huellaContenido","evidencia","acreditadaEn");
    private final String ruta;
    private final ObjectMapper json;

    public AcreditacionesTitularArchivo(@Value("${controllocal.plataforma.acreditaciones-archivo.ruta:}") String ruta) {
        this.ruta=ruta;
        var factory=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                        .maxStringLength(MAX_BYTES).build()).build();
        this.json=new ObjectMapper(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override
    public Optional<Decision> acreditar(UUID referencia) {
        try {
            if(referencia==null||ruta==null||ruta.isBlank()||ruta.startsWith("\\\\")||ruta.startsWith("//"))throw fallo();
            var p=Path.of(ruta);
            if(!p.isAbsolute()||p.toUri().getAuthority()!=null)throw fallo();
            p=p.normalize();
            // No seguir enlaces en el archivo ni en sus directorios de acceso.
            Path parte=p.getRoot();
            for(Path segmento:p) {
                parte=parte.resolve(segmento);
                var a=Files.readAttributes(parte,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(a.isSymbolicLink()||a.isOther()||(!parte.equals(p)&&!a.isDirectory()))throw fallo();
            }
            var antes=regular(p);
            if(antes.size()>MAX_BYTES)throw fallo();
            byte[] bytes;
            try(var canal=Files.newByteChannel(p,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS));
                var entrada=Channels.newInputStream(canal)) {
                if(canal.size()>MAX_BYTES)throw fallo();
                bytes=entrada.readNBytes(MAX_BYTES+1);
            }
            var despues=regular(p);
            if(bytes.length>MAX_BYTES||bytes.length!=antes.size()||antes.size()!=despues.size()
                    ||!antes.lastModifiedTime().equals(despues.lastModifiedTime())
                    ||!java.util.Objects.equals(antes.fileKey(),despues.fileKey()))throw fallo();
            var documento=json.readTree(bytes);
            campos(documento,DOCUMENTO);
            var version=documento.get("version");
            if(!version.isIntegralNumber()||!version.canConvertToInt()||version.intValue()!=1)throw fallo();
            var decisiones=documento.get("decisiones");
            if(!decisiones.isArray())throw fallo();
            var vistas=new HashSet<UUID>();
            Decision encontrada=null;
            for(var d:decisiones) {
                campos(d,DECISION);
                var id=UUID.fromString(texto(d,"referencia"));
                if(!vistas.add(id))throw fallo();
                var destinatario=d.get("destinatario");
                var decision=new Decision(id,entero(d.get("otorgante")),
                        destinatario.isNull()?null:entero(destinatario),texto(d,"actuacion"),
                        texto(d,"huellaContenido"),texto(d,"evidencia"),Instant.parse(texto(d,"acreditadaEn")));
                if(id.equals(referencia))encontrada=decision;
            }
            return Optional.ofNullable(encontrada);
        } catch(IOException|RuntimeException error) {
            // Ni la ruta ni el contenido ni la excepción del parser salen a API/log/cadena de causas.
            throw fallo();
        }
    }

    private static BasicFileAttributes regular(Path p) throws IOException {
        var a=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!a.isRegularFile()||a.isSymbolicLink()||a.isOther())throw fallo();
        return a;
    }
    private static void campos(JsonNode n,Set<String> esperados) {
        if(n==null||!n.isObject()||n.size()!=esperados.size())throw fallo();
        var nombres=n.fieldNames();
        while(nombres.hasNext())if(!esperados.contains(nombres.next()))throw fallo();
    }
    private static long entero(JsonNode n) {
        if(n==null||!n.isIntegralNumber()||!n.canConvertToLong())throw fallo();
        return n.longValue();
    }
    private static String texto(JsonNode n,String nombre) {
        var v=n.get(nombre);
        if(v==null||!v.isTextual())throw fallo();
        return v.textValue();
    }
    private static IllegalStateException fallo() {
        return new IllegalStateException("No se pudo verificar la fuente administrativa de acreditaciones.");
    }
}
