package com.controllocal.service.capacidades;

import com.controllocal.service.excepcion.CapacidadDeshabilitadaException;
import com.controllocal.service.excepcion.NoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.controllocal.service.capacidades.CapacidadesService.Efecto.*;
import static com.controllocal.service.capacidades.CapacidadesService.Interaccion.*;

/** Registro cerrado de casos existentes. El descriptor público no contiene HTTP, SQL ni secretos. */
@Component
public class RegistroDeCapacidades {
    private static final Set<String> PUBLICACION_C6=Set.of("evaluar_publicabilidad","consultar_publicaciones",
            "consultar_publicacion","consultar_historia_publicacion","crear_borrador_anuncio","editar_borrador_anuncio",
            "publicar_anuncio","actualizar_anuncio_visible","pausar_anuncio","reanudar_anuncio","finalizar_difusion_anuncio");
    private static final Set<String> PIEZA_C6=Set.of("consultar_estado_pieza","consultar_fotos_permitidas_para_pieza",
            "consultar_plantillas_y_formatos_pieza","preparar_pieza_comercial","previsualizar_pieza_comercial",
            "regenerar_pieza_comercial","obtener_pieza_para_difusion","evaluar_foto_local","descargar_archivo_pieza");
    private static final Set<String> MATERIAL_IA_C6=Set.of("preparar_material_ia","solicitar_material_ia",
            "consultar_material_ia","descargar_archivo_material_ia","consultar_seleccion_material_ia","consultar_versiones_material_ia",
            "seleccionar_foto_material_ia","guardar_alternativa_material_ia","descartar_temporal_material_ia",
            "eliminar_alternativa_material_ia",
            "presentar_tratamiento_material_ia","registrar_decision_tratamiento_ia");
    private static final Set<String> C5=Set.of("presentar_expediente_operacion","evaluar_expediente_operacion",
            "informar_hito_contractual","confirmar_hito_contractual","rectificar_hito_contractual",
            "registrar_cobro","asignar_reparto_comision","proponer_adenda_encargo",
            "consultar_adenda_encargo","autorizar_borrador_adenda","consultar_comision","consultar_expediente_operacion",
            "reprogramar_gestion_cobro","concluir_gestion_cobro");
    public record Definicion(String idCore, String familia, CapacidadesService.Efecto efecto,
                            CapacidadesService.Interaccion interaccion, Set<String> recursos,
                            Map<String, Object> esquema) { }

    private final Map<String, Definicion> definiciones;
    private final Set<String> deshabilitadas;

