package com.kairos.conversacion;

import com.kairos.brox.ClienteBrox;
import com.kairos.brox.ClienteBrox.Capacidad;
import com.kairos.brox.ClienteBrox.CoincidenciaDeCartera;
import com.kairos.brox.ClienteBrox.CoincidenciasDeCartera;
import com.kairos.brox.ClienteBrox.EstadoCaptura;
import com.kairos.brox.ClienteBrox.OrigenDeCoincidencias;
import com.kairos.brox.SesionBrox;
import com.kairos.brox.Traza;
import com.kairos.brox.Vocabulario;
import com.kairos.herramientas.Confirmacion;
import com.kairos.herramientas.Herramienta;
import com.kairos.herramientas.Herramientas;
import com.kairos.interpretacion.Interprete;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * El adaptador. <b>Ninguna regla inmobiliaria vive aqui.</b>
 *
 * <h2>Como leer esta clase</h2>
 * Cada accion es el mismo parrafo: mirar que herramienta del catalogo se va a
 * invocar, comprobar que BROX se la ofrece a esta sesion, resolver lo que la
 * frase nombra con una lectura que ya existe, y llamar a la operacion. Si
 * alguna vez una de ellas empieza a decidir algo —que un terreno no lleva
 * dormitorios, cuando un encargo esta vivo, si un precio es razonable—, esa
 * decision esta en el sitio equivocado y hay que devolverla a BROX.
 *
 * <h2>La confirmacion se compone, y siempre hacia el lado estricto</h2>
 * <pre>
 *   catalogo de KAIROS   suelo: lo sensible se confirma SIEMPRE   (D-K-1 §3.3)
 *          +
 *   capacidad de BROX    puede exigir MAS, nunca menos            (D-K-1 §4)
 * </pre>
 * Una capacidad declarada {@code AUTO} <b>no</b> apaga la confirmacion de
 * {@code confirmar_captura}: si pudiera, bastaria un error de configuracion del
 * Core para que una conversacion creara propiedades, titularidades y encargos
 * sin que nadie dijera que si. Al reves si: BROX puede pedir confirmacion para
 * algo que el catalogo considera reversible, y KAIROS la pide.
 *
 * <h2>Que pasa cuando BROX no publica capacidades</h2>
 * {@code GET /capacidades} todavia no existe. Un catalogo <b>vacio</b> se lee
 * como «no declarado» y manda el catalogo de KAIROS, con el Core decidiendo por
 * 403 como siempre. Un catalogo <b>publicado</b> que no traiga la herramienta se
 * lee como «esta sesion no la tiene», y entonces no se intenta. Las dos cosas
 * son distintas y se distinguen: tratarlas igual dejaria a KAIROS sin poder
 * hacer nada, o le daria permiso donde el Core dijo que no.
 */
@Service
public class KairosImpl implements Kairos {

    private final Interprete interprete;
    private final ClienteBrox brox;
    private final String nombreDelAgente;
    private final String modelo;
    private final String modeloVersion;

    public KairosImpl(Interprete interprete, ClienteBrox brox,
                      @Value("${kairos.agente:KAIROS}") String nombreDelAgente,
                      @Value("${kairos.modelo:determinista}") String modelo,
                      @Value("${kairos.modelo-version:0.1.0}") String modeloVersion) {
        this.interprete = interprete;
        this.brox = brox;
        this.nombreDelAgente = nombreDelAgente;
        this.modelo = modelo;
        this.modeloVersion = modeloVersion;
    }

    // ==================================================================

