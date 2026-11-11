-- Un único journal/ledger físico, con dueño independiente del job.
CREATE TABLE b0_propietario_binario (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 organizacion_id bigint REFERENCES organizacion(id_organizacion),
 caso uuid REFERENCES b0_evaluacion_caso(id),
 CHECK(num_nonnulls(organizacion_id,caso)=1),
 UNIQUE(organizacion_id),UNIQUE(caso),UNIQUE(id,organizacion_id),UNIQUE(id,caso)
);
INSERT INTO b0_propietario_binario(organizacion_id) SELECT id_organizacion FROM organizacion;
INSERT INTO b0_propietario_binario(caso) SELECT id FROM b0_evaluacion_caso;
CREATE FUNCTION crear_propietario_binario_b0() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='organizacion' THEN
  INSERT INTO b0_propietario_binario(organizacion_id) VALUES(NEW.id_organizacion);
 ELSE INSERT INTO b0_propietario_binario(caso) VALUES(NEW.id);END IF;
 RETURN NEW;
END;$$;
CREATE TRIGGER b0_dueno_org AFTER INSERT ON organizacion FOR EACH ROW EXECUTE FUNCTION crear_propietario_binario_b0();
CREATE TRIGGER b0_dueno_caso AFTER INSERT ON b0_evaluacion_caso FOR EACH ROW EXECUTE FUNCTION crear_propietario_binario_b0();
CREATE FUNCTION b0_resolver_dueno(org bigint,caso_ref uuid) RETURNS uuid LANGUAGE sql STABLE AS $$
 SELECT id FROM b0_propietario_binario WHERE
 (org IS NOT NULL AND caso_ref IS NULL AND organizacion_id=org)
 OR(org IS NULL AND caso_ref IS NOT NULL AND caso=caso_ref)
$$;

-- La ampliación de identidad sólo puede añadir el dueño comprobado a contenido idéntico.
CREATE FUNCTION ampliar_dueno_binario_351() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND OLD.dueno_binario IS NULL AND NEW.dueno_binario=b0_resolver_dueno(OLD.organizacion_id,NULL)
 AND (to_jsonb(NEW)-'dueno_binario'-'id_caso_evaluacion'-'operacion_evaluacion_b0')
  IS NOT DISTINCT FROM (to_jsonb(OLD)-'dueno_binario'-'id_caso_evaluacion'-'operacion_evaluacion_b0')
 THEN RETURN NEW;END IF;
 RAISE EXCEPTION 'La ampliación conserva contenido y dueño original';
END;$$;

ALTER TABLE intento_salida_binaria ADD COLUMN dueno_binario uuid,ADD COLUMN id_caso_evaluacion uuid;
-- Sustituir temporalmente la guarda por otra que sólo admite la adición de FK de esta migración.
DROP TRIGGER intento_binario_c6_conservar ON intento_salida_binaria;
CREATE TRIGGER intento_binario_c6_conservar BEFORE UPDATE OR DELETE ON intento_salida_binaria
 FOR EACH ROW EXECUTE FUNCTION ampliar_dueno_binario_351();
UPDATE intento_salida_binaria SET dueno_binario=b0_resolver_dueno(organizacion_id,NULL);
DROP TRIGGER intento_binario_c6_conservar ON intento_salida_binaria;
CREATE TRIGGER intento_binario_c6_conservar BEFORE UPDATE OR DELETE ON intento_salida_binaria
 FOR EACH ROW EXECUTE FUNCTION conservar_identidad_intento_binario_c6();
ALTER TABLE intento_salida_binaria ALTER COLUMN dueno_binario SET NOT NULL,
 ALTER COLUMN organizacion_id DROP NOT NULL,
 ADD FOREIGN KEY(dueno_binario) REFERENCES b0_propietario_binario(id),
 ADD FOREIGN KEY(dueno_binario,organizacion_id) REFERENCES b0_propietario_binario(id,organizacion_id),
 ADD FOREIGN KEY(dueno_binario,id_caso_evaluacion) REFERENCES b0_propietario_binario(id,caso),
 ADD CHECK(num_nonnulls(organizacion_id,id_caso_evaluacion)=1),
 ADD UNIQUE(dueno_binario,operacion,numero),ADD UNIQUE(id_intento,dueno_binario);
