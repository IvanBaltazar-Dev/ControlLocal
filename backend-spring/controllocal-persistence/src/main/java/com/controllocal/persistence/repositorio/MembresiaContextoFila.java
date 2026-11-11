package com.controllocal.persistence.repositorio;
public record MembresiaContextoFila(long id,long idCuenta,long idOrganizacion,Long idPersona,
        String rol,boolean administrar,String estado,String estadoOrganizacion,
        String nombreOrganizacion,String modalidad) { }
