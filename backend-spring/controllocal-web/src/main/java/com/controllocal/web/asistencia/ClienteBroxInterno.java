package com.controllocal.web.asistencia;

import com.controllocal.asistencia.brox.ClienteBrox;
import com.controllocal.asistencia.brox.ErrorBrox;
import com.controllocal.asistencia.brox.SesionBrox;
import com.controllocal.asistencia.brox.Traza;
import com.controllocal.service.Actor;
import com.controllocal.service.ClienteService;
import com.controllocal.service.CoincidenciaService;
import com.controllocal.service.FocoService;
import com.controllocal.service.PropiedadUniversalService;
import com.controllocal.service.PropietarioService;
import com.controllocal.service.actuacion.ActuacionesService;
import com.controllocal.service.actuacion.ComandosAutomaticosService;
import com.controllocal.service.actuacion.ComandosAutomaticosService.CorrelacionAutomatico;
import com.controllocal.service.capacidades.AdaptadoresDeCapacidad;
import com.controllocal.service.capacidades.CapacidadesService;
import com.controllocal.service.captura.MotorDeCaptura;
import com.controllocal.service.excepcion.AccesoNoAutorizadoException;
import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import com.controllocal.service.excepcion.ConflictoException;
import com.controllocal.service.excepcion.CredencialesInvalidasException;
import com.controllocal.service.excepcion.NoEncontradoException;
import com.controllocal.service.excepcion.ReglaNegocioException;
import com.controllocal.service.soporte.Procedencia;
import com.controllocal.web.almacen.GaleriaDeFotos;
import com.controllocal.web.dto.AsuntoDelBrokerResponse;
import com.controllocal.web.dto.CoincidenciasResponse;
import com.controllocal.web.dto.HallazgoResponse;
import com.controllocal.web.dto.PropiedadUniversalDtos.PropiedadResponse;
import com.controllocal.web.dto.PropietarioRequest;
import com.controllocal.web.dto.PropietarioResponse;
import com.controllocal.web.dto.TareaResponse;
import com.controllocal.web.seguridad.ContextoDeActuacion;
import com.controllocal.web.seguridad.SesionActual;
import com.controllocal.web.seguridad.RevalidacionDeSesion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * P-07: adaptador de aplicación interno. Reutiliza casos, proyecciones y política real de handlers.
 * No hace HTTP contra el propio Core, no recibe un JWT y no conoce repositorios o entidades.
 */
@Component
public class ClienteBroxInterno implements ClienteBrox {
    private final CapacidadesService capacidades;
    private final AdaptadoresDeCapacidad adaptadores;
    private final MotorDeCaptura captura;
    private final PropiedadUniversalService propiedades;
    private final ClienteService clientes;
    private final PropietarioService propietarios;
    private final CoincidenciaService coincidencias;
    private final FocoService focos;
    private final com.controllocal.service.OportunidadService oportunidades;
    private final com.controllocal.service.VisitaService visitas;
    private final com.controllocal.service.InteraccionService interacciones;
    private final com.controllocal.service.ProspeccionService prospecciones;
    private final com.controllocal.service.actividad.CompromisosDeSeguimiento compromisos;
    private final com.controllocal.service.actividad.InformesC7 informesC7;
    private final com.controllocal.service.actividad.ProgramasPersonales programas;
    @org.springframework.beans.factory.annotation.Autowired
    private com.controllocal.service.ReportePropietarioService reportesPropietario;
    @org.springframework.beans.factory.annotation.Autowired private com.controllocal.service.MetaComercialService metasComerciales;
    @org.springframework.beans.factory.annotation.Autowired private com.controllocal.service.RendimientoComercialService rendimientoComercial;
    private final ActuacionesService actuaciones;
    private final ComandosAutomaticosService automaticos;
    private final GaleriaDeFotos galeria;
    private final ObjectMapper json;
    private final RevalidacionDeSesion sesiones;
    private final com.controllocal.service.PublicacionService publicaciones;
    private final com.controllocal.service.actuacion.IdentidadComandoAutomatico identidades;
    private final com.controllocal.service.documentos.PiezasComercialesService piezas;
    private final com.controllocal.service.documentos.FotosComercialesService fotosComerciales;
    private final com.controllocal.service.materialia.MaterialIaService materialIa;

    public ClienteBroxInterno(CapacidadesService capacidades, AdaptadoresDeCapacidad adaptadores,
                              MotorDeCaptura captura, PropiedadUniversalService propiedades,
                              ClienteService clientes, PropietarioService propietarios,
                              CoincidenciaService coincidencias,
                              FocoService focos, ActuacionesService actuaciones,
                              ComandosAutomaticosService automaticos,
                              GaleriaDeFotos galeria, ObjectMapper json, RevalidacionDeSesion sesiones,
                              com.controllocal.service.OportunidadService oportunidades, com.controllocal.service.VisitaService visitas,
                              com.controllocal.service.InteraccionService interacciones,
                              com.controllocal.service.ProspeccionService prospecciones,
                              com.controllocal.service.PublicacionService publicaciones,
                              com.controllocal.service.actuacion.IdentidadComandoAutomatico identidades,
                              com.controllocal.service.documentos.PiezasComercialesService piezas,
                              com.controllocal.service.documentos.FotosComercialesService fotosComerciales,
                              com.controllocal.service.materialia.MaterialIaService materialIa,
                              com.controllocal.service.actividad.CompromisosDeSeguimiento compromisos,
                              com.controllocal.service.actividad.InformesC7 informesC7,
                              com.controllocal.service.actividad.ProgramasPersonales programas) {
        this.compromisos=compromisos;this.informesC7=informesC7;this.programas=programas;
        this.materialIa=materialIa;
        this.piezas=piezas;this.fotosComerciales=fotosComerciales;
        this.publicaciones=publicaciones;this.identidades=identidades;
        this.oportunidades=oportunidades; this.visitas=visitas;this.interacciones=interacciones;
        this.prospecciones=prospecciones;
        this.capacidades = capacidades;
        this.adaptadores = adaptadores;
        this.captura = captura;
        this.propiedades = propiedades;
        this.clientes = clientes;
        this.propietarios = propietarios;
        this.coincidencias = coincidencias;
        this.focos = focos;
        this.actuaciones = actuaciones;
        this.automaticos = automaticos;
        this.galeria = galeria;
        this.json = json.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.sesiones = sesiones;
    }

