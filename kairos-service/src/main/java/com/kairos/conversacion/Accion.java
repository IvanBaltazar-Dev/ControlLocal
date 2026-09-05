package com.kairos.conversacion;

import com.kairos.herramientas.Herramienta;
import com.kairos.herramientas.Herramientas;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * <b>Lo que KAIROS sabe pedirle a BROX.</b>
 *
 * <h2>Que declara este enum, y que NO declara</h2>
 * Declara <b>que sabe decir</b> KAIROS: las intenciones que puede reconocer en
 * una frase y traducir a llamadas. Nada mas.
 *
 * <p>No declara quien puede pedirlas. Eso lo decide BROX con la sesion de la
 * persona, y lo publica —cuando exista {@code GET /capacidades}— en cada turno.
 * Una version anterior de esta clase llevaba la autonomia dentro y era
 * exactamente el error de tener la regla en el sitio donde nadie la audita.
 *
 * <h2>Una intencion no es una herramienta</h2>
 * <pre>
 *   intencion      lo que la persona quiere      ← este enum
 *   herramienta    lo que KAIROS invoca          ← com.kairos.herramientas
 *   caso de uso    lo que el dominio ya sabe     ← metodo + ruta del Core
 * </pre>
 * No son uno a uno, y forzarlo habria escondido la asimetria que mas importa:
 * <b>{@link #REGISTRAR_PROPIEDAD} usa TRES herramientas</b> —abrir, responder y
 * confirmar—, y solo la tercera crea algo. {@link #herramienta()} devuelve la
 * primera, que es la que decide si la conversacion puede siquiera empezar; las
 * otras las nombra el adaptador en el paso en que las usa.
 *
 * <p>{@link #capacidad()} es el enganche con el catalogo de BROX, y desde este
 * paquete devuelve <b>el nombre de D-K-1 §3</b>. Antes devolvia nombres propios
 * en camelCase que no estaban en ningun documento: dos vocabularios para lo
 * mismo, y ninguno de los dos publicado —{@code /capacidades} no existe—, asi
 * que unificarlos no rompio ningun contrato porque no habia ninguno.
 */
public enum Accion {

    /** La ficha por el modelo universal: titulares, atributos y encargos. */
    CONSULTAR_PROPIEDAD(Herramientas.ESTADO_DE_PROPIEDAD),

    /** El alta conversacional, sobre el motor de captura de BROX. */
    REGISTRAR_PROPIEDAD(Herramientas.ABRIR_SESION_CAPTURA),

    /** Retomar lo que quedo a medias, en este canal o en otro. */
    CONTINUAR_BORRADOR(Herramientas.CAPTURAS_EN_CURSO),

    CONSULTAR_CLIENTE(Herramientas.BUSCAR_PERSONA),

    REGISTRAR_PROPIETARIO(Herramientas.REGISTRAR_PROPIETARIO),

    REGISTRAR_INTERACCION(Herramientas.REGISTRAR_INTERACCION),

    /** Que encaja con que. Solo lectura. */
    BUSCAR_COINCIDENCIAS(Herramientas.BUSCAR_COINCIDENCIAS),

    /**
     * Por que encaja: criterios cumplidos, incumplidos y por confirmar.
     *
     * <p>Es la que hace a KAIROS util de verdad. Sin ella, la respuesta es un
     * porcentaje sin causa; con ella, «cumple 4 de 5 y falta confirmar la
     * potencia electrica», que es una frase sobre la que se puede actuar.
     */
    EXPLICAR_MATCH(Herramientas.EXPLICAR_MATCH),

    /** El foco del Inicio: lo que hay que resolver hoy, con su politica de despacho. */
    QUE_RESOLVER_HOY(Herramientas.QUE_RESOLVER_HOY);

    private final Herramienta herramienta;

    /**
     * Una intencion que apunte a una herramienta <b>declarada y sin
     * implementar</b> no arranca.
     *
     * <p>Es un error de programacion, no un caso de uso: significa que KAIROS
     * dice saber reconocer algo que despues no puede ejecutar, y la persona se
     * enteraria en mitad de la conversacion. Fallar al cargar la clase lo
     * convierte en algo que se ve al desplegar, que es cuando todavia no hay
     * nadie esperando una respuesta.
     */
    Accion(Herramienta herramienta) {
        if (!herramienta.implementada()) {
            throw new IllegalStateException(
                    "La accion " + name() + " apunta a la herramienta \"" + herramienta.nombre()
                            + "\", que esta declarada y sin implementar.");
        }
        this.herramienta = herramienta;
    }

    /** La primera herramienta que la intencion invoca. Puede no ser la unica. */
    public Herramienta herramienta() {
        return herramienta;
    }

    /** El nombre con el que BROX la publicaria en su catalogo de capacidades. */
    public String capacidad() {
        return herramienta.nombre();
    }

    public static Optional<Accion> de(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        String limpio = nombre.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(a -> a.name().equals(limpio)).findFirst();
    }
}
