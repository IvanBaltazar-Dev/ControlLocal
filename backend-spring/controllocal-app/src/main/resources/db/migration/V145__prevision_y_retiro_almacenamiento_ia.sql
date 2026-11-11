-- C6-CONS-01/02, 01Nov2026. Un único ledger: techo previo y bytes reales.
-- Sin perfiles, fondos, capacidades físicas o calibración productivos por defecto.
CREATE TABLE b0_limite_salida_ia (
 perfil varchar(60) NOT NULL,version_perfil varchar(80) NOT NULL,
 maximo_bytes bigint NOT NULL CHECK(maximo_bytes>0),
 evidencia_calibracion text NOT NULL CHECK(length(btrim(evidencia_calibracion))>0),
 evidencia_aprobacion text NOT NULL CHECK(length(btrim(evidencia_aprobacion))>0),
 desde timestamptz NOT NULL,hasta timestamptz NOT NULL,habilitado boolean NOT NULL DEFAULT false,
 PRIMARY KEY(perfil,version_perfil),CHECK(hasta>desde),
 FOREIGN KEY(perfil,version_perfil) REFERENCES b0_perfil_operativo(perfil,version)
);
CREATE TABLE b0_politica_capacidad_fisica (
 namespace_hash varchar(64) NOT NULL CHECK(namespace_hash ~ '^[0-9a-f]{64}$'),version varchar(80) NOT NULL,
 capacidad_bytes bigint NOT NULL CHECK(capacidad_bytes>0),
 ocupacion_conciliada_bytes bigint NOT NULL CHECK(ocupacion_conciliada_bytes>=0),
 conciliada_en timestamptz NOT NULL,
 evidencia_calibracion text NOT NULL CHECK(length(btrim(evidencia_calibracion))>0),
 evidencia_aprobacion text NOT NULL CHECK(length(btrim(evidencia_aprobacion))>0),
 evidencia_financiacion text NOT NULL CHECK(length(btrim(evidencia_financiacion))>0),
 evidencia_conciliacion text NOT NULL CHECK(length(btrim(evidencia_conciliacion))>0),
 desde timestamptz NOT NULL,hasta timestamptz NOT NULL,habilitada boolean NOT NULL DEFAULT false,
 PRIMARY KEY(namespace_hash,version),CHECK(hasta>desde),CHECK(conciliada_en<=desde),
 CHECK(ocupacion_conciliada_bytes<=capacidad_bytes)
);
CREATE TABLE b0_capacidad_fisica_actual (
 namespace_hash varchar(64) PRIMARY KEY,version varchar(80) NOT NULL,
 FOREIGN KEY(namespace_hash,version) REFERENCES b0_politica_capacidad_fisica(namespace_hash,version)
);
-- Proyección física del mismo ledger, inicializada únicamente desde conciliación acreditada.
CREATE TABLE b0_uso_capacidad_fisica (
 namespace_hash varchar(64) PRIMARY KEY,version_politica varchar(80) NOT NULL,
 confirmados bigint NOT NULL CHECK(confirmados>=0),reservados bigint NOT NULL CHECK(reservados>=0),
 version bigint NOT NULL DEFAULT 0,
 FOREIGN KEY(namespace_hash,version_politica) REFERENCES b0_politica_capacidad_fisica(namespace_hash,version)
);
ALTER TABLE b0_reserva_almacenamiento ALTER COLUMN bytes DROP NOT NULL;
ALTER TABLE b0_reserva_almacenamiento ALTER COLUMN huella_contenido DROP NOT NULL;
ALTER TABLE b0_reserva_almacenamiento
 ADD COLUMN modalidad varchar(20) NOT NULL DEFAULT 'EXACTA' CHECK(modalidad IN('EXACTA','PREVISION_IA')),
 ADD COLUMN job_ia uuid,ADD COLUMN perfil_ia varchar(60),ADD COLUMN version_perfil_ia varchar(80),
 ADD COLUMN namespace_hash varchar(64) CHECK(namespace_hash IS NULL OR namespace_hash ~ '^[0-9a-f]{64}$'),
 ADD COLUMN techo_bytes bigint CHECK(techo_bytes>0),ADD COLUMN reclamada_en timestamptz,
 ADD COLUMN contenido_fijado_en timestamptz,ADD COLUMN version_fisica varchar(80),
 ADD COLUMN bytes_fisicos_retenidos bigint NOT NULL DEFAULT 0 CHECK(bytes_fisicos_retenidos>=0),
 ADD COLUMN huella_liberacion varchar(64) CHECK(huella_liberacion IS NULL OR huella_liberacion ~ '^[0-9a-f]{64}$'),
 ADD CONSTRAINT uq_b0_prevision_job UNIQUE(organizacion_id,job_ia),
 ADD CONSTRAINT fk_b0_prevision_job FOREIGN KEY(organizacion_id,job_ia)
  REFERENCES material_ia_operacion(organizacion_id,id_operacion),
 ADD CONSTRAINT fk_b0_prevision_perfil FOREIGN KEY(perfil_ia,version_perfil_ia)
  REFERENCES b0_limite_salida_ia(perfil,version_perfil),
 ADD CONSTRAINT fk_b0_reserva_fisica FOREIGN KEY(namespace_hash,version_fisica)
  REFERENCES b0_politica_capacidad_fisica(namespace_hash,version),
 ADD CONSTRAINT ck_b0_contenido_exacto CHECK((bytes IS NULL)=(huella_contenido IS NULL)),
 ADD CONSTRAINT ck_b0_huella_contenido CHECK(huella_contenido IS NULL OR huella_contenido ~ '^[0-9a-f]{64}$'),
 ADD CONSTRAINT ck_b0_prevision_forma CHECK(
  (modalidad='EXACTA' AND categoria<>'DERIVADA_IA' AND bytes IS NOT NULL AND job_ia IS NULL AND techo_bytes IS NULL AND perfil_ia IS NULL AND version_perfil_ia IS NULL)
  OR (modalidad='PREVISION_IA' AND job_ia IS NOT NULL AND techo_bytes IS NOT NULL AND perfil_ia IS NOT NULL
   AND version_perfil_ia IS NOT NULL AND namespace_hash IS NOT NULL AND version_fisica IS NOT NULL
   AND categoria='DERIVADA_IA' AND necesidad='ORDINARIA' AND bytes_retenidos=techo_bytes
   AND bytes_fisicos_retenidos=techo_bytes AND (bytes IS NULL OR bytes<=techo_bytes))),
 ADD CONSTRAINT ck_b0_prevision_confirmada CHECK(modalidad<>'PREVISION_IA' OR estado<>'CONFIRMADA'
  OR (bytes IS NOT NULL AND contenido_fijado_en IS NOT NULL AND reclamada_en IS NOT NULL));

