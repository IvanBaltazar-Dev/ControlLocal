package com.controllocal.asistencia.brox;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Puerto interno de casos Core; la asistencia no recibe repositorios ni autoridad comercial. */
public interface ClienteBrox {

    /** AC101–115, dos actos de comunicación y tres bindings CAT-R27; nunca una URL libre. */
    enum OperacionComercial {
        CONSULTAR_RADAR,CONSULTAR_AVISOS_BROKER,ATENDER_AVISO_BROKER,
        CONSULTAR_SOLICITUDES_DISPONIBILIDAD,SOLICITAR_CONFIRMACION_INICIAL,RESPONDER_SOLICITUD_DISPONIBILIDAD,
        DEFINICION_DE_METAS,CONSULTAR_METAS_COMERCIALES,CONSULTAR_PROPUESTAS_METAS,CONSULTAR_PROPUESTA_META,PROPONER_META,ESTABLECER_META,DECIDIR_PROPUESTA_META,CONSULTAR_AVANCE_METAS,CONSULTAR_PULSO_EQUIPO,
        CONSULTAR_REPORTES_PROPIETARIO,CONSULTAR_AVANCE_REPORTE,CONSULTAR_ANTECEDENTE_REPORTE,PREPARAR_REPORTE_PROPIETARIO,PREVISUALIZAR_REPORTE_PROPIETARIO,OBTENER_REPORTE_PROPIETARIO,CONSULTAR_COMUNICACIONES_REPORTE,REGISTRAR_COMUNICACION_REPORTE_REALIZADA,RECTIFICAR_COMUNICACION_REPORTE,
        CONSULTAR_COMPROMISOS_PROSPECCION,ASUMIR_COMPROMISO_PROSPECCION,ACREDITAR_COMPROMISO_PROSPECCION,RESOLVER_COMPROMISO_PROSPECCION,CONSULTAR_MEDICIONES_INDIVIDUALES,CONSULTAR_DERECHOS_INFORMES,PREPARAR_INFORME_COMERCIAL,CONSULTAR_PROGRAMAS_PERSONALES,CONSULTAR_PROGRAMA_PERSONAL,CONSULTAR_OCURRENCIAS_PROGRAMA_PERSONAL,CONSULTAR_RESULTADO_PROGRAMA_PERSONAL,
        ABRIR_OPORTUNIDAD, RECTIFICAR_INTERACCION, REGISTRAR_INTERACCION,
        VINCULAR_INTERACCION, ANOTAR_OBSERVACION_INTERACCION,
        CONSULTAR_SEGUIMIENTOS_PROSPECCION, PLANIFICAR_SEGUIMIENTO_PROSPECCION,
        POSPONER_SEGUIMIENTO_PROSPECCION,
        REGISTRAR_NO_CONTINUIDAD_OPORTUNIDAD, CONSULTAR_AGENDA_VISITAS,
        PROGRAMAR_VISITA, CANCELAR_VISITA, REPROGRAMAR_VISITA, REGISTRAR_RESULTADO_VISITA,
        PRESENTAR_OFERTA, REGISTRAR_CONTRAOFERTA, REGISTRAR_ACEPTACION_OFERTA,
        REGISTRAR_RECHAZO_OFERTA, REGISTRAR_RETIRADA_OFERTA, CONSULTAR_OPORTUNIDAD,
        PRESENTAR_EXPEDIENTE_OPERACION,EVALUAR_EXPEDIENTE_OPERACION,INFORMAR_HITO_CONTRACTUAL,
        CONFIRMAR_HITO_CONTRACTUAL,RECTIFICAR_HITO_CONTRACTUAL,REGISTRAR_COBRO,
        ASIGNAR_REPARTO_COMISION,AUTORIZAR_BORRADOR_ADENDA,REPROGRAMAR_GESTION_COBRO,CONCLUIR_GESTION_COBRO,
        EVALUAR_PUBLICABILIDAD,CONSULTAR_PUBLICACIONES,CONSULTAR_PUBLICACION,CONSULTAR_HISTORIA_PUBLICACION,
        CREAR_BORRADOR_ANUNCIO,EDITAR_BORRADOR_ANUNCIO,PUBLICAR_ANUNCIO,ACTUALIZAR_ANUNCIO_VISIBLE,
        PAUSAR_ANUNCIO,REANUDAR_ANUNCIO,FINALIZAR_DIFUSION_ANUNCIO,
        CONSULTAR_ESTADO_PIEZA,CONSULTAR_FOTOS_PERMITIDAS_PARA_PIEZA,CONSULTAR_PLANTILLAS_Y_FORMATOS_PIEZA,
        PREPARAR_PIEZA_COMERCIAL,PREVISUALIZAR_PIEZA_COMERCIAL,REGENERAR_PIEZA_COMERCIAL,
        OBTENER_PIEZA_PARA_DIFUSION,EVALUAR_FOTO_LOCAL,
        PREPARAR_MATERIAL_IA,SOLICITAR_MATERIAL_IA,CONSULTAR_MATERIAL_IA;

