-- CAT-R25 / directriz8.9-8.10. Sin cuentas, grants, fondos o perfiles productivos.
CREATE TABLE b0_decision_plataforma (
 id uuid PRIMARY KEY,otorgante bigint NOT NULL REFERENCES cuenta_acceso(id_cuenta_acceso),
 actuacion text NOT NULL,huella varchar(64) NOT NULL CHECK(huella ~ '^[0-9a-f]{64}$'),
 evidencia text NOT NULL CHECK(length(btrim(evidencia))>0),
 acreditada_en timestamptz NOT NULL,registrada_en timestamptz NOT NULL,
 CHECK(acreditada_en<=registrada_en)
);
CREATE TABLE b0_evaluacion_plan (
 id uuid NOT NULL,version varchar(80) NOT NULL,huella varchar(64) NOT NULL CHECK(huella ~ '^[0-9a-f]{64}$'),
 finalidad text NOT NULL,configuracion jsonb NOT NULL CHECK(jsonb_typeof(configuracion)='object'),
 manifiesto text NOT NULL,rubrica text NOT NULL,conservacion text NOT NULL,destino text NOT NULL,
 namespace_hash varchar(64) NOT NULL CHECK(namespace_hash ~ '^[0-9a-f]{64}$'),
 maximo_operacion_usd numeric(38,12) NOT NULL CHECK(maximo_operacion_usd>0),
 limite_llamadas integer NOT NULL CHECK(limite_llamadas>0),
 maximo_salida_bytes bigint NOT NULL CHECK(maximo_salida_bytes>0),
 concurrencia integer NOT NULL CHECK(concurrencia>0),
 decision uuid NOT NULL REFERENCES b0_decision_plataforma(id),
 cuenta bigint NOT NULL REFERENCES cuenta_acceso(id_cuenta_acceso),
 PRIMARY KEY(id,version),
 CHECK(length(btrim(finalidad))>0 AND length(btrim(manifiesto))>0 AND length(btrim(rubrica))>0
  AND length(btrim(conservacion))>0 AND length(btrim(destino))>0)
);
CREATE TABLE b0_evaluacion_caso (
 id uuid PRIMARY KEY,plan uuid NOT NULL,version_plan varchar(80) NOT NULL,codigo text NOT NULL,
 huella varchar(64) NOT NULL CHECK(huella ~ '^[0-9a-f]{64}$'),
 procedencia text NOT NULL CHECK(length(btrim(procedencia))>0),
 legitimacion text NOT NULL CHECK(length(btrim(legitimacion))>0),
 archivo_original uuid NOT NULL UNIQUE,bytes_original bigint NOT NULL CHECK(bytes_original>0),
 mime_original varchar(100) NOT NULL,
 decision uuid NOT NULL REFERENCES b0_decision_plataforma(id),
 cuenta bigint NOT NULL REFERENCES cuenta_acceso(id_cuenta_acceso),
 UNIQUE(plan,version_plan,codigo),UNIQUE(id,plan,version_plan),
 FOREIGN KEY(plan,version_plan) REFERENCES b0_evaluacion_plan(id,version)
);
CREATE TABLE b0_atribucion_plataforma (
 id uuid PRIMARY KEY,otorgante bigint NOT NULL REFERENCES cuenta_acceso(id_cuenta_acceso),
 cuenta bigint NOT NULL REFERENCES cuenta_acceso(id_cuenta_acceso),
 atribucion varchar(40) NOT NULL CHECK(atribucion IN('FINANCIERO_PLATFORM','OPERACION_IA_PLATFORM','BROX_PLATFORM')),
 plan uuid NOT NULL,version_plan varchar(80) NOT NULL,caso uuid,
 fondo uuid REFERENCES b0_fondo_tecnico(id_fondo),finalidad text NOT NULL,
 acciones jsonb NOT NULL CHECK(jsonb_typeof(acciones)='array' AND jsonb_array_length(acciones)>0),
 campos jsonb NOT NULL CHECK(jsonb_typeof(campos)='array' AND jsonb_array_length(campos)>0),
 desde timestamptz NOT NULL,hasta timestamptz NOT NULL CHECK(hasta>desde),
 evidencia text NOT NULL CHECK(length(btrim(evidencia))>0),huella varchar(64) NOT NULL,
 decision uuid NOT NULL REFERENCES b0_decision_plataforma(id),registrada_en timestamptz NOT NULL,
 revocada_en timestamptz,revocada_por bigint REFERENCES cuenta_acceso(id_cuenta_acceso),
 decision_revocacion uuid REFERENCES b0_decision_plataforma(id),motivo_revocacion text,
 FOREIGN KEY(plan,version_plan) REFERENCES b0_evaluacion_plan(id,version),
 FOREIGN KEY(caso,plan,version_plan) REFERENCES b0_evaluacion_caso(id,plan,version_plan),
 UNIQUE(id,cuenta,plan,version_plan),
 CHECK((atribucion='FINANCIERO_PLATFORM')=(fondo IS NOT NULL)),
 CHECK(atribucion<>'BROX_PLATFORM' OR caso IS NOT NULL),
 CHECK(num_nonnulls(revocada_en,revocada_por,decision_revocacion,motivo_revocacion) IN(0,4))
);
CREATE TABLE b0_evaluacion_financiacion (
 id uuid PRIMARY KEY,plan uuid NOT NULL,version_plan varchar(80) NOT NULL,
 fondo uuid NOT NULL REFERENCES b0_fondo_tecnico(id_fondo),
 maximo_usd numeric(38,12) NOT NULL CHECK(maximo_usd>0),desde timestamptz NOT NULL,hasta timestamptz NOT NULL CHECK(hasta>desde),
 cuenta bigint NOT NULL,concesion uuid NOT NULL,evidencia text NOT NULL CHECK(length(btrim(evidencia))>0),
 revocada_en timestamptz,
 FOREIGN KEY(plan,version_plan) REFERENCES b0_evaluacion_plan(id,version),
 FOREIGN KEY(concesion,cuenta,plan,version_plan) REFERENCES b0_atribucion_plataforma(id,cuenta,plan,version_plan),
 UNIQUE(id,plan,version_plan),UNIQUE(plan,version_plan)
);
CREATE FUNCTION conservar_atribucion_plataforma_b0() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN RAISE EXCEPTION 'La atribución conserva su antecedente';END IF;
 IF ROW(NEW.id,NEW.otorgante,NEW.cuenta,NEW.atribucion,NEW.plan,NEW.version_plan,NEW.caso,NEW.fondo,
  NEW.finalidad,NEW.acciones,NEW.campos,NEW.desde,NEW.hasta,NEW.evidencia,NEW.huella,NEW.decision,NEW.registrada_en)
 IS DISTINCT FROM ROW(OLD.id,OLD.otorgante,OLD.cuenta,OLD.atribucion,OLD.plan,OLD.version_plan,OLD.caso,OLD.fondo,
  OLD.finalidad,OLD.acciones,OLD.campos,OLD.desde,OLD.hasta,OLD.evidencia,OLD.huella,OLD.decision,OLD.registrada_en)
 OR OLD.revocada_en IS NOT NULL OR NEW.revocada_en IS NULL
 THEN RAISE EXCEPTION 'Sólo se añade la revocación acreditada';END IF;
 RETURN NEW;
