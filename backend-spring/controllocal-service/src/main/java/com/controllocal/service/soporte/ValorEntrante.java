package com.controllocal.service.soporte;

import java.util.List;

/**
 * <b>Un valor que llega para escribirse, con SU procedencia</b> (4.P).
 *
 * <p>Es la unidad que recorre el enrutador. Que la procedencia viaje <b>dentro
 * de cada valor</b> y no al lado de la operacion no es una comodidad: es la
 * regla que abrio 4.P. Un mismo {@code PUT} puede cambiar {@code tipo_acceso}
 * (visita), {@code zonificacion} (certificado) y {@code vigilancia} (lo dijo el
 * propietario), y con una procedencia por operacion dos de las tres quedarian
 * con una naturaleza <b>falsa</b>.
 *
 * <p>El {@link Procedencia acto} —canal, agente, conversacion— si es comun a
 * toda la operacion y se copia en cada valor: es lo que el Core sabe siempre.
 * La {@code naturaleza} es lo que cambia valor a valor, y a veces no consta.
 *
 * @param valores  los elementos de un {@code LISTA_MULTIPLE}. {@code null} si la
 *                 clave no es multivalor. Nunca junto con {@code valor}: son dos
 *                 formas del mismo dato y elegir una seria descartar la otra sin
 *                 decirlo
 */
public record ValorEntrante(String clave, String valor, String moneda, List<String> valores,
                            ProcedenciaDelValor procedencia) {

    /** {@code true} si lo que llega es un conjunto y no un escalar. */
    public boolean esMultivalor() {
        return valores != null;
    }
}
