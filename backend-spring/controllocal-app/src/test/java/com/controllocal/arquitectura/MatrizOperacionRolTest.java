package com.controllocal.arquitectura;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Cierra la deuda transversal "matriz completa operacion-&gt;rol con test de
 * cobertura": hasta aqui los gates replicaban los de la v1 endpoint por
 * endpoint, sin ninguna prueba que demostrara que estaban TODOS.
 *
 * <p>La fuente de verdad es {@code docs/ai/matriz-operacion-rol.md} —un
 * documento, no una constante— porque su otro consumidor es humano: el SPA
 * Angular se apoya en el para decidir que muestra cada rol. Este test lo parsea
 * y rompe el build si el documento y el codigo dejan de coincidir, de modo que
 * <b>no puede quedar desactualizado</b>.
 *
 * <p>Lo que vigila, en las cuatro pruebas:
 * <ol>
 *   <li><b>Cobertura</b>: un endpoint nuevo no entra sin una fila que declare su
 *       decision de rol.</li>
 *   <li><b>Sin filas muertas</b>: una fila que sobrevive al endpoint que
 *       describia es peor que no tenerla.</li>
 *   <li><b>La matriz no miente</b>: los roles declarados son exactamente los que
 *       dice {@code @PreAuthorize} (de metodo, o de clase si el metodo no lo
 *       trae). Las operaciones sin gate deben declararse {@code TODOS} y traer
 *       nota de alcance: en este backend 53 operaciones autenticadas no filtran
 *       por rol sino por alcance, y ese silencio es justo lo que habia que
 *       documentar.</li>
 *   <li><b>Publico de verdad</b>: {@code PUBLICO} en el documento y
 *       {@code permitAll()} en {@code ConfiguracionSeguridad} son el mismo
 *       conjunto, en los dos sentidos.</li>
 * </ol>
 */
class MatrizOperacionRolTest {

    private static final String PAQUETE_CONTROLADORES = "com.controllocal.web.controlador";
    private static final String DOC = "docs/ai/matriz-operacion-rol.md";
    private static final String CONFIG_SEGURIDAD =
            "backend-spring/controllocal-web/src/main/java/com/controllocal/web/seguridad/"
                    + "ConfiguracionSeguridad.java";

    /** Sin gate de rol: autenticado, y lo que cambia por rol es el ALCANCE. */
    private static final String TODOS = "TODOS";
    /** Sin token: tiene que estar en la lista permitAll de ConfiguracionSeguridad. */
    private static final String PUBLICO = "PUBLICO";

    private static final Pattern ROL = Pattern.compile("'([A-Z_]+)'");
    private static final Pattern LITERAL = Pattern.compile("\"([^\"]+)\"");
    private static final Set<String> VERBOS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private static final JavaClasses CLASES = ImportacionesDeProducto.importador()
            // P-07 añade una entrada de interacción a la misma API comercial.
            // La superficie de recuperación del puerto de gestión conserva su contrato separado.
            .importPackages(PAQUETE_CONTROLADORES, "com.controllocal.web.asistencia");

    // ================================================================
    // El token {autoridad: CLAVE} (N36)
    // ================================================================

    private static final String PAQUETE_SERVICIO = "com.controllocal.service";

    private static final JavaClasses SERVICIOS = ImportacionesDeProducto.importador()
            .importPackages(PAQUETE_SERVICIO);

    /** {@code {autoridad: Clase.metodo}} o {@code {autoridad: TENANT}}. */
    private static final Pattern TOKEN =
            Pattern.compile("\\{autoridad:\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?)\\}");

