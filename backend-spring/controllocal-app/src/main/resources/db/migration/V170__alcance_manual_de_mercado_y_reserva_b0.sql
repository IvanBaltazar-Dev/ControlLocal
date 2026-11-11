-- CAT-R24/R25; ampliación4.5; plan9.1 GI04/05. Sin grants, tarifas o activaciones productivas.
ALTER TABLE b0_atribucion_plataforma ADD COLUMN recurso_mercado varchar(40);
DO $$ DECLARE n integer;r record;BEGIN
 SELECT count(*) INTO n FROM pg_constraint c WHERE c.conrelid='b0_atribucion_plataforma'::regclass AND c.contype='c'
 AND c.conkey=ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid=c.conrelid AND attname='atribucion')]::smallint[];
 IF n<>1 THEN RAISE EXCEPTION 'Se esperaba el control único del vocabulario de atribución';END IF;
 FOR r IN SELECT conname FROM pg_constraint c WHERE c.conrelid='b0_atribucion_plataforma'::regclass AND c.contype='c'
 AND c.conkey=ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid=c.conrelid AND attname='atribucion')]::smallint[]
 LOOP EXECUTE format('ALTER TABLE b0_atribucion_plataforma DROP CONSTRAINT %I',r.conname);END LOOP;
END;$$;
ALTER TABLE b0_atribucion_plataforma DROP CONSTRAINT ck_b0_atribucion_ambito,
 ADD CONSTRAINT ck_b0_atribucion_tipo CHECK(atribucion IN('FINANCIERO_PLATFORM','OPERACION_IA_PLATFORM','BROX_PLATFORM','INVESTIGACION_PLATFORM','REVISION_MERCADO_PLATFORM')),
 ADD CONSTRAINT ck_b0_atribucion_mercado CHECK(recurso_mercado IS NULL OR recurso_mercado='MERCADO_MANUAL_I0'),
 ADD CONSTRAINT ck_b0_atribucion_ambito CHECK(
 (recurso_mercado IS NULL AND recurso_compra IS NULL AND plan IS NOT NULL AND version_plan IS NOT NULL
  AND atribucion IN('FINANCIERO_PLATFORM','OPERACION_IA_PLATFORM','BROX_PLATFORM') AND (atribucion='FINANCIERO_PLATFORM')=(fondo IS NOT NULL))
 OR(recurso_mercado IS NULL AND recurso_compra='COMPRAS_MANUALES_WEB_MVP' AND atribucion='FINANCIERO_PLATFORM'
  AND plan IS NULL AND version_plan IS NULL AND caso IS NULL AND fondo IS NULL AND finalidad='GESTION_COMPRAS_MANUALES_BROX')
 OR(recurso_mercado='MERCADO_MANUAL_I0' AND recurso_compra IS NULL AND plan IS NULL AND version_plan IS NULL
  AND caso IS NULL AND fondo IS NULL AND finalidad='EVIDENCIA_MERCADO_BROX'
  AND atribucion IN('FINANCIERO_PLATFORM','INVESTIGACION_PLATFORM','REVISION_MERCADO_PLATFORM')));
ALTER TABLE b0_atribucion_plataforma ADD CONSTRAINT ck_b0_mercado_acciones CHECK(recurso_mercado IS NULL OR
 (atribucion='INVESTIGACION_PLATFORM' AND acciones<@'["CONSULTAR_TRABAJO","TOMAR_TRABAJO","ENTREGAR_EVIDENCIA"]'::jsonb AND campos<@'["METADATOS_TRABAJO","CONTEXTO_MINIMO","EVIDENCIA_PUBLICA"]'::jsonb)
 OR(atribucion='REVISION_MERCADO_PLATFORM' AND acciones<@'["CONSULTAR_TRABAJO","CONFIGURAR_MERCADO","REVISAR_EVIDENCIA","PUBLICAR_REFERENCIA"]'::jsonb AND campos<@'["METADATOS_TRABAJO","CONFIGURACION","EVIDENCIA_PUBLICA"]'::jsonb)
 OR(atribucion='FINANCIERO_PLATFORM' AND acciones<@'["CONSULTAR_TRABAJO","CONFIGURAR_REGLA_MANUAL","CONCILIAR_TRABAJO"]'::jsonb AND campos<@'["METADATOS_TRABAJO","LEDGER"]'::jsonb));
