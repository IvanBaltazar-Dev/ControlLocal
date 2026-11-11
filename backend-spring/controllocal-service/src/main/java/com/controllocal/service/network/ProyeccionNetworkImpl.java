package com.controllocal.service.network;

import com.controllocal.domain.colaboracion.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.*;
import com.controllocal.service.colaboracion.*;
import com.controllocal.service.encargo.AccesoEncargo;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

/** Cargador de recursos/proyecciones autorizadas. El servicio bilateral conserva las dos decisiones y su vida. */
@Component
public class ProyeccionNetworkImpl implements ProyeccionNetwork {
    private final RequerimientoService demandas;
    private final RequerimientoClienteRepository referenciasDemanda;
    private final CaptacionRepository referenciasEncargo;
    private final AccesoEncargo acceso;
    private final FuenteDeNetwork fuente;
    private final NetworkService network;
    private final FuenteOfertaConcedida ofertaConcedida;
    private final Alcances alcances;
    private final IdentidadColaboracionRepository identidades;
    private final SolicitudColaboracionRepository solicitudes;
    private final DatosDeclaradosColaboracion declarados;
    private final Documentos json;
    private final Clock reloj;
    private final AccesoFuenteDeInvitacion permisosInvitacion;
    private final com.controllocal.service.encargo.EncargoService encargos;
    private final com.controllocal.service.encargo.PactosDeEncargo pactos;
    @org.springframework.beans.factory.annotation.Autowired
    public ProyeccionNetworkImpl(RequerimientoService demandas,RequerimientoClienteRepository referenciasDemanda,
            CaptacionRepository referenciasEncargo,AccesoEncargo acceso,FuenteDeNetwork fuente,NetworkService network,
            FuenteOfertaConcedida ofertaConcedida,Alcances alcances,IdentidadColaboracionRepository identidades,
            SolicitudColaboracionRepository solicitudes,CanalColaboracionRepository canales,Documentos json,
            com.controllocal.service.encargo.EncargoService encargos,com.controllocal.service.encargo.PactosDeEncargo pactos,AccesoFuenteDeInvitacion permisosInvitacion) {
        this(demandas,referenciasDemanda,referenciasEncargo,acceso,fuente,network,ofertaConcedida,alcances,identidades,solicitudes,canales,json,Clock.systemUTC(),encargos,pactos,permisosInvitacion);
    }
    ProyeccionNetworkImpl(RequerimientoService demandas,RequerimientoClienteRepository referenciasDemanda,
            CaptacionRepository referenciasEncargo,AccesoEncargo acceso,FuenteDeNetwork fuente,NetworkService network,
            FuenteOfertaConcedida ofertaConcedida,Alcances alcances,IdentidadColaboracionRepository identidades,
            SolicitudColaboracionRepository solicitudes,CanalColaboracionRepository canales,Documentos json,Clock reloj,
            com.controllocal.service.encargo.EncargoService encargos,com.controllocal.service.encargo.PactosDeEncargo pactos,AccesoFuenteDeInvitacion permisosInvitacion) {
        this.demandas=demandas;this.referenciasDemanda=referenciasDemanda;this.referenciasEncargo=referenciasEncargo;
        this.acceso=acceso;this.fuente=fuente;this.network=network;this.ofertaConcedida=ofertaConcedida;this.alcances=alcances;
        this.identidades=identidades;this.solicitudes=solicitudes;this.declarados=new DatosDeclaradosColaboracion(identidades,canales);this.json=json;this.reloj=reloj;
        this.encargos=encargos;this.pactos=pactos;this.permisosInvitacion=permisosInvitacion;
    }
    @Override public Recursos resolverOferta(String ofertaRef,long idDemanda,Actor actor) {
        Alcances.exigirOperador(actor);if(!actor.esAgente())throw new AccesoNoAutorizadoException();
        var d=contextoDemanda(actor.idOrganizacion(),idDemanda);
        var propia=demandas.autoridadParaColaboracion(idDemanda,d.getVersion(),RequerimientoService.AccionColaboracion.SOLICITAR,actor);
        if(propia.responsableId()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        var oferta=network.resolverReferencia(ofertaRef,actor).orElseThrow(()->new NoEncontradoException("Oferta disponible"));
        var e=referenciasEncargo.contextoActual(oferta.organizacion(),oferta.idEncargo()).orElseThrow(()->new NoEncontradoException("Oferta disponible"));
        if(!Objects.equals(e.getResponsable(),oferta.responsable()))throw new ConflictoException("La autoridad de la oferta cambió.");
        return new Recursos(propia.organizacionId(),idDemanda,propia.responsableId(),propia.version(),
                oferta.organizacion(),oferta.idEncargo(),oferta.responsable(),e.getVersion(),oferta.ofertaRef(),oferta.versionProyeccion());
    }
    @Override public ParteActual exigirPartePropia(Recursos r,LadoColaboracion lado,Accion accion,Actor actor) {
        Alcances.exigirOperador(actor);Objects.requireNonNull(r,"recursos");Objects.requireNonNull(accion,"acción");
        if(r.invitacion()!=null)return parteInvitacion(r,lado,accion,actor);
        if(lado==null) {
            if(accion!=Accion.CONSULTAR)throw new ReglaNegocioException("La actuación exige un lado explícito.");
            for(var candidato:r.lados()) {
                long org=org(r,candidato);if(org!=actor.idOrganizacion())continue;
                Long responsable=candidato==LadoColaboracion.DEMANDA?contextoDemanda(org,r.idDemanda()).getResponsable()
                        :acceso.contexto(r.idEncargo(),actor).getResponsable();
                if(alcances.alcanza(actor,responsable))return exigirPartePropia(r,candidato,accion,actor);
            }
            throw new AccesoNoAutorizadoException();
        }
        if(!r.lados().contains(lado))throw new AccesoNoAutorizadoException();
        if(org(r,lado)!=actor.idOrganizacion())throw new AccesoNoAutorizadoException();
        if(lado==LadoColaboracion.DEMANDA) {
            var c=contextoDemanda(actor.idOrganizacion(),r.idDemanda());
            var a=demandas.autoridadParaColaboracion(r.idDemanda(),c.getVersion(),RequerimientoService.AccionColaboracion.valueOf(accion.name()),actor);
            return new ParteActual(a.organizacionId(),a.responsableId(),a.version(),individual(a.organizacionId()),lado);
        }
        var c=acceso.contexto(r.idEncargo(),actor);var a=fuente.consultarAutoridadPropia(r.idEncargo(),actor);
        if(accion==Accion.SOLICITAR && (!actor.esAgente()||actor.idRolOperativo()!=a.responsable()))throw new AccesoNoAutorizadoException();
        if((accion==Accion.DECIDIR||accion==Accion.REVOCAR)&&!network.capacidadSobre(r.idEncargo(),actor).autoridadDecidir())throw new AccesoNoAutorizadoException();
        return new ParteActual(a.organizacion(),a.responsable(),c.getVersion(),a.individual(),lado);
    }
    @Override public void validarAlcance(Recursos r,AlcanceColaboracion alcance,Actor actor) {
        exigirPartePropia(r,null,Accion.CONSULTAR,actor);
        if(!r.lados().containsAll(alcance.destinatarios())||!r.lados().containsAll(alcance.campos().keySet()))throw new ReglaNegocioException("El alcance identifica un lado que no existe en esta colaboración.");
        if(!PoliticaProyeccionNetworkV1.FINALIDAD.equals(alcance.finalidad())||!Set.of("CONSULTAR").containsAll(alcance.acciones()))
            throw new ReglaNegocioException("La colaboración inicial sólo concede consulta en su finalidad explícita.");
        for(var entrada:alcance.campos().entrySet()) {
            Set<String> admitidos=new HashSet<>(PoliticaColaboracionBilateral.IDENTIDAD_Y_CANAL);
            if(entrada.getKey()!=LadoColaboracion.INVITADO)admitidos.addAll(entrada.getKey()==LadoColaboracion.DEMANDA?PoliticaColaboracionBilateral.CAMPOS_DEMANDA:PoliticaProyeccionNetworkV1.CAMPOS);
            if(!admitidos.containsAll(entrada.getValue()))throw new ReglaNegocioException("El alcance incluye campos no aprobados para esa parte.");
        }
    }
    @Override public SnapshotParte snapshotPartePropia(Recursos r,String solicitudRef,LadoColaboracion lado,AlcanceColaboracion alcance,Actor actor) {
        var propia=exigirPartePropia(r,lado,Accion.DECIDIR,actor);validarAlcance(r,alcance,actor);
        var s=solicitud(solicitudRef,actor);if(!ProyeccionNetwork.recursos(s).equals(r))throw new ConflictoException("La solicitud pertenece a otra versión de los recursos.");
        Set<String> campos=alcance.campos().getOrDefault(lado,Set.of());var camposRecurso=PoliticaColaboracionBilateral.camposDelRecurso(lado,campos);
        Map<String,Object> resultado=new LinkedHashMap<>();
        if(!camposRecurso.isEmpty()) {
            if(lado==LadoColaboracion.DEMANDA)resultado.putAll(demandas.proyectarCamposColaboracion(r.idDemanda(),propia.version(),camposRecurso,
                    camposRecurso.contains("criterios")?PoliticaColaboracionBilateral.selectoresFisicos():Set.of(),actor));
            else {
                var actual=fuente.consultarPropia(r.idEncargo(),camposRecurso,FuenteDeNetwork.Accion.DECIDIR,actor);
                if(r.invitacion()==null&&!actual.elegible())throw new ReglaNegocioException("La oferta dejó de ser elegible para esta colaboración.");
                if(r.invitacion()!=null&&!invitacionVigente(r,actor))throw new ReglaNegocioException("El instrumento de la invitación ya no está habilitado.");
                if(actual.organizacion()!=propia.organizacion()||actual.responsable()!=propia.responsable()||!camposRecurso.containsAll(actual.datosPermitidos().keySet()))
                    throw new IllegalStateException("La fuente de la parte no conserva su recurso/campos autorizados.");
                if(actual.versionEncargo()!=propia.version())
                    throw new ConflictoException("La versión del Encargo cambió al preparar su snapshot; revisa el contenido actual.");
                resultado.putAll(actual.datosPermitidos());
            }
        }
        resultado.putAll(declarados.deLaParte(s,lado,campos,hoy()));return new SnapshotParte(resultado,propia.version());
    }
    @Override public boolean revalidar(Recursos r,AlcanceColaboracion alcance,Actor actor) {
        exigirPartePropia(r,null,Accion.CONSULTAR,actor);validarAlcance(r,alcance,actor);
        if(!alcance.vigente(reloj.instant()))return false;
        if(r.invitacion()!=null)return invitacionVigente(r,actor);
        var oferta=network.resolverReferencia(r.ofertaRef(),actor);if(oferta.isEmpty())return false;
        var e=oferta.get();if(e.organizacion()!=r.organizacionEncargo()||e.idEncargo()!=r.idEncargo()
                ||e.responsable()!=r.responsableEncargo()||e.versionProyeccion()!=r.versionOferta())return false;
        var d=contextoDemanda(r.organizacionDemanda(),r.idDemanda());
        return Objects.equals(d.getResponsable(),r.responsableDemanda())&&"A".equals(d.getEstado())
                &&(d.getVigenteDesde()==null||!hoy().isBefore(d.getVigenteDesde()))
                &&(d.getVigenteHasta()==null||!hoy().isAfter(d.getVigenteHasta()));
    }
    @Override public boolean revalidarSnapshots(Recursos r,AlcanceColaboracion alcance,Map<LadoColaboracion,SnapshotParte> snapshots,
            Map<LadoColaboracion,ReferenciaParteConcedida> proofs,Actor actor) {
        exigirPartePropia(r,null,Accion.CONSULTAR,actor);
        if(r.invitacion()!=null)return revalidarInvitacionConcedida(r,alcance,snapshots,proofs,actor);
        if(snapshots==null||proofs==null||snapshots.size()!=2||proofs.size()!=2)throw new IllegalStateException("Faltan las dos pruebas de decisión seleccionadas.");
        String concesion=null,solicitudRef=null;Long versionConcesion=null;
        for(var lado:r.lados()) {
            var p=proofs.get(lado);var s=snapshots.get(lado);validarPrueba(r,alcance,lado,p,s);
            if(concesion==null){concesion=p.concesion();solicitudRef=p.solicitud();versionConcesion=p.versionConcesion();}
            else if(!concesion.equals(p.concesion())||!solicitudRef.equals(p.solicitud())||versionConcesion!=p.versionConcesion())throw new IllegalStateException("Las partes pertenecen a concesiones distintas.");
            Map<String,Object> subset=new LinkedHashMap<>();s.campos().forEach((k,v)->{if(p.campos().contains(k))subset.put(k,v);});
            String esperada=Idempotencia.huellaExacta(json.objetoCanonico(subset));
            if(lado==LadoColaboracion.DEMANDA) {
                var actual=demandas.verificarCamposColaboracion(p,actor);
                if(actual==null)throw new IllegalStateException("No se recibió la verificación de Demanda.");
                if(actual.idDemanda()!=r.idDemanda()||actual.responsableActual()!=r.responsableDemanda()||!"A".equals(actual.estado())
                        ||!Objects.equals(esperada,actual.huellaSubset())||actual.vigenteDesde()!=null&&hoy().isBefore(actual.vigenteDesde())
                        ||actual.vigenteHasta()!=null&&hoy().isAfter(actual.vigenteHasta()))return false;
            } else {
                var actual=ofertaConcedida.verificarCamposColaboracion(p,actor);
                if(actual==null)throw new IllegalStateException("No se recibió la verificación de oferta.");
                if(!actual.elegible()) {
                    if(actual.causas()==null||actual.causas().isEmpty())throw new IllegalStateException("La fuente no indicó la causa de inelegibilidad de oferta.");
                    return false;
                }
                if(actual.huellaSubset()==null)throw new IllegalStateException("La fuente elegible no produjo la huella del subset.");
                if(actual.organizacion()!=r.organizacionEncargo()||actual.idEncargo()!=r.idEncargo()||actual.responsableActual()!=r.responsableEncargo()
                        ||actual.versionActual()<p.versionRecursoAprobada()
                        ||!Objects.equals(esperada,actual.huellaSubset()))return false;
            }
            var solicitud=solicitud(p.solicitud(),actor);
            Set<String> adicionales=new HashSet<>(alcance.campos().getOrDefault(lado,Set.of()));adicionales.retainAll(PoliticaColaboracionBilateral.IDENTIDAD_Y_CANAL);
            Map<String,Object> anteriores=new LinkedHashMap<>();s.campos().forEach((k,v)->{if(adicionales.contains(k))anteriores.put(k,v);});
            if(!Idempotencia.huellaExacta(json.objetoCanonico(anteriores)).equals(Idempotencia.huellaExacta(json.objetoCanonico(declarados.deLaParte(solicitud,lado,adicionales,hoy())))))return false;
        }
        return true;
    }
    private void validarPrueba(Recursos r,AlcanceColaboracion a,LadoColaboracion lado,ReferenciaParteConcedida p,SnapshotParte s) {
        long id=lado==LadoColaboracion.ENCARGO?r.idEncargo():r.recursoContraparte(),responsable=lado==LadoColaboracion.ENCARGO?r.responsableEncargo():r.responsableContraparte();
        if(p==null||s==null||p.lado()!=lado||p.organizacion()!=org(r,lado)||p.idRecurso()!=id||p.responsable()!=responsable
                ||p.idDecision()<=0||p.versionRecursoAprobada()!=s.versionRelevante()||!p.finalidad().equals(a.finalidad())
                ||!p.desde().equals(a.desde())||!p.hasta().equals(a.hasta())
                ||!p.campos().equals(PoliticaColaboracionBilateral.camposDelRecurso(lado,a.campos().getOrDefault(lado,Set.of())))
                ||!p.huellaSnapshot().equals(Idempotencia.huellaExacta(json.objetoCanonico(s.campos()))))throw new IllegalStateException("La prueba no corresponde al snapshot y alcance concedidos.");
    }
    private RequerimientoClienteRepository.ContextoDemanda contextoDemanda(long org,long id){return referenciasDemanda.contexto(org,id).orElseThrow(()->new NoEncontradoException("Demanda"));}
    private boolean individual(long org){String modalidad=identidades.modalidad(org).orElseThrow(AccesoNoAutorizadoException::new);if(!Set.of("INDIVIDUAL","EMPRESARIAL").contains(modalidad))throw new AccesoNoAutorizadoException();return "INDIVIDUAL".equals(modalidad);}
    private SolicitudColaboracion solicitud(String ref,Actor actor){try{return solicitudes.leer(UUID.fromString(ref),actor.idOrganizacion()).orElseThrow(()->new NoEncontradoException("Colaboración"));}catch(IllegalArgumentException e){throw new NoEncontradoException("Colaboración");}}
    private LocalDate hoy(){return reloj.instant().atZone(ZoneId.of("America/Lima")).toLocalDate();}
    private static long org(Recursos r,LadoColaboracion lado){return lado==LadoColaboracion.ENCARGO?r.organizacionEncargo():r.organizacionContraparte();}
    @Override public Recursos resolverInvitacion(ColaboracionService.DatosInvitacion d,Actor actor){
        Alcances.exigirOperador(actor);if(!actor.esAgente())throw new AccesoNoAutorizadoException();
        var c=acceso.contexto(d.encargo(),actor);if(c.getResponsable()!=actor.idRolOperativo())throw new AccesoNoAutorizadoException();
        if(c.getVersion()!=d.versionEncargo())throw new ConflictoException("Cambió el Encargo que se desea invitar.");
        if(d.organizacionInvitada()==actor.idOrganizacion())throw new ReglaNegocioException("Esta invitación identifica otra organización; L1 conserva su procedimiento.");
        var miembro=miembro(d.organizacionInvitada(),d.responsableInvitado());
        if(d.pacto()==null||d.pacto().encargo()!=d.encargo()||!Objects.equals(c.getRevisionAplicable(),d.pacto().revisionAprobatoria()))throw new ReglaNegocioException("Identifique la revisión vigente del instrumento que permite colaborar.");
        pactos.consultarPactoFirmado(d.pacto(),actor);encargos.exigirHabilitado(d.encargo(),actor);
        return new Recursos(null,null,null,null,actor.idOrganizacion(),d.encargo(),c.getResponsable(),c.getVersion(),null,null,
                new Invitacion(d.organizacionInvitada(),d.responsableInvitado(),miembro.getVersion(),d.pacto().revisionAprobatoria(),d.clausulaPermiso(),null));
    }
    private IdentidadColaboracionRepository.MiembroInvitado miembro(long org,long rol){return identidades.miembroInvitado(org,rol,hoy()).orElseThrow(()->new NoEncontradoException("Destinatario de colaboración"));}
    private ParteActual parteInvitacion(Recursos r,LadoColaboracion lado,Accion accion,Actor actor){
        if(lado==null){if(accion!=Accion.CONSULTAR)throw new ReglaNegocioException("Indique la parte de la invitación.");lado=actor.idOrganizacion()==r.organizacionEncargo()?LadoColaboracion.ENCARGO:LadoColaboracion.INVITADO;}
        if(!r.lados().contains(lado)||org(r,lado)!=actor.idOrganizacion())throw new AccesoNoAutorizadoException();
        long responsable;long version;Long broker;boolean individual=individual(actor.idOrganizacion());
        if(lado==LadoColaboracion.INVITADO){var m=miembro(actor.idOrganizacion(),r.responsableContraparte());responsable=m.getResponsable();version=m.getVersion();broker=m.getBroker();}
        else {var c=acceso.contexto(r.idEncargo(),actor);var a=fuente.consultarAutoridadPropia(r.idEncargo(),actor);responsable=c.getResponsable();version=c.getVersion();broker=a.brokerActual();}
        if(!alcances.alcanza(actor,responsable))throw new AccesoNoAutorizadoException();
        if(accion==Accion.SOLICITAR&&(!actor.esAgente()||actor.idRolOperativo()!=responsable))throw new AccesoNoAutorizadoException();
        if((accion==Accion.DECIDIR||accion==Accion.REVOCAR)&&!(individual&&actor.esAgente()&&actor.idRolOperativo()==responsable||!individual&&actor.esBroker()&&Objects.equals(broker,actor.idRolOperativo())))throw new AccesoNoAutorizadoException();
        return new ParteActual(actor.idOrganizacion(),responsable,version,individual,lado);
    }
    @Override public boolean autoridadDecisionVigente(Recursos r,LadoColaboracion lado,long organizacion,long rol){
        var contexto=identidades.actorVigente(organizacion,rol,hoy());if(contexto.isEmpty())return false;
        var a=contexto.get();try{exigirPartePropia(r,lado,Accion.DECIDIR,new Actor(organizacion,a.getPersona(),rol,a.getRol()));return true;}
        catch(AccesoNoAutorizadoException|NoEncontradoException e){return false;}
    }
    private boolean invitacionVigente(Recursos r,Actor actor){
        var m=identidades.miembroInvitado(r.organizacionContraparte(),r.responsableContraparte(),hoy());
        if(m.isEmpty()||m.get().getVersion()!=r.versionContraparte())return false;
        var c=referenciasEncargo.contextoActual(r.organizacionEncargo(),r.idEncargo()).orElse(null);
        if(c==null||c.getResponsable()!=r.responsableEncargo()||!Objects.equals(c.getRevisionAplicable(),r.invitacion().revisionPermiso()))return false;
        if(actor.idOrganizacion()==r.organizacionEncargo())return encargos.consultarVigencia(r.idEncargo(),hoy(),actor).habilitado();
        return encargos.ofertaDeInvitacion(permisosInvitacion.integridad(r.invitacion().solicitud(),actor)).habilitado();
    }
    private boolean revalidarInvitacionConcedida(Recursos r,AlcanceColaboracion alcance,Map<LadoColaboracion,SnapshotParte> snapshots,
            Map<LadoColaboracion,ReferenciaParteConcedida> proofs,Actor actor){
        if(snapshots==null||proofs==null||!snapshots.keySet().equals(r.lados())||!proofs.keySet().equals(r.lados()))throw new IllegalStateException("Faltan las dos decisiones de la invitación.");
        if(!invitacionVigente(r,actor)||!alcance.vigente(reloj.instant()))return false;
        String concesion=null,solicitudRef=null;Long version=null;
        for(var lado:r.lados()){
            var p=proofs.get(lado);var s=snapshots.get(lado);validarPrueba(r,alcance,lado,p,s);
            if(concesion==null){concesion=p.concesion();solicitudRef=p.solicitud();version=p.versionConcesion();}
            else if(!concesion.equals(p.concesion())||!solicitudRef.equals(p.solicitud())||!version.equals(p.versionConcesion()))throw new IllegalStateException("Decisiones de concesiones distintas.");
        }
        for(var lado:r.lados()){
            var p=proofs.get(lado);var s=snapshots.get(lado);
            var solicitud=solicitud(p.solicitud(),actor);if(!ProyeccionNetwork.recursos(solicitud).equals(r))throw new IllegalStateException("La prueba de invitación identifica otros recursos.");
            Map<String,Object> actuales=new LinkedHashMap<>();
            Set<String> seleccion=alcance.campos().getOrDefault(lado,Set.of());
            // Sólo después de verificar los dos proofs y su solicitud. No se entrega expediente ni se ejecuta una actuación del titular.
            if(lado==LadoColaboracion.ENCARGO){var campos=PoliticaColaboracionBilateral.camposDelRecurso(lado,seleccion);if(!campos.isEmpty())actuales.putAll(fuente.camposDeInvitacion(permisosInvitacion.campos(solicitudRef,campos,actor)));}
            actuales.putAll(declarados.deLaParte(solicitud,lado,seleccion,hoy()));
            if(!json.objetoCanonico(actuales).equals(json.objetoCanonico(s.campos())))return false;
        }
        return true;
    }
}