-- Metadata original inmutable; presencia actual y retirada verificadas son hechos distintos.
CREATE TABLE b0_presencia_objeto_almacenamiento (
 organizacion_id bigint NOT NULL,huella_identidad varchar(64) NOT NULL,
 namespace_hash varchar(64),version_fisica varchar(80),comercial boolean NOT NULL,fisico boolean NOT NULL,
 estado varchar(30) NOT NULL CHECK(estado IN('PRESENTE','RETIRADO_VERIFICADO')),
 revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0),id_retiro uuid,huella_retiro varchar(64),retirado_en timestamptz,
 PRIMARY KEY(organizacion_id,huella_identidad),
 FOREIGN KEY(organizacion_id,huella_identidad) REFERENCES b0_objeto_almacenamiento(organizacion_id,huella_identidad),
 FOREIGN KEY(namespace_hash,version_fisica) REFERENCES b0_politica_capacidad_fisica(namespace_hash,version),
 CHECK(NOT fisico OR (namespace_hash IS NOT NULL AND version_fisica IS NOT NULL)),
 CHECK((estado='PRESENTE' AND num_nonnulls(id_retiro,huella_retiro,retirado_en)=0)
  OR (estado='RETIRADO_VERIFICADO' AND num_nonnulls(id_retiro,huella_retiro,retirado_en)=3)),
 CHECK(huella_retiro IS NULL OR huella_retiro ~ '^[0-9a-f]{64}$')
);
CREATE FUNCTION conservar_prevision_almacenamiento_ia() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN
  RAISE EXCEPTION 'La previsión conserva intención y resultado' USING ERRCODE='23514';
 END IF;
 IF OLD.modalidad='PREVISION_IA' THEN
  IF ROW(NEW.organizacion_id,NEW.id_reserva,NEW.id_persona,NEW.id_rol,NEW.huella_peticion,NEW.recurso_tipo,NEW.recurso_id,
   NEW.finalidad,NEW.huella_origen,NEW.categoria,NEW.necesidad,NEW.bytes_retenidos,NEW.version_politica,NEW.contexto_limite,
   NEW.modalidad,NEW.job_ia,NEW.perfil_ia,NEW.version_perfil_ia,NEW.namespace_hash,NEW.techo_bytes,NEW.version_fisica,
   NEW.bytes_fisicos_retenidos,NEW.creada_en)
   IS DISTINCT FROM ROW(OLD.organizacion_id,OLD.id_reserva,OLD.id_persona,OLD.id_rol,OLD.huella_peticion,OLD.recurso_tipo,OLD.recurso_id,
   OLD.finalidad,OLD.huella_origen,OLD.categoria,OLD.necesidad,OLD.bytes_retenidos,OLD.version_politica,OLD.contexto_limite,
   OLD.modalidad,OLD.job_ia,OLD.perfil_ia,OLD.version_perfil_ia,OLD.namespace_hash,OLD.techo_bytes,OLD.version_fisica,
   OLD.bytes_fisicos_retenidos,OLD.creada_en)
   THEN RAISE EXCEPTION 'La previsión conserva job, techo, actor y políticas' USING ERRCODE='23514'; END IF;
  IF OLD.bytes IS NOT NULL AND ROW(NEW.bytes,NEW.huella_contenido,NEW.huella_objeto,NEW.contenido_fijado_en)
   IS DISTINCT FROM ROW(OLD.bytes,OLD.huella_contenido,OLD.huella_objeto,OLD.contenido_fijado_en)
   THEN RAISE EXCEPTION 'La previsión conserva su único contenido real' USING ERRCODE='23514'; END IF;
  IF OLD.reclamada_en IS NOT NULL AND NEW.reclamada_en IS DISTINCT FROM OLD.reclamada_en
   THEN RAISE EXCEPTION 'La previsión conserva la reclamación' USING ERRCODE='23514'; END IF;
  IF OLD.estado IN('CONFIRMADA','CANCELADA') AND NEW IS DISTINCT FROM OLD
   THEN RAISE EXCEPTION 'La previsión finalizada conserva su resolución' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER b0_prevision_conservar BEFORE UPDATE OR DELETE ON b0_reserva_almacenamiento
 FOR EACH ROW EXECUTE FUNCTION conservar_prevision_almacenamiento_ia();
