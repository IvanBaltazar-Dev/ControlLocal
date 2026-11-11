package com.controllocal.service.presupuesto;

import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository;
import com.controllocal.persistence.presupuesto.EvaluacionesIaB0Repository.*;
import com.controllocal.service.plataforma.AtribucionesPlataforma;
import com.controllocal.service.plataforma.AtribucionesPlataforma.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configuración/autoridad del plan; sin ejecución, ledger ni normalizador paralelos. */
@Service
public class EvaluacionesIaB0 {
    public record Admitida(Plan plan,Caso caso,Financiacion financiacion,Actual autoridad) {}
    /** Proyecciones privadas de Service; ningún record de persistencia atraviesa Web. */
    public record ContextoArchivo(String namespace,String huella,String finalidad) {}
    public record ArchivoMuestra(String namespace,String clave,long bytes,String huella,String mime,String estado) {}
    public record LimiteUnidad(String clase,String unidad,BigDecimal maximo) {}
    public record EtapaPermitida(String codigo,String proveedor,String modelo,String versionTarifa,List<LimiteUnidad> unidades) {}
    public record Configuracion(List<EtapaPermitida> etapas) {}
    private final EvaluacionesIaB0Repository repo;private final AtribucionesPlataforma autoridad;private final Documentos json;
    public EvaluacionesIaB0(EvaluacionesIaB0Repository repo,AtribucionesPlataforma autoridad,Documentos json){
        this.repo=repo;this.autoridad=autoridad;this.json=json;
    }
    @Transactional
    public void registrarPlan(Plan plan,UUID decision,SesionPlataforma sesion){
        if(plan==null||plan.id()==null||!texto(plan.version())||!hash(plan.huella())||!texto(plan.finalidad())
            ||!texto(plan.manifiesto())||!texto(plan.rubrica())||!texto(plan.conservacion())||!texto(plan.destino())
            ||!hash(plan.namespace())||plan.maximoOperacion()==null||plan.maximoOperacion().signum()<=0
            ||plan.llamadas()<=0||plan.salidaBytes()<=0||plan.concurrencia()<=0)
            throw new ReglaNegocioException("Conserva el plan y sus límites acreditados.");
        exigirImporteInterno(plan.maximoOperacion());validarConfiguracion(plan);
        String h=Idempotencia.huellaExacta(json.objetoCanonico(plan));
        long cuenta=autoridad.acreditar(decision,"REGISTRAR_PLAN_EVALUACION",h,null,sesion);
        var previa=repo.plan(plan.id(),plan.version());
        if(previa.isPresent()){if(!previa.get().equals(plan))throw new ConflictoException("La versión conserva otro plan.");return;}
        repo.insertar(plan,decision,cuenta);
    }
    @Transactional
    public void registrarCaso(Caso caso,UUID decision,SesionPlataforma sesion){
        if(caso==null||caso.id()==null||caso.plan()==null||!texto(caso.version())||!texto(caso.codigo())
            ||!hash(caso.huella())||!texto(caso.procedencia())||!texto(caso.legitimacion())
            ||caso.archivo()==null||caso.bytes()<=0||!texto(caso.mime()))
            throw new ReglaNegocioException("Conserva origen y legitimación del caso.");
        String h=Idempotencia.huellaExacta(json.objetoCanonico(caso));
        long cuenta=autoridad.acreditar(decision,"REGISTRAR_CASO_EVALUACION",h,null,sesion);
        repo.plan(caso.plan(),caso.version()).orElseThrow(CapacidadDeshabilitadaException::new);
        var previa=repo.caso(caso.id());
        if(previa.isPresent()){if(!previa.get().equals(caso))throw new ConflictoException("El caso conserva otro origen.");return;}
        repo.insertar(caso,decision,cuenta);
    }
    @Transactional
    public void financiar(UUID id,UUID plan,String version,UUID fondo,BigDecimal maximo,Instant desde,Instant hasta,
                          String evidencia,SesionPlataforma sesion){
        Actual a=autoridad.resolver(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.FINANCIAR,Campo.LEDGER);
        if(!a.plan().equals(plan)||!a.versionPlan().equals(version))throw new AccesoNoAutorizadoException();
        Plan p=repo.plan(plan,version).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!a.finalidad().equals(p.finalidad()))throw new AccesoNoAutorizadoException();
        if(id==null||fondo==null||maximo==null||maximo.signum()<=0||desde==null||hasta==null
            ||!hasta.isAfter(desde)||!texto(evidencia))throw new ReglaNegocioException("Conserva financiación, techo y vigencia acreditados.");
        if(!Objects.equals(a.fondo(),fondo))throw new AccesoNoAutorizadoException();
        exigirImporteInterno(maximo);
        var f=new Financiacion(id,plan,version,fondo,maximo,desde,hasta,a.cuenta(),a.concesion(),evidencia,null);
        var previa=repo.financiacion(id,true);
        if(previa.isPresent()){if(!previa.get().equals(f))throw new ConflictoException("La autorización conserva otro alcance.");return;}
        var existente=repo.autorizacionDePlan(plan,version);
        if(existente.isPresent()&&!existente.get().equals(id))throw new ConflictoException("La versión conserva otra autorización financiera; un cambio exige otra versión.");
        repo.insertar(f);
    }
    @Transactional
    public Admitida exigir(UUID caso,String version,String huella,UUID financiacion,SesionPlataforma sesion,Accion accion){
        var a=autoridad.resolver(sesion,Atribucion.OPERACION_IA_PLATFORM,accion,Campo.ESTADO);
        Caso c=repo.caso(caso).orElseThrow(AccesoNoAutorizadoException::new);
        exigirAmbito(a,c);
        Plan p=repo.plan(c.plan(),c.version()).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!a.finalidad().equals(p.finalidad()))throw new AccesoNoAutorizadoException();
        if(a.caso()!=null&&!a.caso().equals(caso))throw new AccesoNoAutorizadoException();
        if(!p.version().equals(version)||!p.huella().equals(huella))throw new ConflictoException("La actuación conserva otra versión del plan.");
        Financiacion f=repo.financiacion(financiacion,true).orElseThrow(CapacidadDeshabilitadaException::new);
        Instant ahora=Instant.now();
        if(!f.plan().equals(p.id())||!f.version().equals(p.version())||f.revocada()!=null
            ||ahora.isBefore(f.desde())||!ahora.isBefore(f.hasta()))throw new CapacidadDeshabilitadaException();
        return new Admitida(p,c,f,a);
    }
    @Transactional
    public void exigirCupo(Admitida a){
        if(repo.comprometido(a.financiacion().id()).add(a.plan().maximoOperacion()).compareTo(a.financiacion().maximo())>0
            ||repo.enCurso(a.plan().id(),a.plan().version())>=a.plan().concurrencia())
            throw new ReglaNegocioException("El plan no conserva presupuesto o concurrencia disponible.");
    }
    /** La retención ya existe: reclamar no vuelve a sumar otro máximo, pero cuenta toda diferencia conocida. */
    @Transactional
    public void exigirPresupuestoActual(Admitida a){
        if(repo.comprometido(a.financiacion().id()).compareTo(a.financiacion().maximo())>0)
            throw new ReglaNegocioException("El plan conserva una diferencia técnica pendiente de provisión.");
    }
    @Transactional
    public Plan planDeCaso(UUID caso){
        var c=repo.caso(caso).orElseThrow(CapacidadDeshabilitadaException::new);
        return repo.plan(c.plan(),c.version()).orElseThrow(CapacidadDeshabilitadaException::new);
    }
    @Transactional
    public void exigirLecturaMuestra(UUID caso,SesionPlataforma sesion){
        var a=autoridad.resolver(sesion,Atribucion.BROX_PLATFORM,Accion.LEER_MUESTRA,Campo.MUESTRA);
        var c=repo.caso(caso).orElseThrow(AccesoNoAutorizadoException::new);exigirAmbito(a,c);
        if(!Objects.equals(a.caso(),caso)||!a.finalidad().equals(planDeCaso(caso).finalidad()))throw new AccesoNoAutorizadoException();
    }
    @Transactional
    public Financiacion leerFinanciacion(UUID id,SesionPlataforma sesion){
        var a=autoridad.resolver(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.LEER_LEDGER,Campo.LEDGER);
        var f=repo.financiacion(id,false).orElseThrow(AccesoNoAutorizadoException::new);
        if(!a.plan().equals(f.plan())||!a.versionPlan().equals(f.version()))throw new AccesoNoAutorizadoException();
        if(!Objects.equals(a.fondo(),f.fondo())||!a.finalidad().equals(repo.plan(f.plan(),f.version()).orElseThrow().finalidad()))throw new AccesoNoAutorizadoException();
        return f;
    }
    private static boolean texto(String s){return s!=null&&!s.isBlank();}
    private static boolean hash(String s){return s!=null&&s.matches("[0-9a-f]{64}");}

    @Transactional
    public ContextoArchivo exigirOriginal(UUID caso,UUID archivo,long bytes,String hash,SesionPlataforma sesion){
        var a=autoridad.resolver(sesion,Atribucion.OPERACION_IA_PLATFORM,Accion.ADMITIR,Campo.ESTADO);
        Caso c=repo.caso(caso).orElseThrow(AccesoNoAutorizadoException::new);exigirAmbito(a,c);Plan p=planDeCaso(caso);if(!a.finalidad().equals(p.finalidad()))throw new AccesoNoAutorizadoException();
        if(a.caso()!=null&&!a.caso().equals(caso)||!c.archivo().equals(archivo)||c.bytes()!=bytes||!c.huella().equals(hash))
            throw new AccesoNoAutorizadoException();
        return contextoArchivo(p);
    }
    /** Metadatos mínimos para recibir el resultado del claim existente; no autoriza otra llamada. */
    @Transactional(readOnly=true)
    public ContextoArchivo contextoArchivo(UUID caso){return contextoArchivo(planDeCaso(caso));}
    private static ContextoArchivo contextoArchivo(Plan plan){return new ContextoArchivo(plan.namespace(),plan.huella(),plan.finalidad());}
    /** Sólo el hecho del intento comprometido; no vuelve a pedir SID ni dinero al callback. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void comprometerArchivo(UUID caso,UUID archivo,UUID operacionB0,UUID io,int numero,String hash,long bytes){
        // El repositorio valida por el carril2 exclusivamente sobre referencias ya comprometidas.
        repo.comprometer(caso,archivo,operacionB0,io,numero,hash,bytes);
    }
    @Transactional
    public void exigirCustodia(UUID caso,UUID operacion,UUID archivo,UUID io,int numero,String hash,long bytes){
        repo.exigirClaim(caso,operacion);var a=repo.archivo(caso,archivo).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!Objects.equals(a.operacion(),operacion)||!a.operacionIo().equals(io)||a.numero()!=numero
            ||!a.huella().equals(hash)||a.bytes()!=bytes)throw new ConflictoException("La recepción exige su custodia original.");
    }
    @Transactional
    public void verificarArchivo(UUID caso,UUID archivo){repo.verificar(caso,archivo);}
    @Transactional(readOnly=true)
    public ArchivoMuestra archivoInterno(UUID caso,UUID archivo){
        var a=repo.archivo(caso,archivo).orElseThrow(()->new NoEncontradoException("Archivo de evaluación"));
        return muestra(a);
    }

    @Transactional(readOnly=true)
    public UUID identidadOperacion(UUID caso,UUID comando){
        return repo.identidadOperacion(caso,comando).orElseThrow(CapacidadDeshabilitadaException::new);
    }

    /** Proyección mínima para Web; no expone Plan/record de persistencia ni autoriza transferencia. */
    @Transactional
    public ContextoArchivo contextoParaProcesar(UUID caso,SesionPlataforma sesion){
        return contextoArchivo(exigirPlanDeCaso(caso,sesion,Accion.INICIAR));
    }
    /** Lectura exclusivamente interna para procesar el caso autorizado; no equivale a LEER_MUESTRA. */
    @Transactional
    public ArchivoMuestra exigirOriginalParaProcesar(UUID caso,SesionPlataforma sesion){
        Plan p=exigirPlanDeCaso(caso,sesion,Accion.INICIAR);
        Caso c=repo.caso(caso).orElseThrow(AccesoNoAutorizadoException::new);
        var a=repo.archivo(caso,c.archivo()).orElseThrow(()->new NoEncontradoException("Original de evaluación"));
        if(!"ORIGINAL_CORPUS".equals(a.tipo())||a.operacion()!=null||!"VERIFICADA".equals(a.estado())
                ||!a.namespace().equals(p.namespace())||!a.huella().equals(c.huella())||a.bytes()!=c.bytes()
                ||!a.mime().equals(c.mime()))throw new ConflictoException("El original no conserva el caso y plan autorizados.");
        return muestra(a);
    }
    /** El binario procede del claim B0 real, no de otra operación del mismo caso. */
    @Transactional
    public ArchivoMuestra exigirResultadoParaProcesar(PresupuestoIaService.ReferenciaEvaluacion ref,UUID archivo,
                                                     SesionPlataforma sesion){
        Plan p=exigirPlanDeCaso(ref.caso(),sesion,Accion.INICIAR);
        UUID operacion=identidadOperacion(ref.caso(),ref.operacion());
        repo.exigirClaim(ref.caso(),operacion);
        var a=repo.archivo(ref.caso(),archivo).orElseThrow(()->new NoEncontradoException("Resultado de evaluación"));
        if(!"RESULTADO".equals(a.tipo())||!operacion.equals(a.operacion())||!"VERIFICADA".equals(a.estado())
                ||!a.namespace().equals(p.namespace()))throw new ConflictoException("El resultado no conserva su claim y plan autorizados.");
        return muestra(a);
    }
    private static ArchivoMuestra muestra(Archivo a){return new ArchivoMuestra(a.namespace(),a.clave(),a.bytes(),a.huella(),a.mime(),a.estado());}

    /** Proyección de autoridad privada; el operador nunca declara otorgante/hash. Sin TX sobre la fuente. */
    public void verificarAcreditaciones(UUID caso){
        var a=repo.acreditacion(caso).orElseThrow(AccesoNoAutorizadoException::new);
        autoridad.verificarDecisionRegistrada(a.decisionPlan(),"REGISTRAR_PLAN_EVALUACION",a.cuentaPlan(),null);
        autoridad.verificarDecisionRegistrada(a.decisionCaso(),"REGISTRAR_CASO_EVALUACION",a.cuentaCaso(),null);
    }
    public void exigirSalidaSinBinario(PresupuestoIaService.ReferenciaEvaluacion ref,PresupuestoIaService.RespuestaEvaluacion respuesta){
        var d=respuesta.descriptor();if(d==null||d.huella()!=null||d.bytes()!=0)throw new ConflictoException("La respuesta conserva un binario.");
        String esperada=json.objetoCanonico(Map.of("respuesta",Idempotencia.huellaExacta(json.objetoCanonico(respuesta)),"clave",respuesta.claveIntento()));
        var dato=repo.hecho(ref.caso(),ref.operacion(),"SALIDA_SIN_BINARIO").orElseThrow(()->new ConflictoException("Falta el hecho exacto sin binario."));
        if(!esperada.equals(json.objetoCanonico(json.comoObjeto(dato,Map.class))))throw new ConflictoException("El hecho conserva otra respuesta.");
    }
    public void exigirPreparacionCaso(UUID caso,String huella,long bytes,String mime,String legitimacion,UUID decisionPlan){
        var c=repo.caso(caso).orElseThrow(AccesoNoAutorizadoException::new);var a=repo.acreditacion(caso).orElseThrow(AccesoNoAutorizadoException::new);
        if(!c.huella().equals(huella)||c.bytes()!=bytes||!c.mime().equals(mime)||!c.legitimacion().equals(legitimacion)
                ||!a.decisionPlan().equals(decisionPlan))throw new ConflictoException("La preparación no conserva la acreditación original.");
    }
    public java.time.Instant ahoraTecnica(){return repo.ahoraTecnica();}
    public void exigirArchivoResultado(UUID caso,UUID archivo){
        var c=repo.caso(caso).orElseThrow(CapacidadDeshabilitadaException::new);
        if(archivo==null||archivo.equals(c.archivo()))throw new ConflictoException("El resultado no reemplaza al original.");
    }

    public List<EtapaPermitida> etapasPermitidas(UUID caso){return List.copyOf(configuracion(planDeCaso(caso)).etapas());}
    /** La evaluacion conserva su plan por caso; no crea un perfil comercial de creditos. */
    public void exigirObservacionHttp(UUID caso,String codigo,ObservacionHttpIa observacion){
        var p=planDeCaso(caso);
        var etapa=configuracion(p).etapas().stream().filter(e->codigo.equals(e.codigo())).findFirst()
            .orElseThrow(()->new ConflictoException("La observacion requiere la etapa del plan."));
        if(!p.version().equals(observacion.versionConfiguracion())
            ||!etapa.proveedor().equals(observacion.proveedor())||!etapa.modelo().equals(observacion.modelo()))
            throw new ConflictoException("La observacion conserva el modelo y configuracion autorizados.");
    }
    public void exigirEtapa(UUID caso,String etapa,String proveedor){
        var p=planDeCaso(caso);
        if(configuracion(p).etapas().stream().noneMatch(x->x.codigo().equals(etapa)&&x.proveedor().equals(proveedor)))
            throw new CapacidadDeshabilitadaException();
    }
    public boolean medicionDentroPlan(UUID caso,PresupuestoIaService.MedicionFacturable m){
        var p=planDeCaso(caso);
        return configuracion(p).etapas().stream().anyMatch(x->x.proveedor().equals(m.proveedor())&&x.modelo().equals(m.modelo())
            &&x.versionTarifa().equals(m.versionTarifa())&&m.unidades().stream().allMatch(u->x.unidades().stream()
                .anyMatch(l->l.clase().equals(u.clase())&&l.unidad().equals(u.unidad())&&u.cantidad().compareTo(l.maximo())<=0)));
    }
    private Configuracion configuracion(Plan p){return json.comoObjeto(p.configuracion(),Configuracion.class);}
    private void validarConfiguracion(Plan p){
        var c=configuracion(p);Set<String> etapas=new HashSet<>();
        if(c==null||c.etapas()==null||c.etapas().isEmpty()||c.etapas().size()>p.llamadas())throw new ReglaNegocioException("Conserva etapas y cotas del plan.");
        for(var e:c.etapas()){
            if(e==null||!texto(e.codigo())||!etapas.add(e.codigo())||!texto(e.proveedor())||!texto(e.modelo())
                ||!texto(e.versionTarifa())||e.unidades()==null||e.unidades().isEmpty())throw new ReglaNegocioException("Conserva destino y unidades por etapa.");
            Set<String> unidades=new HashSet<>();
            for(var u:e.unidades())if(u==null||!texto(u.clase())||!texto(u.unidad())||u.maximo()==null||u.maximo().signum()<0
                ||!unidades.add(u.clase()))throw new ReglaNegocioException("Conserva cotas nativas verificables.");
        }
    }
    public boolean tieneArchivoOperacion(UUID operacion){return repo.tieneArchivoOperacion(operacion);}

    @Transactional
    public void revocarFinanciacion(UUID id,String motivo,SesionPlataforma sesion){
        var a=autoridad.resolver(sesion,Atribucion.FINANCIERO_PLATFORM,Accion.REVOCAR_FINANCIACION,Campo.LEDGER);
        var f=repo.financiacion(id,false).orElseThrow(AccesoNoAutorizadoException::new);
        if(!a.plan().equals(f.plan())||!a.versionPlan().equals(f.version()))throw new AccesoNoAutorizadoException();
        if(!Objects.equals(a.fondo(),f.fondo())||!a.finalidad().equals(repo.plan(f.plan(),f.version()).orElseThrow().finalidad())||motivo==null||motivo.isBlank())throw new AccesoNoAutorizadoException();
        repo.financiacion(id,true).orElseThrow(AccesoNoAutorizadoException::new);
        repo.revocarFinanciacion(id,a.cuenta(),a.concesion(),motivo);
    }

    private static void exigirAmbito(Actual a,Caso c){
        if(!a.plan().equals(c.plan())||!a.versionPlan().equals(c.version())||a.caso()!=null&&!a.caso().equals(c.id()))
            throw new AccesoNoAutorizadoException();
    }
    @Transactional
    public Plan exigirPlanDeCaso(UUID caso,SesionPlataforma sesion,Accion accion){
        var a=autoridad.resolver(sesion,Atribucion.OPERACION_IA_PLATFORM,accion,Campo.ESTADO);
        var c=repo.caso(caso).orElseThrow(AccesoNoAutorizadoException::new);exigirAmbito(a,c);
        var p=repo.plan(c.plan(),c.version()).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!a.finalidad().equals(p.finalidad()))throw new AccesoNoAutorizadoException();return p;
    }

    private static void exigirImporteInterno(BigDecimal valor){
        if(valor==null||valor.signum()<=0||valor.precision()-valor.scale()>26
            ||com.controllocal.domain.presupuesto.PrecisionCapacidad.liquidar(valor).compareTo(valor)!=0)
            throw new ReglaNegocioException("El techo debe conservar su importe exacto en la precisión interna.");
    }
}