CREATE OR REPLACE FUNCTION conservar_atribucion_plataforma_b0() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN RAISE EXCEPTION 'La atribución conserva su antecedente';END IF;
 IF ROW(NEW.id,NEW.otorgante,NEW.cuenta,NEW.atribucion,NEW.plan,NEW.version_plan,NEW.caso,NEW.fondo,
 NEW.finalidad,NEW.acciones,NEW.campos,NEW.desde,NEW.hasta,NEW.evidencia,NEW.huella,NEW.decision,NEW.registrada_en,NEW.recurso_compra,NEW.recurso_mercado)
 IS DISTINCT FROM ROW(OLD.id,OLD.otorgante,OLD.cuenta,OLD.atribucion,OLD.plan,OLD.version_plan,OLD.caso,OLD.fondo,
 OLD.finalidad,OLD.acciones,OLD.campos,OLD.desde,OLD.hasta,OLD.evidencia,OLD.huella,OLD.decision,OLD.registrada_en,OLD.recurso_compra,OLD.recurso_mercado)
 OR OLD.revocada_en IS NOT NULL OR NEW.revocada_en IS NULL THEN RAISE EXCEPTION 'Sólo se añade la revocación acreditada';END IF;
 RETURN NEW;
END;$$;

-- Presupuesto operativo en rondas completas enteras, no dólares ficticios ni otra bolsa comercial.
CREATE TABLE b0_regla_trabajo_manual (
 id uuid NOT NULL,version varchar(80) NOT NULL,unidad varchar(80) NOT NULL CHECK(unidad='RONDA_COMPLETA'),
 creditos_por_ronda numeric(38,12) NOT NULL CHECK(creditos_por_ronda>0),
 cota_rondas numeric(38,12) NOT NULL CHECK(cota_rondas>0 AND cota_rondas=trunc(cota_rondas)),
 reservadas numeric(38,12) NOT NULL DEFAULT 0 CHECK(reservadas>=0 AND reservadas=trunc(reservadas)),realizadas numeric(38,12) NOT NULL DEFAULT 0 CHECK(realizadas>=0 AND realizadas=trunc(realizadas)),
 desde timestamptz NOT NULL,hasta timestamptz NOT NULL,decision uuid NOT NULL REFERENCES b0_decision_plataforma(id),
 responsable bigint NOT NULL REFERENCES cuenta_acceso(id_cuenta_acceso),evidencia text NOT NULL CHECK(length(btrim(evidencia))>0),
 huella varchar(64) NOT NULL CHECK(huella~'^[0-9a-f]{64}$'),habilitada boolean NOT NULL DEFAULT false,
 PRIMARY KEY(id,version),CHECK(hasta>desde),CHECK(reservadas+realizadas<=cota_rondas)
);
CREATE FUNCTION conservar_regla_trabajo_manual_b0() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN RAISE EXCEPTION 'La regla manual conserva su historia';END IF;
 IF ROW(NEW.id,NEW.version,NEW.unidad,NEW.creditos_por_ronda,NEW.cota_rondas,NEW.desde,NEW.hasta,NEW.decision,NEW.responsable,NEW.evidencia,NEW.huella,NEW.habilitada)
 IS DISTINCT FROM ROW(OLD.id,OLD.version,OLD.unidad,OLD.creditos_por_ronda,OLD.cota_rondas,OLD.desde,OLD.hasta,OLD.decision,OLD.responsable,OLD.evidencia,OLD.huella,OLD.habilitada)
 THEN RAISE EXCEPTION 'Modificar la regla requiere otra versión aprobada';END IF;RETURN NEW;END;$$;
CREATE TRIGGER regla_manual_b0_conservar BEFORE UPDATE OR DELETE ON b0_regla_trabajo_manual FOR EACH ROW EXECUTE FUNCTION conservar_regla_trabajo_manual_b0();
CREATE TRIGGER regla_manual_b0_truncate BEFORE TRUNCATE ON b0_regla_trabajo_manual FOR EACH STATEMENT EXECUTE FUNCTION conservar_regla_trabajo_manual_b0();