    @Override
    public Respuesta turno(Turno turno, SesionBrox sesion) {
        // La traza se construye ANTES de mirar la frase, y falla si le falta la
        // conversacion o el turno. Es deliberado: no se empieza a trabajar sobre
        // algo que despues no se va a poder atribuir.
        Traza traza = new Traza(turno.canal(), nombreDelAgente, modelo, modeloVersion,
                turno.conversacionId(), turno.turnoId(), turno.mensajeId(), turno.texto(), null);

        Interprete.Lectura lectura = interprete.leer(turno.texto(), sesion, traza);
        if (!lectura.hayAccion()) {
            return new Respuesta(turno.conversacionId(), turno.turnoId(), null,
                    Desenlace.NO_ENTENDIDO,
                    Interprete.Lectura.SIN_TEXTO.equals(lectura.motivo())
                            ? SIN_TEXTO : SIN_ACCION_RECONOCIDA,
                    Map.of(), List.of(), List.of(), List.of(), false, Resultado.NADA);
        }

        Accion accion = lectura.accion();
        Herramienta puerta = puertaDe(accion, turno);

        // Lo que puede lo dice BROX, no este codigo.
        Permisos permisos = new Permisos(brox.capacidades(sesion,
                traza.invocando(Herramientas.CONSULTAR_CAPACIDADES.nombre())));
        Optional<Respuesta> rechazo = permisos.rechazo(turno, accion, puerta, lectura);
        if (rechazo.isPresent()) {
            return rechazo.get();
        }
        Confirmacion confirmacion = permisos.confirmacionDe(puerta);

        return switch (accion) {
            case CONSULTAR_PROPIEDAD -> consultarPropiedad(turno, lectura, sesion, traza);
            case CONSULTAR_CLIENTE -> consultarCliente(turno, lectura, sesion, traza);
            case CONTINUAR_BORRADOR -> continuarBorrador(turno, lectura, confirmacion, sesion, traza);
            case REGISTRAR_PROPIEDAD ->
                    registrarPropiedad(turno, lectura, puerta, permisos, traza, sesion);
            case REGISTRAR_PROPIETARIO ->
                    registrarPropietario(turno, lectura, confirmacion, traza, sesion);
            case REGISTRAR_INTERACCION ->
                    registrarInteraccion(turno, lectura, confirmacion, traza, sesion);
            case BUSCAR_COINCIDENCIAS -> buscarCoincidencias(turno, lectura, sesion, traza);
            case EXPLICAR_MATCH -> explicarMatch(turno, lectura, sesion, traza);
            case QUE_RESOLVER_HOY -> queResolverHoy(turno, lectura, sesion, traza);
        };
    }

    /**
     * La herramienta que este turno va a invocar primero.
     *
     * <p>Es la de la accion salvo en la captura: <b>abrir</b> y <b>responder</b>
     * son la misma ruta y lo unico que las distingue es que la segunda ya tiene
     * borrador. Se separan porque son dos herramientas del catalogo, y porque el
     * rastro tiene que poder decir cual de las dos fue.
     */
    private static Herramienta puertaDe(Accion accion, Turno turno) {
        if (accion == Accion.REGISTRAR_PROPIEDAD && turno.idBorrador() != null) {
            return Herramientas.RESPONDER_CAPTURA;
        }
        return accion.herramienta();
    }

    // ==================================================================
    // Lecturas
    // ==================================================================

    /**
     * La ficha, buscada por el texto que la frase diga.
     *
     * <p>La busqueda es la <b>misma</b> que usa el listado de cartera de la
     * pantalla: cruza codigo, direccion, distrito, rubro y nombre del
     * propietario, y respeta el alcance de la sesion. Con una consulta propia
     * habria dos criterios de "que es una coincidencia", y una lista que no
     * cuadra con la que el mismo usuario ve en su pantalla.
     */
    private Respuesta consultarPropiedad(Turno turno, Interprete.Lectura lectura,
                                         SesionBrox sesion, Traza traza) {
        String texto = lectura.datos().get("texto");
        if (texto == null || texto.isBlank()) {
            return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("texto"), List.of(), false, Resultado.NADA);
        }
        List<ClienteBrox.Coincidencia> encontradas = brox.buscarPropiedades(sesion, texto,
                traza.invocando(Herramientas.BUSCAR_PROPIEDADES.nombre()));

