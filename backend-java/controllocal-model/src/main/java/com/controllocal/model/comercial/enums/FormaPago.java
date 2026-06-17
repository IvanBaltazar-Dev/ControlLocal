package com.controllocal.model.comercial.enums;

import com.controllocal.model.CodigoEnum;

public enum FormaPago implements CodigoEnum {
    TRANSFERENCIA, DEPOSITO_BANCARIO, EFECTIVO, CHEQUE, OTRO;

    @Override public String getCodigo() { return name(); }
    @Override public String getDescripcion() { return name().replace('_', ' '); }
}