ALTER TABLE b0_operacion_ia ADD COLUMN modalidad varchar(20) NOT NULL DEFAULT 'API',
 ADD COLUMN regla_manual uuid,ADD COLUMN version_regla_manual varchar(80),ADD COLUMN rondas_previstas numeric(38,12),
 ADD COLUMN aceptada_en timestamptz,ADD COLUMN rondas_acreditadas numeric(38,12),
 ALTER COLUMN maximo_tecnico_usd DROP NOT NULL,ALTER COLUMN id_fondo DROP NOT NULL,
 DROP CONSTRAINT ck_b0_forma_operacion,
 ADD FOREIGN KEY(regla_manual,version_regla_manual) REFERENCES b0_regla_trabajo_manual(id,version),
 ADD CONSTRAINT ck_b0_modalidad_operacion CHECK(
 (modalidad='API' AND maximo_tecnico_usd IS NOT NULL AND id_fondo IS NOT NULL AND
  num_nonnulls(regla_manual,version_regla_manual,rondas_previstas,aceptada_en,rondas_acreditadas)=0 AND
  ((organizacion_id IS NOT NULL AND id_persona IS NOT NULL AND id_rol IS NOT NULL AND perfil IS NOT NULL AND version_perfil IS NOT NULL AND maximo_creditos IS NOT NULL
    AND num_nonnulls(id_caso_evaluacion,cuenta_evaluacion,concesion_evaluacion,autorizacion_evaluacion)=0)
   OR(organizacion_id IS NULL AND num_nonnulls(id_persona,id_rol,perfil,version_perfil,maximo_creditos,creditos_liquidados)=0
    AND num_nonnulls(id_caso_evaluacion,cuenta_evaluacion,concesion_evaluacion,autorizacion_evaluacion)=4)))
 OR(modalidad='MANUAL_I0' AND organizacion_id IS NOT NULL AND id_persona IS NOT NULL AND id_rol IS NOT NULL
  AND num_nonnulls(perfil,version_perfil,maximo_tecnico_usd,id_fondo,costo_tecnico_usd,id_caso_evaluacion,cuenta_evaluacion,concesion_evaluacion,autorizacion_evaluacion)=0
  AND num_nonnulls(regla_manual,version_regla_manual,rondas_previstas,aceptada_en)=4 AND rondas_previstas>0 AND rondas_previstas=trunc(rondas_previstas)
  AND maximo_creditos IS NOT NULL AND recurso_tipo='INVESTIGACION_MERCADO'
  AND (rondas_acreditadas IS NULL OR (rondas_acreditadas BETWEEN 0 AND rondas_previstas AND rondas_acreditadas=trunc(rondas_acreditadas)))
  AND ((estado IN('LIQUIDADA','CANCELADA'))=(rondas_acreditadas IS NOT NULL))));
CREATE OR REPLACE FUNCTION conservar_identidad_operacion_b0_351() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF ROW(NEW.id_operacion_b0,NEW.organizacion_id,NEW.id_caso_evaluacion,NEW.id_operacion,NEW.id_persona,NEW.id_rol,
 NEW.perfil,NEW.version_perfil,NEW.maximo_creditos,NEW.maximo_tecnico_usd,NEW.id_fondo,NEW.admitida_en,
 NEW.cuenta_evaluacion,NEW.concesion_evaluacion,NEW.autorizacion_evaluacion,NEW.huella_peticion,NEW.huella_origen,
 NEW.modalidad,NEW.regla_manual,NEW.version_regla_manual,NEW.rondas_previstas,NEW.aceptada_en,NEW.recurso_tipo,NEW.recurso_id,NEW.finalidad)
 IS DISTINCT FROM ROW(OLD.id_operacion_b0,OLD.organizacion_id,OLD.id_caso_evaluacion,OLD.id_operacion,OLD.id_persona,OLD.id_rol,
 OLD.perfil,OLD.version_perfil,OLD.maximo_creditos,OLD.maximo_tecnico_usd,OLD.id_fondo,OLD.admitida_en,
 OLD.cuenta_evaluacion,OLD.concesion_evaluacion,OLD.autorizacion_evaluacion,OLD.huella_peticion,OLD.huella_origen,
 OLD.modalidad,OLD.regla_manual,OLD.version_regla_manual,OLD.rondas_previstas,OLD.aceptada_en,OLD.recurso_tipo,OLD.recurso_id,OLD.finalidad)
 THEN RAISE EXCEPTION 'La operación conserva ámbito, versión, fuente y máximo';END IF;RETURN NEW;END;$$;
