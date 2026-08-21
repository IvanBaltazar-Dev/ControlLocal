package com.controllocal.service.soporte;

import com.controllocal.domain.inmueble.CatalogoAtributo;
import com.controllocal.domain.inmueble.Propiedad;
import com.controllocal.service.excepcion.ReglaNegocioException;

import java.math.BigDecimal;

/**
 * <b>El adaptador entre un concepto estable del dominio y el agregado que lo
 * implementa</b> (D-E4-3, paso 4).
 *
 * <h2>Por que este {@code switch} SI es correcto</h2>
 * La regla de D-E4-3 es que el servicio no puede volver a saber que claves son
 * especiales: nada de
 * <pre>
 *   if ("metraje_total".equals(clave)) { propiedad.setMetraje(...); }
 * </pre>
 * porque eso es la matriz «clave → campo» otra vez, escondida en el codigo del
 * caso de uso.
 *
 * <p>Lo de aqui es distinto. El {@code switch} no es sobre la CLAVE del
 * catalogo —que es dato, y un tenant puede inventarse las suyas— sino sobre el
 * <b>concepto del dominio</b> que el catalogo declara en
 * {@code campo_estructural}. `METRAJE` es un concepto estable: existe con ese
 * nombre lo llame el tenant «metraje_total», «area» o «superficie», y seguira
 * existiendo si manana la columna se renombra.
 *
 * <p>Esta clase es exactamente el sitio donde ese concepto se traduce a la
 * operacion del agregado, y por eso es la UNICA que puede tenerlo escrito.
 * Anadir un segundo estructural manana anade un {@code case} aqui y una fila de
 * catalogo — y no toca ningun caso de uso.
 *
 * <h2>Que no hace</h2>
 * No escribe columnas espejo. Los seis conceptos que D-E4-3 clasifico como
 * atributos gobernados <b>no pasan por aqui</b>: su autoridad es
 * {@code atributo_propiedad} y el escritor los enruta alli.
 */
public final class EscritorEstructural {

    private EscritorEstructural() {
    }

    /**
     * Aplica un valor al campo canonico que representa su concepto.
     *
     * @param concepto el valor de {@code catalogo_atributo.campo_estructural},
     *                 nunca una clave ni un nombre de columna
     */
    public static void aplicar(Propiedad propiedad, String concepto, String valor, String clave) {
        if (concepto == null) {
            throw new IllegalStateException(
                    "El atributo \"" + clave + "\" se declaro ESTRUCTURAL y no dice que concepto "
                            + "representa. `ck_catalogo_autoridad_completa` deberia haberlo impedido.");
        }
        switch (concepto) {
            case CatalogoAtributo.CAMPO_METRAJE -> propiedad.setMetraje(decimal(valor, clave));
            case CatalogoAtributo.CAMPO_PISO -> propiedad.setPiso(texto(valor));
            default -> throw new IllegalStateException(
                    "Concepto estructural sin escritor: \"" + concepto + "\". Anadirlo al catalogo "
                            + "sin anadirlo aqui deja el valor sin guardar en ninguna parte, que es "
                            + "el campo fantasma que el gate de autoridad persigue (D-E4-3).");
        }
    }

