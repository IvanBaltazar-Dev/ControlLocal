package com.kairos.brox;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * <b>El vocabulario del contrato publico de BROX.</b>
 *
 * <h2>Que hace aqui, y por que no es una copia del dominio</h2>
 * Esto son las <b>claves y los valores cerrados que BROX publica</b> en su API:
 * como se llama el campo de la operacion, que dos valores admite, y cuales son
 * los siete tipos de propiedad. KAIROS los necesita para poder construir una
 * peticion valida, igual que los necesitaria cualquier otro cliente de la API.
 *
 * <p><b>La diferencia con duplicar el dominio</b> esta en lo que NO hay aqui:
 * ni que atributos aplican a cada tipo, ni cuales son obligatorios, ni que
 * falta para publicar, ni cuando un encargo esta vivo. Eso son <i>reglas</i>,
 * viven en BROX y se preguntan por {@link ClienteBrox}. Esto son <i>nombres</i>.
 *
 * <p>La prueba de que la frontera esta bien puesta: anadir un tipo de propiedad
 * a BROX no obliga a tocar KAIROS para que lo pregunte —el catalogo lo dice—,
 * solo para que lo reconozca escrito en una frase. Y eso ultimo es idioma, que
 * es justo el trabajo de KAIROS.
 */
public final class Vocabulario {

    // ------------------------------------------------------------------
    // Claves de la captura de propiedad. Las publica BROX en /captura.
    // ------------------------------------------------------------------

    public static final String TIPO_PROPIEDAD = "tipoPropiedad";
    public static final String OPERACION = "operacion";
    public static final String IMPORTE = "importe";
    public static final String MONEDA = "moneda";
    public static final String TITULARES = "titulares";
    public static final String DIRECCION = "direccion";
    public static final String DISTRITO = "distrito";

    public static final String INTENCION_REGISTRAR_PROPIEDAD = "REGISTRAR_PROPIEDAD";

    // ------------------------------------------------------------------
    // Valores cerrados
    // ------------------------------------------------------------------

    /**
     * <b>Dos, y ni uno mas.</b> BROX rechaza {@code AMBAS} explicando que se
     * representa con dos encargos independientes, y rechaza {@code COMPRA}
     * explicando que comprar es VENTA vista desde el cliente. KAIROS no
     * reproduce esas explicaciones: manda lo que entendio y deja que BROX las
     * de, que es quien las tiene escritas.
     */
    public static final String VENTA = "VENTA";
    public static final String ALQUILER = "ALQUILER";
    public static final List<String> OPERACIONES = List.of(VENTA, ALQUILER);

    /** Los siete. Lo que aplica a cada uno lo dice el catalogo, no esta lista. */
    public static final List<String> TIPOS_PROPIEDAD = List.of(
            "LOCAL", "OFICINA", "DEPARTAMENTO", "CASA", "TERRENO", "ALMACEN", "OTRO");

    public static final List<String> MONEDAS = List.of("PEN", "USD");

    /** De que puede colgar una interaccion. Un CHECK de BROX lo garantiza. */
    public static final List<String> CONTEXTOS_INTERACCION =
            List.of("OPORTUNIDAD", "PROSPECCION", "CAPTACION", "CLIENTE");

    /** L llamada, W WhatsApp, E email, P presencial, R reunion, T portal, O otro. */
    public static final Map<String, String> CANALES_INTERACCION = Map.of(
            "L", "llamada", "W", "WhatsApp", "E", "email", "P", "presencial",
            "R", "reunion", "T", "portal", "O", "otro");

    private Vocabulario() {
    }

    /** El tipo canonico de una palabra, si es uno de los siete. */
    public static Optional<String> tipoPropiedad(String palabra) {
        if (palabra == null || palabra.isBlank()) {
            return Optional.empty();
        }
        String limpio = palabra.trim().toUpperCase(Locale.ROOT);
        return TIPOS_PROPIEDAD.contains(limpio) ? Optional.of(limpio) : Optional.empty();
    }

    public static boolean esOperacion(String valor) {
        return valor != null && OPERACIONES.contains(valor.trim().toUpperCase(Locale.ROOT));
    }
}