    /**
     * <b>El vocabulario cerrado de autoridades</b>, clave &rarr; qué verifica.
     *
     * <h2>Qué problema cierra</h2>
     * La columna <b>Alcance</b> es prosa libre, y este gate sólo exigía que no
     * estuviera vacía —y sólo en las filas {@code TODOS}—. Así es como la fila
     * de {@code POST /locales/{id}/precios} declaró «un local de <b>sus
     * captaciones</b>» mientras el código comprobaba únicamente el tenant: el
     * texto era falso y ningún gate lo veía (`N36`). P0 hizo que el código
     * cumpla lo que esa fila decía; esto impide que la <b>siguiente</b> fila
     * vuelva a mentir.
     *
     * <h2>Por qué un vocabulario y no prosa verificada</h2>
     * Hacer ejecutable un párrafo es imposible; hacer ejecutable <b>una clave
     * de una lista cerrada</b> es trivial. El token no sustituye a la prosa —la
     * prosa sigue explicando el matiz—: le pone al lado el <b>nombre del método
     * que de verdad decide</b>, y ese nombre se comprueba contra el bytecode.
     *
     * <h2>Las claves en MAYÚSCULAS son confesiones, no atajos</h2>
     * {@code BANDA} dice «aquí no hay autoridad de alcance: sólo la anotación
     * del controlador». Escribirlo es la mitad del valor de este token — obliga
     * a mirar el servicio y a decir lo que se encontró, en vez de repetir lo que
     * la fila prometía.
     */
    private static final Map<String, String> VOCABULARIO = vocabulario(
            "MetaComercialServiceImpl.definicion", "20.10: definición estática, sin concesión de lectura comercial",
            "MetaComercialServiceImpl.del", "RF074: metas propias o del equipo competente actual, con historia sin conversión",
            "MetaComercialServiceImpl.validarEstablecimiento", "RF075: broker competente o independiente propio, revisión de versión exacta antes del efecto",
            "MetaComercialServiceImpl.proponer", "RF075: agente empresarial propone para sí, SID e idempotencia, sin establecer objetivo",
            "MetaComercialServiceImpl.propuestasPendientes", "RF075: sólo propuestas propias o supervisadas con permiso vigente",
            "MetaComercialServiceImpl.consultarPropuesta", "RF075: cabecera y ámbito antes del contenido, sin existencia entre organizaciones",
            "MetaComercialServiceImpl.validarDecision", "RF075: decisión competente y versión exacta, sin sustituir la confirmación humana",
            "RendimientoComercialServiceImpl.del", "20.10: conteos por fecha, operación y responsable histórico con permiso actual",
            "RendimientoComercialServiceImpl.pulso", "RF077: sólo broker, cobertura de su equipo actual, sin ranking ni logros propios ficticios",
            "ReportePropietarioServiceImpl.listar", "RF078: cabeceras y fuentes originales con ámbito actual, sin comunicación implícita",
            "ReportePropietarioServiceImpl.preview", "RF078: composición canónica autorizada por período y corte",
            "ReportePropietarioServiceImpl.preparar", "RF078: responsable y SID, versión inmutable, destinatario contextual y finalidad",
            "ReportePropietarioServiceImpl.consultar", "RF078: permiso de fuentes/contacto antes de hidratar y devolver la versión",
            "ReportePropietarioServiceImpl.obtenerArchivo", "RF078: lectura/SID, admisión B0 y salida durable privada; descargar no comunica",
            "ReportePropietarioServiceImpl.consultarAntecedente", "RF078: agregado histórico con su definición y fuentes; sin reinterpretar envío",
            "ReportePropietarioServiceImpl.comunicaciones", "RF079: historia autorizada por reporte/contacto/evidencias y sin acuse ficticio",
            "ReportePropietarioServiceImpl.registrarComunicacion", "RF079: hecho realizado declarado por responsable actual con versión exacta e idempotencia",
            "ReportePropietarioServiceImpl.validarRectificacion", "RF079: revisión humana exacta del antecedente y la corrección; CAS y conservación",
            "CompromisosDeSeguimiento.listar", "C7/R27: alcance actual y fuentes/destinatario originales antes de entregar constancias",
            "CompromisosDeSeguimiento.planificar", "C7: agente responsable, SID, versiones, origen y fecha expresos, historia y retraso conservados",
            "CompromisosDeSeguimiento.acreditar", "C7: comunicación real, tipo/contenido/contexto/destinatario, versión e identidad sin duplicar el hecho",
            "CompromisosDeSeguimiento.resolver", "C7: resolución legítima con evidencia/fecha y sin llamada o cumplimiento ficticios",
            "InformesC7.consultarMediciones", "RF073 básico: cuatro definiciones, ámbito/corte y evidencia, sin comprar permiso por plan",
            "InformesC7.derechos", "C7: operador actual, modalidad y ciclo B0 vigente no revocado",
            "InformesC7.preparar", "C7: composición contratada sobre fuentes/población autorizadas; revalidación y dinero C5 separado",
            "ProgramasPersonales.listar", "E41: sólo metadata de programas propios en organización/contexto actuales",
            "ProgramasPersonales.consultar", "E41: propietario actual de la configuración, sin acceso comercial por autoría histórica",
            "ProgramasPersonales.configurar", "P0: independiente ONE_PRO, SID, mandato humano limitado/versionado e idempotente",
            "ProgramasPersonales.cambiarEstado", "P0: mismo propietario/Pro y CAS; pausa/cancelación y próxima ocurrencia futura sin avalancha",
            "ProgramasPersonales.ocurrencias", "P0: metadata paginada sólo del programa propio, no hidrata resultado",
            "ProgramasPersonales.resultado", "P0: mandato/Pro/contexto/fuentes originales antes de hidratar y antes de entregar resultado privado",
            "SeguridadServiceImpl.cuentas", "C1: proyección mínima de pertenencias de su organización, sin secretos de cuenta",
            "CuentaServiceImpl.cambiarContrasena", "C1: autoridad de cuenta propia o pertenencia vigente y sesión ligada al efecto",
            "CuentaServiceImpl.consultarPropia", "C1: autoridad de cuenta propia o pertenencia vigente y sesión ligada al efecto",
            "CuentaServiceImpl.solicitarCambioCorreo", "C1: autoridad de cuenta propia o pertenencia vigente y sesión ligada al efecto",
            "InvitacionesMembresiaServiceImpl.emitir", "C1: autoridad de cuenta propia o pertenencia vigente y sesión ligada al efecto",
            "InvitacionesMembresiaServiceImpl.aceptar", "C1: autoridad de cuenta propia o pertenencia vigente y sesión ligada al efecto",
            "InvitacionesMembresiaServiceImpl.retirar", "C1: autoridad de cuenta propia o pertenencia vigente y sesión ligada al efecto",
            "MembresiasServiceImpl.darBaja", "C1: autoridad de cuenta propia o pertenencia vigente y sesión ligada al efecto",
            "AgenteServiceImpl.consultarParaGobierno", "C1: autoridad de cuenta propia o pertenencia vigente y sesión ligada al efecto",

            "Alcances.paraGobiernoDeReasignacion", "C1: proyección mínima de gobierno, sin ficha comercial",
            "ContextosServiceImpl.comprobarSesion", "C1: sesión/revisión y pertenencia vigentes sin fallback",
            "ContextosServiceImpl.contextos", "C1: opciones de pertenencia/atribución ofrecidas por Core",
            "ContextosServiceImpl.seleccionar", "C1: selección segura y rotación contextual",
            "AccesoArchivoService.resolver", "C1: referencia unívoca, recurso y finalidad vigentes",
            "AutoridadDePropiedad.buscarParaLectura", "C1: lectura relacional y origen aprobado",
            "ObservacionMercadoServiceImpl.registrar", "AC054: AGENTE y lectura física actual; observación separada e idempotente",
            "ObservacionMercadoServiceImpl.listarDe", "AC054: AGENTE/BROKER y lectura física actual antes de consultar observaciones",

            "InicioSeguroServiceImpl.completar", "C0: contexto, vigencia y autoridad real del contrato seguro",
            "InicioSeguroServiceImpl.consumir", "C0: contexto, vigencia y autoridad real del contrato seguro",
            "ActuacionesServiceImpl.prepararCaptura", "C0: contexto, vigencia y autoridad real del contrato seguro",
            "ActuacionesServiceImpl.prepararOrigen", "RF014: prepara sólo con ambos extremos/versiones y permiso físico actual; no confirma ni registra",
            "ActuacionesServiceImpl.prepararDecisionEncargo", "RF015/4.6.2: prepara revisión exacta de petición y firma; sólo el dispatch humano confirmado decide por AccesoEncargo.exigirDecision",
            "UnidadRelacionadaServiceImpl.consultarOrigen", "RF014: lectura actual de ambos extremos y corte Instant sin acceso por transitividad",
            "ActuacionesServiceImpl.consultar", "C0: contexto, vigencia y autoridad real del contrato seguro",
            "ActuacionesServiceImpl.revisar", "C0: contexto, vigencia y autoridad real del contrato seguro",
            "ActuacionesServiceImpl.confirmar", "C0: contexto, vigencia y autoridad real del contrato seguro",

            // CAT20.9: métodos reales y permiso acotado; se conserva el vocabulario cerrado y su control de bytecode.
            "ColaboracionServiceImpl.invitacionesRecibidas", "Destinatario propio o supervisado; origen autorizado antes de visibilidad y grant bilateral vigente antes de valores",
            "ColaboracionServiceImpl.invitacionesOriginadas", "Responsable actual del Encargo de origen o broker competente de su equipo; no crea permiso de consulta de otra cartera",
            "ActuacionesServiceImpl.prepararInvitacionColaboracion", "Encargo/versiones/instrumento propios y destinatario expreso, sin Demanda sintética; sólo prepara la actuación",
            "ReasignacionDeProspeccion.consultar", "Gobierno autorizado sobre su org/equipo; sólo id/version/responsable, sin expediente comercial",
            "ReasignacionDeProspeccion.reasignar", "RF017: contexto actual, ambos extremos, elegibilidad, CAS y recibo durable; no cambia Encargo ni fechas",
            // C3 aprobado: lista explícita; se conserva la comprobación de existencia en bytecode.
            "CoincidenciaServiceImpl.consultar", "CAT-E20: origen/versiones propios y proyección autorizada por capa antes de recuperar/calcular",
            "CoincidenciaServiceImpl.continuar", "CAT-E20: recorrido/SID/TTL y versiones actuales, sin reiniciar el corte",
            "CoincidenciaServiceImpl.explicar", "CAT-E20: candidato del recorrido reautorizado, misma evaluación y frontera de datos",
            "ColaboracionServiceImpl.consultarAcuerdo", "CAT-E19: dos decisiones seleccionadas, intersección exacta y revalidación del grant antes/después",
            "DisponibilidadContextualServiceImpl.confirmar", "CAT-E28: responsable AGENTE actual, cuenta/SID/CAS y hecho específico conservado",
            "DisponibilidadContextualServiceImpl.consultar", "CAT-E28: lectura actual propia o supervisada, sin facultad de confirmar para broker",
            "AvisosNetworkServiceImpl.consultar", "C3: destinatario histórico más autorización/tenant/SID actuales; sólo metadata privada",
            "NetworkServiceImpl.consultar", "C3: participación del Encargo propio o supervisado competente",
            "NetworkServiceImpl.previsualizar", "C3: fuente propia y selección V1, sin inventar legitimación",
            "NetworkServiceImpl.consultarSolicitud", "C3: solicitud del ámbito competente con su versión y diferencias",
            "ActuacionesServiceImpl.prepararFinDemanda", "RF030: prepara fin de demanda propia sin ejecutar ni afectar oportunidades",
            "ActuacionesServiceImpl.prepararSolicitudColaboracion", "CAT-E19: solicitud propia por recursos/versiones/alcance",
            "ActuacionesServiceImpl.prepararDecisionColaboracion", "CAT-E19: snapshot del decisor real de SU lado; no autoridad de contraparte",
            "ActuacionesServiceImpl.prepararRevocacionColaboracion", "CAT-E19: lado competente y concesión actual, conserva historia",
            "ActuacionesServiceImpl.prepararCanalColaboracion", "C3: sólo titular responsable declara canal explícito, no teléfono personal inferido",
            "ActuacionesServiceImpl.prepararSolicitudNetwork", "C3: solicitud granular del responsable, no publicación unilateral empresarial",
            "ActuacionesServiceImpl.prepararDecisionNetwork", "C3: broker competente o independiente propio, versión exacta y permiso actual",
            "ActuacionesServiceImpl.prepararDesactivacionNetwork", "C3: broker competente, decisión directa trazada sin suprimir historia",
            "RequerimientoServiceImpl.crear", "CAT-E18: AGENTE responsable, interesado contextual legítimo, SID/clave y snapshot atómicos",
            "RequerimientoServiceImpl.actualizar", "CAT-E18: demanda propia abierta y CAS, criterios/definiciones/historia conservados",
            "RequerimientoServiceImpl.buscar", "CAT-E18: ficha privada propia actual, sin acceso por compartir cliente",
            "RequerimientoServiceImpl.listarPorCliente", "CAT-E18: sólo demandas propias del cliente autorizado, sin presupuesto ajeno",

            "MotorDeCapturaImpl.consultarResultado",
            "C0: captura del tenant, clave original y actor iniciador, consulta del resultado confirmado con acceso vigente; no reemite",
            "CapacidadesServiceImpl.consultar",
            "C0/K0: actor de servidor, política del adaptador real y contexto cargado por tenant con las "
                    + "guardas actuales. El descriptor no autoriza el comando y no publica rutas ni secretos",
            // ---- La autoridad de P0, sobre la propiedad y sobre el encargo ----
            "AutoridadDePropiedad.exigirEdicion",
            "quien escribe un hecho de la PROPIEDAD es su responsable actual (P0-1). Deniega; no "
                    + "pregunta",

            "AutoridadDePropiedad.exigirEdicionDelEncargo",
            "quien escribe el trato -importe, exclusividad, vigencia, condiciones- es el agente de "
                    + "ESE encargo (P0-4). No la sustituye exigirEdicion: son dos autoridades",

            "AutoridadDePropiedad.fijarAlAlta",
            "el ALTA fija el responsable inicial y deja su fila origen=ALTA (P0-5, V88). No hay "
                    + "responsable anterior a quien respetar porque la fila nace aqui",

            "AutoridadDePropiedad.asignar",
            "el TRASPASO: banda BROKER/TENANT_ADMIN, alcance sobre el saliente y sobre el destino "
                    + "(C6), elegibilidad del destino (D-P0-7), estado observado con "
                    + "compare-and-set (D-P0-9) y fila de expediente, todo en una transaccion",

            "AutoridadDePropiedad.exigirLecturaDelExpediente",
            "el expediente de traspasos es superficie de gobierno (D-P0-6): BROKER en su alcance "
                    + "-alcanzaParaGobiernoDeTraspasoFisico- y TENANT_ADMIN en su tenant; el AGENTE no, ni "
                    + "siendo el responsable vigente",

            "AutoridadDePropiedad.puedeIniciarTraspaso",
            "banda no-AGENTE mas alcance sobre el SALIENTE, sin destino todavia. Es el mismo "
                    + "predicado que apaga el boton en la ficha, para que no prometa lo que el "
                    + "POST negara",

            "AutoridadDePropiedad.puedeLeerHistoricoDelEncargo",
            "quien puede leer el historico economico de un encargo (D-P0-6): su agente y el BROKER "
                    + "que lo supervisa hoy. El TENANT_ADMIN no: gobernar no es operar",

            "ElegibilidadDeResponsable.candidatos",
            "las cinco condiciones del destino -tenant, rol AGENTE vigente, cuenta habilitada, "
                    + "membresia vigente, disponibilidad operativa- mas supervision si el actor es "
                    + "BROKER, con el mismo predicado SQL que revalida el comando",

            "ElegibilidadDeResponsable.candidatosExcluyendo",
            "lo mismo excluyendo a quien ya lo lleva: ofrecer al actual seria ofrecer un cambio "
                    + "que el comando rechaza",

            // ---- El alcance por banda, que es anterior a todo lo demas ----
            "Alcances.de",
            "el alcance de LECTURA por banda: el AGENTE lo suyo, el BROKER lo de sus supervisados "
                    + "vigentes, el TENANT_ADMIN su tenant. Resuelve tenant y banda en ese orden",

            "Alcances.alcanza",
            "si el actor alcanza a ESTE agente dueno del objeto. Es la pregunta puntual; "
                    + "Alcances.de es la de conjunto",

            "Alcances.alcanzaParaGobiernoDeTraspasoFisico",
            "igual, pero admitiendo que el objeto no tenga dueno: el inventario FALTANTE lo "
                    + "gobierna cualquier BROKER del tenant (C5)",

            // ---- Autoridades propias de cada vertical ----
            "AccesoSolicitud.conAcceso",
            "carga la solicitud con el alcance del actor y 404 fuera del tenant. Es el unico "
                    + "componente de autoridad del expediente del cliente",

            "CaptacionServiceImpl.cargarConAcceso",
            "tenant primero (404 de otra corredora) y alcance sobre el agente del encargo despues",

            "CaptacionServiceImpl.puedeReasignar",
            "BROKER que supervisa hoy al agente que lo lleva, o gobierno del tenant. Es el mismo "
                    + "predicado que publica capacidades.puedeReasignar, para que la lista de "
                    + "destinos y el boton no puedan discrepar",

            "ProspeccionServiceImpl.cargarEnProceso",
            "la prospeccion tiene que ser del actor y estar en un estado que admita el paso. Sin "
                    + "alcance de broker: el trabajo previo al encargo es del agente",

            "ProspeccionServiceImpl.cargarConAcceso",
            "la prospeccion dentro del alcance del actor (lectura), con 404 fuera del tenant",

            "VisitaServiceImpl.validarProgramacion",
            "C4: contexto/version explícitos, AGENTE responsable de la oportunidad; no hereda edición por supervisión o gobierno",

            "InteraccionServiceImpl.validarRegistro",
            "C4: operador actual y autorización de cada vínculo, participante y dato antes de registrar el hecho",

            "OportunidadServiceImpl.validarApertura",
            "C4: responsable del Encargo, versión y manifestación de interesado legítima; no se fabrica Cliente",

            "InteraccionServiceImpl.validarRectificacion", "Original y corrección autorizados, CAS y motivo conservados",
            "InteraccionServiceImpl.vincular", "Vínculo contextual autorizado sobre interacción propia, con SID y CAS",
            "InteraccionServiceImpl.observar", "Hecho posterior contextual autorizado; no sobrescribe la comunicación",
            "OportunidadServiceImpl.validarNoContinuidad", "Responsable actual, oportunidad abierta, fecha y CAS explícitos",
            "VisitaServiceImpl.validarReprogramacion", "Visita propia y versión actual; motivo y programación explícitos",
            "VisitaServiceImpl.validarCancelacion", "Visita propia y versión actual; prepara decisión humana con fecha y motivo",
            "VisitaServiceImpl.validarResultado", "Ocurrencia real y visita autorizada; rectificación conserva antecedente",
            "OfertaServiceImpl.validarRegistroOferta", "Encargo y mesa autorizados con partes, objeto, dinero y versiones explícitos",
            "OfertaServiceImpl.validarContraoferta", "Otra parte real responde a versión exacta de la misma mesa",
            "OfertaServiceImpl.validarAceptacion", "Parte competente manifiesta aceptación de propuesta vigente exacta; no cambia precio del Encargo",
            "OfertaServiceImpl.validarRechazo", "Rechazo económico contextual; no cierra la oportunidad",
            "OfertaServiceImpl.validarRetirada", "Retirada por la parte proponente sobre su propuesta exacta",
            "SeguimientoDeProspeccion.planificar", "R27 propio, fuente y destinatario vigentes, versión explícita; espera no inventa fecha",
            "SeguimientoDeProspeccion.posponer", "R27 asignado al actor actual; no posponer seguimiento ajeno ni reiniciar D2",

            "AccesoOportunidad.conAcceso",
            "la MISMA regla que la anterior, extraida a un componente para que la mesa de "
                    + "negociacion no tuviera que copiarla (WP-A): tenant delante -404 de otra "
                    + "corredora- y despues el alcance, que en una oportunidad es por CAPTACION "
                    + "supervisada y no por agente. Es la pregunta de LECTURA: no concede escribir "
                    + "en la mesa, que es la del encargo",

            "CierreVentaServiceImpl.cargarConAcceso",
            "el encargo cuya venta se consulta o se cierra: tenant primero (404) y alcance sobre "
                    + "su agente despues, el mismo orden y el mismo predicado que "
                    + "CaptacionServiceImpl.cargarConAcceso. Quien ESCRIBE el cierre necesita "
                    + "ademas exigirEdicionDelEncargo, que va justo detras",

            "ClienteServiceImpl.cargarConAcceso",
            "el cliente dentro del alcance del actor; el catalogo es compartido salvo para el "
                    + "BROKER, cuyo conjunto sale de las oportunidades de su equipo",

            "PropietarioServiceImpl.cargarConAcceso",
            "el propietario dentro del alcance del actor",

            "SolicitudServiceImpl.exigirAgenteDisponible",
            "el alta de una solicitud exige que el agente este operativo. Es lo unico que el Core "
                    + "comprueba mas alla del tenant en esta via",

            "ContratoServiceImpl.cargarConAcceso",
            "el contrato dentro del alcance del actor, con 404 fuera del tenant. Cubre tambien las "
                    + "transiciones, que pasan todas por transicionarContrato",

            "ContratoServiceImpl.exigirAlcance",
            "alcance explicito sobre el contrato antes de tocarlo",

            "EvaluacionServiceImpl.exigirSupervisionSobreElAgente",
            "evaluar una solicitud exige supervision vigente sobre el agente que la presento: "
                    + "decidir sobre el trabajo de otro equipo no es supervision",

            "EvaluacionServiceImpl.listar",
            "la regla vive INLINE: la consulta recibe actor.esTenantAdmin() y idRolOperativo, asi "
                    + "que un BROKER solo ve SUS evaluaciones y el gobierno las del tenant",

            "EvaluacionServiceImpl.obtener",
            "la regla vive INLINE y por eso se nombra el metodo: la evaluacion de otro broker "
                    + "responde 404, no 403 -filtrar su propia lista y no encontrar nada",

            "MetaComercialServiceImpl.exigirBroker",
            "fijar o resolver una meta es del BROKER; el alcance del equipo lo pone Alcances.de "
                    + "dentro del mismo metodo",

            "AgenteServiceImpl.exigirBrokerOAdmin",
            "leer el padron de agentes es supervision o gobierno, nunca del agente",

            "AgenteServiceImpl.exigirGobierno",
            "dar de alta o editar un agente es gobierno del tenant (D-S0-17 filas 17 y 18): un "
                    + "BROKER no lo hace ni con su propio equipo",

            "BrokerServiceImpl.validarAdministrador",
            "alta y edicion de brokers: gobierno del tenant",

            "AsignacionServiceImpl.validarAdministrador",
            "el organigrama agente-broker es gobierno del tenant, no supervision",

            "LocalComercialServiceImpl.listarMisLocales",
            "la regla vive INLINE y sale entera de la sesion: los locales de las captaciones del "
                    + "actor, por actor.idRolOperativo(). No hay id ajeno sobre el que decidir",

            "TareaServiceImpl.bandejaDe",
            "la regla vive INLINE: la bandeja es la del agente que pregunta -actor.idRolOperativo-, "
                    + "y el propio servicio rechaza a quien no es AGENTE por si lo llaman desde "
                    + "otro sitio",

            "TareaServiceImpl.cancelar",
            "la regla vive INLINE: la tarea tiene que ser del agente que la cancela",

            "AccesoEncargo.leer",
            "Tenant y alcance comercial actual de episodio: AGENTE propio/BROKER competente; niega gobierno.",

            "AccesoContrato.leer",
            "C5: autoridad contextual de contrato por solicitud propia o responsable actual del Encargo; broker competente y gobierno excluido.",

            "ContratoServiceImpl.solicitudAprobadaPropia",
            "C5: preparación P exige la solicitud aprobada del agente actual; no crea una firma ni dinero.",

            "AccesoEncargo.exigirResponsable",
            "Responsable AGENTE actual del episodio; se ejecuta después del inicio contextual seguro.",

            "AccesoEncargo.exigirDecision",
            "Broker competente o independiente propio declarado; gobierno no decide negocio.",

            "AccesoEncargo.modificarContenido",
            "AC066: editor propio, términos no aceptados, versión de Encargo y contexto actual antes de cambiar pacto o condiciones dinámicas.",

            "AccesoEncargo.iniciarReasignacion",
            "Gobierno de reasignación: cuentas ordenadas, SID actual y autoridad de banda/recurso antes del CAS.",

            "EncargoServiceImpl.comando",
            "Frontera canónica de comandos: SID, locks, autoridad específica, idempotencia, efecto y snapshot atómicos.",

            "EncargoServiceImpl.autorizarDocumento",
            "Documento propio sin episodio, o documento de episodio comercial autorizado; no abre proveedor al cliente.",

            "EncargoServiceImpl.cancelarSolicitudFin",
            "Solicitud propia del responsable actual, versión y motivo dentro del comando seguro; no termina episodio.",

            "GobiernoCatalogoCapturaServiceImpl.exigirGobierno",
            "TENANT_ADMIN vigente de la organización y SID sin restricciones: sólo vocabulario privado; no cartera.",

            "CaptacionServiceImpl.consultarReasignable",
            "Proyección mínima de gobierno, alcance actual y episodio operativamente reasignable.",

            "MotorDeCapturaImpl.descartar",
            "Captura propia, actor AGENTE y versión actual; conserva evento/antecedente.",

            "MotorDeCapturaImpl.posponerRevision",
            "Posposición propia con fecha/motivo y CAS; conserva edad de detección y revisión agrupada.",

            "SeleccionFotosContextualesService.observar",
            "Lectura contextual de galería mediante FotoPropiedadService.listar y su autoridad física canónica; AGENTE/BROKER, sin gobierno.",

            "SeleccionFotosContextualesService.seleccionar",
            "Sólo AGENTE: referencias/orden dentro de la galería autorizada y de su huella observada; no publica ni transforma originales.",

            "EncargoServiceImpl.autorizarDestino",
            "AGENTE/responsable actual del episodio o autor del documento sin episodio; contexto propio real y coherente antes de cargar/vincular.",

            // C6: autoridades Core concretas; los adaptadores no sustituyen estas guardas.
            "FuentesMaterialIaCore.preparar",
            "C6/E44: AGENTE y SID actuales, lectura física o Encargo propio y fuente revalidada; utilidad local sin habilitar proveedor.",
            "MaterialIaServiceImpl.solicitar",
            "C6/B0: contexto propio, versión de fuente, E44 y financiación actuales; UUID idempotente, sin autoridad del modelo.",
            "MaterialIaServiceImpl.consultar",
            "C6: job del actor/tenant, SID y lectura histórica autorizada actuales; consultar no vuelve a generar ni exige gasto activo.",
            "DerivadasMaterialIaService.autorizar",
            "C6: SID, job propio, recurso físico, original, archivo, QC y vínculo exactos; el adaptador refuerza cobertura e integridad binaria.",
            "DerivadasMaterialIaService.seleccionActual",
            "C6: original autorizado y selección única; ORIGINAL inicial no fabrica decisión ni abre el job privado.",
            "DerivadasMaterialIaService.versiones",
            "C6: versiones del recurso actualmente permitido, revisión y fechas reales; consulta no renueva el plazo ni transfiere prompts.",
            "DerivadasMaterialIaService.seleccionar",
            "C6: elección humana por original con SID/CAS/idempotencia e historia; sin nueva IA, borrado silencioso o selección que supere QC.",
            "DerivadasMaterialIaService.guardarAlternativa",
            "C6: conservación expresa de una versión permitida con SID/CAS, cuota y protección separadas de su selección.",
            "DerivadasMaterialIaService.descartarTemporal",
            "C6: retirada expresa del temporal prescindible, con SID/CAS y verificación; no elimina originales o protegidos ni libera cuota ficticia.",
            "DerivadasMaterialIaService.retirarAlternativa",
            "C6: retirada autorizada expresa de guardada no seleccionada/protegida, con SID/CAS y ausencia comprobada; no nueva IA.",
            "FuentesMaterialIaCore.presentarTratamiento",
            "C6/E44: AGENTE prepara el ámbito real del recurso y presenta aviso/condiciones actuales de su cuenta; no acredita terceros.",
            "EvidenciasTratamientoMaterialIaImpl.capturar",
            "E44: cuenta/SID y presentación propios, recurso autorizado, aviso actual y CAS; rechaza procedencia del modelo y decisión por otra cuenta.",

            "ComprasManualesB0.catalogo", "B0: C1 y titular individual o TenantAdmin actual, modalidad real y oferta comercial",
            "ComprasManualesB0.listar", "B0: contexto comprador vigente y filtro por workspace antes de paginar",
            "ComprasManualesB0.consultar", "B0: contexto comprador actual, pedido propio sin autoridad perpetua por autoría",
            "ComprasManualesB0.preparar", "B0: comprador/ciclo/oferta actuales, términos Core sin pago ni consentimiento supuesto",
            "ComprasManualesB0.consentir", "B0: versión revisada, aplicabilidad y comprador actual antes del enlace humano",
            "AtribucionesPlataforma.atribucionesCompra", "R25: opciones de concesión propias vigentes, identidad global sin Actor ficticio",
            "ComprasManualesB0.listarFinanciero", "R25: colección cerrada, acción y todos los campos antes de consultar",
            "ComprasManualesB0.consultarFinanciero", "R25: lectura exacta con concesión/acción/campos/vigencia actuales",
            "ComprasManualesB0.iniciarVerificacion", "R25: sólo pago informado, comando/version/historia, no acredita fondos",
            "ComprasManualesB0.verificarFondos", "R25: fondos reales y pago único durable con cuenta/concesión, antes de activar",
            "ComprasManualesB0.acreditar", "R25: permiso actual y hecho durable, mismo motor y activación atómica/idempotente",
            "ComprasManualesB0.incidencia", "R25: causa privada/version/historia, sin alterar derechos acreditados",
            "MetricasIaB0.tecnica", "R25: identidad/SID y concesion OPERACION_IA_PLATFORM CONSULTAR_ESTADO/METRICA_TECNICA, plan/version/finalidad/caso actuales antes de consulta y entrega; sin ledger ni cantidades individuales",
            "MetricasIaB0.financiera", "R25: identidad/SID y concesion FINANCIERO_PLATFORM LEER_LEDGER/LEDGER, caso/comando/fondo y plan/version/finalidad exactos, revalidados antes de entregar",
            "ConfirmacionesCostoIaB0.confirmar", "R25: FIN explicita AJUSTAR_COSTO/LEDGER, caso/plan/version/finalidad/fondo y SID actuales, fuente completa e inmutable y cadena/CAS; conserva primero y aplica sin nueva inferencia o cargo comercial",
            "ConfirmacionesCostoIaB0.aplicar", "R25: recupera la misma confirmacion bajo accion y ambito actuales; aplica solo diferencia tecnica con fondos/techo validos, sin segunda liquidacion, otro POST, autoridad por correo o ledger general",

            "InvestigacionesMercado.preparar", "C1/contexto AGENTE/BROKER o independiente actual; propiedad opcional autorizada, carencia y base aprobada primero. Solo prepara plan y maximo, sin reserva.",
            "InvestigacionesMercado.solicitar", "C1 actual, alcance/huella/maximo/plazo aceptados, politica/fuentes/regla/plaza/saldo vigentes y admision B0 atomica con historia; no inicia IA ni contacto externo.",
            "InvestigacionesMercado.consultar", "Contexto solicitante actual y trabajo propio por organizacion/persona/atribucion; autor no conserva acceso perpetuo. Estado y maximo aceptado, sin ledger.",
            "InvestigacionesMercado.resultado", "Mismo permiso privado y fuente/politica actuales; solo resultado comercial reducido de revision competente, sin consultas, informe interno o consumo tecnico.",
            "InvestigacionesMercado.retirarNoIniciada", "Solicitante actual, comando/CAS y ejecucion acreditada no iniciada; libera mismas fuentes B0. Incierto no se cancela ni libera por inferencia.",
            "InvestigacionesMercado.referencia", "C1/contexto comercial vigente; ultima referencia comun aprobada, vigente y permitida, sin expediente privado. Lectura determinista gratuita, sin precio inventado ni cargo.",
            "AtribucionesPlataforma.atribucionesMercado", "Cuenta/SID propios, concesiones R25 actuales del recurso cerrado MERCADO_MANUAL_I0. Listar no concede ejecucion ni cartera.",
            "InvestigacionesMercado.configurar", "REVISION_MERCADO_PLATFORM CONFIGURAR_MERCADO/CONFIGURACION y decision del titular acreditada sobre huella exacta. Version inmutable, sin valores productivos implicitos.",
            "InvestigacionesMercado.consultarConfiguracion", "REVISION_MERCADO_PLATFORM CONSULTAR_TRABAJO/CONFIGURACION actual; parametros de evidencia autorizados, sin tarifa financiera o contexto privado del solicitante.",
            "InvestigacionesMercado.retirarPolitica", "Misma autoridad de configuracion y decision acreditada exacta; retiro inmutable/versionado impide nuevas entregas sin borrar historia o conciliacion legitima anterior.",
            "InvestigacionesMercado.mantenimiento", "Revision/configuracion R25 actual, politica y regla corporativa aprobadas con cota. Cuenta real y presupuesto BROX; sin organizacion ficticia o bolsa profesional.",
            "InvestigacionesMercado.cola", "INVESTIGACION_PLATFORM CONSULTAR_TRABAJO/METADATOS_TRABAJO; solo cabeceras de ejecucion, sin maximo financiero, contexto o evidencia.",
            "InvestigacionesMercado.colaRevision", "REVISION_MERCADO_PLATFORM CONSULTAR_TRABAJO/METADATOS_TRABAJO; solo cabeceras de entregas por revisar.",
            "InvestigacionesMercado.colaFinanciera", "FINANCIERO_PLATFORM CONSULTAR_TRABAJO/METADATOS_TRABAJO; solo cabeceras revisadas por liquidar, no informes del operador.",
            "InvestigacionesMercado.consultarOperador", "Investigacion R25 actual, operador asignado, CONSULTAR_TRABAJO y campos METADATOS_TRABAJO/CONTEXTO_MINIMO/EVIDENCIA_PUBLICA. Sin ledger.",
            "InvestigacionesMercado.consultarRevision", "Revision R25 actual, CONSULTAR_TRABAJO y METADATOS_TRABAJO/EVIDENCIA_PUBLICA; sin plan/carencia privados ni maximo del solicitante.",
            "InvestigacionesMercado.consultarFinanciero", "Finanzas R25 actual, CONSULTAR_TRABAJO y METADATOS_TRABAJO/LEDGER; regla/tarifa/maximo/rondas y liquidacion exactos. No concedido al operador.",
            "InvestigacionesMercado.tomar", "Investigacion R25 TOMAR_TRABAJO, METADATOS_TRABAJO/CONTEXTO_MINIMO, politica actual y CAS. Reclamacion exclusiva; recupera permiso revocado sin reiniciar plazo o reserva.",
            "InvestigacionesMercado.entregar", "Operador asignado y concesion actual ENTREGAR_EVIDENCIA, METADATOS_TRABAJO/EVIDENCIA_PUBLICA; fuentes/alcance/fechas/version, sin revision autoatribuida ni nueva ronda por corregir.",
            "InvestigacionesMercado.revisar", "Revisor R25 REVISAR_EVIDENCIA y METADATOS_TRABAJO/EVIDENCIA_PUBLICA; decision/motivo/CAS e historia. Observar evidencia publicada retira esa version, no toda la base.",
            "InvestigacionesMercado.publicar", "Revisor R25 PUBLICAR_REFERENCIA y METADATOS_TRABAJO/EVIDENCIA_PUBLICA; seleccion completa revisada y limites publicos expresos, vigencia y CAS. No publica contexto privado ni cobra.",
            "InvestigacionesMercado.liquidar", "FINANCIERO_PLATFORM CONCILIAR_TRABAJO/LEDGER actual; rondas completas acreditadas y misma reserva B0. Incierto retiene; concurrencia/reintento/correccion sin segundo cargo.",

            "NotificacionesBrokerC7.listar", "CAT-R28/NB: broker actual competente por recurso, destinatario revalidado y proyeccion minima; historia solo propia, sin envio externo o cartera general.",
            "NotificacionesBrokerC7.atender", "Mismo destinatario/permiso actual y SID antes de marcar atencion privada idempotente; atender no resuelve el hecho comercial ni amplifica autoridad.",
            "ReconfirmacionesDisponibilidad.listar", "CAT-E28/D2: solicitud propia del responsable actual y consulta D5 aprobada, identidad e historia conservadas; reloj de frescura distinto de atencion.",
            "ReconfirmacionesDisponibilidad.solicitarInicial", "Responsable/recurso/version y SID actuales, clave estable, procedencia humana y CAS; solicitud disponible no inventa confirmacion ni renueva frescura.",
            "ReconfirmacionesDisponibilidad.registrarEspera", "Responsable actual, SID/CAS/clave y causa propia; posponer/desconocer conserva solicitud y plazos originales30/37/6/7. No trabajo ajeno.",
            "InvestigacionesMercado.liberarNoIniciada", "FINANCIERO_PLATFORM CONCILIAR_TRABAJO/LEDGER actual; solo PENDIENTE sin operador/entrega y perdida de autorizacion comprobada. Reserva/cota original liberada sin cargo o devolucion de dinero. Trabajo iniciado/incierto no se trata como ejecucion cero; fallo tecnico no acredita perdida de permiso.",

            // ---- Pseudo-claves: dicen que NO hay componente de autoridad ----
            "TENANT",
            "SOLO la frontera de organizacion. El objeto se carga por (organizacion, id) y no hay "
                    + "ninguna otra comprobacion de alcance: cualquiera de esa banda dentro del "
                    + "tenant llega",

            "BANDA",
            "SOLO decide la anotacion del controlador. No hay autoridad de alcance en el servicio "
                    + "-y se dice, en vez de dejar que la prosa insinue una-. Cubre tambien las "
                    + "altas cuyo dueno sale del token: no hay objeto previo sobre el que decidir",

            "SESION",
            "actua sobre la PROPIA cuenta o identidad del actor: todo sale del token y no hay id "
                    + "ajeno que autorizar",

            "GOBIERNO",
            "gobierno del tenant: solo TENANT_ADMIN -el gate exige que los roles de la fila sean "
                    + "exactamente eso- y su alcance es su propia organizacion");