        if (encontradas.isEmpty()) {
            return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.RESPONDIDO,
                    SIN_COINCIDENCIAS, lectura, List.of(), List.of(), false,
                    Resultado.NADA.conCoincidencias(List.of()));
        }
        if (encontradas.size() > 1) {
            // Varias: se devuelven para que la persona elija. Elegir por ella
            // —"la primera", "la mas reciente"— es inventar un criterio que
            // nadie declaro.
            return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.RESPONDIDO,
                    VARIAS_COINCIDENCIAS, lectura, List.of(), List.of(), false,
                    Resultado.NADA.conCoincidencias(encontradas));
        }
        Map<String, Object> ficha = brox.propiedad(sesion, encontradas.get(0).id(),
                traza.invocando(Herramientas.ESTADO_DE_PROPIEDAD.nombre()));
        return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.RESPONDIDO, null, lectura,
                List.of(), List.of(), false,
                Resultado.NADA.conCoincidencias(encontradas).conPropiedad(ficha));
    }

    private Respuesta consultarCliente(Turno turno, Interprete.Lectura lectura, SesionBrox sesion,
                                       Traza traza) {
        List<ClienteBrox.Persona> encontrados = brox.buscarClientes(sesion,
                lectura.datos().get("texto"),
                traza.invocando(Herramientas.BUSCAR_PERSONA.nombre()));
        return respuesta(turno, Accion.CONSULTAR_CLIENTE, Desenlace.RESPONDIDO,
                encontrados.isEmpty() ? SIN_COINCIDENCIAS : null, lectura, List.of(), List.of(),
                false, Resultado.NADA.conPersonas(encontrados));
    }

    // ==================================================================
    // Matching: que encaja con que, y por que
    // ==================================================================

    /**
     * Las coincidencias de un origen, sin elegir ninguna.
     *
     * <p>Hacen falta las dos cosas —de que se parte y cual—, y ninguna se
     * supone: pedir «las coincidencias» sin decir de que cliente devolveria las
     * de alguien.
     */
    private Respuesta buscarCoincidencias(Turno turno, Interprete.Lectura lectura,
                                          SesionBrox sesion, Traza traza) {
        Optional<Origen> origen = origenDe(lectura);
        if (origen.isEmpty()) {
            return respuesta(turno, Accion.BUSCAR_COINCIDENCIAS, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("origen", "idOrigen"), nombresDeOrigen(), false,
                    Resultado.NADA);
        }
        CoincidenciasDeCartera encontradas = leerCoincidencias(sesion, origen.get(), traza,
                Herramientas.BUSCAR_COINCIDENCIAS);
        return respuesta(turno, Accion.BUSCAR_COINCIDENCIAS, Desenlace.RESPONDIDO,
                encontradas.items().isEmpty() ? SIN_COINCIDENCIAS : null, lectura, List.of(),
                List.of(), false, Resultado.NADA.conMatching(encontradas));
    }

    /**
     * Por que encaja UNA coincidencia.
     *
     * <p>Sale de la <b>misma</b> lectura que la busqueda —el Core devuelve la
     * explicacion dentro de cada item—, asi que explicar no vuelve a evaluar
     * nada. Si volviera a evaluarlo, habria dos puntajes para el mismo par y el
     * segundo seria el que nadie compara.
     *
     * <p>Con varias y sin decir cual, se devuelven los codigos y se pregunta.
     * Con una sola, esa: no hay eleccion que hacer por nadie.
     */
    private Respuesta explicarMatch(Turno turno, Interprete.Lectura lectura, SesionBrox sesion,
                                    Traza traza) {
        Optional<Origen> origen = origenDe(lectura);
        if (origen.isEmpty()) {
            return respuesta(turno, Accion.EXPLICAR_MATCH, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("origen", "idOrigen"), nombresDeOrigen(), false,
                    Resultado.NADA);
        }
        CoincidenciasDeCartera encontradas = leerCoincidencias(sesion, origen.get(), traza,
                Herramientas.EXPLICAR_MATCH);
        List<CoincidenciaDeCartera> items = encontradas.items();
        if (items.isEmpty()) {
            return respuesta(turno, Accion.EXPLICAR_MATCH, Desenlace.RESPONDIDO, SIN_COINCIDENCIAS,
                    lectura, List.of(), List.of(), false,
                    Resultado.NADA.conMatching(encontradas));
        }
        String cual = lectura.datos().get("codigo");
        List<CoincidenciaDeCartera> candidatas = cual == null ? items
                : items.stream().filter(c -> cual.equalsIgnoreCase(c.codigo())).toList();

        if (candidatas.isEmpty()) {
            return respuesta(turno, Accion.EXPLICAR_MATCH, Desenlace.PREGUNTA,
                    COINCIDENCIA_NO_ENCONTRADA, lectura, List.of("codigo"), codigosDe(items),
                    false, Resultado.NADA.conMatching(encontradas));
        }
        if (candidatas.size() > 1) {
            return respuesta(turno, Accion.EXPLICAR_MATCH, Desenlace.PREGUNTA,
                    VARIAS_COINCIDENCIAS, lectura, List.of("codigo"), codigosDe(items), false,
                    Resultado.NADA.conMatching(encontradas));
        }
        CoincidenciaDeCartera una = candidatas.get(0);
        return respuesta(turno, Accion.EXPLICAR_MATCH, Desenlace.RESPONDIDO, null, lectura,
                List.of(), List.of(), false,
                Resultado.NADA
                        .conMatching(new CoincidenciasDeCartera(encontradas.origen(),
                                encontradas.total(), encontradas.page(), encontradas.pageSize(),
                                List.of(una)))
                        .conExplicacion(una.explicacion()));
    }

    /** De donde se parte para buscar coincidencias: el recurso y su id. */
    private record Origen(OrigenDeCoincidencias recurso, String idOrCodigo) {
    }

    private static Optional<Origen> origenDe(Interprete.Lectura lectura) {
        String recurso = lectura.datos().get("origen");
        String id = lectura.datos().get("idOrigen");
        if (recurso == null || id == null || id.isBlank()) {
            return Optional.empty();
        }
        for (OrigenDeCoincidencias candidato : OrigenDeCoincidencias.values()) {
            if (candidato.name().equalsIgnoreCase(recurso.trim())) {
                return Optional.of(new Origen(candidato, id.trim()));
            }
        }
        return Optional.empty();
    }

    private CoincidenciasDeCartera leerCoincidencias(SesionBrox sesion, Origen origen, Traza traza,
                                                     Herramienta herramienta) {
        CoincidenciasDeCartera encontradas = brox.coincidencias(sesion, origen.recurso(),
                origen.idOrCodigo(), 1, COINCIDENCIAS_POR_TURNO,
                traza.invocando(herramienta.nombre()));
        return encontradas == null
                ? new CoincidenciasDeCartera(null, 0, 1, COINCIDENCIAS_POR_TURNO, List.of())
                : encontradas;
    }

    /**
     * Cuantas coincidencias caben en un turno de conversacion.
     *
     * <p>Seis es lo que el Core pagina por defecto en el panel de la pantalla.
     * KAIROS pide lo mismo para que la conversacion y la pantalla no ofrezcan
     * conjuntos distintos del mismo matching.
     */
    private static final int COINCIDENCIAS_POR_TURNO = 6;

    private static List<String> nombresDeOrigen() {
        return java.util.Arrays.stream(OrigenDeCoincidencias.values())
                .map(Enum::name)
                .toList();
    }

    private static List<String> codigosDe(List<CoincidenciaDeCartera> items) {
        return items.stream()
                .map(CoincidenciaDeCartera::codigo)
                .filter(codigo -> codigo != null && !codigo.isBlank())
                .toList();
    }

    // ==================================================================
    // El foco del Inicio
    // ==================================================================

    /**
     * Que hay que resolver hoy, con el alcance de quien pregunta.
     *
     * <p>No se filtra ni se reordena nada: la politica de despacho —que va
     * primero, que puede esperar— es del Core, y aplicarle aqui un criterio
     * propio produciria un foco que no coincide con el que esa misma persona ve
     * al abrir la pantalla.
     *
     * <p>Vacio es una respuesta, no un fallo, y se dice con su codigo: «hoy no
     * hay nada» y «no pude mirarlo» no son lo mismo.
     */
    private Respuesta queResolverHoy(Turno turno, Interprete.Lectura lectura, SesionBrox sesion,
                                     Traza traza) {
        ClienteBrox.Foco foco = brox.foco(sesion,
                traza.invocando(Herramientas.QUE_RESOLVER_HOY.nombre()));
        return respuesta(turno, Accion.QUE_RESOLVER_HOY, Desenlace.RESPONDIDO,
                foco == null || foco.vacio() ? NADA_QUE_RESOLVER : null, lectura, List.of(),
                List.of(), false, Resultado.NADA.conFoco(foco));
    }

    // ==================================================================
    // Captura: el motor es de BROX
    // ==================================================================

    /**
     * Retomar. Con codigo, ese; sin codigo y con uno solo en curso, ese; con
     * varios, se devuelven todos <b>sin elegir</b>.
     */
    private Respuesta continuarBorrador(Turno turno, Interprete.Lectura lectura,
                                        Confirmacion confirmacion, SesionBrox sesion,
                                        Traza traza) {
        Traza deLaLectura = traza.invocando(Herramientas.CAPTURAS_EN_CURSO.nombre());
        List<EstadoCaptura> enCurso = brox.capturasEnCurso(sesion, deLaLectura);
        String codigo = lectura.datos().get("codigo");

        List<EstadoCaptura> candidatos = codigo == null
                ? enCurso
                : enCurso.stream().filter(e -> codigo.equalsIgnoreCase(e.codigo())).toList();

        if (candidatos.isEmpty()) {
            return respuesta(turno, Accion.CONTINUAR_BORRADOR, Desenlace.RESPONDIDO,
                    SIN_BORRADOR_EN_CURSO, lectura, List.of(), List.of(), false, Resultado.NADA);
        }
        if (candidatos.size() > 1) {
            return respuesta(turno, Accion.CONTINUAR_BORRADOR, Desenlace.PREGUNTA,
                    VARIAS_COINCIDENCIAS, lectura, List.of("codigo"),
                    candidatos.stream().map(EstadoCaptura::codigo).toList(), false, Resultado.NADA);
        }
        return conEstadoDeCaptura(turno, Accion.CONTINUAR_BORRADOR, lectura, confirmacion,
                brox.captura(sesion, candidatos.get(0).idBorrador(), deLaLectura));
    }

    /**
     * El alta conversacional. <b>Tres herramientas, y solo la tercera crea algo.</b>
     *
     * <pre>
     *   abrir_sesion_captura   POST /captura                  no escribe nada del negocio
     *   responder_captura      POST /captura (con borrador)   tampoco
     *   confirmar_captura      POST /captura/{id}/ejecutar    crea propiedad, titularidad y encargos
     * </pre>
     *
     * <p>La tercera es sensible <b>siempre</b>, y no porque lo diga una
     * capacidad: lo dice el catalogo, y una capacidad {@code AUTO} no puede
     * apagarlo. Sin el turno de confirmacion, KAIROS responde que <b>haria</b> y
     * no ejecuta.
     */
    private Respuesta registrarPropiedad(Turno turno, Interprete.Lectura lectura,
                                         Herramienta puerta, Permisos permisos, Traza traza,
                                         SesionBrox sesion) {
        Confirmacion deLaPuerta = permisos.confirmacionDe(puerta);
        EstadoCaptura estado = brox.avanzarCaptura(sesion, Vocabulario.INTENCION_REGISTRAR_PROPIEDAD,
                turno.idBorrador(), lectura.datos(), traza.invocando(puerta.nombre()));

        if (!estado.listoParaEjecutar()) {
            return conEstadoDeCaptura(turno, Accion.REGISTRAR_PROPIEDAD, lectura, deLaPuerta,
                    estado);
        }
        // A partir de aqui la herramienta ya no es la misma: ejecutar es otra
        // cosa que abrir, y tiene su propia fila en el catalogo y en la matriz.
        Herramienta ejecutar = Herramientas.CONFIRMAR_CAPTURA;
        Optional<Respuesta> rechazo = permisos.rechazo(turno, Accion.REGISTRAR_PROPIEDAD, ejecutar,
                lectura);
        if (rechazo.isPresent()) {
            return rechazo.get();
        }
        if (permisos.confirmacionDe(ejecutar).exigeTurnoDeConfirmacion() && !turno.confirmado()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIEDAD, Desenlace.PROPUESTA,
                    CONFIRMA_UNA_PERSONA, lectura, List.of(), List.of(), true,
                    Resultado.NADA.conCaptura(estado));
        }
        Traza deLaEjecucion = traza.invocando(ejecutar.nombre());
        ClienteBrox.Ejecucion ejecucion = brox.ejecutarCaptura(sesion, estado.idBorrador(),
                deLaEjecucion.claveIdempotencia(), deLaEjecucion);
        return respuesta(turno, Accion.REGISTRAR_PROPIEDAD, Desenlace.EJECUTADO, null, lectura,
                List.of(), List.of(), true,
                Resultado.NADA
                        .conCaptura(brox.captura(sesion, estado.idBorrador(), deLaEjecucion))
                        .conEjecucion(ejecucion));
    }

    // ==================================================================
    // Escrituras
    // ==================================================================

    /**
     * Alta de propietario, <b>buscando antes de pedir</b>.
     *
     * <p>El duplicado de personas es el error mas caro de un CRM inmobiliario:
     * no lo arregla desactivar la ficha repetida, porque la historia queda
     * partida en dos y la busqueda del paso 1 de toda captacion futura devuelve
     * las dos. Por eso el documento manda: con el se busca y se devuelve el que
     * ya existe; sin el no se crea nada y se pregunta, en vez de fiarse de que
     * dos personas con el mismo apellido son la misma.
     */
    private Respuesta registrarPropietario(Turno turno, Interprete.Lectura lectura,
                                           Confirmacion confirmacion, Traza traza,
                                           SesionBrox sesion) {
        Map<String, String> datos = lectura.datos();
        String documento = datos.get("numeroDocumento");
        String nombre = datos.get("nombre");
        boolean confirma = confirmacion.exigeTurnoDeConfirmacion();

        if (documento == null || documento.isBlank()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("numeroDocumento"), List.of(), confirma, Resultado.NADA);
        }
        List<ClienteBrox.Persona> yaEstan = brox.buscarPropietarios(sesion, documento,
                traza.invocando(Herramientas.BUSCAR_PERSONA.nombre()));
        if (!yaEstan.isEmpty()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.RESPONDIDO, YA_EXISTE,
                    lectura, List.of(), List.of(), false,
                    Resultado.NADA.conPersonas(yaEstan).conPersona(yaEstan.get(0)));
        }
        if (nombre == null || nombre.isBlank()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("nombre"), List.of(), confirma, Resultado.NADA);
        }
        if (confirma && !turno.confirmado()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.PROPUESTA,
                    CONFIRMA_UNA_PERSONA, lectura, List.of(), List.of(), true, Resultado.NADA);
        }
        Map<String, String> alta = new HashMap<>(datos);
        alta.putIfAbsent("tipoPersona", documento.length() == 11 ? "J" : "N");
        alta.putIfAbsent("tipoDocumento", documento.length() == 11 ? "RUC" : "DNI");

        ClienteBrox.Persona creado = brox.registrarPropietario(sesion, alta,
                traza.invocando(Herramientas.REGISTRAR_PROPIETARIO.nombre()));
        return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.EJECUTADO, null, lectura,
                List.of(), List.of(), confirma, Resultado.NADA.conPersona(creado));
    }

    /**
     * La bitacora de un contacto.
     *
     * <p>Dos datos no se adivinan nunca. <b>De que cuelga</b>, porque una nota
     * sin expediente no la vuelve a encontrar nadie; y <b>como termino</b>,
     * porque su vocabulario depende del contexto y lo decide BROX.
     *
     * <h2>El id no se llama {@code idEntidad}</h2>
     * El cuerpo de {@code POST /interacciones} tiene cuatro campos —uno por
     * contexto— y ninguno se llama asi. KAIROS mandaba {@code idEntidad} y BROX
     * respondia «Falta el id»: el dato viajaba con un nombre que el contrato no
     * conoce, asi que <b>ninguna interaccion de KAIROS llegaba a escribirse</b>.
     * El nombre correcto sale de {@link Vocabulario#CLAVE_DE_ENTIDAD}.
     *
     * <h2>Cuando BROX no publica los resultados admitidos</h2>
     * La lista llega vacia porque el endpoint no existe, no porque no haya
     * vocabulario. Con lista vacia se manda lo que la frase dijo y <b>decide
     * BROX</b>, que es quien tiene la allow-list; lo que no se hace es
     * escribirla aqui. Con lista publicada, un resultado que ese contexto no
     * admite se vuelve a preguntar sin gastar una llamada.
     */
    private Respuesta registrarInteraccion(Turno turno, Interprete.Lectura lectura,
                                           Confirmacion confirmacion, Traza traza,
                                           SesionBrox sesion) {
        Map<String, String> datos = lectura.datos();
        String contexto = normalizado(datos.get("contexto"));
        String idEntidad = datos.get("idEntidad");
        boolean confirma = confirmacion.exigeTurnoDeConfirmacion();

        String claveDelId = contexto == null ? null : Vocabulario.CLAVE_DE_ENTIDAD.get(contexto);
        Long id = numeroDe(idEntidad);
        if (claveDelId == null || id == null) {
            return respuesta(turno, Accion.REGISTRAR_INTERACCION, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("contexto", "idEntidad"), Vocabulario.CONTEXTOS_INTERACCION,
                    confirma, Resultado.NADA);
        }
        // Es una LECTURA de vocabulario, no el alta: iba firmada con el nombre
        // de la escritura, y un rastro que llama alta a una consulta responde
        // mal a «¿que hizo KAIROS en este turno?», que es para lo unico que
        // existe.
        List<String> admitidos = brox.resultadosDeInteraccion(sesion, contexto,
                traza.invocando(Herramientas.RESULTADOS_DE_INTERACCION.nombre()));
        String resultado = datos.get("resultado");
        if (resultado == null) {
            return respuesta(turno, Accion.REGISTRAR_INTERACCION, Desenlace.PREGUNTA,
                    admitidos.isEmpty() ? VOCABULARIO_NO_PUBLICADO : FALTAN_DATOS, lectura,
                    List.of("resultado"), admitidos, confirma, Resultado.NADA);
        }
        if (!admitidos.isEmpty() && !admitidos.contains(resultado)) {
            return respuesta(turno, Accion.REGISTRAR_INTERACCION, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("resultado"), admitidos, confirma, Resultado.NADA);
        }
        String canal = datos.get("canalContacto");
        if (canal == null) {
            return respuesta(turno, Accion.REGISTRAR_INTERACCION, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("canalContacto"),
                    List.copyOf(Vocabulario.CANALES_INTERACCION.keySet()), confirma,
                    Resultado.NADA);
        }
        if (confirma && !turno.confirmado()) {
            return respuesta(turno, Accion.REGISTRAR_INTERACCION, Desenlace.PROPUESTA,
                    CONFIRMA_UNA_PERSONA, lectura, List.of(), List.of(), true, Resultado.NADA);
        }
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("contexto", contexto);
        cuerpo.put(claveDelId, id);
        cuerpo.put("canalContacto", canal);
        cuerpo.put("resultado", resultado);
        cuerpo.put("observaciones", datos.get("observaciones"));

        ClienteBrox.Interaccion ficha = brox.registrarInteraccion(sesion, cuerpo,
                traza.invocando(Herramientas.REGISTRAR_INTERACCION.nombre()));
        return respuesta(turno, Accion.REGISTRAR_INTERACCION, Desenlace.EJECUTADO, null, lectura,
                List.of(), List.of(), confirma, Resultado.NADA.conInteraccion(ficha));
    }

    // ==================================================================
    // Permisos y confirmacion
    // ==================================================================

    /**
     * Lo que BROX declara para esta sesion, y como se compone con el catalogo.
     *
     * <p>Es una clase y no tres ifs sueltos porque la composicion tiene que ser
     * <b>la misma</b> en las dos herramientas que usa una captura: si abrir
     * compusiera de una forma y ejecutar de otra, la segunda seria la que
     * concede de mas.
     */
    private record Permisos(List<Capacidad> publicadas) {

        Optional<Capacidad> de(Herramienta herramienta) {
            return publicadas.stream()
                    .filter(c -> herramienta.nombre().equals(c.nombre()))
                    .findFirst();
        }

        /** {@code true} si BROX publico su catalogo y esta herramienta no esta. */
        boolean noLaOfrece(Herramienta herramienta) {
            return !publicadas.isEmpty() && de(herramienta).isEmpty();
        }

        Confirmacion confirmacionDe(Herramienta herramienta) {
            Confirmacion delCore = de(herramienta).filter(Capacidad::laConfirmaUnaPersona)
                    .map(c -> Confirmacion.SIEMPRE)
                    .orElse(Confirmacion.NUNCA);
            return herramienta.confirmacion().mas(delCore);
        }

        /** El motivo por el que este turno no llega a llamar, si lo hay. */
        Optional<Respuesta> rechazo(Turno turno, Accion accion, Herramienta herramienta,
                                    Interprete.Lectura lectura) {
            if (noLaOfrece(herramienta)) {
                // No es un fallo: es que esta sesion no tiene esa capacidad. Se
                // dice, en vez de intentarlo y recibir un 403 que habria que
                // traducir.
                return Optional.of(respuesta(turno, accion, Desenlace.SIN_PERMISO,
                        CAPACIDAD_NO_DISPONIBLE, lectura, List.of(), List.of(), false,
                        Resultado.NADA));
            }
            if (de(herramienta).filter(c -> !c.laPuedePedirUnAgente()).isPresent()) {
                return Optional.of(respuesta(turno, accion, Desenlace.SOLO_HUMANO,
                        RESERVADA_A_UNA_PERSONA, lectura, List.of(), List.of(), false,
                        Resultado.NADA));
            }
            return Optional.empty();
        }
    }

    // ==================================================================

    private static String normalizado(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().toUpperCase(Locale.ROOT);
    }

    /** Un id que no es un numero no es un id: se pregunta, no se revienta. */
    private static Long numeroDe(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException noEsUnNumero) {
            return null;
        }
    }

    /** El turno que termina enseñando el estado de una captura, tal como BROX lo dio. */
    private static Respuesta conEstadoDeCaptura(Turno turno, Accion accion,
                                                Interprete.Lectura lectura,
                                                Confirmacion confirmacion, EstadoCaptura estado) {
        // El ROTULO es lo que se dice en voz alta; el valor es lo que se
        // devuelve. Antes eran la misma cadena porque la opcion era un String,
        // y por eso KAIROS leia codigos como "LOCAL" en una conversacion.
        List<String> opciones = estado.siguiente() == null || estado.siguiente().opciones() == null
                ? List.of()
                : estado.siguiente().opciones().stream()
                        .map(ClienteBrox.Opcion::rotulo)
                        .toList();
        boolean completo = estado.faltante() == null || estado.faltante().isEmpty();
        // Completo no es ejecutado: lo que viene despues es confirmar_captura, y
        // esa la confirma SIEMPRE una persona.
        return new Respuesta(turno.conversacionId(), turno.turnoId(), accion,
                completo ? Desenlace.PROPUESTA : Desenlace.PREGUNTA,
                completo ? CONFIRMA_UNA_PERSONA : FALTAN_DATOS,
                lectura.datos() == null ? Map.of() : Map.copyOf(lectura.datos()),
                lectura.noEntendido(), estado.faltante() == null ? List.of() : estado.faltante(),
                opciones,
                completo || confirmacion.exigeTurnoDeConfirmacion(),
                Resultado.NADA.conCaptura(estado));
    }

    private static Respuesta respuesta(Turno turno, Accion accion, Desenlace desenlace,
                                       String motivo, Interprete.Lectura lectura,
                                       List<String> falta, List<String> opciones,
                                       boolean confirmaUnaPersona, Resultado resultado) {
        return new Respuesta(turno.conversacionId(), turno.turnoId(), accion, desenlace, motivo,
                lectura.datos() == null ? Map.of() : Map.copyOf(lectura.datos()),
                lectura.noEntendido() == null ? List.of() : lectura.noEntendido(),
                falta, opciones, confirmaUnaPersona, resultado);
    }
}
