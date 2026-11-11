package com.controllocal.persistence.repositorio;
/** CAT-E01/R19: pertenencia local, sin estado ni restricciones de la cuenta global. */
public record CuentaDeGobiernoFila(Long idPersona, Long idCuenta, String nombre, String usuario,
                                  String rolPrincipal, boolean administrar, boolean pertenenciaActiva,
                                  long idMembresia, long version) { }