    /** Las claves con punto nombran un metodo; el gate comprueba que exista. */
    private static boolean esClaveDeMetodo(String clave) {
        return clave.indexOf('.') > 0;
    }

    private static Map<String, String> vocabulario(String... pares) {
        if (pares.length % 2 != 0) {
            throw new AssertionError("el vocabulario tiene una clave sin explicacion: "
                    + pares.length + " cadenas no son pares clave/significado.");
        }
        Map<String, String> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            if (mapa.put(pares[i], pares[i + 1]) != null) {
                throw new AssertionError("clave duplicada en el vocabulario: " + pares[i]);
            }
        }
        return Map.copyOf(mapa);
    }

    /** Clave de la matriz: verbo + ruta completa ya normalizada. */
    private record Operacion(String metodo, String ruta) implements Comparable<Operacion> {
        @Override
        public int compareTo(Operacion otra) {
            int porRuta = ruta.compareTo(otra.ruta);
            return porRuta != 0 ? porRuta : metodo.compareTo(otra.metodo);
        }

        @Override
        public String toString() {
            return metodo + " " + ruta;
        }
    }

    /** Fila del documento: roles declarados + por que no hay gate, cuando no lo hay. */
    private record Fila(String roles, String alcance) {
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    void todaOperacionDelCodigoEstaEnLaMatriz() {
        Set<Operacion> enElCodigo = operacionesDelCodigo().keySet();
        Set<Operacion> enElDoc = filasDelDocumento().keySet();

        List<Operacion> sinDeclarar = new ArrayList<>(new TreeSet<>(enElCodigo));
        sinDeclarar.removeAll(enElDoc);

        if (!sinDeclarar.isEmpty()) {
            fail("Hay " + sinDeclarar.size() + " operacion(es) REST sin fila en " + DOC
                    + ". Un endpoint nuevo exige una decision de rol EXPLICITA; anade su fila"
                    + " (roles + alcance) antes de darlo por hecho:\n  " + unaPorLinea(sinDeclarar));
        }
    }

    @Test
    void laMatrizNoTieneFilasMuertas() {
        Set<Operacion> enElCodigo = operacionesDelCodigo().keySet();
        Set<Operacion> enElDoc = filasDelDocumento().keySet();

        List<Operacion> sobran = new ArrayList<>(new TreeSet<>(enElDoc));
        sobran.removeAll(enElCodigo);

        if (!sobran.isEmpty()) {
            fail("Hay " + sobran.size() + " fila(s) en " + DOC + " que ya no corresponden a"
                    + " ningun endpoint. Una fila que sobrevive al endpoint que describia"
                    + " desinforma al SPA; borrala:\n  " + unaPorLinea(sobran));
        }
    }

    @Test
    void losRolesDeclaradosSonLosQueAplicaSpringSecurity() {
        Map<Operacion, String> codigo = operacionesDelCodigo();
        Map<Operacion, Fila> doc = filasDelDocumento();

        List<String> desviaciones = new ArrayList<>();
        for (Operacion operacion : new TreeSet<>(codigo.keySet())) {
            Fila fila = doc.get(operacion);
            if (fila == null) {
                continue; // lo reporta todaOperacionDelCodigoEstaEnLaMatriz
            }
            String efectivos = codigo.get(operacion);
            // PUBLICO tambien es "sin gate de rol"; que ademas sea permitAll lo
            // comprueba lasRutasPublicasSonExactamenteLasDePermitAll.
            String declarados = PUBLICO.equals(fila.roles()) ? TODOS : ordenados(fila.roles());

            if (!efectivos.equals(declarados)) {
                desviaciones.add(operacion + " -> la matriz dice [" + fila.roles()
                        + "] y @PreAuthorize aplica [" + efectivos + "]");
            } else if (TODOS.equals(efectivos) && fila.alcance().isBlank()) {
                desviaciones.add(operacion + " -> declarada TODOS con la columna Alcance VACIA:"
                        + " sin gate de rol, el alcance es lo unico que limita lo que devuelve");
            }
        }

        if (!desviaciones.isEmpty()) {
            fail("La matriz miente en " + desviaciones.size() + " operacion(es) de " + DOC
                    + ":\n  " + String.join("\n  ", desviaciones));
        }
    }

    /**
     * <b>Toda fila con gate de rol declara QUÉ autoridad decide su alcance</b>
     * (`N36`), y la declaración se comprueba.
     *
     * <p>Cuatro exigencias, y ninguna es cosmética:
     * <ol>
     *   <li><b>exactamente un token</b> por fila protegida — cero deja la fila
     *       en prosa libre, que es el estado del que venimos; dos significa que
     *       nadie eligió, y una fila que declara dos autoridades no declara
     *       ninguna;</li>
     *   <li>la clave está en el <b>vocabulario cerrado</b> — inventar una es
     *       volver a la prosa por otro camino;</li>
     *   <li>las claves {@code Clase.metodo} <b>existen en el bytecode</b> de
     *       {@code com.controllocal.service}: un método renombrado deja la
     *       matriz apuntando a algo que ya no existe, y eso tiene que doler en
     *       el build, no en una auditoría;</li>
     *   <li>{@code GOBIERNO} sólo en filas cuyo rol es <b>exactamente</b>
     *       {@code TENANT_ADMIN} — es la pseudo-clave que más fácil se usaría
     *       para tapar una fila de BRÓKER que nadie quiso mirar.</li>
     * </ol>
     *
     * <p><b>Y imprime el censo.</b> Ese censo —filas, públicas, sin gate,
     * protegidas y cuántas por autoridad— <b>sustituye a las cifras que la
     * cabecera del documento llevaba escritas a mano</b> y que caducaron tres
     * veces seguidas (`N38`). Un número que genera una comprobación no envejece;
     * uno transcrito, siempre.
     */
    @Test
    void todaFilaProtegidaDeclaraQueAutoridadDecideSuAlcance() {
        Map<Operacion, Fila> doc = filasDelDocumento();

        // CONTROL POSITIVO del importador de servicios. Si el paquete cambiara
        // de nombre o el jar no estuviera en el classpath, la comprobacion de
        // "el metodo existe" pasaria a medir sobre un conjunto vacio y todas las
        // claves darian error -o, peor, ninguna-. Es la leccion del barrido de
        // `grep -iF` del 2026-08-24: un cero sin control positivo no es medida.
        assertTrue(existeMetodo("AutoridadDePropiedad", "exigirEdicion"),
                "el gate no ve AutoridadDePropiedad.exigirEdicion en " + PAQUETE_SERVICIO
                        + ", que es la guarda central de P0. Sin ella, la comprobacion de que "
                        + "cada clave apunta a un metodo real no esta midiendo nada.");

        List<String> problemas = new ArrayList<>();
        Map<String, Integer> censoPorClave = new java.util.TreeMap<>();
        int publicas = 0;
        int sinGate = 0;
        int protegidas = 0;

        for (Map.Entry<Operacion, Fila> entrada : new java.util.TreeMap<>(doc).entrySet()) {
            Operacion operacion = entrada.getKey();
            Fila fila = entrada.getValue();
            boolean protegida = !TODOS.equals(fila.roles()) && !PUBLICO.equals(fila.roles());
            if (PUBLICO.equals(fila.roles())) {
                publicas++;
            } else if (!protegida) {
                sinGate++;
            } else {
                protegidas++;
            }

            List<String> claves = new ArrayList<>();
            Matcher matcher = TOKEN.matcher(fila.alcance());
            while (matcher.find()) {
                claves.add(matcher.group(1));
            }
            // Un `{autoridad:` que el patron no reconoce es peor que ninguno:
            // la fila parece declarada y no lo esta.
            int aperturas = contar(fila.alcance(), "{autoridad:");
            if (aperturas != claves.size()) {
                problemas.add(operacion + " -> lleva " + aperturas + " veces `{autoridad:` y solo "
                        + claves.size() + " token(s) bien formado(s). Escribelo "
                        + "`{autoridad: Clase.metodo}` o `{autoridad: TENANT}`");
                continue;
            }

            if (protegida && claves.size() != 1) {
                problemas.add(operacion + " -> fila PROTEGIDA (" + fila.roles() + ") con "
                        + claves.size() + " token(s) `{autoridad: ...}`. Tiene que llevar "
                        + "EXACTAMENTE uno: cero deja la fila en prosa libre -que es como "
                        + "`POST /locales/{id}/precios` declaro un alcance que el codigo no "
                        + "comprobaba- y dos significa que nadie eligio cual decide");
                continue;
            }
            if (claves.size() > 1) {
                problemas.add(operacion + " -> " + claves.size() + " tokens en una fila sin gate "
                        + "de rol. El token es opcional aqui, pero si va, va uno");
                continue;
            }
            if (claves.isEmpty()) {
                continue; // fila TODOS/PUBLICO sin token: permitido
            }

            String clave = claves.get(0);
            censoPorClave.merge(clave, 1, Integer::sum);

            if (!VOCABULARIO.containsKey(clave)) {
                problemas.add(operacion + " -> `" + clave + "` no esta en el vocabulario. El "
                        + "vocabulario es CERRADO a proposito: una clave inventada es prosa "
                        + "libre con aspecto de dato. Vocabulario: " + VOCABULARIO.keySet());
                continue;
            }
            if (esClaveDeMetodo(clave)) {
                String clase = clave.substring(0, clave.indexOf('.'));
                String metodo = clave.substring(clave.indexOf('.') + 1);
                if (!existeMetodo(clase, metodo)) {
                    problemas.add(operacion + " -> `" + clave + "` no corresponde a ningun metodo "
                            + "de " + PAQUETE_SERVICIO + ". O se renombro, o se movio, o nunca "
                            + "existio: mientras tanto la matriz apunta a una autoridad que no "
                            + "esta");
                }
            }
            if ("GOBIERNO".equals(clave) && !"TENANT_ADMIN".equals(fila.roles().trim())) {
                problemas.add(operacion + " -> declara GOBIERNO con roles [" + fila.roles()
                        + "]. GOBIERNO significa que decide el gobierno del tenant Y NADA MAS, "
                        + "asi que sus roles tienen que ser exactamente TENANT_ADMIN. Una fila "
                        + "de BROKER etiquetada GOBIERNO es justo la que nadie volveria a mirar");
            }
        }

        // EL CENSO. Sustituye a las cifras manuales de la cabecera del documento.
        StringBuilder censo = new StringBuilder();
        censo.append("\n=== CENSO DE LA MATRIZ (").append(DOC).append(") ===\n")
                .append("  filas totales .......... ").append(doc.size()).append('\n')
                .append("  PUBLICO ................ ").append(publicas).append('\n')
                .append("  TODOS (sin gate) ....... ").append(sinGate).append('\n')
                .append("  protegidas (con gate) .. ").append(protegidas).append('\n')
                .append("  --- filas por autoridad declarada ---\n");
        censoPorClave.forEach((clave, veces) ->
                censo.append(String.format("  %-52s %3d%n", clave, veces)));
        censo.append("  (las claves en MAYUSCULAS declaran que NO hay componente de autoridad)\n");
        System.out.print(censo);

        if (!problemas.isEmpty()) {
            fail("La matriz declara mal su autoridad en " + problemas.size() + " operacion(es) de "
                    + DOC + ":\n  " + String.join("\n  ", problemas) + censo);
        }
    }

    private static int contar(String texto, String aguja) {
        int veces = 0;
        int desde = texto.indexOf(aguja);
        while (desde >= 0) {
            veces++;
            desde = texto.indexOf(aguja, desde + aguja.length());
        }
        return veces;
    }

    /** ¿Hay en {@code com.controllocal.service} una clase así con ese método? */
    private static boolean existeMetodo(String claseSimple, String metodo) {
        return SERVICIOS.stream()
                .filter(clase -> clase.getSimpleName().equals(claseSimple))
                .flatMap(clase -> clase.getMethods().stream())
                .anyMatch(m -> m.getName().equals(metodo));
    }

    @Test
    void materialIaDeclaraSusDoceAutoridadesCoreYContextoActual() {
        Map<Operacion, String> autoridades = Map.ofEntries(
                Map.entry(new Operacion("POST", "/material-ia/preparaciones"), "FuentesMaterialIaCore.preparar"),
                Map.entry(new Operacion("POST", "/material-ia/solicitudes"), "MaterialIaServiceImpl.solicitar"),
                Map.entry(new Operacion("GET", "/material-ia/{operacion}"), "MaterialIaServiceImpl.consultar"),
                Map.entry(new Operacion("GET", "/material-ia/{operacion}/archivos/{archivo}"), "DerivadasMaterialIaService.autorizar"),
                Map.entry(new Operacion("GET", "/material-ia/fotografias/{original}/seleccion"), "DerivadasMaterialIaService.seleccionActual"),
                Map.entry(new Operacion("GET", "/material-ia/fotografias/{original}/versiones"), "DerivadasMaterialIaService.versiones"),
                Map.entry(new Operacion("POST", "/material-ia/fotografias/{original}/seleccion"), "DerivadasMaterialIaService.seleccionar"),
                Map.entry(new Operacion("POST", "/material-ia/{operacion}/archivos/{archivo}/alternativa"), "DerivadasMaterialIaService.guardarAlternativa"),
                Map.entry(new Operacion("POST", "/material-ia/{operacion}/archivos/{archivo}/descarte"), "DerivadasMaterialIaService.descartarTemporal"),
                Map.entry(new Operacion("POST", "/material-ia/{operacion}/archivos/{archivo}/retirada-alternativa"), "DerivadasMaterialIaService.retirarAlternativa"),
                Map.entry(new Operacion("POST", "/material-ia/tratamiento/presentaciones"), "FuentesMaterialIaCore.presentarTratamiento"),
                Map.entry(new Operacion("POST", "/material-ia/tratamiento/decisiones"), "EvidenciasTratamientoMaterialIaImpl.capturar"));
        var filas = filasDelDocumento();
        autoridades.forEach((operacion, clave) -> {
            assertEquals("AGENTE", filas.get(operacion).roles(), operacion.toString());
            assertTrue(filas.get(operacion).alcance().contains("{autoridad: " + clave + "}"), operacion.toString());
            assertTrue(VOCABULARIO.containsKey(clave), clave);
            String[] partes = clave.split("\\.");
            var metodo = SERVICIOS.stream().filter(c -> c.getSimpleName().equals(partes[0]))
                    .flatMap(c -> c.getMethods().stream()).filter(m -> m.getName().equals(partes[1]))
                    .filter(m -> m.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC))
                    .findFirst().orElseThrow();
            var tipos = metodo.getRawParameterTypes().stream().map(JavaClass::getName).collect(java.util.stream.Collectors.toSet());
            assertTrue(tipos.contains("com.controllocal.service.Actor"), clave);
            assertTrue(tipos.contains("com.controllocal.service.actuacion.ActuacionesService$ContextoSesion"), clave);
        });
        for (String inventada : List.of("SIN_ALCANCE", "MaterialIaServiceImpl.autorizarPorUUID",
                "FuentesMaterialIaCore.autorizarSinAlcance", "ResultadosMaterialIaAlmacen.descargar"))
            assertTrue(!VOCABULARIO.containsKey(inventada), inventada);
        var controlador = CLASES.stream().filter(c -> c.getSimpleName().equals("MaterialIaController")).findFirst().orElseThrow();
        for (String nombre : List.of("preparar", "solicitar", "consultar", "descargar", "seleccion", "versiones", "seleccionar", "guardarAlternativa", "descartarTemporal", "retirarAlternativa", "presentarTratamiento", "decidirTratamiento")) {
            var metodo = controlador.getMethods().stream().filter(m -> m.getName().equals(nombre)).findFirst().orElseThrow();
            assertTrue(metodo.getMethodCallsFromSelf().stream().anyMatch(c -> c.getTargetOwner().getSimpleName().equals("SesionActual")
                    && c.getTarget().getName().equals("actor")), nombre);
            assertTrue(metodo.getMethodCallsFromSelf().stream().anyMatch(c -> c.getTargetOwner().getSimpleName().equals("SesionActual")
                    && c.getTarget().getName().equals("sesion")), nombre);
            assertTrue(metodo.getMethodCallsFromSelf().stream().anyMatch(c -> c.getTargetOwner().getSimpleName().equals("ContextoDeActuacion")
                    && c.getTarget().getName().equals("de")), nombre);
        }
    }

    @Test
    void adaptadorDeResultadosConservaAutoridadCoreCoberturaYVerificacionBinaria() {
        // El vocabulario sigue importando sólo Service. La frontera de I/O no se convierte en autoridad nueva.
        String adaptador = String.join("", lineas("backend-spring/controllocal-web/src/main/java/com/controllocal/web/almacen/ResultadosMaterialIaAlmacen.java"))
                .replaceAll("\\s+", "");
        for (String obligatoria : List.of("varl=registro.autorizar(job,archivo,actor,sid);vigente(l,actor,sid);",
                "varcontenido=leer(l);vigente(l,actor,sid);namespace(l.binario());returncontenido;",
                "vars=registro.seleccionActual(original,actor,sid);autorizarSeleccion(s,actor);returns;",
                "returnregistro.versiones(original,actor,sid).stream().filter(v->permitida(v,actor)).toList();",
                "returnregistro.seleccionar(original,job,archivo,esperada,clave,actor,sid,l->comprobar(l,actor,sid));",
                "returnregistro.guardarAlternativa(job,archivo,esperada,clave,actor,sid,l->comprobar(l,actor,sid));",
                "varplan=registro.descartarTemporal(job,archivo,esperada,clave,actor,sid);retirar(plan);returnregistro.version(job,actor);",
                "varplan=registro.retirarAlternativa(job,archivo,esperada,clave,actor,sid);retirar(plan);returnregistro.version(job,actor);",
                "fuentes.exigirLecturaArtefacto(l.solicitud(),l.snapshot(),actor);namespace(l.binario());",
                "artefacto(l,actor);leer(l);artefacto(l,actor);registro.revalidar(l,actor,sid);",
                "if(!l.equals(actual))thrownewConflictoException",
                "varb=leerBinario(l.binario());returnnewContenido(b.contenido(),l.binario().contentType()",
                "namespace(esperado);Optional<AlmacenDocumentos.ArchivoDescargado>leido;",
                "leido=almacen.abrir(esperado.clavePrivada());",
                "varb=leido.orElseThrow(()->newNoEncontradoException(\"Archivodederivada\"));namespace(esperado);",
                "if(!coincide(esperado,b))thrownewConflictoException",
                "a.contenido().length==b.bytes()&&b.huella().equals(EvaluacionTecnicaFoto.huella(a.contenido()))&&b.contentType().equals(a.contentType())",
                "registro.confirmarAusencia(plan,p->cuota.liberarRetiradaVerificada(",
                "returnregistro.grupos(actor,v->permitida(v,actor));",
                "varestado=fuentes.evaluarLecturaArtefacto(l.solicitud(),l.snapshot(),actor);",
                "if(estado==null)thrownewIllegalStateException",
                "if(estado!=EvidenciasTratamientoMaterialIa.EstadoUsoArtefacto.PERMITIDO)returnfalse;",
                "namespace(l.binario());returntrue;",
                "catch(AccesoNoAutorizadoException|NoEncontradoException|CapacidadDeshabilitadaExceptiondenegada){returnfalse;}"))
            assertTrue(adaptador.contains(obligatoria), obligatoria);
        String fuente = String.join("", lineas("backend-spring/controllocal-service/src/main/java/com/controllocal/service/materialia/FuentesMaterialIaCore.java"))
                .replaceAll("\\s+", "");
        assertTrue(fuente.contains("evidencias.revalidarArtefacto(snapshot.evidencia().referenciaEvidencia(),ambito(s,snapshot),actor);"));
        String registro = String.join("", lineas("backend-spring/controllocal-service/src/main/java/com/controllocal/service/materialia/DerivadasMaterialIaService.java"))
                .replaceAll("\\s+", "");
        assertTrue(registro.contains("List<GrupoCaducidad>grupos(Actoractor,Predicate<Lectura>permitida)"));
        assertTrue(registro.contains("try{lectura=autorizarArchivo(v.operacion(),v.archivo(),actor);}"));
        assertTrue(registro.contains("returnpermitida.test(lectura);"));
        for (var cadena : List.of(
                List.of("DerivadasMaterialIaService", "autorizar", "iniciarArchivo"),
                List.of("DerivadasMaterialIaService", "autorizar", "autorizarContenido"),
                List.of("DerivadasMaterialIaService", "autorizarContenido", "autorizarArchivo"),
                List.of("DerivadasMaterialIaService", "autorizarArchivo", "origen"),
                List.of("DerivadasMaterialIaService", "seleccionActual", "iniciarArchivo"),
                List.of("DerivadasMaterialIaService", "seleccionActual", "foto"),
                List.of("DerivadasMaterialIaService", "versiones", "iniciarArchivo"),
                List.of("DerivadasMaterialIaService", "versiones", "foto"),
                List.of("DerivadasMaterialIaService", "seleccionar", "iniciarArchivo"),
                List.of("DerivadasMaterialIaService", "seleccionar", "autorizarContenido"),
                List.of("DerivadasMaterialIaService", "guardarAlternativa", "iniciarArchivo"),
                List.of("DerivadasMaterialIaService", "guardarAlternativa", "autorizarContenido"),
                List.of("DerivadasMaterialIaService", "descartarTemporal", "retiradaExpresa"),
                List.of("DerivadasMaterialIaService", "retirarAlternativa", "retiradaExpresa"),
                List.of("DerivadasMaterialIaService", "retiradaExpresa", "iniciarArchivo"),
                List.of("DerivadasMaterialIaService", "retiradaExpresa", "foto"),
                List.of("EvidenciasTratamientoMaterialIaImpl", "capturar", "sesion"),
                List.of("EvidenciasTratamientoMaterialIaImpl", "capturar", "autorizar"),
                List.of("EvidenciasTratamientoMaterialIaImpl", "sesion", "exigirContexto"),
                List.of("EvidenciasTratamientoMaterialIaImpl", "revalidarArtefacto", "validarUsoArtefacto"),
                List.of("EvidenciasTratamientoMaterialIaImpl", "evaluarUsoArtefacto", "validarUsoArtefacto"),
                List.of("EvidenciasTratamientoMaterialIaImpl", "validarUsoArtefacto", "cuentaActual"),
                List.of("EvidenciasTratamientoMaterialIaImpl", "validarUsoArtefacto", "autorizar"),
                List.of("FuentesMaterialIaCore", "exigirLecturaArtefacto", "tx"),
                List.of("FuentesMaterialIaCore", "exigirRecursoDeLectura", "exigirAgente"),
                List.of("FuentesMaterialIaCore", "exigirRecursoDeLectura", "exigirLecturaHistorica"),
                List.of("FuentesMaterialIaCore", "fuente", "exigirAgente"),
                List.of("FuentesMaterialIaCore", "fuente", "exigirResponsable"))) {
            assertTrue(SERVICIOS.stream().filter(c -> c.getSimpleName().equals(cadena.get(0)))
                    .flatMap(c -> c.getMethods().stream()).filter(m -> m.getName().equals(cadena.get(1)))
                    .flatMap(m -> m.getMethodCallsFromSelf().stream()).anyMatch(c -> c.getTarget().getName().equals(cadena.get(2))),
                    String.join(" -> ", cadena));
        }
    }

    @Test
    void lasRutasPublicasSonExactamenteLasDePermitAll() {
        Set<String> enElDoc = new TreeSet<>();
        filasDelDocumento().forEach((operacion, fila) -> {
            if (PUBLICO.equals(fila.roles())) {
                enElDoc.add(operacion.ruta());
            }
        });
        Set<String> permitAll = rutasPermitAll();

        // 1) Nada se declara publico en el doc sin estar en permitAll.
        enElDoc.forEach(ruta -> assertTrue(permitAll.contains(ruta),
                "La matriz declara PUBLICO " + ruta + ", pero ConfiguracionSeguridad no lo deja"
                        + " en permitAll: sin token responde 401, no la respuesta publica"
                        + " que el doc promete"));

        // 2) Ni al reves: abrir una ruta propia en permitAll y no declararla es
        //    como se cuela un endpoint sin autenticar.
        Set<String> rutasDelCodigo = new TreeSet<>();
        operacionesDelCodigo().keySet().forEach(operacion -> rutasDelCodigo.add(operacion.ruta()));
        Set<String> abiertasSinDeclarar = new TreeSet<>(permitAll);
        abiertasSinDeclarar.retainAll(rutasDelCodigo);
        abiertasSinDeclarar.removeAll(enElDoc);

        assertEquals(Set.of(), abiertasSinDeclarar,
                "Estas rutas estan en permitAll de ConfiguracionSeguridad pero la matriz no las"
                        + " declara PUBLICO. Abrir un endpoint sin dejarlo escrito es exactamente"
                        + " el descuido que este test viene a evitar");
    }

    // ------------------------------------------------------------- el codigo

    /** Operacion -&gt; roles efectivos (lista ordenada separada por coma, o TODOS). */
    private static Map<Operacion, String> operacionesDelCodigo() {
        Map<Operacion, String> operaciones = new LinkedHashMap<>();
        for (JavaClass controlador : CLASES) {
            String base = primerValor(anotacion(controlador, RequestMapping.class));
            String rolesDeClase = roles(controlador.tryGetAnnotationOfType(PreAuthorize.class)
                    .map(PreAuthorize::value).orElse(null));

            for (JavaMethod metodo : controlador.getMethods()) {
                String rolesDeMetodo = metodo.tryGetAnnotationOfType(PreAuthorize.class)
                        .map(PreAuthorize::value)
                        .map(MatrizOperacionRolTest::roles)
                        .orElse(rolesDeClase);

                for (Map.Entry<String, String[]> mapeo : mapeos(metodo).entrySet()) {
                    String[] rutas = mapeo.getValue().length == 0 ? new String[] {""} : mapeo.getValue();
                    for (String ruta : rutas) {
                        operaciones.put(new Operacion(mapeo.getKey(), unir(base, ruta)), rolesDeMetodo);
                    }
                }
            }
        }
        return operaciones;
    }

    /** Verbo HTTP -&gt; rutas declaradas, para el unico mapeo que puede traer un metodo. */
    private static Map<String, String[]> mapeos(JavaMethod metodo) {
        Map<String, String[]> mapeos = new LinkedHashMap<>();
        metodo.tryGetAnnotationOfType(GetMapping.class)
                .ifPresent(a -> mapeos.put("GET", a.value()));
        metodo.tryGetAnnotationOfType(PostMapping.class)
                .ifPresent(a -> mapeos.put("POST", a.value()));
        metodo.tryGetAnnotationOfType(PutMapping.class)
                .ifPresent(a -> mapeos.put("PUT", a.value()));
        metodo.tryGetAnnotationOfType(PatchMapping.class)
                .ifPresent(a -> mapeos.put("PATCH", a.value()));
        metodo.tryGetAnnotationOfType(DeleteMapping.class)
                .ifPresent(a -> mapeos.put("DELETE", a.value()));
        return mapeos;
    }

    private static <A extends Annotation> A anotacion(JavaClass clase, Class<A> tipo) {
        return clase.tryGetAnnotationOfType(tipo).orElse(null);
    }

    private static String primerValor(RequestMapping mapeo) {
        if (mapeo == null) {
            return "";
        }
        if (mapeo.value().length > 0) {
            return mapeo.value()[0];
        }
        return mapeo.path().length > 0 ? mapeo.path()[0] : "";
    }

    /**
     * {@code hasAnyRole('BROKER', 'ADMIN')} -&gt; {@code "ADMIN, BROKER"}.
     * Se ordena para que la matriz no dependa del orden en que se escribio la
     * anotacion. Sin expresion, TODOS.
     */
    private static String roles(String expresion) {
        if (expresion == null || expresion.isBlank()) {
            return TODOS;
        }
        Set<String> roles = new TreeSet<>();
        Matcher matcher = ROL.matcher(expresion);
        while (matcher.find()) {
            roles.add(matcher.group(1));
        }
        return roles.isEmpty() ? TODOS : String.join(", ", roles);
    }

    /**
     * Canoniza la lista de roles de una fila del documento. El orden en que se
     * escriben no significa nada —"BROKER, ADMIN" y "ADMIN, BROKER" son el mismo
     * permiso—, asi que la comparacion no puede depender de el; el documento se
     * escribe como se lee mejor.
     */
    private static String ordenados(String declarados) {
        if (TODOS.equals(declarados) || declarados.isBlank()) {
            return declarados;
        }
        Set<String> roles = new TreeSet<>();
        for (String rol : declarados.split(",")) {
            if (!rol.isBlank()) {
                roles.add(rol.trim());
            }
        }
        return String.join(", ", roles);
    }

    /** Ruta de clase + ruta de metodo, con las barras colapsadas: {@code /a/b}. */
    private static String unir(String base, String metodo) {
        String ruta = ("/" + base + "/" + metodo).replaceAll("/+", "/");
        return ruta.length() > 1 && ruta.endsWith("/") ? ruta.substring(0, ruta.length() - 1) : ruta;
    }

    // ---------------------------------------------------------- el documento

    /** Filas de las tablas del documento; ignora encabezados, separadores y prosa. */
    private static Map<Operacion, Fila> filasDelDocumento() {
        Map<Operacion, Fila> filas = new LinkedHashMap<>();
        for (String linea : lineas(DOC)) {
            if (!linea.startsWith("|")) {
                continue;
            }
            String[] celdas = linea.split("\\|", -1);
            if (celdas.length < 6) {
                continue; // tablas de menos columnas (la leyenda de Roles)
            }
            String metodo = celdas[1].trim();
            if (!VERBOS.contains(metodo)) {
                continue; // encabezado, separador o fila de otra tabla
            }
            String ruta = celdas[2].trim().replace("`", "");
            Operacion operacion = new Operacion(metodo, ruta);

            Fila previa = filas.put(operacion, new Fila(celdas[3].trim(), celdas[4].trim()));
            assertTrue(previa == null, "La matriz declara dos veces " + operacion
                    + ": una operacion con dos filas es una contradiccion esperando a pasar");
        }
        assertTrue(filas.size() > 100, "Solo se parsearon " + filas.size() + " filas de " + DOC
                + ": el formato de la tabla cambio y el test dejo de leerla de verdad");
        return filas;
    }

    /** Rutas abiertas sin token, leidas de la fuente de ConfiguracionSeguridad. */
    private static Set<String> rutasPermitAll() {
        Set<String> rutas = new LinkedHashSet<>();
        for (String linea : lineas(CONFIG_SEGURIDAD)) {
            if (!linea.contains("requestMatchers(") || !linea.contains("permitAll()")) {
                continue;
            }
            Matcher matcher = LITERAL.matcher(linea);
            while (matcher.find()) {
                rutas.add(matcher.group(1));
            }
        }
        assertTrue(!rutas.isEmpty(), "No se pudo leer ninguna ruta permitAll de " + CONFIG_SEGURIDAD
                + ": si la configuracion cambio de forma, este test dejo de vigilar lo publico");
        return rutas;
    }

    // ------------------------------------------------------------- utilidades

    /**
     * Los dos archivos que este test lee viven fuera del modulo, asi que se
     * busca la raiz del repositorio subiendo desde el directorio de trabajo
     * (surefire lo fija en el basedir del modulo) en vez de asumir cuantos
     * niveles hay.
     */
    private static List<String> lineas(String rutaRelativa) {
        Path directorio = Path.of("").toAbsolutePath();
        while (directorio != null) {
            Path candidato = directorio.resolve(rutaRelativa);
            if (Files.isRegularFile(candidato)) {
                try {
                    return Files.readAllLines(candidato, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException("No se pudo leer " + candidato, e);
                }
            }
            directorio = directorio.getParent();
        }
        throw new IllegalStateException("No se encontro " + rutaRelativa + " subiendo desde "
                + Path.of("").toAbsolutePath() + ". Es la fuente de verdad de la matriz"
                + " operacion-rol; sin ella este test no puede vigilar nada.");
    }

    private static String unaPorLinea(List<Operacion> operaciones) {
        return operaciones.stream().map(Operacion::toString)
                .reduce((a, b) -> a + "\n  " + b).orElse("");
    }
    @Test
    void laConfirmacionComunSeleccionaLaCapacidadGuardadaPorCore() throws ReflectiveOperationException {
        var confirmar=com.controllocal.web.controlador.ActuacionesController.class.getDeclaredMethod("confirmar",
                String.class,com.controllocal.service.actuacion.ActuacionesService.Confirmacion.class,
                jakarta.servlet.http.HttpServletRequest.class);
        var capacidad=confirmar.getAnnotation(com.controllocal.web.capacidades.CapacidadReconocida.class);
        assertTrue(capacidad!=null&&capacidad.porActuacion(),"La ruta común conserva selección por actuación durable.");
        assertEquals(Set.of("confirmar_captura","registrar_origen_fisico_unidad","retirar_finalizar_demanda",
                "solicitar_colaboracion","invitar_colaboracion_encargo","decidir_colaboracion","revocar_colaboracion","declarar_canal_colaboracion",
                "solicitar_participacion_network","decidir_participacion_network","desactivar_participacion_network",
                "abrir_oportunidad","rectificar_interaccion","registrar_no_continuidad_oportunidad","cancelar_visita",
                "presentar_oferta","registrar_contraoferta","registrar_aceptacion_oferta",
                "registrar_rechazo_oferta","registrar_retirada_oferta","decidir_captacion",
                "presentar_expediente_operacion","evaluar_expediente_operacion","informar_hito_contractual",
                "confirmar_hito_contractual","rectificar_hito_contractual","registrar_cobro","asignar_reparto_comision",
                "autorizar_borrador_adenda","reprogramar_gestion_cobro","concluir_gestion_cobro",
                "publicar_anuncio","actualizar_anuncio_visible","pausar_anuncio","reanudar_anuncio",
                "finalizar_difusion_anuncio","obtener_pieza_para_difusion"),Set.of(capacidad.value()));
        String adaptador=String.join("",lineas("backend-spring/controllocal-web/src/main/java/com/controllocal/web/capacidades/RegistroAdaptadoresHttp.java"))
                .replaceAll("\\s+","");
        for(String obligatoria:List.of("handler.getMethodAnnotation(CapacidadReconocida.class).porActuacion()",
                "actuaciones.getObject().capacidadDe(ref,SesionActual.actor())",
                "if(!ids(handler).contains(capacidad))thrownewIllegalStateException",
                "registro.exigirHabilitadas(List.of(capacidad));",
                "}elseregistro.exigirHabilitadas(ids(handler));"))
            assertTrue(adaptador.contains(obligatoria),obligatoria);
        assertEquals(2,adaptador.split(Pattern.quote("registro.exigirHabilitadas("),-1).length-1,
                "No se añade una comprobación conjunta de ambas banderas antes o después de seleccionar el efecto.");
    }

}
