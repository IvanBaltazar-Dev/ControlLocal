/**
 * Cuántos resultados trae una página de lista. **Uno solo para todo BROX.**
 *
 * Antes vivía copiado en cada pantalla (`const POR_PAGINA = 10`), y las copias
 * ya habían divergido: interacciones traía 20 y reasignaciones 8. Eso no es un
 * detalle estético — el usuario aprende cuánto ocupa una página y cuántas
 * vueltas le cuesta revisar una cola, y esa expectativa se rompe al cambiar de
 * pantalla.
 *
 * **Ocho, no diez.** Con ocho filas la lista entra completa en una pantalla de
 * portátil junto con sus filtros y su cabecera, sin obligar a bajar para ver la
 * última fila ni para llegar al paginador.
 *
 * No la usa el buscador de un formulario: ahí se traen muchos para filtrar en
 * el desplegable, y eso tiene su propia constante junto a su pantalla.
 */
export const RESULTADOS_POR_PAGINA = 8;
