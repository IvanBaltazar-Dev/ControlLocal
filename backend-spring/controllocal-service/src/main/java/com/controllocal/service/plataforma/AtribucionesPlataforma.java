package com.controllocal.service.plataforma;

import com.controllocal.service.ContextosService;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import com.controllocal.persistence.presupuesto.AtribucionesPlataformaRepository;
import com.controllocal.persistence.presupuesto.AtribucionesPlataformaRepository.Concesion;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CAT-R25: evaluación o compras manuales expresamente concedidas; nunca Actor o rol de tenant. */
@Service
public class AtribucionesPlataforma {
    public enum Atribucion { FINANCIERO_PLATFORM,OPERACION_IA_PLATFORM,BROX_PLATFORM,INVESTIGACION_PLATFORM,REVISION_MERCADO_PLATFORM }
    public enum Accion { FINANCIAR,REVOCAR_FINANCIACION,LEER_LEDGER,AJUSTAR_COSTO,ADMITIR,INICIAR,CONSULTAR_ESTADO,LEER_MUESTRA,
        CONSULTAR_COMPRA,INICIAR_VERIFICACION,VERIFICAR_FONDOS,REGISTRAR_INCIDENCIA,
        CONSULTAR_TRABAJO,TOMAR_TRABAJO,ENTREGAR_EVIDENCIA,CONFIGURAR_MERCADO,REVISAR_EVIDENCIA,PUBLICAR_REFERENCIA,CONFIGURAR_REGLA_MANUAL,CONCILIAR_TRABAJO }
    public enum Campo { ESTADO,CONFIGURACION,METRICA_TECNICA,LEDGER,MUESTRA,PEDIDO_COMERCIAL,DATOS_PAGO_VERIFICADO,METADATOS_TRABAJO,CONTEXTO_MINIMO,EVIDENCIA_PUBLICA }
    public enum RecursoCompra { COMPRAS_MANUALES_WEB_MVP }
    public enum RecursoMercado { MERCADO_MANUAL_I0 }
    public static final String FINALIDAD_MERCADO="EVIDENCIA_MERCADO_BROX";
    public static final String FINALIDAD_COMPRAS="GESTION_COMPRAS_MANUALES_BROX";
    private static final Set<Accion> ACCIONES_COMPRA=Set.of(Accion.CONSULTAR_COMPRA,Accion.INICIAR_VERIFICACION,
        Accion.VERIFICAR_FONDOS,Accion.REGISTRAR_INCIDENCIA);
    private static final Set<Campo> CAMPOS_COMPRA=Set.of(Campo.ESTADO,Campo.PEDIDO_COMERCIAL,Campo.DATOS_PAGO_VERIFICADO);
    private static final Set<Accion> ACCIONES_EVALUACION=Set.of(Accion.FINANCIAR,Accion.REVOCAR_FINANCIACION,Accion.LEER_LEDGER,Accion.AJUSTAR_COSTO,
        Accion.ADMITIR,Accion.INICIAR,Accion.CONSULTAR_ESTADO,Accion.LEER_MUESTRA);
    private static final Set<Campo> CAMPOS_EVALUACION=Set.of(Campo.ESTADO,Campo.CONFIGURACION,Campo.METRICA_TECNICA,Campo.LEDGER,Campo.MUESTRA);
    public record SesionPlataforma(String referenciaSesion,long revisionSesion,UUID concesion) {}
    public record Datos(UUID id,long destinatario,Atribucion atribucion,UUID plan,String versionPlan,
        UUID caso,UUID fondo,String finalidad,Set<Accion> acciones,Set<Campo> campos,Instant desde,Instant hasta,String evidencia,
        @JsonInclude(JsonInclude.Include.NON_NULL) RecursoCompra recursoCompra,
        @JsonInclude(JsonInclude.Include.NON_NULL) RecursoMercado recursoMercado) {
        public Datos {acciones=acciones==null?null:Collections.unmodifiableSet(new TreeSet<>(acciones));campos=campos==null?null:Collections.unmodifiableSet(new TreeSet<>(campos));}
        public Datos(UUID id,long destinatario,Atribucion atribucion,UUID plan,String versionPlan,UUID caso,UUID fondo,
                String finalidad,Set<Accion> acciones,Set<Campo> campos,Instant desde,Instant hasta,String evidencia){
            this(id,destinatario,atribucion,plan,versionPlan,caso,fondo,finalidad,acciones,campos,desde,hasta,evidencia,null,null);
        }
        public Datos(UUID id,long destinatario,Atribucion atribucion,UUID plan,String versionPlan,UUID caso,UUID fondo,String finalidad,Set<Accion> acciones,Set<Campo> campos,Instant desde,Instant hasta,String evidencia,RecursoCompra compra){this(id,destinatario,atribucion,plan,versionPlan,caso,fondo,finalidad,acciones,campos,desde,hasta,evidencia,compra,null);}
    }
    public record Actual(long cuenta,UUID concesion,UUID plan,String versionPlan,String finalidad,UUID caso,UUID fondo,
            @JsonInclude(JsonInclude.Include.NON_NULL) RecursoCompra recursoCompra,
            @JsonInclude(JsonInclude.Include.NON_NULL) RecursoMercado recursoMercado) {
        public Actual(long cuenta,UUID concesion,UUID plan,String versionPlan,String finalidad,UUID caso,UUID fondo){
            this(cuenta,concesion,plan,versionPlan,finalidad,caso,fondo,null,null);
        }
        public Actual(long cuenta,UUID concesion,UUID plan,String versionPlan,String finalidad,UUID caso,UUID fondo,RecursoCompra compra){this(cuenta,concesion,plan,versionPlan,finalidad,caso,fondo,compra,null);}
    }
    public record OpcionCompra(UUID referencia,String finalidad,Set<Accion> acciones,Set<Campo> campos,Instant desde,Instant hasta) {
        public OpcionCompra {acciones=Collections.unmodifiableSet(new TreeSet<>(acciones));campos=Collections.unmodifiableSet(new TreeSet<>(campos));}
    }
    public record OpcionMercado(UUID referencia,Atribucion atribucion,String finalidad,Set<Accion> acciones,Set<Campo> campos,Instant desde,Instant hasta) {
        public OpcionMercado {acciones=Collections.unmodifiableSet(new TreeSet<>(acciones));campos=Collections.unmodifiableSet(new TreeSet<>(campos));}
    }
    /** Opciones propias; conocer el identificador nunca sustituye la revalidación por acción/campo. */
    @Transactional public List<OpcionMercado> atribucionesMercado(String sid,long revision){
        long cuenta=identidad(new SesionPlataforma(sid,revision,null));Instant ahora=Instant.now();
        return repo.mercadoVigentes(cuenta,ahora).stream().filter(c->vigente(c,cuenta,ahora)).map(c->{
            Atribucion a=Atribucion.valueOf(c.atribucion());Set<Accion> acciones=new TreeSet<>();for(String v:valores(c.acciones()))acciones.add(Accion.valueOf(v));
            Set<Campo> campos=new TreeSet<>();for(String v:valores(c.campos()))campos.add(Campo.valueOf(v));
            if(!FINALIDAD_MERCADO.equals(c.finalidad())||c.recursoCompra()!=null||c.plan()!=null||c.version()!=null||c.caso()!=null||c.fondo()!=null||acciones.isEmpty()||campos.isEmpty()||!accionesMercado(a).containsAll(acciones)||!camposMercado(a).containsAll(campos))throw new IllegalStateException("La concesión conserva su alcance de mercado cerrado.");
            return new OpcionMercado(c.id(),a,c.finalidad(),acciones,campos,c.desde(),c.hasta());
        }).toList();
    }
    private final ContextosService contextos;
    private final AtribucionesPlataformaRepository repo;
    private final ObjectProvider<AcreditacionesTitularPlataforma> acreditadores;
    private final Documentos json;
    public AtribucionesPlataforma(ContextosService contextos,AtribucionesPlataformaRepository repo,
            ObjectProvider<AcreditacionesTitularPlataforma> acreditadores,Documentos json){
        this.contextos=contextos;this.repo=repo;this.acreditadores=acreditadores;this.json=json;
    }
    @Transactional
    public Actual resolver(SesionPlataforma s,Atribucion atribucion,Accion accion,Campo campo){
        if(accion==null||campo==null||!ACCIONES_EVALUACION.contains(accion)||!CAMPOS_EVALUACION.contains(campo))
            throw new AccesoNoAutorizadoException();
        var c=vigente(s,atribucion,accion,Set.of(campo));
        if(c.recursoCompra()!=null||c.recursoMercado()!=null||c.plan()==null||c.version()==null||c.version().isBlank())
            throw new AccesoNoAutorizadoException();
        return new Actual(c.cuenta(),c.id(),c.plan(),c.version(),c.finalidad(),c.caso(),c.fondo());
    }
    /** Concesión de la colección cerrada; el caso de uso sigue comprobando pedido/canal/versiones. */
    @Transactional
    public Actual exigirCompra(SesionPlataforma s,Accion accion,Set<Campo> campos){
        if(accion==null||!ACCIONES_COMPRA.contains(accion)||campos==null||campos.isEmpty()
            ||campos.stream().anyMatch(Objects::isNull)||!CAMPOS_COMPRA.containsAll(campos))
            throw new AccesoNoAutorizadoException();
        var c=vigente(s,Atribucion.FINANCIERO_PLATFORM,accion,campos);
        if(!recursoCompra(c))throw new AccesoNoAutorizadoException();
        return new Actual(c.cuenta(),c.id(),null,null,c.finalidad(),null,null,RecursoCompra.COMPRAS_MANUALES_WEB_MVP);
    }
    /** I0 usa la misma acreditación, sesión y revocación; no un rol de organización. */
    @Transactional
    public Actual exigirMercado(SesionPlataforma s,Atribucion atribucion,Accion accion,Set<Campo> campos){
        if(accion==null||campos==null||campos.isEmpty()||!accionesMercado(atribucion).contains(accion)||!camposMercado(atribucion).containsAll(campos))throw new AccesoNoAutorizadoException();
        var c=vigente(s,atribucion,accion,campos);
        if(!RecursoMercado.MERCADO_MANUAL_I0.name().equals(c.recursoMercado())||c.recursoCompra()!=null||c.plan()!=null||c.version()!=null||c.caso()!=null||c.fondo()!=null||!FINALIDAD_MERCADO.equals(c.finalidad()))throw new AccesoNoAutorizadoException();
        return new Actual(c.cuenta(),c.id(),null,null,c.finalidad(),null,null,null,RecursoMercado.MERCADO_MANUAL_I0);
    }
    private static Set<Accion> accionesMercado(Atribucion a){
        if(a==null)return Set.of();return switch(a){
            case INVESTIGACION_PLATFORM->Set.of(Accion.CONSULTAR_TRABAJO,Accion.TOMAR_TRABAJO,Accion.ENTREGAR_EVIDENCIA);
            case REVISION_MERCADO_PLATFORM->Set.of(Accion.CONSULTAR_TRABAJO,Accion.CONFIGURAR_MERCADO,Accion.REVISAR_EVIDENCIA,Accion.PUBLICAR_REFERENCIA);
            case FINANCIERO_PLATFORM->Set.of(Accion.CONSULTAR_TRABAJO,Accion.CONFIGURAR_REGLA_MANUAL,Accion.CONCILIAR_TRABAJO);
            default->Set.of();};
    }
    private static Set<Campo> camposMercado(Atribucion a){
        if(a==null)return Set.of();return switch(a){
            case INVESTIGACION_PLATFORM->Set.of(Campo.METADATOS_TRABAJO,Campo.CONTEXTO_MINIMO,Campo.EVIDENCIA_PUBLICA);
            case REVISION_MERCADO_PLATFORM->Set.of(Campo.METADATOS_TRABAJO,Campo.CONFIGURACION,Campo.EVIDENCIA_PUBLICA);
            case FINANCIERO_PLATFORM->Set.of(Campo.METADATOS_TRABAJO,Campo.LEDGER);
            default->Set.of();};
    }
    /** Opciones propias, no autoridad conservada para ejecutar después ni padrón de otras cuentas. */
    @Transactional
    public List<OpcionCompra> atribucionesCompra(String sid,long revision){
        long cuenta=identidad(new SesionPlataforma(sid,revision,null));Instant ahora=Instant.now();
        return repo.comprasVigentes(cuenta,ahora).stream().filter(c->vigente(c,cuenta,ahora)&&recursoCompra(c))
            .map(c->{
                Set<Accion> acciones=new TreeSet<>();for(String a:valores(c.acciones()))acciones.add(Accion.valueOf(a));
                Set<Campo> campos=new TreeSet<>();for(String f:valores(c.campos()))campos.add(Campo.valueOf(f));
                if(acciones.isEmpty()||campos.isEmpty()||!ACCIONES_COMPRA.containsAll(acciones)||!CAMPOS_COMPRA.containsAll(campos))
                    throw new IllegalStateException("La concesión de compras no conserva su alcance cerrado.");
                return new OpcionCompra(c.id(),c.finalidad(),acciones,campos,c.desde(),c.hasta());
            }).toList();
    }
    private Concesion vigente(SesionPlataforma s,Atribucion atribucion,Accion accion,Set<Campo> campos){
        long cuenta=identidad(s);
        if(s.concesion()==null||atribucion==null)throw new AccesoNoAutorizadoException();
        var c=repo.bloquear(s.concesion()).orElseThrow(AccesoNoAutorizadoException::new);
        Set<String> requeridos=new HashSet<>();for(var campo:campos)requeridos.add(campo.name());
        if(!vigente(c,cuenta,Instant.now())||!atribucion.name().equals(c.atribucion())
            ||!valores(c.acciones()).contains(accion.name())||!valores(c.campos()).containsAll(requeridos))
            throw new AccesoNoAutorizadoException();
        return c;
    }
    private static boolean vigente(Concesion c,long cuenta,Instant ahora){
        return c.cuenta()==cuenta&&c.revocada()==null&&c.desde()!=null&&c.hasta()!=null
            &&!ahora.isBefore(c.desde())&&ahora.isBefore(c.hasta());
    }
    private static boolean recursoCompra(Concesion c){
        return c.recursoMercado()==null&&RecursoCompra.COMPRAS_MANUALES_WEB_MVP.name().equals(c.recursoCompra())
            &&Atribucion.FINANCIERO_PLATFORM.name().equals(c.atribucion())&&FINALIDAD_COMPRAS.equals(c.finalidad())
            &&c.plan()==null&&c.version()==null&&c.caso()==null&&c.fondo()==null;
    }
    private Set<String> valores(String lista){return Set.of(json.comoObjeto(lista,String[].class));}
    @Transactional
    public Actual exigir(SesionPlataforma s,Atribucion atribucion,Accion accion,Campo campo,
                         UUID plan,String version,String finalidad) {
        var a=resolver(s,atribucion,accion,campo);
        if(!a.plan().equals(plan)||!a.versionPlan().equals(version)||!a.finalidad().equals(finalidad))
            throw new AccesoNoAutorizadoException();
        return a;
    }
    @Transactional
    public void conceder(Datos d,UUID decision,SesionPlataforma s) {
        if(d==null||d.id()==null||d.destinatario()<=0||d.atribucion()==null||d.finalidad()==null||d.finalidad().isBlank()
            ||d.acciones()==null||d.acciones().isEmpty()||d.campos()==null||d.campos().isEmpty()
            ||d.desde()==null||d.hasta()==null||!d.hasta().isAfter(d.desde())
            ||d.evidencia()==null||d.evidencia().isBlank())throw new ReglaNegocioException("Conserva el alcance acreditado de la atribución.");
        if(d.acciones().contains(Accion.AJUSTAR_COSTO)&&(d.atribucion()!=Atribucion.FINANCIERO_PLATFORM||!d.campos().contains(Campo.LEDGER)))
            throw new ReglaNegocioException("El ajuste de costo requiere atribución financiera y campo ledger expresos.");
        if(d.recursoMercado()!=null){
            if(d.recursoCompra()!=null||d.plan()!=null||d.versionPlan()!=null||d.caso()!=null||d.fondo()!=null||!FINALIDAD_MERCADO.equals(d.finalidad())
                ||!accionesMercado(d.atribucion()).containsAll(d.acciones())||!camposMercado(d.atribucion()).containsAll(d.campos()))throw new ReglaNegocioException("La atribución de mercado conserva recurso, finalidad, acciones y campos propios.");
        }else if(d.recursoCompra()==null){
            if(d.plan()==null||d.versionPlan()==null||d.versionPlan().isBlank()
                ||!Set.of(Atribucion.FINANCIERO_PLATFORM,Atribucion.OPERACION_IA_PLATFORM,Atribucion.BROX_PLATFORM).contains(d.atribucion())
                ||(d.atribucion()==Atribucion.FINANCIERO_PLATFORM)!=(d.fondo()!=null)
                ||d.atribucion()==Atribucion.BROX_PLATFORM&&d.caso()==null
                ||!ACCIONES_EVALUACION.containsAll(d.acciones())||!CAMPOS_EVALUACION.containsAll(d.campos()))
                throw new ReglaNegocioException("La atribución requiere su recurso financiero o muestra exacta.");
        }else if(d.atribucion()!=Atribucion.FINANCIERO_PLATFORM||d.plan()!=null||d.versionPlan()!=null||d.caso()!=null||d.fondo()!=null
            ||!FINALIDAD_COMPRAS.equals(d.finalidad())||!ACCIONES_COMPRA.containsAll(d.acciones())||!CAMPOS_COMPRA.containsAll(d.campos()))
            throw new ReglaNegocioException("La atribución de compras conserva su recurso, finalidad, acciones y campos propios.");
        String hash=Idempotencia.huellaExacta(json.objetoCanonico(d));
        long otorgante=acreditar(decision,"CONCEDER_ATRIBUCION",hash,d.destinatario(),s);
        var anterior=repo.bloquear(d.id());
        if(anterior.isPresent()){
            if(!hash.equals(anterior.get().huella()))throw new ConflictoException("La concesión conserva otro contenido.");
            return;
        }
        repo.insertar(new AtribucionesPlataformaRepository.Concesion(d.id(),otorgante,d.destinatario(),
            d.atribucion().name(),d.plan(),d.versionPlan(),d.caso(),d.fondo(),d.finalidad(),json.lista(d.acciones().stream().sorted().toList()),
            json.lista(d.campos().stream().sorted().toList()),d.desde(),d.hasta(),d.evidencia(),hash,null,
            d.recursoCompra()==null?null:d.recursoCompra().name(),d.recursoMercado()==null?null:d.recursoMercado().name()),decision,Instant.now());
    }
    @Transactional
    public void revocar(UUID concesion,UUID decision,String motivo,SesionPlataforma s) {
        if(concesion==null||motivo==null||motivo.isBlank())throw new ReglaNegocioException("Conserva la causa de revocación.");
        identidad(s);
        var c=repo.bloquearParaRevocar(concesion).orElseThrow(AccesoNoAutorizadoException::new);
        String hash=Idempotencia.huellaExacta(json.objetoCanonico(Map.of("concesion",concesion,"motivo",motivo)));
        long actor=acreditar(decision,"REVOCAR_ATRIBUCION",hash,c.cuenta(),s);
        if(c.revocada()==null&&!repo.revocar(concesion,actor,decision,motivo,Instant.now()))
            throw new ConflictoException("La concesión cambió durante su revocación.");
    }
    /** Acreditación administrativa distinta de ejercer una atribución. Sin adaptador no se habilita. */
    @Transactional
    public long acreditar(UUID decision,String accion,String hash,Long destinatario,SesionPlataforma s) {
        long cuenta=identidad(s);
        var proveedor=acreditadores.getIfAvailable();
        if(proveedor==null)throw new CapacidadDeshabilitadaException();
        var d=proveedor.acreditar(decision).orElseThrow(AccesoNoAutorizadoException::new);
        if(d.referencia()==null||!d.referencia().equals(decision)||d.otorgante()!=cuenta
            ||!Objects.equals(d.destinatario(),destinatario)||!accion.equals(d.actuacion())||!hash.equals(d.huellaContenido())
            ||d.evidencia()==null||d.evidencia().isBlank()||d.acreditadaEn()==null||d.acreditadaEn().isAfter(Instant.now()))
            throw new AccesoNoAutorizadoException();
        repo.decision(decision,cuenta,accion,hash,d.evidencia(),d.acreditadaEn(),Instant.now());
        var registrada=repo.decision(decision).orElseThrow();
        if(registrada.cuenta()!=cuenta||!registrada.accion().equals(accion)||!registrada.huella().equals(hash))throw new ConflictoException("La decisión acredita otro contenido.");
        return cuenta;
    }
    /** Revalida una decisión YA registrada. No acredita al operador ni escribe otra aceptación. */
    public void verificarDecisionRegistrada(UUID referencia,String actuacion,long otorgante,Long destinatario){
        var registrada=repo.fuenteDecision(referencia).orElseThrow(AccesoNoAutorizadoException::new);
        var proveedor=acreditadores.getIfAvailable();if(proveedor==null)throw new CapacidadDeshabilitadaException();
        var d=proveedor.acreditar(referencia).orElseThrow(AccesoNoAutorizadoException::new);
        // PostgreSQL conserva microsegundos: no convertir nanos de fuente en otra decisión.
        if(!Objects.equals(d.referencia(),referencia)||d.otorgante()!=otorgante||registrada.otorgante()!=otorgante
                ||!Objects.equals(d.destinatario(),destinatario)||!actuacion.equals(d.actuacion())||!actuacion.equals(registrada.actuacion())
                ||!registrada.huella().equals(d.huellaContenido())||!Objects.equals(registrada.evidencia(),d.evidencia())
                ||d.acreditadaEn()==null||d.acreditadaEn().isAfter(Instant.now())
                ||!registrada.acreditada().plusNanos(500).truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                    .equals(d.acreditadaEn().plusNanos(500).truncatedTo(java.time.temporal.ChronoUnit.MICROS)))throw new AccesoNoAutorizadoException();
    }
    private long identidad(SesionPlataforma s) {
        if(s==null||s.referenciaSesion()==null||s.revisionSesion()<=0)throw new AccesoNoAutorizadoException();
        var actual=contextos.exigirIdentidad(s.referenciaSesion(),s.revisionSesion());
        var r=actual.restricciones();
        if(r!=null&&(r.debeCambiarContrasena()||r.debeEnrolarMfa()))throw new AccesoNoAutorizadoException();
        return actual.identidad().idCuenta();
    }
}
