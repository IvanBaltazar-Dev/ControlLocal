package com.controllocal.service.colaboracion;

import com.controllocal.domain.colaboracion.*;
import com.controllocal.persistence.repositorio.*;
import com.controllocal.service.Actor;
import com.controllocal.service.excepcion.*;
import com.controllocal.service.soporte.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Permiso interno derivado de decisiones existentes. Conserva el actor real y el recurso fuente por separado. */
@Component
public class AccesoFuenteDeInvitacion {
    public static final class Permiso {
        private final long organizacion,encargo,revision,responsable;private final Actor receptor;private final Set<String> campos;
        private Permiso(long o,long e,long r,long responsable,Actor a,Set<String> c){organizacion=o;encargo=e;revision=r;this.responsable=responsable;receptor=a;campos=Set.copyOf(c);}
        public long organizacion(){return organizacion;}public long encargo(){return encargo;}public long revision(){return revision;}
        public Actor receptor(){return receptor;}public Set<String> campos(){return campos;}
        public long responsable(){return responsable;}
    }
    private final SolicitudColaboracionRepository solicitudes;private final ConcesionColaboracionRepository concesiones;
    private final DecisionColaboracionRepository decisiones;private final IdentidadColaboracionRepository identidades;
    private final Alcances alcances;private final Documentos json;
    public AccesoFuenteDeInvitacion(SolicitudColaboracionRepository s,ConcesionColaboracionRepository c,DecisionColaboracionRepository d,
            IdentidadColaboracionRepository i,Alcances a,Documentos j){solicitudes=s;concesiones=c;decisiones=d;identidades=i;alcances=a;json=j;}
    @Transactional(readOnly=true)
    public Permiso integridad(String referencia,Actor actor){return permiso(referencia,Set.of(),actor,false);}
    @Transactional(readOnly=true)
    public Permiso campos(String referencia,Set<String> campos,Actor actor){return permiso(referencia,campos,actor,true);}
    private Permiso permiso(String referencia,Set<String> campos,Actor actor,boolean entrega){
        Alcances.exigirOperador(actor);UUID ref;try{ref=UUID.fromString(referencia);}catch(RuntimeException e){throw new NoEncontradoException("Invitación");}
        var s=solicitudes.leer(ref,actor.idOrganizacion()).filter(SolicitudColaboracion::esInvitacion).orElseThrow(()->new NoEncontradoException("Invitación"));
        boolean origen=actor.idOrganizacion()==s.getOrganizacionEncargo();long responsable=origen?s.getResponsableEncargo():s.responsableContraparte();
        if(!alcances.alcanza(actor,responsable)||identidades.actorVigente(actor.idOrganizacion(),actor.idRolOperativo(),hoy()).isEmpty())throw new AccesoNoAutorizadoException();
        if(!origen&&s.getDecisionEncargo()==null)throw new NoEncontradoException("Invitación");
        if(entrega){
            var c=concesiones.deSolicitud(s.getId(),actor.idOrganizacion()).orElseThrow(()->new AccesoNoAutorizadoException());
            var a=json.comoObjeto(c.getAlcance(),AlcanceColaboracion.class);
            if(c.getRevocadaEn()!=null||!a.vigente(Instant.now())||!a.campos().getOrDefault(LadoColaboracion.ENCARGO,Set.of()).containsAll(campos))throw new AccesoNoAutorizadoException();
            var d=decisiones.actual(c.decisionContraparte(),s.getId(),actor.idOrganizacion()).orElseThrow(AccesoNoAutorizadoException::new);
            var e=decisiones.actual(c.getDecisionEncargo(),s.getId(),actor.idOrganizacion()).orElseThrow(AccesoNoAutorizadoException::new);
            if(d.getLado()!=LadoColaboracion.INVITADO||e.getLado()!=LadoColaboracion.ENCARGO||d.getAlcance()==null||e.getAlcance()==null
                    ||d.getVersionAlcance()!=c.getVersionAlcance()||e.getVersionAlcance()!=c.getVersionAlcance()
                    ||!Set.of("ACEPTAR","LIMITAR").contains(d.getDecision())||!Set.of("ACEPTAR","LIMITAR").contains(e.getDecision()))throw new AccesoNoAutorizadoException();
            var comun=json.comoObjeto(d.getAlcance(),AlcanceColaboracion.class).interseccion(json.comoObjeto(e.getAlcance(),AlcanceColaboracion.class));
            if(comun.isEmpty()||!a.equals(comun.get())||!Objects.equals(s.decisionContraparte(),d.getId())||!Objects.equals(s.getDecisionEncargo(),e.getId()))throw new AccesoNoAutorizadoException();
        }
        return new Permiso(s.getOrganizacionEncargo(),s.getIdEncargo(),s.getRevisionPermiso(),s.getResponsableEncargo(),actor,campos);
    }
    private static LocalDate hoy(){return LocalDate.now(ZoneId.of("America/Lima"));}
}