END;$$;
CREATE TRIGGER b0_atribucion_conservar BEFORE UPDATE OR DELETE ON b0_atribucion_plataforma
 FOR EACH ROW EXECUTE FUNCTION conservar_atribucion_plataforma_b0();
CREATE TRIGGER b0_atribucion_truncate BEFORE TRUNCATE ON b0_atribucion_plataforma
 FOR EACH STATEMENT EXECUTE FUNCTION conservar_atribucion_plataforma_b0();
DO $$ DECLARE t text;BEGIN
 FOREACH t IN ARRAY ARRAY['b0_decision_plataforma','b0_evaluacion_plan','b0_evaluacion_caso'] LOOP
  EXECUTE format('CREATE TRIGGER conservar_351 BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente()',t);
  EXECUTE format('CREATE TRIGGER conservar_351_truncate BEFORE TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION b0_conservar_antecedente()',t);
 END LOOP;
END;$$;

-- Un padre financiero. La referencia previa no se vuelve global por accidente.
ALTER TABLE b0_operacion_ia ADD COLUMN id_operacion_b0 uuid NOT NULL DEFAULT gen_random_uuid(),
 ADD COLUMN id_caso_evaluacion uuid REFERENCES b0_evaluacion_caso(id),
 ADD COLUMN cuenta_evaluacion bigint REFERENCES cuenta_acceso(id_cuenta_acceso),
 ADD COLUMN concesion_evaluacion uuid REFERENCES b0_atribucion_plataforma(id),
 ADD COLUMN autorizacion_evaluacion uuid REFERENCES b0_evaluacion_financiacion(id);
