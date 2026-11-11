package com.controllocal.service.colaboracion;

import com.controllocal.domain.colaboracion.LadoColaboracion;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.Collections;

/** Prueba interna construida desde la concesión y su decisión persistida; nunca entrada HTTP. */
public final class ReferenciaParteConcedida {
    private final String concesion,solicitud,finalidad,huellaSnapshot;
    private final long versionConcesion,versionAlcance,idDecision,organizacion,idRecurso,responsable,versionRecursoAprobada;
    private final LadoColaboracion lado;
    private final Set<String> campos,clavesFisicas;
    private final Instant desde,hasta;
    ReferenciaParteConcedida(String concesion,long versionConcesion,String solicitud,long versionAlcance,
            long idDecision,LadoColaboracion lado,long organizacion,long idRecurso,long responsable,
            long versionRecursoAprobada,Set<String> campos,Set<String> clavesFisicas,String finalidad,
            Instant desde,Instant hasta,String huellaSnapshot) {
        this.concesion=concesion;this.versionConcesion=versionConcesion;this.solicitud=solicitud;
        this.versionAlcance=versionAlcance;this.idDecision=idDecision;this.lado=lado;this.organizacion=organizacion;
        this.idRecurso=idRecurso;this.responsable=responsable;this.versionRecursoAprobada=versionRecursoAprobada;
        this.campos=campos==null?Set.of():Collections.unmodifiableSet(new TreeSet<>(campos));
        this.clavesFisicas=clavesFisicas==null?Set.of():Collections.unmodifiableSet(new TreeSet<>(clavesFisicas));
        this.finalidad=finalidad;this.desde=desde;this.hasta=hasta;this.huellaSnapshot=huellaSnapshot;
    }
    public String concesion(){return concesion;} public long versionConcesion(){return versionConcesion;}
    public String solicitud(){return solicitud;} public long versionAlcance(){return versionAlcance;}
    public long idDecision(){return idDecision;} public LadoColaboracion lado(){return lado;}
    public long organizacion(){return organizacion;} public long idRecurso(){return idRecurso;}
    public long responsable(){return responsable;} public long versionRecursoAprobada(){return versionRecursoAprobada;}
    public Set<String> campos(){return campos;} public Set<String> clavesFisicas(){return clavesFisicas;}
    public String finalidad(){return finalidad;} public Instant desde(){return desde;} public Instant hasta(){return hasta;}
    public String huellaSnapshot(){return huellaSnapshot;}
}
