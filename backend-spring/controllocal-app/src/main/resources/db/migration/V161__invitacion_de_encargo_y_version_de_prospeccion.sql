-- Catálogo20.9. Una invitación tiene contraparte INVITADO, nunca una Demanda sintética.
ALTER TABLE prospeccion ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK(version>=0);
ALTER TABLE solicitud_colaboracion
 ALTER COLUMN id_requerimiento DROP NOT NULL,
 ALTER COLUMN id_responsable_demanda DROP NOT NULL,
 ALTER COLUMN version_demanda DROP NOT NULL,
 ALTER COLUMN version_oferta DROP NOT NULL,
 ALTER COLUMN oferta_ref DROP NOT NULL,
 ADD COLUMN id_responsable_invitado bigint,
 ADD COLUMN version_invitado bigint,
 ADD COLUMN id_revision_permiso bigint,
 ADD COLUMN clausula_permiso text,
 ADD COLUMN id_decision_invitado bigint,
 ADD COLUMN id_canal_invitado bigint,
 ADD COLUMN lado_decision_invitado varchar(16) NOT NULL DEFAULT 'INVITADO' CHECK(lado_decision_invitado='INVITADO'),
 ADD CONSTRAINT fk_inv_responsable FOREIGN KEY(organizacion_id,id_responsable_invitado) REFERENCES detalle_agente(organizacion_id,id_persona_rol),
 ADD CONSTRAINT fk_inv_permiso FOREIGN KEY(organizacion_encargo_id,id_captacion,id_revision_permiso) REFERENCES revision_encargo(organizacion_id,id_captacion,id_revision_encargo),
 ADD CONSTRAINT ck_solicitud_tipo_recursos CHECK(
   (id_requerimiento IS NOT NULL AND id_responsable_demanda IS NOT NULL AND version_demanda IS NOT NULL AND version_oferta IS NOT NULL AND oferta_ref IS NOT NULL
    AND id_responsable_invitado IS NULL AND version_invitado IS NULL AND id_revision_permiso IS NULL AND clausula_permiso IS NULL AND id_decision_invitado IS NULL AND id_canal_invitado IS NULL)
   OR
   (id_requerimiento IS NULL AND id_responsable_demanda IS NULL AND version_demanda IS NULL AND version_oferta IS NULL AND oferta_ref IS NULL
    AND id_responsable_invitado IS NOT NULL AND version_invitado IS NOT NULL AND version_invitado>=0 AND id_revision_permiso IS NOT NULL AND clausula_permiso IS NOT NULL AND length(btrim(clausula_permiso))>0 AND id_decision_demanda IS NULL AND id_canal_demanda IS NULL)
 );
ALTER TABLE solicitud_colaboracion ADD COLUMN organizacion_solicitante_id bigint;
UPDATE solicitud_colaboracion SET organizacion_solicitante_id=organizacion_id;
ALTER TABLE solicitud_colaboracion ALTER COLUMN organizacion_solicitante_id SET NOT NULL,
 DROP CONSTRAINT fk_sol_colab_solicitante,
 ADD CONSTRAINT fk_sol_solicitante_por_tipo FOREIGN KEY(organizacion_solicitante_id,id_rol_solicitante) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 ADD CONSTRAINT ck_sol_solicitante_por_tipo CHECK((id_requerimiento IS NULL AND organizacion_solicitante_id=organizacion_encargo_id) OR(id_requerimiento IS NOT NULL AND organizacion_solicitante_id=organizacion_id));
ALTER TABLE decision_colaboracion ALTER COLUMN lado TYPE varchar(16);
ALTER TABLE decision_colaboracion DROP CONSTRAINT decision_colaboracion_lado_check, DROP CONSTRAINT ck_dec_colab_lado_org;
ALTER TABLE decision_colaboracion ADD CHECK(lado IN('DEMANDA','ENCARGO','INVITADO')),
 ADD CONSTRAINT ck_dec_colab_lado_org CHECK((lado IN('DEMANDA','INVITADO') AND organizacion_id=organizacion_demanda_id) OR (lado='ENCARGO' AND organizacion_id=organizacion_encargo_id));
