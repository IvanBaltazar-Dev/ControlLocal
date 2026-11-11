package com.controllocal.service.soporte;

import com.controllocal.domain.auditoria.EventoDominio;
import com.controllocal.service.Actor;
import java.util.Objects;

/** Referencia al hecho ya persistido por su productor; no otra decisión o copia del expediente. */
public record CambioDeOfertaComercial(Recurso recurso,long idRecurso,Actor actor,EventoDominio eventoCausal) {
    public enum Recurso { ENCARGO, PROPIEDAD }
    public CambioDeOfertaComercial {
        Objects.requireNonNull(recurso);Objects.requireNonNull(actor);Objects.requireNonNull(eventoCausal);
        if(idRecurso<=0)throw new IllegalArgumentException("Recurso causal inválido.");
    }
}