-- Reemplazar el CHECK original de namespace por las dos formas explícitas.
DO $$ DECLARE c record;BEGIN
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='intento_salida_binaria'::regclass
  AND contype='c' AND pg_get_constraintdef(oid) LIKE '%clave_proveedor LIKE%'
 LOOP EXECUTE format('ALTER TABLE intento_salida_binaria DROP CONSTRAINT %I',c.conname);END LOOP;
END;$$;
ALTER TABLE intento_salida_binaria ADD CHECK(
 (organizacion_id IS NOT NULL AND clave_proveedor LIKE 'tenant/'||organizacion_id::text||'/salidas/'||operacion::text||'/%')
 OR(id_caso_evaluacion IS NOT NULL AND clave_proveedor LIKE 'plataforma/evaluacion/'||id_caso_evaluacion::text||'/salidas/'||operacion::text||'/%'));
CREATE FUNCTION conservar_dueno_intento_351() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND ROW(NEW.dueno_binario,NEW.id_caso_evaluacion) IS DISTINCT FROM ROW(OLD.dueno_binario,OLD.id_caso_evaluacion)
 THEN RAISE EXCEPTION 'El intento conserva dueño';END IF;
 RETURN NEW;
END;$$;
CREATE TRIGGER intento_dueno_351 BEFORE UPDATE ON intento_salida_binaria FOR EACH ROW EXECUTE FUNCTION conservar_dueno_intento_351();

DO $$ DECLARE t text;c record;defs jsonb;BEGIN
 FOREACH t IN ARRAY ARRAY['b0_reserva_almacenamiento','b0_objeto_almacenamiento','b0_presencia_objeto_almacenamiento'] LOOP
  EXECUTE format('ALTER TABLE %I ADD COLUMN dueno_binario uuid,ADD COLUMN id_caso_evaluacion uuid',t);
 END LOOP;
END;$$;
ALTER TABLE b0_reserva_almacenamiento ADD COLUMN operacion_evaluacion_b0 uuid REFERENCES b0_operacion_ia(id_operacion_b0);
-- Guards instaladas antes de cada backfill; todos los demás UPDATE/DELETE siguen prohibidos.
DROP TRIGGER tg_b0_objeto_almacen_inmutable ON b0_objeto_almacenamiento;
CREATE TRIGGER tg_b0_objeto_almacen_inmutable BEFORE UPDATE OR DELETE ON b0_objeto_almacenamiento FOR EACH ROW EXECUTE FUNCTION ampliar_dueno_binario_351();
DROP TRIGGER b0_prevision_conservar ON b0_reserva_almacenamiento;
CREATE TRIGGER b0_prevision_conservar BEFORE UPDATE OR DELETE ON b0_reserva_almacenamiento FOR EACH ROW EXECUTE FUNCTION ampliar_dueno_binario_351();
DROP TRIGGER b0_presencia_conservar ON b0_presencia_objeto_almacenamiento;
CREATE TRIGGER b0_presencia_conservar BEFORE UPDATE OR DELETE ON b0_presencia_objeto_almacenamiento FOR EACH ROW EXECUTE FUNCTION ampliar_dueno_binario_351();
UPDATE b0_reserva_almacenamiento SET dueno_binario=b0_resolver_dueno(organizacion_id,NULL);
UPDATE b0_objeto_almacenamiento SET dueno_binario=b0_resolver_dueno(organizacion_id,NULL);
UPDATE b0_presencia_objeto_almacenamiento SET dueno_binario=b0_resolver_dueno(organizacion_id,NULL);
DROP TRIGGER tg_b0_objeto_almacen_inmutable ON b0_objeto_almacenamiento;
CREATE TRIGGER tg_b0_objeto_almacen_inmutable BEFORE UPDATE OR DELETE ON b0_objeto_almacenamiento FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
DROP TRIGGER b0_prevision_conservar ON b0_reserva_almacenamiento;
CREATE TRIGGER b0_prevision_conservar BEFORE UPDATE OR DELETE ON b0_reserva_almacenamiento FOR EACH ROW EXECUTE FUNCTION conservar_prevision_almacenamiento_ia();
DROP TRIGGER b0_presencia_conservar ON b0_presencia_objeto_almacenamiento;
CREATE TRIGGER b0_presencia_conservar BEFORE UPDATE OR DELETE ON b0_presencia_objeto_almacenamiento FOR EACH ROW EXECUTE FUNCTION conservar_presencia_almacenamiento_ia();
-- Sustituir PKs conservando FKs comerciales válidas.
DO $$ DECLARE c record;BEGIN
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='b0_presencia_objeto_almacenamiento'::regclass
 AND confrelid='b0_objeto_almacenamiento'::regclass AND contype='f'
 LOOP EXECUTE format('ALTER TABLE b0_presencia_objeto_almacenamiento DROP CONSTRAINT %I',c.conname);END LOOP;