    /**
     * <b>Retira el valor del campo canonico que representa su concepto.</b>
     *
     * <p>El simetrico de {@link #aplicar} para el borrado explicito, y esta
     * aqui por la misma razon: quien pide retirar dice la <b>clave logica</b>
     * —«quiero quitar el piso»— y no sabe ni tiene por que saber si esa clave
     * vive en {@code atributo_propiedad} o en una columna del agregado. El
     * enrutado del borrado es el mismo que el de la lectura y el de la
     * escritura, o la regla «clave → autoridad» solo valdria para dos tercios
     * de las operaciones.
     *
     * <p>No todo concepto se puede vaciar, y el que no se pueda <b>lo dice</b>:
     * `METRAJE` es NOT NULL porque una propiedad sin metraje no es una
     * propiedad. Dejarlo pasar en silencio, o vaciarlo a cero, seria inventar
     * un dato — justo lo que este corte contiene.
     */
    public static void vaciar(Propiedad propiedad, String concepto, String clave) {
        switch (concepto == null ? "" : concepto) {
            case CatalogoAtributo.CAMPO_PISO -> propiedad.setPiso(null);
            case CatalogoAtributo.CAMPO_METRAJE -> throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" no se puede retirar: toda propiedad tiene "
                            + "metraje. Corrigelo mandando el valor nuevo.");
            default -> throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" no se puede retirar: su autoridad es el campo "
                            + "canonico \"" + concepto + "\" y todavia no esta definido que "
                            + "significa dejarlo vacio.");
        }
    }

    /** ¿Hay escritor para este concepto? Lo usa el gate antes de que sea tarde. */
    public static boolean sabeEscribir(String concepto) {
        return CatalogoAtributo.CAMPO_METRAJE.equals(concepto)
                || CatalogoAtributo.CAMPO_PISO.equals(concepto);
    }

    /**
     * ¿La propiedad ya tiene valor para este concepto?
     *
     * <p>Es el equivalente estructural de "existe la fila en
     * {@code atributo_propiedad}": lo que decide si una clave obligatoria esta
     * cubierta o falta. Sin esto, borrar las copias de {@code metraje_total}
     * haria que TODAS las propiedades reportaran el metraje como faltante,
     * porque la consulta que lo mide busca en la tabla equivocada.
     */
    public static boolean tieneValor(Propiedad propiedad, String concepto) {
        return leer(propiedad, concepto) != null;
    }

    /**
     * <b>El lector, simetrico del escritor.</b>
     *
     * <p>Mover la autoridad de `metraje` a su campo canonico lo saco de la
     * lista de atributos de la ficha — y con ella, de la respuesta del API. El
     * dato seguia guardado y dejaba de poder leerse: exactamente el fallo que
     * la regla del trazado persigue, cometido al arreglar otro.
     *
     * <p>La leccion, anotada donde toca: <b>si el escritor enruta por
     * autoridad, el lector tambien tiene que hacerlo.</b> Un cliente no debe
     * enterarse de donde se guarda cada valor; sigue viendo `metraje_total`
     * entre los atributos aunque su fila ya no exista.
     */
    public static String leer(Propiedad propiedad, String concepto) {
        ValorLogico valor = leerValor(propiedad, concepto);
        return valor == null ? null : valor.comoTexto();
    }

    /**
     * Lo mismo, <b>conservando el tipo</b>. Es lo que consume
     * {@link LectorPorAutoridad} para poder devolver un numero como numero.
     *
     * <p>Existe aparte de {@link #leer} porque bajar todo a texto obliga a cada
     * consumidor a volver a subirlo, y ahi es donde aparecen tres parseos que no
     * coinciden. El {@code switch} sigue siendo sobre el CONCEPTO del dominio,
     * nunca sobre la clave del catalogo: la misma razon por la que el escritor
     * puede tenerlo escrito y el caso de uso no.
     */
    public static ValorLogico leerValor(Propiedad propiedad, String concepto) {
        return switch (concepto == null ? "" : concepto) {
            case CatalogoAtributo.CAMPO_METRAJE -> ValorLogico.deNumero(propiedad.getMetraje());
            case CatalogoAtributo.CAMPO_PISO -> ValorLogico.deTexto(propiedad.getPiso());
            default -> null;
        };
    }

    /** El piso es texto: hay "3", "PB", "Mezanine" y "Sotano 2". */
    private static String texto(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private static BigDecimal decimal(String valor, String clave) {
        try {
            return new BigDecimal(valor.trim().replace(",", "."));
        } catch (RuntimeException e) {
            throw new ReglaNegocioException(
                    "El atributo \"" + clave + "\" es numerico y llego \"" + valor + "\".");
        }
    }
}
