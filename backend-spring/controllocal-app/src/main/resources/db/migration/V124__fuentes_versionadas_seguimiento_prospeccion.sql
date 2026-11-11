-- Constancia mínima del cálculo R27 para reautorizar la versión ORIGINAL de un recibo.
-- No copia notas/contactos ni reconstruye fuentes históricas desde el estado actual.
CREATE TABLE seguimiento_prospeccion_version(
 organizacion_id BIGINT NOT NULL,
 id_seguimiento BIGINT NOT NULL,
 version BIGINT NOT NULL CHECK(version>=0),
 fuentes_json TEXT NOT NULL CHECK(COALESCE(
   jsonb_typeof(fuentes_json::jsonb)='object'
   AND jsonb_typeof(fuentes_json::jsonb->'comunicaciones')='array',FALSE)),
 registrado_en TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(organizacion_id,id_seguimiento,version),
 FOREIGN KEY(organizacion_id,id_seguimiento) REFERENCES seguimiento_prospeccion(organizacion_id,id_seguimiento));
CREATE TRIGGER conservar_fuentes_seguimiento_version BEFORE UPDATE OR DELETE ON seguimiento_prospeccion_version
 FOR EACH ROW EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_fuentes_seguimiento_version_truncate BEFORE TRUNCATE ON seguimiento_prospeccion_version
 FOR EACH STATEMENT EXECUTE FUNCTION conservar_historia_negociacion();