END;$$;
ALTER TABLE b0_objeto_almacenamiento ADD UNIQUE(organizacion_id,huella_identidad);
ALTER TABLE b0_objeto_almacenamiento DROP CONSTRAINT b0_objeto_almacenamiento_pkey;
ALTER TABLE b0_objeto_almacenamiento ADD PRIMARY KEY(dueno_binario,huella_identidad);
ALTER TABLE b0_presencia_objeto_almacenamiento ADD FOREIGN KEY(organizacion_id,huella_identidad)
 REFERENCES b0_objeto_almacenamiento(organizacion_id,huella_identidad);
ALTER TABLE b0_presencia_objeto_almacenamiento DROP CONSTRAINT b0_presencia_objeto_almacenamiento_pkey;
ALTER TABLE b0_presencia_objeto_almacenamiento ADD PRIMARY KEY(dueno_binario,huella_identidad),
 ADD UNIQUE(organizacion_id,huella_identidad),ADD FOREIGN KEY(dueno_binario,huella_identidad)
 REFERENCES b0_objeto_almacenamiento(dueno_binario,huella_identidad);
ALTER TABLE b0_reserva_almacenamiento DROP CONSTRAINT b0_reserva_almacenamiento_pkey;
ALTER TABLE b0_reserva_almacenamiento ADD PRIMARY KEY(dueno_binario,id_reserva),ADD UNIQUE(organizacion_id,id_reserva);
DO $$ DECLARE t text;BEGIN
 FOREACH t IN ARRAY ARRAY['b0_reserva_almacenamiento','b0_objeto_almacenamiento','b0_presencia_objeto_almacenamiento'] LOOP
  EXECUTE format('ALTER TABLE %I ALTER COLUMN organizacion_id DROP NOT NULL,ALTER COLUMN dueno_binario SET NOT NULL',t);
  EXECUTE format('ALTER TABLE %I ADD FOREIGN KEY(dueno_binario) REFERENCES b0_propietario_binario(id)',t);
  EXECUTE format('ALTER TABLE %I ADD FOREIGN KEY(dueno_binario,organizacion_id) REFERENCES b0_propietario_binario(id,organizacion_id)',t);
  EXECUTE format('ALTER TABLE %I ADD FOREIGN KEY(dueno_binario,id_caso_evaluacion) REFERENCES b0_propietario_binario(id,caso)',t);
  EXECUTE format('ALTER TABLE %I ADD CHECK(num_nonnulls(organizacion_id,id_caso_evaluacion)=1)',t);
 END LOOP;
END;$$;
ALTER TABLE b0_reserva_almacenamiento ALTER COLUMN id_persona DROP NOT NULL,ALTER COLUMN id_rol DROP NOT NULL,
 ALTER COLUMN necesidad DROP NOT NULL,ALTER COLUMN bytes_retenidos DROP NOT NULL;
