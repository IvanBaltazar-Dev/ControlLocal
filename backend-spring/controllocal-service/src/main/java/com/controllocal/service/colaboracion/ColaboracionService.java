package com.controllocal.service.colaboracion;

import com.controllocal.domain.colaboracion.*;
import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** RF035–037/AC095–098. Los efectos sólo son consumidores de confirmación humana canónica. */
public interface ColaboracionService {
    enum Decision { ACEPTAR, LIMITAR, RECHAZAR }
    record DatosSolicitud(Long idDemanda,Long versionDemanda,String ofertaRef,Long versionOferta,
                          AlcanceColaboracion alcance) { }
    record DatosInvitacion(long encargo,long versionEncargo,long organizacionInvitada,long responsableInvitado,
                          com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto pacto,String clausulaPermiso,AlcanceColaboracion alcance) { }
    record DatosDecision(String solicitud,LadoColaboracion parte,Decision decision,
                         AlcanceColaboracion alcance,String motivo,Long versionSolicitud,
                         Long versionAlcance,Long versionRecurso) { }
    record DatosRevocacion(String concesion,LadoColaboracion parte,AlcanceColaboracion alcanceEsperado,
                           String motivo,Long versionConcesion,Long versionAlcance,Long versionRecurso) { }
    record DecisionVisible(LadoColaboracion parte,String decision,String motivo,Instant registradaEn) { }
    record PermisoColaboracion(com.controllocal.service.encargo.PactosDeEncargo.ReferenciaPacto pacto,String clausulaDeclarada) { }
    record VistaDecision(DatosDecision datos,ProyeccionNetwork.SnapshotParte datosPropios,
                         @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) PermisoColaboracion permisoDelInstrumento) {
        public VistaDecision(DatosDecision datos,ProyeccionNetwork.SnapshotParte propios){this(datos,propios,null);}
    }
    record MetadatosAcuerdo(String solicitud,long version,String estado,long versionAlcance,
                            AlcanceColaboracion alcanceSolicitado,String concesion,Long versionConcesion,
                            AlcanceColaboracion alcanceConcedido,Instant revocadaEn,
                            ProyeccionNetwork.ParteActual parteActual) { }
    /** Sin IDs/identidades de contraparte. Alcance contiene nombres de campos, nunca sus valores. */
    record Acuerdo(String solicitud,long version,String estado,long versionAlcance,
                   AlcanceColaboracion alcanceSolicitado,List<DecisionVisible> decisiones,
                   String concesion,Long versionConcesion,AlcanceColaboracion alcanceConcedido,
                   boolean efectiva,Instant revocadaEn,Map<LadoColaboracion,Map<String,Object>> datosConcedidos) { }
    record Resultado(String solicitud,long versionSolicitud,String estado,String concesion,
                     Long versionConcesion,LadoColaboracion parte,String decision,
                     AlcanceColaboracion alcance,Instant registradoEn) { }

    DatosSolicitud validarSolicitud(DatosSolicitud datos,Actor actor);
    DatosInvitacion validarInvitacion(DatosInvitacion datos,Actor actor);
    List<Acuerdo> invitacionesRecibidas(Actor actor,int pagina,int tamano);
    List<Acuerdo> invitacionesOriginadas(Actor actor,int pagina,int tamano);
    DatosDecision validarDecision(DatosDecision datos,Actor actor);
    VistaDecision previsualizarDecision(DatosDecision datos,Actor actor);
    DatosRevocacion validarRevocacion(DatosRevocacion datos,Actor actor);
    Acuerdo consultarAcuerdo(String solicitud,Actor actor);
    MetadatosAcuerdo consultarMetadatos(String solicitud,Actor actor);
    MetadatosAcuerdo consultarMetadatosPorConcesion(String concesion,Actor actor);
    /** Selector interno de lectura para actuaciones ya preparadas; no exige otro CAS de escritura. */
    Acuerdo consultarPorConcesion(String concesion,Actor actor);
    Optional<Resultado> consultarResultado(String capacidad,String clave,Actor actor);
    Resultado solicitar(DatosSolicitud datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Resultado invitar(DatosInvitacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Resultado decidirPorParte(DatosDecision datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Resultado revocarPropia(DatosRevocacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
}
