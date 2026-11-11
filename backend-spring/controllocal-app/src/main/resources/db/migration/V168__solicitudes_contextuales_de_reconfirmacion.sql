-- CAT13/D2: disponibilidad real de la solicitud y frescura son hechos distintos.
CREATE TABLE solicitud_reconfirmacion (
 id uuid PRIMARY KEY,
 organizacion_id bigint NOT NULL REFERENCES organizacion(id_organizacion),
 tipo_recurso varchar(12) NOT NULL CHECK(tipo_recurso IN('ENCARGO','DEMANDA')),
 recurso_id bigint NOT NULL CHECK(recurso_id>0),
 operacion varchar(12) NOT NULL CHECK(operacion IN('VENTA','ALQUILER')),
 aspecto varchar(60) NOT NULL, contexto varchar(100) NOT NULL,
 confirmacion_base bigint NOT NULL CHECK(confirmacion_base>=0),
 base_frescura timestamptz,
 disponible_en timestamptz NOT NULL,
 recordatorio_en timestamptz NOT NULL,
 atencion_hasta timestamptz NOT NULL,
 rol_responsable bigint NOT NULL,
 estado varchar(20) NOT NULL CHECK(estado IN('PENDIENTE','POSPUESTA','DESCONOCE','RESUELTA','RETIRADA','SUSTITUIDA')),
 revisar_en date, motivo text,
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 recordatorio_emitido_en timestamptz, escalacion_emitida_en timestamptz,
 UNIQUE(organizacion_id,tipo_recurso,recurso_id,operacion,aspecto,contexto,confirmacion_base),
 UNIQUE(organizacion_id,id),
 FOREIGN KEY(organizacion_id,rol_responsable) REFERENCES detalle_agente(organizacion_id,id_persona_rol),
 CHECK(recordatorio_en=disponible_en+interval '144 hours' AND atencion_hasta=disponible_en+interval '168 hours'),
 CHECK((confirmacion_base=0 AND base_frescura IS NULL) OR (confirmacion_base>0 AND base_frescura IS NOT NULL)),
 CHECK(estado='POSPUESTA' OR revisar_en IS NULL)
);
CREATE INDEX ix_reconfirmacion_abierta ON solicitud_reconfirmacion(organizacion_id,rol_responsable,disponible_en)
 WHERE estado IN('PENDIENTE','POSPUESTA','DESCONOCE');
CREATE TABLE actuacion_reconfirmacion (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 solicitud uuid NOT NULL REFERENCES solicitud_reconfirmacion(id),
 version bigint NOT NULL, hecho varchar(30) NOT NULL,
 rol_actor bigint REFERENCES persona_rol(id_persona_rol),
 registrado_en timestamptz NOT NULL, contenido jsonb NOT NULL CHECK(jsonb_typeof(contenido)='object'),
 UNIQUE(solicitud,version,hecho)
);
CREATE TRIGGER conservar_actuacion_reconfirmacion BEFORE UPDATE OR DELETE ON actuacion_reconfirmacion
 FOR EACH ROW EXECUTE FUNCTION conservar_historia_negociacion();
CREATE TRIGGER conservar_actuacion_reconfirmacion_truncate BEFORE TRUNCATE ON actuacion_reconfirmacion
 FOR EACH STATEMENT EXECUTE FUNCTION conservar_historia_negociacion();
CREATE FUNCTION conservar_ambito_reconfirmacion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'La solicitud conserva su antecedente'; END IF;
 IF NOT ((NEW.tipo_recurso='ENCARGO' AND EXISTS(select 1 from captacion c where c.organizacion_id=NEW.organizacion_id and c.id_captacion=NEW.recurso_id))
   OR (NEW.tipo_recurso='DEMANDA' AND EXISTS(select 1 from requerimiento_cliente d where d.organizacion_id=NEW.organizacion_id and d.id_requerimiento=NEW.recurso_id)))
   THEN RAISE EXCEPTION 'La solicitud requiere un recurso de su organización'; END IF;
 IF TG_OP='UPDATE' AND ROW(OLD.id,OLD.organizacion_id,OLD.tipo_recurso,OLD.recurso_id,OLD.operacion,OLD.aspecto,
   OLD.contexto,OLD.confirmacion_base,OLD.base_frescura,OLD.disponible_en,OLD.recordatorio_en,OLD.atencion_hasta)
   IS DISTINCT FROM ROW(NEW.id,NEW.organizacion_id,NEW.tipo_recurso,NEW.recurso_id,NEW.operacion,NEW.aspecto,
   NEW.contexto,NEW.confirmacion_base,NEW.base_frescura,NEW.disponible_en,NEW.recordatorio_en,NEW.atencion_hasta)
   THEN RAISE EXCEPTION 'No se reinician el ámbito ni los relojes D2'; END IF;
 IF NEW.confirmacion_base>0 AND NOT EXISTS(select 1 from confirmacion_disponibilidad_contextual f
   where f.organizacion_id=NEW.organizacion_id and f.id_confirmacion=NEW.confirmacion_base
     and f.tipo_recurso=NEW.tipo_recurso and f.id_recurso=NEW.recurso_id and f.operacion=NEW.operacion
     and f.aspecto=NEW.aspecto and f.contexto=NEW.contexto and f.base_frescura=NEW.base_frescura and f.disponible)
   THEN RAISE EXCEPTION 'La solicitud requiere su confirmación positiva exacta'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER conservar_ambito_reconfirmacion BEFORE INSERT OR UPDATE OR DELETE ON solicitud_reconfirmacion
 FOR EACH ROW EXECUTE FUNCTION conservar_ambito_reconfirmacion();
