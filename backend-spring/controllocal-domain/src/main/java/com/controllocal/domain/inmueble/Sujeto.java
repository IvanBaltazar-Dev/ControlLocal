package com.controllocal.domain.inmueble;

import java.util.Locale;

/**
 * <b>De quien es un dato gobernado</b> (Corte 0C, V73).
 *
 * <p>D-E4-3 respondio <i>donde vive</i> cada dato. Esta es la pregunta que va
 * antes: <b>¿de quien es?</b> Hasta el Corte 0C el catalogo presuponia una sola
 * respuesta —todo era de la Propiedad— porque {@code atributo_propiedad} cuelga
 * de {@code id_propiedad} y no habia otro sitio donde ponerlo.
 *
 * <p>La regla del reparto, y no admite «depende»:
 *
 * <blockquote>Si al firmar el siguiente encargo el dato puede cambiar sin que
 * la propiedad haya cambiado, es del <b>ENCARGO</b>.</blockquote>
 *
 * <p>{@code amoblado} es el caso que lo prueba. Una vivienda puede tener
 * muebles —hecho fisico, de la PROPIEDAD—, ofrecerse amoblada en un alquiler y
 * sin muebles en la venta —condicion negociada, del ENCARGO—, y tener dos
 * alquileres con condiciones distintas. Con un solo sujeto la tercera historia
 * es irrepresentable: el dato se sobrescribe y nadie se entera.
 *
 * <p>El sujeto se declara <b>una vez por clave</b>, y de el se deriva todo lo
 * demas — donde se declara la aplicabilidad, donde se guarda el valor, que
 * trigger lo vigila:
 *
 * <pre>
 *   PROPIEDAD -> catalogo_atributo_tipo       -> atributo_propiedad
 *   ENCARGO   -> catalogo_atributo_operacion  -> atributo_encargo
 * </pre>
 */
public enum Sujeto {

    /** Un hecho de la cosa fisica. No cambia porque cambie quien la comercializa. */
    PROPIEDAD,

    /**
     * Una condicion de UNA comercializacion concreta.
     *
     * <p>Cuelga del encargo, <b>nunca de la operacion</b>: dos alquileres
     * sucesivos de la misma propiedad comparten operacion y no comparten nada
     * mas. Agruparlos por operacion haria que el segundo heredara las
     * condiciones del primero, en silencio.
     */
    ENCARGO;

    public String codigo() {
        return name();
    }

    public boolean esDeEncargo() {
        return this == ENCARGO;
    }

    /**
     * Sin valor por defecto, a proposito. Un sujeto desconocido no puede
     * caer a PROPIEDAD «porque es lo normal»: eso guardaria una condicion
     * negociada como si fuera un hecho del inmueble, que es exactamente el
     * defecto que este corte cierra.
     */
    public static Sujeto desde(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException(
                    "Una clave del catalogo llego sin sujeto. Un dato que no sabe de quien es no "
                            + "se puede enrutar: no hay donde leerlo ni donde guardarlo.");
        }
        String limpio = codigo.trim().toUpperCase(Locale.ROOT);
        for (Sujeto sujeto : values()) {
            if (sujeto.name().equals(limpio)) {
                return sujeto;
            }
        }
        throw new IllegalArgumentException(
                "El sujeto \"" + codigo + "\" no existe. Son PROPIEDAD y ENCARGO.");
    }
}