ALTER TABLE solicitud_colaboracion ADD CONSTRAINT fk_sol_dec_inv FOREIGN KEY(id_solicitud_colaboracion,lado_decision_invitado,id_decision_invitado) REFERENCES decision_colaboracion(id_solicitud_colaboracion,lado,id_decision_colaboracion);
ALTER TABLE concesion_colaboracion
 ALTER COLUMN id_decision_demanda DROP NOT NULL,
 ALTER COLUMN lado_revocador TYPE varchar(16),
 ADD COLUMN id_decision_invitado bigint,
 ADD COLUMN lado_decision_invitado varchar(16) NOT NULL DEFAULT 'INVITADO' CHECK(lado_decision_invitado='INVITADO'),
 ADD CONSTRAINT fk_con_dec_inv FOREIGN KEY(id_solicitud_colaboracion,lado_decision_invitado,id_decision_invitado) REFERENCES decision_colaboracion(id_solicitud_colaboracion,lado,id_decision_colaboracion),
 ADD CONSTRAINT ck_con_una_contraparte CHECK((id_decision_demanda IS NULL)<>(id_decision_invitado IS NULL));
ALTER TABLE concesion_colaboracion DROP CONSTRAINT ck_con_colab_revocacion;
ALTER TABLE concesion_colaboracion ADD CONSTRAINT ck_con_colab_revocacion CHECK(
 (revocada_en IS NULL AND lado_revocador IS NULL AND organizacion_revocadora_id IS NULL AND id_rol_revocador IS NULL AND motivo_revocacion IS NULL)
 OR (revocada_en IS NOT NULL AND lado_revocador IS NOT NULL AND organizacion_revocadora_id IS NOT NULL AND id_rol_revocador IS NOT NULL AND motivo_revocacion IS NOT NULL AND length(btrim(motivo_revocacion))>0 AND
 ((lado_revocador='DEMANDA' AND id_decision_demanda IS NOT NULL AND organizacion_revocadora_id=organizacion_id)
 OR (lado_revocador='INVITADO' AND id_decision_invitado IS NOT NULL AND organizacion_revocadora_id=organizacion_id)
 OR (lado_revocador='ENCARGO' AND organizacion_revocadora_id=organizacion_encargo_id))));
ALTER TABLE canal_colaboracion ALTER COLUMN lado TYPE varchar(16);
ALTER TABLE canal_colaboracion DROP CONSTRAINT canal_colaboracion_lado_check, DROP CONSTRAINT ck_canal_colab_lado_org;
ALTER TABLE canal_colaboracion ADD CHECK(lado IN('DEMANDA','ENCARGO','INVITADO')),
 ADD CONSTRAINT ck_canal_colab_lado_org CHECK((lado IN('DEMANDA','INVITADO') AND organizacion_id=organizacion_demanda_id) OR (lado='ENCARGO' AND organizacion_id=organizacion_encargo_id));
ALTER TABLE solicitud_colaboracion ADD CONSTRAINT fk_sol_canal_inv FOREIGN KEY(organizacion_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance,lado_decision_invitado,id_canal_invitado) REFERENCES canal_colaboracion(organizacion_demanda_id,organizacion_encargo_id,id_solicitud_colaboracion,version_alcance,lado,id_canal_colaboracion);
CREATE INDEX ix_invitaciones_destinatario ON solicitud_colaboracion(organizacion_id,id_responsable_invitado,id_solicitud_colaboracion) WHERE id_requerimiento IS NULL;