ALTER TABLE b0_reserva_almacenamiento DROP CONSTRAINT ck_b0_prevision_forma;
ALTER TABLE b0_reserva_almacenamiento ADD CONSTRAINT ck_b0_prevision_forma CHECK(
 (organizacion_id IS NOT NULL AND id_persona IS NOT NULL AND id_rol IS NOT NULL AND necesidad IS NOT NULL
  AND bytes_retenidos IS NOT NULL AND operacion_evaluacion_b0 IS NULL AND
   ((modalidad='EXACTA' AND categoria<>'DERIVADA_IA' AND bytes IS NOT NULL AND job_ia IS NULL
     AND techo_bytes IS NULL AND perfil_ia IS NULL AND version_perfil_ia IS NULL)
    OR(modalidad='PREVISION_IA' AND job_ia IS NOT NULL AND techo_bytes IS NOT NULL
      AND perfil_ia IS NOT NULL AND version_perfil_ia IS NOT NULL AND namespace_hash IS NOT NULL AND version_fisica IS NOT NULL AND categoria='DERIVADA_IA'
      AND necesidad='ORDINARIA' AND bytes_retenidos=techo_bytes AND bytes_fisicos_retenidos=techo_bytes)))
 OR(id_caso_evaluacion IS NOT NULL AND num_nonnulls(id_persona,id_rol,necesidad,bytes_retenidos,
   version_politica,job_ia,perfil_ia,version_perfil_ia)=0 AND namespace_hash IS NOT NULL AND version_fisica IS NOT NULL
  AND((modalidad='EXACTA' AND categoria<>'DERIVADA_IA' AND bytes IS NOT NULL AND techo_bytes IS NULL AND operacion_evaluacion_b0 IS NULL)
    OR(modalidad='PREVISION_IA' AND techo_bytes IS NOT NULL AND operacion_evaluacion_b0 IS NOT NULL
      AND categoria='DERIVADA_IA' AND bytes_fisicos_retenidos=techo_bytes)))
);
ALTER TABLE b0_reserva_almacenamiento ADD CHECK(modalidad<>'PREVISION_IA' OR bytes IS NULL OR bytes<=techo_bytes),
 ADD FOREIGN KEY(operacion_evaluacion_b0,id_caso_evaluacion)
 REFERENCES b0_operacion_ia(id_operacion_b0,id_caso_evaluacion);

CREATE TABLE b0_archivo_evaluacion (
 archivo uuid PRIMARY KEY,caso uuid NOT NULL REFERENCES b0_evaluacion_caso(id),
 dueno_binario uuid NOT NULL,id_intento bigint NOT NULL UNIQUE,
 tipo varchar(24) NOT NULL CHECK(tipo IN('ORIGINAL_CORPUS','RESULTADO','EVIDENCIA_QC')),
 operacion_b0 uuid,etapa_clave varchar(64),estado varchar(20) NOT NULL CHECK(estado IN('PREPARADA','VERIFICADA')),
 registrada_en timestamptz NOT NULL DEFAULT clock_timestamp(),verificada_en timestamptz,
 FOREIGN KEY(dueno_binario,caso) REFERENCES b0_propietario_binario(id,caso),
 FOREIGN KEY(id_intento,dueno_binario) REFERENCES intento_salida_binaria(id_intento,dueno_binario),
 FOREIGN KEY(operacion_b0,caso) REFERENCES b0_operacion_ia(id_operacion_b0,id_caso_evaluacion),
 FOREIGN KEY(operacion_b0,etapa_clave) REFERENCES b0_etapa_operacion_ia(id_operacion_b0,clave),
 CHECK((tipo='ORIGINAL_CORPUS' AND operacion_b0 IS NULL AND etapa_clave IS NULL)
  OR(tipo<>'ORIGINAL_CORPUS' AND operacion_b0 IS NOT NULL)),
 CHECK(tipo<>'EVIDENCIA_QC' OR etapa_clave IS NOT NULL),
 CHECK((estado='VERIFICADA')=(verificada_en IS NOT NULL))
);
CREATE FUNCTION conservar_archivo_evaluacion_b0() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE i intento_salida_binaria%ROWTYPE;c b0_evaluacion_caso%ROWTYPE;
BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN RAISE EXCEPTION 'Se conserva corpus y evidencia';END IF;
 SELECT * INTO STRICT i FROM intento_salida_binaria WHERE id_intento=NEW.id_intento;
 SELECT * INTO STRICT c FROM b0_evaluacion_caso WHERE id=NEW.caso;
 IF i.id_caso_evaluacion IS DISTINCT FROM NEW.caso OR i.dueno_binario<>NEW.dueno_binario
  OR(NEW.tipo='ORIGINAL_CORPUS' AND (NEW.archivo<>c.archivo_original OR i.huella<>c.huella OR i.bytes<>c.bytes_original OR i.content_type<>c.mime_original))
 THEN RAISE EXCEPTION 'El archivo exige su caso, original e intento exactos';END IF;
 IF TG_OP='UPDATE' AND (OLD.estado<>'PREPARADA' OR NEW.estado<>'VERIFICADA'
  OR(to_jsonb(NEW)-'estado'-'verificada_en') IS DISTINCT FROM(to_jsonb(OLD)-'estado'-'verificada_en'))
 THEN RAISE EXCEPTION 'La custodia conserva identidad y verificación única';END IF;
 IF NEW.estado='VERIFICADA' AND(i.estado NOT IN('ESCRITO_VERIFICADO','VINCULADO') OR i.escritura_incierta)
 THEN RAISE EXCEPTION 'La presencia no se presume';END IF;
 RETURN NEW;
