-- CAT-E28/E34 y D6: fuente material y conservación de ocurrencias, sin modificar migraciones anteriores.
-- Integrada por Root desde propuesta155 transferida; sólo base de pruebas autorizada.

ALTER TABLE tarea ADD COLUMN huella_causa_c7 varchar(64), ADD COLUMN fuente_causa_c7 text,
 ADD COLUMN estado_causa_c7 varchar(15);
ALTER TABLE alerta ADD COLUMN huella_causa_c7 varchar(64), ADD COLUMN fuente_causa_c7 text,
 ADD COLUMN estado_causa_c7 varchar(15), ADD COLUMN id_antecedente_c7 bigint,
 ADD CONSTRAINT fk_alerta_antecedente_c7 FOREIGN KEY(organizacion_id,id_antecedente_c7)
   REFERENCES alerta(organizacion_id,id_alerta);

ALTER TABLE tarea ADD CONSTRAINT ck_tarea_causa_c7 CHECK(CASE WHEN huella_causa_c7 IS NULL THEN
 fuente_causa_c7 IS NULL AND estado_causa_c7 IS NULL
 ELSE huella_causa_c7~'^[0-9a-f]{64}$' AND fuente_causa_c7 IS NOT NULL
  AND estado_causa_c7 IS NOT NULL AND estado_causa_c7 IN('VIGENTE','RESUELTA','RETIRADA','SUSTITUIDA')
  AND entidad_tipo='SOLICITUD_ALQUILER' AND tipo IN('SEGUIMIENTO','SUBIR_DOCUMENTOS')
  AND id_seguimiento_prospeccion IS NULL AND original_material_ia_c6 IS NULL AND marca_causa IS NULL
  AND (jsonb_typeof(fuente_causa_c7::jsonb)='object'
   AND fuente_causa_c7::jsonb->>'esquema'='1'
   AND fuente_causa_c7::jsonb#>>'{recurso,tipo}'=entidad_tipo
   AND fuente_causa_c7::jsonb#>>'{recurso,id}'=entidad_id::text
   AND fuente_causa_c7::jsonb->>'finalidad'='SEGUIMIENTO_SOLICITUD'
   AND fuente_causa_c7::jsonb#>>'{hecho,tipo}'='HISTORIAL_ESTADO'
   AND (fuente_causa_c7::jsonb#>>'{hecho,referencia}')~'^[1-9][0-9]*$'
   AND (fuente_causa_c7::jsonb->>'responsableAlDetectar')~'^[1-9][0-9]*$') IS TRUE END);

ALTER TABLE alerta ADD CONSTRAINT ck_alerta_causa_c7 CHECK(CASE WHEN huella_causa_c7 IS NULL THEN
 fuente_causa_c7 IS NULL AND estado_causa_c7 IS NULL AND id_antecedente_c7 IS NULL
 ELSE huella_causa_c7~'^[0-9a-f]{64}$' AND fuente_causa_c7 IS NOT NULL AND marca_causa IS NULL
  AND estado_causa_c7 IS NOT NULL AND estado_causa_c7 IN('VIGENTE','RESUELTA','RETIRADA','SUSTITUIDA')
  AND (jsonb_typeof(fuente_causa_c7::jsonb)='object'
   AND fuente_causa_c7::jsonb->>'esquema'='1'
   AND fuente_causa_c7::jsonb#>>'{recurso,tipo}'=entidad_tipo
   AND fuente_causa_c7::jsonb#>>'{recurso,id}'=entidad_id::text
   AND fuente_causa_c7::jsonb->>'finalidad'=tipo
   AND (fuente_causa_c7::jsonb#>>'{hecho,referencia}')~'^[1-9][0-9]*$'
   AND (fuente_causa_c7::jsonb->>'responsableAlDetectar')~'^[1-9][0-9]*$') IS TRUE END);

-- Las ramas previas R27/C6 se conservan; C7 no finge ser una de ellas.
ALTER TABLE tarea DROP CONSTRAINT ck_tarea_origen_r27;
ALTER TABLE tarea ADD CONSTRAINT ck_tarea_origen_r27 CHECK(
 (id_seguimiento_prospeccion IS NULL AND marca_causa IS NULL AND id_antecedente IS NULL AND original_material_ia_c6 IS NULL)
 OR (id_seguimiento_prospeccion IS NOT NULL AND tipo='RECONTACTO' AND entidad_tipo='PROSPECCION'
     AND original_material_ia_c6 IS NULL AND marca_causa IS NOT NULL AND marca_causa~'^[0-9a-f]{64}$')
 OR (original_material_ia_c6 IS NOT NULL AND tipo='SEGUIMIENTO' AND entidad_tipo='FOTO_PROPIEDAD'
     AND id_seguimiento_prospeccion IS NULL AND marca_causa IS NOT NULL AND marca_causa~'^[0-9a-f]{64}$')
 OR (huella_causa_c7 IS NOT NULL AND entidad_tipo='SOLICITUD_ALQUILER' AND marca_causa IS NULL
     AND id_seguimiento_prospeccion IS NULL AND original_material_ia_c6 IS NULL));

DROP INDEX uq_tarea_abierta_por_entidad;
CREATE UNIQUE INDEX uq_tarea_abierta_por_entidad ON tarea(organizacion_id,id_rol_agente,entidad_tipo,entidad_id)
 WHERE estado IN('P','E') AND id_seguimiento_prospeccion IS NULL AND huella_causa_c7 IS NULL
 AND (original_material_ia_c6 IS NULL OR estado_causa_material_ia_c6='VIGENTE');
CREATE UNIQUE INDEX uq_tarea_causa_c7 ON tarea(organizacion_id,huella_causa_c7) WHERE huella_causa_c7 IS NOT NULL;
CREATE UNIQUE INDEX uq_alerta_causa_c7 ON alerta(organizacion_id,huella_causa_c7) WHERE huella_causa_c7 IS NOT NULL;
CREATE INDEX ix_tarea_antecedente_c7 ON tarea(organizacion_id,entidad_tipo,entidad_id,id_tarea DESC) WHERE huella_causa_c7 IS NOT NULL;
CREATE INDEX ix_alerta_antecedente_c7 ON alerta(organizacion_id,entidad_tipo,entidad_id,tipo,id_alerta DESC) WHERE huella_causa_c7 IS NOT NULL;

CREATE FUNCTION conservar_causa_tarea_c7() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE fuente jsonb; origen bigint;
BEGIN
 IF TG_OP='DELETE' THEN
  IF OLD.huella_causa_c7 IS NOT NULL THEN RAISE EXCEPTION 'La ocurrencia conserva su historia' USING ERRCODE='23514'; END IF;
  RETURN OLD;
 END IF;
 IF TG_OP='UPDATE' AND (OLD.huella_causa_c7 IS NOT NULL OR NEW.huella_causa_c7 IS NOT NULL) THEN
  IF ROW(NEW.id_tarea,NEW.organizacion_id,NEW.tipo,NEW.entidad_tipo,NEW.entidad_id,NEW.huella_causa_c7,
         NEW.fuente_causa_c7,NEW.id_antecedente,NEW.fecha_programada,NEW.descripcion)
   IS DISTINCT FROM ROW(OLD.id_tarea,OLD.organizacion_id,OLD.tipo,OLD.entidad_tipo,OLD.entidad_id,OLD.huella_causa_c7,
         OLD.fuente_causa_c7,OLD.id_antecedente,OLD.fecha_programada,OLD.descripcion) THEN
   RAISE EXCEPTION 'La ocurrencia conserva fuente, causa, antecedente y detección' USING ERRCODE='23514';
  END IF;
  IF OLD.estado_causa_c7<>'VIGENTE' AND ROW(NEW.estado_causa_c7,NEW.estado,NEW.id_rol_agente)
      IS DISTINCT FROM ROW(OLD.estado_causa_c7,OLD.estado,OLD.id_rol_agente) THEN
   RAISE EXCEPTION 'Una causa retirada conserva su resultado' USING ERRCODE='23514';
  END IF;
 END IF;
 IF NEW.huella_causa_c7 IS NULL THEN RETURN NEW; END IF;
 fuente=NEW.fuente_causa_c7::jsonb; origen=(fuente#>>'{hecho,referencia}')::bigint;
 IF NOT EXISTS(SELECT 1 FROM historial_estado h WHERE h.organizacion_id=NEW.organizacion_id
       AND h.id_historial=origen AND h.entidad_tipo='SOLICITUD_ALQUILER' AND h.id_entidad=NEW.entidad_id
       AND h.estado_nuevo=fuente->>'estadoFuente'
       AND h.fecha_evento=(fuente->>'registradoEn')::timestamptz) THEN
  RAISE EXCEPTION 'La causa exige su transición real en la misma organización' USING ERRCODE='23514';
 END IF;
 IF TG_OP='INSERT' AND (fuente->>'responsableAlDetectar')::bigint<>NEW.id_rol_agente THEN
  RAISE EXCEPTION 'La detección conserva su responsable original' USING ERRCODE='23514';
 END IF;
 IF (TG_OP='INSERT' OR NEW.id_rol_agente IS DISTINCT FROM OLD.id_rol_agente) AND NOT EXISTS(
       SELECT 1 FROM solicitud_alquiler s WHERE s.organizacion_id=NEW.organizacion_id
        AND s.id_solicitud=NEW.entidad_id AND s.id_rol_agente=NEW.id_rol_agente) THEN
  RAISE EXCEPTION 'El destinatario procede de la asignación canónica' USING ERRCODE='23514';
 END IF;
 IF NEW.id_antecedente IS NOT NULL AND NOT EXISTS(SELECT 1 FROM tarea t
      WHERE t.organizacion_id=NEW.organizacion_id AND t.id_tarea=NEW.id_antecedente AND t.id_tarea<NEW.id_tarea
       AND t.entidad_tipo=NEW.entidad_tipo AND t.entidad_id=NEW.entidad_id AND t.huella_causa_c7 IS NOT NULL
       AND t.fuente_causa_c7::jsonb->>'finalidad'=fuente->>'finalidad') THEN
  RAISE EXCEPTION 'El antecedente conserva recurso, ámbito y finalidad, sin ciclos' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER conservar_causa_tarea_c7 BEFORE INSERT OR UPDATE OR DELETE ON tarea
 FOR EACH ROW EXECUTE FUNCTION conservar_causa_tarea_c7();

CREATE FUNCTION conservar_causa_alerta_c7() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE fuente jsonb; origen bigint; clase text; valida boolean;
BEGIN
 IF TG_OP='DELETE' THEN
  IF OLD.huella_causa_c7 IS NOT NULL THEN RAISE EXCEPTION 'El aviso conserva su antecedente' USING ERRCODE='23514'; END IF;
  RETURN OLD;
 END IF;
 IF TG_OP='UPDATE' AND (OLD.huella_causa_c7 IS NOT NULL OR NEW.huella_causa_c7 IS NOT NULL) THEN
  IF ROW(NEW.id_alerta,NEW.organizacion_id,NEW.tipo,NEW.severidad,NEW.entidad_tipo,NEW.entidad_id,NEW.id_rol_agente,
         NEW.mensaje,NEW.huella_causa_c7,NEW.fuente_causa_c7,NEW.id_antecedente_c7,NEW.fecha_generacion)
   IS DISTINCT FROM ROW(OLD.id_alerta,OLD.organizacion_id,OLD.tipo,OLD.severidad,OLD.entidad_tipo,OLD.entidad_id,OLD.id_rol_agente,
         OLD.mensaje,OLD.huella_causa_c7,OLD.fuente_causa_c7,OLD.id_antecedente_c7,OLD.fecha_generacion) THEN
   RAISE EXCEPTION 'El aviso conserva identidad y contenido de origen' USING ERRCODE='23514';
  END IF;
  IF OLD.estado_causa_c7<>'VIGENTE' AND ROW(NEW.estado_causa_c7,NEW.estado,NEW.fecha_resolucion)
      IS DISTINCT FROM ROW(OLD.estado_causa_c7,OLD.estado,OLD.fecha_resolucion) THEN
   RAISE EXCEPTION 'La causa retirada del aviso conserva su resultado' USING ERRCODE='23514';
  END IF;
 END IF;
 IF NEW.huella_causa_c7 IS NULL THEN RETURN NEW; END IF;
 fuente=NEW.fuente_causa_c7::jsonb; origen=(fuente#>>'{hecho,referencia}')::bigint; clase=fuente#>>'{hecho,tipo}';
 valida=CASE clase
  WHEN 'HISTORIAL_ESTADO' THEN EXISTS(SELECT 1 FROM historial_estado h WHERE h.organizacion_id=NEW.organizacion_id
      AND h.id_historial=origen AND h.entidad_tipo=NEW.entidad_tipo AND h.id_entidad=NEW.entidad_id)
  WHEN 'DOCUMENTO_SOLICITUD' THEN EXISTS(SELECT 1 FROM documento_solicitud d WHERE d.organizacion_id=NEW.organizacion_id
      AND d.id_documento=origen AND NEW.entidad_tipo='SOLICITUD_ALQUILER' AND d.id_solicitud=NEW.entidad_id)
  WHEN 'EVALUACION_SOLICITUD' THEN EXISTS(SELECT 1 FROM evaluacion_solicitud e WHERE e.organizacion_id=NEW.organizacion_id
      AND e.id_evaluacion=origen AND NEW.entidad_tipo='SOLICITUD_ALQUILER' AND e.id_solicitud=NEW.entidad_id)
  WHEN 'REVISION_ENCARGO' THEN EXISTS(SELECT 1 FROM revision_encargo r WHERE r.organizacion_id=NEW.organizacion_id
      AND r.id_revision_encargo=origen AND NEW.entidad_tipo='CAPTACION' AND r.id_captacion=NEW.entidad_id
      AND (fuente#>>'{hecho,version}')::bigint=r.version_encargo)
  WHEN 'COMISION_MOVIMIENTO' THEN EXISTS(SELECT 1 FROM comision_movimiento m JOIN comision_liquidacion c
      ON c.organizacion_id=m.organizacion_id AND c.id_comision_liquidacion=m.id_comision_liquidacion
      WHERE m.organizacion_id=NEW.organizacion_id AND m.id_comision_movimiento=origen
       AND NEW.entidad_tipo='CONTRATO_ALQUILER' AND c.id_contrato_alquiler=NEW.entidad_id)
  WHEN 'COMANDO_IDEMPOTENTE' THEN EXISTS(SELECT 1 FROM comando_idempotente x JOIN comision_liquidacion c
      ON c.organizacion_id=x.organizacion_id AND c.id_comision_liquidacion=x.entidad_id
      WHERE x.organizacion_id=NEW.organizacion_id AND x.id_comando=origen AND x.tipo_comando='ASIGNAR_REPARTO_COMISION'
       AND x.entidad_tipo='COMISION_LIQUIDACION' AND NEW.entidad_tipo='CONTRATO_ALQUILER' AND c.id_contrato_alquiler=NEW.entidad_id)
  WHEN 'REVISION_HITO_CONTRACTUAL' THEN EXISTS(SELECT 1 FROM revision_hito_contractual r JOIN expediente_operacion e
      ON e.organizacion_id=r.organizacion_id AND e.id_expediente=r.id_expediente WHERE r.organizacion_id=NEW.organizacion_id
       AND r.id_revision_hito=origen AND r.tipo_registro='CONFIRMACION' AND NEW.entidad_tipo='OPORTUNIDAD' AND e.id_oportunidad=NEW.entidad_id)
  WHEN 'EVENTO_DOMINIO' THEN EXISTS(SELECT 1 FROM evento_dominio e JOIN documento_solicitud d
      ON d.organizacion_id=e.organizacion_id AND d.id_documento=e.entidad_id WHERE e.organizacion_id=NEW.organizacion_id
       AND e.id_evento=origen AND e.tipo='DOCUMENTO_SOLICITUD_REVISADO' AND e.entidad_tipo='DOCUMENTO_SOLICITUD'
       AND NEW.entidad_tipo='SOLICITUD_ALQUILER' AND d.id_solicitud=NEW.entidad_id)
  ELSE false END;
 IF valida IS NOT TRUE THEN RAISE EXCEPTION 'El aviso exige hecho y recurso canónicos del mismo ámbito' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' AND (fuente->>'responsableAlDetectar')::bigint<>NEW.id_rol_agente THEN
  RAISE EXCEPTION 'El aviso conserva al responsable de la fuente al originarse' USING ERRCODE='23514';
 END IF;
 IF NEW.id_antecedente_c7 IS NOT NULL AND NOT EXISTS(SELECT 1 FROM alerta a
    WHERE a.organizacion_id=NEW.organizacion_id AND a.id_alerta=NEW.id_antecedente_c7 AND a.id_alerta<NEW.id_alerta
     AND a.entidad_tipo=NEW.entidad_tipo AND a.entidad_id=NEW.entidad_id AND a.tipo=NEW.tipo AND a.huella_causa_c7 IS NOT NULL) THEN
  RAISE EXCEPTION 'El antecedente del aviso conserva recurso y finalidad, sin ciclos' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER conservar_causa_alerta_c7 BEFORE INSERT OR UPDATE OR DELETE ON alerta
 FOR EACH ROW EXECUTE FUNCTION conservar_causa_alerta_c7();
