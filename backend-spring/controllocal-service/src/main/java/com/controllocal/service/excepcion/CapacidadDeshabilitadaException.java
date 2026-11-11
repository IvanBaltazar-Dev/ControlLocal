package com.controllocal.service.excepcion;

/** Adaptador reconocido deshabilitado; no significa denegación de permiso ni resultado vacío. */
public class CapacidadDeshabilitadaException extends RuntimeException {
    public CapacidadDeshabilitadaException() { super("La capacidad no está habilitada."); }
}
