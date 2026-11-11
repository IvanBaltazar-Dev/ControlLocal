package com.controllocal.asistencia.catalogo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.controllocal.asistencia.brox.ClienteBrox;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>El catalogo real de BROX, grabado.</b>
 *
 * <h2>Que problema resuelve, dicho como se descubrio</h2>
 * Las pruebas de KAIROS construian el catalogo <b>a mano</b>: cuatro atributos
 * escritos en un metodo del propio test. Con eso, un cambio del Core no podia
 * poner roja ninguna prueba de KAIROS, y el doble se permitia afirmar cosas que
 * el catalogo real no dice — declaraba {@code area_terreno} aplicable a un
 * DEPARTAMENTO, tipo al que <b>nunca</b> aplico: el catalogo vivo la publica
 * solo en CASA y en ALMACEN. Un doble que contradice a su fuente no protege
 * nada: da verde con el comportamiento equivocado.
 *
 * <p>Estos ficheros son la respuesta literal de
 * {@code GET /captura/definicion?tipoPropiedad=…} de los ocho tipos, grabada el
 * <b>2026-09-05</b> contra el Core de desarrollo. No estan escritos a mano y no
 * se editan a mano: se vuelven a grabar. Mientras la base que publica
 * {@code ESTACIONAMIENTO} no este disponible, su fixture es una copia
 * provisional de {@code OTRO}; hay que regenerarlo contra el Core cuando esa
 * base este disponible.
 *
 * <h2>Por que grabados y no en vivo</h2>
 * Porque las pruebas de KAIROS tienen que correr sin BROX levantado —es la
 * mitad del argumento de haberlos separado—. El riesgo de un fixture es que
     * envejezca en silencio. La puerta compartida se comprueba ahora con el puente
     * interno y MotorDeCaptura; no se conserva una prueba HTTP de otro servidor.
 *
 * <h2>Comunes + del tipo, que es lo que KAIROS ve</h2>
 * {@link #catalogoDe} devuelve la union en el mismo orden que
 * {@code ClienteBrox.catalogoDe}. Medir sobre {@code delTipo} solo, o sobre la
 * tabla {@code catalogo_atributo}, deja fuera {@code latitud} y
 * {@code longitud}, que no salen de la tabla sino de {@code GuionRegistroPropiedad}
 * — y son justo las dos que comparten la unidad {@code grados}.
 */
public final class CatalogoReal {

    /** Los ocho, con el nombre del fichero de cada uno. */
    public static final List<String> TIPOS = List.of(
            "LOCAL", "OFICINA", "DEPARTAMENTO", "CASA", "TERRENO", "ALMACEN", "OTRO",
            "ESTACIONAMIENTO");

    /**
     * El mismo comportamiento que el de produccion.
     *
     * <p>{@code FAIL_ON_UNKNOWN_PROPERTIES} va desactivado porque asi lo deja
     * Spring Boot, y porque el Core publica campos que
     * {@code ClienteBrox.Pregunta} no tiene —{@code restricciones}—. Con la
     * opcion activada, esta prueba fallaria por algo que en produccion no falla,
     * que es la peor clase de prueba: la que obliga a cambiar el codigo para
     * arreglar el test.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private CatalogoReal() {
    }

    /** La definicion entera de un tipo, tal como la devolvio el Core. */
    public static ClienteBrox.DefinicionCaptura definicionDe(String tipoPropiedad) {
        return leer(tipoPropiedad, ClienteBrox.DefinicionCaptura.class);
    }

    /** Lo que KAIROS ve de un tipo: comunes + del tipo, en ese orden. */
    public static List<ClienteBrox.Pregunta> catalogoDe(String tipoPropiedad) {
        ClienteBrox.DefinicionCaptura definicion = definicionDe(tipoPropiedad);
        List<ClienteBrox.Pregunta> todas = new ArrayList<>(definicion.comunes());
        todas.addAll(definicion.delTipo());
        return List.copyOf(todas);
    }

    /** El JSON crudo, para compararlo con el vivo sin pasar por ningun record. */
    public static JsonNode arbolDe(String tipoPropiedad) {
        return leer(tipoPropiedad, JsonNode.class);
    }

    /**
     * Los distritos del tenant, de donde el Core los publica: las opciones de la
     * pregunta {@code distrito}.
     *
     * <p>Son 43 y son los reales, no cuatro escritos a mano. Importa para la
     * regla del nombre mas largo: la lista inventada traia «Surco» y «Santiago
     * de Surco», y este tenant <b>no tiene «Surco»</b> — lo que si tiene es
     * «Miraflores» y «San Juan de Miraflores», que es el mismo problema de
     * verdad.
     */
    public static List<String> distritos() {
        return definicionDe("OTRO").comunes().stream()
                .filter(pregunta -> "distrito".equals(pregunta.clave()))
                .flatMap(pregunta -> pregunta.opciones().stream())
                .map(ClienteBrox.Opcion::valor)
                .toList();
    }

    public static String ruta(String tipoPropiedad) {
        return "/catalogo/" + tipoPropiedad.toLowerCase(Locale.ROOT) + ".json";
    }

    private static <T> T leer(String tipoPropiedad, Class<T> tipo) {
        String ruta = ruta(tipoPropiedad);
        try (InputStream fichero = CatalogoReal.class.getResourceAsStream(ruta)) {
            if (fichero == null) {
                throw new IllegalStateException(
                        "Falta el catalogo grabado de " + tipoPropiedad + " en " + ruta
                                + ". Se graba con: curl \"$BROX_URL/captura/definicion"
                                + "?tipoPropiedad=" + tipoPropiedad + "\" -H \"Authorization: "
                                + "Bearer $BROX_TOKEN\"");
            }
            return MAPPER.readValue(fichero, tipo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el catalogo grabado de "
                    + tipoPropiedad, e);
        }
    }
}