        /** Tipo de invocación del puerto cerrado; no concede permisos ni aprobación humana. */
        public boolean preparaPropuesta() {
            return switch (this) {
                case ESTABLECER_META,DECIDIR_PROPUESTA_META, RECTIFICAR_COMUNICACION_REPORTE, ABRIR_OPORTUNIDAD, RECTIFICAR_INTERACCION,
                        REGISTRAR_NO_CONTINUIDAD_OPORTUNIDAD, CANCELAR_VISITA,
                        PRESENTAR_OFERTA, REGISTRAR_CONTRAOFERTA, REGISTRAR_ACEPTACION_OFERTA,
                        REGISTRAR_RECHAZO_OFERTA, REGISTRAR_RETIRADA_OFERTA,
                        PRESENTAR_EXPEDIENTE_OPERACION,EVALUAR_EXPEDIENTE_OPERACION,INFORMAR_HITO_CONTRACTUAL,
                        CONFIRMAR_HITO_CONTRACTUAL,RECTIFICAR_HITO_CONTRACTUAL,REGISTRAR_COBRO,
                        ASIGNAR_REPARTO_COMISION,AUTORIZAR_BORRADOR_ADENDA,
                        REPROGRAMAR_GESTION_COBRO,CONCLUIR_GESTION_COBRO,
                        PUBLICAR_ANUNCIO,ACTUALIZAR_ANUNCIO_VISIBLE,PAUSAR_ANUNCIO,
                        REANUDAR_ANUNCIO,FINALIZAR_DIFUSION_ANUNCIO,OBTENER_PIEZA_PARA_DIFUSION -> true;
                default -> false;
            };
        }
    }
    /** Contenido ya autorizado por Core; no contiene instrucciones ejecutables. */
    record ResultadoComercial(Map<String,Object> datos, PropuestaActuacion propuesta,
                              ReferenciaAutomatico automatico) { }
    ResultadoComercial operarComercial(SesionBrox sesion, OperacionComercial operacion,
                                      Map<String,Object> datos, Traza traza);

    // ------------------------------------------------------------------
    // Capacidades: que se puede hacer, con que permisos y con que autonomia
    // ------------------------------------------------------------------

    /**
     * Lo que <b>esta sesion</b> puede hacer, declarado por BROX.
     *
     * <p><b>La autonomia no se decide aqui.</b> Que una publicacion la confirme
     * una persona es una regla del negocio, no una preferencia del asistente, y
     * por eso llega declarada en vez de estar escrita en un prompt. El dia que
     * BROX cambie de opinion sobre una operacion, KAIROS obedece sin
     * desplegarse.
     *
     * <p>C0/K0: una lista vacía es una respuesta autorizada sin capacidades.
     * Un catálogo ausente, incompatible o fallido se propaga como error; nunca
     * activa el catálogo local como política sustitutiva.
     */
    List<Capacidad> capacidades(SesionBrox sesion, Traza traza);