-- La precisión comercial coincide con PrecisionCapacidad.HALF_EVEN, incluso en empates.
CREATE FUNCTION precision_manual_b0(v numeric) RETURNS numeric LANGUAGE sql IMMUTABLE STRICT AS $$
 SELECT CASE WHEN v-trunc(v,12)=0.0000000000005 THEN trunc(v,12)+CASE WHEN mod(trunc(v,12)*1e12,2)=1 THEN 1e-12 ELSE 0 END ELSE round(v,12) END
$$;
CREATE FUNCTION comprobar_operacion_manual_b0() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE r b0_regla_trabajo_manual%ROWTYPE;BEGIN
 IF NEW.modalidad='MANUAL_I0' THEN
  SELECT * INTO r FROM b0_regla_trabajo_manual WHERE id=NEW.regla_manual AND version=NEW.version_regla_manual FOR SHARE;
  IF NOT FOUND OR NOT r.habilitada OR NEW.admitida_en<r.desde OR NEW.admitida_en>=r.hasta
   OR NEW.aceptada_en<>NEW.admitida_en OR NEW.maximo_creditos<>precision_manual_b0(r.creditos_por_ronda*NEW.rondas_previstas)
  THEN RAISE EXCEPTION 'La admisión manual exige regla vigente y máximo aceptado exacto';END IF;
 END IF;RETURN NEW;END;$$;
CREATE TRIGGER operacion_manual_b0_comprobar BEFORE INSERT ON b0_operacion_ia FOR EACH ROW EXECUTE FUNCTION comprobar_operacion_manual_b0();

CREATE FUNCTION presupuesto_rondas_manuales_b0() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.modalidad<>'MANUAL_I0' THEN RETURN NEW;END IF;
 IF TG_OP='INSERT' THEN
  UPDATE b0_regla_trabajo_manual SET reservadas=reservadas+NEW.rondas_previstas
   WHERE id=NEW.regla_manual AND version=NEW.version_regla_manual AND reservadas+realizadas+NEW.rondas_previstas<=cota_rondas;
  IF NOT FOUND THEN RAISE EXCEPTION 'No hay cota operativa manual disponible';END IF;
 ELSIF OLD.estado NOT IN('LIQUIDADA','CANCELADA') AND NEW.estado IN('LIQUIDADA','CANCELADA') THEN
  IF NEW.creditos_liquidados IS DISTINCT FROM (SELECT precision_manual_b0(creditos_por_ronda*NEW.rondas_acreditadas) FROM b0_regla_trabajo_manual WHERE id=NEW.regla_manual AND version=NEW.version_regla_manual) THEN RAISE EXCEPTION 'El cargo manual conserva regla y rondas completas acreditadas';END IF;
  UPDATE b0_regla_trabajo_manual SET reservadas=reservadas-NEW.rondas_previstas,realizadas=realizadas+NEW.rondas_acreditadas
   WHERE id=NEW.regla_manual AND version=NEW.version_regla_manual AND reservadas>=NEW.rondas_previstas;
  IF NOT FOUND THEN RAISE EXCEPTION 'La reserva manual no conserva su cota';END IF;
 ELSIF NEW.rondas_acreditadas IS DISTINCT FROM OLD.rondas_acreditadas OR OLD.estado IN('LIQUIDADA','CANCELADA') THEN
  RAISE EXCEPTION 'La conciliación manual conserva su cierre';
 END IF;RETURN NEW;END;$$;
