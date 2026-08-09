package com.controllocal.persistence.repositorio;

import java.time.OffsetDateTime;

/**
 * Proyeccion de lo que el filtro necesita en cada peticion: dos columnas de
 * {@code credencial_usuario} y la banda de {@code usuario_organizacion}.
 *
 * <p>La banda viaja aqui —y no en una consulta propia— porque el filtro ya
 * hacia esta lectura por request: anadir un {@code left join} a la membresia
 * es gratis comparado con una segunda ida a la base en el camino caliente.
 *
 * <p>{@code rolEfectivo} es {@code null} cuando la cuenta no tiene membresia
 * activa; el service traduce eso a "sin banda declarada", no a un error.
 *
 * <p>Vive <b>en persistencia</b> porque es lo que construye el {@code new} de
 * JPQL, y el gemelo que cruza a la web es
 * {@code com.controllocal.service.EstadoDeAcceso}: la web no puede depender ni
 * de persistencia ni de dominio (gate {@code ArquitecturaCapasTest}), asi que
 * la traduccion la hace el service.
 */
public record EstadoDeAccesoFila(OffsetDateTime sesionesInvalidasDesde,
                                 boolean debeCambiarContrasena,
                                 boolean debeEnrolarMfa,
                                 String rolEfectivo) {
}