    record ContextoCapacidad(long organizacionId, long personaId, String rol, String recurso,
                             String referencia, String operacion, String atributo, String etapa,
                             String referenciaRelacionada) { }

    /** Forma pública de CAT-E40. No contiene rutas ni una política de roles en el cliente. */
    record Capacidad(String idCore, String familia, String efecto, String interaccion,
                     Map<String, Object> esquema, int version, String estado, String motivo,
                     ContextoCapacidad contexto) {

        public static final String AUTO = "AUTO";
        public static final String CONFIRMA = "CONFIRMA";
        public static final String HUMANO = "HUMANO";

        public boolean laConfirmaUnaPersona() {
            return CONFIRMA.equals(interaccion);
        }

        public boolean laPuedePedirUnAgente() {
            return !HUMANO.equals(interaccion);
        }
    }

    record CatalogoCapacidades(Integer version, List<Capacidad> capacidades) { }

    // ------------------------------------------------------------------
    // Captura: el motor de registro vive en BROX (§6)
    // ------------------------------------------------------------------

    /** Un valor admitido con su nombre. KAIROS dice el rotulo y devuelve el valor. */
    record Opcion(String valor, String rotulo) {
    }

    /**
     * Una pregunta, ya resuelta por BROX contra su catalogo. KAIROS solo la dice.
     *
     * <p>Desde el Corte 0B viene con lo que hace falta para decirla: su
     * vocabulario con rotulos, para que forma parte y cuanto hace falta. KAIROS
     * no completa nada de esto -- si tuviera que hacerlo, tendria su propia
     * matriz inmobiliaria y divergiria de BROX Web desde el primer atributo que
     * alguien anadiera.
     *
     * <p><b>Lo que este record NO trae: {@code restricciones}.</b> El Core las
     * publica —{@code minimo}, {@code maximo}, {@code longitudMaxima},
     * {@code decimales}— y KAIROS las descarta al deserializar. No es una
     * decision, es una carencia, y esta escrita aqui para que se lea como tal:
     * quien quiera decir «tiene que ser un entero mayor que cero» todavia no
     * tiene con que. Validar no cambia —lo hace BROX al anotar—, pero preguntar
     * mejor si.
     */
    record Pregunta(String clave, String rotulo, String seccion, String familia, String control,
                    String tipoDato, String unidad, List<Opcion> opciones, String exigencia,
                    boolean obligatoria, String ayuda, int orden, String referenciaCatalogo,String versionDefinicion) {
        public Pregunta(String clave,String rotulo,String seccion,String familia,String control,String tipoDato,
                        String unidad,List<Opcion> opciones,String exigencia,boolean obligatoria,String ayuda,int orden) {
            this(clave,rotulo,seccion,familia,control,tipoDato,unidad,opciones,exigencia,obligatoria,ayuda,orden,null,null);
        }
    }

    /**
     * Lo que BROX pregunta para un tipo, y opcionalmente para sus operaciones.
     *
     * <p>Sin operaciones vienen {@code deLaOperacion} vacio: la propiedad es la
     * cosa fisica y la operacion vive en el Encargo.
     */
    record DefinicionCaptura(String intencion, String tipoPropiedad, List<String> operaciones,
                             List<Pregunta> comunes, List<Pregunta> delTipo,
                             List<BloqueOperacion> deLaOperacion) {
    }

    /** Las preguntas economicas de UNA operacion, con su rotulo ya puesto. */
    record BloqueOperacion(String operacion, String rotulo, List<Pregunta> preguntas) {
    }

