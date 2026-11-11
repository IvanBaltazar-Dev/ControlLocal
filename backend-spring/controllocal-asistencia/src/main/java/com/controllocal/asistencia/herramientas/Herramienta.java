package com.controllocal.asistencia.herramientas;

/**
 * <b>Una herramienta de KAIROS: un caso de uso que ya existe, nombrado.</b>
 *
 * <h2>Los cuatro datos que declara, y por que esos</h2>
 * <table>
 *   <tr><td>{@code nombre}</td><td>el de D-K-1 §3. No se inventa uno nuevo por herramienta</td></tr>
 *   <tr><td>{@code metodo} + {@code ruta}</td><td><b>el caso de uso del Core</b>. Si esto esta vacio, no hay herramienta: hay un camino propio a la base</td></tr>
 *   <tr><td>{@code permiso}</td><td>la celda de la matriz operacion→rol de esa ruta, <b>copiada</b>, no interpretada</td></tr>
 *   <tr><td>{@code confirmacion}</td><td>el suelo de {@link Confirmacion}</td></tr>
 * </table>
 *
 * <h2>{@code permiso} no autoriza nada</h2>
 * Es una <b>declaracion</b>, y por eso vale la celda literal de la matriz
 * («TODOS», «AGENTE», «BROKER»). Quien decide es el Core con el token de la
 * persona: KAIROS recibe el mismo 403 que recibiria el navegador. Si esta
 * cadena empezara a decidir algo, seria una segunda politica de autorizacion —
 * y la segunda es la que nadie audita.
 *
 * <h2>{@code estado} se declara, no se deduce</h2>
 * Hay herramientas de D-K-1 §3.3 que estan aqui <b>sin implementar</b>: se
 * declaran con su caso de uso y su confirmacion para que el dia que se
 * implementen no haya que decidir otra vez si publicar sale al exterior. Una
 * herramienta declarada y no implementada no se puede invocar; que no se pueda
 * lo comprueba {@code HerramientasTest}.
 *
 * <p>Y hay dos que estan al reves: <b>KAIROS las invoca y el Core no las
 * sirve</b>. Se declaran igual, porque una llamada que sale por el cable y
 * recibe un 404 es una llamada que existe, y omitirla del catalogo la volveria
 * invisible justo en el sitio donde se mira para saber que hace KAIROS.
 *
 * @param nombre       identificador estable, el de D-K-1 §3
 * @param metodo       el verbo HTTP del caso de uso
 * @param ruta         la ruta del Core, tal como la publica la matriz
 * @param permiso      la celda de la matriz para esa ruta
 * @param confirmacion el suelo de confirmacion humana
 * @param estado       de que lado esta lo que falta, si falta algo
 */
public record Herramienta(String nombre, String metodo, String ruta, String permiso,
                          Confirmacion confirmacion, Estado estado) {

    /**
     * <b>Quien tiene que mover ficha para que esta herramienta funcione.</b>
     *
     * <p>Es un enum y no dos banderas —«la implementa KAIROS» y «la sirve el
     * Core»— por dos razones. La primera, que dos {@code boolean} seguidos en un
     * record se intercambian en silencio: compila, corre y miente. La segunda,
     * que de las cuatro combinaciones solo tres existen, y la cuarta —ni KAIROS
     * la invoca ni el Core la sirve— no es una herramienta: es una idea.
     */
    public enum Estado {

        /** KAIROS la invoca y el Core la sirve. Funciona hoy. */
        VIVA,

        /**
         * KAIROS la invoca y el Core <b>todavia no la sirve</b>.
         *
         * <p>Lo que falta esta del lado del Core, y hasta que llegue KAIROS
         * recibe un error de contrato y detiene la actuación dependiente.
         * No lo sustituye por datos vacíos ni por permisos locales.
         */
        SIN_PUERTA_EN_EL_CORE,

        /**
         * El Core la sirve y KAIROS <b>todavia no la invoca</b>.
         *
         * <p>Estan declaradas con su caso de uso y su confirmacion para que el
         * dia que se implementen no se vuelva a decidir si publicar sale al
         * exterior. No se pueden invocar.
         */
        DECLARADA
    }

    /** Las celdas de la matriz operacion→rol, copiadas. */
    public static final String TODOS = "TODOS";
    public static final String AGENTE = "AGENTE";
    public static final String BROKER = "BROKER";
    /** Celda comercial explícita: excluye gobierno administrativo. Core conserva la autoridad. */
    public static final String AGENTE_BROKER = "AGENTE,BROKER";

    /**
     * <b>La matriz no tiene fila para esta ruta</b>, porque el Core no la sirve.
     *
     * <p>No es una celda: es la ausencia de una, dicha en voz alta. Copiar aqui
     * la de una ruta parecida —{@code GET /interacciones} es TODOS, luego
     * {@code GET /interacciones/resultados} tambien— seria <b>interpretar</b>, y
     * el catalogo copia. Va siempre acompanada de
     * {@link Estado#SIN_PUERTA_EN_EL_CORE}, y que las dos cosas se muevan juntas
     * lo comprueba {@code HerramientasTest}: el dia que el Core publique la
     * ruta, el permiso deja de ser este y se copia el de su fila (D-K-1 §4).
     */
    public static final String SIN_FILA = "SIN_FILA_EN_LA_MATRIZ";

    public Herramienta {
        if (esVacio(nombre)) {
            throw new IllegalArgumentException("Una herramienta sin nombre no se puede declarar.");
        }
        if (esVacio(metodo) || esVacio(ruta)) {
            throw new IllegalArgumentException(
                    "La herramienta \"" + nombre + "\" no declara caso de uso. Una herramienta de "
                            + "KAIROS es un caso de uso que ya existe (D-K-1 regla 1): sin metodo "
                            + "y ruta, lo que se esta declarando es un camino propio.");
        }
        if (esVacio(permiso)) {
            throw new IllegalArgumentException(
                    "La herramienta \"" + nombre + "\" no declara permiso. Un endpoint nuevo entra "
                            + "en la matriz operacion→rol o no entra (D-K-1 §4).");
        }
        if (confirmacion == null) {
            throw new IllegalArgumentException(
                    "La herramienta \"" + nombre + "\" no declara confirmacion. Sin declararla, el "
                            + "valor por defecto acabaria siendo el comodo.");
        }
        if (estado == null) {
            throw new IllegalArgumentException(
                    "La herramienta \"" + nombre + "\" no declara estado. Sin declararlo no se "
                            + "puede saber si lo que falta lo pone KAIROS o el Core.");
        }
    }

    /** {@code POST /captura/{id}/ejecutar}. Lo que se dice cuando se explica que hace. */
    public String casoDeUso() {
        return metodo + " " + ruta;
    }

    /** {@code true} si hace falta un turno aparte con la persona diciendo que si. */
    public boolean laConfirmaUnaPersona() {
        return confirmacion.exigeRevisionHumana();
    }

    /**
     * {@code true} si KAIROS sabe invocarla hoy.
     *
     * <p>Incluye las que el Core no sirve: KAIROS <b>si</b> las invoca, y que la
     * llamada vuelva con un 404 es un hecho del Core, no una carencia de KAIROS.
     */
    public boolean implementada() {
        return estado != Estado.DECLARADA;
    }

    /** {@code true} si la llamada sale por el cable y no hay nadie al otro lado. */
    public boolean sinPuertaEnElCore() {
        return estado == Estado.SIN_PUERTA_EN_EL_CORE;
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
