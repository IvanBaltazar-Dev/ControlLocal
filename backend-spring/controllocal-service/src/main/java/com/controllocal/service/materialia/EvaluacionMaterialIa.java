package com.controllocal.service.materialia;

import com.controllocal.service.presupuesto.*;
import com.controllocal.service.presupuesto.PresupuestoIaService.*;
import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.planes.UsoAlmacenamientoB0;
import com.controllocal.service.documentos.EvaluacionTecnicaFoto;
import com.controllocal.service.materialia.MaterialIaService.Accion;
import com.controllocal.service.materialia.ProveedorMaterialIa.*;
import com.controllocal.service.materialia.ControlesMaterialIa.*;
import com.controllocal.service.materialia.FuentesMaterialIa.DestinoTratamiento;
import com.controllocal.service.materialia.openai.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;

/** Un consumidor C6. Estado/dinero/claims pertenecen al mismo B0; sin Actor o expediente comercial ficticio. */
@Service
public class EvaluacionMaterialIa {
    public record Cobertura(String finalidad,List<String> categorias,String proveedorLegal,String condicionesVersion,
                            String condicionesTexto,String huellaCondiciones,String legitimacion,UUID decisionLote,
                            List<DestinoTratamiento> destinos,boolean qcSobreMismoGen){
        public Cobertura{categorias=List.copyOf(categorias);destinos=List.copyOf(destinos);}
        @Override public String toString(){return "CoberturaEvaluacion[privada]";}
    }
    public record CasoPreparado(UUID caso,String particion,Accion accion,String huellaOriginal,long bytesOriginal,String mimeOriginal,
                                String versionMinimizacion,String huellaMinimizada,Map<String,Object> datosPermitidos,
                                String instruccionPermitida,ConfiguracionEdicionOpenAi gen,ConfiguracionQcOpenAi qc,
                                String huellaConfiguracionGen,String huellaConfiguracionQc,Cobertura cobertura){
        public CasoPreparado{datosPermitidos=Map.copyOf(datosPermitidos);}
        @Override public String toString(){return "CasoPreparadoEvaluacion[privado]";}
    }
    public record Manifiesto(String esquema,List<CasoPreparado> casos){public Manifiesto{casos=List.copyOf(casos);}}
    public record Generacion(String texto,String contentType,String proceso,String versionProceso){}
    public record ControlRecibido(String huellaPayload,Control control){@Override public String toString(){return "ControlEvaluacion[privado]";}}
    private record Preparada(SolicitudEvaluacion solicitud,CasoPreparado caso){}
    private final PresupuestoIaService b0;private final EvaluacionesIaB0 planes;private final ArchivosEvaluacionIa archivos;
    private final EvaluacionesIaB0Repository hechos;private final UsoAlmacenamientoB0 uso;private final Documentos json;private final ObjectMapper estricto;
    private final EvaluacionTecnicaFoto fotos;private final ObjectProvider<NativosEvaluacionMaterialIa> nativos;private final TransactionTemplate tx;
    public EvaluacionMaterialIa(PresupuestoIaService b0,EvaluacionesIaB0 planes,ArchivosEvaluacionIa archivos,UsoAlmacenamientoB0 uso,
        Documentos json,ObjectMapper mapper,EvaluacionTecnicaFoto fotos,ObjectProvider<NativosEvaluacionMaterialIa> nativos,PlatformTransactionManager gestor,EvaluacionesIaB0Repository hechos){
        this.hechos=hechos;this.b0=b0;this.planes=planes;this.archivos=archivos;this.uso=uso;this.json=json;this.fotos=fotos;this.nativos=nativos;tx=new TransactionTemplate(gestor);
        estricto=mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public Admision ejecutar(UUID caso,UUID comando,UUID financiacion,SesionPlataforma sesion){return procesar(caso,comando,financiacion,sesion,true);}
    /** Recuperación técnica: no admite, reclama ni cancela una obligación válida para intentar otra inferencia. */
    public Admision recuperar(UUID caso,UUID comando,UUID financiacion,SesionPlataforma sesion){return procesar(caso,comando,financiacion,sesion,false);}
    private Admision procesar(UUID caso,UUID comando,UUID financiacion,SesionPlataforma sesion,boolean permiteInferencia){
        fueraSql();var p=preparar(caso,comando,financiacion,sesion);var s=p.solicitud();var ref=new ReferenciaEvaluacion(caso,comando);
        // CONSULTAR_ESTADO no se infiere de INICIAR. Una reanudación nunca readmite con otro autor.
        var existente=tx.execute(t->b0.consultarEstadoEvaluacion(s,sesion));
        if(existente.isEmpty()){if(!permiteInferencia)throw new NoEncontradoException("Operación de evaluación");tx.execute(t->b0.admitirEvaluacion(s,sesion));}
        var gen=tx.execute(t->b0.leerRespuestaEvaluacion(ref,null));
        Salida salida=null;
        if(gen.isEmpty()){
            if(existente.isPresent()&&!"RESERVADA".equals(existente.get().estado())){conservarIncertidumbreGen(ref);return estado(s,sesion);}
            if(!permiteInferencia){conservarIncertidumbreGen(ref);return estado(s,sesion);}
            antesDeTransferir(p,sesion);
            if(!Boolean.TRUE.equals(tx.execute(t->b0.iniciarEvaluacion(s,sesion)))){conservarIncertidumbreGen(ref);return estado(s,sesion);}
            boolean invocada=false;var observacionHttp=new AtomicReference<ObservacionHttpIa>();
            try{
                try{
                    byte[] original=copia(p,sesion);antesDeTransferir(p,sesion);
                    var limite=uso.limiteRecepcion(UsoAlmacenamientoB0.referenciaEvaluacion(caso,comando));
                    var peticion=new Peticion(PresupuestoIaService.claveDeIntento(ref),Accion.MEJORAR_FOTO,p.caso().datosPermitidos(),p.caso().instruccionPermitida(),original,limite);
                    var factory=fabrica();
                    try(var sesionNativa=factory.abrir(p.caso().gen(),p.caso().qc())){
                        // Después de entrar al adaptador sólo él acredita que no inició; no clasificar un fallo genérico como no IO.
                        invocada=true;salida=sesionNativa.generacion().ejecutar(peticion,observacionHttp::set);
                    }
                }catch(NoIniciadoComprobado no){tx.execute(t->b0.cancelarNoProcesada(ref,no.evidencia()));throw no;}
                catch(ProcesamientoIncierto incierto){tx.executeWithoutResult(t->b0.marcarIncierta(ref));throw incierto;}
                catch(RuntimeException fallo){
                    if(invocada)tx.executeWithoutResult(t->b0.marcarIncierta(ref));
                    else tx.execute(t->b0.cancelarNoProcesada(ref,noInvocada(ref,null,ProveedorEdicionOpenAi.IDENTIFICADOR,fallo)));
                    throw fallo;
                }
                if(salida==null||salida.medicion()==null){tx.executeWithoutResult(t->b0.marcarIncierta(ref));throw new ProcesamientoIncierto();}
                byte[] binario=salida.foto();UUID archivo=UUID.nameUUIDFromBytes(("BROX-EVALUACION-RESULTADO\u0000"+caso+"\u0000"+comando).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                UUID io=UUID.nameUUIDFromBytes(("BROX-EVALUACION-ARCHIVO\u0000"+caso+"\u0000"+comando+"\u0000"+archivo).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var descriptor=new DescriptorSalidaEvaluacion(archivo,io,binario==null?null:EvaluacionTecnicaFoto.huella(binario),binario==null?0:binario.length,salida.contentType());
                var dato=new Generacion(salida.texto(),salida.contentType(),salida.proceso(),salida.versionProceso());
                var medicion=salida.medicion();String contenido=json.objetoCanonico(dato);
                gen=Optional.of(tx.execute(t->b0.conservarRespuestaEvaluacion(ref,null,PresupuestoIaService.claveDeIntento(ref),contenido,medicion,descriptor)));
            }finally{observarHttp(ref,null,observacionHttp.get());}
        }
        var respuesta=gen.orElseThrow();var g=json.comoObjeto(respuesta.contenido(),Generacion.class);var descriptor=respuesta.descriptor();
        if(descriptor==null)throw new ConflictoException("Falta el descriptor original de salida.");
        if(descriptor.huella()==null){
            tx.executeWithoutResult(t->{b0.leerRespuestaEvaluacion(ref,null);String prueba=json.objetoCanonico(Map.of("respuesta",Idempotencia.huellaExacta(json.objetoCanonico(respuesta)),"clave",respuesta.claveIntento()));
                var previa=hechos.hecho(ref.caso(),ref.operacion(),"SALIDA_SIN_BINARIO");
                if(previa.isPresent()&&!json.objetoCanonico(json.comoObjeto(previa.get(),Map.class)).equals(prueba))throw new ConflictoException("El hecho conserva otra respuesta.");
                if(previa.isEmpty())hechos.hecho(ref.caso(),ref.operacion(),"SALIDA_SIN_BINARIO",prueba);});
            var etapaPendiente=tx.execute(t->b0.estadoEtapaEvaluacion(ref,ControlesMaterialIa.ETAPA_FINANCIERA));
            if(etapaPendiente.isPresent()&&"PENDIENTE".equals(etapaPendiente.get()))
                tx.execute(t->b0.acreditarEtapaNoReclamada(ref,ControlesMaterialIa.ETAPA_FINANCIERA,
                    noInvocada(ref,ControlesMaterialIa.ETAPA_FINANCIERA,ControlQcOpenAi.IDENTIFICADOR,new ReglaNegocioException("Gen medido sin binario"))));
            // RECLAMADA/INCIERTA no se cancelan; conciliar mantiene la guarda de sus hechos pendientes.
            var adm=tx.execute(t->b0.conciliar(ref,b0.conjuntoEvaluacion(ref)));
            if("LIQUIDADA".equals(adm.estado()))archivos.liberarSalidaSinBinario(ref);return estado(s,sesion);
        }
        if(salida!=null)archivos.recibir(ref,descriptor.archivo(),g.contentType(),salida.foto());
        var recuperada=archivos.recuperarResultadoParaProcesar(ref,descriptor.archivo(),g.contentType(),descriptor.huella(),sesion);
        if(recuperada.isEmpty()){
            tx.executeWithoutResult(t->{
                b0.leerRespuestaEvaluacion(ref,null);String dato=json.objetoCanonico(Map.of("respuesta",Idempotencia.huellaExacta(json.objetoCanonico(respuesta))));
                var previa=hechos.hecho(ref.caso(),ref.operacion(),"RECEPCION_PENDIENTE");
                if(previa.isEmpty())hechos.hecho(ref.caso(),ref.operacion(),"RECEPCION_PENDIENTE",dato);
                else if(!dato.equals(json.objetoCanonico(json.comoObjeto(previa.get(),Map.class))))throw new ConflictoException("La recepción conserva otro resultado.");
                b0.marcarIncierta(ref);
            });
            throw new ConflictoException("El Gen tiene foto conocida sin binario durable; conserva reserva y recuperación, sin regenerar ni liberar.");
        }
        var binario=recuperada.orElseThrow();
        String etapa=ControlesMaterialIa.ETAPA_FINANCIERA;
        var qc=tx.execute(t->b0.leerRespuestaEvaluacion(ref,etapa));
        if(qc.isEmpty()){
            if(conservarIncertidumbreQc(ref,etapa))return estado(s,sesion);
            if(!permiteInferencia)return estado(s,sesion);
            var actual=estado(s,sesion);if("LIQUIDADA".equals(actual.estado())||"CANCELADA".equals(actual.estado()))return actual;
            String payload=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("gen",respuesta.huellaContenido(),"foto",descriptor.huella(),"original",p.caso().huellaMinimizada(),
                    "configuracion",p.caso().huellaConfiguracionQc(),"datos",p.caso().datosPermitidos(),"instruccion",p.caso().instruccionPermitida())));
            tx.executeWithoutResult(t->b0.programarEtapa(ref,etapa,ControlQcOpenAi.IDENTIFICADOR,payload));
            boolean reclamada=false;boolean invocada=false;var observacionHttp=new AtomicReference<ObservacionHttpIa>();
            try{
                antesDeTransferir(p,sesion);
                reclamada=Boolean.TRUE.equals(tx.execute(t->b0.iniciarEtapaEvaluacion(s,etapa,sesion)));
                if(reclamada){
                byte[] original=copia(p,sesion);antesDeTransferir(p,sesion);
                var medida=new Salida(g.texto(),g.contentType(),binario,g.proceso(),g.versionProceso(),b0.medicionRespuestaEvaluacion(respuesta));
                var peticion=new PeticionControl(Accion.MEJORAR_FOTO,p.caso().datosPermitidos(),p.caso().instruccionPermitida(),medida,
                        PresupuestoIaService.claveDeEtapa(ref,etapa),original);
                EvaluacionFacturable r;
                try(var n=fabrica().abrir(p.caso().gen(),p.caso().qc())){invocada=true;r=n.control().evaluarFacturable(peticion,observacionHttp::set);}
                if(r==null||r.medicion()==null){tx.executeWithoutResult(t->b0.marcarEtapaIncierta(ref,etapa));throw new ProcesamientoIncierto();}
                String contenido=json.objetoCanonico(new ControlRecibido(payload,r.control()));
                tx.execute(t->b0.conservarRespuestaEvaluacion(ref,etapa,PresupuestoIaService.claveDeEtapa(ref,etapa),contenido,r.medicion(),null));
                }
            }catch(NoIniciadoComprobado no){tx.executeWithoutResult(t->b0.acreditarEtapaNoInvocada(ref,etapa,no.evidencia()));}
            catch(RuntimeException fallo){
                if(invocada){if(reclamada)tx.executeWithoutResult(t->b0.marcarEtapaIncierta(ref,etapa));}
                else{
                    var prueba=noInvocada(ref,etapa,ControlQcOpenAi.IDENTIFICADOR,fallo);
                    boolean cerrada;
                    if(reclamada){tx.executeWithoutResult(t->b0.acreditarEtapaNoInvocada(ref,etapa,prueba));cerrada=true;}
                    else cerrada=Boolean.TRUE.equals(tx.execute(t->b0.acreditarEtapaNoReclamada(ref,etapa,prueba)));
                    if(cerrada)tx.execute(t->b0.conciliar(ref,b0.conjuntoEvaluacion(ref)));
                }
                throw fallo;
            }finally{observarHttp(ref,etapa,observacionHttp.get());}
            // La entrega se revalida fuera del catch: denegarla no vuelve incierta una etapa ya medida.
            if(!reclamada){conservarIncertidumbreQc(ref,etapa);return estado(s,sesion);}
        }
        tx.execute(t->b0.conciliar(ref,b0.conjuntoEvaluacion(ref)));
        return estado(s,sesion);
    }
    /** Persistir la observación no condiciona conservar respuesta, medición o incertidumbre. */
    private void observarHttp(ReferenciaEvaluacion ref,String etapa,ObservacionHttpIa observacion){
        if(observacion==null)return;
        try{tx.executeWithoutResult(t->b0.registrarObservacionHttp(ref,etapa,observacion));}
        catch(RuntimeException pendiente){org.slf4j.LoggerFactory.getLogger(EvaluacionMaterialIa.class)
                .warn("La observación HTTP IA conserva persistencia pendiente; no se repite el procesamiento.");}
    }
    /** Relectura bajo el mismo fence B0: una respuesta llegada entretanto nunca se sustituye. */
    private void conservarIncertidumbreGen(ReferenciaEvaluacion ref){
        tx.executeWithoutResult(t->{if(b0.leerRespuestaEvaluacion(ref,null).isEmpty())b0.marcarIncierta(ref);});
    }
    private boolean conservarIncertidumbreQc(ReferenciaEvaluacion ref,String etapa){
        return Boolean.TRUE.equals(tx.execute(t->{
            if(b0.leerRespuestaEvaluacion(ref,etapa).isPresent())return false;
            var estado=b0.estadoEtapaEvaluacion(ref,etapa);
            if(estado.isPresent()&&Set.of("RECLAMADA","INCIERTA").contains(estado.get())){
                b0.marcarEtapaIncierta(ref,etapa);return true;
            }
            return false; // PENDIENTE sigue siendo una obligación válida, también con gastoOFF.
        }));
    }
    private Preparada preparar(UUID caso,UUID comando,UUID financiacion,SesionPlataforma sesion){
        var plan=tx.execute(t->planes.exigirPlanDeCaso(caso,sesion,com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.INICIAR));
        Manifiesto m;try{m=estricto.readValue(plan.manifiesto(),Manifiesto.class);}catch(Exception e){throw new CapacidadDeshabilitadaException();}
        if(!"C6-EVALUACION-1".equals(m.esquema())||m.casos().isEmpty()||m.casos().stream().map(CasoPreparado::caso).distinct().count()!=m.casos().size())throw new CapacidadDeshabilitadaException();
        var c=m.casos().stream().filter(x->caso.equals(x.caso())).findFirst().orElseThrow(CapacidadDeshabilitadaException::new);
        var cobertura=c.cobertura();String perfil=plan.id().toString();
        if(c.gen()==null||c.qc()==null||c.particion()==null||!Set.of("AJUSTE","RESERVADO").contains(c.particion())||c.accion()!=Accion.MEJORAR_FOTO||!EvaluacionTecnicaFoto.VERSION.equals(c.versionMinimizacion())||!hash(c.huellaMinimizada())
            ||c.instruccionPermitida()==null||c.instruccionPermitida().isBlank()||cobertura==null||!cobertura.qcSobreMismoGen()
            ||!plan.finalidad().equals(cobertura.finalidad())||cobertura.categorias().isEmpty()||cobertura.categorias().stream().anyMatch(x->!texto(x))
            ||new HashSet<>(cobertura.categorias()).size()!=cobertura.categorias().size()||cobertura.destinos().size()!=2||!texto(cobertura.proveedorLegal())
            ||!texto(cobertura.condicionesVersion())||!texto(cobertura.legitimacion())||!texto(cobertura.condicionesTexto())
            ||!Idempotencia.huellaExacta(cobertura.condicionesTexto()).equals(cobertura.huellaCondiciones())
            ||!perfil.equals(c.gen().perfil())||!plan.version().equals(c.gen().versionPerfil())
            ||!Idempotencia.huellaExacta(json.objetoCanonico(c.gen())).equals(c.huellaConfiguracionGen())
            ||!Idempotencia.huellaExacta(json.objetoCanonico(c.qc())).equals(c.huellaConfiguracionQc())||c.qc().rutas().size()!=1)
            throw new CapacidadDeshabilitadaException();
        var ruta=c.qc().rutas().getFirst();
        if(ruta.accion()!=Accion.MEJORAR_FOTO||!perfil.equals(ruta.perfil())||!plan.version().equals(ruta.versionPerfil())
            ||!cobertura.destinos().contains(new DestinoTratamiento(ProveedorEdicionOpenAi.IDENTIFICADOR,perfil,plan.version()))
            ||!cobertura.destinos().contains(new DestinoTratamiento(ControlQcOpenAi.IDENTIFICADOR,perfil,plan.version())))throw new CapacidadDeshabilitadaException();
        planes.exigirPreparacionCaso(caso,c.huellaOriginal(),c.bytesOriginal(),c.mimeOriginal(),cobertura.legitimacion(),cobertura.decisionLote());
        var etapas=planes.etapasPermitidas(caso);
        exigirEtapaNativa(etapas,"GENERACION",ProveedorEdicionOpenAi.IDENTIFICADOR,ProveedorEdicionOpenAi.MODELO,c.gen().usage().versionTarifa(),
                c.gen().usage().tarifas().stream().map(t->t.clase()+"\u0000"+t.unidad()).collect(java.util.stream.Collectors.toSet()));
        exigirEtapaNativa(etapas,ControlesMaterialIa.ETAPA_FINANCIERA,ControlQcOpenAi.IDENTIFICADOR,ruta.modelo(),ruta.usage().versionTarifa(),
                ruta.usage().tarifas().stream().map(t->t.clase()+"\u0000"+t.unidad()).collect(java.util.stream.Collectors.toSet()));
        String h=Idempotencia.huellaExacta(json.objetoCanonico(c));
        return new Preparada(new SolicitudEvaluacion(comando,caso,plan.version(),plan.huella(),financiacion,h),c);
    }
    private static void exigirEtapaNativa(List<EvaluacionesIaB0.EtapaPermitida> etapas,String codigo,String proveedor,String modelo,String tarifa,Set<String> unidades){
        var etapa=etapas.stream().filter(e->codigo.equals(e.codigo())).findFirst().orElseThrow(CapacidadDeshabilitadaException::new);
        var declaradas=etapa.unidades().stream().map(u->u.clase()+"\u0000"+u.unidad()).collect(java.util.stream.Collectors.toSet());
        if(!proveedor.equals(etapa.proveedor())||!modelo.equals(etapa.modelo())||!tarifa.equals(etapa.versionTarifa())||!declaradas.equals(unidades))
            throw new CapacidadDeshabilitadaException();
    }
    private byte[] copia(Preparada p,SesionPlataforma s){
        byte[] original=archivos.leerOriginalParaProcesar(p.solicitud().caso(),s);
        if(!EvaluacionTecnicaFoto.huella(original).equals(p.caso().huellaOriginal())||original.length!=p.caso().bytesOriginal())throw new ConflictoException("El original conserva otra versión.");
        var copia=fotos.evaluar(original);if(!copia.huella().equals(p.caso().huellaMinimizada()))throw new ConflictoException("La minimización no coincide con la copia autorizada.");return copia.contenido();
    }
    private void antesDeTransferir(Preparada p,SesionPlataforma sesion){
        var s=p.solicitud();tx.execute(t->planes.exigir(s.caso(),s.versionPlan(),s.huellaPlan(),s.autorizacionFinanciera(),sesion,
                com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.INICIAR));
        fueraSql();planes.verificarAcreditaciones(s.caso());
        // Una retirada durante la consulta de la fuente no hereda la autorización previa.
        tx.execute(t->planes.exigir(s.caso(),s.versionPlan(),s.huellaPlan(),s.autorizacionFinanciera(),sesion,
                com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.INICIAR));
    }
    /** Hecho exclusivo del coordinador antes de entrar al adaptador, nunca una inferencia desde timeout. */
    private ComprobacionNoProcesamiento noInvocada(ReferenciaEvaluacion ref,String etapa,String proveedor,RuntimeException causa){
        String clave=etapa==null?PresupuestoIaService.claveDeIntento(ref):PresupuestoIaService.claveDeEtapa(ref,etapa);
        String contenido=json.objetoCanonico(Map.of("origen","CORE_PREVIO_AL_ADAPTADOR","clave",clave,"proveedor",proveedor,"causa",causa.getClass().getName()));
        String h=Idempotencia.huellaExacta(contenido);
        return new ComprobacionNoProcesamiento(clave,proveedor,clave,"EVALUACION-ANTES-DE-ADAPTADOR:"+h,h,planes.ahoraTecnica());
    }
    private NativosEvaluacionMaterialIa fabrica(){if(!b0.gastoExternoHabilitado())throw new CapacidadDeshabilitadaException();var f=nativos.getIfAvailable();if(f==null)throw new CapacidadDeshabilitadaException();return f;}
    private Admision estado(SolicitudEvaluacion s,SesionPlataforma sesion){return tx.execute(t->b0.consultarEstadoEvaluacion(s,sesion)).orElseThrow();}
    private static boolean hash(String x){return x!=null&&x.matches("[0-9a-f]{64}");}
    private static boolean texto(String x){return x!=null&&!x.isBlank();}
    private static void fueraSql(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("La evaluación requiere transacciones cortas propias.");}
}