    public RegistroDeCapacidades(@Value("${controllocal.capacidades.deshabilitadas:}") String deshabilitadas) {
        List<Definicion> todas = List.of(
            d("definicion_de_metas","METAS_C7",LECTURA,AUTO,""),
            d("consultar_metas_comerciales","METAS_C7",LECTURA,AUTO,"","mes"),
            d("consultar_propuestas_metas","METAS_C7",LECTURA,AUTO,""),
            d("consultar_propuesta_meta","METAS_C7",LECTURA,AUTO,"PROPUESTA_META","idPropuesta"),
            d("proponer_meta","METAS_C7",ESCRITURA,AVISA,"","mes","propuestaMeta"),
            d("establecer_meta","METAS_C7",ESCRITURA,HUMANO,"ACTUACION","metas"),
            d("decidir_propuesta_meta","METAS_C7",ESCRITURA,HUMANO,"PROPUESTA_META,ACTUACION","decisionMeta"),
            d("consultar_avance_metas","METAS_C7",LECTURA,AUTO,"","mes"),
            d("consultar_pulso_equipo","METAS_C7",LECTURA,AUTO,"","mes"),
            d("consultar_compromisos_prospeccion", "COMPROMISO_R27", LECTURA, AUTO, "PROSPECCION", "idProspeccion"),
            d("asumir_compromiso_prospeccion", "COMPROMISO_R27", ESCRITURA, AVISA, "PROSPECCION", "idProspeccion", "idSeguimiento", "plan"),
            d("acreditar_compromiso_prospeccion", "COMPROMISO_R27", ESCRITURA, AVISA, "PROSPECCION", "idProspeccion", "referencia", "acreditacion"),
            d("resolver_compromiso_prospeccion", "COMPROMISO_R27", ESCRITURA, AVISA, "PROSPECCION", "idProspeccion", "referencia", "resolucion"),
            d("consultar_mediciones_individuales", "INFORME_C7", LECTURA, AUTO, "", "desde", "hasta"),
            d("consultar_derechos_informes", "INFORME_C7", LECTURA, AUTO, ""),
            d("preparar_informe_comercial", "INFORME_C7", LECTURA, AUTO, "", "consulta"),
            d("consultar_programas_personales", "PROGRAMA_PERSONAL", LECTURA, AUTO, "", "pagina", "tamano"),
            d("consultar_programa_personal", "PROGRAMA_PERSONAL", LECTURA, AUTO, "", "referencia"),
            d("configurar_programa_personal", "PROGRAMA_PERSONAL", ESCRITURA, HUMANO, "", "referencia", "versionEsperada", "mandato"),
            d("cambiar_estado_programa_personal", "PROGRAMA_PERSONAL", ESCRITURA, HUMANO, "", "referencia", "versionEsperada", "accion", "motivo", "proximaEn"),
            d("consultar_ocurrencias_programa_personal", "PROGRAMA_PERSONAL", LECTURA, AUTO, "", "referencia", "pagina", "tamano"),
            d("consultar_resultado_programa_personal", "PROGRAMA_PERSONAL", LECTURA, AUTO, "", "referencia", "ocurrencia"),
            d("consultar_capacidades", "CONTRATO", LECTURA, AUTO, "", "idCore", "recurso", "referencia", "operacion", "atributo", "etapa"),
            d("catalogo_de_captura", "CAPTURA", LECTURA, AUTO, "", "tipoPropiedad", "operaciones", "jurisdiccion"),
            d("definicion_de_interacciones", "INTERACCION", LECTURA, AUTO, ""),
            d("buscar_propiedades", "PROPIEDAD", LECTURA, AUTO, "", "texto"),
            d("estado_de_propiedad", "PROPIEDAD", LECTURA, AUTO, "PROPIEDAD", "id"),
            d("consultar_origen_fisico_unidad", "FISICO", LECTURA, AUTO, "PROPIEDAD", "id", "corte"),
            d("registrar_origen_fisico_unidad", "FISICO", ESCRITURA, CONFIRMA, "PROPIEDAD,ACTUACION", "idMatriz", "idUnidad", "vigenteDesde", "vigenteHasta", "situacionRegistral", "fuente", "versionMatrizEsperada", "versionUnidadEsperada"),
            d("editar_propiedad", "PROPIEDAD", ESCRITURA, AVISA, "PROPIEDAD", "id", "atributos", "versionEsperada", "motivoRetiro", "contextoCaptura"),
            d("registrar_observacion_mercado", "PROPIEDAD", ESCRITURA, AVISA, "PROPIEDAD", "id", "fechaObservada", "operacion", "importe", "moneda", "fuente", "detalle"),
            d("buscar_persona", "CLIENTE", LECTURA, AUTO, "", "texto"),
            d("buscar_propietarios", "TITULAR", LECTURA, AUTO, "", "texto"),
            d("consultar_demanda", "DEMANDA", LECTURA, AUTO, "DEMANDA,CLIENTE", "id", "version", "corte"),
            d("registrar_demanda", "DEMANDA", ESCRITURA, AVISA, "", "demanda"),
            d("editar_demanda", "DEMANDA", ESCRITURA, AVISA, "DEMANDA", "id", "versionEsperada", "demanda"),
            d("consultar_disponibilidad", "DISPONIBILIDAD", LECTURA, AUTO, "DEMANDA,ENCARGO", "tipo", "idRecurso"),
            d("consultar_solicitudes_disponibilidad", "DISPONIBILIDAD", ESCRITURA, AVISA, "", "vista", "recurso", "referencia"),
            d("solicitar_confirmacion_inicial", "DISPONIBILIDAD", ESCRITURA, AVISA, "ENCARGO,DEMANDA", "tipo", "recurso", "version"),
            d("responder_solicitud_disponibilidad", "DISPONIBILIDAD", ESCRITURA, AVISA, "RECONFIRMACION", "solicitud", "version", "respuesta", "revisarEn", "motivo"),
            d("confirmar_disponibilidad", "DISPONIBILIDAD", ESCRITURA, AVISA, "DEMANDA,ENCARGO", "tipo", "idRecurso", "versionRecursoObservada", "versionConfirmacionEsperada", "disponible", "fechaHecho", "fechaSinHora", "fuente", "evidenciaRef"),
            d("retirar_finalizar_demanda", "DEMANDA", ESCRITURA, CONFIRMA, "DEMANDA,ACTUACION", "idDemanda", "versionEsperada", "tipo", "fechaDelHecho", "motivo"),
            d("buscar_coincidencias", "MATCHING", LECTURA, AUTO, "DEMANDA,ENCARGO,PROSPECCION,CAPTURA,PROPIEDAD", "origen", "idOrigen", "versionOrigen", "tipoVersion", "modalidad", "capas", "categoria", "tamano", "modoExploracion", "limiteExploracion", "monedaExploracion"),
            d("continuar_coincidencias", "MATCHING", LECTURA, AUTO, "", "cursor"),
            d("explicar_match", "MATCHING", LECTURA, AUTO, "", "cursor", "referenciaDemanda", "referencia"),
            d("solicitar_colaboracion", "COLABORACION", ESCRITURA, CONFIRMA, "DEMANDA,ACTUACION", "idDemanda", "versionDemanda", "ofertaRef", "versionOferta", "alcance"),
            d("invitar_colaboracion_encargo", "COLABORACION", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "encargo", "versionEncargo", "organizacionInvitada", "responsableInvitado", "pacto", "clausulaPermiso", "alcance"),
            d("consultar_invitaciones_colaboracion", "COLABORACION", LECTURA, AUTO, "", "pagina", "tamano"),
            d("consultar_invitaciones_originadas", "COLABORACION", LECTURA, AUTO, "", "pagina", "tamano"),
            d("consultar_acuerdo_colaboracion", "COLABORACION", LECTURA, AUTO, "SOLICITUD_COLABORACION", "referencia"),
            d("decidir_colaboracion", "COLABORACION", ESCRITURA, CONFIRMA, "SOLICITUD_COLABORACION,ACTUACION", "solicitud", "parte", "decision", "alcance", "motivo", "versionSolicitud", "versionAlcance", "versionRecurso"),
            d("revocar_colaboracion", "COLABORACION", ESCRITURA, CONFIRMA, "CONCESION_COLABORACION,ACTUACION", "concesion", "parte", "alcanceEsperado", "motivo", "versionConcesion", "versionAlcance", "versionRecurso"),
            d("declarar_canal_colaboracion", "COLABORACION", ESCRITURA, CONFIRMA, "SOLICITUD_COLABORACION,ACTUACION", "solicitud", "lado", "versionSolicitud", "versionAlcance", "versionRecurso", "canalComercial"),
            d("consultar_avisos_network", "NETWORK", LECTURA, AUTO, "", "pagina", "tamano"),
            d("consultar_participacion_network", "NETWORK", LECTURA, AUTO, "ENCARGO", "idEncargo"),
            d("consultar_solicitud_network", "NETWORK", LECTURA, AUTO, "SOLICITUD_NETWORK", "referencia"),
            d("previsualizar_participacion_network", "NETWORK", LECTURA, AUTO, "ENCARGO", "idEncargo", "campos"),
            d("solicitar_participacion_network", "NETWORK", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "idEncargo", "accion", "campos", "finalidad", "desde", "hasta", "motivo", "versionParticipacion", "huellaDatos", "huellaAutoridad"),
            d("decidir_participacion_network", "NETWORK", ESCRITURA, CONFIRMA, "SOLICITUD_NETWORK,ACTUACION", "solicitud", "decision", "motivo", "versionSolicitud", "versionParticipacion", "huellaDatos", "huellaAutoridad"),
            d("desactivar_participacion_network", "NETWORK", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "idEncargo", "motivo", "versionParticipacion"),
            d("consultar_avisos_broker", "RADAR", ESCRITURA, AVISA, "", "pagina", "tamano", "historia"),
            d("atender_aviso_broker", "RADAR", ESCRITURA, AVISA, "EVENTO_DOMINIO", "evento"),
            d("que_resolver_hoy", "RADAR", ESCRITURA, AVISA, ""),
            d("consultar_radar", "RADAR", ESCRITURA, AVISA, "", "vista", "recurso", "referencia"),
            d("capturas_en_curso", "CAPTURA", LECTURA, AUTO, ""),
            d("consultar_captura", "CAPTURA", LECTURA, AUTO, "CAPTURA", "idBorrador"),
            d("resultado_de_captura", "CAPTURA", LECTURA, AUTO, "CAPTURA", "idBorrador", "claveIdempotencia"),
            d("descartar_captura", "CAPTURA", BORRADOR, HUMANO, "CAPTURA", "idBorrador", "versionEsperada"),
            d("posponer_revision_captura", "CAPTURA", BORRADOR, HUMANO, "CAPTURA", "idBorrador", "versionEsperada", "hasta", "motivo"),
            d("consultar_catalogo_gobernado", "GOBIERNO_CATALOGO", LECTURA, HUMANO, "", "clave", "jurisdiccion"),
            d("versionar_definicion_gobernada", "GOBIERNO_CATALOGO", BORRADOR, HUMANO, "", "id", "versionEsperada", "definicion"),
            d("retirar_definicion_gobernada", "GOBIERNO_CATALOGO", BORRADOR, HUMANO, "", "id", "versionEsperada", "sustituta", "motivo"),
            d("consultar_encargo", "ENCARGO", LECTURA, AUTO, "ENCARGO", "id"),
            d("consultar_vigencia_encargo", "ENCARGO", LECTURA, AUTO, "ENCARGO", "id", "corte"),
            d("preparar_documento_encargo", "ENCARGO", BORRADOR, HUMANO, "", "preparacion"),
            d("regenerar_documento_encargo", "ENCARGO", BORRADOR, HUMANO, "ENCARGO", "id", "preparacion"),
            d("previsualizar_documento_encargo", "ENCARGO", LECTURA, AUTO, "DOCUMENTO_ENCARGO", "id"),
            d("obtener_documento_encargo", "ENCARGO", LECTURA, AUTO, "DOCUMENTO_ENCARGO", "id"),
            d("cargar_archivo_contextual", "ENCARGO", BORRADOR, HUMANO, "ENCARGO,DOCUMENTO_ENCARGO", "encargo", "documento", "finalidad", "versionDestino", "archivo"),
            d("adjuntar_reutilizar_documento", "ENCARGO", ESCRITURA, HUMANO, "ENCARGO,DOCUMENTO_ENCARGO", "referenciaOrigen", "encargo", "documento", "finalidad", "versionDestino"),
            d("consultar_fotos_contextuales", "DOCUMENTOS", LECTURA, AUTO, "PROPIEDAD", "id"),
            d("seleccionar_fotos_contextuales", "DOCUMENTOS", BORRADOR, HUMANO, "PROPIEDAD", "id", "fotosVersionadas", "finalidad", "versionObservada"),
            d("presentar_peticion_encargo", "ENCARGO", ESCRITURA, HUMANO, "ENCARGO", "id", "presentacion"),
            d("consultar_peticion_encargo", "ENCARGO", LECTURA, AUTO, "PETICION_ENCARGO", "id"),
            d("reenviar_peticion_encargo", "ENCARGO", ESCRITURA, HUMANO, "PETICION_ENCARGO", "id", "versionPeticion", "presentacion"),
            d("editar_condiciones_encargo", "ENCARGO", ESCRITURA, HUMANO, "ENCARGO", "id", "edicion"),
            d("consultar_composicion_encargo", "ENCARGO", LECTURA, AUTO, "ENCARGO", "id", "versionComposicion"),
            d("declarar_composicion_encargo", "ENCARGO", ESCRITURA, HUMANO, "ENCARGO", "id", "composicion"),
            d("solicitar_fin_encargo", "ENCARGO", ESCRITURA, HUMANO, "ENCARGO", "id", "solicitud"),
            d("consultar_solicitud_fin_encargo", "ENCARGO", LECTURA, AUTO, "SOLICITUD_FIN_ENCARGO", "id"),
            d("cancelar_solicitud_fin_encargo", "ENCARGO", ESCRITURA, HUMANO, "SOLICITUD_FIN_ENCARGO", "id", "versionEsperada", "motivo"),
            d("decidir_solicitud_fin_encargo", "ENCARGO", ESCRITURA, HUMANO, "SOLICITUD_FIN_ENCARGO", "id", "decision"),
            d("consultar_reasignacion_prospeccion", "GOBIERNO_REASIGNACION", LECTURA, HUMANO, "PROSPECCION", "id"),
            d("reasignar_prospeccion", "GOBIERNO_REASIGNACION", ESCRITURA, HUMANO, "PROSPECCION", "id", "versionEsperada", "responsableObservado", "responsableNuevo", "motivo"),
            d("reasignar_encargo", "GOBIERNO_REASIGNACION", ESCRITURA, HUMANO, "ENCARGO", "id", "reasignacion"),
            d("candidatos_reasignacion_encargo", "GOBIERNO_REASIGNACION", LECTURA, HUMANO, "ENCARGO", "id"),
            d("abrir_sesion_captura", "CAPTURA", BORRADOR, AVISA, "", "intencion", "respuestas", "contexto"),
            d("responder_captura", "CAPTURA", BORRADOR, AVISA, "CAPTURA", "idBorrador", "versionEsperada", "respuestas", "contexto"),
            d("preparar_actuacion_captura", "CAPTURA", BORRADOR, AUTO, "CAPTURA", "idBorrador"),
            d("consultar_actuacion", "ACTUACION", LECTURA, AUTO, "ACTUACION", "referencia"),
            d("revisar_actuacion", "ACTUACION", BORRADOR, HUMANO, "ACTUACION", "referencia"),
            d("confirmar_captura", "CAPTURA", ESCRITURA, HUMANO, "ACTUACION", "referencia", "version", "huella", "pruebaRevision", "eventoHumano"),
            d("atender_turno_kairos", "ASISTENCIA", BORRADOR, AVISA, "", "texto", "conversacionId", "turnoId", "mensajeId"),
            d("registrar_interaccion", "INTERACCION", ESCRITURA, AVISA, "PROPIEDAD,CONTACTO,PROSPECCION,ENCARGO,CLIENTE,OPORTUNIDAD", "vinculos", "destinatario", "interlocutor", "tipo", "canal", "medioReal", "eventos", "resultado", "momento", "resumen", "declaraciones", "proxima", "interaccionAnterior", "fuente", "restriccion", "gestionContacto", "atencionConsultas"),
            d("rectificar_interaccion", "INTERACCION", ESCRITURA, CONFIRMA, "INTERACCION,ACTUACION", "idInteraccion", "versionEsperada", "correccion", "motivo"),
            d("vincular_interaccion", "INTERACCION", ESCRITURA, AVISA, "INTERACCION", "idInteraccion", "versionEsperada", "vinculo", "motivo"),
            d("anotar_observacion_interaccion", "INTERACCION", ESCRITURA, AVISA, "INTERACCION", "idInteraccion", "versionEsperada", "momento", "detalle", "fuente", "resultadoObservado"),
            d("abrir_oportunidad", "ACTIVIDAD", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "codigoOportunidad", "idEncargo", "versionEncargo", "interesado", "observaciones", "idPublicacionOrigen", "idDemanda", "versionDemanda", "origen", "referenciaConsulta", "referenciaCoincidencia", "evidenciaInteres"),
            d("registrar_no_continuidad_oportunidad", "ACTIVIDAD", ESCRITURA, CONFIRMA, "OPORTUNIDAD,ACTUACION", "idOportunidad", "versionEsperada", "razon", "observaciones", "fechaHecho"),
            d("consultar_oportunidad", "ACTIVIDAD", LECTURA, AUTO, "OPORTUNIDAD", "idOportunidad", "seccion", "corte"),
            d("consultar_seguimientos_prospeccion", "PROSPECCION", LECTURA, AUTO, "PROSPECCION", "idProspeccion"),
            d("planificar_seguimiento_prospeccion", "PROSPECCION", ESCRITURA, AVISA, "PROSPECCION", "idProspeccion", "idSeguimiento", "versionEsperada", "origen", "fecha", "detalle"),
            d("posponer_seguimiento_prospeccion", "PROSPECCION", ESCRITURA, AVISA, "PROSPECCION", "idProspeccion", "idSeguimiento", "versionEsperada", "hasta", "motivo"),
            d("consultar_agenda_visitas", "VISITA", LECTURA, AUTO, "", "desde", "hasta", "idOportunidad", "estado", "pagina", "tamano"),
            d("programar_visita", "VISITA", ESCRITURA, AVISA, "OPORTUNIDAD", "idOportunidad", "versionContexto", "fechaVisita", "horaVisita", "observaciones", "participantes", "lugarDeclarado"),
            d("reprogramar_visita", "VISITA", ESCRITURA, AVISA, "VISITA", "idVisita", "versionEsperada", "fechaVisita", "horaVisita", "motivo"),
            d("cancelar_visita", "VISITA", ESCRITURA, CONFIRMA, "VISITA,ACTUACION", "idVisita", "versionEsperada", "motivo", "fechaHecho"),
            d("registrar_resultado_visita", "VISITA", ESCRITURA, AVISA, "VISITA", "idVisita", "resultadoVisita"),
            d("registrar_propietario", "TITULAR", ESCRITURA, AVISA, "", "nombre", "tipoDocumento", "numeroDocumento"),
            d("evaluar_publicabilidad", "PUBLICACION", LECTURA, AUTO, "ENCARGO", "encargo", "canal"),
            d("consultar_publicaciones", "PUBLICACION", LECTURA, AUTO, "ENCARGO", "encargo"),
            d("consultar_publicacion", "PUBLICACION", LECTURA, AUTO, "ENCARGO", "encargo", "publicacion"),
            d("consultar_historia_publicacion", "PUBLICACION", LECTURA, AUTO, "ENCARGO", "encargo", "publicacion"),
            d("crear_borrador_anuncio", "PUBLICACION", BORRADOR, AVISA, "ENCARGO", "encargo", "versionEncargo", "contenido"),
            d("editar_borrador_anuncio", "PUBLICACION", BORRADOR, AVISA, "ENCARGO", "encargo", "versionEncargo", "publicacion", "versionPublicacion", "contenido"),
            d("publicar_anuncio", "PUBLICACION", EXTERNO, CONFIRMA, "ENCARGO,ACTUACION", "encargo", "versionEncargo", "publicacion", "versionPublicacion", "contenido", "estadoExterno", "huellaContexto", "motivo"),
            d("actualizar_anuncio_visible", "PUBLICACION", EXTERNO, CONFIRMA, "ENCARGO,ACTUACION", "encargo", "versionEncargo", "publicacion", "versionPublicacion", "contenido", "estadoExterno", "huellaContexto", "motivo"),
            d("pausar_anuncio", "PUBLICACION", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "encargo", "versionEncargo", "publicacion", "versionPublicacion", "estadoExterno", "huellaContexto", "motivo"),
            d("reanudar_anuncio", "PUBLICACION", EXTERNO, CONFIRMA, "ENCARGO,ACTUACION", "encargo", "versionEncargo", "publicacion", "versionPublicacion", "estadoExterno", "huellaContexto", "motivo"),
            d("finalizar_difusion_anuncio", "PUBLICACION", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "encargo", "versionEncargo", "publicacion", "versionPublicacion", "estadoExterno", "huellaContexto", "motivo"),
            d("consultar_estado_pieza", "PIEZA", LECTURA, AUTO, "PIEZA", "pieza"),
            d("consultar_fotos_permitidas_para_pieza", "PIEZA", LECTURA, AUTO, "ENCARGO", "encargo", "propiedadFicha"),
            d("consultar_plantillas_y_formatos_pieza", "PIEZA", LECTURA, AUTO, "ENCARGO", "encargo"),
            d("preparar_pieza_comercial", "PIEZA", BORRADOR, AVISA, "ENCARGO", "encargo", "versionEncargo", "versionFisica", "canal", "fotos", "versionGaleria", "formato", "tema", "contacto", "propiedadFicha"),
            d("previsualizar_pieza_comercial", "PIEZA", BORRADOR, AVISA, "PIEZA", "pieza", "versionContenido"),
            d("regenerar_pieza_comercial", "PIEZA", BORRADOR, AVISA, "PIEZA", "pieza", "versionContenido", "preparacion"),
            d("obtener_pieza_para_difusion", "PIEZA", EXTERNO, CONFIRMA, "PIEZA,ACTUACION", "pieza", "versionContenido", "huellaOrigen"),
            d("evaluar_foto_local", "FOTOGRAFIA", LECTURA, AUTO, "PROPIEDAD", "propiedad", "original"),
            d("descargar_archivo_pieza", "PIEZA", LECTURA, HUMANO, "PIEZA", "archivo"),
            d("preparar_material_ia", "MATERIAL_IA", LECTURA, AUTO, "PROPIEDAD,ENCARGO", "operacion", "accion", "recurso", "versionOrigen", "fotoOriginal", "instruccion"),
            d("solicitar_material_ia", "MATERIAL_IA", EXTERNO, AVISA, "PROPIEDAD,ENCARGO", "operacion", "accion", "recurso", "versionOrigen", "fotoOriginal", "instruccion"),
            d("consultar_material_ia", "MATERIAL_IA", LECTURA, AUTO, "MATERIAL_IA", "operacion"),
            d("descargar_archivo_material_ia", "MATERIAL_IA", LECTURA, HUMANO, "MATERIAL_IA", "operacion", "archivo"),
            d("consultar_seleccion_material_ia", "MATERIAL_IA", LECTURA, HUMANO, "MATERIAL_IA_FOTO", "original"),
            d("consultar_versiones_material_ia", "MATERIAL_IA", LECTURA, HUMANO, "MATERIAL_IA_FOTO", "original"),
            d("seleccionar_foto_material_ia", "MATERIAL_IA", ESCRITURA, HUMANO, "MATERIAL_IA_FOTO", "original", "operacion", "archivo", "revisionEsperada"),
            d("guardar_alternativa_material_ia", "MATERIAL_IA", ESCRITURA, HUMANO, "MATERIAL_IA", "operacion", "archivo", "revisionEsperada"),
            d("descartar_temporal_material_ia", "MATERIAL_IA", ESCRITURA, HUMANO, "MATERIAL_IA", "operacion", "archivo", "revisionEsperada"),
            d("eliminar_alternativa_material_ia", "MATERIAL_IA", ESCRITURA, HUMANO, "MATERIAL_IA", "operacion", "archivo", "revisionEsperada"),
            d("presentar_tratamiento_material_ia", "TRATAMIENTO_IA", BORRADOR, HUMANO, "PROPIEDAD,ENCARGO", "operacion", "accion", "recurso", "versionOrigen", "fotoOriginal", "instruccion"),
            d("registrar_decision_tratamiento_ia", "TRATAMIENTO_IA", ESCRITURA, HUMANO, "", "evento", "referenciaPresentacion", "versionCobertura", "decision"),
            d("presentar_oferta", "NEGOCIACION", ESCRITURA, CONFIRMA, "OPORTUNIDAD,ACTUACION", "oportunidad", "versionOportunidad", "versionMesa", "proponente", "importe", "objeto", "condiciones", "fechaHecho", "evidencia", "ofertaAnterior", "versionOfertaAnterior"),
            d("registrar_contraoferta", "NEGOCIACION", ESCRITURA, CONFIRMA, "OPORTUNIDAD,ACTUACION", "oportunidad", "versionOportunidad", "versionMesa", "ofertaAnterior", "versionOferta", "proponente", "importe", "objeto", "condiciones", "fechaHecho", "evidencia"),
            d("registrar_aceptacion_oferta", "NEGOCIACION", ESCRITURA, CONFIRMA, "OPORTUNIDAD,ACTUACION", "oportunidad", "versionOportunidad", "versionMesa", "versionEncargo", "oferta", "versionOferta", "parte", "fechaHecho", "evidencia", "motivo"),
            d("registrar_rechazo_oferta", "NEGOCIACION", ESCRITURA, CONFIRMA, "OPORTUNIDAD,ACTUACION", "oportunidad", "versionOportunidad", "versionMesa", "versionEncargo", "oferta", "versionOferta", "parte", "fechaHecho", "evidencia", "motivo"),
            d("registrar_retirada_oferta", "NEGOCIACION", ESCRITURA, CONFIRMA, "OPORTUNIDAD,ACTUACION", "oportunidad", "versionOportunidad", "versionMesa", "versionEncargo", "oferta", "versionOferta", "parte", "fechaHecho", "evidencia", "motivo"),
            d("decidir_captacion", "ENCARGO", ESCRITURA, CONFIRMA, "PETICION_ENCARGO", "id", "actuacion", "versionPeticion", "versionEncargo", "observaciones", "revisionFirma"),
            d("evaluar_solicitud", "EXPEDIENTE", ESCRITURA, CONFIRMA, "SOLICITUD", "idSolicitud", "tipoEvaluacion", "resultado", "observaciones"),
            d("cerrar_encargo", "ENCARGO", ESCRITURA, CONFIRMA, "ENCARGO", "id", "tipoFin", "fechaEfectiva", "motivo", "versionEsperada", "evidencias"),
            d("consultar_expediente_operacion", "ENCARGO", LECTURA, AUTO, "ENCARGO", "expediente"),
            d("presentar_expediente_operacion", "ENCARGO", ESCRITURA, CONFIRMA, "ENCARGO,OPORTUNIDAD,ACTUACION", "oportunidad", "versionOportunidad", "encargo", "versionEncargo", "acuerdo", "versionAcuerdo", "pacto", "documentos", "expediente", "versionEsperada", "contratoPreparado"),
            d("evaluar_expediente_operacion", "ENCARGO", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "expediente", "versionEsperada", "decision", "observaciones", "documentosRevisados", "partesYRepresentacionRevisadas", "composicionYAcuerdoRevisados", "acuerdoFinal", "coberturaEsperada"),
            d("informar_hito_contractual", "ENCARGO", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "expediente", "versionExpediente", "tipo", "fechaEfectiva", "evidencias", "descripcion"),
            d("confirmar_hito_contractual", "ENCARGO", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "hito", "versionHito", "versionExpediente", "revisionExpediente", "revisionInformada", "pacto", "acuerdoFinal"),
            d("rectificar_hito_contractual", "ENCARGO", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "confirmado", "versionHito", "versionExpediente", "fechaEfectiva", "motivo", "evidencias", "acuerdoFinal"),
            d("consultar_comision", "ENCARGO", LECTURA, AUTO, "ENCARGO", "encargo", "revisionPacto", "contexto"),
            d("registrar_cobro", "ENCARGO", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "comision", "versionEsperada", "tramo", "monto", "moneda", "fecha", "formaPago", "parteObligada", "beneficiario", "destino", "referenciaExterna", "evidencias", "observacion"),
            d("asignar_reparto_comision", "ENCARGO", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "comision", "versionEsperada", "montoAgente", "observacion"),
            d("reprogramar_gestion_cobro", "ENCARGO", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "comision", "versionEsperada", "proximaRevision", "motivo"),
            d("concluir_gestion_cobro", "ENCARGO", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "comision", "versionEsperada", "motivo"),
            d("proponer_adenda_encargo", "ENCARGO", BORRADOR, AVISA, "ENCARGO", "anterior", "versionEncargoEsperada", "propuesta", "motivo", "alcance", "vigenteDesde", "vigenteHasta", "antecedentePropuesta"),
            d("consultar_adenda_encargo", "ENCARGO", LECTURA, AUTO, "ENCARGO", "referencia"),
            d("autorizar_borrador_adenda", "ENCARGO", ESCRITURA, CONFIRMA, "ENCARGO,ACTUACION", "referencia", "decision"),
            d("consultar_reportes_propietario","REPORTE_C7",LECTURA,AUTO,"ENCARGO","idEncargo","pagina","tamano"),
            d("consultar_avance_reporte","REPORTE_C7",LECTURA,AUTO,"ENCARGO","idEncargo","desde","hasta"),
            d("consultar_antecedente_reporte","REPORTE_C7",LECTURA,AUTO,"ENCARGO","idAntecedente"),
            d("preparar_reporte_propietario","REPORTE_C7",BORRADOR,AVISA,"ENCARGO","idEncargo","datosReporte"),
            d("previsualizar_reporte_propietario","REPORTE_C7",LECTURA,AUTO,"ENCARGO","reporte"),
            d("obtener_reporte_propietario","REPORTE_C7",LECTURA,AUTO,"ENCARGO","reporte"),
            d("consultar_comunicaciones_reporte","REPORTE_C7",LECTURA,AUTO,"ENCARGO","idEncargo","pagina","tamano"),
            d("registrar_comunicacion_reporte_realizada","REPORTE_C7",ESCRITURA,AVISA,"ENCARGO","datosComunicacion"),
            d("rectificar_comunicacion_reporte","REPORTE_C7",ESCRITURA,CONFIRMA,"ENCARGO,ACTUACION","rectificacion")
        );
        Map<String, Definicion> indice = new LinkedHashMap<>();
        for (Definicion d : todas) {
            if (indice.put(d.idCore(), d) != null) throw new IllegalStateException("Capacidad duplicada: " + d.idCore());
        }
        this.definiciones = Collections.unmodifiableMap(indice);
        this.deshabilitadas = deshabilitadas == null || deshabilitadas.isBlank() ? Set.of()
                : Set.copyOf(Arrays.stream(deshabilitadas.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        if (!indice.keySet().containsAll(this.deshabilitadas)) {
            throw new IllegalArgumentException("La configuración deshabilita una capacidad no reconocida.");
        }
    }

    public List<Definicion> todas() { return List.copyOf(definiciones.values()); }
    public Definicion de(String idCore) {
        Definicion d = definiciones.get(idCore);
        if (d == null) throw new NoEncontradoException("Capacidad");
        return d;
    }
    public boolean habilitadas(Collection<String> ids) {
        ids.forEach(this::de);
        return ids.stream().noneMatch(deshabilitadas::contains);
    }
    public void exigirHabilitadas(Collection<String> ids) {
        if (!habilitadas(ids)) throw new CapacidadDeshabilitadaException();
    }

    private static Definicion d(String id, String familia, CapacidadesService.Efecto efecto,
                                CapacidadesService.Interaccion interaccion, String recursos, String... entradas) {
        Map<String, Object> propiedades = new LinkedHashMap<>();
        for (String entrada : entradas) {
            Map<String,Object> tipo;
            if(Set.of("COMPROMISO_R27","INFORME_C7","PROGRAMA_PERSONAL","REPORTE_C7","METAS_C7").contains(familia)) {
                tipo=tipoC7(entrada);
            } else if(MATERIAL_IA_C6.contains(id)) {
                tipo=tipoMaterialIa(entrada);
                if("solicitar_material_ia".equals(id)&&"versionOrigen".equals(entrada))tipo=Map.of("type","string","minLength",1);
                if("seleccionar_foto_material_ia".equals(id)&&Set.of("operacion","archivo").contains(entrada))tipo=Map.of("type",List.of("string","null"),"format","uuid");
            } else if(PIEZA_C6.contains(id)) {
                tipo=tipoPieza(entrada);
            } else if(PUBLICACION_C6.contains(id)) {
                tipo=tipoPublicacion(entrada);
            } else if(C5.contains(id)) {
                tipo=tipoC5(id,entrada);
            } else if("ENCARGO".equals(familia)&&"presentacion".equals(entrada)) {
                tipo=presentacionManuscrita();
            } else if("decidir_captacion".equals(id)&&"revisionFirma".equals(entrada)) {
                tipo=Map.of("type",List.of("object","null"),"properties",Map.of(
                        "revisionPresentacion",Map.of("type","integer","minimum",1),
                        "huellaManifestacion",Map.of("type","string"),
                        "documentoYFirmantesRevisados",Map.of("type","boolean")),
                        "required",List.of("revisionPresentacion","huellaManifestacion","documentoYFirmantesRevisados"),
                        "additionalProperties",false,
                        "description","Revisión humana ligada a la manifestación exacta; sólo es exigida al aprobar la presentación. No sustituye la aprobación humana verificable ni la genera el modelo.");
            } else if("INTERACCION".equals(familia)&&"atencionConsultas".equals(entrada)) {
                tipo=atencionConsultas();
            } else if("INTERACCION".equals(familia)&&"gestionContacto".equals(entrada)) {
                var definicion=com.controllocal.service.actividad.CatalogoInteracciones.definicion();
                tipo=Map.of("type","object","additionalProperties",false,"properties",Map.of(
                        "finalidades",Map.of("type","array","uniqueItems",true,"items",opcionesConocidas(definicion.finalidadesContacto())),
                        "funcionesInterlocutor",Map.of("type","array","uniqueItems",true,"items",opcionesConocidas(definicion.funcionesContacto())),
                        "fuenteFunciones",Map.of("type","string"),"continuarConInterlocutor",Map.of("type","boolean"),
                        "respondeAIntento",Map.of("type","object","properties",Map.of("id",Map.of("type","integer","minimum",1),"version",Map.of("type","integer","minimum",0)),"required",List.of("id","version"),"additionalProperties",false)));
            } else if("INTERACCION".equals(familia)&&Set.of("tipo","canal","resultado","resultadoObservado").contains(entrada)) {
                var definicion=com.controllocal.service.actividad.CatalogoInteracciones.definicion();
                var opciones=switch(entrada) {
                    case "tipo" -> definicion.tipos();
                    case "canal" -> definicion.canales();
                    default -> definicion.resultados();
                };
                tipo=Map.of("type","string","enum",opciones.stream().map(com.controllocal.service.actividad.CatalogoInteracciones.Opcion::valor).toList(),
                        "oneOf",opciones.stream().map(o->Map.of("const",o.valor(),"title",o.rotulo())).toList());
            } else if("INTERACCION".equals(familia)&&"restriccion".equals(entrada)) {
                tipo=Map.of("type","object","properties",Map.of(
                        "declaracion",Map.of("type","string"),
                        "destinatario",Map.of("type","object"),
                        "finalidad",opcionesConocidas(com.controllocal.service.actividad.CatalogoInteracciones.definicion().finalidadesRestriccionConocidas()),
                        "fuente",Map.of("type","string")));
            } else if("INTERACCION".equals(familia)&&Set.of("destinatario","interlocutor").contains(entrada)) {
                tipo=Map.of("type","object","properties",Map.of(
                        "contacto",Map.of("type","object"),
                        "tipoDeclarado",opcionesConocidas(com.controllocal.service.actividad.CatalogoInteracciones.definicion().relacionesDeclaradasParaRecontacto()),
                        "nombre",Map.of("type","string"),"telefono",Map.of("type","string"),
                        "referenciaDeclarada",Map.of("type","string","description","Referencia contextual entregada por Core; no se inventa ni se infiere por nombre o teléfono.")));
            } else if("consultar_oportunidad".equals(id)&&"seccion".equals(entrada)) {
                tipo=Map.of("type","string","enum",Arrays.stream(com.controllocal.service.OportunidadService.Seccion.values()).map(Enum::name).toList());
            } else if (Set.of("INTERACCION","ACTIVIDAD","VISITA","NEGOCIACION").contains(familia)
                    && Set.of("vinculos","eventos","declaraciones","participantes").contains(entrada)) {
                tipo=Map.of("type","array","items",Map.of("type","object"));
            } else if (Set.of("INTERACCION","ACTIVIDAD","VISITA","NEGOCIACION").contains(familia)
                    && Set.of("destinatario","interlocutor","momento","proxima","restriccion","correccion","vinculo","interesado","evidenciaInteres","resultadoVisita","proponente","importe","objeto","parte").contains(entrada)) {
                tipo=Map.of("type","object");
            } else if (Set.of("versionOportunidad","versionMesa","versionOfertaAnterior","versionContexto","oportunidad","ofertaAnterior","oferta","interaccionAnterior","pagina").contains(entrada)) {
                tipo=Map.of("type","integer");
            } else if ("atributos".equals(entrada)) {
                tipo = Map.of("type","array","items",Map.of("type","object",
                        "properties",Map.of("clave",Map.of("type","string"),"valor",Map.of("type","string"),
                                "moneda",Map.of("type","string"),"valores",Map.of("type","array","items",Map.of("type","string")),
                                "naturaleza",Map.of("type","string"),"confianza",Map.of("type","number"),
                                "observadoEn",Map.of("type","string","format","date"),"evidenciaRef",Map.of("type","string"),
                                "captura",Map.of("type","object","properties",Map.of(
                                        "estado",Map.of("type","string","enum",List.of("CONOCIDO","FALTANTE","AUSENCIA_DECLARADA","NO_APLICABLE")),
                                        "concepto",Map.of("type","string"),"referenciaCatalogo",Map.of("type","string"),
                                        "versionDefinicion",Map.of("type","string"),"noSabe",Map.of("type","boolean")),
                                        "required",List.of("estado"),"additionalProperties",false)),
                        "required",List.of("clave"),"additionalProperties",false));
            } else if ("archivo".equals(entrada)) tipo=Map.of("type","string","format","binary",
                    "description","Entrada binaria en superficie humana segura; el modelo sólo conserva la referencia permitida.");
            else if ("fotosVersionadas".equals(entrada)) tipo=Map.of("type","array","items",Map.of("type","string","format","uuid"));
            else if ("referenciaOrigen".equals(entrada)) tipo=Map.of("type","string","format","uuid");
            else if ("respuestas".equals(entrada)) tipo = Map.of("type","object","additionalProperties",
                    Map.of("type","object","properties",Map.of(
                            "valor",Map.of("type","string"),
                            "estado",Map.of("type","string","enum",List.of("CONOCIDO","FALTANTE","AUSENCIA_DECLARADA","NO_APLICABLE")),
                            "evidenciaRef",Map.of("type","string"),"concepto",Map.of("type","string"),
                            "referenciaCatalogo",Map.of("type","string"),"versionDefinicion",Map.of("type","string"),
                            "observadoEn",Map.of("type","string","format","date"),"noSabe",Map.of("type","boolean")),
                            "additionalProperties",false));
            else if ("contextoCaptura".equals(entrada)) tipo=Map.of("type","object");
            else if ("importe".equals(entrada)&&"registrar_observacion_mercado".equals(id)) tipo=Map.of("type","number");
            else if ("contexto".equals(entrada) && "CAPTURA".equals(familia)) tipo=Map.of("type","object");
            else if("disponible".equals(entrada)&&"DISPONIBILIDAD".equals(familia))tipo=Map.of("type","boolean");
            else if("fechaHecho".equals(entrada))tipo=Map.of("type","string","format",
                    Set.of("ACTIVIDAD","VISITA","NEGOCIACION").contains(familia)?"date":"date-time");
            else if("PROSPECCION".equals(familia)&&"fecha".equals(entrada))tipo=Map.of("type",List.of("string","null"),"format","date");
            else if("PROSPECCION".equals(familia)&&"hasta".equals(entrada))tipo=Map.of("type","string","format","date");
            else if("PROSPECCION".equals(familia)&&"origen".equals(entrada))tipo=Map.of("type","string","enum",
                    Arrays.stream(com.controllocal.service.InteraccionService.OrigenActuacion.values()).map(Enum::name).toList());
            else if("NEGOCIACION".equals(familia)&&"evidencia".equals(entrada))tipo=Map.of("type","string");
            else if(Set.of("fechaSinHora","fechaDelHecho").contains(entrada))tipo=Map.of("type","string","format","date");
            else if(Set.of("COLABORACION","NETWORK").contains(familia)&&Set.of("solicitud","decision").contains(entrada))
                tipo=Map.of("type","string");
            else if(Set.of("campos","capas").contains(entrada)) tipo=Map.of("type","array","items",Map.of("type","string"),"uniqueItems",true);
            else if (Set.of("demanda","alcance","alcanceEsperado","definicion","preparacion","presentacion","edicion","composicion","solicitud","decision","reasignacion","evidencia").contains(entrada)) tipo=Map.of("type","object");
            else if ("evidencias".equals(entrada)) tipo=Map.of("type","array","items",Map.of("type","string","format","uuid"));
            else if ("datos".equals(entrada)) tipo = Map.of("type","object","additionalProperties",Map.of("type","string"));
            else if("MATCHING".equals(familia)&&"modoExploracion".equals(entrada))tipo=Map.of("type","string","enum",List.of("DESCUBRIR","EXPLORAR"));
            else if("MATCHING".equals(familia)&&"limiteExploracion".equals(entrada))tipo=Map.of("type","number");
            else if("MATCHING".equals(familia)&&"idOrigen".equals(entrada))tipo=Map.of("type","integer");
            else tipo = Map.of("type", entrada.startsWith("id") && !Set.of("idCore","idOrigen").contains(entrada)
                    || Set.of("versionOrigen","versionDemanda","versionOferta","versionSolicitud","versionAlcance",
                              "versionRecurso","versionConcesion","versionParticipacion","versionRecursoObservada",
                              "versionConfirmacionEsperada").contains(entrada) || "tamano".equals(entrada)
                    || Set.of("consultasReportadas","visitasReportadas","versionEsperada","versionPeticion","versionEncargo","versionComposicion","versionDestino","versionMatrizEsperada","versionUnidadEsperada","encargo","documento").contains(entrada) ? "integer" : "string");
            propiedades.put(entrada,tipo);
        }
        // Identidades y versiones exigidas por los DTO/casos vigentes; nunca completar por defecto.
        Map<String, Object> esquema = new LinkedHashMap<>(Map.of("type", "object", "properties", Collections.unmodifiableMap(propiedades),
                "additionalProperties", false));
        if(Set.of("COMPROMISO_R27","INFORME_C7","PROGRAMA_PERSONAL","REPORTE_C7","METAS_C7").contains(familia))esquema.put("required",requeridosC7(id));
        else if(MATERIAL_IA_C6.contains(id))esquema.put("required",switch(id){
            case "preparar_material_ia","presentar_tratamiento_material_ia"->List.of("operacion","accion","recurso");
            case "solicitar_material_ia"->List.of("operacion","accion","recurso","versionOrigen");
            case "descargar_archivo_material_ia"->List.of("operacion","archivo");
            case "consultar_seleccion_material_ia","consultar_versiones_material_ia"->List.of("original");
            case "seleccionar_foto_material_ia"->List.of("original","operacion","archivo","revisionEsperada");
            case "guardar_alternativa_material_ia","descartar_temporal_material_ia","eliminar_alternativa_material_ia"->List.of("operacion","archivo","revisionEsperada");
            case "registrar_decision_tratamiento_ia"->List.of("evento","referenciaPresentacion","versionCobertura","decision");
            default->List.of("operacion");
        });
        else if(PIEZA_C6.contains(id))esquema.put("required",requeridosPieza(id));
        else if(PUBLICACION_C6.contains(id))esquema.put("required",requeridosPublicacion(id));
        else if(C5.contains(id))esquema.put("required",requeridosC5(id));
        else if(Set.of("INTERACCION","ACTIVIDAD","VISITA","NEGOCIACION").contains(familia)
                || Set.of("consultar_seguimientos_prospeccion","planificar_seguimiento_prospeccion","posponer_seguimiento_prospeccion").contains(id))
            esquema.put("required",requeridosDeActividad(id));
        if("seleccionar_foto_material_ia".equals(id))esquema.put("oneOf",List.of(
                Map.of("properties",Map.of("operacion",Map.of("type","null"),"archivo",Map.of("type","null"))),
                Map.of("properties",Map.of("operacion",Map.of("type","string","format","uuid"),"archivo",Map.of("type","string","format","uuid")))));
        return new Definicion(id, familia, efecto, interaccion,
                recursos.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(recursos.split(","))), Collections.unmodifiableMap(esquema));
    }

    private static List<String> requeridosC7(String id){return switch(id){
        case "consultar_propuesta_meta"->List.of("idPropuesta");
        case "proponer_meta"->List.of("mes","propuestaMeta");
        case "establecer_meta"->List.of("metas");
        case "decidir_propuesta_meta"->List.of("decisionMeta");
        case "consultar_reportes_propietario","consultar_comunicaciones_reporte"->List.of("idEncargo");
        case "consultar_avance_reporte"->List.of("idEncargo","desde","hasta");
        case "consultar_antecedente_reporte"->List.of("idAntecedente");
        case "preparar_reporte_propietario"->List.of("idEncargo","datosReporte");
        case "previsualizar_reporte_propietario","obtener_reporte_propietario"->List.of("reporte");
        case "registrar_comunicacion_reporte_realizada"->List.of("datosComunicacion");
        case "rectificar_comunicacion_reporte"->List.of("rectificacion");
        case "consultar_compromisos_prospeccion"->List.of("idProspeccion");
        case "asumir_compromiso_prospeccion"->List.of("idProspeccion","idSeguimiento","plan");
        case "acreditar_compromiso_prospeccion"->List.of("idProspeccion","referencia","acreditacion");
        case "resolver_compromiso_prospeccion"->List.of("idProspeccion","referencia","resolucion");
        case "preparar_informe_comercial"->List.of("consulta");
        case "configurar_programa_personal"->List.of("mandato");
        case "cambiar_estado_programa_personal"->List.of("referencia","versionEsperada","accion","motivo");
        case "consultar_resultado_programa_personal"->List.of("referencia","ocurrencia");
        case "consultar_programa_personal","consultar_ocurrencias_programa_personal"->List.of("referencia");
        default->List.of();};}
    private static Map<String,Object> tipoC7(String campo){return switch(campo){
        case "mes"->Map.of("type","string","pattern","^[0-9]{4}-[0-9]{2}$");
        case "idPropuesta"->Map.of("type","integer","minimum",1);
        case "metas"->esquemaC7(com.controllocal.service.MetaComercialService.Establecer.class);
        case "propuestaMeta"->esquemaC7(com.controllocal.service.MetaComercialService.Propuesta.class);
        case "decisionMeta"->esquemaC7(com.controllocal.service.MetaComercialService.Decision.class);
        case "pagina","tamano","idProspeccion","idSeguimiento","idEncargo","idAntecedente"->Map.of("type","integer","minimum",1);
        case "versionEsperada"->Map.of("type",List.of("integer","null"),"minimum",0);
        case "referencia","ocurrencia"->Map.of("type",List.of("string","null"),"format","uuid");
        case "proximaEn"->Map.of("type",List.of("string","null"),"format","date-time");
        case "accion"->Map.of("type","string","enum",List.of("PAUSAR","REANUDAR","CANCELAR"));
        case "motivo"->Map.of("type","string","minLength",1);
        case "desde","hasta"->Map.of("type",List.of("string","null"),"format","date");
        case "reporte"->esquemaC7(com.controllocal.service.ReportePropietarioService.Referencia.class);
        case "datosReporte"->esquemaC7(com.controllocal.service.ReportePropietarioService.DatosReporte.class);
        case "datosComunicacion"->esquemaC7(com.controllocal.service.ReportePropietarioService.DatosComunicacion.class);
        case "rectificacion"->esquemaC7(com.controllocal.service.ReportePropietarioService.Rectificacion.class);
        case "plan"->esquemaC7(com.controllocal.service.actividad.CompromisosDeSeguimiento.Plan.class);
        case "acreditacion"->esquemaC7(com.controllocal.service.actividad.CompromisosDeSeguimiento.Acreditacion.class);
        case "resolucion"->esquemaC7(com.controllocal.service.actividad.CompromisosDeSeguimiento.Resolucion.class);
        case "consulta"->esquemaC7(com.controllocal.service.actividad.InformesC7.Consulta.class);
        case "mandato"->esquemaC7(com.controllocal.service.actividad.ProgramasPersonales.Configuracion.class);
        default->throw new IllegalArgumentException("Campo C7 no reconocido: "+campo);};}

    /** Esquema de los records de entrada vigentes, no de una segunda definición de negocio. */
    private static Map<String,Object> esquemaC7(Class<?> tipo){
        if(tipo==String.class)return Map.of("type","string");
        if(tipo==long.class||tipo==Long.class||tipo==int.class||tipo==Integer.class)return Map.of("type","integer");
        if(tipo==boolean.class||tipo==Boolean.class)return Map.of("type","boolean");
        if(tipo==java.math.BigDecimal.class)return Map.of("type","number");
        if(tipo==java.util.UUID.class)return Map.of("type","string","format","uuid");
        if(tipo==java.time.Instant.class)return Map.of("type","string","format","date-time");
        if(tipo==java.time.LocalDate.class)return Map.of("type","string","format","date");
        if(tipo.isEnum())return Map.of("type","string","enum",Arrays.stream(tipo.getEnumConstants()).map(Object::toString).toList());
        if(!tipo.isRecord())throw new IllegalStateException("Entrada C7 sin esquema reconocido: "+tipo.getName());
        List<String> requeridos=switch(tipo.getSimpleName()){
            case "Establecer"->List.of("mes","asignaciones");
            case "Asignacion"->List.of("idRolAgente","kpi","operacion","versionEsperada","valor","motivo");
            case "Propuesta"->List.of("kpi","operacion","versionMetaEsperada","valor","motivo");
            case "Decision"->List.of("propuesta","versionEsperada","decision","motivo");
            case "Referencia"->List.of("referencia","version");
            case "Destinatario"->List.of("contacto","funcionDeclarada","procedenciaDeclaracion");
            case "DatosReporte"->List.of("versionEncargo","periodoInicio","periodoFin","destinatarioPrevisto","finalidad","plantillaVersion");
            case "DatosComunicacion"->List.of("reporte","destinatarioReal","fechaReal","canal","constancia");
            case "Rectificacion"->List.of("comunicacion","versionEsperada","destinatarioReal","fechaReal","canal","constancia","motivo");
            case "ReferenciaContactoContextual"->List.of("tipo");
            case "DatosContacto"->List.of("nombre");
            case "Plan"->List.of("versionSeguimiento","origen","fecha","actividad","detalle");
            case "Acreditacion"->List.of("versionCompromiso","interaccion","constanciaContenido");
            case "Resolucion"->List.of("versionCompromiso","fecha","motivo","evidencia");
            case "Consulta"->List.of("composicion");
            case "Filtros"->List.of();
            case "Situacion"->List.of("recurso","codigo");
            case "Configuracion"->List.of("familia","finalidad","proximaEn","venceEn","frecuenciaSegundos","maxOcurrencias","maxIntentos","destinoPrivado");
            case "Condicion"->List.of("metrica","comparador","umbral");
            case "Recurso"->List.of("tipo","id","version");
            case "ReferenciaVersion"->List.of("id","version");
            default->throw new IllegalStateException("Record C7 no declarado: "+tipo.getName());};
        var campos=new LinkedHashMap<String,Object>();
        for(var c:tipo.getRecordComponents()){
            var valor=esquemaTipoC7(c.getGenericType());
            if(tipo.getEnclosingClass()==com.controllocal.service.MetaComercialService.class&&"kpi".equals(c.getName()))valor=Map.of("type","string","enum",Arrays.stream(com.controllocal.service.soporte.IndicadorMetaComercial.values()).map(Enum::name).toList());
            if(tipo.getEnclosingClass()==com.controllocal.service.MetaComercialService.class&&"operacion".equals(c.getName()))valor=Map.of("type","string","enum",List.of("GENERAL","VENTA","ALQUILER"));
            if(tipo==com.controllocal.service.actividad.CompromisosDeSeguimiento.Plan.class&&"origen".equals(c.getName()))valor=Map.of("type","string","enum",List.of("INTENCION_PROFESIONAL","ACUERDO_EXTERNO"));
            if(tipo==com.controllocal.service.actividad.ProgramasPersonales.Configuracion.class&&"destinoPrivado".equals(c.getName()))valor=Map.of("type","string","enum",List.of("BANDEJA_PRIVADA"));
            if(tipo==com.controllocal.service.actividad.ProgramasPersonales.Recurso.class&&"tipo".equals(c.getName()))valor=Map.of("type","string","enum",List.of("ENCARGO","PROSPECCION"));
            if((tipo==com.controllocal.service.actividad.FiltrosDeInforme.Filtros.class&&"operacion".equals(c.getName()))||tipo==com.controllocal.service.actividad.InformesC7.Consulta.class&&"operacionCohorte".equals(c.getName()))valor=Map.of("type","string","enum",List.of("VENTA","ALQUILER"));
            if(tipo==com.controllocal.service.actividad.FiltrosDeInforme.Situacion.class&&"recurso".equals(c.getName()))valor=Map.of("type","string","enum",List.of("ENCARGO","OPORTUNIDAD","PROSPECCION"));
            campos.put(c.getName(),requeridos.contains(c.getName())||c.getType().isPrimitive()?valor:Map.of("anyOf",List.of(valor,Map.of("type","null"))));
        }
        return Map.of("type","object","properties",campos,"required",requeridos,"additionalProperties",false);
    }

    private static Map<String,Object> esquemaTipoC7(java.lang.reflect.Type tipo){
        if(tipo instanceof java.lang.reflect.ParameterizedType p&&p.getRawType()==List.class)
            return Map.of("type","array","items",esquemaTipoC7(p.getActualTypeArguments()[0]));
        if(tipo instanceof Class<?> c)return esquemaC7(c);
        throw new IllegalStateException("Tipo de entrada C7 no reconocido: "+tipo);
    }
    private static Map<String,Object> tipoMaterialIa(String campo){
        return switch(campo){
            case "operacion","archivo","original","evento","referenciaPresentacion"->Map.of("type","string","format","uuid");
            case "revisionEsperada","versionCobertura"->Map.of("type","integer","minimum",0);
            case "decision"->Map.of("type","string","enum",Arrays.stream(com.controllocal.service.materialia.EvidenciasTratamientoMaterialIa.Decision.values()).map(Enum::name).toList());
            case "fotoOriginal"->Map.of("type",List.of("string","null"),"format","uuid");
            case "accion"->Map.of("type","string","enum",Arrays.stream(com.controllocal.service.materialia.MaterialIaService.Accion.values()).map(Enum::name).toList());
            case "recurso"->Map.of("type","object","properties",Map.of(
                    "tipo",Map.of("type","string","enum",List.of("PROPIEDAD","ENCARGO")),
                    "referencia",Map.of("type","string","pattern","^[1-9][0-9]*$")),
                    "required",List.of("tipo","referencia"),"additionalProperties",false);
            default->Map.of("type",List.of("string","null"));
        };
    }
    private static Map<String,Object> tipoPieza(String campo){
        if("propiedadFicha".equals(campo))return Map.of("type",List.of("integer","null"),"minimum",1);
        if(Set.of("encargo","propiedad","versionEncargo","versionFisica","versionContenido").contains(campo))
            return Map.of("type","integer","minimum",Set.of("versionFisica","versionEncargo").contains(campo)?0:1);
        if(Set.of("pieza","original","archivo").contains(campo))return Map.of("type","string","format","uuid");
        if("fotos".equals(campo))return Map.of("type","array","items",Map.of("type","object","properties",Map.of(
                "original",Map.of("type","string","format","uuid"),"ambiente",Map.of("type","string","minLength",1),
                "rotulo",Map.of("type","string","minLength",1),
                "derivada",Map.of("type",List.of("object","null"),"properties",Map.of(
                        "operacion",Map.of("type","string","format","uuid"),"archivo",Map.of("type","string","format","uuid"),
                        "versionSeleccion",Map.of("type","integer","minimum",1)),
                        "required",List.of("operacion","archivo","versionSeleccion"),"additionalProperties",false)),
                "required",List.of("original"),"additionalProperties",false));
        if("contacto".equals(campo))return Map.of("type",List.of("object","null"),"properties",Map.of(
                "tipo",Map.of("type","string","enum",List.of("WHATSAPP","TELEFONO","CORREO","WEB")),
                "valor",Map.of("type","string","minLength",1)),"required",List.of("tipo","valor"),"additionalProperties",false);
        if("formato".equals(campo))return Map.of("type","string","enum",Arrays.stream(com.controllocal.service.documentos.PiezasComercialesService.Formato.values()).map(Enum::name).toList());
        if("tema".equals(campo))return Map.of("type","string","enum",List.of("marfil","claro"));
        if("preparacion".equals(campo)){
            var campos=new LinkedHashMap<String,Object>();
            for(String nombre:List.of("encargo","versionEncargo","versionFisica","canal","fotos","versionGaleria","formato","tema","contacto","propiedadFicha"))
                campos.put(nombre,tipoPieza(nombre));
            return Map.of("type","object","properties",campos,"required",requeridosPieza("preparar_pieza_comercial"),"additionalProperties",false);
        }
        return Map.of("type","string");
    }
    private static List<String> requeridosPieza(String id){return switch(id){
        case "consultar_estado_pieza" -> List.of("pieza");
        case "consultar_fotos_permitidas_para_pieza","consultar_plantillas_y_formatos_pieza" -> List.of("encargo");
        case "preparar_pieza_comercial" -> List.of("encargo","versionEncargo","versionFisica","versionGaleria","formato");
        case "regenerar_pieza_comercial" -> List.of("pieza","versionContenido","preparacion");
        case "obtener_pieza_para_difusion" -> List.of("pieza","versionContenido","huellaOrigen");
        case "evaluar_foto_local" -> List.of("propiedad","original");
        case "descargar_archivo_pieza" -> List.of("archivo");
        default -> List.of("pieza","versionContenido");
    };}

    private static Map<String,Object> tipoPublicacion(String campo) {
        if("motivo".equals(campo))return Map.of("type","string","minLength",1,"maxLength",2000);
        if("canal".equals(campo))return Map.of("type","string","enum",
                com.controllocal.domain.inmueble.Publicacion.CANALES.stream().sorted().toList());
        if(Set.of("encargo","publicacion","versionEncargo","versionPublicacion").contains(campo))
            return Map.of("type","integer","minimum","versionEncargo".equals(campo)?0:1);
        if("contenido".equals(campo))return Map.of("type",List.of("object","null"),"properties",Map.of(
                "canal",Map.of("type",List.of("string","null"),"enum",canalesPublicacion()),
                "urlPublicacion",Map.of("type",List.of("string","null"),"maxLength",500),
                "importePublicado",Map.of("type",List.of("number","null")),
                "moneda",Map.of("type",List.of("string","null"),"enum",Arrays.asList("PEN","USD",null)),
                "tituloAnuncio",Map.of("type",List.of("string","null"),"maxLength",200),
                "codigoOrigen",Map.of("type",List.of("string","null"),"maxLength",50)),"additionalProperties",false);
        if("estadoExterno".equals(campo))return Map.of("type",List.of("object","null"),"properties",Map.of(
                "estado",Map.of("type","string","minLength",1),
                "fechaEfectiva",Map.of("type","string","format","date-time"),
                "procedencia",Map.of("type","string","minLength",1)),
                "required",List.of("estado","fechaEfectiva","procedencia"),"additionalProperties",false);
        return Map.of("type","string");
    }
    private static List<String> canalesPublicacion(){
        var opciones=new ArrayList<>(com.controllocal.domain.inmueble.Publicacion.CANALES.stream().sorted().toList());
        opciones.add(null);return Collections.unmodifiableList(opciones);
    }
    private static List<String> requeridosPublicacion(String id) {
        return switch(id) {
            case "evaluar_publicabilidad","consultar_publicaciones" -> List.of("encargo");
            case "consultar_publicacion","consultar_historia_publicacion" -> List.of("encargo","publicacion");
            case "crear_borrador_anuncio" -> List.of("encargo","versionEncargo");
            case "pausar_anuncio","finalizar_difusion_anuncio" -> List.of("encargo","versionEncargo","publicacion","versionPublicacion","motivo");
            default -> List.of("encargo","versionEncargo","publicacion","versionPublicacion");
        };
    }

    /** RF015/016 y BROX4.6.2/5.7.7: el instrumento se aporta una vez, por su firmante contextual. */
    private static Map<String,Object> presentacionManuscrita() {
        var firmante=Map.of("type","object","properties",Map.of(
                "titularidad",Map.of("type","integer","minimum",1),
                "persona",Map.of("type","integer","minimum",1)),
                "required",List.of("titularidad","persona"),"additionalProperties",false);
        var firma=Map.of("type","object","properties",Map.of(
                "firmante",firmante,"fechaFirma",Map.of("type","string","format","date"),
                "documentoFirmado",Map.of("type","string","format","uuid"),
                "descripcion",Map.of("type","string","minLength",1)),
                "required",List.of("firmante","fechaFirma","documentoFirmado","descripcion"),
                "additionalProperties",false);
        return Map.of("type","object","properties",Map.of(
                "documento",Map.of("type","integer","minimum",1),
                "versionEsperada",Map.of("type","integer","minimum",0),
                "partes",Map.of("type","array","items",Map.of("type","object")),
                "firmas",Map.of("type","array","minItems",1,"items",firma),
                "respuestaObservaciones",Map.of("type",List.of("string","null"))),
                "required",List.of("documento","versionEsperada","partes","firmas"),
                "additionalProperties",false,
                "description","Documento con firma manuscrita del titular o representante legitimado sobre la versión exacta. Presentar crea una petición pendiente; no autentica la firma ni aprueba el Encargo.");
    }

    private static Map<String,Object> opcionesConocidas(List<com.controllocal.service.actividad.CatalogoInteracciones.ValorContextual> opciones) {
        return Map.of("type",List.of("string","null"),
                "examples",opciones.stream().map(com.controllocal.service.actividad.CatalogoInteracciones.ValorContextual::valor).toList(),
                "description","Opciones conocidas, sin cerrar otros valores ni exigir uno desconocido: "+
                        opciones.stream().map(o->o.valor()+" = "+o.rotulo()).collect(java.util.stream.Collectors.joining("; ")));
    }

    private static Map<String,Object> tipoC5(String id,String campo) {
        if(Set.of("documentos","documentosRevisados","evidencias").contains(campo))
            return Map.of("type","array","items",documentoC5());
        if(Set.of("pacto","anterior").contains(campo))return pactoC5();
        if("confirmado".equals(campo))return Map.of("type","object","properties",Map.of(
                "id",Map.of("type","integer","minimum",1),"revision",Map.of("type","integer","minimum",1)),
                "required",List.of("id","revision"),"additionalProperties",false);
        if("referencia".equals(campo))return Map.of("type","object","properties",Map.of(
                "encargo",Map.of("type","integer","minimum",1),"propuesta",Map.of("type","integer","minimum",1)),
                "required",List.of("encargo","propuesta"),"additionalProperties",false);
        if("acuerdoFinal".equals(campo))return Map.of("type",List.of("object","null"),"properties",Map.of(
                "precioVenta",dineroC5(),"rentaMensual",dineroC5(),"declaradoEnEscritura",dineroC5(),
                "documentos",Map.of("type","array","items",documentoC5()),"descripcion",Map.of("type","string"),
                "terminosAlquiler",Map.of("type","object","properties",Map.of(
                    "inicio",Map.of("type","string","format","date"),"fin",Map.of("type","string","format","date")),"additionalProperties",false)),
                "additionalProperties",false);
        if(Set.of("parteObligada","beneficiario").contains(campo))return sujetoC5();
        if("destino".equals(campo))return Map.of("type","object","properties",Map.of(
                "datoDestino",Map.of("type","string"),"titular",sujetoC5()),
                "required",List.of("datoDestino","titular"),"additionalProperties",false);
        if("decision".equals(campo))return "evaluar_expediente_operacion".equals(id)
                ?Map.of("type","string","enum",List.of("APROBAR","OBSERVAR","RECHAZAR"))
                :Map.of("type","object");
        if("tipo".equals(campo))return Map.of("type","string","enum",Arrays.stream(com.controllocal.domain.comercial.TipoHitoContractual.values()).map(Enum::name).toList());
        if(Set.of("monto","montoAgente").contains(campo))return Map.of("type","number");
        if(Set.of("partesYRepresentacionRevisadas","composicionYAcuerdoRevisados").contains(campo))return Map.of("type","boolean");
        if(Set.of("fecha","fechaEfectiva","vigenteDesde","vigenteHasta","proximaRevision").contains(campo))return Map.of("type","string","format","date");
        if("motivo".equals(campo))return Map.of("type","string","minLength",1,"maxLength",300);
        if(Set.of("propuesta","coberturaEsperada").contains(campo))return Map.of("type","object");
        if(campo.startsWith("version")||Set.of("encargo","oportunidad","acuerdo","expediente","hito",
                "revisionExpediente","revisionInformada","revisionPacto","comision","tramo","contexto","contratoPreparado").contains(campo))
            return Map.of("type","integer");
        return Map.of("type","string");
    }
    private static Map<String,Object> documentoC5() {
        return Map.of("type","object","properties",Map.of("referencia",Map.of("type","string","format","uuid"),
                "version",Map.of("type","integer","minimum",0),"huella",Map.of("type","string","pattern","^[a-fA-F0-9]{64}$")),
                "required",List.of("referencia","version","huella"),"additionalProperties",false);
    }
    private static Map<String,Object> pactoC5() {
        return Map.of("type","object","properties",Map.of("encargo",Map.of("type","integer","minimum",1),
                "revisionAprobatoria",Map.of("type","integer","minimum",1)),
                "required",List.of("encargo","revisionAprobatoria"),"additionalProperties",false);
    }
    private static Map<String,Object> dineroC5() {
        return Map.of("type",List.of("object","null"),"properties",Map.of("importe",Map.of("type","number"),
                "moneda",Map.of("type","string"),"base",Map.of("type","string"),"periodo",Map.of("type","string")),
                "additionalProperties",false);
    }
    private static Map<String,Object> sujetoC5() {
        return Map.of("type","object","properties",Map.of("etiqueta",Map.of("type","string"),"referencia",Map.of(
                "type","object","properties",Map.of("tipo",Map.of("type","string","enum",List.of("PERSONA","ORGANIZACION","CAMPO_PACTO","CAMPO_DOCUMENTO")),
                "id",Map.of("type","integer"),"pacto",pactoC5(),"documento",documentoC5(),"localizador",Map.of("type","string")),
                "required",List.of("tipo"),"additionalProperties",false)),
                "required",List.of("referencia","etiqueta"),"additionalProperties",false);
    }
    private static List<String> requeridosC5(String id) {return switch(id) {
        case "presentar_expediente_operacion" -> List.of("oportunidad","versionOportunidad","encargo","versionEncargo","pacto","documentos");
        case "evaluar_expediente_operacion" -> List.of("expediente","versionEsperada","decision");
        case "informar_hito_contractual" -> List.of("expediente","versionExpediente","tipo");
        case "confirmar_hito_contractual" -> List.of("hito","versionHito","versionExpediente","revisionExpediente","revisionInformada","pacto");
        case "rectificar_hito_contractual" -> List.of("confirmado","versionHito","versionExpediente","motivo","evidencias");
        case "registrar_cobro" -> List.of("comision","versionEsperada","monto","moneda","fecha","parteObligada","beneficiario","destino","evidencias");
        case "asignar_reparto_comision" -> List.of("comision","versionEsperada","montoAgente");
        case "reprogramar_gestion_cobro" -> List.of("comision","versionEsperada","proximaRevision","motivo");
        case "concluir_gestion_cobro" -> List.of("comision","versionEsperada","motivo");
        case "autorizar_borrador_adenda" -> List.of("referencia","decision");
        default -> List.of();
    };}

    private static List<String> requeridosDeActividad(String id) {
        return switch(id) {
            case "consultar_seguimientos_prospeccion" -> List.of("idProspeccion");
            case "planificar_seguimiento_prospeccion" -> List.of("idProspeccion","idSeguimiento","versionEsperada","origen","detalle");
            case "posponer_seguimiento_prospeccion" -> List.of("idProspeccion","idSeguimiento","versionEsperada","hasta","motivo");
            case "rectificar_interaccion" -> List.of("idInteraccion","versionEsperada","correccion","motivo");
            case "vincular_interaccion" -> List.of("idInteraccion","versionEsperada","vinculo","motivo");
            case "anotar_observacion_interaccion" -> List.of("idInteraccion","versionEsperada","detalle","fuente");
            case "abrir_oportunidad" -> List.of("idEncargo","versionEncargo","interesado","origen","evidenciaInteres");
            case "consultar_oportunidad" -> List.of("idOportunidad","seccion");
            case "registrar_no_continuidad_oportunidad" -> List.of("idOportunidad","versionEsperada","razon","fechaHecho");
            case "programar_visita" -> List.of("idOportunidad","versionContexto","fechaVisita","horaVisita");
            case "reprogramar_visita" -> List.of("idVisita","versionEsperada","fechaVisita","horaVisita","motivo");
            case "cancelar_visita" -> List.of("idVisita","versionEsperada","fechaHecho","motivo");
            case "registrar_resultado_visita" -> List.of("idVisita","resultadoVisita");
            case "consultar_agenda_visitas" -> List.of("desde","hasta");
            case "presentar_oferta" -> List.of("oportunidad","versionOportunidad","versionMesa","proponente","importe","objeto","fechaHecho","evidencia");
            case "registrar_contraoferta" -> List.of("oportunidad","versionOportunidad","versionMesa","ofertaAnterior","versionOferta","proponente","importe","objeto","fechaHecho","evidencia");
            case "registrar_aceptacion_oferta","registrar_rechazo_oferta","registrar_retirada_oferta" -> List.of("oportunidad","versionOportunidad","versionMesa","versionEncargo","oferta","versionOferta","parte","fechaHecho","evidencia");
            default -> List.of();
        };
    }
    private static Map<String,Object> atencionConsultas(){
        var momento=Map.of("type",List.of("object","null"),"properties",Map.of(
                "fecha",Map.of("type",List.of("string","null"),"format","date"),"hora",Map.of("type",List.of("string","null")),"offsetConocido",Map.of("type",List.of("string","null"))),"additionalProperties",false);
        var referencia=Map.of("type",List.of("object","null"),"properties",Map.of("id",Map.of("type","integer","minimum",1),"version",Map.of("type","integer","minimum",0)),"required",List.of("id","version"),"additionalProperties",false);
        return Map.of("type",List.of("object","null"),"properties",Map.of(
                "recibida",Map.of("type",List.of("object","null"),"properties",Map.of("evento",Map.of("type","string"),"momento",momento),"required",List.of("evento"),"additionalProperties",false),
                "respuestas",Map.of("type","array","items",Map.of("type","object","properties",Map.of("consulta",referencia,"evento",Map.of("type","string"),"momento",momento,"constancia",Map.of("type","string","minLength",1)),"required",List.of("evento","constancia"),"additionalProperties",false))),
                "additionalProperties",false,"description","Consulta comercial y respuestas efectivamente comunicadas. Referencias con versión y permiso; consulta nula sólo para responder en esta misma conversación. No deducir por canal, proximidad o llamada sin respuesta.");
    }
}
