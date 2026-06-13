package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Hito del precio en el ciclo comercial del inmueble.
 */
public enum HitoPrecio implements CodigoEnum {
    ESPERADO("E", "Esperado"),
    RECOMENDADO("R", "Recomendado"),
    AUTORIZADO("U", "Autorizado"),
    PUBLICADO("P", "Publicado"),
    OFERTADO("O", "Ofertado"),
    ACEPTADO("A", "Aceptado"),
    CERRADO("C", "Cerrado");

    private final String codigo;
    private final String descripcion;

    HitoPrecio(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    @Override
    public String getCodigo() {
        return codigo;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    public static HitoPrecio fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(HitoPrecio.class, codigo);
    }
}
