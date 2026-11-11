-- C2/K1, CAT-E11/E39, MAT-01/02, catálogo18 (aprobado26-10-2026).
-- Guardar/identificar no exige superficie ni zonificación comercial.
-- Los mínimos de Encargo se evalúan en SuficienciaInmueble por acción/contexto.
-- NULL sigue siendo desconocido: no se rellenan áreas ni superficies con cero.
ALTER TABLE propiedad ALTER COLUMN metraje DROP NOT NULL;
ALTER TABLE propiedad ALTER COLUMN direccion DROP NOT NULL;
ALTER TABLE propiedad ADD CONSTRAINT ck_propiedad_referencia_identificable
 CHECK (nullif(btrim(direccion),'') IS NOT NULL OR nullif(btrim(referencia_interna),'') IS NOT NULL);

-- Metadatos sin duplicar los valores canónicos de atributo/columna estructural.
ALTER TABLE propiedad ADD COLUMN contexto_captura jsonb NOT NULL DEFAULT '{}';
ALTER TABLE propiedad ADD COLUMN metadatos_atributos jsonb NOT NULL DEFAULT '{}';
ALTER TABLE propiedad ADD CONSTRAINT ck_propiedad_contexto_captura CHECK(jsonb_typeof(contexto_captura)='object');
ALTER TABLE propiedad ADD CONSTRAINT ck_propiedad_metadatos_atributos CHECK(jsonb_typeof(metadatos_atributos)='object');
ALTER TABLE borrador_captura ADD COLUMN contexto_captura jsonb NOT NULL DEFAULT '{}';
ALTER TABLE borrador_captura ADD COLUMN metadatos_respuestas jsonb NOT NULL DEFAULT '{}';
ALTER TABLE borrador_captura ADD CONSTRAINT ck_borrador_contexto_captura CHECK(jsonb_typeof(contexto_captura)='object');
ALTER TABLE borrador_captura ADD CONSTRAINT ck_borrador_metadatos_respuestas CHECK(jsonb_typeof(metadatos_respuestas)='object');

-- Se retira la antigua obligación universal de ALTA; aplicabilidad y valores
-- permanecen. No convierte un Encargo incompleto en habilitado.
UPDATE catalogo_atributo_tipo t SET requerido=false, exigencia='OPC'
 FROM catalogo_atributo c WHERE c.id_catalogo_atributo=t.id_catalogo_atributo
 AND c.organizacion_id IS NULL AND c.clave IN ('metraje_total','dormitorios','zonificacion')
 AND t.exigencia='ALT';

ALTER TABLE catalogo_atributo ADD COLUMN version_definicion bigint NOT NULL DEFAULT 0;
ALTER TABLE catalogo_atributo ADD COLUMN jurisdiccion varchar(160);
ALTER TABLE catalogo_atributo ADD COLUMN version_fuente varchar(100);
ALTER TABLE catalogo_atributo ADD COLUMN fuente_definicion text;
-- No se inventa ni importa una lista normativa. La extensión privada revisada
-- aporta fuente/jurisdicción/versión y sus opciones mediante gobierno autorizado.
