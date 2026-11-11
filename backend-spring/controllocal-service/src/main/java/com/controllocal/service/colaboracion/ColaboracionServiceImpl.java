package com.controllocal.service.colaboracion;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.domain.auditoria.ComandoIdempotente;
import com.controllocal.domain.colaboracion.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.controllocal.service.colaboracion.ProyeccionNetwork.recursos;
import static com.controllocal.service.colaboracion.ProyeccionNetwork.Accion.*;

/** RF035–037. Dos decisiones sobre un alcance; ningún permiso de cartera por coincidencia. */
@Service
public class ColaboracionServiceImpl implements ColaboracionService {
    private final SolicitudColaboracionRepository solicitudes;
    private final DecisionColaboracionRepository decisiones;
    private final ConcesionColaboracionRepository concesiones;
    private final ProyeccionNetwork network;
    private final ContextosService contextos;
    private final CuentaAccesoRepository cuentas;
    private final UsuarioOrganizacionRepository miembros;
    private final ComandoIdempotenteRepository registros;
    private final ComandosIdempotentes comandos;
    private final Documentos documentos;
    private final EventoDominioRepository eventos;
    private final Transiciones transiciones;
    private final EntityManager entidades;
    private final Clock reloj;
    private final Alcances alcances;
    @org.springframework.beans.factory.annotation.Autowired
    public ColaboracionServiceImpl(SolicitudColaboracionRepository solicitudes,DecisionColaboracionRepository decisiones,
            ConcesionColaboracionRepository concesiones,ProyeccionNetwork network,ContextosService contextos,
            CuentaAccesoRepository cuentas,UsuarioOrganizacionRepository miembros,ComandoIdempotenteRepository registros,
            ComandosIdempotentes comandos,Documentos documentos,EventoDominioRepository eventos,
            Transiciones transiciones,EntityManager entidades,Alcances alcances) {
        this(solicitudes,decisiones,concesiones,network,contextos,cuentas,miembros,registros,comandos,documentos,
                eventos,transiciones,entidades,Clock.systemUTC(),alcances);
    }
    ColaboracionServiceImpl(SolicitudColaboracionRepository solicitudes,DecisionColaboracionRepository decisiones,
            ConcesionColaboracionRepository concesiones,ProyeccionNetwork network,ContextosService contextos,
            CuentaAccesoRepository cuentas,UsuarioOrganizacionRepository miembros,ComandoIdempotenteRepository registros,
            ComandosIdempotentes comandos,Documentos documentos,EventoDominioRepository eventos,
            Transiciones transiciones,EntityManager entidades,Clock reloj,Alcances alcances) {
        this.solicitudes=solicitudes;this.decisiones=decisiones;this.concesiones=concesiones;this.network=network;
        this.contextos=contextos;this.cuentas=cuentas;this.miembros=miembros;this.registros=registros;
        this.comandos=comandos;this.documentos=documentos;this.eventos=eventos;this.transiciones=transiciones;
        this.entidades=entidades;this.reloj=reloj;
        this.alcances=alcances;
    }

