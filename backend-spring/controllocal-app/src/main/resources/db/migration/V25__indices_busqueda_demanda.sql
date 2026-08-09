-- V25 - Indices de busqueda de las tres bandejas de Demanda (F3):
-- GET /oportunidades, GET /visitas y GET /interacciones.
--
-- Las tres estrenan texto libre y las tres lo resuelven por CONJUNTO DE
-- CANDIDATOS —una rama por tabla, unidas con UNION—, como exige la §5 de
-- contrato-listados-paginados.md. Ese patron solo rinde si CADA rama tiene su
-- indice: sin el, la rama hace Seq Scan igual que el OR que vino a sustituir.
--
-- Lo que ya estaba y NO se repite: V11 dejo el trigrama de propiedad
-- (codigo, direccion, distrito) y de persona.nombres_o_razon_social, que son
-- las ramas de "direccion del local" y "nombre del cliente/agente". V7 dejo los
-- indices de FK y los de recorrido por agente.
--
-- Faltan, y son los que crea esta migracion:
--   1. Los tres codigos correlativos que participan en las ramas de texto
--      (oportunidad, captacion y prospeccion). Se indexa la EXPRESION
--      lower(columna) porque es lo que compara la consulta, igual que V11/V23.
--   2. Las observaciones de la interaccion, que son texto libre y la unica rama
--      que mira la propia tabla de la bitacora.
--   3. Tres indices de RECORRIDO por tenant: V7 los dejo por agente
--      (id_rol_agente, ...), que sirve al alcance del AGENTE pero no al del
--      ADMIN, que recorre la organizacion entera en el orden del listado.
--
-- No toca datos ni esquema: solo indices. Es reversible con DROP INDEX.

-- 1. Codigos correlativos buscables.
CREATE INDEX ix_oportunidad_codigo_trgm
    ON oportunidad_comercial USING gin (lower(codigo_oportunidad) gin_trgm_ops);

CREATE INDEX ix_captacion_codigo_trgm
    ON captacion USING gin (lower(codigo_captacion) gin_trgm_ops);

CREATE INDEX ix_prospeccion_codigo_trgm
    ON prospeccion USING gin (lower(codigo_prospeccion) gin_trgm_ops);

-- 2. Observaciones de la bitacora.
CREATE INDEX ix_interaccion_observaciones_trgm
    ON interaccion_comercial USING gin (lower(observaciones) gin_trgm_ops);

-- 3. Recorrido por tenant en el orden de cada listado.
CREATE INDEX ix_oportunidad_org_id
    ON oportunidad_comercial (organizacion_id, id_oportunidad DESC);

CREATE INDEX ix_visita_org_fecha
    ON visita (organizacion_id, fecha_visita DESC, id_visita DESC);

CREATE INDEX ix_interaccion_org_fecha
    ON interaccion_comercial (organizacion_id, fecha_hora DESC, id_interaccion DESC);
