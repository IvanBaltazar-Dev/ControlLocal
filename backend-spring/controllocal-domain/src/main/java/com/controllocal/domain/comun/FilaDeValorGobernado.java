package com.controllocal.domain.comun;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * <b>Una fila de valor gobernado, sea de quien sea</b> (Corte 0C, V73).
 *
 * <p>{@code atributo_propiedad} y {@code atributo_encargo} tienen las mismas
 * cinco columnas de valor porque un tipo de dato significa lo mismo lo lleve
 * quien lo lleve: un IMPORTE es monto y moneda, una FECHA va en su columna, y
 * un multivalor deja la fila sin escalar.
 *
 * <p>Existe para que <b>haya una sola respuesta a «por que columna se lee esta
 * fila»</b>. Sin esta interfaz, el lector tendria dos copias del mismo
 * {@code if} --una por sujeto-- y bastaria anadir una sexta columna y
 * actualizar solo una para que un importe llegara sin moneda por un lado y con
 * ella por el otro. Es literalmente el fallo que aparecio en la ficha durante
 * el Corte 0B, con un lector paralelo que nadie habia mirado.
 *
 * <p>No declara {@code idPropiedad} ni {@code idCaptacion}: <b>de quien es la
 * fila si depende del sujeto</b>, y eso lo resuelve el enrutador, no esto.
 */
public interface FilaDeValorGobernado {

    Long getId();

    String getClave();

    String getValorTexto();

    BigDecimal getValorNumero();

    Boolean getValorBooleano();

    LocalDate getValorFecha();

    /** La moneda de un IMPORTE. No es un valor: es la unidad de otro. */
    String getValorMoneda();

    /** El escalar, sea cual sea. {@code null} en una fila ancla de multivalor. */
    Object valor();
}
