package com.controllocal.persistence.query;

import java.util.UUID;

/** Identidad de un vínculo binario actual; datos internos, nunca una proyección al modelo. */
public interface ReferenciaArchivo {
    UUID getReferencia();
    String getTipo();
    Long getIdReferencia();
    Long getIdRecurso();
    String getClaveProveedor();
    String getEstado();
}
