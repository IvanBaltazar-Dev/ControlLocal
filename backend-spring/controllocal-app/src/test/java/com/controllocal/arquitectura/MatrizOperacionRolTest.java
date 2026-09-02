package com.controllocal.arquitectura;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(PAQUETE_CONTROLADORES);

    // ================================================================
    // El token {autoridad: CLAVE} (N36)
    // ================================================================

    private static final String PAQUETE_SERVICIO = "com.controllocal.service";

    private static final JavaClasses SERVICIOS = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
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
                    + "-alcanzaIncluidoSinDueno- y TENANT_ADMIN en su tenant; el AGENTE no, ni "
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

            "Alcances.alcanzaIncluidoSinDueno",
            "igual, pero admitiendo que el objeto no tenga dueno: el inventario FALTANTE lo "
                    + "gobierna cualquier BROKER del tenant (C5)",

            // ---- Autoridades propias de cada vertical ----
            "AccesoSolicitud.conAcceso",
            "carga la solicitud con el alcance del actor y 404 fuera del tenant. Es el unico "
                    + "componente de autoridad del expediente del cliente",

            "CaptacionServiceImpl.cargarConAcceso",
            "tenant primero (404 de otra corredora) y alcance sobre el agente del encargo despues",

            "CaptacionServiceImpl.exigirBandaComercial",
            "la banda comercial EN EL CORE y no solo en la anotacion (D-S0-17 filas 5 y 7): "
                    + "decidir y cerrar un encargo son operacion, y el gobierno del tenant no las "
                    + "hereda. Va despues de cargarConAcceso para conservar el 404",

            "CaptacionServiceImpl.puedeReasignar",
            "BROKER que supervisa hoy al agente que lo lleva, o gobierno del tenant. Es el mismo "
                    + "predicado que publica capacidades.puedeReasignar, para que la lista de "
                    + "destinos y el boton no puedan discrepar",

            "PublicacionServiceImpl.exigirEncargoPropio",
            "resuelve el encargo del anuncio y le exige exigirEdicionDelEncargo: llegar por el id "
                    + "de la publicacion no puede ser una puerta mas barata que llegar por el del "
                    + "encargo, porque las dos escriben la misma serie",

            "ProspeccionServiceImpl.cargarEnProceso",
            "la prospeccion tiene que ser del actor y estar en un estado que admita el paso. Sin "
                    + "alcance de broker: el trabajo previo al encargo es del agente",

            "ProspeccionServiceImpl.cargarConAcceso",
            "la prospeccion dentro del alcance del actor (lectura), con 404 fuera del tenant",

            "VisitaServiceImpl.cargarConAcceso",
            "la visita dentro del alcance: el AGENTE la suya, el BROKER las de sus supervisados, "
                    + "el TENANT_ADMIN su tenant",

            "InteraccionServiceImpl.cargarConAcceso",
            "la interaccion dentro del alcance del actor",

            "OportunidadServiceImpl.cargarConAcceso",
            "la oportunidad dentro del alcance del actor",

            "OportunidadServiceImpl.registrar",
            "la regla vive INLINE en este metodo y hay que decirlo: la captacion tiene que estar "
                    + "en la lista del AGENTE que registra -sin alcance de broker-, y una ajena "
                    + "responde igual que una inexistente",

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
}
