package com.controllocal.model.inmueble.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Tipo de inmueble captado o buscado.
 */
public enum TipoInmueble implements CodigoEnum {
    LOCAL("L", "Local"),
    OFICINA("O", "Oficina"),
    DEPARTAMENTO("D", "Departamento"),
    CASA("C", "Casa"),
    TERRENO("T", "Terreno"),
    OTRO("X", "Otro");

    private final String codigo;
    private final String descripcion;

    TipoInmueble(String codigo, String descripcion) {
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

    public static TipoInmueble fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(TipoInmueble.class, codigo);
    }
}