ALTER TABLE b0_operacion_ia ADD CONSTRAINT uq_b0_comando_comercial UNIQUE(organizacion_id,id_operacion),
 ADD CONSTRAINT uq_b0_identidad_tecnica UNIQUE(id_operacion_b0),
 ADD UNIQUE(id_operacion_b0,id_caso_evaluacion),
 ADD CONSTRAINT uq_b0_comando_evaluacion UNIQUE(id_caso_evaluacion,id_operacion),
 ADD CONSTRAINT uq_b0_tecnica_comercial UNIQUE(id_operacion_b0,organizacion_id,id_operacion),
 ADD CONSTRAINT uq_b0_tecnica_evaluacion UNIQUE(id_operacion_b0,id_caso_evaluacion,id_operacion);
-- Una PK no admite NULL: sustituirla manteniendo FKs contra el UNIQUE anterior.
DO $$ DECLARE c record;defs jsonb:='[]';BEGIN
 FOR c IN SELECT conrelid::regclass::text AS tabla,conname,pg_get_constraintdef(oid) AS ddl
  FROM pg_constraint WHERE confrelid='b0_operacion_ia'::regclass AND contype='f'
 LOOP
  defs:=defs||jsonb_build_array(jsonb_build_object('t',c.tabla,'n',c.conname,'d',c.ddl));
  EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',c.tabla,c.conname);
 END LOOP;
 ALTER TABLE b0_operacion_ia DROP CONSTRAINT b0_operacion_ia_pkey;
 ALTER TABLE b0_operacion_ia ADD PRIMARY KEY(id_operacion_b0);
 FOR c IN SELECT value AS v FROM jsonb_array_elements(defs) LOOP
  EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s',c.v->>'t',c.v->>'n',c.v->>'d');
 END LOOP;
END;$$;
ALTER TABLE b0_operacion_ia ALTER COLUMN organizacion_id DROP NOT NULL,
 ALTER COLUMN id_persona DROP NOT NULL,ALTER COLUMN id_rol DROP NOT NULL,
 ALTER COLUMN perfil DROP NOT NULL,ALTER COLUMN version_perfil DROP NOT NULL,
 ALTER COLUMN maximo_creditos DROP NOT NULL,
 ADD CONSTRAINT ck_b0_forma_operacion CHECK(
  (organizacion_id IS NOT NULL AND id_persona IS NOT NULL AND id_rol IS NOT NULL
   AND perfil IS NOT NULL AND version_perfil IS NOT NULL AND maximo_creditos IS NOT NULL
   AND num_nonnulls(id_caso_evaluacion,cuenta_evaluacion,concesion_evaluacion,autorizacion_evaluacion)=0)
  OR(organizacion_id IS NULL AND num_nonnulls(id_persona,id_rol,perfil,version_perfil,maximo_creditos,creditos_liquidados)=0
   AND num_nonnulls(id_caso_evaluacion,cuenta_evaluacion,concesion_evaluacion,autorizacion_evaluacion)=4));

CREATE FUNCTION b0_resolver_operacion(org bigint,referencia uuid) RETURNS uuid LANGUAGE sql STABLE AS $$
 SELECT id_operacion_b0 FROM b0_operacion_ia WHERE
 (org IS NOT NULL AND organizacion_id=org AND id_operacion=referencia)
 OR(org IS NULL AND id_caso_evaluacion IS NOT NULL AND id_operacion_b0=referencia)
$$;
CREATE OR REPLACE FUNCTION b0_conservar_antecedente() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND TG_TABLE_NAME IN('b0_medicion_tecnica','b0_antecedente')
 AND OLD.id_operacion_b0 IS NULL AND NEW.id_caso_evaluacion IS NULL
 AND NEW.id_operacion_b0=b0_resolver_operacion(OLD.organizacion_id,OLD.id_operacion)
 AND(to_jsonb(NEW)-'id_operacion_b0'-'id_caso_evaluacion') IS NOT DISTINCT FROM(to_jsonb(OLD)-'id_operacion_b0'-'id_caso_evaluacion')
 THEN RETURN NEW;END IF;
 RAISE EXCEPTION 'B0 conserva antecedentes inmutables';