    /** Donde va una captura, segun BROX. KAIROS no calcula ni un solo campo de esto. */
    record EstadoCaptura(Long idBorrador, String codigo, String intencion, String estado,
                         String canal, Map<String, Object> conocido, List<String> faltante,
                         Pregunta siguiente, boolean listoParaEjecutar, String entidadTipo,
                         Long idEntidad, LocalDateTime actualizadoEn, Integer version,
                         Map<String,Object> contexto, Map<String,Object> metadatos, Map<String,Object> retorno,
                         Map<String,Object> suficiencia, List<Map<String,Object>> suficienciasEncargo) {

        /** Compatibilidad de fuente para adaptadores que aun no usan metadatos del cable. */
        public EstadoCaptura(Long idBorrador, String codigo, String intencion, String estado,
                             Map<String, Object> conocido, List<String> faltante,
                             Pregunta siguiente, boolean listoParaEjecutar, Long idEntidad) {
            this(idBorrador, codigo, intencion, estado, null, conocido, faltante, siguiente,
                    listoParaEjecutar, null, idEntidad, null, null, null, null, null, null, null);
        }
    }

    record Ejecucion(Long idBorrador, Long idPropiedad, String codigoPropiedad,
                     List<Long> idsEncargos, boolean reintento) {
    }

    record PropuestaActuacion(String referencia, String capacidad, long version, String huella,
                              long caducaEnEpochSeconds, Map<String, Object> contenido) { }
    record ResultadoActuacion(String referencia, String estado, PropuestaActuacion propuesta,
                              Map<String, Object> resultado, String motivo, ResultadoComercial comercial) { }

    /** Referencia Core recuperable; no representa una aprobación humana. */
    record ReferenciaAutomatico(String referencia, String capacidad, int version) { }
    record ResultadoAutomatico<T>(ReferenciaAutomatico operacion, T resultado) { }

    PropuestaActuacion prepararCaptura(SesionBrox sesion, long idBorrador, Traza traza, EstadoCaptura esperado);
    ResultadoActuacion consultarActuacion(SesionBrox sesion, String referencia, Traza traza);

    ResultadoAutomatico<EstadoCaptura> avanzarCaptura(SesionBrox sesion, String intencion, Long idBorrador,
                                 Integer versionEsperada, Map<String, String> datos, Traza traza);

    List<EstadoCaptura> capturasEnCurso(SesionBrox sesion, Traza traza);

    EstadoCaptura captura(SesionBrox sesion, long idBorrador, Traza traza);


    // ------------------------------------------------------------------
    // Lecturas
    // ------------------------------------------------------------------

    /** Una coincidencia de cartera, tal como la devuelve la busqueda de BROX. */
    record Coincidencia(Long id, String codigo, String direccion, String distrito) {
    }

    record Persona(Long id, String nombre, String tipoDocumento, String numeroDocumento,
                   String telefono) {
    }

    List<Coincidencia> buscarPropiedades(SesionBrox sesion, String texto, Traza traza);

    Map<String, Object> propiedad(SesionBrox sesion, long idPropiedad, Traza traza);

    List<Persona> buscarClientes(SesionBrox sesion, String texto, Traza traza);

    List<Persona> buscarPropietarios(SesionBrox sesion, String texto, Traza traza);

    // ------------------------------------------------------------------
    // Matching: que encaja con que, y POR QUE
    // ------------------------------------------------------------------

