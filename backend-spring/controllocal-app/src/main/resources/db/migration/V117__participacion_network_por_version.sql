-- Aprobación humana Network del 28/10/2026. Construcción/pruebas; no habilita producción.
-- Participación por Encargo y versión, separada de su estado comercial y de colaboración bilateral.
CREATE TABLE participacion_network (
 id_participacion_network bigserial PRIMARY KEY,
 referencia uuid NOT NULL UNIQUE,
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 id_captacion bigint NOT NULL,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 estado varchar(1) NOT NULL CHECK(estado IN('N','A','S','I')),
 id_version_actual bigint, numero_version bigint NOT NULL DEFAULT 0 CHECK(numero_version>=0),
 id_solicitud_pendiente bigint,
 causas text NOT NULL DEFAULT '[]' CHECK(jsonb_typeof(causas::jsonb)='array'),
 suspendida_en timestamptz, creada_en timestamptz NOT NULL,
 CONSTRAINT uq_participacion_network_org UNIQUE(organizacion_id,id_participacion_network),
 CONSTRAINT uq_participacion_network_encargo UNIQUE(organizacion_id,id_captacion),
 CONSTRAINT fk_participacion_network_encargo FOREIGN KEY(organizacion_id,id_captacion) REFERENCES captacion(organizacion_id,id_captacion)
);
CREATE TABLE solicitud_participacion_network (
 id_solicitud_network bigserial PRIMARY KEY,
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 referencia uuid NOT NULL UNIQUE, version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 id_participacion_network bigint NOT NULL, id_rol_solicitante bigint NOT NULL,
 accion varchar(10) NOT NULL CHECK(accion IN('ACTIVAR','MODIFICAR','DESACTIVAR','REACTIVAR')),
 estado varchar(1) NOT NULL CHECK(estado IN('P','O','A','R')),
 version_participacion_presentada bigint NOT NULL CHECK(version_participacion_presentada>=0),
 id_responsable bigint NOT NULL, id_broker bigint, individual boolean NOT NULL,
 version_encargo bigint NOT NULL CHECK(version_encargo>=0),version_fisica bigint NOT NULL CHECK(version_fisica>=0),
 politica_version varchar(80) NOT NULL,datos text NOT NULL CHECK(jsonb_typeof(datos::jsonb)='object'),
 campos text NOT NULL CHECK(jsonb_typeof(campos::jsonb)='array'),finalidad varchar(80) NOT NULL,
 desde timestamptz NOT NULL,hasta timestamptz NOT NULL CHECK(hasta>desde),
 huella_datos varchar(64) NOT NULL CHECK(huella_datos ~ '^[a-fA-F0-9]{64}$'),
 huella_autoridad varchar(64) NOT NULL CHECK(huella_autoridad ~ '^[a-fA-F0-9]{64}$'),
 diferencias text NOT NULL CHECK(jsonb_typeof(diferencias::jsonb)='array'),
 motivo text NOT NULL CHECK(btrim(motivo)<>''),creada_en timestamptz NOT NULL,id_ultima_decision bigint,
 CONSTRAINT ck_solicitud_network_modalidad CHECK((individual AND id_broker IS NULL) OR (NOT individual AND id_broker IS NOT NULL)),
 CONSTRAINT uq_solicitud_network_org UNIQUE(organizacion_id,id_solicitud_network),
 CONSTRAINT uq_solicitud_network_participacion UNIQUE(organizacion_id,id_participacion_network,id_solicitud_network),
 CONSTRAINT fk_solicitud_network_participacion FOREIGN KEY(organizacion_id,id_participacion_network) REFERENCES participacion_network(organizacion_id,id_participacion_network),
 CONSTRAINT fk_solicitud_network_solicitante FOREIGN KEY(organizacion_id,id_rol_solicitante) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 CONSTRAINT fk_solicitud_network_responsable FOREIGN KEY(organizacion_id,id_responsable) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 CONSTRAINT fk_solicitud_network_broker FOREIGN KEY(organizacion_id,id_broker) REFERENCES persona_rol(organizacion_id,id_persona_rol)
);
CREATE TABLE decision_participacion_network (
 id_decision_network bigserial PRIMARY KEY,
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 id_solicitud_network bigint NOT NULL,id_participacion_network bigint NOT NULL,id_rol_decisor bigint NOT NULL,
 decision varchar(8) NOT NULL CHECK(decision IN('APROBAR','OBSERVAR','RECHAZAR')),
 version_solicitud_revisada bigint NOT NULL CHECK(version_solicitud_revisada>=0),
 huella_datos varchar(64) NOT NULL,huella_autoridad varchar(64) NOT NULL,
 motivo text NOT NULL CHECK(btrim(motivo)<>''),registrada_en timestamptz NOT NULL,
 CONSTRAINT uq_decision_network_contexto UNIQUE(organizacion_id,id_participacion_network,id_solicitud_network,id_decision_network),
 CONSTRAINT uq_decision_network_resultado UNIQUE(organizacion_id,id_participacion_network,id_solicitud_network,id_decision_network,decision),
 CONSTRAINT fk_decision_network_solicitud FOREIGN KEY(organizacion_id,id_participacion_network,id_solicitud_network) REFERENCES solicitud_participacion_network(organizacion_id,id_participacion_network,id_solicitud_network),
 CONSTRAINT fk_decision_network_actor FOREIGN KEY(organizacion_id,id_rol_decisor) REFERENCES persona_rol(organizacion_id,id_persona_rol)
);
CREATE TABLE version_participacion_network (
 id_version_network bigserial PRIMARY KEY,
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),referencia uuid NOT NULL UNIQUE,
 id_participacion_network bigint NOT NULL,numero bigint NOT NULL CHECK(numero>0),
 id_solicitud_network bigint NOT NULL,id_decision_network bigint NOT NULL,
 decision_aprobada varchar(8) NOT NULL DEFAULT 'APROBAR' CHECK(decision_aprobada='APROBAR'),
 id_responsable bigint NOT NULL,id_broker bigint,individual boolean NOT NULL,
 version_encargo bigint NOT NULL CHECK(version_encargo>=0),version_fisica bigint NOT NULL CHECK(version_fisica>=0),
 politica_version varchar(80) NOT NULL,datos text NOT NULL CHECK(jsonb_typeof(datos::jsonb)='object'),
 campos text NOT NULL CHECK(jsonb_typeof(campos::jsonb)='array'),finalidad varchar(80) NOT NULL,
 desde timestamptz NOT NULL,hasta timestamptz NOT NULL CHECK(hasta>desde),
 huella_datos varchar(64) NOT NULL,huella_autoridad varchar(64) NOT NULL,creada_en timestamptz NOT NULL,
 CONSTRAINT ck_version_network_modalidad CHECK((individual AND id_broker IS NULL) OR (NOT individual AND id_broker IS NOT NULL)),
 CONSTRAINT uq_version_network_numero UNIQUE(organizacion_id,id_participacion_network,numero,id_version_network),
 CONSTRAINT uq_version_network_orden UNIQUE(organizacion_id,id_participacion_network,numero),
 CONSTRAINT uq_version_network_solicitud UNIQUE(organizacion_id,id_solicitud_network),
 CONSTRAINT fk_version_network_decision FOREIGN KEY(organizacion_id,id_participacion_network,id_solicitud_network,id_decision_network,decision_aprobada) REFERENCES decision_participacion_network(organizacion_id,id_participacion_network,id_solicitud_network,id_decision_network,decision),
 CONSTRAINT fk_version_network_responsable FOREIGN KEY(organizacion_id,id_responsable) REFERENCES persona_rol(organizacion_id,id_persona_rol),
 CONSTRAINT fk_version_network_broker FOREIGN KEY(organizacion_id,id_broker) REFERENCES persona_rol(organizacion_id,id_persona_rol)
);
ALTER TABLE participacion_network ADD CONSTRAINT fk_participacion_network_version FOREIGN KEY(organizacion_id,id_participacion_network,numero_version,id_version_actual) REFERENCES version_participacion_network(organizacion_id,id_participacion_network,numero,id_version_network);
ALTER TABLE participacion_network ADD CONSTRAINT fk_participacion_network_pendiente FOREIGN KEY(organizacion_id,id_participacion_network,id_solicitud_pendiente) REFERENCES solicitud_participacion_network(organizacion_id,id_participacion_network,id_solicitud_network);
ALTER TABLE solicitud_participacion_network ADD CONSTRAINT fk_solicitud_network_ultima_decision FOREIGN KEY(organizacion_id,id_participacion_network,id_solicitud_network,id_ultima_decision) REFERENCES decision_participacion_network(organizacion_id,id_participacion_network,id_solicitud_network,id_decision_network);

