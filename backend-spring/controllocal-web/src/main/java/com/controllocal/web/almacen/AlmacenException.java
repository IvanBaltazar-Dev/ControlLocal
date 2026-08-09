package com.controllocal.web.almacen;

/** Fallo de E/S del almacen de binarios (el controlador lo traduce a 502). */
public class AlmacenException extends RuntimeException {

    public AlmacenException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
