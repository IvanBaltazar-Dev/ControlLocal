package com.controllocal.web.http;

/**
 * 502: fallo del almacen de binarios (S3/disco) al guardar un archivo,
 * mismo estado y prefijo de mensaje que la v1.
 */
public class ErrorAlmacenException extends RuntimeException {

    public ErrorAlmacenException(String mensaje) {
        super(mensaje);
    }
}
