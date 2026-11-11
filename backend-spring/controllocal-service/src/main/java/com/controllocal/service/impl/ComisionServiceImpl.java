package com.controllocal.service.impl;

import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.comercial.*;
import com.controllocal.domain.inmueble.OperacionInmobiliaria;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.encargo.EncargoService.Tramo;
import com.controllocal.service.encargo.PactosDeEncargo;
import com.controllocal.service.encargo.PactosDeEncargo.*;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.formalizacion.HitosConfirmados;
import com.controllocal.service.formalizacion.HitosConfirmados.*;
import com.controllocal.service.formalizacion.ReferenciasDeMovimiento;
import com.controllocal.service.soporte.*;
import jakarta.persistence.EntityManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Spec8/RF062–064: una raíz por resultado, fuentes inmutables y fondos reales separados. */
@Service
public class ComisionServiceImpl implements ComisionService {
    public static final String EVALUAR="EVALUAR_COMISION";
    public static final String REPARTIR="ASIGNAR_REPARTO_COMISION";
    public static final String COBRAR="REGISTRAR_COBRO_COMISION";
    public static final String REPROGRAMAR="reprogramar_gestion_cobro";
    public static final String CONCLUIR="concluir_gestion_cobro";
    private final ComisionLiquidacionRepository comisiones;
    private final ComisionMovimientoRepository movimientos;
    private final ComisionEvaluacionRepository evaluaciones;
    private final ComisionTramoRepository tramos;
    private final PactosDeEncargo pactos;
    private final HitosConfirmados hitos;
    private final ReferenciasDeMovimiento referencias;
    private final AccesoEncargo acceso;
    private final Alcances alcances;
    private final ComandosIdempotentes comandos;
    private final ComandoIdempotenteRepository recibos;
    private final Documentos documentos;
    private final ObjectMapper json;
    private final AlertaService alertas;
    private final EntityManager em;
    private final Clock reloj;
    private final com.controllocal.persistence.repositorio.EventoDominioRepository eventos;

    @Autowired
    public ComisionServiceImpl(ComisionLiquidacionRepository comisiones,ComisionMovimientoRepository movimientos,
            ComisionEvaluacionRepository evaluaciones,ComisionTramoRepository tramos,PactosDeEncargo pactos,
            HitosConfirmados hitos,ReferenciasDeMovimiento referencias,AccesoEncargo acceso,Alcances alcances,
            ComandosIdempotentes comandos,ComandoIdempotenteRepository recibos,Documentos documentos,
            ObjectMapper json,AlertaService alertas,EntityManager em,com.controllocal.persistence.repositorio.EventoDominioRepository eventos) {
        this(comisiones,movimientos,evaluaciones,tramos,pactos,hitos,referencias,acceso,alcances,comandos,
                recibos,documentos,json,alertas,em,eventos,Clock.systemUTC());
    }
    ComisionServiceImpl(ComisionLiquidacionRepository comisiones,ComisionMovimientoRepository movimientos,
            ComisionEvaluacionRepository evaluaciones,ComisionTramoRepository tramos,PactosDeEncargo pactos,
            HitosConfirmados hitos,ReferenciasDeMovimiento referencias,AccesoEncargo acceso,Alcances alcances,
            ComandosIdempotentes comandos,ComandoIdempotenteRepository recibos,Documentos documentos,
            ObjectMapper json,AlertaService alertas,EntityManager em,com.controllocal.persistence.repositorio.EventoDominioRepository eventos,Clock reloj) {
        this.comisiones=comisiones;this.movimientos=movimientos;this.evaluaciones=evaluaciones;this.tramos=tramos;
        this.pactos=pactos;this.hitos=hitos;this.referencias=referencias;this.acceso=acceso;this.alcances=alcances;
        this.comandos=comandos;this.recibos=recibos;this.documentos=documentos;this.json=json;
        this.alertas=alertas;this.eventos=eventos;this.em=em;this.reloj=reloj;
    }

    @Override @Transactional(readOnly=true)
    public ResumenEconomico resumenDePeriodo(LocalDate desde,LocalDate hasta,java.time.Instant corte,FiltroEconomico filtro,Actor actor) {
        Alcances.exigirOperador(actor);
        if(desde==null||hasta==null||desde.isAfter(hasta)||corte==null||hasta.isAfter(corte.atZone(java.time.ZoneId.of("America/Lima")).toLocalDate()))throw regla("Período y corte válidos requeridos.");
        var scope=alcances.de(actor);var filas=comisiones.enAlcance(actor.idOrganizacion(),scope.paramRoles());
        var versiones=filas.stream().map(c->List.of(c.getId(),c.getVersion())).toList();
        if(filtro!=null){if(filtro.operacion()!=null&&!java.util.Set.of("VENTA","ALQUILER").contains(filtro.operacion()))throw regla("Operación económica no reconocida.");if(filtro.encargos()!=null)for(long id:filtro.encargos())acceso.exigirLectura(id,actor);}
        var calculado=new java.util.TreeMap<String,BigDecimal>();var devengado=new java.util.TreeMap<String,BigDecimal>();var cobrado=new java.util.TreeMap<String,BigDecimal>();int sinCuantia=0;
        int casos=0,exigibles=0,noExigibles=0,sinExigibilidad=0;
        for(var c:filas){
            if(filtro!=null&&filtro.encargos()!=null&&!filtro.encargos().contains(c.getEncargo()))continue;
            var f=privacidadActual(fichaActual(c,actor),actor);
            var operacion=hitos.consultarContexto(new ReferenciaContextoEconomico(c.getContextoEconomico()),actor).operacion().name();
            if(filtro!=null&&filtro.operacion()!=null&&!filtro.operacion().equals(operacion))continue;
            casos++;if(f.exigibilidad().exigible()==null)sinExigibilidad++;else if(f.exigibilidad().exigible())exigibles++;else noExigibles++;
            if(f.calculo().cuantia().importe()==null)sinCuantia++;else calculado.merge(operacion+":"+f.calculo().cuantia().moneda(),f.calculo().cuantia().importe(),BigDecimal::add);
            var d=f.devengo().cuantiaAcreditada();if(d!=null&&d.importe()!=null)devengado.merge(operacion+":"+d.moneda(),d.importe(),BigDecimal::add);
            for(var m:movimientos(c,actor))if(ComisionMovimiento.COBRO.equals(m.getTipo())&&!m.getFecha().isBefore(desde)&&!m.getFecha().isAfter(hasta)
                &&m.getFechaCreacion()!=null&&!m.getFechaCreacion().toInstant().isAfter(corte))cobrado.merge(operacion+":"+m.getMoneda(),m.getMonto(),BigDecimal::add);
            acceso.exigirLectura(c.getEncargo(),actor);
        }
        var ultimas=comisiones.versionesEnAlcance(actor.idOrganizacion(),alcances.de(actor).paramRoles()).stream().map(c->List.of(c.getId(),c.getVersion())).toList();
        if(!versiones.equals(ultimas))throw new ConflictoException("Cambiaron fuentes o permisos de la comisión durante el resumen.");
        return new ResumenEconomico(desde,hasta,corte,dinero(calculado),dinero(devengado),dinero(cobrado),casos,sinCuantia,new CoberturaExigibilidad(exigibles,noExigibles,sinExigibilidad));
    }
    private static List<ImportePorOperacion> dinero(java.util.Map<String,BigDecimal> importes){return importes.entrySet().stream().map(e->{var clave=e.getKey().split(":",2);return new ImportePorOperacion(clave[0],e.getValue(),clave[1]);}).toList();}

