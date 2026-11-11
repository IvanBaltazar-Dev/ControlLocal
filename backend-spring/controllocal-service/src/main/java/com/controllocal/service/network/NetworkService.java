package com.controllocal.service.network;

import com.controllocal.domain.auditoria.EventoDominio;

import com.controllocal.service.Actor;
import com.controllocal.service.actuacion.ActuacionesService.ContextoSesion;
import com.controllocal.service.soporte.Procedencia;
import java.time.Instant;
import java.util.*;

/** Aprobación humana 28/10: participación granular por Encargo; nunca una habilitación de Demanda. */
public interface NetworkService {
    enum OrigenCambio { ENCARGO, PROPIEDAD }
    enum Cambio { ACTIVAR, MODIFICAR, DESACTIVAR, REACTIVAR }
    enum Decision { APROBAR, OBSERVAR, RECHAZAR }
    record DatosSolicitud(Long idEncargo,Cambio accion,Set<String> campos,String finalidad,Instant desde,Instant hasta,
                          String motivo,Long versionParticipacion,String huellaDatos,String huellaAutoridad) { }
    /** versionParticipacion es la versión ACTUAL observada en Vista.version/Capacidad.version; no la histórica de la solicitud. */
    record DatosDecision(String solicitud,Decision decision,String motivo,Long versionSolicitud,
                         Long versionParticipacion,String huellaDatos,String huellaAutoridad) { }
    record DatosDesactivacion(Long idEncargo,String motivo,Long versionParticipacion) { }
    record Diferencia(String campo,String cambio,Object anterior,Object propuesto) { }
    record Vista(long idEncargo,long version,String estadoBase,String estado,String solicitudPendiente,
                 String versionAprobada,Long numeroVersion,Set<String> causas,boolean elegible) { }
    record Preparacion(Vista participacion,FuenteDeNetwork.FuentePropia fuente,
                        Map<String,Object> ultimaAprobada,List<Diferencia> diferencias) { }
    /** versionParticipacion conserva la versión PRESENTADA al solicitar; no se actualiza para completar el CAS de una decisión. */
    record SolicitudVista(String referencia,long version,long idEncargo,String accion,String estado,
                           long versionParticipacion,Set<String> campos,String finalidad,Instant desde,Instant hasta,
                           Map<String,Object> datosPropuestos,List<Diferencia> diferencias,String motivo,
                           String huellaDatos,String huellaAutoridad) { }
    record Resultado(long idEncargo,long versionParticipacion,String solicitud,Long versionSolicitud,
                      String estadoSolicitud,String estadoParticipacion,String versionAprobada,Long numeroVersion,
                      String accion,Instant registradoEn) { }
    record Capacidad(long idEncargo,long version,String estado,String solicitud,Long versionSolicitud,
                      String estadoSolicitud,boolean elegible,Set<String> causas,boolean consultar,
                      boolean solicitar,boolean decidir,boolean desactivarDirectamente,
                      Set<Cambio> cambiosSolicitables,boolean autoridadSolicitar,
                      boolean autoridadDecidir,boolean autoridadDesactivarDirectamente) { }
    /** Sólo para el cargador Core: el egreso público no serializa este record con IDs internos. */
    record VersionParaEntrega(FuenteDeNetwork.ReferenciaAutorizada referencia,
                              Map<String,Object> datos,Instant desde,Instant hasta,
                              com.controllocal.service.matching.ContratoMatching.ConfirmacionDisponibilidad disponibilidad) { }

    Vista consultar(long idEncargo,Actor actor);
    SolicitudVista consultarSolicitud(String referencia,Actor actor);
    Preparacion previsualizar(long idEncargo,Set<String> campos,Actor actor);
    Capacidad capacidadSobre(long idEncargo,Actor actor);
    Capacidad capacidadSobreSolicitud(String solicitud,Actor actor);
    DatosSolicitud validarSolicitud(DatosSolicitud datos,Actor actor);
    DatosDecision validarDecision(DatosDecision datos,Actor actor);
    DatosDesactivacion validarDesactivacionDirecta(DatosDesactivacion datos,Actor actor);
    Resultado solicitar(DatosSolicitud datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Resultado decidir(DatosDecision datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Resultado desactivarDirectamente(DatosDesactivacion datos,Actor actor,ContextoSesion sesion,String clave,Procedencia procedencia);
    Optional<Resultado> consultarResultado(String capacidad,String clave,Actor actor);
    /** Core únicamente: resolver universo/referencia autorizada sin recuperar datos de V1. */
    List<FuenteDeNetwork.ReferenciaAutorizada> referenciasParaConsulta(Actor actorReal);
    /** Selecciona oferta estable; una referencia de versión nunca funciona como alias. */
    Optional<FuenteDeNetwork.ReferenciaAutorizada> resolverReferencia(String ofertaRef,Actor actorReal);
    List<VersionParaEntrega> revalidarParaEntrega(Collection<String> ofertasRef,Actor actorReal);
    /** Hook interno de un hecho Core persistido en la misma TX; no concede lecturas al editor. */
    void reconciliarCambioCore(OrigenCambio origen,long idRecurso,Actor actorReal,EventoDominio eventoCausal);
}
