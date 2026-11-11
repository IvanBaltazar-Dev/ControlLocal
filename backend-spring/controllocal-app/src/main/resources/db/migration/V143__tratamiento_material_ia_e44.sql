-- CAT-E44/R10/RF-K080: aviso servidor y decisiones propias. Sin concesiones ni datos positivos sembrados.
CREATE TABLE material_ia_aviso_e44 (
 id_aviso uuid PRIMARY KEY,
 referencia_servidor text NOT NULL,
 version_aviso bigint NOT NULL CHECK(version_aviso>0),
 huella_aviso varchar(64) NOT NULL CHECK(huella_aviso~'^[0-9a-f]{64}$'),
 contenido text NOT NULL CHECK(jsonb_typeof(contenido::jsonb)='object'),
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(referencia_servidor,version_aviso)
);
CREATE TABLE material_ia_presentacion_e44 (
 id_presentacion uuid PRIMARY KEY,
 id_cuenta bigint NOT NULL REFERENCES cuenta_acceso(id_cuenta_acceso),
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 ambito_huella varchar(64) NOT NULL CHECK(ambito_huella~'^[0-9a-f]{64}$'),
 sesion_huella varchar(64) NOT NULL CHECK(sesion_huella~'^[0-9a-f]{64}$'),
 version_cobertura bigint NOT NULL CHECK(version_cobertura>=0),
 id_aviso uuid REFERENCES material_ia_aviso_e44(id_aviso),
 ambito text NOT NULL CHECK(jsonb_typeof(ambito::jsonb)='object'),
 huella_ambito varchar(64) NOT NULL CHECK(huella_ambito~'^[0-9a-f]{64}$'),
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(id_presentacion,id_cuenta,organizacion_id,ambito_huella)
);
CREATE TABLE material_ia_decision_e44 (
 id_decision bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_evento uuid NOT NULL,
 id_cuenta bigint NOT NULL REFERENCES cuenta_acceso(id_cuenta_acceso),
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 ambito_huella varchar(64) NOT NULL CHECK(ambito_huella~'^[0-9a-f]{64}$'),
 version bigint NOT NULL CHECK(version>0),
 id_presentacion uuid NOT NULL,
 decision text NOT NULL CHECK(decision IN('ACEPTAR','RECHAZAR','REVOCAR')),
 id_aviso uuid REFERENCES material_ia_aviso_e44(id_aviso),
 datos text NOT NULL CHECK(jsonb_typeof(datos::jsonb)='object'),
 huella_datos varchar(64) NOT NULL CHECK(huella_datos~'^[0-9a-f]{64}$'),
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK(decision<>'ACEPTAR' OR id_aviso IS NOT NULL),
 FOREIGN KEY(id_presentacion,id_cuenta,organizacion_id,ambito_huella)
  REFERENCES material_ia_presentacion_e44(id_presentacion,id_cuenta,organizacion_id,ambito_huella),
 UNIQUE(id_cuenta,id_evento),
 UNIQUE(id_cuenta,organizacion_id,ambito_huella,version)
);
CREATE INDEX ix_material_ia_cobertura_e44 ON material_ia_decision_e44
 (id_cuenta,organizacion_id,ambito_huella,version DESC);
CREATE FUNCTION conservar_tratamiento_e44() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Avisos y decisiones E44 conservan su historia de sólo anexión' USING ERRCODE='23514'; END;
$$;
CREATE TRIGGER material_ia_aviso_e44_conservar BEFORE UPDATE OR DELETE ON material_ia_aviso_e44
 FOR EACH ROW EXECUTE FUNCTION conservar_tratamiento_e44();
CREATE TRIGGER material_ia_aviso_e44_no_truncar BEFORE TRUNCATE ON material_ia_aviso_e44
 FOR EACH STATEMENT EXECUTE FUNCTION conservar_tratamiento_e44();
CREATE TRIGGER material_ia_presentacion_e44_conservar BEFORE UPDATE OR DELETE ON material_ia_presentacion_e44
 FOR EACH ROW EXECUTE FUNCTION conservar_tratamiento_e44();
CREATE TRIGGER material_ia_presentacion_e44_no_truncar BEFORE TRUNCATE ON material_ia_presentacion_e44
 FOR EACH STATEMENT EXECUTE FUNCTION conservar_tratamiento_e44();
CREATE TRIGGER material_ia_decision_e44_conservar BEFORE UPDATE OR DELETE ON material_ia_decision_e44
 FOR EACH ROW EXECUTE FUNCTION conservar_tratamiento_e44();
CREATE TRIGGER material_ia_decision_e44_no_truncar BEFORE TRUNCATE ON material_ia_decision_e44
 FOR EACH STATEMENT EXECUTE FUNCTION conservar_tratamiento_e44();