END;$$;
CREATE TRIGGER b0_archivo_eval_conservar BEFORE INSERT OR UPDATE OR DELETE ON b0_archivo_evaluacion FOR EACH ROW EXECUTE FUNCTION conservar_archivo_evaluacion_b0();
CREATE TRIGGER b0_archivo_eval_truncate BEFORE TRUNCATE ON b0_archivo_evaluacion FOR EACH STATEMENT EXECUTE FUNCTION conservar_archivo_evaluacion_b0();
DROP FUNCTION ampliar_dueno_binario_351();

CREATE UNIQUE INDEX uq_b0_resultado_evaluacion ON b0_archivo_evaluacion(operacion_b0) WHERE tipo='RESULTADO';

CREATE FUNCTION conservar_dueno_almacenamiento_b0() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.dueno_binario,NEW.organizacion_id,NEW.id_caso_evaluacion)
  IS DISTINCT FROM ROW(OLD.dueno_binario,OLD.organizacion_id,OLD.id_caso_evaluacion)
 THEN RAISE EXCEPTION 'El objeto y reserva conservan dueño y ámbito';END IF;
 IF TG_TABLE_NAME='b0_reserva_almacenamiento' THEN
  IF NEW.operacion_evaluacion_b0 IS DISTINCT FROM OLD.operacion_evaluacion_b0 THEN RAISE EXCEPTION 'La reserva conserva su operación';END IF;
 END IF;
 RETURN NEW;
END;$$;
CREATE TRIGGER b0_reserva_dueno_351 BEFORE UPDATE ON b0_reserva_almacenamiento FOR EACH ROW EXECUTE FUNCTION conservar_dueno_almacenamiento_b0();
CREATE TRIGGER b0_objeto_dueno_351 BEFORE UPDATE ON b0_objeto_almacenamiento FOR EACH ROW EXECUTE FUNCTION conservar_dueno_almacenamiento_b0();
CREATE TRIGGER b0_presencia_dueno_351 BEFORE UPDATE ON b0_presencia_objeto_almacenamiento FOR EACH ROW EXECUTE FUNCTION conservar_dueno_almacenamiento_b0();

CREATE TRIGGER b0_dueno_inmutable BEFORE UPDATE OR DELETE ON b0_propietario_binario FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
CREATE TRIGGER b0_dueno_inmutable_truncate BEFORE TRUNCATE ON b0_propietario_binario FOR EACH STATEMENT EXECUTE FUNCTION b0_conservar_antecedente();