CREATE OR REPLACE FUNCTION exigir_titular_canal_colaboracion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM solicitud_colaboracion s
   WHERE s.id_solicitud_colaboracion=NEW.id_solicitud_colaboracion
     AND s.organizacion_id=NEW.organizacion_demanda_id AND s.organizacion_encargo_id=NEW.organizacion_encargo_id
     AND s.version_alcance=NEW.version_alcance AND s.estado='P'
     AND NOT EXISTS(SELECT 1 FROM decision_colaboracion d WHERE d.id_solicitud_colaboracion=s.id_solicitud_colaboracion AND d.lado=NEW.lado)
     AND ((NEW.lado='DEMANDA' AND NEW.id_rol_declarante=s.id_responsable_demanda AND s.id_decision_demanda IS NULL)
       OR (NEW.lado='INVITADO' AND NEW.id_rol_declarante=s.id_responsable_invitado AND s.id_decision_invitado IS NULL)
       OR (NEW.lado='ENCARGO' AND NEW.id_rol_declarante=s.id_responsable_encargo AND s.id_decision_encargo IS NULL))) THEN
  RAISE EXCEPTION 'El canal requiere titular responsable y alcance pendiente sin decisión propia' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION exigir_decisiones_de_concesion_colaboracion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM decision_colaboracion d JOIN decision_colaboracion e
    ON e.id_solicitud_colaboracion=d.id_solicitud_colaboracion
    JOIN solicitud_colaboracion s ON s.id_solicitud_colaboracion=d.id_solicitud_colaboracion
    WHERE d.id_decision_colaboracion=COALESCE(NEW.id_decision_demanda,NEW.id_decision_invitado)
      AND e.id_decision_colaboracion=NEW.id_decision_encargo
      AND d.id_solicitud_colaboracion=NEW.id_solicitud_colaboracion
      AND d.lado=CASE WHEN s.id_requerimiento IS NULL THEN 'INVITADO' ELSE 'DEMANDA' END AND e.lado='ENCARGO'
      AND d.decision IN('ACEPTAR','LIMITAR') AND e.decision IN('ACEPTAR','LIMITAR')
      AND d.version_alcance=NEW.version_alcance AND e.version_alcance=NEW.version_alcance
      AND COALESCE(s.id_decision_demanda,s.id_decision_invitado)=d.id_decision_colaboracion AND s.id_decision_encargo=e.id_decision_colaboracion
      AND s.estado='A') THEN
  RAISE EXCEPTION 'La concesión exige dos decisiones de sus lados sobre el mismo alcance' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION proteger_historia_colaboracion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP IN ('DELETE','TRUNCATE') OR TG_TABLE_NAME IN('decision_colaboracion','canal_colaboracion') THEN
  RAISE EXCEPTION 'La historia de colaboración se conserva y no se sobrescribe' USING ERRCODE='23514';
 END IF;
 IF TG_TABLE_NAME='solicitud_colaboracion' THEN
  IF (NEW.id_canal_demanda IS DISTINCT FROM OLD.id_canal_demanda AND EXISTS(SELECT 1 FROM decision_colaboracion d WHERE d.id_solicitud_colaboracion=OLD.id_solicitud_colaboracion AND d.lado='DEMANDA'))
     OR (NEW.id_canal_invitado IS DISTINCT FROM OLD.id_canal_invitado AND EXISTS(SELECT 1 FROM decision_colaboracion d WHERE d.id_solicitud_colaboracion=OLD.id_solicitud_colaboracion AND d.lado='INVITADO'))
     OR (NEW.id_canal_encargo IS DISTINCT FROM OLD.id_canal_encargo AND EXISTS(SELECT 1 FROM decision_colaboracion d WHERE d.id_solicitud_colaboracion=OLD.id_solicitud_colaboracion AND d.lado='ENCARGO')) THEN
   RAISE EXCEPTION 'El canal de una decisión existente no se reescribe; requiere nueva solicitud' USING ERRCODE='23514';
  END IF;
  IF ROW(NEW.id_solicitud_colaboracion,NEW.organizacion_id,NEW.organizacion_encargo_id,NEW.referencia,NEW.id_requerimiento,NEW.id_captacion,
        NEW.id_responsable_demanda,NEW.id_responsable_encargo,NEW.id_rol_solicitante,NEW.version_demanda,
        NEW.version_encargo,NEW.version_oferta,NEW.oferta_ref,NEW.alcance,NEW.huella_alcance,NEW.version_alcance,NEW.creada_en,
        NEW.id_responsable_invitado,NEW.version_invitado,NEW.id_revision_permiso,NEW.clausula_permiso,NEW.organizacion_solicitante_id)
    IS DISTINCT FROM
    ROW(OLD.id_solicitud_colaboracion,OLD.organizacion_id,OLD.organizacion_encargo_id,OLD.referencia,OLD.id_requerimiento,OLD.id_captacion,
        OLD.id_responsable_demanda,OLD.id_responsable_encargo,OLD.id_rol_solicitante,OLD.version_demanda,
        OLD.version_encargo,OLD.version_oferta,OLD.oferta_ref,OLD.alcance,OLD.huella_alcance,OLD.version_alcance,OLD.creada_en,
        OLD.id_responsable_invitado,OLD.version_invitado,OLD.id_revision_permiso,OLD.clausula_permiso,OLD.organizacion_solicitante_id) THEN
  RAISE EXCEPTION 'La solicitud y alcance originales son inmutables' USING ERRCODE='23514';
  END IF;
 ELSIF TG_TABLE_NAME='concesion_colaboracion' THEN
  IF OLD.revocada_en IS NOT NULL OR
     ROW(NEW.id_concesion_colaboracion,NEW.organizacion_id,NEW.organizacion_encargo_id,NEW.referencia,NEW.id_solicitud_colaboracion,
         NEW.id_decision_demanda,NEW.id_decision_invitado,NEW.id_decision_encargo,NEW.version_alcance,NEW.alcance,NEW.concedida_en)
     IS DISTINCT FROM
     ROW(OLD.id_concesion_colaboracion,OLD.organizacion_id,OLD.organizacion_encargo_id,OLD.referencia,OLD.id_solicitud_colaboracion,
         OLD.id_decision_demanda,OLD.id_decision_invitado,OLD.id_decision_encargo,OLD.version_alcance,OLD.alcance,OLD.concedida_en) THEN
   RAISE EXCEPTION 'La concesión original y su revocación se conservan' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
