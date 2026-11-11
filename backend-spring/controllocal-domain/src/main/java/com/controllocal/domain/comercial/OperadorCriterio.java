package com.controllocal.domain.comercial;

import com.controllocal.domain.inmueble.TipoDato;

import java.util.Locale;
import java.util.Set;

/**
 * <b>Como se compara lo que el cliente pide con lo que la propiedad tiene</b>
 * (V92, D-M-3).
 *
 * <h2>Por que son seis y no un operador libre</h2>
 * Un criterio con operador arbitrario —{@code "> 3 o = 0"}— no se puede
 * explicar en una frase ni comprobar contra el catalogo. Estos seis cubren lo
 * que un requerimiento inmobiliario sabe pedir de verdad, y cada uno tiene
 * <b>una</b> forma de valor y <b>una</b> frase:
 *
 * <pre>
 *   I  IGUAL      "Ascensores: 2"                  TEXTO, LISTA, numero, fecha
 *   M  MINIMO     "Frente 8 m (>= 6)"              numero, fecha
 *   X  MAXIMO     "Antiguedad 12 anios (&lt;= 20)"    numero, fecha
 *   E  ENTRE      "Area 120 m2 dentro de 80-200"   numero, fecha
 *   N  INCLUYE    "Rubro: cafeteria"               TEXTO, LISTA, LISTA_MULTIPLE
 *   V  VERDADERO  "Apto para licencia: si"         BOOLEANO
 * </pre>
 *
 * <h2>{@code VERDADERO} admite tambien el negativo declarado</h2>
 * Y no es una licencia: <i>«sin cargas ni gravamenes»</i> es un requisito real
 * de una compraventa, y anadir un septimo operador solo para negar duplicaria
 * el mecanismo entero —dos operadores, dos frases, dos ramas del evaluador—
 * para expresar lo que una columna booleana ya dice. Lo que se guarda es el
 * valor <b>esperado</b>; la frase lo lee.
 *
 * <h2>La compatibilidad vive aqui, y no en tres sitios</h2>
 * La misma tabla la comprueban el trigger {@code tg_criterio_gobernado} en
 * PostgreSQL y el servicio antes de guardar. Que Java pregunte por
 * {@link #aplicaA(TipoDato)} en vez de repetir el {@code switch} es lo que
 * impide que las dos mitades diverjan — el defecto que el Corte 0B encontro con
 * los tres {@code switch} sobre {@code tipo_dato}.
 */
public enum OperadorCriterio {

    IGUAL("I", "igual a"),
    MINIMO("M", "al menos"),
    MAXIMO("X", "como mucho"),
    ENTRE("E", "entre"),
    INCLUYE("N", "incluye"),
    VERDADERO("V", "es");

    private final String codigo;
    private final String rotulo;

    OperadorCriterio(String codigo, String rotulo) {
        this.codigo = codigo;
        this.rotulo = rotulo;
    }

    public String codigo() {
        return codigo;
    }

    /** Como se lee en la frase del matcher: «Frente: al menos 6 m». */
    public String rotulo() {
        return rotulo;
    }

    /** Su valor es un rango cerrado: hacen falta los dos extremos. */
    public boolean esRango() {
        return this == ENTRE;
    }

    /** Su valor es un CONJUNTO, y vive en {@code requerimiento_criterio_valor}. */
    public boolean esConjunto() {
        return this == INCLUYE;
    }

    /**
     * ¿Este operador tiene sentido sobre una clave de este tipo de dato?
     *
     * <p>Es la misma respuesta que da el trigger. Preguntar «>= sobre un
     * booleano» no es un error de tecleo: es un criterio que el matcher no
     * podria evaluar nunca, y que aun asi contaria en el denominador.
     */
    public boolean aplicaA(TipoDato tipo) {
        return switch (tipo) {
            case TEXTO, LISTA -> this == IGUAL || this == INCLUYE;
            case LISTA_MULTIPLE -> this == INCLUYE;
            case ENTERO, DECIMAL, IMPORTE, FECHA ->
                    this == IGUAL || this == MINIMO || this == MAXIMO || this == ENTRE;
            case BOOLEANO -> this == VERDADERO;
        };
    }

    /** Los que se pueden pedir sobre una clave de ese tipo, para el mensaje de error. */
    public static Set<OperadorCriterio> paraTipo(TipoDato tipo) {
        java.util.LinkedHashSet<OperadorCriterio> validos = new java.util.LinkedHashSet<>();
        for (OperadorCriterio operador : values()) {
            if (operador.aplicaA(tipo)) {
                validos.add(operador);
            }
        }
        return validos;
    }

    /** El operador a partir del codigo persistido o del nombre. Sin defecto. */
    public static OperadorCriterio desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "Falta el operador del criterio: declara IGUAL, MINIMO, MAXIMO, ENTRE, "
                            + "INCLUYE o VERDADERO.");
        }
        String limpio = valor.trim().toUpperCase(Locale.ROOT);
        for (OperadorCriterio operador : values()) {
            if (operador.codigo.equals(limpio) || operador.name().equals(limpio)) {
                return operador;
            }
        }
        // Los simbolos que alguien escribiria esperando que funcionen.
        return switch (limpio) {
            case "=", "==" -> IGUAL;
            case ">=", "MIN" -> MINIMO;
            case "<=", "MAX" -> MAXIMO;
            default -> throw new IllegalArgumentException(
                    "Operador de criterio desconocido: \"" + valor + "\". Son IGUAL (=), "
                            + "MINIMO (>=), MAXIMO (<=), ENTRE, INCLUYE y VERDADERO.");
        };
    }
}
