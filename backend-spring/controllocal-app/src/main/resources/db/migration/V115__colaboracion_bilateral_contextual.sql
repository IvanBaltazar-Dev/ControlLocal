-- RF035–037 / CAT-E19 / BROX4.4.5. Solicitud, decisiones y concesión bilateral.
-- Requiere UQ(organizacion_id,id_requerimiento) del corte Demanda y UQ de captacion/persona_rol existentes.
-- No habilita participación, políticas, campos, plan ni tratamiento Network.
CREATE TABLE solicitud_colaboracion (
 id_solicitud_colaboracion bigserial PRIMARY KEY,
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 organizacion_encargo_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 referencia uuid NOT NULL UNIQUE, version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 id_requerimiento bigint NOT NULL, id_captacion bigint NOT NULL,
 id_responsable_demanda bigint NOT NULL, id_responsable_encargo bigint NOT NULL,
 id_rol_solicitante bigint NOT NULL,
 version_demanda bigint NOT NULL CHECK(version_demanda>=0),
 version_encargo bigint NOT NULL CHECK(version_encargo>=0),
 version_oferta bigint NOT NULL CHECK(version_oferta>=0), -- proyección Network propia; no versión física ni Capterminos
 oferta_ref text NOT NULL CHECK(btrim(oferta_ref)<>''),
 alcance text NOT NULL CHECK(jsonb_typeof(alcance::jsonb)='object'),
 huella_alcance varchar(64) NOT NULL CHECK(huella_alcance ~ '^[a-f0-9]{64}$'),
 version_alcance bigint NOT NULL DEFAULT 0 CHECK(version_alcance>=0),
 estado varchar(1) NOT NULL CHECK(estado IN('P','A','R')), creada_en timestamptz NOT NULL,
 id_decision_demanda bigint, id_decision_encargo bigint,id_canal_demanda bigint,id_canal_encargo bigint,
 CONSTRAINT uq_solicitud_colaboracion_alcance UNIQUE(organizacion_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance),
 CONSTRAINT fk_sol_colab_demanda FOREIGN KEY(organizacion_id,id_requerimiento) REFERENCES requerimiento_cliente(organizacion_id,id_requerimiento),
 CONSTRAINT fk_sol_colab_encargo FOREIGN KEY(organizacion_encargo_id,id_captacion) REFERENCES captacion(organizacion_id,id_captacion),
 CONSTRAINT fk_sol_colab_responsable_d FOREIGN KEY(organizacion_id,id_responsable_demanda) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 CONSTRAINT fk_sol_colab_responsable_e FOREIGN KEY(organizacion_encargo_id,id_responsable_encargo) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 CONSTRAINT fk_sol_colab_solicitante FOREIGN KEY(organizacion_id,id_rol_solicitante) REFERENCES persona_rol(organizacion_id,id_persona_rol)
);

CREATE TABLE decision_colaboracion (
 id_decision_colaboracion bigserial PRIMARY KEY,
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 organizacion_demanda_id bigint NOT NULL, organizacion_encargo_id bigint NOT NULL,
 id_solicitud_colaboracion bigint NOT NULL,
 lado varchar(7) NOT NULL CHECK(lado IN('DEMANDA','ENCARGO')),
 decision varchar(8) NOT NULL CHECK(decision IN('ACEPTAR','LIMITAR','RECHAZAR')),
 id_rol_decisor bigint NOT NULL,
 version_alcance bigint NOT NULL CHECK(version_alcance>=0),
 version_recurso bigint NOT NULL CHECK(version_recurso>=0),
 alcance text, snapshot_parte text, motivo text NOT NULL CHECK(btrim(motivo)<>''), registrada_en timestamptz NOT NULL,
 CONSTRAINT ck_dec_colab_lado_org CHECK((lado='DEMANDA' AND organizacion_id=organizacion_demanda_id) OR (lado='ENCARGO' AND organizacion_id=organizacion_encargo_id)),
 CONSTRAINT ck_dec_colab_alcance CHECK((decision='RECHAZAR' AND alcance IS NULL) OR (decision IN('ACEPTAR','LIMITAR') AND alcance IS NOT NULL AND jsonb_typeof(alcance::jsonb)='object')),
 CONSTRAINT ck_dec_colab_snapshot CHECK((decision='RECHAZAR' AND snapshot_parte IS NULL) OR (decision IN('ACEPTAR','LIMITAR') AND snapshot_parte IS NOT NULL AND jsonb_typeof(snapshot_parte::jsonb)='object')),
 CONSTRAINT fk_dec_colab_contextos FOREIGN KEY(organizacion_demanda_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance) REFERENCES solicitud_colaboracion(organizacion_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance),
 CONSTRAINT fk_dec_colab_actor FOREIGN KEY(organizacion_id,id_rol_decisor) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 CONSTRAINT uq_dec_colab_lado UNIQUE(id_solicitud_colaboracion,lado,id_decision_colaboracion)
);