END;$$;
CREATE OR REPLACE FUNCTION conservar_etapa_operacion_ia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND OLD.id_operacion_b0 IS NULL AND NEW.id_caso_evaluacion IS NULL
 AND NEW.id_operacion_b0=b0_resolver_operacion(OLD.organizacion_id,OLD.id_operacion)
 AND(to_jsonb(NEW)-'id_operacion_b0'-'id_caso_evaluacion') IS NOT DISTINCT FROM(to_jsonb(OLD)-'id_operacion_b0'-'id_caso_evaluacion')
 THEN RETURN NEW;END IF;

 IF TG_OP IN('DELETE','TRUNCATE') THEN RAISE EXCEPTION 'La etapa conserva su claim y hechos' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.organizacion_id,NEW.id_operacion,NEW.etapa,NEW.clave,NEW.proveedor,NEW.huella_peticion,NEW.programada_en)
   IS DISTINCT FROM ROW(OLD.organizacion_id,OLD.id_operacion,OLD.etapa,OLD.clave,OLD.proveedor,OLD.huella_peticion,OLD.programada_en)
   OR OLD.estado IN('MEDIDA','NO_INVOCADA')
   OR NOT ((OLD.estado='PENDIENTE' AND NEW.estado IN('RECLAMADA','NO_INVOCADA')) OR (OLD.estado='RECLAMADA' AND NEW.estado IN('MEDIDA','NO_INVOCADA','INCIERTA')) OR (OLD.estado='INCIERTA' AND NEW.estado IN('MEDIDA','NO_INVOCADA')))
   OR (OLD.reclamada_en IS NOT NULL AND NEW.reclamada_en IS DISTINCT FROM OLD.reclamada_en)
 THEN RAISE EXCEPTION 'La etapa no admite otra identidad, claim o resolución' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE OR REPLACE FUNCTION exigir_conjunto_etapas_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o b0_operacion_ia%ROWTYPE;
BEGIN
 IF TG_TABLE_NAME='b0_etapa_operacion_ia' THEN
 IF TG_OP='UPDATE' AND OLD.id_operacion_b0 IS NULL AND NEW.id_caso_evaluacion IS NULL
 AND NEW.id_operacion_b0=b0_resolver_operacion(OLD.organizacion_id,OLD.id_operacion)
 AND(to_jsonb(NEW)-'id_operacion_b0'-'id_caso_evaluacion') IS NOT DISTINCT FROM(to_jsonb(OLD)-'id_operacion_b0'-'id_caso_evaluacion')
 THEN RETURN NEW;END IF;
 END IF;

 IF TG_TABLE_NAME='b0_operacion_ia' THEN
  IF NEW.estado IN('LIQUIDADA','CANCELADA') AND EXISTS(SELECT 1 FROM b0_etapa_operacion_ia e
   WHERE e.organizacion_id=NEW.organizacion_id AND e.id_operacion=NEW.id_operacion AND e.estado NOT IN('MEDIDA','NO_INVOCADA'))
  THEN RAISE EXCEPTION 'El conjunto conserva etapas facturables pendientes' USING ERRCODE='23514'; END IF;
 ELSE
  -- Mismo fence de fila que el cierre Core; una etapa no puede confirmar después de ese cierre.
  SELECT * INTO STRICT o FROM b0_operacion_ia WHERE organizacion_id=NEW.organizacion_id AND id_operacion=NEW.id_operacion FOR NO KEY UPDATE;
  IF o.estado NOT IN('EN_PROCESO','INCIERTA') THEN RAISE EXCEPTION 'La operación cerrada no admite programar o reclamar etapas' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END; $$;
-- Hijos: ID común más una terna exacta no-null de procedencia, sin JSON polimórfico.

ALTER TABLE b0_medicion_tecnica ADD COLUMN id_operacion_b0 uuid,ADD COLUMN id_caso_evaluacion uuid;
UPDATE b0_medicion_tecnica m SET id_operacion_b0=o.id_operacion_b0
 FROM b0_operacion_ia o WHERE o.organizacion_id=m.organizacion_id AND o.id_operacion=m.id_operacion;




