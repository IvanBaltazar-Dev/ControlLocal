package com.controllocal.service.materialia.openai;

import com.controllocal.service.materialia.ProveedorMaterialIa;
import com.controllocal.service.materialia.MaterialIaService.Accion;
import com.controllocal.service.presupuesto.PresupuestoIaService.ComprobacionNoProcesamiento;
import com.controllocal.service.soporte.Idempotencia;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import com.controllocal.service.presupuesto.ObservacionHttpIa;

/** Sólo redacción/análisis solicitados. No modifica ficha, original, publicación, permiso o decisión QC. */
public final class ProveedorRespuestasOpenAi implements ProveedorMaterialIa {
    public static final String IDENTIFICADOR="OPENAI_RESPONSES_MATERIAL";
    public static final String PROCESO="OPENAI_RESPONSES";
    public static final URI DESTINO=URI.create("https://api.openai.com/v1/responses");
    private final ConfiguracionRespuestasOpenAi configuracion;
    private final ProveedorEdicionOpenAi.Credencial credencial;
    private final TransporteOpenAi transporte;
    public ProveedorRespuestasOpenAi(ConfiguracionRespuestasOpenAi configuracion,
            ProveedorEdicionOpenAi.Credencial credencial,TransporteOpenAi transporte){
        this.configuracion=Objects.requireNonNull(configuracion);this.credencial=Objects.requireNonNull(credencial);
        this.transporte=Objects.requireNonNull(transporte);
    }
    @Override public String identificador(){return IDENTIFICADOR;}
    @Override public boolean atiende(Accion accion,String perfil,String version){
        return configuracion.rutas().stream().anyMatch(r->r.accion()==accion&&r.perfil().equals(perfil)&&r.versionPerfil().equals(version));
    }
    @Override public Salida ejecutar(Peticion p,Consumer<ObservacionHttpIa> observador){
        Objects.requireNonNull(p);HttpRequest solicitud;ConfiguracionRespuestasOpenAi.Ruta ruta;
        try{ruta=configuracion.ruta(p.accion());solicitud=peticion(p,ruta);}
        catch(Exception antes){throw noIniciada(p,antes);}
        try{
            var respuesta=transporte.enviar(solicitud,ruta.envoltura().maximoRespuestaBytes(),configuracion.total(),
                    new TransporteOpenAi.ContextoIntento(p.claveExterna(),IDENTIFICADOR,ruta.modelo(),ruta.versionPerfil()),observador);
            return new DecodificadorRespuestasOpenAi(ruta).leer(respuesta);
        }catch(InterruptedException interrupcion){Thread.currentThread().interrupt();throw new ProcesamientoIncierto();}
        catch(Exception incierto){throw new ProcesamientoIncierto();}
    }
    private HttpRequest peticion(Peticion p,ConfiguracionRespuestasOpenAi.Ruta r)throws Exception{
        var e=r.envoltura();
        if(TransactionSynchronizationManager.isActualTransactionActive()||p.claveExterna()==null
                ||!p.claveExterna().matches("[0-9a-f]{64}")||p.limiteRecepcion()!=null
                ||p.instruccion()==null||p.instruccion().isBlank()||p.instruccion().length()>e.maximoInstruccionCaracteres())
            throw new IllegalArgumentException("Petición no admitida antes de transferir.");
        var fabrica=JsonFactory.builder().streamWriteConstraints(StreamWriteConstraints.builder()
                .maxNestingDepth(e.profundidadJson()).build()).build();
        var json=new ObjectMapper(fabrica);
        // Sólo datosPermitidos e instrucción preparada por Core; nunca serializar Peticion/Snapshot.
        var datos=new DecodificadorEdicionOpenAi.BinarioAcotado(e.maximoDatosBytes());json.writeValue(datos,p.datosPermitidos());
        var contenido=new ArrayList<Map<String,Object>>();
        contenido.add(Map.of("type","input_text","text",new String(datos.contenido(),StandardCharsets.UTF_8)));
        byte[] foto=p.fotoAutorizada();
        if(p.accion()==Accion.REDACTAR_FICHA){
            if(foto!=null)throw new IllegalArgumentException("Redacción sin foto.");
        }else{
            if(foto==null||foto.length==0||foto.length>e.maximoFotoBytes()||!png(foto))
                throw new IllegalArgumentException("Falta la copia PNG minimizada por Core.");
            if(4L*((foto.length+2L)/3L)>e.maximoSolicitudBytes())throw new IllegalArgumentException("Entrada mayor que su envoltura.");
            contenido.add(Map.of("type","input_image","detail",r.parametros().detalleImagen(),
                    "image_url","data:image/png;base64,"+Base64.getEncoder().encodeToString(foto)));
        }
        var formato=Map.of("type","json_schema","name","material_texto","strict",true,
                "schema",Map.of("type","object","properties",Map.of("texto",Map.of("type","string")),
                        "required",List.of("texto"),"additionalProperties",false));
        var payload=new LinkedHashMap<String,Object>();
        payload.put("model",r.modelo());payload.put("instructions",p.instruccion());
        payload.put("input",List.of(Map.of("role","user","content",contenido)));
        payload.put("text",Map.of("format",formato));payload.put("reasoning",Map.of("effort",r.parametros().esfuerzo()));
        payload.put("max_output_tokens",r.parametros().maximoSalidaTokens());
        payload.put("store",false);payload.put("stream",false);payload.put("background",false);
        payload.put("service_tier","default"); // Standard explícito; no heredar un tier de pago del proyecto.
        payload.put("tools",List.of());payload.put("tool_choice","none");payload.put("truncation","disabled");
        // Sin conversation/previous_response_id, archivos remotos, URLs de fuentes, metadatos ni IDs humanos.
        var cuerpo=new DecodificadorEdicionOpenAi.BinarioAcotado(e.maximoSolicitudBytes());json.writeValue(cuerpo,payload);
        String token=credencial.bearer();if(token==null||token.isBlank()||!token.equals(token.trim())||token.contains("\r")||token.contains("\n"))
            throw new IllegalStateException("Falta la credencial servidor.");
        return HttpRequest.newBuilder(DESTINO).timeout(configuracion.total()).header("Authorization","Bearer "+token)
                .header("Content-Type","application/json").header("Accept","application/json")
                .header("Accept-Encoding","identity").header("X-Client-Request-Id",p.claveExterna())
                .POST(TransporteHttpOpenAi.unaSuscripcion(HttpRequest.BodyPublishers.ofByteArray(cuerpo.contenido()))).build();
    }
    private static boolean png(byte[] f){
        byte[] firma={(byte)137,80,78,71,13,10,26,10};if(f.length<firma.length)return false;
        for(int i=0;i<firma.length;i++)if(f[i]!=firma[i])return false;return true;
    }
    private static NoIniciadoComprobado noIniciada(Peticion p,Exception causa){
        String h=Idempotencia.huellaExacta("NO_TRANSFERIDA\u0000"+p.claveExterna()+"\u0000"+causa.getClass().getName());
        return new NoIniciadoComprobado(new ComprobacionNoProcesamiento(p.claveExterna(),IDENTIFICADOR,
                "VALIDACION_LOCAL:"+p.claveExterna(),"ADAPTADOR_ANTES_DE_TRANSPORTE:"+h,h,Instant.now()));
    }
    @Override public String toString(){return "ProveedorRespuestasOpenAi[configuración privada]";}
}