CREATE FUNCTION exigir_version_network_aprobada() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s solicitud_participacion_network; d decision_participacion_network;
BEGIN
 SELECT * INTO s FROM solicitud_participacion_network WHERE id_solicitud_network=NEW.id_solicitud_network;
 SELECT * INTO d FROM decision_participacion_network WHERE id_decision_network=NEW.id_decision_network;
 IF s.estado<>'A' OR s.accion='DESACTIVAR' OR d.decision<>'APROBAR' OR
    d.huella_datos IS DISTINCT FROM NEW.huella_datos OR d.huella_autoridad IS DISTINCT FROM NEW.huella_autoridad OR
    ROW(s.id_responsable,s.id_broker,s.individual,s.version_encargo,s.version_fisica,s.politica_version,s.datos,s.campos,s.finalidad,s.desde,s.hasta,s.huella_datos,s.huella_autoridad)
    IS DISTINCT FROM ROW(NEW.id_responsable,NEW.id_broker,NEW.individual,NEW.version_encargo,NEW.version_fisica,NEW.politica_version,NEW.datos,NEW.campos,NEW.finalidad,NEW.desde,NEW.hasta,NEW.huella_datos,NEW.huella_autoridad) THEN
  RAISE EXCEPTION 'La versión Network debe ser exactamente la propuesta aprobada' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_version_network_aprobada BEFORE INSERT ON version_participacion_network
