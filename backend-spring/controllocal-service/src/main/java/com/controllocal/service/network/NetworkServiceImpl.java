package com.controllocal.service.network;

import com.controllocal.domain.auditoria.*;
import com.controllocal.domain.network.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.controllocal.domain.network.ParticipacionNetwork.*;

/** Participación aprobada 28/10. Un único gestor de decisiones; el proveedor sólo aporta hechos actuales. */
@Service
public class NetworkServiceImpl implements NetworkService {
    private final ParticipacionNetworkRepository participaciones;
    private final SolicitudParticipacionNetworkRepository solicitudes;
    private final DecisionParticipacionNetworkRepository decisiones;
    private final VersionParticipacionNetworkRepository versiones;
    private final FuenteDeNetwork fuente;
    private final ContextosService contextos;
    private final CuentaAccesoRepository cuentas;
    private final UsuarioOrganizacionRepository miembros;
    private final ComandoIdempotenteRepository registros;
    private final ComandosIdempotentes comandos;
    private final Documentos json;
    private final EventoDominioRepository eventos;
    private final Transiciones transiciones;
    private final EntityManager entidades;
    private final Clock reloj;

    @Autowired
    public NetworkServiceImpl(ParticipacionNetworkRepository participaciones,
            SolicitudParticipacionNetworkRepository solicitudes,DecisionParticipacionNetworkRepository decisiones,
            VersionParticipacionNetworkRepository versiones,FuenteDeNetwork fuente,ContextosService contextos,
            CuentaAccesoRepository cuentas,UsuarioOrganizacionRepository miembros,ComandoIdempotenteRepository registros,
            ComandosIdempotentes comandos,Documentos json,EventoDominioRepository eventos,
            Transiciones transiciones,EntityManager entidades) {
        this(participaciones,solicitudes,decisiones,versiones,fuente,contextos,cuentas,miembros,registros,
                comandos,json,eventos,transiciones,entidades,Clock.systemUTC());
    }
    NetworkServiceImpl(ParticipacionNetworkRepository participaciones,
            SolicitudParticipacionNetworkRepository solicitudes,DecisionParticipacionNetworkRepository decisiones,
            VersionParticipacionNetworkRepository versiones,FuenteDeNetwork fuente,ContextosService contextos,
            CuentaAccesoRepository cuentas,UsuarioOrganizacionRepository miembros,ComandoIdempotenteRepository registros,
            ComandosIdempotentes comandos,Documentos json,EventoDominioRepository eventos,
            Transiciones transiciones,EntityManager entidades,Clock reloj) {
        this.participaciones=participaciones;this.solicitudes=solicitudes;this.decisiones=decisiones;this.versiones=versiones;
        this.fuente=fuente;this.contextos=contextos;this.cuentas=cuentas;this.miembros=miembros;this.registros=registros;
        this.comandos=comandos;this.json=json;this.eventos=eventos;this.transiciones=transiciones;this.entidades=entidades;this.reloj=reloj;
    }

    @Override @Transactional
    public Vista consultar(long encargo,Actor actor) {
        var a=autoridad(encargo,actor);var p=leerParticipacion(encargo,actor);
        var m=metadata(p);
        if(m.isPresent() && (ACTIVO.equals(p.estadoActual()) || SUSPENDIDO.equals(p.estadoActual()))) {
            referenciasPermitidas(List.of(m.get().getOfertaRef()),actor);
            entidades.refresh(p);m=metadata(p);
        }
        return vista(p,a,m.orElse(null));
    }
    @Override @Transactional(readOnly=true)
    public Capacidad capacidadSobre(long encargo,Actor actor) {
        var a=autoridad(encargo,actor);var p=leerParticipacion(encargo,actor);
        var pendiente=p==null || p.getSolicitudPendiente()==null?null:
                solicitudes.contextoId(actor.idOrganizacion(),p.getSolicitudPendiente()).orElseThrow(()->new IllegalStateException("Solicitud pendiente inexistente."));
        return capacidad(p,a,pendiente,actor);
    }
    @Override @Transactional(readOnly=true)
    public Capacidad capacidadSobreSolicitud(String ref,Actor actor) {
        var s=contextoSolicitud(ref,actor);var a=autoridad(s.getEncargo(),actor);
        var p=participaciones.findByOrganizacionIdAndId(actor.idOrganizacion(),s.getParticipacion())
                .orElseThrow(()->new NoEncontradoException("Participación Network"));
        entidades.refresh(p);return capacidad(p,a,s,actor);
    }
    private Capacidad capacidad(ParticipacionNetwork p,FuenteDeNetwork.AutoridadActual a,
            SolicitudParticipacionNetworkRepository.ContextoSolicitud s,Actor actor) {
        Set<Cambio> cambios=EnumSet.noneOf(Cambio.class);
        String base=p==null?NO_HABILITADO:p.estadoActual();
        if(actor.esAgente() && actor.idRolOperativo()==a.responsable() && (p==null || p.getSolicitudPendiente()==null)) {
            if(a.elegible()) {
                if(NO_HABILITADO.equals(base))cambios.add(Cambio.ACTIVAR);
                if(INACTIVO.equals(base))cambios.add(Cambio.REACTIVAR);
                if(p!=null && p.getVersionActual()!=null && (ACTIVO.equals(base)||SUSPENDIDO.equals(base)))cambios.add(Cambio.MODIFICAR);
            }
            if(p!=null && p.getVersionActual()!=null && !INACTIVO.equals(base))cambios.add(Cambio.DESACTIVAR);
        }
        Set<String> causas=new TreeSet<>(a.causas());if(p!=null)causas.addAll(json.comoLista(p.getCausas()));
        boolean decide=puedeDecidir(a,actor);
        String ref=s==null?null:solicitudes.referenciaDe(a.organizacion(),s.getId()).orElseThrow(()->new IllegalStateException("Solicitud sin referencia.")).toString();
        String estado=s!=null && SolicitudParticipacionNetwork.PENDIENTE.equals(s.getEstado())
                && (!INACTIVO.equals(base)||Cambio.REACTIVAR.name().equals(s.getAccion()))?nombrePendiente(s.getAccion()):nombreBase(base);
        return new Capacidad(a.idEncargo(),p==null?0:p.getVersion(),estado,ref,s==null?null:s.getVersion(),
                s==null?null:nombreSolicitud(s.getEstado()),a.elegible(),Set.copyOf(causas),true,!cambios.isEmpty(),
                decide && s!=null && SolicitudParticipacionNetwork.PENDIENTE.equals(s.getEstado()) && p!=null && Objects.equals(p.getSolicitudPendiente(),s.getId()),
                puedeDesactivarDirectamente(a,actor) && p!=null && p.getVersionActual()!=null && !INACTIVO.equals(base),
                Set.copyOf(cambios),actor.esAgente() && actor.idRolOperativo()==a.responsable(),
                decide,puedeDesactivarDirectamente(a,actor));
    }

