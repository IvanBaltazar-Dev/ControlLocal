package com.controllocal.service.soporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>La capa que convierte hechos en lectura</b> (D-E2-1 §10, E2.4).
 *
 * <h2>Qué produce</h2>
 * <pre>
 *   comoEsta     hasta tres hechos, cada uno con su propio estado
 *   expediente   cuatro renglones fijos: el historial comercial
 *   lectura      una frase que sintetiza los cuatro SIN recitarlos
 * </pre>
 *
 * <h2>Por qué el orden de los hechos es narrativo</h2>
 * <b>Lo que ya está → lo que falta → qué queda parado por ello.</b> No por
 * gravedad. Ordenarlos por gravedad pondría el freno arriba y el bloque dejaría
 * de leerse como una frase: se leería como una alarma con contexto detrás, que
 * es justo lo que hace que nadie lea el contexto.
 *
 * <h2>Por qué la lectura no recita</h2>
 * «Cuatro hechos con iconos siguen siendo cuatro hechos.» Si la frase repitiera
 * los valores del expediente no aportaría nada: el usuario ya los tiene delante,
 * dos centímetros más abajo. Lo que la hace valer es <b>relacionarlos</b> — que
 * la exclusiva se agote MIENTRAS la renta lleva parada dos meses es una
 * conclusión, no un dato.
 *
 * <p>{@link #recita} es la comprobación que lo vigila, y existe porque la
 * tentación de recitar es enorme: recitar siempre compila.
 */
public final class InterpretacionDelAsunto {

    /** Tres viñetas, sin párrafos (D-E2-1 §10). */
    public static final int MAXIMO_HECHOS = 3;

    /** Los cuatro renglones son fijos y siempre los mismos. */
    public static final int RENGLONES_DEL_EXPEDIENTE = 4;

    private InterpretacionDelAsunto() {
    }

    // ==================================================================
    // Las piezas del contrato
    // ==================================================================

    /** Un hecho, con su estado ya decidido por el dominio. */
    public record Hecho(EstadoDelHecho estado, String texto) {
    }

    /**
     * Cuánto se lleva de algo <b>contable de verdad</b>.
     *
     * <p>Documentos verificados, observaciones resueltas, criterios cumplidos.
     * <b>Donde no hay nada que contar no se pone</b>: una barra de dos segmentos
     * inventada para rellenar sería peor que la ausencia, porque promete una
     * precisión que no existe.
     */
    public record Avance(int hechos, int total, String unidad) {
    }

    /** El estado de un asunto, hecho por hecho. */
    public record ComoEsta(Avance avance, List<Hecho> hechos) {

        public static ComoEsta de(Avance avance, List<Hecho> hechos) {
            List<Hecho> recortados = hechos.size() <= MAXIMO_HECHOS
                    ? List.copyOf(hechos)
                    : List.copyOf(hechos.subList(0, MAXIMO_HECHOS));
            return new ComoEsta(avance, recortados);
        }
    }

    /**
     * Cuánto se ha consumido de una ventana, con su razón.
     *
     * <p>Viaja con los dos números y no con el porcentaje: {@code 168/180} se
     * puede pintar Y se puede leer, y el 93 % solo se puede pintar.
     */
    public record Ventana(int consumido, int total) {
    }

    /**
     * Un renglón del expediente comercial.
     *
     * @param estado {@code null} = historial, sin color. Solo uno o dos por
     *               expediente llevan señal: teñir los cuatro es no teñir ninguno
     * @param serie  la chispa de la renta; sale de {@code historico_precio} (E0)
     */
    public record Renglon(String rotulo, String valor, String estado,
                          Ventana ventana, List<BigDecimal> serie) {

        public static final String BIEN = "BIEN";
        public static final String OJO = "OJO";
        public static final String MAL = "MAL";

        public static Renglon historial(String rotulo, String valor) {
            return new Renglon(rotulo, valor, null, null, null);
        }

        public static Renglon conSenal(String rotulo, String valor, String estado) {
            return new Renglon(rotulo, valor, estado, null, null);
        }
    }

    /** La interpretación entera de un asunto. */
    public record Interpretacion(ComoEsta comoEsta, List<Renglon> expediente, String lectura) {
    }

    // ==================================================================
    // Redacción
    // ==================================================================

    /**
     * <b>¿Esta frase recita el expediente en vez de leerlo?</b>
     *
     * <p>La comprobación que exige D-E2-1 §10.3.2. Recita cuando repite
     * literalmente el valor de alguno de los cuatro renglones — y basta con uno
     * para que la frase deje de aportar: el usuario ya lo tiene delante.
     *
     * <p>Compara normalizado (sin acentos, sin mayúsculas, sin espacios de más)
     * porque «US$ 4,500» y «us$ 4,500» son el mismo recitado con otro traje.
     */
    public static boolean recita(String lectura, List<Renglon> expediente) {
        if (lectura == null || lectura.isBlank()) {
            return false;
        }
        String frase = normalizar(lectura);
        for (Renglon renglon : expediente) {
            String valor = normalizar(renglon.valor());
            // Un valor muy corto ("8", "si") aparece por casualidad en cualquier
            // frase; exigir longitud evita el falso positivo que haria imposible
            // escribir nada.
            if (valor.length() >= 12 && frase.contains(valor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * <b>¿Hay un código técnico en el texto visible?</b> (D-E2-1 §10.3.3).
     *
     * <p>«Abierta el 22 jul · OPO-0098» no le dice nada a nadie: quien opera
     * identifica la operación por la dirección y la persona, no por un
     * consecutivo, y el código ocupa el sitio de algo que sí se usa.
     *
     * <p>Los códigos siguen vivos donde hacen falta —búsqueda, soporte, la ficha
     * real— pero no en el Inicio.
     */
    public static boolean llevaCodigoTecnico(String texto) {
        return texto != null && CODIGO.matcher(texto).find();
    }

    /** `PRO-0002`, `CAP-0010`, `OPO-0098`, `REQ-12`… */
    private static final java.util.regex.Pattern CODIGO =
            java.util.regex.Pattern.compile("\\b[A-Z]{2,4}-\\d{2,}\\b");

    /**
     * Los días entre dos fechas, dicho como se dice.
     *
     * <p>«hoy» y «mañana» no son «en 0 días» ni «en 1 día»: nadie habla así, y el
     * Inicio se lee en voz de quien opera.
     */
    public static String enDias(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            return null;
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta);
        if (dias < 0) {
            return "vencio hace " + Math.abs(dias) + (Math.abs(dias) == 1 ? " dia" : " dias");
        }
        return switch ((int) Math.min(dias, 2)) {
            case 0 -> "vence hoy";
            case 1 -> "vence manana";
            default -> "vence en " + dias + " dias";
        };
    }

    /**
     * Une los fragmentos de una frase con el separador del diseño, saltando los
     * vacíos.
     *
     * <p>Existe para que nadie componga «Alta el 12 may ·  · 54 dias» con un
     * hueco en medio: un separador colgando delata que un dato falta y no se
     * dijo, que es peor que decir que falta.
     */
    public static String frase(String... fragmentos) {
        List<String> vivos = new ArrayList<>();
        for (String fragmento : fragmentos) {
            if (fragmento != null && !fragmento.isBlank()) {
                vivos.add(fragmento.trim());
            }
        }
        return String.join(" · ", vivos);
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