CREATE TRIGGER b0_prevision_conservar_truncate BEFORE TRUNCATE ON b0_reserva_almacenamiento
 FOR EACH STATEMENT EXECUTE FUNCTION conservar_prevision_almacenamiento_ia();
CREATE FUNCTION conservar_presencia_almacenamiento_ia() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE categoria_objeto varchar(30);
BEGIN
 IF TG_OP IN('DELETE','TRUNCATE') THEN
  RAISE EXCEPTION 'La presencia conserva su antecedente' USING ERRCODE='23514';
 END IF;
 SELECT categoria INTO STRICT categoria_objeto FROM b0_objeto_almacenamiento
  WHERE organizacion_id=OLD.organizacion_id AND huella_identidad=OLD.huella_identidad;
 IF OLD.estado='PRESENTE' AND NEW.estado='PRESENTE' AND NOT OLD.comercial AND NEW.comercial
  AND NEW.revision=OLD.revision+1
  AND ROW(NEW.organizacion_id,NEW.huella_identidad,NEW.namespace_hash,NEW.version_fisica,NEW.fisico,NEW.id_retiro,NEW.huella_retiro,NEW.retirado_en)
   IS NOT DISTINCT FROM ROW(OLD.organizacion_id,OLD.huella_identidad,OLD.namespace_hash,OLD.version_fisica,OLD.fisico,OLD.id_retiro,OLD.huella_retiro,OLD.retirado_en)
 THEN RETURN NEW; END IF;
 IF ROW(NEW.organizacion_id,NEW.huella_identidad,NEW.namespace_hash,NEW.version_fisica,NEW.comercial,NEW.fisico)
  IS DISTINCT FROM ROW(OLD.organizacion_id,OLD.huella_identidad,OLD.namespace_hash,OLD.version_fisica,OLD.comercial,OLD.fisico)
  OR OLD.estado<>'PRESENTE' OR NEW.estado<>'RETIRADO_VERIFICADO' OR NEW.revision<>OLD.revision+1 OR categoria_objeto<>'DERIVADA_IA'
 THEN RAISE EXCEPTION 'Sólo se retira una derivada IA comprobada conservando su identidad' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER b0_presencia_conservar BEFORE UPDATE OR DELETE ON b0_presencia_objeto_almacenamiento
 FOR EACH ROW EXECUTE FUNCTION conservar_presencia_almacenamiento_ia();
CREATE TRIGGER b0_presencia_conservar_truncate BEFORE TRUNCATE ON b0_presencia_objeto_almacenamiento
 FOR EACH STATEMENT EXECUTE FUNCTION conservar_presencia_almacenamiento_ia();
CREATE TRIGGER b0_limite_salida_ia_conservar BEFORE UPDATE OR DELETE ON b0_limite_salida_ia
 FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
CREATE TRIGGER b0_limite_salida_ia_conservar_truncate BEFORE TRUNCATE ON b0_limite_salida_ia
 FOR EACH STATEMENT EXECUTE FUNCTION b0_conservar_antecedente();
CREATE TRIGGER b0_capacidad_fisica_conservar BEFORE UPDATE OR DELETE ON b0_politica_capacidad_fisica
 FOR EACH ROW EXECUTE FUNCTION b0_conservar_antecedente();
CREATE TRIGGER b0_capacidad_fisica_conservar_truncate BEFORE TRUNCATE ON b0_politica_capacidad_fisica
 FOR EACH STATEMENT EXECUTE FUNCTION b0_conservar_antecedente();
