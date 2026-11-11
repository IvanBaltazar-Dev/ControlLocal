package com.controllocal.domain.comercial;

/** Referencia de un hecho canónico; una revisión inmutable no necesita una versión ficticia. */
public record HechoAsunto(String tipo, long referencia, Long version) {
    public HechoAsunto {
        if (tipo == null || tipo.isBlank() || referencia <= 0 || version != null && version < 0)
            throw new IllegalArgumentException("El asunto debe conservar una referencia de hecho válida.");
    }
}
