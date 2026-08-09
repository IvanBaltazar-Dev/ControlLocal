-- V26 - Indices de busqueda de la bandeja de solicitudes (F4).
--
-- La bandeja estrena texto libre y lo resuelve por CONJUNTO DE CANDIDATOS
-- —una rama por tabla, unidas con UNION—, como exige la §5 de
-- contrato-listados-paginados.md. Ese patron solo rinde si CADA rama tiene su
-- indice: sin el, la rama hace Seq Scan igual que el OR que vino a sustituir.
--
-- Lo que ya estaba y NO se repite:
--   * V11 dejo el trigrama de propiedad (direccion, distrito) y de
--     persona.nombres_o_razon_social ⇒ cubre las ramas "direccion del local",
--     "nombre del cliente" y "nombre del agente".
--   * V25 dejo el de oportunidad_comercial.codigo_oportunidad ⇒ cubre la rama
--     del codigo de la operacion.
--   * V8 dejo ix_solicitud_agente y ix_solicitud_estado ⇒ cubren el alcance del
--     AGENTE y el filtro por estado.
--
-- Faltan, y son los que crea esta migracion:
--   1. El codigo de la solicitud, que es la rama que mira su propia tabla y el
--      termino que mas se escribe (SOL-...). Se indexa la EXPRESION
--      lower(columna) porque es lo que compara la consulta, igual que V11/V23/V25.
--   2. Un indice de RECORRIDO por tenant en el orden del listado (id desc):
--      ix_solicitud_agente y ix_solicitud_estado sirven al AGENTE y al filtro,
--      pero no al ADMIN, que recorre la organizacion entera ordenando por id.
--
-- No toca datos ni esquema: solo indices. Es reversible con DROP INDEX.

-- 1. Codigo correlativo buscable (rama sobre la propia solicitud).
CREATE INDEX ix_solicitud_codigo_trgm
    ON solicitud_alquiler USING gin (lower(codigo_solicitud) gin_trgm_ops);

-- 2. Recorrido por tenant en el orden del listado.
CREATE INDEX ix_solicitud_org_id
    ON solicitud_alquiler (organizacion_id, id_solicitud DESC);
