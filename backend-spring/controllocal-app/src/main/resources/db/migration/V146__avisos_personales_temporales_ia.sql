-- C6-CONS01/02: proyección personal de la causa144, sin nuevo reloj ni importancia inventada.
ALTER TABLE foto_propiedad ADD CONSTRAINT uq_foto_c6_aviso_origen UNIQUE(organizacion_id,id_foto,archivo_ref);
ALTER TABLE tarea
 ADD COLUMN original_material_ia_c6 uuid,
 ADD COLUMN revision_material_ia_c6 bigint,
 ADD COLUMN estado_causa_material_ia_c6 varchar(15),
 ADD CONSTRAINT uq_tarea_origen_material_ia_c6 UNIQUE(organizacion_id,id_tarea,original_material_ia_c6),
 ADD CONSTRAINT fk_tarea_foto_material_ia_c6 FOREIGN KEY(organizacion_id,entidad_id,original_material_ia_c6)
   REFERENCES foto_propiedad(organizacion_id,id_foto,archivo_ref),
 ADD CONSTRAINT fk_tarea_causa_material_ia_c6 FOREIGN KEY(organizacion_id,original_material_ia_c6,marca_causa)
   REFERENCES causa_caducidad_material_ia(organizacion_id,original_ref,huella);
ALTER TABLE tarea DROP CONSTRAINT ck_tarea_tipo_entidad;
ALTER TABLE tarea ADD CONSTRAINT ck_tarea_tipo_entidad CHECK(entidad_tipo IN(
 'PROSPECCION','CAPTACION','OPORTUNIDAD','INTERACCION','VISITA','SOLICITUD_ALQUILER',
 'INMUEBLE','PUBLICACION','CONTRATO_ALQUILER','CLIENTE_INTERESADO','PROPIETARIO','REQUERIMIENTO','FOTO_PROPIEDAD'));
ALTER TABLE tarea DROP CONSTRAINT ck_tarea_prioridad;
ALTER TABLE tarea ALTER COLUMN prioridad DROP NOT NULL;
ALTER TABLE tarea ADD CONSTRAINT ck_tarea_prioridad CHECK(CASE WHEN entidad_tipo='FOTO_PROPIEDAD'
 THEN prioridad IS NULL ELSE prioridad IS NOT NULL AND prioridad IN('ALTA','MEDIA','BAJA') END);
ALTER TABLE tarea ADD CONSTRAINT ck_tarea_fuente_material_ia_c6 CHECK(CASE WHEN entidad_tipo='FOTO_PROPIEDAD' THEN
 tipo='SEGUIMIENTO' AND original_material_ia_c6 IS NOT NULL AND revision_material_ia_c6 IS NOT NULL AND revision_material_ia_c6>=0
 AND estado_causa_material_ia_c6 IS NOT NULL AND estado_causa_material_ia_c6 IN('VIGENTE','SUSTITUIDA','FINALIZADA')
 AND estado IN('P','A') AND id_contrato_origen IS NULL AND id_seguimiento_prospeccion IS NULL
 ELSE original_material_ia_c6 IS NULL AND revision_material_ia_c6 IS NULL AND estado_causa_material_ia_c6 IS NULL END);
ALTER TABLE tarea DROP CONSTRAINT ck_tarea_origen_r27;
ALTER TABLE tarea ADD CONSTRAINT ck_tarea_origen_r27 CHECK(
 (id_seguimiento_prospeccion IS NULL AND marca_causa IS NULL AND id_antecedente IS NULL AND original_material_ia_c6 IS NULL)
 OR (id_seguimiento_prospeccion IS NOT NULL AND tipo='RECONTACTO' AND entidad_tipo='PROSPECCION'
     AND original_material_ia_c6 IS NULL AND marca_causa IS NOT NULL AND marca_causa~'^[0-9a-f]{64}$')
 OR (original_material_ia_c6 IS NOT NULL AND tipo='SEGUIMIENTO' AND entidad_tipo='FOTO_PROPIEDAD'
     AND id_seguimiento_prospeccion IS NULL AND marca_causa IS NOT NULL AND marca_causa~'^[0-9a-f]{64}$'));
DROP INDEX uq_tarea_abierta_por_entidad;
CREATE UNIQUE INDEX uq_tarea_abierta_por_entidad ON tarea(organizacion_id,id_rol_agente,entidad_tipo,entidad_id)
 WHERE estado IN('P','E') AND id_seguimiento_prospeccion IS NULL
 AND (original_material_ia_c6 IS NULL OR estado_causa_material_ia_c6='VIGENTE');
CREATE UNIQUE INDEX uq_tarea_causa_material_ia_c6 ON tarea(organizacion_id,id_rol_agente,original_material_ia_c6,marca_causa)
 WHERE original_material_ia_c6 IS NOT NULL AND estado_causa_material_ia_c6='VIGENTE';