    /** Transporte del resultado Core D1–D4; sin cálculo ni valores por defecto locales. */
    enum ModoExploracion { DESCUBRIR, EXPLORAR }
    record ExploracionPrecio(ModoExploracion modo,BigDecimal limite,String moneda) { }
    record ConsultaCoincidencias(String origen,Long idOrigen,Long versionOrigen,String tipoVersion,
                                 String modalidad,java.util.Set<String> capas,String categoria,Integer tamano,ExploracionPrecio exploracion) { }
    record RazonCoincidencia(String referenciaCriterio,String clave,String exigencia,int peso,
                             short resultado,String causa) { }
    record AlternativaPrecio(BigDecimal precio,BigDecimal maximoOriginal,String moneda,String concepto,String periodo,
                             BigDecimal excesoAbsoluto,BigDecimal excesoPorcentaje) { }
    record CoincidenciaComercial(String referencia,long version,String capa,String categoria,
                                  short g,short h,long s,long u,long w,List<RazonCoincidencia> razones,
                                  Map<String,Object> datosPermitidos,String coberturaCriterios,AlternativaPrecio alternativaPrecio) { }
    record GrupoCoincidencias(String referenciaDemanda,long versionDemanda,List<CoincidenciaComercial> candidatos) { }
    record CoincidenciasComerciales(String estado,ConsultaCoincidencias seleccion,java.time.Instant corte,
                                    String versionRegla,String referenciaConsulta,List<GrupoCoincidencias> grupos,
                                    Long total,String cobertura,java.util.Set<String> capasNoDisponibles,String continuacion) { }
    CoincidenciasComerciales coincidencias(SesionBrox sesion,ConsultaCoincidencias consulta,Traza traza);
    CoincidenciasComerciales continuarCoincidencias(SesionBrox sesion,String cursor,Traza traza);
    CoincidenciaComercial explicarCoincidencia(SesionBrox sesion,String cursor,String referenciaDemanda,String referencia,Traza traza);

    // ------------------------------------------------------------------
    // El foco del Inicio
    // ------------------------------------------------------------------

    /**
     * Algo que <b>hay que resolver</b>. Sale de la bandeja del agente.
     *
     * <p>{@code lectura} es la sintesis que redacta el Core —la que relaciona
     * dos hechos y dice para que sirve atenderlo—. Viene ya escrita a proposito:
     * si la compusiera KAIROS, la pantalla diria una cosa y la conversacion
     * otra sobre el mismo asunto.
     */
    record Asunto(String id, String tipo, String entidadTipo, Long entidadId, String entidadCodigo,
                  String descripcion, String prioridad, Integer diasSinAccion, boolean dependeDeMi,
                  String rutaResolver, String lectura,OrdenRadar orden) {
    }

    /**
     * Algo que <b>solo el broker</b> puede decidir.
     *
     * <p>Va aparte de {@link Asunto} porque no es la bandeja del agente vista
     * por otro rol: son decisiones de otra naturaleza (D-E2-5). Juntarlas seria
     * ofrecerle a cada uno lo que el otro tiene que hacer.
     */
    record AsuntoDelBroker(String id, String tipo, String entidadTipo, Long entidadId,
                           String entidadCodigo, String destino, int diasEsperando,
                           String lectura,OrdenRadar orden) {
    }
    /** Proyección producida por Core; el cliente no calcula prioridades ni interpreta plazos. */
    record OrdenRadar(String banda,boolean correspondeHoy,boolean plazoVencido,java.time.LocalDate fecha,
                      java.time.LocalTime hora,java.time.Instant detectadoEn,boolean fechaEnConflicto,java.time.LocalDate revisarEn) { }

    /** La aptitud y las razones proceden del mismo motor; concentración conserva aptitud null. */
    record Hallazgo(String id,String tipo,String titulo,String porQue,CoincidenciaComercial aptitud,
                    String destino,Long idDemanda,Long idCaptacion,String codigoCaptacion) { }