    private static int paginaReporte(Map<String,Object>d,String campo,int defecto){return d.get(campo)==null?defecto:Math.toIntExact(idComercial(d.get(campo)));}
    @Override
    public ResultadoComercial operarComercial(SesionBrox sesion,OperacionComercial operacion,
            Map<String,Object> datos,Traza traza) {
        Objects.requireNonNull(operacion,"Operación requerida");
        String capacidad=operacion.name().toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(datos,"Datos requeridos");
        return llamar(sesion,capacidad,actor -> {
            var sid=ContextoDeActuacion.de(SesionActual.sesion());
            Object resultado=switch(operacion) {
                case CONSULTAR_SOLICITUDES_DISPONIBILIDAD -> Map.of("solicitudes",solicitudesDisponibilidad.listar(convertir(datos,com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.class),actor));
                case SOLICITAR_CONFIRMACION_INICIAL -> {var d=convertir(datos,com.controllocal.web.controlador.DisponibilidadController.Inicial.class);yield solicitudesDisponibilidad.solicitarInicial(d.tipo(),d.recurso(),d.version(),actor,sid,claveAutomatica(capacidad,actor,traza),procedencia(traza));}
                case RESPONDER_SOLICITUD_DISPONIBILIDAD -> solicitudesDisponibilidad.registrarEspera(convertir(datos,com.controllocal.service.disponibilidad.ReconfirmacionesDisponibilidad.Espera.class),actor,sid,claveAutomatica(capacidad,actor,traza),procedencia(traza));
                case CONSULTAR_AVISOS_BROKER -> avisosBroker.listar(paginaReporte(datos,"pagina",1),paginaReporte(datos,"tamano",20),Boolean.TRUE.equals(datos.get("historia")),actor);
                case ATENDER_AVISO_BROKER -> Map.of("atendida",avisosBroker.atender(idComercial(datos.get("evento")),actor,sid));
                case CONSULTAR_RADAR -> focoEnConsulta(actor,convertir(datos,com.controllocal.service.soporte.PoliticaDeDespacho.Consulta.class));
                case DEFINICION_DE_METAS -> metasComerciales.definicion(actor);
                case CONSULTAR_METAS_COMERCIALES -> Map.of("metas",metasComerciales.del(java.util.Objects.toString(datos.get("mes"),null),actor));
                case CONSULTAR_PROPUESTAS_METAS -> Map.of("propuestas",metasComerciales.propuestasPendientes(actor));
                case CONSULTAR_PROPUESTA_META -> metasComerciales.consultarPropuesta(idComercial(datos.get("idPropuesta")),actor);
                case PROPONER_META -> emitirAutomatico(capacidad,actor,traza,()->new ResultadoComercial(mapaComercial(metasComerciales.proponer(java.util.Objects.toString(datos.get("mes"),null),entradaComercial(datos.get("propuestaMeta"),com.controllocal.service.MetaComercialService.Propuesta.class),actor,sid,claveAutomatica(capacidad,actor,traza),procedencia(traza))),null,referencia(capacidad,claveAutomatica(capacidad,actor,traza))));
                case ESTABLECER_META -> actuaciones.prepararEstablecimientoMetas(actor,entradaComercial(datos.get("metas"),com.controllocal.service.MetaComercialService.Establecer.class),sid,procedencia(traza));
                case DECIDIR_PROPUESTA_META -> actuaciones.prepararDecisionMeta(actor,entradaComercial(datos.get("decisionMeta"),com.controllocal.service.MetaComercialService.Decision.class),sid,procedencia(traza));
                case CONSULTAR_AVANCE_METAS -> rendimientoComercial.del(java.util.Objects.toString(datos.get("mes"),null),actor);
                case CONSULTAR_PULSO_EQUIPO -> rendimientoComercial.pulso(java.util.Objects.toString(datos.get("mes"),null),actor);
                case CONSULTAR_REPORTES_PROPIETARIO -> Map.of("reportes",reportesPropietario.listar(idComercial(datos.get("idEncargo")),paginaReporte(datos,"pagina",1),paginaReporte(datos,"tamano",20),actor));
                case CONSULTAR_AVANCE_REPORTE -> reportesPropietario.preview(idComercial(datos.get("idEncargo")),fechaC7(datos.get("desde")),fechaC7(datos.get("hasta")),actor);
                case CONSULTAR_ANTECEDENTE_REPORTE -> reportesPropietario.consultarAntecedente(idComercial(datos.get("idAntecedente")),actor);
                case PREPARAR_REPORTE_PROPIETARIO -> emitirAutomatico(capacidad,actor,traza,()->new ResultadoComercial(mapaComercial(reportesPropietario.preparar(idComercial(datos.get("idEncargo")),entradaComercial(datos.get("datosReporte"),com.controllocal.service.ReportePropietarioService.DatosReporte.class),actor,sid,claveAutomatica(capacidad,actor,traza),procedencia(traza))),null,referencia(capacidad,claveAutomatica(capacidad,actor,traza))));
                case PREVISUALIZAR_REPORTE_PROPIETARIO -> reportesPropietario.consultar(entradaComercial(datos.get("reporte"),com.controllocal.service.ReportePropietarioService.Referencia.class),actor);
                case OBTENER_REPORTE_PROPIETARIO -> reportesPropietario.obtenerArchivo(entradaComercial(datos.get("reporte"),com.controllocal.service.ReportePropietarioService.Referencia.class),actor,sid);
                case CONSULTAR_COMUNICACIONES_REPORTE -> Map.of("comunicaciones",reportesPropietario.comunicaciones(idComercial(datos.get("idEncargo")),paginaReporte(datos,"pagina",1),paginaReporte(datos,"tamano",20),actor));
                case REGISTRAR_COMUNICACION_REPORTE_REALIZADA -> emitirAutomatico(capacidad,actor,traza,()->new ResultadoComercial(mapaComercial(reportesPropietario.registrarComunicacion(entradaComercial(datos.get("datosComunicacion"),com.controllocal.service.ReportePropietarioService.DatosComunicacion.class),actor,sid,claveAutomatica(capacidad,actor,traza),procedencia(traza))),null,referencia(capacidad,claveAutomatica(capacidad,actor,traza))));
                case RECTIFICAR_COMUNICACION_REPORTE -> actuaciones.prepararRectificacionComunicacionReporte(actor,entradaComercial(datos.get("rectificacion"),com.controllocal.service.ReportePropietarioService.Rectificacion.class),sid,procedencia(traza));
                case PREPARAR_MATERIAL_IA -> materialIa.preparar(entradaComercial(datos,com.controllocal.web.dto.MaterialIaDtos.Solicitud.class).aCore(),actor,sid);
                case SOLICITAR_MATERIAL_IA -> materialIa.solicitar(entradaComercial(datos,com.controllocal.web.dto.MaterialIaDtos.Solicitud.class).aCore(),actor,sid);
                case CONSULTAR_MATERIAL_IA -> materialIa.consultar(entradaComercial(datos,com.controllocal.web.dto.MaterialIaDtos.Consulta.class).operacion(),actor,sid);
                case PUBLICAR_ANUNCIO,ACTUALIZAR_ANUNCIO_VISIBLE,PAUSAR_ANUNCIO,REANUDAR_ANUNCIO,FINALIZAR_DIFUSION_ANUNCIO ->
                        actuaciones.prepararPublicacion(capacidad,actor,entradaComercial(datos,com.controllocal.service.PublicacionService.DatosCambio.class),sid,procedencia(traza));
                case OBTENER_PIEZA_PARA_DIFUSION -> actuaciones.prepararEntregaPieza(actor,
                        entradaComercial(datos,com.controllocal.service.documentos.PiezasComercialesService.Entrega.class),sid,procedencia(traza));
                case PREPARAR_PIEZA_COMERCIAL,PREVISUALIZAR_PIEZA_COMERCIAL,REGENERAR_PIEZA_COMERCIAL -> generarPieza(capacidad,datos,actor,sid,traza);
                case CONSULTAR_ESTADO_PIEZA -> piezas.consultarEstado(uuidComercial(datos.get("pieza")),actor);
                case CONSULTAR_FOTOS_PERMITIDAS_PARA_PIEZA -> piezas.consultarFotosPermitidas(idComercial(datos.get("encargo")),datos.get("propiedadFicha")==null?null:idComercial(datos.get("propiedadFicha")),actor);
                case CONSULTAR_PLANTILLAS_Y_FORMATOS_PIEZA -> piezas.consultarPlantillasYFormatos(idComercial(datos.get("encargo")),actor);
                case EVALUAR_FOTO_LOCAL -> fotosComerciales.evaluar(idComercial(datos.get("propiedad")),uuidComercial(datos.get("original")),actor);
                case CREAR_BORRADOR_ANUNCIO,EDITAR_BORRADOR_ANUNCIO -> borradorAnuncio(capacidad,datos,actor,sid,traza);
                case EVALUAR_PUBLICABILIDAD -> publicaciones.evaluarPublicabilidad(idComercial(datos.get("encargo")),canalPublicacion(datos),actor);
                case CONSULTAR_PUBLICACIONES -> Map.of("publicaciones",publicaciones.listarDeEncargo(idComercial(datos.get("encargo")),actor));
                case CONSULTAR_PUBLICACION -> publicaciones.obtener(idComercial(datos.get("encargo")),idComercial(datos.get("publicacion")),actor);
                case CONSULTAR_HISTORIA_PUBLICACION -> Map.of("historia",publicaciones.consultarHistoria(idComercial(datos.get("encargo")),idComercial(datos.get("publicacion")),actor));
                case ABRIR_OPORTUNIDAD -> actuaciones.prepararAperturaOportunidad(actor,convertir(datos,com.controllocal.service.OportunidadService.DatosOportunidad.class),sid,procedencia(traza));
                case RECTIFICAR_INTERACCION -> actuaciones.prepararRectificacionInteraccion(actor,convertir(datos,com.controllocal.service.InteraccionService.Rectificacion.class),sid,procedencia(traza));
                case REGISTRAR_NO_CONTINUIDAD_OPORTUNIDAD -> actuaciones.prepararNoContinuidadOportunidad(actor,convertir(datos,com.controllocal.service.OportunidadService.NoContinuidad.class),sid,procedencia(traza));
                case CANCELAR_VISITA -> actuaciones.prepararCancelacionVisita(actor,convertir(datos,com.controllocal.service.VisitaService.Cancelacion.class),sid,procedencia(traza));
                case PRESENTAR_OFERTA -> actuaciones.prepararRegistroOferta(actor,convertir(datos,com.controllocal.service.OfertaService.DatosRegistroOferta.class),sid,procedencia(traza));
                case REGISTRAR_CONTRAOFERTA -> actuaciones.prepararContraoferta(actor,convertir(datos,com.controllocal.service.OfertaService.DatosContraoferta.class),sid,procedencia(traza));
                case REGISTRAR_ACEPTACION_OFERTA -> actuaciones.prepararAceptacionOferta(actor,convertir(datos,com.controllocal.service.OfertaService.DatosAceptacion.class),sid,procedencia(traza));
                case REGISTRAR_RECHAZO_OFERTA -> actuaciones.prepararRechazoOferta(actor,convertir(datos,com.controllocal.service.OfertaService.DatosRechazo.class),sid,procedencia(traza));
                case REGISTRAR_RETIRADA_OFERTA -> actuaciones.prepararRetiradaOferta(actor,convertir(datos,com.controllocal.service.OfertaService.DatosRetirada.class),sid,procedencia(traza));
                case PRESENTAR_EXPEDIENTE_OPERACION -> actuaciones.prepararPresentacionExpediente(actor,convertir(datos,com.controllocal.service.formalizacion.FormalizacionService.DatosPresentacion.class),sid,procedencia(traza));
                case EVALUAR_EXPEDIENTE_OPERACION -> actuaciones.prepararEvaluacionExpediente(actor,convertir(datos,com.controllocal.service.formalizacion.FormalizacionService.DatosRevision.class),sid,procedencia(traza));
                case INFORMAR_HITO_CONTRACTUAL -> actuaciones.prepararInformacionHito(actor,convertir(datos,com.controllocal.service.formalizacion.FormalizacionService.DatosInformacionHito.class),sid,procedencia(traza));
                case CONFIRMAR_HITO_CONTRACTUAL -> actuaciones.prepararConfirmacionHito(actor,convertir(datos,com.controllocal.service.formalizacion.FormalizacionService.DatosConfirmacionHito.class),sid,procedencia(traza));
                case RECTIFICAR_HITO_CONTRACTUAL -> actuaciones.prepararRectificacionHito(actor,convertir(datos,com.controllocal.service.formalizacion.FormalizacionService.DatosRectificacionHito.class),sid,procedencia(traza));
                case REGISTRAR_COBRO -> actuaciones.prepararMovimientoComision(actor,convertir(datos,com.controllocal.service.ComisionService.DatosCobro.class),sid,procedencia(traza));
                case ASIGNAR_REPARTO_COMISION -> actuaciones.prepararRepartoComision(actor,convertir(datos,com.controllocal.service.ComisionService.DatosReparto.class),sid,procedencia(traza));
                case REPROGRAMAR_GESTION_COBRO -> actuaciones.prepararReprogramacionCobro(actor,convertir(datos,com.controllocal.service.ComisionService.DatosReprogramacion.class),sid,procedencia(traza));
                case CONCLUIR_GESTION_COBRO -> actuaciones.prepararConclusionCobro(actor,convertir(datos,com.controllocal.service.ComisionService.DatosConclusion.class),sid,procedencia(traza));
                case AUTORIZAR_BORRADOR_ADENDA -> {
                    var d=convertir(datos,com.controllocal.service.actuacion.EfectosAdendaDeActuacion.Datos.class);
                    yield actuaciones.prepararDecisionBorradorAdenda(actor,d.referencia(),d.decision(),sid,procedencia(traza));
                }
                case REGISTRAR_INTERACCION -> emitirAutomatico(capacidad,actor,traza,()->automaticos.registrarInteraccion(convertir(datos,com.controllocal.service.InteraccionService.DatosInteraccion.class),actor,sid,correlacion(traza),procedencia(traza)));
                case VINCULAR_INTERACCION -> emitirAutomatico(capacidad,actor,traza,()->automaticos.vincularInteraccion(idComercial(datos.get("idInteraccion")),convertir(datos,com.controllocal.service.InteraccionService.AdicionVinculo.class),actor,sid,correlacion(traza),procedencia(traza)));
                case ANOTAR_OBSERVACION_INTERACCION -> emitirAutomatico(capacidad,actor,traza,()->automaticos.anotarObservacionInteraccion(idComercial(datos.get("idInteraccion")),convertir(datos,com.controllocal.service.InteraccionService.ObservacionPosterior.class),actor,sid,correlacion(traza),procedencia(traza)));
                case CONSULTAR_COMPROMISOS_PROSPECCION -> Map.of("compromisos",compromisos.listar(idComercial(datos.get("idProspeccion")),actor));
                case ASUMIR_COMPROMISO_PROSPECCION -> compromisos.planificar(idComercial(datos.get("idProspeccion")),idComercial(datos.get("idSeguimiento")),entradaComercial(datos.get("plan"),com.controllocal.service.actividad.CompromisosDeSeguimiento.Plan.class),actor,sid,claveAutomatica(capacidad,actor,traza),procedencia(traza));
                case ACREDITAR_COMPROMISO_PROSPECCION -> compromisos.acreditar(idComercial(datos.get("idProspeccion")),uuidComercial(datos.get("referencia")),entradaComercial(datos.get("acreditacion"),com.controllocal.service.actividad.CompromisosDeSeguimiento.Acreditacion.class),actor,sid,claveAutomatica(capacidad,actor,traza),procedencia(traza));
                case RESOLVER_COMPROMISO_PROSPECCION -> compromisos.resolver(idComercial(datos.get("idProspeccion")),uuidComercial(datos.get("referencia")),entradaComercial(datos.get("resolucion"),com.controllocal.service.actividad.CompromisosDeSeguimiento.Resolucion.class),actor,sid,claveAutomatica(capacidad,actor,traza),procedencia(traza));
                case CONSULTAR_MEDICIONES_INDIVIDUALES -> informesC7.consultarMediciones(fechaC7(datos.get("desde")),fechaC7(datos.get("hasta")),actor);
                case CONSULTAR_DERECHOS_INFORMES -> informesC7.derechos(actor);
                case PREPARAR_INFORME_COMERCIAL -> informesC7.preparar(entradaComercial(datos.get("consulta"),com.controllocal.service.actividad.InformesC7.Consulta.class),actor);
                case CONSULTAR_PROGRAMAS_PERSONALES -> Map.of("programas",programas.listar(datos.get("pagina")==null?1:Math.toIntExact(idComercial(datos.get("pagina"))),datos.get("tamano")==null?20:Math.toIntExact(idComercial(datos.get("tamano"))),actor));
                case CONSULTAR_PROGRAMA_PERSONAL -> programas.consultar(uuidComercial(datos.get("referencia")),actor);
                case CONSULTAR_OCURRENCIAS_PROGRAMA_PERSONAL -> Map.of("ocurrencias",programas.ocurrencias(uuidComercial(datos.get("referencia")),datos.get("pagina")==null?1:Math.toIntExact(idComercial(datos.get("pagina"))),datos.get("tamano")==null?20:Math.toIntExact(idComercial(datos.get("tamano"))),actor));
                case CONSULTAR_RESULTADO_PROGRAMA_PERSONAL -> programas.resultado(uuidComercial(datos.get("referencia")),uuidComercial(datos.get("ocurrencia")),actor);
                case CONSULTAR_SEGUIMIENTOS_PROSPECCION -> Map.of("seguimientos",prospecciones.seguimientos(idComercial(datos.get("idProspeccion")),actor));
                case PLANIFICAR_SEGUIMIENTO_PROSPECCION -> emitirAutomatico(capacidad,actor,traza,()->automaticos.planificarSeguimiento(idComercial(datos.get("idProspeccion")),idComercial(datos.get("idSeguimiento")),convertir(datos,com.controllocal.service.ProspeccionService.PlanificacionSeguimiento.class),actor,sid,correlacion(traza),procedencia(traza)));
                case POSPONER_SEGUIMIENTO_PROSPECCION -> emitirAutomatico(capacidad,actor,traza,()->automaticos.posponerSeguimiento(idComercial(datos.get("idProspeccion")),idComercial(datos.get("idSeguimiento")),convertir(datos,com.controllocal.service.ProspeccionService.PosposicionSeguimiento.class),actor,sid,correlacion(traza),procedencia(traza)));
                case PROGRAMAR_VISITA -> emitirAutomatico(capacidad,actor,traza,()->automaticos.programarVisita(convertir(datos,com.controllocal.service.VisitaService.DatosVisita.class),actor,sid,correlacion(traza),procedencia(traza)));
                case REPROGRAMAR_VISITA -> emitirAutomatico(capacidad,actor,traza,()->automaticos.reprogramarVisita(convertir(datos,com.controllocal.service.VisitaService.Reprogramacion.class),actor,sid,correlacion(traza),procedencia(traza)));
                case REGISTRAR_RESULTADO_VISITA -> emitirAutomatico(capacidad,actor,traza,()->automaticos.registrarResultadoVisita(idComercial(datos.get("idVisita")),convertir(datos.get("resultadoVisita"),com.controllocal.service.VisitaService.DesenlaceVisita.class),actor,sid,correlacion(traza),procedencia(traza)));
                case CONSULTAR_OPORTUNIDAD -> consultarOportunidad(datos,actor);
                case CONSULTAR_AGENDA_VISITAS -> visitas.agenda(convertir(datos,com.controllocal.service.VisitaService.ConsultaAgenda.class),actor);
            };
            if(resultado instanceof ActuacionesService.Propuesta p)
                return new ResultadoComercial(null,convertir(p,PropuestaActuacion.class),null);
            if(resultado instanceof ResultadoComercial comercial)return comercial;
            if(resultado instanceof ComandosAutomaticosService.ResultadoAutomatico<?> a)
                return new ResultadoComercial(mapaComercial(a.resultado()),null,referencia(a.capacidad(),a.clave()));
            return new ResultadoComercial(mapaComercial(resultado),null,null);
        });
    }
    private static java.time.LocalDate fechaC7(Object v){if(v==null)return null;try{if(v instanceof String s)return java.time.LocalDate.parse(s);}catch(java.time.format.DateTimeParseException e){throw new ReglaNegocioException("Fecha comercial no válida.");}throw new ReglaNegocioException("Declara la fecha comercial en formato ISO.");}
    private Map<String,Object> mapaComercial(Object resultado) {
        Objects.requireNonNull(resultado,"Core no devolvió un resultado");
        return json.convertValue(resultado,new TypeReference<Map<String,Object>>(){});
    }
    private ResultadoComercial borradorAnuncio(String capacidad,Map<String,Object> entrada,Actor actor,
            ActuacionesService.ContextoSesion sid,Traza traza){
        String clave=claveAutomatica(capacidad,actor,traza);
        var datos=entradaComercial(entrada,com.controllocal.service.PublicacionService.DatosBorrador.class);
        return emitirAutomatico(capacidad,actor,traza,()->{
            var ficha=com.controllocal.service.PublicacionService.CREAR_BORRADOR.equals(capacidad)
                    ?publicaciones.crearBorradorAnuncio(datos,actor,sid,clave,procedencia(traza))
                    :publicaciones.editarBorradorAnuncio(datos,actor,sid,clave,procedencia(traza));
            return new ResultadoComercial(mapaComercial(ficha),null,referencia(capacidad,clave));
        });
    }
    private static String canalPublicacion(Map<String,Object> datos){
        Object canal=datos.get("canal");if(canal==null)return null;
        if(canal instanceof String texto)return texto;
        throw new ReglaNegocioException("El canal de publicación debe ser un código conocido.");
    }
    private <T>T entradaComercial(Object datos,Class<T> tipo){
        try{return json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).convertValue(datos,tipo);}
        catch(IllegalArgumentException incompatible){throw new ReglaNegocioException("Los datos no corresponden al contrato comercial vigente.");}
    }
    private record VersionPieza(java.util.UUID pieza,
            @com.fasterxml.jackson.annotation.JsonProperty(value="versionContenido",required=true)
            @com.fasterxml.jackson.annotation.JsonSetter(nulls=com.fasterxml.jackson.annotation.Nulls.FAIL) long versionContenido) { }
    private ResultadoComercial generarPieza(String capacidad,Map<String,Object> datos,Actor actor,
            ActuacionesService.ContextoSesion sid,Traza traza){
        String clave=claveAutomatica(capacidad,actor,traza);
        return emitirAutomatico(capacidad,actor,traza,()->{
            Object resultado=switch(capacidad){
                case "preparar_pieza_comercial" -> piezas.preparar(entradaComercial(datos,com.controllocal.service.documentos.PiezasComercialesService.Preparacion.class),actor,sid,clave,procedencia(traza));
                case "regenerar_pieza_comercial" -> piezas.regenerar(entradaComercial(datos,com.controllocal.service.documentos.PiezasComercialesService.Regeneracion.class),actor,sid,clave,procedencia(traza));
                case "previsualizar_pieza_comercial" -> {
                    var d=entradaComercial(datos,VersionPieza.class);
                    yield piezas.previsualizar(d.pieza(),d.versionContenido(),actor,sid,clave,procedencia(traza));
                }
                default -> throw new ReglaNegocioException("Operación de pieza no reconocida.");
            };
            return new ResultadoComercial(mapaComercial(resultado),null,referencia(capacidad,clave));
        });
    }
    private static java.util.UUID uuidComercial(Object valor){
        if(valor instanceof String texto)try{
            var id=java.util.UUID.fromString(texto);if(id.toString().equalsIgnoreCase(texto))return id;
        }catch(IllegalArgumentException invalida){ }
        throw new ReglaNegocioException("La referencia debe ser un UUID válido.");
    }
    private com.controllocal.service.OportunidadService.ConsultaOportunidad consultarOportunidad(Map<String,Object> datos,Actor actor) {
        if(!(datos.get("seccion") instanceof String seccion)||seccion.isBlank())
            throw new ReglaNegocioException("Identifica la sección que deseas consultar.");
        Object corte=datos.get("corte");
        if(corte!=null&&!(corte instanceof String))throw new ReglaNegocioException("El corte debe ser un instante válido.");
        final java.time.Instant instante;
        try {
            instante=corte==null?null:java.time.Instant.parse((String)corte);
        } catch(java.time.DateTimeException entradaInvalida) {
            throw new ReglaNegocioException("El corte debe ser un instante válido.");
        }
        return oportunidades.consultar(idComercial(datos.get("idOportunidad")),
                com.controllocal.service.OportunidadService.Seccion.valueOf(seccion),instante,actor);
    }

    private static long idComercial(Object valor) {
        try {
            long id=new java.math.BigDecimal(String.valueOf(valor)).longValueExact();
            if(id>0)return id;
        } catch(NumberFormatException|ArithmeticException invalido) { }
        throw new com.controllocal.service.excepcion.ReglaNegocioException("Se requiere el identificador entero positivo del recurso.");
    }
    private Actor actorVerificado(SesionBrox contexto) {
        var actor = sesiones.exigirVigente();
        var sesion = ContextoDeActuacion.de(SesionActual.sesion());
        if (contexto == null || contexto.idOrganizacion() != actor.idOrganizacion()
                || contexto.idPersona() != actor.idPersona()
                || contexto.idRolOperativo() != actor.idRolOperativo()
                || !Objects.equals(contexto.rolEfectivo(), actor.rolEfectivo())
                || !Objects.equals(contexto.identidadSesion(), sesion.identidad())
                || contexto.expiraEnEpochSeconds() != sesion.expiraEnEpochSeconds()) {
            throw new ErrorBrox(ErrorBrox.Tipo.PERMISO_INSUFICIENTE, "PERMISO_INSUFICIENTE", 403);
        }
        return actor;
    }

    private <T> T llamar(SesionBrox contexto, String capacidad, Function<Actor, T> caso) {
        ReferenciaAutomatico operacion = null;
        try {
            Actor actor = actorVerificado(contexto);
            var puerta = adaptadores.de(capacidad, actor);
            if (!puerta.reconocido()) {
                throw new ErrorBrox(ErrorBrox.Tipo.CAPACIDAD_DESHABILITADA,
                        "CAPACIDAD_DESHABILITADA", 409);
            }
            if (!puerta.permitido()) throw new AccesoNoAutorizadoException();
            if (!puerta.habilitado()) throw new CapacidadDeshabilitadaException();
            T resultado = Objects.requireNonNull(caso.apply(actor), "Respuesta Core ausente");
            if (resultado instanceof ResultadoAutomatico<?> automatico) operacion = automatico.operacion();
            if (resultado instanceof ResultadoComercial comercial) operacion = comercial.automatico();
            // El caso ya pudo confirmar su transacción; denegar la entrega no lo revierte.
            actorVerificado(contexto);
            return resultado;
        } catch (ErrorBrox error) {
            if (error.tipo() == ErrorBrox.Tipo.PERMISO_INSUFICIENTE && operacion != null)
                throw new ErrorBrox(error.tipo(), error.codigo(), error.estadoHttp(), operacion);
            throw error;
        } catch (AccesoNoAutorizadoException | CredencialesInvalidasException error) {
            throw new ErrorBrox(ErrorBrox.Tipo.PERMISO_INSUFICIENTE, "PERMISO_INSUFICIENTE", 403, operacion);
        } catch (CapacidadDeshabilitadaException error) {
            throw new ErrorBrox(ErrorBrox.Tipo.CAPACIDAD_DESHABILITADA, "CAPACIDAD_DESHABILITADA", 409);
        } catch (NoEncontradoException error) {
            throw new ErrorBrox(ErrorBrox.Tipo.SOLICITUD_RECHAZADA, "RECURSO_NO_ENCONTRADO", 404);
        } catch (ConflictoException error) {
            throw new ErrorBrox(ErrorBrox.Tipo.SOLICITUD_RECHAZADA, "CONFLICTO", 409);
        } catch (ReglaNegocioException | IllegalArgumentException error) {
            throw new ErrorBrox(ErrorBrox.Tipo.SOLICITUD_RECHAZADA, "SOLICITUD_INVALIDA", 400);
        } catch (RuntimeException error) {
            // Nunca trasladar mensajes SQL/infraestructura ni convertir el fallo en vacío.
            throw new ErrorBrox(ErrorBrox.Tipo.FALLO_TECNICO, "FALLO_TECNICO", 500, operacion);
        }
    }

    private <T> T convertir(Object valor, Class<T> tipo) {
        try {
            return json.convertValue(valor, tipo);
        } catch (IllegalArgumentException incompatible) {
            for(Throwable causa=incompatible.getCause();causa!=null;causa=causa.getCause())
                if(causa instanceof ReglaNegocioException regla)throw regla;
            throw ErrorBrox.contratoInvalido();
        }
    }

    @Override
    public List<Capacidad> capacidades(SesionBrox sesion, Traza traza) {
        return llamar(sesion, "consultar_capacidades", actor -> capacidades
                .consultar(CapacidadesService.Consulta.general(), actor).capacidades().stream()
                .map(c -> convertir(c, Capacidad.class)).toList());
    }

    private static Procedencia procedencia(Traza traza) {
        return Procedencia.deAgente(traza.canal(), traza.agente(), traza.modelo(), traza.modeloVersion(),
                traza.conversacionId(), traza.turnoId(), traza.mensajeId(), traza.peticion())
                .invocando(traza.herramienta());
    }

    private static CorrelacionAutomatico correlacion(Traza traza) {
        return new CorrelacionAutomatico(traza.conversacionId(), traza.turnoId(), traza.mensajeId());
    }

    private ReferenciaAutomatico referencia(String capacidad, String clave) {
        return new ReferenciaAutomatico("auto:" + capacidad + ":" + clave, capacidad,
                ComandosAutomaticosService.VERSION);
    }

    private <T, R> ResultadoAutomatico<R> resultadoAutomatico(
            ComandosAutomaticosService.ResultadoAutomatico<T> resultado, Function<T, R> proyectar) {
        return new ResultadoAutomatico<>(referencia(resultado.capacidad(), resultado.clave()),
                proyectar.apply(resultado.resultado()));
    }

    private <T> T emitirAutomatico(String capacidad, Actor actor, Traza traza, java.util.function.Supplier<T> caso) {
        var referencia = referencia(capacidad, claveAutomatica(capacidad,actor,traza));
        try {
            return caso.get();
        } catch (AccesoNoAutorizadoException | CapacidadDeshabilitadaException | NoEncontradoException
                 | ConflictoException | ReglaNegocioException error) {
            throw error;
        } catch (RuntimeException fallo) {
            // El efecto pudo hacer commit antes de perderse la respuesta. La recuperación sólo consulta.
            throw new ErrorBrox(ErrorBrox.Tipo.FALLO_TECNICO, "RESULTADO_INCIERTO", 503, referencia);
        }
    }
    private String claveAutomatica(String capacidad,Actor actor,Traza traza){
        return List.of(com.controllocal.service.PublicacionService.CREAR_BORRADOR,com.controllocal.service.PublicacionService.EDITAR_BORRADOR,
                "solicitar_confirmacion_inicial","responder_solicitud_disponibilidad","preparar_pieza_comercial","previsualizar_pieza_comercial","regenerar_pieza_comercial","proponer_meta","preparar_reporte_propietario","registrar_comunicacion_reporte_realizada").contains(capacidad)
                ?identidades.clavePara(capacidad,actor,correlacion(traza))
                :automaticos.clavePara(capacidad,actor,correlacion(traza));
    }

    @Override
    public ResultadoAutomatico<EstadoCaptura> avanzarCaptura(SesionBrox sesion, String intencion, Long idBorrador,
                                        Integer versionEsperada, Map<String, String> datos, Traza traza) {
        String capacidad = idBorrador == null ? "abrir_sesion_captura" : "responder_captura";
        return llamar(sesion, capacidad, actor -> emitirAutomatico(capacidad, actor, traza,
                () -> resultadoAutomatico(automaticos.avanzarCaptura(new com.controllocal.service.captura.MotorDeCaptura.Avance(
                        intencion, idBorrador, versionEsperada, respuestas(datos), null),
                        actor, correlacion(traza), procedencia(traza)), e -> convertir(e, EstadoCaptura.class))));
    }

    private static java.util.Map<String,com.controllocal.service.captura.MotorDeCaptura.Respuesta> respuestas(Map<String,String> datos) {
        if(datos==null)return Map.of();
        var respuestas = new java.util.LinkedHashMap<String,com.controllocal.service.captura.MotorDeCaptura.Respuesta>();
        datos.forEach((clave,valor)->respuestas.put(clave,com.controllocal.service.captura.MotorDeCaptura.Respuesta.conocida(valor)));
        return respuestas;
    }

    @Override
    public List<EstadoCaptura> capturasEnCurso(SesionBrox sesion, Traza traza) {
        return llamar(sesion, "capturas_en_curso", actor -> captura.enCurso(actor).stream()
                .map(c -> convertir(c, EstadoCaptura.class)).toList());
    }

    @Override
    public EstadoCaptura captura(SesionBrox sesion, long idBorrador, Traza traza) {
        return llamar(sesion, "consultar_captura",
                actor -> convertir(captura.consultar(idBorrador, actor), EstadoCaptura.class));
    }

    @Override
    public PropuestaActuacion prepararCaptura(SesionBrox sesion, long idBorrador, Traza traza, EstadoCaptura esperado) {
        return llamar(sesion, "preparar_actuacion_captura", actor -> convertir(actuaciones.prepararCaptura(
                actor, idBorrador, ContextoDeActuacion.de(SesionActual.sesion()), procedencia(traza), correlacion(traza),
                esperado==null ? null : esperadoParaPreparar(esperado)),
                PropuestaActuacion.class));
    }

    private static Map<String,Object> esperadoParaPreparar(EstadoCaptura esperado) {
        var datos = new java.util.LinkedHashMap<String,Object>();
        datos.put("datos", esperado.conocido());
        datos.put("version", esperado.version());
        datos.put("contexto", esperado.contexto());
        datos.put("metadatos", esperado.metadatos());
        return datos;
    }

    @Override
    public ResultadoActuacion consultarActuacion(SesionBrox sesion, String referencia, Traza traza) {
        if (referencia != null && referencia.startsWith("auto:")) {
            return consultarAutomatico(sesion, referencia);
        }
        return llamar(sesion, "consultar_actuacion", actor -> {
            var r=actuaciones.consultar(actor,referencia,ContextoDeActuacion.de(SesionActual.sesion()));
            if(r.propuesta()!=null&&esActividadComercial(r.propuesta().capacidad())) {
                var comercial=new ResultadoComercial(r.resultado(),r.resultado()==null
                        ? convertir(r.propuesta(),PropuestaActuacion.class):null,null);
                return new ResultadoActuacion(r.referencia(),r.estado(),null,null,r.motivo(),comercial);
            }
            return convertir(r,ResultadoActuacion.class);
        });
    }
    private static boolean esActividadComercial(String capacidad) {
        try { OperacionComercial.valueOf(capacidad.toUpperCase(java.util.Locale.ROOT));return true; }
        catch(IllegalArgumentException desconocida) { return false; }
    }

    @Override
    public List<Coincidencia> buscarPropiedades(SesionBrox sesion, String texto, Traza traza) {
        return llamar(sesion, "buscar_propiedades", actor -> propiedades.listar(
                new PropiedadUniversalService.FiltrosPropiedad(texto, null, null, null, null, 1, 10), actor)
                .items().stream().map(p -> new Coincidencia(p.id(), p.codigo(), p.direccion(), p.distrito())).toList());
    }

    @Override
    public Map<String, Object> propiedad(SesionBrox sesion, long idPropiedad, Traza traza) {
        return llamar(sesion, "estado_de_propiedad", actor -> json.convertValue(
                PropiedadResponse.desde(propiedades.consultar(idPropiedad, actor), galeria.proveedor()),
                new TypeReference<Map<String, Object>>() { }));
    }

    @Override
    public List<Persona> buscarClientes(SesionBrox sesion, String texto, Traza traza) {
        return llamar(sesion, "buscar_persona", actor -> clientes.listar(
                new ClienteService.FiltrosCliente(texto, null, null, null, 1, 10), actor).items().stream()
                .map(p -> new Persona(p.id(), p.nombre(), p.tipoDocumento(), p.numeroDocumento(), p.telefono())).toList());
    }

    @Override
    public List<Persona> buscarPropietarios(SesionBrox sesion, String texto, Traza traza) {
        return llamar(sesion, "buscar_propietarios", actor -> propietarios.listar(
                new PropietarioService.FiltrosPropietario(texto, null, 1, 5), actor).items().stream()
                .map(p -> new Persona(p.id(), p.nombre(), p.tipoDocumento(), p.numeroDocumento(), p.telefono())).toList());
    }

    @Override
    public CoincidenciasComerciales coincidencias(SesionBrox sesion,ConsultaCoincidencias consulta,Traza traza) {
        return llamar(sesion,"buscar_coincidencias",actor -> convertir(CoincidenciasResponse.desde(
                coincidencias.consultar(convertir(consulta,com.controllocal.service.CoincidenciaService.Consulta.class),actor,
                        ContextoDeActuacion.de(SesionActual.sesion()))),CoincidenciasComerciales.class));
    }
    @Override
    public CoincidenciasComerciales continuarCoincidencias(SesionBrox sesion,String cursor,Traza traza) {
        return llamar(sesion,"continuar_coincidencias",actor -> convertir(CoincidenciasResponse.desde(
                coincidencias.continuar(cursor,actor,ContextoDeActuacion.de(SesionActual.sesion()))),CoincidenciasComerciales.class));
    }
    @Override
    public CoincidenciaComercial explicarCoincidencia(SesionBrox sesion,String cursor,String referenciaDemanda,String referencia,Traza traza) {
        return llamar(sesion,"explicar_match",actor -> convertir(com.controllocal.web.dto.CoincidenciaResponse.desde(
                coincidencias.explicar(cursor,referenciaDemanda,referencia,actor,ContextoDeActuacion.de(SesionActual.sesion()))),CoincidenciaComercial.class));
    }

    @Override
    public Foco foco(SesionBrox sesion, Traza traza) {
        return llamar(sesion,"que_resolver_hoy",actor -> focoEnConsulta(actor,new com.controllocal.service.soporte.PoliticaDeDespacho.Consulta(com.controllocal.service.soporte.PoliticaDeDespacho.Vista.HOY,null,null)));
    }
    @org.springframework.beans.factory.annotation.Autowired private com.controllocal.service.disponibilidad.ReconfirmacionesDisponibilidad solicitudesDisponibilidad;
    @org.springframework.beans.factory.annotation.Autowired private com.controllocal.service.soporte.NotificacionesBrokerC7 avisosBroker;
    private Foco focoEnConsulta(Actor actor,com.controllocal.service.soporte.PoliticaDeDespacho.Consulta consulta) {
            var foco = focos.de(consulta,null, actor);
            var bandeja = foco.bandeja().stream().map(TareaResponse::desde)
                    .map(t -> new Asunto(t.id().toString(), t.tipo(), t.entidadTipo(), t.entidadId(),
                            t.entidadCodigo(), t.descripcion(), t.prioridad(), t.diasSinAccion(),
                            t.dependeDeMi(), t.rutaResolver(), t.interpretacion() == null ? null : t.interpretacion().lectura(),convertir(t.orden(),OrdenRadar.class)))
                    .toList();
            var asuntos = foco.asuntosDelBroker().stream().map(AsuntoDelBrokerResponse::desde)
                    .map(a -> new AsuntoDelBroker(a.id(), a.tipo(), a.entidadTipo(), a.entidadId(),
                            a.entidadCodigo(), a.destino(), a.diasEsperando(),
                            a.interpretacion() == null ? null : a.interpretacion().lectura(),convertir(a.orden(),OrdenRadar.class))).toList();
            var hallazgos = foco.hallazgos().stream().map(HallazgoResponse::desde)
                    .map(h -> new Hallazgo(h.id(),h.tipo(),h.titulo(),h.porQue(),
                            h.aptitud()==null?null:convertir(h.aptitud(),CoincidenciaComercial.class),h.destino(),h.idDemanda(),h.idCaptacion(),h.codigoCaptacion())).toList();
            return new Foco(foco.indicadores().ambito(), foco.indicadores().pendientesDeAtencion(),
                    bandeja, foco.bandeja().size(), asuntos, hallazgos,
                    foco.actuaciones().stream().map(a->convertir(a,ActuacionRadar.class)).toList());
    }

    @Override
    public ResultadoAutomatico<Persona> registrarPropietario(SesionBrox sesion, Map<String, String> datos, Traza traza) {
        return llamar(sesion, "registrar_propietario", actor -> {
            var dto = convertir(datos, PropietarioRequest.class).aDatos();
            return emitirAutomatico("registrar_propietario", actor, traza, () -> resultadoAutomatico(
                    automaticos.registrarPropietario(dto, actor, correlacion(traza), procedencia(traza)),
                    p -> convertir(PropietarioResponse.desde(p), Persona.class)));
        });
    }

    @Override
    public Optional<ResultadoAutomatico<Persona>> recuperarPropietario(SesionBrox sesion, Map<String, String> datos, Traza traza) {
        return llamar(sesion, "registrar_propietario", actor -> automaticos.recuperarPropietario(
                convertir(datos, PropietarioRequest.class).aDatos(), actor, correlacion(traza))
                .map(r -> resultadoAutomatico(r, p -> convertir(PropietarioResponse.desde(p), Persona.class))));
    }

    private ResultadoActuacion consultarAutomatico(SesionBrox sesion, String referencia) {
        String[] partes = referencia.split(":", -1);
        if (partes.length != 3 || !partes[2].matches("[a-f0-9]{64}")) {
            throw new ErrorBrox(ErrorBrox.Tipo.SOLICITUD_RECHAZADA, "SOLICITUD_INVALIDA", 400);
        }
        String capacidad = partes[1];
        if (!List.of("registrar_propietario", "registrar_interaccion", "vincular_interaccion", "anotar_observacion_interaccion", "abrir_sesion_captura", "responder_captura", "programar_visita", "reprogramar_visita", "registrar_resultado_visita", "planificar_seguimiento_prospeccion", "posponer_seguimiento_prospeccion","crear_borrador_anuncio","editar_borrador_anuncio",
                "solicitar_confirmacion_inicial","responder_solicitud_disponibilidad","preparar_pieza_comercial","previsualizar_pieza_comercial","regenerar_pieza_comercial","proponer_meta","preparar_reporte_propietario","registrar_comunicacion_reporte_realizada")
                .contains(capacidad)) {
            throw new ErrorBrox(ErrorBrox.Tipo.SOLICITUD_RECHAZADA, "SOLICITUD_INVALIDA", 400);
        }
        return llamar(sesion, capacidad, actor -> {
            Optional<Map<String, Object>> resultado = switch (capacidad) {
                case "proponer_meta" -> metasComerciales.resultado(capacidad,partes[2],actor).map(r->Map.of("comercial",new ResultadoComercial(mapaComercial(r),null,referencia(capacidad,partes[2]))));
                case "preparar_reporte_propietario" -> reportesPropietario.resultadoPreparacion(partes[2],actor).map(r->Map.of("comercial",new ResultadoComercial(mapaComercial(r),null,referencia(capacidad,partes[2]))));
                case "registrar_comunicacion_reporte_realizada" -> reportesPropietario.resultadoComunicacion(partes[2],actor).map(r->Map.of("comercial",new ResultadoComercial(mapaComercial(r),null,referencia(capacidad,partes[2]))));
                case "crear_borrador_anuncio","editar_borrador_anuncio" -> publicaciones.consultarResultado(capacidad,partes[2],actor)
                        .map(r->Map.of("comercial",new ResultadoComercial(mapaComercial(r),null,referencia(capacidad,partes[2]))));
                case "solicitar_confirmacion_inicial","responder_solicitud_disponibilidad","preparar_pieza_comercial","previsualizar_pieza_comercial","regenerar_pieza_comercial" -> piezas.consultarResultadoGeneracion(capacidad,partes[2],actor)
                        .map(r->Map.of("comercial",new ResultadoComercial(r,null,referencia(capacidad,partes[2]))));
                case "registrar_propietario" -> automaticos.consultarPropietario(actor, partes[2])
                        .map(r -> Map.of("persona", convertir(PropietarioResponse.desde(r.resultado()), Persona.class)));
                case "registrar_interaccion","vincular_interaccion","anotar_observacion_interaccion" -> automaticos.consultarInteraccion(capacidad,actor,partes[2])
                        .map(r -> Map.of("comercial",new ResultadoComercial(mapaComercial(r.resultado()),null,referencia(r.capacidad(),r.clave()))));
                case "programar_visita","reprogramar_visita","registrar_resultado_visita" -> automaticos.consultarVisita(capacidad,actor,partes[2])
                        .map(r -> Map.of("comercial",new ResultadoComercial(mapaComercial(r.resultado()),null,referencia(r.capacidad(),r.clave()))));
                case "planificar_seguimiento_prospeccion","posponer_seguimiento_prospeccion" -> automaticos.consultarSeguimiento(capacidad,actor,partes[2])
                        .map(r -> Map.of("comercial",new ResultadoComercial(mapaComercial(r.resultado()),null,referencia(r.capacidad(),r.clave()))));
                default -> automaticos.consultarCaptura(actor, capacidad, partes[2])
                        .map(r -> Map.of("captura", convertir(r.resultado(), EstadoCaptura.class)));
            };
            return resultado.map(r -> r.get("comercial") instanceof ResultadoComercial c
                    ? new ResultadoActuacion(referencia,"EXITO",null,null,null,c)
                    : new ResultadoActuacion(referencia, "EXITO", null,
                            json.convertValue(r, new TypeReference<Map<String, Object>>() { }), null,null))
                    .orElseGet(() -> new ResultadoActuacion(referencia, "EN_VERIFICACION", null, null,
                            "RESULTADO_NO_LOCALIZADO",null));
        });
    }

    @Override
    public List<String> distritos(SesionBrox sesion, Traza traza) {
        var definicion = definicionDe(sesion, "OTRO", null, null, traza);
        return definicion.comunes().stream().filter(p -> "distrito".equals(p.clave()))
                .flatMap(p -> p.opciones() == null ? java.util.stream.Stream.<Opcion>empty() : p.opciones().stream())
                .map(Opcion::valor).filter(v -> v != null && !v.isBlank()).toList();
    }

    @Override
    public List<Pregunta> catalogoDe(SesionBrox sesion, String tipoPropiedad, Traza traza) {
        var definicion = definicionDe(sesion, tipoPropiedad, null, null, traza);
        var todas = new ArrayList<>(definicion.comunes());
        todas.addAll(definicion.delTipo());
        return List.copyOf(todas);
    }

    @Override
    public DefinicionCaptura definicionDe(SesionBrox sesion, String tipoPropiedad,
                                          String operaciones, String jurisdiccion, Traza traza) {
        return llamar(sesion, "catalogo_de_captura", actor -> convertir(
                captura.definicion(MotorDeCaptura.REGISTRAR_PROPIEDAD, tipoPropiedad, operaciones, jurisdiccion, actor),
                DefinicionCaptura.class));
    }

    @Override
    public Map<String,Object> definicionDeInteracciones(SesionBrox sesion, Traza traza) {
        return llamar(sesion, "definicion_de_interacciones", actor -> mapaComercial(interacciones.definicion(actor)));
    }
}




