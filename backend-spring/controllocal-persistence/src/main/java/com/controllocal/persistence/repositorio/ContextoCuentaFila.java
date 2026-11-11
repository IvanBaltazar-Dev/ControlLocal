package com.controllocal.persistence.repositorio;

import com.controllocal.domain.persona.enums.TipoRol;
import java.time.LocalDate;

/** Entrada escalar de opciones: todas las pertenencias, incluido el miembro sin rol. */
public record ContextoCuentaFila(long idMembresia,long idOrganizacion,Long idPersona,
        String rolPrincipal,boolean administrar,String estadoMembresia,String estadoOrganizacion,
        String nombreOrganizacion,String modalidad,Long idRolOperativo,String estadoPersona,
        TipoRol tipoRol,LocalDate desde,LocalDate hasta) { }