FOR EACH ROW EXECUTE FUNCTION exigir_version_network_aprobada();

CREATE FUNCTION proteger_historia_network() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP IN ('DELETE','TRUNCATE') OR TG_TABLE_NAME IN('decision_participacion_network','version_participacion_network') THEN
  RAISE EXCEPTION 'La historia Network es inmutable' USING ERRCODE='23514';
 END IF;
 IF TG_TABLE_NAME='solicitud_participacion_network' THEN
  IF (to_jsonb(NEW)-ARRAY['version','estado','id_ultima_decision']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['version','estado','id_ultima_decision']) THEN
   RAISE EXCEPTION 'La propuesta Network original se conserva' USING ERRCODE='23514';
  END IF;
 ELSIF TG_TABLE_NAME='participacion_network' THEN
  IF ROW(NEW.id_participacion_network,NEW.referencia,NEW.organizacion_id,NEW.id_captacion,NEW.creada_en) IS DISTINCT FROM ROW(OLD.id_participacion_network,OLD.referencia,OLD.organizacion_id,OLD.id_captacion,OLD.creada_en) THEN
   RAISE EXCEPTION 'El contexto Network original se conserva' USING ERRCODE='23514';
  END IF;
  IF NEW.id_version_actual IS DISTINCT FROM OLD.id_version_actual AND NEW.numero_version<>OLD.numero_version+1 THEN
   RAISE EXCEPTION 'Una versión nueva requiere su siguiente número' USING ERRCODE='23514';
  END IF;
  IF OLD.estado='I' AND NEW.estado='A' AND NEW.id_version_actual IS NOT DISTINCT FROM OLD.id_version_actual THEN
   RAISE EXCEPTION 'Reactivar exige una nueva versión aprobada' USING ERRCODE='23514';
  END IF;
  IF OLD.estado='S' AND NEW.estado='A' AND NEW.id_version_actual IS NOT DISTINCT FROM OLD.id_version_actual AND OLD.causas::jsonb<>'["FRESCURA"]'::jsonb THEN
   RAISE EXCEPTION 'Sólo frescura admite retorno de la misma autorización' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_participacion_network_historia BEFORE UPDATE OR DELETE ON participacion_network FOR EACH ROW EXECUTE FUNCTION proteger_historia_network();
CREATE TRIGGER tg_solicitud_network_historia BEFORE UPDATE OR DELETE ON solicitud_participacion_network FOR EACH ROW EXECUTE FUNCTION proteger_historia_network();
CREATE TRIGGER tg_decision_network_historia BEFORE UPDATE OR DELETE ON decision_participacion_network FOR EACH ROW EXECUTE FUNCTION proteger_historia_network();
CREATE TRIGGER tg_version_network_historia BEFORE UPDATE OR DELETE ON version_participacion_network FOR EACH ROW EXECUTE FUNCTION proteger_historia_network();
INSERT INTO entidad_tipo(codigo,descripcion,auditable,referenciable) VALUES
 ('PARTICIPACION_NETWORK','Participación autorizada por Encargo',true,true),
 ('SOLICITUD_NETWORK','Solicitud de participación Network',true,true);

-- La conservación también rige TRUNCATE, que no dispara triggers por fila.
CREATE TRIGGER tg_participacion_network_no_truncate BEFORE TRUNCATE ON participacion_network
FOR EACH STATEMENT EXECUTE FUNCTION proteger_historia_network();
CREATE TRIGGER tg_solicitud_participacion_network_no_truncate BEFORE TRUNCATE ON solicitud_participacion_network
FOR EACH STATEMENT EXECUTE FUNCTION proteger_historia_network();
CREATE TRIGGER tg_decision_participacion_network_no_truncate BEFORE TRUNCATE ON decision_participacion_network
FOR EACH STATEMENT EXECUTE FUNCTION proteger_historia_network();
CREATE TRIGGER tg_version_participacion_network_no_truncate BEFORE TRUNCATE ON version_participacion_network
FOR EACH STATEMENT EXECUTE FUNCTION proteger_historia_network();
