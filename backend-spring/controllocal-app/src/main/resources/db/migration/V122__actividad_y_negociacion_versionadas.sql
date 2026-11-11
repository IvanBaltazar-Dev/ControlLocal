-- C4 aprobado30/10/2026: RF040-050 y especificación de implementación, sin C5/matching.
-- Antecedente técnico SHA256 c7fca8f33f053d8bb719e167ffd08bf997df0b9fa9e4a1be7eef37a3026759c4
-- Autoridad: especificación humana30/10 §6/C4-17–20; CAT-E21–23; RF040–050.
-- NO incluye comisión, devengo/cobro C5 ni cambios de matching. V1–121 intactas.
ALTER TABLE oportunidad_comercial ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0);
ALTER TABLE oportunidad_comercial ALTER COLUMN id_rol_cliente DROP NOT NULL;
ALTER TABLE oportunidad_comercial ADD COLUMN id_persona_interesada BIGINT;
-- Relación existente explícita. No hay unión de personas por documento/correo.
UPDATE oportunidad_comercial o SET id_persona_interesada=r.id_persona
FROM persona_rol r WHERE r.id_persona_rol=o.id_rol_cliente AND r.organizacion_id=o.organizacion_id;
ALTER TABLE oportunidad_comercial ADD CONSTRAINT fk_op_interesada_org FOREIGN KEY(organizacion_id,id_persona_interesada) REFERENCES persona(organizacion_id,id_persona);
ALTER TABLE oportunidad_comercial ADD COLUMN interesado_contexto_json TEXT;
ALTER TABLE oportunidad_comercial ADD COLUMN origen VARCHAR(20);
ALTER TABLE oportunidad_comercial ADD COLUMN id_demanda BIGINT;
ALTER TABLE oportunidad_comercial ADD COLUMN version_demanda BIGINT;
ALTER TABLE oportunidad_comercial ADD COLUMN referencia_consulta VARCHAR(64);
ALTER TABLE oportunidad_comercial ADD COLUMN referencia_coincidencia VARCHAR(64);
ALTER TABLE oportunidad_comercial ADD COLUMN evidencia_interes_json TEXT;
ALTER TABLE oportunidad_comercial ADD COLUMN procedencia_json TEXT;
ALTER TABLE oportunidad_comercial ADD COLUMN fecha_no_continuidad DATE;
ALTER TABLE oportunidad_comercial ADD CONSTRAINT fk_op_demanda_org FOREIGN KEY(organizacion_id,id_demanda) REFERENCES requerimiento_cliente(organizacion_id,id_requerimiento);
ALTER TABLE oportunidad_comercial ADD CONSTRAINT ck_op_demanda_version CHECK((id_demanda IS NULL AND version_demanda IS NULL) OR (id_demanda IS NOT NULL AND version_demanda IS NOT NULL AND version_demanda>=0));
ALTER TABLE oportunidad_comercial ADD CONSTRAINT ck_op_origen CHECK(origen IS NULL OR origen IN('DIRECTO','COINCIDENCIA','COLABORACION'));
ALTER TABLE oportunidad_comercial ADD CONSTRAINT ck_op_contexto CHECK(interesado_contexto_json IS NULL OR jsonb_typeof(interesado_contexto_json::jsonb)='object');
ALTER TABLE oportunidad_comercial ADD CONSTRAINT ck_op_evidencia CHECK(evidencia_interes_json IS NULL OR jsonb_typeof(evidencia_interes_json::jsonb)='object');
ALTER TABLE oportunidad_comercial ADD CONSTRAINT ck_op_procedencia CHECK(procedencia_json IS NULL OR jsonb_typeof(procedencia_json::jsonb)='object');
CREATE UNIQUE INDEX uq_op_interesado_encargo_abierta ON oportunidad_comercial(organizacion_id,id_persona_interesada,id_captacion) WHERE estado='A';

ALTER TABLE visita ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0);
ALTER TABLE visita ADD COLUMN participantes_json TEXT;
ALTER TABLE visita ADD COLUMN lugar_declarado TEXT;
ALTER TABLE visita ADD COLUMN fecha_hecho DATE;
ALTER TABLE visita ADD COLUMN registrado_en TIMESTAMPTZ;
ALTER TABLE visita ADD COLUMN procedencia_json TEXT;
ALTER TABLE visita ADD CONSTRAINT ck_visita_participantes_json CHECK(participantes_json IS NULL OR jsonb_typeof(participantes_json::jsonb)='array');
ALTER TABLE visita ADD CONSTRAINT ck_visita_procedencia_json CHECK(procedencia_json IS NULL OR jsonb_typeof(procedencia_json::jsonb)='object');
ALTER TABLE visita ALTER COLUMN resultado TYPE TEXT;
ALTER TABLE visita DROP CONSTRAINT ck_visita_desenlace;
-- RF045: NO_REALIZADA también conserva resultado contextual. Opiniones de la visita sólo R.
ALTER TABLE visita ADD CONSTRAINT ck_visita_desenlace CHECK(
  (estado IN('R','N') OR resultado IS NULL)
  AND (estado='R' OR (nivel_interes IS NULL AND objecion_principal IS NULL AND opinion_precio IS NULL AND proxima_accion IS NULL)));
