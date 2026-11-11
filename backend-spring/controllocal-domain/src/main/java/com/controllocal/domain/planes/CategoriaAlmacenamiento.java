package com.controllocal.domain.planes;

/** Directriz7.2. La clasificación la determina el caso Core, nunca el cliente. */
public enum CategoriaAlmacenamiento {
    ORIGINAL(true),DOCUMENTO(true),AUDIO_ARCHIVADO(true),EXPORTACION_PERSISTENTE(true),VERSION_PERSISTENTE(true),DERIVADA_IA(true),
    PREVIEW(false),MINIATURA(false),CACHE(false),TEMPORAL(false),COPIA_TECNICA(false);
    private final boolean computa;
    CategoriaAlmacenamiento(boolean computa){this.computa=computa;}
    public boolean computa(){return computa;}
}