-- Una parte puede anexar otro antecedente mientras la solicitud siga pendiente;
-- el puntero actual no borra el anterior ni usa UNIQUE eterno por recurso/pareja.
ALTER TABLE solicitud_colaboracion ADD COLUMN lado_decision_demanda varchar(7) NOT NULL DEFAULT 'DEMANDA' CHECK(lado_decision_demanda='DEMANDA');
ALTER TABLE solicitud_colaboracion ADD COLUMN lado_decision_encargo varchar(7) NOT NULL DEFAULT 'ENCARGO' CHECK(lado_decision_encargo='ENCARGO');
ALTER TABLE solicitud_colaboracion ADD CONSTRAINT fk_sol_colab_dec_d FOREIGN KEY(id_solicitud_colaboracion,lado_decision_demanda,id_decision_demanda) REFERENCES decision_colaboracion(id_solicitud_colaboracion,lado,id_decision_colaboracion);
ALTER TABLE solicitud_colaboracion ADD CONSTRAINT fk_sol_colab_dec_e FOREIGN KEY(id_solicitud_colaboracion,lado_decision_encargo,id_decision_encargo) REFERENCES decision_colaboracion(id_solicitud_colaboracion,lado,id_decision_colaboracion);

CREATE TABLE concesion_colaboracion (
 id_concesion_colaboracion bigserial PRIMARY KEY,
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 organizacion_encargo_id bigint NOT NULL,
 referencia uuid NOT NULL UNIQUE, version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 id_solicitud_colaboracion bigint NOT NULL UNIQUE,
 id_decision_demanda bigint NOT NULL, id_decision_encargo bigint NOT NULL,
 lado_decision_demanda varchar(7) NOT NULL DEFAULT 'DEMANDA' CHECK(lado_decision_demanda='DEMANDA'),
 lado_decision_encargo varchar(7) NOT NULL DEFAULT 'ENCARGO' CHECK(lado_decision_encargo='ENCARGO'),
 version_alcance bigint NOT NULL CHECK(version_alcance>=0),
 alcance text NOT NULL CHECK(jsonb_typeof(alcance::jsonb)='object'), concedida_en timestamptz NOT NULL,
 revocada_en timestamptz, lado_revocador varchar(7), organizacion_revocadora_id bigint,
 id_rol_revocador bigint, motivo_revocacion text,
 CONSTRAINT fk_con_colab_contextos FOREIGN KEY(organizacion_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance) REFERENCES solicitud_colaboracion(organizacion_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance),
 CONSTRAINT fk_con_colab_dec_d FOREIGN KEY(id_solicitud_colaboracion,lado_decision_demanda,id_decision_demanda) REFERENCES decision_colaboracion(id_solicitud_colaboracion,lado,id_decision_colaboracion),
 CONSTRAINT fk_con_colab_dec_e FOREIGN KEY(id_solicitud_colaboracion,lado_decision_encargo,id_decision_encargo) REFERENCES decision_colaboracion(id_solicitud_colaboracion,lado,id_decision_colaboracion),
 CONSTRAINT fk_con_colab_rev_actor FOREIGN KEY(organizacion_revocadora_id,id_rol_revocador) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 CONSTRAINT ck_con_colab_revocacion CHECK((revocada_en IS NULL AND lado_revocador IS NULL AND organizacion_revocadora_id IS NULL AND id_rol_revocador IS NULL AND motivo_revocacion IS NULL) OR (revocada_en IS NOT NULL AND lado_revocador IS NOT NULL AND organizacion_revocadora_id IS NOT NULL AND id_rol_revocador IS NOT NULL AND motivo_revocacion IS NOT NULL AND btrim(motivo_revocacion)<>'' AND ((lado_revocador='DEMANDA' AND organizacion_revocadora_id=organizacion_id) OR (lado_revocador='ENCARGO' AND organizacion_revocadora_id=organizacion_encargo_id))))
);
CREATE INDEX ix_sol_colab_org_demanda ON solicitud_colaboracion(organizacion_id,id_requerimiento);
CREATE INDEX ix_sol_colab_org_encargo ON solicitud_colaboracion(organizacion_encargo_id,id_captacion);
CREATE INDEX ix_dec_colab_historia ON decision_colaboracion(id_solicitud_colaboracion,id_decision_colaboracion);

