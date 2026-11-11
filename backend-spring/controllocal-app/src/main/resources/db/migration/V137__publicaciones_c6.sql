-- RF021–027/CAT-E16, C6 autorizado31/10/2026. SQL previa se conserva intacta.
-- Borrador incompleto no afirma que exista una fecha o precio publicado.
ALTER TABLE publicacion ALTER COLUMN canal DROP NOT NULL;
ALTER TABLE publicacion ALTER COLUMN titulo_anuncio DROP NOT NULL;
ALTER TABLE publicacion ALTER COLUMN importe_publicado DROP NOT NULL;
ALTER TABLE publicacion ALTER COLUMN moneda DROP NOT NULL;
ALTER TABLE publicacion ALTER COLUMN codigo_origen DROP NOT NULL;
ALTER TABLE publicacion ALTER COLUMN fecha_publicacion DROP NOT NULL;
ALTER TABLE publicacion ADD COLUMN estado_externo varchar(80);
ALTER TABLE publicacion ADD COLUMN fecha_estado_externo timestamptz;
ALTER TABLE publicacion ADD COLUMN procedencia_estado_externo varchar(500);
ALTER TABLE publicacion ADD CONSTRAINT ck_publicacion_hecho_externo CHECK (
 (estado_externo IS NULL AND fecha_estado_externo IS NULL AND procedencia_estado_externo IS NULL)
 OR (nullif(btrim(estado_externo),'') IS NOT NULL AND fecha_estado_externo IS NOT NULL
     AND nullif(btrim(procedencia_estado_externo),'') IS NOT NULL));
ALTER TABLE publicacion ADD CONSTRAINT ck_publicacion_version_positiva CHECK(version_anuncio>0);
ALTER TABLE publicacion ADD CONSTRAINT ck_publicacion_exposicion_completa CHECK(
 estado<>'P' OR (canal IS NOT NULL AND nullif(btrim(titulo_anuncio),'') IS NOT NULL
   AND importe_publicado>0 AND moneda IS NOT NULL AND fecha_publicacion IS NOT NULL));
ALTER TABLE publicacion ADD CONSTRAINT uq_publicacion_organizacion UNIQUE(organizacion_id,id_publicacion);

CREATE TABLE version_publicacion (
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 id_publicacion bigint NOT NULL,
 version bigint NOT NULL CHECK(version>0),
 actor_rol bigint,
 actuacion varchar(80) NOT NULL,
 motivo varchar(2000),
 registrada_en timestamptz NOT NULL,
 contenido jsonb NOT NULL CHECK(jsonb_typeof(contenido)='object'),
 procedencia jsonb NOT NULL CHECK(jsonb_typeof(procedencia)='object'),
 evaluacion jsonb CHECK(evaluacion IS NULL OR jsonb_typeof(evaluacion)='object'),
 PRIMARY KEY(organizacion_id,id_publicacion,version),
 FOREIGN KEY(organizacion_id,id_publicacion) REFERENCES publicacion(organizacion_id,id_publicacion),
 FOREIGN KEY(organizacion_id,actor_rol) REFERENCES persona_rol(organizacion_id,id_persona_rol)
);

-- Conserva exactamente el estado/versión existente. No reconstruye versiones ni actores desconocidos.
INSERT INTO version_publicacion(organizacion_id,id_publicacion,version,actuacion,registrada_en,contenido,procedencia)
 SELECT organizacion_id,id_publicacion,version_anuncio,'ANTECEDENTE_PRE_C6',fecha_creacion,
   jsonb_build_object('antecedentePersistido',to_jsonb(p)),
   jsonb_build_object('fuente','publicacion anterior a C6','actorNoAcreditado',true)
 FROM publicacion p;

CREATE FUNCTION impedir_cambio_version_publicacion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Una versión de publicación es inmutable'; END $$;
CREATE TRIGGER version_publicacion_inmutable BEFORE UPDATE OR DELETE ON version_publicacion
 FOR EACH ROW EXECUTE FUNCTION impedir_cambio_version_publicacion();

COMMENT ON TABLE version_publicacion IS 'Historia privada del anuncio, actor/procedencia/evaluación; no autorización ni estado externo inferido.';
