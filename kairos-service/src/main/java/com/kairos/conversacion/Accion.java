package com.kairos.conversacion;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * <b>Lo que KAIROS sabe pedirle a BROX.</b>
 *
 * <h2>Que declara este enum, y que NO declara</h2>
 * Declara <b>que sabe decir</b> KAIROS: seis intenciones que puede reconocer en
 * una frase y traducir a llamadas. Nada mas.
 *
 * <p>No declara quien puede pedirlas ni que se confirma antes de ejecutar. Eso
 * viene de {@code GET /capacidades} en cada sesion, porque son <b>reglas del
 * negocio</b> y no preferencias del asistente: que una publicacion la confirme
 * una persona lo decide BROX, y el dia que cambie de opinion, KAIROS obedece
 * sin desplegarse. Una version anterior de esta clase llevaba la autonomia
 * dentro y era exactamente el error de tener la regla en el sitio donde nadie
 * la audita.
 *
 * <p>{@link #capacidad} es el enganche: el nombre con el que BROX conoce esa
 * operacion en su catalogo de capacidades.
 */
public enum Accion {

    /** La ficha por el modelo universal: titulares, atributos y encargos. */
    CONSULTAR_PROPIEDAD("consultarPropiedad"),

    /** El alta conversacional, sobre el motor de captura de BROX. */
    REGISTRAR_PROPIEDAD("iniciarCapturaPropiedad"),

    /** Retomar lo que quedo a medias, en este canal o en otro. */
    CONTINUAR_BORRADOR("continuarCaptura"),

    CONSULTAR_CLIENTE("buscarPersona"),

    REGISTRAR_PROPIETARIO("registrarPropietario"),

    REGISTRAR_INTERACCION("registrarInteraccion");

    private final String capacidad;

    Accion(String capacidad) {
        this.capacidad = capacidad;
    }

    /** El nombre con el que BROX la publica en su catalogo de capacidades. */
    public String capacidad() {
        return capacidad;
    }

    public static Optional<Accion> de(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        String limpio = nombre.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(a -> a.name().equals(limpio)).findFirst();
    }
}