CREATE TRIGGER operacion_manual_b0_cota AFTER INSERT OR UPDATE ON b0_operacion_ia FOR EACH ROW EXECUTE FUNCTION presupuesto_rondas_manuales_b0();
CREATE FUNCTION medicion_solo_api_b0() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF EXISTS(SELECT 1 FROM b0_operacion_ia WHERE id_operacion_b0=NEW.id_operacion_b0 AND modalidad='MANUAL_I0')
 THEN RAISE EXCEPTION 'Trabajo manual no admite tokens, etapas ni costo API ficticios';END IF;RETURN NEW;END;$$;
CREATE TRIGGER medicion_b0_modalidad BEFORE INSERT ON b0_medicion_tecnica FOR EACH ROW EXECUTE FUNCTION medicion_solo_api_b0();
CREATE TRIGGER etapa_b0_modalidad BEFORE INSERT ON b0_etapa_operacion_ia FOR EACH ROW EXECUTE FUNCTION medicion_solo_api_b0();
CREATE FUNCTION conservar_imputacion_manual_b0() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM b0_operacion_ia WHERE organizacion_id=OLD.organizacion_id AND id_operacion=OLD.id_operacion AND modalidad='MANUAL_I0') THEN RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;END IF;
 IF TG_OP='DELETE' OR ROW(NEW.organizacion_id,NEW.id_operacion,NEW.id_fuente,NEW.retenido,NEW.orden)
 IS DISTINCT FROM ROW(OLD.organizacion_id,OLD.id_operacion,OLD.id_fuente,OLD.retenido,OLD.orden)
 OR OLD.liquidado IS NOT NULL OR NEW.liquidado IS NULL THEN RAISE EXCEPTION 'La imputación manual sólo recibe su liquidación única';END IF;
 RETURN NEW;END;$$;
CREATE TRIGGER imputacion_manual_conservar BEFORE UPDATE OR DELETE ON b0_imputacion FOR EACH ROW EXECUTE FUNCTION conservar_imputacion_manual_b0();
CREATE FUNCTION verificar_reserva_manual_b0() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE o b0_operacion_ia%ROWTYPE;reservado numeric;consumido numeric;pendientes integer;
BEGIN
 SELECT * INTO o FROM b0_operacion_ia WHERE organizacion_id=COALESCE(NEW.organizacion_id,OLD.organizacion_id) AND id_operacion=COALESCE(NEW.id_operacion,OLD.id_operacion);
 IF NOT FOUND OR o.modalidad<>'MANUAL_I0' THEN RETURN NULL;END IF;
 SELECT sum(retenido),sum(liquidado),count(*) FILTER(WHERE liquidado IS NULL) INTO reservado,consumido,pendientes FROM b0_imputacion WHERE organizacion_id=o.organizacion_id AND id_operacion=o.id_operacion;
 IF reservado IS DISTINCT FROM o.maximo_creditos THEN RAISE EXCEPTION 'La reserva manual debe conservar el máximo íntegro en el ledger B0';END IF;
 IF o.estado IN('LIQUIDADA','CANCELADA') AND (pendientes<>0 OR consumido IS DISTINCT FROM o.creditos_liquidados)
 THEN RAISE EXCEPTION 'La conciliación manual debe conservar el cargo único en sus fuentes';END IF;
 IF o.estado NOT IN('LIQUIDADA','CANCELADA') AND consumido IS NOT NULL THEN RAISE EXCEPTION 'La reserva manual abierta no acredita consumo definitivo';END IF;
 RETURN NULL;END;$$;
CREATE CONSTRAINT TRIGGER reserva_manual_operacion AFTER INSERT OR UPDATE ON b0_operacion_ia DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verificar_reserva_manual_b0();
CREATE CONSTRAINT TRIGGER reserva_manual_imputacion AFTER INSERT OR UPDATE OR DELETE ON b0_imputacion DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verificar_reserva_manual_b0();
CREATE FUNCTION conservar_operacion_manual_b0() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF OLD.modalidad='MANUAL_I0' THEN RAISE EXCEPTION 'El trabajo manual conserva operación e historia';END IF;RETURN OLD;END;$$;
CREATE TRIGGER operacion_manual_conservar BEFORE DELETE ON b0_operacion_ia FOR EACH ROW EXECUTE FUNCTION conservar_operacion_manual_b0();