ALTER TABLE b0_etapa_operacion_ia ADD COLUMN id_operacion_b0 uuid,ADD COLUMN id_caso_evaluacion uuid;
UPDATE b0_etapa_operacion_ia e SET id_operacion_b0=o.id_operacion_b0
 FROM b0_operacion_ia o WHERE o.organizacion_id=e.organizacion_id AND o.id_operacion=e.id_operacion;




-- PK de hijos debe permitir evaluación; conservar sus UNIQUE comerciales.
ALTER TABLE b0_medicion_tecnica DROP CONSTRAINT b0_medicion_tecnica_pkey;
ALTER TABLE b0_medicion_tecnica ADD PRIMARY KEY(id_operacion_b0,id_medicion),
 ADD UNIQUE(organizacion_id,id_operacion,id_medicion);
ALTER TABLE b0_medicion_tecnica DROP CONSTRAINT fk_b0_medicion_etapa;
ALTER TABLE b0_etapa_operacion_ia DROP CONSTRAINT b0_etapa_operacion_ia_pkey;
ALTER TABLE b0_etapa_operacion_ia ADD PRIMARY KEY(id_operacion_b0,etapa),
 ADD UNIQUE(organizacion_id,id_operacion,etapa),ADD UNIQUE(id_operacion_b0,clave);
ALTER TABLE b0_medicion_tecnica ADD CONSTRAINT fk_b0_medicion_etapa
 FOREIGN KEY(id_operacion_b0,etapa_clave) REFERENCES b0_etapa_operacion_ia(id_operacion_b0,clave);
DO $$ DECLARE t text;BEGIN
 FOREACH t IN ARRAY ARRAY['b0_medicion_tecnica','b0_etapa_operacion_ia'] LOOP
  EXECUTE format('ALTER TABLE %I ALTER COLUMN id_operacion_b0 SET NOT NULL',t);
  EXECUTE format('ALTER TABLE %I ALTER COLUMN organizacion_id DROP NOT NULL',t);
  EXECUTE format('ALTER TABLE %I ADD FOREIGN KEY(id_operacion_b0) REFERENCES b0_operacion_ia(id_operacion_b0)',t);
  EXECUTE format('ALTER TABLE %I ADD FOREIGN KEY(id_operacion_b0,organizacion_id,id_operacion) REFERENCES b0_operacion_ia(id_operacion_b0,organizacion_id,id_operacion)',t);
  EXECUTE format('ALTER TABLE %I ADD FOREIGN KEY(id_operacion_b0,id_caso_evaluacion,id_operacion) REFERENCES b0_operacion_ia(id_operacion_b0,id_caso_evaluacion,id_operacion)',t);
  EXECUTE format('ALTER TABLE %I ADD CHECK(num_nonnulls(organizacion_id,id_caso_evaluacion)=1)',t);
 END LOOP;
END;$$;


ALTER TABLE b0_antecedente ADD COLUMN id_operacion_b0 uuid REFERENCES b0_operacion_ia(id_operacion_b0),
 ADD COLUMN id_caso_evaluacion uuid;

UPDATE b0_antecedente a SET id_operacion_b0=o.id_operacion_b0
 FROM b0_operacion_ia o WHERE o.organizacion_id=a.organizacion_id AND o.id_operacion=a.id_operacion;

ALTER TABLE b0_antecedente ALTER COLUMN organizacion_id DROP NOT NULL,
 ADD FOREIGN KEY(id_operacion_b0,organizacion_id,id_operacion) REFERENCES b0_operacion_ia(id_operacion_b0,organizacion_id,id_operacion),
 ADD FOREIGN KEY(id_operacion_b0,id_caso_evaluacion,id_operacion) REFERENCES b0_operacion_ia(id_operacion_b0,id_caso_evaluacion,id_operacion),
 ADD CHECK(
  (organizacion_id IS NOT NULL AND id_caso_evaluacion IS NULL AND
   ((id_operacion IS NULL AND id_operacion_b0 IS NULL)
    OR(id_operacion IS NOT NULL AND id_operacion_b0 IS NOT NULL)))
  OR(organizacion_id IS NULL AND id_caso_evaluacion IS NOT NULL
   AND id_operacion_b0 IS NOT NULL AND id_operacion IS NOT NULL));

