package com.controllocal.arquitectura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El sexto gate: una escritura nueva sobre la propiedad no puede nacer sin
 * autoridad</b> (P0).
 *
 * <h2>Que decision protege</h2>
 * Hasta V87 la propiedad no tenia dueno de escritura. {@code PUT
 * /propiedades/{id}} cargaba la fila por {@code (organizacion, id)} y escribia:
 * cualquier AGENTE del tenant editaba la ficha de cualquier inmueble. Y no era
 * una via: eran <b>varias</b>, repartidas por varios servicios, y casi ninguna
 * comprobaba nada mas que el tenant.
 *
 * <h2>Por que aqui no va ninguna cifra</h2>
 * La llevaba, y era falsa. Se escribio "ocho vias" —la cuenta del inventario
 * inicial— y se repitio cuatro veces, incluida "la novena" como nombre de lo
 * que este gate protege. Para cuando se escribio ya no era cierto: el propio
 * gate encontro una via que el inventario no tenia
 * ({@code PublicacionServiceImpl}), el corte declaro trece y la auditoria
 * conto doce. Tres cifras distintas para una sola cosa, y el resultado era
 * correcto: lo falso era <b>la explicacion</b>.
 *
 * <p>Es la familia de fallos que mas caro ha salido en este repositorio —una
 * cifra transcrita a mano caduca sola y nadie la revisa, porque nada la
 * verifica—. Asi que la regla aqui es: <b>o la genera una comprobacion, o no se
 * escribe</b>. Las vias las cuenta este gate en cada build contra el bytecode,
 * y la lista sale en el mensaje de error cuando alguna se queda sin guarda.
 *
 * <p>Lo que este gate protege es el <b>manana</b>: la <b>siguiente</b>. Un caso
 * de uso nuevo que guarde una propiedad, una foto, un atributo gobernado o un
 * hito economico sin pasar por {@code AutoridadDePropiedad} pone el build en
 * rojo, en vez de reabrir el agujero en silencio dos cortes despues, cuando ya
 * nadie recuerde por que la columna existe.
 *
 * <h2>Por que un gate y no un {@code @PreAuthorize}</h2>
 * Dos razones medidas, y las dos aparecieron en el inventario de este P0:
 * <ol>
 *   <li>Las vias viven repartidas por varios servicios. Una anotacion protege
 *       <b>una puerta</b>; la autoridad tiene que proteger <b>el hecho</b>.</li>
 *   <li><b>KAIROS entra por los mismos endpoints</b> con la cabecera
 *       {@code X-Origen} y el mismo token — no tiene escritor propio. Una regla
 *       en la capa web tendria que reescribirse para cada canal; una regla en
 *       el servicio la heredan todos por construccion. Eso es lo que hace
 *       verdadera la afirmacion "Web y KAIROS reciben exactamente la misma
 *       regla": no es que se hayan comprobado las dos, es que <b>es la
 *       misma</b>.</li>
 * </ol>
 *
 * <h2>Lo que este gate NO dice, dicho en vez de disimulado</h2>
 * <ul>
 *   <li><b>No ve el SQL directo.</b> Un {@code UPDATE propiedad} a mano sigue
 *       siendo posible. Lo que garantiza es que ninguna <b>operacion del
 *       producto</b> escribe la propiedad sin decidir quien puede.</li>
 *   <li><b>No ve un metodo privado nuevo</b> dentro de un caso de uso cuyo
 *       metodo publico si comprueba: la comprobacion es transitiva dentro de la
 *       clase, igual que en {@code LinajeDeTodaEscrituraTest}.</li>
 *   <li><b>No decide si la regla es correcta.</b> Decide que la regla se
 *       consulta. Que diga lo que tiene que decir lo prueban las pruebas de
 *       integracion.</li>
 * </ul>
 */
class AutoridadDeLaPropiedadTest {

    private static final JavaClasses CLASES = ImportacionesDeProducto.importador()
            .importPackages("com.controllocal");

    private static final String AUTORIDAD = "com.controllocal.service.soporte.AutoridadDePropiedad";
    private static final String PROPIEDAD = "com.controllocal.domain.inmueble.Propiedad";
    private static final String SERVICIO_DEL_ORIGEN = "com.controllocal.service.impl.UnidadRelacionadaServiceImpl";
    private static final String TRANSICIONES = "com.controllocal.service.soporte.Transiciones";
    private static final String FOTOS_COMERCIALES="com.controllocal.service.documentos.FotosComercialesService";
    private static final String PIEZAS_COMERCIALES="com.controllocal.service.documentos.PiezasComercialesServiceImpl";
    private static final String COPIA_COMERCIAL="com.controllocal.domain.inmueble.CopiaComercialFoto";
    private static final String PIEZA_COMERCIAL="com.controllocal.domain.inmueble.PiezaComercial";
    private static final String HISTORIADOR_PUBLICACION="com.controllocal.service.publicacion.HistoriadorPublicacion";
    private static final String PUBLICACIONES="com.controllocal.service.impl.PublicacionServiceImpl";

    private static final String REPOSITORIO_DEL_RASTRO =
            "com.controllocal.persistence.repositorio.AsignacionResponsablePropiedadRepository";

    /**
     * El repositorio de la propiedad y el nombre del compare-and-set (D-P0-9).
     *
     * <p>No se puede prohibir el repositorio entero —lo usan casi todos los
     * casos de uso, y con razon—, asi que lo que se vigila es <b>el metodo</b>:
     * es la unica de sus sentencias que escribe {@code id_rol_responsable}.
     */
    private static final String REPOSITORIO_DE_LA_PROPIEDAD =
            "com.controllocal.persistence.repositorio.PropiedadRepository";
    private static final String CAS = "cambiarResponsableSi";

    /**
     * <b>La otra autoridad mutable de P0</b>: el agente de un ENCARGO, con sus
     * dos caminos de escritura.
     *
     * <p>{@code setAgente} existe en <b>once</b> entidades distintas —visitas,
     * tareas, solicitudes, alertas…—, asi que la regla se escribe contra el
     * dueno del acceso ({@code Captacion}) y no contra el nombre del metodo.
     * Vigilar «cualquier setAgente» habria puesto en rojo medio dominio y
     * habria acabado en una lista de exenciones que nadie lee.
     */
    private static final String ENCARGO = "com.controllocal.domain.comercial.Captacion";
    private static final String SETTER_DEL_AGENTE = "setAgente";
    private static final String REPOSITORIO_DEL_ENCARGO =
            "com.controllocal.persistence.repositorio.CaptacionRepository";
    private static final String CAS_DEL_AGENTE = "cambiarAgenteSi";

    private static final String PUERTA_CANONICA_DEL_ENCARGO =
            "com.controllocal.service.impl.CaptacionServiceImpl#reasignar";

    /**
     * <b>Los cuatro que pueden fijar el agente de un encargo, con su motivo.</b>
     *
     * <p>Tres son el <b>alta</b> —el {@code INSERT}, donde el encargo nace y no
     * hay agente anterior a quien respetar— y el cuarto es la <b>puerta
     * canonica</b>. Anadir un quinto obliga a escribir su razon aqui, donde
     * cualquiera la va a leer.
     */
    private static final Map<String,String> PUEDEN_FIJAR_EL_AGENTE=Map.of(
            "com.controllocal.service.encargo.EncargoServiceImpl#abrir",
            "C2: único INSERT de episodio, responsable explícito igual al actor AGENTE y propiedad autorizada. "
            +"Los adaptadores de registro/prospección delegan aquí. La reasignación sólo usa el CAS vigilado aparte.");

