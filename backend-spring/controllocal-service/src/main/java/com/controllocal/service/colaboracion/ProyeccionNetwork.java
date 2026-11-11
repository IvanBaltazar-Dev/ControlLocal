package com.controllocal.service.colaboracion;

import com.controllocal.domain.colaboracion.*;
import com.controllocal.service.Actor;
import java.util.*;

/** Puerto interno único. Su implementación no presume política/participación Network aprobadas. */
public interface ProyeccionNetwork {
    enum Accion { SOLICITAR, CONSULTAR, DECIDIR, REVOCAR }
    /** Identidades internas; nunca devolver este valor como tarjeta o resultado al cliente. */
    record Invitacion(long organizacion,long responsable,long version, long revisionPermiso,String clausulaPermiso,String solicitud) { }
    record Recursos(Long organizacionDemanda,Long idDemanda,Long responsableDemanda,Long versionDemanda,
                    long organizacionEncargo,long idEncargo,long responsableEncargo,long versionEncargo,
                    String ofertaRef,Long versionOferta,Invitacion invitacion) {
        public Recursos(long org,long demanda,long responsable,long version,long orgE,long encargo,long responsableE,long versionE,String oferta,long versionOferta){this(org,demanda,responsable,version,orgE,encargo,responsableE,versionE,oferta,versionOferta,null);}
        public LadoColaboracion ladoContraparte(){return invitacion==null?LadoColaboracion.DEMANDA:LadoColaboracion.INVITADO;}
        public Set<LadoColaboracion> lados(){return Set.of(ladoContraparte(),LadoColaboracion.ENCARGO);}
        public long organizacionContraparte(){return invitacion==null?organizacionDemanda:invitacion.organizacion();}
        public long responsableContraparte(){return invitacion==null?responsableDemanda:invitacion.responsable();}
        public long versionContraparte(){return invitacion==null?versionDemanda:invitacion.version();}
        public long recursoContraparte(){return invitacion==null?idDemanda:invitacion.responsable();}
    }
    static Recursos recursos(SolicitudColaboracion s) {
        if(s.esInvitacion())return new Recursos(null,null,null,null,s.getOrganizacionEncargo(),s.getIdEncargo(),s.getResponsableEncargo(),s.getVersionEncargo(),null,null,
                new Invitacion(s.getOrganizacionId(),s.responsableContraparte(),s.versionContraparte(),s.getRevisionPermiso(),s.getClausulaPermiso(),s.getReferencia().toString()));
        return new Recursos(s.getOrganizacionId(),s.getIdDemanda(),s.getResponsableDemanda(),s.getVersionDemanda(),
                s.getOrganizacionEncargo(),s.getIdEncargo(),s.getResponsableEncargo(),s.getVersionEncargo(),s.getOfertaRef(),s.getVersionOferta(),null);
    }
    record ParteActual(long organizacion,long responsable,long version,boolean individual,LadoColaboracion lado) { }
    record SnapshotParte(Map<String,Object> campos,long versionRelevante) {
        public SnapshotParte {campos=campos==null?Map.of():Collections.unmodifiableMap(new LinkedHashMap<>(campos));}
    }
    Recursos resolverOferta(String ofertaRef,long idDemanda,Actor actor);
    Recursos resolverInvitacion(ColaboracionService.DatosInvitacion datos,Actor actor);
    boolean autoridadDecisionVigente(Recursos recursos,LadoColaboracion lado,long organizacion,long rol);
    /** Delega a autoridad canónica de SU recurso; lado null sólo CONSULTAR, resolviendo autoridad real. */
    ParteActual exigirPartePropia(Recursos recursos,LadoColaboracion lado,Accion accion,Actor actor);
    /** Rechaza finalidad/acción/campo no admitidos expresamente por la política/base actuales. */
    void validarAlcance(Recursos recursos,AlcanceColaboracion alcance,Actor actor);
    /** Sólo SU lado, en el contexto real del decisor; sin presupuesto privado, criterios económicos, notas o documentos. */
    SnapshotParte snapshotPartePropia(Recursos recursos,String solicitudRef,LadoColaboracion lado,AlcanceColaboracion alcance,Actor actorRealDecisor);
    /** Revalida participación, elegibilidad y proyección actuales; el servicio ya verificó la concesión. */
    boolean revalidar(Recursos recursos,AlcanceColaboracion alcance,Actor actor);
    /** Sólo snapshots de las dos decisiones legítimas seleccionadas por la concesión actual. */
    boolean revalidarSnapshots(Recursos recursos,AlcanceColaboracion alcance,
                           Map<LadoColaboracion,SnapshotParte> snapshots,
                           Map<LadoColaboracion,ReferenciaParteConcedida> referenciasConcedidas,Actor actor);
}
