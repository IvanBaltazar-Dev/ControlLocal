package com.controllocal.service;

/** Lector RF058/059 independiente de los efectores de contrato y de la comisión. */
public interface PreparacionesDeContrato {
    ContratoService.PreparacionContrato consultar(long idContrato,Actor actor);
}
