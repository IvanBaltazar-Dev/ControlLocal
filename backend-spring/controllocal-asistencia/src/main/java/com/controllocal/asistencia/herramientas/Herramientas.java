package com.controllocal.asistencia.herramientas;

import com.controllocal.asistencia.herramientas.Herramienta.Estado;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <b>El catalogo de D-K-1 §3, como codigo.</b>
 *
 * <h2>Por que existe siendo ya un documento</h2>
 * Porque una tabla en un documento no impide que alguien anada una llamada al
 * Core sin decir por que caso de uso entra ni que se confirma. Aqui cada
 * herramienta tiene que declarar las cuatro cosas o no compila, y las que
 * todavia no se implementan se declaran <b>igual</b>: el dia que se implementen
 * no se vuelve a decidir si publicar sale al exterior.
 *
 * <h2>Lo que este catalogo NO es</h2>
 * <ul>
 *   <li><b>No es la autorizacion.</b> Quien puede lo decide el Core con el
 *       token de la persona; {@code permiso} es la celda de la matriz copiada
 *       para poder compararla, no una comprobacion.</li>
 *   <li><b>No es la lista de capacidades de la sesion.</b> Esa la publica el
 *       Core mediante {@code GET /capacidades} y
 *       solo puede <b>restringir</b>: puede exigir confirmacion donde el
 *       catalogo no la exige, y no puede quitarla donde si.</li>
 *   <li><b>No es un segundo motor.</b> Ninguna herramienta tiene logica: cada
 *       una nombra un metodo y una ruta que ya existen.</li>
 * </ul>
 *
 * <h2>Lo que este catalogo declara y D-K-1 §3 no</h2>
 * {@link #BUSCAR_PERSONA} y {@link #REGISTRAR_PROPIETARIO} las invoca KAIROS
 * desde antes de este catalogo, y {@link #CAPTURAS_EN_CURSO} es la lectura con
 * la que se retoma un borrador. Estan declaradas porque <b>omitirlas seria
 * mentir por ausencia</b>: el catalogo tiene que decir todo lo que KAIROS sabe
 * invocar, no solo lo que el documento previo alcanzo a nombrar.
 *
 * <p>Por esa misma regla estan las <b>tres lecturas de contrato</b>
 * —{@link #CONSULTAR_CAPACIDADES}, {@link #CATALOGO_DE_CAPTURA} y
 * {@link #DEFINICION_DE_INTERACCIONES}—, que no son intenciones que nadie
 * exprese: son lo que KAIROS le pregunta al Core para poder preguntar bien.
 * Faltaban, y su ausencia tenia dos consecuencias medibles: tres llamadas
 * salian sin {@code X-Herramienta}, y la del vocabulario de interacciones
 * viajaba etiquetada como {@code registrar_interaccion} —una lectura firmada
 * con el nombre de una escritura—.
 */
public final class Herramientas {

    // ==================================================================
    // 3.1 Lectura. Nunca necesitan confirmacion: no escriben.
    // ==================================================================

    /**
     * Buscar en la cartera por texto.
     *
     * <p>La puerta universal de D-K-1 es {@code /propiedades}; su respuesta
     * incluye la ficha comun y los encargos de cada propiedad.
     */
    public static final Herramienta BUSCAR_PROPIEDADES = new Herramienta(
            "buscar_propiedades", "GET", "/propiedades", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /** La ficha completa por el modelo universal: titulares, atributos y encargos. */
    public static final Herramienta ESTADO_DE_PROPIEDAD = new Herramienta(
            "estado_de_propiedad", "GET", "/propiedades/{id}", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /** No esta en D-K-1 §3. Se declara porque KAIROS ya la invoca. */
    public static final Herramienta BUSCAR_PERSONA = new Herramienta(
            "buscar_persona", "GET", "/clientes", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    public static final Herramienta BUSCAR_PROPIETARIOS = new Herramienta(
            "buscar_propietarios", "GET", "/propietarios", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    public static final Herramienta CONSULTAR_CAPTURA = new Herramienta(
            "consultar_captura", "GET", "/captura/{id}", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    public static final Herramienta CONSULTAR_ACTUACION = new Herramienta(
            "consultar_actuacion", "GET", "/actuaciones/{referencia}", Herramienta.AGENTE,
            Confirmacion.NUNCA, Estado.VIVA);

    /** Contratos de consulta, continuación y explicación reautorizada de Core. */
    public static final Herramienta BUSCAR_COINCIDENCIAS = new Herramienta(
            "buscar_coincidencias", "GET", "/coincidencias", Herramienta.TODOS, Confirmacion.NUNCA, Estado.VIVA);
    public static final Herramienta CONTINUAR_COINCIDENCIAS = new Herramienta(
            "continuar_coincidencias", "GET", "/coincidencias/continuacion/{cursor}", Herramienta.TODOS, Confirmacion.NUNCA, Estado.VIVA);
    public static final Herramienta EXPLICAR_MATCH = new Herramienta(
            "explicar_match", "GET", "/coincidencias/explicaciones/{cursor}/{referenciaDemanda}/{referencia}", Herramienta.TODOS, Confirmacion.NUNCA, Estado.VIVA);

    /**
     * <b>El foco del Inicio</b>: lo que hay que resolver hoy, con la politica de
     * despacho del Core.
     *
     * <p>Va por {@code /dashboard} y no por {@code /tareas} a proposito:
     * {@code /tareas} es <b>solo del AGENTE</b> —el unico recurso sin acceso de
     * ADMIN—, y {@code que_resolver_hoy} tiene que responderle tambien al
     * broker, cuyo foco son <i>sus</i> asuntos y no la bandeja de nadie
     * (D-E2-5). El dashboard compone las dos colas y los hallazgos con el
     * alcance de cada rol.
     *
     * <p><b>Este GET escribe.</b> La bandeja se deriva y se reconcilia antes de
     * devolverse: no es una lectura pura aunque sea un GET, y por eso no se
     * llama «por si acaso» en cada turno.
     */
    public static final Herramienta QUE_RESOLVER_HOY = new Herramienta(
            "que_resolver_hoy", "GET", "/dashboard", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /** No esta en D-K-1 §3: es la lectura con la que se retoma lo que quedo a medias. */
    public static final Herramienta CAPTURAS_EN_CURSO = new Herramienta(
            "capturas_en_curso", "GET", "/captura", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    // ------------------------------------------------------------------
    // Lecturas de contrato: lo que KAIROS pregunta para poder preguntar bien
    // ------------------------------------------------------------------

    /**
     * <b>Que puede hacer esta sesion</b>, segun el Core.
     *
     * <p>KAIROS la invoca en <b>todos</b> los turnos, antes de decidir nada. No
     * esta en D-K-1 §3 porque no es una intencion que nadie exprese; se declara
     * porque sale por el cable, y una llamada que sale en cada turno sin figurar
     * en el catalogo es exactamente lo que el catalogo existe para impedir.
     *
     * <p>C0/K0 publica el contrato versionado. Su ausencia o incompatibilidad
     * detiene la actuación dependiente; no habilita una política local sustitutiva.
     */
    public static final Herramienta CONSULTAR_CAPACIDADES = new Herramienta(
            "consultar_capacidades", "GET", "/capacidades", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /**
     * <b>Que se pregunta para un tipo de propiedad</b>, derivado del catalogo del
     * tenant.
     *
     * <p>La invoca el interprete, no el adaptador: es lo que le permite
     * reconocer «3 dormitorios» sin llevar una matriz «tipo → campos» propia. Y
     * es la misma ruta con la que se piden los distritos, porque los distritos
     * son las opciones de una de sus preguntas.
     */
    public static final Herramienta CATALOGO_DE_CAPTURA = new Herramienta(
            "catalogo_de_captura", "GET", "/captura/definicion", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    /**
     * Definición de comunicación C4 publicada por Core, sin catálogo local de resultados.
     *
     * <p>El puerto interno reutiliza el vocabulario canónico de Core. Una lista
     * vacía autorizada se conserva como tal; permiso, deshabilitación y fallo
     * no se convierten en vocabulario vacío ni en una lista local inventada.
     *
     * <p>Se declara aparte de {@link #REGISTRAR_INTERACCION} porque es otra cosa:
     * una <b>lectura</b> de vocabulario. Iba etiquetada con el nombre de la
     * escritura, y un rastro que firma una consulta como si fuera un alta
     * responde mal a la unica pregunta que existe para responder.
     */
    public static final Herramienta DEFINICION_DE_INTERACCIONES = new Herramienta(
            "definicion_de_interacciones", "GET", "/interacciones/definicion", Herramienta.TODOS,
            Confirmacion.NUNCA, Estado.VIVA);

    // ==================================================================
    // 3.2 Escritura reversible. Se ejecutan y se avisan.
    // ==================================================================

    /**
     * Abrir el alta conversacional. <b>No escribe nada del negocio</b>: anota lo
     * conocido y responde que falta.
     */
    public static final Herramienta ABRIR_SESION_CAPTURA = new Herramienta(
            "abrir_sesion_captura", "POST", "/captura", Herramienta.TODOS,
            Confirmacion.AVISA, Estado.VIVA);

    /**
     * Contestar una pregunta del alta. Misma ruta que abrirla; lo que las
     * distingue es que esta lleva {@code idBorrador}.
     *
     * <p>D-K-1 §3.2 la nombra {@code POST …/respuestas} y esa ruta no existe:
     * el motor del Core es <b>un solo</b> {@code POST /captura} que abre o
     * continua. Se declara la que existe.
     */
    public static final Herramienta RESPONDER_CAPTURA = new Herramienta(
            "responder_captura", "POST", "/captura", Herramienta.TODOS,
            Confirmacion.AVISA, Estado.VIVA);

    /** Comunicación declarada; rectificar conserva el antecedente y usa su caso propio. */
    public static final Herramienta REGISTRAR_INTERACCION = new Herramienta(
            "registrar_interaccion", "POST", "/interacciones", Herramienta.AGENTE,
            Confirmacion.AVISA, Estado.VIVA);

    /** No esta en D-K-1 §3. Se declara porque KAIROS ya la invoca. */
    public static final Herramienta REGISTRAR_PROPIETARIO = new Herramienta(
            "registrar_propietario", "POST", "/propietarios", Herramienta.AGENTE,
            Confirmacion.AVISA, Estado.VIVA);

    // ==================================================================
    // 3.3 Escritura sensible. SIEMPRE la confirma una persona.
    // ==================================================================

    /**
     * Ejecutar el alta: crea la propiedad, su titularidad y —si se declaro
     * alguna operacion— sus encargos.
     *
     * <p>Es la unica sensible implementada, y es la que prueba que la regla 4
     * se sostiene: el turno que completa el borrador <b>propone</b>, y ejecutar
     * exige otro turno con la persona diciendo que si.
     */
    public static final Herramienta PREPARAR_ACTUACION_CAPTURA = new Herramienta(
            "preparar_actuacion_captura", "POST", "/actuaciones/capturas/{id}/propuestas", Herramienta.AGENTE,
            Confirmacion.AVISA, Estado.VIVA);

    /** C6: los cinco efectos sensibles preparan revisión; esta herramienta nunca confirma. */
    public static final Herramienta EVALUAR_PUBLICABILIDAD=new Herramienta("evaluar_publicabilidad","GET","/encargos/{idEncargo}/publicaciones/publicabilidad",Herramienta.TODOS,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_PUBLICACIONES=new Herramienta("consultar_publicaciones","GET","/encargos/{idEncargo}/publicaciones",Herramienta.TODOS,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_PUBLICACION=new Herramienta("consultar_publicacion","GET","/encargos/{idEncargo}/publicaciones/{idPublicacion}",Herramienta.TODOS,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_HISTORIA_PUBLICACION=new Herramienta("consultar_historia_publicacion","GET","/encargos/{idEncargo}/publicaciones/{idPublicacion}/historia",Herramienta.TODOS,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CREAR_BORRADOR_ANUNCIO=new Herramienta("crear_borrador_anuncio","POST","/encargos/{idEncargo}/publicaciones/borradores",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta EDITAR_BORRADOR_ANUNCIO=new Herramienta("editar_borrador_anuncio","PUT","/encargos/{idEncargo}/publicaciones/{idPublicacion}/borrador",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta PUBLICAR_ANUNCIO=new Herramienta("publicar_anuncio","POST","/encargos/{idEncargo}/publicaciones/{idPublicacion}/publicacion/preparacion",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta ACTUALIZAR_ANUNCIO_VISIBLE=new Herramienta("actualizar_anuncio_visible","POST","/encargos/{idEncargo}/publicaciones/{idPublicacion}/actualizacion-visible/preparacion",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta PAUSAR_ANUNCIO=new Herramienta("pausar_anuncio","POST","/encargos/{idEncargo}/publicaciones/{idPublicacion}/pausa/preparacion",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta REANUDAR_ANUNCIO=new Herramienta("reanudar_anuncio","POST","/encargos/{idEncargo}/publicaciones/{idPublicacion}/reanudacion/preparacion",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta FINALIZAR_DIFUSION_ANUNCIO=new Herramienta("finalizar_difusion_anuncio","POST","/encargos/{idEncargo}/publicaciones/{idPublicacion}/fin-difusion/preparacion",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta CONSULTAR_ESTADO_PIEZA=new Herramienta("consultar_estado_pieza","GET","/piezas/{pieza}",Herramienta.AGENTE,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_FOTOS_PERMITIDAS_PARA_PIEZA=new Herramienta("consultar_fotos_permitidas_para_pieza","GET","/encargos/{idEncargo}/piezas/fotos",Herramienta.AGENTE,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_PLANTILLAS_Y_FORMATOS_PIEZA=new Herramienta("consultar_plantillas_y_formatos_pieza","GET","/encargos/{idEncargo}/piezas/plantillas",Herramienta.AGENTE,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta PREPARAR_PIEZA_COMERCIAL=new Herramienta("preparar_pieza_comercial","POST","/encargos/{idEncargo}/piezas",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta PREVISUALIZAR_PIEZA_COMERCIAL=new Herramienta("previsualizar_pieza_comercial","POST","/piezas/{pieza}/previsualizacion",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta REGENERAR_PIEZA_COMERCIAL=new Herramienta("regenerar_pieza_comercial","POST","/piezas/{pieza}/regeneracion",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta OBTENER_PIEZA_PARA_DIFUSION=new Herramienta("obtener_pieza_para_difusion","POST","/piezas/{pieza}/difusion/preparar",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta EVALUAR_FOTO_LOCAL=new Herramienta("evaluar_foto_local","GET","/propiedades/{idPropiedad}/fotos/{referencia}/evaluacion",Herramienta.AGENTE,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta PREPARAR_MATERIAL_IA=new Herramienta("preparar_material_ia","POST","/material-ia/preparaciones",Herramienta.AGENTE,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta SOLICITAR_MATERIAL_IA=new Herramienta("solicitar_material_ia","POST","/material-ia/solicitudes",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta CONSULTAR_MATERIAL_IA=new Herramienta("consultar_material_ia","GET","/material-ia/{operacion}",Herramienta.AGENTE,Confirmacion.NUNCA,Estado.VIVA);

    /** Puerta vigente de {@code OfertasController}; KAIROS aun no la invoca. */
    public static final Herramienta PRESENTAR_OFERTA = new Herramienta(
            "presentar_oferta", "POST", "/oportunidades/{id}/ofertas/propuestas", Herramienta.TODOS,
            Confirmacion.AVISA, Estado.VIVA);

    /** Es una firma del broker: quien entra a cartera. */
    public static final Herramienta DECIDIR_CAPTACION = new Herramienta(
            "decidir_captacion", "POST", "/encargos/peticiones/{id}/decision", Herramienta.BROKER,
            Confirmacion.SIEMPRE, Estado.DECLARADA);

    /** Es una firma del broker: la decision que desemboca en contrato y comision. */
    public static final Herramienta EVALUAR_SOLICITUD = new Herramienta(
            "evaluar_solicitud", "POST", "/evaluaciones", Herramienta.BROKER,
            Confirmacion.SIEMPRE, Estado.DECLARADA);

    /** Termina una relacion comercial y devuelve el inmueble al mercado o no. */
    public static final Herramienta CERRAR_ENCARGO = new Herramienta(
            "cerrar_encargo", "POST", "/encargos/{id}/fin", Herramienta.BROKER,
            Confirmacion.SIEMPRE, Estado.DECLARADA);

    /** Es dinero: evidencia economica de cobro, pago al agente o reversion. */
    public static final Herramienta REGISTRAR_COBRO = new Herramienta(
            "registrar_cobro", "POST", "/comisiones/movimientos/propuestas",
            Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);

    /** RF078/079: preparar/obtener no envía; la rectificación conserva revisión exacta. */
    public static final Herramienta DEFINICION_DE_METAS=new Herramienta("definicion_de_metas","GET","/indicadores/metas/definicion",Herramienta.TODOS,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_METAS_COMERCIALES=new Herramienta("consultar_metas_comerciales","GET","/indicadores/metas",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_PROPUESTAS_METAS=new Herramienta("consultar_propuestas_metas","GET","/indicadores/metas/propuestas",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_PROPUESTA_META=new Herramienta("consultar_propuesta_meta","GET","/indicadores/metas/propuestas/{id}",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta PROPONER_META=new Herramienta("proponer_meta","POST","/indicadores/metas/propuestas",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta ESTABLECER_META=new Herramienta("establecer_meta","POST","/indicadores/metas/establecimiento/propuestas",Herramienta.AGENTE_BROKER,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta DECIDIR_PROPUESTA_META=new Herramienta("decidir_propuesta_meta","POST","/indicadores/metas/decisiones/propuestas",Herramienta.AGENTE_BROKER,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta CONSULTAR_AVANCE_METAS=new Herramienta("consultar_avance_metas","GET","/indicadores/metas/rendimiento",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_PULSO_EQUIPO=new Herramienta("consultar_pulso_equipo","GET","/indicadores/metas/pulso",Herramienta.BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_REPORTES_PROPIETARIO=new Herramienta("consultar_reportes_propietario","GET","/captaciones/{id}/reportes-propietario",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_AVANCE_REPORTE=new Herramienta("consultar_avance_reporte","GET","/captaciones/{id}/reportes-propietario/avance",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_ANTECEDENTE_REPORTE=new Herramienta("consultar_antecedente_reporte","GET","/reportes-propietario/antecedentes/{id}",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta PREPARAR_REPORTE_PROPIETARIO=new Herramienta("preparar_reporte_propietario","POST","/captaciones/{id}/reportes-propietario",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta PREVISUALIZAR_REPORTE_PROPIETARIO=new Herramienta("previsualizar_reporte_propietario","GET","/reportes-propietario/{referencia}/versiones/{version}",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta OBTENER_REPORTE_PROPIETARIO=new Herramienta("obtener_reporte_propietario","POST","/reportes-propietario/{referencia}/versiones/{version}/archivo",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_COMUNICACIONES_REPORTE=new Herramienta("consultar_comunicaciones_reporte","GET","/captaciones/{id}/reportes-propietario/comunicaciones",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta REGISTRAR_COMUNICACION_REPORTE_REALIZADA=new Herramienta("registrar_comunicacion_reporte_realizada","POST","/reportes-propietario/comunicaciones",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta RECTIFICAR_COMUNICACION_REPORTE=new Herramienta("rectificar_comunicacion_reporte","POST","/reportes-propietario/comunicaciones/{referencia}/rectificacion/propuestas",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);

    // ==================================================================

    public static final Herramienta ABRIR_OPORTUNIDAD = new Herramienta("abrir_oportunidad", "POST", "/oportunidades/propuestas", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta RECTIFICAR_INTERACCION = new Herramienta("rectificar_interaccion", "POST", "/interacciones/{id}/rectificacion/propuestas", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta VINCULAR_INTERACCION = new Herramienta("vincular_interaccion", "POST", "/interacciones/{id}/vinculos", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta ANOTAR_OBSERVACION_INTERACCION = new Herramienta("anotar_observacion_interaccion", "POST", "/interacciones/{id}/observaciones", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta CONSULTAR_SEGUIMIENTOS_PROSPECCION = new Herramienta("consultar_seguimientos_prospeccion", "GET", "/prospecciones/{id}/seguimientos", Herramienta.TODOS, Confirmacion.NUNCA, Estado.VIVA);
    public static final Herramienta PLANIFICAR_SEGUIMIENTO_PROSPECCION = new Herramienta("planificar_seguimiento_prospeccion", "POST", "/prospecciones/{id}/seguimientos/{idSeguimiento}/planificacion", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta POSPONER_SEGUIMIENTO_PROSPECCION = new Herramienta("posponer_seguimiento_prospeccion", "POST", "/prospecciones/{id}/seguimientos/{idSeguimiento}/posposicion", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta REGISTRAR_NO_CONTINUIDAD_OPORTUNIDAD = new Herramienta("registrar_no_continuidad_oportunidad", "POST", "/oportunidades/{id}/no-continuidad/propuestas", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta CONSULTAR_AGENDA_VISITAS = new Herramienta("consultar_agenda_visitas", "GET", "/visitas/agenda", Herramienta.TODOS, Confirmacion.NUNCA, Estado.VIVA);
    public static final Herramienta PROGRAMAR_VISITA = new Herramienta("programar_visita", "POST", "/visitas", Herramienta.TODOS, Confirmacion.NUNCA, Estado.VIVA);
    public static final Herramienta CANCELAR_VISITA = new Herramienta("cancelar_visita", "POST", "/visitas/{id}/cancelacion/propuestas", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta REPROGRAMAR_VISITA = new Herramienta("reprogramar_visita", "PUT", "/visitas/{id}/programacion", Herramienta.TODOS, Confirmacion.NUNCA, Estado.VIVA);
    public static final Herramienta REGISTRAR_RESULTADO_VISITA = new Herramienta("registrar_resultado_visita", "POST", "/visitas/{id}/resultado", Herramienta.TODOS, Confirmacion.NUNCA, Estado.VIVA);
    public static final Herramienta REGISTRAR_CONTRAOFERTA = new Herramienta("registrar_contraoferta", "POST", "/oportunidades/{id}/contraofertas/propuestas", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta REGISTRAR_ACEPTACION_OFERTA = new Herramienta("registrar_aceptacion_oferta", "POST", "/oportunidades/{id}/aceptaciones/propuestas", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta REGISTRAR_RECHAZO_OFERTA = new Herramienta("registrar_rechazo_oferta", "POST", "/oportunidades/{id}/rechazos/propuestas", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta REGISTRAR_RETIRADA_OFERTA = new Herramienta("registrar_retirada_oferta", "POST", "/oportunidades/{id}/retiradas/propuestas", Herramienta.TODOS, Confirmacion.AVISA, Estado.VIVA);
    public static final Herramienta CONSULTAR_OPORTUNIDAD = new Herramienta("consultar_oportunidad", "GET", "/oportunidades/{id}", Herramienta.TODOS, Confirmacion.NUNCA, Estado.VIVA);

    public static final Herramienta PRESENTAR_EXPEDIENTE_OPERACION=new Herramienta("presentar_expediente_operacion","POST","/formalizacion/expedientes/propuestas",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta EVALUAR_EXPEDIENTE_OPERACION=new Herramienta("evaluar_expediente_operacion","POST","/formalizacion/evaluaciones/propuestas",Herramienta.TODOS,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta INFORMAR_HITO_CONTRACTUAL=new Herramienta("informar_hito_contractual","POST","/formalizacion/hitos/informes/propuestas",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta CONFIRMAR_HITO_CONTRACTUAL=new Herramienta("confirmar_hito_contractual","POST","/formalizacion/hitos/confirmaciones/propuestas",Herramienta.TODOS,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta RECTIFICAR_HITO_CONTRACTUAL=new Herramienta("rectificar_hito_contractual","POST","/formalizacion/hitos/rectificaciones/propuestas",Herramienta.TODOS,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta ASIGNAR_REPARTO_COMISION=new Herramienta("asignar_reparto_comision","POST","/comisiones/repartos/propuestas",Herramienta.TODOS,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta AUTORIZAR_BORRADOR_ADENDA=new Herramienta("autorizar_borrador_adenda","POST","/encargos/{id}/adendas/{propuesta}/decisiones/propuestas",Herramienta.TODOS,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta REPROGRAMAR_GESTION_COBRO=new Herramienta("reprogramar_gestion_cobro","POST","/comisiones/seguimientos/propuestas",Herramienta.TODOS,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta CONCLUIR_GESTION_COBRO=new Herramienta("concluir_gestion_cobro","POST","/comisiones/conclusiones/propuestas",Herramienta.TODOS,Confirmacion.AVISA,Estado.VIVA);

    public static final Herramienta CONSULTAR_COMPROMISOS_PROSPECCION=new Herramienta("consultar_compromisos_prospeccion","GET","/prospecciones/{id}/compromisos",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta ASUMIR_COMPROMISO_PROSPECCION=new Herramienta("asumir_compromiso_prospeccion","POST","/prospecciones/{id}/compromisos/seguimientos/{seguimiento}",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta ACREDITAR_COMPROMISO_PROSPECCION=new Herramienta("acreditar_compromiso_prospeccion","POST","/prospecciones/{id}/compromisos/{referencia}/actuacion",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta RESOLVER_COMPROMISO_PROSPECCION=new Herramienta("resolver_compromiso_prospeccion","POST","/prospecciones/{id}/compromisos/{referencia}/resolucion",Herramienta.AGENTE,Confirmacion.AVISA,Estado.VIVA);
    public static final Herramienta CONSULTAR_MEDICIONES_INDIVIDUALES=new Herramienta("consultar_mediciones_individuales","GET","/indicadores/mediciones",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_DERECHOS_INFORMES=new Herramienta("consultar_derechos_informes","GET","/indicadores/informes/derechos",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta PREPARAR_INFORME_COMERCIAL=new Herramienta("preparar_informe_comercial","POST","/indicadores/informes",Herramienta.AGENTE_BROKER,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_PROGRAMAS_PERSONALES=new Herramienta("consultar_programas_personales","GET","/programas-personales",Herramienta.AGENTE,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_PROGRAMA_PERSONAL=new Herramienta("consultar_programa_personal","GET","/programas-personales/{referencia}",Herramienta.AGENTE,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONFIGURAR_PROGRAMA_PERSONAL=new Herramienta("configurar_programa_personal","POST","/programas-personales",Herramienta.AGENTE,Confirmacion.SIEMPRE,Estado.DECLARADA);
    public static final Herramienta CAMBIAR_ESTADO_PROGRAMA_PERSONAL=new Herramienta("cambiar_estado_programa_personal","POST","/programas-personales/{referencia}/estado",Herramienta.AGENTE,Confirmacion.SIEMPRE,Estado.DECLARADA);
    public static final Herramienta CONSULTAR_OCURRENCIAS_PROGRAMA_PERSONAL=new Herramienta("consultar_ocurrencias_programa_personal","GET","/programas-personales/{referencia}/ocurrencias",Herramienta.AGENTE,Confirmacion.NUNCA,Estado.VIVA);
    public static final Herramienta CONSULTAR_RESULTADO_PROGRAMA_PERSONAL=new Herramienta("consultar_resultado_programa_personal","GET","/programas-personales/{referencia}/ocurrencias/{ocurrencia}",Herramienta.AGENTE,Confirmacion.NUNCA,Estado.VIVA);

    private static final Map<String, Herramienta> POR_NOMBRE = indexar(List.of(
            DEFINICION_DE_METAS,CONSULTAR_METAS_COMERCIALES,CONSULTAR_PROPUESTAS_METAS,CONSULTAR_PROPUESTA_META,PROPONER_META,ESTABLECER_META,DECIDIR_PROPUESTA_META,CONSULTAR_AVANCE_METAS,CONSULTAR_PULSO_EQUIPO,
            CONSULTAR_COMPROMISOS_PROSPECCION,ASUMIR_COMPROMISO_PROSPECCION,ACREDITAR_COMPROMISO_PROSPECCION,RESOLVER_COMPROMISO_PROSPECCION,CONSULTAR_MEDICIONES_INDIVIDUALES,CONSULTAR_DERECHOS_INFORMES,PREPARAR_INFORME_COMERCIAL,CONSULTAR_PROGRAMAS_PERSONALES,CONSULTAR_PROGRAMA_PERSONAL,CONFIGURAR_PROGRAMA_PERSONAL,CAMBIAR_ESTADO_PROGRAMA_PERSONAL,CONSULTAR_OCURRENCIAS_PROGRAMA_PERSONAL,CONSULTAR_RESULTADO_PROGRAMA_PERSONAL,
            PRESENTAR_EXPEDIENTE_OPERACION,EVALUAR_EXPEDIENTE_OPERACION,INFORMAR_HITO_CONTRACTUAL,
            CONFIRMAR_HITO_CONTRACTUAL,RECTIFICAR_HITO_CONTRACTUAL,ASIGNAR_REPARTO_COMISION,AUTORIZAR_BORRADOR_ADENDA,
            REPROGRAMAR_GESTION_COBRO,CONCLUIR_GESTION_COBRO,
            BUSCAR_PROPIEDADES, ESTADO_DE_PROPIEDAD, BUSCAR_PERSONA, BUSCAR_PROPIETARIOS, CONSULTAR_CAPTURA, CONSULTAR_ACTUACION, BUSCAR_COINCIDENCIAS,
            EXPLICAR_MATCH, CONTINUAR_COINCIDENCIAS, QUE_RESOLVER_HOY, CAPTURAS_EN_CURSO,
            ABRIR_OPORTUNIDAD, RECTIFICAR_INTERACCION, VINCULAR_INTERACCION, ANOTAR_OBSERVACION_INTERACCION, REGISTRAR_NO_CONTINUIDAD_OPORTUNIDAD, CONSULTAR_AGENDA_VISITAS, PROGRAMAR_VISITA, CANCELAR_VISITA, REPROGRAMAR_VISITA, REGISTRAR_RESULTADO_VISITA, REGISTRAR_CONTRAOFERTA, REGISTRAR_ACEPTACION_OFERTA, REGISTRAR_RECHAZO_OFERTA, REGISTRAR_RETIRADA_OFERTA, CONSULTAR_OPORTUNIDAD,
            CONSULTAR_CAPACIDADES, CATALOGO_DE_CAPTURA, DEFINICION_DE_INTERACCIONES,
            CONSULTAR_SEGUIMIENTOS_PROSPECCION, PLANIFICAR_SEGUIMIENTO_PROSPECCION, POSPONER_SEGUIMIENTO_PROSPECCION,
            ABRIR_SESION_CAPTURA, RESPONDER_CAPTURA, REGISTRAR_INTERACCION, REGISTRAR_PROPIETARIO,
            PREPARAR_ACTUACION_CAPTURA, EVALUAR_PUBLICABILIDAD,CONSULTAR_PUBLICACIONES,CONSULTAR_PUBLICACION,
            CONSULTAR_HISTORIA_PUBLICACION,CREAR_BORRADOR_ANUNCIO,EDITAR_BORRADOR_ANUNCIO,
            PUBLICAR_ANUNCIO,ACTUALIZAR_ANUNCIO_VISIBLE,PAUSAR_ANUNCIO,REANUDAR_ANUNCIO,FINALIZAR_DIFUSION_ANUNCIO,
            CONSULTAR_ESTADO_PIEZA,CONSULTAR_FOTOS_PERMITIDAS_PARA_PIEZA,CONSULTAR_PLANTILLAS_Y_FORMATOS_PIEZA,
            PREPARAR_PIEZA_COMERCIAL,PREVISUALIZAR_PIEZA_COMERCIAL,REGENERAR_PIEZA_COMERCIAL,OBTENER_PIEZA_PARA_DIFUSION,EVALUAR_FOTO_LOCAL,
            PREPARAR_MATERIAL_IA,SOLICITAR_MATERIAL_IA,CONSULTAR_MATERIAL_IA,
            PRESENTAR_OFERTA, DECIDIR_CAPTACION,
            EVALUAR_SOLICITUD, CERRAR_ENCARGO, REGISTRAR_COBRO, CONSULTAR_REPORTES_PROPIETARIO,CONSULTAR_AVANCE_REPORTE,CONSULTAR_ANTECEDENTE_REPORTE,PREPARAR_REPORTE_PROPIETARIO,PREVISUALIZAR_REPORTE_PROPIETARIO,OBTENER_REPORTE_PROPIETARIO,CONSULTAR_COMUNICACIONES_REPORTE,REGISTRAR_COMUNICACION_REPORTE_REALIZADA,RECTIFICAR_COMUNICACION_REPORTE));

    private Herramientas() {
    }

    /** Todas, en el orden de D-K-1 §3: primero las que leen. */
    public static List<Herramienta> todas() {
        return List.copyOf(POR_NOMBRE.values());
    }

    /** Las que KAIROS sabe invocar hoy, sirva el Core la ruta o no. */
    public static List<Herramienta> implementadas() {
        return POR_NOMBRE.values().stream().filter(Herramienta::implementada).toList();
    }

    /** Las declaradas y todavia no implementadas: existen para no volver a decidirlas. */
    public static List<Herramienta> declaradas() {
        return POR_NOMBRE.values().stream()
                .filter(h -> h.estado() == Estado.DECLARADA)
                .toList();
    }

    /**
     * Las que KAIROS invoca y el Core no sirve. <b>Es la lista de lo que le
     * falta al Core</b>, sacada del catalogo y no de un documento aparte.
     */
    public static List<Herramienta> sinPuertaEnElCore() {
        return POR_NOMBRE.values().stream()
                .filter(Herramienta::sinPuertaEnElCore)
                .toList();
    }

    public static Optional<Herramienta> de(String nombre) {
        return nombre == null ? Optional.empty()
                : Optional.ofNullable(POR_NOMBRE.get(nombre.trim()));
    }

    private static Map<String, Herramienta> indexar(List<Herramienta> herramientas) {
        Map<String, Herramienta> indice = new LinkedHashMap<>();
        for (Herramienta herramienta : herramientas) {
            Herramienta repetida = indice.put(herramienta.nombre(), herramienta);
            if (repetida != null) {
                throw new IllegalStateException(
                        "Dos herramientas con el nombre \"" + herramienta.nombre() + "\": el "
                                + "nombre es el identificador con el que se pide y con el que se "
                                + "audita, y repetirlo hace que una de las dos no se pueda nombrar.");
            }
        }
        // unmodifiableMap y no Map.copyOf: el orden de declaracion es el de
        // D-K-1 §3 —primero lo que lee, luego lo reversible, luego lo sensible—
        // y Map.copyOf no lo conserva.
        return Collections.unmodifiableMap(indice);
    }
}