    @Override @Transactional(readOnly=true)
    public GestionRadar gestionParaRadar(ReferenciaPacto pacto,ReferenciaContextoEconomico contexto,Actor actor){
        validarReferencia(pacto);
        if(actor==null||contexto==null)throw new AccesoNoAutorizadoException();
        var responsable=acceso.contexto(pacto.encargo(),actor).getResponsable();
        if(!acceso.paraDecision(actor).rolesAgente().contains(responsable))throw new AccesoNoAutorizadoException();
        var f=consultar(pacto,contexto,actor);
        if(f.id()==null)return new GestionRadar(false,null,null,List.of("COMISION_NO_REALIZADA"));
        var c=comisiones.findByOrganizacionIdAndId(actor.idOrganizacion(),f.id()).orElseThrow(()->new NoEncontradoException("Comisión"));
        autorizarDecision(c,actor);
        var resultado=evaluarGestionRadar(c,f,actor);
        if(!Objects.equals(comisiones.versionActualParaRadar(actor.idOrganizacion(),c.getId()),f.version()))throw new ConflictoException("Cambió la fuente de la gestión de comisión.");
        autorizarDecision(c,actor);return resultado;
    }
    private GestionRadar evaluarGestionRadar(ComisionLiquidacion c,FichaComision f,Actor actor){
        if(f.gestion().concluida())return new GestionRadar(false,false,null,List.of());
        boolean base=f.calculo().cuantia().importe()==null&&!f.calculo().faltantes().isEmpty()
            ||f.devengo().faltantes().contains("CONDICION_DEVENGO_NO_EJECUTABLE")
            ||f.exigibilidad().faltantes().contains("CONDICION_EXIGIBILIDAD_NO_EJECUTABLE");
        if(base)return new GestionRadar(true,null,null,java.util.stream.Stream.of(f.calculo().faltantes(),f.devengo().faltantes(),f.exigibilidad().faltantes()).flatMap(Collection::stream).distinct().toList());
        var movimientos=movimientos(c,actor);var saldo=saldos(c,movimientos);var recibido=recibidoComparable(saldo.recibidos(),c.getMoneda());
        var devengado=f.devengo().cuantiaAcreditada().importe();
        if("PENDIENTE".equals(f.devengo().estado()))return new GestionRadar(false,false,null,List.of());
        if(recibido==null||saldo.saldoInformativo()==null||devengado==null)return new GestionRadar(false,null,null,List.of("SALDO_NO_COMPARABLE"));
        BigDecimal disponible=devengado.subtract(recibido).min(saldo.saldoInformativo()).max(BigDecimal.ZERO);
        if(disponible.signum()==0)return new GestionRadar(false,false,null,List.of());
        LocalDate fecha=null;boolean exigible=false;
        if(f.tramos().isEmpty()){
            if(f.exigibilidad().exigible()==null)return new GestionRadar(false,null,null,f.exigibilidad().faltantes());
            exigible=Boolean.TRUE.equals(f.exigibilidad().exigible());fecha=f.exigibilidad().fechaAcreditada();
        }else{
            // Un cobro sin tramo no se distribuye por suposición entre hitos contractuales.
            if(movimientos.stream().anyMatch(m->m.getTramo()==null&&Set.of("C","R").contains(m.getTipo())))
                return new GestionRadar(false,null,f.gestion().proximaRevision(),List.of("COBROS_SIN_TRAMO_ACREDITADO"));
            for(var t:f.tramos()){
                if(!Boolean.TRUE.equals(t.exigibilidad().exigible())||"PENDIENTE".equals(t.devengo().estado()))continue;
                var cuantia=t.devengo().cuantiaAcreditada().importe();
                var cobrado=recibidoComparable(saldos(c,movimientos.stream().filter(m->Objects.equals(m.getTramo(),t.id())).toList()).recibidos(),c.getMoneda());
                if(cuantia==null||cobrado==null)return new GestionRadar(false,null,null,List.of("TRAMO_NO_COMPARABLE"));
                if(cuantia.subtract(cobrado).signum()>0){exigible=true;var d=t.exigibilidad().fechaAcreditada();if(d!=null&&(fecha==null||d.isBefore(fecha)))fecha=d;}
            }
        }
        return new GestionRadar(false,exigible,exigible?(f.gestion().proximaRevision()!=null?f.gestion().proximaRevision():fecha):null,List.of());
    }

    @Override @Transactional(readOnly=true)
    public FichaComision consultar(ReferenciaPacto referencia,ReferenciaContextoEconomico contexto,Actor actor) {
        validarReferencia(referencia);
        var encargo=acceso.leer(referencia.encargo(),actor);
        var pacto=leerPacto(referencia,actor);
        if(contexto==null) {
            var material=material(pacto,encargo.operacion(),null,hoy(),actor);
            return privacidadActual(ficha(null,pacto,contexto,material,List.of(),List.of(),null),actor);
        }
        var ctx=contexto(contexto,referencia.encargo(),actor);
        var c=comisiones.findByOrganizacionIdAndContextoEconomico(actor.idOrganizacion(),contexto.id());
        if(c.isPresent()) {
            if(!referencia.equals(referencia(c.get())))
                throw new ConflictoException("La comisión conserva otro pacto exacto; consulta su referencia vigente.");
            return privacidadActual(fichaActual(c.get(),actor),actor);
        }
        return privacidadActual(ficha(null,pacto,contexto,material(pacto,ctx.operacion(),ctx,hoy(),actor),List.of(),List.of(),null),actor);
    }

    @Override @Transactional(readOnly=true)
    public Optional<FichaComision> porContratoAlquiler(long id,Actor actor) {
        return comisiones.findByOrganizacionIdAndContratoId(actor.idOrganizacion(),id).map(c->{
            exigirFuente(c);acceso.exigirLectura(c.getEncargo(),actor);
            return privacidadActual(fichaActual(c,actor),actor);
        });
    }

