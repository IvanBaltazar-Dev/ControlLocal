package com.controllocal.rest.almacen;

/** Error de infraestructura del almacen de documentos (S3 o disco). */
public class AlmacenException extends RuntimeException {

    public AlmacenException(String mensaje) {
        super(mensaje);
    }

    public AlmacenException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
