package com.controllocal.service.actuacion;

import com.controllocal.service.Actor;
import com.controllocal.service.soporte.Procedencia;
import java.util.Map;

/** UC-K-06/07: propuesta inmutable, revisión humana y resultado durable del mismo comando. */
public interface ActuacionesService {
    record ContextoSesion(String identidad, long expiraEnEpochSeconds, String referenciaSesion, long revisionContexto) {
        /** Una sola identidad canónica para Web, coordinación y comprobación del efecto. */
        public static ContextoSesion de(com.controllocal.service.ContextosService.SesionAccesoDTO s) {
            var c=s.activo();
            if(c==null) throw new com.controllocal.service.excepcion.AccesoNoAutorizadoException();
            String material=String.join("\u0000","BROX-SESION-ACTUACION-2",Long.toString(c.idOrganizacion()),
                    Long.toString(c.idPersona()),Long.toString(c.idRolOperativo()),c.rol(),
                    s.emitidaEn().toString(),s.venceEn().toString(),s.referencia(),Long.toString(s.revision()));
            return new ContextoSesion(com.controllocal.service.soporte.Idempotencia.huellaExacta(material),
                    s.venceEn().getEpochSecond(),s.referencia(),s.revision());
        }
    }
    record Propuesta(String referencia, String capacidad, long version, String huella,
                     long caducaEnEpochSeconds, Map<String, Object> contenido) { }
    record Resultado(String referencia, String estado, Propuesta propuesta,
                     Map<String, Object> resultado, String motivo) { }
    /** Sólo se entrega por la superficie humana autenticada, fuera del puerto de herramientas. */
    record Revision(Propuesta propuesta, String pruebaRevision) { }
    record Confirmacion(long version, String huella, String pruebaRevision, String eventoHumano) { }

    Propuesta prepararCaptura(Actor actor, long idBorrador, ContextoSesion sesion, Procedencia procedencia);
    Propuesta prepararCaptura(Actor actor, long idBorrador, ContextoSesion sesion, Procedencia procedencia,
                             ComandosAutomaticosService.CorrelacionAutomatico correlacion,
                             Map<String, Object> datosEsperados);
    Propuesta prepararOrigen(Actor actor, com.controllocal.service.UnidadRelacionadaService.DatosOrigen datos,
                            ContextoSesion sesion, Procedencia procedencia);
    Propuesta prepararFinDemanda(Actor actor,com.controllocal.service.RequerimientoService.FinDemanda datos,
                                ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararSolicitudColaboracion(Actor actor,com.controllocal.service.colaboracion.ColaboracionService.DatosSolicitud datos,
                                          ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararInvitacionColaboracion(Actor actor,com.controllocal.service.colaboracion.ColaboracionService.DatosInvitacion datos,
                                          ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararDecisionColaboracion(Actor actor,com.controllocal.service.colaboracion.ColaboracionService.DatosDecision datos,
                                         ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararRevocacionColaboracion(Actor actor,com.controllocal.service.colaboracion.ColaboracionService.DatosRevocacion datos,
                                           ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararCanalColaboracion(Actor actor,com.controllocal.service.colaboracion.CanalesColaboracionService.DatosDeclaracion datos,
                                       ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararSolicitudNetwork(Actor actor,com.controllocal.service.network.NetworkService.DatosSolicitud datos,
                                      ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararDecisionNetwork(Actor actor,com.controllocal.service.network.NetworkService.DatosDecision datos,
                                     ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararDesactivacionNetwork(Actor actor,com.controllocal.service.network.NetworkService.DatosDesactivacion datos,
                                          ContextoSesion sesion,Procedencia procedencia);
    /** Identifica el efecto durable del actor/tenant; no lee cuerpo ni acepta una capacidad del cliente. */
    Propuesta prepararAperturaOportunidad(Actor actor,com.controllocal.service.OportunidadService.DatosOportunidad datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararRectificacionInteraccion(Actor actor,com.controllocal.service.InteraccionService.Rectificacion datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararRectificacionComunicacionReporte(Actor actor,com.controllocal.service.ReportePropietarioService.Rectificacion datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararEstablecimientoMetas(Actor actor,com.controllocal.service.MetaComercialService.Establecer datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararDecisionMeta(Actor actor,com.controllocal.service.MetaComercialService.Decision datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararNoContinuidadOportunidad(Actor actor,com.controllocal.service.OportunidadService.NoContinuidad datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararCancelacionVisita(Actor actor,com.controllocal.service.VisitaService.Cancelacion datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararRegistroOferta(Actor actor,com.controllocal.service.OfertaService.DatosRegistroOferta datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararContraoferta(Actor actor,com.controllocal.service.OfertaService.DatosContraoferta datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararAceptacionOferta(Actor actor,com.controllocal.service.OfertaService.DatosAceptacion datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararRechazoOferta(Actor actor,com.controllocal.service.OfertaService.DatosRechazo datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararRetiradaOferta(Actor actor,com.controllocal.service.OfertaService.DatosRetirada datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararDecisionEncargo(Actor actor,long peticion,
            com.controllocal.service.encargo.EncargoService.Decision decision,
            ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararPresentacionExpediente(Actor actor,com.controllocal.service.formalizacion.FormalizacionService.DatosPresentacion datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararEvaluacionExpediente(Actor actor,com.controllocal.service.formalizacion.FormalizacionService.DatosRevision datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararInformacionHito(Actor actor,com.controllocal.service.formalizacion.FormalizacionService.DatosInformacionHito datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararConfirmacionHito(Actor actor,com.controllocal.service.formalizacion.FormalizacionService.DatosConfirmacionHito datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararRectificacionHito(Actor actor,com.controllocal.service.formalizacion.FormalizacionService.DatosRectificacionHito datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararMovimientoComision(Actor actor,com.controllocal.service.ComisionService.DatosCobro datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararRepartoComision(Actor actor,com.controllocal.service.ComisionService.DatosReparto datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararReprogramacionCobro(Actor actor,com.controllocal.service.ComisionService.DatosReprogramacion datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararConclusionCobro(Actor actor,com.controllocal.service.ComisionService.DatosConclusion datos,ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararPublicacion(String capacidad,Actor actor,com.controllocal.service.PublicacionService.DatosCambio datos,
                                 ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararEntregaPieza(Actor actor,com.controllocal.service.documentos.PiezasComercialesService.Entrega datos,
                                 ContextoSesion sesion,Procedencia procedencia);
    Propuesta prepararDecisionBorradorAdenda(Actor actor,com.controllocal.service.encargo.EncargoService.ReferenciaAdenda referencia,
            com.controllocal.service.encargo.EncargoService.DecisionBorradorAdenda datos,ContextoSesion sesion,Procedencia procedencia);
    String capacidadDe(String referencia, Actor actor);
    Resultado consultar(Actor actor, String referencia, ContextoSesion sesion);
    /** Proyección de lectura y motivos del mismo guard de revisión/confirmación; nunca concede el efecto. */
    record CapacidadSobreActuacion(String capacidad,String etapa,String motivoRevision,String motivoConfirmacion) { }
    CapacidadSobreActuacion capacidadSobre(String referencia,Actor actor);
    Revision revisar(Actor actor, String referencia, ContextoSesion sesion);
    Resultado confirmar(Actor actor, String referencia, ContextoSesion sesion,
                        Confirmacion confirmacion, Procedencia procedencia);
}