-- Canal explícito del titular para esta solicitud/lado/versión. Nunca se importa de perfil.
CREATE TABLE canal_colaboracion (
 id_canal_colaboracion bigserial PRIMARY KEY,
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 organizacion_demanda_id bigint NOT NULL,organizacion_encargo_id bigint NOT NULL,
 referencia uuid NOT NULL UNIQUE,id_solicitud_colaboracion bigint NOT NULL,
 version_alcance bigint NOT NULL CHECK(version_alcance>=0),
 lado varchar(7) NOT NULL CHECK(lado IN('DEMANDA','ENCARGO')),
 version_recurso bigint NOT NULL CHECK(version_recurso>=0),id_rol_declarante bigint NOT NULL,
 canal_comercial text NOT NULL CHECK(btrim(canal_comercial)<>''),declarada_en timestamptz NOT NULL,
 CONSTRAINT ck_canal_colab_lado_org CHECK((lado='DEMANDA' AND organizacion_id=organizacion_demanda_id) OR (lado='ENCARGO' AND organizacion_id=organizacion_encargo_id)),
 CONSTRAINT fk_canal_colab_solicitud FOREIGN KEY(organizacion_demanda_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance) REFERENCES solicitud_colaboracion(organizacion_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance),
 CONSTRAINT fk_canal_colab_declarante FOREIGN KEY(organizacion_id,id_rol_declarante) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 CONSTRAINT uq_canal_colab_contexto UNIQUE(organizacion_demanda_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance,lado,id_canal_colaboracion)
);
ALTER TABLE solicitud_colaboracion ADD CONSTRAINT fk_sol_colab_canal_d FOREIGN KEY(organizacion_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance,lado_decision_demanda,id_canal_demanda) REFERENCES canal_colaboracion(organizacion_demanda_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance,lado,id_canal_colaboracion);
ALTER TABLE solicitud_colaboracion ADD CONSTRAINT fk_sol_colab_canal_e FOREIGN KEY(organizacion_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance,lado_decision_encargo,id_canal_encargo) REFERENCES canal_colaboracion(organizacion_demanda_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance,lado,id_canal_colaboracion);
CREATE FUNCTION exigir_titular_canal_colaboracion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM solicitud_colaboracion s
   WHERE s.id_solicitud_colaboracion=NEW.id_solicitud_colaboracion
     AND s.organizacion_id=NEW.organizacion_demanda_id AND s.organizacion_encargo_id=NEW.organizacion_encargo_id
     AND s.version_alcance=NEW.version_alcance AND s.estado='P'
     AND NOT EXISTS(SELECT 1 FROM decision_colaboracion d WHERE d.id_solicitud_colaboracion=s.id_solicitud_colaboracion AND d.lado=NEW.lado)
     AND ((NEW.lado='DEMANDA' AND NEW.id_rol_declarante=s.id_responsable_demanda AND s.id_decision_demanda IS NULL)
       OR (NEW.lado='ENCARGO' AND NEW.id_rol_declarante=s.id_responsable_encargo AND s.id_decision_encargo IS NULL))) THEN
  RAISE EXCEPTION 'El canal requiere titular responsable y alcance pendiente sin decisión propia' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_canal_colaboracion_titular BEFORE INSERT ON canal_colaboracion
