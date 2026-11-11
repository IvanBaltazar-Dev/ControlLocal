package com.controllocal.service.presupuesto;

import com.controllocal.domain.presupuesto.*;
import com.controllocal.domain.presupuesto.UnidadesDeConsumo.*;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository;
import com.controllocal.persistence.presupuesto.PresupuestoIaRepository.*;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.persistence.repositorio.OportunidadComercialRepository;
import com.controllocal.service.Actor;
import com.controllocal.service.ContextosService;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.Documentos;
import com.controllocal.service.soporte.Idempotencia;
import com.controllocal.service.planes.DerechosDePlanB0;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** CAT-E38: admite financiación, no concede permiso de recurso ni ejecuta proveedores. */
@Service
public class PresupuestoIaService {
    public static final String PERFIL_MANUAL="TRABAJO_MANUAL_I0";
    public enum FinanciacionManual { SOLICITANTE,MANTENIMIENTO_BROX }
    public record ReglaManual(UUID id,String version,BigDecimal creditosPorRonda,BigDecimal cotaRondas,
                              Instant desde,Instant hasta,String evidencia,boolean habilitada,FinanciacionManual financiacion) { }
    public record SolicitudManual(UUID operacion,UUID regla,String versionRegla,Recurso recurso,String huellaContenido,
                                   BigDecimal rondasPrevistas,BigDecimal maximoAceptado) { }
    public record ConciliacionManual(long organizacion,UUID operacion,BigDecimal rondasAcreditadas,String evidencia) { }
    @org.springframework.beans.factory.annotation.Autowired private com.controllocal.persistence.presupuesto.TrabajoManualB0Repository manual;
    @org.springframework.beans.factory.annotation.Autowired private com.controllocal.service.plataforma.AtribucionesPlataforma atribuciones;
    @Value("${controllocal.investigacion.manual.habilitada:false}") private boolean manualHabilitado;
    public record Recurso(String tipo,String referencia,String finalidad,String huellaOrigen){}
    public record Solicitud(UUID operacion,String perfil,String versionPerfil,Recurso recurso,String huellaContenido){}
    /** Es la única salida de admisión: ningún importe, tarifa o modelo técnico. */
    public record Admision(UUID operacion,String estado){}
    public sealed interface ReferenciaFinanciada permits ReferenciaInterna,ReferenciaEvaluacion { UUID operacion(); }
    // Sin discriminador JSON: conserva la serialización y constructor comerciales.
    public record ReferenciaInterna(long organizacion,UUID operacion) implements ReferenciaFinanciada {}
    public record ReferenciaEvaluacion(UUID caso,UUID operacion) implements ReferenciaFinanciada {}
    /** Sobre técnico privado. B0 no interpreta su contenido C6 ni conoce imágenes/Control. */
    public record RespuestaEvaluacion(String etapa,String claveIntento,String huellaSolicitud,String contenido,
                                      String huellaContenido,String medicionOriginal,DescriptorSalidaEvaluacion descriptor){
        @Override public String toString(){return "RespuestaEvaluacion[privada]";}
    }
    public record SolicitudEvaluacion(UUID operacion,UUID caso,String versionPlan,String huellaPlan,
        UUID autorizacionFinanciera,String huellaContenido) {}
    @org.springframework.beans.factory.annotation.Autowired private EvaluacionesIaB0 evaluaciones;
    @org.springframework.beans.factory.annotation.Autowired private com.controllocal.service.planes.UsoAlmacenamientoB0 almacenamiento;
    /** Sólo evidencia comprobada por el adaptador Core: no hubo procesamiento ni cargo atribuible. */
    public record ComprobacionNoProcesamiento(String claveIntento,String proveedor,String referenciaIntento,
            String referenciaEvidencia,String huellaEvidencia,Instant comprobadaEn){}
    /** Resultado del adaptador Core; no se acepta desde herramientas/HTTP comerciales. */
    public record MedicionFacturable(UUID id,String proveedor,String modelo,String versionTarifa,List<Medicion> unidades,
            List<Tarifa> tarifasReales,BigDecimal costoConfirmadoUsd,String referenciaFuente){
        public MedicionFacturable{unidades=unidades==null?null:List.copyOf(unidades);tarifasReales=tarifasReales==null?null:List.copyOf(tarifasReales);}
    }
    public record ResultadoFacturable(List<MedicionFacturable> mediciones){
        public ResultadoFacturable{mediciones=mediciones==null?null:List.copyOf(mediciones);}
    }
    private final PresupuestoIaRepository repo;
    private final ContextoPresupuestoB0 contexto;
    private final List<AutorizadorRecursoIa> autorizadores;
    private final Documentos json;
    private final boolean gastoHabilitado;
    private final Clock reloj;
    private final DerechosDePlanB0 derechos;
    private final CostosTecnicosIaB0 costos;
    @org.springframework.beans.factory.annotation.Autowired
    public PresupuestoIaService(PresupuestoIaRepository repo,ContextoPresupuestoB0 contexto,List<AutorizadorRecursoIa> autorizadores,
            Documentos json,DerechosDePlanB0 derechos,@Value("${controllocal.ia.gasto-externo-habilitado:false}") boolean habilitado){
        this(repo,contexto,autorizadores,json,derechos,habilitado,Clock.systemUTC());
    }
    PresupuestoIaService(PresupuestoIaRepository repo,ContextoPresupuestoB0 contexto,List<AutorizadorRecursoIa> autorizadores,
            Documentos json,DerechosDePlanB0 derechos,boolean habilitado,Clock reloj){
        this.repo=repo;this.contexto=contexto;
        this.autorizadores=List.copyOf(autorizadores);this.json=json;this.derechos=derechos;this.gastoHabilitado=habilitado;this.reloj=reloj;this.costos=new CostosTecnicosIaB0(repo,json);
    }
    @Transactional
    public Admision admitir(Solicitud s,Actor actor,ContextoSesion sesion){
        validar(s);iniciarContexto(actor,sesion);reautorizar(s,actor);
        Organizacion organizacion=repo.bloquearOrganizacion(actor.idOrganizacion());
        exigirOrganizacion(organizacion);
        String hash=huellaPeticion(s,actor);
        var previa=repo.operacion(actor.idOrganizacion(),s.operacion());
        if(previa.isPresent()){exigirMisma(previa.get(),hash,actor);return admision(previa.get());}
        exigirGasto();Instant ahora=ahora();
        if(!repo.esPerfilActual(s.perfil(),s.versionPerfil()))throw new CapacidadDeshabilitadaException();
        Ciclo ciclo=pagado(actor.idOrganizacion(),ahora);
        derechos.exigirPlazaParaIa(actor,ciclo,ahora);
        Perfil perfil=perfilOperativo(s);
        Fondo fondo=repo.bloquearFondo(perfil.fondo()).orElseThrow(CapacidadDeshabilitadaException::new);
        exigirFondo(fondo,perfil.maximoTecnico(),ahora);
        List<Fuente> fuentes=repo.fuentesElegibles(actor.idOrganizacion(),actor.idPersona(),actor.esBroker(),ciclo.id(),ahora,true);
        BigDecimal suma=fuentes.stream().map(Fuente::disponible).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(suma.compareTo(perfil.maximoCreditos())<0)throw new ReglaNegocioException("No hay capacidad KAIROS disponible para esta actuación. La gestión ordinaria continúa.");
        Operacion o=new Operacion(actor.idOrganizacion(),s.operacion(),actor.idPersona(),actor.idRolOperativo(),s.perfil(),s.versionPerfil(),
                s.recurso().tipo(),s.recurso().referencia(),s.recurso().finalidad(),s.recurso().huellaOrigen(),hash,"RESERVADA",
                perfil.maximoCreditos(),perfil.maximoTecnico(),perfil.fondo(),ahora,null);
        repo.insertar(o);repo.retenerFondo(fondo.id(),o.maximoTecnico());costos.refrescar(o);
        reservarFuentes(o,fuentes);
        repo.antecedente(o.organizacion(),o.referenciaRepositorio(),"RESERVA_ADMITIDA",json.objetoCanonico(Map.of("solicitud",s,"actor",actor)),ahora);
        return admision(o);
    }
    /** CAT-R24: configuración acreditada; no configura modelos ni autoriza gasto API. */
    @Transactional
    public void configurarReglaManual(ReglaManual d,UUID decision,com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma sesion){
        if(d==null||d.id()==null||d.financiacion()==null||!texto(d.version(),80)||d.desde()==null||d.hasta()==null||!d.hasta().isAfter(d.desde())||!texto(d.evidencia(),2000))throw new ReglaNegocioException("Conserva versión, vigencia y evidencia de la regla manual.");
        if(d.financiacion()==FinanciacionManual.SOLICITANTE)decimalManual(d.creditosPorRonda(),true);
        else if(d.creditosPorRonda()!=null)throw new ReglaNegocioException("El mantenimiento de BROX no crea una tarifa o cargo al profesional.");
        rondasCompletas(d.cotaRondas(),true);
        var a=finanzasManuales(sesion,com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.CONFIGURAR_REGLA_MANUAL);
        var hash=Idempotencia.huellaExacta(json.objetoCanonico(d));
        atribuciones.acreditar(decision,"CONFIGURAR_REGLA_MANUAL_I0",hash,a.cuenta(),sesion);
        manual.insertarRegla(new com.controllocal.persistence.presupuesto.TrabajoManualB0Repository.Regla(d.id(),d.version(),"RONDA_COMPLETA",d.creditosPorRonda(),d.cotaRondas(),BigDecimal.ZERO,BigDecimal.ZERO,d.desde(),d.hasta(),decision,a.cuenta(),d.evidencia(),hash,d.habilitada(),d.financiacion().name()));
        var guardada=manual.regla(d.id(),d.version(),true).orElseThrow();
        if(!hash.equals(guardada.huella()))throw new ConflictoException("La regla manual conserva otra configuración aprobada.");
    }
    /** Reserva comercial única; la cuota, las fuentes y el ledger son los mismos de B0. */
    @Transactional
    public Admision admitirManual(SolicitudManual s,Actor actor,ContextoSesion sesion){
        var solicitud=solicitudManual(s);iniciarContexto(actor,sesion);reautorizar(solicitud,actor);
        exigirOrganizacion(repo.bloquearOrganizacion(actor.idOrganizacion()));
        String hash=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("solicitudManual",s,"actor",actor)));
        var anterior=repo.operacion(actor.idOrganizacion(),s.operacion());
        if(anterior.isPresent()){exigirMisma(anterior.get(),hash,actor);manual.manual(actor.idOrganizacion(),s.operacion(),false).orElseThrow(()->new ConflictoException("La operación conserva otra modalidad."));return admision(anterior.get());}
        if(!manualHabilitado)throw new CapacidadDeshabilitadaException();Instant ahora=ahora();
        var ciclo=pagado(actor.idOrganizacion(),ahora);derechos.exigirPlazaParaIa(actor,ciclo,ahora);
        var regla=manual.regla(s.regla(),s.versionRegla(),true).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!"SOLICITANTE".equals(regla.financiacion())||!regla.habilitada()||!contiene(regla.desde(),regla.hasta(),ahora))throw new CapacidadDeshabilitadaException();
        if(regla.cota().subtract(regla.reservadas()).subtract(regla.realizadas()).compareTo(s.rondasPrevistas())<0)
            throw new ReglaNegocioException("No hay capacidad operativa disponible para las rondas solicitadas. No se ha reservado consumo.");
        BigDecimal maximo=PrecisionCapacidad.liquidar(regla.creditosPorRonda().multiply(s.rondasPrevistas()));
        if(maximo.signum()<=0||maximo.compareTo(s.maximoAceptado())!=0)throw new ConflictoException("Revisa y acepta el máximo comercial de la regla vigente antes de comenzar.");
        var fuentes=repo.fuentesElegibles(actor.idOrganizacion(),actor.idPersona(),actor.esBroker(),ciclo.id(),ahora,true);
        if(fuentes.stream().map(Fuente::disponible).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(maximo)<0)throw new ReglaNegocioException("No hay capacidad KAIROS disponible para esta investigación. La gestión ordinaria continúa.");
        var o=new Operacion(actor.idOrganizacion(),s.operacion(),actor.idPersona(),actor.idRolOperativo(),null,null,s.recurso().tipo(),s.recurso().referencia(),s.recurso().finalidad(),s.recurso().huellaOrigen(),hash,"RESERVADA",maximo,null,null,ahora,null);
        manual.insertar(o,s.regla(),s.versionRegla(),s.rondasPrevistas());reservarFuentes(o,fuentes);
        repo.antecedente(o.organizacion(),o.id(),"RESERVA_MANUAL_ADMITIDA",json.objetoCanonico(Map.of("regla",s.regla(),"version",s.versionRegla(),"rondasPrevistas",s.rondasPrevistas(),"maximoAceptado",maximo)),ahora);
        return admision(o);
    }
    /** Sólo propietario Core I0, dentro de su actuación autorizada y de la misma transacción. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean iniciarManual(SolicitudManual s,Actor actor){
        var solicitud=solicitudManual(s);reautorizar(solicitud,actor);exigirOrganizacion(repo.bloquearOrganizacion(actor.idOrganizacion()));
        var m=manual.manual(actor.idOrganizacion(),s.operacion(),true).orElseThrow(CapacidadDeshabilitadaException::new);
        var o=repo.operacion(actor.idOrganizacion(),s.operacion()).orElseThrow();
        exigirMisma(o,Idempotencia.huellaExacta(json.objetoCanonico(Map.of("solicitudManual",s,"actor",actor))),actor);
        if(!"RESERVADA".equals(o.estado()))return false;
        if(!manualHabilitado)throw new CapacidadDeshabilitadaException();
        var ciclo=pagado(actor.idOrganizacion(),ahora());derechos.exigirPlazaParaIa(actor,ciclo,ahora());
        var r=manual.regla(m.regla(),m.version(),true).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!"SOLICITANTE".equals(r.financiacion())||!r.habilitada()||!contiene(r.desde(),r.hasta(),ahora()))throw new CapacidadDeshabilitadaException();
        return repo.iniciar(o.organizacion(),o.id(),ahora());
    }
    /** Rondas completas realmente acreditadas y revisión financiera, sin inventar costo USD o mediciones API. */
    @Transactional
    public Admision conciliarManual(ConciliacionManual d,com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma sesion){
        if(d==null||d.organizacion()<=0||d.operacion()==null||!texto(d.evidencia(),2000))throw new ReglaNegocioException("Identifica el trabajo y su evidencia de ejecución.");
        rondasCompletas(d.rondasAcreditadas(),false);
        var a=finanzasManuales(sesion,com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.CONCILIAR_TRABAJO);
        if(repo.bloquearOrganizacion(d.organizacion())==null)throw new NoEncontradoException("Organización del trabajo manual");
        var m=manual.manual(d.organizacion(),d.operacion(),true).orElseThrow(()->new NoEncontradoException("Trabajo manual"));
        var o=repo.operacion(d.organizacion(),d.operacion()).orElseThrow();
        var hash=Idempotencia.huellaExacta(json.objetoCanonico(d));
        if(Set.of("LIQUIDADA","CANCELADA").contains(o.estado())){if(!hash.equals(o.resultado()))throw new ConflictoException("La conciliación conserva su resultado anterior.");return admision(o);}
        if(d.rondasAcreditadas().compareTo(m.rondasPrevistas())>0)throw new ReglaNegocioException("El trabajo excede las rondas autorizadas; una ampliación requiere nueva aceptación.");
        var propietarios=autorizadores.stream().filter(x->x.atiende(PERFIL_MANUAL,o.tipo())).toList();if(propietarios.size()!=1)throw new CapacidadDeshabilitadaException();
        propietarios.getFirst().exigirEvidenciaManual(d.organizacion(),d.operacion(),new Recurso(o.tipo(),o.recurso(),o.finalidad(),o.origen()),d.rondasAcreditadas(),d.evidencia());
        var r=manual.regla(m.regla(),m.version(),true).orElseThrow();
        BigDecimal creditos=PrecisionCapacidad.liquidar(r.creditosPorRonda().multiply(d.rondasAcreditadas()));
        if(creditos.compareTo(o.maximoCreditos())>0)throw new IllegalStateException("La conciliación supera el máximo aceptado.");
        liquidarFuentes(o,creditos);manual.cerrar(d.organizacion(),d.operacion(),"LIQUIDADA",d.rondasAcreditadas(),creditos,hash,ahora());
        repo.antecedente(o.organizacion(),o.id(),"TRABAJO_MANUAL_CONCILIADO",json.objetoCanonico(Map.of("rondasAcreditadas",d.rondasAcreditadas(),"evidencia",d.evidencia(),"cuentaFinanciera",a.cuenta(),"concesion",a.concesion())),ahora());
        finanzasManuales(sesion,com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.CONCILIAR_TRABAJO);
        return new Admision(o.id(),"LIQUIDADA");
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean cancelarManualNoIniciada(long organizacion,UUID operacion,String evidencia){
        if(organizacion<=0||operacion==null||!texto(evidencia,2000))throw new ReglaNegocioException("Identifica la cancelación acreditada del trabajo.");
        if(repo.bloquearOrganizacion(organizacion)==null)throw new NoEncontradoException("Trabajo manual");
        var m=manual.manual(organizacion,operacion,true).orElseThrow(()->new NoEncontradoException("Trabajo manual"));
        var o=repo.operacion(organizacion,operacion).orElseThrow();
        String hash=Idempotencia.huellaExacta("CANCELACION_MANUAL_NO_INICIADA:"+evidencia);
        if("CANCELADA".equals(o.estado())){if(!hash.equals(o.resultado()))throw new ConflictoException("La cancelación conserva otro antecedente.");return false;}
        if(!"RESERVADA".equals(o.estado()))throw new ConflictoException("El trabajo pudo comenzar; conserva su evidencia y concilia la misma operación.");
        var propietarios=autorizadores.stream().filter(x->x.atiende(PERFIL_MANUAL,o.tipo())).toList();if(propietarios.size()!=1)throw new CapacidadDeshabilitadaException();
        propietarios.getFirst().exigirEvidenciaManual(organizacion,operacion,new Recurso(o.tipo(),o.recurso(),o.finalidad(),o.origen()),BigDecimal.ZERO,evidencia);
        liquidarFuentes(o,BigDecimal.ZERO);manual.cerrar(organizacion,operacion,"CANCELADA",BigDecimal.ZERO,BigDecimal.ZERO,hash,ahora());
        repo.antecedente(organizacion,operacion,"TRABAJO_MANUAL_NO_INICIADO",json.objetoCanonico(Map.of("evidencia",evidencia)),ahora());return true;
    }
    private com.controllocal.service.plataforma.AtribucionesPlataforma.Actual finanzasManuales(com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma s,com.controllocal.service.plataforma.AtribucionesPlataforma.Accion accion){
        return atribuciones.exigirMercado(s,com.controllocal.service.plataforma.AtribucionesPlataforma.Atribucion.FINANCIERO_PLATFORM,accion,Set.of(com.controllocal.service.plataforma.AtribucionesPlataforma.Campo.LEDGER));
    }
    private static Solicitud solicitudManual(SolicitudManual s){
        if(s==null||s.regla()==null||!texto(s.versionRegla(),80)||s.recurso()==null||!"INVESTIGACION_MERCADO".equals(s.recurso().tipo()))throw new ReglaNegocioException("Conserva la regla y el alcance de la investigación.");
        rondasCompletas(s.rondasPrevistas(),true);decimalManual(s.maximoAceptado(),true);
        var d=new Solicitud(s.operacion(),PERFIL_MANUAL,s.versionRegla(),s.recurso(),s.huellaContenido());validar(d);return d;
    }
    private static void rondasCompletas(BigDecimal n,boolean positivo){
        decimalManual(n,positivo);
        if(n.stripTrailingZeros().scale()>0)throw new ReglaNegocioException("Sólo se admiten rondas completas enteras; una ronda interrumpida no se prorratea.");
    }
    private static void decimalManual(BigDecimal n,boolean positivo){
        if(n==null||n.signum()<0||positivo&&n.signum()==0)throw new ReglaNegocioException("Cantidad manual no válida.");
        if(n.compareTo(PrecisionCapacidad.liquidar(n))!=0)throw new ReglaNegocioException("La cantidad manual excede la precisión admitida.");
    }
    private void reservarFuentes(Operacion o,List<Fuente> fuentes){
        BigDecimal restante=o.maximoCreditos();int orden=0;
        for(Fuente f:fuentes){if(restante.signum()==0)break;BigDecimal porcion=restante.min(f.disponible());
            repo.retenerFuente(o.organizacion(),f.id(),porcion);repo.imputar(o.organizacion(),o.referenciaRepositorio(),f.id(),porcion,++orden);restante=restante.subtract(porcion);}
        if(restante.signum()!=0)throw new IllegalStateException("La reserva no conserva sus fuentes.");
    }
    /** Estado del mismo comando autorizado; nunca admite ni crea financiación al consultar. */
    @Transactional
    public Optional<Admision> consultarEstado(Solicitud s,Actor actor,ContextoSesion sesion){
        validar(s);iniciarContexto(actor,sesion);reautorizar(s,actor);
        var encontrada=repo.operacion(actor.idOrganizacion(),s.operacion());
        encontrada.ifPresent(o->exigirMisma(o,huellaPeticion(s,actor),actor));return encontrada.map(PresupuestoIaService::admision);
    }
    /** Sólo coordinador Core; no contiene costo, cuotas, tarifa o mediciones para el profesional. */
    @Transactional(readOnly=true)
    public Optional<Admision> estadoInterno(ReferenciaFinanciada ref){
        validarReferencia(ref);return encontrar(ref).map(PresupuestoIaService::admision);
    }
    /** Sólo true autoriza al coordinador a intentar UNA ejecución. No llama una API. */
    @Transactional
    public boolean iniciar(Solicitud s,Actor actor,ContextoSesion sesion){
        validar(s);iniciarContexto(actor,sesion);reautorizar(s,actor);
        exigirOrganizacion(repo.bloquearOrganizacion(actor.idOrganizacion()));
        Operacion o=repo.operacion(actor.idOrganizacion(),s.operacion()).orElseThrow(()->new NoEncontradoException("Operación IA"));
        exigirMisma(o,huellaPeticion(s,actor),actor);
        if(!"RESERVADA".equals(o.estado()))return false;
        exigirGasto();Instant ahora=ahora();Ciclo ciclo=pagado(o.organizacion(),ahora);derechos.exigirPlazaParaIa(actor,ciclo,ahora);Perfil p=perfilOperativo(s);
        Fondo f=repo.bloquearFondo(o.fondo()).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!f.habilitado()||!contiene(f.desde(),f.hasta(),ahora)||!p.fondo().equals(o.fondo()))throw new CapacidadDeshabilitadaException();
        costos.exigirClaim(o,f);
        if(!repo.iniciar(o.organizacion(),o.referenciaRepositorio(),ahora))return false;
        repo.antecedente(o.organizacion(),o.referenciaRepositorio(),"EJECUCION_RECLAMADA","{}",ahora);return true;
    }
    @Transactional
    public CapacidadPublica consultarDisponibilidad(Actor actor,ContextoSesion sesion){
        iniciarContexto(actor,sesion);exigirOrganizacion(repo.bloquearOrganizacion(actor.idOrganizacion()));
        Instant ahora=ahora();var ciclo=repo.cicloVigente(actor.idOrganizacion(),ahora);
        if(ciclo.isEmpty()||ciclo.get().revocado())return CapacidadPublica.de(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        derechos.exigirPlazaParaIa(actor,ciclo.get(),ahora);
        var fuentes=repo.fuentesElegibles(actor.idOrganizacion(),actor.idPersona(),actor.esBroker(),ciclo.get().id(),ahora,false);
        BigDecimal mensual=BigDecimal.ZERO,boost=BigDecimal.ZERO;
        for(var f:fuentes)if("BOOST".equals(f.origen()))boost=boost.add(f.disponible());else mensual=mensual.add(f.disponible());
        return CapacidadPublica.de(mensual,repo.cuotaLegitima(actor.idOrganizacion(),actor.idPersona(),actor.esBroker(),ciclo.get().id()),boost);
    }
    /** Notificación interna del coordinador: incertidumbre nunca libera ni vuelve a reclamar. */
    @Transactional
    public void marcarIncierta(ReferenciaFinanciada ref){
        Operacion o=bloquear(ref);
        if("EN_PROCESO".equals(o.estado())){repo.incierta(o.organizacion(),o.referenciaRepositorio());
            repo.antecedente(o.organizacion(),o.referenciaRepositorio(),"RESULTADO_INCIERTO","{}",ahora());}
    }
    /** Sólo libera cuando Core sabe que el procesamiento NO comenzó. */
    @Transactional
    public Admision cancelarNoIniciada(ReferenciaFinanciada ref){
        Operacion o=bloquear(ref);
        if("CANCELADA".equals(o.estado()))return admision(o);
        if(!"RESERVADA".equals(o.estado()))throw new ConflictoException("La operación requiere conciliar su resultado original.");
        repo.bloquearFondo(o.fondo()).orElseThrow(CapacidadDeshabilitadaException::new);
        liquidar(o,BigDecimal.ZERO,BigDecimal.ZERO,"CANCELADA",Idempotencia.huellaExacta("NO_INICIADA"));
        return new Admision(o.id(),"CANCELADA");
    }
    /**
     * CAT-R13: resuelve el intento ya reclamado sólo con prueba de no procesamiento y ausencia de cargo.
     * El adaptador verifica la procedencia del proveedor; un timeout, silencio o ausencia de medición
     * no constituyen esa prueba. No es una acción comercial/HTTP ni autoriza otro claim o reintento.
     */
    @Transactional
    public Admision cancelarNoProcesada(ReferenciaFinanciada ref,ComprobacionNoProcesamiento comprobacion){
        validarComprobacion(ref,comprobacion);Operacion o=bloquear(ref);
        String contenido=json.objetoCanonico(comprobacion);
        String hash=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("hecho","NO_PROCESAMIENTO_COMPROBADO","comprobacion",comprobacion)));
        if("CANCELADA".equals(o.estado())){
            if(!hash.equals(o.resultado()))throw new ConflictoException("La operación ya conserva otra causa o prueba de cancelación.");
            return admision(o);
        }
        if(!Set.of("EN_PROCESO","INCIERTA").contains(o.estado()))throw new ConflictoException("La comprobación requiere el intento original ya reclamado.");
        Instant inicio=repo.inicio(o.organizacion(),o.referenciaRepositorio()).orElseThrow(()->new ConflictoException("La operación no conserva el inicio de su intento."));
        if(comprobacion.comprobadaEn().isBefore(inicio))throw new ConflictoException("La comprobación no corresponde al intento reclamado.");
        if(!repo.idsMediciones(o.organizacion(),o.referenciaRepositorio()).isEmpty())throw new ConflictoException("La operación conserva mediciones y requiere conciliar su resultado original.");
        repo.bloquearFondo(o.fondo()).orElseThrow(CapacidadDeshabilitadaException::new);
        repo.antecedente(o.organizacion(),o.referenciaRepositorio(),"NO_PROCESAMIENTO_COMPROBADO",contenido,ahora());
        liquidar(o,BigDecimal.ZERO,BigDecimal.ZERO,"CANCELADA",hash);
        return new Admision(o.id(),"CANCELADA");
    }
    /** Una única identidad técnica por organización y operación, compartida con el coordinador Core. */
    public static String claveDeIntento(ReferenciaFinanciada ref){
        validarReferencia(ref);
        return ref instanceof ReferenciaInterna c
            ? Idempotencia.huellaExacta("BROX-IA\u0000"+c.organizacion()+"\u0000"+c.operacion())
            : Idempotencia.huellaExacta("BROX-IA-EVALUACION\u0000"+((ReferenciaEvaluacion)ref).caso()+"\u0000"+ref.operacion());
    }
    /** Resultado técnico verificable del adaptador; registra aun si el usuario perdió acceso. */
    @Transactional
    public Admision conciliar(ReferenciaFinanciada ref,ResultadoFacturable resultado){
        if(resultado==null||resultado.mediciones()==null||resultado.mediciones().isEmpty())throw new ReglaNegocioException("Conserva la medición original de la operación.");
        Operacion o=bloquear(ref);String hash=Idempotencia.huellaExacta(json.objetoCanonico(resultado));
        if("LIQUIDADA".equals(o.estado())){
            if(!hash.equals(o.resultado()))throw new ConflictoException("La operación ya conserva otro resultado conciliado.");return admision(o);}
        // Un ajuste financiero nuevo puede resolver el exceso técnico sin cambiar el resultado original.
        // Reevaluar es local; nunca reclama/invoca de nuevo. La incidencia idéntica no se duplica abajo.
        if(!Set.of("EN_PROCESO","INCIERTA").contains(o.estado()))throw new ConflictoException("La operación no tiene una ejecución pendiente de conciliación.");
        if(repo.etapasPendientes(o.organizacion(),o.referenciaRepositorio()))throw new ConflictoException("La operación conserva etapas facturables pendientes.");
        return registrarDentro(o,resultado,true,hash,null);
    }
    /** Hechos durables por etapa: no cierra ni libera la reserva del conjunto. */
    @Transactional
    public Admision registrarMediciones(ReferenciaFinanciada ref,ResultadoFacturable resultado){
        if(resultado==null||resultado.mediciones()==null||resultado.mediciones().isEmpty())throw new ReglaNegocioException("Conserva la medición original de la operación.");
        var o=bloquear(ref);
        if("LIQUIDADA".equals(o.estado())){
            for(var m:resultado.mediciones()){
                validarMedicion(m);var h=repo.huellaMedicion(o.organizacion(),o.referenciaRepositorio(),m.id());
                if(h.isEmpty()||!h.get().equals(Idempotencia.huellaExacta(json.objetoCanonico(m))))throw new ConflictoException("La operación liquidada no admite otra medición.");
            }
            return admision(o);
        }
        if(!Set.of("EN_PROCESO","INCIERTA").contains(o.estado()))throw new ConflictoException("No hay un claim que admita esos hechos técnicos.");
        return registrarDentro(o,resultado,false,Idempotencia.huellaExacta(json.objetoCanonico(resultado)),null);
    }
    private Admision registrarDentro(Operacion o,ResultadoFacturable resultado,boolean cerrar,String hash,String etapa){
        Perfil p=o.evaluacion()?null:repo.perfil(o.perfil(),o.versionPerfil()).orElseThrow(CapacidadDeshabilitadaException::new);
        repo.bloquearFondo(o.fondo()).orElseThrow(CapacidadDeshabilitadaException::new);
        List<Tarifa> referencia=p==null?List.of():tarifas(p.tarifas());BigDecimal referenciaSinCuantizar=BigDecimal.ZERO;
        Set<UUID> ids=new HashSet<>();Set<String> intentos=new HashSet<>();Instant ahora=ahora();boolean dentroPlan=true;
        Set<UUID> anteriores=repo.idsMediciones(o.organizacion(),o.referenciaRepositorio());
        for(var m:resultado.mediciones()){
            validarMedicion(m);if(o.evaluacion()&&!evaluaciones.medicionDentroPlan(o.caso(),m))dentroPlan=false;if(!ids.add(m.id()))throw new ReglaNegocioException("La medición está duplicada.");
            String intento=Idempotencia.huellaExacta(m.proveedor()+"\u0000"+m.referenciaFuente());
            if(!intentos.add(intento))throw new ConflictoException("Una medición facturable no puede duplicarse con otra identidad.");
            BigDecimal calculado=UnidadesDeConsumo.costoSinCuantizar(m.unidades(),m.tarifasReales());
            BigDecimal real=m.costoConfirmadoUsd()==null?calculado:m.costoConfirmadoUsd();
            if(p!=null)referenciaSinCuantizar=referenciaSinCuantizar.add(UnidadesDeConsumo.costoSinCuantizar(m.unidades(),referencia));
            String hm=Idempotencia.huellaExacta(json.objetoCanonico(m));var anterior=repo.huellaMedicion(o.organizacion(),o.referenciaRepositorio(),m.id());
            if(anterior.isPresent()&&!anterior.get().equals(hm))throw new ConflictoException("La medición original ya conserva otro contenido.");
            if(anterior.isEmpty()&&!repo.medicion(o.organizacion(),o.referenciaRepositorio(),m.id(),m.proveedor(),m.modelo(),m.versionTarifa(),json.lista(m.unidades()),
                    json.lista(m.tarifasReales()),real,m.costoConfirmadoUsd()==null?"CALCULADO":"CONFIRMADO",m.referenciaFuente(),hm,intento,ahora,etapa))
                throw new ConflictoException("La medición facturable ya pertenece a otra operación o identidad.");
        }
        if(!ids.containsAll(anteriores))throw new ConflictoException("La conciliación no puede omitir mediciones anteriores de esta operación.");
        if(cerrar&&!ids.containsAll(repo.idsMedicionesEtapas(o.organizacion(),o.referenciaRepositorio())))throw new ConflictoException("El cierre conserva todas las mediciones resueltas de sus etapas.");
        // La misma proyección conserva confirmaciones previas y TODAS las mediciones recién añadidas.
        BigDecimal costo=costos.refrescar(o).efectivo();
        BigDecimal creditos=p==null?null:PrecisionCapacidad.liquidar(PrecisionCapacidad.dividir(referenciaSinCuantizar,p.normalizador()));
        int limite=p==null?evaluaciones.planDeCaso(o.caso()).llamadas():p.intentos();
        if(!dentroPlan||(p!=null&&creditos.compareTo(o.maximoCreditos())>0)||costo.compareTo(o.maximoTecnico())>0||resultado.mediciones().size()>limite){
            if(!hash.equals(o.resultado())){repo.incidenciaConResultado(o.organizacion(),o.referenciaRepositorio(),hash);repo.antecedente(o.organizacion(),o.referenciaRepositorio(),"INCIDENCIA_LIMITE",
                    json.objetoCanonico(Map.of("resultado",hash,"mediciones",ids)),ahora);}
            return new Admision(o.id(),"INCIERTA");}
        if(cerrar){liquidar(o,creditos,costo,"LIQUIDADA",hash);return new Admision(o.id(),"LIQUIDADA");}
        return admision(o);
    }
    public static String claveDeEtapa(ReferenciaFinanciada ref,String etapa){
        if(etapa==null||etapa.isBlank()||etapa.length()>80)throw new ReglaNegocioException("Identifica la etapa técnica.");
        return Idempotencia.huellaExacta(claveDeIntento(ref)+"\u0000ETAPA\u0000"+etapa);
    }
    /** Metadata del mismo conjunto financiado, no otra operación ni saldo. */
    @Transactional public void programarEtapa(ReferenciaFinanciada ref,String etapa,String proveedor,String peticion){
        var o=bloquear(ref);String clave=claveDeEtapa(ref,etapa);
        if(!texto(proveedor,100)||!hash(peticion))throw new ReglaNegocioException("Conserva destino y petición exactos de la etapa.");
        if(o.evaluacion())evaluaciones.exigirEtapa(o.caso(),etapa,proveedor);
        var anterior=repo.etapa(o.organizacion(),o.referenciaRepositorio(),clave);
        if(anterior.isPresent()){
            if(!anterior.get().proveedor().equals(proveedor)||!anterior.get().peticion().equals(peticion))throw new ConflictoException("La etapa conserva otro destino o petición.");return;
        }
        if(!Set.of("EN_PROCESO","INCIERTA").contains(o.estado()))throw new ConflictoException("La operación no admite nuevas etapas.");
        repo.programarEtapa(o.organizacion(),o.referenciaRepositorio(),etapa,clave,proveedor,peticion);
        if(repo.etapa(o.organizacion(),o.referenciaRepositorio(),clave).isEmpty())throw new ConflictoException("La etapa ya conserva otra identidad.");
    }
    @Transactional public boolean iniciarEtapa(Solicitud solicitud,String etapa,Actor actor,ContextoSesion sesion){
        validar(solicitud);iniciarContexto(actor,sesion);reautorizar(solicitud,actor);var ref=new ReferenciaInterna(actor.idOrganizacion(),solicitud.operacion());
        var o=bloquear(ref);exigirMisma(o,huellaPeticion(solicitud,actor),actor);exigirGasto();
        var e=repo.etapa(o.organizacion(),o.referenciaRepositorio(),claveDeEtapa(ref,etapa)).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!"PENDIENTE".equals(e.estado()))return false;
        if(!Set.of("EN_PROCESO","INCIERTA").contains(o.estado())||o.resultado()!=null)throw new ConflictoException("El conjunto no admite otra inferencia.");
        var ciclo=pagado(o.organizacion(),ahora());derechos.exigirPlazaParaIa(actor,ciclo,ahora());
        var p=perfilOperativo(solicitud);var f=repo.bloquearFondo(o.fondo()).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!f.habilitado()||!contiene(f.desde(),f.hasta(),ahora())||repo.idsMediciones(o.organizacion(),o.referenciaRepositorio()).size()>=p.intentos())throw new CapacidadDeshabilitadaException();
        costos.exigirClaim(o,f);
        return repo.reclamarEtapa(o.organizacion(),o.referenciaRepositorio(),e.clave());
    }
    /** El resultado incluye todas las mediciones conocidas del conjunto, sin omitir las anteriores. */
    @Transactional public void registrarResultadoEtapa(ReferenciaFinanciada ref,String etapa,ResultadoFacturable resultado){
        var o=bloquear(ref);var e=repo.etapa(o.organizacion(),o.referenciaRepositorio(),claveDeEtapa(ref,etapa)).orElseThrow(CapacidadDeshabilitadaException::new);
        String h=Idempotencia.huellaExacta(json.objetoCanonico(resultado));
        if("MEDIDA".equals(e.estado())){if(!h.equals(e.resultado()))throw new ConflictoException("La etapa conserva otro resultado.");return;}
        if(!Set.of("RECLAMADA","INCIERTA").contains(e.estado())||!Set.of("EN_PROCESO","INCIERTA").contains(o.estado()))throw new ConflictoException("La etapa no conserva ejecución pendiente.");
        var antes=repo.idsMediciones(o.organizacion(),o.referenciaRepositorio());
        if(resultado==null||resultado.mediciones()==null||resultado.mediciones().isEmpty())throw new ReglaNegocioException("Conserva la medición de esta etapa.");
        if(resultado.mediciones().stream().anyMatch(m->m==null||!antes.contains(m.id())&&!e.proveedor().equals(m.proveedor())))throw new ConflictoException("La medición pertenece a otro proveedor de etapa.");
        registrarDentro(o,resultado,false,h,e.clave());
        var nuevas=resultado.mediciones().stream().map(MedicionFacturable::id).filter(id->!antes.contains(id)).toList();
        if(nuevas.isEmpty())throw new ConflictoException("La etapa exige su medición propia.");
        repo.resolverEtapa(o.organizacion(),o.referenciaRepositorio(),e.clave(),"MEDIDA",h,json.lista(nuevas),null);
    }
    @Transactional public void marcarEtapaIncierta(ReferenciaFinanciada ref,String etapa){
        var o=bloquear(ref);var e=repo.etapa(o.organizacion(),o.referenciaRepositorio(),claveDeEtapa(ref,etapa)).orElseThrow(CapacidadDeshabilitadaException::new);
        if("INCIERTA".equals(e.estado()))return;
        if(!"RECLAMADA".equals(e.estado())||!Set.of("EN_PROCESO","INCIERTA").contains(o.estado()))throw new ConflictoException("No hay una etapa invocada pendiente.");
        repo.resolverEtapa(o.organizacion(),o.referenciaRepositorio(),e.clave(),"INCIERTA",null,null,null);repo.incierta(o.organizacion(),o.referenciaRepositorio());
    }
    @Transactional public void acreditarEtapaNoInvocada(ReferenciaFinanciada ref,String etapa,ComprobacionNoProcesamiento c){
        var o=bloquear(ref);var e=repo.etapa(o.organizacion(),o.referenciaRepositorio(),claveDeEtapa(ref,etapa)).orElseThrow(CapacidadDeshabilitadaException::new);
        String prueba=json.objetoCanonico(c);
        if("NO_INVOCADA".equals(e.estado())){if(!prueba.equals(json.objetoCanonico(json.comoMapa(e.prueba()))))throw new ConflictoException("La etapa conserva otra prueba.");return;}
        if(!Set.of("PENDIENTE","RECLAMADA","INCIERTA").contains(e.estado())||!Set.of("EN_PROCESO","INCIERTA").contains(o.estado()))throw new ConflictoException("La etapa ya está resuelta.");
        if(c==null||!e.clave().equals(c.claveIntento())||!e.proveedor().equals(c.proveedor())||!texto(c.referenciaIntento(),2000)||!texto(c.referenciaEvidencia(),2000)||!hash(c.huellaEvidencia())||c.comprobadaEn()==null
                ||c.comprobadaEn().isBefore(e.reclamada()==null?e.programada():e.reclamada())||c.comprobadaEn().isAfter(repo.ahoraEtapas()))throw new ReglaNegocioException("Conserva la prueba Core de esta etapa no invocada.");
        repo.resolverEtapa(o.organizacion(),o.referenciaRepositorio(),e.clave(),"NO_INVOCADA",null,null,prueba);
    }
    /** Retirada Core: CAS sólo de etapa nunca reclamada; una obligación en curso no se cancela. */
    @Transactional public boolean acreditarEtapaNoReclamada(ReferenciaFinanciada ref,String etapa,ComprobacionNoProcesamiento c){
        var o=bloquear(ref);var e=repo.etapa(o.organizacion(),o.referenciaRepositorio(),claveDeEtapa(ref,etapa)).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!"PENDIENTE".equals(e.estado())||e.reclamada()!=null)return false;
        acreditarEtapaNoInvocada(ref,etapa,c);return true;
    }
    private void liquidar(Operacion o,BigDecimal creditos,BigDecimal costo,String estado,String hash){
        if(o.fondo()==null)throw new CapacidadDeshabilitadaException();
        liquidarFuentes(o,creditos);
        costos.imputarCierre(o,costo);
        repo.liquidarFondo(o.fondo(),o.maximoTecnico(),costo);Instant ahora=ahora();
        repo.cerrar(o.organizacion(),o.referenciaRepositorio(),estado,o.evaluacion()?null:creditos,costo,hash,ahora);
        repo.antecedente(o.organizacion(),o.referenciaRepositorio(),estado,"{}",ahora);
        if(o.evaluacion()&&"CANCELADA".equals(estado))almacenamiento.cancelarEvaluacionNoAlmacenada(new ReferenciaEvaluacion(o.caso(),o.id()),hash);
    }
    private void liquidarFuentes(Operacion o,BigDecimal creditos){
        BigDecimal restante=o.evaluacion()?BigDecimal.ZERO:PrecisionCapacidad.liquidar(creditos),sumado=BigDecimal.ZERO;
        List<Imputacion> fuentes=o.evaluacion()?List.of():repo.imputaciones(o.organizacion(),o.id());
        if(!o.evaluacion()&&fuentes.isEmpty())throw new IllegalStateException("La reserva no conserva sus fuentes.");
        for(var i:fuentes){BigDecimal consumir=restante.min(i.retenido());repo.liquidarFuente(o.organizacion(),o.referenciaRepositorio(),i,consumir);
            restante=restante.subtract(consumir);sumado=sumado.add(consumir);}
        if(restante.signum()!=0||!o.evaluacion()&&sumado.compareTo(creditos)!=0)throw new IllegalStateException("La imputación no conserva el total liquidado.");
    }
    private Optional<Operacion> encontrar(ReferenciaFinanciada ref){
        validarReferencia(ref);
        return ref instanceof ReferenciaInterna c?repo.operacion(c.organizacion(),c.operacion()):
            repo.evaluacion(((ReferenciaEvaluacion)ref).caso(),ref.operacion());
    }
    /** Conserva el hecho del claim ya comprometido, incluso tras revocacion o incertidumbre.
     * No admite trabajo, liquida dinero, autoriza entrega ni vuelve a invocar un proveedor. */
    @Transactional
    public void registrarObservacionHttp(ReferenciaFinanciada ref,String etapa,ObservacionHttpIa observacion){
        if(observacion==null)throw new ReglaNegocioException("Conserva la observacion del intento.");
        var o=bloquear(ref);
        String clave=etapa==null?claveDeIntento(ref):claveDeEtapa(ref,etapa);
        if(!clave.equals(observacion.claveIntento()))throw new ConflictoException("La observacion pertenece a otro intento.");
        if(!o.evaluacion()&&!o.versionPerfil().equals(observacion.versionConfiguracion()))
            throw new ConflictoException("La observacion conserva la configuracion comprometida.");
        if(etapa==null){
            if(repo.inicio(o.organizacion(),o.referenciaRepositorio()).isEmpty())
                throw new ConflictoException("La observacion requiere un intento comprometido.");
        }else{
            var e=repo.etapa(o.organizacion(),o.referenciaRepositorio(),clave).orElseThrow(()->new ConflictoException("La etapa no fue comprometida."));
            if(e.reclamada()==null||!e.proveedor().equals(observacion.proveedor()))
                throw new ConflictoException("La observacion conserva el proveedor y claim de la etapa.");
        }
        if(o.evaluacion()){
            String codigo=etapa==null?"GENERACION":etapa;
            evaluaciones.exigirObservacionHttp(o.caso(),codigo,observacion);
        }
        String contenido=json.objetoCanonico(observacion);
        var anterior=repo.observacionHttp(o.organizacion(),o.referenciaRepositorio(),clave);
        if(anterior.isPresent()){
            if(!contenido.equals(json.objetoCanonico(json.comoObjeto(anterior.get(),ObservacionHttpIa.class))))
                throw new ConflictoException("El intento conserva otra observacion.");
            return;
        }
        repo.antecedente(o.organizacion(),o.referenciaRepositorio(),"OBSERVACION_HTTP_IA",contenido,ahora());
    }
    Operacion bloquear(ReferenciaFinanciada ref){
        validarReferencia(ref);
        if(ref instanceof ReferenciaInterna c&&repo.bloquearOrganizacion(c.organizacion())==null)
            throw new NoEncontradoException("Operación IA");
        Operacion o=encontrar(ref).orElseThrow(()->new NoEncontradoException("Operación IA"));
        if(!o.evaluacion()&&o.perfil()==null)throw new CapacidadDeshabilitadaException();
        if(ref instanceof ReferenciaEvaluacion v)almacenamiento.bloquearHechoEvaluacion(v);
        repo.bloquearOperacion(o.organizacion(),o.referenciaRepositorio());
        return repo.operacion(o.organizacion(),o.referenciaRepositorio()).orElseThrow();
    }
    private void iniciarContexto(Actor actor,ContextoSesion sesion){
        contexto.iniciarConsumo(actor,sesion);
    }
    private void reautorizar(Solicitud s,Actor a){
        var capaces=autorizadores.stream().filter(x->x.atiende(s.perfil(),s.recurso().tipo())).toList();
        if(capaces.size()!=1)throw new CapacidadDeshabilitadaException();capaces.getFirst().reautorizar(s,a);
    }
    private Ciclo pagado(long org,Instant ahora){
        var ciclo=repo.cicloVigente(org,ahora).orElseThrow(CapacidadDeshabilitadaException::new);
        if(ciclo.revocado())throw new CapacidadDeshabilitadaException();return ciclo;
    }
    private Perfil perfilOperativo(Solicitud s){
        var p=repo.perfil(s.perfil(),s.versionPerfil()).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!p.habilitado())throw new CapacidadDeshabilitadaException();return p;
    }
    private void exigirFondo(Fondo f,BigDecimal maximo,Instant ahora){
        if(!f.habilitado()||!contiene(f.desde(),f.hasta(),ahora)||f.financiado().subtract(f.retenido()).subtract(f.liquidado()).compareTo(maximo)<0)
            throw new CapacidadDeshabilitadaException();
        costos.exigirDisponible(f,maximo);
    }
    private static void exigirOrganizacion(Organizacion o){if(o==null||!o.activa())throw new AccesoNoAutorizadoException();}
    /** Proyección interna de configuración; no admite gasto, recurso, perfil ni financiación. */
    public boolean gastoExternoHabilitado(){return gastoHabilitado;}
    private void exigirGasto(){if(!gastoHabilitado)throw new CapacidadDeshabilitadaException();}
    private String huellaPeticion(Solicitud s,Actor a){return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("solicitud",s,"actor",a)));}
    private static void exigirMisma(Operacion o,String hash,Actor a){
        if(o.persona()!=a.idPersona()||o.rol()!=a.idRolOperativo())throw new AccesoNoAutorizadoException();
        if(!o.peticion().equals(hash))throw new ConflictoException("La operación ya identifica otra solicitud.");
    }
    private List<Tarifa> tarifas(String contenido){return List.of(json.comoObjeto(contenido,Tarifa[].class));}
    private static Admision admision(Operacion o){return new Admision(o.id(),o.estado());}
    private Instant ahora(){return reloj.instant().truncatedTo(ChronoUnit.MICROS);}
    private static boolean contiene(Instant a,Instant b,Instant c){return !c.isBefore(a)&&c.isBefore(b);}
    private static void validar(Solicitud s){
        if(s==null||s.operacion()==null||s.recurso()==null||!texto(s.perfil(),60)||!texto(s.versionPerfil(),80)
                ||!texto(s.recurso().tipo(),60)||!texto(s.recurso().referencia(),100)||!texto(s.recurso().finalidad(),100)
                ||!hash(s.recurso().huellaOrigen())||!hash(s.huellaContenido()))throw new ReglaNegocioException("Identifica la actuación y sus versiones exactas.");
    }
    private static void validarMedicion(MedicionFacturable m){
        if(m==null||m.id()==null||!texto(m.proveedor(),100)||!texto(m.modelo(),150)||!texto(m.versionTarifa(),100)
                ||m.unidades()==null||m.tarifasReales()==null||!texto(m.referenciaFuente(),2000)
                ||m.costoConfirmadoUsd()!=null&&m.costoConfirmadoUsd().signum()<0)
            throw new ReglaNegocioException("Conserva unidades, tarifa y procedencia del resultado técnico.");
    }
    private static boolean texto(String s,int max){return s!=null&&!s.isBlank()&&s.length()<=max&&s.equals(s.trim());}
    private static boolean hash(String s){return s!=null&&s.matches("[0-9a-f]{64}");}
    private static void validarReferencia(ReferenciaFinanciada ref){
        if(ref==null||ref.operacion()==null||ref instanceof ReferenciaInterna c&&c.organizacion()<=0||ref instanceof ReferenciaEvaluacion v&&v.caso()==null)throw new ReglaNegocioException("Referencia de operación requerida.");
    }
    private void validarComprobacion(ReferenciaFinanciada ref,ComprobacionNoProcesamiento c){
        String clave=claveDeIntento(ref);
        if(c==null||!hash(c.claveIntento())||!texto(c.proveedor(),100)||!texto(c.referenciaIntento(),2000)
                ||!texto(c.referenciaEvidencia(),2000)||!hash(c.huellaEvidencia())||c.comprobadaEn()==null
                ||c.comprobadaEn().isAfter(reloj.instant()))
            throw new ReglaNegocioException("Conserva la comprobación y procedencia del intento no procesado.");
        if(!clave.equals(c.claveIntento()))throw new ConflictoException("La comprobación pertenece a otro intento u organización.");
    }

    /** Sólo callback/replay interno; no es permiso de lectura comercial o QA. */
    @Transactional
    public Optional<RespuestaEvaluacion> leerRespuestaEvaluacion(ReferenciaEvaluacion ref,String etapa){
        var o=bloquear(ref);String clave=etapa==null?claveDeIntento(ref):claveDeEtapa(ref,etapa);
        var filas=repo.respuestaEvaluacion(o.tecnica(),clave).stream().map(x->json.comoObjeto(x,RespuestaEvaluacion.class)).toList();
        if(filas.size()>1)throw new IllegalStateException("La respuesta técnica conserva una duplicidad.");
        var r=filas.stream().findFirst();
        r.ifPresent(x->{if(!Objects.equals(etapa,x.etapa())||!clave.equals(x.claveIntento())||!o.peticion().equals(x.huellaSolicitud())
                ||x.contenido()==null||!Idempotencia.huellaExacta(x.contenido()).equals(x.huellaContenido())||x.medicionOriginal()==null)
            throw new ConflictoException("El sobre no conserva la identidad y huella de la respuesta.");});
        return r;
    }
    /** Conserva respuesta y medición originales juntas sin cerrar ni invocar otra etapa. */
    @Transactional
    public RespuestaEvaluacion conservarRespuestaEvaluacion(ReferenciaEvaluacion ref,String etapa,String clave,
                                                            String contenido,ResultadoFacturable medicion,DescriptorSalidaEvaluacion descriptor){
        var o=bloquear(ref);String esperada=etapa==null?claveDeIntento(ref):claveDeEtapa(ref,etapa);
        if(!esperada.equals(clave)||contenido==null||medicion==null||medicion.mediciones()==null||medicion.mediciones().isEmpty())
            throw new ReglaNegocioException("Conserva la respuesta medida del mismo claim.");
        Object objeto=json.comoObjeto(contenido,java.util.Map.class);
        if(!contenido.equals(json.objetoCanonico(objeto)))throw new ReglaNegocioException("La respuesta requiere su representación canónica.");
        if(etapa!=null&&descriptor!=null)throw new ReglaNegocioException("La etapa no modifica el descriptor original.");
        var respuesta=new RespuestaEvaluacion(etapa,clave,o.peticion(),contenido,Idempotencia.huellaExacta(contenido),json.objetoCanonico(medicion),descriptor);
        var previas=repo.respuestaEvaluacion(o.tecnica(),clave).stream().map(x->json.comoObjeto(x,RespuestaEvaluacion.class)).toList();
        if(previas.size()>1)throw new IllegalStateException("La respuesta técnica conserva una duplicidad.");
        if(!previas.isEmpty()){
            var previa=previas.getFirst();if(!json.objetoCanonico(previa).equals(json.objetoCanonico(respuesta)))
                throw new ConflictoException("El claim conserva otra respuesta técnica.");return previa;
        }
        if(etapa==null)registrarMediciones(ref,medicion);
        else{
            var anteriores=repo.respuestasEvaluacion(o.tecnica()).stream().map(x->json.comoObjeto(x,RespuestaEvaluacion.class)).toList();
            var todas=new ArrayList<MedicionFacturable>();for(var previa:anteriores)todas.addAll(medicionRespuestaEvaluacion(previa).mediciones());
            todas.addAll(medicion.mediciones());registrarResultadoEtapa(ref,etapa,new ResultadoFacturable(todas));
        }
        repo.antecedente(null,o.tecnica(),"RESPUESTA_EVALUACION",json.objetoCanonico(respuesta),ahora());return respuesta;
    }
    @Transactional
    public Optional<String> estadoEtapaEvaluacion(ReferenciaEvaluacion ref,String etapa){
        var o=bloquear(ref);return repo.etapa(null,o.tecnica(),claveDeEtapa(ref,etapa)).map(com.controllocal.persistence.presupuesto.PresupuestoIaRepository.Etapa::estado);
    }
    public ResultadoFacturable medicionRespuestaEvaluacion(RespuestaEvaluacion r){
        return json.comoObjeto(r.medicionOriginal(),ResultadoFacturable.class);
    }
    @Transactional
    public ResultadoFacturable conjuntoEvaluacion(ReferenciaEvaluacion ref){
        var o=bloquear(ref);var mediciones=new ArrayList<MedicionFacturable>();
        for(String r:repo.respuestasEvaluacion(o.tecnica()))mediciones.addAll(medicionRespuestaEvaluacion(json.comoObjeto(r,RespuestaEvaluacion.class)).mediciones());
        return new ResultadoFacturable(mediciones);
    }

    @Transactional
    public Admision admitirEvaluacion(SolicitudEvaluacion s,com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma sesion){
        validar(s);
        var a=evaluaciones.exigir(s.caso(),s.versionPlan(),s.huellaPlan(),s.autorizacionFinanciera(),sesion,
            com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.ADMITIR);
        String hash=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("solicitud",s,"cuenta",a.autoridad().cuenta(),"concesion",a.autoridad().concesion())));
        var previa=repo.evaluacion(s.caso(),s.operacion());
        if(previa.isPresent()){mismaEvaluacion(previa.get(),hash,a.autoridad().cuenta());return admision(previa.get());}
        exigirGasto();evaluaciones.exigirCupo(a);
        
        Operacion o=new Operacion(null,s.operacion(),null,null,null,null,"EVALUACION",s.caso().toString(),
            a.plan().finalidad(),s.huellaPlan(),hash,"RESERVADA",null,a.plan().maximoOperacion(),a.financiacion().fondo(),ahora(),null,
            UUID.randomUUID(),s.caso(),a.autoridad().cuenta(),a.autoridad().concesion(),a.financiacion().id());
        repo.insertar(o);
        almacenamiento.previsionarEvaluacion(new ReferenciaEvaluacion(s.caso(),s.operacion()),a.plan().namespace(),sesion);
        Fondo f=repo.bloquearFondo(o.fondo()).orElseThrow(CapacidadDeshabilitadaException::new);exigirFondo(f,o.maximoTecnico(),ahora());repo.retenerFondo(f.id(),o.maximoTecnico());costos.refrescar(o);evaluaciones.exigirPresupuestoActual(a);
        repo.antecedente(null,o.tecnica(),"RESERVA_ADMITIDA",json.objetoCanonico(Map.of("solicitud",s,"cuenta",o.cuenta(),"concesion",o.concesion())),ahora());
        return admision(o);
    }
    @Transactional
    public boolean iniciarEvaluacion(SolicitudEvaluacion s,com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma sesion){
        validar(s);var a=evaluaciones.exigir(s.caso(),s.versionPlan(),s.huellaPlan(),s.autorizacionFinanciera(),sesion,
            com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.INICIAR);
        Operacion o=bloquear(new ReferenciaEvaluacion(s.caso(),s.operacion()));
        String hash=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("solicitud",s,"cuenta",o.cuenta(),"concesion",o.concesion())));
        if(!o.peticion().equals(hash)||!o.autorizacion().equals(a.financiacion().id()))throw new ConflictoException("La actuación conserva otra solicitud admitida.");if(!"RESERVADA".equals(o.estado()))return false;
        exigirGasto();almacenamiento.exigirAntesDeClaimEvaluacion(new ReferenciaEvaluacion(s.caso(),s.operacion()),sesion);Fondo f=repo.bloquearFondo(o.fondo()).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!f.habilitado()||!contiene(f.desde(),f.hasta(),ahora())||!f.id().equals(a.financiacion().fondo()))throw new CapacidadDeshabilitadaException();
        costos.exigirClaim(o,f);evaluaciones.exigirPresupuestoActual(a);
        if(!repo.iniciar(null,o.tecnica(),ahora()))return false;
        repo.antecedente(null,o.tecnica(),"EJECUCION_RECLAMADA",json.objetoCanonico(Map.of("cuenta",a.autoridad().cuenta(),"concesion",a.autoridad().concesion())),ahora());return true;
    }
    @Transactional
    public boolean iniciarEtapaEvaluacion(SolicitudEvaluacion s,String etapa,com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma sesion){
        validar(s);var a=evaluaciones.exigir(s.caso(),s.versionPlan(),s.huellaPlan(),s.autorizacionFinanciera(),sesion,
            com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.INICIAR);
        var ref=new ReferenciaEvaluacion(s.caso(),s.operacion());var o=bloquear(ref);exigirGasto();
        if(!o.autorizacion().equals(a.financiacion().id())||!Set.of("EN_PROCESO","INCIERTA").contains(o.estado())||o.resultado()!=null)throw new ConflictoException("El conjunto no admite otra inferencia.");
        var e=repo.etapa(null,o.tecnica(),claveDeEtapa(ref,etapa)).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!"PENDIENTE".equals(e.estado()))return false;
        Fondo f=repo.bloquearFondo(o.fondo()).orElseThrow(CapacidadDeshabilitadaException::new);
        if(!f.habilitado()||!contiene(f.desde(),f.hasta(),ahora())||repo.idsMediciones(null,o.tecnica()).size()>=a.plan().llamadas())throw new CapacidadDeshabilitadaException();
        costos.exigirClaim(o,f);evaluaciones.exigirPresupuestoActual(a);
        return repo.reclamarEtapa(null,o.tecnica(),e.clave());
    }
    @Transactional
    public Optional<Admision> consultarEstadoEvaluacion(SolicitudEvaluacion s,com.controllocal.service.plataforma.AtribucionesPlataforma.SesionPlataforma sesion){
        validar(s);var p=evaluaciones.exigirPlanDeCaso(s.caso(),sesion,
            com.controllocal.service.plataforma.AtribucionesPlataforma.Accion.CONSULTAR_ESTADO);
        if(!p.version().equals(s.versionPlan())||!p.huella().equals(s.huellaPlan()))throw new ConflictoException("La consulta conserva otra versión.");
        var o=repo.evaluacion(s.caso(),s.operacion());
        o.ifPresent(x->{if(!x.autorizacion().equals(s.autorizacionFinanciera()))throw new ConflictoException("La consulta conserva otra autorización.");});
        return o.map(PresupuestoIaService::admision);
    }
    private static void mismaEvaluacion(Operacion o,String hash,long cuenta){
        if(!o.evaluacion()||!Objects.equals(o.cuenta(),cuenta))throw new AccesoNoAutorizadoException();
        if(!o.peticion().equals(hash))throw new ConflictoException("La operación conserva otra solicitud.");
    }
    private static void validar(SolicitudEvaluacion s){
        if(s==null||s.operacion()==null||s.caso()==null||!texto(s.versionPlan(),80)||!hash(s.huellaPlan())
            ||s.autorizacionFinanciera()==null||!hash(s.huellaContenido()))throw new ReglaNegocioException("Conserva caso, plan y financiación exactos.");
    }
}
