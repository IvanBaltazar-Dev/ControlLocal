package com.controllocal.domain.inmueble;

import java.util.Locale;

/**
 * <b>Que clase de valor es una clave gobernada, y donde vive.</b>
 *
 * <h2>Por que un enum y no el String que habia</h2>
 * Hasta el Corte 0B, {@code catalogo_atributo.tipo_dato} viajaba por Java como
 * una cadena suelta, y habia <b>tres</b> {@code switch} distintos sobre ella en
 * el mismo fichero -- convertir, validar y actualizar -- ninguno de los tres
 * exhaustivo y al menos uno con un {@code default} que no hacia nada. Arreglar
 * dos de los tres dejaba la puerta abierta por la que quedara: el motor
 * aceptaba un valor que el trigger rechazaba a mitad de la transaccion.
 *
 * <p>Con un enum, anadir un noveno tipo <b>no compila</b> hasta que los tres
 * sitios digan que hacer con el. Es la misma proteccion que V72 puso del lado
 * de PostgreSQL con su {@code CASE ... ELSE RAISE}: alli el fallo dejo de ser
 * silencioso, y aqui deja de poder escribirse.
 *
 * <h2>La regla que codifica</h2>
 * Un tipo de dato dice tres cosas, y las tres se preguntan aqui en vez de
 * deducirse en cada consumidor:
 *
 * <pre>
 *   donde vive el escalar   -&gt; {@link #columna()}
 *   lleva moneda            -&gt; {@link #llevaMoneda()}
 *   es una coleccion        -&gt; {@link #esMultivalor()}
 * </pre>
 *
 * <p>Nadie mas debe volver a preguntar «si es IMPORTE entonces...»: eso seria
 * la matriz «clave -&gt; comportamiento» reapareciendo por la puerta del tipo.
 */
public enum TipoDato {

    /** Texto libre, acotado por {@code longitud_maxima} si la clave la declara. */
    TEXTO(Columna.TEXTO, false, false),

    /** Numero sin decimales: dormitorios, ascensores, posiciones de trabajo. */
    ENTERO(Columna.NUMERO, false, false),

    /** Numero con decimales: areas, alturas, frente. */
    DECIMAL(Columna.NUMERO, false, false),

    /**
     * Dinero. Es un tipo propio y no un DECIMAL con una clave de moneda al
     * lado: un numero sin moneda no es un importe, y separarlos dejaria que
     * retirar el monto abandonara una moneda huerfana que nada detecta.
     */
    IMPORTE(Columna.NUMERO, true, false),

    /** Si o no. Nunca «no se»: eso es la ausencia de la fila. */
    BOOLEANO(Columna.BOOLEANO, false, false),

    /** Una fecha del calendario, sin hora: disponible desde, entrega. */
    FECHA(Columna.FECHA, false, false),

    /** Un valor de un vocabulario cerrado que declara el catalogo. */
    LISTA(Columna.TEXTO, false, false),

    /**
     * Varios valores del mismo vocabulario. Su fila en {@code atributo_propiedad}
     * es un <b>ancla</b> sin escalar -- dice «esta clave esta respondida»-- y los
     * valores cuelgan de ella en {@code atributo_propiedad_opcion}.
     */
    LISTA_MULTIPLE(Columna.NINGUNA, false, true);

    /** En que columna de {@code atributo_propiedad} se guarda el escalar. */
    public enum Columna { TEXTO, NUMERO, BOOLEANO, FECHA, NINGUNA }

    private final Columna columna;
    private final boolean llevaMoneda;
    private final boolean multivalor;

    TipoDato(Columna columna, boolean llevaMoneda, boolean multivalor) {
        this.columna = columna;
        this.llevaMoneda = llevaMoneda;
        this.multivalor = multivalor;
    }

    /**
     * El tipo declarado, o un fallo con nombre.
     *
     * <p><b>No tiene valor por defecto y no lo tendra.</b> Caer a TEXTO ante un
     * tipo desconocido convertiria un error de catalogo en un dato guardado
     * como texto que nadie podria volver a comparar -- que es exactamente el
     * defecto que 0B viene a cerrar en {@code servicios_disponibles}.
     */
    public static TipoDato desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Una clave del catalogo sin tipo de dato no se puede interpretar.");
        }
        String limpio = valor.trim().toUpperCase(Locale.ROOT);
        for (TipoDato tipo : values()) {
            if (tipo.name().equals(limpio)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
                "Tipo de dato desconocido: \"" + valor + "\". Son " + resumen() + ".");
    }

    /** Si la cadena nombra un tipo conocido. Lo usan los gates del catalogo. */
    public static boolean conocido(String valor) {
        if (valor == null) {
            return false;
        }
        String limpio = valor.trim().toUpperCase(Locale.ROOT);
        for (TipoDato tipo : values()) {
            if (tipo.name().equals(limpio)) {
                return true;
            }
        }
        return false;
    }

    /** Los ocho, en una frase, para que un mensaje de error sirva de algo. */
    public static String resumen() {
        StringBuilder frase = new StringBuilder();
        TipoDato[] todos = values();
        for (int i = 0; i < todos.length; i++) {
            frase.append(todos[i].name());
            if (i < todos.length - 2) {
                frase.append(", ");
            } else if (i == todos.length - 2) {
                frase.append(" y ");
            }
        }
        return frase.toString();
    }

    /** El codigo tal como lo guarda la base. */
    public String codigo() {
        return name();
    }

    public Columna columna() {
        return columna;
    }

    /**
     * Si el valor va acompanado de su moneda. Solo IMPORTE, y por eso la
     * pregunta se hace aqui y no con un {@code equals(IMPORTE)} repartido.
     */
    public boolean llevaMoneda() {
        return llevaMoneda;
    }

    /**
     * Si la clave admite varios valores. Un multivalor NO guarda escalar en su
     * fila: romperia {@code ck_atributo_un_valor} por el otro lado.
     */
    public boolean esMultivalor() {
        return multivalor;
    }

    /**
     * Si el valor sale de un vocabulario cerrado. Las dos listas lo hacen, y es
     * lo que las separa de un texto libre: sin vocabulario, dos propiedades que
     * dicen lo mismo con distintas palabras dejan de poder compararse.
     */
    public boolean tieneVocabulario() {
        return this == LISTA || this == LISTA_MULTIPLE;
    }

    /** Si el valor es un numero, sea cual sea su forma. */
    public boolean esNumerico() {
        return columna == Columna.NUMERO;
    }
}
