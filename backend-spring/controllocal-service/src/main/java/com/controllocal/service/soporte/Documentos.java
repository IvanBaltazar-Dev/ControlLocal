package com.controllocal.service.soporte;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El serializador de las tres columnas que guardan JSON como texto.
 *
 * <h2>Por que existe y no se serializa a mano</h2>
 * {@code evento_dominio.carga_util} (V53), {@code borrador_captura.datos_conocidos}
 * (V56) y {@code comando_idempotente.resultado} (V57) son TEXTO con un CHECK
 * que exige JSON valido. El dominio no puede fabricarlo — depende solo de
 * {@code jakarta.persistence-api} — asi que lo fabrica esta capa.
 *
 * <p>Y lo fabrica con Jackson, no concatenando cadenas. Concatenar funciona
 * hasta la primera comilla dentro de un valor: <i>Jr. O'Higgins 145</i> es una
 * direccion perfectamente normal en Lima y basta para producir un texto que el
 * CHECK rechaza — con el alta entera dentro de la transaccion que se cae.
 *
 * <h2>Que hace con los numeros</h2>
 * Lee los numeros como {@code BigDecimal} y no como {@code double}. Un importe
 * de 180000.00 que pasa por un double vuelve como 180000.00000000001 el dia
 * menos pensado, y estas columnas guardan importes.
 */
@Component
public class Documentos {

    private final ObjectMapper mapper;

    public Documentos(ObjectMapper mapper) {
        this.mapper = mapper.copy()
                // Los importes vuelven exactos. Sin esto, un JSON releido
                // convierte 2900.50 en un double y la comparacion con lo
                // guardado en la BD empieza a fallar por el ultimo decimal.
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    /** Un objeto JSON. Los valores nulos se omiten: "no lo se" no se guarda. */
    public String objeto(Map<String, ?> contenido) {
        Map<String, Object> limpio = new LinkedHashMap<>();
        contenido.forEach((clave, valor) -> {
            if (valor != null) {
                limpio.put(clave, valor);
            }
        });
        return escribir(limpio);
    }

    /** Una lista JSON. */
    public String lista(List<?> contenido) {
        return escribir(contenido == null ? List.of() : contenido);
    }

    /** De vuelta a un mapa. Un texto que no sea un objeto es un dato corrupto. */
    public Map<String, Object> comoMapa(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("El documento guardado no es un objeto JSON: " + json, e);
        }
    }

    /** De vuelta a una lista de cadenas: es lo unico que guardan los faltantes. */
    public List<String> comoLista(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("El documento guardado no es una lista JSON: " + json, e);
        }
    }

    private String escribir(Object contenido) {
        try {
            return mapper.writeValueAsString(contenido);
        } catch (Exception e) {
            // No es un error del usuario: es un objeto que esta capa construyo
            // y no supo escribir. Se distingue del 400 a proposito.
            throw new IllegalStateException("No se pudo serializar el documento", e);
        }
    }

    /**
     * La huella de un comando, para la idempotencia. Se calcula sobre el JSON
     * <b>canonico</b> —claves ordenadas— porque si no, el mismo comando
     * enviado con los campos en otro orden daria otra huella y un reintento
     * legitimo se rechazaria como clave reutilizada.
     */
    public String huellaDe(Map<String, ?> contenido) {
        Map<String, Object> ordenado = new java.util.TreeMap<>();
        contenido.forEach((clave, valor) -> {
            if (valor != null) {
                ordenado.put(clave, valor);
            }
        });
        return Idempotencia.huella(escribir(ordenado));
    }
}
