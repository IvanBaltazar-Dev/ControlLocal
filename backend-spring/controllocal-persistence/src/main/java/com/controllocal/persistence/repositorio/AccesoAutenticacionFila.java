package com.controllocal.persistence.repositorio;
import java.time.Instant;
import java.time.OffsetDateTime;
/** Proyección interna de autenticación posterior al candado; nunca se entrega a clientes. */
public record AccesoAutenticacionFila(long idCuenta,String correoPropio,Instant correoConfirmadoEn,
 String nombreUsuario,String contrasenaHash,OffsetDateTime sesionesInvalidasDesde) { }
