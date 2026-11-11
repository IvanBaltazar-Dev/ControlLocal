package com.controllocal.persistence.repositorio;
import java.time.Instant;
import java.time.OffsetDateTime;
/** Proyección actual sin secretos; no contiene Persona ni organización de origen. */
public record CuentaAccesoFila(long idCuenta,String nombreUsuario,String correoPropio,Instant correoConfirmadoEn,
 String nombreProfesionalDeclarado,String estadoCuenta,OffsetDateTime sesionesInvalidasDesde,
 boolean debeCambiarContrasena,boolean debeEnrolarMfa) { }
