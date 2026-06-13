package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

/**
 * Objecion principal manifestada por el cliente en la visita.
 */
public enum ObjecionVisita implements CodigoEnum {
    PRECIO("P", "Precio"),
    UBICACION("U", "Ubicacion"),
    ESTADO("E", "Estado del inmueble"),
    CONDICIONES("C", "Condiciones"),
    OTRA("O", "Otra");

    private final String codigo;
    private final String descripcion;

    ObjecionVisita(String codigo, String descripcion) {
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

    public static ObjecionVisita fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(ObjecionVisita.class, codigo);
    }
}
