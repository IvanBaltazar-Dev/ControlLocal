package com.kairos.brox;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <b>De donde sale cada operacion que KAIROS dispara.</b>
 *
 * <h2>No quiero IA sin trazabilidad</h2>
 * Si de un audio sale "ofrezco 165 mil" y esa oferta acaba registrada, tiene
 * que poder relacionarse despues con el audio del que salio. Esta clase es lo
 * que viaja para que se pueda: el canal, el agente, el modelo con su version,
 * la conversacion, el turno, el mensaje y la frase.
 *
 * <p>El medio en si —el audio, la imagen— <b>no</b> viaja a BROX. Se queda en
 * KAIROS con su politica de conservacion, y lo que cruza es
 * {@link #mensajeId}: el puntero con el que ir a buscarlo.
 *
 * <h2>Viaja en cabeceras</h2>
 * Porque es transversal a todas las operaciones. Meterla en el cuerpo obligaria
 * a que cada DTO de BROX la declarara, y la operacion que se olvidara quedaria
 * sin rastro sin que nadie lo notase.
 *
 * <p>{@link #peticion} va en base64 por una razon prosaica: una cabecera HTTP
 * es ASCII y una frase real trae tildes y enes. En claro, o se rompe o llega
 * ilegible.
 */
public record Traza(String canal, String agente, String modelo, String modeloVersion,
                    String conversacionId, String turnoId, String mensajeId, String peticion) {

    public static final String CANAL_WHATSAPP = "WHATSAPP";
    public static final String CANAL_API = "API";

    public Traza {
        if (esVacio(agente) || esVacio(conversacionId) || esVacio(turnoId)) {
            throw new IllegalArgumentException(
                    "Una traza tiene que decir que agente, de que conversacion y de que turno: "
                            + "sin eso, la operacion queda escrita en BROX sin poder explicarse.");
        }
    }

    /** Las cabeceras que entiende BROX. Los nombres son parte del contrato. */
    public Map<String, String> cabeceras() {
        Map<String, String> cabeceras = new LinkedHashMap<>();
        cabeceras.put("X-Canal", canal == null ? CANAL_API : canal);
        cabeceras.put("X-Agente", agente);
        ponSiHay(cabeceras, "X-Agente-Modelo", modelo);
        ponSiHay(cabeceras, "X-Agente-Version", modeloVersion);
        cabeceras.put("X-Conversacion", conversacionId);
        cabeceras.put("X-Turno", turnoId);
        ponSiHay(cabeceras, "X-Mensaje", mensajeId);
        if (!esVacio(peticion)) {
            cabeceras.put("X-Peticion-B64", Base64.getEncoder()
                    .encodeToString(peticion.getBytes(StandardCharsets.UTF_8)));
        }
        return cabeceras;
    }

    /**
     * La clave de idempotencia natural de un canal conversacional.
     *
     * <p>Es el identificador del mensaje, no uno inventado: WhatsApp reenvia
     * webhooks y el reenvio trae el <b>mismo</b> identificador. Con esto, el
     * mismo mensaje no crea dos clientes, dos propiedades ni dos ofertas — la
     * segunda llamada recibe lo que produjo la primera.
     *
     * <p>Sin mensaje se cae al turno, que tambien es unico dentro de la
     * conversacion. Inventar un UUID aqui seria peor que no tener clave: cada
     * reintento traeria una distinta y la idempotencia no valdria de nada.
     */
    public String claveIdempotencia() {
        return esVacio(mensajeId) ? conversacionId + ":" + turnoId : mensajeId;
    }

    private static void ponSiHay(Map<String, String> cabeceras, String nombre, String valor) {
        if (!esVacio(valor)) {
            cabeceras.put(nombre, valor);
        }
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