ALTER TABLE visita ADD CONSTRAINT uq_visita_org_op UNIQUE(organizacion_id,id_visita,id_oportunidad);

CREATE TABLE oportunidad_historia(
 organizacion_id BIGINT NOT NULL,id_oportunidad BIGINT NOT NULL,version BIGINT NOT NULL CHECK(version>=0),
 registrado_en TIMESTAMPTZ NOT NULL,operacion VARCHAR(60) NOT NULL,motivo TEXT,
 ficha JSONB NOT NULL CHECK(jsonb_typeof(ficha)='object'),
 PRIMARY KEY(organizacion_id,id_oportunidad,version),
 FOREIGN KEY(organizacion_id,id_oportunidad) REFERENCES oportunidad_comercial(organizacion_id,id_oportunidad));
CREATE INDEX ix_oportunidad_historia_corte ON oportunidad_historia(organizacion_id,id_oportunidad,registrado_en,version);
CREATE TABLE visita_historia(
 organizacion_id BIGINT NOT NULL,id_visita BIGINT NOT NULL,id_oportunidad BIGINT NOT NULL,version BIGINT NOT NULL CHECK(version>=0),
 registrado_en TIMESTAMPTZ NOT NULL,operacion VARCHAR(60) NOT NULL,motivo TEXT,
 ficha JSONB NOT NULL CHECK(jsonb_typeof(ficha)='object'),
 PRIMARY KEY(organizacion_id,id_visita,version),
 FOREIGN KEY(organizacion_id,id_visita,id_oportunidad) REFERENCES visita(organizacion_id,id_visita,id_oportunidad));
CREATE INDEX ix_visita_historia_corte ON visita_historia(organizacion_id,id_oportunidad,registrado_en,id_visita,version);

-- RF046–049 / CAT-E22/E23 / AC110–115. No produce formalización ni comisión.
ALTER TABLE oferta ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);

ALTER TABLE oferta ADD CONSTRAINT uq_oferta_org_mesa UNIQUE (organizacion_id,id_oferta,id_oportunidad);
ALTER TABLE oferta ADD COLUMN id_oferta_anterior BIGINT;
ALTER TABLE oferta ADD CONSTRAINT ck_oferta_antecedente CHECK(id_oferta_anterior IS NULL OR id_oferta_anterior<>id_oferta);
ALTER TABLE oferta ADD CONSTRAINT fk_oferta_antecedente_misma_mesa FOREIGN KEY(organizacion_id,id_oferta_anterior,id_oportunidad) REFERENCES oferta(organizacion_id,id_oferta,id_oportunidad);

CREATE TABLE mesa_negociacion (
    organizacion_id BIGINT NOT NULL,
    id_oportunidad BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (organizacion_id,id_oportunidad),
    FOREIGN KEY (organizacion_id,id_oportunidad)
      REFERENCES oportunidad_comercial(organizacion_id,id_oportunidad)
);

-- Propuesta comercial exacta, inmutable. No contiene fichas de personas ajenas.
CREATE TABLE oferta_propuesta (
    organizacion_id BIGINT NOT NULL,
    id_oferta BIGINT NOT NULL,
    contenido JSONB NOT NULL CHECK (jsonb_typeof(contenido)='object'),
    registrada_en TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (organizacion_id,id_oferta),
    FOREIGN KEY (organizacion_id,id_oferta) REFERENCES oferta(organizacion_id,id_oferta)
);

