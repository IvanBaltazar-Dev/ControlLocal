-- V23 - El rubro entra en la busqueda por texto del listado de locales.
--
-- Decision de producto (2026-08-01): `texto` pasa a buscar tambien por RUBRO,
-- ademas de codigo, direccion, distrito y propietario. El rubro no vive en
-- `propiedad` sino en su detalle 1:1, asi que la busqueda pasa a tocar TRES
-- tablas.
--
-- Por eso el listado deja de resolver el texto con un OR y pasa a un CONJUNTO
-- DE CANDIDATOS (PropiedadRepository.RAMAS_TEXTO): una rama por tabla, unidas
-- con UNION. Un OR que cruza tablas no lo puede servir ningun indice —Postgres
-- no combina indices de tablas distintas— y degeneraba en Seq Scan con el LIKE
-- como Join Filter; con una rama por tabla, cada una usa su trigrama.
--
-- V11 indexo lower() de codigo, direccion, distrito y del nombre del
-- propietario. Falta el cuarto campo buscable, y sin el la rama del rubro
-- seria justamente la que arrastrase al conjunto entero.
--
-- Se indexa la EXPRESION lower(rubro_permitido) —no la columna cruda— porque
-- es lo que compara la consulta, igual que en V11.
--
-- No toca datos ni esquema: solo un indice. Es reversible con DROP INDEX.

CREATE INDEX ix_detalle_local_rubro_trgm
    ON detalle_local_comercial USING gin (lower(rubro_permitido) gin_trgm_ops);