    /**
     * El foco del Inicio, con el alcance de quien pregunta.
     *
     * <p>Tres colecciones y no una filtrada: la bandeja es del AGENTE —el broker
     * la recibe vacia y eso no es un 403—, los asuntos son del BROKER, y los
     * hallazgos los tienen los dos con naturaleza distinta segun el rol.
     */
    record ActuacionRadar(String id,String motivo,String recurso,long referencia,long encargo,String operacion,
                         HechoRadar fuente,java.time.Instant registradoEn,java.time.LocalDate fechaActuacion,
                         String siguiente,boolean accionable,OrdenRadar orden) { }
    record HechoRadar(String tipo,long referencia,Long version) { }
    record Foco(String ambito, int pendientesDeAtencion, List<Asunto> bandeja, int totalBandeja,
                List<AsuntoDelBroker> asuntosDelBroker, List<Hallazgo> hallazgos,List<ActuacionRadar> actuaciones) {

        public Foco {
            bandeja = bandeja == null ? List.of() : List.copyOf(bandeja);
            asuntosDelBroker = asuntosDelBroker == null ? List.of() : List.copyOf(asuntosDelBroker);
            hallazgos = hallazgos == null ? List.of() : List.copyOf(hallazgos);
            actuaciones = actuaciones == null ? List.of() : List.copyOf(actuaciones);
        }

        /** Nada que resolver <b>hoy</b>. No es lo mismo que no tener trabajo. */
        public boolean vacio() {
            return bandeja.isEmpty() && asuntosDelBroker.isEmpty() && hallazgos.isEmpty() && actuaciones.isEmpty();
        }
    }

    /**
     * Lo que hay que resolver hoy.
     *
     * <p><b>Este GET escribe</b>: la bandeja se deriva y se reconcilia antes de
     * devolverse. No es una lectura pura aunque lo parezca, y por eso no se
     * llama de adorno.
     */
    Foco foco(SesionBrox sesion, Traza traza);

    // ------------------------------------------------------------------
    // Escrituras
    // ------------------------------------------------------------------

    ResultadoAutomatico<Persona> registrarPropietario(SesionBrox sesion, Map<String, String> datos, Traza traza);
    Optional<ResultadoAutomatico<Persona>> recuperarPropietario(SesionBrox sesion, Map<String, String> datos, Traza traza);

// ------------------------------------------------------------------
    // Vocabulario del tenant
    // ------------------------------------------------------------------

    /**
     * Los distritos dados de alta en la organizacion.
     *
     * <p>KAIROS los necesita para reconocer "en Miraflores" en una frase, y no
     * puede llevar una lista propia: los distritos son datos del tenant y una
     * copia aqui envejeceria en silencio, reconociendo distritos que ya no
     * existen e ignorando los nuevos.
     */
    List<String> distritos(SesionBrox sesion, Traza traza);

    /**
     * Que se pregunta para un tipo de propiedad, derivado del catalogo del
     * tenant. Incluye los atributos comunes y los que esa organizacion se creo.
     *
     * <p>Desde el Corte 0B sale de {@code GET /captura/definicion} <b>sin
     * operaciones</b>, que es la misma maquina que usa BROX Web. Antes habia un
     * endpoint aparte, {@code /propiedades/catalogo/{tipo}}, que respondia a la
     * misma pregunta con la mitad de los campos: dos productores de la misma
     * autoridad divergen, y de hecho ya lo hacian en el {@code orden}.
     *
     * <p><b>Comunes + del tipo, y las comunes no salen de la tabla.</b>
     * {@code latitud} y {@code longitud} las aporta {@code GuionRegistroPropiedad}
     * —Java escrito a mano—, asi que toda afirmacion sobre lo que KAIROS ve se
     * mide desde aqui y nunca desde {@code catalogo_atributo}: una consulta SQL
     * al catalogo no ve la mitad de lo que este metodo devuelve.
     */
    List<Pregunta> catalogoDe(SesionBrox sesion, String tipoPropiedad, Traza traza);

    /**
     * La definicion completa, con los bloques economicos de las operaciones que
     * se declaren. Sin operaciones, solo la cosa fisica.
     */
    DefinicionCaptura definicionDe(SesionBrox sesion, String tipoPropiedad, String operaciones, String jurisdiccion,
                                   Traza traza);

    /** Definición completa y versionada de Core: tipos, canales, eventos, resultados y compatibilidad. */
    Map<String, Object> definicionDeInteracciones(SesionBrox sesion, Traza traza);
}

