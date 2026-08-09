/**
 * Lado de LECTURA del CQRS-lite (Doc 5 §6): SQL nativo y proyecciones a
 * read-DTOs para consultas pesadas (dashboard, seguimiento, matching de
 * cartera, reportes, alertas temporales).
 *
 * Reglas del paquete:
 * <ul>
 *   <li>Toda consulta nativa recibe el SCOPE del actor (persona + rol) como
 *       parametro OBLIGATORIO del WHERE: nadie lee cartera ajena por la ruta
 *       nativa (RC-001).</li>
 *   <li>Prohibido el patron listarTodos + filtrar/paginar en memoria (MEJ-05):
 *       la paginacion y la agregacion van en el SQL.</li>
 *   <li>Cada consulta declara su read-DTO y el indice que reutiliza.</li>
 * </ul>
 */
package com.controllocal.persistence.query;