FOR EACH ROW EXECUTE FUNCTION exigir_titular_canal_colaboracion();

-- Una concesión no puede acreditar dos aceptaciones con un rechazo o con otra versión.
CREATE FUNCTION exigir_decisiones_de_concesion_colaboracion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM decision_colaboracion d JOIN decision_colaboracion e
    ON e.id_solicitud_colaboracion=d.id_solicitud_colaboracion
    JOIN solicitud_colaboracion s ON s.id_solicitud_colaboracion=d.id_solicitud_colaboracion
    WHERE d.id_decision_colaboracion=NEW.id_decision_demanda
      AND e.id_decision_colaboracion=NEW.id_decision_encargo
      AND d.id_solicitud_colaboracion=NEW.id_solicitud_colaboracion
      AND d.lado='DEMANDA' AND e.lado='ENCARGO'
      AND d.decision IN('ACEPTAR','LIMITAR') AND e.decision IN('ACEPTAR','LIMITAR')
      AND d.version_alcance=NEW.version_alcance AND e.version_alcance=NEW.version_alcance
      AND s.id_decision_demanda=d.id_decision_colaboracion AND s.id_decision_encargo=e.id_decision_colaboracion
      AND s.estado='A') THEN
  RAISE EXCEPTION 'La concesión exige dos decisiones de sus lados sobre el mismo alcance' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_concesion_colaboracion_dos_decisiones BEFORE INSERT ON concesion_colaboracion
FOR EACH ROW EXECUTE FUNCTION exigir_decisiones_de_concesion_colaboracion();

CREATE FUNCTION proteger_historia_colaboracion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP IN ('DELETE','TRUNCATE') OR TG_TABLE_NAME IN('decision_colaboracion','canal_colaboracion') THEN
  RAISE EXCEPTION 'La historia de colaboración se conserva y no se sobrescribe' USING ERRCODE='23514';
 END IF;
 IF TG_TABLE_NAME='solicitud_colaboracion' THEN
  IF (NEW.id_canal_demanda IS DISTINCT FROM OLD.id_canal_demanda AND EXISTS(SELECT 1 FROM decision_colaboracion d WHERE d.id_solicitud_colaboracion=OLD.id_solicitud_colaboracion AND d.lado='DEMANDA'))
     OR (NEW.id_canal_encargo IS DISTINCT FROM OLD.id_canal_encargo AND EXISTS(SELECT 1 FROM decision_colaboracion d WHERE d.id_solicitud_colaboracion=OLD.id_solicitud_colaboracion AND d.lado='ENCARGO')) THEN
   RAISE EXCEPTION 'El canal de una decisión existente no se reescribe; requiere nueva solicitud' USING ERRCODE='23514';
  END IF;
  IF ROW(NEW.id_solicitud_colaboracion,NEW.organizacion_id,NEW.organizacion_encargo_id,NEW.referencia,NEW.id_requerimiento,NEW.id_captacion,
        NEW.id_responsable_demanda,NEW.id_responsable_encargo,NEW.id_rol_solicitante,NEW.version_demanda,
        NEW.version_encargo,NEW.version_oferta,NEW.oferta_ref,NEW.alcance,NEW.huella_alcance,NEW.version_alcance,NEW.creada_en)
    IS DISTINCT FROM
    ROW(OLD.id_solicitud_colaboracion,OLD.organizacion_id,OLD.organizacion_encargo_id,OLD.referencia,OLD.id_requerimiento,OLD.id_captacion,
        OLD.id_responsable_demanda,OLD.id_responsable_encargo,OLD.id_rol_solicitante,OLD.version_demanda,
        OLD.version_encargo,OLD.version_oferta,OLD.oferta_ref,OLD.alcance,OLD.huella_alcance,OLD.version_alcance,OLD.creada_en) THEN
  RAISE EXCEPTION 'La solicitud y alcance originales son inmutables' USING ERRCODE='23514';
  END IF;
 ELSIF TG_TABLE_NAME='concesion_colaboracion' THEN
  IF OLD.revocada_en IS NOT NULL OR
     ROW(NEW.id_concesion_colaboracion,NEW.organizacion_id,NEW.organizacion_encargo_id,NEW.referencia,NEW.id_solicitud_colaboracion,
         NEW.id_decision_demanda,NEW.id_decision_encargo,NEW.version_alcance,NEW.alcance,NEW.concedida_en)
     IS DISTINCT FROM
     ROW(OLD.id_concesion_colaboracion,OLD.organizacion_id,OLD.organizacion_encargo_id,OLD.referencia,OLD.id_solicitud_colaboracion,
         OLD.id_decision_demanda,OLD.id_decision_encargo,OLD.version_alcance,OLD.alcance,OLD.concedida_en) THEN
   RAISE EXCEPTION 'La concesión original y su revocación se conservan' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_solicitud_colaboracion_historia BEFORE UPDATE OR DELETE ON solicitud_colaboracion
