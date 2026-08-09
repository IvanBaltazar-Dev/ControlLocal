-- V11 - Indices para el listado filtrado de locales (GET /locales y /locales/resumen).
--
-- Contexto: hasta aqui la pantalla de locales descargaba la cartera entera y
-- filtraba en memoria. Al bajar filtro, orden, paginacion y conteo al servidor
-- aparecen dos accesos nuevos que la tabla no tenia indexados:
--
--   1) filtrar por estado dentro del tenant y ordenar por id (la ruta comun);
--   2) buscar texto con comodin a AMBOS lados (lower(col) LIKE '%x%'), que un
--      B-tree no puede servir: sin indice de trigramas es seq scan siempre.
--
-- No toca datos ni esquema: solo indices. Es reversible con DROP INDEX.

-- Trigramas: es lo que hace indexable un LIKE '%x%'. Viene en contrib y esta
-- disponible en la imagen postgres oficial.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- 1) Filtro + orden estable
-- ---------------------------------------------------------------------------

-- Ruta con filtro de estado. El id va en el indice para que el ORDER BY salga
-- ya ordenado y no haya sort: el listado ordena por id ASC (paridad v1).
CREATE INDEX ix_propiedad_org_estado_id ON propiedad (organizacion_id, estado, id_propiedad);

-- Ruta sin filtro de estado (la carga inicial). V6 dejo un indice solo por
-- organizacion_id; con el id detras, la primera pagina se sirve del indice.
CREATE INDEX ix_propiedad_org_id ON propiedad (organizacion_id, id_propiedad);

-- ---------------------------------------------------------------------------
-- 2) Busqueda por texto
-- ---------------------------------------------------------------------------
-- Se indexa la EXPRESION lower(columna), no la columna: la consulta compara
-- lower(col) LIKE lower('%texto%'), y un indice sobre la columna cruda no
-- serviria para esa expresion. Son indices separados por columna a proposito
-- — Postgres combina los que hagan falta con un BitmapOr, y asi cada uno sirve
-- tambien a busquedas de un solo campo.

CREATE INDEX ix_propiedad_codigo_trgm    ON propiedad USING gin (lower(codigo)    gin_trgm_ops);
CREATE INDEX ix_propiedad_direccion_trgm ON propiedad USING gin (lower(direccion) gin_trgm_ops);
CREATE INDEX ix_propiedad_distrito_trgm  ON propiedad USING gin (lower(distrito)  gin_trgm_ops);

-- La busqueda tambien alcanza al propietario, que vive en persona (via
-- persona_rol). Es la rama mas debil del OR —cruza tablas—, pero indexada al
-- menos evita el seq scan sobre persona.
CREATE INDEX ix_persona_nombre_trgm ON persona USING gin (lower(nombres_o_razon_social) gin_trgm_ops);
