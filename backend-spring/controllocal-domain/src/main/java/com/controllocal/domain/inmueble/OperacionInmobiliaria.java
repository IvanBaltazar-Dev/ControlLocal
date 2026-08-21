package com.controllocal.domain.inmueble;

import java.util.Locale;
import java.util.Set;

/**
 * <b>La operacion inmobiliaria, congelada en dos valores: VENTA y ALQUILER.</b>
 *
 * <h2>Por que dos y no cinco</h2>
 * La tentacion de anadir {@code COMPRA} —o {@code ALQUILER_CLIENTE}— viene de
 * confundir la operacion con la PERSPECTIVA desde la que se mira. No son lo
 * mismo, y mezclarlas duplica cada regla del sistema:
 *
 * <pre>
 *   Propietario + VENTA     -> quiere vender
 *   Cliente     + VENTA     -> quiere comprar
 *   Propietario + ALQUILER  -> quiere alquilar su propiedad
 *   Cliente     + ALQUILER  -> busca alquilar
 * </pre>
 *
 * La operacion es la MISMA en cada par; lo que cambia es de que lado esta
 * quien habla, y eso ya lo dice el rol. Con {@code COMPRA} en el vocabulario,
 * un requerimiento de compra y un encargo de venta dejarian de poder cruzarse
 * —el matcher tendria que saber que son la misma cosa escrita de dos maneras—
 * y esa traduccion se olvida en algun sitio siempre.
 *
 * <h2>Y por que no existe AMBAS</h2>
 * Una propiedad disponible para venta <b>y</b> alquiler no es una operacion
 * mixta: son <b>dos relaciones comerciales independientes</b>, cada una con su
 * precio, su vigencia, su publicacion, su negociacion y su cierre.
 *
 * <pre>
 *   Propiedad
 *    +- encargo VENTA     -> 180 000 USD, su historico, su expediente
 *    +- encargo ALQUILER  ->   2 900 USD/mes, el suyo
 * </pre>
 *
 * Con {@code AMBAS} habria UNA fila con dos precios dentro, y en el momento en
 * que se vendiera habria que decidir a mano que pasa con el alquiler. El
 * indice {@code uq_captacion_viva_por_operacion} (V50) impone exactamente esta
 * lectura: dos encargos vivos de operaciones <b>distintas</b> son el caso
 * normal; dos de la <b>misma</b> son un error.
 *
 * <h2>Nunca se infiere</h2>
 * {@link #desde(String)} no tiene valor por defecto a proposito. Hasta el
 * modelo universal, todo lo que el sistema sabia hacer era alquilar, y por eso
 * la operacion "se sabia" sin preguntarla. En cuanto existe la venta, esa
 * suposicion escribe datos falsos en silencio — un precio de venta guardado
 * como renta mensual no lo detecta ningun CHECK—. Si no se conoce la
 * operacion, se declara <b>faltante</b>; no se asume.
 */
public enum OperacionInmobiliaria {

    /** El propietario vende; el cliente compra. */
    VENTA("V"),

    /** El propietario alquila su propiedad; el cliente busca alquilar. */
    ALQUILER("A");

    /** Lo que se escribe en la base: `captacion.motivo_operacion`, `precio_propiedad.operacion`. */
    private final String codigo;