    @Override @Transactional(readOnly=true)
    public Preparacion previsualizar(long encargo,Set<String> campos,Actor actor) {
        var a=autoridad(encargo,actor);
        var f=fuente.consultarPropia(encargo,camposValidos(campos),FuenteDeNetwork.Accion.CONSULTAR,actor);
        coherencia(f,encargo,actor);var p=leerParticipacion(encargo,actor);
        var anterior=ultima(p);Map<String,Object> datos=anterior==null?Map.of():json.comoMapa(anterior.contenido().datos());
        return new Preparacion(vista(p,a,metadata(p).orElse(null)),f,datos,diferencias(datos,f.datosPermitidos()));
    }
    @Override @Transactional(readOnly=true)
    public SolicitudVista consultarSolicitud(String referencia,Actor actor) {
        var c=contextoSolicitud(referencia,actor);autoridad(c.getEncargo(),actor);
        var s=solicitudes.findByOrganizacionIdAndReferencia(actor.idOrganizacion(),uuid(referencia))
                .orElseThrow(()->new NoEncontradoException("Solicitud Network"));
        entidades.refresh(s);return solicitudVista(s);
    }
    @Override @Transactional(readOnly=true)
    public DatosSolicitud validarSolicitud(DatosSolicitud datos,Actor actor) {
        var d=sintaxis(datos);var a=autoridad(d.idEncargo(),actor);
        if(!actor.esAgente() || actor.idRolOperativo()!=a.responsable())throw new AccesoNoAutorizadoException();
        var p=leerParticipacion(d.idEncargo(),actor);version(d.versionParticipacion(),p==null?0:p.getVersion());
        if(p!=null && p.getSolicitudPendiente()!=null)throw new ConflictoException("Ya existe una solicitud pendiente del Encargo; consulta su estado.");
        validarCambio(d.accion(),p);
        if(d.accion()==Cambio.DESACTIVAR) {
            var c=exigirUltima(p).contenido();
            if(d.huellaDatos()!=null && !d.huellaDatos().equals(c.huellaDatos())
                    || d.huellaAutoridad()!=null && !d.huellaAutoridad().equals(c.huellaAutoridad()))
                throw new ConflictoException("La versión que se quiere retirar cambió.");
            return new DatosSolicitud(d.idEncargo(),d.accion(),campos(c.campos()),c.finalidad(),c.desde(),c.hasta(),
                    d.motivo(),d.versionParticipacion(),c.huellaDatos(),c.huellaAutoridad());
        }
        exigirElegible(a);
        var f=fuente.consultarPropia(d.idEncargo(),camposValidos(d.campos()),FuenteDeNetwork.Accion.SOLICITAR,actor);
        coherencia(f,d.idEncargo(),actor);comprobarContenido(d,f);
        return d;
    }
    @Override @Transactional(readOnly=true)
    public DatosDecision validarDecision(DatosDecision datos,Actor actor) {
        var d=sintaxis(datos);var c=contextoSolicitud(d.solicitud(),actor);var a=autoridad(c.getEncargo(),actor);
        exigirDecisor(a,actor);
        var p=participaciones.findByOrganizacionIdAndId(actor.idOrganizacion(),c.getParticipacion())
                .orElseThrow(()->new NoEncontradoException("Participación Network"));
        entidades.refresh(p);
        var s=solicitudes.findByOrganizacionIdAndReferencia(actor.idOrganizacion(),uuid(d.solicitud()))
                .orElseThrow(()->new NoEncontradoException("Solicitud Network"));
        entidades.refresh(s);comprobarDecision(p,s,d,actor);return d;
    }
    @Override @Transactional(readOnly=true)
    public DatosDesactivacion validarDesactivacionDirecta(DatosDesactivacion datos,Actor actor) {
        var d=sintaxis(datos);var a=autoridad(d.idEncargo(),actor);exigirDesactivadorDirecto(a,actor);
        var p=leerParticipacion(d.idEncargo(),actor);
        if(p==null || p.getVersionActual()==null || INACTIVO.equals(p.estadoActual()))
            throw new ReglaNegocioException("No hay participación que desactivar.");
        version(d.versionParticipacion(),p.getVersion());return d;
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Resultado solicitar(DatosSolicitud datos,Actor actor,ContextoSesion sesion,String clave,Procedencia origen) {
        var d=sintaxis(datos);var pr=Procedencia.oPantalla(origen);String k=Idempotencia.exigirClave(clave);
        iniciar(actor,sesion);String h=huella(d,pr);bloquearClave(actor,k);
        var previo=previo(actor,k,"SOLICITAR_PARTICIPACION_NETWORK",h);
        if(previo.isPresent())return restaurar(previo.get().getResultado(),actor);
        var exactos=validarSolicitud(d,actor);
        if(!d.equals(exactos))throw new ConflictoException("Revisa la propuesta completa de la versión que se quiere retirar.");
        var a=autoridad(d.idEncargo(),actor);bloquearRecurso(actor,d.idEncargo());
        var p=participaciones.bloquearEncargo(actor.idOrganizacion(),d.idEncargo()).orElse(null);
        if(p==null) {p=ParticipacionNetwork.nueva(actor.idOrganizacion(),d.idEncargo(),reloj.instant());transiciones.iniciar(p,NO_HABILITADO);participaciones.saveAndFlush(p);}
        else entidades.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        version(d.versionParticipacion(),p.getVersion());
        if(p.getSolicitudPendiente()!=null)throw new ConflictoException("Otra solicitud del Encargo quedó pendiente.");
        validarCambio(d.accion(),p);
        ContenidoNetwork contenido;
        if(d.accion()==Cambio.DESACTIVAR)contenido=exigirUltima(p).contenido();
        else {
            var f=fuente.consultarPropia(d.idEncargo(),d.campos(),FuenteDeNetwork.Accion.SOLICITAR,actor);
            coherencia(f,d.idEncargo(),actor);comprobarContenido(d,f);contenido=contenido(f,d);
        }
        var ultima=ultima(p);var dif=diferencias(ultima==null?Map.of():json.comoMapa(ultima.contenido().datos()),json.comoMapa(contenido.datos()));
        var s=SolicitudParticipacionNetwork.nueva(p,actor.idRolOperativo(),d.accion().name(),contenido,json.lista(dif),d.motivo(),reloj.instant());
        transiciones.iniciar(s,SolicitudParticipacionNetwork.PENDIENTE);solicitudes.saveAndFlush(s);p.solicitar(s.getId());participaciones.saveAndFlush(p);
        if(a.individual())aplicar(p,s,Decision.APROBAR,d.motivo(),actor);
        return registrar("SOLICITAR_PARTICIPACION_NETWORK","NETWORK_SOLICITADA",p,s,actor,k,h,pr);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Resultado decidir(DatosDecision datos,Actor actor,ContextoSesion sesion,String clave,Procedencia origen) {
        var d=sintaxis(datos);var pr=Procedencia.oPantalla(origen);String k=Idempotencia.exigirClave(clave);
        iniciar(actor,sesion);String h=huella(d,pr);bloquearClave(actor,k);
        var previo=previo(actor,k,"DECIDIR_PARTICIPACION_NETWORK",h);
        if(previo.isPresent())return restaurar(previo.get().getResultado(),actor);
        var c=contextoSolicitud(d.solicitud(),actor);var a=autoridad(c.getEncargo(),actor);exigirDecisor(a,actor);
        var p=participaciones.bloquear(actor.idOrganizacion(),c.getParticipacion()).orElseThrow(()->new NoEncontradoException("Participación Network"));
        entidades.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        var s=solicitudes.bloquear(actor.idOrganizacion(),uuid(d.solicitud())).orElseThrow(()->new NoEncontradoException("Solicitud Network"));
        entidades.refresh(s,LockModeType.PESSIMISTIC_WRITE);comprobarDecision(p,s,d,actor);
        aplicar(p,s,d.decision(),d.motivo(),actor);
        return registrar("DECIDIR_PARTICIPACION_NETWORK","NETWORK_DECIDIDA",p,s,actor,k,h,pr);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Resultado desactivarDirectamente(DatosDesactivacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia origen) {
        var d=sintaxis(datos);var pr=Procedencia.oPantalla(origen);String k=Idempotencia.exigirClave(clave);
        iniciar(actor,sesion);String h=huella(d,pr);bloquearClave(actor,k);
        var previo=previo(actor,k,"DESACTIVAR_PARTICIPACION_NETWORK",h);
        if(previo.isPresent())return restaurar(previo.get().getResultado(),actor);
        var a=autoridad(d.idEncargo(),actor);exigirDesactivadorDirecto(a,actor);
        var p=participaciones.bloquearEncargo(actor.idOrganizacion(),d.idEncargo()).orElseThrow(()->new NoEncontradoException("Participación Network"));
        entidades.refresh(p,LockModeType.PESSIMISTIC_WRITE);version(d.versionParticipacion(),p.getVersion());
        if(p.getVersionActual()==null || INACTIVO.equals(p.estadoActual()))throw new ReglaNegocioException("No hay participación que desactivar.");
        var contenido=exigirUltima(p).contenido();
        var s=SolicitudParticipacionNetwork.nueva(p,actor.idRolOperativo(),Cambio.DESACTIVAR.name(),contenido,"[]",d.motivo(),reloj.instant());
        transiciones.iniciar(s,SolicitudParticipacionNetwork.PENDIENTE);solicitudes.saveAndFlush(s);
        // No inventa solicitud de agente ni decide otra propuesta pendiente con contenido distinto.
        aplicar(p,s,Decision.APROBAR,d.motivo(),actor);
        return registrar("DESACTIVAR_PARTICIPACION_NETWORK","NETWORK_DESACTIVADA",p,s,actor,k,h,pr);
    }

    private void comprobarDecision(ParticipacionNetwork p,SolicitudParticipacionNetwork s,DatosDecision d,Actor actor) {
        version(d.versionParticipacion(),p.getVersion());version(d.versionSolicitud(),s.getVersion());
        if(!SolicitudParticipacionNetwork.PENDIENTE.equals(s.estadoActual()))throw new ConflictoException("La solicitud ya tiene una decisión.");
        if(!Objects.equals(p.getSolicitudPendiente(),s.getId()))throw new ConflictoException("La solicitud ya no es la pendiente del Encargo.");
        if(!Objects.equals(d.huellaDatos(),s.contenido().huellaDatos()) || !Objects.equals(d.huellaAutoridad(),s.contenido().huellaAutoridad()))
            throw new ConflictoException("El contenido revisado no corresponde a la solicitud.");
        if(d.decision()==Decision.APROBAR)comprobarAprobacion(p,s,actor);
    }
    private void comprobarAprobacion(ParticipacionNetwork p,SolicitudParticipacionNetwork s,Actor actor) {
        var a=autoridad(p.getEncargo(),actor);exigirDecisor(a,actor);
        validarCambio(Cambio.valueOf(s.getAccion()),p);
        if(Cambio.DESACTIVAR.name().equals(s.getAccion()))return;
        exigirElegible(a);var c=s.contenido();
        var actual=fuente.consultarPropia(p.getEncargo(),campos(c.campos()),FuenteDeNetwork.Accion.DECIDIR,actor);
        coherencia(actual,p.getEncargo(),actor);
        if(!actual.elegible() || !Objects.equals(c.huellaDatos(),actual.huellaRelevante())
                || !Objects.equals(c.huellaAutoridad(),actual.huellaAutoridad())
                || !Objects.equals(c.politicaVersion(),actual.politicaVersion())
                || !equivalentes(json.comoMapa(c.datos()),actual.datosPermitidos())
                || c.responsable()!=actual.responsable() || !Objects.equals(c.broker(),actual.brokerActual()))
            throw new ConflictoException("Cambió el contenido o la autorización de la propuesta; requiere revisión de una versión actual.");
    }
    private void aplicar(ParticipacionNetwork p,SolicitudParticipacionNetwork s,Decision decision,String motivo,Actor actor) {
        if(decision==Decision.APROBAR)comprobarAprobacion(p,s,actor);
        var ahora=reloj.instant();var d=DecisionParticipacionNetwork.nueva(s,actor.idRolOperativo(),decision.name(),motivo,ahora);
        decisiones.saveAndFlush(d);s.decidir(d.getId());
        String estado=switch(decision){case APROBAR->SolicitudParticipacionNetwork.APROBADA;case OBSERVAR->SolicitudParticipacionNetwork.OBSERVADA;case RECHAZAR->SolicitudParticipacionNetwork.RECHAZADA;};
        transiciones.aplicar(s,s.getId(),estado,actor,"Decisión Network "+d.getId(),fecha(ahora));solicitudes.saveAndFlush(s);
        p.resolver(s.getId());
        if(decision==Decision.APROBAR) {
            if(Cambio.DESACTIVAR.name().equals(s.getAccion())) {
                transiciones.aplicar(p,p.getId(),INACTIVO,actor,"Desactivación aprobada; decisión "+d.getId(),fecha(ahora));p.limpiarSuspension();
            } else {
                var v=VersionParticipacionNetwork.aprobada(p,s,d.getId(),ahora);versiones.saveAndFlush(v);
                p.aprobarVersion(v.getId(),v.getNumero());transiciones.aplicar(p,p.getId(),ACTIVO,actor,"Versión Network aprobada "+v.getNumero(),fecha(ahora));
            }
        }
        participaciones.saveAndFlush(p);
    }

    @Override @Transactional(readOnly=true)
    public Optional<Resultado> consultarResultado(String capacidad,String clave,Actor actor) {
        Alcances.exigirOperador(actor);
        String tipo=switch(capacidad==null?"":capacidad) {
            case "solicitar_participacion_network"->"SOLICITAR_PARTICIPACION_NETWORK";
            case "decidir_participacion_network"->"DECIDIR_PARTICIPACION_NETWORK";
            case "desactivar_participacion_network"->"DESACTIVAR_PARTICIPACION_NETWORK";
            default->throw new ReglaNegocioException("La capacidad no produce un resultado Network.");
        };
        return comandos.consultarResultado(actor,clave,tipo).map(c->restaurar(c.getResultado(),actor));
    }
    @Override @Transactional
    public List<FuenteDeNetwork.ReferenciaAutorizada> referenciasParaConsulta(Actor actor) {
        Alcances.exigirOperador(actor);return comprobarCandidatas(versiones.candidatasActuales(),actor,null);
    }
    @Override @Transactional
    public Optional<FuenteDeNetwork.ReferenciaAutorizada> resolverReferencia(String referencia,Actor actor) {
        Alcances.exigirOperador(actor);return referenciasPermitidas(List.of(uuid(referencia)),actor).stream().findFirst();
    }
    @Override @Transactional
    public List<VersionParaEntrega> revalidarParaEntrega(Collection<String> referencias,Actor actor) {
        Alcances.exigirOperador(actor);if(referencias==null)throw new ReglaNegocioException("Indica referencias Network.");
        List<UUID> refs=referencias.stream().map(NetworkServiceImpl::uuid).distinct().sorted().toList();
        var permitidas=referenciasPermitidas(refs,actor);if(permitidas.isEmpty())return List.of();
        var porRef=new HashMap<UUID,FuenteDeNetwork.ReferenciaAutorizada>();
        permitidas.forEach(r->porRef.put(uuid(r.ofertaRef()),r));
        var refsVersion=permitidas.stream().map(r->uuid(r.referenciaVersion())).toList();
        var filas=versiones.salidasActuales(refsVersion);
        // Segunda verificación después de obtener los únicos datos ya autorizados.
        var actuales=fuente.verificarActuales(permitidas,actor);
        var salida=new ArrayList<VersionParaEntrega>();
        for(var fila:filas.stream().sorted(Comparator.comparing(VersionParticipacionNetworkRepository.MetadataVersion::getParticipacion)).toList()) {
            var ver=verificacion(fila,actuales);var causas=causas(fila,ver);
            if(!causas.isEmpty()) {reconciliar(fila,ver,causas,null);continue;}
            if(!enIntervalo(fila))continue;
            var r=porRef.get(fila.getOfertaRef());
            if(r!=null)salida.add(new VersionParaEntrega(r,json.comoMapa(fila.getDatos()),fila.getDesde(),fila.getHasta(),ver.disponibilidad()));
        }
        Set<UUID> activas=versiones.referenciasActivas(refsVersion);
        return salida.stream().filter(s->activas.contains(uuid(s.referencia().referenciaVersion()))).toList();
    }
    private List<FuenteDeNetwork.ReferenciaAutorizada> referenciasPermitidas(Collection<UUID> refs,Actor actor) {
        if(refs.isEmpty())return List.of();return comprobarCandidatas(versiones.candidatasPorReferencia(refs),actor,null);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void reconciliarCambioCore(OrigenCambio origen,long idRecurso,Actor actor,EventoDominio eventoCausal) {
        boolean reasignacionDeGobierno=actor!=null && actor.esTenantAdmin() && origen==OrigenCambio.ENCARGO
                && eventoCausal!=null && "ENCARGO_REASIGNADO".equals(eventoCausal.getTipo());
        if(!reasignacionDeGobierno)Alcances.exigirOperador(actor);
        String tipo=origen==null?null:switch(origen){case ENCARGO->"CAPTACION";case PROPIEDAD->"PROPIEDAD";};
        if(tipo==null || idRecurso<=0 || eventoCausal==null || eventoCausal.getId()==null || eventoCausal.getId()<=0
                || eventoCausal.getOrganizacionId()!=actor.idOrganizacion()
                || !Objects.equals(eventoCausal.getIdPersonaRol(),actor.idRolOperativo())
                || !tipo.equals(eventoCausal.getEntidadTipo()) || !Objects.equals(eventoCausal.getEntidadId(),idRecurso))
            throw new IllegalStateException("El hecho causal persistido no corresponde al actor, organización y recurso del cambio Core.");
        var filas=switch(origen) {
            case ENCARGO->versiones.candidatasDelEncargo(actor.idOrganizacion(),idRecurso);
            case PROPIEDAD->versiones.candidatasDePropiedad(actor.idOrganizacion(),idRecurso);
        };
        comprobarCandidatas(filas,actor,eventoCausal);
    }
    private List<FuenteDeNetwork.ReferenciaAutorizada> comprobarCandidatas(List<VersionParticipacionNetworkRepository.EstadoVersion> filas,Actor actor,EventoDominio eventoCausal) {
        if(filas.isEmpty())return List.of();
        var refs=filas.stream().map(this::referencia).toList();
        var verificadas=eventoCausal==null?fuente.verificarActuales(refs,actor):fuente.verificarCambioCore(refs,actor,eventoCausal);
        var resultado=new ArrayList<FuenteDeNetwork.ReferenciaAutorizada>();
        for(var fila:filas.stream().sorted(Comparator.comparing(VersionParticipacionNetworkRepository.MetadataVersion::getParticipacion)).toList()) {
            var v=verificacion(fila,verificadas);var causas=causas(fila,v);
            boolean activa=ACTIVO.equals(fila.getEstadoParticipacion());
            if(!causas.isEmpty() || SUSPENDIDO.equals(fila.getEstadoParticipacion()))activa=reconciliar(fila,v,causas,eventoCausal);
            if(activa && causas.isEmpty() && enIntervalo(fila))resultado.add(referencia(fila));
        }
        return resultado;
    }
    private Set<String> causas(VersionParticipacionNetworkRepository.MetadataVersion v,FuenteDeNetwork.VerificacionActual f) {
        Set<String> c=new TreeSet<>();
        if(!f.elegible()) {
            if(f.causas().isEmpty())throw new IllegalStateException("La fuente no indicó la causa de inelegibilidad.");
            c.addAll(f.causas());
            if(!reloj.instant().isBefore(v.getHasta()))c.add("VIGENCIA_VENCIDA");
            // Una huella ausente por falta de legitimación no afirma que cambiaron los datos.
            return c;
        }
        if(f.huellaRelevante()==null || f.huellaAutoridad()==null)throw new IllegalStateException("La fuente elegible no tiene huellas verificables.");
        if(f.disponibilidad()==null)throw new IllegalStateException("La fuente elegible no acreditó disponibilidad D2.");
        if(!Objects.equals(v.getHuellaDatos(),f.huellaRelevante()))c.add("CAMBIO_DATOS");
        if(!Objects.equals(v.getHuellaAutoridad(),f.huellaAutoridad()) || v.getResponsable()!=f.responsable()
                || !Objects.equals(v.getBroker(),f.brokerActual()) || !Objects.equals(v.getPoliticaVersion(),f.politicaVersion())
                || !Objects.equals(v.getFinalidad(),f.finalidad()))c.add("CAMBIO_AUTORIDAD");
        if(!reloj.instant().isBefore(v.getHasta()))c.add("VIGENCIA_VENCIDA");
        return c;
    }
    private boolean reconciliar(VersionParticipacionNetworkRepository.EstadoVersion fila,
            FuenteDeNetwork.VerificacionActual actual,Set<String> causasActuales,EventoDominio eventoCausal) {
        var p=participaciones.bloquear(fila.getOrganizacion(),fila.getParticipacion())
                .orElseThrow(()->new IllegalStateException("La participación de la versión desapareció."));
        entidades.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        if(!Objects.equals(p.getVersionActual(),fila.getId()) || INACTIVO.equals(p.estadoActual()))return false;
        Set<String> anteriores=new TreeSet<>(json.comoLista(p.getCausas()));
        Set<String> causasConservadas=new TreeSet<>(causasActuales);
        if(SUSPENDIDO.equals(p.estadoActual())) {
            // Recuperar frescura no concede la revisión pendiente de otra causa de seguridad.
            anteriores.stream().filter(c->!FuenteDeNetwork.FRESCURA.equals(c)).forEach(causasConservadas::add);
        }
        if(causasConservadas.isEmpty()) {
            if(SUSPENDIDO.equals(p.estadoActual()) && anteriores.equals(Set.of(FuenteDeNetwork.FRESCURA))) {
                p.limpiarSuspension();transiciones.aplicar(p,p.getId(),ACTIVO,null,"Frescura recuperada con la misma autorización",fecha(reloj.instant()));
                participaciones.saveAndFlush(p);eventoSistema("NETWORK_FRESCURA_REVALIDADA",p,actual,Set.of(),eventoCausal);return true;
            }
            return ACTIVO.equals(p.estadoActual());
        }
        if(!SUSPENDIDO.equals(p.estadoActual()) || !anteriores.equals(causasConservadas)) {
            p.registrarSuspension(json.lista(new ArrayList<>(causasConservadas)),reloj.instant());
            transiciones.aplicar(p,p.getId(),SUSPENDIDO,null,"Suspensión preventiva Network; causas en su antecedente",fecha(reloj.instant()));
            participaciones.saveAndFlush(p);eventoSistema("NETWORK_SUSPENDIDA",p,actual,causasConservadas,eventoCausal);
        }
        return false;
    }

    private FuenteDeNetwork.AutoridadActual autoridad(long id,Actor actor) {
        Alcances.exigirOperador(actor);if(id<=0)throw new NoEncontradoException("Encargo");
        var a=fuente.consultarAutoridadPropia(id,actor);
        if(a.organizacion()!=actor.idOrganizacion() || a.idEncargo()!=id)throw new AccesoNoAutorizadoException();
        if(actor.esAgente() && actor.idRolOperativo()==a.responsable())return a;
        if(actor.esBroker() && !a.individual() && Objects.equals(a.brokerActual(),actor.idRolOperativo()))return a;
        throw new AccesoNoAutorizadoException();
    }
    private void exigirDecisor(FuenteDeNetwork.AutoridadActual a,Actor actor) {
        if(!puedeDecidir(a,actor))throw new AccesoNoAutorizadoException();
    }
    private boolean puedeDecidir(FuenteDeNetwork.AutoridadActual a,Actor actor) {
        return actor.esBroker() && !a.individual() && Objects.equals(a.brokerActual(),actor.idRolOperativo())
                || actor.esAgente() && a.individual() && actor.idRolOperativo()==a.responsable();
    }
    private boolean puedeDesactivarDirectamente(FuenteDeNetwork.AutoridadActual a,Actor actor) {
        return actor.esBroker() && !a.individual() && Objects.equals(a.brokerActual(),actor.idRolOperativo());
    }
    private void exigirDesactivadorDirecto(FuenteDeNetwork.AutoridadActual a,Actor actor) {
        if(!puedeDesactivarDirectamente(a,actor))throw new AccesoNoAutorizadoException();
    }
    private void exigirElegible(FuenteDeNetwork.AutoridadActual a) {
        if(!a.elegible())throw new ReglaNegocioException("El Encargo no es elegible para Network: "+String.join(", ",a.causas()));
    }
    private void coherencia(FuenteDeNetwork.FuentePropia f,long id,Actor actor) {
        if(f.organizacion()!=actor.idOrganizacion() || f.idEncargo()!=id)throw new AccesoNoAutorizadoException();
        if(!PoliticaProyeccionNetworkV1.VERSION.equals(f.politicaVersion())
                || !PoliticaProyeccionNetworkV1.FINALIDAD.equals(f.finalidad()))
            throw new IllegalStateException("La fuente no indicó la política/finalidad Network V1 vigente.");
    }
    private void comprobarContenido(DatosSolicitud d,FuenteDeNetwork.FuentePropia f) {
        if(!f.elegible())throw new ReglaNegocioException("La proyección no es elegible: "+String.join(", ",f.causas()));
        if(!Objects.equals(d.huellaDatos(),f.huellaRelevante()) || !Objects.equals(d.huellaAutoridad(),f.huellaAutoridad())
                || !Objects.equals(d.finalidad(),f.finalidad()))throw new ConflictoException("Los datos o su autorización cambiaron; revisa la propuesta actual.");
        if(!d.campos().containsAll(f.datosPermitidos().keySet()))throw new IllegalStateException("La fuente devolvió campos no seleccionados.");
    }
    private ContenidoNetwork contenido(FuenteDeNetwork.FuentePropia f,DatosSolicitud d) {
        return new ContenidoNetwork(f.responsable(),f.brokerActual(),f.individual(),f.versionEncargo(),f.versionFisica(),
                f.politicaVersion(),json.objetoCanonico(f.datosPermitidos()),json.lista(new ArrayList<>(new TreeSet<>(d.campos()))),
                d.finalidad(),d.desde(),d.hasta(),f.huellaRelevante(),f.huellaAutoridad());
    }
    private void validarCambio(Cambio cambio,ParticipacionNetwork p) {
        String estado=p==null?NO_HABILITADO:p.estadoActual();
        boolean valido=switch(cambio) {
            case ACTIVAR->NO_HABILITADO.equals(estado);
            case REACTIVAR->INACTIVO.equals(estado);
            case MODIFICAR->p!=null && p.getVersionActual()!=null && (ACTIVO.equals(estado)||SUSPENDIDO.equals(estado));
            case DESACTIVAR->p!=null && p.getVersionActual()!=null && !INACTIVO.equals(estado);
        };
        if(!valido)throw new ReglaNegocioException("La acción "+cambio+" no corresponde al estado actual; la reactivación requiere una nueva solicitud propia.");
    }
    private ParticipacionNetwork leerParticipacion(long encargo,Actor actor) {
        var p=participaciones.findByOrganizacionIdAndEncargo(actor.idOrganizacion(),encargo).orElse(null);
        if(p!=null)entidades.refresh(p);return p;
    }
    private Optional<VersionParticipacionNetworkRepository.MetadataVersion> metadata(ParticipacionNetwork p) {
        if(p==null || p.getVersionActual()==null)return Optional.empty();
        return Optional.of(versiones.metadata(p.getOrganizacionId(),p.getVersionActual()).orElseThrow(()->new IllegalStateException("La versión Network no pertenece a su participación.")));
    }
    private VersionParticipacionNetwork ultima(ParticipacionNetwork p) {
        if(p==null || p.getVersionActual()==null)return null;
        return versiones.findByOrganizacionIdAndId(p.getOrganizacionId(),p.getVersionActual())
                .orElseThrow(()->new IllegalStateException("La versión aprobada no existe."));
    }
    private VersionParticipacionNetwork exigirUltima(ParticipacionNetwork p) {
        var v=ultima(p);if(v==null)throw new ReglaNegocioException("El Encargo aún no tiene una versión Network aprobada.");return v;
    }
    private SolicitudParticipacionNetworkRepository.ContextoSolicitud contextoSolicitud(String ref,Actor actor) {
        Alcances.exigirOperador(actor);return solicitudes.contexto(actor.idOrganizacion(),uuid(ref)).orElseThrow(()->new NoEncontradoException("Solicitud Network"));
    }
    private Vista vista(ParticipacionNetwork p,FuenteDeNetwork.AutoridadActual a,VersionParticipacionNetworkRepository.MetadataVersion v) {
        String base=p==null?NO_HABILITADO:p.estadoActual();String estado=nombreBase(base);String pendiente=null;
        Set<String> causas=new TreeSet<>();if(p!=null)causas.addAll(json.comoLista(p.getCausas()));causas.addAll(a.causas());
        if(p!=null && p.getSolicitudPendiente()!=null) {
            var s=solicitudes.contextoId(a.organizacion(),p.getSolicitudPendiente()).orElseThrow(()->new IllegalStateException("Solicitud pendiente no vinculada."));
            pendiente=solicitudes.referenciaDe(a.organizacion(),s.getId()).orElseThrow(()->new IllegalStateException("Solicitud sin referencia.")).toString();
            if(!INACTIVO.equals(base) || Cambio.REACTIVAR.name().equals(s.getAccion()))estado=nombrePendiente(s.getAccion());
        }
        return new Vista(a.idEncargo(),p==null?0:p.getVersion(),base,estado,pendiente,
                v==null?null:v.getReferencia().toString(),v==null?null:v.getNumero(),Set.copyOf(causas),a.elegible());
    }
    private SolicitudVista solicitudVista(SolicitudParticipacionNetwork s) {
        var c=s.contenido();
        return new SolicitudVista(s.getReferencia().toString(),s.getVersion(),
                participaciones.findByOrganizacionIdAndId(s.getOrganizacionId(),s.getParticipacion()).orElseThrow(()->new IllegalStateException("Participación no vinculada.")).getEncargo(),
                s.getAccion(),s.estadoNombre(),s.getVersionParticipacion(),campos(c.campos()),c.finalidad(),c.desde(),c.hasta(),
                json.comoMapa(c.datos()),diferenciasDe(s.getDiferencias()),s.getMotivo(),c.huellaDatos(),c.huellaAutoridad());
    }
    private List<Diferencia> diferenciasDe(String valor) {
        return Arrays.asList(json.comoObjeto(valor,Diferencia[].class));
    }
    private static List<Diferencia> diferencias(Map<String,Object> antes,Map<String,Object> despues) {
        Set<String> claves=new TreeSet<>(antes.keySet());claves.addAll(despues.keySet());var salida=new ArrayList<Diferencia>();
        for(String k:claves) {
            if(antes.containsKey(k) && despues.containsKey(k) && equivalentes(antes.get(k),despues.get(k)))continue;
            salida.add(new Diferencia(k,!antes.containsKey(k)?"AGREGADO":!despues.containsKey(k)?"RETIRADO":"MODIFICADO",antes.get(k),despues.get(k)));
        }
        return List.copyOf(salida);
    }
    private static boolean equivalentes(Object a,Object b) {
        if(a instanceof Number x && b instanceof Number y)
            return new java.math.BigDecimal(x.toString()).compareTo(new java.math.BigDecimal(y.toString()))==0;
        if(a instanceof Map<?,?> x && b instanceof Map<?,?> y)
            return x.keySet().equals(y.keySet()) && x.entrySet().stream().allMatch(e->equivalentes(e.getValue(),y.get(e.getKey())));
        if(a instanceof List<?> x && b instanceof List<?> y) {
            if(x.size()!=y.size())return false;
            for(int i=0;i<x.size();i++)if(!equivalentes(x.get(i),y.get(i)))return false;
            return true;
        }
        return Objects.equals(a,b);
    }
    private FuenteDeNetwork.ReferenciaAutorizada referencia(VersionParticipacionNetworkRepository.MetadataVersion v) {
        return new FuenteDeNetwork.ReferenciaAutorizada(v.getOrganizacion(),v.getEncargo(),v.getOfertaRef().toString(),v.getReferencia().toString(),v.getNumero(),
                v.getResponsable(),v.getBroker(),v.getPoliticaVersion(),v.getFinalidad(),campos(v.getCampos()),v.getHuellaDatos(),v.getHuellaAutoridad());
    }
    private FuenteDeNetwork.VerificacionActual verificacion(VersionParticipacionNetworkRepository.MetadataVersion v,
            Map<Long,FuenteDeNetwork.VerificacionActual> verificadas) {
        if(verificadas==null)throw new IllegalStateException("La fuente no devolvió la verificación.");
        var f=verificadas.get(v.getEncargo());
        if(f==null || f.idEncargo()!=v.getEncargo() || f.organizacion()!=v.getOrganizacion())
            throw new IllegalStateException("La verificación no corresponde a la versión autorizada.");
        return f;
    }
    private boolean enIntervalo(VersionParticipacionNetworkRepository.MetadataVersion v) {
        return !reloj.instant().isBefore(v.getDesde()) && reloj.instant().isBefore(v.getHasta());
    }
    private void iniciar(Actor actor,ContextoSesion sesion) {
        Alcances.exigirOperador(actor);if(sesion==null)throw new AccesoNoAutorizadoException();
        var ids=miembros.cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona());
        if(ids.size()!=1)throw new AccesoNoAutorizadoException();
        cuentas.bloquearCambioAcceso(ids.getFirst()).orElseThrow(AccesoNoAutorizadoException::new);
        try{if(!ContextoSesion.de(contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor)).equals(sesion))throw new AccesoNoAutorizadoException();}
        catch(CredencialesInvalidasException fallo){throw new AccesoNoAutorizadoException();}
    }
    private void bloquearClave(Actor actor,String clave) {
        var h=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+actor.idOrganizacion()+"\u0000"+clave);
        registros.bloquearIdentidad(Long.parseUnsignedLong(h.substring(0,16),16));
    }
    private void bloquearRecurso(Actor actor,long encargo) {
        var h=Idempotencia.huellaExacta("BROX-PARTICIPACION-NETWORK\u0000"+actor.idOrganizacion()+"\u0000"+encargo);
        registros.bloquearIdentidad(Long.parseUnsignedLong(h.substring(0,16),16));
    }
    private Optional<ComandoIdempotente> previo(Actor actor,String clave,String tipo,String huella) {
        try{return comandos.buscar(actor,clave,tipo,huella);}
        catch(ReglaNegocioException conflictoDeContenido){throw new ConflictoException("La clave corresponde a otro contenido o actuación.");}
    }
    private Resultado restaurar(String valor,Actor actor) {
        var r=json.comoObjeto(valor,Resultado.class);autoridad(r.idEncargo(),actor);return r;
    }
    private Resultado registrar(String comando,String tipo,ParticipacionNetwork p,SolicitudParticipacionNetwork s,
            Actor actor,String clave,String huella,Procedencia procedencia) {
        var v=metadata(p);var r=new Resultado(p.getEncargo(),p.getVersion(),s.getReferencia().toString(),s.getVersion(),
                s.estadoNombre(),nombreBase(p.estadoActual()),v.map(x->x.getReferencia().toString()).orElse(null),
                v.map(VersionParticipacionNetworkRepository.MetadataVersion::getNumero).orElse(null),s.getAccion(),reloj.instant());
        Map<String,Object> carga=new LinkedHashMap<>();carga.put("encargo",p.getEncargo());carga.put("solicitud",s.getReferencia());
        carga.put("decision",s.getUltimaDecision());carga.put("accion",s.getAccion());carga.put("estadoSolicitud",s.estadoNombre());
        carga.put("estadoParticipacion",p.estadoActual());carga.put("versionParticipacion",p.getVersion());
        carga.put("versionProyeccion",p.getNumeroVersion());carga.put("responsable",s.contenido().responsable());
        carga.put("broker",s.contenido().broker());carga.put("ocurridoEn",reloj.instant());
        eventos.saveAndFlush(procedencia.sellar(EventoDominio.de(p.getOrganizacionId(),tipo,ParticipacionNetwork.TIPO,p.getId(),actor.idRolOperativo(),procedencia.canal())).con(json.objetoCanonico(carga)));
        String serializado=json.objetoCanonico(r);comandos.registrar(actor,clave,comando,huella,ParticipacionNetwork.TIPO,p.getId(),procedencia,serializado);
        return json.comoObjeto(serializado,Resultado.class);
    }
    private void eventoSistema(String tipo,ParticipacionNetwork p,FuenteDeNetwork.VerificacionActual a,Set<String> causas,EventoDominio eventoCausal) {
        Map<String,Object> carga=new LinkedHashMap<>();carga.put("encargo",p.getEncargo());carga.put("version",p.getNumeroVersion());
        carga.put("causas",new TreeSet<>(causas));carga.put("responsable",a.responsable());carga.put("broker",a.brokerActual());
        if(eventoCausal!=null)carga.put("idEventoCausal",eventoCausal.getId());
        eventos.saveAndFlush(Procedencia.delSistema().sellar(EventoDominio.de(p.getOrganizacionId(),tipo,ParticipacionNetwork.TIPO,p.getId(),null,EventoDominio.CANAL_SISTEMA)).con(json.objetoCanonico(carga)));
    }
    private String huella(Object d,Procedencia p){return Idempotencia.huellaExacta(json.objetoCanonico(Map.of("datos",d,"procedencia",p)));}
    private Set<String> campos(String lista){return Collections.unmodifiableSet(new TreeSet<>(json.comoLista(lista)));}
    private static Set<String> camposValidos(Set<String> campos){PoliticaProyeccionNetworkV1.exigirCampos(campos);return Collections.unmodifiableSet(new TreeSet<>(campos));}
    private static void version(Long esperada,long actual){requiereVersion(esperada);if(esperada!=actual)throw new ConflictoException("Cambió la versión; revisa el contexto actual.");}
    private static void requiereVersion(Long v){if(v==null || v<0)throw new ReglaNegocioException("Indica la versión observada; no puede omitirse.");}
    private static UUID uuid(String valor){try{return UUID.fromString(valor);}catch(RuntimeException e){throw new NoEncontradoException("Referencia Network");}}
    private static String texto(String s,String campo){if(s==null || s.isBlank())throw new ReglaNegocioException("Indica "+campo+".");return s.trim();}
    private static LocalDate fecha(Instant i){return i.atZone(ZoneId.of("America/Lima")).toLocalDate();}
    private static String nombreBase(String estado){return switch(estado){case NO_HABILITADO->"NO_HABILITADO";case ACTIVO->"ACTIVO_AUTORIZADO";case SUSPENDIDO->"SUSPENDIDO_SEGURIDAD";case INACTIVO->"INACTIVO_AUTORIZADO";default->throw new IllegalStateException("Estado Network desconocido.");};}
    private static String nombrePendiente(String accion){return switch(Cambio.valueOf(accion)){case ACTIVAR,REACTIVAR->"ACTIVACION_PENDIENTE";case MODIFICAR->"MODIFICACION_PENDIENTE";case DESACTIVAR->"DESACTIVACION_PENDIENTE";};}
    private static String nombreSolicitud(String estado){return switch(estado){case SolicitudParticipacionNetwork.PENDIENTE->"PENDIENTE";case SolicitudParticipacionNetwork.OBSERVADA->"OBSERVADA";case SolicitudParticipacionNetwork.APROBADA->"APROBADA";case SolicitudParticipacionNetwork.RECHAZADA->"RECHAZADA";default->throw new IllegalStateException("Estado de solicitud desconocido.");};}
    private static DatosSolicitud sintaxis(DatosSolicitud d) {
        if(d==null || d.idEncargo()==null || d.idEncargo()<=0 || d.accion()==null)throw new ReglaNegocioException("Indica Encargo y acción Network.");
        requiereVersion(d.versionParticipacion());String motivo=texto(d.motivo(),"motivo");
        if(d.accion()!=Cambio.DESACTIVAR) {
            camposValidos(d.campos());texto(d.finalidad(),"finalidad");texto(d.huellaDatos(),"huella de datos");texto(d.huellaAutoridad(),"huella de autorización");
            if(d.desde()==null || d.hasta()==null || !d.desde().isBefore(d.hasta()))throw new ReglaNegocioException("Indica la vigencia explícita.");
        }
        return new DatosSolicitud(d.idEncargo(),d.accion(),d.campos()==null?null:Collections.unmodifiableSet(new TreeSet<>(d.campos())),
                d.finalidad(),d.desde(),d.hasta(),motivo,d.versionParticipacion(),d.huellaDatos(),d.huellaAutoridad());
    }
    private static DatosDecision sintaxis(DatosDecision d) {
        if(d==null || d.decision()==null)throw new ReglaNegocioException("Indica solicitud y decisión.");
        uuid(d.solicitud());requiereVersion(d.versionSolicitud());requiereVersion(d.versionParticipacion());
        return new DatosDecision(d.solicitud(),d.decision(),texto(d.motivo(),"motivo"),d.versionSolicitud(),d.versionParticipacion(),
                texto(d.huellaDatos(),"huella de datos"),texto(d.huellaAutoridad(),"huella de autorización"));
    }
    private static DatosDesactivacion sintaxis(DatosDesactivacion d) {
        if(d==null || d.idEncargo()==null || d.idEncargo()<=0)throw new ReglaNegocioException("Indica Encargo.");
        requiereVersion(d.versionParticipacion());return new DatosDesactivacion(d.idEncargo(),texto(d.motivo(),"motivo"),d.versionParticipacion());
    }
}