    @Override @Transactional
    public DatosSolicitud validarSolicitud(DatosSolicitud datos,Actor actor) {
        var d=solicitudValida(datos);autorizarSolicitud(d,actor);return d;
    }
    @Override @Transactional
    public DatosInvitacion validarInvitacion(DatosInvitacion datos,Actor actor){
        var d=invitacionValida(datos);var r=network.resolverInvitacion(d,actor);network.validarAlcance(r,d.alcance(),actor);exigirRevalidacion(r,d.alcance(),actor);return d;
    }
    @Override @Transactional
    public List<Acuerdo> invitacionesRecibidas(Actor actor,int pagina,int tamano){
        Alcances.exigirOperador(actor);List<Long> roles=alcances.de(actor).rolesAgente();
        if(roles.isEmpty())return List.of();
        var ids=solicitudes.invitacionesRecibidas(actor.idOrganizacion(),roles,org.springframework.data.domain.PageRequest.of(Math.max(0,pagina-1),Math.min(100,Math.max(1,tamano))));
        return ids.stream().map(ref->consultarAcuerdo(ref.toString(),actor)).toList();
    }
    @Override @Transactional
    public List<Acuerdo> invitacionesOriginadas(Actor actor,int pagina,int tamano){
        Alcances.exigirOperador(actor);List<Long> roles=alcances.de(actor).rolesAgente();
        if(roles.isEmpty())return List.of();
        var ids=solicitudes.invitacionesOriginadas(actor.idOrganizacion(),roles,org.springframework.data.domain.PageRequest.of(Math.max(0,pagina-1),Math.min(100,Math.max(1,tamano))));
        return ids.stream().map(ref->consultarAcuerdo(ref.toString(),actor)).toList();
    }
    @Override @Transactional
    public DatosDecision validarDecision(DatosDecision datos,Actor actor) {
        var d=decisionValida(datos);var s=leer(d.solicitud(),actor);
        autorizarDecision(s,d,actor);return d;
    }
    @Override @Transactional
    public VistaDecision previsualizarDecision(DatosDecision datos,Actor actor) {
        var d=decisionValida(datos);var s=leer(d.solicitud(),actor);autorizarDecision(s,d,actor);
        var permiso=s.esInvitacion()&&d.parte()==LadoColaboracion.ENCARGO?
                new PermisoColaboracion(new com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto(s.getIdEncargo(),s.getRevisionPermiso()),s.getClausulaPermiso()):null;
        return new VistaDecision(d,snapshotPropio(s,d,actor),permiso);
    }
    @Override @Transactional
    public DatosRevocacion validarRevocacion(DatosRevocacion datos,Actor actor) {
        var d=revocacionValida(datos);var c=leerConcesion(d.concesion(),actor);
        var s=solicitudes.leerId(c.getSolicitud(),actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));
        autorizarRevocacion(s,c,d,actor);return d;
    }
    @Override @Transactional
    public MetadatosAcuerdo consultarMetadatos(String solicitud,Actor actor) {
        Alcances.exigirOperador(actor);return metadatos(leer(solicitud,actor),actor);
    }
    @Override @Transactional
    public MetadatosAcuerdo consultarMetadatosPorConcesion(String concesion,Actor actor) {
        Alcances.exigirOperador(actor);
        long id=concesiones.solicitudDe(referencia(concesion),actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));
        var s=solicitudes.leerId(id,actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));
        entidades.refresh(s);return metadatos(s,actor);
    }
    private MetadatosAcuerdo metadatos(SolicitudColaboracion s,Actor actor) {
        var parte=network.exigirPartePropia(recursos(s),null,CONSULTAR,actor);
        var c=concesiones.deSolicitud(s.getId(),actor.idOrganizacion());c.ifPresent(entidades::refresh);
        return new MetadatosAcuerdo(s.getReferencia().toString(),s.getVersion(),s.estadoNombre(),s.getVersionAlcance(),
                alcance(s.getAlcance()),c.map(x->x.getReferencia().toString()).orElse(null),c.map(ConcesionColaboracion::getVersion).orElse(null),
                c.map(x->alcance(x.getAlcance())).orElse(null),c.map(ConcesionColaboracion::getRevocadaEn).orElse(null),parte);
    }
    @Override @Transactional
    public Acuerdo consultarAcuerdo(String solicitud,Actor actor) {
        Alcances.exigirOperador(actor);var s=leer(solicitud,actor);var r=recursos(s);
        var parte=network.exigirPartePropia(r,null,CONSULTAR,actor);
        var concesion=concesiones.deSolicitud(s.getId(),actor.idOrganizacion());
        boolean efectiva=false;AlcanceColaboracion alcance=null;
        if(concesion.isPresent()) {
            entidades.refresh(concesion.get());
            alcance=alcanceConcordante(concesion.get(),s,actor);
            efectiva=concesion.get().getRevocadaEn()==null && alcance.vigente(reloj.instant());
            if(efectiva&&s.esInvitacion())efectiva=autoridadesInvitacionVigentes(s,actor);
            if(efectiva) {
                efectiva=network.revalidar(r,alcance,actor);
                entidades.refresh(concesion.get());
                efectiva=efectiva && concesion.get().getRevocadaEn()==null && alcance.vigente(reloj.instant());
            }
        }
        Map<LadoColaboracion,Map<String,Object>> datosConcedidos=null;
        long destinatarioExplicito=parte.lado()!=LadoColaboracion.ENCARGO?s.responsableContraparte():s.getResponsableEncargo();
        boolean puedeRecibir=actor.esAgente() && actor.idRolOperativo()==parte.responsable()
                && actor.idRolOperativo()==destinatarioExplicito
                && alcance!=null && alcance.destinatarios().contains(parte.lado());
        if(efectiva) {
            var snapshots=snapshots(concesion.orElseThrow(),s,alcance,actor);
            var referenciasConcedidas=referenciasConcedidas(concesion.orElseThrow(),s,alcance,snapshots,actor);
            efectiva=network.revalidarSnapshots(r,alcance,snapshots,referenciasConcedidas,actor);
            if(efectiva && puedeRecibir) {
                efectiva=network.revalidar(r,alcance,actor)
                        && network.revalidarSnapshots(r,alcance,snapshots,referenciasConcedidas,actor);
            }
            entidades.refresh(concesion.get());
            efectiva=efectiva && concesion.get().getRevocadaEn()==null && alcance.vigente(reloj.instant());
            if(efectiva && puedeRecibir) {
                Map<LadoColaboracion,Map<String,Object>> datos=new EnumMap<>(LadoColaboracion.class);
                snapshots.forEach((lado,snapshot)->datos.put(lado,snapshot.campos()));datosConcedidos=Collections.unmodifiableMap(datos);
            }
        }
        var visibles=decisiones.historial(s.getId(),actor.idOrganizacion()).stream().map(d->new DecisionVisible(
                d.getLado(),d.getDecision(),d.getLado()==parte.lado()?d.getMotivo():null,d.getRegistradaEn())).toList();
        return new Acuerdo(s.getReferencia().toString(),s.getVersion(),s.estadoNombre(),s.getVersionAlcance(),
                alcance(s.getAlcance()),visibles,concesion.map(c->c.getReferencia().toString()).orElse(null),
                concesion.map(ConcesionColaboracion::getVersion).orElse(null),alcance,efectiva,
                concesion.map(ConcesionColaboracion::getRevocadaEn).orElse(null),datosConcedidos);
    }
    @Override @Transactional
    public Acuerdo consultarPorConcesion(String concesion,Actor actor) {
        Alcances.exigirOperador(actor);
        long id=concesiones.solicitudDe(referencia(concesion),actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));
        var s=solicitudes.leerId(id,actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));
        return consultarAcuerdo(s.getReferencia().toString(),actor);
    }
    @Override @Transactional
    public Optional<Resultado> consultarResultado(String capacidad,String clave,Actor actor) {
        Alcances.exigirOperador(actor);
        String comando=switch(capacidad==null?"":capacidad) {
            case "solicitar_colaboracion"->"SOLICITAR_COLABORACION";
            case "invitar_colaboracion_encargo"->"INVITAR_COLABORACION_ENCARGO";
            case "decidir_colaboracion"->"DECIDIR_COLABORACION";
            case "revocar_colaboracion"->"REVOCAR_COLABORACION";
            default->throw new ReglaNegocioException("La capacidad no tiene un resultado de colaboración.");
        };
        return comandos.consultarResultado(actor,clave,comando).map(c->restaurar(c.getResultado(),actor));
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Resultado invitar(DatosInvitacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia origen){
        var d=invitacionValida(datos);var p=Procedencia.oPantalla(origen);String k=Idempotencia.exigirClave(clave);
        iniciar(actor,sesion);String h=huella(d,p);bloquearComando(actor,k);
        var anterior=previo(actor,k,"INVITAR_COLABORACION_ENCARGO",h);if(anterior.isPresent())return restaurar(anterior.get().getResultado(),actor);
        var r=network.resolverInvitacion(d,actor);network.validarAlcance(r,d.alcance(),actor);exigirRevalidacion(r,d.alcance(),actor);
        var ahora=reloj.instant();var i=r.invitacion();
        var s=SolicitudColaboracion.invitacion(i.organizacion(),r.organizacionEncargo(),r.idEncargo(),i.responsable(),r.responsableEncargo(),actor.idRolOperativo(),
                i.version(),r.versionEncargo(),i.revisionPermiso(),i.clausulaPermiso(),serializar(d.alcance()),Idempotencia.huellaExacta(serializar(d.alcance())),ahora);
        transiciones.iniciar(s,SolicitudColaboracion.PENDIENTE);solicitudes.saveAndFlush(s);
        return registrar("INVITAR_COLABORACION_ENCARGO","INVITACION_COLABORACION_PREPARADA",s,null,null,null,d.alcance(),actor,k,h,p,ahora);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Resultado solicitar(DatosSolicitud datos,Actor actor,ContextoSesion sesion,String clave,Procedencia origen) {
        var d=solicitudValida(datos);var p=Procedencia.oPantalla(origen);String k=Idempotencia.exigirClave(clave);
        iniciar(actor,sesion);String huella=huella(d,p);bloquearComando(actor,k);
        var previo=previo(actor,k,"SOLICITAR_COLABORACION",huella);
        if(previo.isPresent())return restaurar(previo.get().getResultado(),actor);
        var r=autorizarSolicitud(d,actor);var ahora=reloj.instant();
        var s=SolicitudColaboracion.nueva(r.organizacionDemanda(),r.organizacionEncargo(),r.idDemanda(),r.idEncargo(),
                r.responsableDemanda(),r.responsableEncargo(),actor.idRolOperativo(),r.versionDemanda(),r.versionEncargo(),
                r.versionOferta(),r.ofertaRef(),serializar(d.alcance()),Idempotencia.huellaExacta(serializar(d.alcance())),ahora);
        transiciones.iniciar(s,SolicitudColaboracion.PENDIENTE);solicitudes.saveAndFlush(s);
        return registrar("SOLICITAR_COLABORACION","COLABORACION_SOLICITADA",s,null,null,null,d.alcance(),actor,k,huella,p,ahora);
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Resultado decidirPorParte(DatosDecision datos,Actor actor,ContextoSesion sesion,String clave,Procedencia origen) {
        var d=decisionValida(datos);var p=Procedencia.oPantalla(origen);String k=Idempotencia.exigirClave(clave);
        iniciar(actor,sesion);String huella=huella(d,p);bloquearComando(actor,k);
        var previo=previo(actor,k,"DECIDIR_COLABORACION",huella);
        if(previo.isPresent())return restaurar(previo.get().getResultado(),actor);
        var s=solicitudes.bloquear(referencia(d.solicitud()),actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));
        entidades.refresh(s);autorizarDecision(s,d,actor);
        if(s.esInvitacion()&&!autoridadesInvitacionVigentes(s,actor))throw new ReglaNegocioException("Una decisión anterior perdió su autoridad; se requiere una nueva solicitud de colaboración.");
        var ahora=reloj.instant();
        ProyeccionNetwork.SnapshotParte snapshot=snapshotPropio(s,d,actor);
        var decision=DecisionColaboracion.nueva(actor.idOrganizacion(),s.getOrganizacionId(),s.getOrganizacionEncargo(),
                s.getId(),d.parte(),d.decision().name(),actor.idRolOperativo(),d.versionAlcance(),d.versionRecurso(),
                d.decision()==Decision.RECHAZAR?null:serializar(d.alcance()),snapshot==null?null:serializar(snapshot),d.motivo(),ahora);
        decisiones.saveAndFlush(decision);s.registrarDecision(d.parte(),decision.getId());
        ConcesionColaboracion c=null;boolean solicitudGuardada=false;
        String traza=d.decision()+" por "+d.parte()+"; decisión "+decision.getId();
        if(d.decision()==Decision.RECHAZAR)transiciones.aplicar(s,s.getId(),SolicitudColaboracion.RECHAZADA,actor,traza,fecha(ahora));
        else if(s.decisionContraparte()!=null && s.getDecisionEncargo()!=null) {
            var dem=decision(s.decisionContraparte(),s,actor);var enc=decision(s.getDecisionEncargo(),s,actor);
            if(dem.getLado()!=s.ladoContraparte() || enc.getLado()!=LadoColaboracion.ENCARGO
                    || dem.getVersionAlcance()!=s.getVersionAlcance() || enc.getVersionAlcance()!=s.getVersionAlcance())
                throw new IllegalStateException("Decisiones incompatibles con la versión de colaboración.");
            var comun=alcance(dem.getAlcance()).interseccion(alcance(enc.getAlcance()));
            if(comun.isPresent()) {
                network.validarAlcance(recursos(s),comun.get(),actor);exigirRevalidacion(recursos(s),comun.get(),actor);
                c=ConcesionColaboracion.nueva(s.getOrganizacionId(),s.getOrganizacionEncargo(),s.getId(),dem.getId(),
                        enc.getId(),s.getVersionAlcance(),serializar(comun.get()),ahora);
                if(s.esInvitacion())c.paraInvitado();
                transiciones.aplicar(s,s.getId(),SolicitudColaboracion.APROBADA,actor,traza,fecha(ahora));
                solicitudes.saveAndFlush(s);solicitudGuardada=true;
                concesiones.saveAndFlush(c);
            }
        }
        if(!solicitudGuardada)solicitudes.saveAndFlush(s);
        return registrar("DECIDIR_COLABORACION","COLABORACION_DECIDIDA",s,c,d.parte(),d.decision().name(),
                c==null?d.alcance():alcance(c.getAlcance()),actor,k,huella,p,ahora);
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Resultado revocarPropia(DatosRevocacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia origen) {
        var d=revocacionValida(datos);var p=Procedencia.oPantalla(origen);String k=Idempotencia.exigirClave(clave);
        iniciar(actor,sesion);String huella=huella(d,p);bloquearComando(actor,k);
        var previo=previo(actor,k,"REVOCAR_COLABORACION",huella);
        if(previo.isPresent())return restaurar(previo.get().getResultado(),actor);
        long idSolicitud=concesiones.solicitudDe(referencia(d.concesion()),actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));
        var s=solicitudes.bloquearId(idSolicitud,actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));
        entidades.refresh(s);
        var c=concesiones.bloquear(referencia(d.concesion()),actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));
        entidades.refresh(c);autorizarRevocacion(s,c,d,actor);
        var ahora=reloj.instant();c.revocar(d.parte(),actor.idOrganizacion(),actor.idRolOperativo(),d.motivo(),ahora);
        concesiones.saveAndFlush(c);
        return registrar("REVOCAR_COLABORACION","COLABORACION_REVOCADA",s,c,d.parte(),"REVOCAR",
                alcance(c.getAlcance()),actor,k,huella,p,ahora);
    }

    private ProyeccionNetwork.Recursos autorizarSolicitud(DatosSolicitud d,Actor actor) {
        if(!actor.esAgente())throw new AccesoNoAutorizadoException();
        var r=network.resolverOferta(d.ofertaRef(),d.idDemanda(),actor);
        if(r.organizacionDemanda()!=actor.idOrganizacion() || r.idDemanda()!=d.idDemanda())throw new AccesoNoAutorizadoException();
        var parte=network.exigirPartePropia(r,LadoColaboracion.DEMANDA,SOLICITAR,actor);
        if(parte.lado()!=LadoColaboracion.DEMANDA || parte.responsable()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        version(d.versionDemanda(),r.versionDemanda());version(d.versionOferta(),r.versionOferta());
        network.validarAlcance(r,d.alcance(),actor);exigirRevalidacion(r,d.alcance(),actor);return r;
    }
    private void autorizarDecision(SolicitudColaboracion s,DatosDecision d,Actor actor) {
        var r=recursos(s);var parte=network.exigirPartePropia(r,d.parte(),DECIDIR,actor);exigirDecisor(parte,d.parte(),actor);
        version(d.versionSolicitud(),s.getVersion());version(d.versionAlcance(),s.getVersionAlcance());version(d.versionRecurso(),parte.version());
        if(!SolicitudColaboracion.PENDIENTE.equals(s.estadoActual()))throw new ConflictoException("La solicitud ya tiene una resolución; consulta su resultado.");
        if(d.decision()!=Decision.RECHAZAR) {
            var solicitado=alcance(s.getAlcance());
            if(!solicitado.contiene(d.alcance()))throw new ReglaNegocioException("La decisión no puede ampliar el alcance solicitado.");
            if(d.decision()==Decision.ACEPTAR && !solicitado.equals(d.alcance()))throw new ReglaNegocioException("Usa LIMITAR para reducir el alcance solicitado.");
            network.validarAlcance(r,d.alcance(),actor);exigirRevalidacion(r,d.alcance(),actor);
        }
    }
    private void exigirRevalidacion(ProyeccionNetwork.Recursos r,AlcanceColaboracion alcance,Actor actor) {
        if(!network.revalidar(r,alcance,actor))throw new ReglaNegocioException("La colaboración no conserva las condiciones y autorizaciones actuales de esta actuación.");
    }
    private void autorizarRevocacion(SolicitudColaboracion s,ConcesionColaboracion c,DatosRevocacion d,Actor actor) {
        var parte=network.exigirPartePropia(recursos(s),d.parte(),REVOCAR,actor);exigirDecisor(parte,d.parte(),actor);
        version(d.versionConcesion(),c.getVersion());version(d.versionAlcance(),c.getVersionAlcance());version(d.versionRecurso(),parte.version());
        if(c.getRevocadaEn()!=null)throw new ConflictoException("La concesión ya está revocada; consulta su resultado.");
        if(!alcance(c.getAlcance()).equals(d.alcanceEsperado()))throw new ConflictoException("El alcance de la concesión cambió; revisa el actual.");
    }
    private void exigirDecisor(ProyeccionNetwork.ParteActual p,LadoColaboracion lado,Actor actor) {
        if(p.lado()!=lado || p.organizacion()!=actor.idOrganizacion())throw new AccesoNoAutorizadoException();
        if(actor.esBroker() && !p.individual())return;
        if(actor.esAgente() && p.individual() && actor.idRolOperativo()==p.responsable())return;
        throw new AccesoNoAutorizadoException();
    }
    private ProyeccionNetwork.SnapshotParte snapshotPropio(SolicitudColaboracion s,DatosDecision d,Actor actor) {
        if(d.decision()==Decision.RECHAZAR)return null;
        var snapshot=network.snapshotPartePropia(recursos(s),s.getReferencia().toString(),d.parte(),d.alcance(),actor);
        version(d.versionRecurso(),snapshot.versionRelevante());
        if(!d.alcance().campos().getOrDefault(d.parte(),Set.of()).containsAll(snapshot.campos().keySet()))
            throw new IllegalStateException("El snapshot contiene campos fuera del alcance autorizado de su parte.");
        return snapshot;
    }
    private void iniciar(Actor actor,ContextoSesion sesion) {
        Alcances.exigirOperador(actor);if(sesion==null)throw new AccesoNoAutorizadoException();
        var ids=miembros.cuentasActivasDePersona(actor.idOrganizacion(),actor.idPersona());
        if(ids.size()!=1)throw new AccesoNoAutorizadoException();
        cuentas.bloquearCambioAcceso(ids.getFirst()).orElseThrow(AccesoNoAutorizadoException::new);
        try {if(!ContextoSesion.de(contextos.exigirContexto(sesion.referenciaSesion(),sesion.revisionContexto(),actor)).equals(sesion))throw new AccesoNoAutorizadoException();}
        catch(CredencialesInvalidasException fallo){throw new AccesoNoAutorizadoException();}
    }
    private void bloquearComando(Actor actor,String clave) {
        var hash=Idempotencia.huellaExacta("BROX-COMANDO\u0000"+actor.idOrganizacion()+"\u0000"+clave);
        registros.bloquearIdentidad(Long.parseUnsignedLong(hash.substring(0,16),16));
    }
    private Optional<ComandoIdempotente> previo(Actor actor,String clave,String tipo,String huella) {
        try{return comandos.buscar(actor,clave,tipo,huella);}
        catch(ReglaNegocioException conflictoDeContenido){
            // La clave ya fue validada: en buscar esta regla identifica otra entrada del comando.
            throw new ConflictoException("La clave corresponde a otra actuación o contenido; usa una nueva para otro intento.");
        }
    }
    private Resultado restaurar(String json,Actor actor) {
        var r=documentos.comoObjeto(json,Resultado.class);var s=leer(r.solicitud(),actor);
        network.exigirPartePropia(recursos(s),null,CONSULTAR,actor);
        return r; // instantánea original mínima; no afirma que su concesión siga efectiva
    }
    private Resultado registrar(String comando,String evento,SolicitudColaboracion s,ConcesionColaboracion c,
            LadoColaboracion parte,String decision,AlcanceColaboracion alcance,Actor actor,String clave,String huella,Procedencia p,Instant ahora) {
        var resultado=new Resultado(s.getReferencia().toString(),s.getVersion(),s.estadoNombre(),
                c==null?null:c.getReferencia().toString(),c==null?null:c.getVersion(),parte,decision,alcance,ahora);
        var carga=new LinkedHashMap<String,Object>();carga.put("solicitud",s.getReferencia());carga.put("version",s.getVersion());
        carga.put("parte",parte);carga.put("decision",decision);carga.put("estado",s.estadoNombre());carga.put("registradoEn",ahora);
        carga.put("decisionDemanda",s.decisionContraparte());carga.put("decisionEncargo",s.getDecisionEncargo());
        var marca=new com.controllocal.service.soporte.AvisoComercialBroker.Marca("NB-11","COLABORACION",s.getId(),s.getVersion(),List.of("ALCANCE_PROPIO"));
        var salida=eventos.saveAndFlush(p.sellar(EventoDominio.de(actor.idOrganizacion(),evento,SolicitudColaboracion.TIPO,s.getId(),actor.idRolOperativo(),p.canal())).con(documentos.objetoCanonico(com.controllocal.service.soporte.AvisoComercialBroker.con(carga,marca))));
        boolean visibleContraparte=!s.esInvitacion()||s.getDecisionEncargo()!=null&&Set.of("ACEPTAR","LIMITAR").contains(decision(s.getDecisionEncargo(),s,actor).getDecision());
        if(visibleContraparte){
            long otra=actor.idOrganizacion()==s.getOrganizacionEncargo()?s.getOrganizacionId():s.getOrganizacionEncargo();
            if(otra!=actor.idOrganizacion())eventos.saveAndFlush(EventoDominio.de(otra,"COLABORACION_LADO_DISPONIBLE",SolicitudColaboracion.TIPO,s.getId(),null,EventoDominio.CANAL_SISTEMA)
                .con(documentos.objetoCanonico(com.controllocal.service.soporte.AvisoComercialBroker.con(Map.of("solicitud",s.getReferencia(),"eventoOrigen",salida.getId()),marca))));
        }
        String json=serializar(resultado);comandos.registrar(actor,clave,comando,huella,SolicitudColaboracion.TIPO,s.getId(),p,json);
        return documentos.comoObjeto(json,Resultado.class);
    }
    private SolicitudColaboracion leer(String ref,Actor actor) {var s=solicitudes.leer(referencia(ref),actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));entidades.refresh(s);return s;}
    private boolean autoridadesInvitacionVigentes(SolicitudColaboracion s,Actor actor){
        for(var lado:new LadoColaboracion[]{s.ladoContraparte(),LadoColaboracion.ENCARGO}){
            Long id=s.decisionDe(lado);if(id==null)continue;var d=decision(id,s,actor);
            if(!network.autoridadDecisionVigente(recursos(s),lado,d.getOrganizacionId(),d.getDecisor()))return false;
        }return true;
    }
    private ConcesionColaboracion leerConcesion(String ref,Actor actor) {var c=concesiones.leer(referencia(ref),actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));entidades.refresh(c);return c;}
    private DecisionColaboracion decision(long id,SolicitudColaboracion s,Actor actor) {return decisiones.actual(id,s.getId(),actor.idOrganizacion()).orElseThrow(()->new IllegalStateException("La decisión vinculada no existe en la colaboración."));}
    /** Misma intersección de la creación: un JSON de concesión no amplía las decisiones persistidas. */
    private AlcanceColaboracion alcanceConcordante(ConcesionColaboracion c,SolicitudColaboracion s,Actor actor) {
        var dem=decision(c.decisionContraparte(),s,actor);var enc=decision(c.getDecisionEncargo(),s,actor);
        if(dem.getLado()!=s.ladoContraparte() || enc.getLado()!=LadoColaboracion.ENCARGO
                || c.getVersionAlcance()!=s.getVersionAlcance()
                || dem.getVersionAlcance()!=c.getVersionAlcance() || enc.getVersionAlcance()!=c.getVersionAlcance()
                || Decision.RECHAZAR.name().equals(dem.getDecision()) || Decision.RECHAZAR.name().equals(enc.getDecision())
                || dem.getAlcance()==null || enc.getAlcance()==null)
            throw new IllegalStateException("La concesión no corresponde a dos decisiones compatibles de sus lados.");
        var comun=alcance(dem.getAlcance()).interseccion(alcance(enc.getAlcance()))
                .orElseThrow(()->new IllegalStateException("La concesión carece de un alcance común aprobado."));
        var guardado=alcance(c.getAlcance());
        if(!comun.equals(guardado) || !alcance(s.getAlcance()).contiene(guardado))
            throw new IllegalStateException("La concesión difiere del alcance común de las dos decisiones aprobadas.");
        return guardado;
    }
    private Map<LadoColaboracion,ReferenciaParteConcedida> referenciasConcedidas(ConcesionColaboracion c,
            SolicitudColaboracion s,AlcanceColaboracion alcance,
            Map<LadoColaboracion,ProyeccionNetwork.SnapshotParte> snapshots,Actor actor) {
        Map<LadoColaboracion,ReferenciaParteConcedida> resultado=new EnumMap<>(LadoColaboracion.class);
        for(long id:new long[]{c.decisionContraparte(),c.getDecisionEncargo()}) {
            var d=decision(id,s,actor);var lado=d.getLado();var snapshot=snapshots.get(lado);
            if(snapshot==null)throw new IllegalStateException("Falta el snapshot seleccionado de una parte de la concesión.");
            boolean demanda=lado!=LadoColaboracion.ENCARGO;
            var campos=PoliticaColaboracionBilateral.camposDelRecurso(lado,alcance.campos().getOrDefault(lado,Set.of()));
            resultado.put(lado,new ReferenciaParteConcedida(c.getReferencia().toString(),c.getVersion(),s.getReferencia().toString(),
                    s.getVersionAlcance(),d.getId(),lado,demanda?s.getOrganizacionId():s.getOrganizacionEncargo(),
                    demanda?(s.esInvitacion()?s.responsableContraparte():s.getIdDemanda()):s.getIdEncargo(),demanda?s.responsableContraparte():s.getResponsableEncargo(),
                    d.getVersionRecurso(),campos,demanda&&campos.contains("criterios")?PoliticaColaboracionBilateral.selectoresFisicos():Set.of(),
                    alcance.finalidad(),alcance.desde(),alcance.hasta(),Idempotencia.huellaExacta(documentos.objetoCanonico(snapshot.campos()))));
        }
        return Collections.unmodifiableMap(resultado);
    }
    private Map<LadoColaboracion,ProyeccionNetwork.SnapshotParte> snapshots(ConcesionColaboracion c,
            SolicitudColaboracion s,AlcanceColaboracion alcance,Actor actor) {
        Map<LadoColaboracion,ProyeccionNetwork.SnapshotParte> resultado=new EnumMap<>(LadoColaboracion.class);
        for(long id:new long[]{c.decisionContraparte(),c.getDecisionEncargo()}) {
            var d=decision(id,s,actor);var original=documentos.comoObjeto(d.getSnapshotParte(),ProyeccionNetwork.SnapshotParte.class);
            if(original.versionRelevante()!=d.getVersionRecurso() || d.getVersionAlcance()!=c.getVersionAlcance())
                throw new IllegalStateException("El snapshot no corresponde a la decisión de su recurso/versión.");
            Map<String,Object> campos=new LinkedHashMap<>();
            for(String campo:alcance.campos().getOrDefault(d.getLado(),Set.of()))
                if(original.campos().containsKey(campo))campos.put(campo,original.campos().get(campo));
            resultado.put(d.getLado(),new ProyeccionNetwork.SnapshotParte(campos,original.versionRelevante()));
        }
        if(!resultado.keySet().equals(Set.of(s.ladoContraparte(),LadoColaboracion.ENCARGO)))
            throw new IllegalStateException("La concesión no contiene las dos decisiones de sus lados.");
        return Collections.unmodifiableMap(resultado);
    }
    private AlcanceColaboracion alcance(String json){return documentos.comoObjeto(json,AlcanceColaboracion.class);}
    private String serializar(Object valor){return documentos.objetoCanonico(valor);}
    private String huella(Object valor,Procedencia p){return Idempotencia.huellaExacta(documentos.objetoCanonico(Map.of("datos",valor,"procedencia",p)));}
    private static LocalDate fecha(Instant ahora){return ahora.atZone(ZoneId.of("America/Lima")).toLocalDate();}
    private static UUID referencia(String r){try{return UUID.fromString(r);}catch(RuntimeException e){throw new NoEncontradoException("Colaboración");}}
    private static void version(Long esperada,long actual){if(esperada==null || esperada<0)throw new ReglaNegocioException("Indica la versión observada; no puede omitirse.");if(esperada!=actual)throw new ConflictoException("La versión cambió; consulta y revisa el alcance actual.");}
    private static void requiereVersion(Long v){if(v==null || v<0)throw new ReglaNegocioException("Indica la versión observada; no puede omitirse.");}
    private static String texto(String s,String nombre){if(s==null || s.isBlank())throw new ReglaNegocioException("Indica "+nombre+".");return s.trim();}
    private static AlcanceColaboracion alcanceValido(AlcanceColaboracion a){
        if(a==null || a.destinatarios().isEmpty() || a.acciones().isEmpty() || a.campos().isEmpty()
                || a.finalidad()==null || a.finalidad().isBlank() || a.desde()==null || a.hasta()==null || !a.desde().isBefore(a.hasta())
                || a.acciones().stream().anyMatch(String::isBlank) || a.campos().values().stream().anyMatch(v->v.isEmpty() || v.stream().anyMatch(String::isBlank)))
            throw new ReglaNegocioException("Indica destinatarios, acciones, campos, finalidad y vigencia explícitos.");
        return a;
    }
    private static DatosSolicitud solicitudValida(DatosSolicitud d){
        if(d==null || d.idDemanda()==null || d.idDemanda()<=0)throw new ReglaNegocioException("Indica la Demanda propia.");
        requiereVersion(d.versionDemanda());requiereVersion(d.versionOferta());
        return new DatosSolicitud(d.idDemanda(),d.versionDemanda(),texto(d.ofertaRef(),"la referencia autorizada de oferta"),d.versionOferta(),alcanceValido(d.alcance()));
    }
    private static DatosInvitacion invitacionValida(DatosInvitacion d){
        if(d==null||d.encargo()<=0||d.versionEncargo()<0||d.organizacionInvitada()<=0||d.responsableInvitado()<=0||d.pacto()==null)
            throw new ReglaNegocioException("Identifique el Encargo, su versión, el destinatario explícito y el instrumento que permite colaborar.");
        return new DatosInvitacion(d.encargo(),d.versionEncargo(),d.organizacionInvitada(),d.responsableInvitado(),d.pacto(),texto(d.clausulaPermiso(),"la cláusula declarada que permite esta colaboración"),alcanceValido(d.alcance()));
    }
    private static DatosDecision decisionValida(DatosDecision d){
        if(d==null || d.parte()==null || d.decision()==null)throw new ReglaNegocioException("Indica parte y decisión.");
        referencia(d.solicitud());requiereVersion(d.versionSolicitud());requiereVersion(d.versionAlcance());requiereVersion(d.versionRecurso());
        if(d.decision()==Decision.RECHAZAR && d.alcance()!=null)throw new ReglaNegocioException("Rechazar no concede un alcance.");
        return new DatosDecision(d.solicitud(),d.parte(),d.decision(),d.decision()==Decision.RECHAZAR?null:alcanceValido(d.alcance()),
                texto(d.motivo(),"el motivo"),d.versionSolicitud(),d.versionAlcance(),d.versionRecurso());
    }
    private static DatosRevocacion revocacionValida(DatosRevocacion d){
        if(d==null || d.parte()==null)throw new ReglaNegocioException("Indica tu parte de la concesión.");
        referencia(d.concesion());requiereVersion(d.versionConcesion());requiereVersion(d.versionAlcance());requiereVersion(d.versionRecurso());
        return new DatosRevocacion(d.concesion(),d.parte(),alcanceValido(d.alcanceEsperado()),texto(d.motivo(),"el motivo"),d.versionConcesion(),d.versionAlcance(),d.versionRecurso());
    }
}