FOR EACH ROW EXECUTE FUNCTION proteger_historia_colaboracion();
CREATE TRIGGER tg_decision_colaboracion_historia BEFORE UPDATE OR DELETE ON decision_colaboracion
FOR EACH ROW EXECUTE FUNCTION proteger_historia_colaboracion();
CREATE TRIGGER tg_concesion_colaboracion_historia BEFORE UPDATE OR DELETE ON concesion_colaboracion
FOR EACH ROW EXECUTE FUNCTION proteger_historia_colaboracion();
CREATE TRIGGER tg_canal_colaboracion_historia BEFORE UPDATE OR DELETE ON canal_colaboracion
FOR EACH ROW EXECUTE FUNCTION proteger_historia_colaboracion();

INSERT INTO entidad_tipo(codigo,descripcion,auditable,referenciable) VALUES
 ('SOLICITUD_COLABORACION','Solicitud de colaboración bilateral',true,true),
 ('CONCESION_COLABORACION','Concesión de alcance bilateral',true,true);

-- entidad_tipo ya integrado arriba; root integra MaquinasEstado y aplica únicamente DB propia.
-- MaquinasEstado: P inicial, P->A/R (BROX4.4.5/2077); concesión sin estado añadido.
-- Efectos: SOLICITAR_COLABORACION / DECIDIR_COLABORACION / REVOCAR_COLABORACION.
-- Eventos: COLABORACION_SOLICITADA / COLABORACION_DECIDIDA / COLABORACION_REVOCADA.
-- RLS, rol y proyección entre partes: root; actor real, nunca cambiar tenant para leer privados.


-- La conservación también rige TRUNCATE, que no dispara triggers por fila.
CREATE TRIGGER tg_solicitud_colaboracion_no_truncate BEFORE TRUNCATE ON solicitud_colaboracion
FOR EACH STATEMENT EXECUTE FUNCTION proteger_historia_colaboracion();
CREATE TRIGGER tg_decision_colaboracion_no_truncate BEFORE TRUNCATE ON decision_colaboracion
FOR EACH STATEMENT EXECUTE FUNCTION proteger_historia_colaboracion();
CREATE TRIGGER tg_concesion_colaboracion_no_truncate BEFORE TRUNCATE ON concesion_colaboracion
FOR EACH STATEMENT EXECUTE FUNCTION proteger_historia_colaboracion();
CREATE TRIGGER tg_canal_colaboracion_no_truncate BEFORE TRUNCATE ON canal_colaboracion
FOR EACH STATEMENT EXECUTE FUNCTION proteger_historia_colaboracion();
