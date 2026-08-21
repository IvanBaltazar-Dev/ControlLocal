package com.controllocal.service.soporte;

import com.controllocal.domain.comercial.Captacion;
import com.controllocal.domain.inmueble.Propiedad;

/**
 * <b>Las coordenadas de UNA comercializacion concreta</b> (V73).
 *
 * <p>Un dato del Encargo necesita tres cosas para enrutarse, y ninguna sobra:
 *
 * <pre>
 *   idCaptacion    ->  DONDE se guarda el valor    (el episodio concreto)
 *   tipoPropiedad  ->  \  a QUE aplica la clave    (las dos dimensiones
 *   tipoOperacion  ->  /                            deciden juntas)
 * </pre>
 *
 * <p>Es un record y no tres parametros sueltos por una razon practica y fea:
 * {@code tipoPropiedad} y {@code tipoOperacion} son los dos cadenas de un
 * caracter, asi que {@code ("D", "A")} y {@code ("A", "D")} compilan igual y
 * significan cosas distintas --departamento en alquiler frente a almacen en...
 * nada, porque 'D' no es una operacion--. Con {@link #de} el orden lo decide
 * una sola linea y no cada llamante.
 *
 * <p><b>Y no es «la propiedad y su encargo».</b> Lo que viaja es el id del
 * episodio y dos coordenadas de aplicabilidad; el agregado {@code Propiedad} se
 * queda fuera a proposito, para que nadie que reciba esto pueda decidir tambien
 * cosas de la propiedad. Quien necesite las dos cosas las compone arriba, en el
 * caso de uso, y se le ve hacerlo.
 */
public record Comercializacion(long idCaptacion, String tipoPropiedad, String tipoOperacion) {

    public static Comercializacion de(Captacion encargo, Propiedad propiedad) {
        return new Comercializacion(encargo.getId(), propiedad.getTipoInmueble(),
                encargo.getMotivoOperacion());
    }
}
