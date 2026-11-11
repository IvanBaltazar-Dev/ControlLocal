package com.controllocal.web.dto;

/** Contrato CONGELADO: espejo del FotoLocalResponse de la v1. */
public record FotoLocalResponse(Long idFoto,String clave,String nombre,String proveedor,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        com.controllocal.service.FotoPropiedadService.UtilidadFoto utilidad) {
    public FotoLocalResponse(Long idFoto,String clave,String nombre,String proveedor){this(idFoto,clave,nombre,proveedor,null);}
}
