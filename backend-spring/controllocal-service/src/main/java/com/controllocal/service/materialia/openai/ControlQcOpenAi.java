package com.controllocal.service.materialia.openai;

import com.controllocal.service.materialia.*;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.materialia.ControlesMaterialIa.*;
import com.controllocal.service.materialia.MaterialIaService.Accion;
import com.controllocal.service.materialia.ProveedorMaterialIa.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.ComprobacionNoProcesamiento;
import com.controllocal.service.soporte.Idempotencia;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import com.controllocal.service.presupuesto.ObservacionHttpIa;

/** Juez facturable candidato, sin negocio inmobiliario, repositorios, lectura de fotos o efectos posteriores. */
public final class ControlQcOpenAi implements Facturable {
    public static final String IDENTIFICADOR="OPENAI_RESPONSES_QC";
    public static final URI DESTINO=ProveedorRespuestasOpenAi.DESTINO;
    private final ConfiguracionQcOpenAi configuracion;private final ProveedorEdicionOpenAi.Credencial credencial;
    private final TransporteOpenAi transporte;private final Clock reloj;private final EvaluacionTecnicaFoto evaluador;
    public ControlQcOpenAi(ConfiguracionQcOpenAi c,ProveedorEdicionOpenAi.Credencial clave,TransporteOpenAi t,EvaluacionTecnicaFoto evaluador){
        this(c,clave,t,evaluador,Clock.systemUTC());
    }
    ControlQcOpenAi(ConfiguracionQcOpenAi c,ProveedorEdicionOpenAi.Credencial clave,TransporteOpenAi t,EvaluacionTecnicaFoto evaluador,Clock reloj){
        configuracion=Objects.requireNonNull(c);credencial=Objects.requireNonNull(clave);
        transporte=Objects.requireNonNull(t);this.reloj=Objects.requireNonNull(reloj);this.evaluador=Objects.requireNonNull(evaluador);
    }
    @Override public boolean atiende(Accion a,String perfil,String version){
        return configuracion.rutas().stream().anyMatch(r->r.accion()==a&&r.perfil().equals(perfil)&&r.versionPerfil().equals(version));
    }
    @Override public FuentesMaterialIa.DestinoTratamiento destino(String perfil,String version){
        if(configuracion.rutas().stream().noneMatch(r->r.perfil().equals(perfil)&&r.versionPerfil().equals(version)))
            throw new IllegalArgumentException("Perfil QC no configurado.");
        return new FuentesMaterialIa.DestinoTratamiento(IDENTIFICADOR,perfil,version);
    }
    @Override public EvaluacionFacturable evaluarFacturable(PeticionControl p,Consumer<ObservacionHttpIa> observador){
        Objects.requireNonNull(p);PeticionPreparada preparada;ConfiguracionQcOpenAi.Ruta ruta;
        try{ruta=configuracion.ruta(p.accion());preparada=peticion(p,ruta);}
        catch(Exception antes){throw noIniciada(p,antes);}
        try{
            var http=transporte.enviar(preparada.http(),ruta.envoltura().maximoRespuestaBytes(),configuracion.total(),
                    new TransporteOpenAi.ContextoIntento(p.claveEtapa(),IDENTIFICADOR,ruta.modelo(),ruta.versionPerfil()),observador);
            var nativa=new RespuestaNativaOpenAi(IDENTIFICADOR,ruta.modelo(),ruta.envoltura(),ruta.usage()).leer(http);
            Control control=null;
            if(nativa.contenido()!=null){
                try{control=control(nativa,p,ruta,preparada);}catch(java.io.IOException|RuntimeException invalida){control=null;}
            }
            // La medición no depende de que el juez entregue un control utilizable.
            return new EvaluacionFacturable(control,nativa.medicion());
        }catch(InterruptedException interrupcion){Thread.currentThread().interrupt();throw new ProcesamientoIncierto();}
        catch(Exception incierto){throw new ProcesamientoIncierto();}
    }
    private record ProcedenciaDerivada(String version,String huellaMedida,String huellaCopia){}
    private record PeticionPreparada(HttpRequest http,String huella,ProcedenciaDerivada derivada){}
    private PeticionPreparada peticion(PeticionControl p,ConfiguracionQcOpenAi.Ruta r)throws Exception{
        var e=r.envoltura();var s=p.salida();var a=p.accion();
        if(TransactionSynchronizationManager.isActualTransactionActive()||!p.claveEtapa().matches("[0-9a-f]{64}")
                ||p.instruccionPermitida()==null||p.instruccionPermitida().isBlank()
                ||p.instruccionPermitida().length()>e.maximoInstruccionCaracteres())
            throw new IllegalArgumentException("Entrada QC no admitida antes de transferir.");
        byte[] medida=s.foto();
        if(a==Accion.MEJORAR_FOTO?medida==null||medida.length==0||!Set.of("image/png","image/jpeg").contains(s.contentType()):
                medida!=null||s.texto()==null||s.texto().isBlank()||s.texto().getBytes(StandardCharsets.UTF_8).length>r.maximoResultadoTextoBytes())
            throw new IllegalArgumentException("Falta la salida medida de esta acción.");
        var json=RespuestaNativaOpenAi.json(e);var datos=new DecodificadorEdicionOpenAi.BinarioAcotado(e.maximoDatosBytes());
        json.writeValue(datos,p.datosPermitidos());
        var entrada=new LinkedHashMap<String,Object>();entrada.put("accion",a.name());
        entrada.put("datos_permitidos",json.readTree(datos.contenido()));
        entrada.put("instruccion_comercial",p.instruccionPermitida());
        if(a!=Accion.MEJORAR_FOTO)entrada.put("resultado",s.texto());
        var contenido=new ArrayList<Map<String,Object>>();
        contenido.add(Map.of("type","input_text","text",json.writeValueAsString(entrada)));
        byte[] foto=p.originalAutorizado();ProcedenciaDerivada procedencia=null;
        if(a==Accion.REDACTAR_FICHA){if(foto!=null)throw new IllegalArgumentException("Redacción QC sin fotografía.");}
        else{
            if(foto==null||foto.length==0||foto.length>e.maximoFotoBytes()||!png(foto)
                    ||4L*((foto.length+2L)/3L)>e.maximoSolicitudBytes())
                throw new IllegalArgumentException("Falta el original PNG minimizado autorizado.");
            contenido.add(Map.of("type","input_text","text","ORIGINAL_AUTORIZADO"));
            contenido.add(Map.of("type","input_image","detail",r.parametros().detalleImagen(),
                    "image_url","data:image/png;base64,"+Base64.getEncoder().encodeToString(foto)));
            if(a==Accion.MEJORAR_FOTO){
                // Único evaluador Core existente. Copia efímera sin reemplazar Salida, original, hash, selección o pieza.
                var copia=evaluador.evaluar(medida);byte[] depurada=copia.contenido();
                if(!EvaluacionTecnicaFoto.VERSION.equals(copia.evaluacion().versionProceso())||!"image/png".equals(copia.contentType())
                        ||depurada.length==0||depurada.length>e.maximoFotoBytes()||!png(depurada)
                        ||!RespuestaNativaOpenAi.huella(medida).equals(copia.evaluacion().huellaOriginal())
                        ||!RespuestaNativaOpenAi.huella(depurada).equals(copia.huella())
                        ||4L*((depurada.length+2L)/3L)>e.maximoSolicitudBytes())
                    throw new IllegalArgumentException("La copia de trabajo derivada no conserva su procedencia técnica.");
                procedencia=new ProcedenciaDerivada(copia.evaluacion().versionProceso(),copia.evaluacion().huellaOriginal(),copia.huella());
                contenido.add(Map.of("type","input_text","text","DERIVADA_DEPURADA"));
                contenido.add(Map.of("type","input_image","detail",r.parametros().detalleImagen(),
                        "image_url","data:image/png;base64,"+Base64.getEncoder().encodeToString(depurada)));
            }
        }
        var payload=new LinkedHashMap<String,Object>();payload.put("model",r.modelo());
        payload.put("instructions",r.rubricaServidor());payload.put("input",List.of(Map.of("role","user","content",contenido)));
        payload.put("text",Map.of("format",Map.of("type","json_schema","name","material_qc","strict",true,"schema",esquema())));
        payload.put("reasoning",Map.of("effort",r.parametros().esfuerzo()));
        payload.put("max_output_tokens",r.parametros().maximoSalidaTokens());
        payload.put("store",false);payload.put("stream",false);payload.put("background",false);payload.put("service_tier","default");
        payload.put("tools",List.of());payload.put("tool_choice","none");payload.put("truncation","disabled");
        payload.put("prompt_cache_options",Map.of("mode",r.cache().modo(),"ttl",r.cache().ttl(),"prewarm",false));
        var cuerpo=new DecodificadorEdicionOpenAi.BinarioAcotado(e.maximoSolicitudBytes());json.writeValue(cuerpo,payload);
        String token=credencial.bearer();if(token==null||token.isBlank()||!token.equals(token.trim())||token.contains("\r")||token.contains("\n"))
            throw new IllegalStateException("Falta la credencial servidor.");
        byte[] bytes=cuerpo.contenido();var http=HttpRequest.newBuilder(DESTINO).timeout(configuracion.total()).header("Authorization","Bearer "+token)
                .header("Content-Type","application/json").header("Accept","application/json").header("Accept-Encoding","identity")
                .header("X-Client-Request-Id",p.claveEtapa())
                .POST(TransporteHttpOpenAi.unaSuscripcion(HttpRequest.BodyPublishers.ofByteArray(bytes))).build();
        return new PeticionPreparada(http,RespuestaNativaOpenAi.huella(bytes),procedencia);
    }
    private Control control(RespuestaNativaOpenAi.Resultado n,PeticionControl p,ConfiguracionQcOpenAi.Ruta r,PeticionPreparada preparada)throws java.io.IOException{
        byte[] bytes=n.contenido().getBytes(StandardCharsets.UTF_8);if(bytes.length>r.envoltura().maximoTextoBytes())throw invalida();
        var json=RespuestaNativaOpenAi.json(r.envoltura());JsonNode raiz;
        try(JsonParser parser=json.getFactory().createParser(bytes)){raiz=json.readTree(parser);if(parser.nextToken()!=null)throw invalida();}
        if(raiz==null||!raiz.isObject()||!campos(raiz,Set.of("decision","senales"))||!raiz.path("decision").isTextual())throw invalida();
        Decision decision=Decision.valueOf(raiz.get("decision").textValue());var senales=raiz.get("senales");
        if(senales==null||!senales.isArray()||senales.size()!=Aspecto.values().length)throw invalida();
        var vistas=EnumSet.noneOf(Aspecto.class);var resultado=new ArrayList<Senal>();
        String evidencia="QC_OPENAI_RESPUESTA:"+n.referenciaRespuesta()+";CUERPO_SHA256:"+n.huellaRespuesta()
                +";PETICION_SHA256:"+preparada.huella()
                +";VINCULO_CORE:"+Idempotencia.huellaExacta(p.claveEtapa()+"\u0000"+preparada.huella()+"\u0000"+r.versionControl()
                +"\u0000"+r.versionRubrica()+"\u0000"+r.huellaRubrica()+"\u0000"+r.evidenciaRubricaServidor()+"\u0000"+r.cache().evidenciaServidor());
        if(preparada.derivada()!=null)evidencia+=";COPIA_DERIVADA:"+preparada.derivada().version()
                +";DERIVADA_MEDIDA_SHA256:"+preparada.derivada().huellaMedida()+";COPIA_DERIVADA_SHA256:"+preparada.derivada().huellaCopia();
        for(var x:senales){
            if(!x.isObject()||!campos(x,Set.of("aspecto","estado","hallazgoBloqueante","observacion"))
                    ||!x.path("aspecto").isTextual()||!x.path("estado").isTextual()
                    ||!x.path("hallazgoBloqueante").isBoolean()||!x.path("observacion").isTextual()||x.get("observacion").textValue().isBlank()
                    ||!RespuestaNativaOpenAi.textoRepresentable(x.get("observacion").textValue()))throw invalida();
            Aspecto aspecto=Aspecto.valueOf(x.get("aspecto").textValue());if(!vistas.add(aspecto))throw invalida();
            resultado.add(new Senal(aspecto,EstadoSenal.valueOf(x.get("estado").textValue()),x.get("hallazgoBloqueante").booleanValue(),
                    x.get("observacion").textValue(),evidencia));
        }
        // NO_APLICA/vetos se validan en la guarda canónica Core después de conservar la medición.
        return new Control(decision,r.versionControl(),reloj.instant(),resultado);
    }
    static Map<String,Object> esquema(){
        var senal=Map.of("type","object","properties",Map.of(
                "aspecto",Map.of("type","string","enum",Arrays.stream(Aspecto.values()).map(Enum::name).toList()),
                "estado",Map.of("type","string","enum",Arrays.stream(EstadoSenal.values()).map(Enum::name).toList()),
                "hallazgoBloqueante",Map.of("type","boolean"),"observacion",Map.of("type","string")),
                "required",List.of("aspecto","estado","hallazgoBloqueante","observacion"),"additionalProperties",false);
        return Map.of("type","object","properties",Map.of("decision",Map.of("type","string","enum",Arrays.stream(Decision.values()).map(Enum::name).toList()),
                "senales",Map.of("type","array","minItems",5,"maxItems",5,"items",senal)),
                "required",List.of("decision","senales"),"additionalProperties",false);
    }
    private static boolean campos(JsonNode n,Set<String> esperados){var vistos=new HashSet<String>();n.fieldNames().forEachRemaining(vistos::add);return vistos.equals(esperados);}
    private static boolean png(byte[] f){byte[] firma={(byte)137,80,78,71,13,10,26,10};if(f.length<firma.length)return false;for(int i=0;i<firma.length;i++)if(f[i]!=firma[i])return false;return true;}
    private static java.io.IOException invalida(){return new java.io.IOException("Control estructurado no confirmado.");}
    private NoIniciadoComprobado noIniciada(PeticionControl p,Exception causa){
        String h=Idempotencia.huellaExacta("QC_NO_TRANSFERIDA\u0000"+p.claveEtapa()+"\u0000"+causa.getClass().getName());
        return new NoIniciadoComprobado(new ComprobacionNoProcesamiento(p.claveEtapa(),IDENTIFICADOR,"VALIDACION_LOCAL:"+p.claveEtapa(),
                "ADAPTADOR_ANTES_DE_TRANSPORTE:"+h,h,reloj.instant()));
    }
    @Override public String toString(){return "ControlQcOpenAi[configuración privada]";}
}