CREATE OR REPLACE FUNCTION exigir_conjunto_etapas_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o b0_operacion_ia%ROWTYPE;
BEGIN
 IF TG_TABLE_NAME='b0_operacion_ia' THEN
  IF NEW.estado IN('LIQUIDADA','CANCELADA') AND EXISTS(SELECT 1 FROM b0_etapa_operacion_ia e
   WHERE e.id_operacion_b0=NEW.id_operacion_b0 AND e.estado NOT IN('MEDIDA','NO_INVOCADA'))
   THEN RAISE EXCEPTION 'El conjunto conserva etapas facturables pendientes';END IF;
 ELSE
  SELECT * INTO STRICT o FROM b0_operacion_ia WHERE id_operacion_b0=NEW.id_operacion_b0 FOR NO KEY UPDATE;
  IF o.estado NOT IN('EN_PROCESO','INCIERTA') THEN RAISE EXCEPTION 'La operación cerrada no admite etapas';END IF;
 END IF;
 RETURN NEW;
END;$$;
CREATE OR REPLACE FUNCTION verificar_origen_medicion_etapa_ia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.etapa_clave IS NOT NULL AND NOT EXISTS(SELECT 1 FROM b0_etapa_operacion_ia e
  WHERE e.id_operacion_b0=NEW.id_operacion_b0 AND e.clave=NEW.etapa_clave
   AND e.proveedor=NEW.proveedor AND e.estado IN('RECLAMADA','INCIERTA'))
 THEN RAISE EXCEPTION 'La medición conserva el claim exacto' USING ERRCODE='23514';END IF;
 RETURN NEW;
END;$$;
CREATE OR REPLACE FUNCTION verificar_mediciones_etapa_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t text;
BEGIN
 IF NEW.estado='MEDIDA' THEN
  IF jsonb_typeof(NEW.mediciones)<>'array' OR jsonb_array_length(NEW.mediciones)=0 THEN RAISE EXCEPTION 'Faltan mediciones';END IF;
  FOR t IN SELECT value FROM jsonb_array_elements_text(NEW.mediciones) LOOP
   IF NOT EXISTS(SELECT 1 FROM b0_medicion_tecnica m WHERE m.id_operacion_b0=NEW.id_operacion_b0
     AND m.id_medicion=t::uuid AND m.etapa_clave=NEW.clave AND m.proveedor=NEW.proveedor)
   THEN RAISE EXCEPTION 'La medición conserva su etapa y padre' USING ERRCODE='23514';END IF;
  END LOOP;
  IF jsonb_array_length(NEW.mediciones)<>(SELECT count(DISTINCT value::uuid) FROM jsonb_array_elements_text(NEW.mediciones))
   THEN RAISE EXCEPTION 'Mediciones duplicadas';END IF;
 END IF;
 RETURN NEW;
END;$$;

CREATE OR REPLACE FUNCTION conservar_etapa_operacion_ia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN RAISE EXCEPTION 'La etapa conserva su claim y hechos' USING ERRCODE='23514'; END IF;
 IF ROW(NEW.id_operacion_b0,NEW.id_caso_evaluacion,NEW.organizacion_id,NEW.id_operacion,NEW.etapa,NEW.clave,NEW.proveedor,NEW.huella_peticion,NEW.programada_en)
   IS DISTINCT FROM ROW(OLD.id_operacion_b0,OLD.id_caso_evaluacion,OLD.organizacion_id,OLD.id_operacion,OLD.etapa,OLD.clave,OLD.proveedor,OLD.huella_peticion,OLD.programada_en)
   OR OLD.estado IN('MEDIDA','NO_INVOCADA')
   OR NOT ((OLD.estado='PENDIENTE' AND NEW.estado IN('RECLAMADA','NO_INVOCADA')) OR (OLD.estado='RECLAMADA' AND NEW.estado IN('MEDIDA','NO_INVOCADA','INCIERTA')) OR (OLD.estado='INCIERTA' AND NEW.estado IN('MEDIDA','NO_INVOCADA')))
   OR (OLD.reclamada_en IS NOT NULL AND NEW.reclamada_en IS DISTINCT FROM OLD.reclamada_en)
 THEN RAISE EXCEPTION 'La etapa no admite otra identidad, claim o resolución' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE OR REPLACE FUNCTION b0_conservar_antecedente() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'B0 conserva antecedentes inmutables';END;$$;
