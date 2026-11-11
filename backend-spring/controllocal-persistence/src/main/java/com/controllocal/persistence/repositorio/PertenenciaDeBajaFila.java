package com.controllocal.persistence.repositorio;

import java.time.OffsetDateTime;

/** Lectura escalar actual: un estado gestionado anterior no autoriza una baja. */
public record PertenenciaDeBajaFila(long id,long idCuenta,long idOrganizacion,Long idPersona,
        String estado,String rol,boolean administrar,long version,OffsetDateTime altaRealEn,
        OffsetDateTime bajaRealEn) { }
