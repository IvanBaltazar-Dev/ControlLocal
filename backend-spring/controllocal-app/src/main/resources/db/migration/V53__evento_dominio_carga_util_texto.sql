-- =====================================================================
-- V53 - La carga util del outbox pasa de jsonb a text.
--
-- POR QUE SE DESHACE ALGO DE V52
-- `carga_util` se creo como `jsonb` con su indice GIN. Al mapear la entidad
-- aparecio el problema: para que Hibernate escriba un `Map` en una columna
-- jsonb hace falta `@JdbcTypeCode(SqlTypes.JSON)`, que es una anotacion de
-- ORG.HIBERNATE -- y `controllocal-domain` depende UNICAMENTE de
-- `jakarta.persistence-api`.
--
-- Esa dependencia minima no es casualidad: es lo que mantiene el dominio
-- independiente del ORM, y romperla por una columna de una tabla que todavia
-- no lee nadie seria un mal negocio. La alternativa -- meter hibernate-core en
-- el dominio -- cuesta mucho mas de lo que resuelve.
--
-- QUE SE PIERDE, Y CUANTO IMPORTA
-- El indice GIN sobre la carga util, que serviria para "eventos que mencionan
-- esta propiedad". Los dos accesos REALES del outbox no lo usan:
--
--     el consumidor       -> WHERE proyectado_en IS NULL   (indice parcial)
--     la ficha            -> WHERE entidad_tipo, entidad_id (indice propio)
--
-- Los dos siguen intactos. La consulta por carga util es de la proyeccion al
-- grafo, que todavia no existe.
--
-- COMO SE REVIERTE CUANDO HAGA FALTA
-- El dia que el proyector exista -- y vivira FUERA del modulo de dominio, donde
-- Hibernate si esta disponible:
--
--     ALTER TABLE evento_dominio
--         ALTER COLUMN carga_util TYPE jsonb USING carga_util::jsonb;
--     CREATE INDEX ix_evento_dominio_carga ON evento_dominio USING GIN (carga_util);
--
-- El contenido ya es JSON valido, asi que el cast no pierde nada. Por eso la
-- columna se queda con un CHECK que lo garantiza.
-- =====================================================================

DROP INDEX IF EXISTS ix_evento_dominio_carga;

ALTER TABLE evento_dominio
    ALTER COLUMN carga_util TYPE TEXT USING carga_util::text;

ALTER TABLE evento_dominio
    ALTER COLUMN carga_util SET DEFAULT '{}';

-- Sigue siendo JSON, aunque se guarde como texto: es lo que hace que volver a
-- jsonb sea un `USING carga_util::jsonb` y no una limpieza de datos.
ALTER TABLE evento_dominio
    ADD CONSTRAINT ck_evento_carga_util_json
    CHECK (carga_util IS NULL OR jsonb_typeof(carga_util::jsonb) = 'object');

COMMENT ON COLUMN evento_dominio.carga_util IS
    'JSON como texto. Es jsonb valido y el CHECK lo exige; se guarda como texto para no meter Hibernate en el modulo de dominio (V53).';