    /**
     * Un mapa {@code clave -> motivo} escrito como pares consecutivos.
     *
     * <p>Existe por una razon prosaica: {@code Map.of} admite <b>diez</b> pares
     * y estas listas ya no caben. Convertirlas a {@code Map.ofEntries} habria
     * envuelto cada motivo en un {@code Map.entry(...)} y ensuciado la unica
     * parte de este gate que se lee como prosa.
     */
    private static Map<String, String> mapa(String... pares) {
        if (pares.length % 2 != 0) {
            throw new AssertionError("falta el motivo del ultimo elemento: " + pares.length
                    + " cadenas son un numero impar de pares clave/motivo.");
        }
        Map<String, String> resultado = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            if (resultado.put(pares[i], pares[i + 1]) != null) {
                throw new AssertionError("motivo duplicado para " + pares[i]
                        + ": dos razones para lo mismo significan que una es falsa.");
            }
        }
        return Map.copyOf(resultado);
    }

    /** Los dos paquetes de dominio donde viven la propiedad y su comercializacion. */
    private static final String PAQUETE_DEL_INMUEBLE = "com.controllocal.domain.inmueble";
    private static final String PAQUETE_COMERCIAL = "com.controllocal.domain.comercial";

    private static final String REPOSITORIOS = "com.controllocal.persistence.repositorio.";

    /**
     * <b>Los hechos que SON la propiedad</b>, y la tabla de cada uno.
     *
     * <p>Es un mapa {@code repositorio -> tabla} y no un {@code Set} de
     * repositorios porque la unidad de la decision es la <b>tabla</b>: lo que
     * hay que poder responder es "¿que tablas de hechos del inmueble estan
     * vigiladas?", y un conjunto de repositorios no responde eso sin que
     * alguien traduzca de memoria.
     *
     * <p><b>Las dos ultimas entradas faltaban</b>, y se llevaban la mayor parte
     * del hecho: casi todas las claves gobernadas de una PROPIEDAD se guardan
     * como fila de {@code atributo_propiedad} (o de su tabla de opciones, si
     * son multivalor), y solo las cuatro declaradas ESTRUCTURAL viajan por
     * columnas de {@code propiedad}. El gate vigilaba las columnas y dejaba
     * fuera las filas.
     *
     * <p><b>La proporcion no se escribe aqui</b>, a proposito: la mide
     * {@code AlcanceYGobiernoDeLaAutoridadIntegrationTest} contra el catalogo
     * vivo. Este mismo corte fue rechazado, entre otras cosas, por transcribir
     * cifras que ya no eran ciertas cuando se escribieron.
     */
    private static final Map<String, String> TABLAS_DE_LA_PROPIEDAD = Map.of(
            REPOSITORIOS + "PropiedadRepository", "propiedad",
            REPOSITORIOS + "FotoPropiedadRepository", "foto_propiedad",
            REPOSITORIOS + "TitularidadPropiedadRepository", "titularidad_propiedad",
            REPOSITORIOS + "AtributoPropiedadRepository", "atributo_propiedad",
            REPOSITORIOS + "ValorMultipleAtributoRepository", "atributo_propiedad_opcion",
            REPOSITORIOS + "UnidadRelacionadaRepository", "unidad_relacionada");

    /**
     * <b>Los hechos que son del ENCARGO</b>, que es otro universo con OTRA
     * guarda: {@code exigirEdicionDelEncargo}.
     *
     * <p>No es una sutileza: fundir los dos universos hacia "llama a la
     * autoridad, la que sea" es lo que dejo pasar el primer sabotaje de este
     * gate. Quitar {@code exigirEdicion} de {@code editar} lo dejaba VERDE,
     * porque {@code editar} llama a {@code actualizarEncargo} y aquel si
     * consulta la autoridad -- <b>la del encargo</b>. Dos autoridades distintas
     * comprobadas como si fueran una es exactamente el OR que este P0 vino a
     * quitar de {@code exigirPertenencia}.
     *
     * <p>Aqui entran la serie economica y las <b>condiciones pactadas</b>
     * (garantia, adelanto, plazo): son datos del trato tanto como el importe, y
     * responden ante quien lo negocio, no ante quien responde por el inmueble.
     *
     * <p><b>Y la tabla del encargo mismo</b>, que faltaba y se llevaba la via
     * mas directa: {@code captacion} guarda exclusividad, vigencia, urgencia y
     * observaciones, y su <b>importe</b> vive en
     * {@code condicion_economica_captacion}, que no tiene repositorio propio —
     * entra por <b>cascada</b> del {@code save} de {@code CaptacionRepository}
     * (la FK va de {@code captacion.id_condicion_economica} a la condicion, no
     * al reves). Vigilar solo {@code precio_propiedad} dejaba fuera
     * {@code PUT /captaciones/{id}}, que reescribe la condicion economica
     * entera sin dejar hito.
     */
    private static final Map<String, String> TABLAS_DEL_ENCARGO = mapa(
            REPOSITORIOS + "PrecioPropiedadRepository", "precio_propiedad",
            REPOSITORIOS + "AtributoEncargoRepository", "atributo_encargo",
            REPOSITORIOS + "ValorMultipleEncargoRepository", "atributo_encargo_opcion",
            REPOSITORIOS + "CaptacionRepository", "captacion",
            REPOSITORIOS + "DocumentoEncargoRepository", "documento_encargo",
            REPOSITORIOS + "ArchivoEncargoRepository", "archivo_encargo",
            REPOSITORIOS + "PeticionRevisionEncargoRepository", "peticion_revision_encargo",
            REPOSITORIOS + "SolicitudFinalizacionEncargoRepository", "solicitud_finalizacion_encargo",
            REPOSITORIOS + "RevisionEncargoRepository", "revision_encargo",
            REPOSITORIOS + "ComposicionEncargoRepository", "composicion_encargo",
            REPOSITORIOS + "CargaArchivoContextualRepository", "carga_archivo_contextual");

    /**
     * <b>Lo que vive en el paquete del inmueble y NO es un hecho gobernado
     * suyo</b>, con el motivo de cada uno.
     *
     * <p>Existe para que el control de cobertura pueda ser una comparacion de
     * verdad. La version anterior recorria el mismo {@code Set} que declaraba,
     * asi que una tabla ausente del conjunto era <b>invisible</b> — el gate no
     * podia ver lo que le faltaba, que es exactamente la forma del fallo de 4.P
     * que decia prevenir. Ahora se enumeran las entidades reales del paquete y
     * cada tabla tiene que estar clasificada: vigilada en un universo, o aqui
     * con su razon. Una entidad nueva pone el gate en <b>rojo</b> hasta que
     * alguien decida cual de las dos cosas es.
     */
    private static final Map<String, String> FUERA_DE_LOS_DOS_UNIVERSOS = mapa(
            "copia_comercial_foto",
            "Artefacto saneado de Documentos, no un atributo físico ni un pacto. FotosComercialesService.preparar "
                    +"es el único escritor y exige AGENTE, lectura actual de propiedad/original y linaje. "
                    +"Su censo por repositorio y mutadores y sus guardas se comprueban en copiasComercialesTienenUnEscritorConLecturaActual.",
            "pieza_comercial",
            "Versión y salidas de Documentos sobre SU Encargo, no edición de datos físicos ni condiciones pactadas. "
                    +"PiezasComercialesServiceImpl prepara/regenera con C1 y responsable del Encargo; generar sólo llega desde "
                    +"preview/difusión guardados, con selección Core y PUB para DIF. Censo y guardas propios en piezasTienenEscritoresYEntradasCerrados.",
            "asignacion_responsable_propiedad",
            "es el rastro de QUIEN puede escribir, no un hecho escrito. Tiene su propio "
                    + "gate -- unSoloEscritorDelRastroDeTraspasos -- que es mas estricto: "
                    + "un unico escritor, no una guarda",

            "perfil_catalogo_opciones",
            "vocabulario jurisdiccional privado del tenant, subordinado a una definición. "
                    +"GobiernoCatalogoCapturaServiceImpl exige TENANT_ADMIN actual, versión y snapshot; no escribe un inmueble",

            "revision_catalogo_gobernado",
            "snapshot inmutable del gobierno del vocabulario, no del dato de un inmueble. "
                    +"GobiernoCatalogoCapturaServiceImpl conserva autor y versiones de definiciones/perfiles",

            "catalogo_atributo",
            "es el vocabulario de la organizacion, no un dato de ningun inmueble. Lo "
                    + "gobierna el tenant; una propiedad no lo escribe nunca",

            "distrito",
            "es geografia compartida, anterior a cualquier propiedad y comun a todas. No "
                    + "pertenece a ninguna",

            "observacion_mercado",
            "AC054: hecho observado append-only, separado del físico y del pacto. "
                    + "ObservacionMercadoServiceImpl exige AGENTE y lectura física actual para registrar; "
                    + "AGENTE/BROKER autorizados para consultar. No exige ser editor del físico, "
                    + "ni abre una propiedad sin lectura. Sus guardas se comprueban aparte",

            "publicacion",
            "es el anuncio de un ENCARGO, no un hecho del inmueble. Su autoridad es la del "
                    + "encargo y ya se comprueba: el hito 'P' que escribe cae en "
                    + "precio_propiedad, que si esta vigilado en el universo del encargo. Desde "
                    + "V89 (D-P0-11) esa pertenencia es ESTRUCTURAL -- id_captacion es NOT NULL "
                    + "-- asi que ya no puede existir un anuncio del que no se sepa que encargo "
                    + "responde por el",

            // ==========================================================
            // Y ahora el paquete COMERCIAL entero (N44)
            //
            // Hasta este corte el control de cobertura solo miraba
            // `domain.inmueble`, asi que las 24 entidades de
            // `domain.comercial` --incluida `captacion`, que ES el
            // ENCARGO-- no tenian que estar clasificadas: una entidad
            // nueva ahi no ponia nada en rojo. Cada tabla responde ahora
            // por si misma, y las que quedan fuera dicen QUE agregado son
            // y QUIEN decide sobre ellas.
            //
            // El criterio de exclusion es uno solo y es comprobable: la
            // tabla no guarda un hecho de la PROPIEDAD (lo que el inmueble
            // ES) ni del ENCARGO (lo que se pacto: importe, exclusividad,
            // vigencia, condiciones), asi que ni exigirEdicion ni
            // exigirEdicionDelEncargo son la pregunta correcta sobre ella.
            // ==========================================================

            "reasignacion_captacion",
            "es el rastro de QUIEN lleva el encargo, no un hecho escrito del encargo. Gemela "
                    + "exacta de asignacion_responsable_propiedad, y como aquella tiene reglas "
                    + "MAS estrictas que una guarda: unSoloEscritorDelAgenteDelEncargo y "
                    + "unSoloEscritorDelCompareAndSetDelAgente, en esta misma clase",

            "contrato_alquiler",
            "es OTRO agregado con su propio ciclo, el del BROKER: ContratoServiceImpl decide "
                    + "con cargarConAcceso y exigirAlcance. Es el hecho que CIERRA un encargo, y "
                    + "ese efecto SI entra en el universo del ENCARGO -- por registrar, firmar y "
                    + "cerrarLocal, que estan los tres declarados arriba con su motivo",

            "revision_disponibilidad",
            "es la revision que abre el contrato al terminar, del agregado CONTRATO y escrita "
                    + "por ContratoServiceImpl. Su efecto sobre el inmueble -devolver la "
                    + "disponibilidad- si entra en el universo de la PROPIEDAD, por "
                    + "revisarDisponibilidad, que esta declarado arriba",

            "comision_liquidacion",
            "raíz del resultado económico C5, no una edición física ni un pacto. ComisionServiceImpl "
                    +"exige contexto/tenant y decisión financiera competente por Actuaciones, MANDATORY/CAS; "
                    +"evaluaciones y recibos son distintos, con PG/T4 propios. No cambia términos firmados",

            "comision_evaluacion",
            "historia inmutable del agregado comisión, no un atributo físico. ComisionServiceImpl "
                    +"evalúa fuentes/pacto exactos dentro del hito competente; FK tenant/contexto/versión y conservación SQL",

            "comision_tramo",
            "parte explícita del pacto económico de comisión, nunca depósito sintético. ComisionServiceImpl "
                    +"lo materializa desde el pacto autorizado bajo contexto y transacción; no edición física",

            "expediente_operacion",
            "agregado propio C5 de formalización. FormalizacionServiceImpl exige contexto de oferta/Encargo, "
                    +"responsable para presentar y decisión competente para evaluar/confirmar mediante Actuaciones",

            "revision_expediente_operacion",
            "historia inmutable del expediente C5, no estado físico. FormalizacionServiceImpl conserva "
                    +"la revisión humana con cobertura, actor, tenant y versiones; no autoriza datos ajenos",

            "hito_contractual",
            "hecho del agregado de formalización C5. FormalizacionServiceImpl confirma sólo por autoridad "
                    +"competente y actuación humana; callback comercial/financiero atómico, PG/T4 propios",

            "revision_hito_contractual",
            "historia inmutable de hito y rectificación, no atributo físico. FormalizacionServiceImpl "
                    +"conserva fecha efectiva/registro/evidencia/tenant/versiones bajo la misma decisión competente",

            "comision_movimiento",
            "son los movimientos de esa misma liquidacion -mismo agregado, mismo servicio, "
                    + "misma autoridad-, con idempotencia por clave_idempotencia",

            "oportunidad_comercial",
            "es el hub del lado DEMANDA -un interesado y su recorrido-, no un hecho del "
                    + "inmueble. Lo gobierna OportunidadServiceImpl con cargarConAcceso y el "
                    + "alcance de supervision",

            "prospeccion",
            "es el trabajo ANTERIOR al encargo: todavia no hay trato que editar. Lo gobierna "
                    + "ProspeccionServiceImpl con cargarConAcceso. Cuando se convierte en "
                    + "encargo, ese acto -captar- si entra en los dos universos y esta "
                    + "declarado arriba en los dos",

            "solicitud_alquiler",
            "es lo que pide un CLIENTE, no lo que la propiedad es ni lo que se pacto con el "
                    + "propietario. Su autoridad es AccesoSolicitud, que es un componente "
                    + "aparte precisamente porque la pregunta es otra",

            "documento_solicitud",
            "son los adjuntos de esa solicitud -mismo agregado-, escritos por "
                    + "DocumentoSolicitudServiceImpl bajo la misma autoridad, AccesoSolicitud",

            "evaluacion_solicitud",
            "es la decision del BROKER sobre una solicitud: EvaluacionServiceImpl exige "
                    + "AccesoSolicitud y ademas supervision sobre el agente. Es gobierno del "
                    + "expediente del cliente, no edicion del inmueble ni del encargo",

            "tipo_documento_requerido",
            "es el catalogo de que documentos pide la organizacion. Lo gobierna el tenant, "
                    + "igual que catalogo_atributo; ninguna propiedad ni ningun encargo lo "
                    + "escribe",

            "requerimiento_cliente",
            "es lo que el cliente BUSCA -zona, metraje, presupuesto-, el reverso de la oferta. "
                    + "Vive en el expediente del cliente y lo escribe RequerimientoServiceImpl",

            "requerimiento_criterio",
            "son los criterios del mismo agregado REQUERIMIENTO_CLIENTE, no hechos de una "
                    + "propiedad. RequerimientoServiceImpl los escribe junto con el requerimiento "
                    + "consultado por tenant; RequerimientosController exige AGENTE en su puerta. "
                    + "Su autoridad no es AutoridadDePropiedad ni la de un encargo",

            "interaccion_comercial",
            "es lo que se HIZO -llamada, mensaje, reunion-, no lo que la propiedad es ni lo "
                    + "que se pacto. InteraccionServiceImpl autoriza cada vínculo, participante y campo "
                    + "con ContextosDeInteraccion antes de hidratar, contar o devolver historia",

            "visita",
            "es una visita al inmueble: actividad comercial fechada, no un hecho suyo. "
                    + "VisitaServiceImpl reutiliza AccesoOportunidad y conserva versiones, participantes y alcance actual",

            "motivo_no_continuidad",
            "es por que se dejo de seguir una oportunidad. Pertenece al cierre del recorrido "
                    + "del cliente y lo escriben OportunidadServiceImpl y VisitaServiceImpl bajo "
                    + "la autoridad de aquel",

            "reporte_propietario",
            "es lo que se le CUENTA al propietario, un informe derivado de la actividad. "
                    + "ReportePropietarioServiceImpl lo emite bajo el alcance sobre el agente; "
                    + "no escribe ningun hecho del inmueble ni del trato",

            "alerta",
            "es un aviso derivado -«tu captacion fue cerrada»-, no un dato. AlertaServiceImpl "
                    + "lo emite y lo lee por el alcance del actor; exigir aqui la autoridad de "
                    + "edicion impediria avisar al agente de algo que decidio otro",

            "tarea",
            "es la cola de trabajo, derivada de hechos que ya tienen su propia autoridad. La "
                    + "gobierna TareaServiceImpl por el tenant y el destinatario; nada de lo que "
                    + "guarda es un hecho de la propiedad ni del encargo",

            "meta_comercial",
            "son los objetivos del equipo, del agregado PERSONA/EQUIPO. MetaComercialServiceImpl "
                    + "exige BROKER y decide por el alcance de supervision",

            "meta_revision",
            "son las revisiones de esa misma meta: mismo agregado, mismo servicio, misma "
                    + "autoridad",

            // ---------------- Los dos que aparecieron con WP-A ----------------

            "oferta",
            "es una vuelta de la NEGOCIACION -que ofrecio quien, contra que y como acabo-, no un "
                    + "hecho del inmueble ni una condicion pactada del encargo: no toca importe "
                    + "autorizado, exclusividad ni vigencia. Lo gobierna OfertaServiceImpl, y su "
                    + "autoridad SI es la del encargo -exigirEdicionDelEncargo-, porque escribir "
                    + "una oferta escribe la serie economica: cada vuelta deja un hito 'O' en "
                    + "precio_propiedad, que esta vigilado en TABLAS_DEL_ENCARGO y es por donde "
                    + "este gate lo comprueba de verdad",

            "contrato_compraventa",
            "es OTRO agregado, el gemelo de contrato_alquiler por el lado de la venta: el hecho "
                    + "que CIERRA un encargo de venta. Lo gobierna CierreVentaServiceImpl con "
                    + "cargarConAcceso -tenant y alcance- mas exigirEdicionDelEncargo. Sus efectos "
                    + "sobre el ENCARGO -hito 'C' y cierre- y sobre la PROPIEDAD -retirada del "
                    + "mercado- si entran en los dos universos, por cerrar, que esta declarado "
                    + "abajo en los dos con su motivo");

    /**
     * <b>Vigilada de verdad, pero sin repositorio propio</b> (N44).
     *
     * <p>{@code condicion_economica_captacion} guarda el <b>importe</b> de un
     * encargo —el dato mas protegido de P0-4— y no tiene repositorio: la FK va
     * de {@code captacion.id_condicion_economica} a la condicion, asi que entra
     * y sale por <b>cascada</b> del {@code save} de {@code CaptacionRepository},
     * con {@code orphanRemoval}. Vigilarla no necesita una entrada nueva en el
     * universo: <b>ya esta vigilada</b>, porque la unica forma de escribirla es
     * escribir {@code captacion}, que si lo esta. Se declara aqui para que el
     * control de cobertura no la vea como huerfana y para que quede dicho por
     * que no aparece en {@link #TABLAS_DEL_ENCARGO}.
     */
    private static final Map<String, String> VIGILADAS_POR_CASCADA = mapa(
            "condicion_economica_captacion",
            "no tiene repositorio: entra por cascada del save de CaptacionRepository "
                    + "(orphanRemoval), que esta en TABLAS_DEL_ENCARGO. Escribir el importe "
                    + "pactado ES escribir la captacion, y eso ya exige exigirEdicionDelEncargo "
                    + "-- es literalmente la via que descubrio el hallazgo P0-R4");

    private static final Set<String> REPOSITORIOS_DE_LA_PROPIEDAD =
            Set.copyOf(TABLAS_DE_LA_PROPIEDAD.keySet());

    private static final Set<String> REPOSITORIOS_DEL_ENCARGO =
            Set.copyOf(TABLAS_DEL_ENCARGO.keySet());

    /**
     * <b>Los mutadores de una entidad, DERIVADOS del bytecode</b> (N43).
     *
     * <h2>El hueco que cierra</h2>
     * Los dos predicados de este gate eran <b>por repositorio</b>: escribe quien
     * llama a {@code save} —o a uno de {@link #ESCRIBEN}— sobre un repositorio
     * declarado. Con JPA eso deja fuera una familia entera de escrituras: mutar
     * una entidad <b>gestionada</b> y dejar que el {@code flush} la vuelque.
     * Medido el 2026-09-01: {@code CaptacionServiceImpl#decidir},
     * {@code #cerrar} y {@code #cerrarPorContrato} escriben la revision, la
     * fecha y el motivo de cierre <b>sin llamar a {@code captaciones.save}</b> —
     * y el gate no las veia. Un caso de uso nuevo que hiciera lo mismo con el
     * importe o la vigencia habria entrado en verde.
     *
     * <h2>Por que derivado y no una lista</h2>
     * Una lista de nombres de <i>setters</i> transcrita aqui caduca el dia que
     * alguien anada el siguiente, y nada la verificaria — es la familia de
     * fallos que mas caro ha salido en este repositorio. Asi que el mutador se
     * <b>deduce</b>: es un metodo de la entidad que hace al menos un
     * {@link JavaFieldAccess.AccessType#SET} sobre un campo <b>propio</b>. Con
     * esa definicion entran los {@code setXxx}, pero tambien
     * {@code Propiedad#responsable}, {@code Captacion#cerrar},
     * {@code #registrarRevision}, {@code #marcarFinVigencia} y
     * {@code #transicionarA}, que no se llaman "set" nada y escriben igual.
     *
     * <p>Se exige "campo propio" —y no heredado— a proposito: escribir
     * {@code organizacionId}, que vive en {@code EntidadDeOrganizacion}, es
     * fijar el tenant al crear, no editar un hecho del agregado.
     *
     * <p>Los <b>constructores quedan fuera por construccion</b>:
     * {@code JavaClass.getMethods()} no los devuelve ({@code getConstructors()}
     * es aparte). Es lo correcto — un constructor no muta un agregado que ya
     * existe, lo crea, y el alta ya tiene sus propias exenciones declaradas.
     */
    private static Set<String> mutadoresDe(String entidad) {
        Set<String> mutadores = CLASES.stream()
                .filter(clase -> entidad.equals(clase.getFullName()))
                .flatMap(clase -> clase.getMethods().stream())
                .filter(metodo -> metodo.getFieldAccesses().stream()
                        .anyMatch(acceso ->
                                acceso.getAccessType() == JavaFieldAccess.AccessType.SET
                                        && entidad.equals(
                                                acceso.getTargetOwner().getFullName())))
                .map(JavaMethod::getName)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        if (mutadores.isEmpty()) {
            throw new AssertionError(
                    "no se derivo ningun mutador de " + entidad + ". O la entidad cambio de "
                            + "paquete, o dejo de tener campos propios: mientras tanto la mitad "
                            + "de este gate mide una lista vacia y su verde no significa nada.");
        }
        return Set.copyOf(mutadores);
    }

    private static final Set<String> MUTADORES_DE_LA_PROPIEDAD = mutadoresDe(PROPIEDAD);
    private static final Set<String> MUTADORES_DEL_ENCARGO = mutadoresDe(ENCARGO);
    private static final Map<String,Set<String>> MUTADORES_DE_AGREGADOS_ENCARGO=java.util.stream.Stream.of(
            "DocumentoEncargo","ArchivoEncargo","PeticionRevisionEncargo","SolicitudFinalizacionEncargo",
            "RevisionEncargo","ComposicionEncargo","CargaArchivoContextual").collect(java.util.stream.Collectors.toUnmodifiableMap(
                    n->PAQUETE_COMERCIAL+"."+n,n->mutadoresDe(PAQUETE_COMERCIAL+"."+n)));


    /**
     * <b>La guarda de los hechos de la PROPIEDAD: los metodos que DENIEGAN.</b>
     *
     * <p>{@code puedeEditar} y {@code motivoNoEditable} estuvieron aqui y
     * tuvieron que salir: <b>preguntan, no impiden</b>. Los usa la ficha para
     * pintar, asi que cualquier caso de uso que devuelva la ficha los llama de
     * rebote — y con ellos dentro de la lista, {@code editar} contaba como
     * protegido por el simple hecho de responder. Fue el segundo sabotaje de
     * este gate: quitar {@code exigirEdicion} de {@code editar} seguia dando
     * VERDE porque {@code editar} termina en {@code ficha(...)}.
     *
     * <p>La leccion es la que ya conocia este repositorio en otra forma: una
     * comprobacion que acepta un LECTOR como si fuera una guarda no comprueba
     * nada.
     */
    private static final Set<String> GUARDAS_DE_LA_PROPIEDAD =
            Set.of("exigirEdicion", "fijarAlAlta", "asignar");

    /** La guarda de los hechos de un ENCARGO y de su historico economico. */
    private static final Set<String> GUARDAS_DEL_ENCARGO =
            Set.of("exigirEdicionDelEncargo");

    /**
     * <b>La OTRA autoridad legitima sobre un encargo: el ciclo del broker</b>
     * (D-S0-17 filas 5 y 7).
     *
     * <p>Aprobar, observar, rechazar y cerrar un encargo no son edicion del
     * trato —no tocan importe, exclusividad ni vigencia, que es lo que P0-4
     * protege—: son gobierno del ciclo, y quien responde por ellos es el
     * BROKER, no el agente. Exigirles {@code exigirEdicionDelEncargo} seria
     * pedir que el revisor sea el revisado.
     *
     * <p><b>Y no son una exencion.</b> Una exencion en el mapa habria dejado el
     * metodo cubierto pasara lo que pasara con sus guardas: quitarle
     * {@code exigirBandaComercial} —el defecto exacto de P0-R10, el
     * TENANT_ADMIN decidiendo encargos— habria seguido en verde. Se declara
     * como <b>autoridad alternativa</b> y el gate exige <b>las dos</b> llamadas,
     * asi que quitar cualquiera de las dos lo pone rojo.
     *
     * <ul>
     *   <li>{@code cargarConAcceso} — tenant delante (404 de otra corredora) y
     *       alcance sobre el agente del encargo;</li>
     *   <li>{@code exigirBandaComercial} — la banda en el Core y no solo en la
     *       anotacion, <b>despues</b> del anterior para conservar el 404.</li>
     * </ul>
     */
    private static final String SERVICIO_DEL_ENCARGO="com.controllocal.service.encargo.EncargoServiceImpl";
    private static final String ACCESO_ENCARGO="com.controllocal.service.encargo.AccesoEncargo";
    /** Sólo métodos que deniegan: puedeDecidir y facultades NO son guardas. */
    private static final Set<String> GUARDAS_CONTEXTUALES_ENCARGO=Set.of(
            "exigirResponsable","exigirEditor","exigirDecision","modificarContenido");

    /**
     * <b>El censo de llamadores que sostiene tres exenciones</b>, comprobado
     * contra el bytecode en cada build.
     *
     * <p>Una exencion cuyo motivo es «solo se llega desde quien ya exige la
     * autoridad» —o «no se llega desde ningun sitio»— es cierta el dia que se
     * escribe y falsa el dia que aparece el llamador numero N+1. Sin esta
     * comprobacion, ese dia no lo nota nadie y la exencion pasa a tapar
     * exactamente lo que el gate existe para ver.
     */
    private static final Map<String, Set<String>> LLAMADORES_ESPERADOS = Map.of(
            "com.controllocal.service.soporte.EscritorEstructural#aplicar",
            Set.of("com.controllocal.service.soporte.AtributosGobernados"
                            + "#aplicarEstructuralesAlAlta",
                    "com.controllocal.service.soporte.AtributosGobernados#escribirEnEdicion"),

            "com.controllocal.service.soporte.EscritorEstructural#vaciar",
            Set.of("com.controllocal.service.soporte.AtributosGobernados#retirar"),

            "com.controllocal.service.impl.LocalComercialServiceImpl#resolverDistrito",
            Set.of(),

            "com.controllocal.service.soporte.AtributosDeEncargo#escribir",
            Set.of("com.controllocal.service.encargo.CondicionesDinamicasEncargo#aplicar"),
            "com.controllocal.service.soporte.AtributosDeEncargo#retirar",
            Set.of("com.controllocal.service.encargo.CondicionesDinamicasEncargo#aplicar"),
            "com.controllocal.service.encargo.CondicionesDinamicasEncargo#aplicar",
            Set.of("com.controllocal.service.encargo.EncargoServiceImpl#abrir",
                    "com.controllocal.service.encargo.EncargoServiceImpl#editar",
                    "com.controllocal.service.encargo.EncargoServiceImpl#activarAdenda"),
            "com.controllocal.service.impl.ContratoServiceImpl#cerrarComposicionConfirmada",
            Set.of("com.controllocal.service.impl.ContratoServiceImpl#cerrarResultadoConfirmado"),
            "com.controllocal.service.impl.ContratoServiceImpl#cerrarResultadoConfirmado",
            Set.of("com.controllocal.service.impl.ContratoServiceImpl#confirmarHito"),
            "com.controllocal.service.encargo.EncargoServiceImpl#verificarEvidencias",
            Set.of("com.controllocal.service.encargo.EncargoServiceImpl#comprobarDecision",
                    "com.controllocal.service.encargo.EncargoServiceImpl#solicitarFin",
                    "com.controllocal.service.encargo.EncargoServiceImpl#presentar",
                    "com.controllocal.service.encargo.EncargoServiceImpl#validarPartes",
                    "com.controllocal.service.encargo.EncargoServiceImpl#vigencia",
                    "com.controllocal.service.encargo.EncargoServiceImpl#aplicarFin"),
            "com.controllocal.service.encargo.EncargoServiceImpl#comprobarDecision",
            Set.of("com.controllocal.service.encargo.EncargoServiceImpl#validarDecision",
                    "com.controllocal.service.encargo.EncargoServiceImpl#decidir"));

    /**
     * <b>Los metodos que PREGUNTAN por la autoridad para PINTAR, no para
     * impedir</b> — y por los que este gate <b>no</b> sigue el rastro.
     *
     * <h2>La tercera vez que el gate se escapo por el mismo sitio</h2>
     * Las dos primeras fueron {@code puedeEditar} y {@code motivoNoEditable},
     * que estuvieron en la lista de guardas y tuvieron que salir. Esta es la
     * misma leccion en su forma <b>local</b>, y la introdujo D-P0-12 al
     * publicar las capacidades: {@code capacidadesDe} calcula
     * {@code puedeEditar} preguntandoselo al <b>mismo metodo que despues
     * deniega</b> —que es lo correcto para no tener dos criterios— pero dentro
     * de un {@code try/catch} que <b>se traga la negativa</b>. Y toda escritura
     * de esta clase termina devolviendo la ficha.
     *
     * <p>Resultado medido el 2026-09-02: quitar
     * {@code autoridad.exigirEdicionDelEncargo(actor, cap)} de
     * {@code CaptacionServiceImpl#actualizar} —el sabotaje A, el defecto que
     * P0-4 vino a cerrar— dejaba este gate en <b>VERDE</b>, porque
     * {@code actualizar} acaba llamando a {@code fichaDe} &rarr;
     * {@code fichaIndividual} &rarr; {@code capacidadesDe} &rarr;
     * {@code autoridadDelEncargo} &rarr; la guarda. El gate veia la llamada y no
     * podia ver que su excepcion se descartaba.
     *
     * <p><b>Una llamada cuyo rechazo se descarta no es una guarda</b>: es una
     * pregunta. ArchUnit no ve el {@code try/catch}, asi que el corte se declara
     * aqui, por su nombre y con su motivo — igual que las exenciones.
     */
    private static final Map<String, String> PREGUNTAN_NO_IMPIDEN = Map.of(
            // EL GEMELO, que estaba fuera. Mismo patron, misma guarda, otra
            // clase: la ficha universal calcula el `puedeEditar` de cada
            // encargo igual que la ficha individual. Y esta clase es la de
            // `editar` y `actualizarEncargo`, o sea la de las escrituras que
            // este gate mas vigila: dejarlo contar como guarda ponia a
            // disposicion de TODA la clase una "autoridad" que no deniega.
            "com.controllocal.service.impl.PropiedadUniversalServiceImpl#autoridadDelEncargo",
            "identico al de CaptacionServiceImpl y por la misma razon (D-P0-12): try/catch "
                    + "sobre exigirEdicionDelEncargo que devuelve un booleano para la ficha "
                    + "universal. Se traga AccesoNoAutorizadoException, asi que no impide "
                    + "ninguna escritura -- y toda escritura de esta clase termina devolviendo "
                    + "la ficha",

            // EL TERCERO, de WP-A. Mismo patron y misma trampa: las capacidades
            // de la mesa se preguntan al metodo que despues deniega -que es lo
            // correcto para no tener dos criterios- dentro de un try/catch que
            // se traga la negativa. Sin esta entrada, presentar y contraofertar
            // -- que escriben el hito 'O' -- contarian como protegidos por haber
            // preguntado para pintar.
            "com.controllocal.service.impl.OfertaServiceImpl#autoridadDelEncargo",
            "identico a los dos anteriores (WP-A): try/catch sobre exigirEdicionDelEncargo para "
                    + "responder las capacidades de la mesa. No impide nada -- si la autoridad "
                    + "dice que no, devuelve false y la escritura seguiria su camino");

    /**
     * Los metodos por los que un repositorio escribe.
     *
     * <p>Los tres ultimos son los borrados <b>por clave logica</b>, y estaban
     * fuera: un {@code deleteByIdPropiedadAndClave} retira un hecho gobernado
     * igual que un {@code save} lo pone, pero no se llama "delete" a secas y el
     * gate no lo reconocia. Ya estaban inventariados en
     * {@code LinajeDeTodaEscrituraTest.ESCRIBEN_UN_VALOR}: el linaje los veia y
     * la autoridad no.
     */
    private static final Set<String> ESCRIBEN = Set.of(
            "save", "saveAndFlush", "delete", "deleteById", "saveAll",
            "deleteByIdPropiedadAndClave", "deleteByIdCaptacionAndClave", "borrarDe",
            "incrementarVersionFisicaSi");

    /**
     * <b>Las excepciones del universo PROPIEDAD, con su motivo.</b>
     *
     * <p>No es una lista de perdones: es la parte del inventario que se
     * respondio con "no, y por esto". Cada entrada tuvo que justificarse una
     * por una, y anadir la siguiente obliga a escribir su razon en el mismo
     * sitio donde cualquiera la va a leer.
     *
     * <h2>Por que hay DOS mapas y no uno</h2>
     * Habia uno solo, consultado <b>antes</b> de mirar que guarda tocaba, asi
     * que una exencion escrita pensando en la PROPIEDAD eximia al mismo metodo
     * en el universo del ENCARGO. {@code cerrarLocal} estaba exento en los dos
     * por una unica entrada: se sostenia <b>por suerte, no por construccion</b>.
     * Era la fusion "que llame a la autoridad, la que sea" —la misma que este
     * gate quito del lado de las guardas— reintroducida por el lado de las
     * exenciones. Un metodo que necesite las dos ahora lo dice dos veces, cada
     * vez con su razon.
     */
    private static final Map<String, String> SIN_AUTORIDAD_DE_LA_PROPIEDAD = mapa(
            "com.controllocal.service.impl.PropiedadUniversalServiceImpl#registrar",
            "el alta CREA la fila: no hay responsable anterior a quien respetar. Fija "
                    + "el suyo por AutoridadDePropiedad.fijarAlAlta y lo deja escrito por "
                    + "anotarElAlta (V88), que es lo contrario de saltarse la autoridad. "
                    + "Solo vale para una propiedad NUEVA: reutilizar una existente jamas "
                    + "puede pasar por aqui, y el indice parcial uq_asignacion_alta_por_"
                    + "propiedad lo impide en la base",

            "com.controllocal.service.impl.PropiedadUniversalServiceImpl#asignarResponsable",
            "ES el traspaso: llama a AutoridadDePropiedad.asignar, que exige broker. "
                    + "Pedirle ademas la autoridad de edicion haria imposible asignar "
                    + "responsable a una propiedad FALTANTE, que es justo para lo que existe",

            "com.controllocal.service.encargo.EncargoServiceImpl#decidir",
            "C2: revisión competente del Encargo, no edición física. Sólo proyecta disponibilidad D "
                    + "tras aprobar la cobertura exacta y el precio autorizado. El control específico "
                    + "exige comando/exigirDecision y limita todos sus efectos físicos a aplicarDisponibilidad; "
                    + "cualquier setter, repositorio físico u otro auxiliar que los introduzca pone ese control rojo.",

            "com.controllocal.service.impl.ProspeccionServiceImpl#captar",
            "es el ciclo del ENCARGO, no la edicion de la ficha. Ya tiene su autoridad "
                    + "-cargarEnProceso exige que la prospeccion sea del actor- y las tres "
                    + "columnas que toca son la proyeccion del importe del encargo y su "
                    + "entrada al mercado. Exigir aqui la de la propiedad impediria captar "
                    + "una propiedad FALTANTE, y FALTANTE no bloquea el encargo",

            "com.controllocal.service.impl.ContratoServiceImpl#cerrarComposicionConfirmada",
            "RF059/C5: consecuencia de un hito real sobre su composición exacta, no edición física. "
                    +"Único camino privado cerrarResultadoConfirmado←confirmarHito, con exigirDecision del Encargo, "
                    +"contexto/fuente/pacto verificados y MANDATORY. Censo exacto y prueba específica abajo; "
                    +"no concede al broker editar datos físicos ni otras unidades",

            "com.controllocal.service.impl.ContratoServiceImpl#revisarDisponibilidad",
            "misma razon que cerrarLocal, al reves: recupera la disponibilidad cuando el "
                    + "contrato termina. Exigirla dejaria todo inmueble ALQUILADO para "
                    + "siempre",

            "com.controllocal.service.soporte.AtributosGobernados#escribirAlAlta",
            "es el ALTA y solo el alta: escribe los gobernados de una propiedad que acaba "
                    + "de nacer, cuyo responsable lo acaba de fijar fijarAlAlta unas lineas "
                    + "antes. Su gemelo de edicion -escribirEnEdicion- SI exige la "
                    + "autoridad, y el nombre de este metodo es la unica forma de llegar al "
                    + "camino del alta",

            "com.controllocal.service.soporte.AtributosGobernados#aplicarEstructuralesAlAlta",
            "la otra mitad del alta en dos tiempos: aplica las claves ESTRUCTURAL sobre el "
                    + "agregado ANTES del primer save, cuando la fila todavia no tiene id. "
                    + "Misma razon que escribirAlAlta",

            // ---------------- Los que aparecieron con N43 ----------------
            // El predicado por mutadores metio en el universo cuatro metodos
            // que escriben la Propiedad SIN pasar por un repositorio. Ninguno
            // se exime "porque si": los dos primeros SON la autoridad, y los
            // dos ultimos tienen su censo de llamadores comprobado por el gate.

            "com.controllocal.service.soporte.AutoridadDePropiedad#asignar",
            "ES el traspaso, no una escritura que deba consultarlo: fija responsable despues "
                    + "de exigir banda BROKER/TENANT_ADMIN, alcance sobre el saliente (C6), "
                    + "elegibilidad del destino (D-P0-7) y estado observado con compare-and-set "
                    + "(D-P0-9), y deja su fila en asignacion_responsable_propiedad. Que nadie "
                    + "mas pueda hacerlo no se confia a esta lista: lo prueban tres reglas mas "
                    + "estrictas de esta misma clase -- unSoloEscritorDelResponsable, "
                    + "unSoloEscritorDelCompareAndSetDelResponsable y "
                    + "unSoloEscritorDelRastroDeTraspasos",

            "com.controllocal.service.soporte.AutoridadDePropiedad#fijarAlAlta",
            "la otra mitad de lo mismo: es el ALTA de una propiedad NUEVA, donde no hay "
                    + "responsable anterior a quien respetar. Vigilado por las mismas tres "
                    + "reglas, y ademas por uq_asignacion_alta_por_propiedad, que impide en la "
                    + "base un segundo ALTA sobre la misma propiedad",

            "com.controllocal.service.soporte.EscritorEstructural#aplicar",
            "escribe las cuatro claves ESTRUCTURAL sobre el agregado ya cargado y no puede "
                    + "exigir nada: recibe la Propiedad y el nombre del campo, no el Actor. Sus "
                    + "llamadores estan MEDIDOS y son dos: AtributosGobernados#escribirEnEdicion, "
                    + "que llama a exigirEdicion en su primera linea, y "
                    + "#aplicarEstructuralesAlAlta, que es el ALTA y tiene su propia exencion "
                    + "arriba. Ese censo lo comprueba el gate en cada build "
                    + "(elCensoDeLlamadoresQueSostieneCadaExencionSigueSiendoCierto): un tercer "
                    + "llamador pone esto en rojo",

            "com.controllocal.service.soporte.EscritorEstructural#vaciar",
            "el gemelo de retirada, misma razon y mismo mecanismo. Su unico llamador medido es "
                    + "AtributosGobernados#retirar, que exige exigirEdicion antes de nada -- "
                    + "retirar es la escritura mas irreversible de las tres. Censo comprobado "
                    + "por el gate",

            // ---------------- El que aparecio con WP-A ----------------
            "com.controllocal.service.impl.CierreVentaServiceImpl#retirarDelMercado",
            "es el ciclo del ENCARGO de venta, no la edicion de la ficha del inmueble: saca la "
                    + "propiedad del mercado porque se vendio, que es la consecuencia de un hecho "
                    + "ya decidido y no una declaracion sobre el inmueble. Es la misma familia que "
                    + "ContratoServiceImpl#cerrarLocal, con una diferencia que hay que decir: alli "
                    + "el ejecutante es el BROKER, que por P0-1 nunca es responsable de una "
                    + "propiedad; aqui es el AGENTE del encargo, que puede serlo o no. Exigir la "
                    + "autoridad de la propiedad haria imposible cerrar la venta de un inmueble "
                    + "cuyo responsable es otro agente -o que esta FALTANTE-, y eso no es una "
                    + "autoridad sino un bloqueo. Su unico llamador es cerrar, que si exige "
                    + "exigirEdicionDelEncargo",

            "com.controllocal.service.impl.LocalComercialServiceImpl#resolverDistrito",
            "INALCANZABLE: es private y su censo de llamadores, medido el 2026-09-02, esta "
                    + "VACIO -- el resolutor vivo es el homonimo de PropiedadUniversalServiceImpl, "
                    + "que si tiene llamador y esta cubierto por editar. Un metodo al que no se "
                    + "llega no puede alterar ningun dato, que es la unica razon por la que se "
                    + "excluye. El gate comprueba que ese censo siga vacio: el dia que alguien "
                    + "lo cablee, esto se pone rojo y hay que decidir su guarda");

    /**
     * <b>Las excepciones del universo ENCARGO, con su motivo.</b>
     *
     * <p>Lista aparte de la de la propiedad, y esa separacion es la correccion:
     * una exencion concedida en un universo ya no vale en el otro.
     */
    private static final Map<String,String> SIN_AUTORIDAD_DEL_ENCARGO=mapa(
            "com.controllocal.service.encargo.EncargoServiceImpl#abrir",
            "Único INSERT del episodio. AGENTE/responsable coinciden y buscarParaLectura autoriza el objeto; "
                +"la transacción llamante es obligatoria. No autoriza mutar episodios existentes.",
            "com.controllocal.service.soporte.AtributosDeEncargo#escribir",
            "Enrutador con linaje, sin la entidad Captacion necesaria para decidir su autoridad. "
                +"Censo exacto: sólo CondicionesDinamicasEncargo.aplicar, consumido por apertura canónica "
                +"o por la edición con SID/autoridad/CAS o activación privada de adenda tras decisión competente; "
                +"censo exacto y controles específicos abajo.",
            "com.controllocal.service.soporte.AtributosDeEncargo#retirar",
            "Enrutador de retirada con linaje. Sólo CondicionesDinamicasEncargo.aplicar lo invoca; "
                +"la retirada se consume por la edición canónica del Encargo bajo autoridad y CAS, no por el físico.",
            "com.controllocal.service.encargo.EncargoServiceImpl#verificarEvidencias",
            "Auxiliar privado del ciclo C2. La rama de vínculo sólo vincula ArchivoEncargo al episodio "
                +"tras validar tenant, finalidad, autor/origen y documento; presentar está dentro del comando "
                +"responsable, decidir dentro de la decisión competente y sus consultas no vinculan. "
                +"validarPartes se alcanza desde faltantes/presentación/cobertura de adenda y comprueba sin vincular. "
                +"Censo exacto de seis llamadores y control de único mutador ArchivoEncargo.vincular; "
                +"no permite otro hecho del episodio ni edición física.",
            "com.controllocal.service.impl.CaptacionServiceImpl#reasignar",
            "Gobierno de responsabilidad con SID, alcance, destino elegible, versión y responsable observados, CAS y rastro atómicos. "
                +"Las llamadas obligatorias y la unicidad de CAS/rastro se comprueban aparte, no se exime su autoridad.",
            "com.controllocal.service.impl.ContratoServiceImpl#cerrarResultadoConfirmado",
            "Cascada privada desde confirmarHito con exigirDecision, fuente exacta y MANDATORY. "
                +"No modifica condiciones firmadas ni acredita dinero por cerrar. Censo medido arriba.");

    /**
     * <b>Los mutadores se DERIVAN, y el gate lo demuestra</b> (N43).
     *
     * <p>Sin este control positivo, el dia que {@code Propiedad} cambie de
     * paquete o que el importador deje de ver el dominio, {@link #mutadoresDe}
     * devolveria una lista corta y la mitad nueva de los dos predicados dejaria
     * de reconocer nada — verde sin vigilar. Se exigen por nombre los que
     * <b>no</b> se llaman {@code setXxx}, que son justamente los que una lista
     * escrita a mano habria olvidado.
     */
    @Test
    @DisplayName("los mutadores de la propiedad y del encargo salen del bytecode")
    void losMutadoresSalenDelBytecode() {
        for (String imprescindible : List.of("responsable", "setDescripcion")) {
            assertTrue(MUTADORES_DE_LA_PROPIEDAD.contains(imprescindible),
                    "no se derivo el mutador `" + imprescindible + "` de Propiedad. Encontro: "
                            + new java.util.TreeSet<>(MUTADORES_DE_LA_PROPIEDAD));
        }
        for (String imprescindible : List.of(
                "setAgente", "setCondicionEconomica", "cerrar", "registrarRevisionAplicable")) {
            assertTrue(MUTADORES_DEL_ENCARGO.contains(imprescindible),
                    "no se derivo el mutador `" + imprescindible + "` de Captacion. Encontro: "
                            + new java.util.TreeSet<>(MUTADORES_DEL_ENCARGO));
        }
    }

    /**
     * <b>La prueba de que la ampliacion sirvio para algo</b> (N43).
     *
     * <p>Es el control que convierte "ahora tambien mira los mutadores" en un
     * hecho comprobable en vez de una afirmacion: el predicado <b>anterior</b>
     * —solo por repositorio— <b>no veia</b> {@code CaptacionServiceImpl#cerrar},
     * y el actual si. Si alguien revirtiera la ampliacion, esta prueba se
     * pondria roja aunque las demas siguieran verdes, porque las demas solo
     * saben mirar lo que el predicado les da.
     */
    @Test
    @DisplayName("el gate ve ahora las escrituras por dirty-checking que antes no veia")
    void elGateVeLoQueElPredicadoPorRepositorioNoVeia() {
        List<String> porRepositorio = nombresDe(escriturasDe(m ->
                m.getAccessesFromSelf().stream().anyMatch(a ->
                        REPOSITORIOS_DEL_ENCARGO.contains(a.getTargetOwner().getFullName())
                                && ESCRIBEN.contains(a.getName()))));
        List<String> conMutadores = nombresDe(escriturasDe(
                AutoridadDeLaPropiedadTest::escribeLaSerie));

        String porDirtyChecking = "com.controllocal.service.encargo.EncargoServiceImpl#aplicarFin";
        assertFalse(porRepositorio.contains(porDirtyChecking),
                "`cerrar` ya aparecia con el predicado viejo, asi que este control ha dejado de "
                        + "medir lo que dice: escoge otra escritura que solo exista por "
                        + "dirty-checking. Encontro: " + porRepositorio);
        assertTrue(conMutadores.contains(porDirtyChecking),
                "el predicado ampliado no ve " + porDirtyChecking + ", que muta la Captacion "
                        + "gestionada y deja que el flush la vuelque. Sin el, N43 sigue abierto.");
    }

    /**
     * <b>Una exencion cuyo motivo son sus llamadores tiene que envejecer en
     * rojo.</b>
     *
     * <p>Tres de las exenciones nuevas no dicen "aqui no hace falta autoridad":
     * dicen "<b>a este metodo solo se llega desde X</b>, y X ya la exige". Ese
     * motivo es cierto <b>hoy</b> y deja de serlo en cuanto alguien anada un
     * llamador — y entonces la exencion taparia justo lo que este gate existe
     * para ver. Asi que el censo de llamadores se escribe y <b>se compara con el
     * bytecode en cada build</b>.
     *
     * <p>Se vigila tambien la <b>interfaz</b>: un controlador llama a
     * {@code CaptacionService#cerrarPorContrato}, no a la clase, asi que contar
     * solo las llamadas a la implementacion daria cero para un metodo cableado.
     */
    @Test
    @DisplayName("el censo de llamadores que sostiene cada exencion sigue siendo cierto")
    void elCensoDeLlamadoresQueSostieneCadaExencionSigueSiendoCierto() {
        for (Map.Entry<String, Set<String>> entrada : LLAMADORES_ESPERADOS.entrySet()) {
            List<String> reales = llamadoresDe(entrada.getKey());
            assertEquals(entrada.getValue().stream().sorted().toList(), reales,
                    "cambio quien llama a " + entrada.getKey() + ". Su exencion en este gate se "
                            + "sostiene EXACTAMENTE sobre ese censo -- «solo se llega desde "
                            + "alguien que ya exige la autoridad», o «no se llega desde ningun "
                            + "sitio»--, y con un llamador nuevo el motivo dejo de ser cierto. "
                            + "Decide la guarda del camino nuevo antes de tocar esta lista.");
        }
    }

    /**
     * Quien llama a un metodo, mirando tambien las <b>interfaces</b> que lo
     * declaran: en el bytecode una llamada por interfaz tiene como dueno la
     * interfaz, no la clase.
     */
    private static List<String> llamadoresDe(String objetivo) {
        String duena = objetivo.substring(0, objetivo.indexOf('#'));
        String metodo = objetivo.substring(objetivo.indexOf('#') + 1);
        Set<String> duenas = new HashSet<>();
        duenas.add(duena);
        CLASES.stream()
                .filter(clase -> duena.equals(clase.getFullName()))
                .flatMap(clase -> clase.getAllRawInterfaces().stream())
                .map(JavaClass::getFullName)
                .forEach(duenas::add);
        return CLASES.stream()
                .flatMap(clase -> clase.getMethods().stream())
                .filter(m -> !nombre(m).equals(objetivo))
                .filter(m -> m.getAccessesFromSelf().stream().anyMatch(a ->
                        duenas.contains(a.getTargetOwner().getFullName())
                                && metodo.equals(a.getName())))
                .map(AutoridadDeLaPropiedadTest::nombre)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * <b>Solo la autoridad mueve la autoridad.</b>
     *
     * <p>Si {@code Propiedad#responsable} fuera de uso libre, cualquier caso de
     * uso podria darse el permiso a si mismo antes de comprobarlo — y la
     * comprobacion seguiria ahi, verde, sin proteger nada. Es el mismo
     * razonamiento por el que el estado solo muta por {@code Transiciones}.
     */
    @Test
    @DisplayName("solo AutoridadDePropiedad fija quien responde por una propiedad")
    void unSoloEscritorDelResponsable() {
        List<String> intrusos = CLASES.stream()
                .filter(clase -> !AUTORIDAD.equals(clase.getFullName()))
                .flatMap(clase -> clase.getMethods().stream())
                .filter(metodo -> metodo.getAccessesFromSelf().stream()
                        .anyMatch(a -> PROPIEDAD.equals(a.getTargetOwner().getFullName())
                                && "responsable".equals(a.getName())))
                .map(metodo -> metodo.getOwner().getName() + "#" + metodo.getName())
                .sorted()
                .toList();

        assertEquals(List.of(), intrusos,
                "estos metodos fijan el responsable de una propiedad por su cuenta: " + intrusos
                        + ". Quien puede escribir la propiedad lo decide AutoridadDePropiedad, y "
                        + "solo cambia por el alta o por un traspaso de broker con su fila en "
                        + "asignacion_responsable_propiedad. Un setter suelto convierte la "
                        + "autoridad en autoservicio.");
    }

    /**
     * <b>Y la OTRA forma de fijar el responsable tiene el mismo dueno</b>
     * (D-P0-9).
     *
     * <p>El de arriba vigila el <b>setter de la entidad</b>. Desde el
     * compare-and-set hay una segunda escritura de {@code id_rol_responsable}
     * que <b>no pasa por la entidad</b>: un {@code UPDATE} JPQL en
     * {@code PropiedadRepository}. Un gate que solo mirara el setter la dejaria
     * pasar entera — y es justo la que puede mover la columna <b>saltandose el
     * estado observado</b>, que es lo que D-P0-9 vino a impedir.
     *
     * <p>La regla es la misma que la de su gemelo, dicha sobre el otro camino:
     * quien mueva la autoridad tiene que pasar por quien la decide. Lo que
     * cambia es el objetivo del acceso —el metodo del repositorio en vez del
     * metodo del dominio—, no el criterio.
     *
     * <p>Lleva <b>control positivo</b>: se exige que la autoridad SI lo llame.
     * Sin eso, el dia que el metodo se renombre o el repositorio cambie de
     * paquete, este gate mediria una lista vacia y se quedaria verde sin vigilar
     * nada — la leccion del barrido de {@code grep -iF} del 2026-08-24.
     */
    @Test
    @DisplayName("solo AutoridadDePropiedad ejecuta el compare-and-set del responsable")
    void unSoloEscritorDelCompareAndSetDelResponsable() {
        List<String> intrusos = CLASES.stream()
                .filter(clase -> !AUTORIDAD.equals(clase.getFullName()))
                .flatMap(clase -> clase.getMethods().stream())
                .filter(AutoridadDeLaPropiedadTest::ejecutaElCompareAndSet)
                .map(metodo -> metodo.getOwner().getName() + "#" + metodo.getName())
                .sorted()
                .toList();

        assertEquals(List.of(), intrusos,
                "estos metodos mueven id_rol_responsable con el compare-and-set del repositorio, "
                        + "por fuera de la autoridad: " + intrusos
                        + ". Es una escritura del responsable como cualquier otra, y ademas la "
                        + "unica que puede saltarse el estado observado (D-P0-9): un traspaso que "
                        + "no declara de donde parte vuelve a ser «la ultima escritura gana». "
                        + "Quien necesite mover la autoridad llama a AutoridadDePropiedad.asignar.");

        // CONTROL POSITIVO: el gate tiene que VER la llamada legitima. Un cero
        // que no se ha comprobado contra un caso conocido no es una comprobacion.
        boolean laAutoridadLoLlama = CLASES.stream()
                .filter(clase -> AUTORIDAD.equals(clase.getFullName()))
                .flatMap(clase -> clase.getMethods().stream())
                .anyMatch(AutoridadDeLaPropiedadTest::ejecutaElCompareAndSet);
        assertTrue(laAutoridadLoLlama,
                "el gate no ve ninguna llamada a " + REPOSITORIO_DE_LA_PROPIEDAD + "#" + CAS
                        + " ni siquiera desde AutoridadDePropiedad, que es quien la hace. O se "
                        + "renombro el metodo, o se movio el repositorio: mientras tanto esta "
                        + "regla esta verde sin vigilar nada.");
    }

    /** Un acceso al compare-and-set del responsable, mire quien lo mire. */
    private static boolean ejecutaElCompareAndSet(JavaMethod metodo) {
        return metodo.getAccessesFromSelf().stream()
                .anyMatch(a -> REPOSITORIO_DE_LA_PROPIEDAD.equals(a.getTargetOwner().getFullName())
                        && CAS.equals(a.getName()));
    }

    // ------------------------------------------------------------------
    // La SEGUNDA autoridad mutable de P0: el agente de un ENCARGO
    // ------------------------------------------------------------------

    /**
     * <b>Quien puede fijar el agente de un encargo, y por que cada uno.</b>
     *
     * <p>Son dos familias y no una lista de permisos:
     * <ul>
     *   <li><b>Los tres altas</b> ({@code registrar}, {@code captar},
     *       {@code abrirEncargo}) fijan el agente <b>antes del primer
     *       {@code save}</b>, o sea en el {@code INSERT}: no hay agente anterior
     *       a quien respetar porque el encargo nace ahi, y su agente es el
     *       actor. Un encargo sin agente no existe —{@code id_rol_agente} es NOT
     *       NULL desde V5—, asi que ese primer valor no es una escritura de
     *       autoridad sino la creacion del sujeto.</li>
     *   <li><b>La puerta canonica</b> ({@code reasignar}), que es la unica que
     *       lo cambia sobre un encargo que ya existe, y lo hace con motivo,
     *       actor, estado observado, compare-and-set y fila en
     *       {@code reasignacion_captacion}.</li>
     * </ul>
     *
     * <p>Un quinto llamador —un mapper, una copia de agregado, un
     * {@code PUT} generico, una cascada— pondria el build en rojo con su
     * nombre. Es el gemelo de {@code unSoloEscritorDelResponsable}: si el
     * <i>setter</i> fuera de uso libre, cualquier caso de uso podria darse la
     * autoridad a si mismo y las guardas seguirian ahi, verdes, sin proteger
     * nada.
     */
    @Test
    @DisplayName("solo el alta y la reasignacion fijan el agente de un encargo")
    void unSoloEscritorDelAgenteDelEncargo() {
        List<String> intrusos = CLASES.stream()
                .flatMap(clase -> clase.getMethods().stream())
                .filter(AutoridadDeLaPropiedadTest::fijaElAgenteDelEncargo)
                .map(AutoridadDeLaPropiedadTest::nombre)
                .distinct()
                .filter(nombre -> !PUEDEN_FIJAR_EL_AGENTE.containsKey(nombre))
                .sorted()
                .toList();

        assertEquals(List.of(), intrusos,
                "estos metodos fijan por su cuenta el agente de un encargo: " + intrusos
                        + ". Quien lleva un encargo solo cambia por su ALTA -- donde nace, y ahi "
                        + "es un INSERT -- o por CaptacionServiceImpl.reasignar, que es la puerta "
                        + "canonica: motivo, actor, estado observado, compare-and-set y fila en "
                        + "reasignacion_captacion. Un setter suelto convierte la autoridad del "
                        + "ENCARGO en autoservicio, exactamente igual que en la propiedad.");

        // CONTROL POSITIVO: el gate tiene que VER a los cuatro legitimos. Si el
        // metodo se renombrara o la entidad cambiara de paquete, la lista de
        // arriba saldria vacia y esto seguiria verde sin vigilar nada.
        List<String> vistos = CLASES.stream()
                .flatMap(clase -> clase.getMethods().stream())
                .filter(AutoridadDeLaPropiedadTest::fijaElAgenteDelEncargo)
                .map(AutoridadDeLaPropiedadTest::nombre)
                .distinct()
                .sorted()
                .toList();
        for (String legitimo : PUEDEN_FIJAR_EL_AGENTE.keySet()) {
            assertTrue(vistos.contains(legitimo),
                    "el gate no ve " + legitimo + " fijando el agente del encargo, y es uno de "
                            + "los cuatro que si pueden. O se renombro, o se movio, o dejo de "
                            + "hacerlo: mientras tanto esta regla esta verde sin vigilar nada. "
                            + "Encontro: " + vistos);
        }
    }

    /**
     * <b>Y el compare-and-set del agente tiene un solo dueno</b> (D-P0-9).
     *
     * <p>El de arriba vigila el <i>setter</i>. Como la columna esta mapeada
     * {@code updatable = false}, la <b>unica</b> escritura de
     * {@code id_rol_agente} en un {@code UPDATE} es la sentencia nativa del
     * repositorio — y es justo la que puede mover la autoridad <b>saltandose el
     * estado observado</b>. Un gate que solo mirara el <i>setter</i> la dejaria
     * pasar entera.
     */
    @Test
    @DisplayName("solo CaptacionServiceImpl.reasignar ejecuta el compare-and-set del agente")
    void unSoloEscritorDelCompareAndSetDelAgente() {
        List<String> intrusos = CLASES.stream()
                .flatMap(clase -> clase.getMethods().stream())
                .filter(AutoridadDeLaPropiedadTest::ejecutaElCompareAndSetDelAgente)
                .map(AutoridadDeLaPropiedadTest::nombre)
                .distinct()
                .filter(nombre -> !PUERTA_CANONICA_DEL_ENCARGO.equals(nombre))
                .sorted()
                .toList();

        assertEquals(List.of(), intrusos,
                "estos metodos mueven captacion.id_rol_agente con el compare-and-set del "
                        + "repositorio, por fuera de la puerta canonica: " + intrusos
                        + ". Es una escritura del agente como cualquier otra, y ademas la unica "
                        + "que puede saltarse el estado observado (D-P0-9): una reasignacion que "
                        + "no declara de donde parte vuelve a ser «la ultima escritura gana». "
                        + "Quien necesite mover la autoridad llama a "
                        + PUERTA_CANONICA_DEL_ENCARGO + ".");

        // CONTROL POSITIVO: la puerta canonica tiene que verse ejecutandolo.
        boolean laPuertaLoLlama = CLASES.stream()
                .flatMap(clase -> clase.getMethods().stream())
                .filter(AutoridadDeLaPropiedadTest::ejecutaElCompareAndSetDelAgente)
                .anyMatch(m -> PUERTA_CANONICA_DEL_ENCARGO.equals(nombre(m)));
        assertTrue(laPuertaLoLlama,
                "el gate no ve ninguna llamada a " + REPOSITORIO_DEL_ENCARGO + "#" + CAS_DEL_AGENTE
                        + " ni siquiera desde " + PUERTA_CANONICA_DEL_ENCARGO + ", que es quien "
                        + "la hace. O se renombro el metodo, o se movio el repositorio: mientras "
                        + "tanto esta regla esta verde sin vigilar nada.");
    }

    private static boolean fijaElAgenteDelEncargo(JavaMethod metodo) {
        return metodo.getAccessesFromSelf().stream()
                .anyMatch(a -> ENCARGO.equals(a.getTargetOwner().getFullName())
                        && SETTER_DEL_AGENTE.equals(a.getName()));
    }

    private static boolean ejecutaElCompareAndSetDelAgente(JavaMethod metodo) {
        return metodo.getAccessesFromSelf().stream()
                .anyMatch(a -> REPOSITORIO_DEL_ENCARGO.equals(a.getTargetOwner().getFullName())
                        && CAS_DEL_AGENTE.equals(a.getName()));
    }

    /**
     * <b>Y el rastro del traspaso lo escribe quien decide el traspaso.</b>
     *
     * <p>Sin esto, un segundo escritor podria mover la columna dejando una fila
     * distinta —o ninguna— y el expediente diria dos cosas segun por donde se
     * hubiera hecho. Es la leccion de {@code EventosSeguridad}, que es el unico
     * escritor de {@code evento_seguridad} por la misma razon.
     */
    @Test
    @DisplayName("solo AutoridadDePropiedad escribe el rastro de los traspasos")
    void unSoloEscritorDelRastroDeTraspasos() {
        noClasses()
                .that().haveNameNotMatching(java.util.regex.Pattern.quote(AUTORIDAD) + "(\\$.*)?")
                .should().dependOnClassesThat(new DescribedPredicate<>(
                        "es el repositorio del rastro de traspasos") {
                    @Override
                    public boolean test(JavaClass clase) {
                        return REPOSITORIO_DEL_RASTRO.equals(clase.getFullName());
                    }
                })
                .because("""
                        el traspaso y su rastro son un solo hecho. Un segundo escritor \
                        podria mover la columna sin dejar fila -o dejando otra distinta- y \
                        el expediente diria dos cosas segun por donde se hubiera hecho. \
                        Quien necesite traspasar llama a AutoridadDePropiedad.asignar""")
                .check(CLASES);
    }

    /**
     * <b>La comprobacion que caza el olvido real: la via SIGUIENTE.</b>
     *
     * <p>Se recorre <b>dos veces</b>, una por universo, y con la guarda que le
     * toca a cada uno. Esa separacion no es elegancia: es la correccion que
     * exigio el primer sabotaje de este gate. Con un solo universo y "que llame
     * a la autoridad, la que sea", quitar {@code exigirEdicion} de
     * {@code editar} dejaba el gate VERDE — porque {@code editar} llama a
     * {@code actualizarEncargo}, que consulta la autoridad <b>del encargo</b>.
     * Un gate que acepta la autoridad equivocada no protege de nada: es el
     * mismo OR de {@code exigirPertenencia} un nivel mas arriba.
     */
    @Test
    @DisplayName("toda escritura sobre la propiedad pasa por SU autoridad, o declara por que no")
    void ningunaEscrituraDeLaPropiedadSinSuAutoridad() {
        List<JavaMethod> escrituras = escriturasDe(AutoridadDeLaPropiedadTest::escribeLaPropiedad);
        List<String> nombres = nombresDe(escrituras);

        // CONTROL POSITIVO. Sin esto, el dia que alguien renombre `save` o mueva
        // un repositorio de paquete, el predicado dejaria de reconocer nada y
        // este gate pasaria en verde midiendo una lista vacia. Un cero que no se
        // ha comprobado contra un caso conocido no es una comprobacion -- es la
        // leccion de `grep -iF` del 2026-08-24.
        assertTrue(nombres.size() >= 6,
                "el gate dejo de reconocer las escrituras de la PROPIEDAD: encontro " + nombres
                        + ". Revisa REPOSITORIOS_DE_LA_PROPIEDAD y ESCRIBEN antes de creerte el "
                        + "verde.");
        for (String imprescindible : List.of(
                "com.controllocal.service.impl.PropiedadUniversalServiceImpl#editar",
                "com.controllocal.service.impl.LocalComercialServiceImpl#desactivar",
                // La foto: la via era `LocalComercialServiceImpl#agregarFoto` y
                // desde WP-C es esta. **La galeria se movio, no desaparecio**, y
                // el control positivo tiene que seguir nombrando una escritura
                // que exista -- si nombrara la vieja, el `assertTrue` fallaria
                // por ausencia y no por defecto, y el dia que alguien la quitara
                // de verdad el mensaje diria lo mismo.
                "com.controllocal.service.impl.FotoPropiedadServiceImpl#agregar",
                // Y la mas irreversible de las tres, que antes no estaba
                // nombrada: borrar una foto por su id no puede ser mas barato
                // que borrarla por el id del inmueble.
                "com.controllocal.service.impl.FotoPropiedadServiceImpl#eliminar")) {
            assertTrue(nombres.contains(imprescindible),
                    "el gate no ve " + imprescindible + ", que es una de las vias por las que "
                            + "empezo todo esto. Encontro: " + nombres);
        }

        List<String> sinGuarda = sinGuarda(escrituras, GUARDAS_DE_LA_PROPIEDAD,
                SIN_AUTORIDAD_DE_LA_PROPIEDAD);
        assertEquals(List.of(), sinGuarda,
                "estos metodos escriben un hecho de la PROPIEDAD sin preguntar quien responde "
                        + "por ella: " + sinGuarda
                        + ". Tiene que ser exigirEdicion -- no vale exigirEdicionDelEncargo, que "
                        + "responde otra pregunta. O eso, o entran en "
                        + "SIN_AUTORIDAD_DE_LA_PROPIEDAD con el motivo escrito.");
    }

    /**
     * <b>Y el historico economico responde a la autoridad del ENCARGO.</b>
     *
     * <p>Universo aparte porque la pregunta es otra: un hito {@code U}, {@code P}
     * o {@code C} no es un hecho del inmueble, es un hecho del trato que lo
     * autorizo. De aqui salio el hallazgo de este corte —
     * {@code PublicacionServiceImpl}, que escribia un {@code P} en la serie de
     * cualquier encargo del tenant— y no estaba en el inventario inicial de vias:
     * lo encontro este gate.
     */
    @Test
    @DisplayName("todo hito economico pasa por la autoridad de SU encargo")
    void ningunHitoEconomicoSinLaAutoridadDelEncargo() {
        List<JavaMethod> escrituras = escriturasDe(AutoridadDeLaPropiedadTest::escribeLaSerie);
        List<String> nombres = nombresDe(escrituras);

        assertTrue(nombres.size() >= 4,
                "el gate dejo de reconocer las escrituras de la SERIE ECONOMICA: encontro "
                        + nombres + ". Revisa REPOSITORIOS_DEL_ENCARGO antes de creerte el verde.");
        for (String imprescindible : List.of(
                HISTORIADOR_PUBLICACION+"#guardar",
                // La via mas directa al importe del encargo, y la que este gate
                // no veia: `PUT /captaciones/{id}` reescribe la condicion
                // economica entera -- importe, moneda y comision -- por cascada
                // del save de `captacion`, sin pasar por precio_propiedad.
                "com.controllocal.service.encargo.EncargoServiceImpl#editar")) {
            assertTrue(nombres.contains(imprescindible),
                    "el gate no ve " + imprescindible + ", que es una de las vias que escriben "
                            + "el trato sin que nadie las hubiera inventariado. Encontro: "
                            + nombres);
        }

        List<String> sinGuarda = sinGuarda(escrituras, GUARDAS_DEL_ENCARGO,
                SIN_AUTORIDAD_DEL_ENCARGO);
        assertEquals(List.of(), sinGuarda,
                "estos metodos escriben un hecho de un ENCARGO sin comprobar que sea del "
                        + "actor: " + sinGuarda
                        + ". Tiene que ser exigirEdicionDelEncargo -- no vale exigirEdicion, "
                        + "porque responder por la propiedad no es responder por el encargo de "
                        + "otro.");
    }

    private List<JavaMethod> escriturasDe(java.util.function.Predicate<JavaMethod> escribe) {
        return CLASES.stream()
                .filter(clase -> clase.getPackageName().startsWith("com.controllocal.service"))
                // `Transiciones` ES la primitiva de transicion, no un llamador:
                // exigirle que consulte la autoridad seria pedirle que decida
                // sobre la propiedad a la pieza que existe justo para no decidir
                // -- solo aplica y audita. La autoridad se pregunta en el caso de
                // uso, que es quien sabe que esta haciendo.
                .filter(clase -> !TRANSICIONES.equals(clase.getFullName()))
                .flatMap(clase -> clase.getMethods().stream())
                .filter(escribe)
                .toList();
    }

    private static List<String> nombresDe(List<JavaMethod> metodos) {
        return metodos.stream().map(AutoridadDeLaPropiedadTest::nombre).distinct().sorted().toList();
    }

    private List<String> sinGuarda(List<JavaMethod> escrituras, Set<String> guardas,
                                   Map<String, String> exentos) {
        Set<String> escritores = Set.copyOf(nombresDe(escrituras));
        return escrituras.stream()
                .filter(m -> !cubierto(m, guardas, exentos, escritores, new HashSet<>()))
                .map(AutoridadDeLaPropiedadTest::nombre)
                .distinct()
                .sorted()
                .toList();
    }

    private static String nombre(JavaMethod metodo) {
        return metodo.getOwner().getName() + "#" + metodo.getName();
    }

    /**
     * <b>Ninguna tabla del inmueble se queda sin clasificar.</b>
     *
     * <p>Este es el control que <b>no podia existir</b> en la version anterior.
     * Aquella recorria el mismo {@code Set} que declaraba —"para cada
     * repositorio que he declarado, ¿veo alguna escritura suya?"— asi que una
     * tabla <b>ausente</b> del conjunto era invisible por construccion: el gate
     * no puede echar de menos lo que no se ha nombrado. Es, exactamente, la
     * forma del fallo de 4.P que decia prevenir.
     *
     * <p>La correccion es comparar contra una fuente <b>independiente</b>: las
     * entidades JPA reales del paquete del inmueble, leidas del bytecode. Cada
     * tabla que aparezca tiene que estar clasificada en uno de los tres sitios
     * —universo PROPIEDAD, universo ENCARGO, o exclusion con motivo— y una
     * entidad nueva pone esto en rojo hasta que alguien decida cual es.
     */
    @Test
    @DisplayName("toda tabla del inmueble y de lo comercial esta vigilada o excluida con motivo")
    void ningunaTablaDelInmuebleSinClasificar() {
        Set<String> clasificadas = new HashSet<>(TABLAS_DE_LA_PROPIEDAD.values());
        clasificadas.addAll(TABLAS_DEL_ENCARGO.values());
        clasificadas.addAll(VIGILADAS_POR_CASCADA.keySet());
        clasificadas.addAll(FUERA_DE_LOS_DOS_UNIVERSOS.keySet());

        List<String> entidades = CLASES.stream()
                .filter(clase -> PAQUETE_DEL_INMUEBLE.equals(clase.getPackageName())
                        || PAQUETE_COMERCIAL.equals(clase.getPackageName()))
                .filter(clase -> clase.isAnnotatedWith(Entity.class))
                .map(clase -> clase.getAnnotationOfType(Table.class).name())
                .sorted()
                .toList();

        // CONTROL POSITIVO. Si un paquete cambiara de nombre, o las entidades
        // dejaran de llevar @Table, esta lista saldria corta y el bucle de abajo
        // compararia contra menos de lo que hay -- verde sin haber mirado. Es la
        // leccion del barrido de `grep -iF` del 2026-08-24: un cero sin control
        // positivo no es una medicion. El umbral cubre los DOS paquetes: con
        // solo el del inmueble no se llega a 30.
        assertTrue(entidades.size() >= 30,
                "se esperaban las entidades JPA de " + PAQUETE_DEL_INMUEBLE + " y "
                        + PAQUETE_COMERCIAL + " y se encontraron " + entidades.size() + ": "
                        + entidades + ". Sin ellas este control no compara nada y su verde no "
                        + "significa nada.");
        for (String imprescindible : List.of("propiedad", "captacion")) {
            assertTrue(entidades.contains(imprescindible),
                    "no se ve la tabla `" + imprescindible + "` entre las entidades de los dos "
                            + "paquetes, y es uno de los dos sujetos de P0: " + entidades);
        }

        List<String> huerfanas = entidades.stream()
                .filter(tabla -> !clasificadas.contains(tabla))
                .distinct()
                .sorted()
                .toList();
        assertEquals(List.of(), huerfanas,
                "estas tablas no estan ni vigiladas ni excluidas: " + huerfanas
                        + ". Decide: si guardan un hecho gobernado de la propiedad van a "
                        + "TABLAS_DE_LA_PROPIEDAD; si es del trato, a TABLAS_DEL_ENCARGO; si "
                        + "entran por cascada de un agregado ya vigilado, a "
                        + "VIGILADAS_POR_CASCADA; si no es ninguna de las tres, a "
                        + "FUERA_DE_LOS_DOS_UNIVERSOS con el motivo escrito -- y el motivo tiene "
                        + "que decir QUE agregado es y QUIEN decide sobre el. Lo que no puede es "
                        + "quedarse sin respuesta: asi quedo atributo_propiedad fuera del gate, y "
                        + "ahi vive casi todo lo gobernado.");

        // Y lo declarado no puede sobrar: una exclusion para una tabla que ya no
        // existe es ruido que hace creer que se penso en algo.
        List<String> declaradasFantasma = java.util.stream.Stream.of(
                        FUERA_DE_LOS_DOS_UNIVERSOS.keySet().stream(),
                        VIGILADAS_POR_CASCADA.keySet().stream())
                .flatMap(s -> s)
                .filter(tabla -> !entidades.contains(tabla))
                .sorted()
                .toList();
        assertEquals(List.of(), declaradasFantasma,
                "estas clasificaciones ya no corresponden a ninguna entidad de los dos "
                        + "paquetes: " + declaradasFantasma + ". Borralas.");
    }

    /**
     * <b>Y cada repositorio declarado se ve de verdad escribiendo.</b>
     *
     * <p>Complemento del anterior y no un duplicado: aquel comprueba que no
     * falte ninguna tabla, este que ninguna de las declaradas haya dejado de
     * reconocerse. Un repositorio renombrado, movido de paquete o con otro
     * metodo de escritura dejaria de aparecer y el gate seguiria verde
     * vigilandolo sobre el papel.
     */
    @Test
    @DisplayName("el gate ve escrituras reales en cada tabla que declara vigilar")
    void elGateVeEscriturasEnCadaTablaVigilada() {
        Map<String, String> vigiladas = new java.util.HashMap<>(TABLAS_DE_LA_PROPIEDAD);
        vigiladas.putAll(TABLAS_DEL_ENCARGO);
        for (Map.Entry<String, String> entrada : vigiladas.entrySet()) {
            boolean alguna = CLASES.stream()
                    .filter(clase -> clase.getPackageName().startsWith("com.controllocal.service"))
                    .flatMap(clase -> clase.getMethods().stream())
                    .anyMatch(m -> escribeEnRepositorio(m, entrada.getKey()));
            assertTrue(alguna,
                    "el gate no encuentra NINGUNA escritura por " + entrada.getKey() + ", que "
                            + "declara vigilar la tabla `" + entrada.getValue() + "`. O el "
                            + "repositorio cambio de nombre o de paquete, o su metodo de "
                            + "escritura ya no se llama como los de ESCRIBEN. En cualquiera de "
                            + "los dos casos esa tabla ha dejado de estar vigilada.");
        }
    }

    @Test
    @DisplayName("RF014: origen tiene un escritor y una confirmación canónica con doble autoridad física")
    void elOrigenFisicoNoAbreOtraPuertaDeEscritura() throws IOException {
        assertEquals(List.of(SERVICIO_DEL_ORIGEN+"#registrarOrigen"),
                nombresDe(escriturasDe(m->escribeEnRepositorio(m,REPOSITORIOS+"UnidadRelacionadaRepository"))));
        assertEquals(List.of("com.controllocal.service.actuacion.ActuacionesServiceImpl#confirmar"),
                llamadoresDe(SERVICIO_DEL_ORIGEN+"#registrarOrigen"),
                "Una ruta o servicio alterno no puede saltar la revisión humana exacta.");
        JavaMethod registrar=metodo(SERVICIO_DEL_ORIGEN,"registrarOrigen");
        for(String nombre:List.of("exigirAgente","iniciar","exigirLectura","autorizarEscritura","incrementar"))
            assertTrue(llama(registrar,SERVICIO_DEL_ORIGEN,nombre),nombre);
        assertTrue(llama(registrar,REPOSITORIOS+"UnidadRelacionadaRepository","bloquearGrafoOrigen"));
        assertTrue(llama(registrar,REPOSITORIOS+"PropiedadRepository","bloquearParaEscritura"));
        assertTrue(llama(registrar,"jakarta.persistence.EntityManager","refresh"));
        for(String nombre:List.of("buscar","registrar"))
            assertTrue(llama(registrar,"com.controllocal.service.soporte.ComandosIdempotentes",nombre));
        assertTrue(llama(metodo(SERVICIO_DEL_ORIGEN,"autorizarEscritura"),AUTORIDAD,"exigirEdicion"));
        assertTrue(llama(metodo(SERVICIO_DEL_ORIGEN,"exigirLectura"),AUTORIDAD,"buscarParaLectura"));
        JavaMethod inicio=metodo(SERVICIO_DEL_ORIGEN,"iniciar");
        assertTrue(llama(inicio,"com.controllocal.service.ContextosService","exigirContexto"));
        assertTrue(llama(inicio,REPOSITORIOS+"CuentaAccesoRepository","bloquearCambioAcceso"));
        assertTrue(llama(inicio,REPOSITORIOS+"UsuarioOrganizacionRepository","cuentasActivasDePersona"));
        JavaMethod incrementar=metodo(SERVICIO_DEL_ORIGEN,"incrementar");
        assertTrue(llama(incrementar,REPOSITORIOS+"PropiedadRepository","incrementarVersionFisicaSi"));
        assertTrue(llama(incrementar,"jakarta.persistence.EntityManager","refresh"));

        // ArchUnit verifica la llamada; el texto precisa AMBOS extremos incluso
        // cuando dos invocaciones al mismo método comparten línea de bytecode.
        String relativa="controllocal-service/src/main/java/com/controllocal/service/impl/UnidadRelacionadaServiceImpl.java";
        Path fuente=List.of(Path.of("..",relativa),Path.of("backend-spring",relativa),Path.of(relativa)).stream()
                .filter(Files::isRegularFile).findFirst().orElseThrow();
        String codigo=Files.readString(fuente,StandardCharsets.UTF_8).replaceAll("\\s+","");
        for(String obligatoria:List.of("autoridad.exigirEdicion(actor,matriz);","autoridad.exigirEdicion(actor,unidad);",
                "exigirLectura(d.idMatriz(),actor);","exigirLectura(d.idUnidad(),actor);",
                "matriz.getVersionFisica()!=d.versionMatrizEsperada()","unidad.getVersionFisica()!=d.versionUnidadEsperada()",
                "incrementar(matriz,d.versionMatrizEsperada(),actor);","incrementar(unidad,d.versionUnidadEsperada(),actor);",
                "exigirLectura(original.idMatriz(),actor);","exigirLectura(original.idUnidad(),actor);",
                "newTreeSet<>(List.of(d.idMatriz(),d.idUnidad()))","ContextoSesion.de(vigente).equals(sesion)"))
            assertTrue(codigo.contains(obligatoria),obligatoria);
        assertEquals(List.of(SERVICIO_DEL_ORIGEN+"#registrarOrigen"),llamadoresDe(SERVICIO_DEL_ORIGEN+"#incrementar"));
        assertFalse(CLASES.get(SERVICIO_DEL_ORIGEN).getMethods().stream().anyMatch(m->
                m.getAccessesFromSelf().stream().anyMatch(a->
                        REPOSITORIOS_DEL_ENCARGO.contains(a.getTargetOwner().getFullName())&&ESCRIBEN.contains(a.getName()))));
    }

    @Test
    @DisplayName("el detector reconoce una escritura real de bytecode sobre la unidad relacionada")
    void elDetectorDeLaPrimeraEscrituraTieneControlPositivo() {
        JavaClass ejemplo = new ClassFileImporter().importClasses(EscrituraDeUnidadDelFixture.class)
                .get(EscrituraDeUnidadDelFixture.class);
        assertTrue(ejemplo.getMethods().stream().filter(m -> m.getName().equals("guardar"))
                .anyMatch(m -> escribeEnRepositorio(m, REPOSITORIOS + "UnidadRelacionadaRepository")));
        var precio=ejemplo.getMethods().stream().filter(m->m.getName().equals("guardarPrecioSinAutoridad")).findFirst().orElseThrow();
        assertTrue(escribeLaSerie(precio));
        assertFalse(cubierto(precio,GUARDAS_DEL_ENCARGO,Map.of(),Set.of(nombre(precio)),new HashSet<>()),
                "Un escritor económico nuevo no hereda la cobertura del Historiador.");
        var copia=ejemplo.getMethods().stream().filter(m->m.getName().equals("guardarCopia")).findFirst().orElseThrow();
        assertTrue(escribeEnRepositorio(copia,REPOSITORIOS+"CopiaComercialFotoRepository"));
        var pieza=ejemplo.getMethods().stream().filter(m->m.getName().equals("registrarArchivoPieza")).findFirst().orElseThrow();
        assertTrue(invocaUnMutador(pieza,PIEZA_COMERCIAL,mutadoresDe(PIEZA_COMERCIAL)),
                "La ausencia de save no puede ocultar un nuevo escritor del artefacto gestionado.");
    }

    private static boolean escribeEnRepositorio(JavaMethod metodo, String repositorio) {
        return metodo.getAccessesFromSelf().stream()
                .anyMatch(a -> repositorio.equals(a.getTargetOwner().getFullName()) && ESCRIBEN.contains(a.getName()));
    }

    static class EscrituraDeUnidadDelFixture {
        void guardar(com.controllocal.persistence.repositorio.UnidadRelacionadaRepository repositorio,
                     com.controllocal.domain.inmueble.UnidadRelacionada unidad) {
            repositorio.save(unidad);
        }
        void guardarPrecioSinAutoridad(com.controllocal.persistence.repositorio.PrecioPropiedadRepository repositorio,
                com.controllocal.domain.inmueble.PrecioPropiedad precio){repositorio.save(precio);}
        void guardarCopia(com.controllocal.persistence.repositorio.CopiaComercialFotoRepository repositorio,
                com.controllocal.domain.inmueble.CopiaComercialFoto copia){repositorio.save(copia);}
        void registrarArchivoPieza(com.controllocal.domain.inmueble.PiezaComercial pieza){
            pieza.registrarArchivo("REVISION_PRIVADA",java.util.UUID.randomUUID(),"TEST",null,null);
        }
    }

    /**
     * <b>La decisión ejecutable debe estar disponible junto al código.</b>
     *
     * <p>La política vigente mantiene privado el paquete documental. Esta prueba
     * lee sus fuentes locales; en un traslado autorizado deben acompañar al
     * código por el mecanismo privado correspondiente. Que un clon no las tenga
     * exige aportar ese paquete, no publicarlo, cambiar .gitignore ni retirar
     * el gate. ContinuidadDocumentalTest comprueba además las fuentes del corte.
     *
     * <h2>Lo que NO dice</h2>
     * No dice que el documento sea correcto ni que este al dia. Dice que
     * <b>existe donde el codigo lo cita</b>. Que la regla sea la que el codigo
     * aplica lo prueban las otras comprobaciones de esta clase y las de
     * integracion.
     */
    @Test
    @DisplayName("la decisión que gobierna esta autoridad está disponible en el paquete privado")
    void laAutoridadQueGobiernaViajaConElCodigo() throws IOException {
        for (String documento : List.of(
                "decision-autoridad-de-edicion-de-la-propiedad.md",
                "decision-brox-intelligence-alcances-y-frontera.md",
                // WP-A. Gobierna codigo vivo: contra que precio se congela una
                // referencia (D-N-1), donde vive una oferta y por que no cuelga
                // solo del encargo (D-N-2), que el importe de la escritura nace
                // FALTANTE (D-N-3) y que el hito C de un alquiler tambien dice de
                // que vuelta salio (D-N-5). Un clon que no lo lleve deja esas
                // cuatro reglas sin autoridad escrita.
                "decision-oferta-y-cierre-de-venta.md")) {
            Path ruta = RAIZ.resolve("docs/ai").resolve(documento);
            assertTrue(Files.isRegularFile(ruta),
                    "Falta " + ruta + ". Aportar el paquete privado autorizado de documentación; "
                            + "no publicar la fuente ni retirar el gate para sustituir su ausencia.");
            assertTrue(Files.readString(ruta, StandardCharsets.UTF_8).length() > 1000,
                    ruta + " esta practicamente vacio: existe el fichero pero no la decision.");
        }
    }

    /**
     * La raiz del repositorio, resuelta subiendo desde el directorio de trabajo
     * del modulo. Igual que hace {@code FronteraKairosTest} para leer el POM.
     */
    private static final Path RAIZ = raizDelRepositorio();

    private static Path raizDelRepositorio() {
        Path actual = Path.of("").toAbsolutePath();
        while (actual != null && !Files.isDirectory(actual.resolve("docs/ai"))) {
            actual = actual.getParent();
        }
        if (actual == null) {
            throw new AssertionError(
                    "no se encontro la raiz del repositorio subiendo desde "
                            + Path.of("").toAbsolutePath() + ": sin ella esta comprobacion no "
                            + "puede mirar nada, y un verde aqui no significaria nada.");
        }
        return actual;
    }

    // ------------------------------------------------------------------
    // Predicados
    // ------------------------------------------------------------------

    /**
     * Escribe la propiedad quien guarda en una de las tablas declaradas en
     * {@link #TABLAS_DE_LA_PROPIEDAD}, o quien
     * transiciona su estado.
     *
     * <p>La segunda mitad no es opcional: {@code desactivar} no llama a
     * {@code propiedades.save} en ningun momento — la transicion muta la entidad
     * gestionada y JPA la vuelca sola. Un gate que solo mirara los repositorios
     * dejaria fuera exactamente la via que retira una propiedad del registro.
     *
     * <p>Se mira {@code aplicarDisponibilidad} y no {@code aplicar} a secas
     * porque {@code Transiciones} sirve tambien a captaciones, contratos,
     * prospecciones y solicitudes: {@code aplicar} generico daba tres falsos
     * positivos medidos —{@code CaptacionServiceImpl#decidir},
     * {@code ProspeccionServiceImpl#marcarCaptado} y
     * {@code ContratoServiceImpl#transicionarContrato}— que transicionan OTRA
     * entidad y solo leen la propiedad de paso. {@code aplicarDisponibilidad},
     * en cambio, es de la propiedad por construccion: no hay otra entidad con
     * disponibilidad comercial.
     */
    private static boolean escribeLaPropiedad(JavaMethod metodo) {
        boolean porRepositorio = metodo.getAccessesFromSelf().stream().anyMatch(a ->
                REPOSITORIOS_DE_LA_PROPIEDAD.contains(a.getTargetOwner().getFullName())
                        && ESCRIBEN.contains(a.getName()));
        boolean porTransicion = metodo.getAccessesFromSelf().stream().anyMatch(a ->
                TRANSICIONES.equals(a.getTargetOwner().getFullName())
                        && "aplicarDisponibilidad".equals(a.getName()));
        return porRepositorio || porTransicion
                || invocaUnMutador(metodo, PROPIEDAD, MUTADORES_DE_LA_PROPIEDAD);
    }

    /**
     * Escribe la serie economica quien guarda un hito de precio — <b>o quien
     * muta el ENCARGO y deja que el {@code flush} lo vuelque</b> (N43).
     */
    private static boolean escribeLaSerie(JavaMethod metodo) {
        boolean porRepositorio = metodo.getAccessesFromSelf().stream().anyMatch(a ->
                REPOSITORIOS_DEL_ENCARGO.contains(a.getTargetOwner().getFullName())
                        && ESCRIBEN.contains(a.getName()));
        return porRepositorio || invocaUnMutador(metodo, ENCARGO, MUTADORES_DEL_ENCARGO)
                || MUTADORES_DE_AGREGADOS_ENCARGO.entrySet().stream()
                    .anyMatch(e->invocaUnMutador(metodo,e.getKey(),e.getValue()));
    }

    /**
     * <b>Escribir por <i>dirty checking</i> es escribir</b> (N43).
     *
     * <p>Se mira el <b>duena del acceso</b> y no el nombre del metodo, igual que
     * en {@link #fijaElAgenteDelEncargo}: {@code setObservaciones} existe en
     * media docena de entidades, y vigilar «cualquier setObservaciones» habria
     * puesto en rojo medio dominio.
     *
     * <p><b>{@code Transiciones.aplicar} queda fuera, y hay que decir por que.</b>
     * Es {@code <E extends EntidadDeOrganizacion & Transicionable> void
     * aplicar(E, ...)}: <b>generico</b>, asi que en el bytecode el parametro
     * esta borrado a {@code EntidadDeOrganizacion} y no se puede distinguir una
     * llamada con {@code Captacion} de una con {@code Prospeccion},
     * {@code ContratoAlquiler} o {@code SolicitudAlquiler}. Tampoco lo alcanza
     * este predicado por dentro: {@code aplicar} muta la entidad llamando a
     * {@code Transicionable#transicionarA}, y el duena del acceso en el bytecode
     * es la <b>interfaz</b>, no {@code Captacion}. Incluirlo sin poder
     * distinguir el tipo pondria en rojo toda transicion de cualquier entidad
     * del sistema —el mismo falso positivo medido que obligo a mirar
     * {@code aplicarDisponibilidad} en vez de {@code aplicar}—, y el estado del
     * encargo ya tiene su propia autoridad: quien lo mueve es el ciclo del
     * broker (`decidir`, `cerrar`), que este gate SI ve por sus otros mutadores
     * y que esta clasificado abajo con su guarda real.
     */
    private static boolean invocaUnMutador(JavaMethod metodo, String entidad,
                                           Set<String> mutadores) {
        return metodo.getAccessesFromSelf().stream().anyMatch(a ->
                entidad.equals(a.getTargetOwner().getFullName())
                        && mutadores.contains(a.getName()));
    }

    /**
     * <b>Cubierto = pregunta, o esta declarado, o solo se llega por alguien que
     * cumple una de las dos.</b>
     *
     * <p>La tercera rama es la que evita convertir el gate en una lista de
     * perdones para metodos privados. {@code escribirTitularidades} no consulta
     * la autoridad y no hace falta que lo haga: su unico llamador es
     * {@code registrar}, que la fija. Exigirsela a cada auxiliar obligaria a
     * declarar media docena de excepciones cuyo motivo real seria "es privado",
     * y una lista de excepciones que nadie lee ya no protege nada.
     *
     * <p>Y no la debilita: lo que caza este gate es la <b>puerta nueva</b>, y
     * una puerta nueva es por definicion alcanzable desde fuera de la clase. Un
     * metodo sin ningun llamador dentro de su clase es publico de hecho, y
     * entonces la tercera rama no aplica y tiene que responder por si mismo.
     */
    private static boolean cubierto(JavaMethod metodo, Set<String> guardas,
                                    Map<String, String> exentos, Set<String> escritores,
                                    Set<String> visitados) {
        // La exencion se consulta en el mapa DEL UNIVERSO que se esta
        // recorriendo. Con un mapa unico, una exencion escrita para la PROPIEDAD
        // eximia el mismo metodo cuando se median los hechos del ENCARGO.
        if (exentos.containsKey(nombre(metodo))) {
            return true;
        }
        if (GUARDAS_DEL_ENCARGO.equals(guardas) && historiadorConAutoridadDelegada(metodo)) return true;
        if (consultaLaAutoridad(metodo, guardas, escritores, new HashSet<>())) {
            return true;
        }
        if (!visitados.add(metodo.getFullName())) {
            // Ciclo entre auxiliares: no se puede concluir que este cubierto
            // por el camino de sus llamadores. El lado seguro es "no".
            return false;
        }
        List<JavaMethod> llamadores = metodo.getOwner().getMethods().stream()
                .filter(otro -> !otro.equals(metodo))
                .filter(otro -> otro.getAccessesFromSelf().stream()
                        .anyMatch(a -> a.getTargetOwner().equals(metodo.getOwner())
                                && a.getName().equals(metodo.getName())))
                .toList();
        return !llamadores.isEmpty()
                && llamadores.stream()
                        .allMatch(otro -> cubierto(otro, guardas, exentos, escritores, visitados));
    }

    /**
     * <b>El ciclo del broker es autoridad, pero solo si esta entera.</b>
     *
     * <p>Vale unicamente en el universo del ENCARGO —{@code guardas} tiene que
     * ser {@link #GUARDAS_DEL_ENCARGO}—, solo para los metodos declarados en
     * {@link #CICLO_DEL_ENCARGO}, y solo si el metodo llama de verdad a
     * <b>las dos</b> guardas. Es lo que hace que quitar
     * {@code exigirBandaComercial} ponga el gate en rojo en vez de quedar
     * tapado por una exencion.
     */

    /**
     * <b>Consulta LA guarda que le toca</b>, aunque sea a traves de un metodo
     * propio.
     *
     * <p>El {@code guardas} no es un detalle: sin el, llamar a cualquier metodo
     * de {@code AutoridadDePropiedad} contaria como haber preguntado, y
     * preguntar por el encargo no responde por la propiedad. El sabotaje que
     * quito {@code exigirEdicion} de {@code editar} paso por ahi.
     *
     * <p>Y no se sigue el rastro por dentro de un <b>LECTOR</b>
     * ({@link #PREGUNTAN_NO_IMPIDEN}). Es la tercera vez que este gate se
     * escapa por el mismo sitio y la primera en que la fuga era <b>local</b>:
     * un metodo de la propia clase que llama a la guarda dentro de un
     * {@code try/catch} para <b>pintar</b> un booleano. Ver el comentario del
     * mapa.
     *
     * <h2>Ni por dentro de OTRO TRABAJO — la cuarta fuga (2026-09-02)</h2>
     * Es la misma leccion en su forma mas dificil de ver, y la encontro el
     * sabotaje (a) de F3-3, anterior a C2. Entonces {@code PropiedadUniversalServiceImpl#editar}
     * escribía el ENCARGO por <b>dos</b> caminos independientes:
     * {@code actualizarEncargo} —las operaciones— y {@code aplicarCondiciones}
     * &rarr; {@code aplicarCondicionesDe} —las condiciones pactadas—, y
     * <b>cada uno resuelve SU encargo y exige la autoridad sobre EL SUYO</b>.
     * Quitar {@code exigirEdicionDelEncargo} de {@code actualizarEncargo}
     * dejaba este gate en <b>VERDE</b>: el metodo ya no preguntaba, pero su
     * unico llamador —{@code editar}— seguia "consultando la autoridad" porque
     * el <b>otro</b> camino la consultaba, y la cobertura transitiva le
     * extendia el certificado.
     *
     * <p><b>La guarda del hermano no protege mi escritura</b>, y aqui no era un
     * tecnicismo: las condiciones se aplican <b>solo si el comando las trae</b>,
     * asi que una peticion sin condiciones editaba el importe de un encargo
     * ajeno con el gate verde. Este defecto es <b>anterior</b> a N43 —
     * {@code actualizarEncargo} ya estaba en el universo del ENCARGO por su
     * escritura de {@code precio_propiedad}—; lo que hizo N43 fue construir el
     * sabotaje que lo enseno.
     *
     * <p><b>La correccion, y por que no es "no recurras nunca".</b> La
     * recursion hacia abajo hace falta y es correcta cuando el auxiliar es una
     * <b>guarda envuelta</b>: {@code PublicacionServiceImpl#exigirEncargoPropio}
     * resuelve el encargo y exige, no escribe nada, y sin recursion sus tres
     * llamadores se quedarian sin cobertura. Lo que hay que distinguir es una
     * guarda de <b>una segunda unidad de trabajo</b>. Asi que la recursion se
     * detiene en todo auxiliar que <b>alcance una escritura de este universo</b>
     * —directamente o a traves de un colaborador, aunque ese colaborador este
     * exento—: si el auxiliar escribe, su guarda responde por lo que escribe
     * <b>el</b>, y prestarsela a quien lo llama es justo el error.
     */
    private static boolean consultaLaAutoridad(JavaMethod metodo, Set<String> guardas,
                                               Set<String> escritores, Set<String> visitados) {
        if (!visitados.add(metodo.getFullName())) {
            return false;
        }
        for (JavaAccess<?> llamada : metodo.getAccessesFromSelf()) {
            JavaClass destino = llamada.getTargetOwner();
            if (esGuardaQueDeniega(llamada,guardas)) return true;
            if (!destino.getFullName().equals(metodo.getOwner().getFullName())) {
                continue;
            }
            for (JavaMethod propio : destino.getMethods()) {
                if (propio.getName().equals(llamada.getName())
                        && !PREGUNTAN_NO_IMPIDEN.containsKey(nombre(propio))
                        && !alcanzaUnaEscritura(propio, escritores, new HashSet<>())
                        && consultaLaAutoridad(propio, guardas, escritores, visitados)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * ¿Este auxiliar termina escribiendo el universo que se esta midiendo?
     *
     * <p>Se cuenta tanto la escritura propia como la delegada: antes de C2,
     * {@code aplicarCondicionesDe} no tocaba ningún repositorio del encargo por sí
     * mismo, sino {@code AtributosDeEncargo#escribir}, que <b>sí</b> es un
     * escritor. C2 retiró esa ruta física y conserva el mismo control sobre el
     * adaptador canónico CondicionesDinamicasEncargo. Mirar sólo la
     * escritura propia habria dejado la fuga abierta exactamente por donde
     * estaba.
     */
    private static boolean alcanzaUnaEscritura(JavaMethod metodo, Set<String> escritores,
                                               Set<String> visitados) {
        if (escritores.contains(nombre(metodo))) {
            return true;
        }
        if (!visitados.add(metodo.getFullName())) {
            return false;
        }
        for (JavaAccess<?> llamada : metodo.getAccessesFromSelf()) {
            JavaClass destino = llamada.getTargetOwner();
            if (escritores.contains(destino.getName() + "#" + llamada.getName())) {
                return true;
            }
            if (!destino.getFullName().equals(metodo.getOwner().getFullName())) {
                continue;
            }
            for (JavaMethod propio : destino.getMethods()) {
                if (propio.getName().equals(llamada.getName())
                        && alcanzaUnaEscritura(propio, escritores, visitados)) {
                    return true;
                }
            }
        }
        return false;
    }
    private static boolean esGuardaQueDeniega(JavaAccess<?> acceso,Set<String> guardas) {
        if(AUTORIDAD.equals(acceso.getTargetOwner().getFullName())&&guardas.contains(acceso.getName())) return true;
        return GUARDAS_DEL_ENCARGO.equals(guardas)&&ACCESO_ENCARGO.equals(acceso.getTargetOwner().getFullName())
                &&GUARDAS_CONTEXTUALES_ENCARGO.contains(acceso.getName());
    }
    private static JavaMethod metodo(String tipo,String nombre) {
        return CLASES.get(tipo).getMethods().stream().filter(m->m.getName().equals(nombre)).findFirst().orElseThrow();
    }
    private static boolean llama(JavaMethod m,String tipo,String nombre) {
        return m.getAccessesFromSelf().stream().anyMatch(a->a.getTargetOwner().getFullName().equals(tipo)&&a.getName().equals(nombre));
    }
    /** Cobertura exclusiva de este delegado; guardar nunca se convierte en una guarda genérica. */
    private static boolean historiadorConAutoridadDelegada(JavaMethod escritor) {
        if(!nombre(escritor).equals(HISTORIADOR_PUBLICACION+"#guardar")||!mandatorio(escritor))return false;
        if(!Set.copyOf(llamadoresDe(nombre(escritor))).equals(Set.of(PUBLICACIONES+"#borrador",PUBLICACIONES+"#ejecutar",HISTORIADOR_PUBLICACION+"#cerrarPorHito")))return false;
        for(String entrada:List.of("borrador","ejecutar")){
            var metodo=metodo(PUBLICACIONES,entrada);
            if(!metodo.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE))return false;
            for(String guarda:List.of("iniciar","escribir","exigirResponsable"))if(!llama(metodo,ACCESO_ENCARGO,guarda))return false;
        }
        for(String guarda:List.of("validarActuacion","exigirPublicabilidad"))if(!llama(metodo(PUBLICACIONES,"ejecutar"),PUBLICACIONES,guarda))return false;
        if(!llama(metodo(PUBLICACIONES,"validarActuacion"),ACCESO_ENCARGO,"exigirResponsable")
                ||!llama(metodo(PUBLICACIONES,"validarActuacion"),PUBLICACIONES,"exigirPublicabilidad"))return false;
        var cierre=metodo(HISTORIADOR_PUBLICACION,"cerrarPorHito");
        if(!mandatorio(cierre)||escribeEnRepositorio(cierre,REPOSITORIOS+"PrecioPropiedadRepository"))return false;
        String venta="com.controllocal.service.impl.CierreVentaServiceImpl",alquiler="com.controllocal.service.impl.ContratoServiceImpl";
        if(!Set.copyOf(llamadoresDe(HISTORIADOR_PUBLICACION+"#cerrarPorHito")).equals(Set.of(venta+"#retirarDelMercado",alquiler+"#cerrarComposicionConfirmada")))return false;
        if(!llamadoresDe(venta+"#retirarDelMercado").equals(List.of(venta+"#confirmarHito"))
                ||!llamadoresDe(alquiler+"#cerrarComposicionConfirmada").equals(List.of(alquiler+"#cerrarResultadoConfirmado"))
                ||!llamadoresDe(alquiler+"#cerrarResultadoConfirmado").equals(List.of(alquiler+"#confirmarHito")))return false;
        for(String tipo:List.of(venta,alquiler))if(!mandatorio(metodo(tipo,"confirmarHito"))||!llama(metodo(tipo,"confirmarHito"),ACCESO_ENCARGO,"exigirDecision"))return false;
        return metodo(venta,"retirarDelMercado").getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE)
                &&metodo(alquiler,"cerrarComposicionConfirmada").getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE)
                &&metodo(alquiler,"cerrarResultadoConfirmado").getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE);
    }
    private static boolean mandatorio(JavaMethod metodo){
        return metodo.isAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                &&metodo.getAnnotationOfType(org.springframework.transaction.annotation.Transactional.class).propagation()==org.springframework.transaction.annotation.Propagation.MANDATORY;
    }
    @Test void elHitoPublicadoConservaSuDelegadoYCadaCaminoDeAutoridad(){
        assertTrue(historiadorConAutoridadDelegada(metodo(HISTORIADOR_PUBLICACION,"guardar")),
                "El delegado necesita censo cerrado, TX padre y autoridad real Pub/C5, no una exención por nombre.");
        assertFalse(GUARDAS_DEL_ENCARGO.contains("guardar"));
        assertFalse(SIN_AUTORIDAD_DEL_ENCARGO.containsKey(HISTORIADOR_PUBLICACION+"#guardar"));
        assertTrue(llama(metodo(HISTORIADOR_PUBLICACION,"guardar"),REPOSITORIOS+"PrecioPropiedadRepository","save"));
        assertTrue(llama(metodo(HISTORIADOR_PUBLICACION,"cerrarPorHito"),HISTORIADOR_PUBLICACION,"guardar"));
        // El comportamiento de cierre sin otro precio se demuestra además por
        // PublicacionServiceImplTest.hitoContractualCierraConHistoriaSinExigirEdicionFisica.
    }
    private static Set<String> efectosFisicosDesde(JavaMethod metodo,Set<String> visitados){
        Set<String> efectos=new java.util.TreeSet<>();
        if(!visitados.add(metodo.getFullName()))return efectos;
        for(JavaAccess<?> acceso:metodo.getAccessesFromSelf()){
            String tipo=acceso.getTargetOwner().getFullName();String nombre=acceso.getName();
            if(PROPIEDAD.equals(tipo)&&MUTADORES_DE_LA_PROPIEDAD.contains(nombre)
                    ||REPOSITORIOS_DE_LA_PROPIEDAD.contains(tipo)&&ESCRIBEN.contains(nombre)
                    ||TRANSICIONES.equals(tipo)&&"aplicarDisponibilidad".equals(nombre))
                efectos.add(tipo+"#"+nombre);
            if(tipo.equals(metodo.getOwner().getFullName()))
                for(var propio:metodo.getOwner().getMethods())if(propio.getName().equals(nombre))
                    efectos.addAll(efectosFisicosDesde(propio,visitados));
        }
        return efectos;
    }
    @Test
    @DisplayName("C2: decidir sólo proyecta disponibilidad comercial tras la autoridad de revisión")
    void laRevisionDelEncargoNoEximeNingunaEdicionFisica(){
        JavaMethod decidir=metodo(SERVICIO_DEL_ENCARGO,"decidir");
        assertTrue(llama(decidir,SERVICIO_DEL_ENCARGO,"comando"));
        assertTrue(llama(decidir,ACCESO_ENCARGO,"exigirDecision"));
        JavaMethod comprobar=metodo(SERVICIO_DEL_ENCARGO,"comprobarDecision");
        assertTrue(comprobar.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE));
        assertTrue(llama(decidir,SERVICIO_DEL_ENCARGO,"comprobarDecision"));
        JavaMethod validar=metodo(SERVICIO_DEL_ENCARGO,"validarDecision");
        assertTrue(llama(validar,ACCESO_ENCARGO,"leer"));
        assertTrue(llama(validar,ACCESO_ENCARGO,"exigirDecision"));
        assertTrue(llama(validar,SERVICIO_DEL_ENCARGO,"comprobarDecision"));
        assertTrue(llama(comprobar,SERVICIO_DEL_ENCARGO,"version"));
        assertTrue(llama(comprobar,SERVICIO_DEL_ENCARGO,"faltantes"));
        assertTrue(llama(comprobar,SERVICIO_DEL_ENCARGO,"verificarEvidencias"));
        assertTrue(llama(decidir,SERVICIO_DEL_ENCARGO,"registrarPrecioAutorizado"));
        assertEquals(Set.of(TRANSICIONES+"#aplicarDisponibilidad"),efectosFisicosDesde(decidir,new HashSet<>()),
                "La excepción comercial no puede cubrir un setter físico, CAS físico o repositorio del inmueble.");
        var fixture=new ClassFileImporter().importClasses(EfectosRevisionFixture.class).get(EfectosRevisionFixture.class);
        var permitido=fixture.getMethods().stream().filter(m->m.getName().equals("proyeccion")).findFirst().orElseThrow();
        var invasivo=fixture.getMethods().stream().filter(m->m.getName().equals("proyeccionConEdicion")).findFirst().orElseThrow();
        assertEquals(Set.of(TRANSICIONES+"#aplicarDisponibilidad"),efectosFisicosDesde(permitido,new HashSet<>()));
        assertTrue(efectosFisicosDesde(invasivo,new HashSet<>()).contains(PROPIEDAD+"#setDescripcion"),
                "Control negativo: también se detecta el setter escondido en un auxiliar propio.");
    }
    static class EfectosRevisionFixture{
        void proyeccion(com.controllocal.service.soporte.Transiciones t,com.controllocal.domain.inmueble.Propiedad p,
                        com.controllocal.service.Actor actor){
            t.aplicarDisponibilidad(p,1L,com.controllocal.domain.comun.EstadosDominio.DisponibilidadComercial.DISPONIBLE,actor,"Fixture");
        }
        void proyeccionConEdicion(com.controllocal.service.soporte.Transiciones t,com.controllocal.domain.inmueble.Propiedad p,
                                 com.controllocal.service.Actor actor){proyeccion(t,p,actor);editarFisico(p);}
        private void editarFisico(com.controllocal.domain.inmueble.Propiedad p){p.setDescripcion("Edición que la proyección no autoriza");}
    }
    @Test
    @DisplayName("C2: el auxiliar de evidencia sigue privado, vincula sólo el archivo y conserva entradas de comando guardadas")
    void elVinculoTecnicoDeEvidenciaNoEsUnaExencionDeNegocio(){
        JavaMethod verificar=metodo(SERVICIO_DEL_ENCARGO,"verificarEvidencias");
        assertTrue(verificar.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE));
        Set<String> mutaciones=new java.util.TreeSet<>();
        verificar.getAccessesFromSelf().forEach(a->{
            var permitidos=MUTADORES_DE_AGREGADOS_ENCARGO.get(a.getTargetOwner().getFullName());
            if(permitidos!=null&&permitidos.contains(a.getName()))mutaciones.add(a.getTargetOwner().getFullName()+"#"+a.getName());
        });
        assertEquals(Set.of(PAQUETE_COMERCIAL+".ArchivoEncargo#vincular"),mutaciones);
        assertFalse(invocaUnMutador(verificar,ENCARGO,MUTADORES_DEL_ENCARGO));
        assertTrue(efectosFisicosDesde(verificar,new HashSet<>()).isEmpty());
        assertFalse(verificar.getAccessesFromSelf().stream().anyMatch(a->
                REPOSITORIOS_DEL_ENCARGO.contains(a.getTargetOwner().getFullName())&&ESCRIBEN.contains(a.getName())));
        JavaMethod validar=metodo(SERVICIO_DEL_ENCARGO,"validarPartes");
        assertTrue(validar.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE));
        assertEquals(List.of(SERVICIO_DEL_ENCARGO+"#comprobarActivacionAdenda",
                SERVICIO_DEL_ENCARGO+"#faltantes",SERVICIO_DEL_ENCARGO+"#presentar"),
                llamadoresDe(SERVICIO_DEL_ENCARGO+"#validarPartes"));
        assertTrue(llama(validar,SERVICIO_DEL_ENCARGO,"verificarEvidencias"));
        assertFalse(invocaUnMutador(validar,ENCARGO,MUTADORES_DEL_ENCARGO));
        assertTrue(efectosFisicosDesde(validar,new HashSet<>()).isEmpty());
        assertFalse(validar.getAccessesFromSelf().stream().anyMatch(a->
                MUTADORES_DE_AGREGADOS_ENCARGO.getOrDefault(a.getTargetOwner().getFullName(),Set.of()).contains(a.getName())
                ||(REPOSITORIOS_DEL_ENCARGO.contains(a.getTargetOwner().getFullName())&&ESCRIBEN.contains(a.getName()))));
        for(String entrada:List.of("presentar","corregirYReenviar","solicitarFin"))
            assertTrue(CLASES.get(SERVICIO_DEL_ENCARGO).getMethods().stream().filter(m->m.getName().equals(entrada))
                    .anyMatch(m->llama(m,SERVICIO_DEL_ENCARGO,"comando")&&llama(m,ACCESO_ENCARGO,"exigirResponsable")),entrada);
        for(String entrada:List.of("decidir","decidirSolicitudFin","finalizar"))
            assertTrue(llama(metodo(SERVICIO_DEL_ENCARGO,entrada),SERVICIO_DEL_ENCARGO,"comando")
                    &&llama(metodo(SERVICIO_DEL_ENCARGO,entrada),ACCESO_ENCARGO,"exigirDecision"),entrada);
        assertTrue(metodo(SERVICIO_DEL_ENCARGO,"activarAdenda").getModifiers()
                .contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE));
        assertEquals(List.of(SERVICIO_DEL_ENCARGO+"#decidir"),
                llamadoresDe(SERVICIO_DEL_ENCARGO+"#activarAdenda"));
        assertTrue(llama(metodo(SERVICIO_DEL_ENCARGO,"decidir"),SERVICIO_DEL_ENCARGO,"comprobarDecision"));
    }
    @Test
    @DisplayName("C5: el efecto físico del alquiler sólo procede del hito competente y su transacción")
    void laComposicionDelAlquilerNoEsUnaEdicionFisicaLibre() {
        String tipo="com.controllocal.service.impl.ContratoServiceImpl";
        var entrada=metodo(tipo,"confirmarHito");
        assertTrue(llama(entrada,ACCESO_ENCARGO,"exigirDecision"));
        assertEquals(org.springframework.transaction.annotation.Propagation.MANDATORY,
                entrada.getAnnotationOfType(org.springframework.transaction.annotation.Transactional.class).propagation());
        for(String nombre:List.of("cerrarResultadoConfirmado","cerrarComposicionConfirmada"))
            assertTrue(metodo(tipo,nombre).getModifiers()
                    .contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE),nombre);
        assertTrue(llama(metodo(tipo,"cerrarComposicionConfirmada"),
                "com.controllocal.service.soporte.Transiciones","aplicarDisponibilidad"));
    }
    @Test
    @DisplayName("C2: el físico conserva su CAS específico, lectura de replay y recibo sin mutador comercial")
    void laEdicionFisicaNoComparteElEscritorNiLaVersionDelEncargo() throws ReflectiveOperationException {
        String tipo="com.controllocal.service.impl.PropiedadUniversalServiceImpl";
        JavaMethod editar=metodo(tipo,"editar");
        assertTrue(llama(editar,AUTORIDAD,"exigirEdicion"));
        assertTrue(llama(editar,tipo,"consultarPropiedad"),"También el replay exige lectura física actual.");
        assertTrue(llama(editar,REPOSITORIOS+"PropiedadRepository","incrementarVersionFisicaSi"));
        assertTrue(llama(editar,"jakarta.persistence.EntityManager","refresh"));
        for(String m:List.of("buscar","registrar"))assertTrue(llama(editar,"com.controllocal.service.soporte.ComandosIdempotentes",m));
        assertEquals(List.of(tipo+"#editar",SERVICIO_DEL_ORIGEN+"#incrementar"),
                llamadoresDe(REPOSITORIOS+"PropiedadRepository#incrementarVersionFisicaSi"),
                "Sólo edición física y origen RF014 incrementan con el CAS específico; ambos conservan su autoridad.");
        assertFalse(CLASES.get(tipo).getMethods().stream().anyMatch(m->Set.of(
                "aplicarCondiciones","aplicarCondicionesDe","huellaDeCondiciones").contains(m.getName())));
        assertTrue(llamadoresDe("com.controllocal.service.soporte.AtributosDeEncargo#escribir").stream().noneMatch(n->n.startsWith(tipo+"#")));
        assertTrue(llamadoresDe("com.controllocal.service.soporte.AtributosDeEncargo#retirar").stream().noneMatch(n->n.startsWith(tipo+"#")));
        var contador=com.controllocal.domain.inmueble.Propiedad.class.getDeclaredField("versionFisica")
                .getAnnotation(jakarta.persistence.Column.class);
        assertFalse(contador.updatable(),"Un flush comercial no puede sobrescribir el contador nativo.");
        var cas=com.controllocal.persistence.repositorio.PropiedadRepository.class
                .getMethod("incrementarVersionFisicaSi",long.class,long.class,long.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class);
        String sql=cas.value().replaceAll("\\s+","");
        assertTrue(cas.nativeQuery());
        assertTrue(sql.contains("version_fisica=version_fisica+1"));
        for(String precondicion:List.of("organizacion_id=:organizacion","id_propiedad=:id","version_fisica=:observada"))
            assertTrue(sql.contains(precondicion),precondicion);
    }
    @Test
    @DisplayName("C2: las condiciones dinámicas tienen un solo adaptador y entradas canónicas de apertura y edición")
    void lasCondicionesDinamicasNoAbrenUnaSegundaRutaDeNegocio() {
        String tipo="com.controllocal.service.encargo.CondicionesDinamicasEncargo";
        JavaMethod aplicar=metodo(tipo,"aplicar");
        for(String m:List.of("escribir","retirar"))assertTrue(llama(aplicar,"com.controllocal.service.soporte.AtributosDeEncargo",m));
        assertTrue(llama(metodo(SERVICIO_DEL_ENCARGO,"abrir"),tipo,"aplicar"));
        assertTrue(llama(metodo(SERVICIO_DEL_ENCARGO,"editar"),tipo,"aplicar"));
        assertTrue(llama(metodo(SERVICIO_DEL_ENCARGO,"editar"),ACCESO_ENCARGO,"modificarContenido"));
        assertTrue(llama(metodo(SERVICIO_DEL_ENCARGO,"editar"),SERVICIO_DEL_ENCARGO,"comando"));
        assertFalse(llama(metodo(tipo,"propuesta"),"com.controllocal.service.soporte.AtributosDeEncargo","escribir"));
        assertTrue(llama(metodo(tipo,"propuesta"),"com.controllocal.service.soporte.AtributosDeEncargo","interpretarPropuesta"));
    }
    @Test
    @DisplayName("C2: observar el mercado exige lectura actual y conserva una serie separada e idempotente")
    void laObservacionDeMercadoNoEsUnaEscrituraFisicaNiComercial() {
        String tipo="com.controllocal.service.impl.ObservacionMercadoServiceImpl";
        JavaMethod registrar=metodo(tipo,"registrar");
        assertTrue(llama(registrar,"com.controllocal.service.Actor","esAgente"));
        assertTrue(llama(registrar,tipo,"exigirLectura"));
        assertTrue(llama(metodo(tipo,"listarDe"),tipo,"exigirLectura"));
        assertTrue(llama(metodo(tipo,"exigirLectura"),AUTORIDAD,"puedeLeer"));
        assertTrue(llama(registrar,REPOSITORIOS+"ObservacionMercadoRepository","saveAndFlush"));
        assertTrue(llama(registrar,REPOSITORIOS+"ComandoIdempotenteRepository","bloquearIdentidad"));
        for(String m:List.of("buscar","registrar"))assertTrue(llama(registrar,"com.controllocal.service.soporte.ComandosIdempotentes",m));
        assertFalse(CLASES.get(tipo).getMethods().stream().anyMatch(m->escribeLaPropiedad(m)||escribeLaSerie(m)),
                "Una observación no autoriza precio, disponibilidad, pacto ni una edición física.");
    }
    @Test
    @DisplayName("C2: seleccionar fotos sólo observa galería autorizada y devuelve un valor privado sin persistir ni abrir originales")
    void seleccionarFotosNoEsReordenarPublicarNiDescargar() throws ReflectiveOperationException {
        String tipo="com.controllocal.service.documentos.SeleccionFotosContextualesService";
        JavaClass servicio=CLASES.get(tipo);
        assertTrue(llama(metodo(tipo,"observarAutorizado"),"com.controllocal.service.FotoPropiedadService","listar"));
        assertTrue(llama(metodo(tipo,"seleccionar"),tipo,"observarAutorizado"));
        assertFalse(llamaFotoFueraDeLecturas(servicio,Set.of("listar","originalPermitidoParaLectura")),"Seleccionar no muta la galería.");
        assertFalse(servicio.getDirectDependenciesFromSelf().stream().anyMatch(d->{
            String paquete=d.getTargetClass().getPackageName();
            return paquete.startsWith("com.controllocal.persistence")||paquete.startsWith("com.controllocal.web.almacen")
                    ||paquete.startsWith("java.nio.file")||paquete.startsWith("java.net");
        }),"La selección no persiste ni abre bytes de proveedor, disco o red.");
        var clase=com.controllocal.service.documentos.SeleccionFotosContextualesService.class;
        for(var m:List.of(clase.getMethod("observar",long.class,com.controllocal.service.Actor.class),
                clase.getMethod("seleccionar",long.class,
                        com.controllocal.service.documentos.SeleccionFotosContextualesService.Solicitud.class,
                        com.controllocal.service.Actor.class)))
            assertTrue(m.getAnnotation(org.springframework.transaction.annotation.Transactional.class).readOnly());
        String foto="com.controllocal.service.impl.FotoPropiedadServiceImpl",predicado="originalPermitidoParaLectura";
        assertTrue(llama(metodo(tipo,predicado),"com.controllocal.service.FotoPropiedadService",predicado));
        assertTrue(llama(metodo(tipo,"exigirLectura"),"com.controllocal.service.Actor","esAgente"));
        assertTrue(llama(metodo(tipo,"exigirLectura"),"com.controllocal.service.Actor","esBroker"));
        var lectura=metodo(foto,predicado);
        assertTrue(lectura.getAnnotationOfType(org.springframework.transaction.annotation.Transactional.class).readOnly());
        assertTrue(llama(lectura,AUTORIDAD,"buscarParaLectura"));
        assertTrue(llama(lectura,REPOSITORIOS+"FotoPropiedadRepository","deLaPropiedad"));
        assertFalse(alcanzaUnaEscritura(lectura,Set.copyOf(nombresDe(escriturasDe(AutoridadDeLaPropiedadTest::escribeLaPropiedad))),new HashSet<>()));
        assertEquals(List.of(tipo+"#"+predicado),llamadoresDe("com.controllocal.service.FotoPropiedadService#"+predicado));
        assertEquals(List.of("com.controllocal.service.materialia.FuentesMaterialIaCore#exigirRecursoDeLectura"),llamadoresDe(tipo+"#"+predicado));
    }
    private static boolean llamaFotoFueraDeLecturas(JavaClass tipo,Set<String> lecturas){
        return tipo.getMethods().stream().flatMap(m->m.getAccessesFromSelf().stream())
                .anyMatch(a->a.getTargetOwner().getFullName().equals("com.controllocal.service.FotoPropiedadService")&&!lecturas.contains(a.getName()));
    }
    /** Todos los paquetes Main, también Web: otro escritor no queda fuera por cambiar de capa. */
    private static List<String> escritoresDeArtefacto(String repositorio,String entidad){
        var mutadores=mutadoresDe(entidad);
        return CLASES.stream().flatMap(c->c.getMethods().stream())
                .filter(m->escribeEnRepositorio(m,repositorio)||invocaUnMutador(m,entidad,mutadores))
                .map(AutoridadDeLaPropiedadTest::nombre).distinct().sorted().toList();
    }
    @Test void copiasComercialesTienenUnEscritorConLecturaActual(){
        assertTrue(FUERA_DE_LOS_DOS_UNIVERSOS.containsKey("copia_comercial_foto"));
        assertEquals(List.of(FOTOS_COMERCIALES+"#preparar"),escritoresDeArtefacto(REPOSITORIOS+"CopiaComercialFotoRepository",COPIA_COMERCIAL));
        assertTrue(mutadoresDe(COPIA_COMERCIAL).contains("de"));
        var preparar=metodo(FOTOS_COMERCIALES,"preparar");
        assertTrue(escribeEnRepositorio(preparar,REPOSITORIOS+"CopiaComercialFotoRepository"));
        assertTrue(llama(preparar,"com.controllocal.service.actividad.ContextosDeActividad","exigirAgente"));
        assertTrue(llama(preparar,FOTOS_COMERCIALES,"exigirFoto"));
        var exigirFoto=metodo(FOTOS_COMERCIALES,"exigirFoto");
        assertTrue(llama(exigirFoto,"com.controllocal.service.actividad.ContextosDeActividad","exigirOperador"));
        assertTrue(llama(exigirFoto,"com.controllocal.service.FotoPropiedadService","listar"));
        // La galería aplica el mismo permiso SQL fresco, sin una prelectura EXISTS duplicada.
        var listar=metodo("com.controllocal.service.impl.FotoPropiedadServiceImpl","listar");
        assertTrue(llama(listar,AUTORIDAD,"buscarParaLectura"));
        assertTrue(llama(metodo(AUTORIDAD,"buscarParaLectura"),REPOSITORIOS+"PropiedadRepository","buscarParaLectura"));
        assertFalse(llamaFotoFueraDeLecturas(CLASES.get(FOTOS_COMERCIALES),Set.of("listar")));
        assertFalse(CLASES.get(FOTOS_COMERCIALES).getMethods().stream().anyMatch(m->escribeLaPropiedad(m)||escribeLaSerie(m)),
                "Una copia saneada no edita el original ni el pacto.");
    }
    @Test void piezasTienenEscritoresYEntradasCerrados(){
        assertTrue(FUERA_DE_LOS_DOS_UNIVERSOS.containsKey("pieza_comercial"));
        assertEquals(List.of(PIEZAS_COMERCIALES+"#generar",PIEZAS_COMERCIALES+"#preparar",PIEZAS_COMERCIALES+"#regenerar"),
                escritoresDeArtefacto(REPOSITORIOS+"PiezaComercialRepository",PIEZA_COMERCIAL));
        assertTrue(mutadoresDe(PIEZA_COMERCIAL).containsAll(Set.of("preparar","registrarArchivo")));
        assertEquals(List.of(PIEZAS_COMERCIALES+"#obtenerParaDifusion",PIEZAS_COMERCIALES+"#previsualizar"),llamadoresDe(PIEZAS_COMERCIALES+"#generar"));
        assertEquals(List.of(PIEZAS_COMERCIALES+"#obtenerParaDifusion",PIEZAS_COMERCIALES+"#previsualizar"),llamadoresDe(PIEZAS_COMERCIALES+"#paraGenerar"));
        // C1/C6: consulta protegida del Encargo; DIF conserva el camino que sí protege emisión.
        for(String entrada:List.of("preparar","regenerar","paraGenerar"))
            for(String guarda:List.of("iniciarConLecturaProtegida","leerParaActuar","exigirResponsable"))
                assertTrue(llama(metodo(PIEZAS_COMERCIALES,entrada),ACCESO_ENCARGO,guarda),entrada+"/"+guarda);
        for(String guarda:List.of("iniciar","escribir"))
            assertTrue(llama(metodo(PIEZAS_COMERCIALES,"paraGenerar"),ACCESO_ENCARGO,guarda),"DIF/"+guarda);
        for(String entrada:List.of("preparar","regenerar","previsualizar","obtenerParaDifusion"))assertTrue(llama(metodo(PIEZAS_COMERCIALES,entrada),PIEZAS_COMERCIALES,"iniciar"),entrada);
        assertTrue(llama(metodo(PIEZAS_COMERCIALES,"iniciar"),"com.controllocal.service.actividad.ContextosDeActividad","iniciar"));
        assertTrue(llama(metodo(PIEZAS_COMERCIALES,"iniciar"),"com.controllocal.service.actividad.ContextosDeActividad","iniciarConLecturaProtegida"));
        assertTrue(mandatorio(metodo(PIEZAS_COMERCIALES,"obtenerParaDifusion")));
        var resolver=CLASES.get(PIEZAS_COMERCIALES).getMethods().stream()
                .filter(m->m.getName().equals("contenido")&&m.getRawParameterTypes().size()==3).findFirst().orElseThrow();
        assertTrue(llama(resolver,"com.controllocal.service.materialia.ResultadosMaterialIa","bloquearSeleccionesParaLectura"));
        assertTrue(llama(resolver,FOTOS_COMERCIALES,"prepararCopias"));
        assertEquals(List.of(PIEZAS_COMERCIALES+"#contenido"),llamadoresDe(FOTOS_COMERCIALES+"#prepararCopias"));
        assertEquals(List.of(FOTOS_COMERCIALES+"#prepararCopias"),llamadoresDe(FOTOS_COMERCIALES+"#preparar"));
        var lote=metodo(FOTOS_COMERCIALES,"prepararCopias");
        assertTrue(mandatorio(lote));
        assertTrue(llama(lote,REPOSITORIOS+"ComandoIdempotenteRepository","bloquearIdentidad"));
        assertTrue(llama(lote,REPOSITORIOS+"ComandoIdempotenteRepository","bloquearLecturaIdentidad"));
        assertTrue(llama(lote,REPOSITORIOS+"CopiaComercialFotoRepository","originalesConCopia"));
        assertTrue(llama(metodo(PIEZAS_COMERCIALES,"generar"),PIEZAS_COMERCIALES,"bloquearSeleccionesNuevas"));
        assertTrue(llama(metodo(PIEZAS_COMERCIALES,"generar"),"com.controllocal.service.PublicacionService","exigirPublicabilidadDePieza"));
        assertFalse(CLASES.get(PIEZAS_COMERCIALES).getMethods().stream().anyMatch(m->escribeLaPropiedad(m)||escribeLaSerie(m)),
                "Crear una pieza no modifica el inmueble ni sus condiciones pactadas.");
    }
    @Test
    @DisplayName("C2: el comando único revalida SID, alcance y efecto antes de registrar el resultado")
    void laFronteraDeComandosC2ConservaSusGuardas() {
        JavaMethod comando=metodo(SERVICIO_DEL_ENCARGO,"comando");
        for(String nombre:List.of("iniciar","escribir")) assertTrue(llama(comando,ACCESO_ENCARGO,nombre),nombre);
        assertTrue(llama(comando,"com.controllocal.service.soporte.ComandosIdempotentes","buscar"));
        assertTrue(llama(comando,"com.controllocal.service.soporte.ComandosIdempotentes","registrar"));
        assertTrue(llama(comando,"jakarta.persistence.EntityManager","flush"));
        assertTrue(llama(comando,"java.util.function.Consumer","accept"),"La autoridad específica debe ejecutarse, no sólo recibirse.");
        assertTrue(llama(comando,"java.util.function.Function","apply"));
        for(String nombre:List.of("declararComposicion","prepararDocumento","regenerarDocumento","cargarArchivo","adjuntarOReutilizar",
                "presentar","corregirYReenviar","decidir","editar","solicitarFin","cancelarSolicitudFin","decidirSolicitudFin","finalizar")) {
            assertTrue(CLASES.get(SERVICIO_DEL_ENCARGO).getMethods().stream().filter(m->m.getName().equals(nombre))
                    .anyMatch(m->llama(m,SERVICIO_DEL_ENCARGO,"comando")),nombre+" no puede abrir otra vía de mutación.");
        }
        JavaMethod modificar=metodo(ACCESO_ENCARGO,"modificarContenido");
        assertTrue(llama(modificar,ACCESO_ENCARGO,"exigirEditor"));
        assertTrue(llama(modificar,ACCESO_ENCARGO,"contexto"));
        assertTrue(llama(modificar,ENCARGO,"getVersion"));
        assertTrue(llama(modificar,"java.util.function.BooleanSupplier","getAsBoolean"));
        assertTrue(CLASES.get(ACCESO_ENCARGO).getMethods().stream().filter(m->m.getName().equals("iniciar"))
                .anyMatch(m->llama(m,"com.controllocal.service.ContextosService","exigirContexto")
                    &&llama(m,REPOSITORIOS+"CuentaAccesoRepository","bloquearCambioAcceso")
                    &&llama(m,"com.controllocal.service.soporte.Alcances","exigirOperador")));
    }
    @Test
    @DisplayName("C2: la única reasignación conserva contexto, CAS y trazabilidad")
    void laReasignacionC2NoEsUnaExencionSinGuardas() {
        JavaMethod m=metodo("com.controllocal.service.impl.CaptacionServiceImpl","reasignar");
        for(String nombre:List.of("iniciarReasignacion","escribirParaReasignacion")) assertTrue(llama(m,ACCESO_ENCARGO,nombre),nombre);
        assertTrue(llama(m,"com.controllocal.service.encargo.ReglasDeResponsableEncargo","exigirCambioOperativo"));
        assertTrue(llama(m,"com.controllocal.service.soporte.ElegibilidadDeResponsable","exigirElegible"));
        assertTrue(llama(m,REPOSITORIO_DEL_ENCARGO,CAS_DEL_AGENTE));
        assertTrue(llama(m,REPOSITORIOS+"ReasignacionCaptacionRepository","saveAndFlush"));
        assertTrue(llama(m,"com.controllocal.service.soporte.ComandosIdempotentes","buscar"));
        assertTrue(llama(m,"com.controllocal.service.soporte.ComandosIdempotentes","registrar"));
    }
    @Test
    @DisplayName("C2: preguntar capacidad no puede acreditar una guarda que deniega")
    void elDetectorC2DistingueGuardaDePregunta() {
        var ejemplo=new ClassFileImporter().importClasses(GuardasC2DelFixture.class).get(GuardasC2DelFixture.class);
        assertTrue(ejemplo.getMethods().stream().filter(m->m.getName().equals("protegido"))
                .flatMap(m->m.getAccessesFromSelf().stream()).anyMatch(a->esGuardaQueDeniega(a,GUARDAS_DEL_ENCARGO)));
        assertFalse(ejemplo.getMethods().stream().filter(m->m.getName().equals("soloPregunta"))
                .flatMap(m->m.getAccessesFromSelf().stream()).anyMatch(a->esGuardaQueDeniega(a,GUARDAS_DEL_ENCARGO)));
        assertFalse(ejemplo.getMethods().stream().filter(m->m.getName().equals("sinGuarda"))
                .flatMap(m->m.getAccessesFromSelf().stream()).anyMatch(a->esGuardaQueDeniega(a,GUARDAS_DEL_ENCARGO)));
    }
    static class GuardasC2DelFixture {
        void protegido(com.controllocal.service.encargo.AccesoEncargo acceso,com.controllocal.domain.comercial.Captacion c,
                com.controllocal.service.Actor actor){acceso.exigirEditor(c,actor);}
        boolean soloPregunta(com.controllocal.service.encargo.AccesoEncargo acceso,com.controllocal.domain.comercial.Captacion c,
                com.controllocal.service.Actor actor){return acceso.puedeDecidir(c,actor);}
        void sinGuarda(com.controllocal.domain.comercial.Captacion c){c.setObservaciones("dato de prueba");}
    }

    @Test
    @DisplayName("RF052: cargar y vincular conservan destino, versión y reautorización del original")
    void lasCargasYVinculosNoConfundenOriginalConConformidad() {
        for(String nombre:List.of("cargarArchivo","adjuntarOReutilizar")) {
            var m=metodo(SERVICIO_DEL_ENCARGO,nombre);
            assertTrue(llama(m,SERVICIO_DEL_ENCARGO,"comando"));
            assertTrue(llama(m,SERVICIO_DEL_ENCARGO,"autorizarDestino"));
            assertTrue(llama(m,SERVICIO_DEL_ENCARGO,"validarVersionDestino"));
        }
        var vinculo=metodo(SERVICIO_DEL_ENCARGO,"adjuntarOReutilizar");
        String a04="com.controllocal.service.archivo.AccesoArchivoService";
        for(String nombre:List.of("resolver","verificarContenido","revalidar")) assertTrue(llama(vinculo,a04,nombre),nombre);
        assertTrue(llama(metodo(SERVICIO_DEL_ENCARGO,"cargarArchivo"),REPOSITORIOS+"CargaArchivoContextualRepository","save"));
        assertTrue(llama(vinculo,REPOSITORIOS+"ArchivoEncargoRepository","save"));
        assertFalse(CLASES.get(SERVICIO_DEL_ENCARGO).getMethods().stream().anyMatch(m->m.getName().equals("adjuntarEvidencia")),
                "La antigua entrada que recibía bytes y vinculaba en un paso se retira tras migrar consumidores.");
    }

}