    @Override @Transactional(readOnly=true)
    public Map<Long,FichaComision> porContratosAlquiler(Collection<Long> ids,Actor actor) {
        var a=alcances.de(actor);
        if(ids==null||ids.isEmpty())return Map.of();
        var seleccion=ids.stream().filter(Objects::nonNull).filter(v->v>0).distinct().toList();
        if(seleccion.isEmpty())return Map.of();
        var filas=comisiones.porContratosEnAlcance(actor.idOrganizacion(),seleccion,a.paramRoles());
        var keys=filas.stream().map(ComisionLiquidacion::getId).toList();
        if(keys.isEmpty())return Map.of();
        var decision=acceso.paraDecision(actor);
        boolean verNeto=!decision.vacio();
        var evalIds=filas.stream().flatMap(c->java.util.stream.Stream.of(c.getUltimaEvaluacion(),c.getEvaluacionBaseReparto())).filter(Objects::nonNull).distinct().toList();
        var evalMap=evaluaciones.findByOrganizacionIdAndIdIn(actor.idOrganizacion(),evalIds).stream()
                .collect(Collectors.toMap(ComisionEvaluacion::getId,Function.identity()));
        var movMap=movimientos.findByOrganizacionIdAndLiquidacionIdIn(actor.idOrganizacion(),keys).stream()
                .collect(Collectors.groupingBy(m->m.getLiquidacion().getId()));
        var tramosMap=tramos.findByOrganizacionIdAndComisionIn(actor.idOrganizacion(),keys).stream()
                .collect(Collectors.groupingBy(ComisionTramo::getComision));
        Map<Long,FichaComision> resultado=new LinkedHashMap<>();
        for(var c:filas) {
            exigirFuente(c);
            var e=Optional.ofNullable(evalMap.get(c.getUltimaEvaluacion())).orElseThrow(
                    ()->new IllegalStateException("La comisión no conserva su evaluación."));
            var material=materialDe(c,e);
            var f=ficha(c,null,new ReferenciaContextoEconomico(c.getContextoEconomico()),
                    material,movMap.getOrDefault(c.getId(),List.of()),tramosMap.getOrDefault(c.getId(),List.of()),evalMap.get(c.getEvaluacionBaseReparto()));
            resultado.put(c.getContrato().getId(),privacidad(f,verNeto));
        }
        // La página no hereda autorización de sus IDs: comprobar de nuevo el mismo alcance al entregar.
        var actual=alcances.de(actor);
        var idsActuales=comisiones.porContratosEnAlcance(actor.idOrganizacion(),seleccion,actual.paramRoles()).stream()
                .map(ComisionLiquidacion::getId).collect(Collectors.toSet());
        if(!idsActuales.equals(new HashSet<>(keys)))
            throw new ConflictoException("El ámbito de las comisiones cambió; vuelve a consultar.");
        var decisionActual=acceso.paraDecision(actor);
        if(decision.idOrganizacion()!=decisionActual.idOrganizacion()||decision.global()!=decisionActual.global()
                ||!new HashSet<>(decision.rolesAgente()).equals(new HashSet<>(decisionActual.rolesAgente())))
            throw new ConflictoException("La facultad de lectura del reparto cambió; vuelve a consultar.");
        return Map.copyOf(resultado);
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public FichaComision evaluar(ReferenciaContextoEconomico contexto,ReferenciaHito causa,Actor actor,
                                ContextoSesion sesion,String clave,Procedencia proc) {
        if(contexto==null||contexto.id()<=0)throw regla("Identifica el resultado económico real.");
        var ctx=hitos.consultarContexto(contexto,actor);
        validarContexto(ctx,contexto,ctx.encargo(),actor);
        var inicio=acceso.iniciar(actor,sesion,ctx.encargo());
        String k=Idempotencia.exigirClave(clave);
        bloquear("BROX-COMANDO",actor.idOrganizacion(),k);
        var encargo=acceso.escribir(ctx.encargo(),actor,inicio);
        acceso.exigirDecision(encargo,actor);
        String hash=huella(Map.of("contexto",contexto,"causa",causa==null?"SIN_CAUSA":causa),proc);
        var repetido=buscar(actor,k,EVALUAR,hash);
        if(repetido.isPresent())return restaurarEvaluacion(repetido.get(),actor);
        bloquear("BROX-COMISION",actor.idOrganizacion(),Long.toString(contexto.id()));
        ctx=contexto(contexto,ctx.encargo(),actor);
        if(causa!=null&&ctx.hechosConfirmados().stream().noneMatch(h->h.referencia().equals(causa)))
            throw regla("El hito causal debe ser una revisión confirmada vigente del mismo resultado.");
        var c=comisiones.findByOrganizacionIdAndContextoEconomico(actor.idOrganizacion(),contexto.id()).orElse(null);
        Material anterior=null;
        if(c!=null){c=bloquear(c.getId(),actor);anterior=leerMaterial(c,actor);}
        ReferenciaPacto ref=ctx.pacto();
        // Una adenda futura no reescribe la realización nacida del pacto anterior.
        if(c!=null&&(!"PENDIENTE".equals(anterior.devengo().estado())||!movimientos(c,actor).isEmpty()))
            ref=referencia(c);
        var pacto=leerPacto(ref,actor);
        validarPertenencia(pacto,ctx.encargo());
        var material=material(pacto,ctx.operacion(),ctx,hoy(),actor);
        var entrada=new Entrada(pacto,ctx.referencia(),ctx.operacion(),material.fuentesAplicadas(),hechosDelPacto(ctx,pacto),
                ctx.contratoAlquiler(),ctx.contratoVenta());
        String input=documentos.objetoCanonico(entrada);
        String inputHash=Idempotencia.huellaExacta(input);
        if(c==null) {
            c=new ComisionLiquidacion();c.setOrganizacionId(actor.idOrganizacion());c.setContextoEconomico(contexto.id());
            c.setEncargo(ctx.encargo());c.setRevisionPacto(ref.revisionAprobatoria());c.setVersionContenidoPacto(pacto.versionContenido());
            vincularContratos(c,ctx);
            c=comisiones.saveAndFlush(c);
        } else vincularContratos(c,ctx);
        var previo=evaluaciones.findByOrganizacionIdAndComisionAndHuella(actor.idOrganizacion(),c.getId(),inputHash);
        ComisionEvaluacion evaluacion;
        if(previo.isPresent()) {
            evaluacion=previo.get();
            // Recuperar una entrada anterior no vuelve vigente una realización superada.
            if(!Objects.equals(c.getUltimaEvaluacion(),evaluacion.getId()))
                throw new ConflictoException("Esa evaluación ya es un antecedente; no puede reactivarse.");
        } else {
            evaluacion=evaluaciones.saveAndFlush(ComisionEvaluacion.de(actor.idOrganizacion(),c.getId(),contexto.id(),
                    ctx.encargo(),ref.revisionAprobatoria(),pacto.versionContenido(),
                    material.fuenteCalculo()==null?null:material.fuenteCalculo().tipo(),
                    material.fuenteCalculo()==null?null:material.fuenteCalculo().id(),
                    material.fuenteCalculo()==null?null:material.fuenteCalculo().revision(),causa==null?null:causa.id(),
                    causa==null?null:causa.revision(),inputHash,input,documentos.objetoCanonico(material),
                    ahora(),actor.idPersona(),actor.idRolOperativo(),documentos.objetoCanonico(proc)));
            for(var h:ctx.hechosConfirmados())evaluaciones.enlazarHito(actor.idOrganizacion(),evaluacion.getId(),
                    contexto.id(),h.referencia().id(),h.referencia().revision());
            c.setRevisionPacto(ref.revisionAprobatoria());c.setVersionContenidoPacto(pacto.versionContenido());
            c.setMonto(material.calculo().cuantia().importe());c.setMoneda(material.calculo().cuantia().moneda());
            c.setUltimaEvaluacion(evaluacion.getId());
            realizarTramos(c,pacto,actor);
            comisiones.saveAndFlush(c);
        }
        emitirNb(c,"COMISION_EVALUADA","NB-08",evaluacion.getId(),actor,proc);
        var resultado=fichaActual(c,actor);
        var durable=new ReciboEvaluacion(contexto,causa,ref,evaluacion.getId(),resultado,proc);
        String json=documentos.objetoCanonico(durable);
        comandos.registrar(actor,k,EVALUAR,hash,ComisionLiquidacion.ENTIDAD_TIPO,c.getId(),proc,json);
        return restaurarEvaluacion(documentos.comoObjeto(json,ReciboEvaluacion.class),actor);
    }

    @Override @Transactional(readOnly=true)
    public DatosReparto validarReparto(DatosReparto d,Actor actor) {
        var n=normalizar(d);var c=cargar(n.comision(),actor);autorizarDecision(c,actor);
        version(c,n.versionEsperada());reglasReparto(c,n.montoAgente(),movimientos(c,actor));return n;
    }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaReparto(DatosReparto d,Actor actor) {
        if(d==null)throw regla("Falta el reparto original.");
        var c=cargar(d.comision(),actor);autorizarDecision(c,actor);
        leerPacto(referencia(c),actor);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ResultadoReparto asignarReparto(DatosReparto d,Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        var n=normalizar(d);var c=cargar(n.comision(),actor);
        var inicio=acceso.iniciar(actor,sesion,c.getEncargo());
        String k=Idempotencia.exigirClave(clave);bloquear("BROX-COMANDO",actor.idOrganizacion(),k);
        var encargo=acceso.escribir(c.getEncargo(),actor,inicio);acceso.exigirDecision(encargo,actor);
        String hash=huella(n,proc);var previo=buscar(actor,k,REPARTIR,hash);
        if(previo.isPresent())return restaurarReparto(previo.get(),actor);
        c=bloquear(c.getId(),actor);version(c,n.versionEsperada());
        var movs=movimientos(c,actor);reglasReparto(c,n.montoAgente(),movs);
        boolean primera=c.getMontoAgente()==null;
        c.asignarMontoAgente(n.montoAgente());comisiones.saveAndFlush(c);
        var resultado=new ResultadoReparto(c.getId(),c.getVersion(),reparto(c,movs,baseReparto(c,actor)));
        String json=documentos.objetoCanonico(new ReciboReparto(n,resultado,proc));
        comandos.registrar(actor,k,REPARTIR,hash,ComisionLiquidacion.ENTIDAD_TIPO,c.getId(),proc,json);
        if(primera)avisar(c,encargo,Alerta.COMISION_ASIGNADA,"Se registró el reparto de tu comisión de la operación.",
                new FuentesAsuntos.Origen("COMANDO_REPARTO",k),actor);
        return documentos.comoObjeto(json,ReciboReparto.class).resultado();
    }
    @Override @Transactional(readOnly=true)
    public Optional<ResultadoReparto> consultarResultadoReparto(String clave,Actor actor) {
        return comandos.consultarResultado(actor,clave,REPARTIR).map(c->restaurarReparto(c,actor));
    }

    @Override @Transactional(readOnly=true)
    public DatosCobro validarCobro(DatosCobro d,Actor actor) {
        var n=normalizar(d);var c=cargar(n.comision(),actor);autorizarDecision(c,actor);
        version(c,n.versionEsperada());reglasCobro(c,n,actor);return n;
    }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaCobro(DatosCobro original,Actor actor) {
        if(original==null)throw regla("Falta el movimiento original.");
        var c=cargar(original.comision(),actor);autorizarDecision(c,actor);
        referencias.exigirLectura(new ReferenciaContextoEconomico(c.getContextoEconomico()),referencia(c),original,actor);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ResultadoCobro registrarCobro(DatosCobro d,Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        var n=normalizar(d);var c=cargar(n.comision(),actor);
        var inicio=acceso.iniciar(actor,sesion,c.getEncargo());
        String k=Idempotencia.exigirClave(clave);bloquear("BROX-COMANDO",actor.idOrganizacion(),k);
        var encargo=acceso.escribir(c.getEncargo(),actor,inicio);acceso.exigirDecision(encargo,actor);
        String hash=huella(n,proc);var previo=buscar(actor,k,COBRAR,hash);
        if(previo.isPresent())return restaurarCobro(previo.get(),actor);
        c=bloquear(c.getId(),actor);version(c,n.versionEsperada());reglasCobro(c,n,actor);
        var antes=saldos(c,movimientos(c,actor));
        var m=new ComisionMovimiento();m.setOrganizacionId(actor.idOrganizacion());m.setLiquidacion(c);
        m.setEvaluacion(c.getUltimaEvaluacion());m.setRevisionPacto(c.getRevisionPacto());m.setTramo(n.tramo());
        m.setTipo(ComisionMovimiento.COBRO);m.setMonto(n.monto());m.setMoneda(n.moneda());m.setFecha(n.fecha());m.setFormaPago(n.formaPago());
        m.setIdUsuario(actor.idPersona());m.setRolActor(actor.idRolOperativo());m.setRolUsuario(actor.tipoRolOperativo());
        m.setObservacion(n.observacion());m.setFechaCreacion(ahora());m.setClaveIdempotencia(k);m.setHuellaComando(hash);
        m.setParteObligadaRef(refJson(n.parteObligada()));m.setParteObligadaEtiqueta(etiqueta(n.parteObligada()));
        m.setBeneficiarioRef(refJson(n.beneficiario()));m.setBeneficiarioEtiqueta(etiqueta(n.beneficiario()));
        m.setDestino(n.destino()==null?null:n.destino().datoDestino());
        m.setTitularDestinoRef(n.destino()==null?null:refJson(n.destino().titular()));
        m.setTitularDestinoEtiqueta(n.destino()==null?null:etiqueta(n.destino().titular()));
        m.setReferenciaExterna(n.referenciaExterna());m.setEvidencias(documentos.lista(n.evidencias()));
        m.setProcedencia(documentos.objetoCanonico(proc));m=movimientos.saveAndFlush(m);
        c.setUltimoMovimiento(m.getId());comisiones.saveAndFlush(c);
        var despues=saldos(c,movimientos(c,actor));
        if(!"COBRADO".equals(estadoCobro(antes))&&"COBRADO".equals(estadoCobro(despues)))
            avisar(c,encargo,Alerta.COMISION_COBRADA,"Tu comisión de la operación fue cobrada.",FuentesAsuntos.Origen.de("COMISION_MOVIMIENTO",m.getId()),actor);
        emitirNb(c,"COMISION_COBRO_REGISTRADO","NB-09",m.getId(),actor,proc);
        var resultado=new ResultadoCobro(c.getId(),c.getVersion(),m.getId(),new Dinero(m.getMonto(),m.getMoneda()),
                m.getFecha(),m.getFechaCreacion(),m.getTramo());
        String contenido=documentos.objetoCanonico(new ReciboCobro(n,resultado,proc));
        comandos.registrar(actor,k,COBRAR,hash,ComisionLiquidacion.ENTIDAD_TIPO,c.getId(),proc,contenido);
        return documentos.comoObjeto(contenido,ReciboCobro.class).resultado();
    }
    @Override @Transactional(readOnly=true)
    public Optional<ResultadoCobro> consultarResultadoCobro(String clave,Actor actor) {
        return comandos.consultarResultado(actor,clave,COBRAR).map(c->restaurarCobro(c,actor));
    }

    @Override @Transactional(readOnly=true)
    public DatosReprogramacion validarReprogramacion(DatosReprogramacion datos,Actor actor) {
        var n=normalizar(datos);var c=cargar(n.comision(),actor);autorizarDecision(c,actor);
        version(c,n.versionEsperada());return n;
    }
    @Override @Transactional(readOnly=true)
    public DatosConclusion validarConclusion(DatosConclusion datos,Actor actor) {
        var n=normalizar(datos);var c=cargar(n.comision(),actor);autorizarDecision(c,actor);
        version(c,n.versionEsperada());return n;
    }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaReprogramacion(DatosReprogramacion original,Actor actor) {
        if(original==null)throw regla("Falta la reprogramación original.");
        lecturaGestion(original.comision(),actor);
    }
    @Override @Transactional(readOnly=true)
    public void exigirLecturaConclusion(DatosConclusion original,Actor actor) {
        if(original==null)throw regla("Falta la conclusión original.");
        lecturaGestion(original.comision(),actor);
    }
    private void lecturaGestion(Long id,Actor actor) {
        var c=cargar(id,actor);autorizarDecision(c,actor);
        leerPacto(referencia(c),actor);contexto(new ReferenciaContextoEconomico(c.getContextoEconomico()),c.getEncargo(),actor);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ResultadoGestion reprogramarGestion(DatosReprogramacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        var n=normalizar(datos);
        return gestionar(REPROGRAMAR,n,n.comision(),n.versionEsperada(),n.proximaRevision(),n.motivo(),actor,sesion,clave,proc);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public ResultadoGestion concluirGestion(DatosConclusion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        var n=normalizar(datos);
        return gestionar(CONCLUIR,n,n.comision(),n.versionEsperada(),null,n.motivo(),actor,sesion,clave,proc);
    }
    private ResultadoGestion gestionar(String capacidad,Object original,long id,long esperada,LocalDate fecha,String motivo,
            Actor actor,ContextoSesion sesion,String clave,Procedencia proc) {
        var c=cargar(id,actor);var inicio=acceso.iniciar(actor,sesion,c.getEncargo());
        String k=Idempotencia.exigirClave(clave);bloquear("BROX-COMANDO",actor.idOrganizacion(),k);
        var encargo=acceso.escribir(c.getEncargo(),actor,inicio);acceso.exigirDecision(encargo,actor);
        String hash=huella(original,proc);var previo=buscar(actor,k,capacidad,hash);
        if(previo.isPresent())return restaurarGestion(previo.get(),capacidad,actor);
        c=bloquear(id,actor);version(c,esperada);
        leerPacto(referencia(c),actor);contexto(new ReferenciaContextoEconomico(c.getContextoEconomico()),c.getEncargo(),actor);
        String antecedente=c.getClaveUltimaGestion();
        c.registrarGestion(CONCLUIR.equals(capacidad),fecha,motivo,ahora(),k);
        comisiones.saveAndFlush(c);
        var resultado=new ResultadoGestion(c.getId(),c.getVersion(),gestion(c));
        var recibo=new ReciboGestion(capacidad,original instanceof DatosReprogramacion p?p:null,
                original instanceof DatosConclusion q?q:null,antecedente,resultado,proc);
        String contenido=documentos.objetoCanonico(recibo);
        comandos.registrar(actor,k,capacidad,hash,ComisionLiquidacion.ENTIDAD_TIPO,c.getId(),proc,contenido);
        return documentos.comoObjeto(contenido,ReciboGestion.class).resultado();
    }
    @Override @Transactional(readOnly=true)
    public Optional<ResultadoGestion> consultarResultadoGestion(String capacidad,String clave,Actor actor) {
        exigirCapacidadGestion(capacidad);
        return comandos.consultarResultado(actor,clave,capacidad).map(c->restaurarGestion(c,capacidad,actor));
    }
    private ResultadoGestion restaurarGestion(ComandoIdempotente comando,String capacidad,Actor actor) {
        exigirCapacidadGestion(capacidad);
        var r=documentos.comoObjeto(comando.getResultado(),ReciboGestion.class);
        if(!capacidad.equals(r.capacidad())||!capacidad.equals(comando.getTipoComando())
                ||(REPROGRAMAR.equals(capacidad)?r.reprogramacion()==null||r.conclusion()!=null:r.conclusion()==null||r.reprogramacion()!=null))
            throw new IllegalStateException("El recibo corresponde a otra actuación de gestión.");
        Object original;long id;String motivo;LocalDate fecha;
        if(REPROGRAMAR.equals(capacidad)) {
            exigirLecturaReprogramacion(r.reprogramacion(),actor);original=r.reprogramacion();id=r.reprogramacion().comision();
            motivo=r.reprogramacion().motivo();fecha=r.reprogramacion().proximaRevision();
        } else {
            exigirLecturaConclusion(r.conclusion(),actor);original=r.conclusion();id=r.conclusion().comision();
            motivo=r.conclusion().motivo();fecha=null;
        }
        if(r.procedencia()==null||!comando.getHuella().equals(huella(original,r.procedencia()))
                ||r.resultado().comision()!=id||r.resultado().gestion().concluida()!=CONCLUIR.equals(capacidad)
                ||!Objects.equals(r.resultado().gestion().motivo(),motivo)
                ||!Objects.equals(r.resultado().gestion().proximaRevision(),fecha)
                ||r.resultado().gestion().registradoEn()==null)
            throw new IllegalStateException("El recibo no conserva la gestión original.");
        return r.resultado();
    }
    private void exigirCapacidadGestion(String capacidad) {
        if(!REPROGRAMAR.equals(capacidad)&&!CONCLUIR.equals(capacidad))throw regla("Actuación de gestión no reconocida.");
    }

    @Override @Transactional(readOnly=true)
    public Map<String,Object> previsualizarActuacion(String capacidad,Map<String,Object> datos,Actor actor) {
        Object normalizado;long id,esperada;
        switch(capacidad) {
            case "registrar_cobro" -> {var n=validarCobro(entrada(datos,DatosCobro.class),actor);normalizado=n;id=n.comision();esperada=n.versionEsperada();}
            case "asignar_reparto_comision" -> {var n=validarReparto(entrada(datos,DatosReparto.class),actor);normalizado=n;id=n.comision();esperada=n.versionEsperada();}
            case REPROGRAMAR -> {var n=validarReprogramacion(entrada(datos,DatosReprogramacion.class),actor);normalizado=n;id=n.comision();esperada=n.versionEsperada();}
            case CONCLUIR -> {var n=validarConclusion(entrada(datos,DatosConclusion.class),actor);normalizado=n;id=n.comision();esperada=n.versionEsperada();}
            default -> throw regla("Actuación económica no reconocida.");
        }
        var c=cargar(id,actor);em.refresh(c);version(c,esperada);autorizarDecision(c,actor);
        var ficha=fichaActual(c,actor);var pacto=leerPacto(ficha.pacto(),actor);
        // El resumen queda ligado a la misma versión; confirmar vuelve a comprobarla bajo lock.
        em.refresh(c);version(c,esperada);autorizarDecision(c,actor);
        return Map.of("capacidad",capacidad,"datos",normalizado,"comision",ficha,"pacto",pacto);
    }
    private <T>T entrada(Map<String,Object> datos,Class<T> tipo) {
        if(datos==null)throw regla("Aporta los datos de la actuación.");
        try{return json.convertValue(datos,tipo);}
        catch(IllegalArgumentException e) {
            boolean entradaInvalida=false;
            for(Throwable causa=e;causa!=null;causa=causa.getCause()) {
                if(causa instanceof ReglaNegocioException r)throw r;
                if(causa instanceof MismatchedInputException)entradaInvalida=true;
            }
            if(entradaInvalida)throw regla("Los datos de la actuación no cumplen su contrato.");
            throw new IllegalStateException("No se pudo interpretar el contrato de la actuación.",e);
        }
    }
    private DatosReprogramacion normalizar(DatosReprogramacion d) {
        if(d==null||d.proximaRevision()==null)throw regla("Declara la fecha de revisión del seguimiento.");
        return new DatosReprogramacion(id(d.comision()),version(d.versionEsperada()),d.proximaRevision(),motivo(d.motivo()));
    }
    private DatosConclusion normalizar(DatosConclusion d) {
        if(d==null)throw regla("Aporta la conclusión de gestión.");
        return new DatosConclusion(id(d.comision()),version(d.versionEsperada()),motivo(d.motivo()));
    }
    private String motivo(String valor) {
        String motivo=texto(valor,300);if(motivo==null)throw regla("Declara un motivo breve para la gestión.");return motivo;
    }

    private void reglasReparto(ComisionLiquidacion c,BigDecimal monto,List<ComisionMovimiento> movs) {
        var saldo=saldos(c,movs);
        if("COBRADO".equals(estadoCobro(saldo)))throw regla("El reparto se asigna mientras la comisión está pendiente o parcial.");
        if(c.getMonto()==null)throw regla("La comisión conserva base o importe pendiente.");
        if(monto.compareTo(c.getMonto())>0)throw regla("El monto del agente no puede superar la comisión bruta.");
        var pagado=pagadoEnMoneda(saldo.pagos(),c.getMoneda());
        if(pagado==null)throw regla("Los pagos históricos no son comparables con la moneda del reparto.");
        if(monto.compareTo(pagado)<0)throw regla("El monto del agente no puede ser menor que lo ya pagado.");
    }
    private void reglasCobro(ComisionLiquidacion c,DatosCobro n,Actor actor) {
        if(!Objects.equals(c.getMoneda(),n.moneda()))throw regla("La moneda del cobro debe coincidir con la comisión.");
        referencias.exigirLectura(new ReferenciaContextoEconomico(c.getContextoEconomico()),referencia(c),n,actor);
        var rows=movimientos(c,actor);var saldo=saldos(c,rows);
        var tramo=n.tramo()==null?null:tramos.findByOrganizacionIdAndComisionAndId(actor.idOrganizacion(),c.getId(),n.tramo())
                .orElseThrow(()->new NoEncontradoException("Tramo de comisión"));
        var pacto=leerPacto(referencia(c),actor);
        var ctx=contexto(new ReferenciaContextoEconomico(c.getContextoEconomico()),c.getEncargo(),actor);
        var material=material(pacto,ctx.operacion(),ctx,n.fecha(),actor);
        BigDecimal devengado=material.devengo().cuantiaAcreditada().importe();
        if("PENDIENTE".equals(material.devengo().estado())||devengado==null)
            throw regla("No se acredita comisión devengada en la fecha efectiva del cobro; no se admiten anticipos.");
        var recibido=recibidoComparable(saldo.recibidos(),n.moneda());
        if(recibido==null||saldo.saldoInformativo()==null)
            throw regla("Los importes conocidos no permiten calcular un saldo comparable para este cobro.");
        BigDecimal disponible=devengado.subtract(recibido).max(BigDecimal.ZERO);
        if(n.monto().compareTo(saldo.saldoInformativo())>0||n.monto().compareTo(disponible)>0)
            throw regla("El cobro no puede superar el saldo devengado y pendiente.");
        if(tramo!=null) {
            if(tramo.getRevisionPacto()!=c.getRevisionPacto())throw regla("El tramo pertenece a otro pacto realizado.");
            var evaluado=material.tramos().stream().filter(t->t.indice()==tramo.getIndicePacto()).findFirst().orElseThrow(
                    ()->new IllegalStateException("El tramo no tiene regla en su pacto."));
            BigDecimal cuantia=evaluado.devengo().cuantiaAcreditada().importe();
            var porTramo=saldos(c,rows.stream().filter(m->Objects.equals(m.getTramo(),n.tramo())).toList());
            var recibidoTramo=recibidoComparable(porTramo.recibidos(),n.moneda());
            if("PENDIENTE".equals(evaluado.devengo().estado())||cuantia==null||recibidoTramo==null
                    ||n.monto().compareTo(cuantia.subtract(recibidoTramo))>0)
                throw regla("El cobro supera el importe devengado pendiente del tramo.");
        }
    }

    private Material material(PactoFirmado pacto,OperacionInmobiliaria operacion,ContextoEconomico ctx,LocalDate fecha,Actor actor) {
        var regla=pacto.regla();var hechos=hechosDelPacto(ctx,pacto);
        var fuentes=new HashMap<ReferenciaFuenteEconomica,FuenteEconomicaFinal>();
        var condicion=regla.ejecucion()==null?null:regla.ejecucion().devengo();
        var momento=devengo(condicion,hechos,fecha);
        var fuente=fuentePara(momento,ctx,fuentes,actor);
        if(fuente!=null)fuentes.putIfAbsent(fuente.referencia(),fuente);
        var calculo=CalculadoraComision.calcular(regla,operacion,fuente);
        var exigibilidad=exigibilidad(regla.ejecucion()==null?null:regla.ejecucion().exigibilidad(),hechos,fecha);
        List<TramoMaterial> partes=new ArrayList<>();List<Vencimiento> calendario=new ArrayList<>();
        for(int i=0;i<regla.tramos().size();i++) {
            Tramo t=regla.tramos().get(i);
            var ej=t.ejecucion();
            var m=devengo(ej!=null&&ej.devengo()!=null?ej.devengo():condicion,hechos,fecha);
            Calculo cuantia;
            if(t.importe()!=null) {
                String moneda=CondicionesEconomicas.moneda(t.moneda(),"del tramo pactado");
                if(!moneda.equals(calculo.cuantia().moneda()))throw regla("Las monedas del tramo y la comisión no son comparables.");
                cuantia=new Calculo(new Dinero(t.importe().setScale(2,RoundingMode.HALF_UP),moneda),List.of());
            } else cuantia=new Calculo(new Dinero(null,calculo.cuantia().moneda()),List.of(
                    t.porcentaje()==null?"CUANTIA_TRAMO":"REGLA_PORCENTAJE_TRAMO_NO_EJECUTABLE"));
            var ex=exigibilidad(ej==null?null:ej.exigibilidad(),hechos,fecha);
            var d=estadoDevengo(m,cuantia);
            partes.add(new TramoMaterial(i,cuantia,d,ex));
            calendario.add(new Vencimiento(i,ex.fechaAcreditada(),t.exigibilidad(),ex.faltantes()));
        }
        var d=partes.isEmpty()?estadoDevengo(momento,calculo):agregarDevengo(partes,calculo.cuantia().moneda());
        if(partes.isEmpty())calendario.add(new Vencimiento(null,exigibilidad.fechaAcreditada(),regla.exigibilidad(),exigibilidad.faltantes()));
        var aplicadas=fuentes.values().stream().sorted(Comparator.comparing((FuenteEconomicaFinal f)->f.referencia().tipo())
                .thenComparingLong(f->f.referencia().id()).thenComparingLong(f->f.referencia().revision())).toList();
        return new Material(calculo,d,exigibilidad,List.copyOf(calendario),List.copyOf(partes),
                fuente==null?null:fuente.referencia(),aplicadas);
    }
    private Momento devengo(CondicionDevengo c,List<FuenteHitoConfirmado> hechos,LocalDate fecha) {
        if(c==null||c.hito()==null)return new Momento(null,List.of("CONDICION_DEVENGO_NO_EJECUTABLE"));
        var relevantes=hechos.stream().filter(h->h.tipo()==c.hito()).toList();
        if(relevantes.size()>1)throw new IllegalStateException("Más de un hito confirmado vigente para la misma condición.");
        if(relevantes.isEmpty()||relevantes.getFirst().fechaEfectiva().isAfter(fecha))
            return new Momento(null,List.of("HITO_"+c.hito()+"_NO_ACREDITADO"));
        return new Momento(relevantes.getFirst(),List.of());
    }
    private List<FuenteHitoConfirmado> hechosDelPacto(ContextoEconomico ctx,PactoFirmado pacto) {
        if(ctx==null)return List.of();
        return ctx.hechosConfirmados().stream().filter(h->h.pacto().equals(pacto.referencia()))
                .sorted(Comparator.comparingLong((FuenteHitoConfirmado h)->h.referencia().id())
                        .thenComparingLong(h->h.referencia().revision())).toList();
    }
    private FuenteEconomicaFinal fuentePara(Momento momento,ContextoEconomico ctx,
            Map<ReferenciaFuenteEconomica,FuenteEconomicaFinal> leidas,Actor actor) {
        if(ctx==null)return null;
        if(momento.hito()==null)return ctx.fuenteEconomica();
        var ref=momento.hito().fuenteEconomica();
        if(ref==null)return null;
        return leidas.computeIfAbsent(ref,r->leerFuente(ctx.referencia(),r,actor));
    }
    private Devengo estadoDevengo(Momento momento,Calculo calculo) {
        boolean cumple=momento.hito()!=null;
        var faltantes=new ArrayList<>(momento.faltantes());
        if(cumple)faltantes.addAll(calculo.faltantes());
        return new Devengo(cumple?"DEVENGADO":"PENDIENTE",new Dinero(
                cumple?calculo.cuantia().importe():null,calculo.cuantia().moneda()),
                cumple?List.of(momento.hito().referencia()):List.of(),faltantes);
    }
    private Devengo agregarDevengo(List<TramoMaterial> partes,String moneda) {
        long cumplidos=partes.stream().filter(t->"DEVENGADO".equals(t.devengo().estado())).count();
        var realizadas=partes.stream().filter(t->"DEVENGADO".equals(t.devengo().estado())).toList();
        boolean conocido=!realizadas.isEmpty()&&realizadas.stream().allMatch(t->t.devengo().cuantiaAcreditada().importe()!=null);
        BigDecimal monto=conocido?realizadas.stream().map(t->t.devengo().cuantiaAcreditada().importe()).reduce(BigDecimal.ZERO,BigDecimal::add):null;
        return new Devengo(cumplidos==0?"PENDIENTE":cumplidos==partes.size()?"DEVENGADO":"PARCIAL",
                new Dinero(monto,moneda),partes.stream().flatMap(t->t.devengo().hechos().stream()).distinct().toList(),
                partes.stream().flatMap(t->t.devengo().faltantes().stream()).distinct().toList());
    }
    private Exigibilidad exigibilidad(CondicionExigibilidad c,List<FuenteHitoConfirmado> hechos,LocalDate fecha) {
        if(c==null)return new Exigibilidad(null,null,List.of("CONDICION_EXIGIBILIDAD_NO_EJECUTABLE"));
        LocalDate efectiva=null;
        if(c.fechaExpresa()!=null&&c.hito()==null&&c.cantidad()==null)efectiva=c.fechaExpresa();
        else if(c.hito()!=null&&c.fechaExpresa()==null&&c.cantidad()==null) {
            var momento=devengo(new CondicionDevengo(c.hito()),hechos,fecha);
            if(momento.hito()==null)return new Exigibilidad(false,null,momento.faltantes());
            efectiva=momento.hito().fechaEfectiva();
        }
        if(efectiva==null)return new Exigibilidad(null,null,List.of("CONDICION_EXIGIBILIDAD_NO_EJECUTABLE"));
        return new Exigibilidad(!fecha.isBefore(efectiva),efectiva,List.of());
    }

    private void realizarTramos(ComisionLiquidacion c,PactoFirmado pacto,Actor actor) {
        var existentes=tramos.findByOrganizacionIdAndComisionAndRevisionPactoOrderByIndicePactoAsc(actor.idOrganizacion(),
                c.getId(),pacto.referencia().revisionAprobatoria()).stream()
                .collect(Collectors.toMap(ComisionTramo::getIndicePacto,Function.identity()));
        for(int i=0;i<pacto.regla().tramos().size();i++) {
            String hash=Idempotencia.huellaExacta(documentos.objetoCanonico(pacto.regla().tramos().get(i)));
            if(existentes.containsKey(i)) {
                if(!hash.equals(existentes.get(i).getHuellaRegla()))throw new IllegalStateException("Un pacto inmutable cambió su tramo.");
            } else tramos.save(ComisionTramo.de(actor.idOrganizacion(),c.getId(),c.getEncargo(),
                    pacto.referencia().revisionAprobatoria(),i,hash));
        }
    }
    private void vincularContratos(ComisionLiquidacion c,ContextoEconomico ctx) {
        if(ctx.contratoAlquiler()!=null) {
            if(ctx.operacion()!=OperacionInmobiliaria.ALQUILER)
                throw new IllegalStateException("Un contexto de venta no se vincula a un contrato de alquiler.");
            if(c.getContrato()!=null&&!Objects.equals(c.getContrato().getId(),ctx.contratoAlquiler()))
                throw new IllegalStateException("El contexto no sustituye la identidad del contrato ya vinculado.");
            if(c.getContrato()==null)c.setContrato(em.getReference(ContratoAlquiler.class,ctx.contratoAlquiler()));
        }
        if(ctx.contratoVenta()!=null) {
            if(ctx.operacion()!=OperacionInmobiliaria.VENTA)
                throw new IllegalStateException("Un contexto de alquiler no se vincula a un contrato de venta.");
            if(c.getContratoVenta()!=null&&!Objects.equals(c.getContratoVenta(),ctx.contratoVenta()))
                throw new IllegalStateException("El contexto no sustituye la identidad del contrato ya vinculado.");
            c.setContratoVenta(ctx.contratoVenta());
        }
    }
    private FichaComision fichaActual(ComisionLiquidacion c,Actor actor) {
        var e=evaluacion(c,actor);reauthorizar(e,actor);
        return ficha(c,null,new ReferenciaContextoEconomico(c.getContextoEconomico()),materialDe(c,e),movimientos(c,actor),
                tramos.findByOrganizacionIdAndComisionAndRevisionPactoOrderByIndicePactoAsc(actor.idOrganizacion(),c.getId(),c.getRevisionPacto()),
                baseReparto(c,actor));
    }
    private FichaComision ficha(ComisionLiquidacion c,PactoFirmado pacto,ReferenciaContextoEconomico ctx,
            Material m,List<ComisionMovimiento> movimientos,List<ComisionTramo> filas,ComisionEvaluacion base) {
        var saldo=c==null?new Saldos(Map.of(),Map.of(),m.calculo().cuantia().importe()):saldos(c,movimientos);
        String moneda=m.calculo().cuantia().moneda();
        var realizadas=filas.stream().filter(t->c!=null&&t.getRevisionPacto()==c.getRevisionPacto()).map(t->{
            var parte=m.tramos().stream().filter(v->v.indice()==t.getIndicePacto()).findFirst().orElseThrow();
            return new TramoRealizado(t.getId(),t.getIndicePacto(),referencia(c),parte.calculo(),parte.devengo(),actual(parte.exigibilidad()));
        }).toList();
        var ingresos=saldo.recibidos().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(v->new Dinero(v.getValue(),v.getKey())).toList();
        return new FichaComision(c==null?null:c.getId(),c==null?null:c.getVersion(),ctx,
                c==null?pacto.referencia():referencia(c),c==null?pacto.versionContenido():c.getVersionContenidoPacto(),
                m.fuenteCalculo(),m.calculo(),m.devengo(),actual(m.exigibilidad()),m.calendario(),realizadas,
                new Cobro(ingresos,new Dinero(saldo.saldoInformativo(),moneda),estadoCobro(saldo)),
                c==null?new Reparto(null,null,null,null,null,null):reparto(c,movimientos,base),c==null?null:gestion(c));
    }
    private Exigibilidad actual(Exigibilidad e) {
        return e.fechaAcreditada()==null?e:new Exigibilidad(!hoy().isBefore(e.fechaAcreditada()),e.fechaAcreditada(),e.faltantes());
    }
    /** La lectura del Encargo no publica los netos privados del reparto. */
    private FichaComision privacidad(FichaComision f,boolean verNeto) {
        if(verNeto)return f;
        return new FichaComision(f.id(),f.version(),f.contexto(),f.pacto(),f.versionContenidoPacto(),f.fuenteFinal(),
                f.calculo(),f.devengo(),f.exigibilidad(),f.calendario(),f.tramos(),f.cobro(),new Reparto(null,null,null,null,null,null),
                f.gestion()==null?null:new GestionCobro(f.gestion().concluida(),f.gestion().proximaRevision(),null,f.gestion().registradoEn()));
    }
    private FichaComision privacidadActual(FichaComision f,Actor actor) {
        var actual=acceso.leer(f.pacto().encargo(),actor);
        return privacidad(f,acceso.puedeDecidir(actual,actor));
    }
    private Material leerMaterial(ComisionLiquidacion c,Actor actor){return materialDe(c,evaluacion(c,actor));}
    private Material materialDe(ComisionLiquidacion c,ComisionEvaluacion e) {
        if(!Objects.equals(e.getOrganizacionId(),c.getOrganizacionId())||e.getComision()!=c.getId()
                ||e.getContexto()!=c.getContextoEconomico()||e.getEncargo()!=c.getEncargo()
                ||e.getRevisionPacto()!=c.getRevisionPacto()||e.getVersionContenido()!=c.getVersionContenidoPacto())
            throw new IllegalStateException("La evaluación no coincide con las referencias de la comisión.");
        var material=documentos.comoObjeto(e.getResultado(),Material.class);
        var importe=material.calculo().cuantia().importe();
        if((importe==null)!=(c.getMonto()==null)||(importe!=null&&importe.compareTo(c.getMonto())!=0)
                ||!Objects.equals(material.calculo().cuantia().moneda(),c.getMoneda()))
            throw new IllegalStateException("La proyección monetaria no coincide con su evaluación inmutable.");
        return material;
    }
    private ComisionEvaluacion evaluacion(ComisionLiquidacion c,Actor actor) {
        exigirFuente(c);
        return evaluaciones.findByOrganizacionIdAndComisionAndId(actor.idOrganizacion(),c.getId(),c.getUltimaEvaluacion())
                .orElseThrow(()->new IllegalStateException("No se conserva la evaluación de la comisión."));
    }
    private void reauthorizar(ComisionEvaluacion e,Actor actor) {
        var entrada=documentos.comoObjeto(e.getEntrada(),Entrada.class);
        acceso.exigirLectura(e.getEncargo(),actor);
        leerPacto(entrada.pacto().referencia(),actor);
        var ctx=new ReferenciaContextoEconomico(e.getContexto());
        contexto(ctx,e.getEncargo(),actor);
        Set<ReferenciaFuenteEconomica> refs=new HashSet<>();
        entrada.fuentes().stream().map(FuenteEconomicaFinal::referencia).forEach(refs::add);
        entrada.hechos().stream().map(FuenteHitoConfirmado::fuenteEconomica).filter(Objects::nonNull).forEach(refs::add);
        for(var r:refs)leerFuente(ctx,r,actor);
    }
    private FichaComision restaurarEvaluacion(ComandoIdempotente c,Actor actor){return restaurarEvaluacion(documentos.comoObjeto(c.getResultado(),ReciboEvaluacion.class),actor);}
    private FichaComision restaurarEvaluacion(ReciboEvaluacion r,Actor actor) {
        var c=cargar(r.resultado().id(),actor);
        if(!Objects.equals(c.getContextoEconomico(),r.contexto().id()))throw new IllegalStateException("Recibo de otro resultado.");
        var e=evaluaciones.findByOrganizacionIdAndComisionAndId(actor.idOrganizacion(),c.getId(),r.evaluacion()).orElseThrow(
                ()->new IllegalStateException("No se conserva la evaluación original."));
        reauthorizar(e,actor);return r.resultado();
    }
    private ResultadoReparto restaurarReparto(ComandoIdempotente c,Actor actor) {
        var r=documentos.comoObjeto(c.getResultado(),ReciboReparto.class);exigirLecturaReparto(r.original(),actor);
        if(r.procedencia()==null||!c.getHuella().equals(huella(r.original(),r.procedencia())))
            throw new IllegalStateException("El recibo no conserva el contenido del comando original.");
        if(r.resultado().comision()!=r.original().comision()
                ||r.resultado().reparto().agente().compareTo(r.original().montoAgente())!=0)
            throw new IllegalStateException("Recibo de otro reparto.");
        var raiz=cargar(r.original().comision(),actor);
        var base=evaluaciones.findByOrganizacionIdAndComisionAndId(actor.idOrganizacion(),raiz.getId(),
                r.resultado().reparto().evaluacionBase()).orElseThrow(()->new IllegalStateException("Falta la base original del reparto."));
        reauthorizar(base,actor);
        var material=documentos.comoObjeto(base.getResultado(),Material.class);
        if(base.getContexto()!=raiz.getContextoEconomico()
                ||!Objects.equals(material.calculo().cuantia().moneda(),r.resultado().reparto().moneda())
                ||material.calculo().cuantia().importe()==null
                ||r.resultado().reparto().agente().add(r.resultado().reparto().empresa()).compareTo(material.calculo().cuantia().importe())!=0)
            throw new IllegalStateException("El recibo no conserva su base de reparto.");
        return r.resultado();
    }
    private ResultadoCobro restaurarCobro(ComandoIdempotente c,Actor actor) {
        var r=documentos.comoObjeto(c.getResultado(),ReciboCobro.class);exigirLecturaCobro(r.original(),actor);
        if(r.resultado().comision()!=r.original().comision())throw new IllegalStateException("Recibo de otra comisión.");
        var m=movimientos.findByOrganizacionIdAndId(actor.idOrganizacion(),r.resultado().movimiento())
                .orElseThrow(()->new IllegalStateException("No se conserva el movimiento original del recibo."));
        if(r.procedencia()==null||!c.getHuella().equals(huella(r.original(),r.procedencia()))
                ||!c.getHuella().equals(m.getHuellaComando())||!c.getClaveIdempotencia().equals(m.getClaveIdempotencia()))
            throw new IllegalStateException("El recibo no conserva el contenido del comando original.");
        if(m.getLiquidacion().getId()!=r.resultado().comision()||!ComisionMovimiento.COBRO.equals(m.getTipo())
                ||m.getMonto().compareTo(r.resultado().importe().importe())!=0
                ||!Objects.equals(m.getMoneda(),r.resultado().importe().moneda())
                ||!Objects.equals(m.getFecha(),r.resultado().fecha())||!Objects.equals(m.getTramo(),r.resultado().tramo())
                ||!m.getFechaCreacion().toInstant().equals(r.resultado().registradoEn().toInstant()))
            throw new IllegalStateException("El recibo no coincide con la realización inmutable del movimiento.");
        return r.resultado();
    }

    private ComisionLiquidacion cargar(Long id,Actor actor) {
        if(id==null||id<=0)throw regla("Identifica la comisión.");
        var c=comisiones.findByOrganizacionIdAndId(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Comisión"));
        exigirFuente(c);acceso.exigirLectura(c.getEncargo(),actor);return c;
    }
    private ComisionLiquidacion bloquear(long id,Actor actor) {
        var c=comisiones.bloquear(actor.idOrganizacion(),id).orElseThrow(()->new NoEncontradoException("Comisión"));
        em.refresh(c,LockModeType.PESSIMISTIC_WRITE);exigirFuente(c);acceso.exigirLectura(c.getEncargo(),actor);return c;
    }
    private void autorizarDecision(ComisionLiquidacion c,Actor actor){acceso.exigirDecision(acceso.leer(c.getEncargo(),actor),actor);}
    private void emitirNb(ComisionLiquidacion c,String tipo,String codigo,long fuente,Actor actor,Procedencia p){
        var marca=new com.controllocal.service.soporte.AvisoComercialBroker.Marca(codigo,"ENCARGO",c.getEncargo(),c.getVersion(),List.of("COMISION"));
        eventos.saveAndFlush(p.sinPeticion().sellar(com.controllocal.domain.auditoria.EventoDominio.de(actor.idOrganizacion(),tipo,"COMISION_LIQUIDACION",c.getId(),actor.idRolOperativo(),p.canal()))
            .con(documentos.objetoCanonico(com.controllocal.service.soporte.AvisoComercialBroker.con(Map.of("fuente",fuente),marca))));
    }
    private void exigirFuente(ComisionLiquidacion c) {
        if(c.getContextoEconomico()==null||c.getEncargo()==null||c.getRevisionPacto()==null
                ||c.getVersionContenidoPacto()==null||c.getUltimaEvaluacion()==null)
            throw new IllegalStateException("La fila técnica no acredita contexto, pacto y evaluación C5.");
    }
    private ContextoEconomico contexto(ReferenciaContextoEconomico ref,long encargo,Actor actor) {
        var ctx=hitos.consultarContexto(ref,actor);validarContexto(ctx,ref,encargo,actor);return ctx;
    }
    private void validarContexto(ContextoEconomico ctx,ReferenciaContextoEconomico ref,long encargo,Actor actor) {
        if(ctx==null||ctx.organizacion()!=actor.idOrganizacion()||ctx.encargo()!=encargo||!ref.equals(ctx.referencia()))
            throw new NoEncontradoException("Contexto económico");
    }
    private PactoFirmado leerPacto(ReferenciaPacto referencia,Actor actor) {
        validarReferencia(referencia);
        var pacto=pactos.consultarPactoFirmado(referencia,actor);
        if(pacto==null||!referencia.equals(pacto.referencia()))
            throw new IllegalStateException("El lector no devolvió el pacto exacto solicitado.");
        return pacto;
    }
    private FuenteEconomicaFinal leerFuente(ReferenciaContextoEconomico contexto,ReferenciaFuenteEconomica referencia,Actor actor) {
        var fuente=hitos.consultarFuente(contexto,referencia,actor);
        if(fuente==null||!referencia.equals(fuente.referencia()))
            throw new IllegalStateException("El lector no devolvió la fuente económica exacta solicitada.");
        return fuente;
    }
    private void validarPertenencia(PactoFirmado pacto,long encargo) {
        if(pacto==null||pacto.referencia().encargo()!=encargo)throw new IllegalStateException("Pacto de otro Encargo.");
    }
    private void validarReferencia(ReferenciaPacto r){if(r==null||r.encargo()<=0||r.revisionAprobatoria()<=0)throw regla("Identifica el pacto aprobado exacto.");}
    private ReferenciaPacto referencia(ComisionLiquidacion c){return new ReferenciaPacto(c.getEncargo(),c.getRevisionPacto());}
    private List<ComisionMovimiento> movimientos(ComisionLiquidacion c,Actor actor){return movimientos.findByOrganizacionIdAndLiquidacionIdOrderByFechaAscIdAsc(actor.idOrganizacion(),c.getId());}
    private ComisionEvaluacion baseReparto(ComisionLiquidacion c,Actor actor) {
        if(c.getEvaluacionBaseReparto()==null)return null;
        var e=evaluaciones.findByOrganizacionIdAndComisionAndId(actor.idOrganizacion(),c.getId(),c.getEvaluacionBaseReparto())
                .orElseThrow(()->new IllegalStateException("No se conserva la base del reparto."));
        reauthorizar(e,actor);return e;
    }
    private Reparto reparto(ComisionLiquidacion c,List<ComisionMovimiento> filas,ComisionEvaluacion base) {
        if(base==null) {
            if(c.getMontoAgente()!=null||c.getMontoEmpresa()!=null)throw new IllegalStateException("El reparto no conserva su evaluación de base.");
            return new Reparto(null,null,null,null,null,null);
        }
        if(base.getComision()!=c.getId()||base.getContexto()!=c.getContextoEconomico()
                ||!Objects.equals(base.getOrganizacionId(),c.getOrganizacionId()))
            throw new IllegalStateException("La base del reparto pertenece a otro resultado.");
        var material=documentos.comoObjeto(base.getResultado(),Material.class);
        String moneda=material.calculo().cuantia().moneda();
        BigDecimal bruto=material.calculo().cuantia().importe();
        if(bruto==null||c.getMontoAgente()==null||c.getMontoEmpresa()==null
                ||c.getMontoAgente().add(c.getMontoEmpresa()).compareTo(bruto)!=0)
            throw new IllegalStateException("El reparto no coincide con su base original.");
        BigDecimal pagado=pagadoEnMoneda(saldos(c,filas).pagos(),moneda);
        return new Reparto(c.getMontoAgente(),c.getMontoEmpresa(),pagado,
                pagado==null?null:c.getMontoAgente().subtract(pagado).max(BigDecimal.ZERO),base.getId(),moneda);
    }
    private Saldos saldos(ComisionLiquidacion c,List<ComisionMovimiento> filas) {
        Map<String,BigDecimal> recibidos=new TreeMap<>(),pagos=new TreeMap<>();
        for(var m:filas) {
            if(ComisionMovimiento.AJUSTE.equals(m.getTipo()))continue;
            if(!Set.of("C","P","R").contains(m.getTipo()))throw new IllegalStateException("Tipo ilegible en el libro.");
            if(!CondicionesEconomicas.MONEDAS.contains(m.getMoneda())||m.getMonto()==null||m.getMonto().signum()<=0)
                throw new IllegalStateException("Importe o moneda ilegibles en el libro.");
            if("C".equals(m.getTipo()))recibidos.merge(m.getMoneda(),m.getMonto(),BigDecimal::add);
            else if("R".equals(m.getTipo()))recibidos.merge(m.getMoneda(),m.getMonto().negate(),BigDecimal::add);
            else pagos.merge(m.getMoneda(),m.getMonto(),BigDecimal::add);
        }
        if(recibidos.values().stream().anyMatch(v->v.signum()<0))
            throw new IllegalStateException("El libro conserva reversos superiores a los cobros.");
        BigDecimal comparable=recibidoComparable(recibidos,c.getMoneda());
        return new Saldos(Map.copyOf(recibidos),Map.copyOf(pagos),
                c.getMonto()==null||comparable==null?null:c.getMonto().subtract(comparable).max(BigDecimal.ZERO));
    }
    private BigDecimal recibidoComparable(Map<String,BigDecimal> importes,String moneda) {
        if(moneda==null||importes.keySet().stream().anyMatch(m->!m.equals(moneda)))return null;
        return importes.getOrDefault(moneda,BigDecimal.ZERO);
    }
    private BigDecimal pagadoEnMoneda(Map<String,BigDecimal> pagos,String moneda){return recibidoComparable(pagos,moneda);}
    private String estadoCobro(Saldos saldo) {
        if(saldo.recibidos().isEmpty()||saldo.recibidos().values().stream().allMatch(v->v.signum()==0))return "SIN_COBROS";
        if(saldo.saldoInformativo()==null)return null;
        return saldo.saldoInformativo().signum()==0?"COBRADO":"PARCIAL";
    }
    private GestionCobro gestion(ComisionLiquidacion c) {
        return new GestionCobro(c.isGestionConcluida(),c.getProximaRevisionGestion(),c.getMotivoGestion(),c.getGestionRegistradaEn());
    }
    private void avisar(ComisionLiquidacion c,Captacion encargo,String tipo,String texto,FuentesAsuntos.Origen origen,Actor actor) {
        var contrato=c.getContrato();
        if(contrato==null||contrato.getSolicitud()==null||contrato.getSolicitud().getAgente()==null)return;
        if(!Objects.equals(contrato.getSolicitud().getAgente().getId(),encargo.getAgente().getId()))return;
        alertas.emitir(new AlertaService.DatosAlerta(tipo,Alerta.INFO,"CONTRATO_ALQUILER",contrato.getId(),
                encargo.getAgente().getId(),texto,origen),actor);
    }
    private void bloquear(String espacio,long org,String clave) {
        String hash=Idempotencia.huellaExacta(espacio+"\u0000"+org+"\u0000"+clave);
        recibos.bloquearIdentidad(Long.parseUnsignedLong(hash.substring(0,16),16));
    }
    private Optional<ComandoIdempotente> buscar(Actor actor,String clave,String tipo,String hash) {
        try{return comandos.buscar(actor,clave,tipo,hash);}
        catch(ReglaNegocioException ex){throw new ConflictoException("La clave identifica una actuación de otro contenido.");}
    }
    private String huella(Object d,Procedencia proc) {
        if(proc==null)throw regla("Conserva la procedencia del acto.");
        return Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("datos",d,"procedencia",proc)));
    }
    private DatosReparto normalizar(DatosReparto d) {
        if(d==null)throw regla("Aporta el reparto.");
        return new DatosReparto(id(d.comision()),version(d.versionEsperada()),importe(d.montoAgente(),true),texto(d.observacion(),300));
    }
    private DatosCobro normalizar(DatosCobro d) {
        if(d==null)throw regla("Aporta el cobro real.");
        if(d.fecha()==null||d.fecha().isAfter(hoy()))throw regla("Declara la fecha efectiva; no puede ser futura.");
        String forma=texto(d.formaPago(),20);forma=forma==null?null:forma.toUpperCase(Locale.ROOT);
        if(forma==null||!ComisionLiquidacion.FORMAS_PAGO.contains(forma))throw regla("Declara una forma de cobro admitida.");
        return new DatosCobro(id(d.comision()),version(d.versionEsperada()),d.tramo()==null?null:id(d.tramo()),
                importe(d.monto(),false),CondicionesEconomicas.moneda(d.moneda(),"del cobro"),d.fecha(),forma,
                d.parteObligada(),d.beneficiario(),d.destino(),texto(d.referenciaExterna(),null),d.evidencias(),texto(d.observacion(),300));
    }
    private long id(Long id){if(id==null||id<=0)throw regla("Identifica el recurso.");return id;}
    private long version(Long v){if(v==null||v<0)throw regla("Aporta la versión observada de la comisión.");return v;}
    private void version(ComisionLiquidacion c,Long v){if(c.getVersion()!=version(v))throw new ConflictoException("La versión de la comisión cambió; vuelve a revisar.");}
    private BigDecimal importe(BigDecimal n,boolean admiteCero) {
        if(n==null||n.signum()<0||(!admiteCero&&n.signum()==0))throw regla("El importe debe ser "+(admiteCero?"cero o positivo.":"mayor que cero."));
        try{var v=n.setScale(2,RoundingMode.UNNECESSARY);if(v.precision()>14)throw regla("El importe excede la precisión monetaria.");return v;}
        catch(ArithmeticException e){throw regla("El importe debe conservarse exactamente con dos decimales como máximo.");}
    }
    private String texto(String s,Integer max){if(s==null||s.isBlank())return null;String t=s.trim();if(max!=null&&t.length()>max)throw regla("El texto excede su longitud admitida.");return t;}
    private String refJson(SujetoDeclarado s){return s==null?null:documentos.objetoCanonico(s.referencia());}
    private String etiqueta(SujetoDeclarado s){return s==null?null:s.etiqueta();}
    private LocalDate hoy(){return LocalDate.now(reloj.withZone(ZoneId.of("America/Lima")));}
    private OffsetDateTime ahora(){return reloj.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);}
    private ReglaNegocioException regla(String texto){return new ReglaNegocioException(texto);}

    record Momento(FuenteHitoConfirmado hito,List<String> faltantes) { }
    record TramoMaterial(int indice,Calculo calculo,Devengo devengo,Exigibilidad exigibilidad) { }
    record Material(Calculo calculo,Devengo devengo,Exigibilidad exigibilidad,List<Vencimiento> calendario,
                    List<TramoMaterial> tramos,ReferenciaFuenteEconomica fuenteCalculo,
                    List<FuenteEconomicaFinal> fuentesAplicadas) { }
    record Entrada(PactoFirmado pacto,ReferenciaContextoEconomico contexto,OperacionInmobiliaria operacion,
                   List<FuenteEconomicaFinal> fuentes,List<FuenteHitoConfirmado> hechos,
                   Long contratoAlquiler,Long contratoVenta) { }
    record Saldos(Map<String,BigDecimal> recibidos,Map<String,BigDecimal> pagos,BigDecimal saldoInformativo) { }
    record ReciboEvaluacion(ReferenciaContextoEconomico contexto,ReferenciaHito causa,ReferenciaPacto pacto,
                           long evaluacion,FichaComision resultado,Procedencia procedencia) { }
    record ReciboReparto(DatosReparto original,ResultadoReparto resultado,Procedencia procedencia) { }
    record ReciboCobro(DatosCobro original,ResultadoCobro resultado,Procedencia procedencia) { }
    record ReciboGestion(String capacidad,DatosReprogramacion reprogramacion,DatosConclusion conclusion,
                         String claveAntecedente,ResultadoGestion resultado,Procedencia procedencia) { }
}
