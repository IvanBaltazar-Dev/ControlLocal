package com.controllocal.domain.inmueble;

import java.util.Locale;

/**
 * <b>Cuanto hace falta un dato, y para que.</b>
 *
 * <h2>Por que tres niveles y no un booleano</h2>
 * Hasta el Corte 0B esto era {@code catalogo_atributo_tipo.requerido}, un
 * booleano que solo sabia decir «bloquea el alta». Con esa unica herramienta
 * pasa siempre lo mismo: como marcar un dato obligatorio significa impedir que
 * el corredor registre nada sin el, casi todo acaba en «no obligatorio» -- y
 * entonces se publican fichas sin ascensores, sin banos confirmados o sin
 * altura libre, que es lo que de verdad hace incomparable una cartera.
 *
 * <p>El corredor no sabe todo el inmueble en la primera conversacion, y no
 * tiene por que. Pero cuando lo <b>anuncia</b>, ya no es una nota suya: es una
 * afirmacion publica.
 *
 * <pre>
 *   ALT   sin esto no se puede ni registrar
 *   PUB   se puede registrar sin ello, pero no ANUNCIARLO
 *   OPC   ayuda a comparar; no bloquea nada
 * </pre>
 *
 * <h2>La derivacion que nadie debe volver a hacer por su cuenta</h2>
 * {@code ALT} tambien bloquea la publicacion -- si un dato impide registrar,
 * con mas razon impide anunciar-- pero {@code PUB} <b>no</b> bloquea el alta.
 * Esa asimetria es exactamente donde se rompe la regla si cada consumidor la
 * deduce: basta que el camino del alta pregunte «lo que no sea OPC» para que
 * empiece a exigir de golpe todo lo que solo debia exigir el anuncio, y el
 * gate del Corte 0A seguiria verde porque registra sus propiedades con datos
 * que hoy son opcionales.
 *
 * <p>Por eso se pregunta {@link #bloqueaAlta()} y {@link #bloqueaPublicacion()},
 * y no se compara contra un nivel.
 */
public enum Exigencia {

    /** Bloquea el alta. Sin este dato la propiedad no existe. */
    ALT("necesario para registrar"),

    /** Bloquea publicar. La propiedad puede existir incompleta; el anuncio no. */
    PUB("necesario para publicar"),

    /** No bloquea. Ayuda a comparar mejor esta propiedad. */
    OPC("ayuda a comparar");

    /** Como se le dice a una persona, sin jerga de catalogo. */
    private final String enPalabras;

    Exigencia(String enPalabras) {
        this.enPalabras = enPalabras;
    }

    /**
     * El nivel declarado, o un fallo con nombre. Sin valor por defecto: un
     * nivel que no se entiende no puede degradarse a OPC en silencio, porque
     * eso convierte una regla que bloquea en una que no hace nada.
     */
    public static Exigencia desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Una aplicabilidad sin exigencia no dice cuanto hace falta el dato.");
        }
        String limpio = valor.trim().toUpperCase(Locale.ROOT);
        for (Exigencia nivel : values()) {
            if (nivel.name().equals(limpio)) {
                return nivel;
            }
        }
        throw new IllegalArgumentException(
                "Exigencia desconocida: \"" + valor + "\". Son ALT, PUB y OPC.");
    }

    public String codigo() {
        return name();
    }

    public String enPalabras() {
        return enPalabras;
    }

    /** Solo ALT. Registrar es el primer contacto con el inmueble, no el ultimo. */
    public boolean bloqueaAlta() {
        return this == ALT;
    }

    /**
     * ALT y PUB. Lo que impide registrar impide anunciar; lo contrario no es
     * cierto, y esa es toda la diferencia entre los dos niveles.
     */
    public boolean bloqueaPublicacion() {
        return this == ALT || this == PUB;
    }
}
