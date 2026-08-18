package com.controllocal.service.soporte;

import java.util.List;
import java.util.Locale;

/**
 * <b>De qué lado de la operación está un asunto, y en qué paso de su cadena</b>
 * (D-E2-1 §7.0.d, E2.2).
 *
 * <pre>
 *   OFERTA   -> el PROPIETARIO al otro lado   PROSPECCION · CAPTACION · PUBLICACION
 *   DEMANDA  -> el CLIENTE al otro lado       OPORTUNIDAD · VISITA · SOLICITUD · CONTRATO
 * </pre>
 *
 * <h2>Dos cadenas, y no comparten ningún paso</h2>
 * Captar un local y atender a un interesado son procesos distintos que se cruzan
 * una sola vez —cuando se propone una oportunidad—. Tratarlos como una cadena de
 * siete obligaría a decir que una visita es «el paso 5 de la captación», que no
 * significa nada para quien la lee.
 *
 * <h2>Por qué lo decide el dominio</h2>
 * El lado decide el filtro del foco, el color del canto de la fila y desde qué
 * lado se redacta la consecuencia. Si Angular lo dedujera del tipo de entidad
 * tendría su propia tabla {@code entidad → lado}, y KAIROS necesitaría otra: dos
 * copias divergiendo desde el primer tipo de entidad que alguien añada.
 *
 * <p>El paso viaja como <b>código</b> y no como índice, porque el índice sin la
 * cadena no significa nada y publicar la cadena entera en cada asunto sería
 * repetirla en cada fila. Angular sabe cuántos segmentos dibuja mirando el lado
 * —tres o cuatro—, que es lo único que el traspaso le pide saber.
 */
public enum LadoDeLaOperacion {

    OFERTA("PROPIETARIO", "Propietario",
            List.of("PROSPECCION", "CAPTACION", "PUBLICACION")),

    DEMANDA("CLIENTE", "Cliente",
            List.of("OPORTUNIDAD", "VISITA", "SOLICITUD", "CONTRATO"));

    private final String actor;
    private final String rotulo;
    private final List<String> pasos;

    LadoDeLaOperacion(String actor, String rotulo, List<String> pasos) {
        this.actor = actor;
        this.rotulo = rotulo;
        this.pasos = pasos;
    }

    /** Quién está al otro lado: {@code PROPIETARIO} o {@code CLIENTE}. */
    public String actor() {
        return actor;
    }

    /** Cómo se rotula el filtro del foco: «Propietario» o «Cliente». */
    public String rotulo() {
        return rotulo;
    }

    /** La cadena entera, en orden. Tres pasos en OFERTA, cuatro en DEMANDA. */
    public List<String> pasos() {
        return pasos;
    }

    /** Dónde cae ese paso dentro de su cadena, base 0; {@code -1} si no es suyo. */
    public int indiceDe(String paso) {
        return pasos.indexOf(paso);
    }

    /**
     * A qué lado y a qué paso pertenece un tipo de entidad.
     *
     * <p><b>El {@code switch} es sobre el tipo de ENTIDAD</b>, que es vocabulario
     * del dominio, no sobre el tipo de tarea ni sobre su descripción. Añadir un
     * disparador nuevo sobre una entidad ya conocida no toca este método; añadir
     * una entidad nueva sí, y debe hacerlo — un asunto sin lado no se puede
     * filtrar, ni situar en su ruta, ni redactar desde el lado de quien lee.
     *
     * <p>{@code INMUEBLE} está declarado porque es el caso raro: la revisión de
     * un local tras rescindir un contrato mira al propietario, aunque la entidad
     * no sea suya. Y {@code CONTRATO_ALQUILER} cae en el último paso de la
     * demanda aunque la comisión que lo dispara sea un asunto de la casa: el
     * asunto sigue siendo el contrato de ese cliente.
     *
     * @return {@code null} si el tipo de entidad no tiene lado declarado
     */
    public static Ubicacion de(String entidadTipo) {
        String tipo = entidadTipo == null ? "" : entidadTipo.trim().toUpperCase(Locale.ROOT);
        return switch (tipo) {
            case "PROSPECCION" -> new Ubicacion(OFERTA, "PROSPECCION");
            case "CAPTACION" -> new Ubicacion(OFERTA, "CAPTACION");
            case "INMUEBLE", "PROPIEDAD", "PUBLICACION" -> new Ubicacion(OFERTA, "PUBLICACION");
            case "REQUERIMIENTO", "OPORTUNIDAD", "CLIENTE" -> new Ubicacion(DEMANDA, "OPORTUNIDAD");
            case "VISITA" -> new Ubicacion(DEMANDA, "VISITA");
            case "SOLICITUD_ALQUILER" -> new Ubicacion(DEMANDA, "SOLICITUD");
            case "CONTRATO_ALQUILER", "COMISION" -> new Ubicacion(DEMANDA, "CONTRATO");
            default -> null;
        };
    }

    /** Dónde cae un asunto. {@code paso} es un código de la cadena de su lado. */
    public record Ubicacion(LadoDeLaOperacion lado, String paso) {

        public int indice() {
            return lado.indiceDe(paso);
        }
    }
}
