package com.controllocal.domain.network;

import java.time.Instant;

/** Contenido V1 acotado, con fuente y autorización propias; no incluye expedientes/contactos. */
public record ContenidoNetwork(long responsable,Long broker,boolean individual,long versionEncargo,long versionFisica,
        String politicaVersion,String datos,String campos,String finalidad,Instant desde,Instant hasta,
        String huellaDatos,String huellaAutoridad) { }
