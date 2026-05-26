package com.controllocal.model;

public interface CodigoEnum {

    String getCodigo();

    String getDescripcion();

    static <E extends Enum<E> & CodigoEnum> E fromCodigo(Class<E> enumType, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El codigo de " + enumType.getSimpleName() + " es obligatorio.");
        }
        for (E item : enumType.getEnumConstants()) {
            if (item.getCodigo().equals(codigo)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Codigo invalido para " + enumType.getSimpleName() + ": " + codigo);
    }
}