-- Estado y ficha realmente registrados hasta cada corte; no reconstruir con estado actual.
CREATE TABLE oferta_historia (
    organizacion_id BIGINT NOT NULL,
    id_oferta BIGINT NOT NULL,
    version_oferta BIGINT NOT NULL CHECK (version_oferta >= 0),
    id_oportunidad BIGINT NOT NULL,
    version_mesa BIGINT NOT NULL CHECK (version_mesa > 0),
    ficha JSONB NOT NULL CHECK (jsonb_typeof(ficha)='object'),
    registrada_en TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (organizacion_id,id_oferta,version_oferta),
    FOREIGN KEY (organizacion_id,id_oferta) REFERENCES oferta(organizacion_id,id_oferta),
    FOREIGN KEY (organizacion_id,id_oportunidad) REFERENCES mesa_negociacion(organizacion_id,id_oportunidad),
    FOREIGN KEY (organizacion_id,id_oferta,id_oportunidad) REFERENCES oferta(organizacion_id,id_oferta,id_oportunidad)
);
CREATE INDEX ix_oferta_historia_corte ON oferta_historia(organizacion_id,id_oportunidad,registrada_en,id_oferta,version_oferta);

CREATE TABLE acuerdo_aceptado (
    id_acuerdo BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    organizacion_id BIGINT NOT NULL,
    id_oferta BIGINT NOT NULL,
    version_oferta BIGINT NOT NULL CHECK (version_oferta >= 0),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version = 1),
    contenido JSONB NOT NULL CHECK (jsonb_typeof(contenido)='object'),
    registrada_en TIMESTAMPTZ NOT NULL,
    UNIQUE (organizacion_id,id_oferta),
    UNIQUE (organizacion_id,id_acuerdo),
    FOREIGN KEY (organizacion_id,id_oferta,version_oferta)
      REFERENCES oferta_historia(organizacion_id,id_oferta,version_oferta)
);

CREATE FUNCTION conservar_historia_negociacion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La negociación conserva propuestas, versiones y acuerdos; no se sobrescriben ni eliminan' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER conservar_propuesta BEFORE UPDATE OR DELETE ON oferta_propuesta FOR EACH ROW EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_propuesta_truncate BEFORE TRUNCATE ON oferta_propuesta FOR EACH STATEMENT EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_historia_oferta BEFORE UPDATE OR DELETE ON oferta_historia FOR EACH ROW EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_historia_oferta_truncate BEFORE TRUNCATE ON oferta_historia FOR EACH STATEMENT EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_acuerdo BEFORE UPDATE OR DELETE ON acuerdo_aceptado FOR EACH ROW EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_acuerdo_truncate BEFORE TRUNCATE ON acuerdo_aceptado FOR EACH STATEMENT EXECUTE FUNCTION conservar_historia_negociacion();


-- No borrar/sobrescribir ni fabricar estados anteriores a la primera historia registrada.
CREATE TRIGGER conservar_historia_op BEFORE UPDATE OR DELETE ON oportunidad_historia FOR EACH ROW EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_historia_op_truncate BEFORE TRUNCATE ON oportunidad_historia FOR EACH STATEMENT EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_historia_visita BEFORE UPDATE OR DELETE ON visita_historia FOR EACH ROW EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_historia_visita_truncate BEFORE TRUNCATE ON visita_historia FOR EACH STATEMENT EXECUTE FUNCTION conservar_historia_negociacion();
-- Oferta original no reescribe importe/parte/fecha/objeto al decidir. A/R/S/T son terminales.
CREATE FUNCTION conservar_terminos_oferta_c4() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.id_oferta,NEW.fecha_registro,NEW.organizacion_id,NEW.id_oportunidad,NEW.id_captacion,NEW.ronda,NEW.presentada_por,NEW.monto,NEW.moneda,NEW.condiciones,NEW.fecha,NEW.id_rol_registra,NEW.origen,NEW.hito_referencia,NEW.id_precio_referencia,NEW.monto_referencia,NEW.moneda_referencia,NEW.id_oferta_anterior)
 IS DISTINCT FROM ROW(OLD.id_oferta,OLD.fecha_registro,OLD.organizacion_id,OLD.id_oportunidad,OLD.id_captacion,OLD.ronda,OLD.presentada_por,OLD.monto,OLD.moneda,OLD.condiciones,OLD.fecha,OLD.id_rol_registra,OLD.origen,OLD.hito_referencia,OLD.id_precio_referencia,OLD.monto_referencia,OLD.moneda_referencia,OLD.id_oferta_anterior)
 OR (OLD.estado<>'P' AND NEW.estado IS DISTINCT FROM OLD.estado) THEN
 RAISE EXCEPTION 'Se conserva la propuesta original y los desenlaces terminales' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER conservar_terminos_oferta BEFORE UPDATE ON oferta FOR EACH ROW EXECUTE FUNCTION conservar_terminos_oferta_c4();
-- Conservar importe exacto de la Oferta en su hito O, sin redondeo/conversión.
ALTER TABLE precio_propiedad ALTER COLUMN monto TYPE NUMERIC(14,2);
ALTER TABLE oferta ALTER COLUMN monto_referencia TYPE NUMERIC(14,2);
