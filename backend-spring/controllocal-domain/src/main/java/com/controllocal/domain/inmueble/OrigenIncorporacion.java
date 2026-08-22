package com.controllocal.domain.inmueble;

import java.util.Locale;

/**
 * <b>Como llego BROX a conocer este inmueble</b> (V76).
 *
 * <h2>Por que hace falta, y por que no es un estado</h2>
 * Desde V75 una Propiedad puede existir sin encargo: es el registro canonico del
 * inmueble, y puede acumular conocimiento aunque BROX nunca haya tenido su
 * gestion comercial. En cuanto eso es cierto aparece una pregunta que antes no
 * existia porque solo habia una respuesta posible:
 *
 * <blockquote>¿por que esta esta propiedad aqui?</blockquote>
 *
 * <p>Una registrada por un agente para captarla y una anotada porque se vio
 * anunciada valen lo mismo como dato fisico y <b>no valen lo mismo como
 * evidencia</b>. El producto exige procedencia, vigencia y evidencia antes que
 * inferencia: sin esto, una propiedad de referencia es un rumor.
 *
 * <p>Y es <b>procedencia</b>, no <b>estado</b>. No dice en que situacion esta la
 * propiedad —eso se deriva de sus relaciones— sino como entro. No cambia con el
 * tiempo: una propiedad que se conocio observando el mercado y seis meses
 * despues se capta <b>siguio conociendose observando el mercado</b>. Lo que
 * cambia es que ahora ademas tiene un encargo.
 *
 * <h2>Por que estos tres y no mas</h2>
 * El vocabulario sale del inventario de productores reales, no de una lista
 * imaginada. Hoy una fila de {@code propiedad} nace exactamente por tres
 * caminos, y cada valor tiene el suyo:
 *
 * <pre>
 *   OPERACION    el alta universal, que ejecuta un agente     (PropiedadUniversalServiceImpl)
 *   OBSERVACION  el registro de conocimiento de mercado       (sin encargo ni prospeccion)
 *   SEMILLA      las migraciones de arranque y los fixtures   (Flyway, guiones E2E)
 * </pre>
 *
 * <p>No hay {@code IMPORTACION} porque no hay ninguna importacion. Anadirlo
 * ahora seria declarar un productor que no existe, y un vocabulario con valores
 * que nadie produce deja de poder auditarse: nunca se sabe si la lista esta
 * incompleta o si el productor se perdio.
 *
 * <p><b>No se confunde con {@code Procedencia}</b> (D-K-1), que responde otra
 * cosa: por donde entro la PETICION —pantalla, WhatsApp, que conversacion, que
 * turno—. Una propiedad observada puede haberse registrado desde la pantalla o
 * dictada a un asistente; son dos ejes y los dos importan.
 */
public enum OrigenIncorporacion {

    /**
     * La registro un agente haciendo su trabajo: va a captarla, o ya la esta
     * gestionando. Es el caso normal y el unico que existia antes de V75.
     */
    OPERACION,

    /**
     * Se conocio <b>mirando el mercado</b>: un aviso, un cartel, un comparable.
     * BROX no la gestiona y puede que nunca la gestione. Su valor es el dato.
     */
    OBSERVACION,

    /**
     * La sembro una migracion o un fixture. Existe para poder distinguir el
     * dato de arranque del dato ganado: sin este valor, cualquier metrica de
     * cobertura contaria las dos semillas de V4 como cartera real.
     */
    SEMILLA;

    public String codigo() {
        return name();
    }

    /**
     * Sin defecto, y a proposito. Una propiedad cuyo origen no se sabe no puede
     * caer a OPERACION «porque es lo normal»: eso afirmaria que un agente la
     * trabajo, que es exactamente la clase de dato inventado que el producto
     * prohibe.
     */
    public static OrigenIncorporacion desde(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException(
                    "Una propiedad llego sin declarar como se conocio. La procedencia va antes "
                            + "que la inferencia: sin ella, el dato no se puede auditar.");
        }
        String limpio = codigo.trim().toUpperCase(Locale.ROOT);
        for (OrigenIncorporacion origen : values()) {
            if (origen.name().equals(limpio)) {
                return origen;
            }
        }
        throw new IllegalArgumentException(
                "El origen \"" + codigo + "\" no existe. Son OPERACION, OBSERVACION y SEMILLA.");
    }
}
