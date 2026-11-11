package com.controllocal.service.materialia.openai;

import com.controllocal.service.materialia.ProveedorMaterialIa;
import com.controllocal.service.materialia.MaterialIaService.Accion;
import com.controllocal.service.presupuesto.PresupuestoIaService.ComprobacionNoProcesamiento;
import com.controllocal.service.soporte.Idempotencia;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import com.controllocal.service.presupuesto.ObservacionHttpIa;

/** Adaptador de edición directa. C1/E44/B0/claim pertenecen al coordinador Core existente. */
public final class ProveedorEdicionOpenAi implements ProveedorMaterialIa {
    public static final String MODELO="gpt-image-2.5-sunburst";
    public static final String IDENTIFICADOR="OPENAI_IMAGES_EDITS_SUNBURST";
    public static final String PROCESO="OPENAI_IMAGES_EDITS";
    public static final URI DESTINO=URI.create("https://api.openai.com/v1/images/edits");
    /** Bean de configuración segura servidor. No propiedad del DTO, prompt o fuente comercial. */
    @FunctionalInterface public interface Credencial { String bearer(); }
    private final ConfiguracionEdicionOpenAi configuracion;private final Credencial credencial;
    private final TransporteOpenAi transporte;private final DecodificadorEdicionOpenAi decodificador;
    public ProveedorEdicionOpenAi(ConfiguracionEdicionOpenAi c,Credencial credencial,TransporteOpenAi transporte){
        configuracion=Objects.requireNonNull(c);this.credencial=Objects.requireNonNull(credencial);
        this.transporte=Objects.requireNonNull(transporte);decodificador=new DecodificadorEdicionOpenAi(c);
    }
    @Override public String identificador(){return IDENTIFICADOR;}
    @Override public boolean atiende(Accion accion,String perfil,String version){
        return accion==Accion.MEJORAR_FOTO&&configuracion.perfil().equals(perfil)&&configuracion.versionPerfil().equals(version);
    }
    @Override public Salida ejecutar(Peticion p,Consumer<ObservacionHttpIa> observador){
        Objects.requireNonNull(p);HttpRequest solicitud;
        try{solicitud=peticion(p);}
        catch(Exception noTransferida){throw noIniciada(p,noTransferida);}
        // A partir de entrar al transporte no se emite evidencia de no procesamiento.
        try{return decodificador.leer(p,transporte.enviar(solicitud,configuracion.envoltura().maximoRespuestaBytes(),configuracion.total(),
                new TransporteOpenAi.ContextoIntento(p.claveExterna(),IDENTIFICADOR,MODELO,configuracion.versionPerfil()),observador));}
        catch(InterruptedException interrupcion){Thread.currentThread().interrupt();throw new ProcesamientoIncierto();}
        catch(Exception desconocido){throw new ProcesamientoIncierto();}
    }
    private HttpRequest peticion(Peticion p)throws Exception{
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("El proveedor requiere entrada fuera de SQL.");
        if(p.accion()!=Accion.MEJORAR_FOTO||p.claveExterna()==null||!p.claveExterna().matches("[0-9a-f]{64}")
                ||p.instruccion()==null||p.instruccion().isBlank()||p.instruccion().length()>32000
                ||p.limiteRecepcion()==null||!configuracion.versionPerfil().equals(p.limiteRecepcion().versionFuente())
                ||p.limiteRecepcion().maximoBytesContenido()<=0||p.limiteRecepcion().maximoBytesContenido()>Integer.MAX_VALUE)
            throw new IllegalArgumentException("La petición no conserva su configuración y previsión originales.");
        byte[] copia=p.fotoAutorizada();
        if(copia==null||copia.length==0||copia.length>configuracion.envoltura().maximoEntradaBytes())
            throw new IllegalArgumentException("La copia autorizada excede su entrada acreditada.");
        ImagenEdicionOpenAi.exigirEntrada(copia,configuracion.parametros());
        String borde="brox-"+UUID.randomUUID();var partes=new ArrayList<HttpRequest.BodyPublisher>();
        agregar(partes,borde,"model",MODELO);agregar(partes,borde,"prompt",p.instruccion());
        var parametros=configuracion.parametros();
        agregar(partes,borde,"quality",parametros.calidad());agregar(partes,borde,"size",parametros.size());
        agregar(partes,borde,"output_format",parametros.formato());agregar(partes,borde,"background",parametros.fondo());
        if(parametros.compresion()!=null)agregar(partes,borde,"output_compression",parametros.compresion().toString());
        agregar(partes,borde,"n","1");agregar(partes,borde,"stream","false");agregar(partes,borde,"partial_images","0");
        partes.add(HttpRequest.BodyPublishers.ofString("--"+borde+"\r\nContent-Disposition: form-data; name=\"image[]\"; filename=\"fuente.png\"\r\nContent-Type: image/png\r\n\r\n",StandardCharsets.UTF_8));
        partes.add(HttpRequest.BodyPublishers.ofByteArray(copia));
        partes.add(HttpRequest.BodyPublishers.ofString("\r\n--"+borde+"--\r\n",StandardCharsets.UTF_8));
        // Ningún Snapshot, datosPermitidos, límite B0, namespace, identidad humana o clave de almacén se serializa.
        var cuerpo=TransporteHttpOpenAi.unaSuscripcion(HttpRequest.BodyPublishers.concat(partes.toArray(HttpRequest.BodyPublisher[]::new)));
        String token=credencial.bearer();if(token==null||token.isBlank()||!token.equals(token.trim())||token.contains("\r")||token.contains("\n"))
            throw new IllegalStateException("Falta la credencial servidor del destino autorizado.");
        return HttpRequest.newBuilder(DESTINO).timeout(configuracion.total()).header("Authorization","Bearer "+token)
                .header("Content-Type","multipart/form-data; boundary="+borde).header("Accept","application/json")
                .header("Accept-Encoding","identity").header("X-Client-Request-Id",p.claveExterna()).POST(cuerpo).build();
    }
    private static void agregar(List<HttpRequest.BodyPublisher> partes,String borde,String nombre,String valor){
        partes.add(HttpRequest.BodyPublishers.ofString("--"+borde+"\r\nContent-Disposition: form-data; name=\""+nombre+"\"\r\n\r\n"+valor+"\r\n",StandardCharsets.UTF_8));
    }
    private static NoIniciadoComprobado noIniciada(Peticion p,Exception causa){
        // Hecho local verificable: transporte todavía no fue invocado; no inferido de HTTP ni timeout.
        String huella=Idempotencia.huellaExacta("NO_TRANSFERIDA\u0000"+p.claveExterna()+"\u0000"+causa.getClass().getName());
        return new NoIniciadoComprobado(new ComprobacionNoProcesamiento(p.claveExterna(),IDENTIFICADOR,
                "VALIDACION_LOCAL:"+p.claveExterna(),"ADAPTADOR_ANTES_DE_TRANSPORTE:"+huella,huella,Instant.now()));
    }
    @Override public String toString(){return "ProveedorEdicionOpenAi[configuración privada]";}
}
