package com.controllocal.service.materialia;

import com.controllocal.persistence.materialia.RegistroTratamientoMaterialIaRepository;
import com.controllocal.persistence.materialia.RegistroTratamientoMaterialIaRepository.*;
import com.controllocal.persistence.repositorio.CuentaAccesoRepository;
import com.controllocal.persistence.repositorio.UsuarioOrganizacionRepository;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.actividad.ContextosDeActividad;
import com.controllocal.service.documentos.SeleccionInmueblePieza;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.materialia.AvisosTratamientoMaterialIa.*;
import com.controllocal.service.materialia.FuentesMaterialIa.*;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Cuenta/SID C1 y autoridad propietaria, sin otra política comercial o aprobación por un tercero. */
@Service
@Transactional
public class EvidenciasTratamientoMaterialIaImpl implements EvidenciasTratamientoMaterialIa {
    private final RegistroTratamientoMaterialIaRepository repo;
    private final ContextosService contextos;
    private final ContextosDeActividad actividad;
    private final UsuarioOrganizacionRepository miembros;
    private final CuentaAccesoRepository cuentas;
    private final SeleccionInmueblePieza fisica;
    private final AccesoEncargo encargos;
    private final Documentos json;
    private final List<AvisosTratamientoMaterialIa> fuentes;
    public EvidenciasTratamientoMaterialIaImpl(RegistroTratamientoMaterialIaRepository repo,ContextosService contextos,
            ContextosDeActividad actividad,UsuarioOrganizacionRepository miembros,CuentaAccesoRepository cuentas,
            SeleccionInmueblePieza fisica,AccesoEncargo encargos,Documentos json,List<AvisosTratamientoMaterialIa> fuentes){
        this.repo=repo;this.contextos=contextos;this.actividad=actividad;this.miembros=miembros;this.cuentas=cuentas;
        this.fisica=fisica;this.encargos=encargos;this.json=json;this.fuentes=List.copyOf(fuentes);
    }
    @Override public EvidenciaTratamiento resolver(AmbitoTratamiento a,Actor actor,ContextoSesion sesion){
        long cuenta=sesion(actor,sesion);autorizar(a,actor);return evaluar(a,actor,cuenta).evidencia();
    }
    @Override public void exigirVigente(String referencia,AmbitoTratamiento a,DestinoTratamiento destino,Actor actor,ContextoSesion sesion){
        if(destino==null||!texto(destino.proveedor())||!texto(destino.perfil())||!texto(destino.versionPerfil()))
            throw new ReglaNegocioException("La transferencia requiere su destino técnico real.");
        long cuenta=sesion(actor,sesion);autorizar(a,actor);
        exigir(referencia,evaluar(a,actor,cuenta),destino);
    }
    @Override public void revalidarActual(String referencia,AmbitoTratamiento a,Actor actor){
        long cuenta=cuentaActual(actor);autorizar(a,actor);exigir(referencia,evaluar(a,actor,cuenta),null);
    }
    @Override public void revalidarArtefacto(String referencia,AmbitoTratamiento a,Actor actor){
        validarUsoArtefacto(referencia,a,actor);
    }
    @Override public EstadoUsoArtefacto evaluarUsoArtefacto(String referencia,AmbitoTratamiento a,Actor actor){
        // La captura ocurre antes de que la denegación abandone este proxy transaccional.
        try{validarUsoArtefacto(referencia,a,actor);return EstadoUsoArtefacto.PERMITIDO;}
        catch(AccesoNoAutorizadoException|NoEncontradoException denegada){return EstadoUsoArtefacto.PERMISO_INSUFICIENTE;}
        catch(CapacidadDeshabilitadaException deshabilitada){return EstadoUsoArtefacto.CAPACIDAD_DESHABILITADA;}
    }
    private void validarUsoArtefacto(String referencia,AmbitoTratamiento a,Actor actor){
        cuentaActual(actor);autorizar(a,actor);
        UUID idReferencia=uuid(referencia);var decisiones=repo.eventosDeOrganizacion(actor.idOrganizacion(),idReferencia);
        if(decisiones.size()!=1)throw new AccesoNoAutorizadoException();
        var d=decisiones.getFirst();String alcance=alcance(a,actor);
        if(!idReferencia.equals(d.evento())||!"ACEPTAR".equals(d.decision())||d.organizacion()!=actor.idOrganizacion()
                ||!alcance.equals(d.alcance())||d.aviso()==null)throw new AccesoNoAutorizadoException();
        integridad(d.datos(),d.huella());
        var p=repo.presentacion(d.presentacion(),d.cuenta(),d.organizacion()).orElseThrow(AccesoNoAutorizadoException::new);
        integridad(p.ambito(),p.huellaAmbito());
        var presentado=json.comoObjeto(p.ambito(),AmbitoTratamiento.class);
        if(!p.id().equals(d.presentacion())||p.cuenta()!=d.cuenta()||p.organizacion()!=d.organizacion()||!p.alcance().equals(d.alcance())
                ||!p.alcance().equals(alcance(presentado,actor))||!Objects.equals(p.aviso(),d.aviso())
                ||p.versionCobertura()!=d.version()-1)throw new IllegalStateException("La evidencia histórica no conserva su presentación.");
        var registrado=repo.aviso(d.aviso()).orElseThrow(()->new IllegalStateException("Falta el aviso histórico del tratamiento."));
        integridad(registrado.contenido(),registrado.huella());
        var aviso=json.comoObjeto(registrado.contenido(),Contenido.class);
        if(!registrado.id().equals(d.aviso())||!registrado.referencia().equals(aviso.referencia())||registrado.version()!=aviso.version()
                ||!registrado.huella().equals(huella(aviso)))throw new IllegalStateException("El aviso histórico cambió.");
        var datos=json.comoMapa(d.datos());
        var esperados=Map.ofEntries(Map.entry("cuenta",d.cuenta()),Map.entry("organizacion",d.organizacion()),
                Map.entry("evento",d.evento()),Map.entry("presentacion",p.id()),Map.entry("version",d.version()),
                Map.entry("decision",d.decision()),Map.entry("aviso",d.aviso()),Map.entry("huellaAviso",registrado.huella()),
                Map.entry("huellaFuente",presentado.huellaFuente()),Map.entry("huellaPayload",presentado.huellaPayload()));
        esperados.forEach((k,v)->{if(!Objects.equals(String.valueOf(v),String.valueOf(datos.get(k))))
            throw new IllegalStateException("La decisión histórica no conserva su contenido.");});
        if(!aviso.finalidades().contains(a.solicitud().accion())||!aviso.categorias().containsAll(a.categorias())
                ||!esHuella(aviso.huellaCondiciones())||!Idempotencia.huellaExacta(aviso.condicionesTexto()).equals(aviso.huellaCondiciones()))
            throw new AccesoNoAutorizadoException();
        var fuente=fuente();
        if(fuente==null||comprobarLegitimacion(a,aviso,fuente.legitimacionUsoArtefacto(a,aviso,actor).orElse(null))==null)
            throw new CapacidadDeshabilitadaException();
        // La preferencia personal actual se usa para nuevas transferencias, no se hereda ni invalida por sí sola este artefacto.
        // Recurso/base/finalidad actuales sí conservan su autoridad y revocación.
        autorizar(a,actor);cuentaActual(actor);
    }
    @Override public AvisoParaDecision presentar(AmbitoTratamiento a,Actor actor,ContextoSesion sesion){
        long cuenta=sesion(actor,sesion);autorizar(a,actor);String alcance=alcance(a,actor);
        bloquear(cuenta,actor.idOrganizacion(),alcance);
        long version=repo.ultima(cuenta,actor.idOrganizacion(),alcance).map(DecisionFila::version).orElse(0L);
        var contenido=avisoActual(a,actor);Aviso aviso=contenido==null?null:registrar(contenido);
        UUID id=UUID.randomUUID();String datos=json.objetoCanonico(paraHistoria(a));
        repo.presentar(new Presentacion(id,cuenta,actor.idOrganizacion(),alcance,sesion.identidad(),version,
                aviso==null?null:aviso.id(),datos,Idempotencia.huellaExacta(datos)));
        return new AvisoParaDecision(id.toString(),version,aviso==null?"PENDIENTE":"INFORMADO",
                aviso==null?null:aviso.huella(),contenido==null?"No está acreditado un aviso aplicable al tratamiento solicitado.":contenido.texto(),
                contenido==null?null:contenido.proveedorLegal(),contenido==null?null:contenido.condicionesVersion(),
                contenido==null?null:contenido.condicionesTexto(),
                contenido==null?a.categorias():contenido.categorias(),Set.of(a.solicitud().accion()));
    }
    @Override public DecisionRegistrada capturar(Captura d,Actor actor,ContextoSesion sesion,Procedencia procedencia){
        if(d==null||d.evento()==null||d.decision()==null||d.versionCobertura()<0||procedencia==null)
            throw new ReglaNegocioException("La decisión propia necesita su presentación y versión observadas.");
        if(procedencia.agente()!=null)throw new AccesoNoAutorizadoException();
        long cuenta=sesion(actor,sesion);
        var p=repo.presentacion(uuid(d.referenciaPresentacion()),cuenta,actor.idOrganizacion()).orElseThrow(AccesoNoAutorizadoException::new);
        if(!p.sesion().equals(sesion.identidad()))throw new AccesoNoAutorizadoException();
        integridad(p.ambito(),p.huellaAmbito());var a=json.comoObjeto(p.ambito(),AmbitoTratamiento.class);autorizar(a,actor);
        if(!p.alcance().equals(alcance(a,actor)))throw new IllegalStateException("El alcance presentado no conserva su integridad.");
        bloquear(cuenta,actor.idOrganizacion(),p.alcance());
        var previo=repo.evento(cuenta,d.evento());
        if(previo.isPresent()){
            var anterior=previo.get();integridad(anterior.datos(),anterior.huella());
            if(anterior.organizacion()!=actor.idOrganizacion()||!anterior.presentacion().equals(p.id())
                    ||!anterior.decision().equals(d.decision().name())||p.versionCobertura()!=d.versionCobertura())
                throw new ConflictoException("El evento identifica otra decisión propia.");
            return resultado(anterior);
        }
        var ultima=repo.ultima(cuenta,actor.idOrganizacion(),p.alcance()).orElse(null);
        long version=ultima==null?0:ultima.version();
        if(version!=d.versionCobertura()||p.versionCobertura()!=version)throw new ConflictoException("Cambió la preferencia; vuelve a revisar el aviso.");
        Contenido contenido=avisoActual(a,actor);
        Aviso aviso=p.aviso()==null?null:repo.aviso(p.aviso()).orElseThrow();
        Legitimacion legitimacion=null;
        if(d.decision()==Decision.ACEPTAR){
            if(contenido==null||aviso==null)throw new CapacidadDeshabilitadaException();
            integridad(aviso.contenido(),aviso.huella());
            if(!aviso.huella().equals(huella(contenido)))throw new ConflictoException("Cambió el aviso o sus condiciones; vuelve a revisarlo.");
            legitimacion=legitimacion(a,contenido,actor);
        }
        if(d.decision()==Decision.REVOCAR&&(ultima==null||!"ACEPTAR".equals(ultima.decision())))
            throw new ReglaNegocioException("No existe una aceptación propia vigente que retirar.");
        var datos=new LinkedHashMap<String,Object>();
        datos.put("cuenta",cuenta);datos.put("organizacion",actor.idOrganizacion());datos.put("persona",actor.idPersona());
        datos.put("rol",actor.idRolOperativo());datos.put("atribucion",actor.rolEfectivo());
        datos.put("sesion",sesion.identidad());datos.put("revisionContexto",sesion.revisionContexto());
        datos.put("evento",d.evento());datos.put("presentacion",p.id());datos.put("version",version+1);
        datos.put("decision",d.decision());datos.put("aviso",p.aviso());datos.put("huellaAviso",aviso==null?null:aviso.huella());
        datos.put("huellaFuente",a.huellaFuente());datos.put("huellaPayload",a.huellaPayload());
        datos.put("legitimacion",legitimacion==null?null:legitimacion.referencia());
        datos.put("fuenteLegitimacion",legitimacion==null?null:legitimacion.fuente());
        datos.put("huellaLegitimacion",legitimacion==null?null:legitimacion.huella());datos.put("procedencia",procedencia);
        String canonico=json.objetoCanonico(datos);
        return resultado(repo.decidir(new DecisionFila(d.evento(),cuenta,actor.idOrganizacion(),p.alcance(),version+1,
                p.id(),d.decision().name(),p.aviso(),canonico,Idempotencia.huellaExacta(canonico),null)));
    }
    private record Evaluacion(EvidenciaTratamiento evidencia,Contenido aviso) { }
    private Evaluacion evaluar(AmbitoTratamiento a,Actor actor,long cuenta){
        String alcance=alcance(a,actor);bloquear(cuenta,actor.idOrganizacion(),alcance);
        var ultima=repo.ultima(cuenta,actor.idOrganizacion(),alcance).orElse(null);
        if(ultima==null)return pendiente(a,null);
        integridad(ultima.datos(),ultima.huella());
        if(!"ACEPTAR".equals(ultima.decision()))return new Evaluacion(new EvidenciaTratamiento(
                "REVOCAR".equals(ultima.decision())?EstadoEvidencia.REVOCADA:EstadoEvidencia.RECHAZADA,
                a.solicitud().accion().name(),new ArrayList<>(new TreeSet<>(a.categorias())),null,null,ultima.decision(),
                ultima.evento().toString(),null,Long.toString(ultima.version())),null);
        Contenido contenido=avisoActual(a,actor);
        if(contenido==null||ultima.aviso()==null)return pendiente(a,ultima);
        var registrado=repo.aviso(ultima.aviso()).orElseThrow();integridad(registrado.contenido(),registrado.huella());
        if(!registrado.huella().equals(huella(contenido)))return pendiente(a,ultima);
        var legitimacion=legitimacion(a,contenido,actor);
        if(legitimacion==null)return pendiente(a,ultima);
        return new Evaluacion(new EvidenciaTratamiento(EstadoEvidencia.ACREDITADA,a.solicitud().accion().name(),
                new ArrayList<>(new TreeSet<>(a.categorias())),contenido.condicionesVersion(),contenido.proveedorLegal(),"ACEPTAR",
                ultima.evento().toString(),legitimacion.referencia(),Long.toString(ultima.version())),contenido);
    }
    private Evaluacion pendiente(AmbitoTratamiento a,DecisionFila d){
        return new Evaluacion(new EvidenciaTratamiento(EstadoEvidencia.PENDIENTE,a.solicitud().accion().name(),
                new ArrayList<>(new TreeSet<>(a.categorias())),null,null,d==null?null:d.decision(),
                d==null?null:d.evento().toString(),null,d==null?"0":Long.toString(d.version())),null);
    }
    private void exigir(String referencia,Evaluacion e,DestinoTratamiento destino){
        if(e.evidencia().estado()==EstadoEvidencia.RECHAZADA||e.evidencia().estado()==EstadoEvidencia.REVOCADA)
            throw new AccesoNoAutorizadoException();
        if(e.evidencia().estado()!=EstadoEvidencia.ACREDITADA)throw new CapacidadDeshabilitadaException();
        if(!Objects.equals(referencia,e.evidencia().referenciaEvidencia()))throw new ConflictoException("La cobertura de tratamiento cambió; vuelve a preparar.");
        if(destino!=null&&!e.aviso().destinos().contains(destino))throw new AccesoNoAutorizadoException();
    }
    private long sesion(Actor actor,ContextoSesion sesion){
        if(actor==null||sesion==null)throw new AccesoNoAutorizadoException();
        try{
            var actual=contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor);
            if(!ContextoSesion.de(actual).equals(sesion))throw new AccesoNoAutorizadoException();
            actividad.exigirOperador(actor);return actual.identidad().idCuenta();
        }catch(CredencialesInvalidasException ex){throw new AccesoNoAutorizadoException();}
    }
    private long cuentaActual(Actor actor){
        actividad.exigirOperador(actor);var ids=miembros.cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona());
        if(ids.size()!=1)throw new AccesoNoAutorizadoException();
        var cuenta=cuentas.vista(ids.getFirst()).orElseThrow(AccesoNoAutorizadoException::new);
        if(!"A".equals(cuenta.estadoCuenta())||cuenta.correoConfirmadoEn()==null||cuenta.debeCambiarContrasena()||cuenta.debeEnrolarMfa())
            throw new AccesoNoAutorizadoException();
        return cuenta.idCuenta();
    }
    private void autorizar(AmbitoTratamiento a,Actor actor){
        if(a==null||a.solicitud()==null||a.solicitud().accion()==null||a.solicitud().recurso()==null
                ||!esHuella(a.huellaFuente())||!esHuella(a.huellaPayload())||a.categorias().stream().anyMatch(c->!texto(c)))
            throw new ReglaNegocioException("El alcance debe proceder de la fuente Core actual.");
        var recurso=a.solicitud().recurso();if(!texto(recurso.tipo()))throw new ReglaNegocioException("Referencia de recurso inválida.");long id;
        try{id=Long.parseLong(recurso.referencia());}catch(RuntimeException ex){throw new ReglaNegocioException("Referencia de recurso inválida.");}
        if(id<=0)throw new ReglaNegocioException("Referencia de recurso inválida.");
        switch(recurso.tipo()){
            case "PROPIEDAD"->fisica.exigirLecturaHistorica(actor.idOrganizacion(),id,actor);
            case "ENCARGO"->encargos.leer(id,actor);
            default->throw new ReglaNegocioException("El recurso no corresponde a material IA.");
        }
    }
    private AvisosTratamientoMaterialIa fuente(){
        if(fuentes.size()>1)throw new IllegalStateException("La fuente acreditadora E44 no es unívoca.");
        return fuentes.isEmpty()?null:fuentes.getFirst();
    }
    private Contenido avisoActual(AmbitoTratamiento a,Actor actor){
        var fuente=fuente();if(fuente==null)return null;
        var c=fuente.vigente(a,actor).orElse(null);
        if(c==null||a.categorias().isEmpty()||!texto(c.referencia())||c.version()<1||!texto(c.texto())
                ||!c.finalidades().contains(a.solicitud().accion())||!c.categorias().containsAll(a.categorias())
                ||!texto(c.proveedorLegal())||!texto(c.condicionesVersion())||!texto(c.condicionesTexto())
                ||!texto(c.fuenteCondiciones())||!esHuella(c.huellaCondiciones())
                ||!Idempotencia.huellaExacta(c.condicionesTexto()).equals(c.huellaCondiciones())||c.destinos().isEmpty()
                ||c.destinos().stream().anyMatch(d->!texto(d.proveedor())||!texto(d.perfil())||!texto(d.versionPerfil())))return null;
        return c;
    }
    private Legitimacion legitimacion(AmbitoTratamiento a,Contenido aviso,Actor actor){
        var f=fuente();if(f==null)return null;var l=f.legitimacionActual(a,aviso,actor).orElse(null);
        return comprobarLegitimacion(a,aviso,l);
    }
    private Legitimacion comprobarLegitimacion(AmbitoTratamiento a,Contenido aviso,Legitimacion l){
        if(l==null||!texto(l.referencia())||!texto(l.fuente())||!esHuella(l.huella())
                ||!Objects.equals(l.recurso(),a.solicitud().recurso())||!l.finalidades().contains(a.solicitud().accion())
                ||!l.categorias().containsAll(a.categorias())||!Objects.equals(l.proveedorLegal(),aviso.proveedorLegal())
                ||!Objects.equals(l.condicionesVersion(),aviso.condicionesVersion()))return null;
        return l;
    }
    private Aviso registrar(Contenido c){String contenido=contenido(c);return repo.registrarAviso(c.referencia(),c.version(),Idempotencia.huellaExacta(contenido),contenido);}
    private String huella(Contenido c){return Idempotencia.huellaExacta(contenido(c));}
    private String contenido(Contenido c){
        var m=new TreeMap<String,Object>();m.put("referencia",c.referencia());m.put("version",c.version());m.put("texto",c.texto());
        m.put("finalidades",c.finalidades().stream().map(Enum::name).sorted().toList());m.put("categorias",new TreeSet<>(c.categorias()));
        m.put("proveedorLegal",c.proveedorLegal());m.put("condicionesVersion",c.condicionesVersion());m.put("condicionesTexto",c.condicionesTexto());
        m.put("fuenteCondiciones",c.fuenteCondiciones());m.put("huellaCondiciones",c.huellaCondiciones());
        m.put("destinos",c.destinos().stream().sorted(Comparator.comparing(DestinoTratamiento::proveedor).thenComparing(DestinoTratamiento::perfil).thenComparing(DestinoTratamiento::versionPerfil)).toList());
        return json.objetoCanonico(m);
    }
    private String alcance(AmbitoTratamiento a,Actor actor){
        var r=a.solicitud().recurso();var identidad=new MaterialIaService.Recurso(r.tipo(),Long.toString(Long.parseLong(r.referencia())));
        return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("organizacion",actor.idOrganizacion(),"recurso",identidad,"finalidad",a.solicitud().accion())));
    }
    private void bloquear(long cuenta,long org,String alcance){repo.bloquear(Long.parseUnsignedLong(Idempotencia.huellaExacta("E44\u0000"+cuenta+"\u0000"+org+"\u0000"+alcance).substring(0,16),16));}
    private static AmbitoTratamiento paraHistoria(AmbitoTratamiento a){
        var s=a.solicitud();return new AmbitoTratamiento(new MaterialIaService.Solicitud(null,s.accion(),s.recurso(),s.versionOrigen(),s.fotoOriginal(),null),a.huellaFuente(),new TreeSet<>(a.categorias()),a.huellaPayload());
    }
    private static DecisionRegistrada resultado(DecisionFila d){return new DecisionRegistrada(d.evento(),d.version(),Decision.valueOf(d.decision()),d.registradaEn());}
    private static UUID uuid(String s){try{return UUID.fromString(s);}catch(RuntimeException ex){throw new AccesoNoAutorizadoException();}}
    private static void integridad(String datos,String huella){if(!Idempotencia.huellaExacta(datos).equals(huella))throw new IllegalStateException("La evidencia E44 no conserva su integridad.");}
    private static boolean texto(String s){return s!=null&&!s.isBlank();}
    private static boolean esHuella(String s){return s!=null&&s.matches("[0-9a-f]{64}");}
}