-- La guarda R27 conserva sus invariantes; su antecedente sólo se aplica a R27.
CREATE OR REPLACE FUNCTION conservar_origen_tarea_r27_c4() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND (OLD.id_seguimiento_prospeccion IS NOT NULL OR NEW.id_seguimiento_prospeccion IS NOT NULL)
 AND ROW(NEW.id_tarea,NEW.organizacion_id,NEW.tipo,NEW.entidad_tipo,NEW.entidad_id,NEW.marca_causa,NEW.id_antecedente,NEW.id_seguimiento_prospeccion,NEW.fecha_programada)
 IS DISTINCT FROM ROW(OLD.id_tarea,OLD.organizacion_id,OLD.tipo,OLD.entidad_tipo,OLD.entidad_id,OLD.marca_causa,OLD.id_antecedente,OLD.id_seguimiento_prospeccion,OLD.fecha_programada) THEN
   RAISE EXCEPTION 'La ocurrencia conserva causa, origen, antecedente y fecha inicial' USING ERRCODE='23514';
 END IF;
 IF NEW.id_seguimiento_prospeccion IS NOT NULL AND NEW.id_antecedente IS NOT NULL AND NOT EXISTS(
   SELECT 1 FROM tarea a
   JOIN seguimiento_prospeccion previo ON previo.organizacion_id=a.organizacion_id AND previo.id_seguimiento=a.id_seguimiento_prospeccion
   JOIN seguimiento_prospeccion actual ON actual.organizacion_id=previo.organizacion_id AND actual.id_seguimiento=NEW.id_seguimiento_prospeccion
   WHERE a.organizacion_id=NEW.organizacion_id AND a.id_tarea=NEW.id_antecedente
     AND previo.id_prospeccion=actual.id_prospeccion AND previo.destinatario_clave=actual.destinatario_clave AND previo.finalidad=actual.finalidad) THEN
   RAISE EXCEPTION 'El antecedente de tarea conserva prospección, destinatario y finalidad' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;

CREATE FUNCTION conservar_origen_aviso_material_ia_c6() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND (OLD.original_material_ia_c6 IS NOT NULL OR NEW.original_material_ia_c6 IS NOT NULL) THEN
   IF ROW(NEW.id_tarea,NEW.organizacion_id,NEW.tipo,NEW.entidad_tipo,NEW.entidad_id,NEW.id_rol_agente,NEW.original_material_ia_c6,NEW.id_antecedente,NEW.fecha_programada)
   IS DISTINCT FROM ROW(OLD.id_tarea,OLD.organizacion_id,OLD.tipo,OLD.entidad_tipo,OLD.entidad_id,OLD.id_rol_agente,OLD.original_material_ia_c6,OLD.id_antecedente,OLD.fecha_programada) THEN
     RAISE EXCEPTION 'La proyección personal conserva original, destinatario, antecedente y detección' USING ERRCODE='23514';
   END IF;
   IF OLD.estado_causa_material_ia_c6<>'VIGENTE' AND
      ROW(NEW.marca_causa,NEW.revision_material_ia_c6,NEW.estado_causa_material_ia_c6,NEW.estado)
      IS DISTINCT FROM ROW(OLD.marca_causa,OLD.revision_material_ia_c6,OLD.estado_causa_material_ia_c6,OLD.estado) THEN
     RAISE EXCEPTION 'Una causa finalizada o sustituida conserva su antecedente' USING ERRCODE='23514';
   END IF;
 END IF;
 IF NEW.original_material_ia_c6 IS NOT NULL AND NEW.id_antecedente IS NOT NULL AND NOT EXISTS(
   SELECT 1 FROM tarea a WHERE a.organizacion_id=NEW.organizacion_id AND a.id_tarea=NEW.id_antecedente
     AND a.original_material_ia_c6=NEW.original_material_ia_c6 AND a.id_rol_agente=NEW.id_rol_agente
     AND a.id_tarea<NEW.id_tarea) THEN
   RAISE EXCEPTION 'El antecedente conserva original y contexto personal, sin ciclos' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER conservar_origen_aviso_material_ia_c6 BEFORE INSERT OR UPDATE ON tarea
 FOR EACH ROW EXECUTE FUNCTION conservar_origen_aviso_material_ia_c6();

-- Sólo referencias de proyección: los episodios y fechas viven exclusivamente en144.
CREATE TABLE observacion_aviso_material_ia_c6(
 organizacion_id bigint NOT NULL,
 id_observacion bigint GENERATED ALWAYS AS IDENTITY,
 id_tarea bigint NOT NULL,
 original_ref uuid NOT NULL,
 huella varchar(64) NOT NULL CHECK(huella~'^[0-9a-f]{64}$'),
 revision bigint NOT NULL CHECK(revision>=0),
 tipo text NOT NULL CHECK(tipo IN('CREACION','ACTUALIZACION','CANCELACION','CAUSA_FINALIZADA','CAUSA_SUSTITUIDA')),
 observada_en timestamptz NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(organizacion_id,id_observacion),
 FOREIGN KEY(organizacion_id,id_tarea,original_ref) REFERENCES tarea(organizacion_id,id_tarea,original_material_ia_c6),
 FOREIGN KEY(organizacion_id,original_ref,huella) REFERENCES causa_caducidad_material_ia(organizacion_id,original_ref,huella)
);
-- Volver de [A,B] a [A] es otra observación aunque [A] se observó antes.
-- El productor serializado deduplica sólo contra la última observación, no contra toda la historia.
CREATE INDEX ix_observacion_aviso_material_ia_c6_tarea ON observacion_aviso_material_ia_c6(organizacion_id,id_tarea,id_observacion DESC);
CREATE FUNCTION conservar_observacion_aviso_material_ia_c6() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'La observación personal conserva su historia mínima' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER conservar_observacion_aviso_material_ia_c6 BEFORE UPDATE OR DELETE ON observacion_aviso_material_ia_c6
 FOR EACH ROW EXECUTE FUNCTION conservar_observacion_aviso_material_ia_c6();
CREATE TRIGGER no_truncar_observacion_aviso_material_ia_c6 BEFORE TRUNCATE ON observacion_aviso_material_ia_c6
 FOR EACH STATEMENT EXECUTE FUNCTION conservar_observacion_aviso_material_ia_c6();
