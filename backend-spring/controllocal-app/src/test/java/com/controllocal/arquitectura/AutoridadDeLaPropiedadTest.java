package com.controllocal.arquitectura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.controllocal");

    private static final String AUTORIDAD = "com.controllocal.service.soporte.AutoridadDePropiedad";
    private static final String PROPIEDAD = "com.controllocal.domain.inmueble.Propiedad";
    private static final String TRANSICIONES = "com.controllocal.service.soporte.Transiciones";

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
    private static final Map<String, String> PUEDEN_FIJAR_EL_AGENTE = Map.of(
            "com.controllocal.service.impl.CaptacionServiceImpl#registrar",
            "ALTA sobre una propiedad que ya existe. Fija el agente ANTES del primer save, o "
                    + "sea en el INSERT, y lo toma de actor.idRolOperativo() -- no de un id del "
                    + "cuerpo",

            "com.controllocal.service.impl.ProspeccionServiceImpl#captar",
            "ALTA: convierte una prospeccion en encargo. El agente es el de la prospeccion, que "
                    + "ya tiene su propia autoridad (cargarEnProceso exige que sea del actor), y "
                    + "se fija antes del primer save",

            "com.controllocal.service.impl.PropiedadUniversalServiceImpl#abrirEncargo",
            "ALTA: abre el encargo al registrar la propiedad. Mismo INSERT, mismo momento, y el "
                    + "agente es quien registra",

            PUERTA_CANONICA_DEL_ENCARGO,
            "LA PUERTA CANONICA. Es el unico que lo cambia sobre un encargo que ya existe, y lo "
                    + "hace entero: banda y alcance (D-S0-17 fila 6), elegibilidad del destino "
                    + "(D-P0-7), estado observado mas compare-and-set (D-P0-9) y fila en "
                    + "reasignacion_captacion, todo en la misma transaccion (D-P0-10)");

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
            REPOSITORIOS + "ValorMultipleAtributoRepository", "atributo_propiedad_opcion");

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
    private static final Map<String, String> TABLAS_DEL_ENCARGO = Map.of(
            REPOSITORIOS + "PrecioPropiedadRepository", "precio_propiedad",
            REPOSITORIOS + "AtributoEncargoRepository", "atributo_encargo",
            REPOSITORIOS + "ValorMultipleEncargoRepository", "atributo_encargo_opcion",
            REPOSITORIOS + "CaptacionRepository", "captacion");

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
            "asignacion_responsable_propiedad",
            "es el rastro de QUIEN puede escribir, no un hecho escrito. Tiene su propio "
                    + "gate -- unSoloEscritorDelRastroDeTraspasos -- que es mas estricto: "
                    + "un unico escritor, no una guarda",

            "catalogo_atributo",
            "es el vocabulario de la organizacion, no un dato de ningun inmueble. Lo "
                    + "gobierna el tenant; una propiedad no lo escribe nunca",

            "distrito",
            "es geografia compartida, anterior a cualquier propiedad y comun a todas. No "
                    + "pertenece a ninguna",

            "observacion_mercado",
            "es lo que se VIO del mercado (V76), y BROX no lo autorizo, ni lo publico, ni "
                    + "lo negocio. Es append-only y a proposito NO escribe la propiedad: "
                    + "exigir aqui la autoridad de edicion impediria observar un inmueble "
                    + "ajeno, que es justo para lo que existe",

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
            "es el dinero de un CONTRATO firmado, no del encargo: nace al registrar el "
                    + "contrato y la gobierna ComisionServiceImpl con la frontera de tenant "
                    + "(actor.idOrganizacion() en todas sus consultas) y la banda del "
                    + "controlador. No toca importe, exclusividad ni vigencia de ningun encargo",

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

            "interaccion_comercial",
            "es lo que se HIZO -llamada, mensaje, reunion-, no lo que la propiedad es ni lo "
                    + "que se pacto. InteraccionServiceImpl decide con cargarConAcceso y el "
                    + "alcance de supervision",

            "visita",
            "es una visita al inmueble: actividad comercial fechada, no un hecho suyo. "
                    + "VisitaServiceImpl decide con cargarConAcceso y el alcance",

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
                    + "autoridad");

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
    private static final String SERVICIO_DEL_ENCARGO =
            "com.controllocal.service.impl.CaptacionServiceImpl";
    private static final Set<String> GUARDAS_DEL_CICLO_DEL_ENCARGO =
            Set.of("cargarConAcceso", "exigirBandaComercial");

    private static final Map<String, String> CICLO_DEL_ENCARGO = mapa(
            SERVICIO_DEL_ENCARGO + "#decidir",
            "aprobar, observar o rechazar un encargo: escribe la revision -el broker y su "
                    + "observacion- y mueve el estado. Es la decision del BROKER sobre el "
                    + "encargo de OTRO, que es justamente lo que exigirEdicionDelEncargo "
                    + "prohibiria",

            SERVICIO_DEL_ENCARGO + "#cerrar",
            "cierra el encargo por decision del BROKER: escribe fecha, motivo y detalle de "
                    + "cierre y transiciona a CERRADA. No toca importe, exclusividad ni "
                    + "vigencia. Aparecio con N43: muta la Captacion gestionada y deja que el "
                    + "flush la vuelque, asi que el predicado por repositorio no la veia");

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

            SERVICIO_DEL_ENCARGO + "#cerrarPorContrato",
            Set.of());

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
            "com.controllocal.service.impl.CaptacionServiceImpl#autoridadDelEncargo",
            "envuelve exigirEdicionDelEncargo en un try/catch para responder un booleano de la "
                    + "ficha (D-P0-12). No impide nada: si la autoridad dice que no, devuelve "
                    + "false y la escritura sigue su camino. Contarlo como guarda dejaba pasar "
                    + "el sabotaje que quita la guarda de PUT /captaciones/{id}",

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
                    + "la ficha");

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
            "deleteByIdPropiedadAndClave", "deleteByIdCaptacionAndClave", "borrarDe");

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

            "com.controllocal.service.impl.ProspeccionServiceImpl#captar",
            "es el ciclo del ENCARGO, no la edicion de la ficha. Ya tiene su autoridad "
                    + "-cargarEnProceso exige que la prospeccion sea del actor- y las tres "
                    + "columnas que toca son la proyeccion del importe del encargo y su "
                    + "entrada al mercado. Exigir aqui la de la propiedad impediria captar "
                    + "una propiedad FALTANTE, y FALTANTE no bloquea el encargo",

            "com.controllocal.service.impl.ContratoServiceImpl#cerrarLocal",
            "es el ciclo del CONTRATO, y lo ejecuta el BROKER, que por P0-1 nunca es "
                    + "responsable de una propiedad: exigir la autoridad de edicion aqui "
                    + "significaria que ningun contrato se puede cerrar jamas",

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
    private static final Map<String, String> SIN_AUTORIDAD_DEL_ENCARGO = mapa(
            "com.controllocal.service.impl.ContratoServiceImpl#cerrarLocal",
            "cierra el contrato y con el la serie: escribe el hito 'C' del encargo que se "
                    + "firmo. Lo ejecuta el BROKER, que nunca es agente de un encargo, asi "
                    + "que exigirEdicionDelEncargo aqui significaria que ningun contrato se "
                    + "puede cerrar. Es la MISMA exencion que en el universo de la "
                    + "propiedad, y esta escrita dos veces a proposito: alli el motivo es la "
                    + "disponibilidad del inmueble, aqui es el hito economico. Una sola "
                    + "entrada para las dos ocultaba que son dos decisiones",

            "com.controllocal.service.soporte.AtributosDeEncargo#escribir",
            "no puede preguntar: recibe Comercializacion -un record con idCaptacion, tipo y "
                    + "operacion-, no la entidad Captacion, y exigirEdicionDelEncargo "
                    + "necesita la fila para saber de quien es. Sus DOS unicos llamadores "
                    + "estan medidos y los dos responden: actualizarEncargo llama a "
                    + "exigirEdicionDelEncargo antes, y el alta del encargo lo esta creando "
                    + "en ese mismo instante. Deuda declarada: darle la Captacion para que "
                    + "pueda exigirla por si mismo, como ya hace AtributosGobernados",

            "com.controllocal.service.soporte.AtributosDeEncargo#retirar",
            "misma razon y mismos llamadores que escribir",

            "com.controllocal.service.impl.PropiedadUniversalServiceImpl#abrirEncargo",
            "ABRE el encargo: escribe su primer hito 'U' y las condiciones que se pactaron "
                    + "al abrirlo. No hay agente anterior a quien respetar porque el encargo "
                    + "nace aqui, y su agente es el actor. Este metodo aparecio al separar "
                    + "los dos mapas de exenciones: hasta entonces lo cubria, por el camino "
                    + "de sus llamadores, la exencion que `registrar` tenia declarada para "
                    + "el universo de la PROPIEDAD -- una exencion de un universo tapando un "
                    + "hueco del otro, que es justo lo que la separacion vino a impedir",

            "com.controllocal.service.impl.ProspeccionServiceImpl#captar",
            "convierte una prospeccion en encargo: tambien lo CREA. Ya tiene su autoridad "
                    + "-cargarEnProceso exige que la prospeccion sea del actor- y el hito que "
                    + "escribe es el de entrada al mercado del encargo que acaba de abrir. "
                    + "Estaba declarado solo para el universo de la PROPIEDAD y se colaba en "
                    + "este por el mapa unico",

            "com.controllocal.service.impl.CaptacionServiceImpl#registrar",
            "CREA el encargo sobre una propiedad que ya existe: no hay agente anterior a "
                    + "quien respetar porque el encargo nace aqui, y su agente es el actor "
                    + "-lo carga de actor.idRolOperativo(), no de un id del cuerpo-. Misma "
                    + "familia que abrirEncargo y que captar",

            "com.controllocal.service.impl.CaptacionServiceImpl#reasignar",
            "gobierna QUIEN lleva el encargo, no que dice el encargo (D-S0-17 fila 6, H6). "
                    + "Su autoridad es el alcance de supervision del broker, y el agente del "
                    + "encargo es justamente a quien se sustituye: exigir que el actor SEA "
                    + "ese agente haria imposible reasignar. No toca importe, exclusividad "
                    + "ni vigencia -- solo el id del agente y su rastro",

            "com.controllocal.service.impl.ContratoServiceImpl#registrar",
            "es el ciclo del CONTRATO: cierra el encargo que se cumplio y no toca su "
                    + "importe, su exclusividad ni su vigencia. Lo ejecuta el BROKER, que "
                    + "nunca es agente de un encargo, asi que exigirEdicionDelEncargo aqui "
                    + "significaria que ningun contrato se puede registrar. Misma familia "
                    + "que la exencion de cerrarLocal",

            "com.controllocal.service.impl.ContratoServiceImpl#firmar",
            "misma razon que registrar, en el otro extremo del ciclo: al firmar se cierra "
                    + "la operacion formalizada y con ella el encargo. Cubre tambien al "
                    + "privado cerrarOperacionFormalizada, cuyo unico llamador es este",

            // ---------------- El que aparecio con N43 ----------------
            "com.controllocal.service.impl.CaptacionServiceImpl#cerrarPorContrato",
            "cierra el encargo como CASCADA de un contrato, y por eso NO lleva "
                    + "exigirBandaComercial: el propio codigo lo dice en `cerrar` -- exigirla "
                    + "aqui dejaria contratos que no se pueden firmar. Lleva cargarConAcceso, "
                    + "o sea tenant (404) y alcance sobre el agente del encargo. Y hay un "
                    + "hecho medido el 2026-09-02 que hay que decir en vez de suponer: su censo "
                    + "de llamadores esta VACIO -- ningun controlador ni servicio la invoca, ni "
                    + "por la clase ni por CaptacionService; quien cierra el encargo al firmar "
                    + "es ContratoServiceImpl, con su propia exencion. El gate vigila ese censo: "
                    + "el dia que alguien la cablee esto se pone rojo y habra que decidir su "
                    + "guarda con el caso de uso delante, no ahora sin el");

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
                "setAgente", "setCondicionEconomica", "cerrar", "registrarRevision")) {
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

        String porDirtyChecking = "com.controllocal.service.impl.CaptacionServiceImpl#cerrar";
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
                "com.controllocal.service.impl.LocalComercialServiceImpl#agregarFoto")) {
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
                "com.controllocal.service.impl.PublicacionServiceImpl#registrarImportePublicado",
                // La via mas directa al importe del encargo, y la que este gate
                // no veia: `PUT /captaciones/{id}` reescribe la condicion
                // economica entera -- importe, moneda y comision -- por cascada
                // del save de `captacion`, sin pasar por precio_propiedad.
                "com.controllocal.service.impl.CaptacionServiceImpl#actualizar")) {
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
        List<String> declaradasFantasma = java.util.stream.Stream.concat(
                        FUERA_DE_LOS_DOS_UNIVERSOS.keySet().stream(),
                        VIGILADAS_POR_CASCADA.keySet().stream())
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
                    .anyMatch(m -> m.getAccessesFromSelf().stream()
                            .anyMatch(a -> entrada.getKey().equals(a.getTargetOwner().getFullName())
                                    && ESCRIBEN.contains(a.getName())));
            assertTrue(alguna,
                    "el gate no encuentra NINGUNA escritura por " + entrada.getKey() + ", que "
                            + "declara vigilar la tabla `" + entrada.getValue() + "`. O el "
                            + "repositorio cambio de nombre o de paquete, o su metodo de "
                            + "escritura ya no se llama como los de ESCRIBEN. En cualquiera de "
                            + "los dos casos esa tabla ha dejado de estar vigilada.");
        }
    }

    /**
     * <b>La decision que este gate protege tiene que VIAJAR con el codigo.</b>
     *
     * <h2>Por que es una comprobacion y no una nota</h2>
     * `docs/ai/*` esta en `.gitignore` con una lista blanca de excepciones. Es
     * deliberado —solo viaja lo que un clon limpio necesita— pero tiene un
     * filo: <b>un documento que gobierna y no viaja no gobierna nada</b>. El
     * clon no lo tiene, el auditor no lo ve, y la unica copia vive en el disco
     * de una maquina.
     *
     * <p>Esta prueba cierra ese filo por el unico camino que no depende de que
     * alguien se acuerde: <b>lee el fichero</b>. En el arbol de trabajo pasa
     * siempre; en un <b>clon limpio</b> solo pasa si el documento esta en la
     * lista blanca de `.gitignore`. Y la corrida de cierre se ejecuta tambien
     * desde un clon limpio, asi que sacar la decision de la lista pone el
     * cierre en <b>rojo</b> en vez de dejar la regla huerfana en silencio.
     *
     * <h2>Lo que NO dice</h2>
     * No dice que el documento sea correcto ni que este al dia. Dice que
     * <b>existe donde el codigo lo cita</b>. Que la regla sea la que el codigo
     * aplica lo prueban las otras comprobaciones de esta clase y las de
     * integracion.
     */
    @Test
    @DisplayName("la decision que gobierna esta autoridad viaja con el codigo")
    void laAutoridadQueGobiernaViajaConElCodigo() throws IOException {
        for (String documento : List.of(
                "decision-autoridad-de-edicion-de-la-propiedad.md",
                "decision-brox-intelligence-alcances-y-frontera.md")) {
            Path ruta = RAIZ.resolve("docs/ai").resolve(documento);
            assertTrue(Files.isRegularFile(ruta),
                    "falta " + ruta + ". O el documento se borro, o salio de la lista blanca "
                            + "de .gitignore -- y entonces un clon limpio no lo tiene y la regla "
                            + "que este gate protege se quedo sin autoridad escrita.");
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
        return porRepositorio || invocaUnMutador(metodo, ENCARGO, MUTADORES_DEL_ENCARGO);
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
        if (consultaLaAutoridad(metodo, guardas, escritores, new HashSet<>())) {
            return true;
        }
        if (cubiertoPorElCicloDelEncargo(metodo, guardas)) {
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
    private static boolean cubiertoPorElCicloDelEncargo(JavaMethod metodo, Set<String> guardas) {
        if (!GUARDAS_DEL_ENCARGO.equals(guardas) || !CICLO_DEL_ENCARGO.containsKey(nombre(metodo))) {
            return false;
        }
        return GUARDAS_DEL_CICLO_DEL_ENCARGO.stream().allMatch(guarda ->
                metodo.getAccessesFromSelf().stream().anyMatch(a ->
                        SERVICIO_DEL_ENCARGO.equals(a.getTargetOwner().getFullName())
                                && guarda.equals(a.getName())));
    }

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
     * sabotaje (a) de F3-3. {@code PropiedadUniversalServiceImpl#editar} escribe
     * el ENCARGO por <b>dos</b> caminos independientes:
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
            if (AUTORIDAD.equals(destino.getFullName()) && guardas.contains(llamada.getName())) {
                return true;
            }
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
     * <p>Se cuenta tanto la escritura propia como la delegada: {@code
     * aplicarCondicionesDe} no toca ningun repositorio del encargo por si
     * mismo, llama a {@code AtributosDeEncargo#escribir} — que <b>si</b> es un
     * escritor, y ademas esta exento porque no puede preguntar. Mirar solo la
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
}