CREATE FUNCTION conservar_identidad_operacion_b0_351() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.id_operacion_b0,NEW.organizacion_id,NEW.id_caso_evaluacion,NEW.id_operacion,NEW.id_persona,NEW.id_rol,
  NEW.perfil,NEW.version_perfil,NEW.maximo_creditos,NEW.maximo_tecnico_usd,NEW.id_fondo,NEW.admitida_en,
  NEW.cuenta_evaluacion,NEW.concesion_evaluacion,NEW.autorizacion_evaluacion,NEW.huella_peticion,NEW.huella_origen)
 IS DISTINCT FROM ROW(OLD.id_operacion_b0,OLD.organizacion_id,OLD.id_caso_evaluacion,OLD.id_operacion,OLD.id_persona,OLD.id_rol,
  OLD.perfil,OLD.version_perfil,OLD.maximo_creditos,OLD.maximo_tecnico_usd,OLD.id_fondo,OLD.admitida_en,
  OLD.cuenta_evaluacion,OLD.concesion_evaluacion,OLD.autorizacion_evaluacion,OLD.huella_peticion,OLD.huella_origen)
 THEN RAISE EXCEPTION 'La operación conserva ámbito, versión, fuente y máximo';END IF;
 RETURN NEW;
END;$$;
CREATE TRIGGER b0_identidad_operacion_351 BEFORE UPDATE ON b0_operacion_ia FOR EACH ROW EXECUTE FUNCTION conservar_identidad_operacion_b0_351();
CREATE FUNCTION comprobar_admision_evaluacion_b0() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.id_caso_evaluacion IS NOT NULL AND NOT EXISTS(
  SELECT 1 FROM b0_evaluacion_caso c JOIN b0_evaluacion_financiacion f ON f.plan=c.plan AND f.version_plan=c.version_plan
   JOIN b0_atribucion_plataforma g ON g.plan=c.plan AND g.version_plan=c.version_plan
  WHERE c.id=NEW.id_caso_evaluacion AND f.id=NEW.autorizacion_evaluacion AND f.fondo=NEW.id_fondo
   AND g.id=NEW.concesion_evaluacion AND g.cuenta=NEW.cuenta_evaluacion
   AND g.atribucion='OPERACION_IA_PLATFORM' AND(g.caso IS NULL OR g.caso=c.id))
 THEN RAISE EXCEPTION 'La admisión exige caso, cuenta, atribución y fondo del mismo plan';END IF;
 RETURN NEW;
END;$$;
CREATE TRIGGER b0_admision_eval_351 BEFORE INSERT ON b0_operacion_ia FOR EACH ROW EXECUTE FUNCTION comprobar_admision_evaluacion_b0();

ALTER TABLE b0_evaluacion_financiacion ADD COLUMN revocada_por bigint REFERENCES cuenta_acceso(id_cuenta_acceso),
 ADD COLUMN concesion_revocacion uuid REFERENCES b0_atribucion_plataforma(id),ADD COLUMN motivo_revocacion text,
 ADD CHECK(num_nonnulls(revocada_en,revocada_por,concesion_revocacion,motivo_revocacion) IN(0,4));
CREATE FUNCTION conservar_financiacion_evaluacion_b0() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN RAISE EXCEPTION 'La financiación conserva su antecedente';END IF;
 IF OLD.revocada_en IS NOT NULL OR NEW.revocada_en IS NULL
 OR(to_jsonb(NEW)-'revocada_en'-'revocada_por'-'concesion_revocacion'-'motivo_revocacion')
 IS DISTINCT FROM(to_jsonb(OLD)-'revocada_en'-'revocada_por'-'concesion_revocacion'-'motivo_revocacion')
 THEN RAISE EXCEPTION 'Sólo se añade una revocación acreditada';END IF;
 RETURN NEW;
END;$$;
CREATE TRIGGER b0_financiacion_conservar_351 BEFORE UPDATE OR DELETE ON b0_evaluacion_financiacion FOR EACH ROW EXECUTE FUNCTION conservar_financiacion_evaluacion_b0();
CREATE TRIGGER b0_financiacion_truncate_351 BEFORE TRUNCATE ON b0_evaluacion_financiacion FOR EACH STATEMENT EXECUTE FUNCTION conservar_financiacion_evaluacion_b0();
