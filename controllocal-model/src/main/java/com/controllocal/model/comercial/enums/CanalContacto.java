package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum CanalContacto implements CodigoEnum {
    LLAMADA("L", "Llamada"),
    WHATSAPP("W", "WhatsApp"),
    EMAIL("E", "Email"),
    PRESENCIAL("P", "Presencial"),
    OTRO("O", "Otro");

    private final String codigo;
    private final String descripcion;

    CanalContacto(String codigo, String descripcion) {
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

    public static CanalContacto fromCodigo(String codigo) {
        return CodigoEnum.fromCodigo(CanalContacto.class, codigo);
    }
}
