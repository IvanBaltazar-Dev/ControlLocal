package com.controllocal.service.excepcion;

/**
 * La peticion es valida pero choca con el estado actual: un dato UNICO ya
 * existe. Se traduce a <b>409</b>, que es lo mismo que responde el cable v1
 * cuando la restriccion salta en la base de datos
 * ({@code ApiExceptionMapper.violacionUnicidad}).
 *
 * <p>Existe para no perder el 409 cuando el caso de uso se adelanta a la BD.
 * Comprobar antes solo sirve para dar un mensaje que diga QUE corregir —el de
 * la v1 es generico—: el guardian de verdad sigue siendo el indice unico, que
 * ademas es quien cubre la carrera entre dos altas simultaneas.
 *
 * <p>Un {@link ReglaNegocioException} seria un <b>400</b>, y eso si desviaria
 * del contrato: 400 dice "tu peticion esta malformada", que no es el caso.
 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