    OperacionInmobiliaria(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    /**
     * Los nombres que ALGUIEN podria escribir esperando que funcionen, y que
     * esta clase rechaza con una explicacion en vez de con un "valor invalido".
     * Un mensaje que dice <i>por que</i> no existe COMPRA evita que el
     * siguiente que lo intente abra un ticket.
     */
    private static final Set<String> PERSPECTIVAS = Set.of(
            "COMPRA", "COMPRAR", "VENDER", "ALQUILAR", "ARRENDAR", "ARRENDAMIENTO",
            "ALQUILER_PROPIETARIO", "ALQUILER_CLIENTE", "VENTA_PROPIETARIO", "VENTA_CLIENTE");

    private static final Set<String> COMBINADAS = Set.of(
            "AMBAS", "AMBOS", "VENTA_Y_ALQUILER", "ALQUILER_Y_VENTA", "MIXTA", "TODAS");

    /**
     * La operacion a partir de lo que llegue: {@code "VENTA"}, {@code "ALQUILER"}
     * o su codigo de una letra. <b>Sin defecto.</b>
     *
     * @throws IllegalArgumentException si falta, si es una perspectiva
     *         (COMPRA) o si es una combinacion (AMBAS) — cada una con su
     *         motivo, porque los tres errores se arreglan de forma distinta.
     */
    public static OperacionInmobiliaria desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Falta la operacion: declara VENTA o ALQUILER. No se asume ninguna, "
                            + "porque un precio de venta guardado como renta no lo detecta nadie.");
        }
        String limpio = valor.trim().toUpperCase(Locale.ROOT);

        for (OperacionInmobiliaria operacion : values()) {
            if (operacion.name().equals(limpio) || operacion.codigo.equals(limpio)) {
                return operacion;
            }
        }
        if (COMBINADAS.contains(limpio)) {
            throw new IllegalArgumentException(
                    "\"" + valor + "\" no es una operacion. Una propiedad en venta Y en alquiler "
                            + "se representa con DOS encargos independientes, cada uno con su precio "
                            + "y su historico; no con un valor combinado.");
        }
        if (PERSPECTIVAS.contains(limpio)) {
            throw new IllegalArgumentException(
                    "\"" + valor + "\" es una perspectiva, no una operacion. Comprar es VENTA vista "
                            + "desde el cliente, y buscar alquiler es ALQUILER visto desde el cliente: "
                            + "el lado lo dice el rol, no la operacion.");
        }
        throw new IllegalArgumentException(
                "Operacion desconocida: \"" + valor + "\". Solo existen VENTA y ALQUILER.");
    }

    /**
     * <b>Las operaciones que se van a encargar sobre una propiedad</b>, a partir
     * de una lista separada por comas: {@code "VENTA"},
     * {@code "VENTA,ALQUILER"}.
     *
     * <p><b>Esto no reintroduce AMBAS por la puerta de atras.</b> Lo que
     * devuelve no es una operacion combinada: es <b>cuantos encargos</b> se
     * abren y de que tipo es cada uno. La diferencia no es de matiz — con
     * {@code AMBAS} habria una fila con dos precios dentro; con esta lista hay
     * dos encargos independientes, cada uno con su importe, su vigencia y su
     * historico, y {@code uq_captacion_viva_por_operacion} (V50) sigue
     * impidiendo dos vivos de la misma.
     *
     * <p>Existe porque un alta declara de una vez lo que quiere hacer con la
     * propiedad, y la interfaz que la recoge —una pantalla o una conversacion—
     * necesita poder decirlo en un solo dato. Que el usuario elija «venta y
     * alquiler» es una intencion de interfaz; lo que llega aqui ya son dos
     * operaciones nombradas.
     *
     * <p>El orden se conserva: es el orden en que se preguntara por cada
     * condicion economica, y repetir una operacion se rechaza en vez de
     * ignorarse, porque quien lo escribio esperaba dos encargos y solo tendria
     * uno.
     *
     * @throws IllegalArgumentException si esta vacia, si algun elemento no es
     *         una operacion, o si alguna se repite
     */
    public static java.util.List<OperacionInmobiliaria> desdeLista(String valores) {
        if (valores == null || valores.isBlank()) {
            throw new IllegalArgumentException(
                    "Falta la operacion: declara VENTA, ALQUILER, o las dos separadas por coma "
                            + "(VENTA,ALQUILER) si la propiedad se ofrece para ambas cosas.");
        }
        java.util.List<OperacionInmobiliaria> operaciones = new java.util.ArrayList<>();
        for (String elemento : valores.split(",")) {
            if (elemento.isBlank()) {
                continue;
            }
            OperacionInmobiliaria operacion = desde(elemento);
            if (operaciones.contains(operacion)) {
                throw new IllegalArgumentException(
                        "\"" + operacion.name() + "\" esta declarada dos veces. Una propiedad no "
                                + "puede tener dos encargos vivos de la misma operacion: si lo que "
                                + "hace falta son dos precios, es un cambio de precio y va al "
                                + "historico.");
            }
            operaciones.add(operacion);
        }
        if (operaciones.isEmpty()) {
            throw new IllegalArgumentException(
                    "Falta la operacion: declara VENTA, ALQUILER, o las dos separadas por coma.");
        }
        return java.util.List.copyOf(operaciones);
    }

    /**
     * Como se rotula la condicion economica de esta operacion. La ficha de una
     * propiedad con dos encargos ensena dos bloques, y llamarlos igual dejaria
     * al lector sin saber cual es el precio y cual la renta.
     */
    public String rotuloDeLaCondicion() {
        return this == VENTA ? "Condición de venta" : "Condición de alquiler";
    }

    /**
     * La operacion de un codigo ya persistido. Se separa de {@link #desde} para
     * que un dato ilegible en la BD no se confunda con una entrada mal escrita
     * por un usuario: aqui un valor raro es corrupcion, no un error de tecleo.
     */
    public static OperacionInmobiliaria deCodigo(String codigo) {
        if (codigo == null) {
            return null;
        }
        for (OperacionInmobiliaria operacion : values()) {
            if (operacion.codigo.equals(codigo)) {
                return operacion;
            }
        }
        throw new IllegalStateException(
                "Codigo de operacion no reconocido en la base: \"" + codigo + "\"");
    }

    /** ¿Es un codigo que la base admitiria? Para validar sin construir. */
    public static boolean esCodigoValido(String codigo) {
        return "A".equals(codigo) || "V".equals(codigo);
    }

    /**
     * Como se llama el importe de esta operacion. Un precio de venta y una
     * renta mensual no se nombran igual, y el lenguaje del sistema tampoco
     * deberia: "renta" en una ficha de venta es un error de bulto.
     */
    public String nombreDelImporte() {
        return this == VENTA ? "precio de venta" : "renta mensual";
    }
}
