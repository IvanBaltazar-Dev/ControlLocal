package com.kairos.conversacion;

import com.kairos.brox.ClienteBrox;
import com.kairos.brox.ClienteBrox.Capacidad;
import com.kairos.brox.ClienteBrox.EstadoCaptura;
import com.kairos.brox.SesionBrox;
import com.kairos.brox.Traza;
import com.kairos.brox.Vocabulario;
import com.kairos.interpretacion.Interprete;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * El adaptador. <b>Ninguna regla inmobiliaria vive aqui.</b>
 *
 * <h2>Como leer esta clase</h2>
 * Cada accion es el mismo parrafo: mirar si BROX ofrece esa capacidad a esta
 * sesion, resolver lo que la frase nombra con una lectura que ya existe, y
 * llamar a la operacion. Si alguna vez una de ellas empieza a decidir algo —que
 * un terreno no lleva dormitorios, cuando un encargo esta vivo, si un precio es
 * razonable—, esa decision esta en el sitio equivocado y hay que devolverla a
 * BROX.
 *
 * <h2>La autonomia no esta escrita aqui</h2>
 * Se lee de {@code GET /capacidades} en cada turno. Tres niveles: {@code AUTO}
 * se ejecuta, {@code CONFIRMA} se prepara y espera a una persona, {@code HUMANO}
 * no lo hace un agente en ningun caso. El dia que BROX reclasifique una
 * operacion, este codigo obedece sin cambiar una linea — que es exactamente el
 * motivo de no haberlo puesto en un prompt.
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
        Traza traza = new Traza(Traza.CANAL_WHATSAPP, nombreDelAgente, modelo, modeloVersion,
                turno.conversacionId(), turno.turnoId(), turno.mensajeId(), turno.texto());

        Interprete.Lectura lectura = interprete.leer(turno.texto(), sesion);
        if (!lectura.hayAccion()) {
            return new Respuesta(turno.conversacionId(), turno.turnoId(), null,
                    Desenlace.NO_ENTENDIDO,
                    Interprete.Lectura.SIN_TEXTO.equals(lectura.motivo())
                            ? SIN_TEXTO : SIN_ACCION_RECONOCIDA,
                    Map.of(), List.of(), List.of(), List.of(), false, Resultado.NADA);
        }

        Accion accion = lectura.accion();

        // Lo que puede y con que autonomia lo dice BROX, no este codigo.
        Optional<Capacidad> capacidad = brox.capacidades(sesion).stream()
                .filter(c -> c.nombre().equals(accion.capacidad()))
                .findFirst();
        if (capacidad.isEmpty()) {
            // No es un fallo: es que esta sesion no tiene esa capacidad. Se dice,
            // en vez de intentarlo y recibir un 403 que habria que traducir.
            return respuesta(turno, accion, Desenlace.SIN_PERMISO, CAPACIDAD_NO_DISPONIBLE,
                    lectura, List.of(), List.of(), false, Resultado.NADA);
        }
        Capacidad declarada = capacidad.get();
        if (!declarada.laPuedePedirUnAgente()) {
            return respuesta(turno, accion, Desenlace.SOLO_HUMANO, RESERVADA_A_UNA_PERSONA,
                    lectura, List.of(), List.of(), false, Resultado.NADA);
        }

        return switch (accion) {
            case CONSULTAR_PROPIEDAD -> consultarPropiedad(turno, lectura, declarada, sesion);
            case CONSULTAR_CLIENTE -> consultarCliente(turno, lectura, declarada, sesion);
            case CONTINUAR_BORRADOR -> continuarBorrador(turno, lectura, declarada, sesion);
            case REGISTRAR_PROPIEDAD ->
                    registrarPropiedad(turno, lectura, declarada, traza, sesion);
            case REGISTRAR_PROPIETARIO ->
                    registrarPropietario(turno, lectura, declarada, traza, sesion);
            case REGISTRAR_INTERACCION ->
                    registrarInteraccion(turno, lectura, declarada, traza, sesion);
        };
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
                                         Capacidad capacidad, SesionBrox sesion) {
        String texto = lectura.datos().get("texto");
        if (texto == null || texto.isBlank()) {
            return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("texto"), List.of(), capacidad.laConfirmaUnaPersona(),
                    Resultado.NADA);
        }
        List<ClienteBrox.Coincidencia> encontradas = brox.buscarPropiedades(sesion, texto);

        if (encontradas.isEmpty()) {
            return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.RESPONDIDO,
                    SIN_COINCIDENCIAS, lectura, List.of(), List.of(), false,
                    new Resultado(null, List.of(), null, null, null, null, null));
        }
        if (encontradas.size() > 1) {
            // Varias: se devuelven para que la persona elija. Elegir por ella
            // —"la primera", "la mas reciente"— es inventar un criterio que
            // nadie declaro.
            return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.RESPONDIDO,
                    VARIAS_COINCIDENCIAS, lectura, List.of(), List.of(), false,
                    new Resultado(null, encontradas, null, null, null, null, null));
        }
        Map<String, Object> ficha = brox.propiedad(sesion, encontradas.get(0).id());
        return respuesta(turno, Accion.CONSULTAR_PROPIEDAD, Desenlace.RESPONDIDO, null, lectura,
                List.of(), List.of(), false,
                new Resultado(ficha, encontradas, null, null, null, null, null));
    }

    private Respuesta consultarCliente(Turno turno, Interprete.Lectura lectura,
                                       Capacidad capacidad, SesionBrox sesion) {
        List<ClienteBrox.Persona> encontrados =
                brox.buscarClientes(sesion, lectura.datos().get("texto"));
        return respuesta(turno, Accion.CONSULTAR_CLIENTE, Desenlace.RESPONDIDO,
                encontrados.isEmpty() ? SIN_COINCIDENCIAS : null, lectura, List.of(), List.of(),
                false, new Resultado(null, null, encontrados, null, null, null, null));
    }

    // ==================================================================
    // Captura: el motor es de BROX
    // ==================================================================

    /**
     * Retomar. Con codigo, ese; sin codigo y con uno solo en curso, ese; con
     * varios, se devuelven todos <b>sin elegir</b>.
     */
    private Respuesta continuarBorrador(Turno turno, Interprete.Lectura lectura,
                                        Capacidad capacidad, SesionBrox sesion) {
        List<EstadoCaptura> enCurso = brox.capturasEnCurso(sesion);
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
        return conEstadoDeCaptura(turno, Accion.CONTINUAR_BORRADOR, lectura, capacidad,
                brox.captura(sesion, candidatos.get(0).idBorrador()));
    }

    /**
     * El alta conversacional.
     *
     * <p>Tres pasos y ninguno se salta: se anota lo que la frase trajo —lo que
     * no escribe nada del negocio—, BROX dice que falta, y cuando no falta nada
     * se mira la autonomia declarada. Con {@code CONFIRMA}, el turno propone y
     * ejecutar exige otro turno con {@code confirmado}: una persona diciendo
     * que si.
     */
    private Respuesta registrarPropiedad(Turno turno, Interprete.Lectura lectura,
                                         Capacidad capacidad, Traza traza, SesionBrox sesion) {
        EstadoCaptura estado = brox.avanzarCaptura(sesion, Vocabulario.INTENCION_REGISTRAR_PROPIEDAD,
                turno.idBorrador(), lectura.datos(), traza);

        if (!estado.listoParaEjecutar()) {
            return conEstadoDeCaptura(turno, Accion.REGISTRAR_PROPIEDAD, lectura, capacidad, estado);
        }
        if (capacidad.laConfirmaUnaPersona() && !turno.confirmado()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIEDAD, Desenlace.PROPUESTA,
                    CONFIRMA_UNA_PERSONA, lectura, List.of(), List.of(), true,
                    Resultado.NADA.conCaptura(estado));
        }
        ClienteBrox.Ejecucion ejecucion = brox.ejecutarCaptura(sesion, estado.idBorrador(),
                traza.claveIdempotencia(), traza);
        return respuesta(turno, Accion.REGISTRAR_PROPIEDAD, Desenlace.EJECUTADO, null, lectura,
                List.of(), List.of(), capacidad.laConfirmaUnaPersona(),
                new Resultado(null, null, null, null, null,
                        brox.captura(sesion, estado.idBorrador()), ejecucion));
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
                                           Capacidad capacidad, Traza traza, SesionBrox sesion) {
        Map<String, String> datos = lectura.datos();
        String documento = datos.get("numeroDocumento");
        String nombre = datos.get("nombre");

        if (documento == null || documento.isBlank()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("numeroDocumento"), List.of(),
                    capacidad.laConfirmaUnaPersona(), Resultado.NADA);
        }
        List<ClienteBrox.Persona> yaEstan = brox.buscarPropietarios(sesion, documento);
        if (!yaEstan.isEmpty()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.RESPONDIDO, YA_EXISTE,
                    lectura, List.of(), List.of(), false,
                    new Resultado(null, null, yaEstan, yaEstan.get(0), null, null, null));
        }
        if (nombre == null || nombre.isBlank()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("nombre"), List.of(), capacidad.laConfirmaUnaPersona(),
                    Resultado.NADA);
        }
        if (capacidad.laConfirmaUnaPersona() && !turno.confirmado()) {
            return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.PROPUESTA,
                    CONFIRMA_UNA_PERSONA, lectura, List.of(), List.of(), true, Resultado.NADA);
        }
        Map<String, String> alta = new HashMap<>(datos);
        alta.putIfAbsent("tipoPersona", documento.length() == 11 ? "J" : "N");
        alta.putIfAbsent("tipoDocumento", documento.length() == 11 ? "RUC" : "DNI");

        ClienteBrox.Persona creado = brox.registrarPropietario(sesion, alta, traza);
        return respuesta(turno, Accion.REGISTRAR_PROPIETARIO, Desenlace.EJECUTADO, null, lectura,
                List.of(), List.of(), capacidad.laConfirmaUnaPersona(),
                new Resultado(null, null, null, creado, null, null, null));
    }

    /**
     * La bitacora de un contacto.
     *
     * <p>Dos datos no se adivinan nunca. <b>De que cuelga</b>, porque una nota
     * sin expediente no la vuelve a encontrar nadie; y <b>como termino</b>,
     * porque su vocabulario depende del contexto y lo decide BROX. Cuando el
     * resultado falta, la pregunta viaja con la lista exacta que ese contexto
     * admite — pedida a BROX, no escrita aqui.
     */
    private Respuesta registrarInteraccion(Turno turno, Interprete.Lectura lectura,
                                           Capacidad capacidad, Traza traza, SesionBrox sesion) {
        Map<String, String> datos = lectura.datos();
        String contexto = datos.get("contexto");
        String idEntidad = datos.get("idEntidad");
        if (contexto == null || idEntidad == null) {
            return respuesta(turno, Accion.REGISTRAR_INTERACCION, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("contexto", "idEntidad"), Vocabulario.CONTEXTOS_INTERACCION,
                    capacidad.laConfirmaUnaPersona(), Resultado.NADA);
        }
        List<String> admitidos = brox.resultadosDeInteraccion(sesion, contexto);
        String resultado = datos.get("resultado");
        if (resultado == null || !admitidos.contains(resultado)) {
            return respuesta(turno, Accion.REGISTRAR_INTERACCION, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("resultado"), admitidos, capacidad.laConfirmaUnaPersona(),
                    Resultado.NADA);
        }
        String canal = datos.get("canalContacto");
        if (canal == null) {
            return respuesta(turno, Accion.REGISTRAR_INTERACCION, Desenlace.PREGUNTA, FALTAN_DATOS,
                    lectura, List.of("canalContacto"),
                    List.copyOf(Vocabulario.CANALES_INTERACCION.keySet()),
                    capacidad.laConfirmaUnaPersona(), Resultado.NADA);
        }
        if (capacidad.laConfirmaUnaPersona() && !turno.confirmado()) {
            return respuesta(turno, Accion.REGISTRAR_INTERACCION, Desenlace.PROPUESTA,
                    CONFIRMA_UNA_PERSONA, lectura, List.of(), List.of(), true, Resultado.NADA);
        }
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("contexto", contexto);
        cuerpo.put("idEntidad", Long.parseLong(idEntidad));
        cuerpo.put("canalContacto", canal);
        cuerpo.put("resultado", resultado);
        cuerpo.put("observaciones", datos.get("observaciones"));

        ClienteBrox.Interaccion ficha = brox.registrarInteraccion(sesion, cuerpo, traza);
        return respuesta(turno, Accion.REGISTRAR_INTERACCION, Desenlace.EJECUTADO, null, lectura,
                List.of(), List.of(), capacidad.laConfirmaUnaPersona(),
                new Resultado(null, null, null, null, ficha, null, null));
    }

    // ==================================================================

    /** El turno que termina enseñando el estado de una captura, tal como BROX lo dio. */
    private static Respuesta conEstadoDeCaptura(Turno turno, Accion accion,
                                                Interprete.Lectura lectura, Capacidad capacidad,
                                                EstadoCaptura estado) {
        List<String> opciones = estado.siguiente() == null || estado.siguiente().opciones() == null
                ? List.of() : estado.siguiente().opciones();
        boolean completo = estado.faltante() == null || estado.faltante().isEmpty();
        return new Respuesta(turno.conversacionId(), turno.turnoId(), accion,
                completo ? Desenlace.PROPUESTA : Desenlace.PREGUNTA,
                completo ? CONFIRMA_UNA_PERSONA : FALTAN_DATOS,
                lectura.datos() == null ? Map.of() : Map.copyOf(lectura.datos()),
                lectura.noEntendido(), estado.faltante() == null ? List.of() : estado.faltante(),
                opciones, capacidad.laConfirmaUnaPersona(), Resultado.NADA.conCaptura(estado));
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
